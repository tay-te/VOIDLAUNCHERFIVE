package dev.voidmc.mettle.mixin.stealth;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** TargetGoal's mob, for the HurtByTargetGoal hook. */
@Mixin(TargetGoal.class)
public interface TargetGoalAccess {
	@Accessor("mob")
	Mob mettle$mob();
}
