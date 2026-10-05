package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Curtain walls: straight stretches of wall between towers, footed in the ground wherever it lies (so a wall on a hill
 * shows its full height to the field and stands its walk height over the court), with a plinth and a battered talus at
 * the foot where the ground falls away, a wall walk behind a parapet cut in the culture's merlons (corbelled out over a
 * course of machicolation brackets where the manner has them), and flights of steps up from the court.
 */
public final class Curtain {
	private Curtain() {
	}

	/** What a stretch of curtain carries. */
	public record Opts(boolean machicolate, boolean loops, boolean lamps) {
		public static Opts of(Manner man) {
			return new Opts(man.machicolate(), true, true);
		}
	}

	/**
	 * A straight stretch whose field face runs from (ax, az) to (bx, bz) (axis-aligned, inclusive), {@code thick} deep
	 * toward the ward (opposite {@code out}), its walk at standing level {@code walk}. Cells already drawn as something
	 * else are kept where {@code keep} says (a tower that stands there).
	 */
	public static void run(Draft d, Dress m, Manner man, int ax, int az, int bx, int bz, Direction out, int thick, int walk, Opts o) {
		Direction along = ax == bx ? (bz >= az ? Dirs.BACK : Dirs.FRONT) : (bx >= ax ? Dirs.RIGHT : Dirs.LEFT);
		int n = Math.abs(bx - ax) + Math.abs(bz - az) + 1;
		Direction in = out.getOpposite();
		BlockState air = Blocks.AIR.defaultBlockState();
		boolean mach = o.machicolate() && room(d, ax, az, bx, bz, out, walk);
		boolean timber = man.merlon() == Manner.Merlon.TIMBER;
		for (int i = 0; i < n; i++) {
			int x = ax + Dirs.dx(along) * i;
			int z = az + Dirs.dz(along) * i;
			// footings under the whole section, to the lowest ground under it
			int lo = Integer.MAX_VALUE;
			for (int k = 0; k < thick; k++) {
				lo = Math.min(lo, d.site().ground(x + Dirs.dx(in) * k, z + Dirs.dz(in) * k));
			}
			for (int k = 0; k < thick; k++) {
				int px = x + Dirs.dx(in) * k;
				int pz = z + Dirs.dz(in) * k;
				if (!Mason.inPlot(d, px, pz)) {
					continue;
				}
				Mason.footing(d, m, px, pz, Math.min(lo, walk - 2));
				int g = d.site().ground(px, pz);
				int topSolid = k == 0 && !mach ? walk : walk - 1;
				try (var s = d.stage(StageKind.WALLS)) {
					for (int y = Math.min(lo, walk - 2); y <= topSolid; y++) {
						Paint p;
						if (y < g + 1 && k == 0 || y <= lo) {
							p = m.plinth.full();
						} else if (k == 0 && y == topSolid && timber) {
							p = Mason.post(m.frame);
						} else {
							p = m.wall.full();
						}
						d.set(px, y, pz, p);
					}
					if (k > 0 || mach) {
						// the walk, paved
						d.set(px, walk - 1, pz, m.paving.full());
						d.set(px, walk, pz, air);
						d.set(px, walk + 1, pz, air);
					}
				}
			}
			// the parapet: on the field face, or corbelled out a block over machicolations
			try (var s = d.stage(StageKind.WALLS)) {
				if (mach) {
					int ox = x + Dirs.dx(out);
					int oz = z + Dirs.dz(out);
					Mason.corbel(d, m.dress, ox, walk - 1, oz, in);
					if (Mason.ok(d, ox, walk - 2, oz) && (i & 1) == 0) {
						Mason.corbel(d, m.dress, ox, walk - 2, oz, in);
					}
					if (Mason.ok(d, ox, walk, oz)) {
						d.set(ox, walk, oz, timber ? Mason.post(m.frame) : m.wall.full());
					}
					Mason.merlon(d, m, man.merlon(), ox, walk + 1, oz, i, along, out, i == 0 || i == n - 1);
				} else {
					Mason.merlon(d, m, man.merlon(), x, walk + 1, z, i, along, out, i == 0 || i == n - 1);
					// a drip course under the parapet: a shadow line along the wall head
					if (!timber) {
						Mason.corbel(d, m.dress, x + Dirs.dx(out), walk - 1, z + Dirs.dz(out), in);
					}
				}
			}
			// a talus where the field falls away under the wall
			int fx = x + Dirs.dx(out);
			int fz = z + Dirs.dz(out);
			if (Mason.inPlot(d, fx, fz) && d.site().ground(fx, fz) < walk - 4) {
				Mason.talus(d, m, fx, fz, out, walk - 4);
			}
			// arrow loops in the face below the walk, every fourth cell
			if (o.loops() && i % 4 == 2 && !timber) {
				int g = d.site().ground(x, z);
				int ly = walk - 3;
				if (ly > g + 1 && d.isSolid(x + Dirs.dx(in), ly, z + Dirs.dz(in))) {
					Mason.loop(d, x, ly, z, 1);
				}
			}
		}
	}

	/**
	 * Covers the walk of a stretch (drawn by {@link #run} with the same arguments) under a timber pentice, as the walls
	 * of central Europe's castles are: the parapet raised solid to the roof with loops every third cell, timber posts on
	 * the court edge of the walk carrying a lean-to roof that rises to the parapet and oversails the court by a block.
	 */
	public static void covered(Draft d, Dress m, Manner man, int ax, int az, int bx, int bz, Direction out, int thick, int walk) {
		Direction along = ax == bx ? (bz >= az ? Dirs.BACK : Dirs.FRONT) : (bx >= ax ? Dirs.RIGHT : Dirs.LEFT);
		int n = Math.abs(bx - ax) + Math.abs(bz - az) + 1;
		Direction in = out.getOpposite();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int i = 0; i < n; i++) {
			int x = ax + Dirs.dx(along) * i;
			int z = az + Dirs.dz(along) * i;
			if (!Mason.inPlot(d, x, z)) {
				continue;
			}
			// the parapet raised two courses over the walk, loops every third cell
			try (var s = d.stage(StageKind.WALLS)) {
				if (d.isSolid(x, walk, z)) {
					for (int y = walk + 1; y <= walk + 2; y++) {
						boolean loop = y == walk + 1 && i % 3 == 1;
						d.set(x, y, z, loop ? air : m.wall.full().at(x, y, z));
					}
				}
			}
			// the posts on the court edge of the walk
			int px = x + Dirs.dx(in) * (thick - 1);
			int pz = z + Dirs.dz(in) * (thick - 1);
			if (i % 3 == 0 && d.isEmpty(px, walk, pz) && d.isSolid(px, walk - 1, pz)) {
				try (var s = d.stage(StageKind.FRAME)) {
					d.set(px, walk, pz, Mason.post(m.frame));
					d.set(px, walk + 1, pz, Mason.post(m.frame));
				}
			}
		}
		// the roof: from a block over the court down... rising from the court edge to the parapet head
		int x0 = Math.min(ax, bx);
		int x1 = Math.max(ax, bx);
		int z0 = Math.min(az, bz);
		int z1 = Math.max(az, bz);
		int ox0 = Math.min(x0, x0 + Dirs.dx(in) * thick);
		int ox1 = Math.max(x1, x1 + Dirs.dx(in) * thick);
		int oz0 = Math.min(z0, z0 + Dirs.dz(in) * thick);
		int oz1 = Math.max(z1, z1 + Dirs.dz(in) * thick);
		ox0 = Math.max(0, ox0);
		oz0 = Math.max(0, oz0);
		ox1 = Math.min(d.frontage() - 1, ox1);
		oz1 = Math.min(d.depth() - 1, oz1);
		dev.voidmc.hearth.build.kit.Roofs.Field f = dev.voidmc.hearth.build.kit.Roofs.shed(ox0, oz0, ox1, oz1, out,
			dev.voidmc.hearth.build.kit.Roofs.Profile.straight(0.5));
		try (var s = d.stage(StageKind.ROOF)) {
			dev.voidmc.hearth.build.kit.Roofs.raster(d, f, walk + 2, ox0, oz0, ox1, oz1, m.roof, m.trim, dev.voidmc.hearth.build.style.Habits.Ridge.SLAB);
		}
	}

	/** Whether the plot leaves a row outside the whole stretch for a corbelled parapet. */
	private static boolean room(Draft d, int ax, int az, int bx, int bz, Direction out, int walk) {
		int ox0 = Math.min(ax, bx) + Dirs.dx(out);
		int oz0 = Math.min(az, bz) + Dirs.dz(out);
		int ox1 = Math.max(ax, bx) + Dirs.dx(out);
		int oz1 = Math.max(az, bz) + Dirs.dz(out);
		for (int x = ox0; x <= ox1; x++) {
			for (int z = oz0; z <= oz1; z++) {
				if (!Mason.inPlot(d, x, z) && !(z == -1 && walk - 2 >= 3)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * A flight of steps against the inner face of a curtain, climbing {@code along} from the court at standing level
	 * {@code from} to the walk at {@code to}: the flight starts at (x, z) (its first tread), each tread a block higher and
	 * a cell further on, built up solid underneath, the headroom cleared. Returns the cell of the top tread.
	 */
	public static int[] flight(Draft d, Dress m, int x, int z, Direction along, int from, int to) {
		int rise = to - from;
		BlockState air = Blocks.AIR.defaultBlockState();
		int px = x;
		int pz = z;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int k = 1; k <= rise; k++) {
				px = x + Dirs.dx(along) * (k - 1);
				pz = z + Dirs.dz(along) * (k - 1);
				int y = from + k - 1;
				int g = Math.min(d.site().ground(px, pz), from - 1);
				for (int yy = g - 1; yy < y; yy++) {
					d.set(px, yy, pz, m.wall.full());
				}
				d.set(px, y, pz, m.paving.has(Form.STAIRS) ? m.paving.stairs(along, Half.BOTTOM) : m.wall.stairsOrSlab(along, Half.BOTTOM));
				for (int yy = y + 1; yy <= y + 3; yy++) {
					d.set(px, yy, pz, air);
				}
			}
		}
		return new int[]{px, pz};
	}

	/** Lanterns along the court face of a stretch every {@code spacing} cells, hung on brackets two over the court. */
	public static void lamps(Draft d, int ax, int az, int bx, int bz, Direction in, int court, int spacing) {
		Direction along = ax == bx ? (bz >= az ? Dirs.BACK : Dirs.FRONT) : (bx >= ax ? Dirs.RIGHT : Dirs.LEFT);
		int n = Math.abs(bx - ax) + Math.abs(bz - az) + 1;
		for (int i = spacing / 2; i < n; i += spacing) {
			int x = ax + Dirs.dx(along) * i + Dirs.dx(in);
			int z = az + Dirs.dz(along) * i + Dirs.dz(in);
			if (Mason.inPlot(d, x, z) && d.isEmpty(x, court + 2, z) && d.isEmpty(x, court + 3, z) && d.isSolid(x - Dirs.dx(in), court + 2, z - Dirs.dz(in))) {
				Lighting.doorLamp(d, x, court + 2, z, in.getOpposite());
			}
		}
	}

	/** Guard posts along a walk, every {@code spacing} cells, looking out. */
	public static void posts(Draft d, int ax, int az, int bx, int bz, Direction out, int thick, int walk, int spacing) {
		Direction along = ax == bx ? (bz >= az ? Dirs.BACK : Dirs.FRONT) : (bx >= ax ? Dirs.RIGHT : Dirs.LEFT);
		int n = Math.abs(bx - ax) + Math.abs(bz - az) + 1;
		Direction in = out.getOpposite();
		for (int i = spacing / 2; i < n; i += spacing) {
			int x = ax + Dirs.dx(along) * i + Dirs.dx(in) * Math.max(1, thick - 1);
			int z = az + Dirs.dz(along) * i + Dirs.dz(in) * Math.max(1, thick - 1);
			d.poi(PoiType.GUARD_POST, x, walk, z, out);
		}
	}

	/** Cells of a straight run, for callers that lay things along it. */
	public static List<int[]> cells(int ax, int az, int bx, int bz) {
		List<int[]> out = new java.util.ArrayList<>();
		int n = Math.abs(bx - ax) + Math.abs(bz - az) + 1;
		int sx = Integer.signum(bx - ax);
		int sz = Integer.signum(bz - az);
		for (int i = 0; i < n; i++) {
			out.add(new int[]{ax + sx * i, az + sz * i});
		}
		return out;
	}
}
