package dev.voidmc.mettle.client.mixin.gear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.client.gear.GearClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A belt quick use starts eating or drinking without the use key held; vanilla would release the use at once. While a
 * belt use is running, the use key counts as held (only until that use ends).
 */
@Mixin(Minecraft.class)
abstract class GearMinecraftMixin {
	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
	private boolean mettle$holdForBelt(KeyMapping key, Operation<Boolean> original) {
		return original.call(key) || GearClient.holdsUse(key);
	}
}
