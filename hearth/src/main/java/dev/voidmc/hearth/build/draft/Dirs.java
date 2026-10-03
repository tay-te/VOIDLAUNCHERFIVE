package dev.voidmc.hearth.build.draft;

import net.minecraft.core.Direction;

/**
 * Directions in a blueprint's <b>local frame</b>. Archetypes draw every building as if they stood in the street looking
 * at its front: {@code x} runs across the frontage from left to right, {@code z} runs into the depth from the front
 * (z = 0, the street side) to the back, {@code y} runs up from the floor level (the floor block itself is at y = -1).
 *
 * <p>Block states in the local frame use Minecraft's direction <i>labels</i> with this fixed meaning:
 * <pre>
 *   FRONT = SOUTH   toward the street   z - 1
 *   BACK  = NORTH   away from the street z + 1
 *   LEFT  = WEST                         x - 1
 *   RIGHT = EAST                         x + 1
 * </pre>
 * so a stair whose high side faces the back is {@code facing=BACK}, a bed whose head is at the back is
 * {@code facing=BACK}, and a shutter on the front wall is an open trapdoor {@code facing=FRONT}. The compiler rotates
 * every state to the plot's real facing. Never use {@link net.minecraft.core.BlockPos#relative} or
 * {@link Direction#getStepZ()} on local coordinates: in the local frame NORTH is {@code +z}. Use {@link #dx}/{@link #dz}.
 */
public final class Dirs {
	/** Toward the street. */
	public static final Direction FRONT = Direction.SOUTH;
	/** Away from the street. */
	public static final Direction BACK = Direction.NORTH;
	/** The left hand, standing in the street facing the building. */
	public static final Direction LEFT = Direction.WEST;
	/** The right hand, standing in the street facing the building. */
	public static final Direction RIGHT = Direction.EAST;
	/** The four horizontal directions in a fixed order: front, right, back, left. */
	public static final Direction[] HORIZONTAL = {FRONT, RIGHT, BACK, LEFT};

	private Dirs() {
	}

	/** The local x step of a direction. */
	public static int dx(Direction d) {
		return switch (d) {
			case EAST -> 1;
			case WEST -> -1;
			default -> 0;
		};
	}

	/** The local z step of a direction (NORTH, the back, is +z). */
	public static int dz(Direction d) {
		return switch (d) {
			case NORTH -> 1;
			case SOUTH -> -1;
			default -> 0;
		};
	}

	public static int dy(Direction d) {
		return switch (d) {
			case UP -> 1;
			case DOWN -> -1;
			default -> 0;
		};
	}

	/** The horizontal direction of a local step (dx, dz), or null when it is zero or diagonal. */
	public static Direction of(int dx, int dz) {
		if (dx == 0 && dz > 0) {
			return BACK;
		}
		if (dx == 0 && dz < 0) {
			return FRONT;
		}
		if (dz == 0 && dx > 0) {
			return RIGHT;
		}
		if (dz == 0 && dx < 0) {
			return LEFT;
		}
		return null;
	}

	/**
	 * The horizontal direction closest to a local vector. Ties go to the z axis (front/back), which reads better on
	 * buildings that are deeper than they are wide.
	 */
	public static Direction nearest(double dx, double dz) {
		if (Math.abs(dz) >= Math.abs(dx)) {
			return dz >= 0 ? BACK : FRONT;
		}
		return dx >= 0 ? RIGHT : LEFT;
	}

	/**
	 * The direction on your right hand when you face {@code d}: facing the back (as from the street) it is the right,
	 * facing the right it is the front. Label-wise this is {@link Direction#getClockWise()}.
	 */
	public static Direction right(Direction d) {
		return d.getClockWise();
	}

	/** The direction on your left hand when you face {@code d} ({@link Direction#getCounterClockWise()}). */
	public static Direction left(Direction d) {
		return d.getCounterClockWise();
	}
}
