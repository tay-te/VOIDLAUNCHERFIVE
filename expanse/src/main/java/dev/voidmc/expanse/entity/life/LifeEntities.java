package dev.voidmc.expanse.entity.life;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.registry.ExpanseItems;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The wild creatures' entity types. The ambient ones (insects and birds) are {@link MobCategory#AMBIENT},
 * like vanilla's bat: they come and go around the player rather than being placed with the world. Deer
 * are herd animals like the elk, {@link MobCategory#CREATURE}, placed when chunks generate and kept.
 */
public final class LifeEntities {
	public static final EntityType<Butterfly> BUTTERFLY = register("butterfly",
		EntityType.Builder.of(Butterfly::new, MobCategory.AMBIENT).sized(0.5F, 0.3F).eyeHeight(0.15F).clientTrackingRange(5).noLootTable());
	public static final EntityType<Dragonfly> DRAGONFLY = register("dragonfly",
		EntityType.Builder.of(Dragonfly::new, MobCategory.AMBIENT).sized(0.5F, 0.25F).eyeHeight(0.12F).clientTrackingRange(5).noLootTable());
	public static final EntityType<Songbird> SONGBIRD = register("songbird",
		EntityType.Builder.of(Songbird::new, MobCategory.AMBIENT).sized(0.35F, 0.5F).eyeHeight(0.4F).clientTrackingRange(6));
	public static final EntityType<Heron> HERON = register("heron",
		EntityType.Builder.of(Heron::new, MobCategory.AMBIENT).sized(0.6F, 1.35F).eyeHeight(1.25F).clientTrackingRange(8));
	public static final EntityType<Owl> OWL = register("owl",
		EntityType.Builder.of(Owl::new, MobCategory.AMBIENT).sized(0.5F, 0.9F).eyeHeight(0.75F).clientTrackingRange(8));
	public static final EntityType<Deer> DEER = register("deer",
		EntityType.Builder.of(Deer::new, MobCategory.CREATURE).sized(0.9F, 1.5F).eyeHeight(1.35F).clientTrackingRange(10));

	/** The ambient creatures, which LivingWorld's spawner tends around each player. */
	public static final List<EntityType<? extends Critter>> AMBIENT = List.of(BUTTERFLY, DRAGONFLY, SONGBIRD, HERON, OWL);

	private LifeEntities() {
	}

	public static void init() {
		LifeSounds.init();
		FabricDefaultAttributeRegistry.register(BUTTERFLY, Butterfly.createAttributes());
		FabricDefaultAttributeRegistry.register(DRAGONFLY, Dragonfly.createAttributes());
		FabricDefaultAttributeRegistry.register(SONGBIRD, Songbird.createAttributes());
		FabricDefaultAttributeRegistry.register(HERON, Heron.createAttributes());
		FabricDefaultAttributeRegistry.register(OWL, Owl.createAttributes());
		FabricDefaultAttributeRegistry.register(DEER, Deer.createAttributes());

		// The ambient creatures check where they stand themselves (see each checkSpawn).
		SpawnPlacements.register(BUTTERFLY, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Butterfly::checkSpawn);
		SpawnPlacements.register(DRAGONFLY, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Dragonfly::checkSpawn);
		SpawnPlacements.register(SONGBIRD, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Songbird::checkSpawn);
		SpawnPlacements.register(HERON, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heron::checkSpawn);
		SpawnPlacements.register(OWL, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING, Owl::checkSpawn);
		SpawnPlacements.register(DEER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);

		ExpanseItems.registerSpawnEgg("butterfly_spawn_egg", BUTTERFLY);
		ExpanseItems.registerSpawnEgg("dragonfly_spawn_egg", DRAGONFLY);
		ExpanseItems.registerSpawnEgg("songbird_spawn_egg", SONGBIRD);
		ExpanseItems.registerSpawnEgg("heron_spawn_egg", HERON);
		ExpanseItems.registerSpawnEgg("owl_spawn_egg", OWL);
		ExpanseItems.registerSpawnEgg("deer_spawn_egg", DEER);
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Expanse.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
