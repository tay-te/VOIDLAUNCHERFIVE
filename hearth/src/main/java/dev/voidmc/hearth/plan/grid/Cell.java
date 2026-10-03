package dev.voidmc.hearth.plan.grid;

/**
 * What occupies a block column of a settlement, as the planner sees it. Codes are bytes in {@link Occupancy}; a code
 * only ever overwrites a code of lower {@link #rank} (a building on a terrace, a plot taking the land reserved for it).
 */
public final class Cell {
	public static final byte FREE = 0;
	/** A road or stair street. */
	public static final byte ROAD = 1;
	/** A bridge deck or boardwalk over water. */
	public static final byte BRIDGE = 2;
	/** A paved square: public ground, gives access like a road. */
	public static final byte PLAZA = 3;
	public static final byte BUILDING = 4;
	public static final byte FARM = 5;
	/** A terrace platform: level ground other plots may stand on. */
	public static final byte TERRACE = 6;
	/** The corridor of a road that is planned but not built yet. */
	public static final byte PLANNED_ROAD = 7;
	/** Held for a project in the planner's queue, or for land the layout keeps (a square, a landmark's site). */
	public static final byte RESERVED = 8;
	/** A town wall or palisade. */
	public static final byte WALL = 9;
	/** Gardens, parks, fountains: occupied, but open. */
	public static final byte LANDSCAPE = 10;
	/** A pier. */
	public static final byte DOCK = 11;
	/** A gap the planner keeps open between buildings (a side passage, the gap before a detached house). */
	public static final byte SETBACK = 12;

	private Cell() {
	}

	public static int rank(byte code) {
		return switch (code) {
			case FREE -> 0;
			case SETBACK -> 1;
			case PLANNED_ROAD, RESERVED -> 2;
			case TERRACE, PLAZA -> 3;
			default -> 4;
		};
	}

	/** Public ground a building's entrance may open onto. */
	public static boolean isAccess(byte code) {
		return code == ROAD || code == BRIDGE || code == PLAZA;
	}

	/** Open ground a road may cross: free, or a gap kept between buildings. */
	public static boolean isBuildable(byte code) {
		return code == FREE || code == SETBACK;
	}

	/** Ground a new building's footprint may take: only free ground (setbacks stay open). */
	public static boolean isVacant(byte code) {
		return code == FREE;
	}

	/** Something standing that a building keeps its side gap from. */
	public static boolean isSolid(byte code) {
		return code == BUILDING || code == FARM || code == LANDSCAPE || code == TERRACE || code == RESERVED || code == WALL || code == DOCK;
	}

	public static String name(byte code) {
		return switch (code) {
			case FREE -> "free";
			case ROAD -> "road";
			case BRIDGE -> "bridge";
			case PLAZA -> "plaza";
			case BUILDING -> "building";
			case FARM -> "farm";
			case TERRACE -> "terrace";
			case PLANNED_ROAD -> "planned_road";
			case RESERVED -> "reserved";
			case WALL -> "wall";
			case LANDSCAPE -> "landscape";
			case DOCK -> "dock";
			case SETBACK -> "setback";
			default -> "?" + code;
		};
	}
}
