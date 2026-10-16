package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Doors folk can use (the audit's rule S1): a door stands only where someone can stand on both sides of it, a floor
 * under the feet (level, a step down, or a stair or slab up) and two blocks of air over it, with nothing in the way.
 * Nothing is drawn in a door's two front cells that is not a step: no flower box, shutter, pot, chest, counter or
 * bench. Where a door cannot stand, a window does.
 *
 * <p>Use {@link #place} instead of {@link Openings#door} for a door that is not sure of its ground (an upper door, a
 * gallery's, a partition's), {@link #ok} to ask first, {@link #clearFronts} after furnishing. {@link #finish} runs on
 * every blueprint when it compiles ({@code refine.Access}): it clears what still stands before a door and bricks up a
 * door that faces a wall or opens over a drop nobody could climb (never an entrance).
 */
public final class Doors {
	private Doors() {
	}

	// =========================================================================================== asking

	/** The block at a position as drawn so far, else the natural ground of the site (stone, water or air). */
	static BlockState at(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s != null) {
			return s;
		}
		if (y < d.site().ground(x, z)) {
			return Blocks.STONE.defaultBlockState();
		}
		int wy = d.site().water(x, z);
		return wy != dev.voidmc.hearth.build.terrain.Site.NONE && y < wy ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
	}

	private static boolean step(BlockState s) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
	}

	/**
	 * Someone can stand in the cell (x, y, z) beside a door at level y: feet and head pass with a floor under them, or one
	 * step down onto a floor, or a stair or slab to step up onto with two blocks of air over it, or the leaf of a double
	 * door.
	 */
	public static boolean standable(Draft d, int x, int y, int z) {
		BlockState feet = at(d, x, y, z);
		BlockState head = at(d, x, y + 1, z);
		if (feet.getBlock() instanceof DoorBlock) {
			return true;
		}
		if (Spaces.passable(feet) && Spaces.passable(head)) {
			if (Spaces.standable(at(d, x, y - 1, z)) || at(d, x, y - 1, z).getBlock() instanceof LadderBlock) {
				return true;
			}
			// one step down
			return Spaces.passable(at(d, x, y - 1, z)) && Spaces.standable(at(d, x, y - 2, z));
		}
		return step(feet) && Spaces.passable(head) && Spaces.passable(at(d, x, y + 2, z));
	}

	/**
	 * Whether a door at (x, y, z) whose outside is {@code out} has someone-standable ground on both sides (as drawn so
	 * far: ask once the rooms on both sides are carved).
	 */
	public static boolean ok(Draft d, int x, int y, int z, Direction out) {
		return standable(d, x + Dirs.dx(out), y, z + Dirs.dz(out), out) && standable(d, x - Dirs.dx(out), y, z - Dirs.dz(out), out);
	}

	private static boolean standable(Draft d, int x, int y, int z, Direction ignored) {
		return standable(d, x, y, z);
	}

	/**
	 * Whether a door at column {@code col} of wall {@code s}, {@code width} leaves, standing at y, would have ground on
	 * both sides once the dressing in its way is cleared.
	 */
	public static boolean ok(Draft d, Side s, int col, int y, int width) {
		for (int c = 0; c < width; c++) {
			int i = s.index(col + c);
			if (!okIgnoringProps(d, s.x(i), y, s.z(i), s.out())) {
				return false;
			}
		}
		return true;
	}

	private static boolean okIgnoringProps(Draft d, int x, int y, int z, Direction out) {
		for (Direction side : new Direction[]{out, out.getOpposite()}) {
			int fx = x + Dirs.dx(side);
			int fz = z + Dirs.dz(side);
			if (standable(d, fx, y, fz)) {
				continue;
			}
			// standable once the props go?
			BlockState feet = at(d, fx, y, fz);
			BlockState head = at(d, fx, y + 1, fz);
			boolean feetOk = Spaces.passable(feet) || prop(d.cell(fx, y, fz));
			boolean headOk = Spaces.passable(head) || prop(d.cell(fx, y + 1, fz));
			if (!feetOk || !headOk || !(Spaces.standable(at(d, fx, y - 1, fz))
				|| Spaces.passable(at(d, fx, y - 1, fz)) && Spaces.standable(at(d, fx, y - 2, fz)))) {
				return false;
			}
		}
		return true;
	}

	// =========================================================================================== drawing

	/**
	 * A door at column {@code col} of wall {@code s} (see {@link Openings#door}), only if it can be used: the window
	 * dressing and props in its front cells are cleared first, then both sides are checked. Answers whether it went in;
	 * when it did not, nothing was drawn (the caller leaves the window, or calls {@link #placeOrWindow}).
	 */
	public static boolean place(Draft d, Side s, int col, int y, int width, Openings.DoorOpts o) {
		if (!ok(d, s, col, y, width)) {
			return false;
		}
		for (int c = 0; c < width; c++) {
			int i = s.index(col + c);
			clearFronts(d, s.x(i), y, s.z(i), s.out());
		}
		Openings.door(d, s, col, y, width, o);
		return true;
	}

	/** {@link #place}, else a plain window one high over the door's columns, its sill at y. Answers whether a door went in. */
	public static boolean placeOrWindow(Draft d, Side s, int col, int y, int width, Openings.DoorOpts o) {
		if (place(d, s, col, y, width, o)) {
			return true;
		}
		Openings.window(d, s, col, y + 1, width, 1, Openings.WindowStyle.PLAIN,
			new Openings.WindowOpts(false, false, true, true));
		return false;
	}

	/**
	 * Clears both front cells of a door at (x, y, z) (feet and head height): the props a furnisher or a window's dressing
	 * left there (pots, flower boxes, shutters, chests, barrels, benches' tops, counters, hay, bookshelves) are taken
	 * away. Steps (bottom stairs and slabs), beds, rails, ladders, job blocks and the building's body stay.
	 */
	public static int clearFronts(Draft d, int x, int y, int z, Direction out) {
		int n = 0;
		for (Direction side : new Direction[]{out, out.getOpposite()}) {
			int fx = x + Dirs.dx(side);
			int fz = z + Dirs.dz(side);
			for (int dy = 0; dy <= 1; dy++) {
				if (prop(d.cell(fx, y + dy, fz)) || shutter(d.cell(fx, y + dy, fz), side) || dy == 1 && beamEnd(d, fx, y + 1, fz)) {
					if (Boolean.getBoolean("hearth.access.debug")) {
						System.out.println("doors: cleared " + d.get(fx, y + dy, fz) + " at " + fx + "/" + (y + dy) + "/" + fz + " before the door at " + x + "/" + y + "/" + z);
					}
					d.remove(fx, y + dy, fz);
					n++;
				}
			}
		}
		return n;
	}

	/**
	 * Something set down that may be moved out of a doorway: drawn in the interior, exterior or openings stage, in the
	 * way of a person, and not a step, a bed, a rail, a ladder, a pane or a job block (a job block carries a work place).
	 */
	static boolean prop(Draft.Cell c) {
		if (c == null) {
			return false;
		}
		BlockState s = c.state();
		if (s.isAir() || Spaces.passable(s)) {
			return false;
		}
		StageKind st = c.stage();
		if (st != StageKind.INTERIOR && st != StageKind.EXTERIOR && st != StageKind.OPENINGS) {
			return false;
		}
		Block b = s.getBlock();
		if (b instanceof DoorBlock || b instanceof BedBlock || b instanceof LadderBlock || b instanceof FenceBlock || b instanceof WallBlock
			|| b instanceof FenceGateBlock || b instanceof IronBarsBlock || step(s) || jobBlock(s)) {
			return false;
		}
		// in the openings stage only a window's dressing is a prop (a sill, a shutter, a box, a pot); a pane or a wall's
		// glass is the wall itself
		return st != StageKind.OPENINGS || b instanceof net.minecraft.world.level.block.TrapDoorBlock || b instanceof StairBlock
			|| b instanceof SlabBlock || b instanceof net.minecraft.world.level.block.LeavesBlock || b instanceof net.minecraft.world.level.block.FlowerPotBlock;
	}

	/**
	 * An open trapdoor (a shutter, a lattice leaf) in a door's front cell whose leaf lies against the door's wall (an
	 * open trapdoor's leaf is on the side away from its facing): it closes the doorway though a walker passes it. A rail of
	 * trapdoors, its leaves on the far edge, stays.
	 */
	private static boolean shutter(Draft.Cell c, Direction fromDoor) {
		if (c == null || !(c.state().getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock)) {
			return false;
		}
		StageKind st = c.stage();
		if (st != StageKind.INTERIOR && st != StageKind.EXTERIOR && st != StageKind.OPENINGS) {
			return false;
		}
		BlockState s = c.state();
		return s.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)
			&& s.getValue(net.minecraft.world.level.block.TrapDoorBlock.FACING) == fromDoor;
	}

	/**
	 * A beam's end poking out of a wall head at head height before a door (an adobe roof's vigas): a log drawn with the
	 * roof, standing on nothing.
	 */
	private static boolean beamEnd(Draft d, int x, int y, int z) {
		Draft.Cell c = d.cell(x, y, z);
		return c != null && c.stage() == StageKind.ROOF && c.state().getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock
			&& Spaces.passable(at(d, x, y - 1, z));
	}

	/** A block a trade works at (it carries the work place's point of interest): never moved by a door. */
	static boolean jobBlock(BlockState s) {
		Block b = s.getBlock();
		return b == Blocks.CRAFTING_TABLE || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER || b == Blocks.GRINDSTONE
			|| b == Blocks.STONECUTTER || b == Blocks.LOOM || b == Blocks.CARTOGRAPHY_TABLE || b == Blocks.FLETCHING_TABLE || b == Blocks.SMITHING_TABLE
			|| b == Blocks.BREWING_STAND || b == Blocks.COMPOSTER || b == Blocks.LECTERN || b instanceof net.minecraft.world.level.block.AnvilBlock
			|| b instanceof net.minecraft.world.level.block.AbstractCauldronBlock || b == Blocks.BELL;
	}

	// =========================================================================================== the compile pass

	/** What {@link #finish} did, for tests and the audit. */
	public record Report(int doors, int cleared, int bricked, int lids) {
	}

	/**
	 * The doors of a finished draft made usable: every prop in a door's front cells cleared ({@link #clearFronts}); a
	 * door that still faces a wall (feet and head) or opens over a drop of four or more with nothing to stand on is
	 * bricked up (a pane over a course of the wall it stands in), unless it is an entrance; and a chest under a solid
	 * block becomes a barrel, which opens whatever stands on it (its goods kept).
	 */
	public static Report finish(Draft d) {
		List<int[]> doors = new ArrayList<>();
		List<long[]> chests = new ArrayList<>();
		d.forEach((x, y, z, c) -> {
			BlockState s = c.state();
			if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				doors.add(new int[]{x, y, z, s.getValue(DoorBlock.FACING).get2DDataValue()});
			} else if (s.getBlock() instanceof ChestBlock) {
				chests.add(new long[]{Draft.key(x, y, z)});
			}
		});
		int cleared = 0;
		int bricked = 0;
		for (int[] door : doors) {
			Direction f = Direction.from2DDataValue(door[3]);
			cleared += clearFronts(d, door[0], door[1], door[2], f);
		}
		for (int[] door : doors) {
			int x = door[0];
			int y = door[1];
			int z = door[2];
			Direction f = Direction.from2DDataValue(door[3]);
			if (!(d.get(x, y, z) instanceof BlockState s) || !(s.getBlock() instanceof DoorBlock)) {
				continue;
			}
			boolean useless = false;
			boolean outward = false;
			for (Direction side : new Direction[]{f, f.getOpposite()}) {
				int fx = x + Dirs.dx(side);
				int fz = z + Dirs.dz(side);
				if (!d.allowed(fx, y, fz) || !d.allowed(fx, y + 1, fz)) {
					// what is beyond the plot is not known here
					continue;
				}
				if (wall(d, fx, y, fz) && wall(d, fx, y + 1, fz) || dropFrom(d, fx, y, fz) >= 4) {
					useless = true;
					outward |= !wall(d, fx, y, fz);
				}
			}
			if (!useless || entrance(d, x, y, z, f)) {
				continue;
			}
			if (Boolean.getBoolean("hearth.access.debug")) {
				System.out.println("doors: bricked up the door at " + x + "/" + y + "/" + z + (outward ? " over a drop" : " into a wall"));
			}
			brickUp(d, x, y, z, f, outward);
			bricked++;
		}
		int lids = 0;
		for (long[] c : chests) {
			int x = Draft.keyX(c[0]);
			int y = Draft.keyY(c[0]);
			int z = Draft.keyZ(c[0]);
			if (d.isSolid(x, y + 1, z)) {
				Draft.Cell cell = d.cell(x, y, z);
				BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING,
					cell.state().getValue(ChestBlock.FACING));
				try (var s = d.stage(cell.stage())) {
					d.set(x, y, z, barrel, cell.tag());
				}
				lids++;
			}
		}
		return new Report(doors.size(), cleared, bricked, lids);
	}

	/** A full block in the way of a person (a wall, a post, a pier): not a prop, not passable. */
	private static boolean wall(Draft d, int x, int y, int z) {
		BlockState s = at(d, x, y, z);
		return !Spaces.passable(s) && !step(s) && !(s.getBlock() instanceof DoorBlock)
			&& s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** How far someone stepping into (x, y, z) falls: passable cells under the feet until something stops them (12 at most). */
	static int dropFrom(Draft d, int x, int y, int z) {
		if (!Spaces.passable(at(d, x, y, z)) || !Spaces.passable(at(d, x, y + 1, z))) {
			return 0;
		}
		int drop = 0;
		for (int dy = -1; dy >= -12; dy--) {
			BlockState below = at(d, x, y + dy, z);
			if (!below.getFluidState().isEmpty() || !Spaces.passable(below) || below.getBlock() instanceof LadderBlock) {
				break;
			}
			drop++;
		}
		return drop;
	}

	private static boolean entrance(Draft d, int x, int y, int z, Direction f) {
		for (int[] e : d.entrances()) {
			if (Math.abs(e[1] - y) <= 2 && Math.abs(e[0] - x) + Math.abs(e[2] - z) <= 1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A door that leads nowhere becomes wall: a course of the wall it stands in (read off its neighbours along the wall)
	 * and, where it looked out over a drop, a pane above it. Its door points of interest go with it.
	 */
	private static void brickUp(Draft d, int x, int y, int z, Direction f, boolean outward) {
		Direction along = f.getClockWise();
		BlockState course = null;
		for (int k : new int[]{1, -1}) {
			BlockState n = d.get(x + Dirs.dx(along) * k, y, z + Dirs.dz(along) * k);
			if (n != null && n.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				course = n;
				break;
			}
		}
		Paint wall = course != null ? Paint.of(course) : d.mat(Role.WALL).full();
		Draft.Cell lower = d.cell(x, y, z);
		StageKind stage = lower != null ? lower.stage() : StageKind.OPENINGS;
		try (var s = d.stage(stage == StageKind.OPENINGS ? StageKind.WALLS : stage)) {
			d.set(x, y, z, wall);
			d.set(x, y + 1, z, outward ? d.mat(Role.GLASS).pane() : wall);
		}
		int ox = x + Dirs.dx(f);
		int oz = z + Dirs.dz(f);
		int ix = x - Dirs.dx(f);
		int iz = z - Dirs.dz(f);
		d.retainPois(p -> p.type() != PoiType.DOOR || Math.abs(p.y() - y) > 1 || !(p.x() == ox && p.z() == oz || p.x() == ix && p.z() == iz
			|| p.x() == x && p.z() == z));
	}
}
