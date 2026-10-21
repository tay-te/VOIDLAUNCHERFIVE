package dev.voidmc.fray.api;

import java.util.Locale;

/**
 * Fray's shared statuses (CONTRACTS.md §6.2). Build-up statuses have a meter on the target that fills from hits and
 * procs the status when full; the rest are applied directly (by poise, reactions, spells).
 *
 * @since 0.1.0 (2026-10-21)
 */
public enum StatusType {
	// Build-up statuses, in meter order (the network sends meters in this order).
	BLEED(true, true, 0, 0xFFE5484D),
	BURN(true, true, 80, 0xFFFF8A3D),
	CHILL(true, true, 120, 0xFF8EC5FF),
	SHOCK(true, true, 12, 0xFFF5D76E),
	POISON(true, true, 120, 0xFF8BD45E),
	HEX(true, true, 160, 0xFFB07CFF),
	BLIGHT(true, true, 120, 0xFF9A8F6A),
	// Applied statuses.
	FROZEN(false, true, 30, 0xFFBFE3FF),
	STAGGER(false, true, 20, 0xFFEDE6CF),
	DAZE(false, true, 10, 0xFFE8C46A),
	EXPOSED(false, true, 120, 0xFFFF9E5A),
	ROOTED(false, true, 40, 0xFF8B6B4A),
	SILENCED(false, true, 60, 0xFFB07CFF),
	WET(false, false, 100, 0xFF5FA8E8),
	OILED(false, false, 200, 0xFFC9A24B),
	MARKED(false, true, 120, 0xFFFF6B6B);

	public static final StatusType[] VALUES = values();
	/** The build-up statuses, in meter order. */
	public static final StatusType[] METERED = {BLEED, BURN, CHILL, SHOCK, POISON, HEX, BLIGHT};

	/** True when hits build a meter that procs this status. */
	public final boolean metered;
	/** Harmful (sorted first on the status row). */
	public final boolean harmful;
	/** Default duration in ticks when it procs or is applied (0: instant). */
	public final int duration;
	/** The status colour (HUD outline, meter fill). */
	public final int colour;

	StatusType(boolean metered, boolean harmful, int duration, int colour) {
		this.metered = metered;
		this.harmful = harmful;
		this.duration = duration;
		this.colour = colour;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** Index into the meter array, or -1 for statuses without a meter. */
	public int meterIndex() {
		return this.metered ? this.ordinal() : -1;
	}

	public static StatusType byId(String id) {
		for (StatusType s : VALUES) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		throw new IllegalArgumentException("unknown status " + id);
	}
}
