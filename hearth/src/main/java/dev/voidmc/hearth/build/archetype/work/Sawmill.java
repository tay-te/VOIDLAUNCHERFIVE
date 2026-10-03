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
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Mills;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/sawmill}: where the foresters' timber becomes boards. A long saw shed on posts across the back of
 * the yard, boarded at its back, its roof's gables to the sides; down its length runs the saw bench, the log carriage
 * on its rails feeding the blade, sawn boards stacking up beyond it. The yard before it is stacked with logs on bearers
 * in great pyramids, a loaded log cart, a chopping block and stickered boards; a fence and a wide gate keep it. Where
 * water runs at the back of the plot, a water wheel on the shed's end drives the saw.
 *
 * <p>Job sites: FORESTER at the log carriage and the log piles, CARPENTER at the saw and the bench. Storage: a chest in
 * the shed and the log piles.
 *
 * <p>Params: {@code roof}, {@code variant}.
 */
public final class Sawmill implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/sawmill"), ArchetypeCategory.WORKPLACE,
		Set.of(JobType.FORESTER, JobType.CARPENTER), 0, 9, 13, 9, 13, 7, 11, SettlementTier.HAMLET, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.PAGODA, RoofForm.SALTBOX};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		RoofForm form = Hall.pick(d, FORMS);
		int gate = w / 2;
		Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);

		// the saw shed across the back
		int sz1 = dp - 1;
		int sz0 = dp - 5;
		int sx0 = 1;
		int sx1 = w - 2;
		Groundwork.footings(d, sx0, sz0, sx1, sz1, -1);
		Mat floor = d.mat(Role.FLOOR);
		Shed.deck(d, sx0 + 1, sz0 + 1, sx1 - 1, sz1 - 1, -1, floor);
		Roofs.Roof roof = Shed.open(d, sx0, sz0, sx1, sz1, 0, 4, form, Direction.Axis.X, 1, Dirs.BACK);
		d.carve(sx0 + 1, 0, sz0 + 1, sx1 - 1, 3, sz1 - 1);
		d.body(sx0, sz0, sx1, sz1);
		d.eave(4);
		bench(d, sx0, sx1, (sz0 + sz1) / 2);

		// water at the back: a wheel on the shed's end, dipping into a race
		boolean water = false;
		for (int x = 0; x < w && !water; x++) {
			for (int z = dp - 2; z <= dp + 2 && !water; z++) {
				water = d.site().isWater(x, z);
			}
		}
		if (water && w >= 11) {
			wheel(d, w, sz0, sz1);
		}

		// the log yard: piles on bearers, a cart, boards, a chopping block
		yard(d, w, sz0, gate);
		List<int[]> gaps = new ArrayList<>();
		for (int x = gate - 1; x <= gate + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, sz0, gaps, Dirs.BACK);
		Signs.post(d, gate + 2, 0, 0, Dirs.LEFT, 3, "sawmill", "Sawmill");
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, (roof != null ? roof.ridgeY : 6) + 3);
	}

	/**
	 * The saw bench down the shed: rails carrying the log carriage, a log on it, the blade (a stonecutter) in the middle,
	 * sawn boards stacking up beyond, the sawyer's bench and chest against the back wall.
	 */
	private static void bench(Draft d, int x0, int x1, int z) {
		Mat log = d.mat(Role.FRAME);
		Mat boards = d.mat(Role.FLOOR);
		int blade = (x0 + x1) / 2;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				if (x == blade) {
					d.set(x, 0, z, States.facing(Blocks.STONECUTTER, Dirs.FRONT));
				} else if (x < blade) {
					d.set(x, 0, z, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.EAST_WEST));
				} else {
					d.set(x, 0, z, boards.slab(SlabType.BOTTOM));
				}
			}
			// the log on its carriage, nosing toward the blade
			for (int x = Math.max(x0 + 1, blade - 3); x < blade; x++) {
				d.set(x, 1, z, log.log(Direction.Axis.X));
			}
			// sawn boards beyond
			for (int x = blade + 1; x <= Math.min(x1 - 1, blade + 2); x++) {
				d.set(x, 1, z, boards.slab(SlabType.BOTTOM));
			}
		}
		d.poi(PoiType.JOB_SITE, blade, 0, z - 1, Dirs.BACK, JobType.CARPENTER);
		d.poi(PoiType.JOB_SITE, Math.max(x0 + 1, blade - 2), 0, z - 1, Dirs.BACK, JobType.FORESTER);
		// against the back wall: the bench, the chest, a rack of saws
		int bz = z + 1;
		{
			int cx = x0 + 1;
			if (d.isEmpty(cx, 0, bz + 1)) {
				Props.store(d, cx, 0, bz + 1, Dirs.FRONT, true, List.of(new Object[]{"minecraft:oak_planks", 64}, new Object[]{"minecraft:spruce_log", 32},
					new Object[]{"minecraft:iron_axe", 2}));
			}
			int tx = x1 - 1;
			if (d.isEmpty(tx, 0, bz + 1)) {
				Furniture.workstation(d, JobType.CARPENTER, tx, 0, bz + 1, Dirs.FRONT);
				Props.toolRack(d, tx, 2, bz + 1, Dirs.FRONT, List.of(new Object[]{"minecraft:iron_axe", 1, 0}, new Object[]{"minecraft:shears", 1, 1},
					new Object[]{"minecraft:iron_axe", 1, 2}));
			}
		}
	}

	/** A water wheel on the shed's end, its axle into the shed, turning in a stone race fed from the water behind. */
	private static void wheel(Draft d, int w, int sz0, int sz1) {
		boolean right = d.chance(0.5);
		int x = right ? w - 1 : 0;
		int cz = (sz0 + sz1) / 2;
		Mat stone = d.mat(Role.FOUNDATION);
		// the race: a channel along the end under the wheel, from the back
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = cz - 3; z < d.depth(); z++) {
				if (z < 0) {
					continue;
				}
				d.set(x, -3, z, stone.full());
				d.set(x, -2, z, Blocks.WATER.defaultBlockState());
				d.set(x, -1, z, Blocks.WATER.defaultBlockState());
				d.set(x, 0, z, Blocks.AIR.defaultBlockState());
			}
		}
		Mat rim = d.mat(Role.FRAME);
		Mat spoke = Hall.woodFence(d);
		Mat paddle = d.mat(Role.FLOOR);
		Mills.waterWheel(d, x, 1, cz, Direction.Axis.X, 2, 1, rim, spoke, paddle, null);
		// the axle into the shed
		int ax = right ? x - 1 : x + 1;
		d.inStage(StageKind.FRAME, () -> d.set(ax, 1, cz, rim.log(Direction.Axis.X)));
	}

	private static void yard(Draft d, int w, int sz0, int gate) {
		Yard.trodden(d, 1, 1, w - 2, sz0 - 1, 0);
		Mat log = d.mat(Role.FRAME);
		Mat tree = d.mat(Role.TREE_LOG);
		int depth = sz0 - 1;
		// two log piles either side of the cart track, lying across
		int len = Math.min(4, Math.max(2, depth - 2));
		if (gate - 2 >= 2) {
			Props.logPile(d, 1, 0, 1, Direction.Axis.Z, len, Math.min(3, gate - 2), 3, log);
			d.poi(PoiType.JOB_SITE, Math.min(3, gate - 2) + 1, 0, 1, Dirs.LEFT, JobType.FORESTER);
		}
		int rx = gate + 2;
		int rw = Math.min(3, w - 2 - rx);
		if (rw >= 1) {
			Props.logPile(d, rx, 0, 1, Direction.Axis.Z, len, rw, 3, tree.has(dev.voidmc.hearth.build.style.Form.LOG) ? tree : log);
		}
		// a log cart on the track before the shed
		int cz = sz0 - 2;
		if (cz >= 2 && d.isEmpty(gate, 0, cz) && d.isEmpty(gate - 1, 0, cz) && d.isEmpty(gate + 1, 0, cz) && d.isEmpty(gate, 0, cz - 1)) {
			Props.cart(d, gate, 0, cz, Dirs.FRONT, log.log(Direction.Axis.Z).at(gate, 1, cz));
		}
		// boards and a chopping block by the shed
		if (depth >= 5) {
			Props.boardStack(d, 1, 0, sz0 - 1, Direction.Axis.X, Math.min(3, gate - 2), 2, d.mat(Role.FLOOR));
			Props.choppingBlock(d, w - 2, 0, sz0 - 1);
			Garden.woodpile(d, w - 2, 0, sz0 - 2, Dirs.FRONT, Math.min(2, depth - 4));
		}
	}
}
