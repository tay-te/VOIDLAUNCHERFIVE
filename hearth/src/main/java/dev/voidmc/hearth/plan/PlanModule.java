package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.terrain.Ground;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.jspecify.annotations.Nullable;

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

	/**
	 * Dev worlds: {@code -Dhearth.dev.plan.layout=walled} (any {@link LayoutType} name) forces every settlement founded in
	 * this run to that layout, on whichever thread founds it (live founding, histories on the atlas workers). Null when
	 * unset, as in every normal run.
	 */
	public static @Nullable LayoutType devLayout() {
		return DEV_LAYOUT;
	}

	private static final @Nullable LayoutType DEV_LAYOUT = devLayoutProperty();

	private static @Nullable LayoutType devLayoutProperty() {
		String name = System.getProperty("hearth.dev.plan.layout");
		if (name == null || name.isBlank()) {
			return null;
		}
		for (LayoutType t : LayoutType.values()) {
			if (t.getSerializedName().equals(name.trim())) {
				Hearth.LOG.warn("hearth: every settlement founded in this run is laid out as '{}' (hearth.dev.plan.layout)", name);
				return t;
			}
		}
		Hearth.LOG.warn("hearth: unknown layout '{}' in hearth.dev.plan.layout; ignored", name);
		return null;
	}

	public static HearthPlanner planner() {
		return planner;
	}
}
