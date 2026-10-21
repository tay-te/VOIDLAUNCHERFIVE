package dev.voidmc.mettle.mixin.legend;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Beastspeaker's Torc gives a fed animal a goal to follow its feeder. */
@Mixin(Mob.class)
public interface LegendMobAccess {
	@Accessor("goalSelector")
	GoalSelector mettle$goals();
}
