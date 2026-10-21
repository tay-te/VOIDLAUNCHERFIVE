package dev.voidmc.hearth.plan.wild;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The planner of posts in the wild: each type's fixed programme and layout (§7); settings in {@link dev.voidmc.hearth.sim.wild.Wild#config()}.
 * Lane B3 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 *
 * <p>{@code HearthPlanner} hands every settlement of kind {@code POST} to {@link PostPlanner}; {@link #INSTANCE} is the
 * same as a planner of its own, for a post's history worked out off the server thread (which knows it is a post).
 */
public final class WildPlanner {
	/** A planner for posts alone. */
	public static final SettlementPlanner INSTANCE = new SettlementPlanner() {
		@Override
		public List<PlotSpec> found(FoundingContext ctx) {
			return PostPlanner.found(ctx);
		}

		@Override
		public @Nullable GrowthProject next(Settlement settlement, TerrainProbe terrain, RandomSource random) {
			return PostPlanner.next(settlement, terrain, random);
		}

		@Override
		public List<BlockPos> exits(Settlement settlement) {
			return PostPlanner.exits(settlement);
		}
	};

	private WildPlanner() {
	}

	/** Called once from {@code PlanModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		// nothing to register: HearthPlanner dispatches posts here, the archetypes register with the build module
	}
}
