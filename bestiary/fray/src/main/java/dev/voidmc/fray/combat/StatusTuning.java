package dev.voidmc.fray.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.math.MeterSet;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jspecify.annotations.Nullable;

/**
 * Status build-up tuning (the user's call, 2026-10-21: Mettle owns the numbers for players, Bestiary sets each
 * creature's resistances), from datapacks: {@code data/<ns>/fray/status_tuning/*.json}. Fray ships
 * {@code fray:defaults} (the numbers below); every other file is read after it, sorted by id, and overrides key by
 * key. A file may hold any of:
 * <pre>
 * { "base_threshold": 100,
 *   "rank_threshold": { "common": 1, "elite": 1, "boss": 4 },
 *   "drain_delay_ticks": 40, "drain_per_second": 0.10,
 *   "build_factor": { "slash": 1.5, "pierce": 0.75, "blunt": 0, "radiant": 0, "*": 3.0 },
 *   "creatures": { "minecraft:blaze": { "burn": 3.0 } } }
 * </pre>
 * A creature's entry adds to its {@code fray:status_resist/<status>} attribute (players never read the creature table:
 * their resistance is the attribute plus Mettle's {@code mettle:status_resist.<status>}, and Mettle's
 * {@code BalanceProvider} has the last word through {@code fray:status_threshold}).
 */
public final class StatusTuning {
	/** One merged set of numbers. Immutable once built. */
	public record Values(float baseThreshold, float[] rankThreshold, int drainDelayTicks, float drainPerSecond, float[] buildFactor, float defaultBuildFactor,
		Map<Identifier, float[]> creatures) {
		public float rank(Rank r) {
			return this.rankThreshold[r.ordinal()];
		}

		public float buildFactor(DamageKind k) {
			float f = this.buildFactor[k.ordinal()];
			return Float.isNaN(f) ? this.defaultBuildFactor : f;
		}

		public float drainPerTick() {
			return this.drainPerSecond / 20F;
		}

		/** The creature table's extra resistance for a status (0 when the type isn't listed). */
		public float creatureResist(@Nullable Identifier type, StatusType s) {
			if (type == null || s.meterIndex() < 0) {
				return 0;
			}
			float[] r = this.creatures.get(type);
			return r == null ? 0 : r[s.meterIndex()];
		}
	}

	/** The numbers Fray ran on before tuning moved to data (and still the fallback without the file). */
	public static final Values CODE_DEFAULTS = defaults();
	private static Values current = CODE_DEFAULTS;

	private StatusTuning() {
	}

	private static Values defaults() {
		float[] rank = new float[Rank.values().length];
		java.util.Arrays.fill(rank, 1);
		rank[Rank.BOSS.ordinal()] = 4;
		float[] build = new float[DamageKind.VALUES.length];
		java.util.Arrays.fill(build, Float.NaN);
		build[DamageKind.SLASH.ordinal()] = 1.5F;
		build[DamageKind.PIERCE.ordinal()] = 0.75F;
		build[DamageKind.BLUNT.ordinal()] = 0;
		build[DamageKind.RADIANT.ordinal()] = 0;
		return new Values(MeterSet.BASE, rank, MeterSet.DRAIN_DELAY, MeterSet.DRAIN_PER_TICK * 20F, build, 3.0F, Map.of());
	}

	public static Values get() {
		return current;
	}

	/** Tests: replace the live values. */
	public static void set(Values v) {
		current = v;
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Fray.id("status_tuning"), (ResourceManagerReloadListener) manager -> {
			Map<Identifier, Resource> files = manager.listResources("fray/status_tuning", id -> id.getPath().endsWith(".json"));
			List<Identifier> order = new ArrayList<>(files.keySet());
			Identifier first = Fray.id("fray/status_tuning/defaults.json");
			order.sort((a, b) -> a.equals(first) ? -1 : b.equals(first) ? 1 : a.compareTo(b));
			List<JsonObject> roots = new ArrayList<>();
			for (Identifier id : order) {
				try (Reader r = files.get(id).openAsReader()) {
					roots.add(JsonParser.parseReader(r).getAsJsonObject());
				} catch (Exception ex) {
					Fray.LOGGER.warn("bad status tuning file {}: {}", id, ex.toString());
				}
			}
			current = merge(CODE_DEFAULTS, roots);
			Fray.LOGGER.info("Fray: status tuning from {} files, {} creatures", roots.size(), current.creatures().size());
		});
	}

	/** Folds the files over {@code base}, later files winning key by key. */
	public static Values merge(Values base, List<JsonObject> roots) {
		float threshold = base.baseThreshold();
		float[] rank = base.rankThreshold().clone();
		int delay = base.drainDelayTicks();
		float drain = base.drainPerSecond();
		float[] build = base.buildFactor().clone();
		float def = base.defaultBuildFactor();
		Map<Identifier, float[]> creatures = new HashMap<>();
		base.creatures().forEach((k, v) -> creatures.put(k, v.clone()));
		for (JsonObject o : roots) {
			if (o.has("base_threshold")) {
				threshold = Math.max(1, o.get("base_threshold").getAsFloat());
			}
			if (o.has("rank_threshold")) {
				for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rank_threshold").entrySet()) {
					rank[Rank.valueOf(e.getKey().toUpperCase(java.util.Locale.ROOT)).ordinal()] = Math.max(0.1F, e.getValue().getAsFloat());
				}
			}
			if (o.has("drain_delay_ticks")) {
				delay = Math.max(0, o.get("drain_delay_ticks").getAsInt());
			}
			if (o.has("drain_per_second")) {
				drain = Math.max(0, o.get("drain_per_second").getAsFloat());
			}
			if (o.has("build_factor")) {
				for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("build_factor").entrySet()) {
					if (e.getKey().equals("*")) {
						def = e.getValue().getAsFloat();
					} else {
						build[DamageKind.byId(e.getKey()).ordinal()] = e.getValue().getAsFloat();
					}
				}
			}
			if (o.has("creatures")) {
				for (Map.Entry<String, JsonElement> c : o.getAsJsonObject("creatures").entrySet()) {
					float[] r = creatures.computeIfAbsent(Identifier.parse(c.getKey()), k -> new float[StatusType.METERED.length]);
					for (Map.Entry<String, JsonElement> e : c.getValue().getAsJsonObject().entrySet()) {
						StatusType s = StatusType.byId(e.getKey());
						if (s == null || s.meterIndex() < 0) {
							throw new IllegalArgumentException("not a build-up status: " + e.getKey());
						}
						r[s.meterIndex()] = e.getValue().getAsFloat();
					}
				}
			}
		}
		return new Values(threshold, rank, delay, drain, build, def, Map.copyOf(creatures));
	}
}
