package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.terrain.Ground;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * The plan module: settlement layouts, roads, plots and growth decisions (ARCHITECTURE.md §5–7, docs/PLANNING.md).
 * Registers the planner service, its per-settlement state key {@code hearth:plan/state}, its config
 * ({@code config/hearth/plan.json}) and the {@code /hearth plan} debug commands.
 */
public final class PlanModule {
	/** The planner's per-settlement state. Registered when this class loads (mod initialisation). */
	public static final HearthDataKey<PlanState> STATE = HearthDataKey.register(Hearth.id("plan/state"), HearthDataKey.Scope.SETTLEMENT,
		PlanState.CODEC, PlanState::new);

	private static volatile HearthPlanner planner;

	private PlanModule() {
	}

	public static void init() {
		PlanConfig config = HearthConfig.load("plan", PlanConfig.CODEC, PlanConfig.DEFAULTS);
		Ground.setStoreCapacity(config.cacheTiles());
		planner = new HearthPlanner(STATE, config);
		HearthServices.setPlanner(planner);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> PlanCommands.register(dispatcher));
	}

	public static HearthPlanner planner() {
		return planner;
	}
}
