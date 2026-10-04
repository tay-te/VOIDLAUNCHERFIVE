package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * What grows over and falls into a building left to itself, laid over what still stands after a decay pass: rubble
 * where walls and roofs came down, the door beside its doorway, weeds on every patch of soil under the open sky, ivy up
 * the walls from the ground, moss on ledges and wall heads, leaf litter blown into the rooms, cobwebs in the corners
 * under what is left of the ceilings, bushes and at last a tree in the hall (bamboo in the karst, sand drifts in the
 * desert). Everything rests on something that stands, keeps off doorways, steps and paths, and stays in the plot, so a
 * ruin keeps every invariant but the light rule. All choices are per position and seeded, and thresholds rise with
 * the decay, so more decay grows more.
 */
public final class Growth {
	private static final Set<Block> SOIL = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM,
		Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.FARMLAND);
	private static final Set<Block> SAND = Set.of(Blocks.SAND, Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.SUSPICIOUS_SAND);

	private final Draft d;
	private final double decay;
	private final long seed;
	private final Climate climate;
	private final int[] body;
	private final LongOpenHashSet keep;
	private final Long2IntOpenHashMap cover;
	private final int w;
	private final int dp;
	private final int maxY;

	/** What stood at a cell as built (null where nothing did): rubble never lies where the same block stood. */
	@FunctionalInterface
	public interface Built {
		@Nullable BlockState at(int x, int y, int z);
	}

	private Built built = (x, y, z) -> null;

	/** Tells growth what stood where before the decay (see {@link Built}). */
	public Growth built(Built b) {
		this.built = b;
		return this;
	}

	public Growth(Draft d, double decay, long seed, Climate climate, int[] body, LongOpenHashSet keep, Long2IntOpenHashMap cover) {
		this.d = d;
		this.decay = decay;
		this.seed = seed;
		this.climate = climate;
		this.body = body;
		this.keep = keep;
		this.cover = cover;
		this.w = d.frontage();
		this.dp = d.depth();
		this.maxY = d.maxHeight() - 1;
	}

	// ------------------------------------------------------------------------------------------------ cells

	private boolean inPlot(int x, int y, int z) {
		return x >= 0 && x < this.w && z >= 0 && z < this.dp && y < this.maxY;
	}

	/** Free for growth: in the plot, nothing (or air) drawn, above the natural ground, not kept clear. */
	private boolean free(int x, int y, int z) {
		if (!inPlot(x, y, z) || this.keep.contains(Draft.key(x, y, z))) {
			return false;
		}
		Draft.Cell c = this.d.cell(x, y, z);
		if (c == null) {
			int wy = this.d.site().water(x, z);
			return y >= this.d.site().ground(x, z) && (wy == dev.voidmc.hearth.build.terrain.Site.NONE || y >= wy);
		}
		return c.state().isAir();
	}

	/** The block at a cell as the world will have it: drawn, or natural ground (stone-like), or air. */
	private @Nullable BlockState below(int x, int y, int z) {
		BlockState s = this.d.get(x, y, z);
		if (s != null) {
			return s.isAir() ? null : s;
		}
		return y < this.d.site().ground(x, z) ? Blocks.DIRT.defaultBlockState() : null;
	}

	private static boolean full(BlockState s) {
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** Whether a block's top face is whole (things can lie on it): full blocks, top slabs, upside-down stairs. */
	private static boolean sturdyTop(@Nullable BlockState s) {
		if (s == null || s.getBlock() instanceof LeavesBlock) {
			return false;
		}
		if (s.getBlock() instanceof SlabBlock) {
			return s.getValue(SlabBlock.TYPE) != SlabType.BOTTOM;
		}
		if (s.getBlock() instanceof StairBlock) {
			return s.getValue(StairBlock.HALF) == Half.TOP;
		}
		return full(s);
	}

	private static boolean soil(@Nullable BlockState s) {
		return s != null && SOIL.contains(s.getBlock());
	}

	private boolean dryGround(@Nullable BlockState s) {
		return s != null && (SAND.contains(s.getBlock()) || s.getBlock().toString().contains("terracotta"));
	}

	/** Whether a weed can root on a block in this climate. */
	private boolean roots(@Nullable BlockState s, Block plant) {
		if (s == null) {
			return false;
		}
		if (plant == Blocks.SHORT_DRY_GRASS || plant == Blocks.TALL_DRY_GRASS || plant == Blocks.DEAD_BUSH) {
			return soil(s) || dryGround(s);
		}
		return soil(s) && s.getBlock() != Blocks.FARMLAND || s.getBlock() == Blocks.FARMLAND && plant != Blocks.BUSH;
	}

	private boolean open(int x, int y, int z) {
		return y > this.cover.get(colKey(x, z));
	}

	private static long colKey(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private boolean inBody(int x, int z) {
		return x >= this.body[0] && x <= this.body[2] && z >= this.body[1] && z <= this.body[3];
	}

	private double unit(long salt, int x, int y, int z) {
		return Noise.unit(this.seed ^ salt, x, y, z);
	}

	private double patch(long salt, int x, int y, int z, double scale) {
		return Climate.stretch(Noise.value(this.seed ^ salt, x / scale, y / scale, z / scale));
	}

	private void place(int x, int y, int z, BlockState s) {
		try (var sc = this.d.stage(StageKind.EXTERIOR)) {
			this.d.set(x, y, z, s);
		}
	}

	/** A plant's single-block form (a tall plant's short sister), for cells with no room above. */
	public static BlockState single(Block plant) {
		if (plant == Blocks.TALL_GRASS) {
			return Blocks.SHORT_GRASS.defaultBlockState();
		}
		if (plant == Blocks.LARGE_FERN) {
			return Blocks.FERN.defaultBlockState();
		}
		if (plant instanceof DoublePlantBlock) {
			return Blocks.SHORT_GRASS.defaultBlockState();
		}
		if (plant instanceof SweetBerryBushBlock) {
			return plant.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 2);
		}
		return plant.defaultBlockState();
	}

	// ------------------------------------------------------------------------------------------------ what fell

	/**
	 * A piece of rubble falling from (x, fromY, z): it drops through the open cells below and lies on the first thing with
	 * a whole top it meets, if that is in the plot behind the building's front, free and not a way through.
	 */
	public void debris(int x, int fromY, int z, BlockState piece) {
		if (!inPlot(x, fromY, z) || z < this.body[1]) {
			return;
		}
		int y = fromY;
		int floor = this.d.site().ground(x, z) - 2;
		while (y > floor && free(x, y, z) && below(x, y - 1, z) == null) {
			y--;
		}
		if (!free(x, y, z)) {
			// the cell itself is taken: the piece lies on top of whatever stands there
			y++;
			if (!free(x, y, z)) {
				return;
			}
		}
		BlockState under = below(x, y - 1, z);
		if (!sturdyTop(under) || under.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
			return;
		}
		if (piece.equals(this.built.at(x, y, z))) {
			// it would only look as if it had never fallen
			return;
		}
		// a beam lies across what it fell on, two long where there is room
		place(x, y, z, piece);
		if (piece.getBlock() instanceof RotatedPillarBlock && piece.hasProperty(RotatedPillarBlock.AXIS)) {
			Direction.Axis a = piece.getValue(RotatedPillarBlock.AXIS);
			int nx = x + (a == Direction.Axis.X ? 1 : 0);
			int nz = z + (a == Direction.Axis.Z ? 1 : 0);
			if (unit(0xBEA3, x, y, z) < 0.6 && free(nx, y, nz) && nz >= this.body[1] && sturdyTop(below(nx, y - 1, nz))
				&& !piece.equals(this.built.at(nx, y, nz))) {
				place(nx, y, nz, piece);
			}
		}
	}

	/** A door off its hinges, lying beside its doorway (in or out), as a board of its own wood. */
	public void fallenDoor(int x, int y, int z, Block leaf) {
		List<int[]> c = new ArrayList<>();
		for (Direction f : Dirs.HORIZONTAL) {
			for (Direction s : new Direction[]{f.getClockWise(), f.getCounterClockWise()}) {
				c.add(new int[]{x + Dirs.dx(f) + Dirs.dx(s), z + Dirs.dz(f) + Dirs.dz(s)});
			}
		}
		int start = (int) (unit(0xD0D0, x, y, z) * c.size());
		for (int k = 0; k < c.size(); k++) {
			int[] p = c.get((start + k) % c.size());
			if (free(p[0], y, p[1]) && p[1] >= this.body[1] && sturdyTop(below(p[0], y - 1, p[1]))) {
				Direction facing = Dirs.HORIZONTAL[(int) (unit(0xD0D1, x, y, z) * 4) & 3];
				place(p[0], y, p[1], leaf.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.HALF, Half.BOTTOM)
					.setValue(TrapDoorBlock.OPEN, false));
				return;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ what grows

	/** Everything that grows, in order: trees, bushes, weeds, ivy, moss, leaf litter, cobwebs, sand. */
	public void grow() {
		int yLo = -3;
		int yHi = Math.min(this.maxY - 1, 64);
		// the cells with something under them, scanned once in a fixed order
		List<int[]> floors = new ArrayList<>();
		for (int x = 0; x < this.w; x++) {
			for (int z = 0; z < this.dp; z++) {
				int g = this.d.site().ground(x, z);
				for (int y = Math.max(yLo, g - 3); y <= yHi; y++) {
					if (free(x, y, z) && below(x, y - 1, z) != null) {
						floors.add(new int[]{x, y, z});
					}
				}
			}
		}
		if (this.climate.trees() && this.climate != Climate.KARST) {
			trees(floors);
		}
		if (this.climate == Climate.KARST) {
			bamboo(floors);
		}
		for (int[] c : floors) {
			shrub(c[0], c[1], c[2]);
		}
		for (int[] c : floors) {
			weed(c[0], c[1], c[2]);
		}
		if (this.climate.vines) {
			vines();
		}
		for (int[] c : floors) {
			ledge(c[0], c[1], c[2]);
		}
		cobwebs();
	}

	/** Self-sown trees: one in a big ruin's most open patch of soil (two on a big plot), late in its decay. */
	private void trees(List<int[]> floors) {
		Block leaves = this.climate.leaves(this.seed);
		Block log = this.climate.log(this.seed);
		if (leaves == null || log == null) {
			return;
		}
		int want = Math.max(1, this.w * this.dp / 160);
		for (int t = 0; t < want; t++) {
			if (this.decay < 0.76 + 0.2 * unit(0x7EE0 + t, 0, 0, 0)) {
				return;
			}
			int[] best = null;
			double bestScore = 0;
			for (int[] c : floors) {
				int x = c[0];
				int y = c[1];
				int z = c[2];
				if (z < this.body[1] || !open(x, y, z) || !soil(below(x, y - 1, z))) {
					continue;
				}
				// room for a trunk and a crown, preferring the inside of the ruin
				int room = 0;
				for (int dy = 0; dy < 6; dy++) {
					if (free(x, y + dy, z)) {
						room++;
					} else {
						break;
					}
				}
				if (room < 5) {
					continue;
				}
				int crown = 0;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (free(x + dx, y + 4, z + dz)) {
							crown++;
						}
					}
				}
				double score = crown + (inBody(x, z) ? 8 : 0) + unit(0x7EE1 + t, x, y, z) * 6;
				if (score > bestScore) {
					bestScore = score;
					best = c;
				}
			}
			if (best == null) {
				return;
			}
			tree(best[0], best[1], best[2], log, leaves, 3 + (int) (unit(0x7EE2 + t, best[0], 0, best[2]) * 3));
		}
	}

	private void tree(int x, int y, int z, Block log, Block leaves, int height) {
		int h = Math.min(height, this.maxY - y - 2);
		if (h < 2) {
			return;
		}
		for (int k = 0; k < h; k++) {
			place(x, y + k, z, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
		}
		int topY = y + h - 1;
		BlockState lf = States.leaves(leaves);
		for (int dy = -1; dy <= 2; dy++) {
			int r = dy <= 0 ? 2 : 1;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					boolean cornerCut = Math.abs(dx) == r && Math.abs(dz) == r && (r == 2 || dy == 2 || unit(0x7EE3, x + dx, topY + dy, z + dz) < 0.5);
					if (cornerCut || dx == 0 && dz == 0 && dy <= 0) {
						continue;
					}
					if (dy == 2 && (dx != 0 || dz != 0) && unit(0x7EE4, x + dx, topY, z + dz) < 0.5) {
						continue;
					}
					int lx = x + dx;
					int ly = topY + dy;
					int lz = z + dz;
					if (free(lx, ly, lz) && lz >= this.body[1]) {
						place(lx, ly, lz, lf);
					}
				}
			}
		}
		if (free(x, topY + 1, z)) {
			place(x, topY + 1, z, lf);
		}
	}

	/** Bamboo in karst ruins: clumps of stalks on the soil under the open sky. */
	private void bamboo(List<int[]> floors) {
		if (this.decay < 0.5) {
			return;
		}
		for (int[] c : floors) {
			int x = c[0];
			int y = c[1];
			int z = c[2];
			if (z < this.body[1] || !open(x, y, z) || !soil(below(x, y - 1, z))) {
				continue;
			}
			if (this.decay < 0.5 + 0.45 * patch(0xBA4B, x, 0, z, 2.5) || unit(0xBA4C, x, y, z) > 0.45) {
				continue;
			}
			int h = 2 + (int) ((this.decay - 0.45) * 8 * unit(0xBA4D, x, y, z) + 1);
			int k = 0;
			while (k < h && free(x, y + k, z)) {
				k++;
			}
			for (int j = 0; j < k; j++) {
				BambooLeaves lv = j >= k - 1 ? BambooLeaves.LARGE : j >= k - 2 ? BambooLeaves.SMALL : BambooLeaves.NONE;
				place(x, y + j, z, Blocks.BAMBOO.defaultBlockState().setValue(BambooStalkBlock.AGE, k > 4 ? 1 : 0).setValue(BambooStalkBlock.LEAVES, lv));
			}
		}
	}

	/** A bush of leaves on soil under the open sky (in the rooms and against the walls outside), from half decay. */
	private void shrub(int x, int y, int z) {
		Block leaves = this.climate.leaves(this.seed ^ 0x5B);
		if (leaves == null || this.decay < 0.45 || z < this.body[1] || !free(x, y, z) || !open(x, y, z) || !soil(below(x, y - 1, z))) {
			return;
		}
		double t = 0.45 + 0.42 * patch(0x5B00, x, y, z, 3.0);
		double density = inBody(x, z) ? 0.16 : 0.07;
		if (this.decay < t || unit(0x5B01, x, y, z) > density) {
			return;
		}
		BlockState lf = States.leaves(leaves);
		place(x, y, z, lf);
		// a second leaf beside or above
		int n = (int) (unit(0x5B02, x, y, z) * 3);
		for (int k = 0; k < n; k++) {
			Direction dir = Dirs.HORIZONTAL[(int) (unit(0x5B03 + k, x, y, z) * 4) & 3];
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			if (k == 0 && free(x, y + 1, z) && this.decay > 0.6) {
				place(x, y + 1, z, lf);
			} else if (free(nx, y, nz) && nz >= this.body[1] && below(nx, y - 1, nz) != null) {
				place(nx, y, nz, lf);
			}
		}
	}

	/** Weeds on soil under the open sky: the yard first, the rooms once they are open to the rain. */
	private void weed(int x, int y, int z) {
		if (!free(x, y, z) || !open(x, y, z)) {
			return;
		}
		BlockState under = below(x, y - 1, z);
		boolean in = inBody(x, z);
		double t = in ? 0.3 + 0.55 * patch(0x9EED, x, y, z, 2.0) : 0.06 + 0.6 * patch(0x9EED, x, y, z, 2.0);
		if (this.decay < t || unit(0x9EEE, x, y, z) > (in ? 0.8 : 0.6)) {
			return;
		}
		Block plant = this.climate.plant(unit(0x9EEF, x, y, z));
		if (!roots(under, plant)) {
			return;
		}
		if (plant instanceof DoublePlantBlock) {
			if (free(x, y + 1, z) && open(x, y + 1, z)) {
				try (var sc = this.d.stage(StageKind.EXTERIOR)) {
					this.d.tallPlant(x, y, z, plant);
				}
				return;
			}
			place(x, y, z, single(plant));
			return;
		}
		place(x, y, z, single(plant));
	}

	/** Ivy up the walls from the ground: in patches, growing higher with the years, stopping where the wall stops. */
	private void vines() {
		double density = switch (this.climate) {
			case JUNGLE, SWAMP -> 0.55;
			case KARST -> 0.45;
			case TEMPERATE -> 0.32;
			default -> 0.12;
		};
		for (int x = 0; x < this.w; x++) {
			for (int z = 0; z < this.dp; z++) {
				int g = this.d.site().ground(x, z);
				// the foot of a run: a free cell on something, against a wall
				for (int y = g - 2; y <= Math.min(this.maxY - 1, g + 24); y++) {
					if (!free(x, y, z) || below(x, y - 1, z) == null || faces(x, y, z) == null) {
						continue;
					}
					boolean in = inBody(x, z);
					if (in && this.decay < 0.25) {
						break;
					}
					double t = 0.03 + 0.45 * patch(0x1717, x, y, z, 3.0) + (in ? 0.25 : 0);
					if (unit(0x1718, x, 0, z) > density || this.decay < t) {
						break;
					}
					double grown = Math.clamp((this.decay - t) / 0.55, 0, 1);
					int run = 0;
					while (free(x, y + run, z) && faces(x, y + run, z) != null && y + run < this.maxY) {
						run++;
					}
					int h = (int) Math.round(run * grown);
					for (int k = 0; k < h; k++) {
						BlockState v = faces(x, y + k, z);
						if (v == null) {
							break;
						}
						place(x, y + k, z, v);
					}
					break;
				}
			}
		}
	}

	/** A vine for a cell, clinging to every whole wall face beside it; null when there is none. */
	private @Nullable BlockState faces(int x, int y, int z) {
		BlockState v = Blocks.VINE.defaultBlockState();
		boolean any = false;
		for (Direction dir : Dirs.HORIZONTAL) {
			BlockState s = this.d.get(x + Dirs.dx(dir), y, z + Dirs.dz(dir));
			if (s != null && full(s) && !(s.getBlock() instanceof LeavesBlock)) {
				v = v.setValue(VineBlock.getPropertyForFace(dir), true);
				any = true;
			}
		}
		return any ? v : null;
	}

	/** Moss on ledges and wall heads, leaf litter blown into the rooms, sand silting up a desert ruin. */
	private void ledge(int x, int y, int z) {
		if (!free(x, y, z)) {
			return;
		}
		BlockState under = below(x, y - 1, z);
		if (!sturdyTop(under) || soil(under)) {
			return;
		}
		boolean in = inBody(x, z);
		boolean open = open(x, y, z);
		if ((this.climate == Climate.DRY || this.climate == Climate.MESA) && in && this.decay >= 0.35 + 0.5 * patch(0x5A4D, x, y, z, 2.2)
			&& unit(0x5A4E, x, y, z) < 0.5 && wallBeside(x, y, z) && z >= this.body[1]) {
			place(x, y, z, (this.climate == Climate.MESA ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState());
			return;
		}
		if (this.climate.moss && (open || !in) && this.decay >= 0.32 + 0.55 * patch(0x3055, x, y, z, 2.5) && unit(0x3056, x, y, z) < 0.6) {
			place(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
			return;
		}
		boolean leafy = this.climate.trees() && this.climate != Climate.STEPPE;
		if (leafy && in && this.decay >= 0.28 + 0.6 * patch(0x1EAF, x, y, z, 2.0) && unit(0x1EB0, x, y, z) < 0.55) {
			int amount = 1 + (int) (unit(0x1EB1, x, y, z) * 4);
			Direction facing = Dirs.HORIZONTAL[(int) (unit(0x1EB2, x, y, z) * 4) & 3];
			place(x, y, z, Blocks.LEAF_LITTER.defaultBlockState().setValue(LeafLitterBlock.FACING, facing).setValue(LeafLitterBlock.AMOUNT, amount));
		}
	}

	private boolean wallBeside(int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			BlockState s = this.d.get(x + Dirs.dx(dir), y, z + Dirs.dz(dir));
			if (s != null && full(s)) {
				return true;
			}
		}
		return false;
	}

	/** Cobwebs in the corners under what is left of the ceilings, once the house is empty. */
	private void cobwebs() {
		if (this.decay < 0.25) {
			return;
		}
		for (int x = this.body[0]; x <= this.body[2]; x++) {
			for (int z = this.body[1]; z <= this.body[3]; z++) {
				for (int y = 1; y <= Math.min(this.maxY - 1, 40); y++) {
					if (!free(x, y, z)) {
						continue;
					}
					BlockState up = this.d.get(x, y + 1, z);
					if (up == null || !full(up)) {
						continue;
					}
					boolean ex = solid(x + 1, y, z) || solid(x - 1, y, z);
					boolean ez = solid(x, y, z + 1) || solid(x, y, z - 1);
					if (!ex || !ez) {
						continue;
					}
					if (this.decay >= 0.25 + 0.5 * unit(0xC0B0, x, y, z) && unit(0xC0B1, x, y, z) < 0.45) {
						place(x, y, z, Blocks.COBWEB.defaultBlockState());
					}
				}
			}
		}
	}

	private boolean solid(int x, int y, int z) {
		BlockState s = this.d.get(x, y, z);
		return s != null && full(s);
	}

	/** Whether a state is something growth laid (for tests and previews). */
	public static boolean grown(BlockState s) {
		Block b = s.getBlock();
		return b instanceof VegetationBlock || b instanceof VineBlock || b == Blocks.MOSS_CARPET || b == Blocks.COBWEB || b instanceof LeavesBlock
			|| b instanceof LeafLitterBlock || b instanceof BambooStalkBlock;
	}
}
