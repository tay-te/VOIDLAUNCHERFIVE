package dev.voidmc.fray.client.combat;

/**
 * What other mods' HUDs read from Fray's (client side, reflection-friendly static methods): Hearth folds its quest
 * tracker to one line during a boss fight; Atlas and Mettle can check whether Fray's player cluster is up.
 *
 * @since 0.1.0 (2026-10-21)
 */
public final class FrayHudState {
	private FrayHudState() {
	}

	/** True while the local player takes part in a boss encounter (Fray's boss bar is up). */
	public static boolean bossFight() {
		return ClientCombat.bar != null;
	}

	/** True while Fray draws the player cluster (health and stamina) instead of vanilla hearts. */
	public static boolean clusterShown() {
		return dev.voidmc.fray.client.combat.hud.FrayHud.clusterShown;
	}

	/** The cluster's top edge in GUI pixels from the bottom of the screen (to stack above it). */
	public static int clusterTop() {
		return dev.voidmc.fray.client.combat.hud.FrayHud.clusterTop;
	}
}
