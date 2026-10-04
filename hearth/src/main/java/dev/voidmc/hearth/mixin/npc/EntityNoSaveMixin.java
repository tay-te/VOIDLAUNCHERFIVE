package dev.voidmc.hearth.mixin.npc;

import dev.voidmc.hearth.npc.caravan.Caravans;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The caravans' pack animals are views, like folk: they exist only while a player watches the caravan go by and must
 * never be written into a chunk (a mule left in a field after a restart would be a mule nobody owns). Entities tagged
 * {@link Caravans#TAG} are therefore never saved.
 */
@Mixin(Entity.class)
public abstract class EntityNoSaveMixin {
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void hearth$caravanBeastsAreNotSaved(CallbackInfoReturnable<Boolean> cir) {
		if (((Entity) (Object) this).entityTags().contains(Caravans.TAG)) {
			cir.setReturnValue(false);
		}
	}
}
