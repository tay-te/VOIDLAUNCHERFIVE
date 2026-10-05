package dev.voidmc.mettle.character.skill.smithing;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The anvil's Smithing perks, on repairs and renames only (jobs that leave the enchantments as they were), so neither
 * makes enchanting cheaper. <b>Tinker</b> takes 1 / 2 / 3 levels off; <b>A Hundred Hammers</b> drops the "prior work"
 * part of the price and keeps it from growing. Runs on both sides so the client shows the price the server charges.
 */
public final class Anvils {
	private Anvils() {
	}

	/** Whether this anvil job is a repair or rename: no book, and the enchantments come out as they went in. */
	public static boolean repairJob(ItemStack input, ItemStack addition, ItemEnchantments.Mutable after) {
		return !addition.has(DataComponents.STORED_ENCHANTMENTS) && after.toImmutable().equals(EnchantmentHelper.getEnchantmentsForCrafting(input));
	}

	/** The price after Tinker and A Hundred Hammers ({@code cost} is vanilla's, {@code tax} its prior-work part). */
	public static long cost(Player player, long cost, long tax) {
		return SmithingRules.anvilCost(cost, tax, Perks.rank(player, SmithingSkill.TINKER), Perks.has(player, SmithingSkill.HUNDRED_HAMMERS));
	}

	public static boolean keepsPriorWork(Player player) {
		return Perks.has(player, SmithingSkill.HUNDRED_HAMMERS);
	}
}
