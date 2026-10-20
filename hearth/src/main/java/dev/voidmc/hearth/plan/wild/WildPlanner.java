package dev.voidmc.hearth.plan.wild;

/**
 * The planner of posts in the wild: each type's fixed programme and layout (§7); settings in {@link dev.voidmc.hearth.sim.wild.Wild#config()}.
 * Lane B3 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class WildPlanner {
	private WildPlanner() {
	}

	/** Called once from {@code PlanModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		// lane B3 registers its services, behaviours, events and data keys here
	}
}
