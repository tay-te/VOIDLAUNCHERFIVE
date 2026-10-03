package dev.voidmc.lod.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * What Iris is doing this frame, without linking Iris when it isn't installed: while a shader pack runs the
 * frame the LOD draws after the pack has finished, on top of its picture, instead of inside vanilla's pass.
 */
public final class Shaders {
	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
	/** {@code -Dvoid.lod.overPack=false} draws inside vanilla's pass, into the pack's buffers, to compare. */
	private static final boolean OVER_PACK = !"false".equals(System.getProperty("void.lod.overPack"));

	private Shaders() {
	}

	/** Whether a shader pack renders this frame: Iris installed and a pack switched on. */
	public static boolean packInUse() {
		return IRIS && Iris.packInUse();
	}

	/** Whether the LOD draws over the pack's finished picture this frame rather than in vanilla's pass. */
	public static boolean drawOverPack() {
		return OVER_PACK && packInUse();
	}

	/** Iris's API in a class of its own, so the JVM only resolves it once Iris is known to be there. */
	private static final class Iris {
		static boolean packInUse() {
			return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
		}
	}
}
