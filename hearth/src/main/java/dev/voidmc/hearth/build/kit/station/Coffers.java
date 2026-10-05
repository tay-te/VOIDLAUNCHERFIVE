package dev.voidmc.hearth.build.kit.station;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The furniture of money and goods, for the homes of those who keep them (the merchant's counting room, the reeve's
 * office, the priest's tithe chest): the strongbox (a chest against two walls, STRONGBOX where its keeper kneels), the
 * counter across a shop with the merchant's barrel behind it (COUNTER and JOB_SITE behind, MARKET_STALL before), goods
 * stacked in a store (barrels, sacks, bales, crates) and a pair of scales. Every piece claims its cells in the room it is
 * put in, so the room stays walkable.
 */
public final class Coffers {
	private Coffers() {
	}

	/** What a merchant keeps locked away. */
	public static final List<Object[]> MERCHANT = List.of(new Object[]{"minecraft:gold_ingot", 5}, new Object[]{"minecraft:emerald", 9},
		new Object[]{"minecraft:paper", 16}, new Object[]{"minecraft:writable_book", 1});
	/** What an official keeps locked away: the rolls, the seal, the dues. */
	public static final List<Object[]> RECORDS = List.of(new Object[]{"minecraft:paper", 24}, new Object[]{"minecraft:writable_book", 2},
		new Object[]{"minecraft:emerald", 4}, new Object[]{"minecraft:honeycomb", 2});
	/** What a priest keeps locked away: the plate and the tithe. */
	public static final List<Object[]> TITHE = List.of(new Object[]{"minecraft:gold_nugget", 12}, new Object[]{"minecraft:emerald", 2},
		new Object[]{"minecraft:candle", 6}, new Object[]{"minecraft:writable_book", 1});

	/**
	 * A strongbox in a corner of the room (against two walls, else against one), never on a cell in {@code avoid}: a chest
	 * facing into the room, locked by its keeper, a candle or a pot on it where there is head room. Registers STRONGBOX
	 * where its keeper kneels. Returns the chest's cell or null.
	 */
	public static int @Nullable [] strongbox(Draft d, Rooms.Room room, List<Object[]> items, int @Nullable [] near) {
		int[][] cells = candidates(room, near);
		for (int[] c : cells) {
			Direction into = into(room, c[0], c[1]);
			if (into == null) {
				continue;
			}
			int fx = c[0] + Dirs.dx(into);
			int fz = c[1] + Dirs.dz(into);
			if (!room.inside(fx, fz) || !d.isEmpty(c[0], room.y, c[1]) || !d.isEmpty(fx, room.y, fz)) {
				continue;
			}
			room.keep(fx, fz);
			if (!room.claim(c[0], c[1])) {
				continue;
			}
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(c[0], room.y, c[1], States.facing(Blocks.CHEST, into), BlockEntities.container(items));
			}
			d.poi(PoiType.STRONGBOX, fx, room.y, fz, into.getOpposite());
			return c;
		}
		return null;
	}

	/** Corner cells first (nearest {@code near} first when given), then every other cell along the walls. */
	private static int[][] candidates(Rooms.Room r, int @Nullable [] near) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		out.add(new int[]{r.x0, r.z0});
		out.add(new int[]{r.x1, r.z0});
		out.add(new int[]{r.x0, r.z1});
		out.add(new int[]{r.x1, r.z1});
		if (near != null) {
			out.sort(java.util.Comparator.comparingInt(c -> Math.abs(c[0] - near[0]) + Math.abs(c[1] - near[1])));
		}
		for (int x = r.x0 + 1; x < r.x1; x++) {
			out.add(new int[]{x, r.z1});
			out.add(new int[]{x, r.z0});
		}
		for (int z = r.z0 + 1; z < r.z1; z++) {
			out.add(new int[]{r.x0, z});
			out.add(new int[]{r.x1, z});
		}
		return out.toArray(new int[0][]);
	}

	/** The direction from a wall cell into the room (away from the nearer wall), or null for a cell off the walls. */
	static @Nullable Direction into(Rooms.Room r, int x, int z) {
		if (z == r.z1 && r.z1 > r.z0) {
			return Dirs.FRONT;
		}
		if (z == r.z0 && r.z1 > r.z0) {
			return Dirs.BACK;
		}
		if (x == r.x0 && r.x1 > r.x0) {
			return Dirs.RIGHT;
		}
		if (x == r.x1 && r.x1 > r.x0) {
			return Dirs.LEFT;
		}
		return null;
	}

	/**
	 * A shop counter over the straight run of cells from (x0, z0) to (x1, z1): slab tops of the furniture's wood on
	 * barrels and closed trapdoors, a pair of scales (a chain over a pressure plate) and wares on it. The merchant stands
	 * on the far side of {@code toCustomer}; registers COUNTER on the merchant's side at {@code mid}, MARKET_STALL on the
	 * customers' side. Claims the run (skipping it all if the room would be cut).
	 */
	public static boolean counter(Draft d, Rooms.Room room, int x0, int z0, int x1, int z1, Direction toCustomer, int midX, int midZ) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				if (!room.inside(x, z) || !d.isEmpty(x, room.y, z)) {
					return false;
				}
			}
		}
		int bxM = midX - Dirs.dx(toCustomer);
		int bzM = midZ - Dirs.dz(toCustomer);
		int cxM = midX + Dirs.dx(toCustomer);
		int czM = midZ + Dirs.dz(toCustomer);
		if (!room.inside(bxM, bzM) || !room.inside(cxM, czM)) {
			return false;
		}
		room.keep(bxM, bzM);
		room.keep(cxM, czM);
		List<int[]> claimed = new java.util.ArrayList<>();
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				if (!room.claim(x, z)) {
					return claimed.isEmpty() ? false : place(d, room, claimed, toCustomer, midX, midZ, bxM, bzM, cxM, czM);
				}
				claimed.add(new int[]{x, z});
			}
		}
		return place(d, room, claimed, toCustomer, midX, midZ, bxM, bzM, cxM, czM);
	}

	private static boolean place(Draft d, Rooms.Room room, List<int[]> cells, Direction toCustomer, int midX, int midZ, int bxM, int bzM, int cxM,
		int czM) {
		Mat wood = d.mat(Role.FURNITURE);
		int y = room.y;
		Direction back = toCustomer.getOpposite();
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int[] c : cells) {
				double u = Noise.unit(d.seed() ^ 0xC0FFE, c[0], y, c[1]);
				if (c[0] == midX && c[1] == midZ) {
					d.set(c[0], y, c[1], wood.has(Form.SLAB) ? wood.slab(SlabType.TOP) : wood.full());
					if (room.height >= 2) {
						// the scales
						d.set(c[0], y + 1, c[1], Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
					}
				} else if (u < 0.45) {
					d.set(c[0], y, c[1], States.facing(Blocks.BARREL, back));
				} else {
					d.set(c[0], y, c[1], wood.trapdoor(toCustomer, Half.TOP, false));
					if (room.height >= 2 && u > 0.8) {
						d.set(c[0], y + 1, c[1], Blocks.FLOWER_POT.defaultBlockState());
					}
				}
			}
		}
		d.poi(PoiType.COUNTER, bxM, y, bzM, toCustomer);
		d.poi(PoiType.MARKET_STALL, cxM, y, czM, back);
		return true;
	}

	/**
	 * The merchant's own place: a barrel (the trade's workstation, JOB_SITE before it) at (x, z) facing into the room.
	 * Claims the cell and keeps its front free. Returns whether it was placed.
	 */
	public static boolean desk(Draft d, Rooms.Room room, int x, int z, Direction facing, JobType job) {
		int fx = x + Dirs.dx(facing);
		int fz = z + Dirs.dz(facing);
		if (!room.inside(x, z) || !room.inside(fx, fz) || !d.isEmpty(x, room.y, z) || !d.isEmpty(fx, room.y, fz)) {
			return false;
		}
		room.keep(fx, fz);
		if (!room.claim(x, z)) {
			return false;
		}
		Furniture.workstation(d, job, x, room.y, z, facing);
		return true;
	}

	/**
	 * Goods stacked in a store over a rectangle, two high where the head room allows and never on a kept cell: barrels
	 * of wine and herring, sacks (bales of wool and brown cloth), crates (closed barrels on their sides), a chest of the
	 * finer stock, fill about {@code fill} of the cells along the walls and leave a way down the middle.
	 */
	public static void goods(Draft d, Rooms.Room room, double fill, int maxHigh) {
		long seed = Noise.hash(d.seed(), 0x600D5);
		int y = room.y;
		for (int z = room.z0; z <= room.z1; z++) {
			for (int x = room.x0; x <= room.x1; x++) {
				boolean wall = x == room.x0 || x == room.x1 || z == room.z1;
				if (!wall || Noise.unit(seed, x, 0, z) > fill || !d.isEmpty(x, y, z) || !room.claim(x, z)) {
					continue;
				}
				double u = Noise.unit(seed ^ 0x51, x, 1, z);
				int high = Math.min(maxHigh, room.height - 1);
				int hh = u < 0.5 && high >= 2 ? 2 : 1;
				try (var s = d.stage(StageKind.INTERIOR)) {
					for (int k = 0; k < hh; k++) {
						double v = Noise.unit(seed ^ 0x77, x, k, z);
						if (v < 0.4) {
							d.set(x, y + k, z, States.facing(Blocks.BARREL, k == 0 ? Direction.UP : Dirs.FRONT));
						} else if (v < 0.6) {
							d.set(x, y + k, z, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BROWN).defaultBlockState());
						} else if (v < 0.75) {
							d.set(x, y + k, z, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
						} else if (v < 0.9) {
							d.set(x, y + k, z, States.axis(Blocks.HAY_BLOCK, (x & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z));
						} else {
							d.set(x, y + k, z, States.facing(Blocks.BARREL, Dirs.LEFT));
						}
					}
				}
			}
		}
	}
}
