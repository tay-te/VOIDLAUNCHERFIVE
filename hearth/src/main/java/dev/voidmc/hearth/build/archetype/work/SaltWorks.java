package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.industry.Cabin;
import dev.voidmc.hearth.build.kit.industry.Fam;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Salt;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * {@code hearth:work/salt_works}: the salt works on the flats by the shore (or the brine springs inland). A walk runs in
 * from the track between fields of shallow pans, kerbed in stone flush with the ground, some flooded with brine to the
 * brim, some drying white at the edges, some raked into a white crust; beside them stand cones of salt, crystalline
 * white. At the back the boiling house of the culture's walls, its pans of brine steaming over their fires inside and
 * its sacks of salt stacked for the carts (in the sun cultures, where the sun does the boiling, a store house of sacks
 * instead). Inland works in the timber cultures raise a graduation tower down one side: a long wall of brushwood on its
 * timber frame, brine trickling down it in the wind into the trough at its foot.
 *
 * <p>Job sites (SALTER): on the kerbs at the pans (raking), at the boiling pans, at the heaps. Storage: barrels and the
 * chest in the house, the sacks.
 *
 * <p>Params: {@code tower} (boolean: the graduation tower; default in the timber cultures where the plot is large),
 * {@code roof}, {@code variant}; extraction params ({@code site}, {@code kind}, {@code rock}, {@code face}) accepted
 * and unused.
 */
public final class SaltWorks implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/salt_works"), ArchetypeCategory.WORKPLACE, Set.of(JobType.SALTER), 0,
		11, 17, 12, 17, 6, 10, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.PAGODA, RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Pois pois = new Pois();
		int w = d.frontage();
		int dp = d.depth();
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Works.Tradition t = Works.tradition(d);
		boolean boiling = t == Works.Tradition.TIMBER || t == Works.Tradition.EAST || t == Works.Tradition.MARSH;
		boolean tower = d.param("tower", t == Works.Tradition.TIMBER && w >= 14 && d.chance(0.7));
		RoofForm form = Hall.pick(d, FORMS);
		int lane = ex;

		// the house at the back: boiling house or store
		int hd = dp >= 15 ? 6 : 5;
		int hx0 = tower ? (lane < w / 2 ? 1 : 3) : 1;
		int hx1 = tower ? (lane < w / 2 ? w - 4 : w - 2) : w - 2;
		hx0 = Math.min(hx0, lane - 2);
		hx1 = Math.max(hx1, lane + 2);
		int hz1 = dp - 1;
		int hz0 = hz1 - hd + 1;
		Hall house = Cabin.build(d, hx0, hz0, hx1, hz1, 4, Dirs.FRONT, lane, form, Direction.Axis.X, false);
		int door = house.door != null ? house.door : lane;
		int top = house.top;
		int[] in = house.inner(0);
		Floorplan fp = house.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		Fam brick = Works.dry(d) ? Fam.mudBrick() : Fam.brick();
		if (boiling) {
			// the boiling pans along the back wall, steam out of the roof
			int n = Math.min(3, in[2] - in[0] - 1);
			int bx = (in[0] + in[2]) / 2 - n / 2;
			boolean ok = true;
			for (int i = 0; i < n; i++) {
				ok &= fp.take(bx + i, in[3], Dirs.FRONT);
			}
			if (ok && n > 0) {
				for (int[] c : Salt.boilers(d, bx, in[3], Dirs.RIGHT, n, Dirs.FRONT, brick)) {
					pois.site(c[0], 0, c[1], Dirs.BACK, JobType.SALTER);
				}
				int smoke = house.chimney(bx + n / 2, in[3] + 1, 0);
				if (smoke != Integer.MIN_VALUE) {
					top = Math.max(top, smoke);
				}
			}
		}
		// sacks of salt stacked along the side walls, the chest and barrels
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			List<int[]> cells = fp.along(wall);
			int run = 0;
			for (int[] c : cells) {
				if (run < 3 && c[1] > in[1] && fp.take(c[0], c[1], null)) {
					Salt.sacks(d, c[0], 0, c[1], Dirs.BACK, 1);
					run++;
				}
			}
		}
		boolean chest = false;
		for (int[] c : fp.along(Dirs.FRONT)) {
			if (!chest && Math.abs(c[0] - door) > 1 && fp.take(c[0], c[1], Dirs.BACK)) {
				chest = pois.store(d, c[0], 0, c[1], Dirs.BACK, false, Works.items("minecraft:sugar", 64, "minecraft:sugar", 64));
			}
		}
		if (!boiling) {
			pois.site(door, 0, in[1] + 1, Dirs.BACK, JobType.SALTER);
		}
		Lighting.room(d, fp.room());

		// the graduation tower down one side
		int fx0 = 0;
		int fx1 = w - 1;
		if (tower) {
			boolean left = lane >= w / 2;
			int tx = left ? 0 : w - 1;
			int len = Math.min(hz0 - 2, 10);
			if (len >= 5) {
				int tt = Salt.tower(d, tx, 1, Dirs.BACK, len, Math.min(5, d.maxHeight() - 3), left ? Dirs.RIGHT : Dirs.LEFT);
				top = Math.max(top, tt);
				pois.site(tx + (left ? 2 : -2), 0, 1 + len / 2, left ? Dirs.LEFT : Dirs.RIGHT, JobType.SALTER);
				if (left) {
					fx0 = 3;
				} else {
					fx1 = w - 4;
				}
			}
		}

		// the pans either side of the walk, the cones of salt at the ends of the rows
		int pz0 = 0;
		int pz1 = hz0 - 2;
		int step = 3;
		List<Salt.Pan> pans = new ArrayList<>();
		if (pz1 - pz0 >= 3) {
			pans = Salt.pans(d, fx0, pz0, fx1, pz1, step, lane, Works.rubble(d));
		}
		int raking = 0;
		for (Salt.Pan p : pans) {
			if (p.state() >= 1 && raking < 2) {
				// a salter on the kerb beside the pan, raking it
				int kz = p.z0() - 1;
				int kx = (p.x0() + p.x1()) / 2;
				if (kz >= pz0 && pois.siteIf(d, kx, 0, kz, Dirs.BACK, JobType.SALTER)) {
					raking++;
				}
			}
		}
		// cones of salt on the strip before the house
		int cz = hz0 - 1;
		int cones = 0;
		for (int x = 1; x < w - 1 && cones < 3; x += 4) {
			if (Math.abs(x - lane) >= 2 && Math.abs(x + 1 - lane) >= 2) {
				int ct = Salt.cone(d, x + 0.5, cz - 0.3, 1.2, 1.6);
				top = Math.max(top, ct);
				cones++;
			}
		}
		// the walk down the middle and the sign at the track
		List<int[]> walk = new ArrayList<>();
		for (int z = 0; z < hz0; z++) {
			if (z > pz1) {
				walk.add(new int[]{lane, z});
			}
		}
		Garden.path(d, walk, 0);
		int sx = lane + (lane < w / 2 ? 2 : -2);
		Signs.post(d, sx, 0, 0, lane < w / 2 ? Dirs.LEFT : Dirs.RIGHT, 3, "salt_works", "Salt");
		Works.lampPost(d, lane + (lane < w / 2 ? -1 : 1), 0, hz0 - 1);
		Works.keepDoors(d);
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{lane, 0, hz0 - 1}, new int[]{lane, 0, 1});
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 2));
	}
}
