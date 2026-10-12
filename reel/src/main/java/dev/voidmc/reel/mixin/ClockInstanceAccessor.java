package dev.voidmc.reel.mixin;

import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The client's copy of a world clock, set by the director to the exact instant of each frame. */
@Mixin(ClientClockManager.ClientClockInstance.class)
public interface ClockInstanceAccessor {
	@Accessor("totalTicks")
	void reel$setTotalTicks(long ticks);

	@Accessor("partialTick")
	void reel$setPartialTick(float partial);
}
