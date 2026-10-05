package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The weight perks whose data other trees ship (TREES.md), given effect here as {@link dev.voidmc.mettle.api.WeightModifier}s.
 * They read only synced state (the character record), so tooltips on the client agree with the server.
 * <ul>
 * <li>Rock Hauler (Mining): stone, ore, raw metal and ingots weigh 40 % less.</li>
 * <li>Log Hauler (Heavy Weapons): logs, planks and stripped wood weigh 40 % less.</li>
 * <li>Armour Training (Defense, 3 ranks): armour worn in the four armour slots weighs 10 / 20 / 30 % less.</li>
 * <li>Marching Order (Defense synergy): worn armour counts half. Multiplies with Armour Training (rank 3: 35 %).</li>
 * <li>Coin Sense (the Merchant's signature): Hearth coins weigh half.</li>
 * </ul>
 */
public final class WeightPerks {
	public static final Identifier ROCK_HAULER = Mettle.id("mining/rock_hauler");
	public static final Identifier LOG_HAULER = Mettle.id("heavy_weapons/log_hauler");
	public static final Identifier ARMOUR_TRAINING = Mettle.id("defense/armour_training");
	public static final Identifier MARCHING_ORDER = Mettle.id("defense/marching_order");

	public static final float HAULER = 0.6f;
	public static final float ARMOUR_TRAINING_PER_RANK = 0.10f;
	public static final float MARCHING_ORDER_SHARE = 0.5f;
	public static final float COIN_SENSE_SHARE = 0.5f;

	private static final List<TagKey<Item>> ROCK_TAGS = List.of(c("stones"), c("cobblestones"), c("ores"), c("raw_materials"), c("ingots"),
		c("storage_blocks/raw_iron"), c("storage_blocks/raw_gold"), c("storage_blocks/raw_copper"),
		ItemTags.STONE_CRAFTING_MATERIALS, ItemTags.STONE_TOOL_MATERIALS);
	private static final Set<String> ROCK_ITEMS = Set.of("stone", "cobblestone", "andesite", "diorite", "granite", "tuff", "calcite", "deepslate",
		"cobbled_deepslate", "basalt", "smooth_basalt", "blackstone", "netherrack", "end_stone", "obsidian", "crying_obsidian", "ancient_debris",
		"raw_iron", "raw_gold", "raw_copper", "raw_iron_block", "raw_gold_block", "raw_copper_block", "iron_ingot", "gold_ingot", "copper_ingot",
		"netherite_ingot", "netherite_scrap");
	private static final List<TagKey<Item>> WOOD_TAGS = List.of(ItemTags.LOGS, ItemTags.PLANKS, c("stripped_logs"), c("stripped_woods"));
	private static final Set<String> COINS = Set.of("hearth:copper_bit", "hearth:iron_mark", "hearth:gold_crown");

	private WeightPerks() {
	}

	public static void init() {
		MettleServices.addWeightModifier((player, stack) -> Perks.has(player, ROCK_HAULER) && rock(stack) ? HAULER : 1f);
		MettleServices.addWeightModifier((player, stack) -> Perks.has(player, LOG_HAULER) && wood(stack) ? HAULER : 1f);
		MettleServices.addWeightModifier(WeightPerks::armour);
		MettleServices.addWeightModifier((player, stack) -> coin(stack) && SignaturePerks.has(player, SignaturePerks.COIN_SENSE) ? COIN_SENSE_SHARE : 1f);
	}

	private static float armour(Player player, ItemInstance stack) {
		if (!worn(player, stack)) {
			return 1f;
		}
		float m = 1f - ARMOUR_TRAINING_PER_RANK * Math.min(3, Perks.rank(player, ARMOUR_TRAINING));
		return Perks.has(player, MARCHING_ORDER) ? m * MARCHING_ORDER_SHARE : m;
	}

	/** Whether this very stack is in one of the player's four armour slots. */
	public static boolean worn(Player player, ItemInstance stack) {
		if (!(stack instanceof ItemStack s) || s.isEmpty()) {
			return false;
		}
		Equippable equippable = s.get(DataComponents.EQUIPPABLE);
		if (equippable == null) {
			return false;
		}
		EquipmentSlot slot = equippable.slot();
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && player.getItemBySlot(slot) == s;
	}

	public static boolean rock(ItemInstance stack) {
		for (TagKey<Item> tag : ROCK_TAGS) {
			if (stack.is(tag)) {
				return true;
			}
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.typeHolder().value());
		return id.getNamespace().equals("minecraft") && ROCK_ITEMS.contains(id.getPath());
	}

	public static boolean wood(ItemInstance stack) {
		for (TagKey<Item> tag : WOOD_TAGS) {
			if (stack.is(tag)) {
				return true;
			}
		}
		return false;
	}

	public static boolean coin(ItemInstance stack) {
		return COINS.contains(BuiltInRegistries.ITEM.getKey(stack.typeHolder().value()).toString());
	}

	private static TagKey<Item> c(String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
	}
}
