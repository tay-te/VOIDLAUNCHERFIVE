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
import com.mojang.renderpearl.backend.opengl.GlBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.voidmc.lod.api.LodApi;
import dev.voidmc.lod.core.Fades;
import dev.voidmc.lod.core.Horizon;
import dev.voidmc.lod.core.LodEngine;
import dev.voidmc.lod.core.LongList;
import dev.voidmc.lod.core.Tile;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileKey;
import dev.voidmc.lod.mixin.LevelRendererAccessor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.LongPredicate;
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
 *   <li><b>Block textures only where blocks are pixels wide.</b> The blocky tiles next to the chunks sample the
 *       block atlas through a small material table ({@link MaterialTable}): a table read and an atlas fetch per
 *       textured pixel (two on a grass block's side), none where a block is under a pixel or two; the smooth
 *       far field never binds them.</li>
 *   <li><b>Frustum and horizon culling per tile</b> on the CPU: against the tile's real height range, then
 *       against the land in front of it ({@link Horizon}), so the valleys behind a ridge are not drawn.</li>
 *   <li><b>Bounded uploads.</b> Meshes reach the GPU through a fixed staging buffer; what does not fit
 *       this frame waits for the next.</li>
 *   <li><b>Measured.</b> Timestamp queries around the draws give the GPU time, logged with the statistics.</li>
 * </ul>
 */
public final class LodRenderer implements LodEngine.Gpu, AutoCloseable {
	private static final int GL_UNSIGNED_SHORT = 0x1403;
	private static final int GL_UNSIGNED_INT = 0x1405;
	public static final VertexFormat VERTEX = VertexFormat.builder(0)
		.addAttribute("LodPos", GpuFormat.RGBA16_SINT)
		.addAttribute("LodTop", GpuFormat.RGBA8_UNORM)
		.addAttribute("LodSide", GpuFormat.RGBA8_UNORM)
		.build();
	public static final VertexFormat INSTANCE = VertexFormat.builder(1)
		.addAttribute("LodTile", GpuFormat.RGBA32_SINT)
		.build();
	public static final BindGroupLayout LOD_INFO = BindGroupLayout.builder().withUniform("LodInfo", UniformType.UNIFORM_BUFFER).build();
	/** What the blocky tiles draw their textures with: the block atlas, and the table of where each material is in it. */
	public static final BindGroupLayout LOD_MATERIALS = BindGroupLayout.builder()
		.withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("LodMaterials", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
		.build();
	/**
	 * Light laid over the far land from above by another mod ({@code LodApi.setShade}: cloud shadows), and its stand-in,
	 * a texel that changes nothing, while none is.
	 */
	public static final BindGroupLayout LOD_SHADE = BindGroupLayout.builder().withUniform("LodShade", UniformType.COMBINED_IMAGE_SAMPLER).build();
	/** The season table ({@code LodApi.setSeasonTints}): per class and variant a target and amount, then a brightness. */
	public static final BindGroupLayout LOD_SEASONS = BindGroupLayout.builder().withUniform("LodSeasons", UniformType.UNIFORM_BUFFER).build();
	private static final int SEASON_ENTRIES = 128;
	private static final int SEASONS_SIZE = SEASON_ENTRIES * 2 * 16;
	private static final int LOD_INFO_SIZE = 64 + 16 + 16 + 16 + 48 + 16;
	private static final int INSTANCE_BYTES = 16;
	private static final int COMMAND_BYTES = 20;

	/**
	 * The far blocky tiles' quads, one record of four words each ({@link TileBuilder#RECORD_BYTES}): the shader reads
	 * its quad's record for each corner, by the vertex's index, so they have no vertex attributes of their own.
	 */
	public static final BindGroupLayout LOD_QUADS = BindGroupLayout.builder()
		.withUniform("LodQuads", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
		.build();

	/** Kinds of tile, each its own vertex layout or topology: near blocky, smooth, far blocky (compact). */
	private static final int BLOCKY = 0, SMOOTH = 1, COMPACT_KIND = 2;

	private static RenderPipeline.Snippet snippet(VertexFormat vertices) {
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.FOG)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER2)
			.withBindGroupLayout(LOD_INFO)
			.withBindGroupLayout(LOD_SHADE)
			.withBindGroupLayout(LOD_SEASONS)
			.withVertexShader(Identifier.fromNamespaceAndPath("void_lod", "core/lod"))
			.withFragmentShader(Identifier.fromNamespaceAndPath("void_lod", "core/lod"));
		if (vertices != null) {
			b.withVertexBinding(0, vertices);
		}
		return b.withVertexBinding(1, INSTANCE)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(DepthStencilState.DEFAULT)
			.withColorTargetState(ColorTargetState.DEFAULT)
			.buildSnippet();
	}

	/**
	 * Every variant, by kind of tile, whether it is dithered (in the seam with the chunks, fading, or with void in
	 * it: the only variants with {@code discard}) and whether it draws over a shader pack's finished picture (haze
	 * fading into what the pack drew behind each pixel, Sampler1, instead of into vanilla's fog colour).
	 */
	private static final RenderPipeline[] PIPELINES = new RenderPipeline[12];

	static {
		RenderPipeline.Snippet full = snippet(VERTEX), compact = snippet(null);
		String[] names = {"lod", "lod_smooth", "lod_compact"};
		for (int kind = 0; kind < 3; kind++) {
			for (int variant = 0; variant < 4; variant++) {
				boolean clipped = (variant & 2) != 0, overPack = (variant & 1) != 0;
				RenderPipeline.Builder b = RenderPipeline.builder(kind == COMPACT_KIND ? compact : full)
					.withLocation(Identifier.fromNamespaceAndPath("void_lod",
						"pipeline/" + names[kind] + (clipped ? "_clipped" : "") + (overPack ? "_over_pack" : "")));
				if (kind == BLOCKY) {
					// only the near blocky tiles draw block textures
					b.withBindGroupLayout(LOD_MATERIALS);
				}
				if (kind == SMOOTH) {
					b.withShaderDefine("LOD_SMOOTH").withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
				}
				if (kind == COMPACT_KIND) {
					b.withShaderDefine("LOD_COMPACT").withBindGroupLayout(LOD_QUADS);
				}
				if (clipped) {
					b.withShaderDefine("LOD_CLIP");
				}
				if (overPack) {
					b.withBindGroupLayout(BindGroupLayouts.SAMPLER1).withShaderDefine("LOD_OVER_PACK");
				}
				PIPELINES[kind * 4 + variant] = b.build();
			}
		}
	}

	static RenderPipeline pipeline(int kind, boolean clipped, boolean overPack) {
		return PIPELINES[kind * 4 + (clipped ? 2 : 0) + (overPack ? 1 : 0)];
	}

	private final LodEngine engine;
	private final GpuDevice device;
	private final StagingBuffer staging;
	private final UberGpuBuffer<Tile> arena;
	/** The far blocky tiles' meshes, in compact vertices: heaps of their own, since a heap has one vertex size. */
	private final UberGpuBuffer<Tile> compactArena;
	private final boolean indirect;
	private final boolean firstInstance;
	private final int maxIndirect;
	/** Whether faces that cannot face the camera are skipped ({@link #visibleRuns}); off only to compare. */
	public static volatile boolean faceCulling = true;
	/** Whether tiles hidden behind nearer land are skipped ({@link Horizon}); off only to compare. */
	public static volatile boolean occlusion = Boolean.parseBoolean(System.getProperty("void.lod.occlusion", "true"));

	private final FrustumIntersection frustum = new FrustumIntersection();
	private final Fades fades = new Fades();
	private final Horizon horizon = new Horizon();
	/** The engine's readiness test, made once: a method reference built every frame is an allocation every frame. */
	private final LongPredicate ready;
	private final MaterialTable materials;
	private final GpuTexture noShade;
	private final GpuTextureView noShadeView;
	private double viewX;
	private double viewY;
	private double viewZ;
	/** The selection's view test: a world-space box against this frame's frustum. */
	private final dev.voidmc.lod.core.LodSelector.Visibility visibility = (x0, y0, z0, x1, y1, z1) -> this.frustum.testAab(
		(float) (x0 - this.viewX), (float) (y0 - this.viewY), (float) (z0 - this.viewZ),
		(float) (x1 - this.viewX), (float) (y1 - this.viewY), (float) (z1 - this.viewZ));
	private final Matrix4f viewProjection = new Matrix4f();
	/** This tile's groups of faces that can face the camera (scratch). */
	private final boolean[] faceVisible = new boolean[6];

	private interface Run {
		void run(int firstQuad, int quads);
	}

	/**
	 * The runs of a grouped tile's quads that can face the camera at (cx, cy, cz): a wall's plane lies inside the tile,
	 * so walls facing north (toward -z) can be seen only from north of its far edge, and so on.
	 */
	private void visibleRuns(Tile tile, double cx, double cy, double cz, Run out) {
		int[] faces = tile.groups;
		int tx0 = TileKey.minX(tile.key), tz0 = TileKey.minZ(tile.key), span = TileKey.span(TileKey.level(tile.key));
		boolean[] visible = this.faceVisible;
		visible[TileBuilder.GROUP_NORTH] = cz < tz0 + span;
		visible[TileBuilder.GROUP_SOUTH] = cz > tz0;
		visible[TileBuilder.GROUP_WEST] = cx < tx0 + span;
		visible[TileBuilder.GROUP_EAST] = cx > tx0;
		visible[TileBuilder.GROUP_UP] = cy > tile.minY;
		visible[TileBuilder.GROUP_DOWN] = cy < tile.maxY;
		if (!faceCulling) {
			java.util.Arrays.fill(visible, true);
		}
		int run = -1;
		for (int g = 0; g <= 6; g++) {
			boolean on = g < 6 && visible[g] && faces[g + 1] > faces[g];
			if (on && run < 0) {
				run = g;
			} else if (!on && run >= 0) {
				out.run(faces[run], faces[g] - faces[run]);
				run = -1;
			}
		}
	}
	private final Matrix4f modelView = new Matrix4f();

	// this frame's draws, grouped by heap and by pipeline variant; groups are kept from frame to frame
	private final List<Group> order = new ArrayList<>();
	/** How many sections above and below the camera's vanilla draws: past them the LOD draws inside its radius too. */
	public int verticalSections;
	/** Whether tiles are drawn nearest first (off only to measure what it saves). */
	public static volatile boolean frontToBack = true;
	/** This frame's tiles in view, nearest first: each its distance's float bits over its index. */
	private long[] drawOrder = new long[256];
	// this frame's tiles in the frustum, waiting for the horizon's verdict
	private Tile[] inView = new Tile[256];
	private int[] inViewFade = new int[256];
	private boolean[] inViewClipped = new boolean[256];
	private TlsfAllocator.Allocation[] inViewAlloc = new TlsfAllocator.Allocation[256];
	private ByteBuffer instances = MemoryUtil.memAlloc(INSTANCE_BYTES * 1024);
	private ByteBuffer commands = MemoryUtil.memAlloc(COMMAND_BYTES * 1024);
	private final ByteBuffer info = MemoryUtil.memAlloc(LOD_INFO_SIZE);
	private final ByteBuffer seasons = MemoryUtil.memAlloc(SEASONS_SIZE);
	private int seasonsVersion = -1;
	private GpuBufferSlice seasonsSlice;
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
	/**
	 * This frame's tiles for a shader pack's Distant Horizons programs, while {@link #setPackMode pack mode} is on: then
	 * the frame is drawn by the pack ({@code compat.iris}) and not by this renderer's own pipelines.
	 */
	public final PackFrame packFrame = new PackFrame();
	private boolean packMode;
	public long lastQuads;
	/** Tiles in the frustum that the horizon hid. */
	public int lastOccluded;
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
		static final int DRAW_INTS = 4;

		final GpuBuffer heap;
		final int kind;
		final boolean clipped;
		/** This frame's draws, {@value #DRAW_INTS} ints each: index count, first index, base vertex, instance. */
		int[] draws = new int[DRAW_INTS * 64];
		int count;
		int firstCommand;

		Group(GpuBuffer heap, int kind, boolean clipped) {
			this.heap = heap;
			this.kind = kind;
			this.clipped = clipped;
		}

		void add(int indices, int firstIndex, int baseVertex, int instance) {
			int at = this.count * DRAW_INTS;
			if (at == this.draws.length) {
				this.draws = Arrays.copyOf(this.draws, at * 2);
			}
			this.draws[at] = indices;
			this.draws[at + 1] = firstIndex;
			this.draws[at + 2] = baseVertex;
			this.draws[at + 3] = instance;
			this.count++;
		}

		RenderPipeline pipeline(boolean overPack) {
			return LodRenderer.pipeline(this.kind, this.clipped, overPack);
		}
	}

	public LodRenderer(LodEngine engine) {
		this.engine = engine;
		this.ready = engine::ready;
		this.device = RenderSystem.getDevice();
		this.staging = StagingBuffer.create("VOID LOD", this.device, 16 << 20);
		this.arena = new UberGpuBuffer<>("VOID LOD", GpuBuffer.USAGE_VERTEX, 64 << 20, TileBuilder.VERTEX_BYTES, this.staging);
		this.compactArena = new UberGpuBuffer<>("VOID LOD far", GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, 64 << 20, TileBuilder.RECORD_BYTES,
			this.staging);
		DeviceInfo info = this.device.getDeviceInfo();
		this.maxIndirect = info.limits().maxDrawIndirectDrawCount();
		this.firstInstance = info.features().nonZeroFirstInstance();
		this.indirect = this.maxIndirect > 0 && info.features().drawIndirect() && info.features().multiDrawIndirect()
			&& this.firstInstance && !info.hintsAndWorkarounds().multiDrawIndirectHasKnownIssues();
		this.gridIndices = this.device.createBuffer(() -> "VOID LOD grid indices", GpuBuffer.USAGE_INDEX, TileBuilder.smoothIndices());
		this.materials = new MaterialTable(this.device);
		this.noShade = this.device.createTexture("VOID LOD no shade", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
		this.noShadeView = this.device.createTextureView(this.noShade);
		ByteBuffer half = MemoryUtil.memAlloc(4).put(0, (byte) 128).put(1, (byte) 128).put(2, (byte) 128).put(3, (byte) 255);
		try {
			this.device.createCommandEncoder().writeToTexture(this.noShade, half, 0, 0, 0, 0, 1, 1);
		} finally {
			MemoryUtil.memFree(half);
		}
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
		if (!this.arena(tile).addAllocation(tile, this.engine::uploaded, vertices)) {
			return false;
		}
		// marks the tile as holding arena space, so eviction and resets give it back
		tile.gpu = this;
		return true;
	}

	@Override
	public void free(Tile tile) {
		this.arena(tile).removeAllocation(tile);
		tile.gpu = null;
	}

	private UberGpuBuffer<Tile> arena(Tile tile) {
		return tile.compact ? this.compactArena : this.arena;
	}

	private static int kind(Tile tile) {
		return tile.smooth ? SMOOTH : tile.compact ? COMPACT_KIND : BLOCKY;
	}

	// ---- per frame ----------------------------------------------------------------------------------------

	/**
	 * Selects, uploads and prepares this frame's draws. Runs on the render thread while the frame graph is
	 * built, before any pass executes, so every upload lands outside a render pass.
	 */
	public void prepare(CameraRenderState camera, double radius, double clip, double fade, double pixelsPerCell, double farPixelsPerCell,
		float[] haze, float[] sun, float[] sky) {
		long t0 = System.nanoTime();
		for (Group group : this.order) {
			group.count = 0;
		}
		this.drawCount = 0;
		this.lastTiles = 0;
		this.packFrame.clear();
		this.packFrame.clip = (float) clip;
		this.lastQuads = 0;
		this.lastOccluded = 0;
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
		this.fades.update(draw, t1, this.ready);

		CommandEncoder encoder = this.device.createCommandEncoder();
		long t2 = System.nanoTime();
		try (StagingBuffer.Uploader uploader = this.staging.startUploading(encoder)) {
			this.arena.uploadStagedAllocations(this.device, uploader);
			this.compactArena.uploadStagedAllocations(this.device, uploader);
		}
		// after the uploads: every id a tile just uploaded was handed out before it was built
		// the world's registry itself: the source the engine holds is usually a wrapper around the generator (the seen
		// chunks over it, its cache), which does not hand it out
		dev.voidmc.lod.VoidLod lod = dev.voidmc.lod.VoidLod.get();
		this.materials.update(lod == null || this.engine.source() == null ? null : lod.materials(), encoder);
		long t3 = System.nanoTime();
		this.lastStagingNanos = t3 - t2;

		this.instances.clear();
		int maxQuads = 0;
		double clipReach = clip + fade;
		// the frustum first; then the horizon, near to far, over what is left
		this.horizon.begin(cx, cy, cz);
		int inView = 0;
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
			TlsfAllocator.Allocation alloc = this.arena(tile).getAllocation(tile);
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
			// the dithered variant: tiles in the seam with vanilla's chunks, tiles fading in or out, and tiles with void
			boolean clipped = tileFade != 0 || tile.holes || nx * nx + nz * nz < clipReach * clipReach;
			if (inView == this.inView.length) {
				int n = inView * 2;
				this.inView = Arrays.copyOf(this.inView, n);
				this.inViewFade = Arrays.copyOf(this.inViewFade, n);
				this.inViewClipped = Arrays.copyOf(this.inViewClipped, n);
				this.inViewAlloc = Arrays.copyOf(this.inViewAlloc, n);
			}
			this.inView[inView] = tile;
			this.inViewFade[inView] = tileFade;
			this.inViewClipped[inView] = clipped;
			this.inViewAlloc[inView] = alloc;
			// a dithered tile hides nothing: some of its pixels are its partner's in a fade, or the chunks'
			this.horizon.add(x0, z0, span, tile.maxY, clipped ? null : tile.occluders);
			inView++;
		}
		boolean cull = occlusion;
		if (cull) {
			this.horizon.cull();
			this.lastOccluded = this.horizon.culled();
		}
		// nearest first: the near land fills the depth buffer before the land it hides is shaded (early depth test), which
		// matters most through a shader pack, where every fragment is dear
		if (this.drawOrder.length < inView) {
			this.drawOrder = new long[this.inView.length];
		}
		for (int c = 0; c < inView; c++) {
			float near = (float) nearest(this.inView[c].key, cx, cz);
			this.drawOrder[c] = (long) Float.floatToIntBits(near) << 32 | c;
		}
		if (frontToBack) {
			Arrays.sort(this.drawOrder, 0, inView);
		}
		for (int o = 0; o < inView; o++) {
			int c = (int) this.drawOrder[o];
			Tile tile = this.inView[c];
			TlsfAllocator.Allocation alloc = this.inViewAlloc[c];
			this.inView[c] = null;
			this.inViewAlloc[c] = null;
			if (cull && this.horizon.hidden(c)) {
				continue;
			}
			int level = TileKey.level(tile.key);
			GpuBuffer heap = this.arena(tile).getGpuBuffer(alloc);
			if (this.packMode) {
				// the pack fades the LOD in toward the chunks itself; a tile on its way out would only double its successor
				if (this.inViewFade[c] < 0) {
					continue;
				}
				this.lastTiles++;
				this.packFrame.add(((GlBuffer) heap).handle(), (int) (alloc.getOffsetFromHeap() / TileBuilder.VERTEX_BYTES), tile.landIndices,
					tile.landIndices, tile.indices - tile.landIndices, TileKey.minX(tile.key) - cx, -TileBuilder.PACK_Y_OFFSET - cy,
					TileKey.minZ(tile.key) - cz, nearest(tile.key, cx, cz));
				if (tile.groups != null) {
					this.packFrame.clearRuns();
					this.visibleRuns(tile, cx, cy, cz, (first, quads) -> this.packFrame.addRun(first * 6, quads * 6));
				}
				maxQuads = Math.max(maxQuads, tile.quads);
				this.lastQuads += tile.quads;
				continue;
			}
			Group group = this.group(heap, kind(tile), this.inViewClipped[c]);
			int instance = this.lastTiles++;
			this.ensureInstances(instance + 1);
			this.instances.putInt(TileKey.minX(tile.key)).putInt(TileKey.minZ(tile.key)).putInt(level).putInt(this.inViewFade[c]);
			// a compact quad's four corners all read its record, at the vertex's index over four
			int stride = tile.compact ? TileBuilder.COMPACT_BYTES : TileBuilder.VERTEX_BYTES;
			int baseVertex = (int) (alloc.getOffsetFromHeap() / stride);
			int[] faces = tile.groups;
			if (faces == null) {
				group.add(tile.indices, tile.firstIndex, baseVertex, instance);
				this.lastQuads += tile.quads;
			} else {
				// only the faces that can be turned toward the camera: a wall's plane lies inside the tile, so walls facing
				// north (toward -z) can be seen only from north of the tile's far edge, and so on
				Group draws = group;
				this.visibleRuns(tile, cx, cy, cz, (first, quads) -> {
					draws.add(quads * 6, first * 6, baseVertex, instance);
					this.lastQuads += quads;
				});
			}
			if (!tile.smooth) {
				maxQuads = Math.max(maxQuads, tile.quads);
			}
		}
		if (this.packMode) {
			// the pack draws them: only the shared quad indices are needed, as GL handles
			this.drawCount = 0;
			if (this.lastTiles > 0) {
				RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
				this.indexBuffer = quads.getBuffer(Math.max(1, maxQuads) * 6);
				this.indexType = quads.type();
				this.packFrame.indexBuffer = ((GlBuffer) this.indexBuffer).handle();
				this.packFrame.indexType = this.indexType == IndexType.SHORT ? GL_UNSIGNED_SHORT : GL_UNSIGNED_INT;
				this.packFrame.indexBytes = this.indexType == IndexType.SHORT ? 2 : 4;
			}
			this.lastPrepareNanos = System.nanoTime() - t0;
			return;
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
			int[] d = group.draws;
			for (int at = 0; at < group.count * Group.DRAW_INTS; at += Group.DRAW_INTS) {
				this.ensureCommands(command + 1);
				this.commands.putInt(d[at]).putInt(1).putInt(d[at + 1]).putInt(d[at + 2]).putInt(d[at + 3]);
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
		this.info.putFloat((float) clip).putFloat((float) fade).putFloat(this.verticalSections).putFloat(0);
		this.info.putFloat(haze[0]).putFloat(haze[1]).putFloat(haze[2]).putFloat(haze[3]);
		this.info.putFloat(sun[0]).putFloat(sun[1]).putFloat(sun[2]).putFloat(sun[3]);
		for (float f : sky) {
			this.info.putFloat(f);
		}
		// the light laid over the far land (LodApi.setShade): the camera's place on it, its scale, its strength
		LodApi.Shade shade = LodApi.shade();
		if (shade != null) {
			this.info.putFloat((float) (cx - shade.originX())).putFloat((float) (cz - shade.originZ())).putFloat((float) (1.0 / shade.size()))
				.putFloat(shade.strength());
		} else {
			this.info.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
		}
		this.info.flip();
		this.infoSlice = transient_.uploadGpu(this.info, this.device.getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
		this.fillSeasons();
		this.seasons.rewind();
		this.seasonsSlice = transient_.uploadGpu(this.seasons, this.device.getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		this.indexBuffer = quads.getBuffer(Math.max(1, maxQuads) * 6);
		this.indexType = quads.type();
		long t4 = System.nanoTime();
		this.lastBufferNanos = t4 - t3;
		this.lastPrepareNanos = t4 - t0;
	}

	/**
	 * At most four groups per heap (blocky or smooth, clipped or not), and a heap is 64 MB: the list stays short.
	 * Groups are kept from frame to frame, so only a new heap or variant makes one; the groups left empty go
	 * then, so a heap the arena has let go of is not held.
	 */
	private Group group(GpuBuffer heap, int kind, boolean clipped) {
		for (Group g : this.order) {
			if (g.heap == heap && g.kind == kind && g.clipped == clipped) {
				return g;
			}
		}
		this.order.removeIf(g -> g.count == 0);
		Group g = new Group(heap, kind, clipped);
		this.order.add(g);
		return g;
	}

	/** Draws the prepared tiles into vanilla's main pass, after its opaque terrain. */
	public void draw(RenderPass pass) {
		this.draw(pass, false);
	}

	/**
	 * Whether a shader pack draws the LOD (through its Distant Horizons programs): then the frame goes into
	 * {@link #packFrame} instead of this renderer's draws.
	 */
	/** How close tile {@code key} comes to (cx, cz) across the ground, in blocks (0 if it is under the camera). */
	private static double nearest(long key, double cx, double cz) {
		int span = TileKey.span(TileKey.level(key));
		int x0 = TileKey.minX(key), z0 = TileKey.minZ(key);
		double nx = Math.max(0, Math.max(x0 - cx, cx - (x0 + span)));
		double nz = Math.max(0, Math.max(z0 - cz, cz - (z0 + span)));
		return Math.sqrt(nx * nx + nz * nz);
	}

	/** Whether the shader pack draws this renderer's tiles ({@link #packFrame}). */
	public boolean packMode() {
		return this.packMode;
	}

	public void setPackMode(boolean packMode) {
		this.packMode = packMode;
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
		pass.setUniform("LodSeasons", this.seasonsSlice);
		pass.setUniform("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		// the block atlas as the chunks sample it, for the blocky tiles' textures
		GpuSampler blocks = ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).voidLod$chunkLayerSampler();
		pass.setUniform("Sampler0", this.materials.atlas(), blocks != null ? blocks : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true));
		pass.setUniform("LodMaterials", this.materials.buffer());
		LodApi.Shade shade = LodApi.shade();
		pass.setUniform("LodShade", shade != null ? shade.texture() : this.noShadeView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		pass.setVertexBuffer(1, this.instanceSlice);
		for (Group group : this.order) {
			if (group.count == 0) {
				continue;
			}
			pass.setPipeline(RenderSystem.getCompiledPipeline(group.pipeline(overPack)));
			if (group.kind == SMOOTH) {
				pass.setIndexBuffer(this.gridIndices, IndexType.SHORT);
			} else {
				pass.setIndexBuffer(this.indexBuffer, this.indexType);
			}
			if (group.kind == COMPACT_KIND) {
				pass.setUniform("LodQuads", group.heap);
			} else {
				pass.setVertexBuffer(0, group.heap.slice());
			}
			int count = group.count;
			if (this.indirect) {
				long offset = this.commandSlice.offset() + (long) group.firstCommand * COMMAND_BYTES;
				while (count > 0) {
					int n = Math.min(count, this.maxIndirect);
					pass.drawIndexedIndirect(new GpuBufferSlice(this.commandSlice.buffer(), offset, (long) n * COMMAND_BYTES), n);
					offset += (long) n * COMMAND_BYTES;
					count -= n;
				}
			} else {
				int[] d = group.draws;
				for (int at = 0; at < count * Group.DRAW_INTS; at += Group.DRAW_INTS) {
					if (this.firstInstance) {
						pass.drawIndexed(d[at], 1, d[at + 1], d[at + 2], d[at + 3]);
					} else {
						pass.setVertexBuffer(1, this.instanceSlice.slice((long) d[at + 3] * INSTANCE_BYTES, INSTANCE_BYTES));
						pass.drawIndexed(d[at], 1, d[at + 1], d[at + 2], 0);
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

	/** The season table as the shader reads it, rewritten when it changes: targets and amounts, then brightnesses. */
	private void fillSeasons() {
		int version = dev.voidmc.lod.api.LodApi.seasonVersion();
		if (version == this.seasonsVersion) {
			return;
		}
		this.seasonsVersion = version;
		float[] table = dev.voidmc.lod.api.LodApi.seasonTints();
		ByteBuffer b = this.seasons;
		b.clear();
		for (int e = 0; e < SEASON_ENTRIES; e++) {
			if (table == null) {
				b.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
			} else {
				b.putFloat(table[e * 5]).putFloat(table[e * 5 + 1]).putFloat(table[e * 5 + 2]).putFloat(table[e * 5 + 3]);
			}
		}
		for (int e = 0; e < SEASON_ENTRIES; e++) {
			b.putFloat(table == null ? 1 : table[e * 5 + 4]).putFloat(0).putFloat(0).putFloat(0);
		}
		b.flip();
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
		this.noShadeView.close();
		this.noShade.close();
		if (this.timer != null) {
			this.timer.close();
		}
		this.gridIndices.close();
		this.materials.close();
		this.arena.close();
		this.compactArena.close();
		this.staging.close();
		MemoryUtil.memFree(this.instances);
		MemoryUtil.memFree(this.commands);
		MemoryUtil.memFree(this.info);
		MemoryUtil.memFree(this.seasons);
	}
}
