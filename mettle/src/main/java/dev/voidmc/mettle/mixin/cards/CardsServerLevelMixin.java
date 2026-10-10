package dev.voidmc.mettle.mixin.cards;

import dev.voidmc.mettle.character.cards.MythicEffects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Warden's Hush (a Mythic): its holder, and allies sneaking near them, make no vibrations at all. */
@Mixin(ServerLevel.class)
abstract class CardsServerLevelMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
		at = @At("HEAD"), cancellable = true)
	private void mettle$hush(Holder<GameEvent> event, Vec3 at, GameEvent.Context context, CallbackInfo ci) {
		if (MythicEffects.hushed(context.sourceEntity())) {
			ci.cancel();
		}
	}
}
