package dev.voidmc.mettle.mixin.legend;

import dev.voidmc.mettle.character.cards.legend.LegendWild;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Greenway: a hostile that has lost sight of a holder counts the unseen time twice, so it gives up twice as fast. */
@Mixin(TargetGoal.class)
abstract class LegendTargetGoalMixin {
	@Shadow
	@Final
	protected Mob mob;

	@Shadow
	@Final
	protected boolean mustSee;

	@Shadow
	private int unseenTicks;

	@Shadow
	protected @Nullable LivingEntity targetMob;

	@Inject(method = "canContinueToUse", at = @At("HEAD"))
	private void mettle$greenwayTrail(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity target = this.mob.getTarget() != null ? this.mob.getTarget() : this.targetMob;
		if (this.mustSee && target != null && LegendWild.losesTrail(this.mob, target) && !this.mob.getSensing().hasLineOfSight(target)) {
			// Vanilla adds one for this look; this is the second.
			this.unseenTicks++;
		}
	}
}
