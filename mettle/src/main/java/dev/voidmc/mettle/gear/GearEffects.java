package dev.voidmc.mettle.gear;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.core.MettleAttributes;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Worn gear's carry capacity as modifiers on {@code mettle:carry_capacity}, one per slot with the fixed id
 * {@code mettle:gear/<slot>} (ARCHITECTURE.md §6). Idempotent; touches a modifier only when its amount changes.
 */
public final class GearEffects {
	private GearEffects() {
	}

	public static Identifier modifierId(GearSlot slot) {
		return Mettle.id("gear/" + slot.name().toLowerCase(Locale.ROOT));
	}

	public static void apply(ServerPlayer player) {
		AttributeInstance capacity = player.getAttribute(MettleAttributes.CARRY_CAPACITY);
		if (capacity == null) {
			return;
		}
		GearInventory gear = GearInventory.of(player);
		for (GearSlot slot : GearSlot.values()) {
			GearSpec spec = gear.spec(slot);
			double amount = spec == null || spec.slot() != slot ? 0.0 : spec.capacityKg();
			Identifier id = modifierId(slot);
			AttributeModifier current = capacity.getModifier(id);
			if (amount == 0.0) {
				if (current != null) {
					capacity.removeModifier(id);
				}
			} else if (current == null || current.amount() != amount) {
				capacity.removeModifier(id);
				capacity.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
			}
		}
	}
}
