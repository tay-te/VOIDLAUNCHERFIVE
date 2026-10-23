package dev.voidmc.bestiary.creature;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.danger.Danger;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bestiary's own creatures (stage 1.4): the revenant soldier. It joins the overworld's monster spawns by biome selector
 * (never by naming a biome) at a low weight, and spawns naturally only where danger is 2 or more (2 km out, or anywhere
 * at night): the dead keep to the wilder country. Without Expanse's lair anchors there are no ruin-bound spawns yet.
 */
public final class BestiaryEntities {
	public static final ResourceKey<EntityType<?>> REVENANT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Bestiary.id("revenant_soldier"));
	public static final EntityType<RevenantSoldier> REVENANT_SOLDIER = Registry.register(BuiltInRegistries.ENTITY_TYPE, REVENANT_KEY,
		EntityType.Builder.<RevenantSoldier>of(RevenantSoldier::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8)
			.build(REVENANT_KEY));

	private BestiaryEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(REVENANT_SOLDIER, RevenantSoldier.createAttributes());
		SpawnPlacements.register(REVENANT_SOLDIER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(type, level, reason, pos, random) -> Monster.checkMonsterSpawnRules(type, level, reason, pos, random)
				&& (reason != EntitySpawnReason.NATURAL || Danger.at(level.getLevel(), pos) >= 2));
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, REVENANT_SOLDIER, 6, 1, 2);
	}
}
