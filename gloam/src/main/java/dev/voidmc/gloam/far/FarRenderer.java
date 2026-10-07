package dev.voidmc.gloam.far;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.voidmc.gloam.Gloam;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.system.MemoryUtil;

/**
 * Draws the far air below the clouds through Mojang's GPU layer, so it runs on the OpenGL and the Vulkan backend alike,
 * into vanilla's main pass after the translucent terrain and before the weather, in the same reversed depth buffer as
 * the chunks and VOID LOD's far land: the far mist, a mesh lying on the far valleys ({@link FarMist}), and the rain
 * curtains, quads hanging under the far rain ({@link FarRain}); translucent, tested against the depth and not writing
 * it. Over a shader pack's finished picture it first writes the far land's depth the pack drew into the main depth
 * buffer ({@link #drawOverPack}). The clouds themselves are vanilla's renderer drawing Gloam's cells ({@link CloudDeck}).
 * Everything is uploaded beforehand ({@link #prepare}), while the frame graph is built and no pass is open.
 */
final class FarRenderer implements AutoCloseable {
	static final BindGroupLayout FAR_INFO = BindGroupLayout.builder().withUniform("GloamFar", UniformType.UNIFORM_BUFFER).build();
	static final BindGroupLayout NOISE_SAMPLER = BindGroupLayout.builder().withUniform("CloudNoise", UniformType.COMBINED_IMAGE_SAMPLER).build();
	/** std140: a mat4 and ten vec4s ({@code farinfo.glsl}). */
	static final int INFO_BYTES = 64 + 10 * 16;

	static final VertexFormat MIST_VERTEX = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("MistData", GpuFormat.RGBA8_UNORM)
		.build();
	static final VertexFormat RAIN_VERTEX = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Curtain", GpuFormat.RGBA32_FLOAT)
		.addAttribute("Shape", GpuFormat.RGBA32_FLOAT)
		.build();

	/** Tested against the world's depth, never writing it: the far air is all translucent. */
	private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

	private static RenderPipeline translucent(String name, VertexFormat vertices) {
		return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withLocation(Gloam.id("pipeline/" + name))
			.withVertexShader(Gloam.id("core/" + name))
			.withFragmentShader(Gloam.id("core/" + name))
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.FOG)
			.withBindGroupLayout(FAR_INFO)
			.withBindGroupLayout(NOISE_SAMPLER)
			.withVertexBinding(0, vertices)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(false)
			.withDepthStencilState(TEST_ONLY)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build();
	}

	static final BindGroupLayout DEPTH_MERGE = BindGroupLayout.builder()
		.withUniform("GloamFarDepth", UniformType.UNIFORM_BUFFER)
		.withUniform("FarDepth", UniformType.COMBINED_IMAGE_SAMPLER)
		.build();
	/** The far land's depth, as a shader pack drew it, written into the main depth buffer: depth only, no colour. */
	static final RenderPipeline MERGE = RenderPipeline.builder()
		.withLocation(Gloam.id("pipeline/far_depth_merge"))
		.withVertexShader(Gloam.id("core/far_screen"))
		.withFragmentShader(Gloam.id("core/far_depth_merge"))
		.withBindGroupLayout(DEPTH_MERGE)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
		.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
		.build();
	static final RenderPipeline MIST = translucent("far_mist", MIST_VERTEX);
	static final RenderPipeline RAIN = translucent("far_rain", RAIN_VERTEX);

	private final GpuDevice device;
	private final GpuTexture noise;
	private final GpuTextureView noiseView;
	private final ByteBuffer info = MemoryUtil.memAlloc(INFO_BYTES);
	private GpuBufferSlice infoSlice;
	private GpuBuffer mist;
	private int mistQuads;
	private GpuBufferSlice rain;
	private int rainQuads;
	/** What this frame draws (set by {@link #prepare}). */
	private boolean drawMist, drawRain;
	private boolean prepared;

	// timing: GPU time of the draws from timestamp queries, smoothed; negative where the device has none
	private static final int TIMER_SLOTS = 4;
	private final GpuQueryPool timer;
	private final boolean[] timerPending = new boolean[TIMER_SLOTS];
	private int timerSlot;
	double gpuMillis = -1;
	/** How many frames the far air has been drawn in (for the statistics). */
	long draws;

	FarRenderer() {
		this.device = RenderSystem.getDevice();
		this.noise = this.device.createTexture("VOID Gloam far noise", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, CloudNoise.SIZE, CloudNoise.SIZE, 1, 1);
		this.noiseView = this.device.createTextureView(this.noise);
		ByteBuffer texels = CloudNoise.make();
		try {
			this.device.createCommandEncoder().writeToTexture(this.noise, texels, 0, 0, 0, 0, CloudNoise.SIZE, CloudNoise.SIZE);
		} finally {
			MemoryUtil.memFree(texels);
		}
		GpuQueryPool timer = null;
		try {
			timer = this.device.createTimestampQueryPool(TIMER_SLOTS * 2);
		} catch (RuntimeException e) {
			// no timestamp queries on this device: the statistics go without GPU time
		}
		this.timer = timer;
	}

	/**
	 * Uploads this frame's uniforms ({@code info}, already filled), a new mist mesh if there is one, and the curtains.
	 * Called while the frame graph is built, before any pass runs.
	 */
	void prepare(ByteBuffer info, FarMist.Mesh mistMesh, FarRain rain, boolean mist, boolean drawRain) {
		CommandEncoder encoder = this.device.createCommandEncoder();
		if (mistMesh != null) {
			if (this.mist != null) {
				this.mist.close();
				this.mist = null;
			}
			this.mistQuads = mistMesh.quads();
			if (this.mistQuads > 0) {
				this.mist = this.device.createBuffer(() -> "VOID Gloam far mist", GpuBuffer.USAGE_VERTEX, mistMesh.vertices());
			}
		}
		var transientMemory = encoder.transientMemory();
		this.info.clear();
		this.info.put(info);
		this.info.flip();
		this.infoSlice = transientMemory.uploadGpu(this.info, this.device.getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
		this.rainQuads = drawRain ? rain.count() : 0;
		this.rain = this.rainQuads > 0 ? transientMemory.uploadGpu(rain.vertices(), 16, GpuBuffer.USAGE_VERTEX) : null;
		this.drawMist = mist && this.mist != null && this.mistQuads > 0;
		this.drawRain = this.rainQuads > 0;
		int quads = Math.max(this.drawMist ? this.mistQuads : 0, this.rainQuads);
		if (quads > 0) {
			// grown now, outside any pass; vanilla may grow it further before the draw, so it is fetched again there
			RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(quads * 6);
		}
		this.prepared = true;
	}

	/** Nothing to draw this frame. */
	void skip() {
		this.prepared = false;
	}

	/** Whether anything is to be drawn this frame. */
	boolean anything() {
		return this.prepared && (this.drawMist || this.drawRain);
	}

	/** Draws the prepared far air into {@code pass} (vanilla's main pass, or one of its own). */
	void draw(RenderPass pass) {
		if (!this.anything()) {
			return;
		}
		this.prepared = false;
		this.draws++;
		pass.pushDebugGroup(() -> "VOID Gloam far air");
		boolean timed = this.harvestTimer();
		if (timed) {
			pass.writeTimestamp(this.timer, this.timerSlot * 2);
		}
		pass.setUniform("GloamFar", this.infoSlice);
		pass.setUniform("CloudNoise", this.noiseView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		if (this.drawMist) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(MIST));
			pass.setIndexBuffer(quads.getBuffer(), quads.type());
			pass.setVertexBuffer(0, this.mist.slice());
			pass.drawIndexed(this.mistQuads * 6, 1, 0, 0, 0);
		}
		if (this.drawRain) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(RAIN));
			pass.setIndexBuffer(quads.getBuffer(), quads.type());
			pass.setVertexBuffer(0, this.rain);
			pass.drawIndexed(this.rainQuads * 6, 1, 0, 0, 0);
		}
		if (timed) {
			pass.writeTimestamp(this.timer, this.timerSlot * 2 + 1);
			this.timerPending[this.timerSlot] = true;
		}
		pass.popDebugGroup();
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

	private BorrowedDepth farDepth;
	private GpuTextureView farDepthView;
	private final ByteBuffer mergeInfo = MemoryUtil.memAlloc(80);
	private final org.joml.Matrix4f farToMain = new org.joml.Matrix4f();

	/**
	 * Draws the prepared far air over a shader pack's finished picture: first the far land's depth, as the pack drew it
	 * through VOID LOD, into the main depth buffer (which holds the chunks'), then the far air tested against both.
	 */
	void drawOverPack(com.mojang.blaze3d.pipeline.RenderTarget target, int glDepth, org.joml.Matrix4fc lodProjection, org.joml.Matrix4fc projection,
		int depthWidth, int depthHeight, GpuBufferSlice fog) {
		if (!this.anything()) {
			return;
		}
		if (this.farDepth == null || this.farDepth.glId() != glDepth || this.farDepth.getWidth(0) != depthWidth || this.farDepth.getHeight(0) != depthHeight) {
			// the old wrapper is dropped, not closed: the texture is VOID LOD's
			this.farDepth = new BorrowedDepth(glDepth, depthWidth, depthHeight);
			this.farDepthView = this.device.createTextureView(this.farDepth);
		}
		int width = target.width, height = target.height;
		this.farToMain.set(projection).mul(new org.joml.Matrix4f(lodProjection).invert());
		this.mergeInfo.clear();
		this.farToMain.get(0, this.mergeInfo);
		this.mergeInfo.position(64);
		this.mergeInfo.putFloat(depthWidth / (float) width).putFloat(depthHeight / (float) height).putFloat(0).putFloat(0);
		this.mergeInfo.flip();
		CommandEncoder encoder = this.device.createCommandEncoder();
		GpuBufferSlice merge = encoder.transientMemory().uploadGpu(this.mergeInfo, this.device.getDeviceInfo().limits().minUniformOffsetAlignment(),
			GpuBuffer.USAGE_UNIFORM);
		int quads = Math.max(this.drawMist ? this.mistQuads : 0, this.rainQuads);
		if (quads > 0) {
			RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(quads * 6);
		}
		try (RenderPass pass = encoder.createRenderPass(() -> "VOID Gloam far air over the shader pack", target.getColorTextureView(), Optional.empty(),
			target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			if (fog != null) {
				pass.setUniform("Fog", fog);
			}
			pass.setUniform("GloamFarDepth", merge);
			pass.setUniform("FarDepth", this.farDepthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setPipeline(RenderSystem.getCompiledPipeline(MERGE));
			pass.draw(3, 1, 0, 0);
			this.draw(pass);
		}
	}

	int mistQuads() {
		return this.drawMist ? this.mistQuads : 0;
	}

	@Override
	public void close() {
		if (this.timer != null) {
			this.timer.close();
		}
		if (this.mist != null) {
			this.mist.close();
		}
		this.noiseView.close();
		this.noise.close();
		MemoryUtil.memFree(this.info);
		MemoryUtil.memFree(this.mergeInfo);
	}
}
