package dev.voidmc.mettle.character.skill.marksmanship;

/**
 * The Marksmanship numbers (TREES.md §6), free of players and worlds so they can be tested and so the client's bow
 * animation and the server's shot always agree.
 */
public final class MarksmanshipRules {
	/** Ticks a vanilla bow needs to reach full power. */
	public static final float FULL_DRAW = 20f;
	/** Quick Nock: a draw started this soon after the last shot is quicker. */
	public static final int QUICK_NOCK_WINDOW = 20;
	public static final float QUICK_NOCK_SPEED = 1.4f;
	/** Steppe Draw: full power at three-quarter draw. */
	public static final float STEPPE_SPEED = 4f / 3f;
	public static final float STEADY_BREATH_SPEED = 1.3f;
	public static final float SIXTY_PACES_SPEED = 1.2f;
	public static final float WINGSHOT_BONUS = 1.5f;
	public static final float CROSSBOW_CHARGE = 0.6f;
	public static final float QUIVERWISE_KEEP = 0.25f;
	public static final int DOUBLE_NOCK_EVERY = 5;
	public static final float ARROWSTORM_POWER = 0.75f;
	public static final int ARROWSTORM_INTERVAL = 10;
	/** Degrees between Volley's arrows, the same fan as Multishot. */
	public static final float VOLLEY_SPREAD = 10f;
	/** Degrees Double Nock's two arrows sit either side of the aim: "beside", not a fan. */
	public static final float DOUBLE_NOCK_SPREAD = 1.5f;
	public static final double SIXTY_PACES_DISTANCE = 60;
	public static final int HUNTERS_MARK_TICKS = 120;

	private MarksmanshipRules() {
	}

	/** How many times faster than vanilla a bow is drawn. */
	public static float drawSpeed(int steadyAimRank, boolean quickNock, boolean steppeDraw) {
		float speed = 1f + 0.10f * steadyAimRank;
		if (quickNock) {
			speed *= QUICK_NOCK_SPEED;
		}
		if (steppeDraw) {
			speed *= STEPPE_SPEED;
		}
		return speed;
	}

	/** Whether a draw that began at {@code drawStart} counts as nocked straight after the shot at {@code lastShot}. */
	public static boolean quickNock(long drawStart, long lastShot) {
		long gap = drawStart - lastShot;
		return lastShot >= 0 && gap >= 0 && gap <= QUICK_NOCK_WINDOW;
	}

	/** {@code BowItem#getPowerForTime} for fractional (perk-scaled) draw ticks: 0 to 1. */
	public static float bowPower(float ticks) {
		float pow = ticks / FULL_DRAW;
		pow = (pow * pow + pow * 2f) / 3f;
		return Math.min(pow, 1f);
	}

	/** Long Shot: +5 % for every full 5 blocks beyond 15, up to +50 %. Returned as a multiplier. */
	public static float longShot(double distance) {
		if (distance <= 15) {
			return 1f;
		}
		int steps = (int) Math.floor((distance - 15) / 5);
		return 1f + Math.min(0.5f, 0.05f * steps);
	}

	/**
	 * Deadeye: an arrow strikes the head when it enters the target's box no lower than a quarter block under its eyes.
	 * Arrows enter boxes through their faces, so a plunging shot through the top counts too.
	 */
	public static boolean headshot(double hitY, double eyeY) {
		return hitY >= eyeY - 0.25;
	}

	/** Double Nock: the shot that brings the counter to a multiple of five is doubled. */
	public static boolean doubleNock(int shotNumber) {
		return shotNumber > 0 && shotNumber % DOUBLE_NOCK_EVERY == 0;
	}

	/** Fletcher: arrow recipes make half again as many (4 → 6, spectral 2 → 3, tipped 8 → 12). */
	public static int fletcher(int count) {
		return count + (count + 1) / 2;
	}

	/** Retrieval: chance an arrow that hits comes back when its target dies (15 / 30 / 45 %). */
	public static float retrieval(int rank) {
		return 0.15f * rank;
	}

	/** Quiverwise: crossbow charge ticks, 40 % faster than {@code ticks}. */
	public static int crossbowCharge(int ticks) {
		return Math.max(1, (int) Math.floor(ticks * CROSSBOW_CHARGE));
	}
}
