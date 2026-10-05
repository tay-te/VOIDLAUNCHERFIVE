package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * Smithing XP (BALANCE.md §3): 8 per metal unit in a crafted tool, weapon or armour piece (an ingot is 1, a diamond 2),
 * 2 for wooden and stone tools, 1 per ingot taken from a furnace, 50 for a smithing upgrade, 10 + 5 per level for an
 * anvil job. Its perk tree is written in TREES.md; the XP lands now so Forgemaster can be earned.
 */
public final class SmithingSkill {
	public static final ResourceKey<Skill> SMITHING = Progress.skill("smithing");

	private SmithingSkill() {
	}

	/** From ItemStack#onCraftedBy: crafting tables, furnaces and smithing tables all end here. */
	public static void crafted(ServerPlayer player, ItemStack result, int count) {
		String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
		if (path.startsWith("netherite_") && (gear(result) > 0)) {
			Progress.award(player, SMITHING, 50.0 * count);
			return;
		}
		int units = gear(result);
		if (units > 0) {
			double perUnit = path.startsWith("diamond_") ? 16 : path.startsWith("iron_") || path.startsWith("golden_") || path.startsWith("copper_") ? 8 : 0;
			Progress.award(player, SMITHING, perUnit > 0 ? perUnit * units * count : 2.0 * count);
			return;
		}
		if (path.endsWith("_ingot")) {
			Progress.award(player, SMITHING, count);
		}
	}

	/** Anvil job taken: XP grows with the levels it cost. */
	public static void anvil(ServerPlayer player, int cost) {
		Progress.award(player, SMITHING, 10 + 5.0 * Math.max(0, cost));
	}

	/** Metal units in a tool, weapon or armour piece (as in its recipe), or 0. */
	private static int gear(ItemStack stack) {
		if (stack.is(ItemTags.CHEST_ARMOR)) {
			return 8;
		}
		if (stack.is(ItemTags.LEG_ARMOR)) {
			return 7;
		}
		if (stack.is(ItemTags.HEAD_ARMOR)) {
			return 5;
		}
		if (stack.is(ItemTags.FOOT_ARMOR)) {
			return 4;
		}
		if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)) {
			return 3;
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.HOES)) {
			return 2;
		}
		if (stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.SPEARS)) {
			return 1;
		}
		return 0;
	}
}
