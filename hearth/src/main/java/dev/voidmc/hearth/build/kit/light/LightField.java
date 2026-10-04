package dev.voidmc.hearth.build.kit.light;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Block light over a {@link Grid}, computed by the rules of 26.3's light engine
 * ({@code net.minecraft.world.level.lighting.BlockLightEngine}): every block emitting light ({@link
 * BlockState#getLightEmission()}) holds its emission; a neighbour gets the level less its opacity ({@code
 * max(1, getLightDampening())}: 15 for an opaque cube, 1 for air, glass, a door or a lantern), unless the faces between
 * them occlude ({@link LightEngine#getOcclusionShape}, which only stairs, slabs, snow, paths, farmland and a few others
 * use); a cell at 1 spreads no further. The result is the fixed point the game reaches, so a level read here is the
 * level a player sees in the world (F3) once the building stands, before daylight and other buildings are counted.
 *
 * <p>Adding an emitter only ever raises light (fixtures do not dim anything: lanterns, chains, candles, carpets and
 * trapdoors pass light as air does), so {@link #add} floods just from the new source.
 */
public final class LightField {
	private static final Direction[] DIRS = Direction.values();
	private final Grid g;
	private final byte[] level;
	private final IntArrayList[] queue = new IntArrayList[16];
	private final int[][] steps = new int[6][];
	private final Direction[] labels = new Direction[6];
	private final int[] offsets = new int[6];
	private byte[] scratch;
	private final IntArrayList touched = new IntArrayList();
	private final IntArrayList[] probeQueue = new IntArrayList[16];
	/**
	 * Per cell, read once from the grid: the light lost stepping into it ({@code max(1, dampening)}), whether its shape can
	 * stop light at a face (stairs, slabs: the only blocks the occlusion test is asked of), and the faces that lead out of
	 * the grid (bit k: {@link #DIRS}[k]).
	 */
	private byte[] loss;
	private boolean[] shaped;
	private byte[] edge;

	public LightField(Grid g) {
		this.g = g;
		this.level = new byte[g.volume()];
		for (int i = 0; i < 16; i++) {
			this.queue[i] = new IntArrayList();
		}
		for (int k = 0; k < 6; k++) {
			Direction d = DIRS[k];
			int[] s = g.step(d);
			this.steps[k] = s;
			this.labels[k] = d;
			this.offsets[k] = (s[1] * g.sz + s[2]) * g.sx + s[0];
		}
		cells();
		for (int y = 0; y < g.sy; y++) {
			for (int z = 0; z < g.sz; z++) {
				for (int x = 0; x < g.sx; x++) {
					int i = (y * g.sz + z) * g.sx + x;
					int e = g.states[i].getLightEmission();
					if (e > this.level[i]) {
						this.level[i] = (byte) e;
						this.queue[e].add(i);
					}
				}
			}
		}
		flood();
	}

	/**
	 * Sky light by the same spreading rule (for night renders): 15 straight down through blocks that let the sky through
	 * ({@link BlockState#propagatesSkylightDown()}) from the top of the grid, then spread sideways and down into porches,
	 * doorways and windows losing a level a block.
	 */
	public static LightField sky(Grid g) {
		LightField f = new LightField(g, new byte[g.volume()]);
		for (int z = 0; z < g.sz; z++) {
			for (int x = 0; x < g.sx; x++) {
				for (int y = g.sy - 1; y >= 0; y--) {
					int i = (y * g.sz + z) * g.sx + x;
					BlockState s = g.states[i];
					if (!s.propagatesSkylightDown() && !s.isAir()) {
						break;
					}
					f.level[i] = 15;
					f.queue[15].add(i);
				}
			}
		}
		f.flood();
		return f;
	}

	/** The block light at a position (0 outside the grid). */
	public int at(int x, int y, int z) {
		return this.g.contains(x, y, z) ? this.level[this.g.index(x, y, z)] : 0;
	}

	public Grid grid() {
		return this.g;
	}

	/**
	 * Adds an emitter of the given level at a position whose block in the grid has already been changed to it, and
	 * spreads its light.
	 */
	public void add(int x, int y, int z, int emission) {
		if (!this.g.contains(x, y, z) || emission <= 0) {
			return;
		}
		int i = this.g.index(x, y, z);
		if (emission > this.level[i]) {
			this.level[i] = (byte) emission;
			this.queue[emission].add(i);
			flood();
		}
	}

	/** The light at a grid index. */
	int atIndex(int i) {
		return this.level[i];
	}

	/** Receives the cells a probe reaches. */
	@FunctionalInterface
	public interface Visitor {
		void visit(int index, int level);
	}

	/**
	 * The light one more emitter would give on its own (without what is already lit), cell by cell: what a fixture adds
	 * is the larger of this and the light already there. The grid is read as it is; the emitter's own cell may still be
	 * air (a lantern or a chain passes light as air does).
	 */
	public void probe(int x, int y, int z, int emission, Visitor v) {
		if (!this.g.contains(x, y, z) || emission <= 0) {
			return;
		}
		int[] cells = probed(x, y, z, emission);
		for (int c : cells) {
			v.visit(c >>> 4, c & 15);
		}
	}

	/**
	 * The cells one more emitter would light on its own, packed {@code index << 4 | level}, in the order the flood reaches
	 * them. Remembered per emitter (position and level) until {@link #forget}: the auto-lighter tries the same lanterns
	 * over and over while it chooses, and what one emitter alone gives depends only on the blocks, which its fixtures do
	 * not change (lanterns, chains, candles and brackets pass light as air does; a part that does not tells the field to
	 * forget).
	 */
	int[] probed(int x, int y, int z, int emission) {
		int start = this.g.index(x, y, z);
		long key = (long) start << 4 | emission;
		int[] cells = this.probes.get(key);
		if (cells == null) {
			if (this.probes.size() >= PROBES_KEPT) {
				this.probes.clear();
			}
			cells = flood(start, emission, 1);
			this.probes.put(key, cells);
		}
		return cells;
	}

	/**
	 * Limits what probes report to the cells of interest (the cells the rule asks light of; null: every cell): the rest
	 * is flooded through but neither kept nor visited, so a question asked of a lantern costs its few needs, not the
	 * thousands of cells of air it lights. Forgets the probes remembered so far.
	 */
	void interest(java.util.@org.jspecify.annotations.Nullable BitSet cells) {
		this.interest = cells;
		this.probes.clear();
	}

	private java.util.@org.jspecify.annotations.Nullable BitSet interest;

	/**
	 * {@link #probe}, but only the cells lit to at least {@code floor} (the flood stops where it falls below): what a
	 * question about the rooms (8) needs, at a fraction of the cost of the whole reach.
	 */
	public void probe(int x, int y, int z, int emission, int floor, Visitor v) {
		if (!this.g.contains(x, y, z) || emission <= 0 || emission < floor) {
			return;
		}
		int[] cells = floor <= 1 ? probed(x, y, z, emission) : flood(this.g.index(x, y, z), emission, floor);
		for (int c : cells) {
			if ((c & 15) >= floor) {
				v.visit(c >>> 4, c & 15);
			}
		}
	}

	/** Forgets the remembered probes (a block that changes how light spreads has been placed). */
	void forget() {
		this.probes.clear();
	}

	private static final int PROBES_KEPT = 6000;
	private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> probes = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

	/** The flood of one emitter alone from a grid index, down to {@code floor}: packed cells in the order reached. */
	private int[] flood(int start, int emission, int floor) {
		if (this.scratch == null) {
			this.scratch = new byte[this.level.length];
			for (int i = 0; i < 16; i++) {
				this.probeQueue[i] = new IntArrayList();
			}
		}
		Grid g = this.g;
		byte[] lv = this.scratch;
		byte[] loss = this.loss;
		boolean[] shaped = this.shaped;
		byte[] edge = this.edge;
		int[] offsets = this.offsets;
		int keep = Math.max(1, floor);
		lv[start] = (byte) emission;
		this.touched.add(start);
		this.probeQueue[emission].add(start);
		for (int l = emission; l >= 2 && l > keep; l--) {
			IntArrayList q = this.probeQueue[l];
			for (int qi = 0; qi < q.size(); qi++) {
				int i = q.getInt(qi);
				if (lv[i] != l) {
					continue;
				}
				// the emitter's own cell passes light as air does (a lantern or a chain may hang there)
				boolean fromShaped = i != start && shaped[i];
				int out = edge[i];
				for (int k = 0; k < 6; k++) {
					if ((out & 1 << k) != 0) {
						continue;
					}
					int n = i + offsets[k];
					if (l - 1 <= lv[n]) {
						continue;
					}
					int nl = l - loss[n];
					if (nl <= lv[n] || nl < keep) {
						continue;
					}
					if ((fromShaped || shaped[n]) && occludes(i == start ? Blocks.AIR.defaultBlockState() : g.states[i], g.states[n], this.labels[k])) {
						continue;
					}
					if (lv[n] == 0) {
						this.touched.add(n);
					}
					lv[n] = (byte) nl;
					if (nl > 1) {
						this.probeQueue[nl].add(n);
					}
				}
			}
			q.clear();
		}
		for (int l = 0; l < 16; l++) {
			this.probeQueue[l].clear();
		}
		java.util.BitSet keepOnly = this.interest;
		int[] out = new int[this.touched.size()];
		int n = 0;
		for (int t = 0; t < this.touched.size(); t++) {
			int i = this.touched.getInt(t);
			if (keepOnly == null || keepOnly.get(i)) {
				out[n++] = i << 4 | lv[i];
			}
			lv[i] = 0;
		}
		this.touched.clear();
		return n == out.length ? out : java.util.Arrays.copyOf(out, n);
	}

	/** Whether putting {@code to} where {@code from} stood leaves the way light spreads through the cell as it was. */
	static boolean sameForLight(BlockState from, BlockState to) {
		if (from == to) {
			return true;
		}
		return Math.max(1, from.getLightDampening()) == Math.max(1, to.getLightDampening()) && empty(from) && empty(to);
	}

	/** A copy to try fixtures on. */
	public LightField copy(Grid onto) {
		return new LightField(onto, this.level);
	}

	private LightField(Grid g, byte[] levels) {
		this.g = g;
		this.level = levels.clone();
		for (int i = 0; i < 16; i++) {
			this.queue[i] = new IntArrayList();
		}
		for (int k = 0; k < 6; k++) {
			Direction d = DIRS[k];
			int[] s = g.step(d);
			this.steps[k] = s;
			this.labels[k] = d;
			this.offsets[k] = (s[1] * g.sz + s[2]) * g.sx + s[0];
		}
		cells();
	}

	/** Reads what the flood needs of every cell (see {@link #loss}). */
	private void cells() {
		Grid g = this.g;
		int n = g.volume();
		this.loss = new byte[n];
		this.shaped = new boolean[n];
		this.edge = new byte[n];
		for (int y = 0; y < g.sy; y++) {
			for (int z = 0; z < g.sz; z++) {
				for (int x = 0; x < g.sx; x++) {
					int i = (y * g.sz + z) * g.sx + x;
					int e = 0;
					for (int k = 0; k < 6; k++) {
						int[] s = this.steps[k];
						int nx = x + s[0];
						int ny = y + s[1];
						int nz = z + s[2];
						if (nx < 0 || ny < 0 || nz < 0 || nx >= g.sx || ny >= g.sy || nz >= g.sz) {
							e |= 1 << k;
						}
					}
					this.edge[i] = (byte) e;
					cell(i);
				}
			}
		}
	}

	private void cell(int i) {
		BlockState s = this.g.states[i];
		this.loss[i] = (byte) Math.max(1, s.getLightDampening());
		this.shaped[i] = !empty(s);
	}

	/** The block at a position has changed in the grid (a fixture's part): reads it again. */
	void changed(int x, int y, int z) {
		if (this.g.contains(x, y, z)) {
			cell(this.g.index(x, y, z));
		}
	}

	private void flood() {
		Grid g = this.g;
		byte[] level = this.level;
		byte[] loss = this.loss;
		boolean[] shaped = this.shaped;
		byte[] edge = this.edge;
		int[] offsets = this.offsets;
		for (int l = 15; l >= 2; l--) {
			IntArrayList q = this.queue[l];
			for (int qi = 0; qi < q.size(); qi++) {
				int i = q.getInt(qi);
				if (level[i] != l) {
					continue;
				}
				boolean fromShaped = shaped[i];
				int out = edge[i];
				for (int k = 0; k < 6; k++) {
					if ((out & 1 << k) != 0) {
						continue;
					}
					int n = i + offsets[k];
					if (l - 1 <= level[n]) {
						continue;
					}
					int nl = l - loss[n];
					if (nl <= level[n]) {
						continue;
					}
					if ((fromShaped || shaped[n]) && occludes(g.states[i], g.states[n], this.labels[k])) {
						continue;
					}
					level[n] = (byte) nl;
					if (nl > 1) {
						this.queue[nl].add(n);
					}
				}
			}
			q.clear();
		}
	}

	/** Whether the faces between two blocks stop light going from one to the other (as the light engine decides). */
	static boolean occludes(BlockState from, BlockState to, Direction dir) {
		if (empty(from) && empty(to)) {
			return false;
		}
		return Shapes.faceShapeOccludes(LightEngine.getOcclusionShape(empty(from) ? Blocks.AIR.defaultBlockState() : from, dir),
			LightEngine.getOcclusionShape(to, dir.getOpposite()));
	}

	private static boolean empty(BlockState s) {
		return !s.canOcclude() || !s.useShapeForLightOcclusion();
	}
}
