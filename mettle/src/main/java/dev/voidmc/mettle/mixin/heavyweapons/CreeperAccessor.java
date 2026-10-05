package dev.voidmc.mettle.mixin.heavyweapons;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Stagger puts a creeper's fuse out: its swell has no public setter. */
@Mixin(Creeper.class)
public interface CreeperAccessor {
	@Accessor("swell")
	void mettle$heavySetSwell(int swell);
}
