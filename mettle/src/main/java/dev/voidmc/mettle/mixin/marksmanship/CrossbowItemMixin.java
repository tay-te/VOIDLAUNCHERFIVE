package dev.voidmc.mettle.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import dev.voidmc.mettle.character.skill.marksmanship.MarksmanshipRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Quiverwise: crossbows load 40 % faster. The charge time feeds both the server's loading and the client's pull
 * animation, so one hook keeps them in step.
 */
@Mixin(CrossbowItem.class)
abstract class CrossbowItemMixin {
	@ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
	private static int mettle$quiverwise(int ticks, @Local(argsOnly = true) LivingEntity user) {
		return user instanceof Player player && Perks.has(player, MarksmanshipSkill.QUIVERWISE) ? MarksmanshipRules.crossbowCharge(ticks) : ticks;
	}
}
