package dev.voidmc.hearth.build.archetype.landscape;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Pavilions;
import dev.voidmc.hearth.build.kit.landscape.Planters;
import dev.voidmc.hearth.build.kit.landscape.Terraces;
import dev.voidmc.hearth.build.kit.landscape.Trees;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.kit.landscape.Edges;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code hearth:landscape/park}: a town's public park, terraced down a slope where it has to be. A gate on the street
 * opens on a broad walk up the middle (steps where it climbs a terrace wall), cross walks on every terrace and, on
 * level ground, a loop walk round the lawns; a pond with lilies and reeds in one quarter, the culture's trees standing
 * one or two to a lawn in the others (some lawns left open), flower borders by the gate, benches along the walks
 * looking in, lamps every few blocks; in a city, a bandstand (an open pavilion on a podium, roofed in the culture's
 * manner) where the walks meet, with benches facing it. The boundary is the culture's yard fence, hedge or wall, open
 * at the sides as well as the gate.
 *
 * <p>POIs: SEATs, a MEETING_POINT at the bandstand, a VIEWPOINT by the pond. Params: {@code bandstand} (boolean,
 * default: city tier), {@code pond} (boolean, default true where it fits).
 */
public final class Park implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/park"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		13, 25, 13, 25, 1, 12, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		d.entrance(cx, 0, -1);
		int[] level = Terraces.plan(d, 0, w - 1, 6, 3);
		Terraces.build(d, 0, w - 1, level);
		boolean[][] used = new boolean[w][dp];
		// the lawn
		Block lawn = d.mat(Role.GROUND).mainOr(Form.FULL, Blocks.GRASS_BLOCK);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					if (!d.site().isWater(x, z) && Garden.groundLike(d, x, level[z] - 1, z) || d.get(x, level[z] - 1, z) == null) {
						d.set(x, level[z] - 1, z, lawn.defaultBlockState());
					}
				}
			}
		}
		// the boundary, open at the gate and halfway along the sides
		int sideGate = dp / 2;
		List<int[]> front = Garden.line(0, 0, w - 1, 0);
		Garden.fence(d, front, cx, Dirs.FRONT);
		List<int[]> left = Garden.line(0, 1, 0, dp - 1);
		List<int[]> right = Garden.line(w - 1, 1, w - 1, dp - 1);
		List<int[]> back = Garden.line(1, dp - 1, w - 2, dp - 1);
		Garden.fence(d, left, sideGate - 1, Dirs.LEFT);
		Garden.fence(d, right, sideGate - 1, Dirs.RIGHT);
		Garden.fence(d, back, -1, Dirs.FRONT);
		for (int[] c : front) {
			used[c[0]][c[1]] = true;
		}
		for (List<int[]> l : List.of(left, right, back)) {
			for (int[] c : l) {
				used[c[0]][c[1]] = true;
			}
		}
		// the walks: a broad one up the middle, a cross walk on each terrace, a loop on level ground
		int half = w >= 17 ? 1 : 0;
		Mat paving = Manner.wood(d.mat(Role.PAVING)) ? d.mat(Role.PATH) : d.mat(Role.PAVING);
		Mat edge = Manner.masonry(d);
		for (int z = 0; z < dp - 1; z++) {
			for (int x = cx - half; x <= cx + half; x++) {
				walk(d, x, level[z], z, half > 0 && x != cx ? edge : paving);
				used[x][z] = true;
			}
		}
		Terraces.steps(d, cx - half, 2 * half + 1, level);
		List<int[]> bands = bands(level);
		for (int[] b : bands) {
			if (b[1] - b[0] < 4) {
				continue;
			}
			int mz = (b[0] + b[1]) / 2;
			for (int x = 1; x < w - 1; x++) {
				walk(d, x, level[mz], mz, paving);
				used[x][mz] = true;
			}
		}
		boolean stepped = Terraces.stepped(level);
		if (!stepped && Math.min(w, dp) >= 17) {
			for (int[] c : Draft.ellipseCells((w - 1) / 2.0, (dp - 1) / 2.0, (w - 1) / 2.0 - 3, (dp - 1) / 2.0 - 3, false)) {
				if (c[0] > 0 && c[1] > 0 && c[0] < w - 1 && c[1] < dp - 1) {
					walk(d, c[0], level[c[1]], c[1], d.mat(Role.PATH));
					used[c[0]][c[1]] = true;
				}
			}
		}
		int top = 3;
		// the bandstand where the walks meet on the deepest terrace (a city's), or a pond
		int[] main = bands.get(0);
		for (int[] b : bands) {
			if (b[1] - b[0] > main[1] - main[0]) {
				main = b;
			}
		}
		int mz = (main[0] + main[1]) / 2;
		boolean bandstand = d.param("bandstand", d.tier() >= 4) && main[1] - main[0] >= 6 && w >= 13;
		if (bandstand) {
			int bz0 = Math.max(main[0] + 1, mz - 2);
			int bz1 = bz0 + 4;
			if (bz1 <= main[1] - 1) {
				top = Math.max(top, Pavilions.pavilion(d, cx - 2, bz0, cx + 2, bz1, level[mz], false, true));
				for (int z = bz0; z <= bz1; z++) {
					for (int x = cx - 2; x <= cx + 2; x++) {
						used[x][z] = true;
					}
				}
				// benches facing it from the lawn on either side
				for (int x : new int[]{cx - 4, cx + 4}) {
					if (x >= 1 && x <= w - 2 && !used[x][mz]) {
						Benches.bench(d, x, level[mz], mz, x < cx ? Dirs.RIGHT : Dirs.LEFT, Dirs.BACK, 1, false);
						used[x][mz] = true;
					}
				}
			}
		}
		// the quarters: a pond in one, trees in the others
		int qx0 = 1;
		int qx1 = cx - half - 1;
		int rx0 = cx + half + 1;
		int rx1 = w - 2;
		boolean pond = d.param("pond", true);
		boolean pondLeft = d.chance(0.5);
		int pondDone = 0;
		for (int[] b : bands) {
			for (int side = 0; side < 2; side++) {
				int x0 = side == 0 ? qx0 : rx0;
				int x1 = side == 0 ? qx1 : rx1;
				int crossZ = b[1] - b[0] >= 4 ? (b[0] + b[1]) / 2 : -1;
				for (int[] q : quarters(b, crossZ)) {
					if (x1 - x0 < 3 || q[1] - q[0] < 3) {
						continue;
					}
					boolean wantPond = pond && pondDone == 0 && (side == 0) == pondLeft && q[1] - q[0] >= 4 && x1 - x0 >= 4;
					if (wantPond) {
						top = Math.max(top, pond(d, x0 + 1, q[0] + 1, x1 - 1, q[1] - 1, level[q[0]], used));
						pondDone++;
					} else {
						top = Math.max(top, trees(d, x0, q[0], x1, q[1], level[q[0]], used));
					}
				}
			}
		}
		// borders by the gate, benches and lamps along the middle walk
		for (int z = 1; z <= Math.min(3, dp - 2); z++) {
			for (int x : new int[]{cx - half - 1, cx + half + 1}) {
				if (x >= 1 && x <= w - 2 && !used[x][z] && level[z] == level[0]) {
					Planters.bed(d, x, level[z], z);
					used[x][z] = true;
				}
			}
		}
		for (int z = 5; z < dp - 2; z += 5) {
			for (int x : new int[]{cx - half - 1, cx + half + 1}) {
				if (x < 1 || x > w - 2 || used[x][z] || level[z - 1] != level[z] || level[z + 1] != level[z]) {
					continue;
				}
				boolean lamp = ((z / 5) & 1) == (x < cx ? 0 : 1);
				d.remove(x, level[z], z);
				d.remove(x, level[z] + 1, z);
				if (lamp) {
					top = Math.max(top, Lamps.standard(d, x, level[z], z, 4, Manner.lamp(d), null));
				} else {
					Benches.bench(d, x, level[z], z, x < cx ? Dirs.RIGHT : Dirs.LEFT, Dirs.BACK, 1, true);
				}
				used[x][z] = true;
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 1));
		Edges.guard(d);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** A walk's surface at a cell (over ground only; a terrace's coping or a step is left as it is). */
	private static void walk(Draft d, int x, int y, int z, Mat m) {
		if (!Garden.groundLike(d, x, y - 1, z) && d.get(x, y - 1, z) != null) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, m.full());
			if (d.isAbsent(x, y, z)) {
				d.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** Runs of rows at one level: {first, last}. */
	private static List<int[]> bands(int[] level) {
		List<int[]> out = new ArrayList<>();
		int a = 0;
		for (int z = 1; z <= level.length; z++) {
			if (z == level.length || level[z] != level[a]) {
				// leave out the rows against a retaining wall (the wall row itself and the coping)
				int first = a == 0 ? 1 : a + 1;
				int last = z == level.length ? level.length - 2 : z - 2;
				if (last >= first) {
					out.add(new int[]{first, last});
				}
				a = z;
			}
		}
		if (out.isEmpty()) {
			out.add(new int[]{1, Math.max(1, level.length - 2)});
		}
		return out;
	}

	/** A band split by its cross walk into two quarters (row ranges). */
	private static List<int[]> quarters(int[] band, int crossZ) {
		if (crossZ < 0) {
			return List.of(band);
		}
		return List.of(new int[]{band[0], crossZ - 1}, new int[]{crossZ + 1, band[1]});
	}

	/** A pond with an irregular shore over a rectangle at level y: lilies, reeds, a bench looking over it. */
	private static int pond(Draft d, int x0, int z0, int x1, int z1, int y, boolean[][] used) {
		long seed = d.seed() ^ 0x90D;
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		double rx = (x1 - x0) / 2.0;
		double rz = (z1 - z0) / 2.0;
		List<int[]> shore = new ArrayList<>();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (used[x][z]) {
						continue;
					}
					double ex = (x - cx) / (rx + 0.4);
					double ez = (z - cz) / (rz + 0.4);
					double e = ex * ex + ez * ez + 0.3 * (Noise.unit(seed, x, 0, z) - 0.5);
					if (e <= 0.75) {
						d.set(x, y - 1, z, Blocks.WATER.defaultBlockState());
						d.set(x, y - 2, z, (e < 0.3 ? Blocks.WATER : Blocks.MUD).defaultBlockState());
						d.set(x, y - 3, z, Blocks.MUD.defaultBlockState());
						d.remove(x, y, z);
						if (Noise.unit(seed, x, 1, z) < 0.2) {
							d.set(x, y, z, Blocks.LILY_PAD.defaultBlockState());
						}
						used[x][z] = true;
					} else if (e <= 1.05) {
						shore.add(new int[]{x, z});
					}
				}
			}
		}
		// reeds and flowers on the shore
		for (int[] c : shore) {
			double u = Noise.unit(seed, c[0], 2, c[1]);
			if (used[c[0]][c[1]] || !d.isEmpty(c[0], y, c[1])) {
				continue;
			}
			boolean wet = false;
			for (var dir : Dirs.HORIZONTAL) {
				BlockState n = d.get(c[0] + Dirs.dx(dir), y - 1, c[1] + Dirs.dz(dir));
				wet |= n != null && n.is(Blocks.WATER);
			}
			if (wet && u < 0.3) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(c[0], y - 1, c[1], Blocks.GRASS_BLOCK.defaultBlockState());
					d.set(c[0], y, c[1], Blocks.SUGAR_CANE.defaultBlockState());
					if (u < 0.15 && y + 1 < d.maxHeight()) {
						d.set(c[0], y + 1, c[1], Blocks.SUGAR_CANE.defaultBlockState());
					}
				}
			} else if (u < 0.55) {
				Planters.bed(d, c[0], y, c[1]);
			}
		}
		// a bench on the shore looking over the water
		int bx = (int) Math.round(cx);
		int bz = z1 + 1 < used[0].length && !used[bx][z1 + 1] ? z1 + 1 : -1;
		if (bz > 0 && d.isEmpty(bx, y, bz)) {
			d.remove(bx, y, bz);
			Benches.bench(d, bx, y, bz, Dirs.FRONT, Dirs.RIGHT, 1, false);
			d.poi(PoiType.VIEWPOINT, bx, y, bz - 1, Dirs.FRONT);
			used[bx][bz] = true;
		}
		return y + 2;
	}

	/**
	 * A clump of the culture's trees in a quarter (one, or two on a big quarter, five blocks or more apart, off the
	 * walks), or now and then an open lawn with flowers: a park is lawns with trees standing in them, not a wood.
	 */
	private static int trees(Draft d, int x0, int z0, int x1, int z1, int y, boolean[][] used) {
		long seed = d.seed() ^ (x0 * 31L + z0);
		int top = y;
		List<int[]> placed = new ArrayList<>();
		int area = (x1 - x0 + 1) * (z1 - z0 + 1);
		int want = Noise.unit(seed, 7, 7, 7) < 0.3 ? 0 : area >= 40 ? 2 : 1;
		for (int k = 0; k < want * 4 && placed.size() < want; k++) {
			int x = x0 + 1 + (int) (Noise.unit(seed, k, 0, 1) * Math.max(1, x1 - x0 - 1));
			int z = z0 + 1 + (int) (Noise.unit(seed, k, 0, 2) * Math.max(1, z1 - z0 - 1));
			if (x > x1 - 1 || z > z1 - 1 || used[x][z]) {
				continue;
			}
			boolean far = true;
			for (int[] p : placed) {
				far &= Math.abs(p[0] - x) + Math.abs(p[1] - z) >= 5;
			}
			if (!far) {
				continue;
			}
			d.remove(x, y, z);
			int h = Math.min(d.maxHeight() - y, 6 + (int) (Noise.unit(seed, k, 1, 3) * 3));
			top = Math.max(top, Trees.tree(d, x, y, z, h, Trees.kind(d), x0, z0, x1, z1));
			placed.add(new int[]{x, z});
			used[x][z] = true;
		}
		// flowers and grass tufts under them
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (used[x][z] || !d.isAbsent(x, y, z)) {
						continue;
					}
					double u = Noise.unit(seed, x, 4, z);
					if (u < 0.12) {
						Planters.bed(d, x, y, z);
					} else if (u < 0.3 && Garden.groundLike(d, x, y - 1, z)) {
						d.set(x, y, z, Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
		}
		return top;
	}
}
