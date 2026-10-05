package dev.voidmc.mettle.character.skill.defense;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** What a player is wearing, as the Ironclad perks see it. */
public final class Armour {
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private Armour() {
	}

	/**
	 * Four pieces that each give armour in their slot. Read from the items' own modifiers, so a carved pumpkin or an
	 * elytra does not count and modded armour does.
	 */
	public static boolean full(LivingEntity entity) {
		for (EquipmentSlot slot : SLOTS) {
			if (value(entity.getItemBySlot(slot), slot)[0] <= 0) {
				return false;
			}
		}
		return true;
	}

	/** {armour, toughness} a stack gives when worn in a slot (flat modifiers only, as vanilla armour has). */
	public static double[] value(ItemStack stack, EquipmentSlot slot) {
		double[] out = new double[2];
		if (stack.isEmpty()) {
			return out;
		}
		stack.forEachModifier(slot, (attribute, modifier) -> {
			if (modifier.operation() != net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
				return;
			}
			if (attribute.value() == Attributes.ARMOR.value()) {
				out[0] += modifier.amount();
			} else if (attribute.value() == Attributes.ARMOR_TOUGHNESS.value()) {
				out[1] += modifier.amount();
			}
		});
		return out;
	}

	public static boolean isArmourSlot(EquipmentSlot slot) {
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}
}
