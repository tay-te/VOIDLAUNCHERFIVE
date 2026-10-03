package dev.voidmc.lod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.StagingBuffer;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.voidmc.lod.core.LodEngine;
import dev.voidmc.lod.core.LongList;
import dev.voidmc.lod.core.Tile;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileKey;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Draws the LOD inside vanilla's own main pass, right after the opaque chunks, through Mojang's GPU layer,
 * so it runs unchanged on the OpenGL and the Vulkan backend.
 *
 * <p>Where the time goes, and why it is small:
 * <ul>
 *   <li><b>One arena, one draw.</b> Every tile's mesh lives in a few large GPU heaps (Mojang's own TLSF
 *       {@link UberGpuBuffer}, the allocator the chunk renderer uses). Each frame the visible tiles become
 *       one indirect-draw command buffer and one per-tile instance buffer, and the whole LOD goes out in
 *       a single {@code drawIndexedIndirect} per heap: the CPU cost does not grow with the view distance.</li>
 *   <li><b>12-byte vertices.</b> Positions are tile-relative 16-bit integers and the colour is four bytes;
 *       the tile's corner comes from the instance, and the camera is subtracted in integers.</li>
 *   <li><b>Drawn after the chunks, in the same depth buffer.</b> 26.3 renders with reversed depth into a
 *       32-bit float buffer, so pushing the far plane out to the LOD's reach costs no precision: the LOD
 *       and the chunks occlude each other correctly in one buffer, with no second target to composite,
 *       and the GPU's early depth test throws away every LOD fragment behind nearby terrain before
 *       shading it.</li>
 *   <li><b>No discard where it is not needed.</b> Only tiles that reach into vanilla's area use the
 *       shader variant that clips against it; the rest keep early depth testing.</li>
 *   <li><b>Frustum culling per tile</b> on the CPU, against the tile's real height range.</li>
 *   <li><b>Bounded uploads.</b> Meshes reach the GPU through a fixed staging buffer; what does not fit
 *       this frame waits for the next.</li>
 * </ul>
 */
public final class LodRenderer implements LodEngine.Gpu, AutoCloseable {
	public static final VertexFormat VERTEX = VertexFormat.builder(0)
		.addAttribute("LodPos", GpuFormat.RGBA16_SINT)
		.addAttribute("LodColor", GpuFormat.RGBA8_UNORM)
		.build();
	public static final VertexFormat INSTANCE = VertexFormat.builder(1)
		.addAttribute("LodTile", GpuFormat.RGBA32_SINT)
		.build();
	public static final BindGroupLayout LOD_INFO = BindGroupLayout.builder().withUniform("LodInfo", UniformType.UNIFORM_BUFFER).build();
	private static final int LOD_INFO_SIZE = 64 + 16;
	private static final int INSTANCE_BYTES = 16;
	private static final int COMMAND_BYTES = 20;

	private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.FOG)
		.withBindGroupLayout(BindGroupLayouts.SAMPLER2)
		.withBindGroupLayout(LOD_INFO)
		.withVertexShader(Identifier.fromNamespaceAndPath("void_lod", "core/lod"))
		.withFragmentShader(Identifier.fromNamespaceAndPath("void_lod", "core/lod"))
		.withVertexBinding(0, VERTEX)
		.withVertexBinding(1, INSTANCE)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withDepthStencilState(DepthStencilState.DEFAULT)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.buildSnippet();
	public static final RenderPipeline PIPELINE = RenderPipeline.builder(SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("void_lod", "pipeline/lod"))
		.build();
	public static final RenderPipeline PIPELINE_CLIPPED = RenderPipeline.builder(SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("void_lod", "pipeline/lod_clipped"))
		.withShaderDefine("LOD_CLIP")
		.build();

	private final LodEngine engine;
	private final GpuDevice device;
	private final StagingBuffer staging;
	private final UberGpuBuffer<Tile> arena;
	private final boolean indirect;
	private final boolean firstInstance;
	private final int maxIndirect;
	private final FrustumIntersection frustum = new FrustumIntersection();
	private final Matrix4f viewProjection = new Matrix4f();
	private final Matrix4f modelView = new Matrix4f();

	// this frame's draws, grouped by heap and by pipeline variant
	private final List<Group> order = new ArrayList<>();
	private ByteBuffer instances = MemoryUtil.memAlloc(INSTANCE_BYTES * 1024);
	private ByteBuffer commands = MemoryUtil.memAlloc(COMMAND_BYTES * 1024);
	private final ByteBuffer info = MemoryUtil.memAlloc(LOD_INFO_SIZE);
	private GpuBufferSlice instanceSlice;
	private GpuBufferSlice commandSlice;
	private GpuBufferSlice infoSlice;
	private GpuBuffer indexBuffer;
	private IndexType indexType;
	private int drawCount;

	// statistics
	public int lastTiles;
	public long lastQuads;
	public long lastPrepareNanos;

	private static final class Group {
		final GpuBuffer heap;
		final boolean clipped;
		final List<int[]> draws = new ArrayList<>();
		int firstCommand;

		Group(GpuBuffer heap, boolean clipped) {
			this.heap = heap;
			this.clipped = clipped;
		}
	}

	public LodRenderer(LodEngine engine) {
		this.engine = engine;
		this.device = RenderSystem.getDevice();
		this.staging = StagingBuffer.create("VOID LOD", this.device, 16 << 20);
		this.arena = new UberGpuBuffer<>("VOID LOD", GpuBuffer.USAGE_VERTEX, 64 << 20, TileBuilder.VERTEX_BYTES, this.staging);
		DeviceInfo info = this.device.getDeviceInfo();
		this.maxIndirect = info.limits().maxDrawIndirectDrawCount();
		this.firstInstance = info.features().nonZeroFirstInstance();
		this.indirect = this.maxIndirect > 0 && info.features().drawIndirect() && info.features().multiDrawIndirect()
			&& this.firstInstance && !info.hintsAndWorkarounds().multiDrawIndirectHasKnownIssues();
	}

	public boolean usesIndirect() {
		return this.indirect;
	}

	// ---- LodEngine.Gpu ------------------------------------------------------------------------------------

	@Override
	public boolean upload(Tile tile, ByteBuffer vertices) {
		if (!this.arena.addAllocation(tile, this.engine::uploaded, vertices)) {
			return false;
		}
		// marks the tile as holding arena space, so eviction and resets give it back
		tile.gpu = this;
		return true;
	}

	@Override
	public void free(Tile tile) {
		this.arena.removeAllocation(tile);
		tile.gpu = null;
	}

	// ---- per frame ----------------------------------------------------------------------------------------

	/**
	 * Selects, uploads and prepares this frame's draws. Runs on the render thread while the frame graph is
	 * built, before any pass executes, so every upload lands outside a render pass.
	 */
	public void prepare(CameraRenderState camera, double radius, double clip, double fade, double pixelsPerCell) {
		long t0 = System.nanoTime();
		this.order.clear();
		this.drawCount = 0;
		this.lastTiles = 0;
		this.lastQuads = 0;
		double cx = camera.pos.x;
		double cy = camera.pos.y;
		double cz = camera.pos.z;
		int height = Minecraft.getInstance().gameRenderer.mainRenderTarget().height;
		// pixels per block at distance one: half the viewport over tan(fov / 2), which is m11 of the projection
		double scale = 0.5 * height * Math.abs(camera.projectionMatrix.m11());
		LongList draw = this.engine.update(this, cx, cy, cz, radius, clip, scale, pixelsPerCell);

		CommandEncoder encoder = this.device.createCommandEncoder();
		try (StagingBuffer.Uploader uploader = this.staging.startUploading(encoder)) {
			this.arena.uploadStagedAllocations(this.device, uploader);
		}

		this.modelView.set(camera.viewRotationMatrix);
		this.viewProjection.set(camera.projectionMatrix).mul(this.modelView);
		this.frustum.set(this.viewProjection, false);
		int bx = camera.blockPos.getX();
		int by = camera.blockPos.getY();
		int bz = camera.blockPos.getZ();
		this.instances.clear();
		int maxQuads = 0;
		double clipReach = clip + fade;
		for (int i = 0; i < draw.size(); i++) {
			long key = draw.get(i);
			Tile tile = this.engine.tile(key);
			if (tile == null || tile.quads == 0) {
				continue;
			}
			TlsfAllocator.Allocation alloc = this.arena.getAllocation(tile);
			if (alloc == null) {
				continue;
			}
			int level = TileKey.level(key);
			int span = TileKey.span(level);
			int x0 = TileKey.minX(key);
			int z0 = TileKey.minZ(key);
			float rx = (float) (x0 - cx);
			float rz = (float) (z0 - cz);
			if (!this.frustum.testAab(rx, (float) (tile.minY - cy), rz, rx + span, (float) (tile.maxY - cy), rz + span)) {
				continue;
			}
			// does any of the tile reach into the clip ring (vanilla's area plus the dithered seam)?
			double nx = Math.max(0, Math.max(x0 - cx, cx - (x0 + span)));
			double nz = Math.max(0, Math.max(z0 - cz, cz - (z0 + span)));
			boolean clipped = nx * nx + nz * nz < clipReach * clipReach;
			GpuBuffer heap = this.arena.getGpuBuffer(alloc);
			Group group = this.group(heap, clipped);
			int instance = this.lastTiles++;
			this.ensureInstances(instance + 1);
			this.instances.putInt(x0).putInt(z0).putInt(level).putInt(0);
			int indices = tile.quads * 6;
			group.draws.add(new int[]{indices, (int) (alloc.getOffsetFromHeap() / TileBuilder.VERTEX_BYTES), instance});
			maxQuads = Math.max(maxQuads, tile.quads);
			this.lastQuads += tile.quads;
		}
		if (this.lastTiles == 0) {
			this.lastPrepareNanos = System.nanoTime() - t0;
			return;
		}

		// the command buffer, one run per group so each group is a single indirect draw
		this.commands.clear();
		int command = 0;
		for (Group group : this.order) {
			group.firstCommand = command;
			for (int[] d : group.draws) {
				this.ensureCommands(command + 1);
				this.commands.putInt(d[0]).putInt(1).putInt(0).putInt(d[1]).putInt(d[2]);
				command++;
			}
		}
		this.drawCount = command;
		this.instances.flip();
		this.commands.flip();
		var transient_ = encoder.transientMemory();
		this.instanceSlice = transient_.uploadGpu(this.instances, INSTANCE_BYTES, GpuBuffer.USAGE_VERTEX);
		if (this.indirect) {
			this.commandSlice = transient_.uploadGpu(this.commands, 4, GpuBuffer.USAGE_INDIRECT_PARAMETERS);
		}
		this.info.clear();
		this.modelView.get(0, this.info);
		this.info.position(64);
		this.info.putFloat((float) clip).putFloat((float) fade).putFloat(0).putFloat(0);
		this.info.flip();
		this.infoSlice = transient_.uploadGpu(this.info, this.device.getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		this.indexBuffer = quads.getBuffer(maxQuads * 6);
		this.indexType = quads.type();
		this.lastPrepareNanos = System.nanoTime() - t0;
	}

	/** At most two groups per heap (clipped or not), and a heap is 64 MB: the list stays a handful long. */
	private Group group(GpuBuffer heap, boolean clipped) {
		for (Group g : this.order) {
			if (g.heap == heap && g.clipped == clipped) {
				return g;
			}
		}
		Group g = new Group(heap, clipped);
		this.order.add(g);
		return g;
	}

	/** Draws the prepared tiles into vanilla's main pass, after its opaque terrain. */
	public void draw(RenderPass pass) {
		if (this.drawCount == 0) {
			return;
		}
		pass.pushDebugGroup(() -> "VOID LOD");
		pass.setUniform("LodInfo", this.infoSlice);
		pass.setUniform("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		pass.setIndexBuffer(this.indexBuffer, this.indexType);
		pass.setVertexBuffer(1, this.instanceSlice);
		for (Group group : this.order) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(group.clipped ? PIPELINE_CLIPPED : PIPELINE));
			pass.setVertexBuffer(0, group.heap.slice());
			int count = group.draws.size();
			if (this.indirect) {
				long offset = this.commandSlice.offset() + (long) group.firstCommand * COMMAND_BYTES;
				while (count > 0) {
					int n = Math.min(count, this.maxIndirect);
					pass.drawIndexedIndirect(new GpuBufferSlice(this.commandSlice.buffer(), offset, (long) n * COMMAND_BYTES), n);
					offset += (long) n * COMMAND_BYTES;
					count -= n;
				}
			} else {
				for (int[] d : group.draws) {
					if (this.firstInstance) {
						pass.drawIndexed(d[0], 1, 0, d[1], d[2]);
					} else {
						pass.setVertexBuffer(1, this.instanceSlice.slice((long) d[2] * INSTANCE_BYTES, INSTANCE_BYTES));
						pass.drawIndexed(d[0], 1, 0, d[1], 0);
					}
				}
				if (!this.firstInstance) {
					pass.setVertexBuffer(1, this.instanceSlice);
				}
			}
		}
		pass.popDebugGroup();
	}

	private void ensureInstances(int count) {
		if (this.instances.capacity() < count * INSTANCE_BYTES) {
			this.instances = MemoryUtil.memRealloc(this.instances, this.instances.capacity() * 2);
		}
	}

	private void ensureCommands(int count) {
		if (this.commands.capacity() < count * COMMAND_BYTES) {
			this.commands = MemoryUtil.memRealloc(this.commands, this.commands.capacity() * 2);
		}
	}

	@Override
	public void close() {
		this.arena.close();
		this.staging.close();
		MemoryUtil.memFree(this.instances);
		MemoryUtil.memFree(this.commands);
		MemoryUtil.memFree(this.info);
	}
}
