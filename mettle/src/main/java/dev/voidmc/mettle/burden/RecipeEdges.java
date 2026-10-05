package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.burden.WeightDeriver.Edge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.WeatheringCopper;

/**
 * The server's recipes as deriver edges. Any recipe with placeable ingredients and one fixed result counts (shaped,
 * shapeless, transmute, stonecutting, smithing transforms, and modded recipes that follow the same contract); recipes
 * whose result depends on the input (dyeing, trims, special crafting) are skipped. Cooking recipes are kept apart: they
 * only fill gaps.
 */
public final class RecipeEdges {
	private RecipeEdges() {
	}

	public static List<Edge> from(RecipeManager recipes) {
		List<Edge> edges = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes.getRecipes()) {
			try {
				Edge e = edge(holder);
				if (e != null) {
					edges.add(e);
				}
			} catch (RuntimeException ex) {
				Mettle.LOGGER.debug("Skipping recipe {} for weights: {}", holder.id().identifier(), ex.toString());
			}
		}
		edges.addAll(equivalences());
		return edges;
	}

	private static Edge edge(RecipeHolder<?> holder) {
		Recipe<?> recipe = holder.value();
		PlacementInfo placement = recipe.placementInfo();
		if (placement.isImpossibleToPlace()) {
			return null;
		}
		List<Ingredient> ingredients = placement.ingredients();
		if (recipe instanceof TransmuteRecipe && ingredients.size() > 2) {
			// The input and one material; the rest are optional extra copies of the material.
			ingredients = ingredients.subList(0, 2);
		}
		List<RecipeDisplay> displays = recipe.display();
		if (displays.isEmpty() || ingredients.isEmpty()) {
			return null;
		}
		Item output;
		int count;
		switch (displays.getFirst().result()) {
			case SlotDisplay.ItemStackSlotDisplay s -> {
				output = s.stack().item().value();
				count = s.stack().count();
			}
			case SlotDisplay.ItemSlotDisplay s -> {
				output = s.item().value();
				count = 1;
			}
			default -> {
				return null;
			}
		}
		List<List<Item>> slots = new ArrayList<>(ingredients.size());
		for (Ingredient ingredient : ingredients) {
			slots.add(ingredient.items().map(Holder::value).toList());
		}
		return new Edge(holder.id().identifier(), output, count, slots, recipe instanceof AbstractCookingRecipe);
	}

	/**
	 * Same-weight pairs no recipe states, shared with the tests (which read recipes from vanilla's files): weathered and
	 * waxed copper weigh what fresh copper does, stripped wood what the log does, concrete what its powder does,
	 * infested stone what stone does, a worn anvil what an anvil does, a written book what a book and quill does.
	 */
	public static List<Edge> equivalences() {
		List<Edge> edges = new ArrayList<>();
		WeatheringCopper.NEXT_BY_BLOCK.get().forEach((from, to) -> same(edges, from.asItem(), to.asItem(), "weathering"));
		HoneycombItem.WAXABLES.get().forEach((from, to) -> same(edges, from.asItem(), to.asItem(), "waxing"));
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			String path = id.getPath();
			if (path.startsWith("stripped_")) {
				same(edges, sibling(id, path.substring("stripped_".length())), item, "stripping");
			} else if (path.startsWith("infested_")) {
				same(edges, sibling(id, path.substring("infested_".length())), item, "infesting");
			} else if (path.endsWith("_concrete")) {
				same(edges, sibling(id, path + "_powder"), item, "setting");
			} else if (path.endsWith("_map") && !path.equals("map")) {
				same(edges, Items.MAP, item, "mapping");
			}
		}
		same(edges, Items.ANVIL, Items.CHIPPED_ANVIL, "wear");
		same(edges, Items.ANVIL, Items.DAMAGED_ANVIL, "wear");
		same(edges, Items.BOOK, Items.ENCHANTED_BOOK, "enchanting");
		same(edges, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK, "writing");
		same(edges, Items.PUMPKIN, Items.CARVED_PUMPKIN, "carving");
		return edges;
	}

	private static Item sibling(Identifier id, String path) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), path));
	}

	private static void same(List<Edge> edges, Item a, Item b, String why) {
		if (a != Items.AIR && b != Items.AIR && a != b) {
			edges.add(new Edge(Mettle.id(why), b, 1, List.of(List.of(a)), false));
		}
	}
}
