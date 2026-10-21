package dev.voidmc.mettle.mixin.talents;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.cards.TalentHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Light Sleeper (a talent): phantoms leave the player be one night in two. */
@Mixin(PhantomSpawner.class)
abstract class TalentsPhantomSpawnerMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"))
	private boolean mettle$lightSleeper(ServerPlayer player, Operation<Boolean> original) {
		return TalentHooks.phantomsPass(player, original.call(player));
	}
}
