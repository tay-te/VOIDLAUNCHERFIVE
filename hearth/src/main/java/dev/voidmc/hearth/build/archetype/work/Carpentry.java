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
import dev.voidmc.hearth.build.kit.craft.Hatch;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Pentice;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/carpentry}: the joiner's shop. A workshop with its gable to the street and wide doors standing
 * open, benches under its windows, a drying loft for boards in the roof (a hoist beam at the gable where it has one);
 * along its side (or behind it on a narrow plot) the lumber store, the workshop's eave carried on over racks of logs
 * and stickered boards; in the yard sawhorses with a plank across, a saw pit, and furniture half made — a chair frame,
 * a table without its top, a cart wheel. A karst joiner stacks bamboo.
 *
 * <p>Job sites (CARPENTER): the benches (crafting tables), the sawhorses. Storage: the plank chest.
 *
 * <p>Params: {@code roof}, {@code variant}.
 */
public final class Carpentry implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/carpentry"), ArchetypeCategory.WORKPLACE, Set.of(JobType.CARPENTER), 0,
		7, 11, 8, 12, 8, 13, SettlementTier.HAMLET, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.HIP, RoofForm.SALTBOX,
		RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		RoofForm form = Hall.pick(d, FORMS);
		boolean flat = form == RoofForm.FLAT;
		boolean bamboo = isBamboo(d);

		// the plot: the shop on one side with the lumber store beside it, or across the front with the store behind
		boolean sideStore = w >= 9;
		boolean storeLeft = d.chance(0.5);
		int fz = dp >= 11 ? 2 : 1;
		int hx0 = sideStore && storeLeft ? w - Math.min(7, w - 3) : 0;
		int hx1 = sideStore && !storeLeft ? Math.min(7, w - 3) - 1 : w - 1;
		int hz0 = fz;
		int hz1 = sideStore ? dp - 1 : dp - 4;
		int sh = 5;
		boolean loft = !flat && d.maxHeight() >= sh + 7;
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, loft ? new int[]{sh, 2} : new int[]{sh}, false, false);
		int[] in = hall.inner(0);
		int doorW = in[2] - in[0] + 1 >= 4 ? 2 : 1;
		int door = (in[0] + in[2]) / 2 - (doorW == 2 ? 0 : 0);
		if (door + doorW - 1 > in[2]) {
			door = in[2] - doorW + 1;
		}
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		hall.facade(door, doorW, d.habits().windows().tall() ? 2 : 1, 0.6);
		door = hall.door != null ? hall.door : door;
		int ex = Yard.entrance(d);
		hall.ground(false).shell().openings(true);

		// the gable to the street, unless the culture never turns it so
		Direction.Axis ridge = d.chance(0.8) ? Direction.Axis.Z : Direction.Axis.X;
		hall.roof(form, ridge, 1, null);
		if (flat) {
			hall.crown();
		}
		// a hoist over a loft door in the front gable
		if (loft && hall.roof != null && ridge == Direction.Axis.Z) {
			Side g = hall.side(1, Dirs.FRONT);
			int i = g.index(door + (doorW - 1) / 2);
			int y = hall.floorY(1);
			if (i > 0 && i < g.last() && hall.roof.bottomY(g.x(i), g.z(i)) - y >= 3) {
				Hatch.hoistDoor(d, g.x(i), y, g.z(i), Dirs.FRONT);
				dev.voidmc.hearth.build.kit.trade.Lifts.hoist(d, g, i, y + 2, 1, y - 2, Blocks.HAY_BLOCK.defaultBlockState());
			}
		}

		// the side door into the lumber store (or the back door under the lean-to): its inside cell stays clear
		int[] in0 = hall.inner(0);
		int[] sideDoor = sideStore ? new int[]{storeLeft ? in0[0] : in0[2], in0[1] + 1} : new int[]{in0[2], in0[3]};
		interior(d, hall, door, doorW, loft, ridge, sideDoor);
		// the lumber store: along the side under the carried-on eave, or behind under a lean-to
		if (sideStore) {
			int sx0 = storeLeft ? 0 : hx1 + 1;
			int sx1 = storeLeft ? hx0 - 1 : w - 1;
			lumberSide(d, hall, sx0, sx1, hz0, hz1, storeLeft, bamboo, flat);
		} else {
			lumberBack(d, hall, w, hz1 + 1, dp - 1, bamboo, flat);
		}
		front(d, hall, w, fz, door, doorW, bamboo);
		Yard.connect(d, ex, door, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	static boolean isBamboo(Draft d) {
		Block fence = d.mat(Role.FENCE).main(Form.FENCE);
		return fence == Blocks.BAMBOO_FENCE || d.habits().walls().kind() == Habits.WallKind.BAMBOO;
	}

	private static Mat timber(Draft d) {
		return d.mat(Role.FRAME);
	}

	/** Benches under the windows, the plank chest, a tool rack, a chair and a table being made. */
	private static void interior(Draft d, Hall hall, int door, int doorW, boolean loft, Direction.Axis ridge, int[] sideDoor) {
		int[] in = hall.inner(0);
		Floorplan fp = hall.floor(0).blockHoles(d);
		for (int c = door; c < door + doorW; c++) {
			fp.keep(c, in[1]);
		}
		fp.keep(sideDoor[0], sideDoor[1]);
		// the board loft: floored over the shop, a ladder up through a hatch against a gable wall (where the loft has its
		// height), so the loft and its hoist door can be walked to
		int[] ladder = null;
		if (loft && hall.storeys() >= 2) {
			ladder = ridge == Direction.Axis.Z ? Hatch.ladder(d, hall, 1, fp, Dirs.BACK, Dirs.FRONT) : Hatch.ladder(d, hall, 1, fp, Dirs.LEFT, Dirs.RIGHT);
		}
		// benches along the side walls and the back, facing into the room
		int benches = 0;
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			for (int[] c : fp.along(wall)) {
				if (benches >= 3) {
					break;
				}
				if (c[1] == in[1]) {
					continue;
				}
				Direction into = wall.getOpposite();
				// a bench under a window, where the light falls
				int wx = c[0] + Dirs.dx(wall);
				int wz = c[1] + Dirs.dz(wall);
				boolean window = d.get(wx, 1, wz) != null && !d.isSolid(wx, 1, wz);
				if ((window || benches == 0 && wall == Dirs.BACK) && fp.take(c[0], c[1], into)) {
					Furniture.workstation(d, JobType.CARPENTER, c[0], 0, c[1], into);
					benches++;
				}
			}
		}
		for (int[] c : fp.along(Dirs.BACK)) {
			if (benches >= 2) {
				break;
			}
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				Furniture.workstation(d, JobType.CARPENTER, c[0], 0, c[1], Dirs.FRONT);
				benches++;
			}
		}
		// the plank chest and a rack of tools over it
		for (int[] c : fp.along(Dirs.BACK)) {
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				Props.store(d, c[0], 0, c[1], Dirs.FRONT, true, List.of(new Object[]{"minecraft:oak_planks", 48}, new Object[]{"minecraft:stick", 32},
					new Object[]{"minecraft:spruce_planks", 32}, new Object[]{"minecraft:iron_axe", 1}));
				Props.toolRack(d, c[0], 2, c[1], Dirs.FRONT, List.of(new Object[]{"minecraft:iron_axe", 1, 0}, new Object[]{"minecraft:stick", 4, 1},
					new Object[]{"minecraft:iron_hoe", 1, 2}));
				break;
			}
		}
		// furniture half made in the middle of the floor: a chair frame and a table without its top
		Mat wood = d.mat(Role.FURNITURE);
		int mx = (in[0] + in[2]) / 2;
		int mz = (in[1] + in[3]) / 2 + 1;
		if (in[3] - in[1] >= 4 && in[2] - in[0] >= 3) {
			if (fp.take(mx, mz, null)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(mx, 0, mz, wood.fence()));
			}
			if (fp.take(mx + 1, mz, null)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(mx + 1, 0, mz, wood.stairsOrSlab(Dirs.BACK, Half.BOTTOM)));
			}
		}
		// boards drying in the loft: stacked along its eaves, clear of the hatch and the walk to the hoist door
		if (loft && ladder != null) {
			int fy = hall.floorY(1) - 1;
			int[] up = hall.inner(1);
			Mat planks = d.mat(Role.FLOOR);
			boolean alongZ = ridge == Direction.Axis.Z;
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int a = (alongZ ? up[1] : up[0]) + 1; a < (alongZ ? up[3] : up[2]); a += 2) {
					for (int b : alongZ ? new int[]{up[0], up[2]} : new int[]{up[1], up[3]}) {
						int x = alongZ ? b : a;
						int z = alongZ ? a : b;
						if (Math.abs(x - ladder[0]) + Math.abs(z - ladder[1]) > 1 && d.isSolid(x, fy, z) && d.isEmpty(x, fy + 1, z)) {
							d.set(x, fy + 1, z, planks.slab(SlabType.BOTTOM));
						}
					}
				}
			}
		} else if (loft) {
			// no ladder fits: the loft is opened over the shop to its rafters, nobody's floor
			int fy = hall.floorY(1) - 1;
			int[] up = hall.inner(1);
			for (int x = up[0]; x <= up[2]; x++) {
				for (int z = up[1]; z <= up[3]; z++) {
					d.set(x, fy, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
		// a lantern from the tie beam
		int plate = loft && ladder != null ? hall.floorY(1) - 1 : hall.plateY();
		Mat beam = d.mat(Role.BEAM);
		int tz = (in[1] + in[3]) / 2;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = in[0]; x <= in[2]; x++) {
				if (d.isEmpty(x, plate, tz)) {
					d.set(x, plate, tz, beam.log(Direction.Axis.X));
				}
			}
		}
		int lx = (in[0] + in[2]) / 2;
		if (d.isSolid(lx, plate, tz) && d.isEmpty(lx, plate - 1, tz) && d.isEmpty(lx, plate - 2, tz)) {
			Furniture.hangingLantern(d, lx, plate, tz, 1);
		}
	}

	/** The lumber store along the shop's side: its eave carried on over log piles and board stacks, posts at the edge. */
	private static void lumberSide(Draft d, Hall hall, int x0, int x1, int z0, int z1, boolean left, boolean bamboo, boolean flat) {
		Yard.trodden(d, x0, z0, x1, z1, 0);
		Side wall = hall.side(0, left ? Dirs.LEFT : Dirs.RIGHT);
		int width = x1 - x0 + 1;
		int[] r = hall.rect(0);
		Roofs.Roof pent = null;
		if (!flat && hall.roof != null) {
			pent = Pentice.over(d, hall.roof, wall, r[3], r[1] + 1, width, 0);
		}
		if (pent == null) {
			pent = Shed.leanTo(d, wall, r[3], r[1] + 1, width - 1, 0, 3, Math.max(3, hall.plateY()));
		}
		// under it: logs along the wall, boards beyond, a door from the shop
		int near = left ? x1 : x0;
		int far = left ? x0 : x1;
		// the side door from the shop opens at dz: the logs start behind it, its outside cell and the chest's stay clear
		int dz = hall.inner(0)[1] + 1;
		int len = Math.min(5, z1 - dz - 1);
		Mat log = bamboo ? null : timber(d);
		int lz = z1 - len + 1;
		if (len >= 2) {
			if (bamboo) {
				bambooPile(d, near, 0, lz, len);
			} else {
				Props.logPile(d, near, 0, lz, Direction.Axis.Z, len, 1, 2, log);
			}
			if (width >= 3) {
				Props.boardStack(d, near + (left ? -1 : 1), 0, lz, Direction.Axis.Z, Math.max(2, len - 2), 2, d.mat(Role.FLOOR));
			}
		}
		int bz = lz - 1 > dz ? lz - 1 : dz - 1;
		if (bz != dz && bz - 1 >= z0 && bz - 1 != dz && d.isEmpty(near, 0, bz) && d.isEmpty(near, 0, bz - 1) && d.isEmpty(near, 1, bz - 1)) {
			Props.store(d, near, 0, bz, Dirs.FRONT, false, List.of(new Object[]{"minecraft:oak_log", 32}, new Object[]{"minecraft:stick", 24}));
		}
		// a side door from the shop into the store
		int[] in = hall.inner(0);
		hall.door(0, left ? Dirs.LEFT : Dirs.RIGHT, in[1] + 1, 1, false, false);
	}

	/** The lumber store behind the shop under a lean-to off its back wall. */
	private static void lumberBack(Draft d, Hall hall, int w, int z0, int z1, boolean bamboo, boolean flat) {
		Yard.trodden(d, 0, z0, w - 1, z1, 0);
		Side wall = hall.side(0, Dirs.BACK);
		int[] r = hall.rect(0);
		Roofs.Roof pent = null;
		int depth = z1 - z0 + 1;
		if (!flat && hall.roof != null) {
			pent = Pentice.over(d, hall.roof, wall, r[2], r[0], depth, 0);
		}
		if (pent == null) {
			Shed.leanTo(d, wall, r[2], r[0], depth - 1, 0, 3, Math.max(3, hall.plateY()));
		}
		int len = Math.min(4, w - 3);
		if (bamboo) {
			for (int x = 1; x < 1 + len; x++) {
				bambooPile(d, x, 0, z1, 1);
			}
		} else {
			Props.logPile(d, 1, 0, z1, Direction.Axis.X, len, 1, 2, timber(d));
		}
		if (depth >= 3) {
			Props.boardStack(d, 1, 0, z1 - 1, Direction.Axis.X, Math.max(2, len - 1), 2, d.mat(Role.FLOOR));
		}
		if (z1 - 1 >= z0 && d.isEmpty(w - 2, 0, z1) && d.isEmpty(w - 2, 0, z1 - 1)) {
			Props.store(d, w - 2, 0, z1, Dirs.FRONT, false, List.of(new Object[]{"minecraft:oak_log", 32}, new Object[]{"minecraft:stick", 24}));
		}
		int[] in = hall.inner(0);
		hall.door(0, Dirs.BACK, in[2], 1, false, false);
	}

	/** Bamboo poles stacked on their sides. */
	private static void bambooPile(Draft d, int x, int y, int z, int len) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < len; i++) {
				d.set(x, y, z + i, States.axis(Blocks.BAMBOO_BLOCK, Direction.Axis.Z));
				if (i % 2 == 0) {
					d.set(x, y + 1, z + i, States.axis(Blocks.STRIPPED_BAMBOO_BLOCK, Direction.Axis.Z));
				}
			}
		}
	}

	/** The front: sawhorses with a plank, a cart wheel against the wall, a bench, the sign. */
	private static void front(Draft d, Hall hall, int w, int fz, int door, int doorW, boolean bamboo) {
		int[] r = hall.rect(0);
		Yard.trodden(d, 0, 0, w - 1, fz - 1, 0);
		Yard.pave(d, door, fz - 1, door + doorW - 1, fz - 1, 0);
		Side wall = hall.side(0, Dirs.FRONT);
		// sawhorses at the street edge, worked from behind
		int sx = door > (r[0] + r[2]) / 2 ? r[0] : door + doorW + 1;
		if (sx + 2 <= w - 1 && sx + 2 < door - 1 || sx > door + doorW) {
			if (sx + 2 <= w - 1 && d.isAbsent(sx, 0, 0) && d.isAbsent(sx + 1, 0, 0) && d.isAbsent(sx + 2, 0, 0)) {
				Props.sawhorses(d, sx, 0, 0, Direction.Axis.X, false);
				if (fz >= 2) {
					Props.site(d, sx + 1, 0, 1, Dirs.FRONT, JobType.CARPENTER);
				}
			}
		}
		// a cart wheel leaning on the wall beside the door
		int wx = door > (r[0] + r[2]) / 2 ? door + doorW : door - 1;
		Mat frame = d.mat(Role.FRAME);
		if (wx >= r[0] && wx <= r[2] && d.isAbsent(wx, 0, r[1] - 1) && frame.has(Form.TRAPDOOR)) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(wx, 0, r[1] - 1, frame.trapdoor(Dirs.FRONT, Half.BOTTOM, true)));
		}
		// the sign on the gable over the doors
		if (!Signs.bracket(d, wall, wall.index(door > (r[0] + r[2]) / 2 ? door - 1 : door + doorW), 3, "carpentry", "Joiner")) {
			Signs.bracket(d, wall, wall.index(r[0] + 1), 3, "carpentry", "Joiner");
		}
		if (bamboo) {
			int bx = door > (r[0] + r[2]) / 2 ? r[2] : r[0];
			if (d.isAbsent(bx, 0, r[1] - 1)) {
				d.inStage(StageKind.EXTERIOR, () -> {
					for (int y = 0; y < 3; y++) {
						d.set(bx, y, r[1] - 1, Blocks.SCAFFOLDING.defaultBlockState().setValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE, 0)
							.setValue(net.minecraft.world.level.block.ScaffoldingBlock.BOTTOM, false));
					}
				});
			}
		}
	}
}
