package dev.voidmc.hub.model;

import java.util.List;

/**
 * Everything the hovered-item card shows about a stack, as plain values. Filled from an
 * {@code ItemStack} by {@code dev.voidmc.hub.screen.ItemFactsReader}; kept free of Minecraft types
 * so the card's data model is tested without the game.
 *
 * @param name display name (custom names included)
 * @param rarity common / uncommon / rare / epic
 * @param category one of {@link Category}
 * @param attackDamage total attack damage when held in the main hand, NaN if not a weapon/tool
 * @param attackSpeed attacks per second when held, NaN if not a weapon/tool
 * @param armor armour points when worn, 0 if not armour
 * @param nutrition food points, 0 if not food
 */
public record ItemFacts(
	String name,
	String rarity,
	Category category,
	int count,
	int maxStack,
	int damage,
	int maxDamage,
	double attackDamage,
	double attackSpeed,
	int armor,
	int nutrition,
	List<Enchant> enchantments
) {

	public enum Category {
		WEAPON, TOOL, ARMOUR, BLOCK, FOOD, ITEM;

		/** Filter chip this category answers to ("all" matches everything). */
		public String filter() {
			return switch (this) {
				case WEAPON, TOOL, ARMOUR -> "tools";
				case BLOCK -> "blocks";
				case FOOD -> "food";
				case ITEM -> "items";
			};
		}
	}

	/** One enchantment: its display name without the level, the level, and the max level. */
	public record Enchant(String name, int level, int maxLevel) {
	}

	public boolean damageable() {
		return this.maxDamage > 0;
	}

	public int durability() {
		return Math.max(0, this.maxDamage - this.damage);
	}

	public boolean matchesFilter(String filter) {
		return filter == null || filter.isEmpty() || "all".equals(filter) || this.category.filter().equals(filter);
	}
}
