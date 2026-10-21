package dev.voidmc.mettle.mixin.stealth;

import dev.voidmc.mettle.character.skill.stealth.StealthHooks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shadowstep: the server sees the jump in the movement packet; a sneak-jump at an unaware mob steps behind it. */
@Mixin(ServerPlayer.class)
abstract class StealthServerPlayerMixin {
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void mettle$shadowstep(CallbackInfo ci) {
		StealthHooks.jumped((ServerPlayer) (Object) this);
	}
}
