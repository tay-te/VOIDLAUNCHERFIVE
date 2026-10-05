package dev.voidmc.mettle.client.heavy;

import dev.voidmc.mettle.character.skill.heavy.ThrownAxe;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** The client half of the Heavy Weapons tree: how a thrown axe is drawn. Called once from {@code MettleClient}. */
public final class HeavyWeaponsClient {
	private static boolean initialised;

	private HeavyWeaponsClient() {
	}

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		EntityRendererRegistry.register(ThrownAxe.TYPE, ThrownAxeRenderer::new);
	}

	/** Whether {@link #init} has run (the client tour wires it itself when the coordinator hasn't yet). */
	public static boolean initialised() {
		return initialised;
	}
}
