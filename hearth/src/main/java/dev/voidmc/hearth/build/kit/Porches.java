package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Things that stand against a wall: porches and verandahs (a deck, posts, a lean-to roof, railings, a bench), galleries
 * along raised houses, balconies on upper floors (a deck on brackets with a railing), and bay windows. They give a house
 * its human scale and break its outline. All keep to the plot, overhanging the street only above three blocks.
 */
public final class Porches {
	private Porches() {
	}

	private static boolean allowed(Draft d, int x, int y, int z) {
		return x >= 0 && x < d.frontage() && z < d.depth() && (z >= 0 || z == -1 && y >= 3);
	}

	/**
	 * A porch against wall {@code side} over columns {@code [c0..c1]} (along the wall), {@code depth} deep, its deck at
	 * standing level {@code y}: a floor, corner posts, a lean-to roof whose eave is {@code clear} blocks above the deck,
	 * railings along the open edges (leaving the {@code opening} column free, or none if it is out of range) and a bench
	 * against the wall. A raised deck stands on posts down to the ground.
	 */
	public static void porch(Draft d, Side side, int c0, int c1, int depth, int y, int clear, int opening) {
		Mat floor = d.mat(Role.FLOOR);
		Mat frame = d.mat(Role.FRAME);
		Mat rail = d.mat(Role.RAILING);
		Mat roof = d.mat(Role.ROOF);
		Mat stilt = d.mat(Role.STILT);
		Direction out = side.out();
		int i0 = side.index(c0);
		int i1 = side.index(c1);
		int ia = Math.min(i0, i1);
		int ib = Math.max(i0, i1);
		boolean raised = false;
		for (int i = ia; i <= ib; i++) {
			for (int k = 1; k <= depth; k++) {
				int x = side.outX(i, k);
				int z = side.outZ(i, k);
				if (!allowed(d, x, y, z)) {
					continue;
				}
				int g = d.site().ground(x, z);
				try (var s = d.stage(StageKind.FOUNDATION)) {
					d.set(x, y - 1, z, g >= y ? d.mat(Role.PAVING).full() : floor.full());
				}
				if (g < y - 1) {
					raised = true;
				}
				for (int yy = y; yy < y + clear; yy++) {
					d.setIfAbsent(x, yy, z, Paint.of(Blocks.AIR.defaultBlockState()));
				}
			}
		}
		// posts at the outer corners (and every third column), standing on the ground
		int open = side.index(opening);
		for (int i = ia; i <= ib; i++) {
			boolean corner = i == ia || i == ib;
			if (!corner && (i - ia) % 3 != 0 || i == open) {
				continue;
			}
			int x = side.outX(i, depth);
			int z = side.outZ(i, depth);
			if (!allowed(d, x, y, z)) {
				continue;
			}
			try (var s = d.stage(StageKind.FRAME)) {
				for (int yy = y; yy < y + clear; yy++) {
					d.set(x, yy, z, frame.log(Direction.Axis.Y));
				}
				int g = d.site().ground(x, z);
				for (int yy = g - 1; yy < y - 1; yy++) {
					d.set(x, yy, z, stilt.log(Direction.Axis.Y));
				}
			}
		}
		// railings along the open edges
		for (int i = ia; i <= ib; i++) {
			int x = side.outX(i, depth);
			int z = side.outZ(i, depth);
			if (i == side.index(opening) || !allowed(d, x, y, z) || !d.isEmpty(x, y, z)) {
				continue;
			}
			if (raised || d.chance(0.5)) {
				d.set(x, y, z, rail.fence());
			}
		}
		for (int k = 1; k < depth; k++) {
			for (int i : new int[]{ia, ib}) {
				int x = side.outX(i, k);
				int z = side.outZ(i, k);
				if (allowed(d, x, y, z) && d.isEmpty(x, y, z) && (raised || d.chance(0.5))) {
					d.set(x, y, z, rail.fence());
				}
			}
		}
		// a lean-to roof from the posts up to the wall
		try (var s = d.stage(StageKind.ROOF)) {
			for (int i = ia - 1; i <= ib + 1; i++) {
				for (int k = 1; k <= depth + 1; k++) {
					int x = side.outX(i, k);
					int z = side.outZ(i, k);
					int ry = y + clear + (depth + 1 - k) / 2;
					if (!allowed(d, x, ry, z) || !d.isAbsent(x, ry, z)) {
						continue;
					}
					boolean edge = i == ia - 1 || i == ib + 1;
					boolean half = ((depth + 1 - k) & 1) == 1;
					if (edge && k == depth + 1) {
						d.set(x, ry, z, roof.slab(SlabType.BOTTOM));
					} else {
						d.set(x, ry, z, half ? roof.slab(SlabType.TOP) : roof.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
					}
				}
			}
		}
		// a bench against the wall, facing out
		int bi = (ia + ib) / 2 + (ib > ia ? 1 : 0);
		if (bi != side.index(opening) && bi >= ia && bi <= ib) {
			int x = side.outX(bi, 1);
			int z = side.outZ(bi, 1);
			if (d.isEmpty(x, y, z) && depth >= 2) {
				Furniture.chair(d, x, y, z, out.getOpposite());
				d.poi(PoiType.VIEWPOINT, x, y, z, out);
			}
		}
	}

	/**
	 * A balcony on wall {@code side} of a storey whose standing level is {@code floorY}, over columns {@code [c0..c1]}:
	 * a deck two blocks out at the storey's floor row, carried on beams and corbels, with its railing on the outer row and
	 * returned to the wall at both ends, so the row before the wall (before the door onto it) stays clear to stand in
	 * (the audit's rule S1: never a rail before a door). Window dressing on that row is cleared. Where the plot leaves
	 * room for one row only (over the street), the deck is one deep and its rail is a row of open trapdoors whose leaves
	 * stand on the deck's outer edge. Answers how deep it was built (0: not at all).
	 */
	public static int balcony(Draft d, Side side, int c0, int c1, int floorY) {
		Mat floor = d.mat(Role.FLOOR);
		Mat rail = d.mat(Role.RAILING);
		Mat beam = d.mat(Role.BEAM);
		Direction out = side.out();
		int ia = Math.min(side.index(c0), side.index(c1));
		int ib = Math.max(side.index(c0), side.index(c1));
		// two deep wherever the plot allows the outer row over the whole span, its returns included
		int depth = 2;
		for (int i = ia - 1; i <= ib + 1 && depth == 2; i++) {
			if (i < 0 || i > side.last()) {
				continue;
			}
			int x = side.outX(i, 2);
			int z = side.outZ(i, 2);
			if (!allowed(d, x, floorY - 2, z) || !d.isAbsent(x, floorY - 1, z)) {
				depth = 1;
			}
		}
		int built = 0;
		for (int i = ia; i <= ib; i++) {
			int x = side.outX(i, 1);
			int z = side.outZ(i, 1);
			if (!allowed(d, x, floorY, z) || !d.isAbsent(x, floorY - 1, z)) {
				continue;
			}
			built++;
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(x, floorY - 1, z, floor.slab(SlabType.TOP));
				// the row before the wall is for standing in: what a window's dressing put there goes
				for (int yy = floorY; yy <= floorY + 1; yy++) {
					Draft.Cell c = d.cell(x, yy, z);
					if (c != null && !c.state().isAir() && !(c.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
						&& (c.stage() == StageKind.EXTERIOR || c.stage() == StageKind.OPENINGS || c.stage() == StageKind.INTERIOR)) {
						d.remove(x, yy, z);
					}
				}
				boolean carried = i == ia || i == ib || (i - ia) % 2 == 0;
				if (depth == 2) {
					int x2 = side.outX(i, 2);
					int z2 = side.outZ(i, 2);
					d.set(x2, floorY - 1, z2, floor.slab(SlabType.TOP));
					if (carried && d.isAbsent(x, floorY - 2, z) && d.isAbsent(x2, floorY - 2, z2)) {
						// a beam out of the wall under the deck, its end cut as a corbel
						d.set(x, floorY - 2, z, beam.log(out.getAxis()));
						d.set(x2, floorY - 2, z2, beam.stairsOrSlab(out.getOpposite(), Half.TOP));
					}
					if (d.isAbsent(x2, floorY, z2) || d.isEmpty(x2, floorY, z2)) {
						d.set(x2, floorY, z2, rail.has(Form.FENCE) ? rail.fence() : Guards.rail(d));
					}
				} else {
					if (carried && d.isAbsent(x, floorY - 2, z)) {
						d.set(x, floorY - 2, z, beam.stairsOrSlab(out.getOpposite(), Half.TOP));
					}
					// the rail stands on the deck's outer edge: an open trapdoor facing the wall has its leaf there
					Mat leaf = rail.has(Form.TRAPDOOR) ? rail : d.mat(Role.SHUTTER).has(Form.TRAPDOOR) ? d.mat(Role.SHUTTER) : d.mat(Role.FURNITURE);
					d.set(x, floorY, z, leaf.trapdoor(out.getOpposite(), Half.BOTTOM, true));
				}
			}
		}
		if (built == 0) {
			return 0;
		}
		// the railing returns to the wall at both ends, on the deck carried out under it
		for (int i : new int[]{ia - 1, ib + 1}) {
			if (i < 0 || i > side.last()) {
				continue;
			}
			for (int k = 1; k <= depth; k++) {
				int x = side.outX(i, k);
				int z = side.outZ(i, k);
				if (!allowed(d, x, floorY, z) || !d.isAbsent(x, floorY - 1, z) || !(d.isAbsent(x, floorY, z) || d.isEmpty(x, floorY, z))) {
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, floorY - 1, z, floor.slab(SlabType.TOP));
					d.set(x, floorY, z, rail.has(Form.FENCE) ? rail.fence() : Guards.rail(d));
				}
			}
		}
		d.poi(PoiType.VIEWPOINT, side.outX((ia + ib) / 2, 1), floorY, side.outZ((ia + ib) / 2, 1), out);
		return depth;
	}

	/**
	 * A bay window on the ground floor: a box one block out from wall {@code side} over columns {@code [c0..c1]}, glazed on
	 * its three faces above a plinth course, under a little hipped roof of slabs and stairs.
	 */
	public static void bay(Draft d, Side side, int c0, int c1, int y) {
		Mat plinth = d.mat(Role.PLINTH);
		Mat glass = d.mat(Role.GLASS);
		Mat frame = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		Direction out = side.out();
		int ia = Math.min(side.index(c0), side.index(c1));
		int ib = Math.max(side.index(c0), side.index(c1));
		Habits.WallKind kind = d.habits().walls().kind();
		boolean timber = kind == Habits.WallKind.TIMBER_FRAME || kind == Habits.WallKind.LOG || kind == Habits.WallKind.PLANK;
		for (int i = ia; i <= ib; i++) {
			int x = side.outX(i, 1);
			int z = side.outZ(i, 1);
			if (!allowed(d, x, y, z)) {
				return;
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int i = ia; i <= ib; i++) {
				int x = side.outX(i, 1);
				int z = side.outZ(i, 1);
				int g = d.site().ground(x, z);
				for (int yy = Math.min(g - 1, y - 1); yy <= y; yy++) {
					d.set(x, yy, z, plinth.full());
				}
				boolean end = i == ia || i == ib;
				for (int yy = y + 1; yy <= y + 2; yy++) {
					d.set(x, yy, z, end && timber ? frame.log(Direction.Axis.Y) : glass.pane());
				}
				// the wall behind opens into the bay
				d.set(side.x(i), y + 1, side.z(i), Blocks.AIR.defaultBlockState());
				d.set(side.x(i), y + 2, side.z(i), Blocks.AIR.defaultBlockState());
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int i = ia; i <= ib; i++) {
				int x = side.outX(i, 1);
				int z = side.outZ(i, 1);
				Direction f = i == ia && ib > ia ? side.along() : i == ib && ib > ia ? side.along().getOpposite() : out.getOpposite();
				d.set(x, y + 3, z, i == ia || i == ib ? roof.stairsOrSlab(f, Half.BOTTOM) : roof.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
			}
		}
	}
}
