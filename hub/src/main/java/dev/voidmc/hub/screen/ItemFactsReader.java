package dev.voidmc.hub.screen;

import dev.voidmc.hub.model.ItemFacts;
import dev.voidmc.hub.model.StackView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;

/** Reads an {@link ItemStack} into the plain {@link ItemFacts} the card and the planners use. */
public final class ItemFactsReader {

	private ItemFactsReader() {
	}

	public static ItemFacts read(ItemStack stack) {
		ItemFacts.Category category = category(stack);
		ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double damage = Double.NaN;
		double speed = Double.NaN;
		if (hasModifier(mods, Attributes.ATTACK_DAMAGE)) {
			// Player base attack damage is 1 and base attack speed 4, as the vanilla tooltip adds.
			damage = mods.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
			speed = mods.compute(Attributes.ATTACK_SPEED, 4.0, EquipmentSlot.MAINHAND);
		}
		int armor = 0;
		Equippable eq = stack.get(DataComponents.EQUIPPABLE);
		if (eq != null && eq.slot().isArmor()) {
			armor = (int) Math.round(mods.compute(Attributes.ARMOR, 0.0, eq.slot()));
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		List<ItemFacts.Enchant> enchants = new ArrayList<>();
		ItemEnchantments ench = stack.getEnchantments();
		for (var entry : ench.entrySet()) {
			Holder<Enchantment> holder = entry.getKey();
			enchants.add(new ItemFacts.Enchant(holder.value().description().getString(), entry.getIntValue(), holder.value().getMaxLevel()));
		}
		enchants.sort(Comparator.comparing(ItemFacts.Enchant::name));
		return new ItemFacts(
			stack.getHoverName().getString(),
			stack.getRarity().getSerializedName(),
			category,
			stack.getCount(),
			stack.getMaxStackSize(),
			stack.isDamageableItem() ? stack.getDamageValue() : 0,
			stack.isDamageableItem() ? stack.getMaxDamage() : 0,
			damage,
			speed,
			armor,
			food != null ? food.nutrition() : 0,
			enchants);
	}

	private static boolean hasModifier(ItemAttributeModifiers mods, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.attribute().equals(attribute)) {
				return true;
			}
		}
		return false;
	}

	public static ItemFacts.Category category(ItemStack stack) {
		Equippable eq = stack.get(DataComponents.EQUIPPABLE);
		if (eq != null && eq.slot().isArmor()) {
			return ItemFacts.Category.ARMOUR;
		}
		if (stack.is(ItemTags.SWORDS) || stack.getItem() instanceof ProjectileWeaponItem
			|| stack.has(DataComponents.WEAPON) && !stack.has(DataComponents.TOOL)) {
			return ItemFacts.Category.WEAPON;
		}
		if (stack.has(DataComponents.TOOL)) {
			return ItemFacts.Category.TOOL;
		}
		if (stack.has(DataComponents.FOOD)) {
			return ItemFacts.Category.FOOD;
		}
		if (stack.getItem() instanceof BlockItem) {
			return ItemFacts.Category.BLOCK;
		}
		return ItemFacts.Category.ITEM;
	}

	/** The planners' view of a stack: merge identity, sort order, count, max. */
	public static StackView view(ItemStack stack) {
		if (stack.isEmpty()) {
			return StackView.EMPTY;
		}
		String key = BuiltInRegistries.ITEM.getKey(stack.getItem()) + "#" + Integer.toHexString(ItemStack.hashItemAndComponents(stack));
		String sortKey = String.format(Locale.ROOT, "%d:%06d", category(stack).ordinal(), BuiltInRegistries.ITEM.getId(stack.getItem()));
		return new StackView(key, sortKey, stack.getCount(), stack.getMaxStackSize());
	}
}
