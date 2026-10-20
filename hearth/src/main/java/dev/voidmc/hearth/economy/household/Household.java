package dev.voidmc.hearth.economy.household;

/**
 * The player's place: a household, servants, wages and larder (§8); settings in {@link dev.voidmc.hearth.npc.companion.Companions#config()}.
 * Lane B4 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Household {
	private Household() {
	}

	/** Called once from {@code EconomyModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		// lane B4 registers its services, behaviours, events and data keys here
	}
}
