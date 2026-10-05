package dev.voidmc.mettle.api;

/**
 * Where a player's load bands start (as a share of capacity) and how hard they bite (BURDEN.md §3). Perks change these
 * through {@link BandModifier}s: Strong Back moves {@code burdened}, Beast of Burden clears {@code canBePinned}, Ox
 * halves {@code penaltyScale}.
 */
public record BandRules(float burdened, float overburdened, float pinned, float penaltyScale, boolean canBePinned) {
	public static final BandRules DEFAULT = new BandRules(0.75f, 1.0f, 1.25f, 1.0f, true);
}
