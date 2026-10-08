package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.landscape.Terraces;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Enclosure;
import dev.voidmc.hearth.build.kit.sociallife.Graves;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * {@code hearth:civic/cemetery}: the burial ground by the church, walled and quiet. A roofed lychgate in the middle of
 * the front wall, its coffin benches under a steep little gable and a lantern hung from the ridge; the main walk of
 * gravel from it straight to the mortuary chapel at the back (a small rendered chapel with a bell-cote for the passing
 * bell over its gable, an altar and a few pews, its door lamp lit); across the middle a cross walk, and where they meet
 * the calvary on its stepped plinth with candles at its foot, the place where the town gathers for a funeral (the
 * social module stands mourners round the middle of the plot). The graves lie in rows along gravel walks between them,
 * heads to the back: fresh ones under stone covers with steles and their little lanterns, older mounds under wooden and
 * wrought-iron crosses, now and then a chest tomb; dark yews in the corners and flanking the chapel. On a hillside the
 * ground is terraced in bands with retaining walls, the walk climbing between them by steps.
 *
 * <p>By culture (graves after {@link Graves.Manner}): the Bohemian churchyard behind a white wall under a shingle
 * coping; rune stones and timber crosses inside a fence in the north; iron crosses under little roofs in the
 * mountains; slate slabs and ringed crosses behind dry-stone walls in the stone country; white chest tombs among
 * cypresses in the south; whitened crosses and cairns behind adobe in the desert; balbal stones over grassy mounds on the
 * steppe; horseshoe-kerbed hill tombs and a stone lantern in the karst; whitewashed vaults above the wet ground in the
 * bayou and the tropics.
 *
 * <p>POIs: the PRIEST's JOB_SITE (the chapel's lectern, or a lectern by the calvary), the ALTAR where there is a chapel,
 * SEATs (pews, the lychgate's benches, benches on the cross walk), the DOOR. Params: {@code chapel} (boolean, default
 * where the plot is 17+ deep and 13+ wide and the height allows).
 */
public final class Cemetery implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/cemetery"), ArchetypeCategory.CIVIC, Set.of(JobType.PRIEST), 0,
		13, 25, 13, 25, 6, 14, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Tradition t = Tradition.of(d);
		Graves.Manner manner = Graves.of(d);
		int cx = (w - 1) / 2;
		int cz = dp / 2;

		// --- where the chapel stands: across the back at the end of the main walk on a deep plot, in a back corner on a
		// middling one (its door to a walk of its own), none on a small one
		int cw = 5;
		int cd = dp >= 24 ? 7 : 6;
		int chapelZ0 = dp - cd;
		boolean allow = d.param("chapel", true) && maxH >= 12 && w >= 13;
		boolean centre = allow && chapelZ0 - cz >= 5;
		boolean corner = allow && !centre && w >= 15 && dp >= 15 && chapelZ0 - cz >= 2;
		boolean chapel = centre || corner;
		boolean cornerLeft = d.chance(0.5);
		int chx0 = centre ? cx - cw / 2 : cornerLeft ? 0 : w - cw;
		int chx1 = chx0 + cw - 1;
		int chDoor = (chx0 + chx1) / 2;

		// --- the ground: terraced in bands where the land rises or falls behind the street; the square round the calvary
		// and (with a chapel) everything behind it lie on one level
		int[] level = Terraces.plan(d, 0, w - 1, 5, 2);
		for (int z = Math.max(0, cz - 3); z <= (chapel ? dp - 1 : Math.min(dp - 1, cz + 3)); z++) {
			level[z] = level[cz];
		}
		Terraces.build(d, 0, w - 1, level);
		Terraces.steps(d, cx - 1, 3, level);

		// --- the boundary and the lychgate in the middle of the front
		boolean gate = Enclosure.lychgate(d, cx, level[0]);
		if (!gate) {
			Enclosure.piers(d, cx, level[0]);
		}
		List<int[]> front = new ArrayList<>();
		for (int x = 0; x < w; x++) {
			if (Math.abs(x - cx) > 2) {
				front.add(new int[]{x, 0});
			}
		}
		boundary(d, front, 2, level);

		// --- the chapel, its back (and in a corner its side) in the boundary
		if (chapel) {
			chapel(d, t, chx0, chapelZ0, chx1, dp - 1, level[chapelZ0]);
		}
		// (where the terraces step up beside a run of wall, the wall rises with them: its coping stays two and a half over
		// the highest ground inside beside it, so nobody steps from a terrace onto the wall head and along it over the drop)
		for (int z = 1; z < dp; z++) {
			int hi = level[z];
			for (int k = -1; k <= 1; k++) {
				if (z + k >= 0 && z + k < dp) {
					hi = Math.max(hi, level[z + k]);
				}
			}
			int hgt = 2 + hi - level[z];
			boundary(d, List.of(new int[]{0, z}, new int[]{w - 1, z}), hgt, level);
		}
		List<int[]> back = new ArrayList<>();
		for (int x = 1; x < w - 1; x++) {
			back.add(new int[]{x, dp - 1});
		}
		boundary(d, back, 2 + Math.max(0, level[Math.max(0, dp - 2)] - level[dp - 1]), level);

		// --- the walks: the main walk from the gate to the chapel, cross walks every seven rows from the middle one, the
		// square round the calvary; graves fill the rows between the walks, their feet on a walk, heads away from it
		Mat path = d.mat(Role.PATH);
		Mat paving = d.mat(Role.PAVING);
		int walkEnd = centre ? chapelZ0 - 1 : dp - 2;
		boolean[][] taken = new boolean[w][dp];
		for (int z = 0; z < 3 && gate; z++) {
			for (int x = cx - 3; x <= cx + 3; x++) {
				taken[x][z] = true;
			}
		}
		if (chapel) {
			for (int z = chapelZ0 - 1; z < dp; z++) {
				for (int x = Math.max(0, chx0 - 1); x <= Math.min(w - 1, chx1 + 1); x++) {
					taken[x][z] = true;
				}
			}
		}
		List<Integer> walks = new ArrayList<>();
		for (int z = cz; z >= 1; z -= 7) {
			walks.add(0, z);
		}
		for (int z = cz + 7; z <= walkEnd; z += 7) {
			walks.add(z);
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = gate ? 3 : 1; z <= walkEnd; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					lay(d, x, z, level[z], path);
					taken[x][z] = true;
				}
			}
			for (int wz : walks) {
				for (int x = 1; x <= w - 2; x++) {
					lay(d, x, wz, level[wz], path);
					taken[x][wz] = true;
				}
			}
			if (corner) {
				// the chapel's own walk from its door to the cross walk
				for (int z = cz + 1; z < chapelZ0; z++) {
					lay(d, chDoor, z, level[z], path);
					taken[chDoor][z] = true;
				}
			}
			for (int z = cz - 2; z <= cz + 2; z++) {
				for (int x = cx - 2; x <= cx + 2; x++) {
					if (z >= 1 && z < dp - 1) {
						if (level[z] == level[cz]) {
							lay(d, x, z, level[z], paving);
						}
						taken[x][z] = true;
					}
				}
			}
		}
		Graves.calvary(d, cx, level[cz], cz, Dirs.FRONT);
		// lamps at two corners of the square, benches facing it across the cross walk
		int ly = level[cz];
		for (int[] c : new int[][]{{cx - 2, cz - 2}, {cx + 2, cz + 2}}) {
			if (Enclosure.ground(d, c[0], ly, c[1])) {
				Lighting.post(d, c[0], ly, c[1]);
			}
		}
		// yews in the back corners and flanking the chapel's door (planned first, so graves keep clear of their trunks)
		List<int[]> yews = new ArrayList<>();
		yews.add(new int[]{2, dp - 3});
		yews.add(new int[]{w - 3, dp - 3});
		if (centre) {
			yews.add(new int[]{chx0 - 2, chapelZ0 - 1});
			yews.add(new int[]{chx1 + 2, chapelZ0 - 1});
		} else if (corner) {
			yews.removeIf(c -> c[0] >= chx0 - 1 && c[0] <= chx1 + 1 && c[1] >= chapelZ0 - 2);
			yews.add(new int[]{cornerLeft ? chx1 + 2 : chx0 - 2, chapelZ0 - 2});
		}
		for (int[] c : yews) {
			if (c[0] >= 1 && c[0] <= w - 2 && c[1] >= 1 && c[1] <= dp - 2) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int tx = c[0] + dx;
						int tz = c[1] + dz;
						if (tx >= 0 && tx < w && tz >= 0 && tz < dp) {
							taken[tx][tz] = true;
						}
					}
				}
			}
		}

		// the graves
		boolean wide = Graves.wideMarkers(manner);
		int lampEvery = manner == Graves.Manner.BOHEMIAN || manner == Graves.Manner.CREOLE ? 3 : 4;
		int n = 0;
		for (int wz : walks) {
			for (Direction head : new Direction[]{Dirs.BACK, Dirs.FRONT}) {
				int fz = wz + Dirs.dz(head);
				int mz = fz + 2 * Dirs.dz(head);
				if (fz < 1 || mz < 1 || fz > dp - 2 || mz > dp - 2 || level[fz] != level[wz] || level[mz] != level[wz]) {
					continue;
				}
				int y = level[wz];
				int pitch = wide && Noise.unit(d.seed(), wz, head.ordinal(), 7) < 0.6 ? 3 : 2;
				boolean cross = pitch == 3;
				for (int side : new int[]{-1, 1}) {
					int start = cx + side * (cross ? 3 : 2);
					for (int x = start; x >= 1 && x <= w - 2; x += side * pitch) {
						int[][] cells = Graves.cells(x, fz, head, cross);
						boolean ok = true;
						for (int[] c : cells) {
							// (a walk left along the boundary: a headstone against the wall is the step up onto its head)
							ok &= c[0] >= 2 && c[0] <= w - 3 && c[1] >= 2 && c[1] <= dp - 3 && !taken[c[0]][c[1]] && Enclosure.ground(d, c[0], y, c[1]);
						}
						if (!ok) {
							continue;
						}
						// now and then an empty plot of grass
						double u = Noise.unit(d.seed() ^ 0x6A5E, x, y, fz);
						if (u < 0.1) {
							continue;
						}
						for (int[] c : cells) {
							taken[c[0]][c[1]] = true;
						}
						double age = Noise.value(d.seed() ^ 0xA6E, x / 3.0, 0, fz / 3.0) * 0.6 + Noise.unit(d.seed(), x, fz, 1) * 0.4;
						Graves.grave(d, x, y, fz, head, age, cross, n++ % lampEvery == 0);
					}
				}
			}
		}
		// benches on the cross walk's ends where no grave took the ground
		for (int k : new int[]{-1, 1}) {
			int bz = cz + k * 3;
			for (int bx : new int[]{1, w - 2}) {
				if (bz >= 1 && bz <= dp - 2 && !taken[bx][bz] && level[bz] == ly && Enclosure.ground(d, bx, ly, bz)) {
					Venue.bench(d, bx, ly, bz, Dirs.BACK, 1, bx == 1 ? Dirs.LEFT : Dirs.RIGHT);
				}
			}
		}
		int yh = Math.min(6, maxH - 2);
		for (int[] c : yews) {
			int y = level[Math.clamp(c[1], 0, dp - 1)];
			if (c[0] >= 1 && c[0] <= w - 2 && Enclosure.ground(d, c[0], y, c[1])) {
				Graves.yew(d, c[0], y, c[1], yh - (Noise.unit(d.seed(), c[0], 0, c[1]) < 0.5 ? 1 : 0));
			}
		}

		// --- without a chapel, the priest's prayer desk on the calvary's front step, where he stands before the cross
		if (!chapel) {
			d.remove(cx, ly, cz - 1);
			Furniture.workstation(d, JobType.PRIEST, cx, ly, cz - 1, Dirs.FRONT);
		}
		// benches in the square's free corners, facing the cross
		for (int[] c : new int[][]{{cx - 2, cz + 2, 0}, {cx + 2, cz - 2, 1}}) {
			if (Enclosure.ground(d, c[0], ly, c[1])) {
				Venue.bench(d, c[0], ly, c[1], Dirs.RIGHT, 1, c[2] == 0 ? Dirs.BACK : Dirs.FRONT);
			}
		}
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, maxH - 1);
	}

	/** A walk's surface at (x, z), standing level y: the material laid in the ground course where the ground is bare. */
	private static void lay(Draft d, int x, int z, int y, Mat m) {
		if (d.site().isWater(x, z) || Groundwork.surface(d, x, z) != y || !d.isEmpty(x, y, z)) {
			return;
		}
		d.set(x, y - 1, z, m.full());
	}

	/**
	 * The mortuary chapel over {@code [x0..x1] × [z0..z1]} on standing level y, its door to the front: walls of the culture's
	 * masonry (stone in the timber cultures), lancets either side, a steep gable along the walk with a bell-cote on its
	 * front apex (a bell wall in the dry south), the altar at the back with the priest's lectern, pews, candles and a
	 * lantern.
	 */
	private static void chapel(Draft d, Tradition t, int x0, int z0, int x1, int z1, int y) {
		int cx = (x0 + x1) / 2;
		Body body = new Body(x0, z0, x1, z1, y, new int[]{5}, false, false);
		d.body(x0, z0, x1, z1);
		Storeys.ground(d, body, false);
		Facade fac = Facade.plan(d, body, cx, 1, 1, 0.0);
		WallKind kind = d.habits().walls().kind();
		boolean stone = kind == WallKind.TIMBER_FRAME || kind == WallKind.PLANK || kind == WallKind.BAMBOO;
		Storeys.walls(d, body, fac, 0, 0, stone ? new WallKind[]{WallKind.STONE} : null, true);
		Side front = body.side(0, Dirs.FRONT);
		Openings.door(d, front, cx, y, 1, new Openings.DoorOpts(false, false, false));
		Lighting.doorLamp(d, cx + 1, y + 2, z0 - 1, Dirs.BACK);
		// lancets along the sides
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			Side s = body.side(0, sd);
			for (int z = z0 + 2; z <= z1 - 2; z += 2) {
				Sacred.lancet(d, s, z, y + 1, 1, 2, true);
			}
		}
		// the roof: steep, along the walk; the bell-cote on the front apex
		Roofs.Opts o = Roofs.Opts.of(d);
		double half = (x1 - x0 + 1) / 2.0 + o.eaves();
		int plate = body.plateY();
		double fits = (d.maxHeight() - 6 - (plate + 1)) / half;
		double p = fits >= 2.0 ? 2.0 : fits >= 1.5 ? 1.5 : fits >= 1.0 ? 1.0 : 0.5;
		RoofForm form = t.flatRoofed() ? RoofForm.FLAT : t == Tradition.KARST ? RoofForm.PAGODA : RoofForm.GABLE;
		int top;
		if (form == RoofForm.FLAT) {
			Storeys.Top tp = Storeys.roof(d, body, RoofForm.FLAT, Direction.Axis.Z, 1, 1);
			top = Towers.bellWall(d, front, front.middle(), tp.deck() + 1, 1, d.maxHeight());
		} else if (form == RoofForm.PAGODA) {
			Storeys.Top tp = Storeys.roof(d, body, RoofForm.PAGODA, Direction.Axis.Z, 1, 1);
			top = tp.ridge();
		} else {
			Roofs.Plan plan = Roofs.plan(d, x0, z0, x1, z1, plate, RoofForm.GABLE, Direction.Axis.Z, o.withPitch(p), false, false);
			Roofs.Roof r = plan.draw(d).roof();
			if (Bohemia.is(d)) {
				Bohemia.carryRidge(d, r);
			}
			// the bell gable grows out of the front gable's peak, one piece with it
			int apex = r.surfaceY(cx, z0) - 1;
			top = Towers.bellCote(d, front, front.middle(), apex, d.maxHeight());
		}
		// inside: the altar at the back, pews, candles, a lantern
		int iz1 = z1 - 1;
		Sacred.altar(d, cx, y, iz1, Dirs.FRONT, x1 - x0 >= 6, x1 - x0 >= 6 ? cx + 2 : cx + 1);
		if (iz1 - 3 >= z0 + 2) {
			Sacred.pews(d, x0 + 1, x1 - 1, z0 + 2, iz1 - 3, y, cx, Dirs.BACK, false);
		}
		Lighting.room(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y);
	}

	/**
	 * The boundary over the cells, each at least {@code height} high, and higher where the land outside rises toward the
	 * wall's head (a retaining wall on the uphill side): its coping stands two and a half over the ground beyond it too, so
	 * nobody walks from the hillside onto the wall head and along it over the drop inside.
	 */
	private static void boundary(Draft d, List<int[]> cells, int height, int[] level) {
		int w = d.frontage();
		int dp = d.depth();
		for (int[] c : cells) {
			int x = c[0];
			int z = c[1];
			int out = Integer.MIN_VALUE;
			for (int[] o : new int[][]{{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}}) {
				if (o[0] < 0 || o[0] >= w || o[1] < 0 || o[1] >= dp) {
					out = Math.max(out, d.site().ground(o[0], o[1]));
				}
			}
			int g = level[Math.clamp(z, 0, dp - 1)];
			int h = out == Integer.MIN_VALUE ? height : Math.max(height, out - g + 2);
			Enclosure.run(d, List.of(c), h);
		}
	}
}
