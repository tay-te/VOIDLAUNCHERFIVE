package dev.voidmc.expanse.world.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.expanse.Expanse;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

/**
 * {@code expanse:cavern_halls}: structures placed in the deep halls of the {@link CavernModel}, one start
 * per hall at most: the chunk holding the hall's centre, for a share ({@code frequency}) of the halls,
 * chosen by {@code salt}.
 *
 * <p>It is a random spread whose grid is the halls' own (one cell per {@code CavernModel.HALL_CELL}
 * blocks) and whose candidate chunk in each cell is the hall's centre rather than a random one, so
 * vanilla's structure search and {@code /locate} work on it unchanged. Where a cell has no hall, the
 * candidate is its corner chunk and the structure there finds no hall and builds nothing.
 */
public class CavernHallPlacement extends RandomSpreadStructurePlacement {
	public static final MapCodec<CavernHallPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.INT.fieldOf("salt").forGetter(CavernHallPlacement::hallSalt),
		Codec.floatRange(0, 1).optionalFieldOf("frequency", 1.0F).forGetter(CavernHallPlacement::share)
	).apply(i, CavernHallPlacement::new));

	private final int salt;
	private final float share;

	public CavernHallPlacement(int salt, float share) {
		super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(), CavernModel.HALL_CELL / 16, 1, RandomSpreadType.LINEAR);
		this.salt = salt;
		this.share = share;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Expanse.id("cavern_halls"), CODEC);
	}

	int hallSalt() {
		return this.salt;
	}

	float share() {
		return this.share;
	}

	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int sourceX, int sourceZ) {
		int hx = Math.floorDiv(sourceX, this.spacing());
		int hz = Math.floorDiv(sourceZ, this.spacing());
		CavernModel.Hall hall = TerrainModel.forSeed(TerrainModel.seedFor(seed)).caverns().hall(hx, hz);
		if (hall.exists() && PlateMap.unit(PlateMap.mix(this.salt, hx, hz)) < this.share) {
			return ChunkPos.containing(new net.minecraft.core.BlockPos(hall.x(), 0, hall.z()));
		}
		return new ChunkPos(hx * this.spacing(), hz * this.spacing());
	}

	/** The registry finds a placement's type by its codec, so this must hand back our own. */
	@Override
	@SuppressWarnings("unchecked")
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		return (MapCodec<RandomSpreadStructurePlacement>) (MapCodec<?>) CODEC;
	}
}
