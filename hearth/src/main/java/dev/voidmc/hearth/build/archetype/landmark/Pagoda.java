package dev.voidmc.hearth.build.archetype.landmark;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Eaves;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landmark/pagoda}: a temple court and its pagoda. From the street a roofed gateway on two posts opens on
 * a paved way between stone lanterns and blossom trees, over a pond on an arched moon bridge, up the steps of a stone
 * podium to a pagoda of five tiers (seven where the sky allows): each tier a frame of posts with lattice lights between
 * them, ringed by its own curved eave lifted at the corners where lanterns hang, the upper tiers stepping in behind
 * little balustrades; the top roof carried up to a spire of rings. Inside, a spiral stair climbs from the shrine on the
 * ground floor (an altar with its figure, candles, cushions) past a scroll room and a bell to a lookout in the top tier.
 *
 * <p>Built tier by tier: the podium, the posts, the walls, floors, then the eaves from the bottom up.
 *
 * <p>Params (all optional): {@code tiers} (5 or 7), {@code variant}.
 */
public final class Pagoda implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landmark/pagoda"), ArchetypeCategory.LANDMARK, Set.of(), 0,
		15, 21, 17, 25, 28, 40, SettlementTier.TOWN, Set.of(Culture.KARST, Culture.WISTERIA),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	private static final int TH = 4;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		int xc = (w - 1) / 2;
		// the pagoda's base and its tiers: as broad as the court allows, five tiers or seven
		int base = w >= 17 && dp >= 19 ? 9 : 7;
		int podiumH = 2;
		int tiers = d.param("tiers", top - podiumH - 9 >= 7 * TH && d.chance(0.5) ? 7 : 5);
		tiers = tiers >= 7 && top - podiumH - 9 >= 7 * TH ? 7 : 5;
		int[] widths = new int[tiers];
		for (int k = 0; k < tiers; k++) {
			int shrink = tiers == 7 ? (k >= 5 ? 4 : k >= 2 ? 2 : 0) : (k >= 4 ? 4 : k >= 2 ? 2 : 0);
			widths[k] = Math.max(5, base - shrink);
		}
		int back = 2;                       // the eaves behind
		int zc = dp - 1 - back - base / 2;
		int pz0 = zc - base / 2 - 2;        // the podium's front edge
		d.body(xc - base / 2, zc - base / 2, xc + base / 2, zc + base / 2);
		Stone dress = Stone.dressing(d);
		Stone found = Stone.footing(d);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);

		// folk come in through the gateway at the street
		d.entrance(xc, 0, -1);
		// the court levelled, the podium, its steps
		Grounds.podium(d, 0, 0, w - 1, dp - 1, 0, false);
		int px0 = xc - base / 2 - 2;
		int px1 = xc + base / 2 + 2;
		int pz1 = zc + base / 2 + 2;
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = pz0; z <= pz1; z++) {
				for (int x = px0; x <= px1; x++) {
					boolean edge = x == px0 || x == px1 || z == pz0 || z == pz1;
					for (int y = -1; y < podiumH; y++) {
						d.set(x, y, z, edge ? found.full() : dress.full());
					}
					if (edge && dress.has(Form.SLAB)) {
						d.set(x, podiumH - 1, z, dress.full());
					}
				}
			}
		}
		// a balustrade round the podium, broken by the steps at the front
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(px0, pz0, px1, pz1)) {
				if (c[1] == pz0 && Math.abs(c[0] - xc) <= 1) {
					continue;
				}
				d.set(c[0], podiumH, c[1], (c[0] + c[1] & 1) == 0 ? dress.full() : dress.wallOrFull());
			}
			for (int k = 0; k < podiumH; k++) {
				for (int x = xc - 1; x <= xc + 1; x++) {
					int z = pz0 - podiumH + k;
					if (z >= 0) {
						d.set(x, k, z, dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
						for (int y = -1; y < k; y++) {
							d.set(x, y, z, found.full());
						}
					}
				}
			}
		}
		int y0 = podiumH;                    // the ground tier's standing level

		// the tiers: posts, walls with lattice lights, a beam course; floors between them
		int floorY = y0 - 1;
		List<int[]> rects = new ArrayList<>();
		for (int k = 0; k < tiers; k++) {
			int hw = widths[k] / 2;
			int x0 = xc - hw;
			int x1 = xc + hw;
			int z0 = zc - hw;
			int z1 = zc + hw;
			rects.add(new int[]{x0, z0, x1, z1});
			int fy = floorY + k * TH;
			tier(d, k, x0, z0, x1, z1, fy, frame, beam);
			// the floor of this tier over the one below's whole width
			int[] under = k == 0 ? new int[]{x0, z0, x1, z1} : rects.get(k - 1);
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int z = under[1]; z <= under[3]; z++) {
					for (int x = under[0]; x <= under[2]; x++) {
						d.set(x, fy, z, k == 0 ? dress.full() : d.mat(Role.FLOOR).full());
					}
				}
			}
			d.carve(x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + TH - 1, z1 - 1);
		}
		// the eaves, from the bottom tier up, and the little balustrades on the ledges where a tier steps in
		for (int k = 0; k < tiers; k++) {
			int[] r = rects.get(k);
			int fy = floorY + k * TH;
			if (k < tiers - 1) {
				Eaves.skirt(d, r[0], r[1], r[2], r[3], fy + TH - 1, 2, true);
				int[] nr = rects.get(k + 1);
				if (nr[0] > r[0]) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						for (int[] c : Draft.ring(r[0], r[1], r[2], r[3])) {
							d.set(c[0], fy + TH + 1, c[1], d.mat(Role.RAILING).fence());
						}
					}
				}
			}
		}
		// the top roof: a curved hip to a point, the spire of rings
		int[] last = rects.get(tiers - 1);
		int fyTop = floorY + (tiers - 1) * TH;
		int peak = Eaves.hipped(d, last, fyTop + TH - 1, top, 2, true, true);

		// inside: the spiral, the shrine, the scroll room, a bell, the lookout
		int doorZ = rects.get(0)[1];
		Side front = Side.of(rects.get(0)[0], rects.get(0)[1], rects.get(0)[2], rects.get(0)[3], Dirs.FRONT);
		Openings.door(d, front, xc, y0, 1, Openings.DoorOpts.MAIN);
		int stairTop = fyTop + 1;
		Shapes.spiral(d, xc, zc, y0, stairTop - y0, 1, dress, frame.log(Direction.Axis.Y));
		interior(d, rects, xc, zc, y0, tiers, dress);

		// the court: the gate, the way, lanterns, the pond and its bridge, trees
		court(d, xc, pz0 - podiumH, dress, found);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(top, peak + 2));
	}

	/** One tier's walls: posts at the corners and every other cell, lattice or wall between, a beam over them. */
	private static void tier(Draft d, int k, int x0, int z0, int x1, int z1, int fy, Mat frame, Mat beam) {
		Mat wall = d.mat(Role.WALL);
		Mat shutter = d.mat(Role.SHUTTER);
		for (Side s : Side.of(x0, z0, x1, z1)) {
			int mid = s.middle();
			for (int i = 0; i < s.length(); i++) {
				boolean post = s.isCorner(i) || Math.abs(i - mid) == 2;
				boolean light = Math.abs(i - mid) <= 1;
				for (int y = fy + 1; y < fy + TH; y++) {
					boolean beamRow = y == fy + TH - 1;
					Paint p;
					StageKind st;
					if (post) {
						p = frame.log(Direction.Axis.Y);
						st = StageKind.FRAME;
					} else if (beamRow) {
						p = beam.log(s.axis());
						st = StageKind.FRAME;
					} else if (light && shutter.has(Form.TRAPDOOR)) {
						p = shutter.trapdoor(s.out().getOpposite(), y == fy + 1 ? Half.BOTTOM : Half.TOP, true);
						st = StageKind.OPENINGS;
					} else {
						p = wall.full();
						st = StageKind.WALLS;
					}
					try (var sc = d.stage(st)) {
						d.set(s.x(i), y, s.z(i), p);
					}
				}
			}
		}
	}

	/** The rooms: a shrine on the ground floor, a scroll room, a bell, a lookout at the top. */
	private static void interior(Draft d, List<int[]> rects, int xc, int zc, int y0, int tiers, Stone dress) {
		int[] g = rects.get(0);
		int bz = g[3] - 1;
		// the shrine: a figure on a plinth against the back wall, candles, cushions before it
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(xc, y0, bz, dress.full());
			d.set(xc, y0 + 1, bz, Blocks.GOLD_BLOCK.defaultBlockState());
			if (g[2] - g[0] >= 6) {
				d.set(xc - 1, y0, bz, dress.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
				d.set(xc + 1, y0, bz, dress.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
				d.set(xc - 2, y0, bz, Details.candles(3, true));
				d.set(xc + 2, y0, bz, Details.candles(3, true));
			}
		}
		if (bz - 1 > zc + 1) {
			d.poi(PoiType.ALTAR, xc, y0, bz - 1, Dirs.BACK);
		} else {
			d.poi(PoiType.ALTAR, xc - 1, y0, bz, Dirs.RIGHT);
		}
		if (g[2] - g[0] >= 6) {
			Block carpet = d.mat(Role.CLOTH).mainOr(Form.CARPET, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED));
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(g[0] + 1, y0, zc, carpet.defaultBlockState());
				d.set(g[2] - 1, y0, zc, carpet.defaultBlockState());
			}
			d.poi(PoiType.SEAT, g[0] + 1, y0, zc, Dirs.RIGHT);
			d.poi(PoiType.SEAT, g[2] - 1, y0, zc, Dirs.LEFT);
			Furniture.hangingLantern(d, g[0] + 1, y0 + TH - 1, g[1] + 1, 0);
			Furniture.hangingLantern(d, g[2] - 1, y0 + TH - 1, g[3] - 1, 0);
		}
		// the tiers above: scrolls on the second, a bell hung on the middle one, a lookout at the top
		for (int k = 1; k < tiers; k++) {
			int[] r = rects.get(k);
			int y = y0 + k * TH;
			if (r[2] - r[0] < 6) {
				continue;
			}
			if (k == 1) {
				try (var s = d.stage(StageKind.INTERIOR)) {
					d.set(r[0] + 1, y, r[3] - 1, Blocks.BOOKSHELF.defaultBlockState());
					d.set(r[2] - 1, y, r[3] - 1, Blocks.BOOKSHELF.defaultBlockState());
					d.set(r[0] + 1, y, r[1] + 1, States.facing(Blocks.LECTERN, Dirs.RIGHT));
				}
				d.poi(PoiType.LECTERN, r[0] + 2, y, r[1] + 1, Dirs.LEFT);
			} else if (k == tiers / 2) {
				Details.bell(d, r[0] + 1, y + TH - 2, r[1] + 1, Dirs.FRONT);
			}
		}
		int[] t = rects.get(tiers - 1);
		d.poi(PoiType.VIEWPOINT, xc, y0 + (tiers - 1) * TH, t[1] + 1 <= zc - 2 ? t[1] + 1 : zc - 1, Dirs.FRONT);
	}

	/** The court before the podium: a gateway at the street, a paved way between lanterns, a pond under a moon bridge. */
	private static void court(Draft d, int xc, int zEnd, Stone dress, Stone found) {
		int w = d.frontage();
		Mat paving = d.mat(Role.PAVING);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		// the way from the street to the podium steps
		List<int[]> way = new ArrayList<>();
		for (int z = 0; z < zEnd; z++) {
			for (int x = xc - 1; x <= xc + 1; x++) {
				way.add(new int[]{x, z});
			}
		}
		// the pond and the moon bridge across it, where the court is deep enough
		int pondZ0 = 2;
		int pondZ1 = pondZ0 + 2;
		boolean pond = zEnd - pondZ1 >= 2;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : way) {
				if (pond && c[1] >= pondZ0 - 1 && c[1] <= pondZ1 + 1) {
					continue;
				}
				d.set(c[0], -1, c[1], paving.full());
			}
		}
		if (pond) {
			int pondX0 = Math.max(1, xc - 5);
			int pondX1 = Math.min(w - 2, xc + 5);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int z = pondZ0 - 1; z <= pondZ1 + 1; z++) {
					for (int x = pondX0 - 1; x <= pondX1 + 1; x++) {
						boolean rim = z == pondZ0 - 1 || z == pondZ1 + 1 || x == pondX0 - 1 || x == pondX1 + 1;
						if (rim) {
							d.set(x, -1, z, found.full());
						} else {
							d.set(x, -2, z, found.full());
							d.set(x, -1, z, Blocks.WATER.defaultBlockState());
						}
					}
				}
				// lily pads and a lantern on the water's edge
				d.set(pondX0, 0, pondZ0, Blocks.LILY_PAD.defaultBlockState());
				d.set(pondX1, 0, pondZ1, Blocks.LILY_PAD.defaultBlockState());
			}
			// the bridge: an arch three wide over the water, stairs up, a crown block, stairs down, railed both sides
			Mat rail = d.mat(Role.RAILING);
			int bz0 = pondZ0 - 1;
			int bz1 = pondZ1 + 1;
			int mid = (bz0 + bz1) / 2;
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int z = bz0 + 1; z < bz1; z++) {
					int h = Math.min(z - bz0, bz1 - z);
					for (int x = xc - 1; x <= xc + 1; x++) {
						Paint p = z == mid ? paving.full() : paving.stairsOrSlab(z < mid ? Dirs.BACK : Dirs.FRONT, Half.BOTTOM);
						d.set(x, h - 1, z, p);
					}
					for (int x : new int[]{xc - 2, xc + 2}) {
						d.set(x, h - 1, z, paving.full());
						d.set(x, h, z, rail.fence());
					}
				}
			}
		}
		// the gateway at the street: two posts, a beam, a little curved roof
		int gx0 = xc - 2;
		int gx1 = xc + 2;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{gx0, gx1}) {
				d.set(x, 0, 0, dress.full());
				for (int y = 1; y <= 4; y++) {
					d.set(x, y, 0, frame.log(Direction.Axis.Y));
				}
			}
			for (int x = gx0 - 1; x <= gx1 + 1; x++) {
				d.set(x, 4, 0, beam.log(Direction.Axis.X));
			}
			for (int x = gx0; x <= gx1; x++) {
				if (x != gx0 && x != gx1) {
					d.set(x, 3, 0, frame.fence());
				}
			}
		}
		Roofs.Field gate = Roofs.flared(Roofs.gable(gx0 - 2, -1, gx1 + 2, 1, Direction.Axis.X, Roofs.Profile.straight(0.5)), gx0 - 2, -1, gx1 + 2, 1, 1, 2);
		Roofs.Roof gr = Roofs.raster(d, gate, 5, Math.max(0, gx0 - 2), -1, Math.min(w - 1, gx1 + 2), 1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM),
			Habits.Ridge.FULL);
		Shapes.support(d, gr, d.mat(Role.ROOF).full());
		// stone lanterns along the way, blossom trees in the corners, gravel or flowers between
		for (int z = 2; z < zEnd - 1; z += 3) {
			if (pond && z >= pondZ0 - 2 && z <= pondZ1 + 2) {
				continue;
			}
			for (int x : new int[]{xc - 3, xc + 3}) {
				if (Grounds.standable(d, x, 0, z)) {
					Details.stoneLantern(d, x, 0, z, dress);
				}
			}
		}
		for (int[] c : new int[][]{{1, 1}, {w - 2, 1}, {1, zEnd - 2}, {w - 2, zEnd - 2}}) {
			if (c[1] > 0 && Math.abs(c[0] - xc) > 4 && w >= 17 && (c[1] < pondZ0 - 1 || c[1] > pondZ1 + 1 || !pond)) {
				Grounds.tree(d, c[0], 0, c[1], 4);
			}
		}
		for (int z = 1; z < zEnd; z++) {
			for (int x = 0; x < w; x++) {
				if (Math.abs(x - xc) >= 4 && Math.abs(x - xc) <= 5 && d.isAbsent(x, 0, z) && d.isAbsent(x, -1, z) && d.site().ground(x, z) == 0) {
					Garden.garden(d, x, z, x, z, 0);
				}
			}
		}
		d.poi(PoiType.MEETING_POINT, xc, 0, Math.max(1, zEnd - 1), Dirs.BACK);
	}
}
