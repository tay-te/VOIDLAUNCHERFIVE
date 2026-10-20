package dev.voidmc.hearth.mixin.npc;

import dev.voidmc.hearth.npc.caravan.Caravans;
import dev.voidmc.hearth.npc.work.field.FieldWork;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The caravans' pack animals are views, like folk: they exist only while a player watches the caravan go by and must
 * never be written into a chunk (a mule left in a field after a restart would be a mule nobody owns). Entities tagged
 * {@link Caravans#TAG} are therefore never saved, and neither are the living world's other views: a flock's beasts and
 * dog, a dock's boats, a stall's wares (docs/LIVING_WORLD_2.md §6.5, {@link FieldWork#NO_SAVE}).
 */
@Mixin(Entity.class)
public abstract class EntityNoSaveMixin {
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void hearth$caravanBeastsAreNotSaved(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self.entityTags().contains(Caravans.TAG) || FieldWork.noSave(self)) {
			cir.setReturnValue(false);
		}
	}
}
