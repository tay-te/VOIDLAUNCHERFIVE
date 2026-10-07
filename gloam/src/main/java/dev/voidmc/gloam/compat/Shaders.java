package dev.voidmc.gloam.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether a shader pack is drawing this frame, without linking Iris when it isn't installed. A pack replaces the sky,
 * the fog and the lighting with its own, so Gloam steers those through what packs read (the environment attributes,
 * the rain level, the particles) and leaves the rest to the pack.
 */
public final class Shaders {
	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

	private Shaders() {
	}

	/** Whether a shader pack renders this frame: Iris installed and a pack switched on. */
	public static boolean packInUse() {
		return IRIS && Iris.packInUse();
	}

	/** Iris's side in a class of its own, so the JVM only resolves it once Iris is known to be there. */
	private static final class Iris {
		static boolean packInUse() {
			return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
		}
	}
}
