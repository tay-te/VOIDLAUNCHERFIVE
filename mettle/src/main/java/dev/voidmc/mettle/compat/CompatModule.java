package dev.voidmc.mettle.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The partners (INTEGRATION.md). Every one is optional: each lives in its own package, compiled against a copy of its api
 * and reached only from here, after Fabric says it is loaded, so its classes are never touched without it.
 */
public final class CompatModule {
	public static final String HEARTH = "hearth";

	private CompatModule() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded(HEARTH)) {
			dev.voidmc.mettle.compat.hearth.HearthCompat.init();
		}
	}
}
