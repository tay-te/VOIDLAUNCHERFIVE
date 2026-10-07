package dev.voidmc.mettle.gear;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.GearSlot;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The seven gear items (BALANCE.md §5.4). Plain items: everything they do comes from the {@code mettle:gear} component. */
public final class GearItems {
	/** Carry capacity each piece adds while worn, in kg (BALANCE.md §5.4). */
	public static final float SATCHEL_KG = 10f;
	public static final float LEATHER_PACK_KG = 20f;
	public static final float FRAME_PACK_KG = 30f;
	public static final float BELT_KG = 3f;
	public static final float TOOL_BELT_KG = 5f;

	public static final Item SATCHEL = register("satchel", new GearSpec(GearSlot.BACK, SATCHEL_KG, 9, Optional.empty()));
	public static final Item LEATHER_PACK = register("leather_pack", new GearSpec(GearSlot.BACK, LEATHER_PACK_KG, 18, Optional.empty()));
	public static final Item FRAME_PACK = register("frame_pack", new GearSpec(GearSlot.BACK, FRAME_PACK_KG, 27, Optional.empty()));
	public static final Item BELT = register("belt", new GearSpec(GearSlot.BELT, BELT_KG, 4, Optional.empty()));
	public static final Item TOOL_BELT = register("tool_belt", new GearSpec(GearSlot.BELT, TOOL_BELT_KG, 6, Optional.empty()));
	public static final Item QUIVER = register("quiver", new GearSpec(GearSlot.QUIVER, 0f, 2, Optional.of(ItemTags.ARROWS)));
	public static final Item HUNTERS_QUIVER = register("hunters_quiver", new GearSpec(GearSlot.QUIVER, 0f, 4, Optional.of(ItemTags.ARROWS)));

	public static final List<Item> ALL = List.of(SATCHEL, LEATHER_PACK, FRAME_PACK, BELT, TOOL_BELT, QUIVER, HUNTERS_QUIVER);

	private GearItems() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.insertBefore(Items.BUNDLE, ALL.toArray(Item[]::new)));
	}

	private static Item register(String id, GearSpec spec) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Mettle.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(1).component(GearComponents.GEAR, spec).setId(key)));
	}
}
