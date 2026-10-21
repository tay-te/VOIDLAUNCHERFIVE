package dev.voidmc.mettle.character;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * Teaching tomes (TREES.md §2): one rare book per skill, found in the hidden chest of the Expanse landmark TREES.md names
 * (or, without Expanse, in a fitting vanilla chest). Holding use for 5 s reads it and teaches its perk. The book is kept,
 * so a find can be passed round a party; each reader learns it once.
 */
public final class Tomes {
	public static final DataComponentType<Identifier> TEACHES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mettle.id("teaches"),
		DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());

	public static final Item TOME = register();

	/** Teaching perk → where its tome lies: the Expanse hidden chest, then the vanilla stand-in. */
	public static final Map<String, Source> SOURCES = Map.of(
		"mining/stonesense", new Source("deepstone_ruins_hidden", "ancient_city"),
		"blades/master_strike", new Source("wisteria_manor_hidden", "woodland_mansion"),
		"heavy_weapons/overhead_blow", new Source("logging_camp_hidden", "pillager_outpost"),
		"marksmanship/steppe_draw", new Source("caravanserai_hidden", "desert_pyramid"),
		"defense/roll_with_the_blow", new Source("frost_outpost_hidden", "igloo_chest"),
		"athletics/couriers_stride", new Source("palm_harbour_hidden", "shipwreck_map"),
		"survival/poultice", new Source("cloud_monastery_hidden", "jungle_temple"),
		"smithing/folded_steel", new Source("karst_pagoda_hidden", "bastion_other"),
		"stealth/the_quiet_way", new Source("gallows_hill_hidden", "simple_dungeon"),
		"speech/the_courtiers_manners", new Source("wisteria_manor", "woodland_mansion"));

	/** A landmark's hidden chest is the place to look: a fair chance. A vanilla chest is a stand-in: a smaller one. */
	public static final float EXPANSE_CHANCE = 0.3f;
	public static final float VANILLA_CHANCE = 0.08f;

	public record Source(String expanseChest, String vanillaChest) {
	}

	private Tomes() {
	}

	public static void init() {
		boolean expanse = FabricLoader.getInstance().isModLoaded("expanse");
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			Identifier id = key.identifier();
			SOURCES.forEach((perk, where) -> {
				boolean match = expanse
					? id.getNamespace().equals("expanse") && id.getPath().equals("chests/" + where.expanseChest())
					: id.getNamespace().equals("minecraft") && id.getPath().equals("chests/" + where.vanillaChest());
				if (match) {
					Identifier perkId = Mettle.id(perk);
					table.withPool(LootPool.lootPool()
						.add(LootItem.lootTableItem(TOME)
							.apply(SetComponentsFunction.setComponent(TEACHES, perkId))
							.apply(SetComponentsFunction.setComponent(DataComponents.ITEM_NAME, Component.translatable(titleKey(perkId)))))
						.when(LootItemRandomChanceCondition.randomChance(expanse ? EXPANSE_CHANCE : VANILLA_CHANCE)));
				}
			});
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> SOURCES.keySet().stream().sorted().forEach(perk -> output.accept(of(Mettle.id(perk)))));
	}

	public static ItemStack of(Identifier perk) {
		ItemStack stack = new ItemStack(TOME);
		stack.set(TEACHES, perk);
		stack.set(DataComponents.ITEM_NAME, Component.translatable(titleKey(perk)));
		return stack;
	}

	public static String titleKey(Identifier perk) {
		return "mettle.tome." + perk.getPath().substring(perk.getPath().indexOf('/') + 1);
	}

	public static ResourceKey<Perk> perk(Identifier id) {
		return ResourceKey.create(MettleRegistries.PERK, id);
	}

	private static Item register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Mettle.id("tome"));
		return Registry.register(BuiltInRegistries.ITEM, key, new TomeItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).setId(key)));
	}
}
