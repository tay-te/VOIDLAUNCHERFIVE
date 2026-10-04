package dev.voidmc.hearth.build.kit.extraction;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

/**
 * A pit cut in benches, the way quarrymen, clay diggers and sand diggers work open ground: the plan of the finished
 * pit, and the cutting of it lift by lift, each lift a part of the blueprint (docs/LIVING_WORLD.md §8.5).
 *
 * <p><b>The plan.</b> A rim rectangle {@code R1} is the pit's outline one lift below the floor. Each level down steps in
 * by the side's inset (a steep working face on one side, broad benches on the others), so the walls go down in steps
 * {@code h} high; on rising ground the steps continue up into the hillside (levels above the floor, their rectangles
 * stepping out to the limit the archetype allows), so a hillside quarry eats back into the slope as a stair of faces
 * rather than one cliff. The innermost level's rectangle is the pit floor. Columns the archetype keeps (the yard, the
 * spoil ground) and every column within two of water are never cut, so a pit on a bank never lets the river in.
 *
 * <p><b>The way down.</b> Each level is joined to the next by a short flight cut into the lower bench's outer row along
 * a wall: the rock left standing in steps (a stair tread of the rock on each), a block down per cell, so folk walk down
 * and carry back up. The flights turn from side to side as the pit deepens, a spiral of steps down the terraces. The
 * plan is walked (a step of at most one block between neighbours) from the archetype's entrance cell: a flight that
 * does not join is moved; every level reached gets its work faces.
 *
 * <p><b>The cutting.</b> {@link #cut} draws every lift in its own part, top-down: in a lift the columns are cut in order
 * of their walking distance from where the diggers come down (each a key, so the face advances across the bench), top
 * course first within a column; the flight's treads and a ladder or two on the walls go in after the cuts. Each lift
 * gets {@code WORK_FACE} points on its floor at the foot of its wall (where the gatherers stand) for the archetype's
 * trade.
 */
public final class Benches {
	public static final int NONE = Integer.MIN_VALUE;
	/** Side indices: front (low z), right (high x), back (high z), left (low x). */
	public static final int FRONT = 0;
	public static final int RIGHT = 1;
	public static final int BACK = 2;
	public static final int LEFT = 3;

	/**
	 * One level of the pit: its standing level and the rectangle cut down to it (and deeper, inside the next), its corners
	 * cut off by {@code bevel} cells (a worked face rounds its corners).
	 */
	public record Level(int y, int x0, int z0, int x1, int z1, int bevel) {
		public boolean contains(int x, int z) {
			if (x < this.x0 || x > this.x1 || z < this.z0 || z > this.z1) {
				return false;
			}
			int dx = Math.min(x - this.x0, this.x1 - x);
			int dz = Math.min(z - this.z0, this.z1 - z);
			return dx + dz >= this.bevel;
		}

		public int width() {
			return this.x1 - this.x0 + 1;
		}

		public int length() {
			return this.z1 - this.z0 + 1;
		}
	}

	/**
	 * A flight from level {@code from} (-1: the yard or the ground beside the pit) down to level {@code to}: its cells in
	 * walking order (top first) and their standing levels.
	 */
	public record Flight(int from, int to, int[][] cells, int[] levels, Direction down) {
	}

	private final Draft d;
	private final int w;
	private final int dp;
	/** Lift height: how deep each bench is cut. */
	public final int h;
	/** Levels top to bottom. */
	public final List<Level> levels = new ArrayList<>();
	/** Index of the first level below the floor (y = -h) in {@link #levels}, or -1. */
	public int rim = -1;
	private final boolean[] keep;
	/** The standing level each column is cut down to (NONE: uncut). */
	private final int[] target;
	/** The level index a column's target belongs to (-1: uncut). */
	private final int[] levelOf;
	/** Columns a flight stands in, by index → flight. */
	public final List<Flight> flights = new ArrayList<>();
	/** Columns left standing a block or two above their bench (blocks being cut free). */
	private final int[] stub;
	/** Columns reached from the entrance in the plan (by the walking rule). */
	private boolean[] reached;
	private final int @Nullable [] base;
	private final int entranceX;
	private final int entranceZ;
	private final int entranceY;

	/**
	 * Plans a pit.
	 *
	 * @param rim        the rectangle {x0, z0, x1, z1} cut one lift below the floor
	 * @param h          the lift (2 for clay and sand, 3 for rock)
	 * @param inset      how far each level steps in per side {front, right, back, left} (each ≥ 1)
	 * @param depth      how far below the floor the pit may go (it stops sooner when the floor would be under 3 × 3)
	 * @param limit      the rectangle {x0, z0, x1, z1} the levels above the floor may step out into (on rising ground)
	 * @param keep       columns never cut (w × dp, row-major by z), or null
	 * @param entrance   the cell {x, y, z} the diggers come from (on the yard at the floor)
	 */
	public Benches(Draft d, int[] rim, int h, int[] inset, int depth, int[] limit, boolean @Nullable [] keep, int[] entrance) {
		this(d, rim, h, inset, depth, limit, keep, entrance, null);
	}

	/**
	 * As above, with the standing level the archetype has already made of some columns ({@code base}, w × dp: the yard
	 * levelled to the street; NONE where the ground is left as it lies), for walking the plan.
	 */
	public Benches(Draft d, int[] rim, int h, int[] inset, int depth, int[] limit, boolean @Nullable [] keep, int[] entrance, int @Nullable [] base) {
		this(d, rim, h, inset, depth, limit, keep, entrance, base, -1);
	}

	/** As above, the yard's row along the rim fenced except where the first flight comes up ({@code rimRow}, -1: none). */
	public Benches(Draft d, int[] rim, int h, int[] inset, int depth, int[] limit, boolean @Nullable [] keep, int[] entrance, int @Nullable [] base,
		int rimRow) {
		this.d = d;
		this.base = base;
		this.rimRow = rimRow;
		this.w = d.frontage();
		this.dp = d.depth();
		this.h = h;
		this.keep = keep == null ? new boolean[this.w * this.dp] : keep;
		this.target = new int[this.w * this.dp];
		this.levelOf = new int[this.w * this.dp];
		this.stub = new int[this.w * this.dp];
		Arrays.fill(this.target, NONE);
		Arrays.fill(this.levelOf, -1);
		this.entranceX = entrance[0];
		this.entranceY = entrance[1];
		this.entranceZ = entrance[2];
		// water and the ground beside it is never cut (two columns of margin, looking past the plot), nor ground standing
		// above the plot's height (it cannot be cut from the top down)
		for (int z = 0; z < this.dp; z++) {
			for (int x = 0; x < this.w; x++) {
				if (d.site().ground(x, z) > d.maxHeight()) {
					this.keep[z * this.w + x] = true;
				}
				for (int dz = -2; dz <= 2 && !this.keep[z * this.w + x]; dz++) {
					for (int dx = -2; dx <= 2; dx++) {
						if (d.site().isWater(x + dx, z + dz)) {
							this.keep[z * this.w + x] = true;
							break;
						}
					}
				}
			}
		}
		plan(rim, inset, depth, limit);
		// where the pit's ground meets the yard level at the rim (or a stair comes down to it), the rim is left open at
		// the one column nearest the gate
		if (this.rimRow >= 0 && this.base != null) {
			int best = Integer.MAX_VALUE;
			for (int x = 0; x < this.w; x++) {
				int i = this.rimRow * this.w + x;
				if (this.base[i] == NONE || !inPlot(x, this.rimRow + 1) || this.keep[(this.rimRow + 1) * this.w + x] && this.base[i + this.w] == NONE) {
					continue;
				}
				int beyond = surface(x, this.rimRow + 1);
				if (Math.abs(beyond - this.base[i]) <= 1 && Math.abs(x - this.entranceX) < best) {
					best = Math.abs(x - this.entranceX);
					this.naturalHead = x;
				}
			}
		}
		flights();
	}

	// ------------------------------------------------------------------------------------------- the plan

	private void plan(int[] rim, int[] inset, int depth, int[] limit) {
		// levels below the floor, stepping in: each wall steps in by its inset, give or take one (benches are cut by
		// hand, never to a ruler; the working face stays steep), the corners rounded off on the upper levels
		List<Level> below = new ArrayList<>();
		int n = Math.max(1, (depth + this.h - 1) / this.h);
		int[] cur = rim.clone();
		for (int k = 1; k <= n; k++) {
			if (k > 1) {
				int[] step = new int[4];
				for (int sd = 0; sd < 4; sd++) {
					int j = this.d.random().nextInt(4);
					step[sd] = inset[sd] <= 0 ? 0 : inset[sd] == 1 ? (j == 0 ? 2 : 1) : Math.clamp(inset[sd] + (j == 0 ? -1 : j == 3 ? 1 : 0), 1, 3);
				}
				cur = new int[]{cur[0] + step[LEFT], cur[1] + step[FRONT], cur[2] - step[RIGHT], cur[3] - step[BACK]};
			}
			if (cur[2] - cur[0] < 2 || cur[3] - cur[1] < 2) {
				break;
			}
			int bevel = Math.min(cur[2] - cur[0], cur[3] - cur[1]) >= 8 ? this.d.random().nextInt(3) : 0;
			below.add(new Level(-this.h * k, cur[0], cur[1], cur[2], cur[3], bevel));
		}
		// levels at and above the floor, stepping out into the hillside as far as the limit lets them
		List<Level> above = new ArrayList<>();
		int top = Integer.MIN_VALUE;
		for (int z = limit[1]; z <= limit[3]; z++) {
			for (int x = limit[0]; x <= limit[2]; x++) {
				if (inPlot(x, z) && !this.keep[z * this.w + x]) {
					top = Math.max(top, this.d.site().ground(x, z));
				}
			}
		}
		Level prev = new Level(0, rim[0], rim[1], rim[2], rim[3], 0);
		for (int y = 0; y < top; y += this.h) {
			int x0 = Math.max(limit[0], prev.x0 - inset[LEFT]);
			int z0 = Math.max(limit[1], prev.z0 - (y == 0 ? 0 : inset[FRONT]));
			int x1 = Math.min(limit[2], prev.x1 + inset[RIGHT]);
			int z1 = Math.min(limit[3], prev.z1 + inset[BACK]);
			// the front edge stays at the rim (the yard lies before it)
			z0 = Math.max(z0, rim[1]);
			prev = new Level(y, x0, z0, x1, z1, Math.min(2, Math.min(x1 - x0, z1 - z0) / 6));
			above.add(0, prev);
		}
		this.levels.addAll(above);
		this.rim = this.levels.size();
		this.levels.addAll(below);
		if (below.isEmpty()) {
			this.rim = -1;
		}
		// each column is cut down to the innermost level that holds it
		for (int i = 0; i < this.levels.size(); i++) {
			Level l = this.levels.get(i);
			for (int z = l.z0; z <= l.z1; z++) {
				for (int x = l.x0; x <= l.x1; x++) {
					if (inPlot(x, z) && !this.keep[z * this.w + x] && l.contains(x, z)) {
						this.target[z * this.w + x] = l.y;
						this.levelOf[z * this.w + x] = i;
					}
				}
			}
		}
	}

	private boolean inPlot(int x, int z) {
		return x >= 0 && z >= 0 && x < this.w && z < this.dp;
	}

	/** The standing level of a column in the finished pit: its cut, a flight's step, a stub's top, else the ground. */
	public int surface(int x, int z) {
		int g = this.d.site().ground(x, z);
		if (!inPlot(x, z)) {
			return g;
		}
		int t = this.target[z * this.w + x];
		if (t == NONE) {
			return this.base != null && this.base[z * this.w + x] != NONE ? this.base[z * this.w + x] : g;
		}
		return Math.min(g, t + this.stub[z * this.w + x]);
	}

	/** Whether a column is cut at all. */
	public boolean cut(int x, int z) {
		return inPlot(x, z) && this.target[z * this.w + x] != NONE && this.target[z * this.w + x] < this.d.site().ground(x, z);
	}

	/** The cut level of a column (NONE: never cut). */
	public int target(int x, int z) {
		return inPlot(x, z) ? this.target[z * this.w + x] : NONE;
	}

	/** The index of the level a column belongs to (-1: not in the pit). */
	public int levelOf(int x, int z) {
		return inPlot(x, z) ? this.levelOf[z * this.w + x] : -1;
	}

	/** The pit's floor: the innermost level. */
	public @Nullable Level floor() {
		return this.levels.isEmpty() ? null : this.levels.get(this.levels.size() - 1);
	}

	/** The deepest standing level cut. */
	public int bottom() {
		int b = 0;
		for (int z = 0; z < this.dp; z++) {
			for (int x = 0; x < this.w; x++) {
				if (cut(x, z)) {
					b = Math.min(b, surface(x, z));
				}
			}
		}
		return b;
	}

	/** Whether a column was reached from the entrance in the plan. */
	public boolean reached(int x, int z) {
		return inPlot(x, z) && this.reached != null && this.reached[z * this.w + x];
	}

	// ------------------------------------------------------------------------------------------- the way down

	private void flights() {
		// joins level to level until every level with cuts is reached from the entrance: from the level above, or up from
		// the level below, or down from the yard or the ground beside the pit at the right height; the first level below
		// the floor comes down from the yard on the front side, the others turn round the pit
		int[] order = {FRONT, LEFT, BACK, RIGHT};
		int turn = this.d.random().nextInt(4);
		walk();
		boolean progress = true;
		while (progress) {
			progress = false;
			for (int j = 0; j < this.levels.size(); j++) {
				if (!anyCut(this.levels.get(j)) || ringReached(j)) {
					continue;
				}
				boolean placed = false;
				for (int mode = 0; mode < 3 && !placed; mode++) {
					int upper = mode == 0 ? j - 1 : mode == 1 ? j : -1;
					int lower = mode == 0 ? j : mode == 1 ? j + 1 : j;
					if (upper >= this.levels.size() || lower >= this.levels.size() || mode == 0 && upper < 0) {
						continue;
					}
					// the level on the other side of the flight must be reached already (the ground: its entry cell is)
					int other = mode == 0 ? upper : mode == 1 ? lower : -1;
					if (other >= 0 && (!anyCut(this.levels.get(other)) || !ringReached(other))) {
						continue;
					}
					for (int t = 0; t < 4 && !placed; t++) {
						int side = lower == this.rim && upper < 0 ? (t == 0 ? FRONT : order[(turn + t) % 4]) : order[(turn + j + t + 4) % 4];
						placed = flight(upper, lower, side);
					}
				}
				progress |= placed;
			}
		}
		walk();
	}

	private boolean anyCut(Level l) {
		for (int z = l.z0; z <= l.z1; z++) {
			for (int x = l.x0; x <= l.x1; x++) {
				if (cut(x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether any cell of a level's own ring (its floor outside the next level) is reached. */
	private boolean ringReached(int i) {
		Level l = this.levels.get(i);
		for (int z = l.z0; z <= l.z1; z++) {
			for (int x = l.x0; x <= l.x1; x++) {
				if (this.levelOf[z * this.w + x] == i && cut(x, z) && this.reached[z * this.w + x]) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Tries a flight from level {@code upper} (-1: the yard or the ground beside the pit, at one lift above the lower
	 * level) down into level {@code lower} along one side: three cells (two for a lift of two) in the lower level's outer
	 * row, each a block lower than the last, entered from a cell of the upper level (or a reached cell of the ground)
	 * beside the first. It stays only if it joins the two.
	 */
	private boolean flight(int upper, int lower0, int side) {
		Level lower = this.levels.get(lower0);
		int upperY = upper < 0 ? lower.y + this.h : this.levels.get(upper).y;
		int steps = this.h - 1;
		int len = steps + 1;
		// the row: lower's edge on that side, walked along it
		int ax;
		int az;
		int bx;
		int bz;
		int ox;
		int oz;
		switch (side) {
			case FRONT -> {
				ax = lower.x0 + 1;
				bx = lower.x1 - 1;
				az = bz = lower.z0;
				ox = 0;
				oz = -1;
			}
			case BACK -> {
				ax = lower.x0 + 1;
				bx = lower.x1 - 1;
				az = bz = lower.z1;
				ox = 0;
				oz = 1;
			}
			case LEFT -> {
				az = lower.z0 + 1;
				bz = lower.z1 - 1;
				ax = bx = lower.x0;
				ox = -1;
				oz = 0;
			}
			default -> {
				az = lower.z0 + 1;
				bz = lower.z1 - 1;
				ax = bx = lower.x1;
				ox = 1;
				oz = 0;
			}
		}
		boolean alongX = side == FRONT || side == BACK;
		int span = alongX ? bx - ax + 1 : bz - az + 1;
		if (span < len) {
			return false;
		}
		// candidate starts, from the middle out, in both directions
		List<int[]> starts = new ArrayList<>();
		int mid = span / 2;
		for (int k = 0; k < span; k++) {
			int off = (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2);
			int s0 = mid + off;
			if (s0 >= 0 && s0 < span) {
				starts.add(new int[]{s0, 1});
				starts.add(new int[]{s0, -1});
			}
		}
		for (int[] st : starts) {
			int s0 = st[0];
			int dir = st[1];
			int sEnd = s0 + dir * (len - 1);
			if (sEnd < 0 || sEnd >= span) {
				continue;
			}
			int[][] cells = new int[len][];
			boolean ok = true;
			for (int k = 0; k < len && ok; k++) {
				int sk = s0 + dir * k;
				int x = alongX ? ax + sk : ax;
				int z = alongX ? az : az + sk;
				if (!inPlot(x, z) || this.keep[z * this.w + x] || this.levelOf[z * this.w + x] != lower0 || !cut(x, z) || this.stub[z * this.w + x] != 0
					|| inFlight(x, z)) {
					ok = false;
					break;
				}
				cells[k] = new int[]{x, z};
			}
			if (!ok) {
				continue;
			}
			// entered from a cell of the upper level (or the ground at the floor) beside the first step
			int ex = cells[0][0] + ox;
			int ez = cells[0][1] + oz;
			if (!inPlot(ex, ez) || surface(ex, ez) != upperY || inFlight(ex, ez) || this.d.site().isWater(ex, ez)
				|| ez != this.rimRow && !walkable(ex, ez)) {
				continue;
			}
			// from the ground: a cell already reached (a level's own cells are checked by the joining below)
			if (upper < 0 && !this.reached[ez * this.w + ex] && !(ez == this.rimRow && reachedBeside(ex, ez))) {
				continue;
			}
			if (upper >= 0 && this.levelOf[ez * this.w + ex] != upper) {
				continue;
			}
			// the steps' ground must stand above them (the rock is left in steps, never built up)
			int[] lv = new int[len];
			for (int k = 0; k < len; k++) {
				lv[k] = upperY - 1 - k;
				if (k < steps && this.d.site().ground(cells[k][0], cells[k][1]) <= lv[k]) {
					ok = false;
				}
			}
			if (!ok) {
				continue;
			}
			Direction down = alongX ? (dir > 0 ? Dirs.RIGHT : Dirs.LEFT) : (dir > 0 ? Dirs.BACK : Dirs.FRONT);
			for (int k = 0; k < steps; k++) {
				this.stub[cells[k][1] * this.w + cells[k][0]] = lv[k] - lower.y;
			}
			this.flights.add(new Flight(upper, lower0, cells, lv, down));
			walk();
			if ((upper < 0 || ringReached(upper)) && ringReached(lower0)) {
				return true;
			}
			// it did not join: take it back
			this.flights.remove(this.flights.size() - 1);
			for (int k = 0; k < steps; k++) {
				this.stub[cells[k][1] * this.w + cells[k][0]] = 0;
			}
			walk();
		}
		return false;
	}

	/** Whether a cell of the rim row has a reached yard cell beside it (the rim is crossed only at a flight's head). */
	private boolean reachedBeside(int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			if (inPlot(nx, nz) && this.reached[nz * this.w + nx] && Math.abs(surface(nx, nz) - surface(x, z)) <= 1) {
				return true;
			}
		}
		return false;
	}

	private boolean inFlight(int x, int z) {
		for (Flight f : this.flights) {
			for (int[] c : f.cells) {
				if (c[0] == x && c[1] == z) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The row of the yard along the rim (fenced against the drop, open only where a flight comes up), or -1: walking the
	 * plan crosses it only at a flight's head.
	 */
	public final int rimRow;
	/** The rim column where the pit's ground meets the yard without a flight (or a stair comes down), or -1. */
	private int naturalHead = -1;

	/** Whether the plan may be walked through a column: the yard, the pit, ground left in it; not the kept ground. */
	private boolean walkable(int x, int z) {
		int i = z * this.w + x;
		if (this.base != null && this.base[i] != NONE) {
			return z != this.rimRow || rimOpen(x);
		}
		// the yard's ground left as it lies (nothing stands on it), the pit, ground left in the pit; not the kept ground
		return z <= this.rimRow || !this.keep[i] || this.target[i] != NONE;
	}

	/**
	 * Whether the rim is open at a column (no fence): at the head of a flight down, or where the ground beyond is no more
	 * than a step below the yard (a pit falling away down a hillside is walked into over the ground as it lies).
	 */
	public boolean rimOpen(int x) {
		int z = this.rimRow;
		if (z < 0 || !inPlot(x, z)) {
			return false;
		}
		return flightHead(x, z) || x == this.naturalHead;
	}

	private boolean flightHead(int x, int z) {
		for (Flight f : this.flights) {
			if (f.from < 0) {
				int[] c = f.cells[0];
				if (Math.abs(c[0] - x) + Math.abs(c[1] - z) == 1) {
					return true;
				}
			}
		}
		return false;
	}

	/** Walks the plan from the entrance: a step of at most one block between side neighbours, both ways. */
	private void walk() {
		this.reached = new boolean[this.w * this.dp];
		if (!inPlot(this.entranceX, this.entranceZ)) {
			return;
		}
		IntArrayFIFOQueue q = new IntArrayFIFOQueue();
		int start = this.entranceZ * this.w + this.entranceX;
		this.reached[start] = true;
		q.enqueue(start);
		while (!q.isEmpty()) {
			int i = q.dequeueInt();
			int x = i % this.w;
			int z = i / this.w;
			int s = surface(x, z);
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				if (!inPlot(nx, nz) || this.reached[nz * this.w + nx] || this.d.site().isWater(nx, nz) || !walkable(nx, nz)) {
					continue;
				}
				if (Math.abs(surface(nx, nz) - s) <= 1) {
					this.reached[nz * this.w + nx] = true;
					q.enqueue(nz * this.w + nx);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------- stubs

	/**
	 * Leaves up to {@code count} blocks standing a block or two proud of the benches (blocks being cut free) on the inner
	 * edge of the levels' rings, where they block neither the flights nor the way along the bench.
	 */
	public void stubs(int count) {
		int placed = 0;
		for (int tries = 0; tries < count * 12 && placed < count; tries++) {
			int i = this.d.random().nextInt(Math.max(1, this.levels.size() - 1));
			Level l = this.levels.get(i);
			Level in = i + 1 < this.levels.size() ? this.levels.get(i + 1) : null;
			if (in == null) {
				continue;
			}
			int x = l.x0 + this.d.random().nextInt(l.width());
			int z = l.z0 + this.d.random().nextInt(l.length());
			if (this.levelOf[z * this.w + x] != i || !cut(x, z) || this.stub[z * this.w + x] != 0 || inFlight(x, z)) {
				continue;
			}
			// on the inner edge: beside the next level's rectangle, the outer row stays walkable
			boolean edge = false;
			int free = 0;
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				if (in.contains(nx, nz)) {
					edge = true;
				}
				if (inPlot(nx, nz) && this.levelOf[nz * this.w + nx] == i && this.stub[nz * this.w + nx] == 0 && !inFlight(nx, nz)) {
					free++;
				}
			}
			if (!edge || free < 2 || this.d.site().ground(x, z) <= l.y + 2) {
				continue;
			}
			this.stub[z * this.w + x] = this.d.chance(0.6) ? 1 : 2;
			walk();
			// never cut a bench in two
			if (!ringReached(i) || in != null && !reachedAnyOf(i + 1)) {
				this.stub[z * this.w + x] = 0;
				walk();
				continue;
			}
			placed++;
		}
	}

	private boolean reachedAnyOf(int i) {
		return !anyCut(this.levels.get(i)) || ringReached(i);
	}

	// ------------------------------------------------------------------------------------------- the cutting

	/**
	 * Cuts every lift, each in its own part from {@code firstPart} on, top level first. Returns the part of each level
	 * (-1 where a level had nothing to cut). Work faces go to {@code job}.
	 */
	public int[] cut(int firstPart, Rock rock, JobType job) {
		int[] parts = new int[this.levels.size()];
		Arrays.fill(parts, -1);
		int part = firstPart;
		for (int i = 0; i < this.levels.size(); i++) {
			if (lift(i, part, rock, job)) {
				parts[i] = part;
				part++;
			}
		}
		return parts;
	}

	/** The cells of lift i: {x, z, yTop (exclusive)} for every column it cuts. */
	public List<int[]> lift(int i) {
		Level l = this.levels.get(i);
		boolean first = i == 0;
		List<int[]> out = new ArrayList<>();
		for (int z = l.z0; z <= l.z1; z++) {
			for (int x = l.x0; x <= l.x1; x++) {
				if (!inPlot(x, z) || this.levelOf[z * this.w + x] < i) {
					continue;
				}
				int g = this.d.site().ground(x, z);
				int lo = Math.max(l.y, surface(x, z));
				int hi = first ? g : Math.min(g, l.y + this.h);
				if (lo < hi) {
					out.add(new int[]{x, z, lo, hi});
				}
			}
		}
		return out;
	}

	private boolean lift(int i, int part, Rock rock, JobType job) {
		List<int[]> cells = lift(i);
		if (cells.isEmpty()) {
			return false;
		}
		Level l = this.levels.get(i);
		// where the diggers come down into this lift: the foot of the flight from above, else the cell nearest the entrance
		int[] from = null;
		for (Flight f : this.flights) {
			if (f.to == i) {
				from = f.cells[f.cells.length - 1];
			}
		}
		if (from == null) {
			int best = Integer.MAX_VALUE;
			for (int[] c : cells) {
				int dd = Math.abs(c[0] - this.entranceX) + Math.abs(c[1] - this.entranceZ);
				if (dd < best) {
					best = dd;
					from = new int[]{c[0], c[1]};
				}
			}
		}
		int[] dist = distances(cells, from);
		int maxKey = 0;
		try (var ps = this.d.part(part); var st = this.d.stage(StageKind.EARTHWORK)) {
			BlockState air = Blocks.AIR.defaultBlockState();
			for (int k = 0; k < cells.size(); k++) {
				int[] c = cells.get(k);
				int key = dist[k];
				maxKey = Math.max(maxKey, key);
				try (var o = this.d.order(key)) {
					for (int y = c[3] - 1; y >= c[2]; y--) {
						this.d.set(c[0], y, c[1], air);
					}
				}
			}
			// the flight's treads, cut into the rock left standing in steps
			try (var o = this.d.order(maxKey + 1)) {
				for (Flight f : this.flights) {
					if (f.to != i) {
						continue;
					}
					for (int k = 0; k + 1 < f.cells.length; k++) {
						int[] c = f.cells[k];
						this.d.set(c[0], f.levels[k] - 1, c[1], States.stairs(rock.stairs(), f.down.getOpposite(), Half.BOTTOM));
					}
				}
			}
		}
		debris(i, part, rock);
		// a ladder up a wall of the bench now and then (a quicker way up than the flights)
		if (i > 0 && this.d.chance(0.55)) {
			ladder(i, part);
		}
		faces(i, part, job);
		return true;
	}

	/**
	 * What a worked bench is strewn with: patches of grit trodden into its floor and spalls lying about (half blocks of
	 * the broken rock, walked over), never on a flight, a stub or where a ladder stands.
	 */
	private void debris(int i, int part, Rock rock) {
		if (!this.debris) {
			return;
		}
		Level l = this.levels.get(i);
		boolean bottom = i == this.levels.size() - 1;
		try (var ps = this.d.part(part); var st = this.d.stage(StageKind.EARTHWORK); var o = this.d.order(Integer.MAX_VALUE - 16)) {
			for (int z = l.z0; z <= l.z1; z++) {
				for (int x = l.x0; x <= l.x1; x++) {
					if (this.levelOf[z * this.w + x] != i || !cut(x, z) || this.stub[z * this.w + x] != 0 || inFlight(x, z)) {
						continue;
					}
					int y = surface(x, z);
					double patch = Noise.value(this.d.seed() ^ 0xDEB1, x / 3.0, i * 7.0, z / 3.0);
					if (patch > 0.68) {
						this.d.set(x, y - 1, z, rock.spoil()[0].defaultBlockState());
					}
					double u = Noise.unit(this.d.seed() ^ 0xDEB2, x, y, z);
					// a spall lies only out on the level floor, where it makes no step too high (beside a flight or a drop)
					boolean level = true;
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						level &= inPlot(nx, nz) && surface(nx, nz) == y && !inFlight(nx, nz) && this.stub[nz * this.w + nx] == 0;
					}
					if (level && u < (bottom ? 0.07 : 0.045) && this.d.isEmpty(x, y, z)) {
						this.d.set(x, y, z, States.slab(rock.slab(), net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
					}
				}
			}
		}
	}

	/** Whether benches are strewn with grit and spalls (quarries; a clay or sand pit is cleaner). */
	public boolean debris = true;

	/** Walking distances over a lift's columns from a start cell (cells not reached keep a large key). */
	private int[] distances(List<int[]> cells, int[] from) {
		int[] grid = new int[this.w * this.dp];
		Arrays.fill(grid, -2);
		for (int[] c : cells) {
			grid[c[1] * this.w + c[0]] = -1;
		}
		IntArrayFIFOQueue q = new IntArrayFIFOQueue();
		// start from the lift cell nearest the start
		int best = -1;
		int bestD = Integer.MAX_VALUE;
		for (int[] c : cells) {
			int dd = Math.abs(c[0] - from[0]) + Math.abs(c[1] - from[1]);
			if (dd < bestD) {
				bestD = dd;
				best = c[1] * this.w + c[0];
			}
		}
		grid[best] = 0;
		q.enqueue(best);
		int far = 0;
		while (!q.isEmpty()) {
			int i = q.dequeueInt();
			int x = i % this.w;
			int z = i / this.w;
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				if (inPlot(nx, nz) && grid[nz * this.w + nx] == -1) {
					grid[nz * this.w + nx] = grid[i] + 1;
					far = Math.max(far, grid[i] + 1);
					q.enqueue(nz * this.w + nx);
				}
			}
		}
		int[] out = new int[cells.size()];
		for (int k = 0; k < cells.size(); k++) {
			int v = grid[cells.get(k)[1] * this.w + cells.get(k)[0]];
			out[k] = v < 0 ? far + 1 + Math.abs(cells.get(k)[0] - from[0]) + Math.abs(cells.get(k)[1] - from[1]) : v;
		}
		return out;
	}

	/** A ladder against the wall of level i - 1, from level i's floor to its top (in level i's ring, out of the way). */
	private void ladder(int i, int part) {
		Level l = this.levels.get(i);
		List<int[]> spots = new ArrayList<>();
		for (int z = l.z0; z <= l.z1; z++) {
			for (int x = l.x0; x <= l.x1; x++) {
				if (this.levelOf[z * this.w + x] != i || !cut(x, z) || this.stub[z * this.w + x] != 0 || inFlight(x, z)) {
					continue;
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (!inPlot(nx, nz) || this.levelOf[nz * this.w + nx] != i - 1 || surface(nx, nz) != l.y + this.h || inFlight(nx, nz)) {
						continue;
					}
					// the cells either side along the wall stay free to walk past
					Direction along = Dirs.right(dir);
					boolean free = true;
					for (int s : new int[]{-1, 1}) {
						int px = x + Dirs.dx(along) * s;
						int pz = z + Dirs.dz(along) * s;
						if (!inPlot(px, pz) || surface(px, pz) != l.y || inFlight(px, pz)) {
							free = false;
						}
					}
					if (free) {
						spots.add(new int[]{x, z, dir.get2DDataValue()});
					}
				}
			}
		}
		if (spots.isEmpty()) {
			return;
		}
		int[] s = spots.get(this.d.random().nextInt(spots.size()));
		Direction toWall = Direction.from2DDataValue(s[2]);
		try (var ps = this.d.part(part); var st = this.d.stage(StageKind.EXTERIOR)) {
			for (int y = l.y; y < l.y + this.h; y++) {
				this.d.set(s[0], y, s[1], Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, toWall.getOpposite()));
			}
		}
	}

	/** Work faces on level i's floor at the foot of the wall it was cut from: two, far apart, both reached. */
	private void faces(int i, int part, JobType job) {
		Level l = this.levels.get(i);
		List<int[]> spots = new ArrayList<>();
		for (int z = l.z0; z <= l.z1; z++) {
			for (int x = l.x0; x <= l.x1; x++) {
				if (this.levelOf[z * this.w + x] != i || !cut(x, z) || this.stub[z * this.w + x] != 0 || inFlight(x, z) || !this.reached[z * this.w + x]
					|| !this.d.isEmpty(x, surface(x, z), z) || !this.d.isEmpty(x, surface(x, z) + 1, z)) {
					continue;
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (inPlot(nx, nz) && surface(nx, nz) >= l.y + 2) {
						spots.add(new int[]{x, z, dir.get2DDataValue()});
						break;
					}
				}
			}
		}
		if (spots.isEmpty()) {
			// the floor of the pit: anywhere on it, facing the middle of the nearest wall
			for (int z = l.z0; z <= l.z1 && spots.isEmpty(); z++) {
				for (int x = l.x0; x <= l.x1; x++) {
					if (this.levelOf[z * this.w + x] == i && cut(x, z) && this.reached[z * this.w + x] && !inFlight(x, z) && this.stub[z * this.w + x] == 0) {
						spots.add(new int[]{x, z, Dirs.BACK.get2DDataValue()});
						break;
					}
				}
			}
		}
		if (spots.isEmpty()) {
			return;
		}
		int[] a = spots.get(this.d.random().nextInt(spots.size()));
		int[] b = a;
		int far = -1;
		for (int[] s : spots) {
			int dd = Math.abs(s[0] - a[0]) + Math.abs(s[1] - a[1]);
			if (dd > far) {
				far = dd;
				b = s;
			}
		}
		this.d.poi(PoiType.WORK_FACE, a[0], surface(a[0], a[1]), a[1], Direction.from2DDataValue(a[2]), job);
		if (b != a && far >= 3) {
			this.d.poi(PoiType.WORK_FACE, b[0], surface(b[0], b[1]), b[1], Direction.from2DDataValue(b[2]), job);
		}
	}
}
