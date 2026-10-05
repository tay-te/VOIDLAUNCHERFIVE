package dev.voidmc.mettle.mixin.survival;

import dev.voidmc.mettle.character.skill.survival.Food;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iron Stomach: raw meat, rotten flesh and spider eyes give no Hunger or Poison. */
@Mixin(ApplyStatusEffectsConsumeEffect.class)
abstract class SurvivalApplyStatusEffectsMixin {
	@Inject(method = "apply", at = @At("HEAD"), cancellable = true)
	private void mettle$ironStomach(Level level, ItemStack stack, LivingEntity user, CallbackInfoReturnable<Boolean> cir) {
		if (Food.ironStomach(user, stack)) {
			cir.setReturnValue(false);
		}
	}
}
