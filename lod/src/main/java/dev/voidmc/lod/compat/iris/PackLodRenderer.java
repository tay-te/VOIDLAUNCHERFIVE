package dev.voidmc.lod.compat.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import dev.voidmc.lod.render.PackFrame;
import java.util.Optional;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.DepthTexture;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL32C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LOD drawn by a shader pack, through the programs it carries for Distant Horizons. The pack then lights the far
 * terrain as it lights everything else, with its shadows, fog, sky and colour grading, and its composite passes treat it
 * as distant terrain: no seam where the chunks end.
 *
 * <p>The contract is Distant Horizons': the tiles go into the pack's buffers (the framebuffer Iris builds for
 * {@code dh_terrain}) with a depth texture of their own, in a perspective of their own ({@link PackLod#projection}),
 * before the chunks, which then cover them wherever they stand; the pack reads that depth as {@code dhDepthTex} to know
 * where the far terrain is. Iris's own Distant Horizons support builds all of this only with Distant Horizons installed;
 * mixins on its facade ({@code DHCompatMixin}) hand Iris this instead.
 *
 * <p>Depth is reversed, as everywhere in 26.3: Iris keeps the game's reversed depth and patches the pack's programs to
 * write it ({@code gl_Position.z} becomes {@code (w - z) / 2}) and to read every depth texture, dhDepthTex included, as
 * one minus what is stored. So the LOD's depth clears to 0 (the pack reads 1: no far terrain) and the nearer surface
 * is the greater depth.
 */
public final class PackLodRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger("VOID LOD");
	/** The reversed depth of nothing: past the far plane. */
	private static final float[] NO_DEPTH = {0.0F};

	private final PackLodProgram solid;
	private final GlFramebuffer terrain;
	/** The pack's dh_water program and its framebuffer, or null without one (the water then goes with the land). */
	private final PackLodProgram water;
	private final GlFramebuffer waterTarget;
	private DepthTexture depth;
	/** The LOD's depth before its water: the pack's dhDepthTex1, what it sees through the water. */
	private DepthTexture depthUnderWater;
	private int width = -1;
	private int height = -1;
	private int vao;
	/** The GPU time of the LOD's draws through the pack, for the F3 line and auto-tune. */
	private final GlTimer timer = new GlTimer();

	private PackLodRenderer(IrisRenderingPipeline pipeline, ProgramSource source) {
		this.solid = PackLodProgram.create(source.getName(), false, false, source, pipeline);
		this.terrain = pipeline.createDHFramebuffer(source, false);
		Optional<ProgramSource> water = pipeline.getDHWaterShader();
		this.water = water.isPresent() ? PackLodProgram.create(water.get().getName(), false, true, water.get(), pipeline) : null;
		this.waterTarget = water.isPresent() ? pipeline.createDHFramebuffer(water.get(), true) : null;
		this.resize();
	}

	/** The LOD's renderer for {@code pipeline}, or null if its pack has no Distant Horizons programs (or they fail). */
	public static PackLodRenderer create(IrisRenderingPipeline pipeline) {
		Optional<ProgramSource> source = pipeline.getDHTerrainShader();
		if (source.isEmpty()) {
			LOGGER.info("VOID LOD: this shader pack has no Distant Horizons programs; the LOD draws over its picture instead");
			return null;
		}
		try {
			PackLodRenderer renderer = new PackLodRenderer(pipeline, source.get());
			LOGGER.info("VOID LOD: drawing through the shader pack's Distant Horizons programs");
			return renderer;
		} catch (RuntimeException e) {
			LOGGER.warn("VOID LOD: could not set up the shader pack's Distant Horizons programs; the LOD draws over its picture instead", e);
			return null;
		}
	}

	/** The LOD's depth, for the pack's dhDepthTex: reversed, 0 where there is no far terrain. */
	public int depthTexture() {
		return this.depth == null ? 0 : this.depth.getTextureId();
	}

	/** The LOD's depth under its water, for the pack's dhDepthTex1. */
	public int depthTextureUnderWater() {
		if (this.water == null) {
			return this.depthTexture();
		}
		return this.depthUnderWater == null ? 0 : this.depthUnderWater.getTextureId();
	}

	private void resize() {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		if (main.width == this.width && main.height == this.height) {
			return;
		}
		if (this.depth != null) {
			this.depth.destroy();
			this.depthUnderWater.destroy();
		}
		this.width = main.width;
		this.height = main.height;
		this.depth = new DepthTexture("VOID LOD depth", this.width, this.height, DepthBufferFormat.DEPTH32F);
		this.depthUnderWater = new DepthTexture("VOID LOD depth under water", this.width, this.height, DepthBufferFormat.DEPTH32F);
		this.terrain.addDepthAttachmentBypass(this.depth.getTextureId());
		if (this.waterTarget != null) {
			this.waterTarget.addDepthAttachmentBypass(this.depth.getTextureId());
		}
	}

	/**
	 * Clears the LOD's depth and draws {@code frame}'s land through the pack's dh_terrain program (null: just clear, so
	 * the pack sees no far terrain). Runs on the render thread outside any of the game's render passes, before the
	 * chunks: they cover the LOD wherever they stand.
	 */
	public void drawOpaque(PackFrame frame) {
		this.timer.frame();
		this.resize();
		this.terrain.bind();
		GlStateManager._viewport(0, 0, this.width, this.height);
		noScissor();
		GlStateManager._depthMask(true);
		GL32C.glClearBufferfv(GL32C.GL_DEPTH, 0, NO_DEPTH);
		if (frame == null || frame.count == 0) {
			return;
		}
		GlStateManager._enableDepthTest();
		GlStateManager._depthFunc(GL32C.GL_GEQUAL);
		for (int i = 0; i < 8; i++) {
			GlStateManager._disableBlend(i);
		}
		GlStateManager._enableCull();

		Matrix4f projection = PackLod.projection();
		Matrix4f modelView = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
		this.timer.begin();
		this.solid.bind();
		this.solid.fill(projection, modelView, frame.clip, -PackLod.Y_OFFSET);
		if (this.vao == 0) {
			this.vao = GL32C.glGenVertexArrays();
		}
		GL32C.glBindVertexArray(this.vao);
		GL32C.glBindBuffer(GL32C.GL_ELEMENT_ARRAY_BUFFER, frame.indexBuffer);
		// without a water program the pack's terrain program takes the water too, in one range with the land
		this.ranges(this.solid, frame, false, this.water == null);
		GL32C.glBindVertexArray(0);
		this.solid.unbind();
		this.timer.end();
	}

	/**
	 * Draws {@code frame}'s water through the pack's dh_water program, after the world's translucent pass and before the
	 * pack's composite passes, as Distant Horizons draws its own: first keeping the depth under it for dhDepthTex1, then
	 * blended the way the pack asks, writing its surface into the LOD's depth.
	 */
	public void drawWater(PackFrame frame) {
		if (this.water == null || this.depth == null) {
			return;
		}
		DepthCopyStrategy.fastest(false).copy(this.terrain, this.depth.getTextureId(), null, this.depthUnderWater.getTextureId(), this.width,
			this.height);
		if (frame == null || frame.count == 0) {
			return;
		}
		this.waterTarget.bind();
		GlStateManager._viewport(0, 0, this.width, this.height);
		noScissor();
		GlStateManager._enableDepthTest();
		GlStateManager._depthFunc(GL32C.GL_GEQUAL);
		GlStateManager._depthMask(true);
		// water is seen from below as well, from the shore of a lake higher up
		GlStateManager._disableCull();
		Matrix4f projection = PackLod.projection();
		Matrix4f modelView = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
		this.timer.begin();
		this.water.bind();
		this.water.fill(projection, modelView, frame.clip, -PackLod.Y_OFFSET);
		if (this.vao == 0) {
			this.vao = GL32C.glGenVertexArrays();
		}
		GL32C.glBindVertexArray(this.vao);
		GL32C.glBindBuffer(GL32C.GL_ELEMENT_ARRAY_BUFFER, frame.indexBuffer);
		this.ranges(this.water, frame, true, false);
		GL32C.glBindVertexArray(0);
		this.water.unbind();
		this.timer.end();
		GlStateManager._enableCull();
	}

	/**
	 * Turns the scissor test off, which the game can leave on from its last pass: the LOD's depth is cleared and drawn
	 * over the whole screen. Through the state cache both ways, so the cache and GL agree whatever came before.
	 */
	private static void noScissor() {
		GlStateManager._enableScissorTest();
		GlStateManager._disableScissorTest();
	}

	/** Each tile's land ({@code water} false; with its water too if {@code both}) or its water, through {@code program}. */
	private void ranges(PackLodProgram program, PackFrame frame, boolean water, boolean both) {
		int boundHeap = -1;
		for (int i = 0; i < frame.count; i++) {
			int first = water ? frame.waterFirst[i] : 0;
			int count = water ? frame.waterIndices[i] : frame.landIndices[i] + (both ? frame.waterIndices[i] : 0);
			if (count == 0) {
				continue;
			}
			if (frame.heap[i] != boundHeap) {
				boundHeap = frame.heap[i];
				this.layout(boundHeap);
			}
			program.modelOffset(frame.offset[i * 3], frame.offset[i * 3 + 1], frame.offset[i * 3 + 2]);
			GL32C.glDrawElementsBaseVertex(GL32C.GL_TRIANGLES, count, frame.indexType, (long) first * frame.indexBytes, frame.baseVertex[i]);
		}
	}

	/** Points the Distant Horizons attributes at {@code buffer}: 16 bytes a vertex (PackLod's layout). */
	private void layout(int buffer) {
		GL32C.glBindBuffer(GL32C.GL_ARRAY_BUFFER, buffer);
		GL32C.glEnableVertexAttribArray(0);
		GL32C.glVertexAttribIPointer(0, 4, GL32C.GL_UNSIGNED_SHORT, 16, 0L);
		GL32C.glEnableVertexAttribArray(1);
		GL32C.glVertexAttribPointer(1, 4, GL32C.GL_UNSIGNED_BYTE, true, 16, 8L);
		GL32C.glEnableVertexAttribArray(2);
		GL32C.glVertexAttribIPointer(2, 4, GL32C.GL_UNSIGNED_BYTE, 16, 12L);
	}

	/** The GPU time a frame of the LOD's draws through the pack (its programs' own cost), in ms; -1 until measured. */
	public double gpuMillis() {
		return this.timer.millis;
	}

	/**
	 * Frees what the LOD made for the pack. Not the framebuffers: Iris's render targets own those, and have freed them
	 * already when the pipeline is torn down (a pack reload, F8).
	 */
	public void destroy() {
		this.timer.free();
		this.solid.free();
		if (this.water != null) {
			this.water.free();
		}
		if (this.depth != null) {
			this.depth.destroy();
			this.depthUnderWater.destroy();
		}
		if (this.vao != 0) {
			GL32C.glDeleteVertexArrays(this.vao);
		}
	}
}
