package dev.voidmc.fray.combat.math;

import dev.voidmc.fray.api.HitFlags;

/**
 * The pipeline's arithmetic (CONTRACTS.md §4.2, §7.6; COMBAT.md §10.2), free of Minecraft so it is unit-tested alone.
 */
public final class DamageMath {
	/** Stage 2 ceiling: the attacker's pools over the weapon's base (all sources together). */
	public static final float ATTACKER_CAP = 2.6F;
	/** Stage 3 ceiling: the largest situational multiplier times a weak point's. */
	public static final float SITUATIONAL_CAP = 3.0F;
	/** Incoming damage after every reduction, before armour, never below this share of the raw hit. */
	public static final float INCOMING_FLOOR = 0.25F;
	/** Damage between players (COMBAT.md §14). */
	public static final float PVP_SCALE = 0.6F;

	public static final float RIPOSTE = 2.0F;
	public static final float BACKSTAB = 2.0F;
	public static final float PLUNGE = 1.5F;
	public static final float EXECUTE = 2.5F;
	public static final float HEAVY = 1.6F;
	public static final float RUNNING = 1.2F;
	public static final float GUARD_COUNTER = 1.2F;
	public static final float ROLL = 1.1F;

	private DamageMath() {
	}

	/** Stage 2: (base + add) × (1 + Σ increased) × Π more, never negative. */
	public static float pools(float base, float add, float increased, float more) {
		return Math.max(0, (base + add) * (1 + increased) * more);
	}

	/** Stage 2's ceiling: the pooled value may not exceed {@code cap} × base. */
	public static float capAttacker(float base, float pooled, float cap) {
		return base <= 0 ? pooled : Math.min(pooled, base * cap);
	}

	/**
	 * Stage 3: situational multipliers don't multiply each other. The largest of riposte, backstab, plunge, execute,
	 * a charged release ({@code charged}, 1 when none) and a rolled crit ({@code critDamage} when {@code rolledCrit}),
	 * then a weak point's multiplier on top, capped.
	 */
	public static float situational(int flags, float charged, float critDamage, boolean rolledCrit, float weakPoint, float plunge, float cap) {
		float best = 1;
		if (HitFlags.has(flags, HitFlags.RIPOSTE)) {
			best = Math.max(best, RIPOSTE);
		}
		if (HitFlags.has(flags, HitFlags.BACKSTAB)) {
			best = Math.max(best, BACKSTAB);
		}
		if (HitFlags.has(flags, HitFlags.PLUNGE)) {
			best = Math.max(best, plunge);
		}
		if (HitFlags.has(flags, HitFlags.EXECUTE)) {
			best = Math.max(best, EXECUTE);
		}
		best = Math.max(best, charged);
		if (rolledCrit) {
			best = Math.max(best, critDamage);
		}
		float m = best * (HitFlags.has(flags, HitFlags.WEAK_POINT) ? Math.max(1, weakPoint) : 1);
		return Math.min(m, cap);
	}

	/** A charged release: ×1.6 (a heavy) rising to {@code max} as {@code charge} goes 0 → 1. */
	public static float charged(float charge, float max) {
		float c = Math.max(0, Math.min(1, charge));
		return HEAVY + (max - HEAVY) * c;
	}

	/** Vanilla's armour formula (CombatRules.getDamageAfterAbsorb without Breach), with a kind's toughness factor. */
	public static float armour(float damage, float armour, float toughness) {
		if (damage <= 0 || armour <= 0) {
			return damage;
		}
		float f = 2.0F + toughness / 4.0F;
		float g = Math.max(armour * 0.2F, Math.min(armour - damage / f, 20.0F));
		return damage * (1.0F - g / 25.0F);
	}

	/** A resistance in -1.0 … 0.9 (a 10 % floor; -1 doubles). */
	public static float resist(float damage, float resist) {
		return damage * (1 - Math.max(-1, Math.min(0.9F, resist)));
	}

	/** The incoming ceiling: after every reduction, at least {@link #INCOMING_FLOOR} of the raw hit. */
	public static float floor(float raw, float reduced) {
		return Math.max(reduced, raw * INCOMING_FLOOR);
	}

	/** Poise damage: damage before resistances × the weapon's poise factor × the action's (light 1, heavy 1.5, charged 2). */
	public static float poise(float damage, float weaponFactor, int flags) {
		float action = HitFlags.has(flags, HitFlags.CHARGED) ? 2.0F : HitFlags.has(flags, HitFlags.HEAVY) ? 1.5F : 1.0F;
		return damage * weaponFactor * action;
	}

	/** The shortest distance between segments {@code p1-q1} and {@code p2-q2} (weak-point tests). */
	public static double segmentDistance(net.minecraft.world.phys.Vec3 p1, net.minecraft.world.phys.Vec3 q1, net.minecraft.world.phys.Vec3 p2,
		net.minecraft.world.phys.Vec3 q2) {
		net.minecraft.world.phys.Vec3 d1 = q1.subtract(p1), d2 = q2.subtract(p2), r = p1.subtract(p2);
		double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
		double s, t;
		if (a <= 1e-9 && e <= 1e-9) {
			return p1.distanceTo(p2);
		}
		if (a <= 1e-9) {
			s = 0;
			t = clamp01(f / e);
		} else {
			double c = d1.dot(r);
			if (e <= 1e-9) {
				t = 0;
				s = clamp01(-c / a);
			} else {
				double b = d1.dot(d2), denom = a * e - b * b;
				s = denom > 1e-9 ? clamp01((b * f - c * e) / denom) : 0;
				t = (b * s + f) / e;
				if (t < 0) {
					t = 0;
					s = clamp01(-c / a);
				} else if (t > 1) {
					t = 1;
					s = clamp01((b - c) / a);
				}
			}
		}
		return p1.add(d1.scale(s)).distanceTo(p2.add(d2.scale(t)));
	}

	private static double clamp01(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
