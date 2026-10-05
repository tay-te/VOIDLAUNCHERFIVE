package dev.voidmc.mettle.mixin.athletics;

import dev.voidmc.mettle.character.skill.athletics.Movement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Movement the client predicts, so it runs on both sides: the sprint modifiers, Swimmer's stroke and Spring's push. */
@Mixin(LivingEntity.class)
abstract class AthleticsLivingEntityMixin {
	// Right after vanilla adds or drops its own sprint modifier, so ours never lag a tick behind it.
	@Inject(method = "setSprinting", at = @At("TAIL"))
	private void mettle$sprintChanged(boolean sprinting, CallbackInfo ci) {
		if ((Object) this instanceof Player player) {
			Movement.refresh(player);
		}
	}

	@ModifyArg(method = "travelInWater", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"), index = 0)
	private float mettle$swimmer(float speed) {
		return Movement.swimSpeed((LivingEntity) (Object) this, speed);
	}

	// Inside vanilla's sprint branch, so only real sprint-jumps (not a pinned player's non-jump) get the push.
	@Inject(method = "jumpFromGround", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/LivingEntity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER))
	private void mettle$spring(CallbackInfo ci) {
		Movement.sprintJump((LivingEntity) (Object) this);
	}
}
