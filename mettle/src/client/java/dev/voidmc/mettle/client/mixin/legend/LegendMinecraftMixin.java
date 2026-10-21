package dev.voidmc.mettle.client.mixin.legend;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.client.legend.LegendClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Long Watch: the hostiles it found glow on this client only (the watcher's and their allies'). */
@Mixin(Minecraft.class)
abstract class LegendMinecraftMixin {
	@ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
	private boolean mettle$longWatch(boolean glowing, @Local(argsOnly = true) Entity entity) {
		return glowing || LegendClient.watched(entity);
	}
}
