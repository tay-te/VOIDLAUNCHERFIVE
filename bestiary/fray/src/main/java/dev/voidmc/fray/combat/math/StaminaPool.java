package dev.voidmc.fray.combat.math;

/**
 * A player's stamina (CONTRACTS.md §7.2, COMBAT.md §8): spent only by combat actions; regenerates {@code regen} a
 * second starting {@link #REGEN_DELAY} ticks after the last spend, half while guarding; reaching 0 makes the player
 * Winded for {@link #WINDED_TICKS} (no dodge, heavy, charged or parry; a blocked hit breaks the guard).
 */
public final class StaminaPool {
	public static final int REGEN_DELAY = 12;
	public static final int WINDED_TICKS = 30;

	public float value;
	public float max;
	public long lastSpend = Long.MIN_VALUE / 2;
	public long windedUntil = Long.MIN_VALUE / 2;

	public StaminaPool(float max) {
		this.max = max;
		this.value = max;
	}

	public boolean winded(long now) {
		return now < this.windedUntil;
	}

	public int windedLeft(long now) {
		return (int) Math.max(0, this.windedUntil - now);
	}

	/** Ticks left before regeneration starts. */
	public int regenDelayLeft(long now) {
		return (int) Math.max(0, this.lastSpend + REGEN_DELAY - now);
	}

	/** True when the pool can pay for an action at all (any stamina left). */
	public boolean canAfford(float cost) {
		return cost <= 0 || this.value > 0;
	}

	/**
	 * Spends {@code cost} (the pool may hit 0, never below). Returns true if this spend made the player Winded.
	 */
	public boolean spend(float cost, long now) {
		if (cost <= 0) {
			return false;
		}
		this.value = Math.max(0, this.value - cost);
		this.lastSpend = now;
		if (this.value <= 0 && !this.winded(now)) {
			this.windedUntil = now + WINDED_TICKS;
			return true;
		}
		return false;
	}

	public void restore(float amount) {
		this.value = Math.min(this.max, this.value + Math.max(0, amount));
	}

	/** One tick of regeneration at {@code perSecond}. */
	public void tick(long now, float perSecond, boolean guarding) {
		if (this.value > this.max) {
			this.value = this.max;
		}
		if (now < this.lastSpend + REGEN_DELAY || this.value >= this.max) {
			return;
		}
		float rate = perSecond / 20F * (guarding ? 0.5F : 1F);
		this.value = Math.min(this.max, this.value + rate);
	}

	public boolean full() {
		return this.value >= this.max;
	}
}
