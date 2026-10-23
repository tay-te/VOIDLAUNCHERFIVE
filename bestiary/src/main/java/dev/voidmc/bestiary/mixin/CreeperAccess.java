package dev.voidmc.bestiary.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A Staggered creeper's fuse resets (BESTIARY.md §9.1): hit it hard to defuse it. */
@Mixin(Creeper.class)
public interface CreeperAccess {
	@Accessor("swell")
	int bestiary$swell();

	@Accessor("swell")
	void bestiary$setSwell(int swell);
}
