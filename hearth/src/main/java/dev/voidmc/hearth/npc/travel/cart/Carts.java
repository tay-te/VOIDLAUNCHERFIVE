package dev.voidmc.hearth.npc.travel.cart;

import dev.voidmc.hearth.Hearth;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The {@code hearth:cart} entity type: never saved, tracked as far as folk, updated every other tick so it rolls smoothly. */
public final class Carts {
	public static final EntityType<CartEntity> CART = register();

	private Carts() {
	}

	public static void init() {
		// loading the class registers the type
	}

	private static EntityType<CartEntity> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Hearth.id("cart"));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<CartEntity>of(CartEntity::new, MobCategory.MISC).sized(1.6F, 1.6F).clientTrackingRange(10).updateInterval(2).noSave().build(key));
	}
}
