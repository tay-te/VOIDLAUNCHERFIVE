package dev.voidmc.hearth.sim.land;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.sim.chains.BiomeTable;
import dev.voidmc.hearth.sim.chains.SimData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Surveys a settlement's territory into resource sites (docs/LIVING_WORLD.md §7.1): a grid of columns over the disc of
 * the territory, each read from the terrain probe and its biome's land, classified into the kinds of site it could be
 * (a wooded slope is woods and stone at once), then joined into connected patches of each kind (and of each material:
 * a birch wood and an oak wood are two sites) of at most a few cells, the richest and nearest kept.
 *
 * <p>A pure function of the land, the heart, the radius and the data: no chunk is loaded, nothing is random, and the same
 * inputs give the same sites, so a history surveyed on a world generation worker and a live town surveyed again agree.
 * Costs one probe column per cell (at most {@code cellsAcross}², about a thousand), plus the caverns under it.
 */
public final class Survey {
	/** One site found. */
	public record Found(ResourceSite.Kind kind, BlockPos center, BoundingBox area, int cells, float density, double capacity, double regen,
		float[] yields, Identifier material, float ore, float fuel) {
	}

	/**
	 * @param cellsAcross the grid is at most this many cells across the territory's diameter
	 * @param minStep     and its cells at least this many blocks apart
	 * @param maxSites    sites kept in all
	 */
	public record Params(int cellsAcross, int minStep, int maxSites) {
		public static final Params DEFAULTS = new Params(36, 24, 48);
	}

	private static final ResourceSite.Kind[] KINDS = ResourceSite.Kind.values();

	private Survey() {
	}

	/** The grid spacing for a radius: as fine as {@code minStep}, as coarse as fits {@code cellsAcross}, in steps of 8. */
	public static int step(int radius, Params p) {
		int s = Math.max(p.minStep, (int) Math.ceil(2.0 * radius / Math.max(4, p.cellsAcross)));
		return (s + 7) / 8 * 8;
	}

	/**
	 * @param excluded columns that are not this settlement's to work (another settlement's heart round about)
	 */
	public static List<Found> run(Land land, BlockPos heart, int radius, SimData data, Params p, BiPredicate<Integer, Integer> excluded) {
		TerrainProbe t = land.terrain();
		int step = step(radius, p);
		int half = (int) Math.ceil(radius / (double) step);
		int n = 2 * half + 1;
		int x0 = heart.getX() - half * step;
		int z0 = heart.getZ() - half * step;
		int sea = t.seaLevel();
		int cells = n * n;
		int[] h = new int[cells];
		int[] w = new int[cells];
		boolean[] in = new boolean[cells];
		BiomeLand[] b = new BiomeLand[cells];
		long r2 = (long) radius * radius;
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int c = j * n + i;
				int x = x0 + i * step;
				int z = z0 + j * step;
				h[c] = t.groundY(x, z);
				w[c] = t.waterY(x, z);
				long dx = x - heart.getX();
				long dz = z - heart.getZ();
				in[c] = dx * dx + dz * dz <= r2 && !excluded.test(x, z);
				b[c] = in[c] ? land.at(x, z) : null;
			}
		}
		UndergroundProbe under = land.underground();
		// density of each kind in each cell (0: not that kind), and the cell's material for it
		float[][] dens = new float[KINDS.length][cells];
		Identifier[][] mat = new Identifier[KINDS.length][cells];
		float[] ore = new float[cells];
		float[] fuel = new float[cells];
		Identifier[] oreMain = new Identifier[cells];
		Map<Identifier, float[]> special = new LinkedHashMap<>();
		Map<Identifier, BiomeTable.Specialty> specialOf = new LinkedHashMap<>();
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int c = j * n + i;
				if (!in[c]) {
					continue;
				}
				BiomeLand land0 = b[c];
				int g = h[c];
				boolean wet = w[c] != TerrainProbe.NO_WATER && w[c] - g >= 1;
				int depth = wet ? w[c] - g : 0;
				double slope = 0;
				int low = g;
				boolean nearWater = false;
				boolean nearLand = false;
				for (int dj = -2; dj <= 2; dj++) {
					for (int di = -2; di <= 2; di++) {
						int ii = Math.max(0, Math.min(n - 1, i + di));
						int jj = Math.max(0, Math.min(n - 1, j + dj));
						int o = jj * n + ii;
						low = Math.min(low, h[o]);
						if (Math.abs(di) + Math.abs(dj) == 1) {
							slope = Math.max(slope, Math.abs(h[o] - g) / (double) step);
							boolean ow = w[o] != TerrainProbe.NO_WATER && w[o] - h[o] >= 1;
							nearWater |= ow;
							nearLand |= !ow;
						}
					}
				}
				int relief = g - low;
				int x = x0 + i * step;
				int z = z0 + j * step;
				if (wet) {
					if (depth >= 2) {
						set(dens, mat, ResourceSite.Kind.FISH, c, clamp(depth / 8F, 0.3F, 1F), null);
					}
					if (depth <= 2 && nearLand) {
						if (land0.sandy() || land0.water() && !land0.clay().equals(BiomeLand.CLAY)) {
							set(dens, mat, ResourceSite.Kind.SAND, c, 0.7F, land0.sand());
						} else if (!land0.snowy()) {
							set(dens, mat, ResourceSite.Kind.CLAY, c, 0.6F, land0.clay());
						}
					}
				} else {
					if (land0.sandy() && slope < 0.3) {
						set(dens, mat, ResourceSite.Kind.SAND, c, 0.6F, land0.sand());
					}
					if (nearWater && g <= sea + 6 && !land0.sandy() && !land0.snowy() && slope < 0.25) {
						set(dens, mat, ResourceSite.Kind.CLAY, c, 0.6F, land0.clay());
					}
					if (slope < 0.22 && land0.fertility() > 0.15F && !land0.snowy() && g > sea) {
						// level land is best; gentle slopes are farmed in terraces, for less
						float d = land0.fertility() * (float) (1 - slope * 3) * (nearWater ? 1F : 0.8F) * (g > sea + 70 ? 0.6F : 1F);
						set(dens, mat, ResourceSite.Kind.FERTILE, c, d, null);
					}
					if (slope < 0.35 && land0.pasture() > 0.15F) {
						set(dens, mat, ResourceSite.Kind.PASTURE, c, land0.pasture() * (float) (1 - slope * 2), null);
					}
					if (land0.cover() >= 0.12F) {
						set(dens, mat, ResourceSite.Kind.WOODS, c, Math.min(1F, land0.cover()), land0.wood());
					}
					if (slope >= 0.45 || land0.rocky() && slope >= 0.15 || g >= sea + 100) {
						float d = clamp(0.4F + (float) slope * 0.6F + (land0.rocky() ? 0.2F : 0), 0.4F, 1F);
						set(dens, mat, ResourceSite.Kind.STONE, c, d, land0.rock());
					}
					boolean caverns = under != null && cavernsNear(under, x, z, g);
					if (relief >= 16 && (slope >= 0.25 || land0.rocky()) || caverns && (relief >= 6 || land0.rocky())) {
						OreModel.Estimate e = OreModel.estimate(g, Math.min(64, relief + 16), land0.ore(), caverns);
						float d = clamp((e.ore() + e.fuel()) / 0.02F, 0.3F, 1.5F);
						set(dens, mat, ResourceSite.Kind.ORE, c, d, land0.rock());
						ore[c] = e.ore();
						fuel[c] = e.fuel();
						oreMain[c] = e.main();
					}
					if (land0.salt() || t.temperature(x, z) > 1.0F && nearWater && g <= sea + 3 && land0.sandy()) {
						set(dens, mat, ResourceSite.Kind.SALT, c, land0.salt() ? 0.9F : 0.5F, null);
					}
				}
				for (BiomeTable.Specialty sp : land0.specialties()) {
					// a rarity grows on only part of its biome's ground, in clumps: thinned by a hash of 96-block cells, the same every survey
					if (unit(Math.floorDiv(x, 96), Math.floorDiv(z, 96), sp.item().hashCode()) < sp.density()) {
						special.computeIfAbsent(sp.item(), k -> new float[cells])[c] = 1;
						specialOf.putIfAbsent(sp.item(), sp);
					}
				}
			}
		}

		double cellArea = step * (double) step / 1024.0;
		Map<ResourceSite.Kind, List<Found>> byKind = new EnumMap<>(ResourceSite.Kind.class);
		for (ResourceSite.Kind kind : KINDS) {
			if (kind == ResourceSite.Kind.SPECIALTY) {
				continue;
			}
			SimData.SiteKind table = data.site(kind);
			for (IntArrayList patch : patches(dens[kind.ordinal()], mat[kind.ordinal()], n, table.maxCells())) {
				byKind.computeIfAbsent(kind, k -> new ArrayList<>()).add(found(kind, patch, dens[kind.ordinal()], mat[kind.ordinal()], table, data,
					null, n, x0, z0, step, h, w, ore, fuel, oreMain, cellArea));
			}
		}
		SimData.SiteKind specialTable = data.site(ResourceSite.Kind.SPECIALTY);
		for (Map.Entry<Identifier, float[]> e : special.entrySet()) {
			BiomeTable.Specialty sp = specialOf.get(e.getKey());
			Identifier[] m = new Identifier[cells];
			for (IntArrayList patch : patches(e.getValue(), m, n, specialTable.maxCells())) {
				byKind.computeIfAbsent(ResourceSite.Kind.SPECIALTY, k -> new ArrayList<>()).add(found(ResourceSite.Kind.SPECIALTY, patch, e.getValue(), m,
					specialTable, data, sp, n, x0, z0, step, h, w, ore, fuel, oreMain, cellArea));
			}
		}
		if (under != null) {
			realms(under, heart, radius, data, byKind, t);
		}
		// the richest and nearest of each kind, then the richest and nearest of all
		List<Found> out = new ArrayList<>();
		for (Map.Entry<ResourceSite.Kind, List<Found>> e : byKind.entrySet()) {
			List<Found> l = e.getValue();
			l.sort(Comparator.comparingDouble((Found f) -> -score(f, heart, radius)));
			out.addAll(l.subList(0, Math.min(l.size(), data.site(e.getKey()).maxSites())));
		}
		out.sort(Comparator.comparingDouble((Found f) -> -score(f, heart, radius)));
		if (out.size() > p.maxSites) {
			// never lose a whole kind to the cap: keep the best of each first
			List<Found> keep = new ArrayList<>();
			EnumMap<ResourceSite.Kind, Boolean> have = new EnumMap<>(ResourceSite.Kind.class);
			for (Found f : out) {
				if (have.putIfAbsent(f.kind(), true) == null) {
					keep.add(f);
				}
			}
			for (Found f : out) {
				if (keep.size() >= p.maxSites) {
					break;
				}
				if (!keep.contains(f)) {
					keep.add(f);
				}
			}
			out = keep;
		}
		return out;
	}

	/** Bigger and nearer is better; a site at the territory's edge counts half. */
	private static double score(Found f, BlockPos heart, int radius) {
		double dx = f.center().getX() - heart.getX();
		double dz = f.center().getZ() - heart.getZ();
		double near = 1.5 - Math.min(1, Math.sqrt(dx * dx + dz * dz) / Math.max(1, radius));
		return Math.log1p(f.capacity() * f.density()) * near;
	}

	private static void set(float[][] dens, Identifier[][] mat, ResourceSite.Kind k, int c, float d, Identifier m) {
		if (d > 0.05F) {
			dens[k.ordinal()][c] = d;
			mat[k.ordinal()][c] = m;
		}
	}

	/** Connected patches (4-neighbours) of cells of one kind and one material, each cut into pieces of at most {@code max} cells. */
	private static List<IntArrayList> patches(float[] dens, Identifier[] mat, int n, int max) {
		List<IntArrayList> out = new ArrayList<>();
		boolean[] seen = new boolean[dens.length];
		int[] queue = new int[dens.length];
		for (int start = 0; start < dens.length; start++) {
			if (seen[start] || dens[start] <= 0) {
				continue;
			}
			Identifier m = mat[start];
			int head = 0;
			int tail = 0;
			queue[tail++] = start;
			seen[start] = true;
			IntArrayList piece = new IntArrayList();
			while (head < tail) {
				int c = queue[head++];
				piece.add(c);
				if (piece.size() >= max) {
					out.add(piece);
					piece = new IntArrayList();
				}
				int i = c % n;
				int j = c / n;
				int[] next = {i > 0 ? c - 1 : -1, i < n - 1 ? c + 1 : -1, j > 0 ? c - n : -1, j < n - 1 ? c + n : -1};
				for (int o : next) {
					if (o >= 0 && !seen[o] && dens[o] > 0 && java.util.Objects.equals(mat[o], m)) {
						seen[o] = true;
						queue[tail++] = o;
					}
				}
			}
			if (!piece.isEmpty()) {
				out.add(piece);
			}
		}
		return out;
	}

	private static Found found(ResourceSite.Kind kind, IntArrayList patch, float[] dens, Identifier[] mat, SimData.SiteKind table, SimData data,
		BiomeTable.Specialty sp, int n, int x0, int z0, int step, int[] h, int[] w, float[] ore, float[] fuel, Identifier[] oreMain, double cellArea) {
		double sx = 0;
		double sz = 0;
		double sd = 0;
		double so = 0;
		double sf = 0;
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		Map<Identifier, Integer> mains = new LinkedHashMap<>();
		Map<Identifier, Integer> mats = new LinkedHashMap<>();
		for (int k = 0; k < patch.size(); k++) {
			int c = patch.getInt(k);
			int x = x0 + c % n * step;
			int z = z0 + c / n * step;
			sx += x;
			sz += z;
			sd += dens[c];
			so += ore[c];
			sf += fuel[c];
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
			minY = Math.min(minY, h[c]);
			maxY = Math.max(maxY, Math.max(h[c], w[c] == TerrainProbe.NO_WATER ? h[c] : w[c]));
			if (oreMain[c] != null) {
				mains.merge(oreMain[c], 1, Integer::sum);
			}
			if (mat[c] != null) {
				mats.merge(mat[c], 1, Integer::sum);
			}
		}
		int count = patch.size();
		double cx = sx / count;
		double cz = sz / count;
		// the centre is the patch's own cell nearest its centroid (a crescent's centroid may lie outside it)
		int best = patch.getInt(0);
		double bestD = Double.MAX_VALUE;
		for (int k = 0; k < count; k++) {
			int c = patch.getInt(k);
			double dx = x0 + c % n * step - cx;
			double dz = z0 + c / n * step - cz;
			if (dx * dx + dz * dz < bestD) {
				bestD = dx * dx + dz * dz;
				best = c;
			}
		}
		int bx = x0 + best % n * step;
		int bz = z0 + best / n * step;
		int by = kind == ResourceSite.Kind.FISH && w[best] != TerrainProbe.NO_WATER ? w[best] : h[best];
		int hs = step / 2;
		int below = kind == ResourceSite.Kind.ORE || kind == ResourceSite.Kind.STONE ? 64 : 8;
		BoundingBox area = new BoundingBox(minX - hs, minY - below, minZ - hs, maxX + hs - 1, maxY + 24, maxZ + hs - 1);
		float density = (float) (sd / count);
		double capacity = sd * table.perCell() * cellArea;
		double regenRate = sp != null ? sp.regen() : table.regen();
		float[] yields = new float[Goods.VALUES.length];
		Identifier material;
		if (sp != null) {
			yields[sp.goods().ordinal()] = (float) (sp.yield() * density);
			material = sp.item();
		} else {
			JobType primary = table.primary();
			SimData.Gathering gather = data.gathering(primary);
			SimData.Gathering.Entry e = gather == null ? null : gather.entry(kind);
			if (e != null) {
				for (int g = 0; g < yields.length; g++) {
					yields[g] = (float) (e.yields()[g] * density);
				}
			}
			material = majority(kind == ResourceSite.Kind.ORE && !mains.isEmpty() ? mains : mats,
				table.material().orElse(null));
		}
		return new Found(kind, new BlockPos(bx, by, bz), area, count, density, capacity, capacity * regenRate, yields, material, (float) (so / count),
			(float) (sf / count));
	}

	private static Identifier majority(Map<Identifier, Integer> counts, Identifier fallback) {
		Identifier best = fallback;
		int most = 0;
		for (Map.Entry<Identifier, Integer> e : counts.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	/** Whether a realm or a great tunnel opens in the rock under a column (mines aim for them, and their walls show veins). */
	private static boolean cavernsNear(UndergroundProbe u, int x, int z, int ground) {
		UndergroundProbe.Realm r = u.realm(x, z);
		if (r != null && r.ceilingY() < ground - 8) {
			return true;
		}
		int floor = u.floorY(x, z, ground - 40);
		return floor != Integer.MIN_VALUE && floor < ground - 12 && floor > ground - 96;
	}

	/**
	 * The realms under the territory: each gives workings in its walls (ore, richer for the open rock) and its own
	 * rarities from the land table's {@code <ns>:realm_<character>} entry (amber, prismite, glowcaps...).
	 */
	private static void realms(UndergroundProbe u, BlockPos heart, int radius, SimData data, Map<ResourceSite.Kind, List<Found>> byKind,
		TerrainProbe t) {
		for (UndergroundProbe.Realm r : u.realmsNear(heart.getX(), heart.getZ(), radius)) {
			int top = t.groundY(r.center().getX(), r.center().getZ());
			BiomeTable.Entry entry = data.land().biomes().getOrDefault(Identifier.fromNamespaceAndPath("expanse", "realm_" + r.character()),
				BiomeTable.Entry.NONE);
			float[] mult = {1, 1, 1, 1};
			String[] names = {"coal", "iron", "copper", "gold"};
			for (int k = 0; k < names.length; k++) {
				mult[k] = entry.ore().getOrDefault(names[k], 1.0).floatValue();
			}
			OreModel.Estimate e = OreModel.estimate(r.ceilingY(), Math.max(16, r.ceilingY() - r.floorY()), mult, true);
			int rr = r.radius();
			BoundingBox area = new BoundingBox(r.center().getX() - rr, r.floorY() - 4, r.center().getZ() - rr, r.center().getX() + rr, top,
				r.center().getZ() + rr);
			double cellArea = Math.PI * rr * rr / 1024.0;
			SimData.SiteKind oreTable = data.site(ResourceSite.Kind.ORE);
			float d = clamp((e.ore() + e.fuel()) / 0.02F, 0.3F, 1.5F);
			float[] yields = new float[Goods.VALUES.length];
			SimData.Gathering miner = data.gathering(JobType.MINER);
			SimData.Gathering.Entry me = miner == null ? null : miner.entry(ResourceSite.Kind.ORE);
			if (me != null) {
				for (int g = 0; g < yields.length; g++) {
					yields[g] = (float) (me.yields()[g] * d);
				}
			}
			// the walls of a realm: a long way down, so a fraction of its outline counts as workable rock
			double capacity = d * oreTable.perCell() * cellArea * 0.25;
			byKind.computeIfAbsent(ResourceSite.Kind.ORE, k -> new ArrayList<>()).add(new Found(ResourceSite.Kind.ORE,
				new BlockPos(r.center().getX(), r.floorY(), r.center().getZ()), area, Math.max(1, (int) cellArea), d, capacity, 0, yields, e.main(), e.ore(),
				e.fuel()));
			SimData.SiteKind special = data.site(ResourceSite.Kind.SPECIALTY);
			for (BiomeTable.Specialty sp : entry.specialties()) {
				float[] y = new float[Goods.VALUES.length];
				y[sp.goods().ordinal()] = (float) (sp.yield() * sp.density());
				double cap = sp.density() * special.perCell() * cellArea * 0.5;
				byKind.computeIfAbsent(ResourceSite.Kind.SPECIALTY, k -> new ArrayList<>()).add(new Found(ResourceSite.Kind.SPECIALTY,
					new BlockPos(r.center().getX(), r.floorY(), r.center().getZ()), area, Math.max(1, (int) cellArea), (float) sp.density(), cap,
					cap * sp.regen(), y, sp.item(), 0, 0));
			}
		}
	}

	private static float unit(int x, int z, int salt) {
		long hh = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		hh = (hh ^ (hh >>> 31)) * 0xBF58476D1CE4E5B9L;
		hh ^= hh >>> 29;
		return (hh >>> 40) / (float) (1 << 24);
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
