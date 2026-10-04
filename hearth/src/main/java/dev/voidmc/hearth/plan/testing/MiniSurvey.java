package dev.voidmc.hearth.plan.testing;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A stand-in for the sim module's territory survey (LIVING_WORLD.md §7.1), for the planner's tests and previews: reads
 * the probe on a coarse grid round a settlement and names a resource site per cell where the land clearly gives
 * something — fertile flats by water, open pasture, woods (by climate and a coarse noise, since synthetic land has no
 * trees), exposed rock on steep ground, ore in high broken hills, clay on river banks, sand on warm shores, salt on hot
 * flats, fish in wide water. Capacities and regrowth are plausible round numbers (a fertile site feeds some fifteen
 * folk). Deterministic for a probe and centre; no chunks, no biomes needed.
 */
public final class MiniSurvey {
	private static final int CELL = 48;

	private MiniSurvey() {
	}

	public static List<ResourceSite> survey(TerrainProbe t, BlockPos center, int radius) {
		List<ResourceSite> out = new ArrayList<>();
		int sea = t.seaLevel();
		int n = radius / CELL;
		int id = 1;
		for (int gz = -n; gz <= n; gz++) {
			for (int gx = -n; gx <= n; gx++) {
				// cells keep their place as the territory grows, so a site found once is found again
				int cx = Math.floorDiv(center.getX(), CELL) * CELL + gx * CELL + CELL / 2;
				int cz = Math.floorDiv(center.getZ(), CELL) * CELL + gz * CELL + CELL / 2;
				double d = Math.hypot(cx - center.getX(), cz - center.getZ());
				if (d > radius) {
					continue;
				}
				Cell c = read(t, cx, cz);
				ResourceSite.Kind kind = classify(t, c, sea, cx, cz, d);
				if (kind == null) {
					continue;
				}
				out.add(site(id, kind, c, cx, cz, t.seed()));
				id = out.size() + 1;
			}
		}
		// ids in a stable order: by distance from the heart, then position
		out.sort((a, b) -> {
			double da = Math.hypot(a.center().getX() - center.getX(), a.center().getZ() - center.getZ());
			double db = Math.hypot(b.center().getX() - center.getX(), b.center().getZ() - center.getZ());
			return da != db ? Double.compare(da, db) : Long.compare(a.center().asLong(), b.center().asLong());
		});
		List<ResourceSite> renumbered = new ArrayList<>();
		for (int i = 0; i < out.size(); i++) {
			ResourceSite s = out.get(i);
			renumbered.add(new ResourceSite(i + 1, s.kind(), s.center(), s.area(), s.capacity(), s.remaining(), s.regenPerDay(), s.yields(), s.speciality(),
				PlotView.NONE));
		}
		return renumbered;
	}

	/** What a cell's samples say: ground range, water share, temperature. */
	private record Cell(int lo, int hi, double mean, double wet, float temperature, int lowX, int lowZ, int highX, int highZ) {
		int relief() {
			return this.hi - this.lo;
		}
	}

	private static Cell read(TerrainProbe t, int cx, int cz) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		double sum = 0;
		int wet = 0;
		int count = 0;
		int lowX = cx;
		int lowZ = cz;
		int highX = cx;
		int highZ = cz;
		for (int dz = -20; dz <= 20; dz += 8) {
			for (int dx = -20; dx <= 20; dx += 8) {
				int x = cx + dx;
				int z = cz + dz;
				count++;
				if (t.waterY(x, z) != TerrainProbe.NO_WATER) {
					wet++;
					continue;
				}
				int g = t.groundY(x, z);
				sum += g;
				if (g < lo) {
					lo = g;
					lowX = x;
					lowZ = z;
				}
				if (g > hi) {
					hi = g;
					highX = x;
					highZ = z;
				}
			}
		}
		int dry = count - wet;
		if (dry == 0) {
			lo = hi = t.groundY(cx, cz);
		}
		return new Cell(lo, hi, dry == 0 ? lo : sum / dry, wet / (double) count, t.temperature(cx, cz), lowX, lowZ, highX, highZ);
	}

	private static ResourceSite.Kind classify(TerrainProbe t, Cell c, int sea, int cx, int cz, double d) {
		double h = hash(t.seed(), cx, cz, 1);
		float temp = c.temperature;
		if (c.wet >= 0.65) {
			return d > 30 ? ResourceSite.Kind.FISH : null;
		}
		if (d < 40) {
			// the town stands here
			return null;
		}
		if (c.wet >= 0.12) {
			if (temp > 1.4F && c.mean < sea + 4) {
				return ResourceSite.Kind.SALT;
			}
			if (temp > 0.7F && c.mean < sea + 4 && h < 0.5) {
				return ResourceSite.Kind.SAND;
			}
			return c.mean < sea + 10 ? ResourceSite.Kind.CLAY : ResourceSite.Kind.FERTILE;
		}
		int relief = c.relief();
		if (relief >= 16 && c.hi > sea + 18 && h < 0.6) {
			return ResourceSite.Kind.ORE;
		}
		if (relief >= 10) {
			return ResourceSite.Kind.STONE;
		}
		double forest = forest(t.seed(), cx, cz);
		if (temp > 0.15F && temp < 1.6F && forest > 0.62) {
			return ResourceSite.Kind.WOODS;
		}
		// the best of the open land only: a survey names fields worth working, not every meadow
		if (relief <= 5 && temp > 0.25F && temp < 1.3F) {
			return h < 0.45 ? ResourceSite.Kind.FERTILE : h < 0.65 ? ResourceSite.Kind.PASTURE : null;
		}
		if (relief <= 9 && h < 0.3) {
			return ResourceSite.Kind.PASTURE;
		}
		return null;
	}

	private static ResourceSite site(int id, ResourceSite.Kind kind, Cell c, int cx, int cz, long seed) {
		float[] y = new float[Goods.VALUES.length];
		int capacity;
		float regen;
		double h = hash(seed, cx, cz, 2);
		int x = cx;
		int z = cz;
		switch (kind) {
			case FERTILE -> {
				capacity = 2400;
				regen = (float) (26 + 18 * h);
				y[Goods.GRAIN.ordinal()] = 18;
				y[Goods.PRODUCE.ordinal()] = 6;
			}
			case PASTURE -> {
				capacity = 1600;
				regen = (float) (12 + 10 * h);
				y[Goods.MEAT.ordinal()] = 8;
				y[Goods.WOOL.ordinal()] = 6;
			}
			case FISH -> {
				capacity = 1800;
				regen = (float) (16 + 14 * h);
				y[Goods.FISH.ordinal()] = 14;
			}
			case WOODS -> {
				capacity = 6000;
				regen = 30;
				y[Goods.LOGS.ordinal()] = 40;
			}
			case STONE -> {
				capacity = 30000;
				regen = 0;
				y[Goods.STONE.ordinal()] = 60;
				x = c.highX;
				z = c.highZ;
			}
			case ORE -> {
				capacity = 8000;
				regen = 0;
				y[Goods.ORE.ordinal()] = 12;
				y[Goods.STONE.ordinal()] = 30;
				x = (c.lowX + c.highX) / 2;
				z = (c.lowZ + c.highZ) / 2;
			}
			case CLAY -> {
				capacity = 4000;
				regen = 0;
				y[Goods.CLAY.ordinal()] = 30;
				x = c.lowX;
				z = c.lowZ;
			}
			case SAND -> {
				capacity = 5000;
				regen = 0;
				y[Goods.SAND.ordinal()] = 40;
				x = c.lowX;
				z = c.lowZ;
			}
			case SALT -> {
				capacity = 3000;
				regen = 4;
				y[Goods.SALT.ordinal()] = 10;
			}
			default -> {
				capacity = 1000;
				regen = 0;
				y[Goods.SPECIALTY.ordinal()] = 2;
			}
		}
		BoundingBox area = new BoundingBox(cx - CELL / 2, c.lo, cz - CELL / 2, cx + CELL / 2 - 1, c.hi, cz + CELL / 2 - 1);
		return new ResourceSite(id, kind, new BlockPos(x, c.lo, z), area, capacity, capacity, regen, y, null, PlotView.NONE);
	}

	/** A coarse value noise in [0, 1): woods come in patches a few cells across. */
	private static double forest(long seed, int x, int z) {
		double fx = x / 150.0;
		double fz = z / 150.0;
		int ix = (int) Math.floor(fx);
		int iz = (int) Math.floor(fz);
		double tx = fx - ix;
		double tz = fz - iz;
		tx = tx * tx * (3 - 2 * tx);
		tz = tz * tz * (3 - 2 * tz);
		double a = hash(seed, ix, iz, 7);
		double b = hash(seed, ix + 1, iz, 7);
		double c = hash(seed, ix, iz + 1, 7);
		double d = hash(seed, ix + 1, iz + 1, 7);
		return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
	}

	private static double hash(long seed, int x, int z, int salt) {
		long h = seed * 0x9E3779B97F4A7C15L ^ x * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 32;
		return (h >>> 11) * 0x1.0p-53;
	}
}
