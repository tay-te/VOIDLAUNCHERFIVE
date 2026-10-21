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

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(GALLOWS_KNIGHT, GallowsKnight.createAttributes());
	}
}
