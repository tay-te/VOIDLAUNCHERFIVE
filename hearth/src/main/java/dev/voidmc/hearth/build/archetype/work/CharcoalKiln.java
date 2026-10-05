package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.industry.Huts;
import dev.voidmc.hearth.build.kit.industry.Mound;
import dev.voidmc.hearth.build.kit.industry.Timber;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * {@code hearth:work/charcoal_kiln}: the charcoal burner's clearing at the wood's edge. A trodden lane runs in from
 * the track. On one side of it the clamp smoulders, a low round hill of earth and turf breathing smoke from its crown,
 * the ground about it black with cinders; behind it the next clamp going up (billets stood on end round a centre pole,
 * the covering going on from one side) or the last one raked open into a black heap of charcoal. On the other side cords
 * of billets wait between their stakes, and at the back stands the burner's hut, poles leant together into a steep
 * A-frame shingled with bark down to the ground (a round mud hut in the thatch cultures, a little cell of the culture's
 * walls elsewhere), its door on the lane, his bed and his lamp inside. Barrels of charcoal by the track wait for the
 * smelter and the smith.
 *
 * <p>Job sites (CHARCOAL_BURNER): on the lane at each clamp (tending its vents), at the cordwood. Storage: barrels of
 * charcoal by the track, the chest in the hut. The hut has a bed.
 *
 * <p>Params: {@code clamps} (1 or 2: a second clamp building behind the burning one, else the last raked out; default by
 * seed), {@code variant}.
 */
public final class CharcoalKiln implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/charcoal_kiln"), ArchetypeCategory.WORKPLACE, Set.of(JobType.CHARCOAL_BURNER),
		0, 11, 14, 10, 13, 6, 9, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

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
		boolean hutLeft = d.chance(0.5);
		// the lane in from the track: the entrance column, all the way back
		int lane = ex;
		Direction toHut = hutLeft ? Dirs.LEFT : Dirs.RIGHT;
		Direction toClamp = toHut.getOpposite();
		int hx0 = hutLeft ? 0 : lane + 1;
		int hx1 = hutLeft ? lane - 1 : w - 1;
		int cx0 = hutLeft ? lane + 1 : 0;
		int cx1 = hutLeft ? w - 1 : lane - 1;
		Mat logs = Works.logs(d);

		// the clearing's floor trodden, the lane worn into it
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		List<int[]> laneCells = new ArrayList<>();
		for (int z = 0; z < dp; z++) {
			laneCells.add(new int[]{lane, z});
		}
		Garden.path(d, laneCells, 0);

		// the burning clamp at the front of its side, the second at the back
		double span = cx1 - cx0 + 1;
		double mr = Math.max(2.0, Math.min(2.8, (span - 1.2) / 2.0));
		double mcx = (cx0 + cx1) / 2.0 + (hutLeft ? 0.3 : -0.3);
		double mcz = 1.0 + mr;
		int[] crown = Mound.clamp(d, mcx, mcz, mr, mr >= 2.5 ? 4 : 3, 0, Mound.State.BURNING);
		int top = crown[2];
		double br = Math.min(2.0, (span - 1.5) / 2.0);
		double bcz = dp - 1.2 - br;
		boolean second = bcz - br > mcz + mr + 0.4;
		if (second) {
			Mound.State st = d.param("clamps", d.chance(0.6) ? 2 : 1) >= 2 ? Mound.State.BUILDING : Mound.State.RAKED;
			int[] c2 = Mound.clamp(d, (cx0 + cx1) / 2.0, bcz, br, 3, 0, st);
			top = Math.max(top, c2[2] + 2);
		}

		// the hut at the back of the other side, its door on the lane
		Huts.Kind kind = Huts.kind(d);
		Huts.Hut hut = null;
		int hutZ0 = dp - 5;
		if (hx1 - hx0 + 1 >= 5) {
			int hcx = hutLeft ? hx1 - 2 : hx0 + 2;
			int hcz = dp - 3;
			hut = switch (kind) {
				case CONE -> Huts.cone(d, hcx, hcz, toClamp, d.chance(0.3));
				case ROUND_MUD -> Huts.mud(d, hcx, hcz, 2.0, toClamp);
				case CELL -> Huts.cell(d, hutLeft ? hx1 - 4 : hx0, dp - 5, hutLeft ? hx1 : hx0 + 4, dp - 1, hutLeft ? hx1 - 2 : hx0 + 2);
			};
			if (kind == Huts.Kind.CELL) {
				// the cell's door is in its front wall: keep the cell before it open to the lane
				hutZ0 = dp - 6;
			}
		}
		if (hut != null) {
			top = Math.max(top, hut.top() + 1);
		}

		// cords of billets along the hut's side toward the track
		int edge = hutLeft ? hx0 : hx1;
		int cordLen = Math.min(5, hutZ0 - 2);
		if (cordLen >= 2) {
			Timber.cord(d, edge, 0, 2, Dirs.BACK, cordLen, 2, logs);
			if (hx1 - hx0 >= 4) {
				Timber.cord(d, edge + (hutLeft ? 2 : -2), 0, 2, Dirs.BACK, Math.max(2, cordLen - 1), 2, logs);
			}
		}
		// the charcoal: barrels by the track on the clamp's side
		int bx = hutLeft ? w - 1 : 0;
		if (Math.abs(bx - lane) > 1) {
			pois.store(d, bx, 0, 0, toHut, false, Works.items("minecraft:charcoal", 64, "minecraft:charcoal", 64));
		}
		int bx2 = bx + Dirs.dx(toHut);
		if (Math.abs(bx2 - lane) > 1 && d.isEmpty(bx2, 0, 0)) {
			pois.store(d, bx2, 0, 0, Dirs.BACK, false, Works.items("minecraft:charcoal", 48));
		}

		// the burner's places: on the lane at each clamp, and between the cords
		pois.siteIf(d, lane, 0, (int) Math.round(mcz), toClamp, JobType.CHARCOAL_BURNER);
		if (second) {
			pois.siteIf(d, lane, 0, (int) Math.round(bcz), toClamp, JobType.CHARCOAL_BURNER);
		}
		int cordSite = edge + (hutLeft ? 1 : -1);
		if (cordLen < 2 || !pois.siteIf(d, cordSite, 0, 2 + cordLen / 2, toHut, JobType.CHARCOAL_BURNER)) {
			pois.siteIf(d, lane, 0, 1, toHut, JobType.CHARCOAL_BURNER);
		}

		// stumps of the trees the burner has felled about the clearing's edge, a water butt by the clamp
		for (int i = 0; i < 5; i++) {
			int x = d.between(0, w - 1);
			int z = d.between(1, dp - 1);
			boolean onEdge = x == 0 || x == w - 1 || z == dp - 1;
			if (onEdge && Math.abs(x - lane) > 1 && (hut == null || z < hutZ0 - 1)) {
				Timber.stump(d, x, 0, z, logs);
			}
		}
		int wx = lane + Dirs.dx(toClamp);
		int wz = (int) Math.round(mcz + mr + 1);
		if (Works.in(d, wx, wz) && (!second || wz < bcz - br - 0.5) && d.isEmpty(wx, 0, wz) && d.isEmpty(wx, 1, wz)) {
			Props.trough(d, wx, 0, wz, Dirs.BACK, 1);
		}

		// the sign at the track, a lamp by the hut's door
		int px = lane + Dirs.dx(toHut) * 2;
		if (Works.in(d, px, 0) && d.isEmpty(px, 0, 0) && d.isEmpty(px, 0, 1)) {
			Signs.post(d, px, 0, 0, toClamp, 3, "charcoal_kiln", "Charcoal");
		}
		if (hut != null) {
			int lz = hut.outZ() - 1;
			int lx = lane + Dirs.dx(toHut);
			if (lz >= 1 && Works.in(d, lx, lz) && d.isEmpty(lx, 0, lz) && d.isEmpty(lx, 1, lz)) {
				Works.lampPost(d, lx, 0, lz);
			}
		}
		Works.keepDoors(d);
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{w / 2, 0, 1}, new int[]{w / 2, 0, 0});
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 2));
	}
}
