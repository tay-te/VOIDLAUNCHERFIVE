package dev.voidmc.fray.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Status build-up and shared statuses (CONTRACTS.md §6). Server thread only.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface StatusService {
	/** Adds build-up to a metered status (scaled by the target's status resistance, Wet and Oiled); a full meter procs it. */
	void addBuildUp(LivingEntity target, StatusType status, float amount, @Nullable Entity source);

	/** How full a metered status is, 0..1. */
	float meter(LivingEntity target, StatusType status);

	/** The meter's size for this target: base 100 x (1 + status resistance). */
	float threshold(LivingEntity target, StatusType status);

	/** Applies a status for {@code ticks} (0: its default duration). */
	void apply(LivingEntity target, StatusType status, int ticks, @Nullable Entity source);

	boolean has(LivingEntity target, StatusType status);

	/** Ticks left, 0 when absent. */
	int remaining(LivingEntity target, StatusType status);

	void clear(LivingEntity target, StatusType status);

	/** True while the entity can't act (Staggered, Dazed, Frozen, the Shock stun). */
	boolean incapacitated(LivingEntity target);
}
