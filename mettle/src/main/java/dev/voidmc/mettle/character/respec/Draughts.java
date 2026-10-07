package dev.voidmc.mettle.character.respec;

import dev.voidmc.mettle.Mettle;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;

/**
 * The player's respec (CHARACTER.md §6): a <b>Draught of Forgetting</b>, brewed for one skill, refunds that tree's bought
 * perks; a <b>Draught of Unbinding</b> returns the placed attribute points. Both are crafted (shapeless recipes in
 * {@code data/mettle/recipe/draught_of_*.json}, the skill set by the result's {@code mettle:forgets} component), drunk
 * like a potion, and refused (and kept) when there is nothing to give back.
 */
public final class Draughts {
	/** The skill a Draught of Forgetting makes you forget, e.g. {@code mettle:mining}. */
	public static final DataComponentType<Identifier> FORGETS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mettle.id("forgets"),
		DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());

	public static final Item FORGETTING = register("draught_of_forgetting", DraughtItem.Kind.FORGETTING);
	public static final Item UNBINDING = register("draught_of_unbinding", DraughtItem.Kind.UNBINDING);

	/** The skills a draught can be crafted for: the released trees, in the K screen's order. */
	public static final List<String> SKILLS = List.of("blades", "heavy_weapons", "marksmanship", "defense", "athletics", "mining", "survival", "smithing");

	private Draughts() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			SKILLS.forEach(skill -> output.accept(forgetting(Mettle.id(skill))));
			output.accept(new ItemStack(UNBINDING));
		});
	}

	/** A Draught of Forgetting brewed for {@code skill}. */
	public static ItemStack forgetting(Identifier skill) {
		ItemStack stack = new ItemStack(FORGETTING);
		stack.set(FORGETS, skill);
		return stack;
	}

	private static Item register(String id, DraughtItem.Kind kind) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Mettle.id(id));
		Item.Properties properties = new Item.Properties()
			.stacksTo(16)
			.rarity(Rarity.UNCOMMON)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build())
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new DraughtItem(kind, properties));
	}
}
