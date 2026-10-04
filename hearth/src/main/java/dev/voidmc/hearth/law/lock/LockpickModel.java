package dev.voidmc.hearth.law.lock;

import java.util.Random;

/**
 * The lockpicking minigame's mechanics (docs/LIVING_WORLD.md §4), shared by the client screen that plays it and the
 * tests that pin its feel. KCD-style: work the pick round the keyhole to find the sweet spot, then turn the cylinder
 * under tension. Near the sweet spot the cylinder turns freely; off it, it turns only part way and binds, and forcing a
 * bound cylinder strains the pick until it snaps. Better locks have more pins (each with its own sweet spot, set one
 * after another while tension is held), narrower sweet spots and picks that give way sooner. Pure; one instance is one
 * attempt.
 */
public final class LockpickModel {
	/** The pick's travel: a half circle above the keyhole, in degrees. */
	public static final float ARC = 180.0F;
	/** Degrees per second the pick follows the hand (slower while turning: the pick is pinned under tension). */
	static final float PICK_SPEED = 540.0F;
	static final float PICK_SPEED_TURNING = 70.0F;
	/** Pin progress per second while turning freely, and lost per second once tension is let go. */
	static final float TURN_SPEED = 1.6F;
	static final float SPRING_SPEED = 2.2F;
	/** Seconds without tension before the last set pin drops back. */
	static final float DROP_DELAY = 0.7F;

	/**
	 * One lock, as the minigame needs it.
	 *
	 * @param tier       1 (very easy) to 5 (very hard)
	 * @param sweet      the sweet spot of each pin, degrees along the arc
	 * @param tolerance  half-width of a sweet spot in degrees (where the cylinder turns all the way)
	 * @param falloff    degrees beyond the tolerance over which the cylinder's give falls to nothing
	 * @param strainRate how fast a bound pick strains (1 = snaps after a second of full force)
	 */
	public record Spec(int tier, float[] sweet, float tolerance, float falloff, float strainRate) {
		public int pins() {
			return this.sweet.length;
		}
	}

	/** The lock for a tier and seed, eased by the picker's skill (0–20). Deterministic. */
	public static Spec spec(int tier, long seed, int skill) {
		int t = Math.clamp(tier, 1, 5);
		int pins = switch (t) {
			case 1, 2 -> 1;
			case 3 -> 2;
			default -> 3;
		};
		Random random = new Random(seed);
		float[] sweet = new float[pins];
		for (int i = 0; i < pins; i++) {
			// keep sweet spots off the very ends of the arc, where the hand cannot comfortably hold the pick
			sweet[i] = 15.0F + random.nextFloat() * (ARC - 30.0F);
		}
		float tolerance = new float[]{14.0F, 9.0F, 6.5F, 4.5F, 3.0F}[t - 1] + Math.clamp(skill, 0, 20) * 0.15F;
		float falloff = new float[]{45.0F, 38.0F, 32.0F, 26.0F, 22.0F}[t - 1];
		float strain = new float[]{0.55F, 0.7F, 0.85F, 1.05F, 1.3F}[t - 1] * (1.0F - Math.clamp(skill, 0, 20) * 0.015F);
		return new Spec(t, sweet, tolerance, falloff, strain);
	}

	/** What a step of the game did. */
	public enum Event {
		NONE,
		/** A pin set: the cylinder turned a notch further (a click). */
		PIN_SET,
		/** A set pin dropped back for want of tension. */
		PIN_DROPPED,
		/** The pick snapped. */
		BROKE,
		/** The last pin set and the lock opened. */
		OPENED
	}

	private final Spec spec;
	private float pick = ARC / 2;
	private float progress;
	private int setPins;
	private float strain;
	private float idle;
	private boolean bound;
	private boolean open;
	private int breaks;

	public LockpickModel(Spec spec) {
		this.spec = spec;
	}

	/**
	 * Advances the game.
	 *
	 * @param target  where the hand wants the pick, degrees along the arc
	 * @param turning tension applied (the cylinder is being turned)
	 * @param dt      seconds
	 */
	public Event tick(float target, boolean turning, float dt) {
		if (this.open) {
			return Event.NONE;
		}
		float speed = (turning ? PICK_SPEED_TURNING : PICK_SPEED) * dt;
		float t = Math.clamp(target, 0.0F, ARC);
		this.pick += Math.clamp(t - this.pick, -speed, speed);

		Event event = Event.NONE;
		this.bound = false;
		if (turning) {
			this.idle = 0;
			float give = give(this.pick);
			if (this.progress < give) {
				this.progress = Math.min(give, this.progress + TURN_SPEED * dt);
				this.strain = Math.max(0, this.strain - dt * 0.3F);
			} else if (give < 1.0F) {
				// bound: forcing it strains the pick, faster the further off the sweet spot
				this.bound = true;
				this.strain += this.spec.strainRate * dt * (1.0F + (1.0F - give));
				if (this.strain >= 1.0F) {
					this.strain = 0;
					this.progress = 0;
					this.breaks++;
					if (this.setPins > 0) {
						this.setPins--;
					}
					return Event.BROKE;
				}
			}
			if (this.progress >= 1.0F) {
				this.setPins++;
				this.progress = 0;
				this.strain = 0;
				if (this.setPins >= this.spec.pins()) {
					this.open = true;
					return Event.OPENED;
				}
				event = Event.PIN_SET;
			}
		} else {
			this.progress = Math.max(0, this.progress - SPRING_SPEED * dt);
			this.strain = Math.max(0, this.strain - dt * 0.8F);
			if (this.progress <= 0 && this.setPins > 0) {
				this.idle += dt;
				if (this.idle >= DROP_DELAY) {
					this.idle = 0;
					this.setPins--;
					event = Event.PIN_DROPPED;
				}
			}
		}
		return event;
	}

	/** How far the current pin lets the cylinder turn with the pick at {@code angle}: 1 in the sweet spot, falling to 0. */
	public float give(float angle) {
		if (this.setPins >= this.spec.pins()) {
			return 1.0F;
		}
		float off = Math.abs(angle - this.spec.sweet[this.setPins]);
		if (off <= this.spec.tolerance) {
			return 1.0F;
		}
		return Math.max(0.0F, 1.0F - (off - this.spec.tolerance) / this.spec.falloff);
	}

	public Spec spec() {
		return this.spec;
	}

	/** The pick's position along the arc, degrees. */
	public float pick() {
		return this.pick;
	}

	/** How far the whole cylinder has turned, 0 (locked) to 1 (open). */
	public float rotation() {
		return Math.min(1.0F, (this.setPins + this.progress) / this.spec.pins());
	}

	public int setPins() {
		return this.setPins;
	}

	/** Strain on the pick, 0–1 (it snaps at 1). */
	public float strain() {
		return this.strain;
	}

	/** Whether the cylinder is bound right now (forced off the sweet spot): the pick shakes. */
	public boolean bound() {
		return this.bound;
	}

	public boolean open() {
		return this.open;
	}

	public int breaks() {
		return this.breaks;
	}
}
