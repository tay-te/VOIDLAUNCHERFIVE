package dev.voidmc.hearth.build.archetype.work;

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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.homestead.Barns;
import dev.voidmc.hearth.build.kit.homestead.Crops;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/barn}: the herder's barn, the biggest roof on a farm. Its gable faces the yard: wide cart doors
 * under a lintel with their leaves hung open against the boards, a hayloft door above them under a hoist beam with a
 * bale on its chain. Inside, a threshing aisle of beaten earth runs to the back between stalls (fence partitions, gates
 * to the aisle, mangers and water), the loft over the stalls is stacked with hay and reached by a stair along the back
 * wall, lanterns hang from the tie beams. Boards between posts on a stone plinth where the culture builds in timber, logs
 * in log country, the culture's masonry elsewhere (an alpine barn boards its loft over a stone byre); a gambrel roof in
 * timber country, the culture's own roof otherwise, a cupola on the ridge of a big one. In front, a yard levelled to the
 * street with a water trough, a muck heap and a rick.
 *
 * <p>Job sites (HERDER): the feed cauldron at the back of the aisle, the loft, the trough. Storage: the aisle chest.
 *
 * <p>Params (all optional): {@code roof} (a RoofForm name), {@code loft} (int 1–3: knee height of the loft walls).
 */
public final class Barn implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/barn"), ArchetypeCategory.WORKPLACE, Set.of(JobType.HERDER), 0,
		9, 13, 11, 15, 11, 16, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int side = w >= 11 ? 1 : 0;
		int x0 = side;
		int x1 = w - 1 - side;
		// an odd width keeps the doors and the ridge on the middle
		if ((x1 - x0 + 1) % 2 == 0) {
			x1--;
		}
		int z0 = dp >= 13 ? 3 : 2;
		int z1 = dp - 1 - (dp - z0 >= 13 ? 1 : 0);
		int bw = x1 - x0 + 1;
		int doorW = bw >= 11 ? 3 : bw >= 9 ? 3 : 1;
		// the roof and loft that fit under the plot's height
		RoofForm form = roofParam(d, Barns.form(d, bw >= 9));
		int groundH = 5;
		int loftH = Math.clamp(d.param("loft", 3), 1, 3);
		Fit fit = fit(d, form, bw, groundH, loftH);
		d.body(x0, z0, x1, z1);
		// the yard in front levelled to the street (raised to the water's surface where a bank floods)
		int base = 0;
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				int wy = d.site().water(x, z);
				if (wy != dev.voidmc.hearth.build.terrain.Site.NONE) {
					base = Math.max(base, wy);
				}
			}
		}
		base = Math.min(base, 3);
		Groundwork.level(d, 0, 0, w - 1, z0 - 1, base, true);
		Barns.Hall hall = Barns.shell(d, x0, z0, x1, z1, base, fit.groundH, fit.loftH, fit.form, doorW, false, false);
		if (base > 0) {
			Groundwork.approach(d, (x0 + x1) / 2, base, 0);
		}
		d.eave(hall.plateY());
		Barns.sideWindows(d, hall);
		Barns.interior(d, hall, doorW, false);
		if (hall.roof() == null) {
			roofHay(d, hall);
		}
		if (bw >= 11 && dp >= 13 && d.chance(0.6)) {
			Barns.cupola(d, hall);
		}
		yard(d, hall, w, z0, base);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, (hall.roof() != null ? hall.roof().ridgeY : hall.plateY() + 3) + 2));
	}

	private static RoofForm roofParam(Draft d, RoofForm chosen) {
		String p = d.param("roof", "");
		if (p.isEmpty()) {
			return chosen;
		}
		try {
			return RoofForm.valueOf(p.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return chosen;
		}
	}

	private record Fit(RoofForm form, int groundH, int loftH) {
	}

	/** Hay stacked on a flat roof under a shade of cloth on posts, with a ladder up the side wall. */
	private static void roofHay(Draft d, Barns.Hall h) {
		int deck = h.deck();
		if (deck == Integer.MIN_VALUE || deck + 3 >= d.maxHeight()) {
			return;
		}
		var fence = d.mat(Role.FENCE).has(dev.voidmc.hearth.build.style.Form.FENCE) ? d.mat(Role.FENCE) : d.mat(Role.FRAME);
		var cloth = d.mat(Role.CLOTH);
		int x0 = h.x0() + 1;
		int x1 = h.x1() - 1;
		int z0 = (h.z0() + h.z1()) / 2;
		int z1 = Math.min(h.z1() - 1, z0 + 3);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (Noise.unit(d.seed() ^ 0x4AF, x, 0, z) < 0.55) {
						d.set(x, deck, z, Blocks.HAY_BLOCK.defaultBlockState());
					}
				}
			}
			for (int[] p : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				d.set(p[0], deck, p[1], fence.fence());
				d.set(p[0], deck + 1, p[1], fence.fence());
			}
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, deck + 2, z, cloth.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				}
			}
		}
	}

	/** The tallest of: the chosen roof and loft, a lower loft, a plain gable, a lower storey, that clears the plot's height. */
	private static Fit fit(Draft d, RoofForm form, int bw, int groundH, int loftH) {
		RoofForm[] forms = {form, form == RoofForm.GAMBREL ? RoofForm.GABLE : form, RoofForm.FLAT};
		for (RoofForm f : forms) {
			for (int lh = loftH; lh >= 1; lh--) {
				for (int gh = groundH; gh >= 4; gh--) {
					int plate = gh + lh - 1;
					if (f == RoofForm.FLAT) {
						// a flat-roofed byre: one tall storey, hay kept on the roof
						if (gh + 3 < d.maxHeight()) {
							return new Fit(f, gh, 0);
						}
						continue;
					}
					if (ridge(d, f, bw, plate) + 2 < d.maxHeight()) {
						return new Fit(f, gh, lh);
					}
				}
			}
		}
		return new Fit(RoofForm.FLAT, 4, 0);
	}

	/** Where a roof of this form over a hall {@code bw} wide with its plate at {@code plate} puts its ridge (approximately). */
	static int ridge(Draft d, RoofForm f, int bw, int plate) {
		double pitch = d.habits().roof().pitch();
		int eaves = Math.max(1, Math.min(2, d.habits().roof().eaves()));
		if (f == RoofForm.GAMBREL) {
			pitch = Math.max(0.9, pitch);
		} else if (pitch < 0.75) {
			pitch = 0.75;
		}
		double span = bw + 2.0 * eaves;
		double dmax = (span - 1) / 2.0;
		double steep = Math.max(1.5, pitch * 2);
		double hWall;
		double hTop;
		if (f == RoofForm.GAMBREL || f == RoofForm.MANSARD) {
			double knee = eaves + 2;
			hWall = 1 + steep * Math.min(eaves, knee);
			hTop = 1 + steep * Math.min(dmax, knee) + 0.5 * Math.max(0, dmax - knee);
		} else {
			hWall = 1 + pitch * eaves;
			hTop = 1 + pitch * dmax;
		}
		int tWall = (int) Math.round(hWall * 2);
		int y0 = plate + 1 - ((tWall + 1) / 2 - 1);
		int t = (int) Math.round(hTop * 2);
		return y0 + (t + 1) / 2 - 1;
	}

	/** The yard before the doors: a water trough to one side, a rick or a muck heap to the other, a lamp at the gate. */
	private static void yard(Draft d, Barns.Hall h, int w, int z0, int y) {
		int mid = h.midX();
		// the cart way to the doors
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < z0; z++) {
				for (int x = mid - 1; x <= mid + 1; x++) {
					if (Spots.free(d, x, y, z) && Garden.groundLike(d, x, y - 1, z)) {
						d.set(x, y - 1, z, (Noise.unit(d.seed() ^ 0xCA7, x, 0, z) < 0.5 ? Blocks.COARSE_DIRT : d.mat(Role.PATH).main(dev.voidmc.hearth.build.style.Form.FULL))
							.defaultBlockState());
					}
				}
			}
		}
		boolean troughLeft = d.chance(0.5);
		int tz = z0 - 1;
		// water by the doors: a stone or wooden trough against the wall
		int tx = troughLeft ? mid - 4 : mid + 3;
		if (tx >= h.x0() + 1 && tx + 1 <= h.x1() - 1) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int k = 0; k < 2; k++) {
					d.set(tx + k, y, tz, Blocks.WATER_CAULDRON.defaultBlockState()
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3));
				}
			}
			Spots.at(d, PoiType.JOB_SITE, tx, y, tz - 1, Dirs.BACK, JobType.HERDER);
		}
		int ox = troughLeft ? mid + 3 : mid - 4;
		if (ox >= 1 && ox + 1 <= w - 2 && z0 >= 3) {
			if (d.chance(0.5)) {
				Yard.dungHeap(d, ox, y, tz, Dirs.RIGHT);
			} else {
				Crops.rick(d, ox, y, tz - 1, Dirs.RIGHT, 2);
			}
		}
		if (z0 >= 2 && w >= 11) {
			Garden.lampPost(d, troughLeft ? w - 2 : 1, y, 0, 2, null);
		}
	}
}
