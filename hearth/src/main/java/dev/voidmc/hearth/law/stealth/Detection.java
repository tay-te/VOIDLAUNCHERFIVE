package dev.voidmc.hearth.law.stealth;

/**
 * Whether a folk notices a player (docs/LIVING_WORLD.md §4): sight shortened by darkness, sneaking and a quiet,
 * unarmoured body; hearing carried by noise. Pure and cheap: the caller supplies what the world says (distance,
 * whether the folk's eyes reach the player, the light at the player) and gets a score from 0 (unnoticed) to 1 (plain
 * as day, close by).
 *
 * <p>The light rule of §1 makes this bite: a lit room is a dangerous room to rob, and a thief works the dark lanes.
 */
public final class Detection {
	/** Sight range left in pitch darkness, as a part of the full range (with {@code lightWeight} 1). */
	static final double DARK_FLOOR = 0.3;

	private Detection() {
	}

	/**
	 * What the player is doing, as the world sees it.
	 *
	 * @param light     the brightness at the player's eyes, 0–15 (block light, and sky light by day)
	 * @param sneaking  crouched
	 * @param sprinting running
	 * @param moving    moving at all this second
	 * @param armour    armour points worn, 0–20 (chain and plate clank)
	 * @param invisible under an invisibility effect
	 */
	public record Stance(int light, boolean sneaking, boolean sprinting, boolean moving, int armour, boolean invisible) {
		/** How loud the player's body is, 0–1: footsteps and armour. */
		public double bodyNoise() {
			double n = this.sprinting ? 0.7 : this.moving ? (this.sneaking ? 0.06 : 0.3) : 0.02;
			if (this.moving) {
				n += Math.clamp(this.armour, 0, 20) / 20.0 * (this.sneaking ? 0.25 : 0.4);
			}
			return Math.min(1.0, n);
		}
	}

	/**
	 * Who is looking.
	 *
	 * @param alertness 1 for an ordinary person; above for the curious, the cautious and guards, below for dreamers
	 */
	public record Observer(double alertness) {
		public static final Observer ORDINARY = new Observer(1.0);
	}

	/** The distance at which this observer would spot this stance, in blocks. */
	public static double sightRange(double baseRange, double lightWeight, Stance stance, Observer observer) {
		double dark = 1.0 - Math.clamp(stance.light, 0, 15) / 15.0;
		double lightFactor = 1.0 - Math.clamp(lightWeight, 0, 1) * (1.0 - DARK_FLOOR) * Math.pow(dark, 1.3);
		double stanceFactor = 1.0;
		if (stance.sneaking) {
			// crouching hides a quiet body well and an armoured one barely
			stanceFactor = 0.55 + 0.3 * Math.clamp(stance.armour, 0, 20) / 20.0;
		} else if (stance.sprinting) {
			stanceFactor = 1.15;
		}
		if (stance.invisible) {
			stanceFactor *= 0.15;
		}
		return baseRange * lightFactor * stanceFactor * observer.alertness;
	}

	/**
	 * How plainly the observer sees the player: 0 if out of view or beyond the (shortened) sight range, rising to 1 at
	 * arm's length.
	 *
	 * @param inView whether the observer's field of view and line of sight reach the player ({@code FolkHandle.canSee})
	 */
	public static double sight(double distance, boolean inView, double baseRange, double lightWeight, Stance stance, Observer observer) {
		if (!inView) {
			return 0;
		}
		double range = sightRange(baseRange, lightWeight, stance, observer);
		if (distance >= range) {
			return 0;
		}
		// Close by, anyone is seen; the score falls off toward the edge of the range.
		return Math.clamp(1.0 - Math.max(0, distance - 1.5) / Math.max(0.5, range - 1.5), 0, 1);
	}

	/**
	 * How plainly the observer hears the player: the player's body noise plus what the deed itself sounds like (a
	 * smashed door is loud, a lifted purse silent), over distance; walls muffle it.
	 *
	 * @param deedNoise 0 (silent) to 1 (a crash, a scream)
	 * @param clearPath nothing solid between them (otherwise sound carries half as far)
	 */
	public static double hearing(double distance, double hearingRange, Stance stance, double deedNoise, boolean clearPath, Observer observer,
		boolean asleep) {
		double noise = Math.min(1.0, stance.bodyNoise() + Math.clamp(deedNoise, 0, 1));
		double range = hearingRange * noise * (clearPath ? 1.0 : 0.5) * observer.alertness * (asleep ? 0.4 : 1.0);
		if (range <= 0 || distance >= range) {
			return 0;
		}
		return Math.clamp(1.0 - distance / range, 0, 1);
	}

	/**
	 * Whether a crime counts as witnessed by this observer: seen at all, or heard clearly enough to know who it was
	 * (only loud deeds: a fight, a smashed door).
	 */
	public static boolean witnessed(double sight, double hearing, double deedNoise) {
		return sight > 0 || deedNoise >= 0.8 && hearing >= 0.5;
	}
}
