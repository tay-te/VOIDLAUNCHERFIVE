package dev.voidmc.atlas.core.landmarks;

import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.core.Coords;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One dimension's areas: the patches of every analysed region ({@link RegionFeatures}) joined across region borders
 * (the same biome, or the same kind of water, on both sides of a border) into regions of one biome, lakes and rivers,
 * each with its size and the best place for its label. Areas big enough to deserve a name keep the id they first had
 * as they grow, so their names stay: an area inherits the id of an earlier area whose label point it now covers.
 * Thread-safe.
 */
public final class AreaIndex {
	/** A region of one biome needs this many cells (of 64 square blocks) for a name: about 160 x 160 blocks. */
	public static final int REGION_MIN_CELLS = 400;
	public static final int REGION_MAJOR_CELLS = 2500;
	public static final int LAKE_MIN_CELLS = 24;
	public static final int LAKE_MAJOR_CELLS = 400;
	/** Larger than this, standing water is a sea, not a lake. */
	public static final int LAKE_MAX_CELLS = 40_000;
	public static final int RIVER_MIN_CELLS = 30;
	public static final int RIVER_MAJOR_CELLS = 300;

	/** An area worth a name. {@code kind}: {@link Place#REGION}, {@link Place#LAKE} or {@link Place#RIVER}. */
	public record Area(String id, String kind, int biome, long cells, int labelX, int labelZ, boolean major) {
		/** Its size in square blocks. */
		public long blocks() {
			return this.cells * RegionFeatures.CELL * RegionFeatures.CELL;
		}

		/** Roughly how far it reaches from its middle, in blocks. */
		public int radius() {
			return (int) Math.round(Math.sqrt(this.blocks()) / 2);
		}
	}

	private final Map<Long, RegionFeatures> regions = new HashMap<>();
	private List<Area> areas = List.of();
	/** Per region, per land patch: the index of its area in {@link #areas}, or -1. */
	private final Map<Long, int[]> landArea = new HashMap<>();
	private final Map<Long, int[]> waterArea = new HashMap<>();

	public synchronized void put(RegionFeatures f) {
		this.regions.put(Coords.pack(f.rx, f.rz), f);
	}

	public synchronized RegionFeatures get(int rx, int rz) {
		return this.regions.get(Coords.pack(rx, rz));
	}

	public synchronized int regionCount() {
		return this.regions.size();
	}

	public synchronized List<Area> areas() {
		return this.areas;
	}

	/** Every point landmark of every region. */
	public synchronized List<RegionFeatures> all() {
		return new ArrayList<>(this.regions.values());
	}

	/** The id of the named region of one biome at a block, or null. */
	public synchronized String regionAt(int x, int z) {
		return this.at(x, z, true);
	}

	/** The id of the named lake or river at a block, or null. */
	public synchronized String waterAt(int x, int z) {
		return this.at(x, z, false);
	}

	private String at(int x, int z, boolean land) {
		long key = Coords.pack(Coords.region(x), Coords.region(z));
		RegionFeatures f = this.regions.get(key);
		int[] map = (land ? this.landArea : this.waterArea).get(key);
		if (f == null || map == null) {
			return null;
		}
		int cell = (Coords.inRegion(z) / RegionFeatures.CELL) * RegionFeatures.CELLS + Coords.inRegion(x) / RegionFeatures.CELL;
		int patch = land ? f.landAt(cell) : f.waterAt(cell);
		if (patch == RegionFeatures.NONE || patch >= map.length || map[patch] < 0) {
			return null;
		}
		return this.areas.get(map[patch]).id();
	}

	/** Joins the patches into areas again (after regions were added or analysed again). */
	public synchronized List<Area> rebuild() {
		List<Long> keys = new ArrayList<>(this.regions.keySet());
		Map<Long, Integer> landBase = new HashMap<>();
		Map<Long, Integer> waterBase = new HashMap<>();
		int nodes = 0;
		for (long k : keys) {
			landBase.put(k, nodes);
			nodes += this.regions.get(k).land.size();
		}
		int waterStart = nodes;
		for (long k : keys) {
			waterBase.put(k, nodes);
			nodes += this.regions.get(k).water.size();
		}
		int[] parent = new int[nodes];
		for (int i = 0; i < nodes; i++) {
			parent[i] = i;
		}
		int n = RegionFeatures.CELLS;
		for (long k : keys) {
			RegionFeatures a = this.regions.get(k);
			int rx = Coords.unpackX(k);
			int rz = Coords.unpackZ(k);
			for (int side = 0; side < 2; side++) {
				long nk = side == 0 ? Coords.pack(rx + 1, rz) : Coords.pack(rx, rz + 1);
				RegionFeatures b = this.regions.get(nk);
				if (b == null) {
					continue;
				}
				for (int i = 0; i < n; i++) {
					int ca = side == 0 ? i * n + n - 1 : (n - 1) * n + i;
					int cb = side == 0 ? i * n : i;
					int pa = a.landAt(ca);
					int pb = b.landAt(cb);
					if (pa != RegionFeatures.NONE && pb != RegionFeatures.NONE && a.land.get(pa).biome() == b.land.get(pb).biome()) {
						union(parent, landBase.get(k) + pa, landBase.get(nk) + pb);
					}
					int wa = a.waterAt(ca);
					int wb = b.waterAt(cb);
					if (wa != RegionFeatures.NONE && wb != RegionFeatures.NONE && a.water.get(wa).kind() == b.water.get(wb).kind()) {
						union(parent, waterBase.get(k) + wa, waterBase.get(nk) + wb);
					}
				}
			}
		}
		// gather each root's size and its best label
		Map<Integer, long[]> sums = new HashMap<>(); // cells, best depth, label x, label z, biome, kind, best patch cells
		for (long k : keys) {
			RegionFeatures f = this.regions.get(k);
			int rx = Coords.unpackX(k);
			int rz = Coords.unpackZ(k);
			for (int water = 0; water < 2; water++) {
				List<RegionFeatures.Patch> patches = water == 0 ? f.land : f.water;
				int base = (water == 0 ? landBase : waterBase).get(k);
				for (int p = 0; p < patches.size(); p++) {
					RegionFeatures.Patch patch = patches.get(p);
					int root = find(parent, base + p);
					long[] s = sums.computeIfAbsent(root, r -> new long[]{0, -1, 0, 0, 0, 0, 0});
					s[0] += patch.cells();
					if (patch.labelDepth() > s[1] || patch.labelDepth() == s[1] && patch.cells() > s[6]) {
						s[1] = patch.labelDepth();
						s[2] = patch.labelX(rx);
						s[3] = patch.labelZ(rz);
						s[4] = patch.biome();
						s[5] = patch.kind().ordinal();
						s[6] = patch.cells();
					}
				}
			}
		}
		// the areas worth a name, biggest first
		List<Integer> roots = new ArrayList<>(sums.keySet());
		roots.sort((a, b) -> Long.compare(sums.get(b)[0], sums.get(a)[0]));
		List<long[]> chosen = new ArrayList<>();
		Map<Integer, Integer> rootToArea = new HashMap<>();
		for (int root : roots) {
			long[] s = sums.get(root);
			RegionFeatures.PatchKind kind = RegionFeatures.PatchKind.values()[(int) s[5]];
			long cells = s[0];
			boolean named = switch (kind) {
				case BIOME -> cells >= REGION_MIN_CELLS;
				case LAKE -> cells >= LAKE_MIN_CELLS && cells <= LAKE_MAX_CELLS;
				case RIVER -> cells >= RIVER_MIN_CELLS;
			};
			if (named) {
				rootToArea.put(root, chosen.size());
				chosen.add(s);
			}
		}
		// membership, for inheriting ids and for "which area is this block in"
		this.landArea.clear();
		this.waterArea.clear();
		for (long k : keys) {
			RegionFeatures f = this.regions.get(k);
			int[] la = new int[f.land.size()];
			for (int p = 0; p < la.length; p++) {
				la[p] = rootToArea.getOrDefault(find(parent, landBase.get(k) + p), -1);
			}
			int[] wa = new int[f.water.size()];
			for (int p = 0; p < wa.length; p++) {
				wa[p] = rootToArea.getOrDefault(find(parent, waterBase.get(k) + p), -1);
			}
			this.landArea.put(k, la);
			this.waterArea.put(k, wa);
		}
		// ids: an area keeps the id of an earlier one whose label point it covers (the biggest such), so names stay
		List<Area> before = this.areas;
		List<Area> next = new ArrayList<>(chosen.size());
		Set<String> claimed = new HashSet<>();
		String[] ids = new String[chosen.size()];
		for (Area old : before) {
			int index = this.areaIndexAt(old.labelX(), old.labelZ(), old.kind().equals(Place.REGION), keys.isEmpty());
			if (index < 0 || ids[index] != null || claimed.contains(old.id())) {
				continue;
			}
			RegionFeatures.PatchKind kind = RegionFeatures.PatchKind.values()[(int) chosen.get(index)[5]];
			if (!kindName(kind).equals(old.kind())) {
				continue;
			}
			ids[index] = old.id();
			claimed.add(old.id());
		}
		for (int i = 0; i < chosen.size(); i++) {
			long[] s = chosen.get(i);
			RegionFeatures.PatchKind kind = RegionFeatures.PatchKind.values()[(int) s[5]];
			String kindName = kindName(kind);
			String id = ids[i] != null ? ids[i] : kindName + "@" + s[2] + "," + s[3];
			boolean major = switch (kind) {
				case BIOME -> s[0] >= REGION_MAJOR_CELLS;
				case LAKE -> s[0] >= LAKE_MAJOR_CELLS;
				case RIVER -> s[0] >= RIVER_MAJOR_CELLS;
			};
			next.add(new Area(id, kindName, (int) s[4], s[0], (int) s[2], (int) s[3], major));
		}
		this.areas = List.copyOf(next);
		return this.areas;
	}

	/** The index (in the areas being built) of the area at a block, from the fresh membership. */
	private int areaIndexAt(int x, int z, boolean land, boolean none) {
		if (none) {
			return -1;
		}
		long key = Coords.pack(Coords.region(x), Coords.region(z));
		RegionFeatures f = this.regions.get(key);
		int[] map = (land ? this.landArea : this.waterArea).get(key);
		if (f == null || map == null) {
			return -1;
		}
		int cell = (Coords.inRegion(z) / RegionFeatures.CELL) * RegionFeatures.CELLS + Coords.inRegion(x) / RegionFeatures.CELL;
		int patch = land ? f.landAt(cell) : f.waterAt(cell);
		return patch == RegionFeatures.NONE || patch >= map.length ? -1 : map[patch];
	}

	/** Seeds the ids areas had last session (so a name survives a restart even before the land is analysed again). */
	public synchronized void remember(List<Area> previous) {
		this.areas = List.copyOf(previous);
	}

	private static String kindName(RegionFeatures.PatchKind kind) {
		return switch (kind) {
			case BIOME -> Place.REGION;
			case LAKE -> Place.LAKE;
			case RIVER -> Place.RIVER;
		};
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private static void union(int[] parent, int a, int b) {
		int ra = find(parent, a);
		int rb = find(parent, b);
		if (ra != rb) {
			parent[Math.max(ra, rb)] = Math.min(ra, rb);
		}
	}
}
