package dev.voidmc.hearth.client.npc.travel;

/**
 * Client side of the travellers: the cart model and renderer (§1).
 * Lane A1 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class TravelClient {
	private TravelClient() {
	}

	/** Called once from {@code NpcClient.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		// lane A1 registers its services, behaviours, events and data keys here
	}
}
