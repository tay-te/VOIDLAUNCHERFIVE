package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/**
 * Places in a building that folk use. Blueprints report them (world coordinates) so NPCs know where to sleep, work,
 * sit, shop and gather without scanning the world for blocks.
 */
public enum PoiType implements StringRepresentable {
	/** The head of a bed. */
	BED("bed"),
	/** Where a worker stands to work: a workstation block, a field corner, a stall counter. {@link Poi#job()} says whose. */
	JOB_SITE("job_site"),
	/** A chest or barrel that is the building's store. */
	STORAGE("storage"),
	MARKET_STALL("market_stall"),
	/** A shop counter: the merchant stands behind it, the customer in front. */
	COUNTER("counter"),
	NOTICE_BOARD("notice_board"),
	/** A bell, a well, a fountain, a plaza centre: where folk gather. */
	MEETING_POINT("meeting_point"),
	/** A chair or bench (the block a seated NPC occupies). */
	SEAT("seat"),
	TABLE("table"),
	/** A fireplace, stove or campfire: cooking, warming, the evening's centre of a home. */
	HEARTH("hearth"),
	ALTAR("altar"),
	LECTERN("lectern"),
	/** A doorway's outside block: how a building is entered. */
	DOOR("door"),
	GUARD_POST("guard_post"),
	/** A mooring for boats. */
	DOCK("dock"),
	/** A settlement gate or the end of a road where it leaves. */
	GATE("gate"),
	/** A spot with a view: a balcony, a bench on a terrace, a tower top. Folk linger here on their free time. */
	VIEWPOINT("viewpoint"),
	// ---- phase 3 (LIVING_WORLD.md): appended; codecs use names, so order is free
	CELL("cell"),
	PILLORY("pillory"),
	GALLOWS("gallows"),
	BATH("bath"),
	GAMING_TABLE("gaming_table"),
	WORK_FACE("work_face"),
	HITCHING_POST("hitching_post"),
	THRONE("throne"),
	STAGE("stage"),
	SICKBED("sickbed"),
	STRONGBOX("strongbox"),
	/** Where beasts are kept: a pasture's middle, a kraal, a pen, a stall (the sim stocks and returns animals there). */
	PEN("pen");

	public static final StringRepresentable.EnumCodec<PoiType> CODEC = StringRepresentable.fromEnum(PoiType::values);

	private final String name;

	PoiType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
