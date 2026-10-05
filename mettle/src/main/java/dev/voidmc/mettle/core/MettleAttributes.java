package dev.voidmc.mettle.core;

import dev.voidmc.mettle.Mettle;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Mettle's own attributes. Carry capacity is a real attribute so anything (Strength, perks, a backpack, a potion, another
 * mod) can raise it with an ordinary modifier, and {@code /attribute} works on it.
 */
public final class MettleAttributes {
	public static final Holder<Attribute> CARRY_CAPACITY = Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, Mettle.id("carry_capacity"),
		new RangedAttribute("attribute.name.mettle.carry_capacity", 40.0, 0.0, 10_000.0).setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

	private MettleAttributes() {
	}

	public static void init() {
		// Runs on both sides; a client without the attribute would log a warning for every attribute packet.
		FabricDefaultAttributeRegistry.MODIFY.register(context -> context.modify(EntityTypes.PLAYER, (type, builder) -> builder.add(CARRY_CAPACITY)));
	}
}
