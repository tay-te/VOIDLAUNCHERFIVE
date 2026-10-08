package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.LightRules;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HayBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

/**
 * The guard against falls, walked the way folk walk: the finished drawing is read with the natural ground round it, every
 * cell a body can reach from the street and the doors is found ({@link Spaces}, the rules the structure audit walks by),
 * and wherever one of them stands beside a drop of four or more, the drop is guarded in the manner of what the body
 * stands on:
 *
 * <ul>
 * <li>over a floor, a terrace, a landing or a flight (what is meant to be walked) a rail on the open side, footed on a
 * post down to the ground where the column under it is clear (a stair's handrail, a well's balustrade);</li>
 * <li>on a wall's head, a coping or a roof (what nobody should walk) a rail on it, so it is no longer stood on;</li>
 * <li>on furniture or a fixture at the plot's edge (a chest, a bench, a shutter, a ledge) the thing is taken away, and a
 * light's lattice becomes a balustrade.</li>
 * </ul>
 *
 * <p>Repeated until nothing reached stands over a fall (a few rounds: a rail can open a new step).
 */
public final class Falls {
	private Falls() {
	}

	/** The drop that needs a guard (the audit's: a fall of four or more, no water below). */
	private static final int DROP = 4;

	/** Guards every fall a body can walk to (see the class comment). Call it last, when the drawing is finished. */
	public static void guard(Draft d) {
		Paint rail = Halls.rail(d, (dev.voidmc.hearth.build.draft.Mat) null);
		for (int round = 0; round < 4; round++) {
			Walk wk = walk(d);
			// a shutter or a bracket stood on is a ladder up the wall: taken away wherever anyone stands on one
			if (shutters(d, wk)) {
				wk = walk(d);
			}
			List<int[]> edges = edges(d, wk);
			if (edges.isEmpty()) {
				return;
			}
			boolean changed = false;
			for (int[] e : edges) {
				changed |= fix(d, wk, e, rail);
			}
			if (!changed) {
				return;
			}
		}
	}

	/** The walk over the drawing: its grid and who reaches where. */
	record Walk(Grid g, Spaces sp) {
	}

	static Walk walk(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int[] yr = {0, 0};
		d.forEach((x, y, z, c) -> {
			yr[0] = Math.min(yr[0], y);
			yr[1] = Math.max(yr[1], y);
		});
		Site site = d.site();
		BlockState air = Blocks.AIR.defaultBlockState();
		Grid g = new Grid(new Grid.Source() {
			@Override
			public BlockState at(int x, int y, int z) {
				Draft.Cell c = d.cell(x, y, z);
				return c != null ? c.state() : natural(x, y, z);
			}

			@Override
			public BlockState drawn(int x, int y, int z) {
				Draft.Cell c = d.cell(x, y, z);
				return c != null ? c.state() : null;
			}

			@Override
			public boolean paint(Grid.Painter sink) {
				d.forEach((x, y, z, c) -> sink.put(x, y, z, c.state()));
				return true;
			}

			@Override
			public BlockState natural(int x, int y, int z) {
				if (y < site.ground(x, z)) {
					return Blocks.STONE.defaultBlockState();
				}
				int wy = site.water(x, z);
				if (wy != Site.NONE && y < wy) {
					return Blocks.WATER.defaultBlockState();
				}
				return air;
			}

			@Override
			public boolean placed(int x, int y, int z) {
				return d.cell(x, y, z) != null;
			}

			@Override
			public boolean inPlot(int x, int z) {
				return x >= 0 && z >= 0 && x < w && z < dp;
			}

			@Override
			public Direction plusZ() {
				return Dirs.BACK;
			}
		}, -LightRules.MARGIN, yr[0] - LightRules.BELOW, -LightRules.MARGIN, w - 1 + LightRules.MARGIN, yr[1] + LightRules.ABOVE, dp - 1 + LightRules.MARGIN);
		List<BlockPos> starts = new ArrayList<>();
		for (int[] e : d.entrances()) {
			starts.add(new BlockPos(e[0], e[1], e[2]));
		}
		for (int x = 0; x < w; x++) {
			starts.add(new BlockPos(x, Math.max(site.ground(x, -1), 0), -1));
		}
		return new Walk(g, new Spaces(g, starts));
	}

	/** Takes away every shutter leaf or closed bracket hung over air that someone reaches the top of; whether any went. */
	private static boolean shutters(Draft d, Walk wk) {
		List<int[]> hit = new ArrayList<>();
		d.forEach((x, y, z, c) -> {
			BlockState s = c.state();
			if (s.getBlock() instanceof TrapDoorBlock && (c.stage() == StageKind.OPENINGS || c.stage() == StageKind.EXTERIOR)
				&& (s.getValue(TrapDoorBlock.OPEN) || s.getValue(TrapDoorBlock.HALF) == Half.TOP) && wk.sp().reached(x, y + 1, z)
				&& wk.g().at(x, y - 1, z).isAir()) {
				hit.add(new int[]{x, y, z});
			}
		});
		for (int[] h : hit) {
			BlockState s = d.get(h[0], h[1], h[2]);
			if (s != null && s.getBlock() instanceof TrapDoorBlock) {
				clear(d, h[0], h[1], h[2], s);
			}
		}
		return !hit.isEmpty();
	}

	/** Every reached cell of the plot beside a fall: {x, y, z, nx, nz} (feet, and the open column beside them). */
	private static List<int[]> edges(Draft d, Walk wk) {
		Grid g = wk.g();
		Spaces sp = wk.sp();
		List<int[]> out = new ArrayList<>();
		for (int y = g.y0 + 1; y < g.y0 + g.sy - 1; y++) {
			if (y >= d.maxHeight()) {
				break;
			}
			for (int z = 0; z < d.depth(); z++) {
				for (int x = 0; x < d.frontage(); x++) {
					if (!sp.reached(x, y, z) || sp.wet(x, y, z)) {
						continue;
					}
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						if (!Spaces.passable(g.at(nx, y, nz)) || !Spaces.passable(g.at(nx, y + 1, nz))) {
							continue;
						}
						int drop = 0;
						boolean water = false;
						for (int dy = -1; dy >= -12 && g.contains(nx, y + dy, nz); dy--) {
							BlockState below = g.at(nx, y + dy, nz);
							if (!below.getFluidState().isEmpty()) {
								water = true;
								break;
							}
							if (!Spaces.passable(below) || below.getBlock() instanceof LadderBlock) {
								break;
							}
							drop++;
						}
						if (drop >= DROP && !water) {
							out.add(new int[]{x, y, z, nx, nz});
							break;
						}
					}
				}
			}
		}
		return out;
	}

	/** Guards one edge; whether the drawing changed. */
	private static boolean fix(Draft d, Walk wk, int[] e, Paint rail) {
		int x = e[0];
		int y = e[1];
		int z = e[2];
		int nx = e[3];
		int nz = e[4];
		Draft.Cell under = d.cell(x, y - 1, z);
		BlockState u = under == null ? null : under.state();
		boolean inPlot = nx >= 0 && nz >= 0 && nx < d.frontage() && nz < d.depth();
		// a light's lattice (a closed trapdoor in a wall's line) stood in: a balustrade in its foot
		if (u != null && u.getBlock() instanceof TrapDoorBlock && !u.getValue(TrapDoorBlock.OPEN) && u.getValue(TrapDoorBlock.HALF) == Half.BOTTOM
			&& under.stage() == StageKind.OPENINGS) {
			d.inStage(StageKind.OPENINGS, () -> d.set(x, y - 1, z, rail));
			return true;
		}
		// furniture or a fixture stood on: taken away where the fall is off the plot, railed where it is the drawing's own
		if (u != null && (clutter(u) || under.stage() == StageKind.OPENINGS && (u.getBlock() instanceof StairBlock || u.getBlock() instanceof SlabBlock))) {
			if (!inPlot) {
				clear(d, x, y - 1, z, u);
				return true;
			}
			return railAt(d, wk, nx, y, nz, rail);
		}
		// a wall's head, a coping or a roof: a rail on it, so nobody stands there (never before a door)
		if (under != null && (under.stage() == StageKind.ROOF || under.stage() == StageKind.WALLS || under.stage() == StageKind.FRAME)
			&& !nearDoor(d, x, y, z)) {
			if (d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && d.allowed(x, y, z)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, rail));
				return true;
			}
			// (a light screened with open leaves over a sill: a balustrade in its foot instead)
			BlockState feet = d.get(x, y, z);
			if (feet != null && feet.getBlock() instanceof TrapDoorBlock) {
				d.inStage(StageKind.OPENINGS, () -> d.set(x, y, z, rail));
				return true;
			}
			return false;
		}
		// a floor, a terrace, a landing or a flight: a rail on the open side, inside the plot
		if (inPlot) {
			return railAt(d, wk, nx, y, nz, rail);
		}
		// at the plot's edge, over the land beyond: a rail on the cell itself where it is no way in (a corner of the yard)
		if (!nearDoor(d, x, y, z) && !(u != null && (u.getBlock() instanceof StairBlock || u.getBlock() instanceof SlabBlock)) && !nearEntrance(d, x, z)
			&& d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && d.allowed(x, y, z)) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, rail));
			return true;
		}
		return false;
	}

	/** A rail at (x, y, z) over the drop, footed on a post down to the ground where the column under it is clear. */
	private static boolean railAt(Draft d, Walk wk, int x, int y, int z, Paint rail) {
		if (!d.isEmpty(x, y, z) || !d.allowed(x, y, z)) {
			return false;
		}
		Grid g = wk.g();
		// the post: down through clear air to something solid (natural ground or a built floor), not through a room's
		// flight or a hanging lamp
		int foot = Integer.MIN_VALUE;
		for (int yy = y - 1; yy >= y - 12 && g.contains(x, yy, z); yy--) {
			BlockState s = g.at(x, yy, z);
			if (s.isAir()) {
				continue;
			}
			if (s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				foot = yy + 1;
			}
			break;
		}
		// (only outdoors: a post through a stairwell would stand in the flight's way)
		boolean outdoors = foot != Integer.MIN_VALUE && !roofedAbove(d, x, y, z);
		int f = foot;
		d.inStage(StageKind.EXTERIOR, () -> {
			d.set(x, y, z, rail);
			if (outdoors) {
				for (int yy = f; yy < y; yy++) {
					if (d.isEmpty(x, yy, z)) {
						d.set(x, yy, z, rail);
					}
				}
			}
		});
		return true;
	}

	/** Something drawn over the cell within a few rows (a roof or a floor): indoors. */
	private static boolean roofedAbove(Draft d, int x, int y, int z) {
		for (int yy = y + 1; yy <= y + 6; yy++) {
			if (!d.isEmpty(x, yy, z)) {
				return true;
			}
		}
		return false;
	}

	/** Furniture and fixtures a body might use as a step: chests, barrels, beds, jars, bales, shutters and ledges. */
	private static boolean clutter(BlockState s) {
		Block b = s.getBlock();
		return b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ComposterBlock || b instanceof DecoratedPotBlock || b instanceof HayBlock
			|| b instanceof BedBlock || b instanceof TrapDoorBlock;
	}

	/** Takes a step-giving fixture away: a bed whole, a shutter with its other leaf. */
	private static void clear(Draft d, int x, int y, int z, BlockState s) {
		BlockState airS = Blocks.AIR.defaultBlockState();
		// (a bracket a lantern hangs from becomes a stub of the railing, which holds the lantern and is no step)
		BlockState hung = d.get(x, y - 1, z);
		if (hung != null && Spaces.hangs(hung)) {
			d.set(x, y, z, Halls.rail(d, (dev.voidmc.hearth.build.draft.Mat) null));
			return;
		}
		if (s.getBlock() instanceof BedBlock) {
			Direction f = s.getValue(BedBlock.FACING);
			Direction toOther = s.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT ? f : f.getOpposite();
			d.set(x + Dirs.dx(toOther), y, z + Dirs.dz(toOther), airS);
		}
		if (s.getBlock() instanceof TrapDoorBlock && s.getValue(TrapDoorBlock.OPEN)) {
			for (int dy : new int[]{-1, 1}) {
				BlockState o = d.get(x, y + dy, z);
				if (o != null && o.getBlock() instanceof TrapDoorBlock && o.getValue(TrapDoorBlock.OPEN)) {
					d.set(x, y + dy, z, airS);
				}
			}
		}
		d.set(x, y, z, airS);
	}

	/** A door within a cell of (x, z) at the feet's level or the one under it. */
	private static boolean nearDoor(Draft d, int x, int y, int z) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 0; dy++) {
					@Nullable BlockState s = d.get(x + dx, y + dy, z + dz);
					if (s != null && s.getBlock() instanceof DoorBlock) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A registered way in within a column of (x, z). */
	private static boolean nearEntrance(Draft d, int x, int z) {
		for (int[] en : d.entrances()) {
			if (Math.abs(en[0] - x) <= 1 && Math.abs(en[2] - z) <= 1) {
				return true;
			}
		}
		return false;
	}
}
