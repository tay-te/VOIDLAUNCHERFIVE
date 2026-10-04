package dev.voidmc.hearth.sim.world;

import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.land.Land;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.land.Survey;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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
 * Daughter hamlets (ARCHITECTURE.md §5.1): a settlement that has run out of beds (or of land to feed its people) for days
 * sends a party of settlers out, and this finds them a site 600–2500 blocks away — dry, gentle ground, water near, clear
 * of every town live or historic — a few candidates per tick under a time budget, never computing a region's history on
 * the server thread (a region not yet surveyed is surveyed off-thread and asked about again).
 *
 * <p>Phase 3 (LIVING_WORLD.md §7.2): settlers go for what their town lacks. The parent's missing kinds of land (no
 * woods, no stone, no ore, no fishing, little farmland) are worth a lot at a candidate, read with a small survey there —
 * so a mountain town founds a farming hamlet on the plain and a plain town a mining hamlet by the ore, and the hamlet
 * ships its goods to its parent (caravans favour kin). Server thread only.
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
		/** The kinds of land the parent lacks: worth seeking. */
		final Set<ResourceSite.Kind> lacking;
		int tried;
		@Nullable BlockPos best;
		double bestScore = Double.NEGATIVE_INFINITY;
		Set<ResourceSite.Kind> bestHas = Set.of();

		Search(long parent, long seed, Set<ResourceSite.Kind> lacking) {
			this.parent = parent;
			this.random = RandomSource.create(seed);
			this.lacking = lacking;
		}
	}

	/** The kinds of land worth founding a hamlet for. */
	private static final ResourceSite.Kind[] SOUGHT = {ResourceSite.Kind.FERTILE, ResourceSite.Kind.WOODS, ResourceSite.Kind.STONE,
		ResourceSite.Kind.ORE, ResourceSite.Kind.FISH, ResourceSite.Kind.CLAY};
	/** What a lacking kind found at a candidate is worth, against its ground's score (0–15). */
	private static final double LACK_BONUS = 6;

	/** The kinds of land a settlement has none of (or none left worth working). */
	static Set<ResourceSite.Kind> lacking(SimSettlement s) {
		Set<ResourceSite.Kind> lack = EnumSet.noneOf(ResourceSite.Kind.class);
		for (ResourceSite.Kind k : SOUGHT) {
			boolean has = false;
			for (SimSite site : s.land().sites()) {
				if (site.kind() == k && (site.fill() > 0.15 || site.renewable())) {
					has = true;
					break;
				}
			}
			if (!has) {
				lack.add(k);
			}
		}
		return lack;
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
		this.searches.put(s.id(), new Search(s.id(), s.id() * 0x9E3779B97F4A7C15L ^ s.simTick(), lacking(s)));
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
		if (this.world.whyNotFound(q.best, SimWorld.FOUND_SPACING, true) != null || nearPending(q.best)) {
			this.failed++;
			return;
		}
		BlockPos site = q.best;
		this.pending.add(site);
		String why = q.bestHas.isEmpty() ? "settlers from " + parent.name() + ", which had outgrown its beds"
			: "settlers from " + parent.name() + ", gone for the " + names(q.bestHas) + " it lacked";
		this.world.foundDaughterAsync(site, parent, Math.min(d.settlers(), Math.max(2, parent.population() / 8)), why, s -> this.pending.remove(site));
	}

	/** Sites being laid out off-thread: not registered yet, so whyNotFound cannot see them. */
	private final java.util.List<BlockPos> pending = new java.util.ArrayList<>();

	private boolean nearPending(BlockPos at) {
		for (BlockPos p : this.pending) {
			if (Math.abs(p.getX() - at.getX()) + Math.abs(p.getZ() - at.getZ()) < SimWorld.FOUND_SPACING * 1.4) {
				return true;
			}
		}
		return false;
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
		if (why != null || nearPending(at)) {
			return true;
		}
		double score = score(this.world.terrain(), x, z);
		// a little nearer home is better: the settlers walk there
		score -= 2.0 * (dist - d.minDistance()) / Math.max(1, d.maxDistance() - d.minDistance());
		Set<ResourceSite.Kind> has = Set.of();
		if (score > Double.NEGATIVE_INFINITY && !q.lacking.isEmpty()) {
			has = offers(x, z, q.lacking);
			score += LACK_BONUS * has.size();
		}
		if (score > q.bestScore) {
			q.bestScore = score;
			q.best = at;
			q.bestHas = has;
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

	/** Which of the wanted kinds of land a small survey finds round a candidate (nine columns on a 48-block grid). */
	private Set<ResourceSite.Kind> offers(int x, int z, Set<ResourceSite.Kind> wanted) {
		Set<ResourceSite.Kind> out = EnumSet.noneOf(ResourceSite.Kind.class);
		Land land = Land.of(this.world.terrain(), this.world.underground());
		List<Survey.Found> found = Survey.run(land, new BlockPos(x, 0, z), 48, this.world.data(), PROBE, (a, b) -> false);
		for (Survey.Found f : found) {
			if (wanted.contains(f.kind())) {
				out.add(f.kind());
			}
		}
		return out;
	}

	/** A 3 × 3 grid of columns 48 apart: about what the ground score above already costs. */
	private static final Survey.Params PROBE = new Survey.Params(2, 48, 16);

	private static String names(Set<ResourceSite.Kind> kinds) {
		List<String> n = new java.util.ArrayList<>();
		for (ResourceSite.Kind k : kinds) {
			n.add(switch (k) {
				case FERTILE -> "farmland";
				case WOODS -> "timber";
				case STONE -> "stone";
				case ORE -> "ore";
				case FISH -> "fishing";
				case CLAY -> "clay";
				default -> k.getSerializedName();
			});
		}
		return String.join(" and ", n);
	}

	String describe() {
		return String.format(Locale.ROOT, "%d site searches started, %d found nowhere, %d under way", this.started, this.failed, this.searches.size());
	}
}
