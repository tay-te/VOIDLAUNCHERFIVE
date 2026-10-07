package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * The house's last walk-round before it is handed over, as a builder walks a house with the owner: every door is tried
 * from both sides, and every edge someone can walk to is looked over for a fall.
 *
 * <ul>
 *   <li><b>Doors.</b> A door must open from a floor on both sides with head room over it (the audit's rule: level, a
 *   step down, or a stair or slab up). What the dressing put in a door's way (a pot from the window box, a shutter, a
 *   candle, a lantern stood on the floor) is taken away. A door that still opens onto a wall, a rail or the air is no
 *   door: it is walled up to a window (the balcony it looked onto stays as a French balcony).</li>
 *   <li><b>Edges.</b> The house is walked from its entrances the way a person walks it (the light check's walker: no
 *   standing on pots, candles or lanterns; a step of one, a drop of three, ladders). Every walked cell beside a drop of
 *   four or more gets a guard in the air cell over the drop: a rail of the culture's fencing, or a low wall of its
 *   masonry where it builds in stone and mud. A rail never stands in a walked cell, so no way through is closed.</li>
 *   <li>Then the sealed voids are filled ({@link dev.voidmc.hearth.build.kit.refine.Voids#finish}).</li>
 * </ul>
 *
 * <p>This is the homes' own rule until the shared {@code kit/Doors} and {@code kit/Guards} land (AUDIT.md §7 L1); it
 * then becomes a call to them.
 */
public final class Settle {
	private Settle() {
	}

	/** The door sweep, the edge guards and the sealed voids, in that order: the last stroke of a home. */
	public static void finish(Draft d) {
		doors(d);
		yardDoors(d);
		reachThings(d);
		guards(d);
		// (a gallery door whose lattice became a rail is a window now)
		doors(d);
		if (debug(d)) {
			int[] lost = lostOutside(d, walk(d), d.bodyRect());
			System.out.println("settle end lost=" + (lost == null ? "none" : lost[0] + "/" + lost[1] + "/" + lost[2]));
			LongOpenHashSet w = walk(d);
			for (var e : d.cells().long2ObjectEntrySet()) {
				BlockState st = e.getValue().state();
				long k = e.getLongKey();
				if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && !w.contains(k)) {
					int x = Draft.keyX(k);
					int y = Draft.keyY(k);
					int z = Draft.keyZ(k);
					Direction f = st.getValue(DoorBlock.FACING);
					System.out.println("settle unreached door " + x + "/" + y + "/" + z + " " + f + " below=" + at(d, x, y - 1, z)
						+ " out=" + at(d, x + f.getStepX(), y, z + f.getStepZ()) + "/" + at(d, x + f.getStepX(), y - 1, z + f.getStepZ()) + " r=" + w.contains(Draft.key(x + f.getStepX(), y, z + f.getStepZ()))
						+ " in=" + at(d, x - f.getStepX(), y, z - f.getStepZ()) + "/" + at(d, x - f.getStepX(), y - 1, z - f.getStepZ()) + " r=" + w.contains(Draft.key(x - f.getStepX(), y, z - f.getStepZ())));
				}
			}
		}
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	// ------------------------------------------------------------------------------------------------- doors

	/** Tries every door from both sides; clears dressing in its way, walls up the ones that still open onto nothing. */
	public static int doors(Draft d) {
		List<int[]> found = new ArrayList<>();
		for (var e : d.cells().long2ObjectEntrySet()) {
			BlockState s = e.getValue().state();
			if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				long k = e.getLongKey();
				found.add(new int[]{Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k)});
			}
		}
		int walled = 0;
		for (int[] p : found) {
			BlockState s = d.get(p[0], p[1], p[2]);
			if (s == null || !(s.getBlock() instanceof DoorBlock)) {
				continue;
			}
			Direction f = s.getValue(DoorBlock.FACING);
			threshold(d, p[0], p[1], p[2], f);
			lintel(d, p[0], p[1], p[2], f);
			boolean ok = true;
			for (Direction side : new Direction[]{f, f.getOpposite()}) {
				int nx = p[0] + side.getStepX();
				int nz = p[2] + side.getStepZ();
				if (!sideOk(d, nx, p[1], nz)) {
					clear(d, nx, p[1], nz);
					ok &= sideOk(d, nx, p[1], nz);
				}
			}
			if (!ok && debug(d)) {
				System.out.println("settle door " + p[0] + "/" + p[1] + "/" + p[2] + " " + f + " entrance=" + entrance(d, p[0], p[1], p[2])
					+ " out=" + at(d, p[0] + f.getStepX(), p[1], p[2] + f.getStepZ()) + "/" + at(d, p[0] + f.getStepX(), p[1] - 1, p[2] + f.getStepZ())
					+ " in=" + at(d, p[0] - f.getStepX(), p[1], p[2] - f.getStepZ()) + "/" + at(d, p[0] - f.getStepX(), p[1] - 1, p[2] - f.getStepZ()));
			}
			if (!ok && !entrance(d, p[0], p[1], p[2])) {
				wallUp(d, p[0], p[1], p[2], f);
				walled++;
			}
		}
		return walled;
	}

	/**
	 * A door hung over nothing (an inner wall's doorway over a slope's fall, where the floor boards stop at the wall): a
	 * threshold under it, of the floor beside it.
	 */
	private static void threshold(Draft d, int x, int y, int z, Direction f) {
		if (standable(at(d, x, y - 1, z)) || !d.allowed(x, y - 1, z)) {
			return;
		}
		BlockState sill = null;
		for (Direction side : new Direction[]{f, f.getOpposite()}) {
			BlockState b = d.get(x + side.getStepX(), y - 1, z + side.getStepZ());
			if (b != null && b.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				sill = b;
				break;
			}
		}
		BlockState fill = sill != null ? sill : d.mat(Role.FOUNDATION).block(Form.FULL, x, y - 1, z).defaultBlockState();
		d.inStage(StageKind.FLOORS, () -> d.set(x, y - 1, z, fill));
	}

	/**
	 * A door in a wall that stops at its head (an inner wall two high under a tall storey): a lintel over it of the wall
	 * beside it, or one could climb over the shut door as over a step.
	 */
	private static void lintel(Draft d, int x, int y, int z, Direction f) {
		if (!passable(at(d, x, y + 2, z)) || !d.allowed(x, y + 2, z)) {
			return;
		}
		Direction along = f.getClockWise();
		BlockState wall = null;
		for (Direction a : new Direction[]{along, along.getOpposite()}) {
			for (int yy = y + 2; yy >= y + 1 && wall == null; yy--) {
				BlockState n = d.get(x + a.getStepX(), yy, z + a.getStepZ());
				if (n != null && n.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					wall = n;
				}
			}
		}
		if (wall == null) {
			return;
		}
		BlockState w = wall;
		d.inStage(StageKind.WALLS, () -> d.set(x, y + 2, z, w));
	}

	/** Whether someone can stand on this side of a door: level, a step down, a stair or slab up, or another door. */
	public static boolean sideOk(Draft d, int x, int y, int z) {
		BlockState here = at(d, x, y, z);
		boolean stepUp = here.getBlock() instanceof StairBlock && here.getValue(StairBlock.HALF) == Half.BOTTOM
			|| here.getBlock() instanceof SlabBlock && here.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
		return walkable(d, x, y, z) || walkable(d, x, y - 1, z) && passable(here) || stepUp && walkable(d, x, y + 1, z)
			|| here.getBlock() instanceof DoorBlock;
	}

	/** Whether a door at (x, y, z) facing {@code facing} would open from a floor on both sides. */
	public static boolean ok(Draft d, int x, int y, int z, Direction facing) {
		return sideOk(d, x + facing.getStepX(), y, z + facing.getStepZ()) && sideOk(d, x - facing.getStepX(), y, z - facing.getStepZ());
	}

	/** Takes away the small things dressing left at feet and head height in a door's way. */
	private static void clear(Draft d, int x, int y, int z) {
		// ground banked up against the door (a slope, a bed of soil): dug away to the door's sill
		if (d.allowed(x, y, z) && dev.voidmc.hearth.build.kit.Garden.groundLike(d, x, y, z) && standable(at(d, x, y - 1, z))) {
			d.inStage(StageKind.EARTHWORK, () -> {
				d.set(x, y, z, Blocks.AIR.defaultBlockState());
				if (d.allowed(x, y + 1, z) && dev.voidmc.hearth.build.kit.Garden.groundLike(d, x, y + 1, z)) {
					d.set(x, y + 1, z, Blocks.AIR.defaultBlockState());
				}
			});
		}
		for (int yy = y; yy <= y + 1; yy++) {
			BlockState s = d.get(x, yy, z);
			if (s != null && dressing(s)) {
				lift(d, x, yy, z);
			}
		}
	}

	/** Takes a thing away and whatever rests on it (a carpet hung over a rail, a pot or a lamp on a table, a candle). */
	public static void lift(Draft d, int x, int y, int z) {
		d.inStage(StageKind.INTERIOR, () -> {
			d.set(x, y, z, Blocks.AIR.defaultBlockState());
			for (int yy = y + 1; yy <= y + 2; yy++) {
				BlockState up = d.get(x, yy, z);
				if (up == null || !rests(up)) {
					break;
				}
				d.set(x, yy, z, Blocks.AIR.defaultBlockState());
			}
		});
	}

	/** Something that stands on the block under it and falls without it. */
	static boolean rests(BlockState s) {
		Block b = s.getBlock();
		return b instanceof CarpetBlock || b instanceof FlowerPotBlock || b instanceof net.minecraft.world.level.block.CandleBlock
			|| b instanceof LanternBlock && !s.getValue(LanternBlock.HANGING) || b instanceof VegetationBlock
			|| b instanceof net.minecraft.world.level.block.DecoratedPotBlock || b instanceof net.minecraft.world.level.block.SnowLayerBlock;
	}

	/** Dressing that may be lifted out of a doorway: pots, candles, a lantern on the floor, shutters, a skull, a sign. */
	private static boolean dressing(BlockState s) {
		Block b = s.getBlock();
		return b instanceof FlowerPotBlock || b instanceof net.minecraft.world.level.block.CandleBlock
			|| b instanceof LanternBlock && !s.getValue(LanternBlock.HANGING) || b instanceof TrapDoorBlock
			|| b instanceof net.minecraft.world.level.block.AbstractSkullBlock || b instanceof net.minecraft.world.level.block.SignBlock
			|| b instanceof net.minecraft.world.level.block.SeaPickleBlock;
	}

	/** Whether a door is a declared entrance (its outside cell is one): the front door is never walled up. */
	private static boolean entrance(Draft d, int x, int y, int z) {
		for (int[] e : d.entrances()) {
			if (Math.abs(e[0] - x) + Math.abs(e[2] - z) <= 1 && Math.abs(e[1] - y) <= 1) {
				return true;
			}
		}
		return false;
	}

	/** A door that opens onto nothing walled up below and glazed above, in the stuff of the wall beside it. */
	private static void wallUp(Draft d, int x, int y, int z, Direction facing) {
		Direction along = facing.getClockWise();
		BlockState wall = null;
		for (Direction a : new Direction[]{along, along.getOpposite()}) {
			BlockState n = d.get(x + a.getStepX(), y, z + a.getStepZ());
			if (n != null && n.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				wall = n;
				break;
			}
		}
		BlockState fill = wall != null ? wall : d.mat(Role.WALL).block(Form.FULL, x, y, z).defaultBlockState();
		Mat glass = d.mat(Role.GLASS);
		d.inStage(StageKind.OPENINGS, () -> {
			d.set(x, y, z, fill);
			d.set(x, y + 1, z, glass.pane());
		});
	}

	// ------------------------------------------------------------------------------------------------- yard doors

	/**
	 * A back door to the yard: where a water butt, a barrel or a composter out in the yard cannot be walked to (the house
	 * fills its plot's width, the slope cuts the way round), a door is cut through the body's wall from a walked cell inside
	 * to the yard, the nearest one that brings the thing within reach. Answers how many doors were cut.
	 */
	public static int yardDoors(Draft d) {
		int cut = 0;
		int[] b = d.bodyRect();
		for (int round = 0; round < 6; round++) {
			LongOpenHashSet reached = walk(d);
			int[] lost = lostOutside(d, reached, b);
			if (debug(d)) {
				System.out.println("settle yard lost=" + (lost == null ? "none" : lost[0] + "/" + lost[1] + "/" + lost[2]) + " body=" + java.util.Arrays.toString(b));
			}
			if (lost == null) {
				return cut;
			}
			// the wall cells of the body's ground storey, nearest the lost thing first
			List<int[]> walls = new ArrayList<>();
			for (int[] c : Draft.ring(b[0], b[1], b[2], b[3])) {
				boolean corner = (c[0] == b[0] || c[0] == b[2]) && (c[1] == b[1] || c[1] == b[3]);
				if (!corner) {
					walls.add(c);
				}
			}
			walls.sort(java.util.Comparator.comparingInt(c -> Math.abs(c[0] - lost[0]) + Math.abs(c[1] - lost[2])));
			boolean done = false;
			for (int i = 0; i < Math.min(16, walls.size()) && !done; i++) {
				int x = walls.get(i)[0];
				int z = walls.get(i)[1];
				Direction out = x == b[0] ? Dirs.LEFT : x == b[2] ? Dirs.RIGHT : z == b[1] ? Dirs.FRONT : Dirs.BACK;
				int ix = x - out.getStepX();
				int iz = z - out.getStepZ();
				for (int y = lost[1] - 1; y <= lost[1] + 1 && !done; y++) {
					if (!reached.contains(Draft.key(ix, y, iz)) || !wallOrPane(d, x, y, z) || !wallOrPane(d, x, y + 1, z) || d.isEmpty(x, y + 2, z)
						|| !d.isEmpty(ix, y, iz) || !d.allowed(x + out.getStepX(), y, z + out.getStepZ())) {
						continue;
					}
					BlockState was0 = d.get(x, y, z);
					BlockState was1 = d.get(x, y + 1, z);
					Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
					int fy = y;
					d.inStage(StageKind.OPENINGS, () -> d.door(x, fy, z, door, out, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
					if (ok(d, x, y, z, out) && reachedThing(d, walk(d), lost)) {
						d.poi(dev.voidmc.hearth.api.PoiType.DOOR, x + out.getStepX(), y, z + out.getStepZ(), out.getOpposite());
						if (debug(d)) {
							System.out.println("settle yard door " + x + "/" + y + "/" + z + " " + out);
						}
						cut++;
						done = true;
					} else {
						d.inStage(StageKind.WALLS, () -> {
							d.set(x, fy, z, was0);
							d.set(x, fy + 1, z, was1);
						});
					}
				}
			}
			if (!done) {
				// no wall a door could be cut through (the stair fills the back of the house, the plot's edge is at its side):
				// the yard is a garden seen from the windows, and what it would take a door to use is not put there
				int lx = lost[0];
				int ly = lost[1];
				int lz = lost[2];
				lift(d, lx, ly, lz);
				d.retainPois(p -> !(p.x() == lx && p.z() == lz && Math.abs(p.y() - ly) <= 1));
			}
		}
		return cut;
	}

	/** A wall block or a window's glass (a door may be cut through either). */
	private static boolean wallOrPane(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null && (d.isSolid(x, y, z) && !(s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock)
			|| s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock);
	}

	/** The first thing someone uses (a store, a butt, a job block) outside the body that no walked cell stands beside. */
	private static int[] lostOutside(Draft d, LongOpenHashSet reached, int[] b) {
		for (var e : d.cells().long2ObjectEntrySet()) {
			if (!used(e.getValue().state()) || e.getValue().state().getBlock() instanceof BedBlock) {
				continue;
			}
			long k = e.getLongKey();
			int[] c = {Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k)};
			if (c[0] >= b[0] && c[0] <= b[2] && c[2] >= b[1] && c[2] <= b[3] || !inPlot(d, c[0], c[2])) {
				continue;
			}
			if (!reachedThing(d, reached, c)) {
				return c;
			}
		}
		return null;
	}

	/** Whether someone walked stands beside (or on) a thing at c. */
	private static boolean reachedThing(Draft d, LongOpenHashSet reached, int[] c) {
		for (Direction dir : Dirs.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy++) {
				if (reached.contains(Draft.key(c[0] + dir.getStepX(), c[1] + dy, c[2] + dir.getStepZ()))) {
					return true;
				}
			}
		}
		return reached.contains(Draft.key(c[0], c[1] + 1, c[2]));
	}

	// ------------------------------------------------------------------------------------------------- reach

	/**
	 * Beds, stores and job blocks nobody can get to: where a stool, a table, a pot or a crate stands in the one cell from
	 * which a bed could be got into (or a chest opened), it is taken away, if that brings the thing within reach. Answers
	 * how many things were taken away.
	 */
	public static int reachThings(Draft d) {
		int taken = 0;
		LongOpenHashSet reached = walk(d);
		List<int[]> targets = new ArrayList<>();
		for (var e : d.cells().long2ObjectEntrySet()) {
			BlockState s = e.getValue().state();
			if (!used(s) || s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) != net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
				continue;
			}
			long k = e.getLongKey();
			targets.add(new int[]{Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k)});
		}
		for (int[] c : targets) {
			BlockState s = d.get(c[0], c[1], c[2]);
			if (s == null) {
				continue;
			}
			List<int[]> cells = new ArrayList<>();
			cells.add(c);
			if (s.getBlock() instanceof BedBlock) {
				Direction foot = s.getValue(BedBlock.FACING).getOpposite();
				cells.add(new int[]{c[0] + foot.getStepX(), c[1], c[2] + foot.getStepZ()});
			}
			if (gotTo(reached, cells, s)) {
				continue;
			}
			boolean done = false;
			for (int[] part : cells) {
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = part[0] + dir.getStepX();
					int nz = part[2] + dir.getStepZ();
					int y = part[1];
					BlockState n = d.get(nx, y, nz);
					if (done || n == null || movable(n) < 0 || n.getBlock() instanceof BedBlock || jobAt(d, nx, y, nz)) {
						continue;
					}
					BlockState above = d.get(nx, y + 1, nz);
					d.inStage(StageKind.INTERIOR, () -> d.set(nx, y, nz, Blocks.AIR.defaultBlockState()));
					LongOpenHashSet again = walk(d);
					if (gotTo(again, cells, s)) {
						reached = again;
						if (above != null && rests(above)) {
							d.inStage(StageKind.INTERIOR, () -> d.set(nx, y + 1, nz, Blocks.AIR.defaultBlockState()));
						}
						d.retainPois(p -> !(p.x() == nx && p.z() == nz && Math.abs(p.y() - y) <= 1 && p.type() == dev.voidmc.hearth.api.PoiType.STORAGE));
						taken++;
						done = true;
					} else {
						d.inStage(StageKind.INTERIOR, () -> d.set(nx, y, nz, n));
					}
				}
			}
		}
		return taken;
	}

	/** Whether someone walked stands beside a thing (beside either half of a bed; not on a chest's lid). */
	private static boolean gotTo(LongOpenHashSet reached, List<int[]> cells, BlockState s) {
		for (int[] c : cells) {
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = c[0] + dir.getStepX();
				int nz = c[2] + dir.getStepZ();
				boolean other = false;
				for (int[] o : cells) {
					other |= o[0] == nx && o[2] == nz;
				}
				if (other) {
					continue;
				}
				for (int dy = s.getBlock() instanceof BedBlock ? 0 : -1; dy <= (s.getBlock() instanceof BedBlock ? 0 : 1); dy++) {
					if (reached.contains(Draft.key(nx, c[1] + dy, nz))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * How readily a thing standing on a floor makes way (lower first: pots and lamps, stools and tables, stores, the
	 * trade's bales and logs), or -1 if it stays (beds, fires, ladders, doors, job blocks, anything one walks through).
	 */
	public static int movable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof BedBlock || b instanceof net.minecraft.world.level.block.CampfireBlock || b instanceof LadderBlock
			|| b instanceof DoorBlock || b instanceof net.minecraft.world.level.block.AbstractFurnaceBlock || b instanceof CarpetBlock || passable(s)) {
			return -1;
		}
		if (b instanceof FlowerPotBlock || b instanceof net.minecraft.world.level.block.DecoratedPotBlock || b instanceof LanternBlock
			|| b instanceof net.minecraft.world.level.block.CandleBlock) {
			return 0;
		}
		if (b instanceof StairBlock || b instanceof SlabBlock || b instanceof FenceBlock) {
			return 1;
		}
		if (b instanceof net.minecraft.world.level.block.ChestBlock || b instanceof net.minecraft.world.level.block.BarrelBlock) {
			return 2;
		}
		if (s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !(b instanceof net.minecraft.world.level.block.CraftingTableBlock)
			&& (b == Blocks.HAY_BLOCK || b instanceof net.minecraft.world.level.block.RotatedPillarBlock || b instanceof net.minecraft.world.level.block.CarvedPumpkinBlock
			|| b == Blocks.HONEYCOMB_BLOCK || b == Blocks.BOOKSHELF)) {
			return 3;
		}
		return -1;
	}

	/** Whether a job site stands at the cell (its block stays). */
	private static boolean jobAt(Draft d, int x, int y, int z) {
		for (Draft.LocalPoi p : d.pois()) {
			if (p.x() == x && p.z() == z && Math.abs(p.y() - y) <= 1 && p.type() == dev.voidmc.hearth.api.PoiType.JOB_SITE) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------- guards

	/** Rails every walked edge over a drop of four or more; answers how many guards were set. */
	public static int guards(Draft d) {
		LongOpenHashSet reached = walk(d);
		Mat fence = d.mat(Role.FENCE);
		Traits t = Traits.of(d);
		Mat wall = d.mat(Role.WALL);
		boolean masonry = (t.flat() || t.masonry() && !t.timber()) && wall.has(Form.WALL);
		var guard = masonry ? wall.wall() : fence.fence();
		LongOpenHashSet guarded = new LongOpenHashSet();
		// walked cells at the plot's edge whose drop lies outside it, where a rail cannot stand: railed in themselves
		LongArrayList edge = new LongArrayList();
		LongArrayList order = new LongArrayList(reached);
		for (int i = 0; i < order.size(); i++) {
			long k = order.getLong(i);
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			if (!inPlot(d, x, z) || wet(d, x, y, z)) {
				continue;
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				if (!passable(at(d, nx, y, nz)) || !passable(at(d, nx, y + 1, nz)) || at(d, nx, y, nz).getBlock() instanceof DoorBlock) {
					continue;
				}
				if (drop(d, nx, y, nz) < 4 || reached.contains(Draft.key(nx, y, nz))) {
					continue;
				}
				if (!d.allowed(nx, y, nz) || at(d, x, y, z).getBlock() instanceof TrapDoorBlock) {
					if (!edge.contains(k)) {
						edge.add(k);
					}
					continue;
				}
				if (!d.isEmpty(nx, y, nz) && !(at(d, nx, y, nz).getBlock() instanceof CarpetBlock)) {
					continue;
				}
				if (guarded.add(Draft.key(nx, y, nz))) {
					int gx = nx;
					int gz = nz;
					d.inStage(StageKind.EXTERIOR, () -> d.set(gx, y, gz, guard));
				}
			}
		}
		if (!edge.isEmpty()) {
			guarded.addAll(railIn(d, edge, guard));
		}
		return guarded.size();
	}

	/**
	 * Rails walked cells in themselves (the plot's edge, where the drop beyond is not the house's to rail), all at once
	 * where that closes no way to anywhere else that was walked, else one by one, keeping each only if it closes none.
	 */
	private static LongArrayList railIn(Draft d, LongArrayList cells, dev.voidmc.hearth.build.draft.Paint guard) {
		LongArrayList kept = new LongArrayList();
		boolean dbg = debug(d);
		LongOpenHashSet before = needed(d, walk(d));
		// the cells to rail in must be free of anything but air, and not the floor of a door, ladder or stair
		LongArrayList ok = new LongArrayList();
		for (int i = 0; i < cells.size(); i++) {
			long k = cells.getLong(i);
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			if (dbg) {
				System.out.println("settle edge " + x + "/" + y + "/" + z + " allowed=" + d.allowed(x, y, z) + " access=" + access(d, x, y, z) + " feet=" + d.get(x, y, z));
			}
			if (!d.allowed(x, y, z) || access(d, x, y, z)) {
				continue;
			}
			BlockState feet = d.get(x, y, z);
			if (feet != null && feet.getBlock() instanceof TrapDoorBlock) {
				// a lattice at the floor (a window down to the boards, a gallery's panels): the walker steps through it, and
				// so might a child: a sill of the wall under a window, the railing's balusters on a gallery
				boolean window = solid(d, x, y, z, feet.getValue(TrapDoorBlock.FACING).getClockWise());
				BlockState sill = window ? wallBeside(d, x, y, z, feet.getValue(TrapDoorBlock.FACING).getClockWise()) : null;
				var rail = d.mat(Role.RAILING);
				d.inStage(StageKind.WALLS, () -> d.set(x, y, z, sill != null ? sill : rail.has(Form.FENCE) ? rail.fence().at(x, y, z) : guard.at(x, y, z)));
				kept.add(k);
				continue;
			}
			if (!d.isEmpty(x, y, z) && !(feet.getBlock() instanceof VegetationBlock) && !(feet.getBlock() instanceof CarpetBlock)) {
				continue;
			}
			ok.add(k);
		}
		if (ok.isEmpty()) {
			return kept;
		}
		if (tryRails(d, ok, guard, before)) {
			kept.addAll(ok);
			return kept;
		}
		for (int i = 0; i < ok.size(); i++) {
			LongArrayList one = LongArrayList.of(ok.getLong(i));
			if (tryRails(d, one, guard, before)) {
				kept.add(ok.getLong(i));
				before = needed(d, walk(d));
			}
		}
		return kept;
	}

	static boolean debug(Draft d) {
		String p = System.getProperty("hearth.settle.debug");
		return p != null && (d.spec().archetype().getPath() + "," + d.style().id().getPath() + "," + d.seed()).contains(p);
	}

	/**
	 * The walked cells that matter: door and ladder cells, the cells of points of interest, and the cells beside a bed, a
	 * chest, a barrel or a job block (where someone stands to use it). A strip of yard along the plot's edge may be railed
	 * off; a way to any of these may not.
	 */
	static LongOpenHashSet needed(Draft d, LongOpenHashSet reached) {
		LongOpenHashSet out = new LongOpenHashSet();
		LongOpenHashSet poi = new LongOpenHashSet();
		for (Draft.LocalPoi p : d.pois()) {
			for (int dy = -1; dy <= 1; dy++) {
				poi.add(Draft.key(p.x(), p.y() + dy, p.z()));
			}
		}
		for (long k : reached) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			Block here = at(d, x, y, z).getBlock();
			boolean need = here instanceof DoorBlock || here instanceof LadderBlock || here instanceof FenceGateBlock || poi.contains(k);
			for (Direction dir : Dirs.HORIZONTAL) {
				for (int dy = -1; dy <= 1 && !need; dy++) {
					need = used(at(d, x + dir.getStepX(), y + dy, z + dir.getStepZ()));
				}
			}
			if (need) {
				out.add(k);
			}
		}
		return out;
	}

	/** A thing someone walks up to use: a bed, a store, a job block. */
	private static boolean used(BlockState s) {
		Block b = s.getBlock();
		return b instanceof BedBlock || b instanceof net.minecraft.world.level.block.ChestBlock || b instanceof net.minecraft.world.level.block.BarrelBlock
			|| b instanceof net.minecraft.world.level.block.CraftingTableBlock || b instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
			|| b instanceof net.minecraft.world.level.block.AbstractCauldronBlock || b instanceof net.minecraft.world.level.block.ComposterBlock
			|| b instanceof net.minecraft.world.level.block.LecternBlock || b instanceof net.minecraft.world.level.block.StonecutterBlock
			|| b instanceof net.minecraft.world.level.block.GrindstoneBlock || b instanceof net.minecraft.world.level.block.LoomBlock
			|| b instanceof net.minecraft.world.level.block.BrewingStandBlock || b instanceof net.minecraft.world.level.block.AnvilBlock;
	}

	/** Sets rails in the cells; keeps them only if every cell that matters walked before is walked still. */
	private static boolean tryRails(Draft d, LongArrayList cells, dev.voidmc.hearth.build.draft.Paint guard, LongOpenHashSet before) {
		LongOpenHashSet set = new LongOpenHashSet(cells);
		List<BlockState> was = new ArrayList<>();
		d.inStage(StageKind.EXTERIOR, () -> {
			for (int i = 0; i < cells.size(); i++) {
				long k = cells.getLong(i);
				was.add(d.get(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k)));
				d.set(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k), guard);
			}
		});
		LongOpenHashSet after = walk(d);
		for (long k : before) {
			if (!set.contains(k) && !set.contains(Draft.key(Draft.keyX(k), Draft.keyY(k) - 1, Draft.keyZ(k))) && !after.contains(k)) {
				if (debug(d)) {
					System.out.println("settle rails " + cells.size() + " lose " + Draft.keyX(k) + "/" + Draft.keyY(k) + "/" + Draft.keyZ(k));
				}
				for (int i = 0; i < cells.size(); i++) {
					long c = cells.getLong(i);
					if (was.get(i) == null) {
						d.remove(Draft.keyX(c), Draft.keyY(c), Draft.keyZ(c));
					} else {
						BlockState w = was.get(i);
						d.inStage(StageKind.EXTERIOR, () -> d.set(Draft.keyX(c), Draft.keyY(c), Draft.keyZ(c), w));
					}
				}
				return false;
			}
		}
		return true;
	}

	/** Whether both cells beside (x, y, z) along {@code along} are solid: the cell is an opening in a wall. */
	private static boolean solid(Draft d, int x, int y, int z, Direction along) {
		return d.isSolid(x + along.getStepX(), y, z + along.getStepZ()) && d.isSolid(x - along.getStepX(), y, z - along.getStepZ());
	}

	/** The wall block beside an opening, to fill its foot with. */
	private static BlockState wallBeside(Draft d, int x, int y, int z, Direction along) {
		BlockState a = d.get(x + along.getStepX(), y, z + along.getStepZ());
		return a != null && a.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? a : null;
	}

	/** A cell someone needs to stand in to use a door, a ladder or a stair beside it. */
	private static boolean access(Draft d, int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			BlockState n = at(d, x + dir.getStepX(), y, z + dir.getStepZ());
			BlockState below = at(d, x + dir.getStepX(), y - 1, z + dir.getStepZ());
			if (n.getBlock() instanceof DoorBlock || n.getBlock() instanceof LadderBlock || below.getBlock() instanceof LadderBlock
				|| n.getBlock() instanceof StairBlock || below.getBlock() instanceof StairBlock || n.getBlock() instanceof FenceGateBlock) {
				return true;
			}
		}
		BlockState under = at(d, x, y - 1, z);
		return under.getBlock() instanceof LadderBlock || at(d, x, y, z).getBlock() instanceof LadderBlock;
	}

	/** How far someone stepping off into this cell falls (to something solid, a ladder, or water: then 0). */
	static int drop(Draft d, int x, int y, int z) {
		int n = 0;
		for (int dy = -1; dy >= -12; dy--) {
			BlockState below = at(d, x, y + dy, z);
			if (!below.getFluidState().isEmpty()) {
				return 0;
			}
			if (!passable(below) || below.getBlock() instanceof LadderBlock) {
				break;
			}
			n++;
		}
		return n;
	}

	private static boolean inPlot(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	private static boolean wet(Draft d, int x, int y, int z) {
		return !at(d, x, y, z).getFluidState().isEmpty() || !at(d, x, y - 1, z).getFluidState().isEmpty();
	}

	// ------------------------------------------------------------------------------------------------- the walk

	/** What stands at a cell: what was drawn, else the natural ground, its water, or air. */
	public static BlockState at(Draft d, int x, int y, int z) {
		Draft.Cell c = d.cell(x, y, z);
		if (c != null) {
			return c.state();
		}
		Site site = d.site();
		if (y < site.ground(x, z)) {
			return Blocks.STONE.defaultBlockState();
		}
		int w = site.water(x, z);
		if (w != Site.NONE && y < w) {
			return Blocks.WATER.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	/** Feet or head can be in this block (the light check's rules). */
	public static boolean passable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof CarpetBlock || b instanceof VegetationBlock
			|| b instanceof LadderBlock || b instanceof ScaffoldingBlock || b instanceof LiquidBlock || hangs(s)) {
			return true;
		}
		if (b instanceof TrapDoorBlock) {
			return s.getValue(TrapDoorBlock.OPEN) || s.getValue(TrapDoorBlock.HALF) == Half.BOTTOM;
		}
		var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || s.isPathfindable(PathComputationType.LAND) && !(b instanceof FenceBlock) && !(b instanceof WallBlock)
			&& shape.max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean hangs(BlockState s) {
		Block b = s.getBlock();
		return b instanceof LanternBlock && s.getValue(LanternBlock.HANGING) || b instanceof ChainBlock
			|| b instanceof net.minecraft.world.level.block.CeilingHangingSignBlock || b instanceof net.minecraft.world.level.block.VineBlock;
	}

	/** Something to stand on: not a fence, wall or gate, nor the small things set on furniture (candles, lanterns, pots). */
	public static boolean standable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof FenceBlock || b instanceof WallBlock || b instanceof FenceGateBlock || b instanceof net.minecraft.world.level.block.CandleBlock
			|| b instanceof LanternBlock || b instanceof FlowerPotBlock || b instanceof net.minecraft.world.level.block.CakeBlock
			|| b instanceof net.minecraft.world.level.block.AbstractSkullBlock || b instanceof net.minecraft.world.level.block.SeaPickleBlock) {
			return false;
		}
		if (b instanceof LiquidBlock) {
			return true;
		}
		return !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private static boolean climbable(BlockState s) {
		return s.getBlock() instanceof LadderBlock || s.getBlock() instanceof ScaffoldingBlock;
	}

	/** Feet can be at this cell: feet and head pass, and something under them to stand on (or a ladder). */
	public static boolean walkable(Draft d, int x, int y, int z) {
		BlockState feet = at(d, x, y, z);
		if (!passable(feet) || !passable(at(d, x, y + 1, z))) {
			return false;
		}
		if (climbable(feet)) {
			return true;
		}
		BlockState below = at(d, x, y - 1, z);
		return standable(below) || climbable(below);
	}

	private static boolean step(BlockState s) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM || s.getBlock() instanceof BedBlock;
	}

	/** Every cell walked to from the entrances and the street, within the plot and four round it. */
	public static LongOpenHashSet walk(Draft d) {
		int m = 4;
		int x0 = -m;
		int x1 = d.frontage() - 1 + m;
		int z0 = -m;
		int z1 = d.depth() - 1 + m;
		int y0 = -12;
		int y1 = d.maxHeight() + 2;
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		List<int[]> starts = new ArrayList<>(d.entrances());
		// (the light check's starts: the entrances and the street row at the plot's floor or above)
		for (int x = 0; x < d.frontage(); x++) {
			starts.add(new int[]{x, Math.max(d.site().ground(x, -1), 0), -1});
		}
		for (int[] p : starts) {
			for (int dy = 0; dy >= -2; dy--) {
				if (walkable(d, p[0], p[1] + dy, p[2])) {
					if (seen.add(Draft.key(p[0], p[1] + dy, p[2]))) {
						q.enqueue(Draft.key(p[0], p[1] + dy, p[2]));
					}
					break;
				}
			}
			if (walkable(d, p[0], p[1] + 1, p[2]) && seen.add(Draft.key(p[0], p[1] + 1, p[2]))) {
				q.enqueue(Draft.key(p[0], p[1] + 1, p[2]));
			}
		}
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			boolean climb = climbable(at(d, x, y, z));
			boolean headroom = passable(at(d, x, y + 2, z));
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				if (nx < x0 || nx > x1 || nz < z0 || nz > z1) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					int ny = y + dy;
					if (ny < y0 || ny > y1 || dy == 1 && !headroom) {
						continue;
					}
					if (walkable(d, nx, ny, nz)) {
						if (seen.add(Draft.key(nx, ny, nz))) {
							q.enqueue(Draft.key(nx, ny, nz));
						}
						break;
					}
					BlockState there = at(d, nx, ny, nz);
					if (dy == 0 && step(there) && walkable(d, nx, ny + 1, nz) && headroom) {
						if (seen.add(Draft.key(nx, ny + 1, nz))) {
							q.enqueue(Draft.key(nx, ny + 1, nz));
						}
						break;
					}
					if ((!passable(there) || dy < 0 && !passable(at(d, nx, ny + 1, nz))) && dy <= 0) {
						break;
					}
				}
			}
			if (climb) {
				for (int dy : new int[]{1, -1}) {
					if ((walkable(d, x, y + dy, z) || at(d, x, y + dy, z).getBlock() instanceof LadderBlock) && seen.add(Draft.key(x, y + dy, z))) {
						q.enqueue(Draft.key(x, y + dy, z));
					}
				}
			}
		}
		return seen;
	}
}
