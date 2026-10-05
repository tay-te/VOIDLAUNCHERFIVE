package dev.voidmc.atlas.core.waypoints;

/**
 * Overworld and nether positions: one nether block is eight overworld blocks across, and height is the same in both.
 * Going into the nether divides x and z by eight, rounding towards negative infinity (so -1 becomes -1, not 0, which
 * is the block the game itself puts a portal search at); coming out multiplies by eight. The end, and any modded
 * dimension, has no fixed relation to the others, so conversions involving them give null.
 */
public final class NetherScale {
	public static final int SCALE = 8;
	/** The world border's reach on x and z; scaled results are held inside it. */
	public static final int WORLD_LIMIT = 30_000_000;

	private NetherScale() {
	}

	/** A converted position. */
	public record Point(int x, int y, int z) {
	}

	/** An overworld x or z as the matching nether one. */
	public static int toNether(int overworld) {
		return Math.floorDiv(overworld, SCALE);
	}

	/** A nether x or z as the matching overworld one, held inside the world border. */
	public static int toOverworld(int nether) {
		long scaled = (long) nether * SCALE;
		return (int) Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, scaled));
	}

	/** Whether positions can be carried from one dimension to the other (both are the overworld or the nether). */
	public static boolean convertible(String fromDim, String toDim) {
		String from = DimensionIds.normalise(fromDim);
		String to = DimensionIds.normalise(toDim);
		return from != null && to != null && (from.equals(to) || scaled(from) && scaled(to));
	}

	/**
	 * The position in {@code toDim} that matches {@code x, y, z} in {@code fromDim}, or null when the two are not
	 * related (the end, a modded dimension, or a name that is not a dimension). The same dimension gives the same
	 * position back. Dimension names may be ids or the nicknames {@link DimensionIds} knows.
	 */
	public static Point convert(int x, int y, int z, String fromDim, String toDim) {
		String from = DimensionIds.normalise(fromDim);
		String to = DimensionIds.normalise(toDim);
		if (from == null || to == null) {
			return null;
		}
		if (from.equals(to)) {
			return new Point(x, y, z);
		}
		if (from.equals(DimensionIds.OVERWORLD) && to.equals(DimensionIds.NETHER)) {
			return new Point(toNether(x), y, toNether(z));
		}
		if (from.equals(DimensionIds.NETHER) && to.equals(DimensionIds.OVERWORLD)) {
			return new Point(toOverworld(x), y, toOverworld(z));
		}
		return null;
	}

	private static boolean scaled(String id) {
		return id.equals(DimensionIds.OVERWORLD) || id.equals(DimensionIds.NETHER);
	}
}
