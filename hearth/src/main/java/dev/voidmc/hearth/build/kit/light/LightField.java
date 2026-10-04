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
		if (this.scratch == null) {
			this.scratch = new byte[this.level.length];
			for (int i = 0; i < 16; i++) {
				this.probeQueue[i] = new IntArrayList();
			}
		}
		Grid g = this.g;
		byte[] lv = this.scratch;
		int start = g.index(x, y, z);
		lv[start] = (byte) emission;
		this.touched.add(start);
		this.probeQueue[emission].add(start);
		for (int l = emission; l >= 2; l--) {
			IntArrayList q = this.probeQueue[l];
			for (int qi = 0; qi < q.size(); qi++) {
				int i = q.getInt(qi);
				if (lv[i] != l) {
					continue;
				}
				int cx = i % g.sx;
				int rest = i / g.sx;
				int cz = rest % g.sz;
				int cy = rest / g.sz;
				BlockState from = i == start ? Blocks.AIR.defaultBlockState() : g.states[i];
				for (int k = 0; k < 6; k++) {
					int[] s = this.steps[k];
					int nx = cx + s[0];
					int ny = cy + s[1];
					int nz = cz + s[2];
					if (nx < 0 || ny < 0 || nz < 0 || nx >= g.sx || ny >= g.sy || nz >= g.sz) {
						continue;
					}
					int n = i + this.offsets[k];
					if (l - 1 <= lv[n]) {
						continue;
					}
					BlockState to = g.states[n];
					int nl = l - Math.max(1, to.getLightDampening());
					if (nl <= lv[n] || occludes(from, to, this.labels[k])) {
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
		for (int t = 0; t < this.touched.size(); t++) {
			int i = this.touched.getInt(t);
			v.visit(i, lv[i]);
			lv[i] = 0;
		}
		this.touched.clear();
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
	}

	private void flood() {
		Grid g = this.g;
		for (int l = 15; l >= 2; l--) {
			IntArrayList q = this.queue[l];
			for (int qi = 0; qi < q.size(); qi++) {
				int i = q.getInt(qi);
				if (this.level[i] != l) {
					continue;
				}
				int x = i % g.sx;
				int rest = i / g.sx;
				int z = rest % g.sz;
				int y = rest / g.sz;
				BlockState from = g.states[i];
				for (int k = 0; k < 6; k++) {
					int[] s = this.steps[k];
					int nx = x + s[0];
					int ny = y + s[1];
					int nz = z + s[2];
					if (nx < 0 || ny < 0 || nz < 0 || nx >= g.sx || ny >= g.sy || nz >= g.sz) {
						continue;
					}
					int n = i + this.offsets[k];
					if (l - 1 <= this.level[n]) {
						continue;
					}
					BlockState to = g.states[n];
					int nl = l - Math.max(1, to.getLightDampening());
					if (nl <= this.level[n]) {
						continue;
					}
					if (occludes(from, to, this.labels[k])) {
						continue;
					}
					this.level[n] = (byte) nl;
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
