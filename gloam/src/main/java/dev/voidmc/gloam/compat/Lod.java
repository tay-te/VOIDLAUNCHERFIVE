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

	private static final class Api {
		static boolean active() {
			return dev.voidmc.lod.api.LodApi.active();
		}
	}
}
