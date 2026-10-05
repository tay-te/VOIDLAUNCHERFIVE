package dev.voidmc.mettle.character.skill.blades;

/** The numbers behind the Blades perks (TREES.md §4), free of the game so they can be tested. Times are in ticks. */
public final class BladeMath {
	/** Momentum: a hit counts as "the next" only within 2 s of the last one on the same foe. */
	public static final int MOMENTUM_WINDOW = 40;
	public static final int MOMENTUM_MAX_STACKS = 4;
	/** Unbroken: this long without taking damage arms the next critical. */
	public static final int UNBROKEN_QUIET = 100;
	public static final int PARRY_WINDOW = 8;
	/** A parry attempt, caught or not, can't be repeated for this long; stops right-click spam from being a wall. */
	public static final int PARRY_COOLDOWN = 20;
	public static final int STAGGER = 20;
	/** Master Strike: strike within 0.5 s of a block or parry. */
	public static final int COUNTER_WINDOW = 10;
	/** How long a parry's free critical waits for the riposte. */
	public static final int RIPOSTE_WINDOW = 100;
	public static final int WHIRLWIND_COOLDOWN = 160;
	public static final double WHIRLWIND_RADIUS = 3.0;
	public static final int WEAPON_MASTER_COOLDOWN = 100;
	public static final float EXECUTE_BELOW = 0.25f;

	private BladeMath() {
	}

	/** Quick Draw's bonus at a rank: +20 / 40 / 60 %. */
	public static float quickDraw(int rank) {
		return 0.2f * Math.max(0, Math.min(3, rank));
	}

	/** Fencer's swing speed at a rank: +5 / 10 / 15 %. */
	public static double fencer(int rank) {
		return 0.05 * Math.max(0, Math.min(3, rank));
	}

	/** Momentum stacks after a hit: one more for each quick hit on the same foe (up to four), otherwise none. */
	public static int momentum(int stacks, boolean sameTarget, long ticksSinceLast) {
		if (!sameTarget || ticksSinceLast > MOMENTUM_WINDOW || ticksSinceLast < 0) {
			return 0;
		}
		return Math.min(MOMENTUM_MAX_STACKS, stacks + 1);
	}

	public static float momentumBonus(int stacks) {
		return 0.1f * stacks;
	}

	/** Executioner: a living foe left below a quarter of its health. */
	public static boolean executes(float healthLeft, float maxHealth) {
		return healthLeft > 0 && healthLeft < EXECUTE_BELOW * maxHealth;
	}

	/** Executioner against bosses and players: whether this hit would take them below a quarter. */
	public static boolean finishingBlow(float health, float damage, float maxHealth) {
		return health - damage < EXECUTE_BELOW * maxHealth;
	}

	public static boolean unbrokenReady(long now, long lastHurt, long lastUsed) {
		return now - Math.max(lastHurt, lastUsed) >= UNBROKEN_QUIET;
	}

	/** Whether something at (dx, dz) from you is in front of a look direction (lx, lz): within 90° either side. */
	public static boolean inFront(double lookX, double lookZ, double dx, double dz) {
		return lookX * dx + lookZ * dz > 0;
	}

	/**
	 * Bleed wounds on one foe: each deals 1 damage a second for 4 s. A fourth wound reopens the one nearest its end
	 * instead of stacking further.
	 */
	public static final class Wounds {
		public static final int MAX = 3;
		public static final int SECONDS = 4;
		private final int[] left = new int[MAX];

		/** Opens a wound and returns how many are open now. */
		public int open() {
			int slot = 0;
			for (int i = 0; i < MAX; i++) {
				if (this.left[i] < this.left[slot]) {
					slot = i;
				}
			}
			this.left[slot] = SECONDS;
			return this.count();
		}

		/** One second passes: returns the damage dealt (one per open wound) and closes the spent ones. */
		public int tick() {
			int damage = 0;
			for (int i = 0; i < MAX; i++) {
				if (this.left[i] > 0) {
					damage++;
					this.left[i]--;
				}
			}
			return damage;
		}

		public int count() {
			int n = 0;
			for (int l : this.left) {
				if (l > 0) {
					n++;
				}
			}
			return n;
		}

		public boolean healed() {
			return this.count() == 0;
		}
	}
}
