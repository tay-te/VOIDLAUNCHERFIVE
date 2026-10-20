package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.api.HearthConfig;

/**
 * A visible daily rhythm: services, wash day, play, tavern nights, watch rounds and lights, the calendar (§2).
 * Lane A2 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Rhythm {
	private static RhythmConfig config = RhythmConfig.DEFAULTS;

	private Rhythm() {
	}

	public static RhythmConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(RhythmConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code SocialModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("rhythm", RhythmConfig.CODEC, RhythmConfig.DEFAULTS);
		// lane A2 registers its services, behaviours, events and data keys here
	}
}
