package dev.voidmc.mettle.mixin.stealth;

import dev.voidmc.mettle.character.skill.stealth.StealthHooks;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hushed (a Stealth trait): sculk sensors, shriekers and Wardens hear the player's vibrations from less far. */
@Mixin(VibrationSystem.Listener.class)
abstract class StealthVibrationListenerMixin {
	@Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
	private void mettle$hushed(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 from, CallbackInfoReturnable<Boolean> cir) {
		if (context.sourceEntity() == null) {
			return;
		}
		VibrationSystem.Listener self = (VibrationSystem.Listener) (Object) this;
		self.getListenerSource().getPosition(level).ifPresent(at -> {
			if (StealthHooks.hushed(context.sourceEntity(), from, at, self.getListenerRadius())) {
				cir.setReturnValue(false);
			}
		});
	}
}
