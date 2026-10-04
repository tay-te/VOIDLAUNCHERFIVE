package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.Site;
import dev.voidmc.hearth.plan.resource.Extraction;
import dev.voidmc.hearth.plan.resource.Territory;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What a settlement builds as it grows, beyond homes and food (ARCHITECTURE.md §5.2, LIVING_WORLD.md §7–§8): its
 * workplaces and stores, the workings on its land and the workshops of its chains placed by their inputs, the civic,
 * social and law buildings of each tier, each with how many a town of a given size wants, how urgently, and where it
 * belongs. Archetypes the build module has not registered fall back or are skipped (see {@code Catalogue.resolve}).
 */
public final class Program {
	/**
	 * @param minPop   the population at which it is first wanted
	 * @param count    how many are wanted at a population
	 * @param priority how urgent, against housing (600+) and food (400+)
	 * @param siting   where exactly it wants to stand, when that depends on the town (near its inputs, by the chapel); null
	 *                 for the placement alone, or when the function answers null
	 * @param custom   how to plan it instead of the ordinary frontage search (workings at their sites, square furniture)
	 */
	public record Entry(Identifier id, int minPop, IntUnaryOperator count, double priority, Placement placement,
		District.@Nullable Kind district, Predicate<PlanContext> when, @Nullable Function<PlanContext, @Nullable Siting> siting,
		@Nullable BiPredicate<PlanContext, ArchetypeInfo> custom) {
		Entry sited(Function<PlanContext, @Nullable Siting> s) {
			return new Entry(this.id, this.minPop, this.count, this.priority, this.placement, this.district, this.when, s, this.custom);
		}

		Entry custom(BiPredicate<PlanContext, ArchetypeInfo> c) {
			return new Entry(this.id, this.minPop, this.count, this.priority, this.placement, this.district, this.when, this.siting, c);
		}
	}

	private static Entry e(Identifier id, int minPop, IntUnaryOperator count, double priority, Placement placement, District.@Nullable Kind district,
		Predicate<PlanContext> when) {
		return new Entry(id, minPop, count, priority, placement, district, when, null, null);
	}

	private static final Predicate<PlanContext> ALWAYS = c -> true;
	private static final Predicate<PlanContext> NEAR_WATER = c -> c.state.site != null && c.state.site.hasWater() && c.state.site.waterDistance() < 90;
	private static final Predicate<PlanContext> NEAR_RIVER = c -> c.state.site != null && c.state.site.water() == Site.WaterKind.RIVER
		&& c.state.site.waterDistance() < 90;
	private static final Predicate<PlanContext> NO_RIVER = NEAR_RIVER.negate();
	private static final Predicate<PlanContext> LAW = c -> c.config.living().law();
	private static final Predicate<PlanContext> SOCIAL = c -> c.config.living().social();
	private static final Predicate<PlanContext> EXTRACTING = c -> c.config.living().extraction();

	/** A workshop of a chain: as near its input as it can be, at the town's edge, by water if it wants it, downwind if it smells. */
	private static Function<PlanContext, @Nullable Siting> by(double water, double downwind, Identifier... inputs) {
		return c -> {
			int[] at = c.territory().first(inputs);
			if (at == null) {
				return null;
			}
			return Siting.near(at[0], at[1], 48).withWater(water).withDownwind(downwind).withEdge(0.15);
		};
	}

	/** Near one of these, in order of preference (the rectory by the chapel, the jail by the court). */
	private static Function<PlanContext, @Nullable Siting> near(double max, Identifier... ids) {
		return c -> {
			int[] at = c.territory().first(ids);
			return at == null ? null : Siting.near(at[0], at[1], max).withMain(0.5);
		};
	}

	/** At the edge of town on the downwind side, by water if there is any: the tannery, the dye works. */
	private static Function<PlanContext, @Nullable Siting> downwindEdge(double water) {
		return c -> {
			Territory t = c.territory();
			double[] w = t.windToward();
			double r = Parcels.edgeRing(c);
			int x = c.heartX() + (int) Math.round(w[0] * r);
			int z = c.heartZ() + (int) Math.round(w[1] * r);
			return new Siting(x, z, 0.5, water, 1.2, 0.4, -0.3, 0, 90);
		};
	}

	private static boolean site(PlanContext c, ResourceSite.Kind kind) {
		return !c.territory().workable(kind).isEmpty();
	}

	private static boolean has(PlanContext c, Identifier... ids) {
		return c.territory().first(ids) != null;
	}

	private static boolean culture(PlanContext c, Culture... cultures) {
		for (Culture k : cultures) {
			if (c.culture == k) {
				return true;
			}
		}
		return false;
	}

	public static final List<Entry> ENTRIES = List.of(
		e(Archetypes.BUILDERS_YARD, 0, p -> p < 80 ? 1 : p < 180 ? 2 : 3, 1000, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS),
		e(Archetypes.STOREHOUSE, 5, p -> 1 + p / 35, 330, Placement.CRAFTS, District.Kind.MARKET, ALWAYS),
		e(Archetypes.FISHERY, 4, p -> 1 + p / 45, 320, Placement.WATER, District.Kind.HARBOUR, NEAR_WATER),
		e(Archetypes.CARPENTRY, 8, p -> 1 + p / 120, 250, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS),
		e(Archetypes.SMITHY, 10, p -> 1 + p / 90, 250, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS),
		e(Archetypes.MARKET_STALL, 12, p -> Math.min(10, 1 + p / 14), 240, Placement.CENTRAL, District.Kind.MARKET, ALWAYS),
		// timber: the logging camp at the wood's edge, the sawmill by it and by the river that turns its saw
		e(Archetypes.LOGGING_CAMP, 8, p -> 1 + p / 90, 215, Placement.SITED, District.Kind.CRAFTS,
			c -> EXTRACTING.test(c) && Extraction.available(c, Archetypes.LOGGING_CAMP)).custom(Extraction::place),
		e(Archetypes.SAWMILL, 14, p -> 1 + p / 130, 200, Placement.EDGE, District.Kind.CRAFTS, ALWAYS).sited(by(1.0, 0, Archetypes.LOGGING_CAMP)),
		e(Archetypes.HUNTERS_LODGE, 10, p -> 1, 120, Placement.EDGE, null, ALWAYS),
		e(Archetypes.BARN, 16, p -> 1 + p / 60, 170, Placement.EDGE, District.Kind.FARMS, ALWAYS),
		// stone: the quarry pit on the town's rock, the mason's yard beside it
		e(Archetypes.QUARRY_PIT, 16, p -> Math.min(3, 1 + p / 90), 205, Placement.SITED, District.Kind.CRAFTS,
			c -> EXTRACTING.test(c) && Extraction.available(c, Archetypes.QUARRY_PIT)).custom(Extraction::place),
		e(Archetypes.QUARRY, 22, p -> 1 + p / 140, 160, Placement.EDGE, District.Kind.CRAFTS, ALWAYS).sited(by(0, 0.2, Archetypes.QUARRY_PIT)),
		e(Archetypes.CHAPEL, 20, p -> 1, 290, Placement.RISE, District.Kind.CIVIC, ALWAYS),
		e(Archetypes.INN, 20, p -> p < 100 ? 1 : 2, 270, Placement.MAIN, District.Kind.MARKET, ALWAYS),
		e(Archetypes.BAKERY, 22, p -> 1 + p / 80, 230, Placement.MAIN, District.Kind.MARKET, ALWAYS),
		e(Archetypes.BANK, 30, p -> 1, 260, Placement.CENTRAL, District.Kind.MARKET, ALWAYS),
		e(Archetypes.WATERMILL, 26, p -> 1, 180, Placement.WATER, District.Kind.CRAFTS, NEAR_RIVER),
		e(Archetypes.WINDMILL, 26, p -> 1 + p / 160, 170, Placement.EDGE, District.Kind.FARMS, NO_RIVER),
		e(Archetypes.GRANARY, 24, p -> 1 + p / 90, 180, Placement.CRAFTS, District.Kind.FARMS, ALWAYS),
		e(Archetypes.MASONRY, 28, p -> 1, 180, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS).sited(by(0, 0, Archetypes.QUARRY_PIT, Archetypes.QUARRY)),
		e(Archetypes.GUARDHOUSE, 32, p -> 1 + p / 70, 160, Placement.MAIN, null, ALWAYS),
		e(Archetypes.BREWERY, 40, p -> 1, 140, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS),
		e(Archetypes.TAILOR, 44, p -> 1, 140, Placement.MAIN, District.Kind.MARKET, ALWAYS),
		e(Archetypes.TOWN_HALL, 60, p -> 1, 320, Placement.CIVIC, District.Kind.CIVIC, ALWAYS),
		e(Archetypes.MARKET_HALL, 64, p -> 1, 280, Placement.CENTRAL, District.Kind.MARKET, ALWAYS),
		e(Archetypes.LIBRARY, 70, p -> 1, 160, Placement.CIVIC, District.Kind.CIVIC, ALWAYS),
		e(Archetypes.SCHOOL, 76, p -> 1, 150, Placement.CIVIC, District.Kind.RESIDENTIAL, ALWAYS),
		e(Archetypes.WAREHOUSE, 70, p -> 1 + p / 120, 140, Placement.CRAFTS, District.Kind.HARBOUR, ALWAYS),
		e(Archetypes.MANOR, 66, p -> Math.min(4, p / 60), 130, Placement.RICH, District.Kind.RICH, c -> !c.config.living().stations()),
		e(Archetypes.MANOR, 90, p -> Math.min(3, p / 90), 120, Placement.RICH, District.Kind.RICH, c -> c.config.living().stations()),
		e(Archetypes.BATHHOUSE, 150, p -> 1, 140, Placement.CIVIC, District.Kind.MARKET, ALWAYS),

		// ---- the chains (LIVING_WORLD.md §7.5): workings on the land and the workshops by them
		e(Archetypes.MINE, 30, p -> Math.min(3, 1 + p / 80), 195, Placement.SITED, District.Kind.CRAFTS,
			c -> EXTRACTING.test(c) && Extraction.available(c, Archetypes.MINE)).custom(Extraction::place),
		e(Archetypes.CHARCOAL_KILN, 26, p -> 1 + p / 100, 150, Placement.EDGE, District.Kind.CRAFTS,
			c -> has(c, Archetypes.LOGGING_CAMP, Archetypes.MINE, Archetypes.SHAFT)).sited(by(0, 0.4, Archetypes.LOGGING_CAMP, Archetypes.SMELTER)),
		e(Archetypes.SMELTER, 30, p -> 1 + p / 100, 175, Placement.EDGE, District.Kind.CRAFTS,
			c -> has(c, Archetypes.MINE, Archetypes.SHAFT)).sited(by(0.2, 0.6, Archetypes.MINE, Archetypes.SHAFT)),
		e(Archetypes.CLAY_PIT, 28, p -> 1, 150, Placement.SITED, District.Kind.CRAFTS,
			c -> EXTRACTING.test(c) && Extraction.available(c, Archetypes.CLAY_PIT)).custom(Extraction::place),
		e(Archetypes.BRICK_KILN, 30, p -> 1 + p / 150, 150, Placement.EDGE, District.Kind.CRAFTS,
			c -> has(c, Archetypes.CLAY_PIT) || c.population >= 80).sited(by(0.2, 0.3, Archetypes.CLAY_PIT)),
		e(Archetypes.SAND_PIT, 55, p -> 1, 120, Placement.SITED, District.Kind.CRAFTS,
			c -> EXTRACTING.test(c) && Extraction.available(c, Archetypes.SAND_PIT)).custom(Extraction::place),
		e(Archetypes.GLASSWORKS, 55, p -> 1, 125, Placement.CRAFTS, District.Kind.CRAFTS,
			c -> has(c, Archetypes.SAND_PIT, Archetypes.BRICK_KILN) || c.population >= 100).sited(by(0.2, 0.2, Archetypes.SAND_PIT, Archetypes.BRICK_KILN)),
		e(Archetypes.SALT_WORKS, 18, p -> 1, 140, Placement.SITED, District.Kind.HARBOUR,
			c -> EXTRACTING.test(c) && Extraction.available(c, Archetypes.SALT_WORKS)).custom(Extraction::place),
		e(Archetypes.PLANTATION, 70, p -> 1 + p / 140, 110, Placement.SITED, District.Kind.FARMS,
			c -> EXTRACTING.test(c) && has(c, Archetypes.LOGGING_CAMP) && Extraction.available(c, Archetypes.PLANTATION)).custom(Extraction::place),
		e(Archetypes.TANNERY, 34, p -> 1 + p / 130, 150, Placement.EDGE, District.Kind.POOR,
			c -> has(c, Archetypes.PASTURE, Archetypes.BARN, Archetypes.HUNTERS_LODGE) || c.population >= 50).sited(downwindEdge(0.8)),
		e(Archetypes.BUTCHER, 30, p -> 1 + p / 90, 140, Placement.MAIN, District.Kind.MARKET, ALWAYS),
		e(Archetypes.CHANDLER, 40, p -> 1 + p / 160, 135, Placement.MAIN, District.Kind.MARKET, ALWAYS),
		e(Archetypes.APIARY, 18, p -> 1 + p / 150, 100, Placement.EDGE, District.Kind.FARMS,
			c -> culture(c, Culture.TEMPERATE, Culture.WISTERIA, Culture.MEDITERRANEAN, Culture.ALPINE, Culture.MOORLAND) || has(c, Archetypes.ORCHARD)),
		e(Archetypes.DYE_WORKS, 70, p -> 1, 110, Placement.CRAFTS, District.Kind.CRAFTS, ALWAYS).sited(downwindEdge(1.2)),
		e(Archetypes.PAPER_MILL, 90, p -> 1, 100, Placement.WATER, District.Kind.CRAFTS, NEAR_RIVER),
		e(Archetypes.SHIPYARD, 60, p -> 1, 125, Placement.WATER, District.Kind.HARBOUR,
			c -> NEAR_WATER.test(c) && (c.state.site.water() == Site.WaterKind.COAST || site(c, ResourceSite.Kind.FISH) || c.state.signature.docks)),
		e(Archetypes.COUNTING_HOUSE, 80, p -> 1, 120, Placement.CENTRAL, District.Kind.MARKET, ALWAYS),
		e(Archetypes.CARAVAN_YARD, 75, p -> 1, 120, Placement.GATEWAY, null, ALWAYS),

		// ---- law (LIVING_WORLD.md §4, §8.5): justice on the square, the jail by the court
		e(Archetypes.PILLORY, 24, p -> 1, 150, Placement.CENTRAL, District.Kind.MARKET, LAW),
		e(Archetypes.COURTHOUSE, 75, p -> 1, 215, Placement.CIVIC, District.Kind.CIVIC, LAW),
		e(Archetypes.JAIL, 66, p -> 1, 190, Placement.CIVIC, District.Kind.CIVIC, LAW)
			.sited(near(36, Archetypes.COURTHOUSE, Archetypes.TOWN_HALL, Archetypes.GUARDHOUSE)),
		e(Archetypes.GALLOWS, 95, p -> 1, 120, Placement.CENTRAL, District.Kind.MARKET, c -> LAW.test(c) && c.config.living().gallows()),

		// ---- social life (LIVING_WORLD.md §5–§6, §8.5)
		e(Archetypes.CEMETERY, 25, p -> 1, 160, Placement.EDGE, District.Kind.CIVIC, SOCIAL)
			.sited(c -> {
				int[] at = c.territory().first(Archetypes.CHAPEL, Archetypes.CATHEDRAL, Archetypes.TEMPLE);
				return at == null ? null : Siting.near(at[0], at[1], 56).withHeight(0.4).withEdge(0.2);
			}),
		e(Archetypes.TAVERN, 30, p -> 1 + p / 80, 200, Placement.MAIN, District.Kind.MARKET, SOCIAL),
		e(Archetypes.HOSTEL, 40, p -> 1, 140, Placement.GATEWAY, null, SOCIAL),
		e(Archetypes.FESTIVAL_GROUND, 40, p -> 1, 110, Placement.EDGE, District.Kind.FARMS, SOCIAL),
		e(Archetypes.SHRINE, 12, p -> 1 + p / 70, 70, Placement.GATEWAY, null, SOCIAL),
		e(Archetypes.STABLES, 50, p -> 1, 105, Placement.MAIN, District.Kind.MARKET, SOCIAL).sited(near(40, Archetypes.INN, Archetypes.HOSTEL)),
		e(Archetypes.INFIRMARY, 70, p -> 1, 150, Placement.CIVIC, District.Kind.CIVIC, SOCIAL)
			.sited(near(48, Archetypes.CONVENT, Archetypes.CHAPEL, Archetypes.CATHEDRAL, Archetypes.TEMPLE)),
		e(Archetypes.CARAVANSERAI, 70, p -> 1, 140, Placement.GATEWAY, null, SOCIAL),
		e(Archetypes.ALMSHOUSE, 80, p -> 1 + p / 160, 140, Placement.HOVELS, District.Kind.POOR, SOCIAL)
			.sited(near(56, Archetypes.CHAPEL, Archetypes.CATHEDRAL, Archetypes.TEMPLE, Archetypes.MONASTERY)),
		e(Archetypes.MONASTERY, 90, p -> 1, 125, Placement.QUIET, null, SOCIAL),
		e(Archetypes.PLAYHOUSE, 100, p -> 1, 130, Placement.MAIN, District.Kind.MARKET, SOCIAL),
		e(Archetypes.THIEVES_DEN, 100, p -> 1, 90, Placement.HIDDEN, District.Kind.POOR, SOCIAL),
		e(Archetypes.TILT_YARD, 110, p -> 1, 100, Placement.EDGE, District.Kind.CASTLE, SOCIAL)
			.sited(c -> {
				int[] at = c.territory().first(Archetypes.KEEP, Archetypes.CASTLE, Archetypes.PALACE);
				return at == null ? null : Siting.near(at[0], at[1], 80).withEdge(0.3);
			}),
		e(Archetypes.ORPHANAGE, 120, p -> 1, 110, Placement.CIVIC, District.Kind.RESIDENTIAL, SOCIAL)
			.sited(near(56, Archetypes.CONVENT, Archetypes.CHAPEL, Archetypes.CATHEDRAL)),
		e(Archetypes.ALCHEMIST_TOWER, 120, p -> 1, 80, Placement.CIVIC, District.Kind.CIVIC, SOCIAL)
			.sited(near(48, Archetypes.LIBRARY, Archetypes.UNIVERSITY)),
		e(Archetypes.CONVENT, 130, p -> 1, 115, Placement.QUIET, null, SOCIAL),
		e(Archetypes.UNIVERSITY, 160, p -> 1, 145, Placement.CIVIC, District.Kind.CIVIC, SOCIAL),
		e(Archetypes.OBSERVATORY, 170, p -> 1, 100, Placement.RISE, District.Kind.CIVIC, SOCIAL),
		e(Archetypes.HERMIT_HUT, 60, p -> 1, 40, Placement.QUIET, null, SOCIAL)
	);

	private Program() {
	}

	/** The program's entry for an archetype (the first, when there are several), or null. */
	public static @Nullable Entry entry(Identifier id) {
		for (Entry e : ENTRIES) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}
}
