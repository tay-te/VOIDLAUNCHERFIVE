package dev.voidmc.fray.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * What Fray needs from Iris (1.11.6+26.3), reached only when Iris is loaded: whether the shadow pass is drawing (for the
 * frame-cost probe; the pose itself needs nothing, the shadow pass re-extracts and re-poses from the render state) and
 * Iris's hand-pass rule. Iris draws first-person hands twice (solid, then translucent) through vanilla's
 * {@code submitHandsWithItems}, skipping in each pass the hands whose item belongs to the other
 * ({@code MixinItemInHandRenderer.iris$skipTranslucentHands}); Fray's first-person body follows the same rule so it is
 * drawn exactly once, in Iris's hand pass.
 */
public final class IrisCompat {
	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

	private IrisCompat() {
	}

	public static boolean loaded() {
		return IRIS;
	}

	public static boolean inShadowPass() {
		return IRIS && Iris.shadow();
	}

	public static boolean packInUse() {
		return IRIS && Iris.pack();
	}

	/** True when Iris is drawing the hand pass that doesn't own a hand holding {@code item} (so draw nothing now). */
	public static boolean skipHand(ItemStack item) {
		return IRIS && Iris.skipHand(item);
	}

	/** Holds the Iris references, so this class loads without Iris. */
	private static final class Iris {
		static boolean shadow() {
			return net.irisshaders.iris.api.v0.IrisApi.getInstance().isRenderingShadowPass();
		}

		static boolean pack() {
			return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
		}

		static boolean skipHand(ItemStack item) {
			if (!net.irisshaders.iris.Iris.isPackInUseQuick()) {
				return false;
			}
			net.irisshaders.iris.pathways.HandRenderer hands = net.irisshaders.iris.pathways.HandRenderer.INSTANCE;
			return hands.isRenderingSolid() == hands.isHandTranslucent(item);
		}
	}
}
