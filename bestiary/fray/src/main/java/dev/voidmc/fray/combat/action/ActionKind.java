package dev.voidmc.fray.combat.action;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.HitFlags;
import net.minecraft.resources.Identifier;

/**
 * Every player action the server runs, with the hit flags it carries. The ordinal is the network form (Ack, ActionPlay).
 */
public enum ActionKind {
	LIGHT(HitFlags.LIGHT),
	HEAVY(HitFlags.HEAVY),
	CHARGED(HitFlags.CHARGED | HitFlags.HEAVY),
	RUNNING(HitFlags.LIGHT | HitFlags.RUNNING),
	PLUNGE(HitFlags.LIGHT | HitFlags.PLUNGE),
	RIPOSTE(HitFlags.LIGHT | HitFlags.RIPOSTE | HitFlags.CRIT | HitFlags.UNBLOCKABLE),
	GUARD_COUNTER(HitFlags.LIGHT | HitFlags.GUARD_COUNTER),
	ROLL_ATTACK(HitFlags.LIGHT | HitFlags.ROLL),
	EXECUTE(HitFlags.HEAVY | HitFlags.EXECUTE | HitFlags.CRIT),
	DODGE(0),
	BACKSTEP(0),
	PARRY(0),
	WHIRLWIND(HitFlags.ART | HitFlags.AOE | HitFlags.HEAVY),
	EARTHSHAKER(HitFlags.ART | HitFlags.AOE | HitFlags.HEAVY);

	public static final ActionKind[] VALUES = values();

	public final int flags;

	ActionKind(int flags) {
		this.flags = flags;
	}

	public Identifier id() {
		return Fray.id("player/" + this.name().toLowerCase(java.util.Locale.ROOT));
	}

	public boolean attack() {
		return this != DODGE && this != BACKSTEP && this != PARRY;
	}

	public boolean art() {
		return this == WHIRLWIND || this == EARTHSHAKER;
	}

	/** Heavies, charged attacks and Arts carry hyper-armour (+20 poise) in windup and active. */
	public boolean hyperArmour() {
		return this == HEAVY || this == CHARGED || this == EXECUTE || this.art();
	}

	/**
	 * Playback speed of the action's clip so the clip's strike lines up with the action's ACTIVE phase (stage 1B plays
	 * the prototype's swing, ACTIVE at its tick 11, for every attack).
	 */
	public static float clipSpeed(ActionKind kind, int windup) {
		if (kind == DODGE || kind == BACKSTEP || kind == PARRY) {
			return 1;
		}
		return Math.max(0.5F, Math.min(4F, 11F / Math.max(1, windup)));
	}

	public static ActionKind byIndex(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : null;
	}
}
