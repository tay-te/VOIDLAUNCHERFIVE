package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.expanse.Expanse;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * One small hand-made template set into the ground at the surface: a stump, a cold campfire, a cairn, a
 * lost chest. The little things that fill the land between structures (see {@link LittleThings}).
 *
 * <p>Modelled on vanilla's {@code FossilFeature} and {@code TemplateFeature}: a template is drawn from a
 * weighted list and turned and mirrored at random. Unlike them it settles on the surface the way a
 * builder would: the footprint is surveyed first, and the template is skipped over water or lava, on
 * ground that is too steep ({@code max_relief}), or on anything that is not natural ground (the
 * {@code ground} predicate, tested on the top block of every column, so not on a boulder, a log or another
 * little thing). Layer 0 of the template is the ground course: it replaces the top block of the lowest
 * column, so the thing sits in the ground, never on stilts over a dip; {@code sink} buries it deeper.
 *
 * <p>Templates are authored with their final shapes (fence connections, stair corners), as the jigsaw
 * pieces are, so they are placed with a known shape and no neighbour updates. A processor list (by id)
 * weathers each copy differently.
 */
public record SurfaceTemplateFeature(
	WeightedList<Identifier> templates, Optional<Holder<StructureProcessorList>> processors, Optional<BlockPredicate> ground,
	int maxRelief, int sink
) implements Feature {
	public static final MapCodec<SurfaceTemplateFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		WeightedList.nonEmptyCodec(Identifier.CODEC).fieldOf("templates").forGetter(SurfaceTemplateFeature::templates),
		StructureProcessorType.LIST_CODEC.optionalFieldOf("processors").forGetter(SurfaceTemplateFeature::processors),
		BlockPredicate.CODEC.optionalFieldOf("ground").forGetter(SurfaceTemplateFeature::ground),
		Codec.intRange(0, 8).optionalFieldOf("max_relief", 2).forGetter(SurfaceTemplateFeature::maxRelief),
		Codec.intRange(0, 4).optionalFieldOf("sink", 0).forGetter(SurfaceTemplateFeature::sink)
	).apply(i, SurfaceTemplateFeature::new));

	/** Little things only: anything wider would reach past the chunks a feature may write to. */
	private static final int MAX_FOOTPRINT = 24;
	private static final Mirror[] MIRRORS = Mirror.values();
	private static final Set<Identifier> REPORTED = ConcurrentHashMap.newKeySet();

	@Override
	public MapCodec<SurfaceTemplateFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Identifier id = this.templates.getRandomOrThrow(random);
		Rotation rotation = Rotation.getRandom(random);
		Mirror mirror = MIRRORS[random.nextInt(MIRRORS.length)];
		Optional<StructureTemplate> found = level.getLevel().getServer().getStructureTemplateManager().get(id);
		if (found.isEmpty()) {
			if (REPORTED.add(id)) {
				Expanse.LOG.warn("Little thing {} has no template", id);
			}
			return false;
		}
		StructureTemplate template = found.get();
		Vec3i size = template.getSize(rotation);
		if (size.getX() < 1 || size.getZ() < 1 || size.getX() > MAX_FOOTPRINT || size.getZ() > MAX_FOOTPRINT) {
			return false;
		}
		BlockPos low = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);

		// Survey the footprint: every column must be dry, natural ground, and the whole no steeper than allowed.
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		int[] surfaces = new int[size.getX() * size.getZ()];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < size.getX(); dx++) {
			for (int dz = 0; dz < size.getZ(); dz++) {
				int x = low.getX() + dx;
				int z = low.getZ() + dz;
				int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				surfaces[dx * size.getZ() + dz] = surface;
				if (surface != level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z)) {
					return false; // water, lava or powder snow over the ground
				}
				p.set(x, surface - 1, z);
				if (!this.isGround(level, p)) {
					return false;
				}
				lowest = Math.min(lowest, surface);
				highest = Math.max(highest, surface);
				if (highest - lowest > this.maxRelief) {
					return false;
				}
			}
		}
		if (lowest - 1 - this.sink <= level.getMinY()) {
			return false;
		}

		// Layer 0 replaces the top block of the lowest column.
		BlockPos zero = low.atY(lowest - 1 - this.sink);
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
		settings.addProcessor(new Settle(low, size.getZ(), zero.getY(), surfaces));
		BlockPos pos = template.getZeroPositionWithTransform(zero, mirror, rotation);
		return template.placeInWorld(level, pos, pos, settings, random, Block.UPDATE_CLIENTS);
	}

	/**
	 * On a slope the template is snapped to the lowest column, so on the higher columns its layer 1 and up
	 * fall inside the bank. Stones and logs belong there (bedded in the hillside); a flower, a fern or a moss
	 * carpet would sit at the bottom of a little pit instead, so those are left out and the bank stays whole.
	 * Runs after the weathering list, on what it made of the markers. Used only while placing, never saved.
	 */
	private record Settle(BlockPos low, int sizeZ, int zeroY, int[] surfaces) implements StructureProcessor {
		@Override
		public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos,
			BlockPos templateRelativePos, StructureTemplate.StructureBlockInfo info, StructurePlaceSettings settings) {
			int dx = info.pos().getX() - this.low.getX();
			int dz = info.pos().getZ() - this.low.getZ();
			if (info.pos().getY() <= this.zeroY || dx < 0 || dz < 0 || dz >= this.sizeZ || dx * this.sizeZ + dz >= this.surfaces.length) {
				return info;
			}
			BlockState state = info.state();
			// the top of a tall flower goes with its foot
			int y = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? info.pos().getY() - 1 : info.pos().getY();
			boolean buried = y < this.surfaces[dx * this.sizeZ + dz];
			boolean lying = state.getBlock() instanceof CarpetBlock || state.getFluidState().isEmpty() && !state.isAir()
				&& state.getCollisionShape(level, info.pos()).isEmpty();
			return buried && lying ? null : info;
		}

		@Override
		public MapCodec<? extends StructureProcessor> codec() {
			return MapCodec.unit(this);
		}
	}

	private boolean isGround(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.ICE)
			|| state.is(Blocks.POWDER_SNOW) || !state.isFaceSturdy(level, pos, Direction.UP)) {
			return false;
		}
		return this.ground.map(g -> g.test(level, pos)).orElse(true);
	}
}
