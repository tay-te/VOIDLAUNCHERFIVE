package dev.voidmc.reel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.reel.client.Director;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While the director films, the integrated server sends a few chunks more than the client draws, so a moving camera
 * finds the land ahead already there instead of arriving a few ticks late at the edge of the view.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	@ModifyExpressionValue(method = "tickServer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 0))
	private int reel$sendAhead(int viewDistance) {
		return Director.get().clockActive() ? viewDistance + Director.SEND_AHEAD : viewDistance;
	}
}
