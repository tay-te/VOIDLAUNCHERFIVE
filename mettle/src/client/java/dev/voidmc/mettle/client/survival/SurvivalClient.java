package dev.voidmc.mettle.client.survival;

/**
 * Survival on the client (called from MettleClient). Nothing to register: every personal cue the tree has is sent by the
 * server to its one player through vanilla packets the client already handles, so no other player can see or hear it
 * and no client code is needed: Tracker's prints are dust particles addressed to that player, Lay of the Land's marker
 * is a locator-bar waypoint, and Sixth Sense's heartbeat and Weatherwise's rumble are positioned sounds. Pathfinder's
 * berry-bush mixin runs on both sides from the common mixin config, using the perks synced to the owning client.
 */
public final class SurvivalClient {
	private SurvivalClient() {
	}

	public static void init() {
	}
}
