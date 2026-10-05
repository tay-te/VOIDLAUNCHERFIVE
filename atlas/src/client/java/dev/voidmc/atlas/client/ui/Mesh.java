package dev.voidmc.atlas.client.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/**
 * A batch of quads for the GUI renderer: Atlas's own shapes (anti-aliased rounded rectangles, rings, glyph cells) and
 * map tiles clipped to a rounded viewport. One mesh is one GUI element, so a whole layer of chrome or every tile on an
 * atlas page is one batch; it only ever uses the stock GUI pipelines, so it draws the same on OpenGL and Vulkan.
 */
public final class Mesh implements GuiElementRenderState {
	private final RenderPipeline pipeline;
	private final TextureSetup textures;
	private final Matrix3x2f pose;
	private final float[] xy;
	private final float @Nullable [] uv;
	private final int[] colours;
	private final int vertices;
	private final @Nullable ScreenRectangle scissor;
	private final @Nullable ScreenRectangle bounds;

	public Mesh(RenderPipeline pipeline, TextureSetup textures, Matrix3x2f pose, float[] xy, float @Nullable [] uv, int[] colours, int vertices,
		@Nullable ScreenRectangle scissor) {
		this.pipeline = pipeline;
		this.textures = textures;
		this.pose = pose;
		this.xy = xy;
		this.uv = uv;
		this.colours = colours;
		this.vertices = vertices;
		this.scissor = scissor;
		this.bounds = bounds(pose, xy, vertices, scissor);
	}

	private static @Nullable ScreenRectangle bounds(Matrix3x2f pose, float[] xy, int n, @Nullable ScreenRectangle scissor) {
		if (n == 0) {
			return null;
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		Vector2f v = new Vector2f();
		for (int i = 0; i < n; i++) {
			pose.transformPosition(xy[i * 2], xy[i * 2 + 1], v);
			minX = Math.min(minX, v.x);
			minY = Math.min(minY, v.y);
			maxX = Math.max(maxX, v.x);
			maxY = Math.max(maxY, v.y);
		}
		int x0 = (int) Math.floor(minX);
		int y0 = (int) Math.floor(minY);
		ScreenRectangle r = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0));
		return scissor != null ? scissor.intersection(r) : r;
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		for (int i = 0; i < this.vertices; i++) {
			VertexConsumer v = consumer.addVertexWith2DPose(this.pose, this.xy[i * 2], this.xy[i * 2 + 1]);
			if (this.uv != null) {
				v.setUv(this.uv[i * 2], this.uv[i * 2 + 1]);
			}
			v.setColor(this.colours[i]);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return this.pipeline;
	}

	@Override
	public TextureSetup textureSetup() {
		return this.textures;
	}

	@Override
	public @Nullable ScreenRectangle scissorArea() {
		return this.scissor;
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		return this.bounds;
	}

	public int vertexCount() {
		return this.vertices;
	}
}
