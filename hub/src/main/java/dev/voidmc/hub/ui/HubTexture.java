package dev.voidmc.hub.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * The texture the Ultralight view is uploaded into. A plain RGBA8 renderpearl texture, so it works
 * on the OpenGL and the Vulkan backend alike: nothing here touches a graphics API directly.
 *
 * <p>Pixels are premultiplied (Ultralight paints premultiplied BGRA; the binding swaps to RGBA on
 * the way out), so it is drawn with {@code RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA}.
 * Sampled NEAREST and clamped: it is drawn 1:1 onto the framebuffer at every GUI scale (under a layout scale of 1 the
 * page zooms itself, {@code HubView.pageZoom}).
 */
public final class HubTexture extends AbstractTexture {

	private final int width;
	private final int height;

	public HubTexture(int width, int height) {
		this.width = width;
		this.height = height;
		GpuDevice device = RenderSystem.getDevice();
		this.texture = device.createTexture("VOID Hub view", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		this.textureView = device.createTextureView(this.texture);
		this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	/** Uploads a tightly packed RGBA8 rectangle (position 0 .. limit of {@code pixels}). */
	public void upload(ByteBuffer pixels, int x, int y, int w, int h) {
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.getTexture(), pixels, 0, 0, x, y, w, h);
	}
}
