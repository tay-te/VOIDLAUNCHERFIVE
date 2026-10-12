package dev.voidmc.reel.mixin;

import dev.voidmc.reel.client.Director;
import dev.voidmc.reel.client.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * VOID LOD times its cross-fades, rebuilds and tile swaps by the wall clock. Offline, a frame that runs a game tick
 * takes longer to render than one that does not, so those timers would advance in a tick rhythm and far land would
 * change on it. While the director films, the frame's clock in LodEngine.update and LodRenderer.prepare is the
 * recording's instead. Only those two methods: elsewhere LOD measures real work. Skipped without VOID LOD.
 */
@Pseudo
@Mixin(targets = {"dev.voidmc.lod.core.LodEngine", "dev.voidmc.lod.render.LodRenderer"})
public abstract class LodClockMixin {
	@Redirect(method = {"update", "prepare"}, at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"), require = 0)
	private long reel$recordingClock() {
		Director director = Director.get();
		return director.clockActive() && Tuning.lodClock() ? director.lodNanos() : System.nanoTime();
	}
}
