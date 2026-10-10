package dev.voidmc.mettle.mixin.survival;

import dev.voidmc.mettle.character.skill.survival.Hunting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Quiet Approach: a skittish animal doesn't bolt from a sneaking hunter until they're within 4 blocks (vanilla and Expanse alike). */
@Mixin(AvoidEntityGoal.class)
abstract class SurvivalAvoidEntityGoalMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@Shadow
	protected @Nullable LivingEntity toAvoid;

	@Shadow
	@Final
	protected float maxDist;

	@Inject(method = "canUse", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/ai/goal/AvoidEntityGoal;toAvoid:Lnet/minecraft/world/entity/LivingEntity;",
		opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER), cancellable = true)
	private void mettle$quietApproach(CallbackInfoReturnable<Boolean> cir) {
		if (Hunting.staysCalm(this.mob, this.toAvoid) || dev.voidmc.mettle.character.cards.CardHooks.unnoticed(this.mob, this.toAvoid, this.maxDist)) {
			this.toAvoid = null;
			cir.setReturnValue(false);
		}
	}
}
