package dev.voidmc.lod.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.StagingBuffer;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
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
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.voidmc.lod.core.Fades;
import dev.voidmc.lod.core.LodEngine;
import dev.voidmc.lod.core.LongList;
import dev.voidmc.lod.core.Tile;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileKey;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
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
 *   <li><b>One arena, a few draws.</b> Every tile's mesh lives in a few large GPU heaps (Mojang's own TLSF
 *       {@link UberGpuBuffer}, the allocator the chunk renderer uses). Each frame the visible tiles become
 *       one indirect-draw command buffer and one per-tile instance buffer, and the whole LOD goes out in one
 *       {@code drawIndexedIndirect} per heap and kind of tile (blocky or smooth, dithered or not): the CPU
 *       cost does not grow with the view distance.</li>
 *   <li><b>16-byte vertices, and shared indices.</b> Positions are tile-relative 16-bit integers, then two
 *       RGBA8 colours; the tile's corner comes from the instance, and the camera is subtracted in integers.
 *       Smooth tiles all have the same grid, so they share one index buffer and upload only vertices.</li>
 *   <li><b>Drawn after the chunks, in the same depth buffer.</b> 26.3 renders with reversed depth into a
 *       32-bit float buffer, so pushing the far plane out to the LOD's reach costs no precision: the LOD
 *       and the chunks occlude each other correctly in one buffer, with no second target to composite,
 *       and the GPU's early depth test throws away every LOD fragment behind nearby terrain before
 *       shading it.</li>
 *   <li><b>No discard where it is not needed.</b> Only tiles in the seam with vanilla's chunks, or
 *       cross-fading between levels ({@link Fades}), use the shader variant with {@code discard}; the rest
 *       keep early depth testing.</li>
 *   <li><b>Frustum culling per tile</b> on the CPU, against the tile's real height range.</li>
 *   <li><b>Bounded uploads.</b> Meshes reach the GPU through a fixed staging buffer; what does not fit
 *       this frame waits for the next.</li>
 *   <li><b>Measured.</b> Timestamp queries around the draws give the GPU time, logged with the statistics.</li>
 * </ul>
 */
public final class LodRenderer implements LodEngine.Gpu, AutoCloseable {
	public static final VertexFormat VERTEX = VertexFormat.builder(0)
		.addAttribute("LodPos", GpuFormat.RGBA16_SINT)
		.addAttribute("LodTop", GpuFormat.RGBA8_UNORM)
		.addAttribute("LodSide", GpuFormat.RGBA8_UNORM)
		.build();
	public static final VertexFormat INSTANCE = VertexFormat.builder(1)
		.addAttribute("LodTile", GpuFormat.RGBA32_SINT)
		.build();
	public static final BindGroupLayout LOD_INFO = BindGroupLayout.builder().withUniform("LodInfo", UniformType.UNIFORM_BUFFER).build();
	private static final int LOD_INFO_SIZE = 64 + 16 + 16 + 16;
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
	public static final RenderPipeline PIPELINE_SMOOTH = RenderPipeline.builder(SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("void_lod", "pipeline/lod_smooth"))
		.withShaderDefine("LOD_SMOOTH")
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.build();
	public static final RenderPipeline PIPELINE_SMOOTH_CLIPPED = RenderPipeline.builder(SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("void_lod", "pipeline/lod_smooth_clipped"))
		.withShaderDefine("LOD_SMOOTH")
		.withShaderDefine("LOD_CLIP")
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.build();

	/**
	 * The same four variants for drawing over a shader pack's finished picture: haze fades into what the pack
	 * drew behind each pixel (Sampler1, a copy of its picture) instead of into vanilla's fog colour.
	 */
	private static final RenderPipeline[] OVER_PACK = {
		overPack("lod", false, false), overPack("lod_clipped", false, true),
		overPack("lod_smooth", true, false), overPack("lod_smooth_clipped", true, true),
	};

	private static RenderPipeline overPack(String name, boolean smooth, boolean clipped) {
		RenderPipeline.Builder builder = RenderPipeline.builder(SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("void_lod", "pipeline/" + name + "_over_pack"))
			.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
			.withShaderDefine("LOD_OVER_PACK");
		if (smooth) {
			builder.withShaderDefine("LOD_SMOOTH").withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
		}
		if (clipped) {
			builder.withShaderDefine("LOD_CLIP");
		}
		return builder.build();
	}

	private final LodEngine engine;
	private final GpuDevice device;
	private final StagingBuffer staging;
	private final UberGpuBuffer<Tile> arena;
	private final boolean indirect;
	private final boolean firstInstance;
	private final int maxIndirect;
	private final FrustumIntersection frustum = new FrustumIntersection();
	private final Fades fades = new Fades();
	private double viewX;
	private double viewY;
	private double viewZ;
	/** The selection's view test: a world-space box against this frame's frustum. */
	private final dev.voidmc.lod.core.LodSelector.Visibility visibility = (x0, y0, z0, x1, y1, z1) -> this.frustum.testAab(
		(float) (x0 - this.viewX), (float) (y0 - this.viewY), (float) (z0 - this.viewZ),
		(float) (x1 - this.viewX), (float) (y1 - this.viewY), (float) (z1 - this.viewZ));
	private final Matrix4f viewProjection = new Matrix4f();
	private final Matrix4f modelView = new Matrix4f();

	// this frame's draws, grouped by heap and by pipeline variant
	private final List<Group> order = new ArrayList<>();
	private ByteBuffer instances = MemoryUtil.memAlloc(INSTANCE_BYTES * 1024);
	private ByteBuffer commands = MemoryUtil.memAlloc(COMMAND_BYTES * 1024);
	private final ByteBuffer info = MemoryUtil.memAlloc(LOD_INFO_SIZE);
	// a copy of the shader pack's picture, for drawOver's haze
	private GpuTexture backdrop;
	private GpuTextureView backdropView;
	private GpuBufferSlice instanceSlice;
	private GpuBufferSlice commandSlice;
	private GpuBufferSlice infoSlice;
	private GpuBuffer indexBuffer;
	private IndexType indexType;
	/** The index buffer every smooth tile shares: they all have the same grid. */
	private final GpuBuffer gridIndices;
	private int drawCount;

	// statistics
	public int lastTiles;
	public long lastQuads;
	public long lastPrepareNanos;
	/** Where the last prepare's time went: selection and uploads (engine), fades, staging copies, draw buffers. */
	public long lastEngineNanos;
	public long lastStagingNanos;
	public long lastBufferNanos;
	/** GPU time of the LOD's draws, smoothed over recent frames, from timestamp queries; negative if unsupported. */
	public double gpuMillis = -1;
	private static final int TIMER_SLOTS = 4;
	private final GpuQueryPool timer;
	private final boolean[] timerPending = new boolean[TIMER_SLOTS];
	private int timerSlot;

	private static final class Group {
		final GpuBuffer heap;
		final boolean smooth;
		final boolean clipped;
		final List<int[]> draws = new ArrayList<>();
		int firstCommand;

		Group(GpuBuffer heap, boolean smooth, boolean clipped) {
			this.heap = heap;
			this.smooth = smooth;
			this.clipped = clipped;
		}

		RenderPipeline pipeline(boolean overPack) {
			if (overPack) {
				return OVER_PACK[(this.smooth ? 2 : 0) + (this.clipped ? 1 : 0)];
			}
			return this.smooth ? (this.clipped ? PIPELINE_SMOOTH_CLIPPED : PIPELINE_SMOOTH) : (this.clipped ? PIPELINE_CLIPPED : PIPELINE);
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
		this.gridIndices = this.device.createBuffer(() -> "VOID LOD grid indices", GpuBuffer.USAGE_INDEX, TileBuilder.smoothIndices());
		GpuQueryPool timer = null;
		try {
			timer = this.device.createTimestampQueryPool(TIMER_SLOTS * 2);
		} catch (RuntimeException e) {
			// no timestamp queries on this device: the statistics go without GPU time
		}
		this.timer = timer;
	}

	/** Folds in the GPU times that have come back, and says whether the next slot is free to time a frame. */
	private boolean harvestTimer() {
		if (this.timer == null) {
			return false;
		}
		for (int s = 0; s < TIMER_SLOTS; s++) {
			if (!this.timerPending[s]) {
				continue;
			}
			var values = this.timer.getValues(s * 2, 2);
			if (values[0].isPresent() && values[1].isPresent()) {
				double ms = (values[1].getAsLong() - values[0].getAsLong()) * (double) this.device.getDeviceInfo().timestampPeriod() / 1e6;
				this.gpuMillis = this.gpuMillis < 0 ? ms : this.gpuMillis * 0.9 + ms * 0.1;
				this.timerPending[s] = false;
			}
		}
		this.timerSlot = (this.timerSlot + 1) % TIMER_SLOTS;
		return !this.timerPending[this.timerSlot];
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
	public void prepare(CameraRenderState camera, double radius, double clip, double fade, double pixelsPerCell, double farPixelsPerCell,
		float[] haze, float[] sun) {
		long t0 = System.nanoTime();
		this.order.clear();
		this.drawCount = 0;
		this.lastTiles = 0;
		this.lastQuads = 0;
		double cx = camera.pos.x;
		double cy = camera.pos.y;
		double cz = camera.pos.z;
		this.viewX = cx;
		this.viewY = cy;
		this.viewZ = cz;
		int height = Minecraft.getInstance().gameRenderer.mainRenderTarget().height;
		// pixels per block at distance one: half the viewport over tan(fov / 2), which is m11 of the projection
		double scale = 0.5 * height * Math.abs(camera.projectionMatrix.m11());
		this.modelView.set(camera.viewRotationMatrix);
		this.viewProjection.set(camera.projectionMatrix).mul(this.modelView);
		this.frustum.set(this.viewProjection, false);
		// zoomed in past a 70 degree field of view, the land out of view keeps the detail 70 degrees would give it
		double normal = 0.5 * height / Math.tan(Math.toRadians(35));
		double outOfView = Math.min(1, normal / scale);
		LongList draw = this.engine.update(this, cx, cy, cz, radius, clip, scale, pixelsPerCell, farPixelsPerCell, this.visibility, outOfView);
		long t1 = System.nanoTime();
		this.lastEngineNanos = t1 - t0;
		this.fades.update(draw, t1, this.engine::ready);

		CommandEncoder encoder = this.device.createCommandEncoder();
		long t2 = System.nanoTime();
		try (StagingBuffer.Uploader uploader = this.staging.startUploading(encoder)) {
			this.arena.uploadStagedAllocations(this.device, uploader);
		}
		long t3 = System.nanoTime();
		this.lastStagingNanos = t3 - t2;

		int bx = camera.blockPos.getX();
		int by = camera.blockPos.getY();
		int bz = camera.blockPos.getZ();
		this.instances.clear();
		int maxQuads = 0;
		double clipReach = clip + fade;
		for (int i = 0; i < this.fades.size(); i++) {
			long key = this.fades.key(i);
			int tileFade = this.fades.fade(i);
			if (tileFade < 0) {
				// fading out: keep it on the GPU until it has gone
				this.engine.touch(key);
			}
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
			// the dithered variant: tiles in the seam with vanilla's chunks, and tiles fading in or out
			boolean clipped = tileFade != 0 || nx * nx + nz * nz < clipReach * clipReach;
			GpuBuffer heap = this.arena.getGpuBuffer(alloc);
			Group group = this.group(heap, tile.smooth, clipped);
			int instance = this.lastTiles++;
			this.ensureInstances(instance + 1);
			this.instances.putInt(x0).putInt(z0).putInt(level).putInt(tileFade);
			group.draws.add(new int[]{tile.indices, (int) (alloc.getOffsetFromHeap() / TileBuilder.VERTEX_BYTES), instance});
			if (!tile.smooth) {
				maxQuads = Math.max(maxQuads, tile.quads);
			}
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
		this.info.putFloat(haze[0]).putFloat(haze[1]).putFloat(haze[2]).putFloat(haze[3]);
		this.info.putFloat(sun[0]).putFloat(sun[1]).putFloat(sun[2]).putFloat(sun[3]);
		this.info.flip();
		this.infoSlice = transient_.uploadGpu(this.info, this.device.getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		this.indexBuffer = quads.getBuffer(Math.max(1, maxQuads) * 6);
		this.indexType = quads.type();
		long t4 = System.nanoTime();
		this.lastBufferNanos = t4 - t3;
		this.lastPrepareNanos = t4 - t0;
	}

	/** At most four groups per heap (blocky or smooth, clipped or not), and a heap is 64 MB: the list stays short. */
	private Group group(GpuBuffer heap, boolean smooth, boolean clipped) {
		for (Group g : this.order) {
			if (g.heap == heap && g.smooth == smooth && g.clipped == clipped) {
				return g;
			}
		}
		Group g = new Group(heap, smooth, clipped);
		this.order.add(g);
		return g;
	}

	/** Draws the prepared tiles into vanilla's main pass, after its opaque terrain. */
	public void draw(RenderPass pass) {
		this.draw(pass, false);
	}

	/**
	 * Draws over {@code target}'s finished picture, for a shader pack: Iris renders the world through the pack's
	 * own buffers and lighting, which know nothing of the LOD, so the LOD waits until the pack has written its
	 * final image and then draws on top, tested against the depth the world left, so only sky is covered. Its
	 * haze fades into a copy of the pack's picture, so the far land melts into the pack's own sky.
	 */
	public void drawOver(RenderTarget target) {
		if (this.drawCount == 0) {
			return;
		}
		GpuTexture color = target.getColorTexture();
		int width = color.getWidth(0), height = color.getHeight(0);
		if (this.backdrop == null || this.backdrop.getWidth(0) != width || this.backdrop.getHeight(0) != height
			|| this.backdrop.getFormat() != color.getFormat()) {
			this.closeBackdrop();
			this.backdrop = this.device.createTexture("VOID LOD backdrop", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				color.getFormat(), width, height, 1, 1);
			this.backdropView = this.device.createTextureView(this.backdrop);
		}
		CommandEncoder encoder = this.device.createCommandEncoder();
		encoder.copyTextureToTexture(color, this.backdrop, 0, 0, 0, 0, 0, width, height);
		try (RenderPass pass = encoder.createRenderPass(() -> "VOID LOD over the shader pack", target.getColorTextureView(), Optional.empty(),
			target.getDepthTextureView(), OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("Sampler1", this.backdropView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			this.draw(pass, true);
		}
	}

	private void closeBackdrop() {
		if (this.backdropView != null) {
			this.backdropView.close();
			this.backdropView = null;
		}
		if (this.backdrop != null) {
			this.backdrop.close();
			this.backdrop = null;
		}
	}

	private void draw(RenderPass pass, boolean overPack) {
		if (this.drawCount == 0) {
			return;
		}
		pass.pushDebugGroup(() -> "VOID LOD");
		boolean timed = this.harvestTimer();
		if (timed) {
			pass.writeTimestamp(this.timer, this.timerSlot * 2);
		}
		pass.setUniform("LodInfo", this.infoSlice);
		pass.setUniform("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		pass.setVertexBuffer(1, this.instanceSlice);
		for (Group group : this.order) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(group.pipeline(overPack)));
			if (group.smooth) {
				pass.setIndexBuffer(this.gridIndices, IndexType.SHORT);
			} else {
				pass.setIndexBuffer(this.indexBuffer, this.indexType);
			}
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
		if (timed) {
			pass.writeTimestamp(this.timer, this.timerSlot * 2 + 1);
			this.timerPending[this.timerSlot] = true;
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
		this.closeBackdrop();
		if (this.timer != null) {
			this.timer.close();
		}
		this.gridIndices.close();
		this.arena.close();
		this.staging.close();
		MemoryUtil.memFree(this.instances);
		MemoryUtil.memFree(this.commands);
		MemoryUtil.memFree(this.info);
	}
}
