package dev.voidmc.fray.combat.math;

/**
 * Poise (COMBAT.md §9): hits take poise; at 0 a creature is Staggered, a player flinches. It refills fully after
 * {@code refillTicks} without poise damage.
 */
public final class PoiseMeter {
	public float max;
	public float value;
	public long lastHit = Long.MIN_VALUE / 2;

	public PoiseMeter(float max) {
		this.max = max;
		this.value = max;
	}

	/**
	 * Takes {@code amount} (plus {@code hyperArmour} absorbed first, not consumed). Returns true if poise broke (it is
	 * then refilled: the stagger is the punishment).
	 */
	public boolean hit(float amount, float hyperArmour, long now) {
		this.lastHit = now;
		float through = amount - Math.max(0, hyperArmour);
		if (through <= 0) {
			return false;
		}
		this.value -= through;
		if (this.value <= 0) {
			this.value = this.max;
			return true;
		}
		return false;
	}

	/** A player's flinch rule: a single hit whose poise damage is at least the remaining poise. */
	public boolean flinches(float amount, float hyperArmour) {
		return amount - Math.max(0, hyperArmour) >= this.value;
	}

	public void tick(long now, int refillTicks) {
		if (this.value < this.max && now - this.lastHit >= refillTicks) {
			this.value = this.max;
		}
	}
}
