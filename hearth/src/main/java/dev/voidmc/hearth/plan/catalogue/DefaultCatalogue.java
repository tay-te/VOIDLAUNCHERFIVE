package dev.voidmc.hearth.plan.catalogue;

import static dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.ATTACHABLE;
import static dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.CENTRAL;
import static dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.HILLSIDE;
import static dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.ON_WATER;
import static dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.OUTSKIRTS;
import static dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.STACKABLE;
import static dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.VERTICAL;
import static dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.WATERFRONT;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * The archetype catalogue of ARCHITECTURE.md §6.1 as {@link ArchetypeInfo}s: what the planner assumes when no build
 * module is registered (dev tools, the sim's own tests) and what the planner's test double serves. The build module's
 * real infos replace these whenever it is registered; sizes here follow the catalogue's tables.
 */
public final class DefaultCatalogue {
	private static final List<ArchetypeInfo> ALL = build();

	private DefaultCatalogue() {
	}

	public static List<ArchetypeInfo> all() {
		return ALL;
	}

	private static List<ArchetypeInfo> build() {
		List<ArchetypeInfo> l = new ArrayList<>();
		ArchetypeCategory H = ArchetypeCategory.HOUSING;
		// housing                       jobs       beds  front  depth  height  tier                   cultures                                                       flags                     upgrades                                   max weight
		l.add(info(Archetypes.HUT, H, jobs(), 2, 5, 7, 5, 7, 4, 6, SettlementTier.CAMP, cultures(), flags(), List.of(Archetypes.COTTAGE), 0, 1.0));
		l.add(info(Archetypes.COTTAGE, H, jobs(), 3, 7, 11, 7, 11, 6, 10, SettlementTier.CAMP, cultures(), flags(),
			List.of(Archetypes.ROWHOUSE, Archetypes.SHOPHOUSE, Archetypes.TOWNHOUSE), 0, 1.0));
		l.add(info(Archetypes.FARMHOUSE, H, jobs(), 4, 11, 17, 11, 17, 7, 11, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.LONGHOUSE, H, jobs(), 6, 7, 9, 13, 21, 6, 10, SettlementTier.CAMP,
			cultures(Culture.NORTHERN, Culture.TUNDRA, Culture.MOORLAND), flags(), List.of(), 0, 1.4));
		l.add(info(Archetypes.ROWHOUSE, H, jobs(), 4, 5, 7, 8, 12, 8, 12, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE),
			List.of(Archetypes.TOWNHOUSE), 0, 1.0));
		l.add(info(Archetypes.TOWNHOUSE, H, jobs(), 6, 5, 8, 8, 13, 12, 18, SettlementTier.TOWN, cultures(), flags(ATTACHABLE, VERTICAL),
			List.of(Archetypes.TENEMENT), 0, 1.0));
		l.add(info(Archetypes.TENEMENT, H, jobs(), 12, 8, 12, 10, 14, 16, 26, SettlementTier.CITY, cultures(), flags(ATTACHABLE, VERTICAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.SHOPHOUSE, H, jobs(JobType.MERCHANT), 3, 5, 8, 8, 12, 8, 14, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE),
			List.of(Archetypes.TOWNHOUSE), 0, 0.8));
		l.add(info(Archetypes.MANOR, H, jobs(), 8, 15, 25, 15, 25, 10, 16, SettlementTier.TOWN, cultures(), flags(), List.of(), 4, 0.5));
		l.add(info(Archetypes.TOWER_HOUSE, H, jobs(), 3, 5, 7, 5, 7, 12, 20, SettlementTier.HAMLET,
			cultures(Culture.ALPINE, Culture.MOORLAND, Culture.MEDITERRANEAN, Culture.KARST), flags(VERTICAL), List.of(), 0, 0.7));
		l.add(info(Archetypes.STILT_HOUSE, H, jobs(), 3, 7, 9, 7, 9, 7, 10, SettlementTier.CAMP,
			cultures(Culture.BAYOU, Culture.TROPICAL, Culture.MARITIME), flags(ON_WATER), List.of(), 0, 1.5));
		l.add(info(Archetypes.CLIFF_DWELLING, H, jobs(), 3, 6, 10, 6, 9, 5, 8, SettlementTier.CAMP,
			cultures(Culture.MESA, Culture.KARST, Culture.ALPINE), flags(HILLSIDE, STACKABLE), List.of(), 0, 1.5));
		l.add(info(Archetypes.ROUND_HOUSE, H, jobs(), 3, 7, 9, 7, 9, 6, 9, SettlementTier.CAMP,
			cultures(Culture.STEPPE, Culture.TROPICAL), flags(), List.of(), 0, 1.5));
		l.add(info(Archetypes.COURTYARD_HOUSE, H, jobs(), 5, 11, 15, 11, 15, 6, 10, SettlementTier.HAMLET,
			cultures(Culture.DESERT, Culture.MEDITERRANEAN, Culture.KARST), flags(), List.of(), 0, 1.2));

		ArchetypeCategory W = ArchetypeCategory.WORKPLACE;
		l.add(info(Archetypes.BUILDERS_YARD, W, jobs(JobType.BUILDER), 0, 9, 13, 9, 13, 5, 8, SettlementTier.CAMP, cultures(), flags(), List.of(), 3, 1.0));
		l.add(info(Archetypes.SMITHY, W, jobs(JobType.SMITH), 0, 7, 11, 7, 11, 6, 10, SettlementTier.HAMLET, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.CARPENTRY, W, jobs(JobType.CARPENTER), 0, 7, 11, 8, 12, 6, 10, SettlementTier.HAMLET, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.MASONRY, W, jobs(JobType.MASON), 0, 8, 12, 8, 12, 6, 10, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.BAKERY, W, jobs(JobType.BAKER), 0, 7, 10, 7, 10, 6, 10, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE), List.of(), 0, 1.0));
		l.add(info(Archetypes.BREWERY, W, jobs(JobType.BREWER), 0, 8, 12, 9, 13, 7, 11, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.TAILOR, W, jobs(JobType.TAILOR), 0, 6, 9, 7, 10, 7, 11, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE), List.of(), 0, 1.0));
		l.add(info(Archetypes.SAWMILL, W, jobs(JobType.FORESTER), 0, 9, 13, 9, 13, 6, 10, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.QUARRY, W, jobs(JobType.QUARRIER, JobType.MASON), 0, 12, 20, 12, 20, 4, 8, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.FISHERY, W, jobs(JobType.FISHER), 0, 7, 11, 7, 11, 5, 9, SettlementTier.CAMP, cultures(), flags(WATERFRONT), List.of(), 0, 1.0));
		l.add(info(Archetypes.WINDMILL, W, jobs(JobType.FARMER, JobType.BAKER), 0, 7, 9, 7, 9, 14, 20, SettlementTier.VILLAGE, cultures(), flags(VERTICAL, OUTSKIRTS), List.of(), 2, 1.0));
		l.add(info(Archetypes.WATERMILL, W, jobs(JobType.FARMER, JobType.BAKER), 0, 8, 11, 9, 12, 8, 12, SettlementTier.VILLAGE, cultures(), flags(WATERFRONT), List.of(), 2, 1.2));
		l.add(info(Archetypes.BARN, W, jobs(JobType.HERDER), 0, 9, 13, 11, 15, 7, 11, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.HUNTERS_LODGE, W, jobs(JobType.HUNTER), 0, 7, 10, 7, 10, 5, 8, SettlementTier.CAMP, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));

		ArchetypeCategory F = ArchetypeCategory.FARM;
		l.add(info(Archetypes.FIELD, F, jobs(JobType.FARMER), 0, 9, 24, 9, 24, 1, 3, SettlementTier.CAMP, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.ORCHARD, F, jobs(JobType.FARMER), 0, 11, 21, 11, 21, 6, 9, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 0.6));
		l.add(info(Archetypes.PASTURE, F, jobs(JobType.HERDER), 0, 13, 25, 13, 25, 1, 3, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 0.8));
		l.add(info(Archetypes.PADDY, F, jobs(JobType.FARMER), 0, 9, 19, 9, 19, 1, 2, SettlementTier.CAMP,
			cultures(Culture.KARST, Culture.TROPICAL), flags(OUTSKIRTS, HILLSIDE), List.of(), 0, 1.5));
		l.add(info(Archetypes.TERRACE_FIELD, F, jobs(JobType.FARMER), 0, 9, 21, 7, 13, 1, 3, SettlementTier.HAMLET, cultures(), flags(HILLSIDE, OUTSKIRTS), List.of(), 0, 1.0));

		ArchetypeCategory C = ArchetypeCategory.COMMERCE;
		l.add(info(Archetypes.MARKET_STALL, C, jobs(JobType.MERCHANT), 0, 3, 5, 3, 5, 3, 5, SettlementTier.HAMLET, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.MARKET_HALL, C, jobs(JobType.MERCHANT), 0, 11, 17, 13, 21, 9, 14, SettlementTier.TOWN, cultures(), flags(CENTRAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.INN, C, jobs(JobType.INNKEEPER), 6, 11, 15, 11, 15, 10, 14, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 2, 1.0));
		l.add(info(Archetypes.BANK, C, jobs(JobType.MINTER), 0, 9, 13, 9, 13, 8, 12, SettlementTier.VILLAGE, cultures(), flags(CENTRAL), List.of(), 1, 1.0));

		ArchetypeCategory V = ArchetypeCategory.CIVIC;
		l.add(info(Archetypes.CHAPEL, V, jobs(JobType.PRIEST), 0, 9, 13, 13, 19, 10, 18, SettlementTier.VILLAGE, cultures(), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.TOWN_HALL, V, jobs(), 0, 13, 19, 13, 19, 12, 18, SettlementTier.TOWN, cultures(), flags(CENTRAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.LIBRARY, V, jobs(JobType.SCHOLAR), 0, 9, 13, 11, 15, 9, 14, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.SCHOOL, V, jobs(), 0, 11, 15, 11, 15, 8, 12, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.BATHHOUSE, V, jobs(), 0, 11, 15, 11, 17, 7, 11, SettlementTier.CITY, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.GUARDHOUSE, V, jobs(JobType.GUARD), 0, 7, 11, 7, 11, 7, 11, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 4, 1.0));

		ArchetypeCategory S = ArchetypeCategory.STORAGE;
		l.add(info(Archetypes.STOREHOUSE, S, jobs(), 0, 7, 11, 7, 11, 6, 9, SettlementTier.CAMP, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.GRANARY, S, jobs(), 0, 7, 11, 9, 13, 7, 11, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.WAREHOUSE, S, jobs(), 0, 11, 17, 13, 19, 9, 13, SettlementTier.TOWN, cultures(), flags(), List.of(), 0, 1.0));

		ArchetypeCategory D = ArchetypeCategory.DEFENSE;
		l.add(info(Archetypes.PALISADE, D, jobs(), 0, 1, 256, 1, 256, 4, 6, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(Archetypes.WALL), 0, 1.0));
		l.add(info(Archetypes.WALL, D, jobs(), 0, 1, 256, 1, 256, 7, 11, SettlementTier.TOWN, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.GATEHOUSE, D, jobs(JobType.GUARD), 0, 7, 11, 5, 9, 10, 15, SettlementTier.TOWN, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.WATCHTOWER, D, jobs(JobType.GUARD), 0, 5, 7, 5, 7, 14, 22, SettlementTier.VILLAGE, cultures(), flags(VERTICAL, OUTSKIRTS), List.of(), 0, 1.0));

		ArchetypeCategory I = ArchetypeCategory.INFRASTRUCTURE;
		for (Identifier id : List.of(Archetypes.ROAD, Archetypes.STAIRS, Archetypes.BRIDGE, Archetypes.TERRACE, Archetypes.WALKWAY, Archetypes.DOCK)) {
			l.add(info(id, I, jobs(), 0, 1, 256, 1, 256, 1, 16, SettlementTier.CAMP, cultures(), flags(), List.of(), 0, 1.0));
		}

		ArchetypeCategory L = ArchetypeCategory.LANDSCAPE;
		l.add(info(Archetypes.PLAZA, L, jobs(), 0, 7, 31, 7, 31, 1, 4, SettlementTier.CAMP, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.WELL, L, jobs(), 0, 3, 5, 3, 5, 3, 5, SettlementTier.CAMP, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.FOUNTAIN, L, jobs(), 0, 5, 9, 5, 9, 2, 6, SettlementTier.VILLAGE, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.GARDEN, L, jobs(), 0, 5, 13, 5, 13, 1, 5, SettlementTier.HAMLET, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.PARK, L, jobs(), 0, 13, 25, 13, 25, 1, 8, SettlementTier.TOWN, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.LAMP_POST, L, jobs(), 0, 1, 1, 1, 1, 3, 5, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.STATUE, L, jobs(), 0, 3, 5, 3, 5, 4, 8, SettlementTier.TOWN, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.BELL, L, jobs(), 0, 3, 5, 3, 5, 3, 6, SettlementTier.CAMP, cultures(), flags(CENTRAL), List.of(), 1, 1.0));

		ArchetypeCategory K = ArchetypeCategory.LANDMARK;
		l.add(info(Archetypes.BELL_TOWER, K, jobs(), 0, 7, 11, 7, 11, 24, 40, SettlementTier.TOWN,
			cultures(Culture.TEMPERATE, Culture.MEDITERRANEAN, Culture.ALPINE, Culture.MOORLAND, Culture.MARITIME, Culture.WISTERIA), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.GREAT_HALL, K, jobs(), 0, 15, 23, 21, 31, 12, 20, SettlementTier.TOWN,
			cultures(Culture.NORTHERN, Culture.TUNDRA, Culture.MOORLAND, Culture.ALPINE), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.CATHEDRAL, K, jobs(JobType.PRIEST), 0, 17, 25, 29, 41, 24, 44, SettlementTier.CITY,
			cultures(Culture.TEMPERATE, Culture.MEDITERRANEAN, Culture.ALPINE, Culture.MOORLAND, Culture.MARITIME, Culture.WISTERIA, Culture.NORTHERN),
			flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.TEMPLE, K, jobs(JobType.PRIEST), 0, 15, 21, 15, 25, 14, 28, SettlementTier.CITY,
			cultures(Culture.KARST, Culture.DESERT, Culture.STEPPE, Culture.TROPICAL, Culture.MESA, Culture.BAYOU), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.LIGHTHOUSE, K, jobs(), 0, 7, 9, 7, 9, 24, 36, SettlementTier.TOWN,
			cultures(Culture.MARITIME, Culture.TROPICAL, Culture.MEDITERRANEAN), flags(VERTICAL, WATERFRONT), List.of(), 1, 1.0));
		l.add(info(Archetypes.PAGODA, K, jobs(), 0, 9, 13, 9, 13, 24, 36, SettlementTier.TOWN, cultures(Culture.KARST, Culture.WISTERIA), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.CLOCK_TOWER, K, jobs(), 0, 7, 9, 7, 9, 24, 34, SettlementTier.CITY, cultures(), flags(VERTICAL, CENTRAL), List.of(), 1, 1.0));
		return Collections.unmodifiableList(l);
	}

	private static ArchetypeInfo info(Identifier id, ArchetypeCategory category, Set<JobType> jobs, int beds, int minFront, int maxFront,
		int minDepth, int maxDepth, int minHeight, int maxHeight, SettlementTier tier, Set<Culture> cultures, Set<SiteFlag> flags,
		List<Identifier> upgrades, int max, double weight) {
		return new ArchetypeInfo(id, category, jobs, beds, minFront, maxFront, minDepth, maxDepth, minHeight, maxHeight, tier, cultures,
			flags, upgrades, max, weight);
	}

	private static Set<JobType> jobs(JobType... jobs) {
		return jobs.length == 0 ? Set.of() : Collections.unmodifiableSet(EnumSet.of(jobs[0], jobs));
	}

	private static Set<Culture> cultures(Culture... cultures) {
		return cultures.length == 0 ? Set.of() : Collections.unmodifiableSet(EnumSet.of(cultures[0], cultures));
	}

	private static Set<SiteFlag> flags(SiteFlag... flags) {
		return flags.length == 0 ? Set.of() : Collections.unmodifiableSet(EnumSet.of(flags[0], flags));
	}
}
