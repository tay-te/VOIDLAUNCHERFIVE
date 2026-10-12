package dev.voidmc.reel.mixin;

import dev.voidmc.reel.client.Director;
import dev.voidmc.reel.client.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The samples of one frame are blended into it, so far land must be the same in all of them: a tile swapped in
 * between two samples would show as a ghost of both. On a frame's later samples LodEngine.update is skipped and
 * answers the tiles it chose for the first. Skipped without VOID LOD.
 */
@Pseudo
@Mixin(targets = "dev.voidmc.lod.core.LodEngine")
public abstract class LodFreezeMixin {
	@Unique
	private Object reel$lastDraw;

	@Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
	private void reel$holdTiles(CallbackInfoReturnable<Object> cir) {
		if (this.reel$lastDraw != null && Tuning.lodFreeze() && Director.get().midFrame()) {
			cir.setReturnValue(this.reel$lastDraw);
		}
	}

	@Inject(method = "update", at = @At("RETURN"), require = 0)
	private void reel$keepTiles(CallbackInfoReturnable<Object> cir) {
		this.reel$lastDraw = cir.getReturnValue();
	}
}
