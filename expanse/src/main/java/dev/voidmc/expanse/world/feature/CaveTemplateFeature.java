package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.world.StructureClearance;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * One small hand-made template set on the floor of a cave: a miner's rest, a cairn with a candle, a
 * timber prop, a frozen explorer, a forager's hut under the giant mushrooms. The underground half of the
 * little things (see {@link CaveThings}); the surface half is {@link SurfaceTemplateFeature}.
 *
 * <p>The feature is handed a column (the placement's {@code in_square}) and finds the caves in it itself:
 * it scans down from {@code min_depth} under the ground to the bottom of the world for floors, rock with
 * open air over it and a roof above that, in one of its {@code biomes} (the biome is read where the thing
 * would stand, so a realm's own biome works although no vanilla biome filter would pass it; the scan only
 * reads blocks, a few hundred a column). One floor is drawn at random, then a template, turned and
 * mirrored at random, and tried on it the way a builder would:
 * <ul>
 *   <li>every column of the footprint must have natural ground within {@code max_relief} of the floor
 *       drawn (so nothing hangs over a drop or straddles a step), and the {@code ground} predicate;</li>
 *   <li>the air must be clear from the ground to the top of the template in every column (so nothing
 *       pokes into the roof or a wall, and nothing stands in water or lava);</li>
 *   <li>no structure piece (mineshafts and the cavern structures too) within {@code clearance} blocks, and
 *       nothing built within {@value #BUILT_REACH}: a mineshaft's planks and rails, a dungeon's cobblestone, a
 *       stronghold's bricks, another cave thing. (A structure's pieces are only known while its start is in the
 *       region being decorated, and a mineshaft reaches further than that.)</li>
 * </ul>
 * Up to three templates are tried before the column is given up. As on the surface, layer 0 replaces the
 * floor of the lowest column, and on the higher ones plants and carpets that would be buried are left out
 * ({@link SurfaceTemplateFeature.Settle}).
 *
 * <p>With {@code wall_depth} the template is cut into the side of the cave instead: a cell behind a door, a
 * store at the end of a timbered adit, a niche of bones. Its first {@code wall_depth} rows (template z, the
 * north side as authored) go into the rock and the rest stand in the cave in front of it. From the floor drawn,
 * the feature looks along the four directions at head height for the cave wall, turns the template to face
 * out of it, and places it only if the rows in front have ground and two blocks of open air (what stands there
 * is kept that low), and
 * the rows behind are solid rock all the way round, one block beyond the template on every side but the front
 * (so a cell never breaks into another cave, the sea or a lava lake). What the template leaves unset in there
 * stays rock: a room is hewn out, not built.
 */
public record CaveTemplateFeature(
	WeightedList<Identifier> templates, Optional<Holder<StructureProcessorList>> processors, HolderSet<Biome> biomes,
	Optional<BlockPredicate> ground, int maxRelief, int minDepth, int minY, int maxY, int clearance, int wallDepth
) implements Feature {
	public static final MapCodec<CaveTemplateFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		WeightedList.nonEmptyCodec(Identifier.CODEC).fieldOf("templates").forGetter(CaveTemplateFeature::templates),
		StructureProcessorType.LIST_CODEC.optionalFieldOf("processors").forGetter(CaveTemplateFeature::processors),
		Biome.LIST_CODEC.fieldOf("biomes").forGetter(CaveTemplateFeature::biomes),
		BlockPredicate.CODEC.optionalFieldOf("ground").forGetter(CaveTemplateFeature::ground),
		Codec.intRange(0, 4).optionalFieldOf("max_relief", 1).forGetter(CaveTemplateFeature::maxRelief),
		Codec.intRange(0, 64).optionalFieldOf("min_depth", 10).forGetter(CaveTemplateFeature::minDepth),
		Codec.intRange(-2032, 2031).optionalFieldOf("min_y", -2032).forGetter(CaveTemplateFeature::minY),
		Codec.intRange(-2032, 2031).optionalFieldOf("max_y", 2031).forGetter(CaveTemplateFeature::maxY),
		Codec.intRange(0, 32).optionalFieldOf("clearance", 4).forGetter(CaveTemplateFeature::clearance),
		Codec.intRange(0, 16).optionalFieldOf("wall_depth", 0).forGetter(CaveTemplateFeature::wallDepth)
	).apply(i, CaveTemplateFeature::new));

	/** As on the surface: anything wider would reach past the chunks a feature may write to. */
	private static final int MAX_FOOTPRINT = 24;
	/** Floors with less open air over them than this are crawlways, not worth a look. */
	private static final int MIN_ROOM = 3;
	private static final int MAX_FLOORS = 24;
	private static final int TRIES = 3;
	/** In front of a template set into the wall: the layers above the floor course that must be open. */
	private static final int FRONT_ROOM = 2;
	/** How close to anything built a cave thing may stand. */
	private static final int BUILT_REACH = 2;
	private static final Mirror[] MIRRORS = Mirror.values();
	private static final Set<Identifier> REPORTED = ConcurrentHashMap.newKeySet();
	/** With -Dexpanse.dev.caves=true, how many of each template went in (logged by the dev harness). */
	public static final Map<Identifier, AtomicInteger> PLACED = new ConcurrentHashMap<>();
	/** With -Dexpanse.dev.caves=true, where the first few of each template went in. */
	public static final Map<Identifier, List<BlockPos>> WHERE = new ConcurrentHashMap<>();
	private static final boolean COUNT = Boolean.getBoolean("expanse.dev.caves");
	/** With -Dexpanse.dev.caves=true, the time spent placing (or failing to place) cave things, and how many tries. */
	public static final java.util.concurrent.atomic.AtomicLong NANOS = new java.util.concurrent.atomic.AtomicLong();
	public static final AtomicInteger CALLS = new AtomicInteger();

	@Override
	public MapCodec<CaveTemplateFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!COUNT) {
			return this.placeHere(level, random, origin);
		}
		long start = System.nanoTime();
		boolean placed = this.placeHere(level, random, origin);
		NANOS.addAndGet(System.nanoTime() - start);
		CALLS.incrementAndGet();
		return placed;
	}

	private boolean placeHere(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int x = origin.getX();
		int z = origin.getZ();
		int top = Math.min(level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - this.minDepth, this.maxY);
		int bottom = Math.max(level.getMinY() + 1, this.minY);
		if (top <= bottom || !this.anywhere(level, x, z, top, bottom)) {
			return false;
		}

		// The floors in this column: ground under open air with a roof over it, in one of our biomes.
		int[] floors = new int[MAX_FLOORS];
		int[] rooms = new int[MAX_FLOORS];
		int found = 0;
		int open = 0;
		boolean roofed = false;
		int quart = Integer.MIN_VALUE;
		boolean ours = false;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = top; y >= bottom && found < MAX_FLOORS; y--) {
			BlockState here = level.getBlockState(p.set(x, y, z));
			if (isOpen(here)) {
				open++;
				continue;
			}
			if (roofed && open >= MIN_ROOM && here.getFluidState().isEmpty()) {
				int q = QuartPos.fromBlock(y + 1);
				if (q != quart) {
					quart = q;
					ours = this.biomes.contains(level.getNoiseBiome(QuartPos.fromBlock(x), q, QuartPos.fromBlock(z)));
				}
				if (ours) {
					floors[found] = y;
					rooms[found] = open;
					found++;
				}
			}
			roofed = true;
			open = 0;
		}
		if (found == 0) {
			return false;
		}
		int pick = random.nextInt(found);
		int floor = floors[pick];
		int room = rooms[pick];
		if (!this.isGround(level, p.set(x, floor, z))) {
			return false;
		}

		if (this.wallDepth > 0) {
			return this.placeInWall(level, random, origin, floor, p);
		}
		List<BoundingBox> boxes = null;
		for (int attempt = 0; attempt < TRIES; attempt++) {
			Identifier id = this.templates.getRandomOrThrow(random);
			Optional<StructureTemplate> loaded = level.getLevel().getServer().getStructureTemplateManager().get(id);
			if (loaded.isEmpty()) {
				if (REPORTED.add(id)) {
					Expanse.LOG.warn("Cave thing {} has no template", id);
				}
				continue;
			}
			StructureTemplate template = loaded.get();
			Rotation rotation = Rotation.getRandom(random);
			Mirror mirror = MIRRORS[random.nextInt(MIRRORS.length)];
			Vec3i size = template.getSize(rotation);
			if (size.getX() < 1 || size.getZ() < 1 || size.getX() > MAX_FOOTPRINT || size.getZ() > MAX_FOOTPRINT
				|| size.getY() - 1 > room + this.maxRelief) {
				continue;
			}
			BlockPos low = new BlockPos(x - size.getX() / 2, floor, z - size.getZ() / 2);
			int[] surfaces = this.survey(level, low, size, floor, p);
			if (surfaces == null) {
				continue;
			}
			int lowest = Integer.MAX_VALUE;
			for (int s : surfaces) {
				lowest = Math.min(lowest, s - 1);
			}
			BoundingBox box = new BoundingBox(low.getX(), lowest, low.getZ(), low.getX() + size.getX() - 1, lowest + size.getY() - 1,
				low.getZ() + size.getZ() - 1);
			if (boxes == null) {
				boxes = StructureClearance.nearbyBoxes(level, true);
			}
			if (blocked(boxes, box.inflatedBy(this.clearance)) || nearBuilt(level, box.inflatedBy(BUILT_REACH), p)) {
				continue;
			}

			return this.put(level, random, origin, id, template, rotation, mirror, low, size, lowest, surfaces);
		}
		return false;
	}

	/**
	 * Set into the side of the cave: the wall found along one of the four directions from the floor drawn, the
	 * template turned so its back rows go into it (see the class description).
	 */
	private boolean placeInWall(WorldGenLevel level, RandomSource random, BlockPos origin, int floor, BlockPos.MutableBlockPos p) {
		Identifier id = this.templates.getRandomOrThrow(random);
		Optional<StructureTemplate> loaded = level.getLevel().getServer().getStructureTemplateManager().get(id);
		if (loaded.isEmpty()) {
			if (REPORTED.add(id)) {
				Expanse.LOG.warn("Cave thing {} has no template", id);
			}
			return false;
		}
		StructureTemplate template = loaded.get();
		Vec3i authored = template.getSize();
		int depth = this.wallDepth;
		int front = authored.getZ() - depth;
		int height = authored.getY();
		if (front < 1 || authored.getX() > MAX_FOOTPRINT || authored.getZ() > MAX_FOOTPRINT) {
			return false;
		}
		List<Direction> directions = new java.util.ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
		for (int i = directions.size() - 1; i > 0; i--) {
			java.util.Collections.swap(directions, i, random.nextInt(i + 1));
		}
		int x = origin.getX();
		int z = origin.getZ();
		for (Direction facing : directions) {
			// the wall: the first thing in the way at head height, a few blocks off
			int wall = 0;
			for (int k = 1; k <= 8; k++) {
				if (!isOpen(level.getBlockState(p.set(x + facing.getStepX() * k, floor + 1, z + facing.getStepZ() * k)))) {
					wall = k;
					break;
				}
			}
			if (wall == 0) {
				continue;
			}
			Rotation rotation = Rotation.NONE;
			for (Rotation r : Rotation.values()) {
				if (r.rotate(Direction.NORTH) == facing) {
					rotation = r;
				}
			}
			Mirror mirror = random.nextBoolean() ? Mirror.NONE : Mirror.FRONT_BACK;
			// the footprint: along `facing` from the front row (wall - front) to the back (wall + depth - 1), across it
			// the template's width centred on the column
			Direction side = facing.getClockWise();
			int along0 = wall - front;
			int along1 = wall + depth - 1;
			int across0 = -(authored.getX() / 2);
			int across1 = authored.getX() - 1 + across0;
			int minX = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			for (int a : new int[]{along0, along1}) {
				for (int c : new int[]{across0, across1}) {
					minX = Math.min(minX, x + facing.getStepX() * a + side.getStepX() * c);
					minZ = Math.min(minZ, z + facing.getStepZ() * a + side.getStepZ() * c);
				}
			}
			Vec3i size = template.getSize(rotation);
			BlockPos low = new BlockPos(minX, floor, minZ);
			int[] surfaces = this.surveyWall(level, low, size, floor, height, x, z, facing, wall, depth, side, across0, across1, p);
			if (surfaces == null) {
				continue;
			}
			int lowest = surfaces[surfaces.length - 1];
			int[] columns = java.util.Arrays.copyOf(surfaces, surfaces.length - 1);
			BoundingBox box = new BoundingBox(low.getX(), lowest, low.getZ(), low.getX() + size.getX() - 1, lowest + size.getY() - 1,
				low.getZ() + size.getZ() - 1);
			if (blocked(StructureClearance.nearbyBoxes(level, true), box.inflatedBy(this.clearance))
				|| nearBuilt(level, box.inflatedBy(BUILT_REACH), p)) {
				return false;
			}
			return this.put(level, random, origin, id, template, rotation, mirror, low, size, lowest, columns);
		}
		return false;
	}

	/**
	 * For a template set into the wall: the ground in each column of the footprint as {@link #survey} gives it
	 * (the rows in the rock count as level with the lowest), with the lowest ground appended; null if the front
	 * rows have no ground or no room, or the back rows and the shell round them are not all solid rock.
	 */
	private int[] surveyWall(WorldGenLevel level, BlockPos low, Vec3i size, int floor, int height, int x, int z, Direction facing,
		int wall, int depth, Direction side, int across0, int across1, BlockPos.MutableBlockPos p) {
		int[] surfaces = new int[size.getX() * size.getZ() + 1];
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		boolean[] inside = new boolean[size.getX() * size.getZ()];
		for (int dx = 0; dx < size.getX(); dx++) {
			for (int dz = 0; dz < size.getZ(); dz++) {
				int cx = low.getX() + dx;
				int cz = low.getZ() + dz;
				int along = (cx - x) * facing.getStepX() + (cz - z) * facing.getStepZ();
				if (along >= wall) {
					inside[dx * size.getZ() + dz] = true;
					continue;
				}
				int ground = Integer.MIN_VALUE;
				for (int y = floor + this.maxRelief; y >= floor - this.maxRelief; y--) {
					if (isOpen(level.getBlockState(p.set(cx, y + 1, cz))) && this.isGround(level, p.set(cx, y, cz))) {
						ground = y;
						break;
					}
				}
				if (ground == Integer.MIN_VALUE) {
					return null;
				}
				surfaces[dx * size.getZ() + dz] = ground + 1;
				lowest = Math.min(lowest, ground);
				highest = Math.max(highest, ground);
				if (highest - lowest > this.maxRelief) {
					return null;
				}
			}
		}
		if (lowest == Integer.MAX_VALUE) {
			return null;
		}
		int ceiling = lowest + height - 1;
		// in front of the wall only head room is asked for: a cave's walls lean in overhead, and what stands in
		// front of a cell (a lamp, a doorstep, rails) is kept low
		int front = Math.min(ceiling, lowest + FRONT_ROOM);
		for (int dx = 0; dx < size.getX(); dx++) {
			for (int dz = 0; dz < size.getZ(); dz++) {
				int i = dx * size.getZ() + dz;
				if (inside[i]) {
					surfaces[i] = lowest + 1;
					continue;
				}
				for (int y = surfaces[i]; y <= front; y++) {
					if (!isOpen(level.getBlockState(p.set(low.getX() + dx, y, low.getZ() + dz)))) {
						return null;
					}
				}
			}
		}
		// the rock: the back rows and a shell one block round them (behind, both sides, under and over)
		for (int a = wall; a <= wall + depth; a++) {
			for (int c = across0 - 1; c <= across1 + 1; c++) {
				int cx = x + facing.getStepX() * a + side.getStepX() * c;
				int cz = z + facing.getStepZ() * a + side.getStepZ() * c;
				for (int y = lowest - 1; y <= ceiling + 1; y++) {
					BlockState rock = level.getBlockState(p.set(cx, y, cz));
					if (isOpen(rock) || !rock.getFluidState().isEmpty() || rock.is(BlockTags.LEAVES) || rock.is(BlockTags.LOGS)) {
						return null;
					}
				}
			}
		}
		surfaces[surfaces.length - 1] = lowest;
		return surfaces;
	}

	private boolean put(WorldGenLevel level, RandomSource random, BlockPos origin, Identifier id, StructureTemplate template, Rotation rotation,
		Mirror mirror, BlockPos low, Vec3i size, int lowest, int[] surfaces) {
		BlockPos zero = low.atY(lowest);
		ChunkPos chunk = ChunkPos.containing(origin);
		BoundingBox writable = new BoundingBox(chunk.getMinBlockX() - 16, level.getMinY(), chunk.getMinBlockZ() - 16,
			chunk.getMaxBlockX() + 16, level.getMaxY(), chunk.getMaxBlockZ() + 16);
		StructurePlaceSettings settings = new StructurePlaceSettings()
			.setRotation(rotation)
			.setMirror(mirror)
			.setBoundingBox(writable)
			.setRandom(random)
			.setKnownShape(true);
		this.processors.ifPresent(list -> list.value().list().forEach(settings::addProcessor));
		settings.addProcessor(new SurfaceTemplateFeature.Settle(low, size.getZ(), lowest, surfaces));
		BlockPos pos = template.getZeroPositionWithTransform(zero, mirror, rotation);
		boolean placed = template.placeInWorld(level, pos, pos, settings, random, Block.UPDATE_CLIENTS);
		if (placed && COUNT) {
			PLACED.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
			List<BlockPos> at = WHERE.computeIfAbsent(id, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
			if (at.size() < 4) {
				at.add(new BlockPos(low.getX() + size.getX() / 2, lowest + 1, low.getZ() + size.getZ() / 2));
			}
		}
		return placed;
	}

	/**
	 * Whether any of the column between {@code top} and {@code bottom} lies in one of our biomes: biomes are
	 * stored every four blocks, so this costs a quarter of the scan and spares most columns a realm's things
	 * will never stand in.
	 */
	private boolean anywhere(WorldGenLevel level, int x, int z, int top, int bottom) {
		int qx = QuartPos.fromBlock(x);
		int qz = QuartPos.fromBlock(z);
		for (int q = QuartPos.fromBlock(top); q >= QuartPos.fromBlock(bottom); q--) {
			if (this.biomes.contains(level.getNoiseBiome(qx, q, qz))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The first open block over the ground in each column of the footprint (index {@code dx * sizeZ + dz}),
	 * or null if the template does not fit: a column with no ground near {@code floor}, ground too uneven,
	 * or anything in the way between the ground and the top of the template.
	 */
	private int[] survey(WorldGenLevel level, BlockPos low, Vec3i size, int floor, BlockPos.MutableBlockPos p) {
		int[] surfaces = new int[size.getX() * size.getZ()];
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int dx = 0; dx < size.getX(); dx++) {
			for (int dz = 0; dz < size.getZ(); dz++) {
				int x = low.getX() + dx;
				int z = low.getZ() + dz;
				int ground = Integer.MIN_VALUE;
				for (int y = floor + this.maxRelief; y >= floor - this.maxRelief; y--) {
					if (isOpen(level.getBlockState(p.set(x, y + 1, z))) && this.isGround(level, p.set(x, y, z))) {
						ground = y;
						break;
					}
				}
				if (ground == Integer.MIN_VALUE) {
					return null;
				}
				surfaces[dx * size.getZ() + dz] = ground + 1;
				lowest = Math.min(lowest, ground);
				highest = Math.max(highest, ground);
				if (highest - lowest > this.maxRelief) {
					return null;
				}
			}
		}
		int ceiling = lowest + size.getY() - 1;
		for (int dx = 0; dx < size.getX(); dx++) {
			for (int dz = 0; dz < size.getZ(); dz++) {
				for (int y = surfaces[dx * size.getZ() + dz]; y <= ceiling; y++) {
					if (!isOpen(level.getBlockState(p.set(low.getX() + dx, y, low.getZ() + dz)))) {
						return null;
					}
				}
			}
		}
		return surfaces;
	}

	private static boolean nearBuilt(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos p) {
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					if (built(level.getBlockState(p.set(x, y, z)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Something people made: nothing the land or the cave decoration puts underground. */
	private static boolean built(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_STAIRS)
			|| s.is(BlockTags.RAILS) || s.is(BlockTags.WOOL) || s.is(BlockTags.BEDS) || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS)
			|| s.is(BlockTags.STONE_BRICKS) || s.is(BlockTags.CANDLES) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)
			|| s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.DEEPSLATE_BRICKS) || s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.TUFF_BRICKS)
			|| s.is(Blocks.POLISHED_TUFF) || s.is(Blocks.SPAWNER) || s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.LANTERN)
			|| s.is(Blocks.SOUL_LANTERN) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LADDER) || s.is(Blocks.IRON_BARS)
			|| s.is(Blocks.BOOKSHELF) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.DECORATED_POT);
	}

	private static boolean blocked(List<BoundingBox> boxes, BoundingBox box) {
		for (BoundingBox b : boxes) {
			if (b.intersects(box)) {
				return true;
			}
		}
		return false;
	}

	/** Air, or something a builder would clear by hand: a moss carpet, a glowcap, a little snow. No water, no lava. */
	private static boolean isOpen(BlockState state) {
		return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	private boolean isGround(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.POWDER_SNOW)
			|| state.is(Blocks.POINTED_DRIPSTONE) || !state.isFaceSturdy(level, pos, Direction.UP)) {
			return false;
		}
		return this.ground.map(g -> g.test(level, pos)).orElse(true);
	}
}
