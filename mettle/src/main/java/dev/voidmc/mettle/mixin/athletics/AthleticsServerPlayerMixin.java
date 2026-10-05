package dev.voidmc.mettle.mixin.athletics;

import dev.voidmc.mettle.character.skill.athletics.AthleticsXp;
import dev.voidmc.mettle.character.skill.athletics.Movement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Athletics on the server: XP from the distance stats vanilla already keeps (CHARACTER.md §3.2), and the hunger of
 * sprinting for Marathon and Courier's Stride.
 */
@Mixin(ServerPlayer.class)
abstract class AthleticsServerPlayerMixin {
	@Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
	private void mettle$moved(Stat<?> stat, int count, CallbackInfo ci) {
		AthleticsXp.stat((ServerPlayer) (Object) this, stat, count);
	}

	// checkMovementStatistics feeds hunger for swimming (0), under water (1), on water (2), sprinting (3), crouching (4)
	// and walking (5); only the sprint costs anything on land.
	@ModifyArg(method = "checkMovementStatistics", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V", ordinal = 3))
	private float mettle$sprintHunger(float amount) {
		return Movement.sprintExhaustion((ServerPlayer) (Object) this, amount);
	}

	// The first exhaustion in jumpFromGround is the sprint-jump's.
	@ModifyArg(method = "jumpFromGround", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V", ordinal = 0))
	private float mettle$sprintJumpHunger(float amount) {
		return Movement.sprintExhaustion((ServerPlayer) (Object) this, amount);
	}
}
