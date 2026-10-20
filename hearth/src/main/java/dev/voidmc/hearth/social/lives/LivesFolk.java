package dev.voidmc.hearth.social.lives;

/**
 * The behaviours of people with lives: courting walks, personal schedules, feuds acted out (§4); settings in {@link dev.voidmc.hearth.sim.lives.Lives#config()}.
 * Lane B2 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class LivesFolk {
	private LivesFolk() {
	}

	/** Called once from {@code SocialModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		// lane B2 registers its services, behaviours, events and data keys here
	}
}
