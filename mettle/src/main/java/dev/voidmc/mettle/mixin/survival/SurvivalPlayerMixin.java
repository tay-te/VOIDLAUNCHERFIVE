package dev.voidmc.mettle.mixin.survival;

import dev.voidmc.mettle.character.skill.survival.Camp;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Self-Sufficient: everything that makes you hungry does so a quarter more slowly. */
@Mixin(Player.class)
abstract class SurvivalPlayerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$selfSufficient(float amount) {
		return Camp.exhaustion((Player) (Object) this, amount);
	}
}
