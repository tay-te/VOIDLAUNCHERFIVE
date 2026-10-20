package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.HearthConfig;

/**
 * Town events you stumble into: the event book, scenes, tax, raids, fairs, trials (§3).
 * Lane B1 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class TownEvents {
	private static EventsConfig config = EventsConfig.DEFAULTS;

	private TownEvents() {
	}

	public static EventsConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(EventsConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code SocialModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("events", EventsConfig.CODEC, EventsConfig.DEFAULTS);
		// lane B1 registers its services, behaviours, events and data keys here
	}
}
