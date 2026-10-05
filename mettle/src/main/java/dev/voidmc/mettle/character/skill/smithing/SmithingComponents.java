package dev.voidmc.mettle.character.skill.smithing;

import dev.voidmc.mettle.Mettle;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** The two item components Smithing stamps. Registered when {@code SmithingSkill.init()} loads this class. */
public final class SmithingComponents {
	public static final DataComponentType<Forged> FORGED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mettle.id("forged"),
		DataComponentType.<Forged>builder().persistent(Forged.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Forged.CODEC)).build());

	public static final DataComponentType<Honed> HONED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mettle.id("honed"),
		DataComponentType.<Honed>builder().persistent(Honed.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Honed.CODEC)).build());

	private SmithingComponents() {
	}

	public static void init() {
	}
}
