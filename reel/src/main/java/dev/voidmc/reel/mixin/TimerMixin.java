package dev.voidmc.reel.mixin;

import dev.voidmc.reel.client.Director;
import dev.voidmc.reel.client.WelkinDesk;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the director films, game time stops following the wall clock: each pass of the game loop runs the ticks
 * and draws at the partial tick the director asks for, so a frame that takes a second to draw still shows 1/60 s
 * of the world. While the Welkin studio settles a held world, each pass runs ten ticks.
 */
@Mixin(targets = "net.minecraft.client.DeltaTracker$Timer")
public abstract class TimerMixin {
	@Shadow
	private float deltaTicks;

	@Shadow
	private float deltaTickResidual;

	@Shadow
	private long lastMs;

	@Inject(method = "advanceGameTime", at = @At("HEAD"), cancellable = true)
	private void reel$fixedStep(long currentMs, CallbackInfoReturnable<Integer> cir) {
		Director director = Director.get();
		if (director.clockActive()) {
			int ticks = director.stepClock(Minecraft.getInstance());
			this.lastMs = currentMs;
			this.deltaTicks = ticks;
			this.deltaTickResidual = director.partialTick();
			cir.setReturnValue(ticks);
		} else if (WelkinDesk.fastForward()) {
			// the Welkin studio's settle: as many ticks a pass as the game runs (the game test harness ticks the
			// integrated server in step with the client, so the server's sprint goes only as fast as the client ticks)
			this.lastMs = currentMs;
			this.deltaTicks = WelkinDesk.FAST_TICKS;
			this.deltaTickResidual = 0;
			cir.setReturnValue(WelkinDesk.FAST_TICKS);
		}
	}

	@Inject(method = "getGameTimeDeltaPartialTick", at = @At("HEAD"), cancellable = true)
	private void reel$partial(boolean ignoreFrozenGame, CallbackInfoReturnable<Float> cir) {
		Director director = Director.get();
		if (director.clockActive()) {
			cir.setReturnValue(director.partialTick());
		}
	}
}
