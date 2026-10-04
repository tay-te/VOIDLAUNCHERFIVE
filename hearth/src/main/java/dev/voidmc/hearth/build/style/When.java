package dev.voidmc.hearth.build.style;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Where a dressing belongs: the condition on a style variant, a sub-style or a regional style, tested against the plot's
 * {@link StyleContext}. In JSON a {@code when} is an object whose keys must all hold (each key's list is a choice), or an
 * array of such objects of which one must hold:
 *
 * <pre>
 *   "when": {"stations": ["merchant", "gentry"], "tiers": {"min": 2}}
 *   "when": [{"stations": ["lord"]}, {"categories": ["noble", "fort"]}]
 *   "when": {"categories": ["home"], "not": {"stations": ["poor"]}}
 * </pre>
 *
 * Keys: {@code stations} (a {@link dev.voidmc.hearth.api.Station} name: the plot's {@code station} param, or the station
 * its archetype and district imply), {@code categories} (an {@link dev.voidmc.hearth.api.ArchetypeCategory} name or a
 * purpose: {@code home, workshop, religious, noble, fort, public, trade, farmstead, grand, underground}),
 * {@code archetypes} (ids or paths with {@code *} wildcards: {@code "civic/chapel*"}), {@code tiers} (a list, a number,
 * or {@code {"min": 2, "max": 4}}), {@code biomes} (ids with wildcards: {@code "expanse:ancient_oakwood"},
 * {@code "minecraft:*taiga*"}; Expanse's renamed biomes match by their old or new id, see {@link #RENAMED_BIOMES}), {@code landforms} (see {@link StyleContext}), {@code districts} (the planner's district
 * kinds), {@code regions} (the {@code region} param), {@code cultures}, and {@code not} (a nested condition that must
 * fail). A missing {@code when} always holds.
 */
public final class When {
	private final List<Clause> any;
	private final List<When> all;

	private When(List<Clause> any, List<When> all) {
		this.any = List.copyOf(any);
		this.all = List.copyOf(all);
	}

	/** Both conditions (either may be null, meaning "always"); null when both are. */
	public static @Nullable When and(@Nullable When a, @Nullable When b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return new When(List.of(), List.of(a, b));
	}

	/** Whether a (possibly absent) condition holds. */
	public static boolean holds(@Nullable When w, StyleContext c) {
		return w == null || w.test(c);
	}

	public boolean test(StyleContext c) {
		for (When w : this.all) {
			if (!w.test(c)) {
				return false;
			}
		}
		if (this.any.isEmpty()) {
			return true;
		}
		for (Clause k : this.any) {
			if (k.test(c)) {
				return true;
			}
		}
		return false;
	}

	/** How many kinds of thing the condition names (a station and a tier: 2), for reports. */
	public int specificity() {
		int n = 0;
		for (When w : this.all) {
			n += w.specificity();
		}
		int best = 0;
		for (Clause k : this.any) {
			best = Math.max(best, k.specificity());
		}
		return n + best;
	}

	public static @Nullable When parse(@Nullable JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return null;
		}
		List<Clause> clauses = new ArrayList<>();
		if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				clauses.add(Clause.parse(x.getAsJsonObject()));
			}
		} else {
			clauses.add(Clause.parse(e.getAsJsonObject()));
		}
		return new When(clauses, List.of());
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (When w : this.all) {
			b.append(b.isEmpty() ? "" : " and ").append('(').append(w).append(')');
		}
		if (!this.any.isEmpty()) {
			List<String> parts = new ArrayList<>();
			for (Clause k : this.any) {
				parts.add(k.toString());
			}
			b.append(b.isEmpty() ? "" : " and ").append(String.join(" or ", parts));
		}
		return b.toString();
	}

	/**
	 * Expanse's surface biomes renamed when they became real-world landscapes (2026-10): old id to new. A {@code biomes}
	 * condition naming either id matches a plot in either, so styles and worlds of both generations agree.
	 */
	public static final java.util.Map<String, String> RENAMED_BIOMES = java.util.Map.of(
		"expanse:wisteria_vale", "expanse:woodland_glades",
		"expanse:lumen_grove", "expanse:ancient_oakwood",
		"expanse:opal_dunes", "expanse:red_dunes",
		"expanse:prismatic_peaks", "expanse:dolomite_peaks",
		"expanse:frostbloom_tundra", "expanse:lichen_tundra");

	/** A biome id (or pattern) under its current name: Expanse's old ids become their new ones, anything else is unchanged. */
	public static String canonicalBiome(String id) {
		String l = id.toLowerCase(Locale.ROOT);
		return RENAMED_BIOMES.getOrDefault(l, l);
	}

	/** Glob match with {@code *} wildcards, case-insensitive. */
	static boolean glob(String pattern, String text) {
		String p = pattern.toLowerCase(Locale.ROOT);
		String t = text.toLowerCase(Locale.ROOT);
		if (!p.contains("*")) {
			return p.equals(t);
		}
		String[] parts = p.split("\\*", -1);
		int at = 0;
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (part.isEmpty()) {
				continue;
			}
			int found = t.indexOf(part, at);
			if (found < 0 || i == 0 && found != 0) {
				return false;
			}
			at = found + part.length();
		}
		return parts[parts.length - 1].isEmpty() || t.endsWith(parts[parts.length - 1]);
	}

	/** One set of requirements that must all hold. Empty sets are not checked. */
	record Clause(Set<String> stations, Set<String> categories, List<String> archetypes, int minTier, int maxTier, List<String> biomes,
		Set<String> landforms, Set<String> districts, Set<String> regions, Set<String> cultures, @Nullable When not) {

		boolean test(StyleContext c) {
			if (!this.stations.isEmpty() && (c.station() == null || !this.stations.contains(c.station().getSerializedName()))) {
				return false;
			}
			if (!this.categories.isEmpty()) {
				boolean any = false;
				for (String k : this.categories) {
					if (c.isA(k)) {
						any = true;
						break;
					}
				}
				if (!any) {
					return false;
				}
			}
			if (!this.archetypes.isEmpty()) {
				boolean any = false;
				for (String a : this.archetypes) {
					if (glob(a, c.archetype()) || glob(a, "hearth:" + c.archetype())) {
						any = true;
						break;
					}
				}
				if (!any) {
					return false;
				}
			}
			if (c.tier() < this.minTier || c.tier() > this.maxTier) {
				return false;
			}
			if (!this.biomes.isEmpty()) {
				if (c.biome() == null) {
					return false;
				}
				// Expanse's renamed biomes answer to their old ids and their new ones alike
				String raw = c.biome().toString();
				String canon = canonicalBiome(raw);
				boolean any = false;
				for (String b : this.biomes) {
					if (glob(b, raw) || glob(canonicalBiome(b), canon)) {
						any = true;
						break;
					}
				}
				if (!any) {
					return false;
				}
			}
			if (!this.landforms.isEmpty()) {
				boolean any = false;
				for (String l : this.landforms) {
					if (c.landforms().contains(l)) {
						any = true;
						break;
					}
				}
				if (!any) {
					return false;
				}
			}
			if (!this.districts.isEmpty() && (c.district() == null || !this.districts.contains(c.district()))) {
				return false;
			}
			if (!this.regions.isEmpty() && (c.region() == null || !this.regions.contains(c.region()))) {
				return false;
			}
			if (!this.cultures.isEmpty() && (c.culture() == null || !this.cultures.contains(c.culture().getSerializedName()))) {
				return false;
			}
			return this.not == null || !this.not.test(c);
		}

		int specificity() {
			int n = 0;
			n += this.stations.isEmpty() ? 0 : 1;
			n += this.categories.isEmpty() ? 0 : 1;
			n += this.archetypes.isEmpty() ? 0 : 1;
			n += this.minTier > 0 || this.maxTier < 4 ? 1 : 0;
			n += this.biomes.isEmpty() ? 0 : 1;
			n += this.landforms.isEmpty() ? 0 : 1;
			n += this.districts.isEmpty() ? 0 : 1;
			n += this.regions.isEmpty() ? 0 : 1;
			n += this.cultures.isEmpty() ? 0 : 1;
			return n;
		}

		static Clause parse(JsonObject o) {
			for (String k : o.keySet()) {
				if (!KEYS.contains(k)) {
					throw new IllegalArgumentException("unknown condition '" + k + "' (expected one of " + KEYS + ")");
				}
			}
			int min = 0;
			int max = 4;
			if (o.has("tiers")) {
				JsonElement t = o.get("tiers");
				if (t.isJsonPrimitive()) {
					min = max = t.getAsInt();
				} else if (t.isJsonArray()) {
					min = 4;
					max = 0;
					for (JsonElement x : t.getAsJsonArray()) {
						min = Math.min(min, x.getAsInt());
						max = Math.max(max, x.getAsInt());
					}
				} else {
					JsonObject r = t.getAsJsonObject();
					min = r.has("min") ? r.get("min").getAsInt() : 0;
					max = r.has("max") ? r.get("max").getAsInt() : 4;
				}
			}
			return new Clause(set(o, "stations"), set(o, "categories"), list(o, "archetypes"), min, max, list(o, "biomes"), set(o, "landforms"),
				set(o, "districts"), set(o, "regions"), set(o, "cultures"), o.has("not") ? When.parse(o.get("not")) : null);
		}

		private static final Set<String> KEYS = Set.of("stations", "categories", "archetypes", "tiers", "biomes", "landforms", "districts", "regions",
			"cultures", "not");

		private static List<String> list(JsonObject o, String k) {
			List<String> out = new ArrayList<>();
			if (!o.has(k)) {
				return out;
			}
			JsonElement e = o.get(k);
			if (e.isJsonArray()) {
				JsonArray a = e.getAsJsonArray();
				for (JsonElement x : a) {
					out.add(x.getAsString().toLowerCase(Locale.ROOT));
				}
			} else {
				out.add(e.getAsString().toLowerCase(Locale.ROOT));
			}
			return out;
		}

		private static Set<String> set(JsonObject o, String k) {
			return Set.copyOf(new HashSet<>(list(o, k)));
		}

		@Override
		public String toString() {
			List<String> p = new ArrayList<>();
			if (!this.stations.isEmpty()) {
				p.add("stations " + this.stations);
			}
			if (!this.categories.isEmpty()) {
				p.add("categories " + this.categories);
			}
			if (!this.archetypes.isEmpty()) {
				p.add("archetypes " + this.archetypes);
			}
			if (this.minTier > 0 || this.maxTier < 4) {
				p.add("tiers " + this.minTier + "-" + this.maxTier);
			}
			if (!this.biomes.isEmpty()) {
				p.add("biomes " + this.biomes);
			}
			if (!this.landforms.isEmpty()) {
				p.add("landforms " + this.landforms);
			}
			if (!this.districts.isEmpty()) {
				p.add("districts " + this.districts);
			}
			if (!this.regions.isEmpty()) {
				p.add("regions " + this.regions);
			}
			if (!this.cultures.isEmpty()) {
				p.add("cultures " + this.cultures);
			}
			if (this.not != null) {
				p.add("not (" + this.not + ")");
			}
			return String.join(", ", p);
		}
	}
}
