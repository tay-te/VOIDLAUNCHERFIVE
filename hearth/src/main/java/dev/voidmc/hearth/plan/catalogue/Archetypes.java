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

	/** Small things that may stand inside a plaza without counting as an overlap (the plaza's {@code keep} lists them). */
	public static boolean isPlazaFurniture(Identifier id) {
		return id.equals(WELL) || id.equals(BELL) || id.equals(FOUNTAIN) || id.equals(STATUE) || id.equals(LAMP_POST)
			|| id.equals(MARKET_STALL);
	}
}
