package dev.voidmc.hearth.economy.item;

import dev.voidmc.hearth.Hearth;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/** The economy's item components: what a pouch holds and what a deed records. */
public final class EconomyComponents {
	public static final DataComponentType<PouchContents> POUCH_CONTENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Hearth.id("pouch_contents"), DataComponentType.<PouchContents>builder()
			.persistent(PouchContents.CODEC)
			.networkSynchronized(PouchContents.STREAM_CODEC)
			.build());

	public static final DataComponentType<DeedData> DEED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Hearth.id("deed"), DataComponentType.<DeedData>builder()
			.persistent(DeedData.CODEC)
			.networkSynchronized(DeedData.STREAM_CODEC)
			.build());

	private EconomyComponents() {
	}

	/** Loads the class (registers the components); call before the items. */
	public static void init() {
	}
}
