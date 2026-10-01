package dev.voidmc.expanse.entity;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.registry.ExpanseItems;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ExpanseEntities {
	public static final EntityType<Elk> ELK = register("elk",
		EntityType.Builder.of(Elk::new, MobCategory.CREATURE).sized(1.3F, 2.1F).eyeHeight(1.9F).clientTrackingRange(10));
	public static final EntityType<Mammoth> MAMMOTH = register("mammoth",
		EntityType.Builder.of(Mammoth::new, MobCategory.CREATURE).sized(2.4F, 3.1F).eyeHeight(2.6F).clientTrackingRange(10));
	public static final EntityType<Capybara> CAPYBARA = register("capybara",
		EntityType.Builder.of(Capybara::new, MobCategory.CREATURE).sized(0.8F, 0.85F).eyeHeight(0.7F).clientTrackingRange(10));
	public static final EntityType<Crab> CRAB = register("crab",
		EntityType.Builder.of(Crab::new, MobCategory.CREATURE).sized(0.6F, 0.4F).eyeHeight(0.3F).clientTrackingRange(8));

	private ExpanseEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(ELK, Elk.createAttributes());
		FabricDefaultAttributeRegistry.register(MAMMOTH, Mammoth.createAttributes());
		FabricDefaultAttributeRegistry.register(CAPYBARA, Capybara.createAttributes());
		FabricDefaultAttributeRegistry.register(CRAB, Crab.createAttributes());

		SpawnPlacements.register(ELK, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		SpawnPlacements.register(MAMMOTH, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(type, level, reason, pos, random) -> level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
				|| Animal.checkAnimalSpawnRules(type, level, reason, pos, random));
		SpawnPlacements.register(CAPYBARA, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		SpawnPlacements.register(CRAB, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Crab::checkCrabSpawnRules);

		ExpanseItems.registerSpawnEgg("elk_spawn_egg", ELK);
		ExpanseItems.registerSpawnEgg("mammoth_spawn_egg", MAMMOTH);
		ExpanseItems.registerSpawnEgg("capybara_spawn_egg", CAPYBARA);
		ExpanseItems.registerSpawnEgg("crab_spawn_egg", CRAB);
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Expanse.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
