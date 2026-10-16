package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.industry.Fam;
import dev.voidmc.hearth.build.kit.industry.Glass;
import dev.voidmc.hearth.build.kit.industry.Heap;
import dev.voidmc.hearth.build.kit.industry.Stack;
import dev.voidmc.hearth.build.kit.industry.Timber;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * {@code hearth:work/glassworks}: the glasshouse, Bohemia's pride. A tall hall of the culture's walls with its gable to
 * the street and a wide doorway, so the glow of the furnace shows from the lane; inside, in the middle of the floor, the
 * round furnace of brick, three courses and a dome, its glory holes glowing on three sides where the gaffers stand at
 * their marvers, the fire's mouth at its foot, its flue rising through the roof as a tall round-cornered chimney,
 * smoking. Along the back wall the annealing oven where the finished glass cools; racks of blowpipes, a trough, chests
 * of glassware, barrels of sand and potash. In the yard before it racks of sheet glass catch the light, clear with a
 * green cast and the odd blue sheet; down the side the wood the furnace eats in cords, the sand heap, and the cullet heap
 * of broken glass glittering by the wall.
 *
 * <p>Job sites (GLASSMAKER): at each glory hole, at the annealing oven. Storage: chests of glassware, barrels of sand.
 *
 * <p>Params: {@code roof} (a roof form name), {@code variant}.
 */
public final class Glassworks implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/glassworks"), ArchetypeCategory.WORKPLACE, Set.of(JobType.GLASSMAKER), 0,
		10, 14, 12, 16, 10, 16, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.PAGODA, RoofForm.SALTBOX, RoofForm.FLAT};

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
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		boolean yardLeft = d.chance(0.5);
		RoofForm form = Hall.pick(d, FORMS);

		// the hall: all but a strip down one side and the yard in front (a cell wider beside a log wall, whose corner
		// ends stand out across the strip's mouth)
		int strip = d.habits().walls().kind() == dev.voidmc.hearth.build.style.Habits.WallKind.LOG && w >= 11 ? 3 : 2;
		int hx0 = yardLeft ? strip : 0;
		int hx1 = yardLeft ? w - 1 : w - 1 - strip;
		int hz0 = 3;
		int hz1 = dp - 1;
		int[] heights = Hall.budget(d, hx0, hz0, hx1, hz1, 0, new int[]{6}, false, form, 2, Direction.Axis.Z);
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, heights, false, false);
		int[] in = hall.inner(0);
		int bayW = in[2] - in[0] + 1 >= 7 ? 3 : 2;
		int mid = (in[0] + in[2]) / 2;
		int bay = mid - bayW / 2;
		hall.facade(-1, 1, 1, 0.35).ground(false).shell().openings(false);
		hall.bay(Dirs.FRONT, bay, bayW, 3);
		d.poi(dev.voidmc.hearth.api.PoiType.DOOR, mid, 0, hz0 - 1, Dirs.BACK);
		hall.roof(form, Direction.Axis.Z, 2, null);

		// the furnace in the middle of the floor, its glory holes toward the door and the side walls
		int iw = in[2] - in[0] + 1;
		int id = in[3] - in[1] + 1;
		int r = iw >= 9 && id >= 8 ? 2 : 1;
		int fcx = mid;
		int fcz = in[1] + Math.max(r + 2, (id - 2) / 2);
		fcz = Math.min(fcz, in[3] - r - 2);
		Fam brick = Works.tradition(d) == Works.Tradition.EARTH || Works.tradition(d) == Works.Tradition.THATCH ? Fam.mudBrick() : Fam.brick();
		Glass.Furnace furnace = Glass.furnace(d, fcx, fcz, r, brick, Dirs.FRONT, Dirs.LEFT, Dirs.RIGHT);
		int top = Stack.round(d, fcx, fcz, furnace.top() + 1, d.maxHeight() - 1, brick, brick, hall.roof, false);
		if (top == Integer.MIN_VALUE) {
			top = hall.top;
		}

		// the floor: the furnace and its gaffers, the annealing oven at the back, racks and stores round the walls
		Floorplan fp = hall.floor(0).blockHoles(d);
		for (int c = bay; c < bay + bayW; c++) {
			fp.keep(c, in[1]);
		}
		for (int[] h : furnace.holes()) {
			if (fp.inside(h[0], h[1])) {
				fp.keep(h[0], h[1]);
				Direction face = Direction.from2DDataValue(h[2]).getOpposite();
				pois.siteIf(d, h[0], 0, h[1], face, JobType.GLASSMAKER);
			}
		}
		// the annealing oven along the back wall, behind the furnace
		int lz = in[3];
		int lehr = 0;
		for (int x = fcx - 1; x <= fcx + 1; x++) {
			if (fp.take(x, lz, x == fcx ? Dirs.FRONT : null)) {
				try (var s = d.stage(StageKind.INTERIOR)) {
					d.set(x, 0, lz, States.facing(Blocks.SMOKER, Dirs.FRONT).trySetValue(BlockStateProperties.LIT, x == fcx));
				}
				try (var s = d.stage(StageKind.WALLS)) {
					d.set(x, 1, lz, brick.full());
					d.set(x, 2, lz, brick.stairsOrSlab(Dirs.BACK, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
				}
				lehr++;
			}
		}
		if (lehr > 0) {
			pois.siteIf(d, fcx, 0, lz - 1, Dirs.BACK, JobType.GLASSMAKER);
		}
		// marvers beside the gaffers' places, blowpipes against the side walls, a trough
		Mat wood = d.mat(Role.FURNITURE);
		for (int[] h : furnace.holes()) {
			Direction face = Direction.from2DDataValue(h[2]);
			Direction along = Dirs.right(face);
			int mx = h[0] + Dirs.dx(along);
			int mz = h[1] + Dirs.dz(along);
			if (fp.take(mx, mz, null)) {
				Glass.marver(d, mx, 0, mz);
			}
		}
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			List<int[]> cells = fp.along(wall);
			int placed = 0;
			for (int[] c : cells) {
				if (placed >= 2 || c[1] == in[1] || c[1] == in[3]) {
					continue;
				}
				if (fp.take(c[0], c[1], null)) {
					if (placed == 0) {
						Glass.pipes(d, c[0], 0, c[1]);
					} else {
						Props.trough(d, c[0], 0, c[1], Dirs.BACK, 1);
					}
					placed++;
				}
			}
		}
		// stores: a chest of glassware and a barrel of sand by the walls
		boolean chest = false;
		for (Direction wall : new Direction[]{Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
			for (int[] c : fp.along(wall)) {
				if (!chest && fp.take(c[0], c[1], wall.getOpposite())) {
					pois.store(d, c[0], 0, c[1], wall.getOpposite(), true, Works.items("minecraft:glass", 64, "minecraft:glass_pane", 64,
						"minecraft:glass_bottle", 32, "minecraft:green_stained_glass_pane", 32));
					chest = true;
				}
			}
		}
		if (!chest) {
			Props.storeAnywhere(d, fp, Works.items("minecraft:glass", 64, "minecraft:glass_pane", 64));
		}
		for (int[] c : fp.along(Dirs.BACK)) {
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				pois.store(d, c[0], 0, c[1], Dirs.FRONT, false, Works.items("minecraft:sand", 64, "minecraft:bone_meal", 16));
				break;
			}
		}
		Lighting.room(d, fp.room());

		// the yard before the hall: racks of sheet glass either side of the way in
		Mat frame = d.mat(Role.FRAME);
		if (ex - 2 >= 2) {
			Glass.sheets(d, Math.max(0, ex - 5), 0, 1, Dirs.RIGHT, Math.min(4, ex - 1), 2, frame);
		}
		if (w - 1 - (ex + 2) >= 2) {
			Glass.sheets(d, ex + 2, 0, 1, Dirs.RIGHT, Math.min(4, w - 1 - (ex + 2) + 1), 2, frame);
		}
		// down the side strip: cords of wood for the furnace, the sand heap, the cullet heap at the back
		int sx = yardLeft ? 0 : w - 1;
		if (dp - 1 - 4 >= 2) {
			Timber.cord(d, sx, 0, 4, Dirs.BACK, Math.min(4, dp - 9), 3, Works.logs(d));
		}
		double hx = yardLeft ? 0.6 : w - 1.6;
		Heap.heap(d, hx, dp - 2.0, 1.1, 1.6, 1.6, 0, Glass.cullet(), null, 81);
		Heap.heap(d, yardLeft ? 0.6 : w - 1.6, dp - 5.5, 1.0, 1.2, 1.2, 0, new net.minecraft.world.level.block.Block[]{Blocks.SAND, Blocks.SAND,
			Blocks.SANDSTONE}, null, 83);

		// the sign out over the lane, a lamp at the gate
		Side front = hall.side(0, Dirs.FRONT);
		int si = front.index(bay + bayW) + 1;
		if (si <= front.last() - 1) {
			Signs.bracket(d, front, si, 3, "glassworks", "Glassworks");
		}
		Works.keepDoors(d);
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{w / 2, 0, 1}, new int[]{w / 2, 0, 0});
		// a rail wherever the levelled yard ends over a drop
		dev.voidmc.hearth.build.kit.craft.Rails.yard(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(top, hall.top) + 1);
	}
}
