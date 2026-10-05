package dev.voidmc.mettle.character.skill.heavy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * The Heavy Weapons tree's numbers and shapes (TREES.md §5), free of players and worlds so JUnit can check them. The
 * behaviours that use them live beside this class; every number a player reads in a tooltip is here.
 */
public final class HeavyRules {
	/** Rhythm of the Axe: a log counts towards the rhythm when it falls within this many ticks of the last. */
	public static final int RHYTHM_WINDOW = 60;
	public static final int RHYTHM_MAX_STEPS = 4;
	public static final float RHYTHM_STEP = 0.15f;

	public static final float CLEAVE_SHARE = 0.30f;
	public static final float ARMOUR_CRUSHER_KEEP = 0.75f;
	public static final double BONE_BREAKER_SLOW = -0.40;
	public static final int BONE_BREAKER_TICKS = 60;
	public static final int STAGGER_TICKS = 10;
	public static final int OVERHEAD_STAGGER_TICKS = 10;
	public static final float OVERHEAD_CRIT = 2.0f;

	public static final float BERSERK_HEALTH = 0.40f;
	public static final float BERSERK_DAMAGE = 1.40f;
	public static final float BERSERK_LIFESTEAL = 0.20f;

	public static final double EARTHSHAKER_RADIUS = 4.0;
	public static final int EARTHSHAKER_STAGGER_TICKS = 20;
	public static final int EARTHSHAKER_COOLDOWN = 200;

	public static final int TIMBER_LIMIT = 64;
	/** Timber follows a trunk this far sideways from the cut, enough for acacia and jungle branches but not the next tree. */
	public static final int TIMBER_RADIUS = 6;
	/** A felled trunk must touch this many natural leaves, so a log wall or cabin is never "a tree". */
	public static final int TIMBER_MIN_LEAVES = 3;

	public static final double THROW_SPEED = 1.6;
	public static final double THROW_RANGE = 16.0;

	public static final float HEADHUNTER_CHANCE = 0.2f;

	private HeavyRules() {
	}

	/** Heft: 5 / 10 / 15 % harder hits. */
	public static float heft(int rank) {
		return 1f + 0.05f * rank;
	}

	/** Feller: 15 / 30 / 45 % faster chopping. */
	public static float feller(int rank) {
		return 1f + 0.15f * rank;
	}

	/**
	 * Rhythm of the Axe: the speed bonus for the next log, given how many logs fell in a row and how long ago the last
	 * one fell. The first log of a rhythm gets nothing; each one after adds 15 %, up to 60 %.
	 */
	public static float rhythm(int streak, long ticksSinceLast) {
		if (streak <= 0 || ticksSinceLast > RHYTHM_WINDOW || ticksSinceLast < 0) {
			return 0f;
		}
		return RHYTHM_STEP * Math.min(streak, RHYTHM_MAX_STEPS);
	}

	/** The streak after one more log falls: it grows inside the window and starts over outside it. */
	public static int nextStreak(int streak, long ticksSinceLast) {
		return ticksSinceLast >= 0 && ticksSinceLast <= RHYTHM_WINDOW ? Math.min(streak + 1, RHYTHM_MAX_STEPS) : 1;
	}

	/** Berserker: below 40 % of max health, and alive. */
	public static boolean berserk(float health, float maxHealth) {
		return health > 0 && health < maxHealth * BERSERK_HEALTH;
	}

	/** Overhead Blow turns vanilla's ×1.5 critical into ×2. */
	public static float critMultiplier(boolean overhead) {
		return overhead ? OVERHEAD_CRIT : 1.5f;
	}

	/**
	 * Timber: the logs of the trunk standing on {@code base} (which has just been cut), lowest first. Logs touch on any
	 * side or corner, never below the cut and never further than {@link #TIMBER_RADIUS} sideways, at most {@code limit}.
	 */
	public static List<BlockPos> trunk(BlockPos base, Predicate<BlockPos> isLog, int limit) {
		List<BlockPos> found = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(base);
		seen.add(base.asLong());
		while (!queue.isEmpty() && found.size() < limit) {
			BlockPos p = queue.poll();
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = p.offset(dx, dy, dz);
						if (n.getY() < base.getY() || Math.abs(n.getX() - base.getX()) > TIMBER_RADIUS || Math.abs(n.getZ() - base.getZ()) > TIMBER_RADIUS
							|| !seen.add(n.asLong()) || !isLog.test(n)) {
							continue;
						}
						found.add(n);
						queue.add(n);
						if (found.size() >= limit) {
							break;
						}
					}
				}
			}
		}
		found.sort(Comparator.comparingInt(BlockPos::getY));
		return found;
	}

	/** Whether a trunk carries a crown: at least {@link #TIMBER_MIN_LEAVES} natural leaves touch it. */
	public static boolean hasCrown(Collection<BlockPos> trunk, Predicate<BlockPos> isNaturalLeaves) {
		Set<Long> counted = new HashSet<>();
		for (BlockPos log : trunk) {
			for (BlockPos n : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
				if (isNaturalLeaves.test(n) && counted.add(n.asLong()) && counted.size() >= TIMBER_MIN_LEAVES) {
					return true;
				}
			}
		}
		return false;
	}
}
