package dev.voidmc.fray.combat.math;

/**
 * Attack-button timing (COMBAT.md §2, §4): a tap is a light; held 6+ ticks a heavy on release; held past 16 ticks the
 * swing charges, full after the moveset's charge ticks; held at full it releases itself after 40 ticks.
 */
public final class HoldMath {
	public static final int HEAVY_HOLD = 6;
	public static final int CHARGE_HOLD = 16;
	public static final int FULL_HOLD_RELEASE = 40;
	public static final int BUFFER = 6;

	public enum Kind {
		LIGHT, HEAVY, CHARGED
	}

	private HoldMath() {
	}

	public static Kind classify(int held) {
		return held < HEAVY_HOLD ? Kind.LIGHT : held < CHARGE_HOLD ? Kind.HEAVY : Kind.CHARGED;
	}

	/** 0 … 1 charge after {@code held} ticks with {@code chargeTicks} to full (charge speed scales it). */
	public static float charge(int held, int chargeTicks, double chargeSpeed) {
		if (held < CHARGE_HOLD) {
			return 0;
		}
		double ticks = Math.max(1, chargeTicks / Math.max(0.1, chargeSpeed));
		return (float) Math.min(1, (held - CHARGE_HOLD) / ticks);
	}

	/** True once a full charge has been held long enough to release itself. */
	public static boolean autoRelease(int held, int chargeTicks, double chargeSpeed) {
		double full = CHARGE_HOLD + Math.max(1, chargeTicks / Math.max(0.1, chargeSpeed));
		return held >= full + FULL_HOLD_RELEASE;
	}

	/** Stamina of a charged release: heavy cost rising to the charged maximum. */
	public static float chargedCost(float heavyCost, float maxCost, float charge) {
		return heavyCost + (maxCost - heavyCost) * Math.max(0, Math.min(1, charge));
	}
}
