package dev.voidmc.mettle.burden;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.core.MettleAttributes;
import dev.voidmc.mettle.gear.GearItems;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The weight half of the balance report (BALANCE.md §5): the derived weight of common items, and four realistic
 * inventories against capacity at Strength 3, 6 and 10, with and without a pack, through the real derivation
 * ({@link Weights#derive}) and band rules ({@link Bands#band}). Runs only with {@code -Dmettle.balance=true}; writes
 * {@code build/reports/mettle-weight.md}. The in-game twin is the client tour's BalanceReport.
 */
class WeightReportTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
			.forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
	}

	/** Items worth knowing the weight of. */
	static final Item[] COMMON = {Items.COBBLESTONE, Items.STONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL, Items.SAND, Items.SANDSTONE,
		Items.GLASS, Items.STONE_BRICKS, Items.BRICKS, Items.OAK_LOG, Items.OAK_PLANKS, Items.STICK, Items.TORCH, Items.CHEST, Items.CRAFTING_TABLE,
		Items.FURNACE, Items.COAL, Items.RAW_IRON, Items.IRON_INGOT, Items.IRON_BLOCK, Items.RAW_GOLD, Items.GOLD_INGOT, Items.RAW_COPPER,
		Items.COPPER_INGOT, Items.DIAMOND, Items.REDSTONE, Items.IRON_ORE, Items.OBSIDIAN, Items.ARROW, Items.BOW, Items.SHIELD, Items.IRON_SWORD,
		Items.IRON_PICKAXE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.LEATHER_CHESTPLATE, Items.BREAD, Items.COOKED_BEEF,
		Items.APPLE, Items.WATER_BUCKET, Items.ANVIL, Items.RAIL, Items.LEATHER, Items.STRING};

	record Stack(Item item, int count) {
	}

	record Gear(String name, String id, float capacity) {
	}

	static Stack s(Item item, int count) {
		return new Stack(item, count);
	}

	static List<Stack> armour(String metal) {
		List<Stack> out = new ArrayList<>();
		for (String piece : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
			out.add(s(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(metal + "_" + piece)), 1));
		}
		return out;
	}

	static Map<String, List<Stack>> scenarios() {
		Map<String, List<Stack>> out = new LinkedHashMap<>();
		// (a) the Ranger's kit: bow, 32 arrows in the quiver (the quiver's own weight is added below), leather cap, mutton.
		out.put("(a) fresh Ranger kit", List.of(s(Items.BOW, 1), s(Items.ARROW, 32), s(Items.LEATHER_HELMET, 1), s(Items.COOKED_MUTTON, 4)));
		// (b) twenty minutes of strip-mining, everything kept.
		List<Stack> mine = new ArrayList<>(armour("iron"));
		mine.addAll(List.of(s(Items.IRON_PICKAXE, 1), s(Items.TORCH, 32), s(Items.COOKED_BEEF, 8), s(Items.COBBLESTONE, 192),
			s(Items.COBBLED_DEEPSLATE, 192), s(Items.DIRT, 64), s(Items.GRAVEL, 64), s(Items.RAW_IRON, 40), s(Items.COAL, 25)));
		out.put("(b) 20-minute strip-mine", mine);
		// (c) a builder carrying 8 stacks to the site, with their tools.
		out.put("(c) builder, 8 stacks", List.of(s(Items.OAK_PLANKS, 192), s(Items.OAK_LOG, 192), s(Items.STONE_BRICKS, 128), s(Items.BREAD, 8),
			s(Items.IRON_AXE, 1), s(Items.IRON_PICKAXE, 1)));
		// (d) a mid-game explorer.
		List<Stack> explorer = new ArrayList<>(armour("iron"));
		explorer.addAll(List.of(s(Items.IRON_SWORD, 1), s(Items.IRON_PICKAXE, 1), s(Items.IRON_AXE, 1), s(Items.SHIELD, 1), s(Items.BOW, 1),
			s(Items.ARROW, 128), s(Items.COOKED_BEEF, 16), s(Items.BREAD, 16), s(Items.COBBLESTONE, 64), s(Items.DIRT, 64), s(Items.OAK_PLANKS, 64),
			s(Items.OAK_LOG, 64), s(Items.TORCH, 32)));
		out.put("(d) mid-game explorer", explorer);
		return out;
	}

	static final Gear[] GEAR = {new Gear("no pack", "", 0f), new Gear("Satchel", "mettle:satchel", GearItems.SATCHEL_KG),
		new Gear("Leather Pack", "mettle:leather_pack", GearItems.LEATHER_PACK_KG)};
	static final int[] STRENGTH = {3, 6, 10};

	static WeightTable derive() throws Exception {
		Identifier file = Identifier.fromNamespaceAndPath("mettle", "mettle/weight/equipment.json");
		WeightOverrides overrides;
		try (Reader r = new InputStreamReader(WeightReportTest.class.getClassLoader().getResourceAsStream("data/mettle/mettle/weight/equipment.json"),
			StandardCharsets.UTF_8)) {
			overrides = new WeightOverrides(WeightOverrides.parse(JsonParser.parseReader(r), file));
		}
		return Weights.derive(VanillaData::tag, overrides, VanillaData.recipes());
	}

	static Map<String, Float> gearWeights() throws Exception {
		try (Reader r = new InputStreamReader(WeightReportTest.class.getClassLoader().getResourceAsStream("data/mettle/mettle/weight/mettle_items.json"),
			StandardCharsets.UTF_8)) {
			JsonObject values = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("values");
			Map<String, Float> out = new LinkedHashMap<>();
			values.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsFloat()));
			return out;
		}
	}

	static float capacity(int strength, Gear gear) {
		return (float) (MettleAttributes.BASE_CARRY_CAPACITY + strength * EffectsApplier.CAPACITY_PER_STRENGTH) + gear.capacity();
	}

	@Test
	void report() throws Exception {
		Assumptions.assumeTrue(Boolean.getBoolean("mettle.balance"), "balance report: run with -Dmettle.balance=true");
		WeightTable table = derive();
		Map<String, Float> gearKg = gearWeights();
		StringBuilder out = new StringBuilder();

		out.append(String.format(Locale.ROOT, "%n## Capacity: base %.0f kg + %.0f per Strength; Satchel +%.0f, Leather Pack +%.0f, Frame Pack +%.0f%n%n",
			MettleAttributes.BASE_CARRY_CAPACITY, EffectsApplier.CAPACITY_PER_STRENGTH, GearItems.SATCHEL_KG, GearItems.LEATHER_PACK_KG, GearItems.FRAME_PACK_KG));

		out.append("## Derived weights\n\n| Item | kg | Stack | How |\n|---|---|---|---|\n");
		for (Item item : COMMON) {
			float kg = table.get(item);
			int max = item.getDefaultMaxStackSize();
			out.append(String.format(Locale.ROOT, "| %s | %s | %s | %s |%n", Weights.name(item), Weights.num(kg),
				max > 1 ? Weights.total(kg * max) + " (" + max + ")" : "—", Weights.explain(table, item).replace("|", "/")));
		}

		out.append("\n## Loads and bands\n\n| Inventory | Load kg | Pack | Str 3 | Str 6 | Str 10 |\n|---|---|---|---|---|---|\n");
		for (Map.Entry<String, List<Stack>> e : scenarios().entrySet()) {
			float load = 0;
			for (Stack stack : e.getValue()) {
				load += stack.count() * table.get(stack.item());
			}
			if (e.getKey().startsWith("(a)")) {
				load += gearKg.getOrDefault("mettle:quiver", 0f);
			}
			for (Gear gear : GEAR) {
				float carried = load + (gear.id().isEmpty() ? 0f : gearKg.getOrDefault(gear.id(), 0f));
				StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "| %s | %.1f | %s", e.getKey(), carried, gear.name()));
				for (int str : STRENGTH) {
					float cap = capacity(str, gear);
					LoadBand band = Bands.band(carried, cap, BandRules.DEFAULT);
					row.append(String.format(Locale.ROOT, " | %.0f%% %s (of %.0f)", 100 * carried / cap, band.name().charAt(0) + band.name().substring(1).toLowerCase(Locale.ROOT), cap));
				}
				out.append(row).append(" |\n");
			}
		}
		String text = out.toString();
		System.out.println(text);
		Path file = Path.of("build", "reports", "mettle-weight.md");
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
		assertTrue(text.contains("cobblestone"));
	}
}
