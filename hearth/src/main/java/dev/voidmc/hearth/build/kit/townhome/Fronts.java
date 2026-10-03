package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The street face of a town house: what gives a facade on a crowded street its depth and its trade. Moulded bands at
 * the floors and a cornice under the eaves, door cases and hoods, pent roofs over a ground floor, shop fronts (glazed,
 * arched, or wide open between posts) with a counter inside, striped awnings, and hanging signs over the street.
 * Everything keeps to the plot and overhangs the street only from three blocks up.
 */
public final class Fronts {
	private Fronts() {
	}

	/** Whether a local cell may be built on (inside the plot, or over the street at least three up). */
	public static boolean allowed(Draft d, int x, int y, int z) {
		return x >= 0 && x < d.frontage() && z < d.depth() && y < d.maxHeight() && (z >= 0 || z == -1 && y >= 3);
	}

	/**
	 * A moulded band (string course or cornice): upside-down stairs one block out from wall cells {@code i0..i1} at
	 * height y, their sloped undersides toward the wall. Never over something already drawn (a balcony, a sill).
	 */
	public static void band(Draft d, Side s, int i0, int i1, int y, Mat m) {
		Direction in = s.out().getOpposite();
		try (var st = d.stage(StageKind.WALLS)) {
			for (int i = Math.max(0, i0); i <= Math.min(s.last(), i1); i++) {
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				if (allowed(d, x, y, z) && d.isAbsent(x, y, z) && Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
					d.set(x, y, z, m.stairsOrSlab(in, Half.TOP));
				}
			}
		}
	}

	/** Corner pilasters: the two end columns of a wall restated in the accent from y0 to y1 (painted corner strips). */
	public static void corners(Draft d, Side s, int y0, int y1, Mat m) {
		try (var st = d.stage(StageKind.WALLS)) {
			for (int i : new int[]{0, s.last()}) {
				for (int y = y0; y <= y1; y++) {
					if (Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
						d.set(s.x(i), y, s.z(i), m.full());
					}
				}
			}
		}
	}

	/**
	 * A door case round a door at column {@code col} (width 1 or 2) on wall s, standing at y: the jambs restated in the
	 * accent, a lintel, and over it a hood on brackets (a little pent of roof slabs over the street from three up, or
	 * a pediment of stairs where the cell in front is in the plot).
	 */
	public static void doorCase(Draft d, Side s, int col, int width, int y, Traits t) {
		int i0 = s.index(col);
		int i1 = i0 + width - 1;
		Mat accent = t.timber() && !t.masonry() ? d.mat(Role.FRAME) : d.mat(Role.WALL_ACCENT);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int i : new int[]{i0 - 1, i1 + 1}) {
				if (i < 1 || i > s.last() - 1) {
					continue;
				}
				for (int yy = y; yy <= y + 2; yy++) {
					if (Openings.isWallBlock(d, s.x(i), yy, s.z(i))) {
						d.set(s.x(i), yy, s.z(i), t.timber() && !t.masonry() ? accent.log(Direction.Axis.Y) : accent.full());
					}
				}
			}
		}
		// the hood: over the door, a block out
		int hy = y + 3;
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.WINDOW);
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int i = i0 - 1; i <= i1 + 1; i++) {
				if (i < 0 || i > s.last()) {
					continue;
				}
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				if (!allowed(d, x, hy, z) || !d.isAbsent(x, hy, z)) {
					continue;
				}
				boolean end = i == i0 - 1 || i == i1 + 1;
				if (t.flat() || t.masonry() && !t.timber()) {
					// a cornice-like hood: stairs at the ends, slabs between
					d.set(x, hy, z, end ? trim.stairsOrSlab(i == i0 - 1 ? s.along() : s.along().getOpposite(), Half.TOP) : trim.slab(SlabType.TOP));
				} else {
					d.set(x, hy, z, end ? roof.stairsOrSlab(i == i0 - 1 ? s.along() : s.along().getOpposite(), Half.BOTTOM) : roof.slab(SlabType.BOTTOM));
				}
			}
		}
	}

	/**
	 * A pent roof (pentice) along a wall from cell i0 to i1, its low edge {@code depth} blocks out at height y rising one
	 * block per block back to the wall, on brackets: the tiered eaves of the karst tradition, the pentice over a shop.
	 */
	public static void pent(Draft d, Side s, int i0, int i1, int y, int depth) {
		Mat roof = d.mat(Role.ROOF);
		Mat beam = d.mat(Role.BEAM);
		Direction in = s.out().getOpposite();
		try (var st = d.stage(StageKind.ROOF)) {
			for (int i = Math.max(0, i0); i <= Math.min(s.last(), i1); i++) {
				for (int k = 1; k <= depth; k++) {
					int x = s.outX(i, k);
					int z = s.outZ(i, k);
					int ry = y + (depth - k);
					if (!allowed(d, x, ry, z) || !d.isAbsent(x, ry, z)) {
						continue;
					}
					boolean edge = i == i0 || i == i1;
					d.set(x, ry, z, k == depth && edge ? roof.stairsOrSlab(in, Half.BOTTOM) : roof.stairsOrSlab(in, Half.BOTTOM));
				}
				// brackets under the eave at every other cell
				if ((i - i0) % 2 == 0 || i == i1) {
					int x = s.outX(i, 1);
					int z = s.outZ(i, 1);
					int by = y + depth - 2;
					if (allowed(d, x, by, z) && d.isAbsent(x, by, z) && by > d.site().ground(x, z) + 1) {
						d.set(x, by, z, beam.stairsOrSlab(in, Half.TOP));
					}
				}
			}
		}
	}

	/** How a shop opens to the street. */
	public enum ShopFront {
		/** Display windows between mullions over a stall riser, a fascia above. */
		GLAZED,
		/** Round-headed openings with glazed doors (hill towns, the desert). */
		ARCHED,
		/** The whole front open between posts, folding lattice leaves pushed back (the karst tradition). */
		OPEN
	}

	/** The shop front a culture builds. */
	public static ShopFront shopFront(Traits t) {
		if (t.eastern() || t.lattice()) {
			return ShopFront.OPEN;
		}
		if (t.masonry() && (t.flat() || t.flatSome()) || t.walls() == WallKind.ADOBE) {
			return ShopFront.ARCHED;
		}
		return ShopFront.GLAZED;
	}

	/**
	 * A shop front in wall s over columns {@code c0..c1} (along the wall), from the floor y up {@code height} blocks
	 * (3 or 4), leaving the door column {@code door} (or none) to the caller. Returns nothing; the wall behind is cut.
	 */
	public static void shopFront(Draft d, Side s, int c0, int c1, int y, int height, int door, ShopFront kind, Traits t) {
		int ia = Math.min(s.index(c0), s.index(c1));
		int ib = Math.max(s.index(c0), s.index(c1));
		int di = door == Integer.MIN_VALUE ? -99 : s.index(door);
		Mat glass = d.mat(Role.GLASS);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat plinth = d.mat(Role.PLINTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat shutter = d.mat(Role.SHUTTER);
		Mat window = d.mat(Role.WINDOW);
		Direction in = s.out().getOpposite();
		int top = y + height - 1;
		switch (kind) {
			case GLAZED -> {
				try (var st = d.stage(StageKind.OPENINGS)) {
					for (int i = ia; i <= ib; i++) {
						if (i == di) {
							continue;
						}
						int x = s.x(i);
						int z = s.z(i);
						// the stall riser, panes over it, a mullion every third cell
						d.set(x, y, z, plinth.full());
						boolean mullion = (i - ia) % 3 == 2 && i < ib;
						for (int yy = y + 1; yy < top; yy++) {
							d.set(x, yy, z, mullion ? frame.log(Direction.Axis.Y) : glass.pane());
						}
						d.set(x, top, z, beam.log(s.axis()));
						// a sill board under the glass, outside where there is room
						int ox = s.outX(i, 1);
						int oz = s.outZ(i, 1);
						if (allowed(d, ox, y, oz) && d.isAbsent(ox, y, oz)) {
							d.set(ox, y, oz, window.slab(SlabType.BOTTOM));
						}
					}
				}
			}
			case ARCHED -> {
				// openings two wide (or one) between piers, each round-headed, glazed doors in them
				try (var st = d.stage(StageKind.OPENINGS)) {
					int i = ia;
					while (i <= ib) {
						if (i == di) {
							i++;
							continue;
						}
						int w = i + 1 <= ib && i + 1 != di ? 2 : 1;
						for (int c = 0; c < w; c++) {
							int x = s.x(i + c);
							int z = s.z(i + c);
							d.set(x, y, z, window.slab(SlabType.TOP));
							for (int yy = y + 1; yy < top; yy++) {
								d.set(x, yy, z, glass.pane());
							}
							if (w == 2) {
								d.set(x, top, z, accent.stairsOrSlab(c == 0 ? s.along().getOpposite() : s.along(), Half.TOP));
							} else {
								d.set(x, top, z, accent.full());
							}
						}
						i += w + 1;
					}
				}
			}
			case OPEN -> {
				// posts at the ends, the opening clear to a beam, folding leaves (open trapdoors) against the posts
				try (var st = d.stage(StageKind.OPENINGS)) {
					for (int i = ia; i <= ib; i++) {
						if (i == di) {
							continue;
						}
						int x = s.x(i);
						int z = s.z(i);
						for (int yy = y; yy < top; yy++) {
							d.set(x, yy, z, Blocks.AIR.defaultBlockState());
						}
						d.set(x, top, z, beam.log(s.axis()));
					}
					for (int i : new int[]{ia - 1, ib + 1}) {
						if (i < 0 || i > s.last()) {
							continue;
						}
						for (int yy = y; yy < top; yy++) {
							d.set(s.x(i), yy, s.z(i), frame.log(Direction.Axis.Y));
						}
					}
					// leaves folded back inside, against the posts
					for (int i : new int[]{ia, ib}) {
						if (i == di) {
							continue;
						}
						int x = s.x(i) + Dirs.dx(in);
						int z = s.z(i) + Dirs.dz(in);
						Direction face = i == ia ? s.along() : s.along().getOpposite();
						for (int yy = y; yy < top; yy++) {
							if (d.isAbsent(x, yy, z) || d.isEmpty(x, yy, z)) {
								d.set(x, yy, z, shutter.trapdoor(face, Half.BOTTOM, true));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * A cloth awning over wall cells {@code i0..i1} at height y (a block out; at least three up when it overhangs the
	 * street), in stripes of the house colour and white when {@code stripes}.
	 */
	public static void awning(Draft d, Side s, int i0, int i1, int y, boolean stripes) {
		Mat cloth = d.mat(Role.CLOTH);
		Direction in = s.out().getOpposite();
		Block whiteStairs = Blocks.WOOL_STAIRS.pick(net.minecraft.world.item.DyeColor.WHITE);
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int i = Math.max(0, i0); i <= Math.min(s.last(), i1); i++) {
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				if (!allowed(d, x, y, z) || !d.isAbsent(x, y, z)) {
					continue;
				}
				Paint p = cloth.stairsOrSlab(in, Half.BOTTOM);
				if (stripes && ((i - i0) & 1) == 1) {
					p = Paint.of(States.stairs(whiteStairs, in, Half.BOTTOM));
				}
				d.set(x, y, z, p);
			}
		}
	}

	/**
	 * A hanging sign on a bracket out from wall cell i at height y (at least three up over the street): a beam into the
	 * wall's face with the board hanging from it, the trade's name on both faces.
	 */
	public static void hangingSign(Draft d, Side s, int i, int y, List<String> lines) {
		int x = s.outX(i, 1);
		int z = s.outZ(i, 1);
		if (!allowed(d, x, y, z) || !d.isAbsent(x, y, z) || !Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
			return;
		}
		Block sign = d.mat(Role.FURNITURE).main(Form.WALL_HANGING_SIGN);
		if (sign == null) {
			sign = Blocks.SPRUCE_WALL_HANGING_SIGN;
		}
		// facing along the wall so its bar runs back into the wall behind
		Direction face = s.along();
		CompoundTag tag = signBoth(lines);
		BlockState state = States.facing(sign, face);
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, state, tag));
	}

	/** Sign text on both faces, waxed. */
	public static CompoundTag signBoth(List<String> lines) {
		CompoundTag t = BlockEntities.sign(lines, "black", false);
		CompoundTag front = t.getCompoundOrEmpty("front_text").copy();
		t.put("back_text", front);
		return t;
	}

	/** A trade's sign text: the house's trade and a name drawn from the seed. */
	public static List<String> tradeName(Draft d, String trade) {
		String[] names = {"Alder", "Brook", "Corwin", "Dale", "Ellis", "Fenn", "Garrow", "Hale", "Ives", "Jory", "Kell", "Lark", "Moss", "Nye", "Oakes",
			"Penn", "Quill", "Rook", "Sable", "Thorne", "Vale", "Wren"};
		String n = names[(int) Math.floorMod(Noise.hash(d.seed(), 0x5167), (long) names.length)];
		return List.of("", n + "'s", trade, "");
	}

	/**
	 * Inside a shop: a counter across the shop a row in from the front, the merchant's side behind it, goods on shelves
	 * and in barrels at the back. Registers COUNTER (where the merchant stands, facing the customer), the customer's
	 * spot as a MARKET_STALL, the merchant's JOB_SITE and the store. The counter runs from x0 to x1 at row z, with a gap
	 * at {@code gap} to get behind it.
	 */
	public static void counter(Draft d, int x0, int x1, int z, int y, int gap, int backZ, Direction toFront) {
		Mat wood = d.mat(Role.FURNITURE);
		Direction toBack = toFront.getOpposite();
		int mid = (x0 + x1) / 2 == gap ? (x0 + x1) / 2 + (gap > x0 ? -1 : 1) : (x0 + x1) / 2;
		try (var st = d.stage(StageKind.INTERIOR)) {
			for (int x = x0; x <= x1; x++) {
				if (x == gap) {
					continue;
				}
				double u = Noise.unit(d.seed() ^ 0xC0C0, x, y, z);
				if (u < 0.25 && x != mid) {
					d.set(x, y, z, States.facing(Blocks.BARREL, toFront));
				} else {
					d.set(x, y, z, wood.slab(SlabType.TOP));
					if (x != mid) {
						wares(d, x, y + 1, z, u);
					}
				}
			}
		}
		d.poi(PoiType.COUNTER, mid, y, z + Dirs.dz(toBack), toFront);
		d.poi(PoiType.MARKET_STALL, mid, y, z + Dirs.dz(toFront), toBack);
		// behind: the merchant's barrel and a chest against the back
		if (backZ != z) {
			int jx = gap == x0 ? x1 : x0;
			Furniture.workstation(d, JobType.MERCHANT, jx, y, backZ, toFront);
			int cx = jx == x0 ? Math.min(x1, x0 + 2) : Math.max(x0, x1 - 2);
			if (cx != jx && cx != gap) {
				Furniture.chest(d, cx, y, backZ, toFront, List.of(new Object[]{"minecraft:bread", 6}, new Object[]{"minecraft:candle", 4},
					new Object[]{"minecraft:paper", 8}));
			}
		}
	}

	/** Something for sale on a counter top or shelf. */
	public static void wares(Draft d, int x, int y, int z, double u) {
		BlockState s = u < 0.35 ? Blocks.FLOWER_POT.defaultBlockState() : u < 0.55 ? Blocks.CANDLE.defaultBlockState()
			: u < 0.7 ? Blocks.DECORATED_POT.defaultBlockState() : u < 0.8 ? Blocks.POTTED_FERN.defaultBlockState() : null;
		if (s != null) {
			d.set(x, y, z, s);
		}
	}

	/** A door with a fanlight-less lintel: two leaves for a wide door, one otherwise; registers nothing (see Openings). */
	public static void plainDoor(Draft d, int x, int y, int z, Direction facing) {
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.inStage(StageKind.OPENINGS, () -> d.door(x, y, z, door, facing, DoorHingeSide.LEFT));
	}

	/** Potted plants and a bench along the front row in the cells that are free, avoiding the door's approach. */
	public static void streetLife(Draft d, int x0, int x1, int z, int y, int door) {
		for (int x = x0; x <= x1; x++) {
			if (Math.abs(x - door) <= 1 || !d.isAbsent(x, y, z) || !d.isSolid(x, y - 1, z) && !Garden.groundLike(d, x, y - 1, z)) {
				continue;
			}
			double u = Noise.unit(d.seed() ^ 0x57EE7, x, y, z);
			if (u < 0.3) {
				Garden.pot(d, x, y, z);
			} else if (u < 0.4) {
				int bx = x;
				d.inStage(StageKind.EXTERIOR, () -> d.set(bx, y, z, States.facing(Blocks.BARREL, Direction.UP)));
			}
		}
	}
}
