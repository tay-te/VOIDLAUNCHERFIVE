package dev.voidmc.hearth.npc.entity;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.HearthTab;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** The {@code hearth:folk} entity type and its spawn egg. */
public final class FolkEntities {
	/**
	 * Never saved with chunks ({@code noSave}): the resident record is the person and the entity is made again from it.
	 * Player-sized; tracked as far as players see entities.
	 */
	public static final EntityType<Folk> FOLK = register("folk",
		EntityType.Builder.of(Folk::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).noSave());

	public static final Item FOLK_SPAWN_EGG = registerEgg();

	private FolkEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(FOLK, Folk.createAttributes());
		HearthTab.add(FOLK_SPAWN_EGG);
	}

	private static EntityType<Folk> register(String name, EntityType.Builder<Folk> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Hearth.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item registerEgg() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hearth.id("folk_spawn_egg"));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(FOLK).setId(key)));
	}
}
