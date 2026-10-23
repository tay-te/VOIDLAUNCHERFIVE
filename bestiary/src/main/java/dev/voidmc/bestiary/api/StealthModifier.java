package dev.voidmc.bestiary.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The observer side of stealth (BESTIARY.md §3): Mettle registers its Shadow, Cloak of Dusk, Ghost, Vanish and Quiet
 * Feet here. {@code value} is the sense's current factor (1 = fully perceived): return the new factor. Bestiary never
 * hard-codes a perk.
 */
@FunctionalInterface
public interface StealthModifier {
	enum Sense {
		/** Sight range factor. */
		SIGHT,
		/** Footstep loudness factor. */
		STEPS
	}

	float modify(Mob observer, LivingEntity target, Sense sense, float value);
}
