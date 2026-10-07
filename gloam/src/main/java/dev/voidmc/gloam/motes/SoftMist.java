package dev.voidmc.gloam.motes;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.compat.Shaders;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Soft ground mist: the sheets ({@link MistMote}) fade out where they come close to the scene behind them, so a sheet
 * meeting a slope or a step in the ground, seen nearly edge-on, thins away into it instead of ending in a straight line.
 *
 * <p>That needs the depth of the scene behind each pixel, and vanilla draws the translucent particles inside its main
 * pass, where the depth buffer is the pass's own attachment and cannot be read. So without a shader pack (and without
 * improved transparency, whose particles take vanilla's order-independent pipelines) the sheets' draw is held back
 * from vanilla's particle pass ({@link #defer}) and made once the main pass is over ({@link #draw}): the main depth is
 * copied into a texture of Gloam's own, and the sheets are drawn into the main picture with their soft pipeline
 * ({@link MoteLayers#GROUND_SOFT_PIPELINE}), tested against the same depth and reading the copy. The particle buffers
 * live until the frame ends, so the held-back draw is still there to make.
 *
 * <p>If a frame's held-back draw is never made (another mod reshaping vanilla's frame), the next frame finds it still
 * waiting and the sheets go back to vanilla's pass for good, drawn as before, only not soft.
 */
public final class SoftMist {
	private static boolean working = true, announced;
	private static @Nullable Pending pending;
	private static @Nullable GpuBufferSlice transforms;
	private static @Nullable GpuTextureView lightmap;
	private static @Nullable GpuTexture depth;
	private static @Nullable GpuTextureView depthView;
	/** The mist's uniforms (GloamMist in ground_mist.fsh): the camera modulo 256, the clouds' colour. std140. */
	private static final ByteBuffer INFO = MemoryUtil.memAlloc(32);

	private record Pending(StagedVertexBuffer.ExecuteInfo info, AbstractTexture atlas, GpuBufferSlice transforms, GpuTextureView lightmap) {
	}

	private SoftMist() {
	}

	/**
	 * Whether the sheets are drawn here this frame, on the world's texel grid (MistMote then hands its centre, size and
	 * seed over in the vertex data instead of a sprite and a tint): without a shader pack and without improved
	 * transparency, as long as drawing after the main pass works.
	 */
	public static boolean active() {
		return working && !Shaders.packInUse() && !Minecraft.getInstance().gameRenderer.useImprovedTransparency();
	}

	/** {@code v} modulo 256, as the sheets' centres and the camera are handed to the shader (floats keep their texels). */
	static double wrap(double v) {
		return v - Math.floor(v / 256.0) * 256.0;
	}

	/** The particle pass's own uniforms, as it binds them for a group of particles. */
	public static void capture(@Nullable GpuBufferSlice dynamicTransforms, GpuTextureView lightmapView) {
		transforms = dynamicTransforms;
		lightmap = lightmapView;
	}

	/**
	 * Whether the particle layer drawn next is the ground mist's and is held back, to be drawn soft after the main pass
	 * ({@code oit}: whether vanilla is drawing through its order-independent pipelines).
	 */
	public static boolean defer(Object layer, boolean oit, StagedVertexBuffer.ExecuteInfo info, AbstractTexture atlas) {
		if (layer != MoteLayers.GROUND || oit || !working || transforms == null || lightmap == null || Shaders.packInUse()) {
			return false;
		}
		if (pending != null) {
			// last frame's never got drawn: this frame is not shaped the way it is expected to be
			working = false;
			pending = null;
			Gloam.LOGGER.warn("VOID Gloam: the ground mist could not be drawn after the main pass; drawing it with the other particles (not soft)");
			return false;
		}
		pending = new Pending(info, atlas, transforms, lightmap);
		return true;
	}

	/** Draws the held-back sheets into {@code main}, reading a copy of its depth (once its main pass is over). */
	public static void draw(RenderTarget main) {
		Pending p = pending;
		pending = null;
		if (p == null || main.getDepthTexture() == null || main.getColorTextureView() == null) {
			return;
		}
		GpuTexture source = main.getDepthTexture();
		if (depth == null || depth.getWidth(0) != source.getWidth(0) || depth.getHeight(0) != source.getHeight(0) || depth.getFormat() != source.getFormat()) {
			close();
			GpuFormat format = source.getFormat();
			depth = RenderSystem.getDevice().createTexture("VOID Gloam mist depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, format,
				source.getWidth(0), source.getHeight(0), 1, 1);
			depthView = RenderSystem.getDevice().createTextureView(depth);
		}
		if (!announced) {
			announced = true;
			Gloam.LOGGER.info("VOID Gloam: soft ground mist drawn after the main pass, against a copy of the depth ({})", source.getFormat());
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
		Vector3fc cloud = Motes.cloud();
		INFO.clear();
		INFO.putFloat((float) wrap(eye.x)).putFloat((float) wrap(eye.y)).putFloat((float) wrap(eye.z)).putFloat(0);
		INFO.putFloat(cloud.x()).putFloat(cloud.y()).putFloat(cloud.z()).putFloat(1);
		INFO.flip();
		GpuBufferSlice info = encoder.transientMemory().uploadGpu(INFO, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(),
			GpuBuffer.USAGE_UNIFORM);
		encoder.copyTextureToTexture(source, depth, 0, 0, 0, 0, 0, source.getWidth(0), source.getHeight(0));
		try (RenderPass pass = encoder.createRenderPass(() -> "VOID Gloam ground mist", main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(),
			OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", p.transforms());
			pass.setUniform("Sampler2", p.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setUniform("SceneDepth", depthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setUniform("GloamMist", info);
			pass.setPipeline(RenderSystem.getCompiledPipeline(MoteLayers.GROUND_SOFT_PIPELINE));
			GpuBuffer vertices = p.info().vertexBuffer();
			pass.setVertexBuffer(0, vertices.slice());
			pass.setIndexBuffer(p.info().indexBuffer(), p.info().indexType());
			pass.setUniform("Sampler0", p.atlas().getTextureView(), p.atlas().getSampler());
			pass.drawIndexed(p.info().indexCount(), 1, p.info().firstIndex(), p.info().baseVertex(), 0);
		}
	}

	private static void close() {
		if (depthView != null) {
			depthView.close();
			depthView = null;
		}
		if (depth != null) {
			depth.close();
			depth = null;
		}
	}
}
