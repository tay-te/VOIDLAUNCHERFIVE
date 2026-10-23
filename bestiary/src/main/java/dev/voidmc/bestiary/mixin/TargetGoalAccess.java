package dev.voidmc.bestiary.mixin;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which kind a target goal looks for: only the goal that hunts players makes way for Bestiary's perception. */
@Mixin(NearestAttackableTargetGoal.class)
public interface TargetGoalAccess {
	@Accessor("targetType")
	Class<?> bestiary$targetType();
}
