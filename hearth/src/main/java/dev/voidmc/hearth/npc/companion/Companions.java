package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.api.HearthConfig;

/**
 * The player's place: companions (§8).
 * Lane B4 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Companions {
	private static PlaceConfig config = PlaceConfig.DEFAULTS;

	private Companions() {
	}

	public static PlaceConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(PlaceConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code NpcModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("place", PlaceConfig.CODEC, PlaceConfig.DEFAULTS);
		// lane B4 registers its services, behaviours, events and data keys here
	}
}
