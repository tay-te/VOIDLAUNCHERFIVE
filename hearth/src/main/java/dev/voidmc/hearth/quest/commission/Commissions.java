package dev.voidmc.hearth.quest.commission;

/**
 * The player's place: a lord's commissions and retinue ranks (§8).
 * Lane B4 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Commissions {
	private Commissions() {
	}

	/** Called once from {@code QuestModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		// lane B4 registers its services, behaviours, events and data keys here
	}
}
