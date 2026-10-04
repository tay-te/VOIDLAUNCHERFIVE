package dev.voidmc.hearth.law.item;

import dev.voidmc.hearth.Hearth;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/** The law's item components: stolen marks, the owned mark on items in an open container, and what a key opens. */
public final class LawComponents {
	public static final DataComponentType<StolenMark> STOLEN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Hearth.id("stolen"), DataComponentType.<StolenMark>builder()
			.persistent(StolenMark.CODEC)
			.networkSynchronized(StolenMark.STREAM_CODEC)
			.build());

	public static final DataComponentType<OwnedMark> OWNED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Hearth.id("owned"), DataComponentType.<OwnedMark>builder()
			.persistent(OwnedMark.CODEC)
			.networkSynchronized(OwnedMark.STREAM_CODEC)
			.build());

	public static final DataComponentType<KeyData> KEY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Hearth.id("key"), DataComponentType.<KeyData>builder()
			.persistent(KeyData.CODEC)
			.networkSynchronized(KeyData.STREAM_CODEC)
			.build());

	private LawComponents() {
	}

	/** Registers the components (class load) and their tooltip lines; call before the items. */
	public static void init() {
		ItemComponentTooltipProviderRegistry.addLast(STOLEN);
		ItemComponentTooltipProviderRegistry.addLast(OWNED);
		ItemComponentTooltipProviderRegistry.addLast(KEY);
	}
}
