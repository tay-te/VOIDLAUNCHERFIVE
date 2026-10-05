package dev.voidmc.mettle.client.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.client.marksmanship.MarksmanshipClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hunter's Mark: marked entities draw with the glowing outline on this client only. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
	private boolean mettle$huntersMark(boolean glowing, @Local(argsOnly = true) Entity entity) {
		return glowing || MarksmanshipClient.marked(entity);
	}
}
