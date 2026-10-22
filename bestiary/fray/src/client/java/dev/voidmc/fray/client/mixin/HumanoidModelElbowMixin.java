package dev.voidmc.fray.client.mixin;

import dev.voidmc.fray.client.Elbows;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every humanoid model (player, armour, mannequin, zombie...) gets elbows when it is built (Elbows). */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelElbowMixin {
	@Inject(method = "<init>*", at = @At("TAIL"))
	private void fray$elbows(CallbackInfo ci) {
		Elbows.split((HumanoidModel<?>) (Object) this);
	}
}
