package dev.voidmc.bestiary.ai;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The one goal Bestiary adds to a creature it takes over (BESTIARY.md §2.2), at the priority of the attack goals it
 * replaced, with the flags MOVE and LOOK: vanilla's other goals (floating, fleeing the sun, avoiding cats, breaking
 * doors, wandering when unaware) keep their places around it. Perception runs from here every 4 ticks, tactics every
 * 5 while engaged, the executor every tick.
 */
public final class BestiaryCombatGoal extends Goal {
	private final Brain brain;
	private long lastSense = Long.MIN_VALUE / 2;
	/** Creatures that ran a Bestiary tick this server tick (the perf gate counts them). */
	public static long ticked;

	public BestiaryCombatGoal(Brain brain) {
		this.brain = brain;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private void sense() {
		if (!(this.brain.mob.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		// Staggered: each creature senses on its own phase of the 4-tick cycle.
		if (now - this.lastSense >= Perception.PERIOD && (now + this.brain.mob.getId()) % 2 == 0 || now - this.lastSense >= Perception.PERIOD + 2) {
			this.lastSense = now;
			Perception.sense(this.brain, level, now);
			if (now % 20 == this.brain.mob.getId() % 20) {
				Squads.morale(this.brain, level, now);
			}
		}
	}

	@Override
	public boolean canUse() {
		this.sense();
		return this.brain.awareness != Brain.Awareness.UNAWARE || this.brain.move != null;
	}

	@Override
	public boolean canContinueToUse() {
		this.sense();
		return this.brain.awareness != Brain.Awareness.UNAWARE || this.brain.move != null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		if (this.brain.move != null && this.brain.mob.level() instanceof ServerLevel level) {
			Executor.end(this.brain, level, level.getGameTime(), true);
		}
		Tokens.release(this.brain);
		Executor.setGuard(this.brain, false);
		this.brain.mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!(this.brain.mob.level() instanceof ServerLevel level)) {
			return;
		}
		ticked++;
		Brain b = this.brain;
		if (b.awareness == Brain.Awareness.ENGAGED && b.target != null && b.mob.getTarget() != b.target) {
			b.mob.setTarget(b.target);
		}
		Executor.tick(b, level, level.getGameTime());
	}
}
