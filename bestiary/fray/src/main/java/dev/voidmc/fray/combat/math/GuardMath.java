package dev.voidmc.fray.combat.math;

import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitFlags;

/**
 * Block and parry (COMBAT.md §7), pure. Times are ticks since the guard was pressed.
 */
public final class GuardMath {
	/** Parry windows never exceed this (CONTRACTS.md §7.6, PvE; the accessibility setting doubles separately). */
	public static final int PARRY_CAP = 9;
	/** A press within this many ticks of the last attempt gets half a window (no spam). */
	public static final int SPAM_TICKS = 12;
	/** A missed parry released early: whiff recovery. */
	public static final int WHIFF_TICKS = 8;
	public static final float PARRY_COST = 6;
	public static final float PARRY_REFUND = 15;
	public static final float FEINT_COST = 8;
	/** Guard counter: an attack within this many ticks of blocking a hit. */
	public static final int COUNTER_TICKS = 6;

	/**
	 * @param parryWindow   ticks 0 … window-1 after the press parry
	 * @param blockFrom     the block is up from this tick while held
	 * @param physical      share of physical damage blocked
	 * @param elemental     share of elemental
	 * @param occult        share of occult
	 * @param staminaPerPoint stamina per blocked point
	 * @param cover         degrees covered in front (180 = the front half)
	 */
	public record Spec(boolean shield, int parryWindow, int blockFrom, float physical, float elemental, float occult, float staminaPerPoint, float cover) {
		public float blocked(DamageKind kind) {
			return switch (kind.group) {
				case PHYSICAL -> this.physical;
				case ELEMENTAL -> this.elemental;
				case OCCULT -> this.occult;
			};
		}
	}

	/** A shield: 6-tick parry window, block from tick 2, front half, physical 100 %, elemental 50 %. */
	public static Spec shield(float stability) {
		return new Spec(true, 6, 2, 1.0F, 0.5F, 0.0F, stability, 180);
	}

	/** A weapon's guard: 5-tick parry window, block from tick 3, 120°, physical 60 %. */
	public static Spec weapon() {
		return new Spec(false, 5, 3, 0.6F, 0.0F, 0.0F, 1.6F, 120);
	}

	private GuardMath() {
	}

	/** The window for one press: base + bonus ticks, halved if spammed, capped. */
	public static int window(int base, int bonus, boolean spammed) {
		int w = Math.min(PARRY_CAP, base + Math.max(0, bonus));
		return spammed ? Math.max(1, w / 2) : w;
	}

	public static boolean inParry(int sincePress, int window) {
		return sincePress >= 0 && sincePress < window;
	}

	public static boolean covers(Spec spec, float degreesFromFront) {
		return Math.abs(degreesFromFront) <= spec.cover() / 2;
	}

	/**
	 * What a guard does with a hit landing {@code sincePress} ticks after the press, while {@code held}.
	 * Red (unblockable) and purple hits ignore guards; orange (heavy) hits parry only against a shield; arrows are
	 * blocked only by shields.
	 */
	public static GuardOutcome resolve(Spec spec, int sincePress, int window, boolean held, boolean coveredAngle, int hitFlags) {
		if (HitFlags.has(hitFlags, HitFlags.UNBLOCKABLE) || HitFlags.has(hitFlags, HitFlags.STATUS) || !coveredAngle) {
			return GuardOutcome.NONE;
		}
		if (HitFlags.has(hitFlags, HitFlags.PROJECTILE) && !spec.shield()) {
			return GuardOutcome.NONE;
		}
		boolean heavy = HitFlags.has(hitFlags, HitFlags.HEAVY_TELL);
		if (inParry(sincePress, window) && (!heavy || spec.shield())) {
			return GuardOutcome.PARRIED;
		}
		if (held && sincePress >= spec.blockFrom()) {
			return GuardOutcome.BLOCKED;
		}
		return GuardOutcome.NONE;
	}

	/** Stamina a block of {@code blockedPoints} costs (orange hits ×1.5). */
	public static float blockCost(Spec spec, float blockedPoints, int hitFlags, float costScale) {
		return blockedPoints * spec.staminaPerPoint() * (HitFlags.has(hitFlags, HitFlags.HEAVY_TELL) ? 1.5F : 1.0F) * costScale;
	}
}
