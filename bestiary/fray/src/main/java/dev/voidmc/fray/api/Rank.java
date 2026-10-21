package dev.voidmc.fray.api;

/**
 * A creature's rank (Bestiary sets it; the pipeline, the HUD and parries read it).
 *
 * @since 0.1.0 (2026-10-21)
 */
public enum Rank {
	COMMON(20, 20, 60, 1.0F),
	ELITE(14, 14, 80, 1.5F),
	BOSS(0, 0, 0, 1.0F);

	/** Ticks Staggered when its poise breaks. */
	public final int staggerTicks;
	/** Riposte window granted to whoever parries it. */
	public final int riposteWindow;
	/** Ticks without poise damage before its poise refills. */
	public final int poiseRefill;
	/** Poise multiplier over the profile's base. */
	public final float poiseScale;

	Rank(int staggerTicks, int riposteWindow, int poiseRefill, float poiseScale) {
		this.staggerTicks = staggerTicks;
		this.riposteWindow = riposteWindow;
		this.poiseRefill = poiseRefill;
		this.poiseScale = poiseScale;
	}
}
