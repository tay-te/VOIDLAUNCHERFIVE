package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What stands out from or is cut into an upper storey and makes a tall house read as lived in from the street: oriel
 * windows on corbels (glazed, or latticed like a mashrabiya), galleries along a whole floor (cantilevered on brackets,
 * or on posts from the ground with a roof over the top one), loggias (a storey's wall opened into an arcade behind a
 * balustrade), arcades on piers (porticoes, courtyard cloisters), and flower-hung balustrades.
 */
public final class Projections {
	private Projections() {
	}

	private static boolean ok(Draft d, int x, int y, int z) {
		return Fronts.allowed(d, x, y, z);
	}

	/**
	 * An oriel: a box one block out from wall cells {@code i0..i1} of a storey whose floor is at {@code floorY}, glazed
	 * on its three faces ({@code lattice}: wooden grilles instead), standing on stepped corbels and capped with a little
	 * roof. The wall behind opens into it.
	 */
	public static boolean oriel(Draft d, Side s, int i0, int i1, int floorY, int height, boolean lattice) {
		Direction out = s.out();
		Direction in = out.getOpposite();
		for (int i = i0; i <= i1; i++) {
			if (!ok(d, s.outX(i, 1), floorY - 2, s.outZ(i, 1)) || !d.isAbsent(s.outX(i, 1), floorY, s.outZ(i, 1))) {
				return false;
			}
		}
		Mat glass = d.mat(Role.GLASS);
		Mat frame = d.mat(Role.FRAME);
		Mat shutter = d.mat(Role.SHUTTER);
		Mat beam = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.ROOF);
		Mat floor = d.mat(Role.FLOOR);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int i = i0; i <= i1; i++) {
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				boolean end = i == i0 || i == i1;
				// the floor of the box on a corbel, a second corbel step under it
				d.set(x, floorY - 1, z, floor.slab(SlabType.TOP));
				if (end || (i - i0) % 2 == 0) {
					d.set(x, floorY - 2, z, beam.stairsOrSlab(in, Half.TOP));
				}
				for (int y = floorY; y < floorY + height; y++) {
					if (lattice) {
						d.set(x, y, z, shutter.trapdoor(in, Half.BOTTOM, true));
					} else if (end && i1 > i0) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					} else {
						d.set(x, y, z, glass.pane());
					}
				}
				// the wall behind opens into the box
				for (int y = floorY; y < floorY + height; y++) {
					if (Openings.isWallBlock(d, s.x(i), y, s.z(i)) || d.get(s.x(i), y, s.z(i)) != null && !d.isEmpty(s.x(i), y, s.z(i))) {
						d.set(s.x(i), y, s.z(i), Blocks.AIR.defaultBlockState());
					}
				}
			}
			// side cheeks glazed where the box has open ends
			for (int i : new int[]{i0 - 1, i1 + 1}) {
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				if (i < 0 || i > s.last() || !ok(d, x, floorY, z) || !d.isAbsent(x, floorY, z)) {
					continue;
				}
				// nothing: the box's end cells carry the frame; the neighbour cells stay clear
			}
		}
		try (var st = d.stage(StageKind.ROOF)) {
			for (int i = i0; i <= i1; i++) {
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				int y = floorY + height;
				if (ok(d, x, y, z) && d.isAbsent(x, y, z)) {
					boolean end = i == i0 || i == i1;
					d.set(x, y, z, end && i1 > i0 ? roof.stairsOrSlab(i == i0 ? s.along() : s.along().getOpposite(), Half.BOTTOM)
						: roof.stairsOrSlab(in, Half.BOTTOM));
				}
			}
		}
		return true;
	}

	/**
	 * A gallery along a whole storey: a deck one block out over cells {@code i0..i1} at the storey's floor, on brackets,
	 * with a balustrade ({@code lattice}: lattice panels) and pots on its rail; folk step out of a door onto it.
	 */
	public static void gallery(Draft d, Side s, int i0, int i1, int floorY, boolean lattice, boolean pots) {
		Mat floor = d.mat(Role.FLOOR);
		Mat rail = d.mat(Role.RAILING);
		Mat beam = d.mat(Role.BEAM);
		Direction out = s.out();
		Direction in = out.getOpposite();
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int i = i0; i <= i1; i++) {
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				if (!ok(d, x, floorY, z) || !d.isAbsent(x, floorY - 1, z)) {
					continue;
				}
				d.set(x, floorY - 1, z, floor.slab(SlabType.TOP));
				if ((i - i0) % 2 == 0 || i == i1) {
					if (d.isAbsent(x, floorY - 2, z) && ok(d, x, floorY - 2, z)) {
						d.set(x, floorY - 2, z, beam.stairsOrSlab(in, Half.TOP));
					}
				}
				if (d.isAbsent(x, floorY, z)) {
					d.set(x, floorY, z, lattice ? rail.trapdoor(in, Half.BOTTOM, true) : rail.fence());
					if (pots && !lattice && ((i * 7 + floorY) % 3 == 0)) {
						Garden.pot(d, x, floorY + 1, z);
					}
				}
			}
			for (int i : new int[]{i0 - 1, i1 + 1}) {
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				if (i >= 0 && i <= s.last() && ok(d, x, floorY, z) && d.isAbsent(x, floorY, z)) {
					d.set(x, floorY, z, rail.fence());
				}
			}
		}
		d.poi(PoiType.VIEWPOINT, s.outX((i0 + i1) / 2, 1), floorY, s.outZ((i0 + i1) / 2, 1), out);
	}

	/**
	 * Galleries one above another on posts that stand on the ground in front of wall s (the body must leave
	 * {@code depth} rows in front of it inside the plot): decks at each listed floor level, posts at the ends and every
	 * third cell running from the ground to a lean-to roof over the top deck, balustrades between the posts.
	 */
	public static void postedGalleries(Draft d, Side s, int i0, int i1, int[] floors, int depth, int roofY, boolean lattice, int openAt) {
		Mat floor = d.mat(Role.FLOOR);
		Mat rail = d.mat(Role.RAILING);
		Mat post = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		Direction in = s.out().getOpposite();
		for (int i = i0; i <= i1; i++) {
			boolean isPost = i == i0 || i == i1 || (i - i0) % 3 == 0;
			for (int k = 1; k <= depth; k++) {
				int x = s.outX(i, k);
				int z = s.outZ(i, k);
				for (int f : floors) {
					if (!ok(d, x, f, z)) {
						continue;
					}
					d.inStage(StageKind.FLOORS, () -> d.set(x, f - 1, z, floor.full()));
					if (k == depth && !isPost && !(f == floors[0] && Math.abs(i - openAt) <= 0)) {
						d.inStage(StageKind.EXTERIOR, () -> d.set(x, f, z, lattice ? rail.trapdoor(in, Half.BOTTOM, true) : rail.fence()));
					}
				}
			}
			if (isPost && i != openAt) {
				int x = s.outX(i, depth);
				int z = s.outZ(i, depth);
				int g = d.site().ground(x, z);
				d.inStage(StageKind.FRAME, () -> {
					for (int y = Math.min(g - 1, floors[0] - 1); y < roofY; y++) {
						if (d.isAbsent(x, y, z) || !d.isSolid(x, y, z) || y < floors[0] - 1) {
							d.set(x, y, z, post.log(Direction.Axis.Y));
						}
					}
				});
			}
		}
		// the ends return to the wall
		for (int f : floors) {
			for (int k = 1; k < depth; k++) {
				for (int i : new int[]{i0, i1}) {
					int x = s.outX(i, k);
					int z = s.outZ(i, k);
					if (ok(d, x, f, z) && d.isAbsent(x, f, z)) {
						d.inStage(StageKind.EXTERIOR, () -> d.set(x, f, z, rail.fence()));
					}
				}
			}
		}
		// the lean-to roof over the top deck
		try (var st = d.stage(StageKind.ROOF)) {
			for (int i = i0 - 1; i <= i1 + 1; i++) {
				if (i < 0 || i > s.last()) {
					continue;
				}
				for (int k = 1; k <= depth + 1; k++) {
					int x = s.outX(i, k);
					int z = s.outZ(i, k);
					int y = roofY + (depth + 1 - k) / 2;
					if (!ok(d, x, y, z) || !d.isAbsent(x, y, z)) {
						continue;
					}
					boolean half = ((depth + 1 - k) & 1) == 1;
					d.set(x, y, z, half ? roof.slab(SlabType.TOP) : roof.stairsOrSlab(in, Half.BOTTOM));
				}
			}
		}
		d.poi(PoiType.VIEWPOINT, s.outX((i0 + i1) / 2, 1), floors[floors.length - 1], s.outZ((i0 + i1) / 2, 1), s.out());
	}

	/**
	 * Opens wall cells {@code i0..i1} of a storey into an arcade: piers every {@code bay + 1} cells, the openings
	 * {@code height} high with round heads (stair shoulders in the top row when two or more wide), a balustrade of
	 * fences at the floor if {@code rail}. Used for loggias, porticoes and courtyard cloisters.
	 */
	public static void arcade(Draft d, Side s, int i0, int i1, int y, int height, int bay, boolean rail) {
		Mat pier = d.mat(Role.WALL_ACCENT);
		Mat railing = d.mat(Role.RAILING);
		int n = i1 - i0 + 1;
		// spread the openings evenly: piers at both ends, bays between
		int bays = Math.max(1, Math.round((n - 1) / (float) (bay + 1)));
		try (var st = d.stage(StageKind.OPENINGS)) {
			int prev = i0;
			for (int b = 1; b <= bays; b++) {
				int next = b == bays ? i1 : i0 + Math.round(b * (n - 1) / (float) bays);
				int a0 = prev + 1;
				int a1 = next - 1;
				for (int i = a0; i <= a1; i++) {
					int x = s.x(i);
					int z = s.z(i);
					for (int yy = y; yy < y + height; yy++) {
						d.set(x, yy, z, Blocks.AIR.defaultBlockState());
					}
					if (rail) {
						d.set(x, y, z, railing.fence());
					}
				}
				// the arch: shoulders in the top row of a wide opening, the course over it in the pier stone
				if (a1 - a0 >= 1) {
					d.set(s.x(a0), y + height - 1, s.z(a0), pier.stairsOrSlab(s.along().getOpposite(), Half.TOP));
					d.set(s.x(a1), y + height - 1, s.z(a1), pier.stairsOrSlab(s.along(), Half.TOP));
				}
				for (int i = a0; i <= a1; i++) {
					if (Openings.isWallBlock(d, s.x(i), y + height, s.z(i))) {
						d.set(s.x(i), y + height, s.z(i), pier.full());
					}
				}
				for (int yy = y; yy < y + height; yy++) {
					d.set(s.x(next), yy, s.z(next), pier.full());
					d.set(s.x(prev), yy, s.z(prev), pier.full());
				}
				prev = next;
			}
		}
	}

	/** An arcade's piers as round columns where the culture builds in timber: posts with a capital of stairs. */
	public static Paint column(Draft d, Traits t) {
		return t.timber() && !t.masonry() ? d.mat(Role.FRAME).log(Direction.Axis.Y) : d.mat(Role.WALL_ACCENT).full();
	}

	/** Flower boxes hung on a balustrade's cells: leaves on the rail top where nothing stands. */
	public static void boxes(Draft d, Side s, int i0, int i1, int railY) {
		Mat hedge = d.mat(Role.HEDGE);
		for (int i = i0; i <= i1; i++) {
			int x = s.outX(i, 1);
			int z = s.outZ(i, 1);
			if ((i & 1) == 0 && ok(d, x, railY + 1, z) && d.isAbsent(x, railY + 1, z) && d.get(x, railY, z) != null && !d.isEmpty(x, railY, z)) {
				if (hedge.has(Form.FULL)) {
					d.inStage(StageKind.EXTERIOR, () -> d.set(x, railY + 1, z, hedge.leaves()));
				}
			}
		}
	}
}
