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
 * Draws the far air through Mojang's GPU layer, so it runs on the OpenGL and the Vulkan backend alike, into vanilla's
 * main pass after the translucent terrain and before the weather (where vanilla draws its clouds), in the same reversed
 * depth buffer as the chunks and VOID LOD's far land:
 * <ul>
 *   <li><b>The cloud deck</b>, one triangle over the screen: each pixel marches its ray through the cloud layer, the
 *       clouds shaped from the weather map and the noise, and writes the depth of the first cloud it meets, so the land
 *       in front of a cloud (a mountain against a storm) covers it and the land under a cloud does not.</li>
 *   <li><b>The far mist</b>, a mesh lying on the far valleys ({@link FarMist}), and <b>the rain curtains</b>, quads
 *       hanging under the far rain ({@link FarRain}): translucent, tested against the depth and not writing it.</li>
 * </ul>
 * Everything it needs is uploaded beforehand ({@link #prepare}), while the frame graph is built and no pass is open.
 */
final class FarRenderer implements AutoCloseable {
	static final BindGroupLayout FAR_INFO = BindGroupLayout.builder().withUniform("GloamFar", UniformType.UNIFORM_BUFFER).build();
	static final BindGroupLayout CLOUD_SAMPLERS = BindGroupLayout.builder()
		.withUniform("WeatherMap", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("CloudNoise", UniformType.COMBINED_IMAGE_SAMPLER)
		.build();
	static final BindGroupLayout NOISE_SAMPLER = BindGroupLayout.builder().withUniform("CloudNoise", UniformType.COMBINED_IMAGE_SAMPLER).build();
	/** std140: a mat4 and ten vec4s. */
	static final int INFO_BYTES = 64 + 10 * 16;
	/** The cloud shadows' picture, texels across. */
	static final int SHADE_SIZE = 1024;

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

	private static RenderPipeline clouds(boolean fast) {
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withLocation(Gloam.id(fast ? "pipeline/far_clouds_fast" : "pipeline/far_clouds"))
			.withVertexShader(Gloam.id("core/far_clouds"))
			.withFragmentShader(Gloam.id("core/far_clouds"))
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.FOG)
			.withBindGroupLayout(FAR_INFO)
			.withBindGroupLayout(CLOUD_SAMPLERS)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withDepthStencilState(TEST_ONLY)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA));
		if (fast) {
			b.withShaderDefine("FAR_FAST");
		}
		return b.build();
	}

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

	/** The cloud shadows' picture: one triangle over it, nothing tested, nothing blended. */
	static final RenderPipeline SHADE = RenderPipeline.builder()
		.withLocation(Gloam.id("pipeline/far_shade"))
		.withVertexShader(Gloam.id("core/far_shade"))
		.withFragmentShader(Gloam.id("core/far_shade"))
		.withBindGroupLayout(FAR_INFO)
		.withBindGroupLayout(CLOUD_SAMPLERS)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withDepthStencilState(Optional.empty())
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();
	static final RenderPipeline CLOUDS = clouds(false);
	static final RenderPipeline CLOUDS_FAST = clouds(true);
	static final RenderPipeline MIST = translucent("far_mist", MIST_VERTEX);
	static final RenderPipeline RAIN = translucent("far_rain", RAIN_VERTEX);

	private final GpuDevice device;
	private final GpuTexture weather;
	private final GpuTextureView weatherView;
	private final GpuTexture noise;
	private final GpuTextureView noiseView;
	private final GpuTexture shade;
	private final GpuTextureView shadeView;
	private final ByteBuffer info = MemoryUtil.memAlloc(INFO_BYTES);
	private GpuBufferSlice infoSlice;
	private GpuBuffer mist;
	private int mistQuads;
	private GpuBufferSlice rain;
	private int rainQuads;
	/** What this frame draws (set by {@link #prepare}). */
	private boolean drawClouds, fastClouds, drawMist, drawRain;
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
		this.weather = this.device.createTexture("VOID Gloam weather map", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, WeatherMap.SIZE, WeatherMap.SIZE, 1, 1);
		this.weatherView = this.device.createTextureView(this.weather);
		this.noise = this.device.createTexture("VOID Gloam cloud noise", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, CloudNoise.SIZE, CloudNoise.SIZE, 1, 1);
		this.noiseView = this.device.createTextureView(this.noise);
		this.shade = this.device.createTexture("VOID Gloam cloud shadows", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, SHADE_SIZE, SHADE_SIZE, 1, 1);
		this.shadeView = this.device.createTextureView(this.shade);
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
	 * Uploads this frame's uniforms ({@code info}, already filled), a new weather map or mist mesh if there is one, and
	 * the curtains. Called while the frame graph is built, before any pass runs.
	 */
	void prepare(ByteBuffer info, ByteBuffer weatherTexels, FarMist.Mesh mistMesh, FarRain rain, boolean clouds, boolean fast, boolean mist,
		boolean drawRain, boolean shadows) {
		CommandEncoder encoder = this.device.createCommandEncoder();
		if (weatherTexels != null) {
			encoder.writeToTexture(this.weather, weatherTexels, 0, 0, 0, 0, WeatherMap.SIZE, WeatherMap.SIZE);
		}
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
		if (shadows) {
			// the cloud shadows' picture, for VOID LOD to lay over its land this frame
			try (RenderPass pass = encoder.createRenderPass(() -> "VOID Gloam cloud shadows", this.shadeView, Optional.empty())) {
				pass.setUniform("GloamFar", this.infoSlice);
				var samplers = RenderSystem.getSamplerCache();
				pass.setUniform("WeatherMap", this.weatherView, samplers.getClampToEdge(FilterMode.LINEAR));
				pass.setUniform("CloudNoise", this.noiseView, samplers.getRepeat(FilterMode.LINEAR));
				pass.setPipeline(RenderSystem.getCompiledPipeline(SHADE));
				pass.draw(3, 1, 0, 0);
			}
		}
		this.drawClouds = clouds;
		this.fastClouds = fast;
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
		return this.prepared && (this.drawClouds || this.drawMist || this.drawRain);
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
		var samplers = RenderSystem.getSamplerCache();
		pass.setUniform("CloudNoise", this.noiseView, samplers.getRepeat(FilterMode.LINEAR));
		if (this.drawClouds) {
			// far to near: the deck is behind the curtains and the mist from anywhere below it
			pass.setUniform("WeatherMap", this.weatherView, samplers.getClampToEdge(FilterMode.LINEAR));
			pass.setPipeline(RenderSystem.getCompiledPipeline(this.fastClouds ? CLOUDS_FAST : CLOUDS));
			pass.draw(3, 1, 0, 0);
		}
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

	/** The cloud shadows' picture. */
	GpuTextureView shadeView() {
		return this.shadeView;
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
		this.weatherView.close();
		this.weather.close();
		this.noiseView.close();
		this.noise.close();
		this.shadeView.close();
		this.shade.close();
		MemoryUtil.memFree(this.info);
	}
}
