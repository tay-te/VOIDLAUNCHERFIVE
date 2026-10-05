package dev.voidmc.mettle.burden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.voidmc.mettle.burden.WeightDeriver.Edge;
import dev.voidmc.mettle.burden.WeightDeriver.Rule;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Weight derivation over vanilla's real recipes and tags (BURDEN.md §1, ARCHITECTURE.md §11). */
class WeightDerivationTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// Item components (max stack size…) are bound when a world loads its registries; do the same here.
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
			.forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
	}

	private static WeightTable table;

	@BeforeAll
	static void derive() throws Exception {
		Identifier file = Identifier.fromNamespaceAndPath("mettle", "mettle/weight/equipment.json");
		WeightOverrides overrides;
		try (Reader r = new InputStreamReader(WeightDerivationTest.class.getClassLoader().getResourceAsStream("data/mettle/mettle/weight/equipment.json"),
			StandardCharsets.UTF_8)) {
			overrides = new WeightOverrides(WeightOverrides.parse(JsonParser.parseReader(r), file));
		}
		long start = System.nanoTime();
		table = Weights.derive(VanillaData::tag, overrides, VanillaData.recipes());
		System.out.println("Derived " + table.size() + " weights in " + (System.nanoTime() - start) / 1_000_000 + " ms");
	}

	private static float w(Item item) {
		return table.get(item);
	}

	@Test
	void ironBlockIsNineIngots() {
		assertEquals(4.5f, w(Items.IRON_BLOCK), 1e-4);
		assertEquals("iron_block = 9 × iron_ingot (leaf 0.5) = 4.5 kg", Weights.explain(table, Items.IRON_BLOCK));
		assertEquals(0.5f / 9f, w(Items.IRON_NUGGET), 1e-4);
	}

	@Test
	void woodDerivesFromLogs() {
		assertEquals(0.2f, w(Items.OAK_PLANKS), 1e-4);
		assertEquals(0.1f, w(Items.STICK), 1e-4);
		assertEquals(1.6f, w(Items.CHEST), 1e-4);
		assertEquals("oak_planks = oak_log (leaf 0.8) ÷ 4 = 0.2 kg", Weights.explain(table, Items.OAK_PLANKS));
	}

	@Test
	void leavesOverridesAndBuckets() {
		assertEquals(0.25f, w(Items.COBBLESTONE), 1e-4);
		assertEquals(0.35f, w(Items.IRON_ORE), 1e-4);
		assertEquals(0.4f, w(Items.DEEPSLATE_IRON_ORE), 1e-4);
		assertEquals(1.0f, w(Items.ANCIENT_DEBRIS), 1e-4);
		assertEquals(2.4f, w(Items.IRON_HELMET), 1e-4);
		assertEquals(4.8f, w(Items.IRON_CHESTPLATE), 1e-4);
		assertEquals(2.0f, w(Items.DIAMOND_PICKAXE), 1e-4);
		assertEquals(1.8f, w(Items.IRON_SPEAR), 1e-4);
		assertEquals(1.5f, w(Items.BUCKET), 1e-4);
		assertEquals(2.5f, w(Items.WATER_BUCKET), 1e-4);
		assertEquals(3.0f, w(Items.LAVA_BUCKET), 1e-4);
		assertEquals(0.3f, w(Items.COOKED_BEEF), 1e-4);
		assertEquals(0f, w(Items.PIG_SPAWN_EGG));
		assertEquals(0f, w(Items.DEBUG_STICK));
		// The cake's three milk buckets give their buckets back.
		float cake = 3 * w(Items.MILK_BUCKET) + 2 * w(Items.SUGAR) + w(Items.EGG) + 3 * w(Items.WHEAT) - 3 * w(Items.BUCKET);
		assertEquals(cake, w(Items.CAKE), 1e-3);
		assertTrue(Weights.explain(table, Items.CAKE).contains("− 3 × bucket"), Weights.explain(table, Items.CAKE));
		// Weathered copper weighs what fresh copper does.
		Item copper = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("copper_block"));
		assertEquals(3.6f, w(copper), 1e-4);
		for (String id : new String[] {"oxidized_copper", "waxed_weathered_copper"}) {
			assertEquals(w(copper), w(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id))), 1e-4, id);
		}
	}

	@Test
	void everyItemGetsASaneWeight() {
		List<String> bad = new ArrayList<>();
		int fallbacks = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			float kg = table.get(item);
			if (!(kg >= 0f) || Float.isInfinite(kg)) {
				bad.add(Weights.name(item) + "=" + kg);
			}
			if (!Leaves.technical(item) && kg == 0f) {
				bad.add(Weights.name(item) + " weighs nothing");
			}
			if (table.how(item) instanceof WeightDeriver.How.Fallback) {
				fallbacks++;
			}
			if (table.how(item) == null) {
				bad.add(Weights.name(item) + " has no explanation");
			}
		}
		System.out.println(fallbacks + " items use the fallback rule");
		assertTrue(bad.isEmpty(), "bad weights: " + bad);
		assertEquals(BuiltInRegistries.ITEM.size(), table.size());
	}

	@Test
	void nestedContentsCount() {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(Items.COBBLESTONE, 16))));
		assertEquals(w(Items.BUNDLE) + 16 * 0.25f, Weights.weigh(table, bundle), 1e-3);

		List<ItemStack> stone = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			stone.add(new ItemStack(Items.COBBLESTONE, 64));
		}
		ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stone));
		float full = w(Items.SHULKER_BOX) + 27 * 16f;
		assertEquals(full, Weights.weigh(table, shulker), 1e-2);

		// A shulker box inside a bundle inside a shulker box: everything counts, all the way down.
		ItemStack inner = new ItemStack(Items.BUNDLE);
		inner.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(shulker))));
		ItemStack outer = new ItemStack(Items.SHULKER_BOX);
		outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(inner)));
		assertEquals(w(Items.SHULKER_BOX) + w(Items.BUNDLE) + full, Weights.weigh(table, outer), 1e-2);
		assertEquals(4 * w(Items.SHULKER_BOX), Weights.weigh(table, new ItemStack(Items.SHULKER_BOX, 4)), 1e-4);
	}

	@Test
	void cyclesTerminate() {
		Item a = Items.SLIME_BALL;
		Item b = Items.SLIME_BLOCK;
		Item x = Items.HONEY_BOTTLE;
		Item y = Items.HONEY_BLOCK;
		Item leaf = Items.IRON_INGOT;
		Item gain = Items.CLAY;
		List<Edge> edges = List.of(
			// An unanchored pair: 9 a → b, b → 9 a.
			new Edge(null, b, 1, List.of(List.of(a), List.of(a), List.of(a), List.of(a), List.of(a), List.of(a), List.of(a), List.of(a), List.of(a)), false),
			new Edge(null, a, 9, List.of(List.of(b)), false),
			// A cycle that gains mass every lap (x → 2 y → 2 x), which a naive fixed point would chase to zero forever.
			new Edge(null, y, 2, List.of(List.of(x)), false),
			new Edge(null, x, 1, List.of(List.of(y)), false),
			new Edge(null, gain, 1, List.of(List.of(leaf), List.of(gain)), false));
		Map<Item, Rule> rules = new HashMap<>();
		rules.put(leaf, new Rule.Fixed(0.5f, "leaf"));
		WeightDeriver.Result result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> WeightDeriver.derive(List.of(a, b, x, y, leaf, gain), rules, edges,
			i -> null, Item::getDefaultMaxStackSize, i -> false, Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString())));
		for (Item item : List.of(a, b, x, y, leaf, gain)) {
			float kg = result.weight(item);
			assertTrue(kg >= 0f && !Float.isInfinite(kg), Weights.name(item) + " = " + kg);
		}
		// The slime ball is the loose member (recipes hand out nine), so it takes the fallback and the block is nine of them.
		assertEquals(4f / 64f, result.weight(a), 1e-5);
		assertEquals(9 * 4f / 64f, result.weight(b), 1e-4);
	}

	@Test
	void equipmentOverridesNameRealItems() throws Exception {
		try (Reader r = new InputStreamReader(getClass().getClassLoader().getResourceAsStream("data/mettle/mettle/weight/equipment.json"), StandardCharsets.UTF_8)) {
			for (WeightOverrides.Line line : WeightOverrides.parse(JsonParser.parseReader(r), Identifier.parse("mettle:x"))) {
				assertTrue(BuiltInRegistries.ITEM.getOptional(Identifier.parse(line.target())).isPresent(), line.target());
			}
		}
	}
}
