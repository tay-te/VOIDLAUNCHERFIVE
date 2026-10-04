package dev.voidmc.hub.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The VOID tooltip — the "hovered item" card — as the JSON the page renders.
 *
 * <p>All the judgement lives here so it is tested: what the meta line says, how many durability
 * cells are lit, which cell carries the live (violet) marker, enchantment numerals and level
 * cells. The page only lays the values out.
 */
public final class TooltipModel {

	/** Durability cells on the card (Figma: 24 cells across 409 design px). */
	public static final int DURABILITY_CELLS = 24;

	private TooltipModel() {
	}

	/** The empty card: nothing hovered and nothing held. */
	public static String empty() {
		return Json.object().put("type", "detail").put("has", false).end();
	}

	/**
	 * @param label what the card is showing, e.g. "HOVERED · HOTBAR 1" or "HELD · HOTBAR 3"
	 * @param slot the menu slot index (lets the page and the tests tie the card to a well)
	 */
	public static String of(ItemFacts f, String label, int slot) {
		Json.Writer w = Json.object();
		w.put("type", "detail");
		w.put("has", true);
		w.put("slot", slot);
		w.put("label", label);
		w.put("name", f.name());
		w.put("count", f.count());
		w.put("rarity", f.rarity());
		w.put("category", f.category().name().toLowerCase(Locale.ROOT));
		w.put("meta", metaLine(f));
		if (f.damageable()) {
			w.raw("durability", Json.object()
				.put("cur", f.durability())
				.put("max", f.maxDamage())
				.put("text", grouped(f.durability()) + " / " + grouped(f.maxDamage()))
				.put("cells", DURABILITY_CELLS)
				.put("lit", litCells(f.durability(), f.maxDamage(), DURABILITY_CELLS))
				.end());
		}
		List<String> ench = new ArrayList<>();
		for (ItemFacts.Enchant e : f.enchantments()) {
			int cells = Math.max(1, Math.max(e.level(), e.maxLevel()));
			ench.add(Json.object()
				.put("name", e.name())
				.put("level", e.level())
				.put("roman", roman(e.level()))
				.put("cells", cells)
				.put("lit", Math.min(cells, e.level()))
				.end());
		}
		w.raw("ench", Json.array(ench));
		List<String> lines = new ArrayList<>();
		if (f.category() == ItemFacts.Category.FOOD && f.nutrition() > 0) {
			lines.add(line("Restores", f.nutrition() + " hunger"));
		}
		if (f.armor() > 0) {
			lines.add(line("Armour", "+" + f.armor()));
		}
		if (f.maxStack() > 1) {
			lines.add(line("Stack", f.count() + " / " + f.maxStack()));
		}
		w.raw("lines", Json.array(lines));
		return w.end();
	}

	private static String line(String k, String v) {
		return Json.object().put("k", k).put("v", v).end();
	}

	/**
	 * "RARE · WEAPON · 7 DAMAGE · 1.6 SPEED": rarity when it is not common, the category, then
	 * the numbers that category is about.
	 */
	public static String metaLine(ItemFacts f) {
		List<String> parts = new ArrayList<>();
		if (f.rarity() != null && !"common".equals(f.rarity())) {
			parts.add(f.rarity().toUpperCase(Locale.ROOT));
		}
		parts.add(f.category().name());
		if (!f.enchantments().isEmpty()) {
			parts.add("ENCHANTED");
		}
		if (!Double.isNaN(f.attackDamage()) && (f.category() == ItemFacts.Category.WEAPON || f.category() == ItemFacts.Category.TOOL)) {
			parts.add(trim(f.attackDamage()) + " DAMAGE");
			if (!Double.isNaN(f.attackSpeed())) {
				parts.add(trim(f.attackSpeed()) + " SPEED");
			}
		}
		if (f.armor() > 0) {
			parts.add(f.armor() + " ARMOUR");
		}
		return String.join("  ·  ", parts);
	}

	/**
	 * Lit cells for a durability bar: proportional, rounded up so any durability left shows at
	 * least one cell, and 0 only when the item is broken.
	 */
	public static int litCells(int current, int max, int cells) {
		if (max <= 0 || current <= 0) {
			return 0;
		}
		if (current >= max) {
			return cells;
		}
		return Math.max(1, Math.min(cells, (int) Math.ceil(current * (double) cells / max)));
	}

	/** Enchantment level numerals as the game shows them: I-X, then plain numbers. */
	public static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 1 && n <= 10 ? r[n] : Integer.toString(n);
	}

	/** 1204 -> "1,204". */
	public static String grouped(int n) {
		return String.format(Locale.ROOT, "%,d", n);
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	/** The slot label in the card's eyebrow, e.g. "HOTBAR 1", "CHEST SLOT 17", "HELMET". */
	public static String slotLabel(String role, int ordinal) {
		return switch (role) {
			case "hotbar" -> "HOTBAR " + (ordinal + 1);
			case "storage" -> "STORAGE " + (ordinal + 1);
			case "container" -> "CHEST SLOT " + (ordinal + 1);
			case "craft" -> "CRAFTING " + (ordinal + 1);
			case "result" -> "CRAFTING RESULT";
			case "head" -> "HELMET";
			case "chest" -> "CHESTPLATE";
			case "legs" -> "LEGGINGS";
			case "feet" -> "BOOTS";
			case "offhand" -> "OFFHAND";
			default -> role.toUpperCase(Locale.ROOT);
		};
	}
}
