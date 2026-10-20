package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.HearthConfig;

/**
 * Crowds that react: stimuli, reactions, shutters, brawls, barks (§5).
 * Lane A3 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Crowds {
	private static CrowdConfig config = CrowdConfig.DEFAULTS;

	private Crowds() {
	}

	public static CrowdConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(CrowdConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code NpcModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("crowds", CrowdConfig.CODEC, CrowdConfig.DEFAULTS);
		// lane A3 registers its services, behaviours, events and data keys here
	}
}
