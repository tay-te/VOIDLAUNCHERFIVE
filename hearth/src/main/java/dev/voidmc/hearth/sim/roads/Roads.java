package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.api.HearthConfig;

/**
 * The roads between settlements: network, routes, traffic and bandits (§1).
 * Lane A1 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Roads {
	private static RoadsConfig config = RoadsConfig.DEFAULTS;

	private Roads() {
	}

	public static RoadsConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(RoadsConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code SimModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("roads", RoadsConfig.CODEC, RoadsConfig.DEFAULTS);
		// lane A1 registers its services, behaviours, events and data keys here
	}
}
