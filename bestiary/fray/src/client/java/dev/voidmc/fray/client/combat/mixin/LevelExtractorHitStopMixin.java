package dev.voidmc.fray.client.combat.mixin;

import dev.voidmc.fray.client.combat.feel.HitStop;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hit-stop (COMBAT.md §5): while an entity is frozen, the level's extraction hands back the render state captured on
 * the hit's first frame (its pose, Fray Motion's sampled clip included), moved along with the entity. Only the level's
 * pass: inventory previews extract through the dispatcher directly and never freeze.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorHitStopMixin {
	@Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
	private void fray$frozen(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
		EntityRenderState s = HitStop.frozen(entity, partialTick);
		if (s != null) {
			cir.setReturnValue(s);
		}
	}

	@Inject(method = "extractEntity", at = @At("RETURN"))
	private void fray$capture(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
		HitStop.capture(entity, cir.getReturnValue());
	}
}
