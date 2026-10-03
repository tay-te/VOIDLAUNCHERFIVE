package dev.voidmc.hearth.npc.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * Ground navigation that opens doors, climbs ({@link ClimbNodeEvaluator}) and gets down off scaffolding: standing on
 * scaffolding a mob only drops through while "sneaking", so the folk sneaks whenever its next step is below it.
 */
public class FolkNavigation extends GroundPathNavigation {
	public FolkNavigation(Mob mob, Level level) {
		super(mob, level);
	}

	@Override
	protected PathFinder createPathFinder(int maxVisitedNodes) {
		this.nodeEvaluator = new ClimbNodeEvaluator();
		this.nodeEvaluator.setCanOpenDoors(true);
		this.nodeEvaluator.setCanPassDoors(true);
		return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
	}

	@Override
	protected boolean canUpdatePath() {
		return super.canUpdatePath() || this.mob.onClimbable();
	}

	private boolean sneaking;

	@Override
	public void tick() {
		super.tick();
		boolean descend = false;
		if (this.path != null && !this.path.isDone()
			&& (this.mob.getInBlockState().is(Blocks.SCAFFOLDING) || this.mob.getBlockStateOn().is(Blocks.SCAFFOLDING))) {
			Node next = this.path.getNextNode();
			descend = next.y < this.mob.getBlockY();
		}
		// Only undo a sneak this navigation started: a builder climbing down its own scaffold sneaks on purpose.
		if (descend != this.sneaking) {
			this.sneaking = descend;
			this.mob.setShiftKeyDown(descend);
		}
	}
}
