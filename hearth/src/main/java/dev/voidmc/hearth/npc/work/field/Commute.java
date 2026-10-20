package dev.voidmc.hearth.npc.work.field;

import net.minecraft.core.BlockPos;

/**
 * The way out to a work site and back (docs/LIVING_WORLD_2.md §6.3): a worker whose site lies far from home — a herder
 * with the flock, miners up the track to the pithead — spends the first stretch of the working window walking out and the
 * last walking home. Where they are on the way is a pure function of the minute, so a record placed by it and the folk
 * walking it agree. Pure.
 */
public final class Commute {
	/** Game ticks in a minute of the day (a day is 24 000 ticks, 1 440 minutes). */
	public static final double TICKS_PER_MINUTE = 24000.0 / 1440.0;

	public enum Phase {
		/** Outside the working window. */
		NONE,
		/** On the way out: {@link Where#fraction} of the way from home to the site. */
		OUT,
		/** At the site. */
		AT,
		/** On the way home: {@link Where#fraction} of the way from home to the site (falling to 0 at the door). */
		BACK
	}

	/** @param fraction 0 at home (or the pen's gate), 1 at the site */
	public record Where(Phase phase, double fraction) {
		public static final Where NONE = new Where(Phase.NONE, 0);
	}

	private Commute() {
	}

	/** Minutes of the day the way takes at {@code speed} blocks a tick (at least one, at most a quarter of a day). */
	public static int minutes(double blocks, double speed) {
		double ticks = blocks / Math.max(0.01, speed);
		return (int) Math.max(1, Math.min(360, Math.ceil(ticks / TICKS_PER_MINUTE)));
	}

	/**
	 * Where in the window {@code [start, end)} (minutes of the day) a worker whose way takes {@code travel} minutes is at
	 * {@code minute}. A window too short for the way both ways is split down the middle.
	 */
	public static Where at(int minute, int start, int end, int travel) {
		if (minute < start || minute >= end || end <= start) {
			return Where.NONE;
		}
		int t = Math.max(1, Math.min(travel, (end - start) / 2));
		if (minute < start + t) {
			return new Where(Phase.OUT, (minute - start + 0.5) / t);
		}
		if (minute >= end - t) {
			return new Where(Phase.BACK, (end - minute - 0.5) / t);
		}
		return new Where(Phase.AT, 1);
	}

	/** The point {@code f} of the way from {@code from} to {@code to} (heights straight between too; callers find the ground). */
	public static BlockPos along(BlockPos from, BlockPos to, double f) {
		double c = Math.max(0, Math.min(1, f));
		return new BlockPos((int) Math.round(from.getX() + (to.getX() - from.getX()) * c), (int) Math.round(from.getY() + (to.getY() - from.getY()) * c),
			(int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * c));
	}

	/** Horizontal distance between two points, in blocks. */
	public static double distance(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
