package dev.voidmc.fray.api;

import java.util.EnumMap;
import java.util.Map;

/**
 * A hit's base numbers (pipeline stage 1): damage per kind, poise damage and status build-up.
 *
 * @since 0.1.0 (2026-10-21)
 */
public record DamageSpec(Map<DamageKind, Float> amounts, float poise, Map<StatusType, Float> buildUp) {
	public DamageSpec {
		amounts = amounts.isEmpty() ? new EnumMap<>(DamageKind.class) : new EnumMap<>(amounts);
		buildUp = buildUp.isEmpty() ? new EnumMap<>(StatusType.class) : new EnumMap<>(buildUp);
	}

	public float total() {
		float t = 0;
		for (float v : this.amounts.values()) {
			t += v;
		}
		return t;
	}

	public static DamageSpec of(DamageKind kind, float amount, float poise) {
		Map<DamageKind, Float> m = new EnumMap<>(DamageKind.class);
		m.put(kind, amount);
		return new DamageSpec(m, poise, Map.of());
	}

	/** {@code total} split by {@code shares} (kind → share, summing to about 1). */
	public static DamageSpec split(Map<DamageKind, Float> shares, float total, float poise, Map<StatusType, Float> buildUp) {
		Map<DamageKind, Float> m = new EnumMap<>(DamageKind.class);
		float sum = 0;
		for (float v : shares.values()) {
			sum += v;
		}
		for (Map.Entry<DamageKind, Float> e : shares.entrySet()) {
			if (e.getValue() > 0) {
				m.put(e.getKey(), total * e.getValue() / (sum <= 0 ? 1 : sum));
			}
		}
		return new DamageSpec(m, poise, buildUp);
	}
}
