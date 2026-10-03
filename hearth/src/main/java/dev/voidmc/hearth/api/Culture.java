package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/**
 * The building and living tradition of a settlement, chosen from the land it was founded on. Everything regional keys
 * off it: the build module's styles, the planner's preferred layouts, folk names and clothes, the market's local goods.
 *
 * <p>The biome-to-culture mapping lives in the sim module ({@link HearthServices#culture}); it reads biome tags and
 * climate so Expanse and modded biomes are covered without naming them, with Expanse's own biomes mapped by id.
 */
public enum Culture implements StringRepresentable {
	/** Plains, temperate forest, flower fields: timber frames on stone footings, cottage gardens. */
	TEMPERATE("temperate"),
	/** Expanse Wisteria Vale, cherry groves: refined timber frame, plaster, clay tile, blossom lanes. */
	WISTERIA("wisteria"),
	/** Taiga, redwood giants, pine heath: log halls, steep shingle roofs, carved gables. */
	NORTHERN("northern"),
	/** Meadows, groves, verdant peaks: stone ground floors under wooden chalets, deep eaves. */
	ALPINE("alpine"),
	/** Snowy plains and tundra: low spruce longhouses banked in snow, smoke and lantern light. */
	TUNDRA("tundra"),
	/** Heather moor, windswept hills: rubble-stone cottages under slate, dry-stone walls. */
	MOORLAND("moorland"),
	/** Cool coasts and fjords: stone harbour towns, net lofts, quays and lighthouses. */
	MARITIME("maritime"),
	/** Warm coasts and jungle: palm-wood houses on stilts under thatch, jetties. */
	TROPICAL("tropical"),
	/** Desert, dunes, saguaro flats: sandstone and adobe, flat roofs, courtyards, domes. */
	DESERT("desert"),
	/** Olive groves and warm temperate hills: whitewashed stone, terracotta roofs, hill towns. */
	MEDITERRANEAN("mediterranean"),
	/** Savanna, amber steppe, outback: round ochre houses, cone roofs, market rings. */
	STEPPE("steppe"),
	/** Jade karst, bamboo, monsoon forest: limestone sills, bamboo and timber, curved tile roofs. */
	KARST("karst"),
	/** Swamps, willow bayou, muskeg: stilt houses on boardwalks over the water. */
	BAYOU("bayou"),
	/** Badlands and painted canyons: terraced adobe pueblos stacked up the cliff. */
	MESA("mesa");

	public static final StringRepresentable.EnumCodec<Culture> CODEC = StringRepresentable.fromEnum(Culture::values);

	private final String name;

	Culture(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	/** Translation key for the culture's display name. */
	public String translationKey() {
		return "hearth.culture." + this.name;
	}
}
