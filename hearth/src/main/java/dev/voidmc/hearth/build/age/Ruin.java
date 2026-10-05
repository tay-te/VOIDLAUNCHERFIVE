package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.age.Climate;
import dev.voidmc.hearth.build.kit.age.Growth;
import dev.voidmc.hearth.build.kit.age.Rubble;
import dev.voidmc.hearth.build.kit.age.Weather;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * One decay pass over one drawing ({@link Decay}): lives, the widest-path pass that caps them by support, then what is
 * taken, what weathers, what falls and what grows.
 */
final class Ruin {
	/** A life no decay reaches. */
	private static final double NEVER = 9.0;

	/** What a block is to the building, for how long it lasts and what becomes of it. */
	enum Part {
		GROUND, ROOF, CHIMNEY, GABLE, STRUCTURE, FLOOR, GROUND_FLOOR, DOOR, GLASS, SHUTTER, BARS, OPENING, LIGHT, GLOW_FLOOR, CANDLE, FIRE, BED, POT,
		CARPET, CLOTH, CONTAINER, LECTERN, HEAVY, BOOKS, CHAIN, FURNITURE, PLANT, CROP, FARMLAND, FENCE, YARD_WALL, YARD_THING, TREE, PATH;

		boolean structural() {
			return this == ROOF || this == CHIMNEY || this == GABLE || this == STRUCTURE || this == FLOOR;
		}
	}

	/** How a block is held up: on anything beside or below it, on what is below, from what is above, or on a wall. */
	enum Hold {
		/** Anything that shares a face or an edge with it, at its level or below (a roof's stair courses meet on edges). */
		GENERAL,
		/** What is under it. */
		RESTS,
		/** What is over it. */
		HANGS,
		/** A wall face beside it, or what is under it. */
		SIDE,
		/** Walls and posts: the course under them, or the wall beside them; never a mere edge (a gable's frame does not stand on its own rake). */
		WALL
	}

	/** The 18-neighbourhood (blocks sharing a face or an edge), as the floating check joins blocks. */
	private static final int[][] N18 = n18();

	private final Draft d;
	private final double decay;
	private final long seed;
	private final Climate climate;
	private final int w;
	private final int dp;
	private final int[] body;
	private int eave;
	private int top;

	private int n;
	private int[] xs;
	private int[] ys;
	private int[] zs;
	private BlockState[] st;
	private StageKind[] sg;
	private CompoundTag[] tg;
	private Part[] part;
	private Hold[] hold;
	private double[] life;
	private double[] eff;
	/** The block each block's best support came through (itself on the ground, -1 if unheld). */
	private int[] via;
	/** What stands at this decay (see {@link #standing}). */
	private boolean[] alive;
	/** Wall blocks that stood free as built (see {@link #posts}). */
	private boolean[] builtPost;
	private int[] partner;
	private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
	/** Cells growth and rubble must leave free: doorways, the cells either side of a door, entrances, stairs. */
	private final LongOpenHashSet keep = new LongOpenHashSet();
	/** Per column (x, z) the highest y still covered by something solid after the collapse (the sky above it is open). */
	private final Long2IntOpenHashMap cover = new Long2IntOpenHashMap();

	Ruin(Draft d, double decay) {
		this.d = d;
		this.decay = decay;
		this.seed = Noise.hash(d.seed(), 0xDECA7L);
		this.climate = Climate.of(d);
		this.w = d.frontage();
		this.dp = d.depth();
		this.body = d.bodyRect();
		this.index.defaultReturnValue(-1);
		this.cover.defaultReturnValue(Integer.MIN_VALUE);
	}

	void run() {
		snapshot();
		if (this.n == 0) {
			return;
		}
		measure();
		classify();
		pairs();
		protectRoute();
		support();
		standing();
		keepClear();
		List<Integer> fallen = new ArrayList<>();
		// what is gone is gone first (so weathering and growth read the ruin as it stands)
		for (int i = 0; i < this.n; i++) {
			if (!present(i)) {
				fallen.add(i);
				setAir(i);
			}
		}
		covers();
		for (int i = 0; i < this.n; i++) {
			if (present(i)) {
				wear(i);
			}
		}
		Growth g = new Growth(this.d, this.decay, this.seed, this.climate, this.body, this.keep, this.cover).built((x, y, z) -> {
			int j = at(x, y, z);
			return j < 0 ? null : this.st[j];
		});
		rubble(fallen, g);
		g.grow();
		if (this.decay >= Decay.ABANDONED) {
			// nobody lives here: no beds, workplaces, stores or seats to go to (the doors and gates still say where it opens)
			this.d.retainPois(p -> p.type() == PoiType.DOOR || p.type() == PoiType.GATE || p.type() == PoiType.VIEWPOINT);
			// and nobody lights it (the light rule exempts it; the lighting pass must not hang new lamps in a ruin)
			this.d.markLit();
		}
	}

	// ------------------------------------------------------------------------------------------------ reading

	private void snapshot() {
		var cells = this.d.cells();
		long[] keys = new long[cells.size()];
		int k = 0;
		for (var e : cells.long2ObjectEntrySet()) {
			if (!e.getValue().state().isAir()) {
				keys[k++] = e.getLongKey();
			}
		}
		keys = java.util.Arrays.copyOf(keys, k);
		LongArrays.quickSort(keys);
		this.n = k;
		this.xs = new int[k];
		this.ys = new int[k];
		this.zs = new int[k];
		this.st = new BlockState[k];
		this.sg = new StageKind[k];
		this.tg = new CompoundTag[k];
		for (int i = 0; i < k; i++) {
			long key = keys[i];
			Draft.Cell c = cells.get(key);
			this.xs[i] = Draft.keyX(key);
			this.ys[i] = Draft.keyY(key);
			this.zs[i] = Draft.keyZ(key);
			this.st[i] = c.state();
			this.sg[i] = c.stage();
			this.tg[i] = c.tag();
			this.index.put(key, i);
		}
	}

	private int at(int x, int y, int z) {
		return this.index.get(Draft.key(x, y, z));
	}

	/** Eave and top: the eave the archetype declared, else the lowest roof course over the body, else the wall heads. */
	private void measure() {
		int lowRoof = Integer.MAX_VALUE;
		int wallTop = Integer.MIN_VALUE;
		this.top = Integer.MIN_VALUE;
		for (int i = 0; i < this.n; i++) {
			this.top = Math.max(this.top, this.ys[i]);
			boolean inBody = this.xs[i] >= this.body[0] && this.xs[i] <= this.body[2] && this.zs[i] >= this.body[1] && this.zs[i] <= this.body[3];
			if (this.sg[i] == StageKind.ROOF && inBody && this.ys[i] >= 2) {
				lowRoof = Math.min(lowRoof, this.ys[i]);
			}
			if (this.sg[i] == StageKind.WALLS || this.sg[i] == StageKind.FRAME) {
				wallTop = Math.max(wallTop, this.ys[i]);
			}
		}
		int declared = this.d.eaveY();
		this.eave = declared != Integer.MIN_VALUE && declared >= 2 ? declared : lowRoof != Integer.MAX_VALUE ? lowRoof : wallTop != Integer.MIN_VALUE ? wallTop + 1 : 4;
		this.eave = Math.max(2, this.eave);
		this.top = Math.max(this.top, this.eave + 1);
	}

	// ------------------------------------------------------------------------------------------------ lives

	private void classify() {
		this.part = new Part[this.n];
		this.hold = new Hold[this.n];
		this.life = new double[this.n];
		// chimney runs: ROOF-stage masonry columns that come up from below the eave
		int[] runBottom = new int[this.n];
		int[] runTop = new int[this.n];
		for (int i = 0; i < this.n; i++) {
			runBottom[i] = Integer.MIN_VALUE;
		}
		for (int i = 0; i < this.n; i++) {
			if (chimneyish(i) && runBottom[i] == Integer.MIN_VALUE) {
				// walk the whole run
				int y0 = this.ys[i];
				while (true) {
					int b = at(this.xs[i], y0 - 1, this.zs[i]);
					if (b < 0 || !chimneyish(b)) {
						break;
					}
					y0--;
				}
				int y1 = this.ys[i];
				while (true) {
					int t = at(this.xs[i], y1 + 1, this.zs[i]);
					if (t < 0 || !chimneyish(t)) {
						break;
					}
					y1++;
				}
				for (int y = y0; y <= y1; y++) {
					int c = at(this.xs[i], y, this.zs[i]);
					runBottom[c] = y0;
					runTop[c] = y1;
				}
			}
		}
		for (int i = 0; i < this.n; i++) {
			Part p = classify(i, runBottom[i]);
			this.part[i] = p;
			this.hold[i] = hold(this.st[i]);
			if (this.hold[i] == Hold.GENERAL && (p == Part.STRUCTURE || p == Part.GABLE || p == Part.CHIMNEY)) {
				this.hold[i] = Hold.WALL;
			}
			this.life[i] = life(i, p, runBottom[i], runTop[i]);
		}
	}

	private boolean chimneyish(int i) {
		BlockState s = this.st[i];
		return this.sg[i] == StageKind.ROOF && (Weather.masonry(s) || s.getBlock() instanceof CampfireBlock) && !(s.getBlock() instanceof StairBlock);
	}

	private Part classify(int i, int chimneyBottom) {
		BlockState s = this.st[i];
		Block b = s.getBlock();
		StageKind k = this.sg[i];
		int y = this.ys[i];
		boolean light = s.getLightEmission() > 0 || b == Blocks.LIGHT;
		if (b instanceof CampfireBlock || b instanceof AbstractFurnaceBlock) {
			return Part.FIRE;
		}
		if (b instanceof CandleBlock || b instanceof CandleCakeBlock) {
			return Part.CANDLE;
		}
		if (light) {
			boolean full = s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			if (full && (k == StageKind.FLOORS || k == StageKind.WALLS || k == StageKind.FRAME || k == StageKind.ROOF || k == StageKind.FOUNDATION)) {
				return Part.GLOW_FLOOR;
			}
			return Part.LIGHT;
		}
		if (k == StageKind.CLEAR || k == StageKind.EARTHWORK || k == StageKind.FOUNDATION) {
			return b instanceof VegetationBlock ? Part.PLANT : Part.GROUND;
		}
		if (y < 0 && k != StageKind.INTERIOR && k != StageKind.EXTERIOR) {
			return k == StageKind.FLOORS ? Part.GROUND_FLOOR : Part.GROUND;
		}
		if (b instanceof BedBlock) {
			return Part.BED;
		}
		if (b instanceof DoorBlock) {
			return Part.DOOR;
		}
		if (b instanceof TrapDoorBlock) {
			return k == StageKind.OPENINGS || k == StageKind.WALLS || k == StageKind.FRAME ? Part.SHUTTER : Part.FURNITURE;
		}
		String name = Weather.name(b);
		if (name.contains("glass")) {
			return Part.GLASS;
		}
		if (b == Blocks.IRON_BARS) {
			return Part.BARS;
		}
		if (b instanceof ChainBlock) {
			return Part.CHAIN;
		}
		if (b instanceof CarpetBlock) {
			return Part.CARPET;
		}
		if (b instanceof BannerBlock || b instanceof WallBannerBlock || b instanceof SignBlock) {
			return Part.CLOTH;
		}
		if (b instanceof FlowerPotBlock) {
			return Part.POT;
		}
		switch (k) {
			case ROOF -> {
				if (chimneyBottom != Integer.MIN_VALUE && chimneyBottom <= this.eave - 2) {
					return Part.CHIMNEY;
				}
				boolean perimeter = this.xs[i] == this.body[0] || this.xs[i] == this.body[2] || this.zs[i] == this.body[1] || this.zs[i] == this.body[3];
				if (perimeter && y >= this.eave && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					return Part.GABLE;
				}
				return Part.ROOF;
			}
			case FRAME, WALLS -> {
				return Part.STRUCTURE;
			}
			case FLOORS -> {
				return y >= 0 ? Part.FLOOR : Part.GROUND_FLOOR;
			}
			case OPENINGS -> {
				return b instanceof FenceGateBlock ? Part.FENCE : Part.OPENING;
			}
			case INTERIOR -> {
				if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
					return Part.CONTAINER;
				}
				if (b instanceof LecternBlock) {
					return Part.LECTERN;
				}
				if (name.contains("bookshelf")) {
					return Part.BOOKS;
				}
				if (b instanceof AnvilBlock || b instanceof GrindstoneBlock || b instanceof StonecutterBlock || b instanceof BrewingStandBlock
					|| b instanceof BellBlock || name.contains("cauldron")) {
					return Part.HEAVY;
				}
				if (Weather.masonry(s) && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					// a chimney breast, a hearth surround, a stone counter
					return Part.STRUCTURE;
				}
				return Part.FURNITURE;
			}
			case EXTERIOR -> {
				if (b instanceof CropBlock || b instanceof StemBlock || b == Blocks.ATTACHED_MELON_STEM || b == Blocks.ATTACHED_PUMPKIN_STEM) {
					return Part.CROP;
				}
				if (b == Blocks.FARMLAND) {
					return Part.FARMLAND;
				}
				if (b instanceof LeavesBlock || b instanceof RotatedPillarBlock && name.endsWith("_log")) {
					return Part.TREE;
				}
				if (b instanceof VegetationBlock) {
					return Part.PLANT;
				}
				if (b instanceof FenceBlock || b instanceof FenceGateBlock) {
					return Part.FENCE;
				}
				if (b instanceof WallBlock) {
					return Part.YARD_WALL;
				}
				if (b == Blocks.DIRT_PATH) {
					return Part.PATH;
				}
				if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ComposterBlock || b == Blocks.HAY_BLOCK || name.contains("beehive")
					|| name.contains("bee_nest") || b instanceof LecternBlock || name.contains("wool")) {
					return Part.YARD_THING;
				}
				return Part.GROUND;
			}
			default -> {
				return Part.GROUND;
			}
		}
	}

	private static Hold hold(BlockState s) {
		Block b = s.getBlock();
		if (b instanceof WallTorchBlock || b instanceof WallSignBlock || b instanceof WallBannerBlock || b instanceof WallHangingSignBlock
			|| b instanceof LadderBlock || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof VineBlock || b instanceof CocoaBlock
			|| b instanceof TrapDoorBlock) {
			return Hold.SIDE;
		}
		if (b instanceof LanternBlock) {
			return s.getValue(LanternBlock.HANGING) ? Hold.HANGS : Hold.RESTS;
		}
		if (b instanceof ChainBlock) {
			return s.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y ? Hold.HANGS : Hold.SIDE;
		}
		if (b instanceof CeilingHangingSignBlock || b instanceof HangingRootsBlock || b instanceof SporeBlossomBlock
			|| b instanceof net.minecraft.world.level.block.WeepingVinesBlock || b instanceof net.minecraft.world.level.block.WeepingVinesPlantBlock
			|| b instanceof net.minecraft.world.level.block.HangingMossBlock) {
			return Hold.HANGS;
		}
		if (b instanceof VegetationBlock || b instanceof CarpetBlock || b instanceof CandleBlock || b instanceof CandleCakeBlock || b instanceof FlowerPotBlock
			|| b instanceof BasePressurePlateBlock || b instanceof SnowLayerBlock || b instanceof BedBlock || b instanceof DoorBlock || b instanceof CakeBlock
			|| b instanceof BaseRailBlock || b instanceof TorchBlock || b instanceof DoublePlantBlock || b instanceof BannerBlock && !(b instanceof WallBannerBlock)
			|| b instanceof SignBlock && !(b instanceof WallSignBlock)) {
			return Hold.RESTS;
		}
		return Hold.GENERAL;
	}

	private double patch(long salt, int x, int y, int z, double scale) {
		return Climate.stretch(Noise.value(this.seed ^ salt, x / scale, y / scale, z / scale));
	}

	private double unit(long salt, int x, int y, int z) {
		return Noise.unit(this.seed ^ salt, x, y, z);
	}

	private double life(int i, Part p, int runBottom, int runTop) {
		int x = this.xs[i];
		int y = this.ys[i];
		int z = this.zs[i];
		BlockState s = this.st[i];
		double u = unit(0x11FE, x, y, z);
		double mat = Weather.soft(s) ? -0.3 : Weather.timber(s) ? -0.22 : Weather.masonry(s) ? 0.06 : 0;
		double span = Math.max(1, this.top - this.eave);
		return switch (p) {
			case GROUND, GROUND_FLOOR, TREE, PATH, PLANT, FARMLAND, GLOW_FLOOR -> NEVER;
			case ROOF -> {
				// a roof falls between its rafters: in bays along each slope, the upper courses of a bay first
				double th = Math.clamp((y - this.eave) / span, 0, 1);
				double bay = bay(i);
				yield Math.clamp(0.29 + 0.5 * bay + 0.13 * (1 - th) + 0.06 * patch(0x7001, x, y, z, 2.0) + 0.02 * (u - 0.5), 0.26, 0.97);
			}
			case CHIMNEY -> {
				double rel = Math.clamp((y - runBottom) / (double) Math.max(1, runTop - runBottom), 0, 1);
				yield 0.86 + 0.55 * (1 - rel) + 0.12 * patch(0x3A11, x, y, z, 2.2) + 0.03 * (u - 0.5);
			}
			case GABLE -> 0.5 + 0.36 * (1 - Math.clamp((y - this.eave) / span, 0, 1)) + 0.16 * patch(0x3A11, x, y, z, 2.2) + mat;
			case STRUCTURE -> {
				double pw = patch(0x3A11, x, y, z, 2.2);
				if (y >= this.eave) {
					yield 0.5 + 0.36 * (1 - Math.clamp((y - this.eave) / span, 0, 1)) + 0.16 * pw + mat;
				}
				double rel = Math.clamp(y / (double) Math.max(1, this.eave), 0, 1);
				yield 0.62 + 0.78 * (1 - rel) + 0.24 * pw + 0.10 * corner(i) + mat + 0.03 * (u - 0.5);
			}
			case FLOOR -> 0.45 + 0.45 * patch(0xF100, x, y, z, 2.6) + (Weather.masonry(s) ? 0.25 : 0);
			case DOOR -> 0.30 + 0.32 * unit(0xD00A, x, 0, z);
			case GLASS -> 0.26 + 0.5 * patch(0x61A5, x, y, z, 1.6);
			case SHUTTER -> 0.18 + 0.4 * u;
			case BARS -> 0.95 + 0.3 * u;
			case OPENING -> 0.35 + 0.5 * u;
			case LIGHT, BED, CROP -> Decay.ABANDONED;
			case CANDLE -> 0.33 + 0.4 * u;
			case FIRE -> s.getBlock() instanceof CampfireBlock ? 0.55 + 0.4 * u : 0.85 + 0.5 * u;
			case POT -> 0.3 + 0.4 * u;
			case CARPET -> 0.28 + 0.35 * u;
			case CLOTH -> 0.3 + 0.35 * u;
			case CONTAINER -> 0.45 + 0.55 * u;
			case LECTERN, BOOKS -> 0.5 + 0.4 * u;
			case HEAVY -> 0.85 + 0.5 * u;
			case CHAIN -> 0.45 + 0.5 * u;
			case FURNITURE -> 0.38 + 0.5 * u;
			case FENCE -> 0.42 + 0.55 * u;
			case YARD_WALL -> 0.9 + 0.4 * u;
			case YARD_THING -> 0.35 + 0.55 * u;
		};
	}

	/**
	 * The life of the roof bay a roof block is in, 0 to 1: the slope it lies on (by its stair's facing, else by which side
	 * of the body it is) and its bay along that slope (three or four blocks wide, offset by seed), so a roof falls in
	 * whole sections between its rafters and its holes have straight sides.
	 */
	private double bay(int i) {
		BlockState s = this.st[i];
		int x = this.xs[i];
		int z = this.zs[i];
		Direction side;
		if (s.getBlock() instanceof StairBlock) {
			side = s.getValue(StairBlock.FACING);
		} else {
			double cx = (this.body[0] + this.body[2]) / 2.0;
			double cz = (this.body[1] + this.body[3]) / 2.0;
			boolean alongX = this.body[2] - this.body[0] >= this.body[3] - this.body[1];
			side = alongX ? (z < cz ? Dirs.BACK : Dirs.FRONT) : (x < cx ? Dirs.RIGHT : Dirs.LEFT);
		}
		int width = 3 + (int) Math.floorMod(Noise.hash(this.seed, 0xBA1), 2);
		int offset = (int) Math.floorMod(Noise.hash(this.seed, 0xBA2), width);
		int along = side.getAxis() == Direction.Axis.Z ? x : z;
		int b = Math.floorDiv(along + offset, width);
		return Climate.stretch(Noise.unit(this.seed ^ 0xBA3, side.ordinal(), b, 0) * 0.9 + 0.05);
	}

	/** 1 where a wall block has wall beside it along both axes (a corner holds longest), else 0. */
	private double corner(int i) {
		boolean ax = solidWall(this.xs[i] + 1, this.ys[i], this.zs[i]) || solidWall(this.xs[i] - 1, this.ys[i], this.zs[i]);
		boolean az = solidWall(this.xs[i], this.ys[i], this.zs[i] + 1) || solidWall(this.xs[i], this.ys[i], this.zs[i] - 1);
		return ax && az ? 1 : 0;
	}

	private boolean solidWall(int x, int y, int z) {
		int j = at(x, y, z);
		return j >= 0 && (this.sg[j] == StageKind.WALLS || this.sg[j] == StageKind.FRAME)
			&& this.st[j].isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	// ------------------------------------------------------------------------------------------------ support

	/** Two-block things go together: both halves of a door, a bed, a tall plant. */
	private void pairs() {
		this.partner = new int[this.n];
		java.util.Arrays.fill(this.partner, -1);
		for (int i = 0; i < this.n; i++) {
			BlockState s = this.st[i];
			Block b = s.getBlock();
			int j = -1;
			if (b instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				j = at(this.xs[i], this.ys[i] + 1, this.zs[i]);
			} else if (b instanceof DoublePlantBlock && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
				j = at(this.xs[i], this.ys[i] + 1, this.zs[i]);
			} else if (b instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.FOOT) {
				Direction f = s.getValue(BedBlock.FACING);
				j = at(this.xs[i] + Dirs.dx(f), this.ys[i], this.zs[i] + Dirs.dz(f));
			}
			if (j >= 0 && this.st[j].getBlock() == b) {
				this.partner[i] = j;
				this.partner[j] = i;
				double l = Math.min(this.life[i], this.life[j]);
				this.life[i] = l;
				this.life[j] = l;
			}
		}
	}

	private static int[][] n18() {
		List<int[]> l = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					int nz = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
					if (nz == 1 || nz == 2) {
						l.add(new int[]{dx, dy, dz});
					}
				}
			}
		}
		return l.toArray(int[][]::new);
	}

	/** Whether a block held as {@code h} can be held by a block at offset (ox, oy, oz) from it. */
	private static boolean holdsBy(Hold h, int ox, int oy, int oz) {
		return switch (h) {
			case GENERAL -> oy <= 0;
			case RESTS -> ox == 0 && oz == 0 && oy == -1;
			case HANGS -> ox == 0 && oz == 0 && oy == 1;
			case SIDE, WALL -> oy == 0 && Math.abs(ox) + Math.abs(oz) == 1 || ox == 0 && oz == 0 && oy == -1;
		};
	}

	/** Natural ground at a cell: nothing drawn there and below the ground's surface. */
	private boolean natural(int x, int y, int z) {
		return this.d.isAbsent(x, y, z) && y < this.d.site().ground(x, z);
	}

	/**
	 * Caps every life by what holds the block up: the widest path from the ground through blocks that may hold each
	 * other (the bottleneck of lives along it). A block stands at a decay exactly while some chain of standing blocks
	 * joins it to the ground, so nothing floats at any decay and more decay only ever takes more away.
	 */
	/**
	 * The way in stands: what is under the walk from the street to the main entrance, and every block on the chain that
	 * holds it up (found by a first support pass), lasts for ever, and the walk is kept clear of growth and rubble.
	 */
	private void protectRoute() {
		LongOpenHashSet route = Access.route(this.d);
		if (route.isEmpty()) {
			return;
		}
		List<Integer> floor = new ArrayList<>();
		for (long k : route) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			for (int dy = 0; dy <= 2; dy++) {
				this.keep.add(Draft.key(x, y + dy, z));
			}
			for (int dy : new int[]{-1, 0}) {
				int j = at(x, y + dy, z);
				if (j >= 0) {
					floor.add(j);
				}
			}
		}
		if (floor.isEmpty()) {
			return;
		}
		for (int j : floor) {
			this.life[j] = NEVER;
		}
		support();
		// the chains that hold the way up
		for (int j : floor) {
			int c = j;
			int guard = 0;
			while (c >= 0 && guard++ < 4096) {
				this.life[c] = NEVER;
				int v = this.via[c];
				if (v == c) {
					break;
				}
				c = v;
			}
		}
	}

	private void support() {
		this.eff = new double[this.n];
		this.via = new int[this.n];
		java.util.Arrays.fill(this.via, -1);
		LongHeapPriorityQueue q = new LongHeapPriorityQueue(it.unimi.dsi.fastutil.longs.LongComparators.OPPOSITE_COMPARATOR);
		for (int i = 0; i < this.n; i++) {
			Hold h = this.hold[i];
			boolean grounded = false;
			for (Direction dir : Direction.values()) {
				int ox = dir.getStepX();
				int oy = dir.getStepY();
				int oz = dir.getStepZ();
				if (holdsBy(h, ox, oy, oz) && natural(this.xs[i] + ox, this.ys[i] + oy, this.zs[i] + oz)) {
					grounded = true;
					break;
				}
			}
			if (grounded) {
				this.eff[i] = this.life[i];
				this.via[i] = i;
				q.enqueue(code(this.life[i], i));
			}
		}
		while (!q.isEmpty()) {
			long c = q.dequeueLong();
			int i = (int) (c & 0xFFFFFF);
			double v = decode(c);
			if (v < quant(this.eff[i])) {
				continue;
			}
			for (int[] o : N18) {
				int j = at(this.xs[i] + o[0], this.ys[i] + o[1], this.zs[i] + o[2]);
				if (j < 0 || !holdsBy(this.hold[j], -o[0], -o[1], -o[2]) || !holds(i, j)) {
					continue;
				}
				double cand = Math.min(this.life[j], this.eff[i]);
				if (quant(cand) > quant(this.eff[j])) {
					this.eff[j] = cand;
					this.via[j] = i;
					q.enqueue(code(cand, j));
				}
			}
		}
		for (int i = 0; i < this.n; i++) {
			int j = this.partner[i];
			if (j >= 0) {
				double m = Math.min(this.eff[i], this.eff[j]);
				this.eff[i] = m;
				this.eff[j] = m;
			}
		}
	}

	/**
	 * Whether block {@code i} can hold block {@code j} up. Plants, carpets, pots and snow hold nothing (weathering turns a
	 * garden to weeds and takes the pots, and what they seemed to hold would stand on in the air), and leaves hold only
	 * leaves (a hedge); everything else holds what its place allows ({@link #holdsBy}).
	 */
	private boolean holds(int i, int j) {
		Block b = this.st[i].getBlock();
		if (b instanceof LeavesBlock) {
			return this.st[j].getBlock() instanceof LeavesBlock;
		}
		return !(b instanceof VegetationBlock || b instanceof CarpetBlock || b instanceof FlowerPotBlock || b instanceof SnowLayerBlock);
	}

	private static double quant(double v) {
		return Math.round(Math.min(v, 15) * (1 << 20)) / (double) (1 << 20);
	}

	private static long code(double v, int i) {
		return (Math.round(Math.min(v, 15) * (1 << 20)) << 24) | i;
	}

	private static double decode(long c) {
		return (c >>> 24) / (double) (1 << 20);
	}

	private boolean present(int i) {
		return this.alive[i];
	}

	/** Whether a part spans between bearings (a roof, a floor) rather than standing on them. */
	private static boolean spans(Part p) {
		return p == Part.ROOF || p == Part.FLOOR;
	}

	/** Whether a part can bear a span laid on it: walls, posts, gables, chimneys, the ground. */
	private static boolean bears(Part p) {
		return p == Part.STRUCTURE || p == Part.GABLE || p == Part.GROUND || p == Part.GROUND_FLOOR || p == Part.HEAVY || p == Part.YARD_WALL
			|| p == Part.FENCE || p == Part.BARS;
	}

	/**
	 * What stands at this decay: what has life left and is held up ({@link #support}), less the spans that would hang off
	 * their bearings further than they did when built (a roof's half whose other half fell, a strip of floor left along a
	 * gutted storey): a span cell stands only while its way to a bearing, through what is left of its span, is no more
	 * than a block longer than it was in the whole building. Then what those took with them (a lantern on their chain).
	 * Every step only takes away more as the decay rises, so the pass stays monotone and nothing floats.
	 */
	private void standing() {
		this.alive = new boolean[this.n];
		boolean[] all = new boolean[this.n];
		for (int i = 0; i < this.n; i++) {
			all[i] = this.eff[i] > 0;
			this.alive[i] = this.eff[i] > this.decay;
		}
		int[] d0 = spanDistances(all);
		int[] sides0 = spanSides(all);
		boolean pruned = false;
		boolean changed = true;
		while (changed) {
			changed = false;
			// a roof course laid on a wall (the eaves on the wall plate, the rake on the gable) falls with that wall
			for (int i = 0; i < this.n; i++) {
				if (this.alive[i] && this.part[i] == Part.ROOF) {
					int b = at(this.xs[i], this.ys[i] - 1, this.zs[i]);
					if (b >= 0 && bears(this.part[b]) && this.part[b] != Part.GROUND && !this.alive[b]) {
						this.alive[i] = false;
						changed = true;
					}
				}
			}
			int[] dd = spanDistances(this.alive);
			int[] sides = spanSides(this.alive);
			for (int i = 0; i < this.n; i++) {
				if (!this.alive[i] || !spans(this.part[i]) || d0[i] == Integer.MAX_VALUE) {
					continue;
				}
				// further from a bearing than it was built, or held from one side only where it was held from two
				boolean farther = dd[i] > d0[i] + 1;
				boolean oneSided = Integer.bitCount(sides0[i]) >= 2 && Integer.bitCount(sides[i]) < 2 && dd[i] > 1;
				if (farther || oneSided) {
					this.alive[i] = false;
					changed = true;
				}
			}
			changed |= posts();
			pruned |= changed;
		}
		if (!pruned) {
			return;
		}
		// what is no longer joined to the ground goes too
		boolean[] reached = new boolean[this.n];
		it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue q = new it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue();
		for (int i = 0; i < this.n; i++) {
			if (!this.alive[i]) {
				continue;
			}
			for (Direction dir : Direction.values()) {
				if (holdsBy(this.hold[i], dir.getStepX(), dir.getStepY(), dir.getStepZ())
					&& natural(this.xs[i] + dir.getStepX(), this.ys[i] + dir.getStepY(), this.zs[i] + dir.getStepZ())) {
					reached[i] = true;
					q.enqueue(i);
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			int i = q.dequeueInt();
			for (int[] o : N18) {
				int j = at(this.xs[i] + o[0], this.ys[i] + o[1], this.zs[i] + o[2]);
				if (j >= 0 && this.alive[j] && !reached[j] && holdsBy(this.hold[j], -o[0], -o[1], -o[2]) && holds(i, j)) {
					reached[j] = true;
					q.enqueue(j);
				}
			}
		}
		for (int i = 0; i < this.n; i++) {
			this.alive[i] &= reached[i];
		}
		if (System.getProperty("hearth.age.debug") != null) {
			int[] dd = spanDistances(this.alive);
			for (int i = 0; i < this.n; i++) {
				if (this.alive[i] && this.ys[i] >= this.eave + 1) {
					System.out.println("AGE " + this.xs[i] + "," + this.ys[i] + "," + this.zs[i] + " " + this.st[i] + " " + this.sg[i] + " " + this.part[i] + " "
						+ this.hold[i] + " life " + String.format("%.2f", this.life[i]) + " eff " + String.format("%.2f", this.eff[i]) + " d0 " + d0[i] + " dd " + dd[i]);
				}
			}
		}
		for (int i = 0; i < this.n; i++) {
			int j = this.partner[i];
			if (j >= 0 && !(this.alive[i] && this.alive[j])) {
				this.alive[i] = false;
				this.alive[j] = false;
			}
		}
	}

	/**
	 * Free-standing posts come down to the wall they rise from: the top of a wall left standing on its own (nothing beside
	 * it, nothing on it) more than a block above the wall heads round it goes, course by course. Chimney stacks may stand
	 * alone. Returns whether anything went.
	 */
	private boolean posts() {
		if (this.builtPost == null) {
			// what stood free as built (a finial, a gable's king post) is meant to: only what decay leaves free comes down
			boolean[] saved = this.alive;
			this.alive = new boolean[this.n];
			java.util.Arrays.fill(this.alive, true);
			this.builtPost = new boolean[this.n];
			for (int i = 0; i < this.n; i++) {
				this.builtPost[i] = freePost(i);
			}
			this.alive = saved;
		}
		Integer[] order = new Integer[this.n];
		for (int i = 0; i < this.n; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> this.ys[a] != this.ys[b] ? Integer.compare(this.ys[b], this.ys[a]) : Integer.compare(a, b));
		boolean any = false;
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int i : order) {
				if (this.alive[i] && !this.builtPost[i] && freePost(i)) {
					this.alive[i] = false;
					any = true;
					changed = true;
				}
			}
		}
		return any;
	}

	/** Whether a wall block stands as the free top of a post: nothing on it or beside it, two or more above the wall round it. */
	private boolean freePost(int i) {
		Part p = this.part[i];
		if (!this.alive[i] || p != Part.STRUCTURE && p != Part.GABLE || this.ys[i] < 2) {
			return false;
		}
		int above = at(this.xs[i], this.ys[i] + 1, this.zs[i]);
		if (above >= 0 && this.alive[above]) {
			return false;
		}
		int high = Integer.MIN_VALUE;
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = this.xs[i] + Dirs.dx(dir);
			int nz = this.zs[i] + Dirs.dz(dir);
			int j = at(nx, this.ys[i], nz);
			if (j >= 0 && this.alive[j] && (bears(this.part[j]) || this.part[j] == Part.CHIMNEY)) {
				return false;
			}
			for (int y = this.ys[i] - 1; y >= this.ys[i] - 4; y--) {
				int k = at(nx, y, nz);
				if (k >= 0 && this.alive[k] && (bears(this.part[k]) || this.part[k] == Part.GLASS || this.part[k] == Part.OPENING)) {
					high = Math.max(high, y);
					break;
				}
			}
		}
		return this.ys[i] - high >= 2;
	}

	/**
	 * For each span cell among {@code in}: a mask of the sides of the body (front, right, back, left) whose walls it is
	 * joined to through span cells lying on bearings there. A roof slope that has lost its other half, or a strip of
	 * floor along one wall, is held from one side only.
	 */
	private int[] spanSides(boolean[] in) {
		int[] mask = new int[this.n];
		for (int side = 0; side < 4; side++) {
			it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue q = new it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue();
			for (int i = 0; i < this.n; i++) {
				if (!in[i] || !spans(this.part[i]) || sideOf(this.xs[i], this.zs[i]) != side) {
					continue;
				}
				for (int[] o : N18) {
					if (o[1] > 0) {
						continue;
					}
					int j = at(this.xs[i] + o[0], this.ys[i] + o[1], this.zs[i] + o[2]);
					if (j >= 0 && in[j] && bears(this.part[j])) {
						mask[i] |= 1 << side;
						q.enqueue(i);
						break;
					}
				}
			}
			while (!q.isEmpty()) {
				int i = q.dequeueInt();
				for (int[] o : N18) {
					int j = at(this.xs[i] + o[0], this.ys[i] + o[1], this.zs[i] + o[2]);
					if (j >= 0 && in[j] && spans(this.part[j]) && (mask[j] & (1 << side)) == 0) {
						mask[j] |= 1 << side;
						q.enqueue(j);
					}
				}
			}
		}
		return mask;
	}

	/** The side of the body (0 front, 1 right, 2 back, 3 left) a cell is nearest. */
	private int sideOf(int x, int z) {
		int front = Math.abs(z - this.body[1]);
		int back = Math.abs(this.body[3] - z);
		int left = Math.abs(x - this.body[0]);
		int right = Math.abs(this.body[2] - x);
		int m = Math.min(Math.min(front, back), Math.min(left, right));
		return m == front ? 0 : m == right ? 1 : m == back ? 2 : 3;
	}

	/** For each span cell among {@code in}: steps through span cells to the nearest one lying on a bearing (MAX if none). */
	private int[] spanDistances(boolean[] in) {
		int[] dist = new int[this.n];
		java.util.Arrays.fill(dist, Integer.MAX_VALUE);
		it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue q = new it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue();
		for (int i = 0; i < this.n; i++) {
			if (!in[i] || !spans(this.part[i])) {
				continue;
			}
			for (int[] o : N18) {
				if (o[1] > 0) {
					continue;
				}
				int j = at(this.xs[i] + o[0], this.ys[i] + o[1], this.zs[i] + o[2]);
				if (j >= 0 && in[j] && bears(this.part[j])) {
					dist[i] = 0;
					q.enqueue(i);
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			int i = q.dequeueInt();
			for (int[] o : N18) {
				int j = at(this.xs[i] + o[0], this.ys[i] + o[1], this.zs[i] + o[2]);
				if (j >= 0 && in[j] && spans(this.part[j]) && dist[j] == Integer.MAX_VALUE) {
					dist[j] = dist[i] + 1;
					q.enqueue(j);
				}
			}
		}
		return dist;
	}

	// ------------------------------------------------------------------------------------------------ applying

	private void keepClear() {
		for (int[] e : this.d.entrances()) {
			for (int dy = -1; dy <= 2; dy++) {
				this.keep.add(Draft.key(e[0], e[1] + dy, e[2]));
			}
		}
		for (int i = 0; i < this.n; i++) {
			Block b = this.st[i].getBlock();
			if (b instanceof DoorBlock) {
				this.keep.add(Draft.key(this.xs[i], this.ys[i], this.zs[i]));
				for (Direction dir : Dirs.HORIZONTAL) {
					int x = this.xs[i] + Dirs.dx(dir);
					int z = this.zs[i] + Dirs.dz(dir);
					if (!this.d.isSolid(x, this.ys[i], z)) {
						this.keep.add(Draft.key(x, this.ys[i], z));
						this.keep.add(Draft.key(x, this.ys[i] + 1, z));
					}
				}
			} else if (b instanceof StairBlock && this.st[i].getValue(StairBlock.HALF) == Half.BOTTOM
				|| b instanceof SlabBlock && this.st[i].getValue(SlabBlock.TYPE) == SlabType.BOTTOM && this.sg[i] == StageKind.EXTERIOR
				|| b instanceof LadderBlock || b == Blocks.DIRT_PATH) {
				// steps, ladders and paths are ways through: nothing grows or falls on them
				this.keep.add(Draft.key(this.xs[i], this.ys[i], this.zs[i]));
				this.keep.add(Draft.key(this.xs[i], this.ys[i] + 1, this.zs[i]));
				this.keep.add(Draft.key(this.xs[i], this.ys[i] + 2, this.zs[i]));
			}
		}
	}

	private void setAir(int i) {
		try (var s = this.d.stage(this.sg[i])) {
			this.d.set(this.xs[i], this.ys[i], this.zs[i], Blocks.AIR.defaultBlockState());
		}
	}

	private void put(int i, BlockState s, CompoundTag tag) {
		try (var sc = this.d.stage(this.sg[i])) {
			this.d.set(this.xs[i], this.ys[i], this.zs[i], s, tag);
		}
	}

	/** What still covers each column after the collapse (for what may grow under the open sky). */
	private void covers() {
		for (int i = 0; i < this.n; i++) {
			if (!present(i)) {
				continue;
			}
			BlockState s = this.st[i];
			if (s.getBlock() instanceof VegetationBlock || s.getBlock() instanceof LeavesBlock || s.getBlock() instanceof CarpetBlock || s.getBlock() instanceof VineBlock) {
				continue;
			}
			if (this.ys[i] < 0) {
				continue;
			}
			long k = colKey(this.xs[i], this.zs[i]);
			this.cover.put(k, Math.max(this.cover.get(k), this.ys[i]));
		}
	}

	static long colKey(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private boolean open(int x, int y, int z) {
		return y > this.cover.get(colKey(x, z));
	}

	/** Weathering, cold hearths and what the household took with them, on a block that still stands. */
	private void wear(int i) {
		int x = this.xs[i];
		int y = this.ys[i];
		int z = this.zs[i];
		BlockState s = this.st[i];
		Block b = s.getBlock();
		boolean left = this.decay >= Decay.ABANDONED;
		switch (this.part[i]) {
			case CANDLE, FIRE -> {
				if (left && s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT)) {
					put(i, s.setValue(BlockStateProperties.LIT, false), this.tg[i]);
					return;
				}
			}
			case GLOW_FLOOR -> {
				if (left) {
					put(i, floorAround(x, y, z), null);
				}
				return;
			}
			case POT -> {
				if (left && b != Blocks.FLOWER_POT) {
					put(i, (unit(0x9071, x, y, z) < 0.5 ? Blocks.POTTED_DEAD_BUSH : Blocks.FLOWER_POT).defaultBlockState(), null);
				}
				return;
			}
			case CONTAINER, YARD_THING -> {
				if (left && this.tg[i] != null) {
					put(i, s, junk(x, y, z));
				}
				return;
			}
			case LECTERN -> {
				if (left && s.hasProperty(LecternBlock.HAS_BOOK)) {
					put(i, s.setValue(LecternBlock.HAS_BOOK, false), null);
				}
				return;
			}
			case DOOR -> {
				// left standing open (both halves agree: the lower half decides)
				if (left && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && unit(0xD00B, x, 0, z) < 0.5) {
					put(i, s.setValue(DoorBlock.OPEN, true), null);
					int j = this.partner[i];
					if (j >= 0) {
						put(j, this.st[j].setValue(DoorBlock.OPEN, true), null);
					}
				}
				return;
			}
			case SHUTTER -> {
				if (s.getValue(TrapDoorBlock.OPEN) && this.decay >= 0.08 + 0.5 * unit(0x5407, x, y, z)) {
					// hanging askew off one hinge
					put(i, s.setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.HALF, unit(0x5408, x, y, z) < 0.5 ? Half.TOP : Half.BOTTOM), null);
				}
				return;
			}
			case FARMLAND -> {
				if (left) {
					put(i, (this.climate.ground == Blocks.GRASS_BLOCK ? Blocks.COARSE_DIRT : this.climate.ground).defaultBlockState(), null);
				}
				return;
			}
			case PLANT -> {
				// the garden goes to weeds (a tall plant's lower half becomes a weed, its upper half goes)
				if (this.sg[i] == StageKind.EXTERIOR && this.decay >= 0.15 + 0.3 * unit(0x9A7D, x, y, z)) {
					if (b instanceof DoublePlantBlock && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
						setAir(i);
					} else if (!(b instanceof net.minecraft.world.level.block.SaplingBlock)) {
						put(i, Growth.single(this.climate.plant(unit(0x9A7E, x, y, z))), null);
					}
				}
				return;
			}
			case PATH -> {
				if (this.decay >= 0.4 + 0.45 * patch(0x9A70, x, y, z, 2.0)) {
					put(i, (this.climate.ground == Blocks.GRASS_BLOCK ? Blocks.GRASS_BLOCK : Blocks.COARSE_DIRT).defaultBlockState(), null);
				}
				return;
			}
			case GROUND_FLOOR -> {
				boolean inBody = x >= this.body[0] && x <= this.body[2] && z >= this.body[1] && z <= this.body[3];
				if (inBody && y == -1 && this.d.isEmpty(x, 0, z)) {
					double t = 0.5 + 0.45 * patch(0x2077, x, y, z, 2.4) - (open(x, 0, z) ? 0.15 : 0);
					if (this.decay >= t) {
						put(i, this.climate.soil(this.seed, x, y, z).defaultBlockState(), null);
						return;
					}
				}
			}
			default -> {
			}
		}
		// moss and cracks: from the ground and the north side up, in patches
		if (Weather.weathers(s)) {
			double low = Math.clamp(1 - (y - this.d.site().ground(x, z)) / 3.0, 0, 1);
			double rel = Math.clamp(y / (double) Math.max(1, this.top), 0, 1);
			double t = 0.06 + 0.62 * patch(0x3055, x, y, z, 2.8) + 0.22 * rel - 0.12 * low - 0.12 * north(x, z);
			if (this.decay >= t) {
				boolean mossy = low > 0.3 || north(x, z) > 0 || patch(0x3056, x, y, z, 2.0) > 0.45;
				BlockState wthr = Weather.weathered(s, mossy, this.climate.moss);
				if (wthr != null) {
					put(i, wthr, this.tg[i]);
				}
			}
		}
	}

	/** 1 on the north-facing side of the body (in the world), else 0: where moss grows first. */
	private double north(int x, int z) {
		double cx = (this.body[0] + this.body[2]) / 2.0;
		double cz = (this.body[1] + this.body[3]) / 2.0;
		Direction out = Dirs.nearest((x - cx) / Math.max(1, (this.body[2] - this.body[0]) / 2.0), (z - cz) / Math.max(1, (this.body[3] - this.body[1]) / 2.0));
		return this.d.frame().toWorld(out) == Direction.NORTH ? 1 : 0;
	}

	/** The floor laid round a cell (what a glowing recess is boarded over with once its lamp is gone). */
	private BlockState floorAround(int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			int j = at(x + Dirs.dx(dir), y, z + Dirs.dz(dir));
			if (j >= 0 && (this.part[j] == Part.GROUND_FLOOR || this.part[j] == Part.FLOOR || this.part[j] == Part.GROUND)
				&& this.st[j].isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				return this.st[j];
			}
		}
		return Blocks.COARSE_DIRT.defaultBlockState();
	}

	/** What a household leaves in a chest when it goes: odds and ends, sometimes nothing. */
	private CompoundTag junk(int x, int y, int z) {
		double u = unit(0x1007, x, y, z);
		List<Object[]> items = new ArrayList<>();
		if (u < 0.35) {
			return dev.voidmc.hearth.build.draft.BlockEntities.container(items);
		}
		String[][] odds = {{"minecraft:stick", "3"}, {"minecraft:bone", "2"}, {"minecraft:string", "2"}, {"minecraft:bowl", "1"}, {"minecraft:paper", "3"},
			{"minecraft:leather", "1"}, {"minecraft:coal", "2"}, {"minecraft:wheat_seeds", "4"}, {"minecraft:flint", "1"}, {"minecraft:feather", "2"},
			{"minecraft:rotten_flesh", "1"}, {"minecraft:glass_bottle", "1"}, {"minecraft:book", "1"}, {"minecraft:iron_nugget", "3"}};
		int count = 1 + (int) (unit(0x1008, x, y, z) * 3);
		for (int k = 0; k < count; k++) {
			String[] o = odds[(int) (unit(0x1009 + k, x, y, z) * odds.length)];
			items.add(new Object[]{o[0], 1 + (int) (unit(0x1010 + k, x, y, z) * Integer.parseInt(o[1]))});
		}
		return dev.voidmc.hearth.build.draft.BlockEntities.container(items);
	}

	/** What fell lands below it: masonry as rubble, tiles as loose courses, beams as beams, the door beside its doorway. */
	private void rubble(List<Integer> fallen, Growth g) {
		// highest first, so the last to fall lies on top
		fallen.sort((a, b) -> this.ys[a] != this.ys[b] ? Integer.compare(this.ys[b], this.ys[a]) : Integer.compare(a, b));
		for (int i : fallen) {
			Part p = this.part[i];
			int x = this.xs[i];
			int y = this.ys[i];
			int z = this.zs[i];
			if (p == Part.DOOR && this.st[i].getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				if (unit(0xD00C, x, 0, z) < 0.65) {
					Block leaf = Rubble.doorLeaf(this.st[i].getBlock());
					if (leaf != null) {
						g.fallenDoor(x, y, z, leaf);
					}
				}
				continue;
			}
			if (!p.structural() || this.decay < Decay.ABANDONED) {
				// (a house still lived in is kept: what falls off it is cleared away, it lies in nobody's path)
				continue;
			}
			BlockState s = this.st[i];
			double chance = Weather.masonry(s) ? 0.3 : Weather.timber(s) ? 0.16 : 0.12;
			if (p == Part.ROOF) {
				chance = 0.14;
			}
			// rubble lies in and round the building, not across its yard
			if (x < this.body[0] - 1 || x > this.body[2] + 1 || z > this.body[3] + 1) {
				continue;
			}
			if (unit(0x8B1E, x, y, z) >= chance) {
				continue;
			}
			BlockState piece = Rubble.piece(s, unit(0x8B1F, x, y, z), unit(0x8B20, x, y, z));
			if (piece == null) {
				continue;
			}
			// walls topple a little to either side, the rest falls straight
			int ox = 0;
			int oz = 0;
			if (p == Part.STRUCTURE || p == Part.GABLE || p == Part.CHIMNEY) {
				double v = unit(0x8B21, x, y, z);
				if (v < 0.5) {
					Direction dir = Dirs.HORIZONTAL[(int) (v * 8) & 3];
					ox = Dirs.dx(dir);
					oz = Dirs.dz(dir);
				}
			}
			g.debris(x + ox, y - 1, z + oz, piece);
		}
	}
}
