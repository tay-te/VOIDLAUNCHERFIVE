package dev.voidmc.mettle.gear;

import dev.voidmc.mettle.Mettle;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The two gear components. {@code mettle:pockets} has exactly vanilla's container-contents shape so anything that reads
 * a shulker box can read a backpack; the burden module weighs it by id.
 */
public final class GearComponents {
	public static final DataComponentType<GearSpec> GEAR = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mettle.id("gear"),
		DataComponentType.<GearSpec>builder().persistent(GearSpec.CODEC).networkSynchronized(GearSpec.STREAM_CODEC).build());

	public static final DataComponentType<ItemContainerContents> POCKETS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Mettle.id("pockets"), DataComponentType.<ItemContainerContents>builder()
			.persistent(ItemContainerContents.CODEC)
			.networkSynchronized(ItemContainerContents.STREAM_CODEC)
			.cacheEncoding()
			.build());

	private GearComponents() {
	}

	/** Loads the class (registers the components); call before the items. */
	public static void init() {
	}
}
