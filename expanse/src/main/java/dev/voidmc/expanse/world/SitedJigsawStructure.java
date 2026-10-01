package dev.voidmc.expanse.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.expanse.Expanse;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * A jigsaw structure that picks its site the way a builder would. Data files are written exactly like
 * {@code minecraft:jigsaw}, with type {@code expanse:sited_jigsaw} and three optional fields:
 * {@code max_relief}, {@code allow_water} and {@code avoid}.
 *
 * <p>Vanilla projects a jigsaw structure's start onto the surface at one column and builds there,
 * whatever is there. Water counts as surface, so a well whose grid cell falls on a pond is built on the
 * pond, and terrain adaptation heaps an island of earth under it. A narrow ravine counts too, so a
 * statue lands at the bottom of a crack with the land standing over it. And a small structure's grid
 * knows nothing of the big ones, so a campsite can land in the middle of a temple. Each time a
 * hand-made place turns into an obviously generated one.
 *
 * <p>So before building, this turns the site down if, at the start point or four points
 * {@value #REACH} blocks round it, in the generator's own terrain (no chunk needs to exist yet):
 * <ul>
 *   <li>any is under water: the surface stands above the sea floor there (unless {@code allow_water});</li>
 *   <li>the ground rises or falls more than {@code max_relief} blocks across them: a cliff edge;</li>
 *   <li>the start sits well below the ground round it: a pit or a gully;</li>
 * </ul>
 * or if a structure set listed in {@code avoid} has a grid point within {@code radius} blocks whose
 * biome would take one of that set's structures. Like vanilla's exclusion zones this reads the other
 * set's placement grid, which is arithmetic, but it also asks the biome, so a temple's grid points out
 * in the wrong biome, where no temple will ever stand, don't push anything away.
 * The grid cell then simply has no structure, as when the biome is wrong.
 */
public final class SitedJigsawStructure extends Structure {
	private record Avoid(Identifier set, int radius) {
		static final Codec<Avoid> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("other_set").forGetter(Avoid::set),
			Codec.intRange(1, 256).fieldOf("radius").forGetter(Avoid::radius)
		).apply(i, Avoid::new));
	}

	public static final MapCodec<SitedJigsawStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		JigsawStructure.CODEC.forGetter(s -> s.jigsaw),
		Codec.intRange(1, 128).optionalFieldOf("max_relief", 12).forGetter(s -> s.maxRelief),
		Codec.BOOL.optionalFieldOf("allow_water", false).forGetter(s -> s.allowWater),
		Avoid.CODEC.listOf().optionalFieldOf("avoid", List.of()).forGetter(s -> s.avoid),
		Codec.BOOL.optionalFieldOf("in_cavern", false).forGetter(s -> s.inCavern)
	).apply(i, SitedJigsawStructure::new));
	public static final StructureType<SitedJigsawStructure> TYPE = () -> CODEC;
	/** How far from the start point to look: the footprint of a small structure. */
	private static final int REACH = 8;
	/** How far below the ground round it the start may sit. */
	private static final int MAX_SINK = 4;

	private final JigsawStructure jigsaw;
	private final int maxRelief;
	private final boolean allowWater;
	private final List<Avoid> avoid;
	private final boolean inCavern;

	private SitedJigsawStructure(JigsawStructure jigsaw, int maxRelief, boolean allowWater, List<Avoid> avoid, boolean inCavern) {
		super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(), jigsaw.step(), jigsaw.terrainAdaptation()));
		this.jigsaw = jigsaw;
		this.maxRelief = maxRelief;
		this.allowWater = allowWater;
		this.avoid = List.copyOf(avoid);
		this.inCavern = inCavern;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Expanse.id("sited_jigsaw"), TYPE);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		if (this.inCavern) {
			return this.inHall(context);
		}
		return this.jigsaw.findGenerationPoint(context).filter(stub -> {
			int x = stub.position().getX();
			int z = stub.position().getZ();
			return this.goodGround(context, x, z) && this.clearOfOthers(context, x, z);
		});
	}

	/**
	 * With {@code in_cavern}: built on the floor of the cavern hall whose centre lies in this chunk
	 * ({@link dev.voidmc.expanse.world.terrain.CavernModel}), the jigsaw assembled from there; nothing
	 * where there is no hall, or no room under its roof. Placed by {@code expanse:cavern_halls}, which
	 * picks exactly those chunks.
	 */
	private Optional<GenerationStub> inHall(GenerationContext context) {
		if (!(context.chunkGenerator() instanceof dev.voidmc.expanse.world.terrain.EarthChunkGenerator earth)) {
			return Optional.empty();
		}
		var caverns = earth.model(context.randomState()).caverns();
		var chunk = context.chunkPos();
		var hall = caverns.hallNear(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), 24, 24);
		if (hall == null) {
			return Optional.empty();
		}
		var centre = caverns.info(hall.x(), hall.z());
		net.minecraft.core.BlockPos start = new net.minecraft.core.BlockPos(hall.x(), Math.round(centre.floor), hall.z());
		var j = (dev.voidmc.expanse.mixin.JigsawStructureAccessor) (Object) this.jigsaw;
		return net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement.addPieces(context, j.expanse$startPool(), j.expanse$startJigsawName(),
			j.expanse$maxDepth(), start, j.expanse$useExpansionHack(), Optional.empty(), j.expanse$maxDistanceFromCenter(),
			net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup.create(j.expanse$poolAliases(), start, context.seed()),
			j.expanse$dimensionPadding(), j.expanse$liquidSettings());
	}

	private boolean goodGround(GenerationContext context, int x, int z) {
		int[][] probes = {{0, 0}, {REACH, 0}, {-REACH, 0}, {0, REACH}, {0, -REACH}};
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int around = 0;
		int centre = 0;
		for (int[] o : probes) {
			int floor = height(context, x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG);
			if (!this.allowWater && height(context, x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG) > floor) {
				return false;
			}
			min = Math.min(min, floor);
			max = Math.max(max, floor);
			if (o[0] == 0 && o[1] == 0) {
				centre = floor;
			} else {
				around += floor;
			}
		}
		return max - min <= this.maxRelief && centre >= around / 4 - MAX_SINK;
	}

	private boolean clearOfOthers(GenerationContext context, int x, int z) {
		var sets = context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
		for (Avoid a : this.avoid) {
			Optional<Holder.Reference<StructureSet>> set = sets.get(ResourceKey.create(Registries.STRUCTURE_SET, a.set()));
			if (set.isEmpty() || !(set.get().value().placement() instanceof RandomSpreadStructurePlacement grid)) {
				continue;
			}
			// Every grid cell the radius reaches into has exactly one candidate chunk.
			int reach = (a.radius() >> 4) + 1;
			int cx = x >> 4;
			int cz = z >> 4;
			for (int gx = Math.floorDiv(cx - reach, grid.spacing()); gx <= Math.floorDiv(cx + reach, grid.spacing()); gx++) {
				for (int gz = Math.floorDiv(cz - reach, grid.spacing()); gz <= Math.floorDiv(cz + reach, grid.spacing()); gz++) {
					ChunkPos at = grid.getPotentialStructureChunk(context.seed(), gx * grid.spacing(), gz * grid.spacing());
					int ax = at.getMiddleBlockX();
					int az = at.getMiddleBlockZ();
					if (Math.max(Math.abs(ax - x), Math.abs(az - z)) <= a.radius() && couldStand(context, set.get().value(), ax, az)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Whether the biome at the surface of (x, z) would take any of the set's structures. */
	private static boolean couldStand(GenerationContext context, StructureSet set, int x, int z) {
		int y = height(context, x, z, Heightmap.Types.WORLD_SURFACE_WG);
		Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		for (StructureSet.StructureSelectionEntry entry : set.structures()) {
			if (entry.structure().value().biomes().contains(biome)) {
				return true;
			}
		}
		return false;
	}

	private static int height(GenerationContext context, int x, int z, Heightmap.Types type) {
		return context.chunkGenerator().getFirstOccupiedHeight(x, z, type, context.heightAccessor(), context.randomState());
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
