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
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.landscape.Terraces;
import dev.voidmc.hearth.build.kit.landscape.Trees;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Enclosure;
import dev.voidmc.hearth.build.kit.sociallife.Fair;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.kit.landscape.Edges;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * {@code hearth:landscape/festival_ground}: the green at the edge of town where the feasts are kept. In its middle the
 * culture's centrepiece on a dance floor round it (the Bohemian maypole, its trunk stripped, a green crown and a
 * garland hung with the town's ribbons, over a floor of boards; the north's midsummer pole with its crossbar and hoops;
 * a beacon on the moors and the coast; a ring of lantern poles in the karst; the steppe's horsetail standard over the
 * wrestling ring; a wine vat under a vine pergola in the south; an obelisk with braziers in the desert; a bonfire in
 * the cold and the wet lands). Behind it the musicians' stage under its roof with the culture's cloth hung behind; a
 * row of booths with striped awnings down either side, the length of the green; trestles with benches for the feast in
 * front; a bonfire pit and heaps of hay; shade trees in the corners; lamps round the floor; a garlanded arch over the
 * way in from the street. The social module's festivals gather at the stage (STAGE), its feasts at the tables (TABLE);
 * MEETING_POINTs at the centrepiece and before the stage. On rough ground the green is terraced in broad bands, the
 * middle on one level.
 */
public final class FestivalGround implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/festival_ground"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		21, 41, 21, 41, 6, 16, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		int cx = (w - 1) / 2;
		int cz = Math.min(dp / 2, dp - 12);
		int ring = Math.clamp(Math.min(w, dp) / 8, 3, 5);

		// --- the ground: broad terraces, the floor, the stage and the booths on one level
		// the front rows meet the street; from there the walk climbs (or falls) by at most two a band of three rows to the
		// green's one level, which is the ground's at the floor as far as that climb can reach
		int[] level = Terraces.plan(d, 0, w - 1, 7, 2);
		int front = Math.max(1, cz - ring - 2);
		int reach = 2 * ((front - 1) / 3);
		// (and low enough under the plot's height for the stage and its players)
		int green = Math.clamp(level[cz], -reach, Math.max(0, Math.min(reach, maxH - 5)));
		for (int z = 0; z < dp; z++) {
			level[z] = z >= front ? green : Math.clamp(green, -2 * (z / 3), 2 * (z / 3));
		}
		Terraces.build(d, 0, w - 1, level);
		Terraces.steps(d, cx - 1, 3, level);
		int y = level[cz];
		d.entrance(cx, level[0], -1);

		// --- the way in: a gravel walk from the street to the floor under a garlanded arch
		Mat path = d.mat(Role.PATH);
		boolean[][] taken = new boolean[w][dp];
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < cz - ring; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					lay(d, x, z, level[z], path);
					taken[x][z] = true;
				}
			}
		}
		if (level[1] == level[0]) {
			Fair.arch(d, cx - 2, cx + 2, level[1], 1);
			for (int z = 0; z <= 2; z++) {
				taken[cx - 2][z] = true;
				taken[cx + 2][z] = true;
			}
		}

		// --- the dance floor round the centrepiece
		Mat floor = Tradition.of(d) == Tradition.TIMBER || Tradition.of(d) == Tradition.ALPINE || Tradition.of(d) == Tradition.LOG ? d.mat(Role.FLOOR)
			: d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ellipseCells(cx, cz, ring, ring, true)) {
				if (inside(d, c[0], c[1])) {
					lay(d, c[0], c[1], y, floor);
					taken[c[0]][c[1]] = true;
				}
			}
		}
		Fair.Centre centre = Fair.centre(d);
		Fair.centrepiece(d, centre, cx, y, cz, maxH - y, Dirs.FRONT);
		d.poi(PoiType.MEETING_POINT, cx, y, cz - 2, Dirs.BACK);
		// --- the stage behind the floor, facing it
		int sw = w >= 27 ? 7 : 5;
		int sdp = 3;
		int sz = cz + ring + 3;
		if (sz + sdp + 1 <= dp - 1 && maxH - y >= 8) {
			int sx = cx - sw / 2;
			// x, z is the front-left corner seen from the audience
			Fair.stage(d, sx, y, sz, sw, sdp, Dirs.FRONT);
			for (int z = sz - 1; z <= sz + sdp; z++) {
				for (int x = sx - 1; x <= sx + sw; x++) {
					if (inside(d, x, z)) {
						taken[x][z] = true;
					}
				}
			}
			d.poi(PoiType.MEETING_POINT, cx, y, sz - 2, Dirs.BACK);
		} else {
			// a small green: the stage is a boarded platform at the floor's back edge
			int px = cx - 1;
			int pz = cz + ring + 1;
			if (inside(d, px + 2, pz + 1)) {
				Venue.stage(d, px, pz, px + 2, pz + 1, y, Dirs.FRONT, false);
				for (int z = pz - 1; z <= pz + 1; z++) {
					for (int x = px - 1; x <= px + 3; x++) {
						if (inside(d, x, z)) {
							taken[x][z] = true;
						}
					}
				}
			}
		}

		// lamps round the floor, on the diagonals just outside it
		int lr = ring + 1;
		for (int[] o : new int[][]{{-lr, -lr}, {lr, -lr}, {-lr, lr}, {lr, lr}}) {
			int lx = cx + o[0] * 3 / 4;
			int lz = cz + o[1] * 3 / 4;
			if (inside(d, lx, lz) && !taken[lx][lz] && Enclosure.ground(d, lx, y, lz)) {
				Lighting.post(d, lx, y, lz);
				taken[lx][lz] = true;
			}
		}

		// banner posts at the floor's four quarters
		int bp = 0;
		for (Direction dir : Dirs.HORIZONTAL) {
			int px = cx + Dirs.dx(Dirs.right(dir)) * (ring + 1) + Dirs.dx(dir) * (ring + 1);
			int pz = cz + Dirs.dz(Dirs.right(dir)) * (ring + 1) + Dirs.dz(dir) * (ring + 1);
			px = cx + (px - cx) * 2 / 3;
			pz = cz + (pz - cz) * 2 / 3;
			if (inside(d, px, pz) && !taken[px][pz] && Enclosure.ground(d, px, y, pz)) {
				Fair.bannerPost(d, px, y, pz, Dirs.FRONT, bp++);
				taken[px][pz] = true;
			}
		}
		// benches round the floor facing in
		for (Direction dir : Dirs.HORIZONTAL) {
			if (dir == Dirs.FRONT || dir == Dirs.BACK) {
				continue;
			}
			for (int k = -1; k <= 1; k += 2) {
				int bx = cx + Dirs.dx(dir) * (ring + 2);
				int bz = cz + k;
				if (inside(d, bx, bz) && !taken[bx][bz] && Enclosure.ground(d, bx, y, bz)) {
					Venue.bench(d, bx, y, bz, Dirs.BACK, 1, dir);
					taken[bx][bz] = true;
				}
			}
		}

		// --- booths down either side of the floor, facing it, a row of them the length of the green
		for (int side : new int[]{-1, 1}) {
			for (int bz = 3; bz + 3 <= dp - 2; bz += 5) {
				// the booth's front-left corner seen from the floor
				Direction face = side < 0 ? Dirs.RIGHT : Dirs.LEFT;
				int bx = side < 0 ? cx - ring - 6 : cx + ring + 6;
				if (bx < 1 || bx > w - 2) {
					continue;
				}
				int[][] cells = boothCells(bx, bz, face);
				boolean ok = true;
				for (int[] c : cells) {
					ok &= inside(d, c[0], c[1]) && c[0] >= 1 && c[0] <= w - 2 && !taken[c[0]][c[1]] && level[c[1]] == y && Enclosure.ground(d, c[0], y, c[1]);
				}
				if (!ok) {
					continue;
				}
				Fair.booth(d, bx, y, bz, face);
				for (int[] c : cells) {
					taken[c[0]][c[1]] = true;
				}
			}
		}

		// --- the feast's trestles round the floor, either side of the walk and behind the booths
		int placedTables = 0;
		for (int[] c : new int[][]{{cx - 8, cz - ring - 3}, {cx + 4, cz - ring - 3}, {cx - ring - 10, cz + ring + 3}, {cx + ring + 6, cz + ring + 3},
			{cx - 13, cz - ring - 3}, {cx + 9, cz - ring - 3}}) {
			int tx = c[0];
			int tz = c[1];
			if (placedTables >= (w >= 31 ? 4 : 2) || tz < 2 || tz > dp - 3 || level[tz] != level[tz - 1] || level[tz] != level[tz + 1]) {
				continue;
			}
			int ty = level[tz];
			boolean ok = true;
			for (int x = tx - 1; x <= tx + 5; x++) {
				for (int z = tz - 1; z <= tz + 1; z++) {
					ok &= inside(d, x, z) && x >= 1 && x <= w - 2 && !taken[x][z] && Enclosure.ground(d, x, ty, z);
				}
			}
			if (!ok) {
				continue;
			}
			Venue.trestle(d, tx, ty, tz, Dirs.RIGHT, 5);
			Venue.candles(d, tx + 2, ty + 1, tz, 3);
			placedTables++;
			for (int x = tx - 1; x <= tx + 5; x++) {
				for (int z = tz - 1; z <= tz + 1; z++) {
					taken[x][z] = true;
				}
			}
		}

		// --- a bonfire pit and heaps of hay near the front corners, shade trees in the corners
		int bfx = Noise.unit(d.seed(), 1, 2, 3) < 0.5 ? 4 : w - 5;
		int bfz = Math.max(3, cz - ring - 6);
		if (centre != Fair.Centre.BONFIRE && inside(d, bfx, bfz) && clear(d, taken, bfx, level[bfz], bfz, 1)) {
			Fair.bonfire(d, bfx, level[bfz], bfz, false);
			mark(taken, d, bfx, bfz, 1);
			for (Direction dir : Dirs.HORIZONTAL) {
				int hx = bfx + Dirs.dx(dir) * 3;
				int hz = bfz + Dirs.dz(dir) * 3;
				if (inside(d, hx, hz) && clear(d, taken, hx, level[hz], hz, 0) && level[hz] == level[bfz]) {
					Venue.bench(d, hx, level[hz], hz, Dirs.RIGHT, 1, dir);
					taken[hx][hz] = true;
				}
			}
		}
		for (int[] c : new int[][]{{w - 5, Math.max(3, cz - ring - 6)}, {4, cz + 2}, {w - 5, cz + 2}}) {
			if (inside(d, c[0], c[1]) && clear(d, taken, c[0], level[c[1]], c[1], 1)) {
				Fair.heap(d, c[0], level[c[1]], c[1]);
				mark(taken, d, c[0], c[1], 1);
			}
		}
		Trees.Kind kind = Trees.kind(d);
		int th = Math.min(8, maxH - 1 - y);
		for (int[] c : new int[][]{{3, dp - 4}, {w - 4, dp - 4}, {3, 3}, {w - 4, 3}}) {
			int ty = level[c[1]];
			if (inside(d, c[0], c[1]) && clear(d, taken, c[0], ty, c[1], 1) && th >= 4) {
				Trees.tree(d, c[0], ty, c[1], th, kind, c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3);
				mark(taken, d, c[0], c[1], 2);
			}
		}
		// a wattle fence along the back of the green in the Bohemian countryside
		if (Bohemia.is(d)) {
			List<int[]> run = new ArrayList<>();
			for (int x = 0; x < w; x++) {
				if (!taken[x][dp - 1]) {
					run.add(new int[]{x, dp - 1});
				}
			}
			dev.voidmc.hearth.build.kit.Garden.fence(d, run, -1, Dirs.FRONT);
			Bohemia.wattle(d, 0, dp - 1, w - 1, dp - 1);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, maxH - 1);
		// rails where the green's terraces stand over the falling ground
		Edges.guard(d);
	}

	private static int[][] boothCells(int x, int z, Direction toCustomers) {
		Direction back = toCustomers.getOpposite();
		Direction right = Dirs.left(toCustomers);
		List<int[]> out = new ArrayList<>();
		for (int i = -1; i <= 3; i++) {
			for (int k = -1; k <= 2; k++) {
				out.add(new int[]{x + Dirs.dx(right) * i + Dirs.dx(back) * k, z + Dirs.dz(right) * i + Dirs.dz(back) * k});
			}
		}
		return out.toArray(int[][]::new);
	}

	private static boolean inside(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	private static boolean clear(Draft d, boolean[][] taken, int x, int y, int z, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (!inside(d, x + dx, z + dz) || taken[x + dx][z + dz] || !Enclosure.ground(d, x + dx, y, z + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	private static void mark(boolean[][] taken, Draft d, int x, int z, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (inside(d, x + dx, z + dz)) {
					taken[x + dx][z + dz] = true;
				}
			}
		}
	}

	private static void lay(Draft d, int x, int z, int y, Mat m) {
		if (d.site().isWater(x, z) || Groundwork.surface(d, x, z) != y || !d.isEmpty(x, y, z)) {
			return;
		}
		d.set(x, y - 1, z, m.full());
	}
}
