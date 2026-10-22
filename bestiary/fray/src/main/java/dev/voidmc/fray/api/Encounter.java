package dev.voidmc.fray.api;

import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

/**
 * A boss fight (CONTRACTS.md §10 ENCOUNTER_*): the boss, its phases, the Break meter and who took part.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface Encounter {
	enum Outcome {
		VICTORY, DEFEAT, RESET
	}

	int id();

	Mob boss();

	Component name();

	/** 0-based phase index. */
	int phase();

	/** Health fractions where each phase after the first starts, descending (0.55 for the Gallows Knight's phase 2). */
	float[] phaseThresholds();

	/** 0..1 of the Break meter. */
	float breakMeter();

	boolean broken();

	/** Damage dealt per participant. */
	Map<UUID, Float> participants();
}
