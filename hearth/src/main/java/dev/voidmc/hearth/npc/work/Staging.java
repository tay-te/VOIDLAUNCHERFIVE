package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A building site's scaffolding (docs/FOLK.md §6): a ring of vanilla scaffold columns one block outside the building's
 * shell (its walls, eaves and roof as the blueprint will have them), raised lift by lift as the work climbs, and taken
 * down once no high work on the shell is left. A column of scaffolding is climbed inside and stood in at any height, as
 * players use it, so the ring is a walkway at every level: the crew climbs it once, walks round it and works the wall in
 * front of them, and comes down when the roof is on. Shared by the site's crew; every block is entered in the
 * settlement's {@link ScaffoldLedger} under the site's own id and removed with it.
 *
 * <p>Cells where the blueprint will put a block (a porch, a chimney, a garden wall) or where something already stands
 * are left out of the ring, as gaps; a column stops three short of anything above it, so there is headroom on top.
 */
final class Staging {
	/** A lift: how much higher the ring goes at a time. */
	static final int LIFT = 4;
	/** No column taller than this. */
	static final int MAX_COLUMN = 40;
	static final int NONE = Integer.MIN_VALUE;

	final int n;
	final int[] xs;
	final int[] zs;
	/** Feet height at the bottom of each column (one above the ground), or {@link #NONE} for a gap. */
	final int[] base;
	/** The highest y each column may reach (headroom kept above it). */
	final int[] cap;
	/** The highest scaffold block in each column, {@code base - 1} if none. */
	final int[] height;
	/** Ring index by column (packed x, z with y 0). */
	private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
	/** The last stage with high work on the shell: the ring comes down once it is done. */
	final int lastHighStage;
	/** The wanted top (absolute y): columns are raised to {@code min(target, cap)}. */
	int target = NONE;
	final int minBase;
	final int shellMinX;
	final int shellMaxX;
	final int shellMinZ;
	final int shellMaxZ;

	private Staging(int[] xs, int[] zs, int[] base, int[] cap, int lastHighStage, int[] shell) {
		this.n = xs.length;
		this.xs = xs;
		this.zs = zs;
		this.base = base;
		this.cap = cap;
		this.height = new int[this.n];
		int lo = Integer.MAX_VALUE;
		for (int i = 0; i < this.n; i++) {
			this.height[i] = base[i] == NONE ? NONE : base[i] - 1;
			this.index.put(BlockPos.asLong(xs[i], 0, zs[i]), i);
			if (base[i] != NONE) {
				lo = Math.min(lo, base[i]);
			}
		}
		this.index.defaultReturnValue(-1);
		this.minBase = lo == Integer.MAX_VALUE ? NONE : lo;
		this.lastHighStage = lastHighStage;
		this.shellMinX = shell[0];
		this.shellMaxX = shell[1];
		this.shellMinZ = shell[2];
		this.shellMaxZ = shell[3];
	}

	/** Free for scaffolding: air, a plant or snow that a block simply replaces, or scaffolding already. */
	static boolean free(BlockState s) {
		return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty() || s.is(Blocks.SCAFFOLDING);
	}

	/** Height above the lowest ground of the ring from which work counts as high: out of reach from the ground. */
	static final int HIGH = 3;

	/**
	 * The ring round what stages {@code fromStage} on will build above the floor; null when there is no shell to stand
	 * round (a garden, a path) or no column could stand.
	 */
	static @Nullable Staging plan(ServerLevel level, Blueprint bp, int fromStage, PlotSpec spec) {
		int floor = spec.floorY();
		LongOpenHashSet solids = new LongOpenHashSet();
		int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (int st = fromStage; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> {
					if (state.isAir()) {
						return;
					}
					solids.add(BlockPos.asLong(x, y, z));
					if (y >= floor + 2) {
						box[0] = Math.min(box[0], x);
						box[1] = Math.max(box[1], x);
						box[2] = Math.min(box[2], z);
						box[3] = Math.max(box[3], z);
						box[4] = Math.max(box[4], y);
					}
				});
			}
		}
		if (box[0] == Integer.MAX_VALUE || box[4] < floor + HIGH) {
			return null;
		}
		int x0 = box[0] - 1;
		int x1 = box[1] + 1;
		int z0 = box[2] - 1;
		int z1 = box[3] + 1;
		int len = 2 * (x1 - x0) + 2 * (z1 - z0);
		int[] xs = new int[len];
		int[] zs = new int[len];
		int k = 0;
		for (int x = x0; x < x1; x++) {
			xs[k] = x;
			zs[k++] = z0;
		}
		for (int z = z0; z < z1; z++) {
			xs[k] = x1;
			zs[k++] = z;
		}
		for (int x = x1; x > x0; x--) {
			xs[k] = x;
			zs[k++] = z1;
		}
		for (int z = z1; z > z0; z--) {
			xs[k] = x0;
			zs[k++] = z;
		}
		int[] base = new int[len];
		int[] cap = new int[len];
		int top = Math.min(box[4] + 3, level.getMaxY());
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		boolean any = false;
		for (int c = 0; c < len; c++) {
			base[c] = NONE;
			cap[c] = NONE;
			// the ground under the cell: the first block down from well above the floor. A cell where that first block is
			// high above the floor (a neighbour's wall or roof, a tree) is a gap: the ring never stands on another building
			m.set(xs[c], floor + 9, zs[c]);
			if (!level.isLoaded(m) || !free(level.getBlockState(m))) {
				continue;
			}
			int ground = NONE;
			for (int y = floor + 8; y >= floor - 16; y--) {
				m.setY(y);
				if (!free(level.getBlockState(m))) {
					ground = y;
					break;
				}
			}
			if (ground == NONE || ground > floor + 4) {
				continue;
			}
			int b = ground + 1;
			int blocked = Integer.MAX_VALUE;
			for (int y = b; y <= Math.min(top + 3, b + MAX_COLUMN + 2); y++) {
				m.setY(y);
				if (solids.contains(m.asLong()) || !free(level.getBlockState(m))) {
					blocked = y;
					break;
				}
			}
			// headroom of two above the top block; a cell that cannot even be stood in at the bottom is a gap
			int c2 = blocked == Integer.MAX_VALUE ? Math.min(top, b + MAX_COLUMN - 1) : blocked - 3;
			if (c2 < b - 1) {
				continue;
			}
			base[c] = b;
			cap[c] = Math.min(c2, b + MAX_COLUMN - 1);
			any = true;
		}
		if (!any) {
			return null;
		}
		// the last stage with work high on the shell, within reach of the ring
		int lowest = Integer.MAX_VALUE;
		for (int c = 0; c < len; c++) {
			if (base[c] != NONE) {
				lowest = Math.min(lowest, base[c]);
			}
		}
		int high = lowest + HIGH;
		int last = -1;
		for (int st = bp.stageCount() - 1; st >= fromStage && last < 0; st--) {
			for (int i = 0; i < bp.stepCount(st) && last < 0; i++) {
				int stage = st;
				int[] hit = {0};
				bp.step(st, i, (x, y, z, state, tag) -> {
					if (y > high && nearShell(x, z, x0 + 1, x1 - 1, z0 + 1, z1 - 1)) {
						hit[0] = 1;
					}
				});
				if (hit[0] == 1) {
					last = stage;
				}
			}
		}
		return new Staging(xs, zs, base, cap, last, new int[]{x0 + 1, x1 - 1, z0 + 1, z1 - 1});
	}

	/** Within reach (horizontally) of the ring round the shell box: on or near its outer faces. */
	static boolean nearShell(int x, int z, int minX, int maxX, int minZ, int maxZ) {
		int in = Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
		return in <= 3;
	}

	boolean valid(int c) {
		return this.base[c] != NONE;
	}

	/** The ring index of a column, or -1. */
	int cellAt(int x, int z) {
		return this.index.get(BlockPos.asLong(x, 0, z));
	}

	/** The top a column is raised to now. */
	int want(int c) {
		return this.target == NONE ? this.base[c] - 1 : Math.min(this.target, this.cap[c]);
	}

	/** Columns still short of the wanted top. */
	boolean raising() {
		for (int c = 0; c < this.n; c++) {
			if (valid(c) && this.height[c] < want(c)) {
				return true;
			}
		}
		return false;
	}

	/** Whether anything of it stands. */
	boolean standing() {
		for (int c = 0; c < this.n; c++) {
			if (valid(c) && this.height[c] >= this.base[c]) {
				return true;
			}
		}
		return false;
	}

	int blocks() {
		int b = 0;
		for (int c = 0; c < this.n; c++) {
			if (valid(c)) {
				b += Math.max(0, this.height[c] - this.base[c] + 1);
			}
		}
		return b;
	}

	/**
	 * Asks for the ring to reach feet height {@code feet} (a builder must stand there): the top goes up in whole lifts.
	 * False if no column can go that high.
	 */
	boolean require(int feet) {
		int top = feet - 1;
		if (this.minBase == NONE || top < this.minBase) {
			return top < this.minBase;
		}
		int lifts = (top - (this.minBase - 1) + LIFT - 1) / LIFT;
		int t = this.minBase - 1 + lifts * LIFT;
		int maxCap = NONE;
		for (int c = 0; c < this.n; c++) {
			if (valid(c)) {
				maxCap = Math.max(maxCap, this.cap[c]);
			}
		}
		if (maxCap < top) {
			return false;
		}
		this.target = Math.max(this.target, Math.min(t, maxCap));
		return true;
	}

	/** Someone could stand in (or on) column {@code c} with their feet at {@code feet}. */
	boolean supports(int c, int feet) {
		if (!valid(c) || feet > this.cap[c] + 1) {
			return false;
		}
		return feet == this.base[c] || feet - 1 >= this.base[c] && feet - 1 <= this.height[c];
	}

	/** Ring cells between {@code from} and {@code to} going {@code dir} (+1 or -1), exclusive of {@code from}, inclusive of {@code to}. */
	int steps(int from, int to, int dir) {
		return Math.floorMod((to - from) * dir, this.n);
	}

	int next(int c, int dir) {
		return Math.floorMod(c + dir, this.n);
	}

	/** Puts one scaffold block on top of column {@code c}, entered in the ledger. False if the spot is no longer free. */
	boolean raise(ServerLevel level, ScaffoldLedger ledger, UUID id, int c) {
		int y = this.height[c] + 1;
		BlockPos p = new BlockPos(this.xs[c], y, this.zs[c]);
		if (!level.isLoaded(p)) {
			return false;
		}
		BlockState there = level.getBlockState(p);
		if (there.is(Blocks.SCAFFOLDING)) {
			this.height[c] = y;
			return true;
		}
		if (!free(there)) {
			// something was built in the way: the column stops here
			this.cap[c] = Math.min(this.cap[c], y - 3);
			return false;
		}
		// set as a column standing on the ground (no scheduled stability check that could drop it as a falling block)
		level.setBlock(p, Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0),
			Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
		ledger.add(id, p);
		this.height[c] = y;
		return true;
	}

	/** Takes a whole column down at once (as pulling out the bottom of vanilla scaffolding brings it all down). */
	int takeDown(ServerLevel level, ScaffoldLedger ledger, UUID id, int c) {
		int removed = 0;
		for (int y = this.height[c]; y >= this.base[c]; y--) {
			BlockPos p = new BlockPos(this.xs[c], y, this.zs[c]);
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
				if (removed < 3 || y == this.base[c]) {
					level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(Blocks.SCAFFOLDING.defaultBlockState()));
				}
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				removed++;
			}
			ledger.remove(id, p);
		}
		if (removed > 0) {
			level.playSound(null, new BlockPos(this.xs[c], this.base[c], this.zs[c]), SoundEvents.SCAFFOLDING_BREAK, SoundSource.BLOCKS, 0.9F, 0.9F);
		}
		this.height[c] = this.base[c] - 1;
		return removed;
	}

	/** Re-reads every column from the world (a player may have broken or added some). */
	void resync(ServerLevel level) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int c = 0; c < this.n; c++) {
			if (!valid(c)) {
				continue;
			}
			int h = this.base[c] - 1;
			for (int y = this.base[c]; y <= this.cap[c]; y++) {
				m.set(this.xs[c], y, this.zs[c]);
				if (!level.isLoaded(m) || !level.getBlockState(m).is(Blocks.SCAFFOLDING)) {
					break;
				}
				h = y;
			}
			this.height[c] = h;
		}
	}
}
