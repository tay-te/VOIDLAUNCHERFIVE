package dev.voidmc.bestiary.anim;

import dev.voidmc.bestiary.Bestiary;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The animation lane's half of Bestiary (stage 1A): VOID's own creatures with custom rigs. Kept apart from
 * {@link Bestiary} so the combat lane's work there doesn't collide (docs/ANIM_EVENTS.md).
 */
public final class BestiaryAnim implements ModInitializer {
	public static final ResourceKey<EntityType<?>> GALLOWS_KNIGHT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Bestiary.id("gallows_knight"));
	public static final EntityType<GallowsKnight> GALLOWS_KNIGHT = Registry.register(BuiltInRegistries.ENTITY_TYPE, GALLOWS_KNIGHT_KEY,
		EntityType.Builder.<GallowsKnight>of(GallowsKnight::new, MobCategory.MONSTER).sized(1.2F, 2.8F).eyeHeight(2.45F).clientTrackingRange(10)
			.build(GALLOWS_KNIGHT_KEY));

	public static final ResourceKey<EntityType<?>> GIBBET_CAGE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Bestiary.id("gibbet_cage"));
	/** The cage the knight tears off its back in phase 2: a rolling hazard (BESTIARY.md §8.3). */
	public static final EntityType<dev.voidmc.bestiary.boss.GibbetCage> GIBBET_CAGE = Registry.register(BuiltInRegistries.ENTITY_TYPE, GIBBET_CAGE_KEY,
		EntityType.Builder.<dev.voidmc.bestiary.boss.GibbetCage>of(dev.voidmc.bestiary.boss.GibbetCage::new, MobCategory.MISC).sized(1.0F, 0.9F)
			.clientTrackingRange(10).build(GIBBET_CAGE_KEY));

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(GALLOWS_KNIGHT, GallowsKnight.createAttributes());
		FabricDefaultAttributeRegistry.register(GIBBET_CAGE, dev.voidmc.bestiary.boss.GibbetCage.createAttributes());
		dev.voidmc.bestiary.api.BestiaryApi.registerBehaviour(Bestiary.id("knight"), new dev.voidmc.bestiary.boss.KnightMoves());
		dev.voidmc.bestiary.boss.KnightScript.init();
	}
}
