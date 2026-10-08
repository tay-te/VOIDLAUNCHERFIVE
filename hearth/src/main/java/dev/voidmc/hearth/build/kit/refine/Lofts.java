package dev.voidmc.hearth.build.kit.refine;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.Spaces;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The way up into a loft a stair cannot reach: a ladder against a wall, through a hatch cut in the loft's floor. A loft
 * folk can climb to is a room (lit, furnished, never a dark void under the roof).
 */
public final class Lofts {
	private Lofts() {
	}

	/**
	 * A ladder in the cell (x, z) from standing level {@code yFrom} up through the floor of the loft standing at
	 * {@code yTo}, against the wall toward {@code toWall}: every cell of the climb free, the wall solid behind each rung,
	 * two free rows over the hatch (under the roof, when one is given). Answers whether it went in.
	 */
	public static boolean ladder(Draft d, int x, int z, int yFrom, int yTo, Direction toWall, Roofs.Roof roof) {
		int wx = x + Dirs.dx(toWall);
		int wz = z + Dirs.dz(toWall);
		for (int y = yFrom; y < yTo; y++) {
			if (!d.isSolid(wx, y, wz)) {
				return false;
			}
			if (y < yTo - 1 && !d.isEmpty(x, y, z)) {
				return false;
			}
		}
		if (!d.isEmpty(x, yTo, z) || !d.isEmpty(x, yTo + 1, z) || roof != null && roof.covers(x, z) && roof.bottomY(x, z) < yTo + 2) {
			return false;
		}
		// a landing: a floor cell beside the hatch (not behind the ladder) with two rows of air over it, to step off onto
		if (landing(d, x, z, yTo, toWall, roof) == null) {
			return false;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int y = yFrom; y < yTo; y++) {
				d.set(x, y, z, States.facing(Blocks.LADDER, toWall.getOpposite()));
			}
		}
		return true;
	}

	/**
	 * The cell a climber steps off onto at the head of a ladder in (x, z) whose loft floor stands at {@code yTo}: beside
	 * the hatch, not behind the ladder (the wall), boarded, with two rows of air over it (under the roof, when one is
	 * given). Returns {x, z}, or null where there is none.
	 */
	public static int @org.jspecify.annotations.Nullable [] landing(Draft d, int x, int z, int yTo, Direction toWall, Roofs.Roof roof) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (dir == toWall) {
				continue;
			}
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			if (d.isSolid(nx, yTo - 1, nz) && d.isEmpty(nx, yTo, nz) && d.isEmpty(nx, yTo + 1, nz)
				&& (roof == null || !roof.covers(nx, nz) || roof.bottomY(nx, nz) >= yTo + 2)) {
				return new int[]{nx, nz};
			}
		}
		return null;
	}

	/** What {@link #finish} did: lofts given a ladder, floor cells opened to the room below. */
	public record Report(int ladders, int opened) {
	}

	/**
	 * The lofts nobody reaches, made right once the archetype has drawn (the audit's rule S2: a floor folk cannot get to
	 * is a dark void that reads as a storey from outside). Every level of roofed, walled floor of four cells or more the
	 * walk does not reach is given a ladder from the reached floor under it, against a wall, through a hatch, with a cell
	 * to step off onto ({@link #ladder}). A loft too thin to be a room (a rim of boards round an opened floor, a strip
	 * under the eaves), or one no ladder fits, with nothing in it, is opened to the room below instead: its boards are
	 * taken up wherever nothing stands on them and open air is under them, so the rafters show. A furnished loft no
	 * ladder fits is left for its archetype.
	 */
	public static Report finish(Draft d) {
		int ladders = 0;
		int opened = 0;
		Set<Long> tried = new HashSet<>();
		for (int round = 0; round < 12; round++) {
			Spaces sp = Spaces.of(d);
			boolean changed = false;
			for (List<int[]> loft : unreached(d, sp)) {
				int[] first = loft.get(0);
				if (!tried.add(Draft.key(first[0], first[1], first[2]))) {
					continue;
				}
				// a loft that is a room (two by two or more) gets a ladder; one too thin to be a room, or one no ladder
				// fits, is opened to the room under it if nothing in it is used (the rafters show)
				if (square(loft, 2) && climb(d, sp, loft)) {
					debug("laddered " + loft.size(), first);
					ladders++;
					changed = true;
					// the walk has changed: read it again, and try the lofts over this one anew
					tried.clear();
					break;
				}
				if (!furnished(d, loft)) {
					int n = open(d, loft);
					if (n > 0) {
						debug("opened " + n + " of " + loft.size(), first);
						opened += n;
						changed = true;
					}
				}
			}
			if (!changed) {
				break;
			}
		}
		if (Boolean.getBoolean("hearth.access.debug")) {
			for (List<int[]> loft : unreached(d, Spaces.of(d))) {
				int[] c = loft.get(0);
				System.out.println("lofts: left " + loft.size() + " cells at " + c[0] + "/" + c[1] + "/" + c[2] + (square(loft, 2) ? "" : " (thin)")
					+ (furnished(d, loft) ? " (furnished)" : ""));
			}
			System.out.println("lofts: " + ladders + " ladders, " + opened + " cells opened");
		}
		return new Report(ladders, opened);
	}

	/** The unreached levels: 4-connected roofed, walled, walkable cells on one level in the plot, four or more. */
	private static List<List<int[]>> unreached(Draft d, Spaces sp) {
		Grid g = sp.grid();
		Set<Long> seen = new HashSet<>();
		List<List<int[]>> out = new ArrayList<>();
		for (int y = g.y0 + 1; y < g.y0 + g.sy - 1; y++) {
			for (int z = 0; z < d.depth(); z++) {
				for (int x = 0; x < d.frontage(); x++) {
					if (!loftCell(sp, x, y, z) || !seen.add(Draft.key(x, y, z))) {
						continue;
					}
					List<int[]> comp = new ArrayList<>();
					ArrayDeque<int[]> q = new ArrayDeque<>();
					q.add(new int[]{x, y, z});
					while (!q.isEmpty()) {
						int[] c = q.poll();
						comp.add(c);
						for (Direction dir : Dirs.HORIZONTAL) {
							int nx = c[0] + Dirs.dx(dir);
							int nz = c[2] + Dirs.dz(dir);
							if (nx >= 0 && nz >= 0 && nx < d.frontage() && nz < d.depth() && loftCell(sp, nx, y, nz) && seen.add(Draft.key(nx, y, nz))) {
								q.add(new int[]{nx, y, nz});
							}
						}
					}
					if (comp.size() >= 4) {
						out.add(comp);
					}
				}
			}
		}
		return out;
	}

	private static boolean loftCell(Spaces sp, int x, int y, int z) {
		return sp.walkable(x, y, z) && !sp.reached(x, y, z) && !sp.wet(x, y, z) && sp.enclosed(x, y, z) && !sp.doorway(x, y, z);
	}

	/** Whether the cells hold a square of n by n. */
	private static boolean square(List<int[]> cells, int n) {
		Set<Long> set = new HashSet<>();
		for (int[] c : cells) {
			set.add(Draft.key(c[0], c[1], c[2]));
		}
		for (int[] c : cells) {
			boolean whole = true;
			for (int dx = 0; dx < n && whole; dx++) {
				for (int dz = 0; dz < n && whole; dz++) {
					whole = set.contains(Draft.key(c[0] + dx, c[1], c[2] + dz));
				}
			}
			if (whole) {
				return true;
			}
		}
		return false;
	}

	/** Something meant to be used stands in or beside the loft: a point of interest, furniture, a bed, a door. */
	private static boolean furnished(Draft d, List<int[]> loft) {
		Set<Long> cells = new HashSet<>();
		for (int[] c : loft) {
			cells.add(Draft.key(c[0], c[1], c[2]));
		}
		for (Draft.LocalPoi p : d.pois()) {
			for (int dy = -1; dy <= 1; dy++) {
				if (cells.contains(Draft.key(p.x(), p.y() + dy, p.z()))) {
					return true;
				}
			}
		}
		for (int[] c : loft) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					Draft.Cell n = d.cell(c[0] + dx, c[1], c[2] + dz);
					if (n != null && !n.state().isAir() && !(n.state().getBlock() instanceof LadderBlock)
						&& (n.stage() == StageKind.INTERIOR || n.state().getBlock() instanceof DoorBlock)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * A ladder up into the loft from the reached floor under it: in a cell of the loft against a wall, whose board is
	 * the hatch, over open cells down to a floor the walk reaches (three to eight rows down), its foot beside no door,
	 * bed, chest, stair or workstation. Answers whether one went in.
	 */
	private static boolean climb(Draft d, Spaces sp, List<int[]> loft) {
		for (int[] c : loft) {
			int x = c[0];
			int y = c[1];
			int z = c[2];
			Draft.Cell board = d.cell(x, y - 1, z);
			if (board == null || board.stage() != StageKind.FLOORS || !d.isSolid(x, y - 1, z)) {
				continue;
			}
			int yFrom = y - 2;
			while (yFrom > y - 9 && d.isEmpty(x, yFrom - 1, z)) {
				yFrom--;
			}
			if (y - yFrom < 3 || !d.isEmpty(x, yFrom, z) || !sp.reached(x, yFrom, z) || busy(d, x, yFrom, z)) {
				continue;
			}
			for (Direction toWall : Dirs.HORIZONTAL) {
				if (ladder(d, x, z, yFrom, y, toWall, null)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A ladder's foot here would stand before a door, a bed, a container, a stair or a workstation. */
	private static boolean busy(Draft d, int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockState n = d.get(x + Dirs.dx(dir), y + dy, z + Dirs.dz(dir));
				if (n == null || n.isAir()) {
					continue;
				}
				Block b = n.getBlock();
				if (b instanceof DoorBlock || b instanceof BedBlock || b instanceof EntityBlock || b instanceof CraftingTableBlock || b instanceof StairBlock
					|| b instanceof LadderBlock) {
					return true;
				}
			}
		}
		for (Draft.LocalPoi p : d.pois()) {
			if (Math.abs(p.x() - x) + Math.abs(p.z() - z) <= 1 && Math.abs(p.y() - y) <= 1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Takes up the loft's boards where nothing stands on them and drawn air runs down from them to
	 * something the building drew (a floor, a rack, a table: never the ground under a plot). A lamp hung under a board
	 * goes with it (the light pass lights the room again). Boards left over that no longer touch a wall or anything
	 * under them (they would float) come up too. Returns how many boards came up.
	 */
	private static int open(Draft d, List<int[]> loft) {
		int n = 0;
		Set<Long> left = new HashSet<>();
		for (int[] c : loft) {
			if (openable(d, c[0], c[1], c[2])) {
				take(d, c[0], c[1], c[2]);
				n++;
			} else if (d.isSolid(c[0], c[1] - 1, c[2])) {
				left.add(Draft.key(c[0], c[1] - 1, c[2]));
			}
		}
		if (n == 0) {
			return 0;
		}
		// what is left in pieces: a piece that touches no wall and stands on nothing is taken up whole
		Set<Long> seen = new HashSet<>();
		for (long k : new ArrayList<>(left)) {
			if (!seen.add(k)) {
				continue;
			}
			List<Long> piece = new ArrayList<>();
			ArrayDeque<Long> q = new ArrayDeque<>();
			q.add(k);
			boolean held = false;
			while (!q.isEmpty()) {
				long p = q.poll();
				piece.add(p);
				int x = Draft.keyX(p);
				int y = Draft.keyY(p);
				int z = Draft.keyZ(p);
				Draft.Cell under = d.cell(x, y - 1, z);
				held |= under != null && !under.state().isAir() && !Spaces.hangs(under.state());
				for (Direction dir : Dirs.HORIZONTAL) {
					long nk = Draft.key(x + Dirs.dx(dir), y, z + Dirs.dz(dir));
					if (left.contains(nk)) {
						if (seen.add(nk)) {
							q.add(nk);
						}
					} else if (d.isSolid(x + Dirs.dx(dir), y, z + Dirs.dz(dir))) {
						held = true;
					}
				}
			}
			if (held) {
				continue;
			}
			for (long p : piece) {
				int x = Draft.keyX(p);
				int y = Draft.keyY(p);
				int z = Draft.keyZ(p);
				if (d.isEmpty(x, y + 1, z)) {
					take(d, x, y + 1, z);
					n++;
				}
			}
		}
		return n;
	}

	/** The board under the loft cell (x, y, z) can come up: nothing on it, drawn air (or hung lamps) under it, then the building. */
	private static boolean openable(Draft d, int x, int y, int z) {
		Draft.Cell board = d.cell(x, y - 1, z);
		if (board == null || board.stage() != StageKind.FLOORS || !d.isSolid(x, y - 1, z) || !d.isEmpty(x, y, z)) {
			return false;
		}
		int fy = y - 2;
		while (fy > y - 12 && hung(d, fy, x, z)) {
			fy--;
		}
		if (!drawnAir(d, fy, x, z)) {
			return false;
		}
		while (fy > y - 12 && drawnAir(d, fy - 1, x, z)) {
			fy--;
		}
		return d.get(x, fy - 1, z) != null;
	}

	/** Takes up the board under the loft cell (x, y, z) and the lamps hung from it. */
	private static void take(Draft d, int x, int y, int z) {
		for (int fy = y - 2; fy > y - 12 && hung(d, fy, x, z); fy--) {
			d.air(x, fy, z);
		}
		d.air(x, y - 1, z);
	}

	private static boolean hung(Draft d, int y, int x, int z) {
		BlockState s = d.get(x, y, z);
		return s != null && Spaces.hangs(s);
	}

	private static void debug(String what, int[] at) {
		if (Boolean.getBoolean("hearth.access.debug")) {
			System.out.println("lofts: " + what + " cells at " + at[0] + "/" + at[1] + "/" + at[2]);
		}
	}

	private static boolean drawnAir(Draft d, int y, int x, int z) {
		BlockState s = d.get(x, y, z);
		return s != null && s.isAir();
	}
}
