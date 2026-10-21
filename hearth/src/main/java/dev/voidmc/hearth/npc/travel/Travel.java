package dev.voidmc.hearth.npc.travel;

/**
 * Travellers on the roads: parties, strangers, bandits and carts (§1); settings in {@link dev.voidmc.hearth.sim.roads.Roads#config()}.
 * Lane A1 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class Travel {
	private Travel() {
	}

	/** Called once from {@code NpcModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		// lane A1 registers its services, behaviours, events and data keys here
	}
}
