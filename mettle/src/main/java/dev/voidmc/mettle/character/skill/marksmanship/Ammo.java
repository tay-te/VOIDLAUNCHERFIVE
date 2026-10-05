package dev.voidmc.mettle.character.skill.marksmanship;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * Where arrows come from: Fletcher (crafting more) and Quiverwise (spending fewer).
 *
 * <p>Quiverwise's arrow half is meant for arrows taken from the worn quiver; the quiver item doesn't exist yet (gear
 * module), so for now it applies to every arrow a bow or crossbow takes from the inventory. Once gear lands, narrow
 * {@link #keepArrow} to stacks that came from the quiver.
 */
public final class Ammo {
	private Ammo() {
	}

	/** Quiverwise: one arrow in four is fired without leaving the stack. */
	public static boolean keepArrow(ServerPlayer player, ItemStack projectile) {
		if (!projectile.is(ItemTags.ARROWS) || !Perks.has(player, MarksmanshipSkill.QUIVERWISE)
			|| player.getRandom().nextFloat() >= MarksmanshipRules.QUIVERWISE_KEEP) {
			return false;
		}
		MarksmanshipSkill.chip(player, MarksmanshipSkill.QUIVERWISE, MarksmanshipSkill.text(MarksmanshipSkill.QUIVERWISE));
		return true;
	}

	/** Fletcher: an arrow recipe's result, made half again as large. */
	public static ItemStack fletcher(ServerPlayer player, ItemStack result) {
		if (result.isEmpty() || !result.is(ItemTags.ARROWS) || !Perks.has(player, MarksmanshipSkill.FLETCHER)) {
			return result;
		}
		int count = Math.min(result.getMaxStackSize(), MarksmanshipRules.fletcher(result.getCount()));
		MarksmanshipSkill.chip(player, MarksmanshipSkill.FLETCHER, MarksmanshipSkill.text(MarksmanshipSkill.FLETCHER, count));
		return result.copyWithCount(count);
	}
}
