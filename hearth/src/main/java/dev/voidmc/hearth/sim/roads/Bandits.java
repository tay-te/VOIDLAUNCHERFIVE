package dev.voidmc.hearth.sim.roads;

import net.minecraft.util.Mth;

/**
 * The danger of the roads and the odds of a robbery (docs/LIVING_WORLD_2.md §1.2, "Bandits"). Pure: the traffic's
 * books ({@link Traffic}) keep the bands and roll their days with these.
 *
 * <ul>
 *   <li>A road is dangerous where it is lonely (more than {@link #LONELY} blocks from either of its towns), where it
 *   runs through cover (woods, gorges), and where nobody has patrolled it lately; for a while after a band is broken
 *   on it, it is quiet.</li>
 *   <li>A band steps out at its spot by its morale; it robs the party unless the guards see it off, the odds its
 *   strength against twice the guards.</li>
 * </ul>
 */
public final class Bandits {
	/** Blocks from a town within which a road is never lonely. */
	public static final double LONELY = 500;
	/** Blocks beyond {@link #LONELY} at which a road is as lonely as it gets. */
	public static final double LONELIER = 700;
	/** Days a patrol keeps a road quiet. */
	public static final int PATROL_DAYS = 3;
	/** Days a road stays quiet after a band is broken on it. */
	public static final int BROKEN_DAYS = 10;
	/** Danger a road must reach somewhere along it to draw a band. */
	public static final float DANGEROUS = 0.45F;

	private Bandits() {
	}

	/** 0 near a town, 1 deep in the lonely middle of a long road. */
	public static float loneliness(double along, double length) {
		double fromEnd = Math.min(along, length - along);
		return (float) Mth.clamp((fromEnd - LONELY) / LONELIER, 0, 1);
	}

	/**
	 * The danger at a point of a road, 0–1.
	 *
	 * @param cover        0–1: how much of the road runs through woods or broken ground
	 * @param patrolled    a patrol walked it in the last {@link #PATROL_DAYS} days
	 * @param brokenLately a band was broken on it in the last {@link #BROKEN_DAYS} days
	 */
	public static float danger(double along, double length, float cover, boolean patrolled, boolean brokenLately) {
		float d = loneliness(along, length) * (0.45F + 0.55F * Mth.clamp(cover, 0, 1));
		if (patrolled) {
			d *= 0.3F;
		}
		if (brokenLately) {
			d *= 0.2F;
		}
		return d;
	}

	/** The worst danger anywhere along a road (at its middle, the loneliest point). */
	public static float peak(double length, float cover, boolean patrolled, boolean brokenLately) {
		return danger(length / 2, length, cover, patrolled, brokenLately);
	}

	/** Chance a band of {@code strength} robs a party with {@code guards} guards (else the guards see it off). */
	public static double robOdds(int strength, int guards) {
		return strength / (strength + 2.0 * Math.max(0, guards) + 1.5);
	}

	/** Chance a band steps out at a passing party on a day it has one to choose: by its morale and how dangerous its road is. */
	public static double attackOdds(double morale, float danger) {
		return Mth.clamp(0.15 + 0.5 * morale, 0, 0.9) * Mth.clamp(0.4 + danger, 0, 1);
	}

	/** What a fight the guards won costs a band: often a man, always some nerve. */
	public static Band repulsed(Band b, boolean lostOne) {
		return b.withStrength(b.strength() - (lostOne ? 1 : 0), b.morale() - 0.2);
	}

	/**
	 * The risk a sim caravan sets out with (rolled at its departure, so the sim stays the authority): one minus the chance
	 * of getting through every road on its way, each at its peak danger, with its guards.
	 */
	public static double caravanRisk(float[] peaks, boolean[] banded, int guards) {
		double through = 1;
		for (int i = 0; i < peaks.length; i++) {
			double meet = banded[i] ? 0.55 * peaks[i] + 0.1 : 0.12 * peaks[i];
			through *= 1 - meet * robOdds(5, guards);
		}
		return Mth.clamp(1 - through, 0, 0.9);
	}
}
