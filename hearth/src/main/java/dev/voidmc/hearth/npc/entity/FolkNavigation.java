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
		boolean scaffold = this.mob.getInBlockState().is(Blocks.SCAFFOLDING) || this.mob.getBlockStateOn().is(Blocks.SCAFFOLDING);
		if (scaffold && this.path != null && !this.path.isDone()) {
			Node next = this.path.getNextNode();
			descend = next.y < this.mob.getBlockY();
		}
		if (scaffold && !descend && this.mob instanceof Folk folk && folk.job() != dev.voidmc.hearth.api.JobType.BUILDER) {
			// Anyone but a builder who walks into a site's scaffold column is carried up it (a climbable block and a
			// push against it), and up top no path leads anywhere: they keep low and come down instead.
			descend = true;
		}
		// Only undo a sneak this navigation started: a builder climbing down its own scaffold sneaks on purpose.
		if (descend != this.sneaking) {
			this.sneaking = descend;
			this.mob.setShiftKeyDown(descend);
		}
	}
}
