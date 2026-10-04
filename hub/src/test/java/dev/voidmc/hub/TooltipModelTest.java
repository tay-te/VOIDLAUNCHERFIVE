package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.model.ItemFacts;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.model.TooltipModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The hovered-item card: item facts in, the exact JSON the page renders out. */
class TooltipModelTest {

	static ItemFacts diamondSword() {
		return new ItemFacts("Diamond Sword", "rare", ItemFacts.Category.WEAPON, 1, 1, 357, 1561, 7.0, 1.6, 0, 0,
			List.of(new ItemFacts.Enchant("Looting", 2, 3), new ItemFacts.Enchant("Sharpness", 4, 5), new ItemFacts.Enchant("Unbreaking", 3, 3)));
	}

	@Test
	@SuppressWarnings("unchecked")
	void diamondSwordMatchesTheDesignCard() {
		Map<String, Object> d = Json.parseObject(TooltipModel.of(diamondSword(), "HOVERED  ·  HOTBAR 1", 36));
		assertEquals("detail", d.get("type"));
		assertEquals(Boolean.TRUE, d.get("has"));
		assertEquals(36.0, d.get("slot"));
		assertEquals("Diamond Sword", d.get("name"));
		assertEquals("HOVERED  ·  HOTBAR 1", d.get("label"));
		assertEquals("RARE  ·  WEAPON  ·  ENCHANTED  ·  7 DAMAGE  ·  1.6 SPEED", d.get("meta"));
		assertEquals("rare", d.get("rarity"));
		Map<String, Object> dur = (Map<String, Object>) d.get("durability");
		assertEquals(1204.0, dur.get("cur"));
		assertEquals(1561.0, dur.get("max"));
		assertEquals("1,204 / 1,561", dur.get("text"));
		assertEquals(24.0, dur.get("cells"));
		assertEquals(19.0, dur.get("lit"), "1204/1561 of 24 cells is 18.5, rounded up so wear never hides a cell");
		List<Object> ench = (List<Object>) d.get("ench");
		assertEquals(3, ench.size());
		Map<String, Object> sharp = (Map<String, Object>) ench.get(1);
		assertEquals("Sharpness", sharp.get("name"));
		assertEquals("IV", sharp.get("roman"));
		assertEquals(5.0, sharp.get("cells"), "level cells run to the enchantment's max level");
		assertEquals(4.0, sharp.get("lit"));
		assertEquals("III", ((Map<String, Object>) ench.get(2)).get("roman"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void plainItemsHaveNoDurabilityAndShowStackAndFoodLines() {
		ItemFacts bread = new ItemFacts("Bread", "common", ItemFacts.Category.FOOD, 9, 64, 0, 0, Double.NaN, Double.NaN, 0, 5, List.of());
		Map<String, Object> d = Json.parseObject(TooltipModel.of(bread, "HOVERED  ·  HOTBAR 6", 41));
		assertNull(d.get("durability"));
		assertEquals("FOOD", d.get("meta"), "common rarity is not shown");
		List<Object> lines = (List<Object>) d.get("lines");
		assertEquals("Restores", ((Map<String, Object>) lines.get(0)).get("k"));
		assertEquals("5 hunger", ((Map<String, Object>) lines.get(0)).get("v"));
		assertEquals("9 / 64", ((Map<String, Object>) lines.get(1)).get("v"));
		assertTrue(((List<Object>) d.get("ench")).isEmpty());
	}

	@Test
	void armourShowsItsPoints() {
		ItemFacts helmet = new ItemFacts("Iron Helmet", "common", ItemFacts.Category.ARMOUR, 1, 1, 20, 165, Double.NaN, Double.NaN, 2, 0, List.of());
		assertEquals("ARMOUR  ·  2 ARMOUR", TooltipModel.metaLine(helmet));
		assertTrue(TooltipModel.of(helmet, "HELMET", 5).contains("\"Armour\""));
	}

	@Test
	void emptyCardSaysSo() {
		Map<String, Object> d = Json.parseObject(TooltipModel.empty());
		assertEquals("detail", d.get("type"));
		assertEquals(Boolean.FALSE, d.get("has"));
	}

	@Test
	void durabilityCells() {
		assertEquals(24, TooltipModel.litCells(1561, 1561, 24));
		assertEquals(0, TooltipModel.litCells(0, 1561, 24), "a broken tool lights nothing");
		assertEquals(1, TooltipModel.litCells(1, 1561, 24), "one use left still shows a cell");
		assertEquals(24, TooltipModel.litCells(1560, 1561, 24), "rounded up: one point of wear is not a whole cell");
		assertEquals(23, TooltipModel.litCells(1480, 1561, 24));
		assertEquals(12, TooltipModel.litCells(50, 100, 24));
		assertEquals(0, TooltipModel.litCells(5, 0, 24));
	}

	@Test
	void numerals() {
		String[] want = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		for (int i = 1; i <= 10; i++) {
			assertEquals(want[i - 1], TooltipModel.roman(i));
		}
		assertEquals("11", TooltipModel.roman(11));
		assertEquals("255", TooltipModel.roman(255));
	}

	@Test
	void slotLabels() {
		assertEquals("HOTBAR 1", TooltipModel.slotLabel("hotbar", 0));
		assertEquals("CHEST SLOT 17", TooltipModel.slotLabel("container", 16));
		assertEquals("STORAGE 27", TooltipModel.slotLabel("storage", 26));
		assertEquals("HELMET", TooltipModel.slotLabel("head", 0));
		assertEquals("OFFHAND", TooltipModel.slotLabel("offhand", 0));
		assertEquals("CRAFTING RESULT", TooltipModel.slotLabel("result", 0));
	}

	@Test
	void namesAreEscapedForTheScriptTheyTravelIn() {
		ItemFacts named = new ItemFacts("\"Grim\" \\ Reaper\n ", "epic", ItemFacts.Category.WEAPON, 1, 1, 0, 0, Double.NaN, Double.NaN, 0, 0, List.of());
		String json = TooltipModel.of(named, "HOVERED", 0);
		assertFalse(json.contains(" "), "U+2028 would end the evaluateScript statement");
		assertEquals("\"Grim\" \\ Reaper\n ", Json.parseObject(json).get("name"));
	}

	@Test
	void filters() {
		ItemFacts sword = diamondSword();
		assertTrue(sword.matchesFilter("all"));
		assertTrue(sword.matchesFilter("tools"));
		assertFalse(sword.matchesFilter("blocks"));
		assertEquals("blocks", ItemFacts.Category.BLOCK.filter());
		assertEquals("food", ItemFacts.Category.FOOD.filter());
	}
}
