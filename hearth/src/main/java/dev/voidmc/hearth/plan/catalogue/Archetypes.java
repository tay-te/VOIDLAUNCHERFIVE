package dev.voidmc.hearth.plan.catalogue;

import net.minecraft.resources.Identifier;

/**
 * The archetype ids of ARCHITECTURE.md §6.1 that the planner asks for by name. The planner never assumes one exists:
 * it goes through {@link Catalogue}, which knows what the build module actually registered and falls back when an id
 * is missing.
 */
public final class Archetypes {
	// housing
	public static final Identifier HUT = id("house/hut");
	public static final Identifier COTTAGE = id("house/cottage");
	public static final Identifier FARMHOUSE = id("house/farmhouse");
	public static final Identifier LONGHOUSE = id("house/longhouse");
	public static final Identifier ROWHOUSE = id("house/rowhouse");
	public static final Identifier TOWNHOUSE = id("house/townhouse");
	public static final Identifier TENEMENT = id("house/tenement");
	public static final Identifier SHOPHOUSE = id("house/shophouse");
	public static final Identifier MANOR = id("house/manor");
	public static final Identifier TOWER_HOUSE = id("house/tower_house");
	public static final Identifier STILT_HOUSE = id("house/stilt_house");
	public static final Identifier CLIFF_DWELLING = id("house/cliff_dwelling");
	public static final Identifier ROUND_HOUSE = id("house/round_house");
	public static final Identifier COURTYARD_HOUSE = id("house/courtyard_house");

	// workplaces
	public static final Identifier BUILDERS_YARD = id("work/builders_yard");
	public static final Identifier SMITHY = id("work/smithy");
	public static final Identifier CARPENTRY = id("work/carpentry");
	public static final Identifier MASONRY = id("work/masonry");
	public static final Identifier BAKERY = id("work/bakery");
	public static final Identifier BREWERY = id("work/brewery");
	public static final Identifier TAILOR = id("work/tailor");
	public static final Identifier SAWMILL = id("work/sawmill");
	public static final Identifier QUARRY = id("work/quarry");
	public static final Identifier FISHERY = id("work/fishery");
	public static final Identifier WINDMILL = id("work/windmill");
	public static final Identifier WATERMILL = id("work/watermill");
	public static final Identifier BARN = id("work/barn");
	public static final Identifier HUNTERS_LODGE = id("work/hunters_lodge");

	// farms
	public static final Identifier FIELD = id("farm/field");
	public static final Identifier ORCHARD = id("farm/orchard");
	public static final Identifier PASTURE = id("farm/pasture");
	public static final Identifier PADDY = id("farm/paddy");
	public static final Identifier TERRACE_FIELD = id("farm/terrace_field");

	// commerce
	public static final Identifier MARKET_STALL = id("commerce/market_stall");
	public static final Identifier MARKET_HALL = id("commerce/market_hall");
	public static final Identifier INN = id("commerce/inn");
	public static final Identifier BANK = id("commerce/bank");

	// civic
	public static final Identifier CHAPEL = id("civic/chapel");
	public static final Identifier TOWN_HALL = id("civic/town_hall");
	public static final Identifier LIBRARY = id("civic/library");
	public static final Identifier SCHOOL = id("civic/school");
	public static final Identifier BATHHOUSE = id("civic/bathhouse");
	public static final Identifier GUARDHOUSE = id("civic/guardhouse");

	// storage
	public static final Identifier STOREHOUSE = id("storage/storehouse");
	public static final Identifier GRANARY = id("storage/granary");
	public static final Identifier WAREHOUSE = id("storage/warehouse");

	// defense
	public static final Identifier PALISADE = id("defense/palisade");
	public static final Identifier WALL = id("defense/wall");
	public static final Identifier GATEHOUSE = id("defense/gatehouse");
	public static final Identifier WATCHTOWER = id("defense/watchtower");

	// infrastructure
	public static final Identifier ROAD = id("infra/road");
	public static final Identifier STAIRS = id("infra/stairs");
	public static final Identifier BRIDGE = id("infra/bridge");
	public static final Identifier TERRACE = id("infra/terrace");
	public static final Identifier WALKWAY = id("infra/walkway");
	public static final Identifier DOCK = id("infra/dock");

	// landscape
	public static final Identifier PLAZA = id("landscape/plaza");
	public static final Identifier WELL = id("landscape/well");
	public static final Identifier FOUNTAIN = id("landscape/fountain");
	public static final Identifier GARDEN = id("landscape/garden");
	public static final Identifier PARK = id("landscape/park");
	public static final Identifier LAMP_POST = id("landscape/lamp_post");
	public static final Identifier STATUE = id("landscape/statue");
	public static final Identifier BELL = id("landscape/bell");

	// landmarks
	public static final Identifier BELL_TOWER = id("landmark/bell_tower");
	public static final Identifier GREAT_HALL = id("landmark/great_hall");
	public static final Identifier CATHEDRAL = id("landmark/cathedral");
	public static final Identifier TEMPLE = id("landmark/temple");
	public static final Identifier LIGHTHOUSE = id("landmark/lighthouse");
	public static final Identifier PAGODA = id("landmark/pagoda");
	public static final Identifier CLOCK_TOWER = id("landmark/clock_tower");

	// ---- phase 3 (LIVING_WORLD.md §8.5): homes by station
	public static final Identifier HOVEL = id("house/hovel");
	public static final Identifier SHACK = id("house/shack");
	public static final Identifier CROFT = id("house/croft");
	public static final Identifier MERCHANT_HOUSE = id("house/merchant_house");
	public static final Identifier REEVE_HOUSE = id("house/reeve_house");
	public static final Identifier RECTORY = id("house/rectory");
	public static final Identifier SCRIBE_HOUSE = id("house/scribe_house");
	public static final Identifier HERMIT_HUT = id("house/hermit_hut");

	// nobility
	public static final Identifier KEEP = id("noble/keep");
	public static final Identifier CASTLE = id("noble/castle");
	public static final Identifier PALACE = id("noble/palace");
	public static final Identifier HUNTING_LODGE = id("noble/hunting_lodge");
	public static final Identifier STABLES = id("noble/stables");

	// clergy and learning
	public static final Identifier MONASTERY = id("civic/monastery");
	public static final Identifier CONVENT = id("civic/convent");
	public static final Identifier SHRINE = id("civic/shrine");
	public static final Identifier UNIVERSITY = id("civic/university");
	public static final Identifier OBSERVATORY = id("civic/observatory");
	public static final Identifier ALCHEMIST_TOWER = id("work/alchemist_tower");

	// social life
	public static final Identifier TAVERN = id("commerce/tavern");
	public static final Identifier HOSTEL = id("commerce/hostel");
	public static final Identifier CARAVANSERAI = id("commerce/caravanserai");
	public static final Identifier THIEVES_DEN = id("commerce/thieves_den");
	public static final Identifier PLAYHOUSE = id("civic/playhouse");
	public static final Identifier TILT_YARD = id("civic/tilt_yard");
	public static final Identifier INFIRMARY = id("civic/infirmary");
	public static final Identifier ALMSHOUSE = id("civic/almshouse");
	public static final Identifier ORPHANAGE = id("civic/orphanage");
	public static final Identifier CEMETERY = id("civic/cemetery");
	public static final Identifier FESTIVAL_GROUND = id("landscape/festival_ground");

	// law
	public static final Identifier COURTHOUSE = id("law/courthouse");
	public static final Identifier JAIL = id("law/jail");
	public static final Identifier GALLOWS = id("law/gallows");
	public static final Identifier PILLORY = id("law/pillory");
	public static final Identifier WATCH_HOUSE = id("law/watch_house");
	public static final Identifier TOLL_GATE = id("law/toll_gate");

	// workshops of the chains
	public static final Identifier LOGGING_CAMP = id("work/logging_camp");
	public static final Identifier CHARCOAL_KILN = id("work/charcoal_kiln");
	public static final Identifier BRICK_KILN = id("work/brick_kiln");
	public static final Identifier GLASSWORKS = id("work/glassworks");
	public static final Identifier SMELTER = id("work/smelter");
	public static final Identifier TANNERY = id("work/tannery");
	public static final Identifier BUTCHER = id("work/butcher");
	public static final Identifier CHANDLER = id("work/chandler");
	public static final Identifier DYE_WORKS = id("work/dye_works");
	public static final Identifier PAPER_MILL = id("work/paper_mill");
	public static final Identifier APIARY = id("work/apiary");
	public static final Identifier SHIPYARD = id("work/shipyard");
	public static final Identifier SALT_WORKS = id("work/salt_works");
	public static final Identifier CARAVAN_YARD = id("commerce/caravan_yard");
	public static final Identifier COUNTING_HOUSE = id("commerce/counting_house");

	// extraction: blueprints whose steps are cuts (LIVING_WORLD.md §7.3)
	public static final Identifier QUARRY_PIT = id("extract/quarry_pit");
	public static final Identifier MINE = id("extract/mine");
	public static final Identifier SHAFT = id("extract/shaft");
	public static final Identifier CLAY_PIT = id("extract/clay_pit");
	public static final Identifier SAND_PIT = id("extract/sand_pit");
	public static final Identifier PLANTATION = id("extract/plantation");

	// underground: the deep towns in the realms (LIVING_WORLD.md §8.2)
	public static final Identifier DEEP_HALL = id("deep/hall");
	public static final Identifier DEEP_DWELLING = id("deep/dwelling");
	public static final Identifier DEEP_MARKET = id("deep/market");
	public static final Identifier DEEP_FORGE = id("deep/forge");
	public static final Identifier DEEP_GLOW_GARDEN = id("deep/glow_garden");
	public static final Identifier DEEP_LIFT = id("deep/lift");

	private Archetypes() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("hearth", path);
	}

	/** Infrastructure whose geometry is a polyline in params (the footprint is only its bounding box). */
	public static boolean isLinear(Identifier id) {
		return id.equals(ROAD) || id.equals(STAIRS) || id.equals(BRIDGE) || id.equals(WALKWAY) || id.equals(DOCK)
			|| id.equals(WALL) || id.equals(PALISADE);
	}

	/** Plots that stand over a road (the road runs through them along their facing): gatehouses, toll gates. */
	public static boolean isOverRoad(Identifier id) {
		return id.equals(GATEHOUSE) || id.equals(TOLL_GATE);
	}

	/** Small things that may stand inside a plaza without counting as an overlap (the plaza's {@code keep} lists them). */
	public static boolean isPlazaFurniture(Identifier id) {
		return id.equals(WELL) || id.equals(BELL) || id.equals(FOUNTAIN) || id.equals(STATUE) || id.equals(LAMP_POST)
			|| id.equals(MARKET_STALL) || id.equals(PILLORY) || id.equals(GALLOWS);
	}
}
