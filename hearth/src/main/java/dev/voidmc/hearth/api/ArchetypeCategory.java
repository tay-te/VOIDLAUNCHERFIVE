package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/** What kind of need an archetype answers, so the planner can match needs to buildings. */
public enum ArchetypeCategory implements StringRepresentable {
	/** Homes: cottages, farmhouses, rowhouses, townhouses, tenements, manors. */
	HOUSING("housing"),
	/** Where a trade is worked: smithy, bakery, mill, sawmill, quarry yard, fishery. */
	WORKPLACE("workplace"),
	/** Fields, orchards, pens, paddies: land that is worked. */
	FARM("farm"),
	/** Inn, market hall, shops, bank and mint. */
	COMMERCE("commerce"),
	/** Chapel or temple, town hall, library, school, bathhouse. */
	CIVIC("civic"),
	/** Granary, warehouse, storehouse. */
	STORAGE("storage"),
	/** Walls, gates, towers, barracks. */
	DEFENSE("defense"),
	/** Roads, lanes, stairs, bridges, terraces, retaining walls, walkways, docks. */
	INFRASTRUCTURE("infrastructure"),
	/** Plazas, wells, fountains, gardens, parks, lamp posts, statues. */
	LANDSCAPE("landscape"),
	/** The one building a settlement is known for: a great hall, a cathedral, a lighthouse, a pagoda. */
	LANDMARK("landmark"),
	// ---- phase 3 (LIVING_WORLD.md §7–§8): appended; codecs use names, so order is free
	EXTRACTION("extraction"),
	LAW("law"),
	NOBLE("noble"),
	UNDERGROUND("underground");

	public static final StringRepresentable.EnumCodec<ArchetypeCategory> CODEC = StringRepresentable.fromEnum(ArchetypeCategory::values);

	private final String name;

	ArchetypeCategory(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
