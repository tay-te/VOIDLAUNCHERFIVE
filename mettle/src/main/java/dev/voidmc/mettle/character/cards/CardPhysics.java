package dev.voidmc.mettle.character.cards;

/**
 * Vanilla movement physics, so a card that says "X higher" or "X faster" gives X (docs/CARDS.md §4.2). A jump's height
 * grows with the square of its strength, and water efficiency speeds swimming far from linearly, so the attribute
 * modifier is worked out from the promise rather than copied from it. Pure, so tests replay the same physics.
 */
public final class CardPhysics {
	/** Vanilla's jump strength. */
	public static final double JUMP = 0.42;

	private CardPhysics() {
	}

	/** The height a jump of this upward speed peaks at (vanilla gravity 0.08 and drag 0.98 a tick). */
	public static double apex(double v) {
		double y = 0;
		double best = 0;
		for (int t = 0; t < 60 && v > -1; t++) {
			y += v;
			v = (v - 0.08) * 0.98;
			best = Math.max(best, y);
		}
		return best;
	}

	/** The jump-strength share (ADD_MULTIPLIED_BASE) that makes a jump peak {@code higher} more (0.25 = a quarter higher). */
	public static double jumpStrengthFor(double higher) {
		if (higher <= 0) {
			return higher;
		}
		double want = apex(JUMP) * (1 + higher);
		double lo = 0;
		double hi = 2;
		for (int i = 0; i < 40; i++) {
			double mid = (lo + hi) / 2;
			if (apex(JUMP * (1 + mid)) < want) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) / 2;
	}

	/** Top swimming speed (blocks a tick, not sprinting, off the ground) at this water movement efficiency. */
	public static double swimSpeed(double efficiency) {
		double w = Math.min(1, efficiency) * 0.5;
		double slowDown = 0.8 + (0.546 - 0.8) * w;
		double push = 0.02 + (0.1 - 0.02) * w;
		return push * slowDown / (1 - slowDown);
	}

	/** The water movement efficiency (ADD_VALUE) that makes swimming {@code faster} more (0.15 = 15 % faster). */
	public static double waterEfficiencyFor(double faster) {
		if (faster <= 0) {
			return faster;
		}
		double want = swimSpeed(0) * (1 + faster);
		if (swimSpeed(1) <= want) {
			return 1;
		}
		double lo = 0;
		double hi = 1;
		for (int i = 0; i < 40; i++) {
			double mid = (lo + hi) / 2;
			if (swimSpeed(mid) < want) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) / 2;
	}

	/** A card modifier's amount as the attribute needs it: jump and swim promises turned into strength and efficiency. */
	public static double attributeAmount(String attribute, String operation, double promised) {
		if (attribute.equals("minecraft:jump_strength") && operation.equals("add_multiplied_base")) {
			return jumpStrengthFor(promised);
		}
		if (attribute.equals("minecraft:water_movement_efficiency") && operation.equals("add_value")) {
			return waterEfficiencyFor(promised);
		}
		return promised;
	}
}
