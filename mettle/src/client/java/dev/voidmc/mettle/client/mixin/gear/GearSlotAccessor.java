package dev.voidmc.mettle.client.mixin.gear;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Pocket slots move with the gear panel's layout (and out of sight in creative). Client screens only. */
@Mixin(Slot.class)
public interface GearSlotAccessor {
	@Mutable
	@Accessor("x")
	void mettle$setX(int x);

	@Mutable
	@Accessor("y")
	void mettle$setY(int y);
}
