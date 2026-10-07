package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.LightRules;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Guards on walkable edges (the audit's rule S4): wherever folk can stand beside a drop of {@value #DROP} or more, a
 * rail of the culture's railing (or, on a masonry deck, a parapet course) stops them stepping off, with gaps only where a
 * ladder, a stair or a door serves the edge.
 *
 * <p>{@link #edge} guards one edge by hand, {@link #deck} the open edges of a deck or a gallery. {@link #finish} runs on
 * every blueprint when it compiles ({@code refine.Access}): it walks the draft as the light check does and sets a guard
 * on the edge cell of every flat floor that still opens onto a drop, never on a roof's slope, and takes nothing back
 * if a guard would cut a way through (it then leaves that building's edges to its archetype).
 */
public final class Guards {
	private Guards() {
	}

	/** A fall this deep or deeper needs a guard (the audit's fall edge: four or more). */
	public static final int DROP = 4;

	/** The culture's rail: its railing's fence, else a wooden fence of its timber. */
	public static Paint rail(Draft d) {
		Mat rail = d.mat(Role.RAILING);
		return rail.has(Form.FENCE) ? rail.fence() : Hall.woodFence(d).fence();
	}

	/**
	 * A guard for an edge on a deck of this floor block: on a masonry roof deck a balustrade of the wall's stone (its
	 * wall block, where the stone has one), else the rail. Never a full block: a guard nobody can stand on.
	 */
	public static Paint guardOn(Draft d, BlockState floor, StageKind floorStage) {
		if (floorStage == StageKind.ROOF && Hall.masonry(d) && floor.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
			Mat wall = d.mat(Role.WALL);
			if (wall.has(Form.WALL)) {
				return wall.wall();
			}
		}
		return rail(d);
	}

	/**
	 * Guards the edge of the standing cell (x, y, z) toward {@code toward}: a rail in the cell beyond at y if that cell
	 * is open and falls {@link #DROP} or more. Answers whether one went in.
	 */
	public static boolean edge(Draft d, int x, int y, int z, Direction toward) {
		int nx = x + Dirs.dx(toward);
		int nz = z + Dirs.dz(toward);
		if (!d.allowed(nx, y, nz) || !d.isEmpty(nx, y, nz) || Doors.dropFrom(d, nx, y, nz) < DROP) {
			return false;
		}
		d.inStage(StageKind.EXTERIOR, () -> d.set(nx, y, nz, rail(d)));
		return true;
	}

	/**
	 * Rails round the open edges of a deck, a gallery or a balcony standing at y over {@code [x0..x1] × [z0..z1]}: on
	 * every edge cell beyond which the floor drops {@link #DROP} or more, the rail goes in the cell beyond (inside the
	 * plot), except at the {@code gaps} (x, z pairs: a ladder's head, a stair's top, a door's front). Returns how many
	 * rails went in.
	 */
	public static int deck(Draft d, int x0, int z0, int x1, int z1, int y, int... gaps) {
		int n = 0;
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (nx >= Math.min(x0, x1) && nx <= Math.max(x0, x1) && nz >= Math.min(z0, z1) && nz <= Math.max(z0, z1)) {
						continue;
					}
					boolean gap = false;
					for (int i = 0; i + 1 < gaps.length; i += 2) {
						gap |= gaps[i] == nx && gaps[i + 1] == nz || gaps[i] == x && gaps[i + 1] == z;
					}
					if (!gap && edge(d, x, y, z, dir)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	// =========================================================================================== the compile pass

	/** Whether the pass guards this blueprint: buildings and grounds (not walls, docks, roads, workings, fields). */
	static boolean applies(Draft d) {
		String p = d.spec().archetype().getPath();
		LightRules.Family f = LightRules.family(d.spec().archetype());
		return (f == LightRules.Family.BUILDING || f == LightRules.Family.GROUNDS) && !p.startsWith("defense/") && !d.param("guards", "auto").equals("none");
	}

	/**
	 * Guards every flat floor's edge a person reaches and could step off into a drop of {@link #DROP} or more (as the
	 * audit's fall edge): the guard goes on the edge cell itself, a rail on boards, a parapet course on a masonry deck,
	 * so nothing hangs out over the drop. Not on a roof's slope or ridge, not where a ladder, a stair, a hatch or a door
	 * arrives, not on an entrance or a point of interest, and only where the floor goes on inward (a one-wide walk
	 * keeps its way). If the guards would leave any cell unreached that was reached, none is kept. Returns how many
	 * went in.
	 */
	public static int finish(Draft d) {
		if (!applies(d)) {
			return 0;
		}
		Spaces sp = Spaces.of(d);
		Grid g = sp.grid();
		LongOpenHashSet keep = new LongOpenHashSet();
		for (int[] e : d.entrances()) {
			for (int dy = -2; dy <= 1; dy++) {
				keep.add(BlockPos.asLong(e[0], e[1] + dy, e[2]));
			}
		}
		for (Draft.LocalPoi p : d.pois()) {
			keep.add(BlockPos.asLong(p.x(), p.y(), p.z()));
		}
		LongArrayList cells = new LongArrayList();
		LongOpenHashSet taken = new LongOpenHashSet();
		java.util.List<Paint> paints = new java.util.ArrayList<>();
		LongArrayList reachedBefore = new LongArrayList();
		for (int y = g.y0 + 1; y < g.y0 + g.sy - 1; y++) {
			for (int z = 0; z < d.depth(); z++) {
				for (int x = 0; x < d.frontage(); x++) {
					if (!sp.reached(x, y, z)) {
						continue;
					}
					reachedBefore.add(BlockPos.asLong(x, y, z));
					if (sp.wet(x, y, z) || !d.allowed(x, y, z) || !d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z) || keep.contains(BlockPos.asLong(x, y, z))) {
						continue;
					}
					Draft.Cell floor = d.cell(x, y - 1, z);
					if (floor == null || !flatFloor(floor.state()) || roofSlope(d, x, y - 1, z) || arrival(d, g, x, y, z)) {
						continue;
					}
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						if (!fall(g, nx, y, nz)) {
							continue;
						}
						// the floor goes on inward: the guard takes the edge and leaves the walk behind it
						int ix = x - Dirs.dx(dir);
						int iz = z - Dirs.dz(dir);
						if (!sp.reached(ix, y, iz) || !d.isEmpty(ix, y, iz) || !through(sp, taken, x, y, z)) {
							break;
						}
						cells.add(BlockPos.asLong(x, y, z));
						taken.add(BlockPos.asLong(x, y, z));
						paints.add(guardOn(d, floor.state(), floor.stage()));
						break;
					}
				}
			}
		}
		if (cells.isEmpty()) {
			return 0;
		}
		java.util.List<Draft.Cell> was = new java.util.ArrayList<>();
		for (int i = 0; i < cells.size(); i++) {
			long k = cells.getLong(i);
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			was.add(d.cell(x, y, z));
			StageKind stage = sp.roofed(x, y, z) ? StageKind.INTERIOR : StageKind.EXTERIOR;
			Paint p = paints.get(i);
			d.inStage(stage, () -> d.set(x, y, z, p));
		}
		// a guard must never cut a way: every cell reached before (but the guards' own) is reached after; the guards
		// beside a cell cut off are taken back (and the walk read again) until nothing is cut, three times at most
		boolean debug = Boolean.getBoolean("hearth.guards.debug");
		LongOpenHashSet kept = new LongOpenHashSet(cells);
		for (int round = 0; round < 4 && !kept.isEmpty(); round++) {
			Spaces after = Spaces.of(d);
			LongArrayList lost = new LongArrayList();
			for (int i = 0; i < reachedBefore.size(); i++) {
				long k = reachedBefore.getLong(i);
				if (!kept.contains(k) && !after.reached(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k))) {
					lost.add(k);
				}
			}
			if (lost.isEmpty()) {
				break;
			}
			LongArrayList back = new LongArrayList();
			for (long g0 : kept) {
				boolean near = round == 3;
				for (int i = 0; i < lost.size() && !near; i++) {
					long k = lost.getLong(i);
					near = Math.abs(BlockPos.getX(k) - BlockPos.getX(g0)) <= 2 && Math.abs(BlockPos.getZ(k) - BlockPos.getZ(g0)) <= 2
						&& Math.abs(BlockPos.getY(k) - BlockPos.getY(g0)) <= 2;
				}
				if (near) {
					back.add(g0);
				}
			}
			if (back.isEmpty()) {
				back.addAll(kept);
			}
			for (long k : back) {
				int i = cells.indexOf(k);
				Draft.Cell c = was.get(i);
				if (c == null) {
					d.remove(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
				} else {
					try (var st = d.stage(c.stage())) {
						d.set(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k), c.state());
					}
				}
				kept.remove(k);
			}
		}
		if (debug) {
			StringBuilder sb = new StringBuilder();
			for (long k : cells) {
				sb.append(' ').append(BlockPos.getX(k)).append('/').append(BlockPos.getY(k)).append('/').append(BlockPos.getZ(k)).append(kept.contains(k) ? "" : "x");
			}
			System.out.println("guards " + d.spec().archetype() + " " + d.spec().style() + ": " + kept.size() + " of " + cells.size() + sb);
		}
		return kept.size();
	}

	/**
	 * Whether the walk still passes round a cell once it is taken: of its eight neighbours at its level, those walked to
	 * (and not taken already) that touch it side on are joined to one another round the ring of eight. A corner of a
	 * walk round a spire, or a cell between two lanes of a gallery, is a way through and is never taken.
	 */
	private static boolean through(Spaces sp, LongOpenHashSet taken, int x, int y, int z) {
		int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
		boolean[] on = new boolean[8];
		int sides = 0;
		for (int i = 0; i < 8; i++) {
			int nx = x + ring[i][0];
			int nz = z + ring[i][1];
			on[i] = sp.reached(nx, y, nz) && !taken.contains(BlockPos.asLong(nx, y, nz));
			if (on[i] && (i & 1) == 0) {
				sides++;
			}
		}
		if (sides <= 1) {
			return sides == 1;
		}
		// walk the ring from a side neighbour that is on: every side neighbour must be met before a gap
		int start = -1;
		for (int i = 0; i < 8; i += 2) {
			if (on[i] && !on[(i + 7) % 8]) {
				start = i;
				break;
			}
		}
		if (start < 0) {
			// the whole ring is on
			return true;
		}
		int met = 0;
		for (int k = 0; k < 8; k++) {
			int i = (start + k) % 8;
			if (!on[i]) {
				break;
			}
			if ((i & 1) == 0) {
				met++;
			}
		}
		return met == sides;
	}

	/** A floor one stands level on: a full block, a top slab, an upside-down stair (not a step, a slope or a trapdoor). */
	private static boolean flatFloor(BlockState s) {
		Block b = s.getBlock();
		if (b instanceof StairBlock) {
			return s.getValue(StairBlock.HALF) == Half.TOP;
		}
		if (b instanceof SlabBlock) {
			return s.getValue(SlabBlock.TYPE) != SlabType.BOTTOM;
		}
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** A roof's surface: the floor is roof work with a sloping course of the roof beside it (a ridge, an eave). */
	private static boolean roofSlope(Draft d, int x, int y, int z) {
		Draft.Cell c = d.cell(x, y, z);
		if (c == null || c.stage() != StageKind.ROOF) {
			return false;
		}
		for (Direction dir : Dirs.HORIZONTAL) {
			for (int dy = -1; dy <= 0; dy++) {
				Draft.Cell n = d.cell(x + Dirs.dx(dir), y + dy, z + Dirs.dz(dir));
				if (n != null && n.stage() == StageKind.ROOF && n.state().getBlock() instanceof StairBlock
					&& n.state().getValue(StairBlock.HALF) == Half.BOTTOM) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Someone arrives on this cell from a ladder, a hatch, a stair or a door: a ladder in it or under it or beside it,
	 * a trapdoor under it, a step climbing into it, a door beside it.
	 */
	private static boolean arrival(Draft d, Grid g, int x, int y, int z) {
		if (g.at(x, y - 1, z).getBlock() instanceof LadderBlock || g.at(x, y, z).getBlock() instanceof LadderBlock
			|| g.at(x, y - 1, z).getBlock() instanceof TrapDoorBlock) {
			return true;
		}
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			for (int dy = -1; dy <= 1; dy++) {
				BlockState n = g.at(nx, y + dy, nz);
				if (n.getBlock() instanceof LadderBlock || n.getBlock() instanceof DoorBlock && dy >= 0) {
					return true;
				}
				// a stair (or a bottom slab) one down beside the cell: a flight or a step comes up onto it
				if (dy == -1 && (n.getBlock() instanceof StairBlock && n.getValue(StairBlock.HALF) == Half.BOTTOM
					|| n.getBlock() instanceof SlabBlock && n.getValue(SlabBlock.TYPE) == SlabType.BOTTOM)) {
					return true;
				}
				if (dy == 0 && n.getBlock() instanceof StairBlock && n.getValue(StairBlock.HALF) == Half.BOTTOM) {
					return true;
				}
				// a slab beside at the feet or the floor: a spiral's half steps, a stepped landing
				if (dy <= 0 && n.getBlock() instanceof SlabBlock) {
					return true;
				}
			}
		}
		return false;
	}

	/** Stepping into (x, y, z) falls {@link #DROP} or more onto no water (the audit's fall edge). */
	private static boolean fall(Grid g, int x, int y, int z) {
		if (!Spaces.passable(g.at(x, y, z)) || !Spaces.passable(g.at(x, y + 1, z))) {
			return false;
		}
		int drop = 0;
		for (int dy = -1; dy >= -12 && g.contains(x, y + dy, z); dy--) {
			BlockState below = g.at(x, y + dy, z);
			if (!below.getFluidState().isEmpty()) {
				return false;
			}
			if (!Spaces.passable(below) || below.getBlock() instanceof LadderBlock) {
				break;
			}
			drop++;
		}
		return drop >= DROP;
	}
}
