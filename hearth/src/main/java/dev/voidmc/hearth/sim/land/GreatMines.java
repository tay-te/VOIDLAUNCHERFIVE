package dev.voidmc.hearth.sim.land;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.sim.chains.SimData;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The Great Mine reopened (docs/LIVING_WORLD_2.md §6.1–6.2): Expanse's great mines — a shaft sunk from a pithead high on a
 * mountain, its levels every thirty blocks or so, abandoned — are found by the survey of a town whose territory reaches
 * one, as an ORE site "the Great Mine" (rich and deep, its galleries opening off the old levels). The square round the
 * shaft is claimed ({@code sim/world/Claims}) for the living pithead the planner builds there, so Expanse's abandoned one
 * stands down; where history will reach a mine (an atlas site within the largest territory of it), the claim stands from
 * the start, so Expanse never writes the abandoned pithead there.
 *
 * <p>The mines themselves come from a {@link Source}: Expanse's cavern model through {@code sim/terrain/ExpanseGreatMines}
 * when the land is an Expanse Earth world (its underground probe is a {@link Provider}), none anywhere else. Everything
 * here is a function of the seed: the same before and after the chunks are generated, safe from any thread.
 */
public final class GreatMines {
	/** The living pithead's archetype (the build module draws it, the planner sites it on the shaft). */
	public static final Identifier PITHEAD = Hearth.id("extract/great_pithead");
	/**
	 * The Great Mine site's material: it marks the site (never merged with an ore hill beside it, never taken by an
	 * ordinary mine) and names it in the books ("the great mine"); the pithead takes its rock from the land there.
	 */
	public static final Identifier MATERIAL = Hearth.id("great_mine");
	/** The deposit, in ore-site units: the old workings run far into the mountain (an ordinary ore hill holds a few thousand). */
	static final double CAPACITY = 60_000;
	/** How rich the old workings are against an ordinary ore hill (yields scale with it). */
	static final float RICHNESS = 1.5F;
	/** The square (± blocks of the shaft's centre) claimed for the pithead, unless the config says otherwise. */
	public static final int CLAIM = 12;

	/**
	 * A great mine.
	 *
	 * @param pithead     the centre of the shaft at the deck's level (one above the ground there)
	 * @param shaftBottom the lowest open block of the shaft
	 * @param levels      the floors of the levels off the shaft, highest first
	 */
	public record Mine(BlockPos pithead, int shaftBottom, List<Integer> levels) {
		/** Whether (x, z) lies in the square claimed round the shaft. */
		public boolean claims(int x, int z, int claim) {
			return Math.abs(x - this.pithead.getX()) <= claim && Math.abs(z - this.pithead.getZ()) <= claim;
		}
	}

	/** Where the great mines are. Thread-safe and cheap (a lookup of the seed's plan). */
	@FunctionalInterface
	public interface Source {
		/** The mines whose shafts lie within {@code radius} (square) of (x, z), nearest first. */
		List<Mine> near(int x, int z, int radius);

		Source NONE = (x, z, r) -> List.of();
	}

	/** An underground probe that also knows the great mines (Expanse's, through its cavern model). */
	public interface Provider {
		Source greatMines();
	}

	private static volatile boolean enabled = true;

	private GreatMines() {
	}

	/** Switched by the field work config ({@code mines.great_mines}). */
	public static void setEnabled(boolean on) {
		enabled = on;
	}

	public static boolean enabled() {
		return enabled;
	}

	// ---------------------------------------------------------------- the claim on the pithead's square

	/** Mines (by shaft column) a live town has found in its land: claimed whatever history says. */
	private static final java.util.Set<Long> REOPENED = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** Whether history reaches a mine, by world and shaft column, once worked out. */
	private static final java.util.Map<Long, Boolean> REACHED = new java.util.concurrent.ConcurrentHashMap<>();
	private static volatile int claim = CLAIM;

	/** Registers the Great Mine's claim with {@code sim/world/Claims} (from the field work's init). */
	public static void registerClaims(int square) {
		claim = square;
		dev.voidmc.hearth.sim.world.Claims.register(GreatMines::claimed);
	}

	/** A live town found a mine in its land (its survey has the site): its square is claimed from now on. */
	public static void reopened(BlockPos pithead) {
		REOPENED.add(BlockPos.asLong(pithead.getX(), 0, pithead.getZ()));
	}

	/** Forgets what was worked out (the server stopped). */
	public static void clearClaims() {
		REOPENED.clear();
		REACHED.clear();
	}

	/**
	 * Whether (x, z) lies in the square of a Great Mine that a town reopens or will: one a live town has found, or one
	 * within the largest territory of an atlas site (history). Cheap off the squares (one cell lookup); a mine's answer is
	 * worked out once. On the server thread a region whose plan is not ready counts as planned, as
	 * {@link dev.voidmc.hearth.sim.world.SimWorld#isPlannedNow} does.
	 */
	static boolean claimed(dev.voidmc.hearth.sim.world.SimWorld world, int x, int z, boolean now) {
		if (!enabled) {
			return false;
		}
		Source source = of(world.underground());
		if (source == Source.NONE) {
			return false;
		}
		int sq = claim;
		for (Mine m : source.near(x, z, sq)) {
			if (!m.claims(x, z, sq)) {
				continue;
			}
			long key = BlockPos.asLong(m.pithead().getX(), 0, m.pithead().getZ());
			if (REOPENED.contains(key)) {
				return true;
			}
			long wkey = key * 31 + System.identityHashCode(world);
			Boolean known = REACHED.get(wkey);
			if (known != null) {
				return known;
			}
			Boolean reached = historyReaches(world, m, now);
			if (reached == null) {
				// not known yet on the server thread: planned until it is
				return true;
			}
			REACHED.put(wkey, reached);
			if (reached) {
				return true;
			}
		}
		return false;
	}

	/** Whether an atlas site lies within the largest territory of a mine; null when a plan is not ready ({@code now}). */
	private static @Nullable Boolean historyReaches(dev.voidmc.hearth.sim.world.SimWorld world, Mine m, boolean now) {
		dev.voidmc.hearth.sim.atlas.Atlas atlas = world.atlas();
		if (!atlas.enabled()) {
			return false;
		}
		int reach = 0;
		for (int r : world.rules().config.resources().land().territory().values()) {
			reach = Math.max(reach, r);
		}
		BlockPos p = m.pithead();
		boolean unknown = false;
		List<BlockPos> sites = new java.util.ArrayList<>();
		for (int rx = dev.voidmc.hearth.sim.atlas.Atlas.region(p.getX() - reach); rx <= dev.voidmc.hearth.sim.atlas.Atlas.region(p.getX() + reach); rx++) {
			for (int rz = dev.voidmc.hearth.sim.atlas.Atlas.region(p.getZ() - reach); rz <= dev.voidmc.hearth.sim.atlas.Atlas.region(p.getZ() + reach); rz++) {
				dev.voidmc.hearth.sim.atlas.RegionPlan plan = now ? atlas.planIfReady(rx, rz) : atlas.plan(rx, rz);
				if (plan == null) {
					atlas.planAsync(rx, rz);
					unknown = true;
					continue;
				}
				dev.voidmc.hearth.sim.atlas.RegionPlan.Site site = plan.site();
				if (site != null) {
					sites.add(site.center());
				}
			}
		}
		if (reaches(sites, m, reach)) {
			return true;
		}
		return unknown ? null : false;
	}

	/** Whether any of these settlement sites' largest territory ({@code reach}) takes in a mine's shaft. Pure. */
	public static boolean reaches(List<BlockPos> sites, Mine m, int reach) {
		long r2 = (long) reach * reach;
		for (BlockPos c : sites) {
			long dx = c.getX() - m.pithead().getX();
			long dz = c.getZ() - m.pithead().getZ();
			if (dx * dx + dz * dz <= r2) {
				return true;
			}
		}
		return false;
	}

	/** The mine whose claimed square takes in (x, z), or null. Cheap: the source's lookup of the column's cell. */
	public static @Nullable Mine claiming(Source source, int x, int z, int square) {
		for (Mine m : source.near(x, z, square)) {
			if (m.claims(x, z, square)) {
				return m;
			}
		}
		return null;
	}

	/** The mines of a land's underground, or none. */
	public static Source of(@Nullable UndergroundProbe underground) {
		return enabled && underground instanceof Provider p ? p.greatMines() : Source.NONE;
	}

	/** Whether a site is a Great Mine's. */
	public static boolean isGreat(ResourceSite site) {
		return site.kind() == ResourceSite.Kind.ORE && MATERIAL.equals(site.material());
	}

	/** The mine whose shaft a site stands on (a Great Mine site's centre is its shaft), or null. */
	public static @Nullable Mine at(Source source, ResourceSite site) {
		if (!isGreat(site)) {
			return null;
		}
		for (Mine m : source.near(site.center().getX(), site.center().getZ(), 2)) {
			return m;
		}
		return null;
	}

	/**
	 * The survey's one call (docs/LIVING_WORLD_2.md §11.3): a Great Mine within the territory adds its ORE site to what was
	 * found, after the per-kind cuts so it is never lost to them.
	 */
	public static void survey(Land land, BlockPos heart, int radius, SimData data, List<Survey.Found> out, BiPredicate<Integer, Integer> excluded) {
		// (a history's land carries no underground of its own; its terrain probe does)
		Source source = of(land.underground() != null ? land.underground() : land.terrain().underground());
		if (source == Source.NONE) {
			return;
		}
		long r2 = (long) radius * radius;
		for (Mine m : source.near(heart.getX(), heart.getZ(), radius)) {
			long dx = m.pithead().getX() - heart.getX();
			long dz = m.pithead().getZ() - heart.getZ();
			if (dx * dx + dz * dz > r2 || excluded.test(m.pithead().getX(), m.pithead().getZ())) {
				continue;
			}
			out.add(found(land, m, data));
		}
	}

	/** The Great Mine as a survey finds it: an ORE site on the shaft, rich, reaching down to the shaft's foot. */
	public static Survey.Found found(Land land, Mine m, SimData data) {
		BlockPos p = m.pithead();
		BiomeLand ground = land.at(p.getX(), p.getZ());
		int depth = Math.max(16, p.getY() - m.shaftBottom());
		OreModel.Estimate e = OreModel.estimate(p.getY(), Math.min(96, depth), ground.ore(), true);
		float[] yields = new float[Goods.VALUES.length];
		SimData.Gathering miner = data.gathering(JobType.MINER);
		SimData.Gathering.Entry me = miner == null ? null : miner.entry(ResourceSite.Kind.ORE);
		if (me != null) {
			for (int g = 0; g < yields.length; g++) {
				yields[g] = (float) (me.yields()[g] * RICHNESS);
			}
		}
		BoundingBox area = new BoundingBox(p.getX() - CLAIM, m.shaftBottom() - 4, p.getZ() - CLAIM, p.getX() + CLAIM, p.getY() + 24, p.getZ() + CLAIM);
		return new Survey.Found(ResourceSite.Kind.ORE, p, area, 24, RICHNESS, CAPACITY, 0, yields, MATERIAL, Math.max(e.ore(), 0.02F),
			Math.max(e.fuel(), 0.01F));
	}
}
