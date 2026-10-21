package dev.voidmc.mettle.mixin.stealth;

import dev.voidmc.mettle.character.skill.stealth.StealthHooks;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Quiet Feet, Silent Fall and Footpad: a sneaking (or climbing, swimming) player's steps and landings make no vibration. */
@Mixin(ServerLevel.class)
abstract class StealthServerLevelMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
		at = @At("HEAD"), cancellable = true)
	private void mettle$quietFeet(Holder<GameEvent> event, Vec3 at, GameEvent.Context context, CallbackInfo ci) {
		if (StealthHooks.silent(event, context.sourceEntity())) {
			ci.cancel();
		}
	}
}
