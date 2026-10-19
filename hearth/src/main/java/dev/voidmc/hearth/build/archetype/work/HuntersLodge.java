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
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Pentice;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/hunters_lodge}: the hunters' lodge at the forest's edge. A low lodge under a steep roof whose eave
 * runs on over a deep porch; a skull hangs over the door between antlers of bleached wood, hides are stretched to dry
 * on a frame along the porch, and smoke drifts from the smoking rack in the yard where the meat hangs over a slow fire.
 * An archery butt stands at the end of the yard, a chopping block and the woodpile by the wall. Inside, the fletcher's
 * bench, furs on the floor, a store of arrows and hides, a fire in the hearth.
 *
 * <p>Job sites (HUNTER): the fletching table, the smoking rack, the skinning bench. Storage: the chest of hides. A
 * HEARTH by the fire.
 *
 * <p>Params: {@code roof}, {@code variant}.
 */
public final class HuntersLodge implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/hunters_lodge"), ArchetypeCategory.WORKPLACE, Set.of(JobType.HUNTER), 0,
		7, 10, 7, 10, 6, 10, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.PAGODA, RoofForm.SALTBOX, RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean logs = dev.voidmc.hearth.build.kit.refine.LogWalls.suits(d);
		// a log lodge sits under a steep gable turned to the clearing; elsewhere the culture's own roof
		RoofForm form = logs ? Hall.pick(d, RoofForm.GABLE, RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.SALTBOX) : Hall.pick(d, FORMS);
		boolean flat = form == RoofForm.FLAT;
		int fz = dp >= 9 ? 3 : 2;
		int side = w >= 9 ? 3 : 0;
		boolean yardLeft = d.chance(0.5);
		int hx0 = yardLeft ? side : 0;
		int hx1 = yardLeft ? w - 1 : w - 1 - side;
		int hz0 = fz;
		int hz1 = dp - 1;
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, new int[]{Math.max(4, h.walls().storeyHeight())}, false, false);
		int[] in = hall.inner(0);
		int door = (in[0] + in[2]) / 2;
		hall.facade(door, 1, 1, 0.35);
		door = hall.door != null ? hall.door : door;
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		hall.ground(false).shell().openings(true);
		if (logs) {
			// round logs course on course, notched at the corners, their ends standing out
			int[] wr = hall.rect(0);
			dev.voidmc.hearth.build.kit.refine.LogWalls.lay(d, wr[0], wr[1], wr[2], wr[3], Math.max(0, h.walls().plinth()), hall.plateY(),
				dev.voidmc.hearth.build.kit.refine.LogWalls.log(d));
		}
		Direction.Axis ridge = logs || d.chance(0.35) ? Direction.Axis.Z : Direction.Axis.X;
		hall.roof(form, ridge, 2, null);
		if (flat) {
			hall.crown();
		}
		// the hearth on the back wall, its chimney
		int hx = door > (in[0] + in[2]) / 2 ? in[0] + 1 : in[2] - 1;
		Furniture.hearth(d, hx, 0, in[3], Dirs.FRONT, true);
		hall.chimney(hx, in[3], 1);

		// the porch: the eave carried on over the front, hides drying on a frame along it
		Side front = hall.side(0, Dirs.FRONT);
		int[] r = hall.rect(0);
		Roofs.Roof porch = null;
		if (!flat && hall.roof != null) {
			porch = Pentice.over(d, hall.roof, front, r[0], r[2], fz + 1, 0, door);
		}
		if (porch == null) {
			Pentice.awning(d, front, r[0], r[2], Math.min(2, fz), 0, 3, false, door);
		}
		closeGable(d, hall);
		trophy(d, front, door);
		hides(d, hall, door, fz);

		interior(d, hall, door, hx);
		int[] rack = yard(d, hall, w, dp, side, yardLeft, fz);
		Yard.trodden(d, 0, 0, w - 1, fz - 1, 0);
		if (h.snow()) {
			Garden.snowBanks(d, r[0], r[1], r[2], r[3], 0, door);
		}
		Yard.connect(d, ex, door, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		// a corner end at a walker's feet may not bar the yard
		if (logs) {
			dev.voidmc.hearth.build.kit.refine.LogWalls.keepWays(d, r[0], r[1], r[2], r[3], Math.max(0, h.walls().plinth()));
		}
		// a rail wherever the levelled yard ends over a drop
		dev.voidmc.hearth.build.kit.craft.Rails.yard(d);
		if (rack != null) {
			tend(d, rack[0], rack[1], rack[2] == 1, fz);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/**
	 * The front gable closed up to the roof: boards in every cell of the triangle the roof and the porch left open (it
	 * read as a hole in the roof with the trophies seen through it), a little window in its middle.
	 */
	private static void closeGable(Draft d, Hall hall) {
		if (hall.roof == null) {
			return;
		}
		int[] r = hall.rect(0);
		int z = r[1];
		int mid = (r[0] + r[2]) / 2;
		Mat boards = d.mat(Role.GABLE).has(Form.FULL) ? d.mat(Role.GABLE) : d.mat(Role.WALL);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = r[0]; x <= r[2]; x++) {
				if (!hall.roof.covers(x, z)) {
					continue;
				}
				int under = hall.roof.bottomY(x, z);
				for (int y = hall.plateY() + 1; y < under; y++) {
					if (d.isEmpty(x, y, z)) {
						d.set(x, y, z, x == mid && y == hall.plateY() + 2 ? d.mat(Role.GLASS).pane() : boards.full());
					}
				}
			}
		}
	}

	/** Over the door: a skull mounted on the wall between two antlers of bleached wood. */
	private static void trophy(Draft d, Side front, int door) {
		int i = front.index(door);
		int y = 3;
		int x = front.outX(i, 1);
		int z = front.outZ(i, 1);
		if (!d.allowed(x, y, z) || !d.isEmpty(x, y, z) || !dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, front.x(i), y, front.z(i))) {
			return;
		}
		d.inStage(StageKind.EXTERIOR, () -> {
			d.set(x, y, z, States.facing(Blocks.SKELETON_WALL_SKULL, Dirs.FRONT));
			// the antlers: bleached fence branches either side, a tine above
			for (int k : new int[]{-1, 1}) {
				int ax = front.outX(i + k, 1);
				int az = front.outZ(i + k, 1);
				if (d.allowed(ax, y, az) && d.isEmpty(ax, y, az) && d.allowed(ax, y + 1, az) && d.isEmpty(ax, y + 1, az)) {
					d.set(ax, y, az, Blocks.BIRCH_FENCE.defaultBlockState());
					d.set(ax, y + 1, az, Blocks.BIRCH_FENCE.defaultBlockState());
				}
			}
		});
	}

	/** A frame of poles along the porch with hides stretched on it (banners of brown, white and grey). */
	private static void hides(Draft d, Hall hall, int door, int fz) {
		int[] r = hall.rect(0);
		int z = 0;
		boolean right = door <= (r[0] + r[2]) / 2;
		int x0 = right ? door + 2 : r[0];
		int x1 = right ? r[2] : door - 2;
		if (x1 - x0 < 1 || fz < 2) {
			return;
		}
		Mat post = Hall.woodFence(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = x0; x <= x1; x++) {
				if (!d.isEmpty(x, 0, z) || !d.isEmpty(x, 2, z)) {
					return;
				}
			}
			for (int x : new int[]{x0, x1}) {
				d.set(x, 0, z, post.fence());
				d.set(x, 1, z, post.fence());
			}
		}
		Props.clothRail(d, x0, 2, z, Dirs.RIGHT, x1 - x0 + 1, Dirs.BACK, "brown", "white", "light_gray", "brown");
	}

	private static void interior(Draft d, Hall hall, int door, int hx) {
		int[] in = hall.inner(0);
		Floorplan fp = hall.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		fp.block(hx, in[3]);
		fp.keep(hx, in[3] - 1);
		d.poi(PoiType.SEAT, hx, 0, in[3] - 1, Dirs.BACK);
		// the fletcher's bench and the skinning bench along the side walls
		int placed = 0;
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			for (int[] c : fp.along(wall)) {
				if (placed >= 2 || c[1] == in[1]) {
					continue;
				}
				Direction into = wall.getOpposite();
				if (fp.take(c[0], c[1], into)) {
					if (placed == 0) {
						Props.station(d, Blocks.FLETCHING_TABLE, c[0], 0, c[1], into, JobType.HUNTER);
					} else {
						Props.station(d, Blocks.CRAFTING_TABLE, c[0], 0, c[1], into, JobType.HUNTER);
					}
					placed++;
					break;
				}
			}
		}
		for (int[] c : fp.along(Dirs.BACK)) {
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				Props.store(d, c[0], 0, c[1], Dirs.FRONT, true, List.of(new Object[]{"minecraft:leather", 12}, new Object[]{"minecraft:rabbit_hide", 8},
					new Object[]{"minecraft:arrow", 32}, new Object[]{"minecraft:bow", 2}, new Object[]{"minecraft:cooked_beef", 8}));
				Props.toolRack(d, c[0], 2, c[1], Dirs.FRONT, List.of(new Object[]{"minecraft:bow", 1, 0}, new Object[]{"minecraft:arrow", 8, 1},
					new Object[]{"minecraft:leather", 2, 2}));
				break;
			}
		}
		// furs on the floor
		Furniture.rug(d, Math.min(in[2], in[0] + 1), Math.min(in[3], in[1] + 1), Math.max(in[0], in[2] - 1), Math.max(in[1], in[3] - 2), 0);
		Rooms.furnish(d, fp.room(), Rooms.Type.HALL, 0, JobType.NONE);
	}

	/** The yard: a smoking rack over a slow fire, an archery butt, a chopping block, the woodpile. */
	private static int[] yard(Draft d, Hall hall, int w, int dp, int side, boolean left, int fz) {
		int[] r = hall.rect(0);
		int[] rack = null;
		Mat post = Hall.woodFence(d);
		Mat beam = d.mat(Role.BEAM);
		if (side > 0) {
			int x0 = left ? 0 : r[2] + 1;
			int x1 = left ? side - 1 : w - 1;
			Yard.trodden(d, x0, fz, x1, dp - 1, 0);
			int cx = (x0 + x1) / 2;
			int cz = fz + 1;
			// the smoking rack: posts, a beam across, chains with the meat, the fire beneath
			if (dp - 1 - cz >= 2) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(cx, 0, cz, post.fence());
					d.set(cx, 1, cz, post.fence());
					d.set(cx, 0, cz + 2, post.fence());
					d.set(cx, 1, cz + 2, post.fence());
					for (int z = cz; z <= cz + 2; z++) {
						d.set(cx, 2, z, beam.log(Direction.Axis.Z));
					}
					d.set(cx, 0, cz + 1, States.lit(Blocks.CAMPFIRE, true).trySetValue(
						net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
				}
				// a little roof of boards over the rack keeps the rain off the meat; the smoke finds its way out under it
				Mat boards = d.mat(Role.ROOF);
				if (boards.has(dev.voidmc.hearth.build.style.Form.SLAB) && d.maxHeight() > 4) {
					try (var s = d.stage(StageKind.ROOF)) {
						for (int z = cz - 1; z <= cz + 3; z++) {
							for (int x = cx - 1; x <= cx + 1; x++) {
								if (d.allowed(x, 3, z) && d.isAbsent(x, 3, z) && x >= 0 && x < w && z >= 0 && z < dp) {
									d.set(x, 3, z, boards.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
								}
							}
						}
					}
				}
				// the hunter's place at it is chosen when the yard is railed ({@link #tend})
				rack = new int[]{cx, cz, left ? 1 : 0};
			}
			// the archery butt at the back of the yard
			int tz = dp - 1;
			d.inStage(StageKind.EXTERIOR, () -> {
				d.set(cx, 0, tz, Blocks.HAY_BLOCK.defaultBlockState());
				d.set(cx, 1, tz, Blocks.TARGET.defaultBlockState());
			});
			Props.choppingBlock(d, left ? x1 : x0, 0, dp - 2);
		}
		// the woodpile against the back of the lodge
		if (r[3] + 1 < dp) {
			Garden.woodpile(d, r[0] + 1, 0, r[3] + 1, Dirs.RIGHT, Math.min(3, r[2] - r[0] - 1));
		} else if (side == 0) {
			int x = r[0] > 0 ? r[0] - 1 : r[2] + 1 < w ? r[2] + 1 : -1;
			if (x >= 0) {
				Garden.woodpile(d, x, 0, r[3], Dirs.FRONT, 2);
			}
		}
		return rack;
	}

	/**
	 * The hunter's place at the smoking rack (posts at (cx, cz) and (cx, cz + 2), the fire between): beside the fire on
	 * the house side, before the rack, beside the fire on the outer side, behind it; the first one folk can walk to from
	 * the street once the yard is railed (on a hillside the plot's edge is railed, a snow bank or the porch's corner can
	 * shut the side yard off). Where none can be, his place is the free cell of the front yard nearest the side yard's
	 * mouth, where he hangs the game before it goes to the rack.
	 */
	private static void tend(Draft d, int cx, int cz, boolean left, int fz) {
		dev.voidmc.hearth.build.kit.light.Spaces walk = dev.voidmc.hearth.build.kit.light.Spaces.of(d, false);
		int in = left ? 1 : -1;
		int[][] cells = {{cx + in, cz + 1}, {cx, cz - 1}, {cx - in, cz + 1}, {cx, cz + 3}};
		Direction[] faces = {left ? Dirs.LEFT : Dirs.RIGHT, Dirs.BACK, left ? Dirs.RIGHT : Dirs.LEFT, Dirs.FRONT};
		for (int i = 0; i < cells.length; i++) {
			int x = cells[i][0];
			int z = cells[i][1];
			if (free(d, x, z) && walk.reached(x, 0, z)) {
				d.poi(dev.voidmc.hearth.api.PoiType.JOB_SITE, x, 0, z, faces[i], JobType.HUNTER);
				return;
			}
		}
		for (int z = fz - 1; z >= 0; z--) {
			for (int o = 0; o < d.frontage(); o++) {
				int x = left ? o : d.frontage() - 1 - o;
				if (free(d, x, z) && walk.reached(x, 0, z)) {
					d.poi(dev.voidmc.hearth.api.PoiType.JOB_SITE, x, 0, z, Dirs.BACK, JobType.HUNTER);
					return;
				}
			}
		}
	}

	private static boolean free(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth() && d.isEmpty(x, 0, z) && d.isEmpty(x, 1, z);
	}
}
