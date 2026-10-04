package dev.voidmc.lod.compat;

import dev.voidmc.lod.render.PackFrame;
import net.fabricmc.loader.api.FabricLoader;

/**
 * What Iris is doing this frame, without linking Iris when it isn't installed. With a shader pack that carries Distant
 * Horizons programs, the pack itself draws the LOD ({@code compat.iris.PackLodRenderer}); with any other pack the LOD
 * draws after the pack has finished, on top of its picture, instead of inside vanilla's pass.
 */
public final class Shaders {
	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
	/** With Distant Horizons installed, Iris's own support for it takes the pack's programs: the LOD stays out of them. */
	private static final boolean DISTANT_HORIZONS = FabricLoader.getInstance().isModLoaded("distanthorizons");
	/** {@code -Dvoid.lod.overPack=false} draws inside vanilla's pass, into the pack's buffers, to compare. */
	private static final boolean OVER_PACK = !"false".equals(System.getProperty("void.lod.overPack"));

	private Shaders() {
	}

	/** Whether a shader pack renders this frame: Iris installed and a pack switched on. */
	public static boolean packInUse() {
		return IRIS && Iris.packInUse();
	}

	/** Whether the pack in use draws the LOD through its own Distant Horizons programs. */
	public static boolean packDraws() {
		return IRIS && !DISTANT_HORIZONS && Iris.packDraws();
	}

	/** Whether the LOD draws over the pack's finished picture this frame rather than in vanilla's pass. */
	public static boolean drawOverPack() {
		return OVER_PACK && packInUse() && !packDraws();
	}

	/** Has the pack draw {@code frame}'s far terrain (null: only clear its depth, so the pack sees none). */
	public static void drawPack(PackFrame frame) {
		if (IRIS && !DISTANT_HORIZONS) {
			Iris.drawPack(frame);
		}
	}

	/** Iris's side in a class of its own, so the JVM only resolves it once Iris is known to be there. */
	private static final class Iris {
		static boolean packInUse() {
			return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
		}

		static boolean packDraws() {
			return dev.voidmc.lod.compat.iris.PackLod.renderer() != null;
		}

		static void drawPack(PackFrame frame) {
			dev.voidmc.lod.compat.iris.PackLod.drawOpaque(frame);
		}
	}
}
