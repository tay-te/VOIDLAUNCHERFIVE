package dev.voidmc.mettle.mixin.talents;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.cards.TalentHooks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Light Step (a talent): a sneaking player sets off no pressure plate or tripwire. */
@Mixin(Entity.class)
abstract class TalentsEntityMixin {
	@ModifyReturnValue(method = "isIgnoringBlockTriggers", at = @At("RETURN"))
	private boolean mettle$lightStep(boolean ignores) {
		return TalentHooks.ignoresTriggers((Entity) (Object) this, ignores);
	}
}
