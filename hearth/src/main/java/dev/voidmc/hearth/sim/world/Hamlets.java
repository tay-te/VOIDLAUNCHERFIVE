package dev.voidmc.hearth.sim.world;

import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Daughter hamlets (ARCHITECTURE.md §5.1): a settlement that has run out of beds for days sends a party of settlers out,
 * and this finds them a site 600–2500 blocks away — dry, gentle ground, water near, clear of every town live or historic
 * — a few candidates per tick under a time budget, never computing a region's history on the server thread (a region
 * not yet surveyed is surveyed off-thread and asked about again). Server thread only.
 */
final class Hamlets {
	/** Candidates looked at per search before taking the best seen (or giving up). */
	private static final int MAX_TRIES = 48;
	/** A site this good is taken at once. */
	private static final double GOOD = 14;
	/** Searches waiting at once, at most (a town asks again later if refused). */
	private static final int MAX_SEARCHES = 16;

	private final SimWorld world;
	private final Map<Long, Search> searches = new LinkedHashMap<>();
	private int started;
	private int failed;

	private static final class Search {
		final long parent;
		final RandomSource random;
		int tried;
		@Nullable BlockPos best;
		double bestScore = Double.NEGATIVE_INFINITY;

		Search(long parent, long seed) {
			this.parent = parent;
			this.random = RandomSource.create(seed);
		}
	}

	Hamlets(SimWorld world) {
		this.world = world;
	}

	/** Starts a search for a hamlet of {@code s}; true if one is now under way. */
	boolean request(SimSettlement s) {
		if (this.searches.containsKey(s.id())) {
			return true;
		}
		if (this.searches.size() >= MAX_SEARCHES) {
			return false;
		}
		this.searches.put(s.id(), new Search(s.id(), s.id() * 0x9E3779B97F4A7C15L ^ s.simTick()));
		this.started++;
		return true;
	}

	/** Looks at candidates for the oldest search until the budget is spent; founds the hamlet when a site is chosen. */
	void tick(long budgetNanos) {
		if (this.searches.isEmpty()) {
			return;
		}
		long start = System.nanoTime();
		Iterator<Search> it = this.searches.values().iterator();
		Search q = it.next();
		SimSettlement parent = this.world.settlement(q.parent);
		if (parent == null) {
			it.remove();
			return;
		}
		SimConfig.Daughters d = this.world.rules().config.daughters();
		while (q.tried < MAX_TRIES && q.bestScore < GOOD && System.nanoTime() - start < budgetNanos) {
			if (!candidate(q, parent, d)) {
				// a region is being surveyed off-thread: carry on next tick
				break;
			}
		}
		if (q.tried < MAX_TRIES && q.bestScore < GOOD) {
			return;
		}
		it.remove();
		if (q.best == null) {
			this.failed++;
			return;
		}
		// the site may have been taken while the search went on
		if (this.world.whyNotFound(q.best, SimWorld.FOUND_SPACING, true) != null) {
			this.failed++;
			return;
		}
		this.world.foundDaughter(q.best, null, parent, Math.min(d.settlers(), Math.max(2, parent.population() / 8)),
			"settlers from " + parent.name() + ", which had outgrown its beds");
	}

	/** Scores one random spot in the distance band; false if it must wait for a region to be surveyed. */
	private boolean candidate(Search q, SimSettlement parent, SimConfig.Daughters d) {
		double angle = q.random.nextDouble() * Math.PI * 2;
		double dist = d.minDistance() + q.random.nextDouble() * Math.max(0, d.maxDistance() - d.minDistance());
		int x = parent.center().getX() + (int) Math.round(Math.cos(angle) * dist);
		int z = parent.center().getZ() + (int) Math.round(Math.sin(angle) * dist);
		BlockPos at = new BlockPos(x, 0, z);
		String why = this.world.whyNotFound(at, SimWorld.FOUND_SPACING, false);
		if (SimWorld.SURVEYING.equals(why)) {
			return false;
		}
		q.tried++;
		if (why != null) {
			return true;
		}
		double score = score(this.world.terrain(), x, z);
		// a little nearer home is better: the settlers walk there
		score -= 2.0 * (dist - d.minDistance()) / Math.max(1, d.maxDistance() - d.minDistance());
		if (score > q.bestScore) {
			q.bestScore = score;
			q.best = at;
		}
		return true;
	}

	/** Gentle, dry ground above the sea with water within a short walk scores well; a crag or a marsh does not. */
	static double score(TerrainProbe t, int x, int z) {
		int g = t.groundY(x, z);
		if (t.isWater(x, z) || g <= t.seaLevel()) {
			return Double.NEGATIVE_INFINITY;
		}
		int lo = g;
		int hi = g;
		for (int dx = -16; dx <= 16; dx += 16) {
			for (int dz = -16; dz <= 16; dz += 16) {
				int h = t.groundY(x + dx, z + dz);
				lo = Math.min(lo, h);
				hi = Math.max(hi, h);
			}
		}
		int relief = hi - lo;
		if (relief > 10) {
			return Double.NEGATIVE_INFINITY;
		}
		double score = 10 - relief;
		boolean water = false;
		for (int r = 40; r <= 100 && !water; r += 30) {
			for (int k = 0; k < 8 && !water; k++) {
				double a = k * Math.PI / 4;
				water = t.isWater(x + (int) (Math.cos(a) * r), z + (int) (Math.sin(a) * r));
			}
		}
		if (water) {
			score += 5;
		}
		// not up on a mountain
		score -= Math.max(0, g - t.seaLevel() - 60) / 10.0;
		return score;
	}

	String describe() {
		return String.format(Locale.ROOT, "%d site searches started, %d found nowhere, %d under way", this.started, this.failed, this.searches.size());
	}
}
