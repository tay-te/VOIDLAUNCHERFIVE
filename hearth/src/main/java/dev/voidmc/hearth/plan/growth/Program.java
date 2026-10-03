package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.Site;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What a settlement builds as it grows, beyond homes and food (ARCHITECTURE.md §5.2): its workplaces and stores, the
 * civic buildings of each tier, each with how many a town of a given size wants, how urgently, and where it belongs.
 * Archetypes the build module has not registered fall back or are skipped (see {@code Catalogue.resolve}).
 */
public final class Program {
	/**
	 * @param minPop   the population at which it is first wanted
	 * @param count    how many are wanted at a population
	 * @param priority how urgent, against housing (600+) and food (400+)
	 */
	public record Entry(Identifier id, int minPop, IntUnaryOperator count, double priority, Placement placement,
		District.@Nullable Kind district, Predicate<PlanContext> when) {
	}

	private static final Predicate<PlanContext> ALWAYS = c -> true;
	private static final Predicate<PlanContext> NEAR_WATER = c -> c.state.site != null && c.state.site.hasWater() && c.state.site.waterDistance() < 90;
	private static final Predicate<PlanContext> NEAR_RIVER = c -> c.state.site != null && c.state.site.water() == Site.WaterKind.RIVER
		&& c.state.site.waterDistance() < 90;
	private static final Predicate<PlanContext> NO_RIVER = NEAR_RIVER.negate();

	public static final List<Entry> ENTRIES = List.of(
		new Entry(Archetypes.BUILDERS_YARD, 0, p -> p < 70 ? 1 : p < 150 ? 2 : 3, 1000, Placement.MAIN, District.Kind.CRAFTS, ALWAYS),
		new Entry(Archetypes.STOREHOUSE, 5, p -> 1 + p / 35, 330, Placement.HOME, District.Kind.MARKET, ALWAYS),
		new Entry(Archetypes.FISHERY, 4, p -> 1 + p / 45, 320, Placement.WATER, District.Kind.HARBOUR, NEAR_WATER),
		new Entry(Archetypes.CARPENTRY, 8, p -> 1 + p / 120, 250, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS),
		new Entry(Archetypes.SMITHY, 10, p -> 1 + p / 90, 250, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS),
		new Entry(Archetypes.MARKET_STALL, 12, p -> Math.min(10, 1 + p / 14), 240, Placement.CENTRAL, District.Kind.MARKET, ALWAYS),
		new Entry(Archetypes.SAWMILL, 14, p -> 1 + p / 130, 200, Placement.EDGE, District.Kind.CRAFTS, ALWAYS),
		new Entry(Archetypes.HUNTERS_LODGE, 10, p -> 1, 120, Placement.EDGE, null, ALWAYS),
		new Entry(Archetypes.BARN, 16, p -> 1 + p / 60, 170, Placement.EDGE, District.Kind.FARMS, ALWAYS),
		new Entry(Archetypes.QUARRY, 22, p -> 1 + p / 140, 160, Placement.EDGE, District.Kind.CRAFTS, ALWAYS),
		new Entry(Archetypes.CHAPEL, 20, p -> 1, 290, Placement.RISE, District.Kind.CIVIC, ALWAYS),
		new Entry(Archetypes.INN, 20, p -> p < 100 ? 1 : 2, 270, Placement.MAIN, District.Kind.MARKET, ALWAYS),
		new Entry(Archetypes.BAKERY, 22, p -> 1 + p / 80, 230, Placement.MAIN, District.Kind.MARKET, ALWAYS),
		new Entry(Archetypes.BANK, 30, p -> 1, 260, Placement.CENTRAL, District.Kind.MARKET, ALWAYS),
		new Entry(Archetypes.WATERMILL, 26, p -> 1, 180, Placement.WATER, District.Kind.CRAFTS, NEAR_RIVER),
		new Entry(Archetypes.WINDMILL, 26, p -> 1 + p / 160, 170, Placement.EDGE, District.Kind.FARMS, NO_RIVER),
		new Entry(Archetypes.GRANARY, 24, p -> 1 + p / 90, 180, Placement.HOME, District.Kind.MARKET, ALWAYS),
		new Entry(Archetypes.MASONRY, 28, p -> 1, 180, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS),
		new Entry(Archetypes.GUARDHOUSE, 32, p -> 1 + p / 70, 160, Placement.MAIN, null, ALWAYS),
		new Entry(Archetypes.BREWERY, 40, p -> 1, 140, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS),
		new Entry(Archetypes.TAILOR, 44, p -> 1, 140, Placement.MAIN, District.Kind.MARKET, ALWAYS),
		new Entry(Archetypes.TOWN_HALL, 60, p -> 1, 320, Placement.CIVIC, District.Kind.CIVIC, ALWAYS),
		new Entry(Archetypes.MARKET_HALL, 64, p -> 1, 280, Placement.CENTRAL, District.Kind.MARKET, ALWAYS),
		new Entry(Archetypes.LIBRARY, 70, p -> 1, 160, Placement.CIVIC, District.Kind.CIVIC, ALWAYS),
		new Entry(Archetypes.SCHOOL, 76, p -> 1, 150, Placement.CIVIC, District.Kind.RESIDENTIAL, ALWAYS),
		new Entry(Archetypes.WAREHOUSE, 70, p -> 1 + p / 120, 140, Placement.CRAFTS, District.Kind.HARBOUR, ALWAYS),
		new Entry(Archetypes.MANOR, 66, p -> Math.min(4, p / 60), 130, Placement.RICH, District.Kind.RICH, ALWAYS),
		new Entry(Archetypes.BATHHOUSE, 150, p -> 1, 140, Placement.CIVIC, District.Kind.MARKET, ALWAYS)
	);

	private Program() {
	}
}
