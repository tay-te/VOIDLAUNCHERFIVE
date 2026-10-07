package dev.voidmc.gloam.far;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
import com.mojang.renderpearl.backend.opengl.GlTexture;

/**
 * Another renderer's OpenGL depth texture seen through Mojang's GPU layer, so it can be sampled in a render pass like
 * any texture: the far land's depth a shader pack drew through VOID LOD ({@code LodApi.FarDepth}). Borrowed, never
 * owned: it is never closed, so the GL texture is never deleted here (a closed GlTexture deletes its GL name once its
 * last view goes). Only on the OpenGL backend, which is the only one shader packs run on.
 */
final class BorrowedDepth extends GlTexture {
	BorrowedDepth(int glTexture, int width, int height) {
		super(GpuTexture.USAGE_TEXTURE_BINDING, "VOID LOD far depth", GpuFormat.D32_FLOAT, width, height, 1, 1, glTexture, new FrameBufferCache());
	}

	@Override
	public void close() {
		// not ours to delete
	}
}
