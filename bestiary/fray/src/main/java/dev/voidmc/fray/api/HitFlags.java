package dev.voidmc.fray.api;

/**
 * Bit flags carried by a {@link HitContext} (CONTRACTS.md §4.2). Additive: new flags take new bits.
 *
 * @since 0.1.0 (2026-10-21)
 */
public final class HitFlags {
	public static final int LIGHT = 1;
	public static final int HEAVY = 1 << 1;
	public static final int CHARGED = 1 << 2;
	/** A critical (rolled from crit chance, or a riposte, backstab or execute: those count as critical for perks). */
	public static final int CRIT = 1 << 3;
	public static final int RIPOSTE = 1 << 4;
	public static final int BACKSTAB = 1 << 5;
	public static final int PLUNGE = 1 << 6;
	public static final int WEAK_POINT = 1 << 7;
	public static final int PROJECTILE = 1 << 8;
	public static final int SPELL = 1 << 9;
	public static final int AOE = 1 << 10;
	public static final int RUNNING = 1 << 11;
	public static final int GUARD_COUNTER = 1 << 12;
	public static final int EXECUTE = 1 << 13;
	public static final int ROLL = 1 << 14;
	public static final int ART = 1 << 15;
	/** Red tell: can't be blocked or parried, only dodged. */
	public static final int UNBLOCKABLE = 1 << 16;
	/** Orange tell (a heavy): block costs ×1.5 stamina; parry only with a shield. */
	public static final int HEAVY_TELL = 1 << 17;
	/** Purple tell: a grab or a spell; dodge or interrupt it (not blockable). */
	public static final int GRAB = 1 << 18;
	/** The action may hit the same target more than once (flurries, channels). */
	public static final int MULTI_HIT = 1 << 19;
	/** Set by the pipeline when attacker and target are both players. */
	public static final int PVP = 1 << 20;
	/** A status proc (Bleed burst, Shock jump): never builds statuses again and is never guarded. */
	public static final int STATUS = 1 << 21;
	/** A melee hit by a creature (vanilla or Bestiary) on a player: guardable by the defender. */
	public static final int MELEE = 1 << 22;
	/**
	 * A guard crusher (a boss's kick): blocking it costs twice the stamina. Creature clips set it with a {@code guard_crush}
	 * marker.
	 *
	 * @since 0.3.0 (2026-10-22)
	 */
	public static final int GUARD_CRUSH = 1 << 23;

	private HitFlags() {
	}

	public static boolean has(int flags, int flag) {
		return (flags & flag) != 0;
	}
}
