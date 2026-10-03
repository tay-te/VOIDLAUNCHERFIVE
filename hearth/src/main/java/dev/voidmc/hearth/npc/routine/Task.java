package dev.voidmc.hearth.npc.routine;

import net.minecraft.server.level.ServerLevel;

/**
 * One thing a folk is doing now: walk somewhere, sit and eat, hammer at the anvil, place the next block. Ticked by
 * the brain at the folk's think rate with the ticks elapsed since the last call; a task that ends hands control back
 * and the brain plans the next one from the activity.
 */
public interface Task {
	Status tick(FolkBrain brain, ServerLevel level, int elapsed);

	/** Called when the task ends or is interrupted: undo poses, stand up, put tools away. */
	default void stop(FolkBrain brain, ServerLevel level) {
	}

	/** True for tasks nothing should interrupt for a routine replan (a guard in a fight). */
	default boolean urgent() {
		return false;
	}

	/** A short description for the debug command. */
	default String describe() {
		return getClass().getSimpleName();
	}

	enum Status {
		RUNNING, DONE, FAILED
	}
}
