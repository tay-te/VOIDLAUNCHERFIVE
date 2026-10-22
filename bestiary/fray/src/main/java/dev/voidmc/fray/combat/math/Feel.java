package dev.voidmc.fray.combat.math;

import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitFlags;

/**
 * Hit feel (COMBAT.md §5): how long a landed hit freezes the attacker's and the victim's pose (hit-stop) and how hard
 * it shakes the camera. Pure maths; the client applies both, the server never sees them (a hit-stop is a picture, never
 * a delay: server damage and timing are untouched).
 */
public final class Feel {
	/** The longest freeze, in ticks. */
	public static final int MAX_STOP = 9;
	/** The largest camera offset, in degrees, all impulses together. */
	public static final float MAX_SHAKE = 4.0F;
	/** Shake frequency along the push, and across it (Hz). */
	public static final float FREQ = 13F, FREQ_ACROSS = 21F;

	/** Freeze ticks for the attacker and for the victim. */
	public record Stop(int attacker, int victim) {
		public static final Stop NONE = new Stop(0, 0);
	}

	/** Whose camera a hit shakes: the victim's, the attacker's, or a bystander's (boss slams only). */
	public enum Role {
		INCOMING, OUTGOING, BYSTANDER
	}

	private Feel() {
	}

	/**
	 * Hit-stop by weight: light 2 (a running or rolling light 3), Art 3, heavy / plunge / guard counter 4, charged 5,
	 * riposte and execute 6, a Break 8; a crit and a kill each add one; parry 4 on both, guard break 5 / 6, a block 2
	 * (3 for heavies). Dodged hits, status procs and nothing-landed don't stop. Projectiles and spells freeze only the
	 * victim.
	 */
	public static Stop hitStop(int flags, GuardOutcome outcome, boolean broke, boolean killed) {
		if (HitFlags.has(flags, HitFlags.STATUS)) {
			return Stop.NONE;
		}
		int t;
		switch (outcome) {
			case DODGED, SLIP -> {
				return Stop.NONE;
			}
			case PARRIED -> {
				return new Stop(4, 4);
			}
			case GUARD_BREAK -> {
				return new Stop(5, 6);
			}
			case BLOCKED -> t = HitFlags.has(flags, HitFlags.HEAVY | HitFlags.CHARGED | HitFlags.HEAVY_TELL) ? 3 : 2;
			default -> {
				if (broke) {
					t = 8;
				} else if (HitFlags.has(flags, HitFlags.RIPOSTE | HitFlags.EXECUTE)) {
					t = 6;
				} else if (HitFlags.has(flags, HitFlags.CHARGED)) {
					t = 5;
				} else if (HitFlags.has(flags, HitFlags.HEAVY | HitFlags.PLUNGE | HitFlags.GUARD_COUNTER | HitFlags.HEAVY_TELL)) {
					t = 4;
				} else if (HitFlags.has(flags, HitFlags.ART)) {
					t = 3;
				} else if (HitFlags.has(flags, HitFlags.RUNNING | HitFlags.ROLL)) {
					t = 3;
				} else {
					t = 2;
				}
				if (HitFlags.has(flags, HitFlags.CRIT)) {
					t++;
				}
				if (killed) {
					t++;
				}
			}
		}
		t = Math.min(MAX_STOP, t);
		boolean ranged = HitFlags.has(flags, HitFlags.PROJECTILE | HitFlags.SPELL);
		return new Stop(ranged ? 0 : t, t);
	}

	/** True for a boss's slam: a heavy, charged, red, orange, purple or area strike by a boss. */
	public static boolean slam(int flags, boolean bossAttacker) {
		return bossAttacker && HitFlags.has(flags, HitFlags.HEAVY | HitFlags.CHARGED | HitFlags.HEAVY_TELL | HitFlags.UNBLOCKABLE | HitFlags.GRAB | HitFlags.AOE);
	}

	/** The hit's weight for the shake, 0..1: Break and boss slams 1, guard break 0.9, a light 0.25. */
	public static float weight(int flags, GuardOutcome outcome, boolean broke, boolean bossAttacker) {
		if (HitFlags.has(flags, HitFlags.STATUS)) {
			return 0;
		}
		float w;
		switch (outcome) {
			case DODGED, SLIP -> {
				return 0;
			}
			case PARRIED -> w = 0.35F;
			case GUARD_BREAK -> w = 0.9F;
			case BLOCKED -> w = HitFlags.has(flags, HitFlags.HEAVY | HitFlags.CHARGED | HitFlags.HEAVY_TELL) ? 0.45F : 0.3F;
			default -> {
				if (broke) {
					w = 1;
				} else if (HitFlags.has(flags, HitFlags.CHARGED)) {
					w = 0.7F;
				} else if (HitFlags.has(flags, HitFlags.RIPOSTE | HitFlags.EXECUTE)) {
					w = 0.65F;
				} else if (HitFlags.has(flags, HitFlags.HEAVY | HitFlags.PLUNGE | HitFlags.GUARD_COUNTER | HitFlags.HEAVY_TELL)) {
					w = 0.5F;
				} else if (HitFlags.has(flags, HitFlags.ART)) {
					w = 0.45F;
				} else {
					w = 0.25F;
				}
				if (HitFlags.has(flags, HitFlags.CRIT)) {
					w += 0.1F;
				}
			}
		}
		if (bossAttacker) {
			w = Math.max(w, slam(flags, true) ? 1 : 0.6F);
		}
		return Math.min(1, w);
	}

	/**
	 * The shake's peak in degrees before the player's intensity. Incoming: 2.2° × weight, scaled 0.55-1.45 by the damage
	 * as a share of max health (a quarter of it or more is the top). Outgoing: heavies and up only (1.1° × weight), a
	 * Break 1.6°. Bystanders: boss slams within 16 blocks, fading with distance.
	 */
	public static float amplitude(Role role, float weight, float damageFraction, float distance, boolean broke) {
		if (weight <= 0) {
			return 0;
		}
		return switch (role) {
			case INCOMING -> 2.2F * weight * (0.55F + 0.9F * Math.min(1, Math.max(0, damageFraction) * 4));
			case OUTGOING -> broke ? 1.6F : weight >= 0.5F ? 1.1F * weight : 0;
			case BYSTANDER -> weight >= 1 ? 1.4F * Math.max(0, 1 - distance / 16F) : 0;
		};
	}

	/** Decay time constant in seconds: 50 ms for a feather, 120 ms for the heaviest. */
	public static float tau(float weight) {
		return 0.05F + 0.07F * Math.max(0, Math.min(1, weight));
	}

	/** An impulse ends after four time constants. */
	public static float life(float weight) {
		return 4 * tau(weight);
	}

	/** One impulse's offset along its push at {@code t} seconds: a decaying cosine, peak 1 at 0. */
	public static float along(float t, float tau) {
		return t < 0 || t > 4 * tau ? 0 : (float) (Math.exp(-t / tau) * Math.cos(2 * Math.PI * FREQ * t));
	}

	/** Across the push: a smaller, faster wobble (0.3 of the peak) so the shake isn't a straight line. */
	public static float across(float t, float tau, float phase) {
		return t < 0 || t > 4 * tau ? 0 : (float) (0.3 * Math.exp(-t / tau) * Math.sin(2 * Math.PI * FREQ_ACROSS * t + phase));
	}

	/**
	 * The push's direction on screen as (yaw, pitch), unit length: hit from the left, the view turns right; hit from
	 * the front, it tips up. {@code dx, dz}: the push in the world (victim minus attacker); {@code yawDeg}: the
	 * camera's yaw (0 faces +Z, the right-hand side is -X). No direction: straight down (a dip).
	 */
	public static float[] direction(double dx, double dz, float yawDeg) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-4) {
			return new float[] {0, 1};
		}
		double y = Math.toRadians(yawDeg);
		double fx = -Math.sin(y), fz = Math.cos(y);
		double rx = -Math.cos(y), rz = -Math.sin(y);
		double right = (dx * rx + dz * rz) / len;
		double fwd = (dx * fx + dz * fz) / len;
		float yaw = (float) right;
		float pitch = (float) (0.6 * fwd);
		float n = (float) Math.sqrt(yaw * yaw + pitch * pitch);
		return n < 1e-4 ? new float[] {0, 1} : new float[] {yaw / n, pitch / n};
	}

	/** Clamps a summed offset (yaw, pitch) to {@link #MAX_SHAKE} in length, in place. */
	public static void clamp(float[] off) {
		float n = (float) Math.sqrt(off[0] * off[0] + off[1] * off[1]);
		if (n > MAX_SHAKE) {
			off[0] *= MAX_SHAKE / n;
			off[1] *= MAX_SHAKE / n;
		}
	}
}
