package dev.voidmc.mettle.mixin.stealth;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.mettle.character.skill.stealth.StealthHooks;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Vanishing and Never Seen: a mob that lost sight of the player gives up the hunt sooner. */
@Mixin(TargetGoal.class)
abstract class StealthTargetGoalMixin {
	@Shadow
	@Final
	protected Mob mob;

	@ModifyExpressionValue(method = "canContinueToUse", at = @At(value = "FIELD",
		target = "Lnet/minecraft/world/entity/ai/goal/target/TargetGoal;unseenMemoryTicks:I"))
	private int mettle$vanishing(int ticks) {
		return StealthHooks.unseenMemory(this.mob, ticks);
	}
}
