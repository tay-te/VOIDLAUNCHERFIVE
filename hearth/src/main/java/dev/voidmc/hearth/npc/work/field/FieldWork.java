package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.HearthConfig;

/**
 * Work you can see: remote work sites, droves, clamps, boats, stall wares (§6).
 * Lane A4 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class FieldWork {
	private static FieldWorkConfig config = FieldWorkConfig.DEFAULTS;

	private FieldWork() {
	}

	public static FieldWorkConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(FieldWorkConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code NpcModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("fieldwork", FieldWorkConfig.CODEC, FieldWorkConfig.DEFAULTS);
		// lane A4 registers its services, behaviours, events and data keys here
	}
}
