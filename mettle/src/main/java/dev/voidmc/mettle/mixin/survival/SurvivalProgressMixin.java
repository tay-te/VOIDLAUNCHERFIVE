package dev.voidmc.mettle.mixin.survival;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.skill.survival.GeneralFeats;
import dev.voidmc.mettle.core.CharacterState;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Jack of All Trades: skills below level 10 learn 25 % faster. A stand-in until Progress#multiplier takes perk XP
 * bonuses itself (docs/requests/survival.md); chains with anything else on the same method.
 */
@Mixin(Progress.class)
abstract class SurvivalProgressMixin {
	@ModifyReturnValue(method = "multiplier", at = @At("RETURN"))
	private static double mettle$jackOfAllTrades(double multiplier, CharacterState state, Background background, ResourceKey<Skill> skill) {
		return multiplier * GeneralFeats.jackBonus(state, skill);
	}
}
