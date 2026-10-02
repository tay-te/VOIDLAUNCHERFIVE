package dev.voidmc.expanse.world;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.entity.life.Critter;
import dev.voidmc.expanse.entity.life.LifeEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Life in the land: who lives where, and the spawner that keeps the small creatures about.
 *
 * <p><b>Where.</b> Everything is chosen by biome tag or by climate through Fabric's biome modifications,
 * never by naming biomes, so biomes added later are covered the moment they join vanilla's tags (the
 * Expanse biomes join their analog's) or simply have the right climate:
 * <ul>
 *   <li>Songbirds, butterflies, dragonflies and herons live wherever the climate suits them, on any land
 *       where rain falls (or savanna); their own spawn rules then find the flowers, shallows and treetops.
 *       Owls and deer keep to the habitats in {@code #expanse:habitat/owls} and {@code /deer}.</li>
 *   <li>The Earth terrain's rivers cross every biome, but vanilla's fish only live in the river and ocean
 *       biomes. So salmon (and some cod) join the cool and temperate land biomes and tropical fish the hot
 *       ones, and they swim up whatever river crosses them (SurfaceWaterSpawnMixin lets them spawn in
 *       rivers above sea level). Frogs join the wet land biomes, spawning beside water
 *       (FrogSpawnMixin), and the river biome; turtles the jungles; rabbits and foxes the plains and
 *       woods vanilla leaves without them.</li>
 *   <li>Firefly bushes (vanilla's fireflies, at dusk and in deep shade) along the banks of every
 *       temperate and warm river, and in wet woods.</li>
 * </ul>
 *
 * <p><b>The spawner.</b> Vanilla spawns ambient mobs at a random height and caps them at 15 per player,
 * shared with the bats in the caves below, so a butterfly would be a rare sight a hundred blocks away.
 * These are scenery: they matter within sight, near the surface. So once a second, for each player, this
 * picks a couple of surface columns 20 to 48 blocks off, reads the ambient spawn list there (the same
 * biome data vanilla reads) and spawns the first of these creatures whose own rules the spot satisfies,
 * up to a budget of {@link #BUDGET} within 48 blocks and a cap per kind. They leave again once nobody is
 * near (see {@link Critter}). Vanilla's own ambient spawning still applies to them too, within the rules.
 */
public final class LivingWorld {
	private static final TagKey<Biome> OWLS = tag("habitat/owls");
	private static final TagKey<Biome> DEER = tag("habitat/deer");
	private static final TagKey<Biome> C_CAVE = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_cave"));
	private static final TagKey<Biome> C_UNDERGROUND = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_underground"));
	private static final TagKey<Biome> C_PLAINS = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_plains"));
	private static final TagKey<Biome> C_SWAMP = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_swamp"));
	private static final TagKey<Biome> REALMS = tag("realms");

	private static final ResourceKey<PlacedFeature> RIVERBANK_FIREFLIES = placed("living/riverbank_fireflies");
	private static final ResourceKey<PlacedFeature> WOODLAND_FIREFLIES = placed("living/woodland_fireflies");

	/** Ambient creatures allowed within 48 blocks of a player, all kinds together. */
	public static final int BUDGET = 16;
	/** And of each kind. */
	private static final Map<EntityType<?>, Integer> CAPS = Map.of(LifeEntities.BUTTERFLY, 8, LifeEntities.DRAGONFLY, 6, LifeEntities.SONGBIRD, 10,
		LifeEntities.HERON, 2, LifeEntities.OWL, 3);

	private LivingWorld() {
	}

	public static void init() {
		LifeEntities.init();
		ambientLife();
		vanillaLife();
		fireflies();
		ServerTickEvents.END_LEVEL_TICK.register(LivingWorld::tick);
	}

	// ------------------------------------------------------------------ where things live

	private static void ambientLife() {
		Predicate<BiomeSelectionContext> rainyOrSavanna = rainy().or(tagged(BiomeTags.IS_SAVANNA));
		BiomeModifications.addSpawn(land().and(rainyOrSavanna), MobCategory.AMBIENT, LifeEntities.SONGBIRD, 10, 2, 5);
		BiomeModifications.addSpawn(land().and(warmerThan(0.5F)).and(rainyOrSavanna), MobCategory.AMBIENT, LifeEntities.BUTTERFLY, 8, 2, 4);
		BiomeModifications.addSpawn(inland().and(warmerThan(0.5F)), MobCategory.AMBIENT, LifeEntities.DRAGONFLY, 6, 1, 3);
		BiomeModifications.addSpawn(inland().and(warmerThan(0.3F)), MobCategory.AMBIENT, LifeEntities.HERON, 4, 1, 1);
		BiomeModifications.addSpawn(surface().and(tagged(OWLS)), MobCategory.AMBIENT, LifeEntities.OWL, 6, 1, 1);
		BiomeModifications.addSpawn(surface().and(tagged(DEER)).and(BiomeSelectors.excludeByKey(Biomes.PALE_GARDEN)), MobCategory.CREATURE,
			LifeEntities.DEER, 7, 2, 4);
	}

	/** Vanilla's own creatures, where vanilla leaves them out. */
	private static void vanillaLife() {
		// Fish up every river: salmon in cool and temperate country, some cod in the mild, tropical fish in the hot.
		BiomeModifications.addSpawn(land().and(colderThan(0.95F)).and(BiomeSelectors.spawnsOneOf(EntityTypes.SALMON).negate()),
			MobCategory.WATER_AMBIENT, EntityTypes.SALMON, 10, 2, 5);
		BiomeModifications.addSpawn(land().and(warmerThan(0.3F)).and(colderThan(0.95F)).and(BiomeSelectors.spawnsOneOf(EntityTypes.COD).negate()),
			MobCategory.WATER_AMBIENT, EntityTypes.COD, 4, 2, 4);
		BiomeModifications.addSpawn(land().and(warmerThan(0.95F)).and(BiomeSelectors.spawnsOneOf(EntityTypes.TROPICAL_FISH).negate()),
			MobCategory.WATER_AMBIENT, EntityTypes.TROPICAL_FISH, 12, 3, 6);
		// Frogs by the water (the mixin keeps them within a few blocks of it outside the swamps).
		BiomeModifications.addSpawn(land().and(rainy()).and(BiomeSelectors.spawnsOneOf(EntityTypes.FROG).negate()),
			MobCategory.CREATURE, EntityTypes.FROG, 6, 2, 4);
		BiomeModifications.addSpawn(surface().and(tagged(BiomeTags.IS_RIVER)).and(warmerThan(0.1F)),
			MobCategory.CREATURE, EntityTypes.FROG, 8, 2, 4);
		// Turtles on the sandbars of jungle rivers near the sea.
		BiomeModifications.addSpawn(surface().and(tagged(BiomeTags.IS_JUNGLE).or(BiomeSelectors.includeByKey(Biomes.MANGROVE_SWAMP)))
			.and(BiomeSelectors.spawnsOneOf(EntityTypes.TURTLE).negate()), MobCategory.CREATURE, EntityTypes.TURTLE, 3, 2, 4);
		// Rabbits in the plains and woods, foxes in the broadleaf woods.
		Predicate<BiomeSelectionContext> openWoods = surface().and(tagged(C_PLAINS).or(tagged(BiomeTags.IS_FOREST)))
			.and(BiomeSelectors.excludeByKey(Biomes.PALE_GARDEN));
		BiomeModifications.addSpawn(openWoods.and(BiomeSelectors.spawnsOneOf(EntityTypes.RABBIT).negate()), MobCategory.CREATURE, EntityTypes.RABBIT, 4, 2, 3);
		BiomeModifications.addSpawn(surface().and(tagged(BiomeTags.IS_FOREST)).and(BiomeSelectors.excludeByKey(Biomes.PALE_GARDEN))
			.and(BiomeSelectors.spawnsOneOf(EntityTypes.FOX).negate()), MobCategory.CREATURE, EntityTypes.FOX, 2, 1, 2);
	}

	private static void fireflies() {
		Predicate<BiomeSelectionContext> banks = surface().and(c -> !c.hasTag(BiomeTags.IS_OCEAN) && !c.hasTag(BiomeTags.IS_DEEP_OCEAN)
			&& !c.hasTag(BiomeTags.IS_BEACH)).and(warmerThan(0.3F)).and(rainy());
		Predicate<BiomeSelectionContext> woods = surface().and(tagged(BiomeTags.IS_FOREST).or(tagged(BiomeTags.IS_JUNGLE)).or(tagged(C_SWAMP))
				.or(tagged(BiomeTags.IS_TAIGA).and(warmerThan(0.2F))))
			.and(BiomeSelectors.excludeByKey(Biomes.PALE_GARDEN));
		// One modification, applied after the ground cover (expanse:foliage sorts before it), adding these two
		// placed features of our own in a fixed order, so every biome lists them alike.
		BiomeModifications.create(Expanse.id("living_world")).add(ModificationPhase.ADDITIONS, banks.or(woods), (selection, context) -> {
			if (banks.test(selection)) {
				context.getGenerationSettings().addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, RIVERBANK_FIREFLIES);
			}
			if (woods.test(selection)) {
				context.getGenerationSettings().addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, WOODLAND_FIREFLIES);
			}
		});
	}

	// ------------------------------------------------------------------ biome selectors

	/** An overworld biome on the surface: not a cave biome, not one of the realms underground. */
	private static Predicate<BiomeSelectionContext> surface() {
		return c -> c.hasTag(BiomeTags.IS_OVERWORLD) && !c.hasTag(C_CAVE) && !c.hasTag(C_UNDERGROUND) && !c.hasTag(REALMS);
	}

	/** Dry land: no sea, beach or river biome. */
	private static Predicate<BiomeSelectionContext> land() {
		return surface().and(c -> !c.hasTag(BiomeTags.IS_OCEAN) && !c.hasTag(BiomeTags.IS_DEEP_OCEAN) && !c.hasTag(BiomeTags.IS_RIVER)
			&& !c.hasTag(BiomeTags.IS_BEACH));
	}

	/** Land or river: anywhere fresh water runs. */
	private static Predicate<BiomeSelectionContext> inland() {
		return surface().and(c -> !c.hasTag(BiomeTags.IS_OCEAN) && !c.hasTag(BiomeTags.IS_DEEP_OCEAN) && !c.hasTag(BiomeTags.IS_BEACH));
	}

	private static Predicate<BiomeSelectionContext> rainy() {
		return c -> c.getBiome().hasPrecipitation();
	}

	private static Predicate<BiomeSelectionContext> warmerThan(float t) {
		return c -> c.getBiome().getBaseTemperature() >= t;
	}

	private static Predicate<BiomeSelectionContext> colderThan(float t) {
		return c -> c.getBiome().getBaseTemperature() < t;
	}

	private static Predicate<BiomeSelectionContext> tagged(TagKey<Biome> tag) {
		return c -> c.hasTag(tag);
	}

	private static TagKey<Biome> tag(String path) {
		return TagKey.create(Registries.BIOME, Expanse.id(path));
	}

	private static ResourceKey<PlacedFeature> placed(String path) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Expanse.id(path));
	}

	// ------------------------------------------------------------------ the spawner

	private static void tick(ServerLevel level) {
		if ((level.getGameTime() + 7) % 20 != 0 || !level.dimensionType().hasSkyLight() || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				spawnAround(level, player);
			}
		}
	}

	private static void spawnAround(ServerLevel level, ServerPlayer player) {
		List<Critter> near = level.getEntitiesOfClass(Critter.class, player.getBoundingBox().inflate(48.0, 32.0, 48.0), c -> true);
		if (near.size() >= BUDGET) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 2; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 20.0 + random.nextDouble() * 28.0;
			int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
			BlockPos column = new BlockPos(x, player.getBlockY(), z);
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (Math.abs(ground - player.getY()) > 40 && Math.abs(canopy - player.getY()) > 40) {
				continue;
			}
			BlockPos pos = new BlockPos(x, ground, z);
			List<Weighted<MobSpawnSettings.SpawnerData>> listed = level.environmentAttributes()
				.getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos).getMobsToSpawn(MobCategory.AMBIENT).unwrap();
			for (MobSpawnSettings.SpawnerData data : ours(listed, random)) {
				int have = (int) near.stream().filter(c -> c.getType() == data.type()).count();
				int cap = CAPS.getOrDefault(data.type(), 4);
				if (have >= cap) {
					continue;
				}
				List<BlockPos> spots = canopy > ground ? List.of(pos.atY(canopy), pos) : List.of(pos);
				for (BlockPos at : spots) {
					if (SpawnPlacements.checkSpawnRules(data.type(), level, EntitySpawnReason.NATURAL, at, random)) {
						spawnGroup(level, data, at, at.getY() == canopy && canopy > ground, Math.min(cap - have, BUDGET - near.size()), random);
						return;
					}
				}
			}
		}
	}

	/** This mod's ambient creatures from a spawn list, in a weighted random order. */
	private static List<MobSpawnSettings.SpawnerData> ours(List<Weighted<MobSpawnSettings.SpawnerData>> listed, RandomSource random) {
		List<Weighted<MobSpawnSettings.SpawnerData>> pool = new ArrayList<>();
		for (Weighted<MobSpawnSettings.SpawnerData> w : listed) {
			if (CAPS.containsKey(w.value().type())) {
				pool.add(w);
			}
		}
		List<MobSpawnSettings.SpawnerData> order = new ArrayList<>(pool.size());
		while (!pool.isEmpty()) {
			int total = 0;
			for (Weighted<MobSpawnSettings.SpawnerData> w : pool) {
				total += w.weight();
			}
			int roll = random.nextInt(Math.max(1, total));
			for (int i = 0; i < pool.size(); i++) {
				roll -= pool.get(i).weight();
				if (roll < 0 || i == pool.size() - 1) {
					order.add(pool.remove(i).value());
					break;
				}
			}
		}
		return order;
	}

	private static void spawnGroup(ServerLevel level, MobSpawnSettings.SpawnerData data, BlockPos first, boolean onCanopy, int room, RandomSource random) {
		int count = Math.min(data.count().sample(random), room);
		SpawnGroupData group = null;
		for (int i = 0; i < count; i++) {
			BlockPos at = first;
			if (i > 0) {
				int x = first.getX() + random.nextInt(7) - 3;
				int z = first.getZ() + random.nextInt(7) - 3;
				at = new BlockPos(x, level.getHeight(onCanopy ? Heightmap.Types.MOTION_BLOCKING : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				if (!level.isPositionEntityTicking(at) || !SpawnPlacements.checkSpawnRules(data.type(), level, EntitySpawnReason.NATURAL, at, random)) {
					continue;
				}
			}
			Entity entity = data.type().create(level, EntitySpawnReason.NATURAL);
			if (!(entity instanceof Critter critter)) {
				return;
			}
			// Insects appear on the wing, a little above where they were placed.
			double lift = critter.isNoGravity() ? 0.5 + random.nextDouble() : 0.0;
			critter.snapTo(at.getX() + 0.5, at.getY() + lift, at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(critter)) {
				critter.discard();
				continue;
			}
			group = critter.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, group);
			level.addFreshEntity(critter);
		}
	}
}
