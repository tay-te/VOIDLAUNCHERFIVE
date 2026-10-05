package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A jetty: the upper storeys of a house rebuilt a block out over its front (the street) or its back, on beam ends and
 * stair brackets, gaining a strip of floor on every upper storey: how timber towns grew over their streets. Only where the
 * wall runs along the eave (not under a gable, whose triangle would be left behind), nothing but window dressing stands
 * in front of it, and the house has a storey above the ground floor. The front wall's upper rows move out whole (windows
 * and all, the corner posts staying put and the side walls closing on the new corners), the floors run out to it, and
 * what hung on the inside of the old wall goes with it.
 */
public final class Jetty {
	private Jetty() {
	}

	public static boolean add(HouseScan h, long seed) {
		Draft d = h.d;
		if (h.floors.size() < 2 || h.flat) {
			return Why.no("jetty", "floors " + h.floors + " flat " + h.flat);
		}
		int first = h.floors.get(1);
		if (first < 3) {
			return Why.no("jetty", "floors " + h.floors);
		}
		for (Direction out : (seed & 1) == 0 ? new Direction[]{Dirs.FRONT, Dirs.BACK} : new Direction[]{Dirs.BACK, Dirs.FRONT}) {
			Side s = h.side(out);
			if (fits(h, s, first)) {
				build(h, s, first);
				return true;
			}
		}
		return Why.no("jetty", "line");
	}

	private static boolean fits(HouseScan h, Side s, int first) {
		Draft d = h.d;
		int y0 = first - 1;
		int y1 = h.eave - 1;
		if (y1 - y0 < 2) {
			return Why.no("jetty", "line 54");
		}
		for (int i = 0; i <= s.last(); i++) {
			int x = s.x(i);
			int z = s.z(i);
			// the wall stands full height to the eave and the roof's eave course runs over it (no gable above)
			for (int y = y0; y <= y1; y++) {
				Draft.Cell c = d.cell(x, y, z);
				if (c == null || c.state().isAir() || c.stage() == StageKind.ROOF) {
					return Why.no("jetty", "line 63");
				}
			}
			// a ladder up the inside of the wall would be torn in two
			for (int y = 0; y <= y1; y++) {
				net.minecraft.world.level.block.state.BlockState st = d.get(x + Dirs.dx(s.out().getOpposite()), y, z + Dirs.dz(s.out().getOpposite()));
				if (st != null && st.getBlock() instanceof LadderBlock) {
					return Why.no("jetty", "line 70");
				}
			}
			Draft.Cell above = d.cell(x, h.eave + 1, z);
			if (i > 0 && i < s.last() && above != null && !above.state().isAir() && above.stage() != StageKind.ROOF) {
				return Why.no("jetty", "line 75");
			}
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			for (int y = y0 - 1; y <= y1; y++) {
				if (!Fronts.allowed(d, ox, y, oz)) {
					return Why.no("jetty", "line 81");
				}
				Draft.Cell c = d.cell(ox, y, oz);
				if (c == null || c.state().isAir()) {
					continue;
				}
				if (HouseScan.full(c.state()) || c.stage() == StageKind.ROOF || c.stage() == StageKind.FLOORS) {
					return Why.no("jetty", "line 88");
				}
			}
		}
		return true;
	}

	private static void build(HouseScan h, Side s, int first) {
		Draft d = h.d;
		Direction out = s.out();
		Direction in = out.getOpposite();
		int y0 = first - 1;
		int y1 = h.eave - 1;
		Mat floor = d.mat(Role.FLOOR);
		Mat beam = d.mat(Role.BEAM);
		Mat frame = d.mat(Role.FRAME);
		for (int i = 0; i <= s.last(); i++) {
			int x = s.x(i);
			int z = s.z(i);
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			boolean corner = i == 0 || i == s.last();
			// window dressing in front of the old wall moves a block further out, or goes
			for (int y = y0; y <= y1; y++) {
				Draft.Cell c = d.cell(ox, y, oz);
				if (c != null && !c.state().isAir()) {
					int fx = s.outX(i, 2);
					int fz = s.outZ(i, 2);
					d.remove(ox, y, oz);
					if (Fronts.allowed(d, fx, y, fz) && d.isAbsent(fx, y, fz) && c.state().getBlock() instanceof TrapDoorBlock) {
						final int yy = y;
						d.inStage(c.stage(), () -> d.set(fx, yy, fz, c.state()));
					}
				}
			}
			for (int y = y0; y <= y1; y++) {
				Draft.Cell c = d.cell(x, y, z);
				final int yy = y;
				d.inStage(c.stage(), () -> d.set(ox, yy, oz, c.state(), c.tag()));
				if (corner) {
					continue;
				}
				// the old wall's place: floor where a floor runs, else open to the room
				boolean floorRow = h.floors.contains(y + 1);
				if (floorRow) {
					d.inStage(StageKind.FLOORS, () -> d.set(x, yy, z, floor.full()));
				} else {
					d.inStage(StageKind.CLEAR, () -> d.air(x, yy, z));
					// what hung on the inside of the old wall goes with it
					int ix = x + Dirs.dx(in);
					int iz = z + Dirs.dz(in);
					Draft.Cell hung = d.cell(ix, y, iz);
					if (hung != null && hangs(hung.state())) {
						d.remove(ix, y, iz);
						d.inStage(hung.stage(), () -> d.set(x, yy, z, hung.state(), hung.tag()));
					}
				}
			}
			// the beam ends and brackets that carry it
			int by = y0 - 1;
			if (d.isEmpty(ox, by, oz) && Fronts.allowed(d, ox, by, oz)) {
				boolean post = corner || i % 2 == 0;
				if (post) {
					d.inStage(StageKind.FRAME, () -> d.set(ox, by, oz, beam.stairsOrSlab(in, Half.TOP)));
				}
			}
		}
		// the corner posts run up the new corners
		for (int i : new int[]{0, s.last()}) {
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			for (int y = y0 + 1; y <= y1; y++) {
				if (d.habits().walls().kind() == dev.voidmc.hearth.build.style.Habits.WallKind.TIMBER_FRAME) {
					final int yy = y;
					d.inStage(StageKind.FRAME, () -> d.set(ox, yy, oz, frame.log(Direction.Axis.Y)));
				}
			}
		}
	}

	private static boolean hangs(BlockState s) {
		return s.getBlock() instanceof WallTorchBlock || s.is(Blocks.WALL_TORCH)
			|| s.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock;
	}
}
