package dev.voidmc.hearth.sim.wild;

import dev.voidmc.hearth.api.HearthConfig;

/**
 * Small places in the wild: post sites and post rules (§7).
 * Lane B3 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Wild {
	private static WildConfig config = WildConfig.DEFAULTS;

	private Wild() {
	}

	public static WildConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(WildConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code SimModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("wild", WildConfig.CODEC, WildConfig.DEFAULTS);
		// lane B3 registers its services, behaviours, events and data keys here
	}
}
