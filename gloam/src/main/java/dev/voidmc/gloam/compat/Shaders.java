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

	/** The name of the shader pack Iris has loaded (its file or folder name), or null without Iris or a pack. */
	public static String packName() {
		return IRIS ? Iris.packName() : null;
	}

	/**
	 * Runs {@code draw} with Iris's shader overrides held off: for Gloam's own pipelines drawn over a pack's finished
	 * picture, which must keep their own shaders (and which Iris would otherwise report missing from its override list).
	 */
	public static void withoutPackShaders(Runnable draw) {
		if (!IRIS) {
			draw.run();
			return;
		}
		Iris.withoutOverrides(draw);
	}

	/** Iris's side in a class of its own, so the JVM only resolves it once Iris is known to be there. */
	private static final class Iris {
		static boolean packInUse() {
			return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
		}

		static String packName() {
			try {
				return net.irisshaders.iris.Iris.getCurrentPackName();
			} catch (LinkageError e) {
				return null;
			}
		}

		static void withoutOverrides(Runnable draw) {
			boolean was = net.irisshaders.iris.vertices.ImmediateState.bypass;
			net.irisshaders.iris.vertices.ImmediateState.bypass = true;
			try {
				draw.run();
			} finally {
				net.irisshaders.iris.vertices.ImmediateState.bypass = was;
			}
		}
	}
}
