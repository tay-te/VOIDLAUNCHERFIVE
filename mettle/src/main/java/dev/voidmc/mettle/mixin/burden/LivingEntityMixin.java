package dev.voidmc.mettle.mixin.burden;

import dev.voidmc.mettle.burden.Sprint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** No sprinting from Overburdened on, enforced on the server too, asking the same gate the client asks. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
	private boolean mettle$burdenSprint(boolean sprinting) {
		if (sprinting && (Object) this instanceof ServerPlayer player && Sprint.blockedByLoad(player)) {
			return false;
		}
		return sprinting;
	}
}
