package dev.voidmc.hearth.build.kit.light;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * One lighting pass over a draft: reads the draft and its ground into a {@link Grid} (local frame), floods its light,
 * finds what the rule of its family ({@link LightRules}) still needs, and places fixtures the way a builder would until
 * nothing is dark:
 *
 * <ol>
 *   <li><b>doors</b>: a lantern on a bracket beside every door (or over it, under the eave, on a post by the path);</li>
 *   <li><b>rooms</b>: lanterns hung from the ceiling, evenly along the middle of the room (a wheel of lanterns, or of
 *   candles in the north, in tall halls), then whatever corners stay dark: candles on its tables, barrels and shelves
 *   (unlit candles already there are lit first), a lantern stood on a chest or a counter, a lantern on a bracket or a
 *   torch on a wall, and only then light hidden under a rug;</li>
 *   <li><b>ground</b>: under porches and galleries lanterns hung from the roof; in yards lanterns on the fence and wall
 *   posts that are there, lanterns on the house walls, then the culture's lamp post, brazier, stone lantern or tiki
 *   torch where they stand out of everyone's way;</li>
 *   <li>and last, for the cells nothing can reach (sealed spaces under roofs, a corner behind a press), vanilla's
 *   invisible light block at the lowest level that clears them.</li>
 * </ol>
 *
 * Nothing is placed in a doorway, on a stair, on a bed, on a path or a job site; nothing with a body stands where anyone
 * walks or bumps their head (lanterns hang above head height); everything hangs from or stands on something.
 */
final class Session {
	static final int MARGIN = LightRules.MARGIN;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	final Draft d;
	final Manner m;
	final LightRules.Family family;
	final Grid g;
	final Spaces sp;
	final LightField lf;
	final List<LightRules.Need> needs;
	/** The light each grid cell needs (0: none). */
	private final byte[] needMin;
	private final byte[] needKind;
	/** Cells kept clear (doorways, the cells before them, job sites, entrances). */
	private final LongOpenHashSet keep = new LongOpenHashSet();
	/** Cells near which nothing with a body stands (round points of interest: a bench's sides, a well's rim). */
	final LongOpenHashSet nearPoi = new LongOpenHashSet();
	/** Local rectangles {x0, z0, x1, z1} left to other plots. */
	private final List<int[]> keepRects = new ArrayList<>();
	/** Columns the draft already builds in (linear works may not leave them). */
	private final @Nullable BitSet columns;
	private final int colW;
	/** Whether the pass may stand lamps in the plot's open ground (not on a square, a furniture plot or a linear work). */
	final boolean yards;
	/** What was placed, by kind (for tests and the preview). */
	final int[] placed = new int[Fixture.Kind.values().length];
	/** Every fixture placed, in order. */
	final List<Fixture> log = new ArrayList<>();
	/** Where the walk starts (entrances, the street row), and the entrances: kept for the next round. */
	private final List<BlockPos> starts;
	private final List<BlockPos> entrances;
	/** The drawing's height range when the grid was read (the grid's box follows it). */
	private final int drawnY0;
	private final int drawnY1;
	/** The height range of the parts this pass placed. */
	private int placedY0 = Integer.MAX_VALUE;
	private int placedY1 = Integer.MIN_VALUE;
	/** Whether a part placed changed how light spreads (the field is then flooded afresh for another round). */
	private boolean spreadChanged;

	Session(Draft d, LightRules.Family family, int x0, int z0, int x1, int z1, @Nullable List<int[]> onlyCells) {
		this(d, family, x0, z0, x1, z1, onlyCells, List.of());
	}

	/** A pass over one room only (for {@link Lighting#room}): its cells at its floor, walked from themselves. */
	static Session forRoom(Draft d, int x0, int z0, int x1, int z1, int floorY) {
		List<int[]> cells = new ArrayList<>();
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				cells.add(new int[]{x, floorY, z});
			}
		}
		return new Session(d, LightRules.family(d.spec().archetype()), x0, z0, x1, z1, cells, cells);
	}

	/** Lights the rooms among the needs (design, then fill), nothing else. */
	void lightRooms() {
		if (!this.needs.isEmpty() && (unmetCount() > 0 || belowComfort())) {
			rooms();
		}
	}

	Session(Draft d, LightRules.Family family, int x0, int z0, int x1, int z1, @Nullable List<int[]> onlyCells, List<int[]> extraStarts) {
		this.d = d;
		this.m = new Manner(d);
		this.family = family;
		this.yards = family == LightRules.Family.BUILDING || family == LightRules.Family.FARM;
		// one pass over the drawing: its height range, the doorways and gates to keep clear, the columns a work builds in
		int[] yr = {Integer.MAX_VALUE, Integer.MIN_VALUE};
		int w = d.frontage();
		int dp = d.depth();
		boolean works = family == LightRules.Family.WORKS;
		BitSet built = works ? new BitSet(w * dp) : null;
		d.forEach((x, y, z, c) -> {
			yr[0] = Math.min(yr[0], y);
			yr[1] = Math.max(yr[1], y);
			BlockState st = c.state();
			Block b = st.getBlock();
			if (b instanceof DoorBlock && st.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
				|| b instanceof FenceGateBlock) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 || dz == 0) {
							this.keep.add(BlockPos.asLong(x + dx, y, z + dz));
							this.keep.add(BlockPos.asLong(x + dx, y + 1, z + dz));
						}
					}
				}
			}
			if (built != null && x >= 0 && z >= 0 && x < w && z < dp && !st.isAir()) {
				built.set(z * w + x);
			}
		});
		if (yr[0] == Integer.MAX_VALUE) {
			yr[0] = 0;
			yr[1] = 0;
		}
		Site site = d.site();
		this.g = new Grid(new Grid.Source() {
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
				return AIR;
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
		}, x0 - MARGIN, Math.min(yr[0], 0) - LightRules.BELOW, z0 - MARGIN, x1 + MARGIN, Math.max(yr[1], 0) + LightRules.ABOVE, z1 + MARGIN);
		List<BlockPos> starts = new ArrayList<>();
		List<BlockPos> entrances = new ArrayList<>();
		for (int[] e : d.entrances()) {
			BlockPos p = new BlockPos(e[0], e[1], e[2]);
			starts.add(p);
			entrances.add(p);
		}
		for (int x = 0; x < w; x++) {
			starts.add(new BlockPos(x, Math.max(site.ground(x, -1), 0), -1));
		}
		for (int[] c : extraStarts) {
			starts.add(new BlockPos(c[0], c[1], c[2]));
		}
		this.starts = starts;
		this.entrances = entrances;
		this.drawnY0 = yr[0];
		this.drawnY1 = yr[1];
		this.sp = new Spaces(this.g, starts);
		this.lf = new LightField(this.g);
		this.needs = needs(onlyCells);
		this.needMin = new byte[this.g.volume()];
		this.needKind = new byte[this.g.volume()];
		indexNeeds();
		// keep doorways (above), the cells either side of them and where folk work clear
		for (int[] e : d.entrances()) {
			for (int dy = -2; dy <= 1; dy++) {
				this.keep.add(BlockPos.asLong(e[0], e[1] + dy, e[2]));
			}
		}
		for (Draft.LocalPoi p : d.pois()) {
			this.keep.add(BlockPos.asLong(p.x(), p.y(), p.z()));
			this.keep.add(BlockPos.asLong(p.x(), p.y() + 1, p.z()));
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 1; dy++) {
						this.nearPoi.add(BlockPos.asLong(p.x() + dx, p.y() + dy, p.z() + dz));
					}
				}
			}
		}
		// the keep rectangles (world, x0,z0,x1,z1 ...), and keep_local for previews
		int[] k = d.params().getIntArray("keep").orElse(new int[0]);
		for (int i = 0; i + 3 < k.length; i += 4) {
			int ax = d.frame().localX(k[i], k[i + 1]);
			int az = d.frame().localZ(k[i], k[i + 1]);
			int bx = d.frame().localX(k[i + 2], k[i + 3]);
			int bz = d.frame().localZ(k[i + 2], k[i + 3]);
			this.keepRects.add(new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)});
		}
		for (String r : d.param("keep_local", "").split("/")) {
			String[] v = r.split(":");
			if (v.length == 4) {
				try {
					this.keepRects.add(new int[]{Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])});
				} catch (NumberFormatException e) {
					// not a rectangle
				}
			}
		}
		if (works) {
			this.colW = w;
			this.columns = built;
		} else {
			this.columns = null;
			this.colW = 0;
		}
	}

	/**
	 * The next round over the same draft: the grid and the light already hold what the last pass placed (it set both as
	 * it went), so only where folk stand and what each cell needs are read again (a bracket or a post can roof a cell or
	 * give a step). The doorways, points of interest, keep rectangles and built columns are the same: fixtures add none.
	 */
	private Session(Session prev) {
		this.d = prev.d;
		this.m = prev.m;
		this.family = prev.family;
		this.yards = prev.yards;
		this.g = prev.g;
		this.keep.addAll(prev.keep);
		this.nearPoi.addAll(prev.nearPoi);
		this.keepRects.addAll(prev.keepRects);
		this.columns = prev.columns;
		this.colW = prev.colW;
		this.starts = prev.starts;
		this.entrances = prev.entrances;
		this.drawnY0 = prev.drawnY0;
		this.drawnY1 = prev.drawnY1;
		this.sp = new Spaces(this.g, this.starts);
		this.lf = prev.lf;
		this.needs = needs(null);
		this.needMin = new byte[this.g.volume()];
		this.needKind = new byte[this.g.volume()];
		indexNeeds();
	}

	/**
	 * The session for the next round: this one's grid and light carried on when reading the draft afresh would give the
	 * same (every part placed inside the drawing's height range, none changing how light spreads), else a fresh read.
	 */
	Session next(int x0, int z0, int x1, int z1) {
		if (this.spreadChanged || this.placedY0 < this.drawnY0 || this.placedY1 > this.drawnY1) {
			return new Session(this.d, this.family, x0, z0, x1, z1, null);
		}
		return new Session(this);
	}

	private List<LightRules.Need> needs(@Nullable List<int[]> onlyCells) {
		List<LightRules.Need> all = LightRules.needs(this.sp, this.entrances, LightRules.Context.of(this.family, this.d.tier(), this.d.maxHeight() - 1));
		if (onlyCells != null) {
			LongOpenHashSet only = new LongOpenHashSet();
			for (int[] c : onlyCells) {
				only.add(BlockPos.asLong(c[0], c[1], c[2]));
			}
			List<LightRules.Need> sub = new ArrayList<>();
			for (LightRules.Need n : all) {
				if (only.contains(BlockPos.asLong(n.x(), n.y(), n.z()))) {
					sub.add(n);
				}
			}
			all = sub;
		}
		return all;
	}

	private void indexNeeds() {
		BitSet asked = new BitSet(this.g.volume());
		for (LightRules.Need n : this.needs) {
			int i = this.g.index(n.x(), n.y(), n.z());
			asked.set(i);
			if (n.min() > this.needMin[i]) {
				this.needMin[i] = (byte) n.min();
				this.needKind[i] = (byte) n.kind().ordinal();
			}
		}
		// every question the pass asks of a fixture is about cells that need light: probes report only those
		this.lf.interest(asked);
	}

	// ============================================================================================ queries

	boolean unmet(int i) {
		return this.needMin[i] > 0 && this.lf.atIndex(i) < this.needMin[i];
	}

	LightRules.Kind kindAt(int i) {
		return LightRules.Kind.values()[this.needKind[i]];
	}

	int unmetCount() {
		int n = 0;
		for (LightRules.Need need : this.needs) {
			if (this.lf.at(need.x(), need.y(), need.z()) < need.min()) {
				n++;
			}
		}
		return n;
	}

	List<LightRules.Need> unmetNeeds() {
		return LightRules.unmet(this.lf, this.needs);
	}

	/** A cell the pass may build in: in the plot (or over the street high enough), empty, not natural ground. */
	boolean free(int x, int y, int z) {
		if (!this.d.allowed(x, y, z) || !this.d.isEmpty(x, y, z) || !this.g.contains(x, y, z) || !this.g.at(x, y, z).isAir()) {
			return false;
		}
		if (this.columns != null && (z < 0 || !this.columns.get(z * this.colW + x))) {
			return false;
		}
		return !this.keep.contains(BlockPos.asLong(x, y, z)) && !kept(x, z);
	}

	/** Columns another plot stands in (the {@code keep} rectangles a plaza or a wall leaves alone). */
	boolean kept(int x, int z) {
		for (int[] r : this.keepRects) {
			if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a body here would be in someone's way although nobody stands in the cell: a neighbour stands at this
	 * height or one below (a step up onto a stair passes the head through it, stepping off a ledge the whole body).
	 */
	boolean swing(int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			if (this.sp.walkable(nx, y, nz) || this.sp.walkable(nx, y - 1, nz)) {
				return true;
			}
		}
		return false;
	}

	/** Feet or head of someone standing there. */
	boolean body(int x, int y, int z) {
		return this.sp.walkable(x, y, z) || this.sp.walkable(x, y - 1, z);
	}

	/** Something solid here would block a step up from below (the head room of a climb). */
	boolean climbRoom(int x, int y, int z) {
		if (!this.sp.walkable(x, y - 2, z)) {
			return false;
		}
		for (Direction dir : Dirs.HORIZONTAL) {
			if (this.sp.walkable(x + Dirs.dx(dir), y - 1, z + Dirs.dz(dir))) {
				return true;
			}
		}
		return false;
	}

	/** A cell a solid fixture part (a bracket, a wheel's arm) may take. */
	boolean solidOk(int x, int y, int z) {
		return free(x, y, z) && !body(x, y, z) && !climbRoom(x, y, z) && !swing(x, y, z);
	}

	/** A cell a hung lantern or a chain may take: above head height, and out of the way of a step or a drop. */
	boolean hungOk(int x, int y, int z) {
		return free(x, y, z) && !body(x, y, z) && !climbRoom(x, y, z) && !swing(x, y, z);
	}

	static boolean sturdyBelow(BlockState s) {
		return !s.isAir() && s.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.DOWN, SupportType.CENTER);
	}

	static boolean sturdyAbove(BlockState s) {
		return !s.isAir() && s.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP, SupportType.CENTER);
	}

	/** A full wall block whose face toward {@code from} carries a bracket or a torch. */
	boolean wallAt(int x, int y, int z, Direction toward) {
		BlockState s = this.g.at(x, y, z);
		if (!this.g.placed(x, y, z) || s.isAir()) {
			return false;
		}
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
			&& s.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, toward, SupportType.FULL) && !(s.getBlock() instanceof LightBlock);
	}

	/** Inside a building (under a roof with walls round), where fixtures are INTERIOR work. */
	StageKind stageAt(int x, int y, int z) {
		for (int dy = 0; dy >= -4; dy--) {
			if (this.sp.walkable(x, y + dy, z)) {
				return this.sp.enclosed(x, y + dy, z) ? StageKind.INTERIOR : StageKind.EXTERIOR;
			}
		}
		return this.sp.roofed(x, y - 1, z) && this.sp.enclosed(x, y - 1, z) ? StageKind.INTERIOR : StageKind.EXTERIOR;
	}

	// ============================================================================================ scoring

	private double gainSat;
	private double gainPart;
	private boolean targetHit;
	private int target = -1;
	private int targetMin;
	private byte[] multi;
	private final IntArrayList multiTouched = new IntArrayList();

	/** What the fixture's emitters would add: newly lit needs, and progress toward the rest (of the kinds asked). */
	double gain(Fixture f, int kinds) {
		return evaluate(f, kinds, -1, 0);
	}

	/**
	 * Probes a fixture once: the gain over the needs of the given kinds, and whether it lights the target cell (grid
	 * index, or -1) to {@code min} ({@link #targetHit}).
	 */
	double evaluate(Fixture f, int kinds, int targetIndex, int min) {
		this.gainSat = 0;
		this.gainPart = 0;
		this.targetHit = false;
		this.target = targetIndex;
		this.targetMin = min;
		if (f.emitters.size() == 1) {
			int[] e = f.emitters.get(0);
			this.lf.probe(e[0], e[1], e[2], e[3], (i, l) -> score(i, l, kinds));
		} else if (!f.emitters.isEmpty()) {
			// several emitters: the brightest per cell
			if (this.multi == null) {
				this.multi = new byte[this.g.volume()];
			}
			for (int[] e : f.emitters) {
				this.lf.probe(e[0], e[1], e[2], e[3], (i, l) -> {
					if (this.multi[i] == 0) {
						this.multiTouched.add(i);
					}
					if (l > this.multi[i]) {
						this.multi[i] = (byte) l;
					}
				});
			}
			for (int t = 0; t < this.multiTouched.size(); t++) {
				int i = this.multiTouched.getInt(t);
				score(i, this.multi[i], kinds);
				this.multi[i] = 0;
			}
			this.multiTouched.clear();
		}
		this.target = -1;
		return this.gainSat + 0.12 * this.gainPart;
	}

	private void score(int i, int l, int kinds) {
		if (i == this.target && l >= this.targetMin) {
			this.targetHit = true;
		}
		int min = this.needMin[i];
		if (min == 0 || (kinds & 1 << this.needKind[i]) == 0) {
			return;
		}
		int cur = this.lf.atIndex(i);
		if (cur >= min || l <= cur) {
			return;
		}
		if (l >= min) {
			this.gainSat++;
		} else {
			this.gainPart += (double) (l - cur) / min;
		}
	}

	boolean targetHit() {
		return this.targetHit;
	}

	/** Whether any emitter of the fixture is near enough (by steps) to light a cell to {@code min} at all. */
	static boolean within(Fixture f, int x, int y, int z, int min) {
		for (int[] e : f.emitters) {
			if (Math.abs(e[0] - x) + Math.abs(e[1] - y) + Math.abs(e[2] - z) <= e[3] - min) {
				return true;
			}
		}
		return false;
	}

	/** How many needs of the given kinds a fixture would meet outright. */
	int satisfies(Fixture f, int kinds) {
		gain(f, kinds);
		return (int) this.gainSat;
	}

	// ============================================================================================ placing

	/** Places a fixture in the draft and the grid, and spreads its light. */
	void place(Fixture f) {
		for (Fixture.Part p : f.parts) {
			StageKind stage = p.stage() != null ? p.stage() : stageAt(p.x(), p.y(), p.z());
			try (var s = this.d.stage(stage)) {
				this.d.set(p.x(), p.y(), p.z(), p.state());
			}
			if (!LightField.sameForLight(this.g.at(p.x(), p.y(), p.z()), p.state())) {
				// a part that changes how light spreads (a slab cap, a stand): what single emitters give must be read again
				this.lf.forget();
				this.spreadChanged = true;
			}
			this.placedY0 = Math.min(this.placedY0, p.y());
			this.placedY1 = Math.max(this.placedY1, p.y());
			if (!this.d.allowed(p.x(), p.y(), p.z())) {
				// the draft drops what falls outside the plot, the grid does not: read the draft afresh next round
				this.spreadChanged = true;
			}
			this.g.set(p.x(), p.y(), p.z(), p.state());
			this.lf.changed(p.x(), p.y(), p.z());
		}
		for (int[] e : f.emitters) {
			this.lf.add(e[0], e[1], e[2], e[3]);
		}
		this.placed[f.kind.ordinal()]++;
		this.log.add(f);
	}

	static int bit(LightRules.Kind... kinds) {
		int b = 0;
		for (LightRules.Kind k : kinds) {
			b |= 1 << k.ordinal();
		}
		return b;
	}

	// ============================================================================================ the pass

	/** Time spent per phase (setup, doors, rooms, ground, last), for the timing survey. */
	static final long[] TIME = new long[5];

	void run() {
		trace("start");
		if (this.needs.isEmpty() || unmetCount() == 0 && !belowComfort()) {
			return;
		}
		long t0 = System.nanoTime();
		doors();
		long t1 = System.nanoTime();
		rooms();
		trace("rooms");
		long t2 = System.nanoTime();
		ground();
		trace("ground");
		long t3 = System.nanoTime();
		last();
		trace("last");
		long t4 = System.nanoTime();
		TIME[1] += t1 - t0;
		TIME[2] += t2 - t1;
		TIME[3] += t3 - t2;
		TIME[4] += t4 - t3;
	}

	/** Debugging: a local cell to follow through the pass ({@link AutoLighter#trace}). */
	private void trace(String phase) {
		int[] c = AutoLighter.trace;
		if (c == null) {
			return;
		}
		boolean in = this.g.contains(c[0], c[1], c[2]);
		int i = in ? this.g.index(c[0], c[1], c[2]) : -1;
		System.out.println("TRACE " + phase + " cell " + c[0] + "," + c[1] + "," + c[2] + " in " + in + (in ? " light " + this.lf.atIndex(i) + " need "
			+ this.needMin[i] + " kind " + this.needKind[i] + " walkable " + this.sp.walkable(c[0], c[1], c[2]) + " reached " + this.sp.reached(c[0], c[1], c[2])
			+ " roofed " + this.sp.roofed(c[0], c[1], c[2]) + " enclosed " + this.sp.enclosed(c[0], c[1], c[2]) + " below " + this.g.at(c[0], c[1] - 1, c[2]) : "")
			+ " needs " + this.needs.size() + " placed " + this.log.size());
	}

	// ---------------------------------------------------------------------------------------------- doors

	private void doors() {
		int kinds = bit(LightRules.Kind.DOOR);
		for (LightRules.Need n : this.needs) {
			if (n.kind() != LightRules.Kind.DOOR || this.lf.at(n.x(), n.y(), n.z()) >= n.min()) {
				continue;
			}
			List<Fixture> cands = new ArrayList<>();
			Fixtures.doorLamps(this, n.x(), n.y(), n.z(), cands);
			Fixture best = best(cands, kinds, true);
			if (best != null) {
				place(best);
			}
		}
	}

	/** The candidate with the most gain for its cost (ties: the earlier, as the generator ordered them by taste). */
	@Nullable Fixture best(List<Fixture> cands, int kinds, boolean mustSatisfy) {
		Fixture best = null;
		double bestScore = 0;
		for (Fixture f : cands) {
			double gain = gain(f, kinds);
			if (mustSatisfy && this.gainSat == 0) {
				continue;
			}
			double score = gain / f.cost + f.bonus;
			if (gain > 0 && score > bestScore + 1e-9) {
				best = f;
				bestScore = score;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------------------------------------- rooms

	/** A room: room-kind need cells at one standing level, joined side to side. */
	static final class Room {
		final IntArrayList cells = new IntArrayList();
		int y;
		int ax = Integer.MAX_VALUE;
		int az = Integer.MAX_VALUE;
		int bx = Integer.MIN_VALUE;
		int bz = Integer.MIN_VALUE;
		int height;

		int width() {
			return this.bx - this.ax + 1;
		}

		int length() {
			return this.bz - this.az + 1;
		}
	}

	List<Room> roomsOf() {
		List<Room> out = new ArrayList<>();
		IntOpenHashSet seen = new IntOpenHashSet();
		int roomKind = LightRules.Kind.ROOM.ordinal();
		for (LightRules.Need n : this.needs) {
			if (n.kind() != LightRules.Kind.ROOM) {
				continue;
			}
			int start = this.g.index(n.x(), n.y(), n.z());
			if (seen.contains(start)) {
				continue;
			}
			Room r = new Room();
			r.y = n.y();
			IntArrayFIFOQueue q = new IntArrayFIFOQueue();
			q.enqueue(start);
			seen.add(start);
			IntArrayList heights = new IntArrayList();
			while (!q.isEmpty()) {
				int i = q.dequeueInt();
				r.cells.add(i);
				int x = this.g.x0 + i % this.g.sx;
				int z = this.g.z0 + (i / this.g.sx) % this.g.sz;
				r.ax = Math.min(r.ax, x);
				r.az = Math.min(r.az, z);
				r.bx = Math.max(r.bx, x);
				r.bz = Math.max(r.bz, z);
				int roof = this.sp.roofY(x, r.y, z);
				if (roof != Integer.MAX_VALUE) {
					heights.add(roof - r.y);
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (!this.g.contains(nx, r.y, nz)) {
						continue;
					}
					int ni = this.g.index(nx, r.y, nz);
					if (this.needMin[ni] > 0 && this.needKind[ni] == roomKind && seen.add(ni)) {
						q.enqueue(ni);
					}
				}
			}
			heights.sort(null);
			r.height = heights.isEmpty() ? 3 : heights.getInt(heights.size() / 2);
			out.add(r);
		}
		// light the big rooms first: their fixtures spill into the small ones round them
		out.sort((a, b) -> Integer.compare(b.cells.size(), a.cells.size()));
		return out;
	}

	private boolean anyUnmet(Room r) {
		for (int k = 0; k < r.cells.size(); k++) {
			if (unmet(r.cells.getInt(k))) {
				return true;
			}
		}
		return false;
	}

	private int unmetIn(Room r) {
		int n = 0;
		for (int k = 0; k < r.cells.size(); k++) {
			if (unmet(r.cells.getInt(k))) {
				n++;
			}
		}
		return n;
	}

	/**
	 * The light the room phase gives the rooms it lights, where it can: the user's rule is interiors <b>very</b> well lit.
	 * The check asks {@link LightRules#ROOM}, but a room at 8 still reads dim at night (on the game's light curve 8 is
	 * about two thirds of full brightness, 10 four fifths), so the room phase aims at 10 with what folk hang and stand
	 * in a room (lanterns, candles, lamps, sconces); light under a rug and the hidden block stay for what the rule
	 * itself needs.
	 */
	static final int COMFORT = 10;

	private void rooms() {
		int kinds = bit(LightRules.Kind.ROOM, LightRules.Kind.DARK);
		IntArrayList raised = comfort();
		try {
			for (Room r : roomsOf()) {
				if (!anyUnmet(r)) {
					continue;
				}
				// a room its builder lit to the rule keeps its lights: only its dim corners are filled, unless most of it is dim
				if (r.cells.size() >= 4 && r.height >= 3 && (below(r, LightRules.ROOM) > 0 || below(r, COMFORT) * 3 > r.cells.size())) {
					design(r);
				}
				fill(r, kinds);
			}
		} finally {
			for (int k = 0; k < raised.size(); k++) {
				this.needMin[raised.getInt(k)] = (byte) LightRules.ROOM;
			}
		}
	}

	/** How many of a room's cells stand below a level of light. */
	private int below(Room r, int level) {
		int n = 0;
		for (int k = 0; k < r.cells.size(); k++) {
			if (this.lf.atIndex(r.cells.getInt(k)) < level) {
				n++;
			}
		}
		return n;
	}

	/** Raises the rooms' cells to {@link #COMFORT} for the room phase; returns them, to be put back after. */
	private IntArrayList comfort() {
		IntArrayList raised = new IntArrayList();
		for (LightRules.Need n : this.needs) {
			if (n.kind() != LightRules.Kind.ROOM) {
				continue;
			}
			int i = this.g.index(n.x(), n.y(), n.z());
			if (this.needKind[i] == LightRules.Kind.ROOM.ordinal() && this.needMin[i] < COMFORT) {
				this.needMin[i] = (byte) COMFORT;
				raised.add(i);
			}
		}
		return raised;
	}

	/** Whether a room cell stands below {@link #COMFORT} (the pass runs for comfort too, where nothing is dark). */
	private boolean belowComfort() {
		for (LightRules.Need n : this.needs) {
			if (n.kind() == LightRules.Kind.ROOM && this.lf.at(n.x(), n.y(), n.z()) < COMFORT) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The room's main light, by design: n × k ceiling lanterns (or wheels in tall halls) spaced evenly over the room,
	 * along its middle, the fewest that light it (to nine in ten of its cells; the corners are the fill's).
	 */
	private void design(Room r) {
		int roomBit = bit(LightRules.Kind.ROOM);
		// a small room is lit by candles where it has a table, a barrel, a counter or a shelf to stand them on
		if (r.cells.size() <= 24 && candles(r, roomBit)) {
			return;
		}
		boolean longX = r.width() >= r.length();
		int along = longX ? r.width() : r.length();
		int across = longX ? r.length() : r.width();
		boolean wheel = r.height >= 7 && across >= 5 && along >= 5 && !this.m.rows;
		int hang = Fixtures.hangHeight(r.height);
		int before = unmetIn(r);
		List<Fixture> chosen = null;
		int chosenLit = -1;
		// a lantern lights about a diamond of radius 7 - hang to 8 (a wheel one more): start near the count that could do
		int reach = Math.max(2, 7 - hang + (wheel ? 1 : 0));
		int area = 2 * reach * reach + 2 * reach + 1;
		int start = Math.clamp(r.cells.size() / area, 1, 12);
		outer:
		for (int total = start; total <= Math.max(12, start + 4); total++) {
			for (int k = 1; k <= Math.min(total, 4); k++) {
				if (total % k != 0) {
					continue;
				}
				int n = total / k;
				if (n > along || k > across) {
					continue;
				}
				// rows across only when the room is wide for them
				if (k > 1 && across < 4 * k - 1) {
					continue;
				}
				List<Fixture> set = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					for (int j = 0; j < k; j++) {
						double fa = (i + 0.5) * along / n - 0.5;
						double fc = (j + 0.5) * across / k - 0.5;
						double fx = longX ? r.ax + fa : r.ax + fc;
						double fz = longX ? r.az + fc : r.az + fa;
						Fixture f = Fixtures.ceilingNear(this, r, fx, fz, hang, wheel);
						if (f != null && !overlaps(set, f)) {
							set.add(f);
						}
					}
				}
				if (set.isEmpty()) {
					continue;
				}
				int lit = litBy(set, r, roomBit);
				if (lit > chosenLit) {
					chosen = set;
					chosenLit = lit;
				}
				if (lit >= Math.ceil(before * 0.9)) {
					break outer;
				}
			}
		}
		if (chosen != null && chosenLit > 0) {
			for (Fixture f : chosen) {
				place(f);
			}
		}
	}

	/** Lights a small room with one or two groups of candles on its furniture, if that is enough; true if done. */
	private boolean candles(Room r, int roomBit) {
		List<Fixture> spots = new ArrayList<>();
		Fixtures.candleSpots(this, r, spots);
		if (spots.isEmpty()) {
			return false;
		}
		int before = unmetIn(r);
		int enough = (int) Math.ceil(before * 0.9);
		Fixture one = null;
		int oneLit = -1;
		for (Fixture f : spots) {
			int lit = litBy(List.of(f), r, roomBit);
			if (lit > oneLit || lit == oneLit && one != null && f.cost < one.cost) {
				one = f;
				oneLit = lit;
			}
		}
		if (oneLit >= enough && oneLit > 0) {
			place(one);
			return true;
		}
		List<Fixture> pair = null;
		int pairLit = -1;
		for (int i = 0; i < spots.size(); i++) {
			for (int j = i + 1; j < spots.size(); j++) {
				Fixture a = spots.get(i);
				Fixture b = spots.get(j);
				if (a.parts.get(0).x() == b.parts.get(0).x() && a.parts.get(0).z() == b.parts.get(0).z()) {
					continue;
				}
				int lit = litBy(List.of(a, b), r, roomBit);
				if (lit > pairLit) {
					pair = List.of(a, b);
					pairLit = lit;
				}
			}
		}
		if (pair != null && pairLit >= enough && pairLit > 0) {
			for (Fixture f : pair) {
				place(f);
			}
			return true;
		}
		return false;
	}

	private static boolean overlaps(List<Fixture> set, Fixture f) {
		for (Fixture o : set) {
			for (Fixture.Part a : o.parts) {
				for (Fixture.Part b : f.parts) {
					if (Math.abs(a.x() - b.x()) <= 1 && Math.abs(a.z() - b.z()) <= 1 && Math.abs(a.y() - b.y()) <= 1) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** How many of the room's unmet cells a set of fixtures would light. */
	private int litBy(List<Fixture> set, Room r, int kinds) {
		// the brightest any of the set gives per cell, down to the least light a room cell needs (every cell of a room
		// needs the room's light, so nothing dimmer can light one)
		if (this.multi == null) {
			this.multi = new byte[this.g.volume()];
		}
		byte[] best = this.multi;
		IntArrayList touched = this.multiTouched;
		for (Fixture f : set) {
			for (int[] e : f.emitters) {
				this.lf.probe(e[0], e[1], e[2], e[3], LightRules.ROOM, (i, l) -> {
					if (best[i] == 0) {
						touched.add(i);
					}
					if (l > best[i]) {
						best[i] = (byte) l;
					}
				});
			}
		}
		int lit = 0;
		for (int k = 0; k < r.cells.size(); k++) {
			int i = r.cells.getInt(k);
			if (unmet(i) && best[i] >= this.needMin[i]) {
				lit++;
			}
		}
		for (int t = 0; t < touched.size(); t++) {
			best[touched.getInt(t)] = 0;
		}
		touched.clear();
		return lit;
	}

	/**
	 * The rest of a room's dark corners, darkest first: for each, the fitting near it (candles, a lamp on furniture, a
	 * sconce, another ceiling lantern, light under a rug) that lights it and the most round it for its cost.
	 */
	private void fill(Room r, int kinds) {
		IntOpenHashSet given = new IntOpenHashSet();
		for (int round = 0; round < 48; round++) {
			int worst = -1;
			int worstLevel = 99;
			for (int k = 0; k < r.cells.size(); k++) {
				int i = r.cells.getInt(k);
				if (unmet(i) && !given.contains(i) && this.lf.atIndex(i) < worstLevel) {
					worst = i;
					worstLevel = this.lf.atIndex(i);
				}
			}
			if (worst < 0) {
				return;
			}
			int wx = this.g.x0 + worst % this.g.sx;
			int wz = this.g.z0 + (worst / this.g.sx) % this.g.sz;
			List<Fixture> cands = new ArrayList<>();
			Fixtures.roomFill(this, r, wx, wz, 5, cands);
			Fixture best = null;
			double bestScore = 0;
			int min = this.needMin[worst];
			// a corner the rule is already met in is lit for comfort only: by what folk hang and stand, not a glow or a hidden lamp
			boolean comfortOnly = worstLevel >= LightRules.ROOM;
			for (Fixture f : cands) {
				if (!within(f, wx, r.y, wz, min) || comfortOnly && (f.kind == Fixture.Kind.GLOW || f.kind == Fixture.Kind.HIDDEN)) {
					continue;
				}
				double gain = evaluate(f, kinds, worst, min);
				if (!this.targetHit) {
					continue;
				}
				double score = gain / f.cost + f.bonus;
				if (score > bestScore + 1e-9) {
					best = f;
					bestScore = score;
				}
			}
			if (best == null) {
				given.add(worst);
				continue;
			}
			place(best);
		}
	}

	// ---------------------------------------------------------------------------------------------- ground

	private void ground() {
		int kinds = bit(LightRules.Kind.DARK, LightRules.Kind.ROOM, LightRules.Kind.DOOR);
		for (int round = 0; round < 64; round++) {
			// the darkest reached cell first: light it and as much round it as one fixture can
			LightRules.Need worst = null;
			int worstLevel = 99;
			for (LightRules.Need n : this.needs) {
				if (n.kind() != LightRules.Kind.DARK || !this.sp.reached(n.x(), n.y(), n.z())) {
					continue;
				}
				int l = this.lf.at(n.x(), n.y(), n.z());
				if (l < n.min() && l < worstLevel) {
					worst = n;
					worstLevel = l;
				}
			}
			if (worst == null) {
				return;
			}
			List<Fixture> cands = new ArrayList<>();
			Fixtures.groundNear(this, worst.x(), worst.y(), worst.z(), cands);
			int wi = this.g.index(worst.x(), worst.y(), worst.z());
			Fixture best = null;
			double bestScore = 0;
			for (Fixture f : cands) {
				if (!within(f, worst.x(), worst.y(), worst.z(), worst.min())) {
					continue;
				}
				double gain = evaluate(f, kinds, wi, worst.min());
				if (!this.targetHit) {
					continue;
				}
				double score = gain / f.cost + f.bonus;
				if (score > bestScore + 1e-9) {
					best = f;
					bestScore = score;
				}
			}
			if (best == null) {
				// nothing a builder would put here reaches it: the last resort
				markSkipped(worst);
				continue;
			}
			place(best);
		}
	}


	private void markSkipped(LightRules.Need n) {
		// a cell given up on is lit by the last resort; take it out of the ground pass's search
		Fixture f = Fixtures.hidden(this, n.x(), n.y(), n.z(), n.min());
		if (f != null) {
			f.why = "ground";
			place(f);
		}
	}

	/** Whether a fixture alone would light one cell to {@code min}. */
	boolean satisfiesTarget(Fixture f, int x, int y, int z, int min) {
		if (!this.g.contains(x, y, z) || !within(f, x, y, z, min)) {
			return false;
		}
		evaluate(f, 0, this.g.index(x, y, z), min);
		return this.targetHit;
	}

	// ---------------------------------------------------------------------------------------------- last resort

	private void last() {
		for (int round = 0; round < 400; round++) {
			LightRules.Need worst = null;
			for (LightRules.Need n : this.needs) {
				if (this.lf.at(n.x(), n.y(), n.z()) < n.min()) {
					worst = n;
					break;
				}
			}
			if (worst == null) {
				return;
			}
			Fixture f = Fixtures.hidden(this, worst.x(), worst.y(), worst.z(), worst.min());
			if (f != null) {
				f.why = worst.kind() == LightRules.Kind.DARK && !this.sp.reached(worst.x(), worst.y(), worst.z()) ? "pocket" : worst.kind().label();
			} else {
				// no air beside it to hide a light in (the foot of a ladder in a shaft): a lit recess in its floor
				f = Fixtures.recess(this, worst.x(), worst.y(), worst.z(), worst.min());
				if (f != null) {
					f.why = "recess";
				}
			}
			if (f == null) {
				// nowhere to hide a light next to it: give up on this cell (the check will say so)
				this.needMin[this.g.index(worst.x(), worst.y(), worst.z())] = 0;
				this.needs.remove(worst);
				continue;
			}
			place(f);
		}
	}

	// ============================================================================================ furniture

	/** A surface at standing level y a candle or a lantern can stand on (a table, a barrel, a counter, a shelf). */
	@Nullable String surface(int x, int y, int z) {
		BlockState s = this.g.at(x, y, z);
		Block b = s.getBlock();
		if (!this.g.placed(x, y, z)) {
			return null;
		}
		if (b instanceof FenceBlock) {
			return "table";
		}
		if (b instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.TOP) {
			return "table";
		}
		if (b instanceof TrapDoorBlock && !s.getValue(TrapDoorBlock.OPEN) && s.getValue(TrapDoorBlock.HALF) == Half.TOP) {
			return "counter";
		}
		if (b instanceof BarrelBlock) {
			return "barrel";
		}
		if (b == Blocks.BOOKSHELF || b == Blocks.CHISELED_BOOKSHELF) {
			return "shelf";
		}
		return null;
	}

	static boolean isPlate(BlockState s) {
		return s.getBlock() instanceof BasePressurePlateBlock;
	}

	static boolean unlitCandle(BlockState s) {
		return s.getBlock() instanceof CandleBlock && !s.getValue(BlockStateProperties.LIT);
	}

	static boolean carpet(BlockState s) {
		return s.getBlock() instanceof CarpetBlock;
	}

	static boolean fenceOrWall(BlockState s) {
		return s.getBlock() instanceof FenceBlock || s.getBlock() instanceof WallBlock;
	}

	Block cloth(int x, int y, int z) {
		Block c = this.d.mat(Role.CLOTH).block(dev.voidmc.hearth.build.style.Form.CARPET, x, y, z);
		return c == null ? Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED) : c;
	}

	static BlockState lit(BlockState candle) {
		return candle.trySetValue(BlockStateProperties.LIT, true);
	}

	static BlockState candles(Block b, int n) {
		return States.of(b).trySetValue(BlockStateProperties.CANDLES, Math.clamp(n, 1, 4)).trySetValue(BlockStateProperties.LIT, true);
	}
}
