package dev.voidmc.gloam.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Whether VOID LOD is here, so its classes are only touched when they can load. */
public final class Lod {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("void_lod");

	private Lod() {
	}

	/** Whether VOID LOD is loaded and drawing far terrain in this level. */
	public static boolean active() {
		return LOADED && Api.active();
	}

	/** How far VOID LOD draws the far land, in blocks (0 when it is not loaded or not drawing). */
	public static double radius() {
		return LOADED ? Api.radius() : 0;
	}

	/** Sets how many times as thick as its own VOID LOD's haze is (1: its own); nothing without VOID LOD. */
	public static void setHazeThickness(float thickness) {
		if (LOADED) {
			Api.setHazeThickness(thickness);
		}
	}

	/**
	 * Lays a picture of sunlight over VOID LOD's far land (cloud shadows; see {@code LodApi.setShade}), or takes it off
	 * with a null texture; nothing without VOID LOD.
	 */
	public static void setShade(com.mojang.renderpearl.api.textures.GpuTextureView texture, double originX, double originZ, double size, float strength) {
		if (LOADED) {
			Api.setShade(texture, originX, originZ, size, strength);
		}
	}

	/** The far land's depth as a shader pack drew it (VOID LOD's {@code LodApi.FarDepth}), in Gloam's own terms. */
	public record PackDepth(int glTexture, org.joml.Matrix4f projection, int width, int height) {
	}

	/** Calls {@code listener} each frame a shader pack has finished with VOID LOD's far land in it; nothing without VOID LOD. */
	public static void onPackFrame(java.util.function.Consumer<PackDepth> listener) {
		if (LOADED) {
			Api.onPackFrame(listener);
		}
	}

	private static final class Api {
		static boolean active() {
			return dev.voidmc.lod.api.LodApi.active();
		}

		static double radius() {
			return dev.voidmc.lod.api.LodApi.radius();
		}

		static void setShade(com.mojang.renderpearl.api.textures.GpuTextureView texture, double originX, double originZ, double size, float strength) {
			dev.voidmc.lod.api.LodApi.setShade(texture == null ? null : new dev.voidmc.lod.api.LodApi.Shade(texture, originX, originZ, size, strength));
		}

		static void onPackFrame(java.util.function.Consumer<PackDepth> listener) {
			dev.voidmc.lod.api.LodApi.onPackFrame(d -> listener.accept(new PackDepth(d.glTexture(), d.projection(), d.width(), d.height())));
		}

		static void setHazeThickness(float thickness) {
			dev.voidmc.lod.api.LodApi.setHazeThickness(thickness);
		}
	}
}
