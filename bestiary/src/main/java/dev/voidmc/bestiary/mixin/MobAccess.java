package dev.voidmc.bestiary.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Bestiary's takeover of a creature's attack goals (BESTIARY.md §2.2). */
@Mixin(Mob.class)
public interface MobAccess {
	@Accessor("goalSelector")
	GoalSelector bestiary$goals();

	@Accessor("targetSelector")
	GoalSelector bestiary$targets();
}
