package dev.voidmc.atlas.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.voidmc.atlas.client.ui.MeshBuilder;
import dev.voidmc.atlas.client.ui.Ui;
import net.minecraft.client.gui.render.TextureSetup;

/**
 * The picture of the world behind the open map. On the first frame the map is open the world is drawn as usual and
 * copied (GPU to GPU) into a texture; from then on the world is not drawn at all ({@code GameRendererMixin}) and this
 * picture stands in for it, dimmed. Off when the "live backdrop" setting is on.
 */
public final class Backdrop {
	private static boolean wanted;
	private static boolean captured;
	private static GpuTexture texture;
	private static GpuTextureView view;

	private Backdrop() {
	}

	/** The map opened: capture the next frame. */
	public static void open(boolean freeze) {
		wanted = freeze;
		captured = false;
	}

	public static void close() {
		wanted = false;
		captured = false;
	}

	/** Whether the world can be skipped this frame: a picture of it is ready and still the window's size. */
	public static boolean skipWorld() {
		if (!wanted || !captured || texture == null || texture.isClosed()) {
			return false;
		}
		var window = net.minecraft.client.Minecraft.getInstance().getWindow();
		if (texture.getWidth(0) != window.getWidth() || texture.getHeight(0) != window.getHeight()) {
			// resized: draw the world once more and take a new picture
			captured = false;
			return false;
		}
		return true;
	}

	public static boolean ready() {
		return wanted && captured && texture != null && !texture.isClosed();
	}

	public static void captureIfWanted(RenderTarget target) {
		if (!wanted || captured) {
			return;
		}
		GpuTexture source = target.getColorTexture();
		if (source == null) {
			return;
		}
		int w = source.getWidth(0);
		int h = source.getHeight(0);
		if (texture == null || texture.isClosed() || texture.getWidth(0) != w || texture.getHeight(0) != h || texture.getFormat() != source.getFormat()) {
			if (view != null) {
				view.close();
			}
			if (texture != null) {
				texture.close();
			}
			var device = RenderSystem.getDevice();
			texture = device.createTexture(() -> "Atlas backdrop", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, source.getFormat(), w, h, 1, 1);
			view = device.createTextureView(texture);
		}
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, w, h);
		captured = true;
	}

	/** Draws the picture over the whole screen (design pixels {@code w x h}), if there is one. */
	public static boolean draw(Ui ui, float w, float h) {
		if (!ready()) {
			return false;
		}
		MeshBuilder mesh = new MeshBuilder(true);
		// a render target is stored bottom-up: flip v, as the game's own picture-in-picture blits do
		mesh.vertex(0, 0, 0, 1, 0xFFFFFFFF);
		mesh.vertex(0, h, 0, 0, 0xFFFFFFFF);
		mesh.vertex(w, h, 1, 0, 0xFFFFFFFF);
		mesh.vertex(w, 0, 1, 1, 0xFFFFFFFF);
		ui.submit(TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)), mesh);
		return true;
	}
}
