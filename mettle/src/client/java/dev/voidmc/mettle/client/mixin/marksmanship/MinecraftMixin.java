package dev.voidmc.mettle.client.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.client.marksmanship.MarksmanshipClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hunter's Mark: marked entities draw with the glowing outline on this client only. Also starts
 * {@link MarksmanshipClient} until MettleClient calls it itself (see docs/requests/marksmanship.md).
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void mettle$marksmanshipClient(CallbackInfo ci) {
		MarksmanshipClient.init();
	}

	@ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
	private boolean mettle$huntersMark(boolean glowing, @Local(argsOnly = true) Entity entity) {
		return glowing || MarksmanshipClient.marked(entity);
	}
}
