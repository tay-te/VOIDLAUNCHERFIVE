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
 * The archetype catalogue of ARCHITECTURE.md §6.1 and the phase 3 catalogue of LIVING_WORLD.md §8.5 as
 * {@link ArchetypeInfo}s: what the planner assumes when no build module is registered (dev tools, the sim's own tests),
 * what the planner's test double serves, and the templates {@code Catalogue.resolve} falls back from when the build
 * module has not registered an id yet. The build module's real infos replace these whenever it is registered; sizes
 * here follow the catalogue's tables.
 */
public final class DefaultCatalogue {
	private static final java.util.Set<Identifier> PHASE3 = new java.util.HashSet<>();
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
		l.add(info(Archetypes.HUT, H, jobs(), 1, 5, 7, 5, 7, 5, 10, SettlementTier.CAMP, cultures(), flags(), List.of(Archetypes.COTTAGE), 0, 1.0));
		l.add(info(Archetypes.COTTAGE, H, jobs(), 2, 7, 11, 7, 11, 6, 16, SettlementTier.CAMP, cultures(), flags(),
			List.of(Archetypes.ROWHOUSE, Archetypes.SHOPHOUSE, Archetypes.TOWNHOUSE), 0, 1.0));
		l.add(info(Archetypes.FARMHOUSE, H, jobs(), 2, 11, 17, 11, 17, 11, 15, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.LONGHOUSE, H, jobs(), 3, 7, 9, 13, 21, 9, 13, SettlementTier.CAMP,
			cultures(Culture.NORTHERN, Culture.TUNDRA, Culture.MOORLAND), flags(), List.of(), 0, 1.4));
		l.add(info(Archetypes.ROWHOUSE, H, jobs(), 2, 5, 7, 8, 12, 9, 14, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE),
			List.of(Archetypes.TOWNHOUSE), 0, 1.0));
		l.add(info(Archetypes.TOWNHOUSE, H, jobs(), 4, 5, 8, 8, 13, 14, 24, SettlementTier.TOWN, cultures(), flags(ATTACHABLE, VERTICAL),
			List.of(Archetypes.TENEMENT), 0, 1.0));
		l.add(info(Archetypes.TENEMENT, H, jobs(), 8, 8, 12, 10, 14, 18, 28, SettlementTier.CITY, cultures(), flags(ATTACHABLE, VERTICAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.SHOPHOUSE, H, jobs(JobType.MERCHANT), 2, 5, 8, 8, 12, 9, 15, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE),
			List.of(Archetypes.TOWNHOUSE), 0, 0.8));
		l.add(info(Archetypes.MANOR, H, jobs(), 6, 15, 25, 15, 25, 10, 16, SettlementTier.TOWN, cultures(), flags(), List.of(), 4, 0.5));
		l.add(info(Archetypes.TOWER_HOUSE, H, jobs(), 2, 6, 7, 7, 8, 14, 22, SettlementTier.HAMLET,
			cultures(Culture.MOORLAND, Culture.ALPINE, Culture.MEDITERRANEAN, Culture.KARST), flags(VERTICAL), List.of(), 0, 0.7));
		l.add(info(Archetypes.STILT_HOUSE, H, jobs(), 2, 7, 9, 7, 9, 9, 13, SettlementTier.CAMP,
			cultures(Culture.MARITIME, Culture.TROPICAL, Culture.BAYOU), flags(ON_WATER), List.of(), 0, 1.5));
		l.add(info(Archetypes.CLIFF_DWELLING, H, jobs(), 2, 6, 10, 6, 9, 5, 6, SettlementTier.CAMP,
			cultures(Culture.ALPINE, Culture.KARST, Culture.MESA), flags(HILLSIDE, STACKABLE), List.of(), 0, 1.5));
		l.add(info(Archetypes.ROUND_HOUSE, H, jobs(), 2, 8, 9, 8, 9, 9, 13, SettlementTier.CAMP,
			cultures(Culture.STEPPE, Culture.TROPICAL), flags(), List.of(), 0, 1.5));
		l.add(info(Archetypes.COURTYARD_HOUSE, H, jobs(), 4, 11, 15, 11, 15, 7, 12, SettlementTier.HAMLET,
			cultures(Culture.MEDITERRANEAN, Culture.KARST, Culture.DESERT), flags(), List.of(), 0, 1.2));

		ArchetypeCategory W = ArchetypeCategory.WORKPLACE;
		l.add(info(Archetypes.BUILDERS_YARD, W, jobs(JobType.BUILDER), 0, 11, 17, 11, 17, 9, 14, SettlementTier.CAMP, cultures(), flags(), List.of(), 3, 1.0));
		l.add(info(Archetypes.SMITHY, W, jobs(JobType.SMITH), 0, 8, 12, 8, 12, 8, 13, SettlementTier.HAMLET, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.CARPENTRY, W, jobs(JobType.CARPENTER), 0, 7, 11, 8, 12, 8, 13, SettlementTier.HAMLET, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.MASONRY, W, jobs(JobType.MASON), 0, 8, 12, 8, 12, 8, 13, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.BAKERY, W, jobs(JobType.BAKER), 0, 7, 10, 7, 10, 8, 13, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE), List.of(), 0, 1.0));
		l.add(info(Archetypes.BREWERY, W, jobs(JobType.BREWER), 0, 9, 13, 9, 13, 11, 16, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.TAILOR, W, jobs(JobType.TAILOR), 0, 6, 9, 7, 11, 8, 14, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE), List.of(), 0, 1.0));
		l.add(info(Archetypes.SAWMILL, W, jobs(JobType.CARPENTER, JobType.FORESTER), 0, 9, 13, 9, 13, 7, 11, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.QUARRY, W, jobs(JobType.MASON, JobType.QUARRIER), 0, 12, 20, 12, 20, 6, 12, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.FISHERY, W, jobs(JobType.FISHER), 0, 7, 11, 7, 12, 7, 14, SettlementTier.CAMP, cultures(), flags(WATERFRONT), List.of(), 0, 1.0));
		l.add(info(Archetypes.WINDMILL, W, jobs(JobType.BAKER, JobType.FARMER), 0, 13, 17, 11, 15, 16, 26, SettlementTier.VILLAGE, cultures(), flags(VERTICAL, OUTSKIRTS), List.of(), 2, 1.0));
		l.add(info(Archetypes.WATERMILL, W, jobs(JobType.BAKER, JobType.FARMER), 0, 9, 12, 10, 13, 9, 14, SettlementTier.VILLAGE, cultures(), flags(WATERFRONT), List.of(), 2, 1.2));
		l.add(info(Archetypes.BARN, W, jobs(JobType.HERDER), 0, 9, 13, 11, 15, 11, 16, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.HUNTERS_LODGE, W, jobs(JobType.HUNTER), 0, 7, 10, 7, 10, 6, 10, SettlementTier.CAMP, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));

		ArchetypeCategory F = ArchetypeCategory.FARM;
		l.add(info(Archetypes.FIELD, F, jobs(JobType.FARMER), 0, 9, 24, 9, 24, 6, 10, SettlementTier.CAMP, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.ORCHARD, F, jobs(JobType.FARMER), 0, 11, 21, 11, 21, 8, 12, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 0.6));
		l.add(info(Archetypes.PASTURE, F, jobs(JobType.HERDER), 0, 13, 25, 13, 25, 6, 10, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 0.8));
		l.add(info(Archetypes.PADDY, F, jobs(JobType.FARMER), 0, 9, 19, 9, 19, 6, 12, SettlementTier.CAMP,
			cultures(Culture.KARST, Culture.TROPICAL), flags(OUTSKIRTS, HILLSIDE), List.of(), 0, 1.5));
		l.add(info(Archetypes.TERRACE_FIELD, F, jobs(JobType.FARMER), 0, 9, 21, 7, 13, 7, 14, SettlementTier.HAMLET, cultures(), flags(HILLSIDE, OUTSKIRTS), List.of(), 0, 1.0));

		ArchetypeCategory C = ArchetypeCategory.COMMERCE;
		l.add(info(Archetypes.MARKET_STALL, C, jobs(JobType.MERCHANT), 0, 4, 7, 4, 6, 4, 7, SettlementTier.HAMLET, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.MARKET_HALL, C, jobs(JobType.MERCHANT), 0, 11, 17, 13, 21, 12, 24, SettlementTier.TOWN, cultures(), flags(CENTRAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.INN, C, jobs(JobType.INNKEEPER), 4, 11, 15, 11, 15, 11, 17, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 2, 1.0));
		l.add(info(Archetypes.BANK, C, jobs(JobType.MINTER), 0, 9, 13, 9, 13, 9, 16, SettlementTier.VILLAGE, cultures(), flags(CENTRAL), List.of(), 1, 1.0));

		ArchetypeCategory V = ArchetypeCategory.CIVIC;
		l.add(info(Archetypes.CHAPEL, V, jobs(JobType.PRIEST), 0, 9, 13, 13, 19, 12, 30, SettlementTier.VILLAGE, cultures(), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.TOWN_HALL, V, jobs(), 0, 13, 19, 13, 19, 14, 32, SettlementTier.TOWN, cultures(), flags(CENTRAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.LIBRARY, V, jobs(JobType.SCHOLAR), 0, 9, 13, 11, 15, 12, 20, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.SCHOOL, V, jobs(), 0, 11, 15, 11, 15, 9, 15, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.BATHHOUSE, V, jobs(), 0, 11, 15, 11, 17, 8, 16, SettlementTier.CITY, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.GUARDHOUSE, V, jobs(JobType.GUARD), 2, 7, 11, 7, 11, 8, 18, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 4, 1.0));

		ArchetypeCategory S = ArchetypeCategory.STORAGE;
		l.add(info(Archetypes.STOREHOUSE, S, jobs(), 0, 7, 11, 7, 11, 8, 13, SettlementTier.CAMP, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.GRANARY, S, jobs(), 0, 7, 11, 9, 13, 8, 12, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.WAREHOUSE, S, jobs(), 0, 11, 17, 13, 19, 10, 18, SettlementTier.TOWN, cultures(), flags(), List.of(), 0, 1.0));

		ArchetypeCategory D = ArchetypeCategory.DEFENSE;
		l.add(info(Archetypes.PALISADE, D, jobs(), 0, 1, 256, 1, 256, 4, 6, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(Archetypes.WALL), 0, 1.0));
		l.add(info(Archetypes.WALL, D, jobs(), 0, 1, 256, 1, 256, 7, 11, SettlementTier.TOWN, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.GATEHOUSE, D, jobs(JobType.GUARD), 0, 13, 15, 7, 9, 14, 22, SettlementTier.TOWN, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.WATCHTOWER, D, jobs(JobType.GUARD), 0, 7, 9, 7, 9, 14, 22, SettlementTier.VILLAGE, cultures(), flags(VERTICAL, OUTSKIRTS), List.of(), 0, 1.0));

		ArchetypeCategory I = ArchetypeCategory.INFRASTRUCTURE;
		for (Identifier id : List.of(Archetypes.ROAD, Archetypes.STAIRS, Archetypes.BRIDGE, Archetypes.TERRACE, Archetypes.WALKWAY, Archetypes.DOCK)) {
			l.add(info(id, I, jobs(), 0, 1, 256, 1, 256, 1, 16, SettlementTier.CAMP, cultures(), flags(), List.of(), 0, 1.0));
		}

		ArchetypeCategory L = ArchetypeCategory.LANDSCAPE;
		l.add(info(Archetypes.PLAZA, L, jobs(), 0, 7, 31, 7, 31, 1, 10, SettlementTier.CAMP, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.WELL, L, jobs(), 0, 5, 9, 5, 9, 6, 9, SettlementTier.CAMP, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.FOUNTAIN, L, jobs(), 0, 5, 9, 5, 9, 4, 8, SettlementTier.VILLAGE, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.GARDEN, L, jobs(), 0, 7, 15, 7, 15, 1, 9, SettlementTier.HAMLET, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.PARK, L, jobs(), 0, 13, 25, 13, 25, 1, 12, SettlementTier.TOWN, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.LAMP_POST, L, jobs(), 0, 3, 5, 3, 5, 4, 7, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.STATUE, L, jobs(), 0, 5, 7, 5, 7, 7, 11, SettlementTier.TOWN, cultures(), flags(CENTRAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.BELL, L, jobs(), 0, 3, 5, 3, 5, 5, 9, SettlementTier.CAMP, cultures(), flags(CENTRAL), List.of(), 1, 1.0));

		ArchetypeCategory K = ArchetypeCategory.LANDMARK;
		l.add(info(Archetypes.BELL_TOWER, K, jobs(), 0, 7, 11, 7, 11, 26, 44, SettlementTier.TOWN,
			cultures(Culture.TEMPERATE, Culture.NORTHERN, Culture.MOORLAND, Culture.ALPINE, Culture.MEDITERRANEAN, Culture.MARITIME, Culture.WISTERIA, Culture.BAYOU), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.GREAT_HALL, K, jobs(), 0, 15, 23, 21, 31, 14, 22, SettlementTier.TOWN,
			cultures(Culture.NORTHERN, Culture.TUNDRA, Culture.MOORLAND, Culture.ALPINE), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.CATHEDRAL, K, jobs(JobType.PRIEST), 0, 17, 25, 29, 41, 26, 44, SettlementTier.CITY,
			cultures(Culture.TEMPERATE, Culture.MOORLAND, Culture.ALPINE, Culture.MEDITERRANEAN, Culture.MARITIME, Culture.WISTERIA),
			flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.TEMPLE, K, jobs(JobType.PRIEST), 0, 15, 21, 17, 25, 16, 30, SettlementTier.CITY,
			cultures(Culture.MEDITERRANEAN, Culture.KARST, Culture.DESERT, Culture.STEPPE, Culture.MESA, Culture.TROPICAL, Culture.BAYOU), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.LIGHTHOUSE, K, jobs(), 1, 11, 15, 13, 21, 26, 40, SettlementTier.TOWN,
			cultures(Culture.NORTHERN, Culture.MEDITERRANEAN, Culture.MARITIME, Culture.TROPICAL), flags(VERTICAL, WATERFRONT), List.of(), 1, 1.0));
		l.add(info(Archetypes.PAGODA, K, jobs(), 0, 15, 21, 17, 25, 28, 40, SettlementTier.TOWN, cultures(Culture.WISTERIA, Culture.KARST), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.CLOCK_TOWER, K, jobs(), 0, 7, 11, 7, 11, 28, 44, SettlementTier.CITY, cultures(), flags(VERTICAL, CENTRAL), List.of(), 1, 1.0));
		phase3(l);
		return Collections.unmodifiableList(l);
	}

	/**
	 * LIVING_WORLD.md §8.5: homes by station, the nobility, clergy and learning, social life, law, the workshops of the
	 * chains, extraction and the deep. Jobs, beds, sizes, heights, tiers, cultures, flags and limits are what the build
	 * module registers (CatalogueMatchesBuildTest keeps them so); upgrades and weights are the planner's own.
	 */
	/** Whether an id is of the phase 3 catalogue (LIVING_WORLD.md §8.5): these are asked for exactly, never by fallback. */
	public static boolean isPhase3(Identifier id) {
		return PHASE3.contains(id);
	}

	private static void phase3(List<ArchetypeInfo> l) {
		int before = l.size();
		phase3Infos(l);
		for (int i = before; i < l.size(); i++) {
			PHASE3.add(l.get(i).id());
		}
	}

	private static void phase3Infos(List<ArchetypeInfo> l) {
		ArchetypeCategory H = ArchetypeCategory.HOUSING;
		// homes by station
		l.add(info(Archetypes.HOVEL, H, jobs(), 2, 7, 9, 6, 8, 8, 9, SettlementTier.CAMP, cultures(), flags(), List.of(Archetypes.COTTAGE), 0, 0.8));
		l.add(info(Archetypes.SHACK, H, jobs(), 2, 5, 7, 5, 8, 8, 11, SettlementTier.CAMP, cultures(), flags(ATTACHABLE), List.of(Archetypes.ROWHOUSE), 0, 0.8));
		l.add(info(Archetypes.CROFT, H, jobs(), 3, 9, 12, 12, 16, 9, 12, SettlementTier.CAMP, cultures(), flags(), List.of(Archetypes.COTTAGE), 0, 1.0));
		l.add(info(Archetypes.MERCHANT_HOUSE, H, jobs(JobType.MERCHANT), 5, 7, 11, 11, 15, 20, 28, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE, VERTICAL), List.of(), 0, 1.0));
		l.add(info(Archetypes.REEVE_HOUSE, H, jobs(JobType.REEVE), 4, 10, 13, 13, 16, 13, 18, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 2, 1.0));
		l.add(info(Archetypes.RECTORY, H, jobs(), 3, 9, 12, 14, 17, 12, 17, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 2, 1.0));
		l.add(info(Archetypes.SCRIBE_HOUSE, H, jobs(), 3, 6, 8, 9, 12, 18, 24, SettlementTier.TOWN, cultures(), flags(ATTACHABLE), List.of(), 0, 1.0));
		l.add(info(Archetypes.HERMIT_HUT, H, jobs(), 1, 6, 8, 7, 9, 7, 9, SettlementTier.CAMP, cultures(), flags(OUTSKIRTS), List.of(), 1, 0.5));

		ArchetypeCategory N = ArchetypeCategory.NOBLE;
		l.add(info(Archetypes.KEEP, N, jobs(JobType.GUARD, JobType.LORD, JobType.STEWARD), 6, 17, 25, 17, 25, 24, 34, SettlementTier.TOWN, cultures(), flags(VERTICAL), List.of(Archetypes.CASTLE, Archetypes.PALACE), 1, 1.0));
		l.add(info(Archetypes.CASTLE, N, jobs(JobType.GUARD, JobType.LORD, JobType.PRIEST, JobType.STEWARD), 14, 37, 47, 39, 49, 30, 42, SettlementTier.CITY, cultures(), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.PALACE, N, jobs(JobType.GUARD, JobType.LORD, JobType.STEWARD), 8, 37, 47, 39, 49, 30, 42, SettlementTier.CITY, cultures(Culture.DESERT, Culture.KARST, Culture.MEDITERRANEAN), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.HUNTING_LODGE, N, jobs(JobType.HUNTER), 4, 15, 21, 14, 20, 20, 30, SettlementTier.TOWN, cultures(), flags(OUTSKIRTS), List.of(), 1, 1.0));
		l.add(info(Archetypes.STABLES, N, jobs(JobType.STABLEMASTER), 1, 17, 25, 15, 21, 12, 20, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));

		ArchetypeCategory V = ArchetypeCategory.CIVIC;
		l.add(info(Archetypes.MONASTERY, V, jobs(JobType.PRIEST, JobType.SCHOLAR, JobType.BREWER), 10, 21, 31, 21, 31, 12, 26, SettlementTier.TOWN, cultures(),
			flags(OUTSKIRTS), List.of(), 1, 1.0));
		l.add(info(Archetypes.CONVENT, V, jobs(JobType.PRIEST, JobType.HEALER), 8, 17, 27, 17, 27, 10, 18, SettlementTier.TOWN, cultures(), flags(OUTSKIRTS),
			List.of(), 1, 1.0));
		l.add(info(Archetypes.SHRINE, V, jobs(), 0, 3, 5, 3, 5, 6, 10, SettlementTier.HAMLET, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.UNIVERSITY, V, jobs(JobType.SCHOLAR), 8, 23, 33, 23, 33, 14, 24, SettlementTier.CITY, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.OBSERVATORY, V, jobs(JobType.SCHOLAR), 0, 11, 13, 11, 13, 16, 26, SettlementTier.CITY, cultures(), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.PLAYHOUSE, V, jobs(JobType.ENTERTAINER), 0, 15, 21, 15, 23, 10, 16, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.TILT_YARD, V, jobs(), 0, 15, 21, 33, 45, 7, 10, SettlementTier.TOWN, cultures(), flags(OUTSKIRTS), List.of(), 1, 1.0));
		l.add(info(Archetypes.INFIRMARY, V, jobs(JobType.HEALER), 0, 11, 15, 11, 17, 9, 14, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.ALMSHOUSE, V, jobs(), 6, 11, 15, 11, 15, 14, 16, SettlementTier.TOWN, cultures(), flags(), List.of(), 2, 1.0));
		l.add(info(Archetypes.ORPHANAGE, V, jobs(), 8, 11, 15, 11, 15, 14, 16, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.CEMETERY, V, jobs(JobType.PRIEST), 0, 13, 25, 13, 25, 6, 14, SettlementTier.VILLAGE, cultures(), flags(OUTSKIRTS), List.of(), 1, 1.0));

		ArchetypeCategory W = ArchetypeCategory.WORKPLACE;
		l.add(info(Archetypes.ALCHEMIST_TOWER, W, jobs(JobType.SCHOLAR), 1, 7, 9, 7, 9, 14, 22, SettlementTier.TOWN, cultures(), flags(VERTICAL), List.of(), 1,
			1.0));
		l.add(info(Archetypes.LOGGING_CAMP, W, jobs(JobType.FORESTER, JobType.HAULER), 0, 12, 17, 12, 17, 7, 11, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.CHARCOAL_KILN, W, jobs(JobType.CHARCOAL_BURNER), 0, 11, 14, 10, 13, 6, 9, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.BRICK_KILN, W, jobs(JobType.KILN_WORKER), 0, 10, 14, 10, 14, 9, 15, SettlementTier.VILLAGE, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.GLASSWORKS, W, jobs(JobType.GLASSMAKER), 0, 10, 14, 12, 16, 10, 16, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.SMELTER, W, jobs(JobType.SMELTER), 0, 10, 14, 10, 14, 12, 20, SettlementTier.VILLAGE, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.TANNERY, W, jobs(JobType.TANNER), 0, 9, 13, 11, 15, 11, 16, SettlementTier.VILLAGE, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.BUTCHER, W, jobs(JobType.BUTCHER), 0, 6, 9, 8, 11, 9, 14, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE), List.of(), 0, 1.0));
		l.add(info(Archetypes.CHANDLER, W, jobs(JobType.CHANDLER), 0, 5, 8, 7, 10, 9, 14, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE), List.of(), 0, 1.0));
		l.add(info(Archetypes.DYE_WORKS, W, jobs(JobType.DYER), 0, 9, 13, 9, 13, 10, 15, SettlementTier.TOWN, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.PAPER_MILL, W, jobs(JobType.PAPERMAKER), 0, 9, 12, 10, 13, 10, 15, SettlementTier.TOWN, cultures(), flags(WATERFRONT), List.of(), 1, 1.0));
		l.add(info(Archetypes.APIARY, W, jobs(JobType.BEEKEEPER), 0, 7, 11, 7, 11, 9, 12, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.SHIPYARD, W, jobs(JobType.BOATWRIGHT), 0, 11, 17, 15, 25, 9, 15, SettlementTier.VILLAGE, cultures(), flags(WATERFRONT), List.of(), 0, 1.0));
		l.add(info(Archetypes.SALT_WORKS, W, jobs(JobType.SALTER), 0, 11, 17, 12, 17, 6, 10, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));

		ArchetypeCategory C = ArchetypeCategory.COMMERCE;
		l.add(info(Archetypes.TAVERN, C, jobs(JobType.ENTERTAINER, JobType.INNKEEPER), 0, 10, 13, 10, 14, 9, 16, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 3, 1.0));
		l.add(info(Archetypes.HOSTEL, C, jobs(JobType.INNKEEPER), 6, 11, 15, 11, 15, 12, 16, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.CARAVANSERAI, C, jobs(JobType.INNKEEPER, JobType.TRADER), 8, 21, 31, 21, 31, 10, 14, SettlementTier.TOWN, cultures(Culture.DESERT, Culture.KARST, Culture.MEDITERRANEAN, Culture.MESA, Culture.STEPPE), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.THIEVES_DEN, C, jobs(JobType.FENCE), 0, 7, 10, 7, 10, 7, 10, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.CARAVAN_YARD, C, jobs(JobType.HAULER, JobType.TRADER), 0, 15, 21, 15, 21, 7, 11, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.COUNTING_HOUSE, C, jobs(JobType.MERCHANT, JobType.TRADER), 0, 7, 10, 9, 13, 9, 14, SettlementTier.TOWN, cultures(),
			flags(ATTACHABLE), List.of(), 1, 1.0));

		ArchetypeCategory L = ArchetypeCategory.LANDSCAPE;
		l.add(info(Archetypes.FESTIVAL_GROUND, L, jobs(), 0, 21, 41, 21, 41, 6, 16, SettlementTier.VILLAGE, cultures(), flags(OUTSKIRTS), List.of(), 1, 1.0));

		ArchetypeCategory J = ArchetypeCategory.LAW;
		l.add(info(Archetypes.COURTHOUSE, J, jobs(JobType.JUDGE), 0, 13, 19, 13, 19, 28, 36, SettlementTier.TOWN, cultures(), flags(VERTICAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.JAIL, J, jobs(JobType.GUARD, JobType.JAILER), 2, 9, 13, 10, 13, 12, 16, SettlementTier.TOWN, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.GALLOWS, J, jobs(), 0, 7, 9, 7, 9, 9, 12, SettlementTier.TOWN, cultures(), flags(CENTRAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.PILLORY, J, jobs(), 0, 5, 7, 5, 7, 6, 8, SettlementTier.VILLAGE, cultures(), flags(CENTRAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.WATCH_HOUSE, J, jobs(JobType.GUARD), 2, 8, 10, 8, 10, 16, 20, SettlementTier.VILLAGE, cultures(), flags(VERTICAL), List.of(), 6, 1.0));
		l.add(info(Archetypes.TOLL_GATE, J, jobs(JobType.GUARD), 1, 11, 13, 6, 8, 14, 17, SettlementTier.TOWN, cultures(), flags(), List.of(), 2, 1.0));

		ArchetypeCategory E = ArchetypeCategory.EXTRACTION;
		l.add(info(Archetypes.QUARRY_PIT, E, jobs(JobType.QUARRIER), 0, 15, 31, 15, 31, 8, 14, SettlementTier.HAMLET, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.2));
		l.add(info(Archetypes.MINE, E, jobs(JobType.MINER), 0, 7, 11, 16, 48, 6, 12, SettlementTier.HAMLET, cultures(), flags(HILLSIDE, OUTSKIRTS), List.of(), 0, 1.2));
		l.add(info(Archetypes.SHAFT, E, jobs(JobType.MINER), 0, 7, 11, 7, 11, 10, 16, SettlementTier.VILLAGE, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.CLAY_PIT, E, jobs(JobType.CLAY_DIGGER), 0, 9, 17, 9, 17, 6, 9, SettlementTier.CAMP, cultures(), flags(OUTSKIRTS), List.of(), 0, 1.0));
		l.add(info(Archetypes.SAND_PIT, E, jobs(JobType.CLAY_DIGGER), 0, 9, 17, 9, 17, 6, 9, SettlementTier.CAMP, cultures(), flags(OUTSKIRTS), List.of(), 0, 0.8));
		l.add(info(Archetypes.PLANTATION, E, jobs(JobType.FORESTER), 0, 15, 31, 15, 31, 9, 16, SettlementTier.VILLAGE, cultures(), flags(OUTSKIRTS), List.of(), 0, 0.8));

		ArchetypeCategory U = ArchetypeCategory.UNDERGROUND;
		l.add(info(Archetypes.DEEP_HALL, U, jobs(JobType.REEVE), 0, 15, 23, 17, 27, 12, 20, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 1, 1.0));
		l.add(info(Archetypes.DEEP_DWELLING, U, jobs(), 4, 7, 11, 7, 11, 7, 11, SettlementTier.VILLAGE, cultures(), flags(ATTACHABLE, HILLSIDE, STACKABLE), List.of(), 0, 1.0));
		l.add(info(Archetypes.DEEP_MARKET, U, jobs(JobType.MERCHANT), 0, 11, 17, 11, 17, 8, 12, SettlementTier.VILLAGE, cultures(), flags(CENTRAL), List.of(), 1, 1.0));
		l.add(info(Archetypes.DEEP_FORGE, U, jobs(JobType.SMELTER, JobType.SMITH), 0, 9, 13, 9, 13, 8, 14, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.DEEP_GLOW_GARDEN, U, jobs(JobType.FARMER), 0, 11, 19, 11, 19, 6, 9, SettlementTier.VILLAGE, cultures(), flags(), List.of(), 0, 1.0));
		l.add(info(Archetypes.DEEP_LIFT, U, jobs(JobType.HAULER), 0, 9, 11, 9, 11, 12, 320, SettlementTier.VILLAGE, cultures(), flags(VERTICAL), List.of(), 2, 1.0));
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
