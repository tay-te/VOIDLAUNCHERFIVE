package dev.voidmc.hearth.law.pocket;

/**
 * The pickpocketing minigame's clock (docs/LIVING_WORLD.md §4): the mark's awareness fills while the player's hand is
 * in their pockets, faster while an item is being lifted; lift what you can and leave before it is full. Sleepers fill
 * slowly. Run on the server (the client only draws it), so a modified client cannot steal faster. Pure.
 */
public final class PickpocketModel {
	/** How much faster awareness fills while an item is being lifted. */
	static final double LIFT_FACTOR = 2.2;

	private final double rate;
	private double awareness;
	private int lifting = -1;
	private double liftProgress;
	private double liftSeconds;
	private boolean caught;

	/** @param rate awareness per second while just looking (1 = caught) */
	public PickpocketModel(double rate) {
		this.rate = Math.max(0.0001, rate);
	}

	/** Seconds it takes to lift an item of this weight (1: a coin, 8: a heavy tool). */
	public static double liftSeconds(int weight) {
		return 0.55 + Math.clamp(weight, 1, 10) * 0.28;
	}

	/** Starts lifting a slot (switching away from another drops it unlifted). */
	public void lift(int slot, int weight) {
		if (this.caught) {
			return;
		}
		this.lifting = slot;
		this.liftProgress = 0;
		this.liftSeconds = liftSeconds(weight);
	}

	/** Something startled the mark (they turned and glimpsed the thief): awareness jumps. */
	public void alarm(double amount) {
		if (this.caught) {
			return;
		}
		this.awareness = Math.min(1.0, this.awareness + Math.max(0, amount));
		if (this.awareness >= 1.0) {
			this.caught = true;
			this.lifting = -1;
		}
	}

	public void cancelLift() {
		this.lifting = -1;
		this.liftProgress = 0;
	}

	/**
	 * Advances the clock.
	 *
	 * @return the slot whose lift completed this step, or -1
	 */
	public int tick(double dt) {
		if (this.caught) {
			return -1;
		}
		this.awareness += this.rate * dt * (this.lifting >= 0 ? LIFT_FACTOR : 1.0);
		if (this.awareness >= 1.0) {
			this.awareness = 1.0;
			this.caught = true;
			this.lifting = -1;
			return -1;
		}
		if (this.lifting >= 0) {
			this.liftProgress += dt / this.liftSeconds;
			if (this.liftProgress >= 1.0) {
				int done = this.lifting;
				this.lifting = -1;
				this.liftProgress = 0;
				return done;
			}
		}
		return -1;
	}

	public double rate() {
		return this.rate;
	}

	public double awareness() {
		return this.awareness;
	}

	public int lifting() {
		return this.lifting;
	}

	public double liftProgress() {
		return this.liftProgress;
	}

	/** The current lift's progress per second (for the client to animate between updates). */
	public double liftRate() {
		return this.lifting >= 0 ? 1.0 / this.liftSeconds : 0;
	}

	/** Awareness per second right now (for the client to animate between updates). */
	public double awarenessRate() {
		return this.rate * (this.lifting >= 0 ? LIFT_FACTOR : 1.0);
	}

	public boolean caught() {
		return this.caught;
	}
}
