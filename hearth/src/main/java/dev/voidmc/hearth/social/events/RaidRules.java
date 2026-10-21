package dev.voidmc.hearth.social.events;

import java.util.List;
import java.util.function.ToDoubleBiFunction;

/**
 * Which band may raid a town (docs/LIVING_WORLD_2.md §3.2, "Raids"): a band whose strength exceeds the town's defence,
 * within {@link #REACH} road blocks of the town's edge. Walls and guards make raids rare; hamlets on lonely roads suffer
 * most. Pure: the bands come from the roads' traffic (lane A1) as {@link BandView}s.
 */
public final class RaidRules {
	/** Road blocks from a town's edge within which a band may come by night. */
	public static final double REACH = 1200;

	private RaidRules() {
	}

	/**
	 * A band on its road: the road's two ends (settlement ids), its length, and how far along from {@code a} it lies.
	 */
	public record BandView(long id, int strength, double morale, long a, long b, double along, double length, int x, int z) {
		public BandView(long id, int strength, double morale, long a, long b, double along, double length) {
			this(id, strength, morale, a, b, along, length, 0, 0);
		}
	}

	/** The guards count twice, a wall four more, a keep six (§3.2). */
	public static int defence(int guards, boolean walls, boolean keep) {
		return Math.max(0, guards) * 2 + (walls ? 4 : 0) + (keep ? 6 : 0);
	}

	/**
	 * The boldest band within reach of the town that is stronger than its defence, or null.
	 *
	 * @param edge  the town's radius (its edge is this far from its heart along the road)
	 * @param road  the road distance between two settlements, negative when not joined
	 */
	public static BandView threat(long town, double edge, int defence, List<BandView> bands, ToDoubleBiFunction<Long, Long> road) {
		BandView best = null;
		for (BandView b : bands) {
			if (b.strength() <= defence) {
				continue;
			}
			double d = distance(town, b, road) - edge;
			if (d > REACH) {
				continue;
			}
			if (best == null || b.strength() > best.strength() || b.strength() == best.strength() && b.morale() > best.morale()) {
				best = b;
			}
		}
		return best;
	}

	/** Road blocks from a town's heart to a band's spot, or infinity. */
	public static double distance(long town, BandView b, ToDoubleBiFunction<Long, Long> road) {
		if (b.a() == town) {
			return b.along();
		}
		if (b.b() == town) {
			return b.length() - b.along();
		}
		double viaA = road.applyAsDouble(b.a(), town);
		double viaB = road.applyAsDouble(b.b(), town);
		double best = Double.POSITIVE_INFINITY;
		if (viaA >= 0) {
			best = Math.min(best, viaA + b.along());
		}
		if (viaB >= 0) {
			best = Math.min(best, viaB + b.length() - b.along());
		}
		return best;
	}
}
