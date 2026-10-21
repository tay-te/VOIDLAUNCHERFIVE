package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.HearthConfig;

/**
 * People with lives: relations, apprentices and masters, named characters, threads (§4).
 * Lane B2 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Lives {
	private static LivesConfig config = LivesConfig.DEFAULTS;

	private Lives() {
	}

	public static LivesConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(LivesConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code SimModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("lives", LivesConfig.CODEC, LivesConfig.DEFAULTS);
		// lane B2 registers its services, behaviours, events and data keys here
	}
}
