package dev.voidmc.bestiary.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * A boss in Bestiary's encounter framework (BESTIARY.md §8): a mob that calls {@link BossEncounter#tick} from its own
 * server tick and answers these. The framework runs the arena, start and reset, the bar, multiplayer health, personal
 * loot and the music cues; the boss runs its moves and phase script.
 */
public interface Boss {
	/** The bar's name ("The Gallows Knight"). */
	Component bossName();

	/** The bar's subtitle (the place: "Gallows Hill"), or null. */
	@Nullable Component bossSubtitle();

	/** Health fractions where phases after the first start, descending ({0.55}). */
	float[] phaseThresholds();

	/** The personal loot table each eligible participant rolls. */
	Identifier personalLoot();

	/** A fight began with this target in the arena. */
	default void onFightStart(ServerLevel level, LivingEntity first) {
	}

	/** The boss crossed into {@code phase} (2, 3; 1-based). The data's phase is already set. */
	default void onPhase(ServerLevel level, int phase) {
	}

	/** The fight reset: put back what the fight changed (the cage, hazards). Health, place and the bar are the framework's. */
	default void onReset(ServerLevel level) {
	}

	/** Whether the boss is in the middle of something it shouldn't be pulled out of (a transition); the watchdog waits. */
	default boolean busyScripted() {
		return false;
	}
}
