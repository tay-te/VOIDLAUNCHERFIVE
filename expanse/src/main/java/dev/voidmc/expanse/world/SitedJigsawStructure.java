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
 *
 * <p>With {@code peopled}, a place folk live and work in (a hamlet, a harbour, a mine camp): VOID Hearth builds living
 * versions of these, so with Hearth installed it never finds a site. A shared set then tries its other members in its
 * place, and no sight keeps clear of where it would have stood. Played alone, the Expanse keeps it. With
 * {@code abandoned} (a ruin of one of those, tools/structures/abandoned.py) it is the other way round: only with
 * Hearth, the traces of folk who left.
 *
 * <p>With {@code site}, it is built only where the land itself gives the place a reason to be, the best such spot
 * within three chunks of the grid point (read from the Earth terrain model, so nothing need be generated):
 * <ul>
 *   <li>{@code knoll}: the top of a rise standing a dozen blocks and more over the land round it (the ruined
 *       chapels and keeps, the gallows hills: where they would be seen from far off);</li>
 *   <li>{@code summit}: a mountain top, y 190 and up, the highest ground round it (the summit crosses);</li>
 *   <li>{@code pass}: a saddle between two heights, the way over a range (the pass refuges);</li>
 *   <li>{@code wood_edge}: where a wood gives onto open land (the hunters' hides);</li>
 *   <li>{@code border}: where one country gives way to another (the boundary stones).</li>
 * </ul>
 *
 * <p>A surface site also has to be clear of land another mod has claimed (Hearth's planned settlements,
 * {@link dev.voidmc.expanse.api.LandClaims}) over the whole of what would be built.
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
		Codec.BOOL.optionalFieldOf("in_cavern", false).forGetter(s -> s.inCavern),
		Codec.BOOL.optionalFieldOf("peopled", false).forGetter(s -> s.peopled),
		Codec.BOOL.optionalFieldOf("abandoned", false).forGetter(s -> s.abandoned),
		Codec.STRING.listOf().optionalFieldOf("realms", List.of()).forGetter(s -> s.realms),
		Codec.STRING.optionalFieldOf("site", "any").forGetter(s -> s.site)
	).apply(i, SitedJigsawStructure::new));
	public static final StructureType<SitedJigsawStructure> TYPE = () -> CODEC;
	/** How far from the start point to look: the footprint of a small structure. */
	private static final int REACH = 8;
	/** How far below the ground round it the start may sit. */
	private static final int MAX_SINK = 4;
	/** Whether VOID Hearth peoples the land (or {@code -Dexpanse.dev.hearth=true}, to see the world as it would). */
	private static final boolean HEARTH = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("hearth") || Boolean.getBoolean("expanse.dev.hearth");

	private final JigsawStructure jigsaw;
	private final int maxRelief;
	private final boolean allowWater;
	private final List<Avoid> avoid;
	private final boolean inCavern;
	private final boolean peopled;
	private final boolean abandoned;
	private final List<String> realms;
	private final String site;

	private SitedJigsawStructure(JigsawStructure jigsaw, int maxRelief, boolean allowWater, List<Avoid> avoid, boolean inCavern, boolean peopled,
		boolean abandoned, List<String> realms, String site) {
		super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(), jigsaw.step(), jigsaw.terrainAdaptation()));
		this.jigsaw = jigsaw;
		this.maxRelief = maxRelief;
		this.allowWater = allowWater;
		this.avoid = List.copyOf(avoid);
		this.inCavern = inCavern;
		this.peopled = peopled;
		this.abandoned = abandoned;
		this.realms = List.copyOf(realms);
		this.site = site;
	}

	/** Whether this never generates in this game: a peopled place with VOID Hearth installed, an abandoned one without. */
	public boolean standsDown() {
		return HEARTH ? this.peopled : this.abandoned;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Expanse.id("sited_jigsaw"), TYPE);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		if (this.standsDown()) {
			return Optional.empty();
		}
		if (this.inCavern) {
			return this.inHall(context);
		}
		if (!this.site.equals("any")) {
			return this.atSite(context);
		}
		// A shared grid tries its members one after another until one suits the place, and vanilla assembles the
		// whole jigsaw before it asks the biome; with a dozen or more regional members in a set, most of that work
		// would be thrown away. So ask first, where the start piece will be (at the chunk's corner, centred within
		// a piece's width of it).
		var chunk = context.chunkPos();
		if (!context.couldValidBiomeExistInTerrainColumn(chunk.getMinBlockX(), chunk.getMinBlockZ())
			&& !context.couldValidBiomeExistInTerrainColumn(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ())) {
			return Optional.empty();
		}
		return this.jigsaw.findGenerationPoint(context).filter(stub -> {
			int x = stub.position().getX();
			int z = stub.position().getZ();
			return this.goodGround(context, x, z) && this.clearOfOthers(context, x, z) && unclaimed(stub);
		});
	}

	/**
	 * With {@code in_cavern}: built on the floor of the cavern hall whose centre lies in this chunk
	 * ({@link dev.voidmc.expanse.world.terrain.CavernModel}), the jigsaw assembled from there; nothing
	 * where there is no hall, or no room under its roof. Placed by {@code expanse:cavern_halls}, which
	 * picks exactly those chunks. With {@code realms}, only in the halls of those realms (named as their biomes
	 * are: the generator paints the realms' biomes in, so a structure's own biome test never sees them).
	 */
	private Optional<GenerationStub> inHall(GenerationContext context) {
		if (!(context.chunkGenerator() instanceof dev.voidmc.expanse.world.terrain.EarthChunkGenerator earth)) {
			return Optional.empty();
		}
		var caverns = earth.model(context.randomState()).caverns();
		var chunk = context.chunkPos();
		var hall = caverns.hallNear(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), 24, 24);
		if (hall == null || !this.realms.isEmpty() && !this.realms.contains(dev.voidmc.expanse.world.biome.UndergroundBiomes.REALMS[hall.theme()])) {
			return Optional.empty();
		}
		var centre = caverns.info(hall.x(), hall.z());
		if (centre.sea) {
			return Optional.empty();     // (a buried sea has taken the realm's place: nothing stands in the water)
		}
		net.minecraft.core.BlockPos start = new net.minecraft.core.BlockPos(hall.x(), Math.round(centre.floor), hall.z());
		var j = (dev.voidmc.expanse.mixin.JigsawStructureAccessor) (Object) this.jigsaw;
		return net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement.addPieces(context, j.expanse$startPool(), j.expanse$startJigsawName(),
			j.expanse$maxDepth(), start, j.expanse$useExpansionHack(), Optional.empty(), j.expanse$maxDistanceFromCenter(),
			net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup.create(j.expanse$poolAliases(), start, context.seed()),
			j.expanse$dimensionPadding(), j.expanse$liquidSettings());
	}

	/** How far round the grid point to look for a {@code site}. */
	private static final int SITE_SEARCH = 48;

	/** With {@code site}: the best spot of its kind round the grid point, the jigsaw assembled there. */
	private Optional<GenerationStub> atSite(GenerationContext context) {
		if (!(context.chunkGenerator() instanceof dev.voidmc.expanse.world.terrain.EarthChunkGenerator earth)) {
			return Optional.empty();
		}
		var model = earth.model(context.randomState());
		var chunk = context.chunkPos();
		int bestX = 0;
		int bestZ = 0;
		float best = 0;
		for (int dx = -SITE_SEARCH; dx <= SITE_SEARCH; dx += 16) {
			for (int dz = -SITE_SEARCH; dz <= SITE_SEARCH; dz += 16) {
				int x = chunk.getMiddleBlockX() + dx;
				int z = chunk.getMiddleBlockZ() + dz;
				float score = this.siteScore(model, x, z);
				if (score > best) {
					best = score;
					bestX = x;
					bestZ = z;
				}
			}
		}
		if (best <= 0 || !context.couldValidBiomeExistInTerrainColumn(bestX, bestZ)) {
			return Optional.empty();
		}
		// The jigsaw puts its start piece's corner where it is told, so assemble it once to see where its middle falls,
		// then again (the same pieces: the random seeded alike) moved so its middle stands on the spot, at the ground's
		// height there
		var first = this.assemble(context, new net.minecraft.core.BlockPos(bestX, 0, bestZ));
		if (first.isEmpty()) {
			return Optional.empty();
		}
		var box = first.get().getPiecesBuilder().getBoundingBox();
		int sx = bestX + (bestX - box.getCenter().getX());
		int sz = bestZ + (bestZ - box.getCenter().getZ());
		int y = context.chunkGenerator().getBaseHeight(bestX, bestZ, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		return this.assemble(context, new net.minecraft.core.BlockPos(sx, y, sz)).filter(stub -> {
				int x = stub.position().getX();
				int z = stub.position().getZ();
				return this.goodGround(context, x, z) && this.clearOfOthers(context, x, z) && unclaimed(stub);
			});
	}

	/** The jigsaw assembled from {@code start} (not projected: at its height), the random seeded as for the chunk. */
	private Optional<GenerationStub> assemble(GenerationContext context, net.minecraft.core.BlockPos start) {
		context.random().setLargeFeatureSeed(context.seed(), context.chunkPos().x(), context.chunkPos().z());
		var j = (dev.voidmc.expanse.mixin.JigsawStructureAccessor) (Object) this.jigsaw;
		return net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement.addPieces(context, j.expanse$startPool(), j.expanse$startJigsawName(),
			j.expanse$maxDepth(), start, j.expanse$useExpansionHack(), Optional.empty(), j.expanse$maxDistanceFromCenter(),
			net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup.create(j.expanse$poolAliases(), start, context.seed()),
			j.expanse$dimensionPadding(), j.expanse$liquidSettings());
	}

	/** How well (x, z) suits the {@code site}: 0 not at all, more the better. */
	private float siteScore(dev.voidmc.expanse.world.terrain.TerrainModel model, int x, int z) {
		var c = model.sample(x, z);
		float h = c.height;
		if (c.underWater(1) || c.nearRiver() && c.riverDist < c.riverHalfWidth + 8) {
			return 0;
		}
		switch (this.site) {
			case "knoll", "summit" -> {
				boolean summit = this.site.equals("summit");
				if (h < (summit ? 190 : 72)) {
					return 0;
				}
				// the top: nothing higher close round it
				for (int[] o : new int[][]{{16, 0}, {-16, 0}, {0, 16}, {0, -16}}) {
					if (model.sample(x + o[0], z + o[1]).height > h + 1) {
						return 0;
					}
				}
				// standing well over the land round it
				float sum = 0;
				float max = -Float.MAX_VALUE;
				int r = summit ? 48 : 56;
				for (int k = 0; k < 8; k++) {
					double a = k * Math.PI / 4;
					float g = model.sample(x + (int) Math.round(r * Math.cos(a)), z + (int) Math.round(r * Math.sin(a))).height;
					sum += g;
					max = Math.max(max, g);
				}
				float prominence = h - sum / 8;
				if (max > h + 2 || prominence < (summit ? 25 : 12)) {
					return 0;
				}
				return prominence;
			}
			case "pass" -> {
				if (h < 110) {
					return 0;
				}
				for (int[] o : new int[][]{{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
					if (Math.abs(model.sample(x + o[0], z + o[1]).height - h) > 4) {
						return 0;
					}
				}
				float best = 0;
				for (int axis = 0; axis < 2; axis++) {
					float a = model.sample(x + (axis == 0 ? 48 : 0), z + (axis == 0 ? 0 : 48)).height;
					float b = model.sample(x - (axis == 0 ? 48 : 0), z - (axis == 0 ? 0 : 48)).height;
					float p = model.sample(x + (axis == 0 ? 0 : 48), z + (axis == 0 ? 48 : 0)).height;
					float q = model.sample(x - (axis == 0 ? 0 : 48), z - (axis == 0 ? 48 : 0)).height;
					float up = Math.min(a, b) - h;
					float down = h - Math.max(p, q);
					if (up >= 12 && down >= 8) {
						best = Math.max(best, up + down);
					}
				}
				return best;
			}
			case "wood_edge" -> {
				float w = model.woodland(x, z);
				if (w < 0.22F || w > 0.5F) {
					return 0;
				}
				float lo = Float.MAX_VALUE;
				float hi = -Float.MAX_VALUE;
				for (int[] o : new int[][]{{14, 0}, {-14, 0}, {0, 14}, {0, -14}}) {
					float v = model.woodland(x + o[0], z + o[1]);
					lo = Math.min(lo, v);
					hi = Math.max(hi, v);
				}
				return hi > 0.6F && lo < 0.15F ? 1 - Math.abs(w - 0.36F) : 0;
			}
			case "border" -> {
				return c.countryStrength < 0.2F ? 0.2F - c.countryStrength + 0.01F : 0;
			}
			default -> {
				return 0;
			}
		}
	}

	/**
	 * Whether none of what would be built stands on claimed land. Only asked while structure starts are made for a
	 * level ({@link #BUILDING}); a search for the nearest structure ({@code /locate}) has no level to ask about.
	 */
	private static boolean unclaimed(GenerationStub stub) {
		net.minecraft.server.level.ServerLevel level = BUILDING.get();
		if (level == null || !dev.voidmc.expanse.api.LandClaims.any()) {
			return true;
		}
		var box = stub.getPiecesBuilder().getBoundingBox();
		return !dev.voidmc.expanse.api.LandClaims.anyClaimed(level, box.minX(), box.minZ(), box.maxX(), box.maxZ(), 8);
	}

	/** The level whose structure starts this thread is making (set by mixin.StructureStartsMixin), or null. */
	public static final ThreadLocal<net.minecraft.server.level.ServerLevel> BUILDING = new ThreadLocal<>();

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
					// (a set that skips some of its grid points, as the pillager outposts skip four in five, only
					// counts the ones it keeps)
					if (Math.max(Math.abs(ax - x), Math.abs(az - z)) <= a.radius() && grid.applyAdditionalChunkRestrictions(at.x(), at.z(), context.seed())
						&& couldStand(context, set.get().value(), ax, az)) {
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
			if (entry.structure().value() instanceof SitedJigsawStructure s && s.standsDown()) {
				continue;
			}
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
