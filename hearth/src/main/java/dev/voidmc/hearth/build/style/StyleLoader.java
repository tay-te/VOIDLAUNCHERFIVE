package dev.voidmc.hearth.build.style;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Station;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Reads styles from JSON. The format ({@code data/hearth/hearth_style/<name>.json}):
 *
 * <pre>
 * {
 *   "culture": "temperate",                 // optional: this style is the culture's default
 *   "name": "Temperate timber and thatch",
 *   "base_name": "cottage",                 // optional: the common manner's name (labels, the style bible)
 *   "roles": {                              // tier 2 materials, per role (keys: Role.key())
 *     "wall":  ["minecraft:white_terracotta", {"m": "minecraft:smooth_quartz", "w": 0.3}],
 *     "foundation": [{"m": "minecraft:cobblestone", "w": 3},
 *                    {"m": "minecraft:mossy_cobblestone", "w": 1, "low": 2, "north": 1.5}],
 *     "frame": "wood:minecraft:dark_oak",
 *     "roof":  [{"m": "wood:expanse:wisteria|wood:minecraft:cherry"}]
 *   },
 *   "tiers": { "0": { "roof": "minecraft:hay_block" }, "3": { "wall": "minecraft:smooth_quartz" } },
 *   "habits": { ... see Habits ... },
 *   "base_weight": 2,                       // optional: the base dressing's weight among the variants (default 1)
 *   "base_when": {"not": {"stations": ["lord"]}},   // optional: where the common manner (and its fashions) may be chosen
 *   "variants": [                           // optional: fashions of the common manner, chosen per building by seed
 *     {"name": "brick nogging", "w": 1, "roles": {"wall": "minecraft:bricks"}, "habits": {"roof": {"pitch": 1.2}},
 *      "when": {"districts": ["crafts"]},   // optional condition (see When)
 *      "tiers": {"3": {"wall": "..."}}}     // optional: its own tier overrides (win over the culture's tiers)
 *   ],
 *   "substyles": [                          // optional: the culture's other manners
 *     {"name": "burgher", "w": 6, "when": {"stations": ["merchant"]},
 *      "roles": {...}, "tiers": {...}, "habits": {...},       // over the culture's base
 *      "base_weight": 2, "variants": [{"name": "limewashed", "w": 1, "roles": {...}}]}
 *   ],
 *   "overrides": [                          // optional: roles dressed one way for a purpose or place, in every dressing
 *     {"when": {"categories": ["fort"]}, "roles": {"tree_log": "minecraft:stripped_acacia_log"}}
 *   ],
 *   "stations": {                           // optional: how stations dress here, over StationFinish's defaults
 *     "lord": {"finish": 2, "weathering": 0.3, "patched_roof": false, "local_share": 0.3, "roles": {"accent": "..."}}
 *   },
 *   "region": {"when": {"biomes": ["expanse:ancient_oakwood"]}, "priority": 10, "aliases": ["lumen"]}   // only in a regional overlay
 * }
 * </pre>
 *
 * A role is one material (a string), or a list of entries; an entry is a material string or an object with {@code m}
 * (the material), {@code w} (weight, default 1), the biases {@code low}, {@code high}, {@code north}, {@code corner},
 * and optional per-form overrides ({@code "stairs": "minecraft:stone_brick_stairs"}, keys are {@link Form} names in
 * lower case). An entry with {@code "patch": 0.25} is not mixed but laid over the mix in clusters on about that share of
 * positions, whichever material a building chose (moss on shingles, a mended roof). Material strings are described in
 * {@link Material}.
 *
 * <p>Sub-styles are flattened into {@link Style#variants()} after the fashions, a sub-style's base first and then its
 * fashions (named "sub-style · fashion"), so variant indices of existing JSON never move when sub-styles are appended.
 */
public final class StyleLoader {
	private StyleLoader() {
	}

	public static Style parse(Identifier id, JsonObject json) {
		Culture culture = null;
		if (json.has("culture")) {
			String c = json.get("culture").getAsString();
			culture = Culture.CODEC.byName(c);
			if (culture == null) {
				throw new IllegalArgumentException("unknown culture '" + c + "'");
			}
		}
		String name = json.has("name") ? json.get("name").getAsString() : id.toString();
		String baseName = json.has("base_name") ? json.get("base_name").getAsString() : "base";
		Map<Role, Palette> base = json.has("roles") ? roles(json.getAsJsonObject("roles")) : Map.of();
		Map<Integer, Map<Role, Palette>> tiers = json.has("tiers") ? tiers(json.getAsJsonObject("tiers")) : Map.of();
		JsonObject habitsJson = json.has("habits") ? json.getAsJsonObject("habits").deepCopy() : new JsonObject();
		Habits habits = Habits.parse(habitsJson);
		When baseWhen = When.parse(json.get("base_when"));
		List<Style.Variant> variants = new ArrayList<>();
		if (json.has("variants")) {
			for (JsonElement e : json.getAsJsonArray("variants")) {
				JsonObject v = e.getAsJsonObject();
				String vname = v.has("name") ? v.get("name").getAsString() : "variant " + variants.size();
				double w = v.has("w") ? v.get("w").getAsDouble() : 1.0;
				Map<Role, Palette> roles = v.has("roles") ? roles(v.getAsJsonObject("roles")) : Map.of();
				JsonObject vj = v.has("habits") ? mergeHabits(habitsJson, v.getAsJsonObject("habits")) : habitsJson;
				Habits vh = v.has("habits") ? Habits.parse(vj) : habits;
				// a fashion of the common manner goes where the common manner goes
				When when = When.and(baseWhen, When.parse(v.get("when")));
				Map<Integer, Map<Role, Palette>> vt = v.has("tiers") ? tiers(v.getAsJsonObject("tiers")) : Map.of();
				variants.add(new Style.Variant(vname, w, roles, vh, when, null, vt, vj));
			}
		}
		if (json.has("substyles")) {
			for (JsonElement e : json.getAsJsonArray("substyles")) {
				substyle(e.getAsJsonObject(), base, habitsJson, variants);
			}
		}
		double baseWeight = json.has("base_weight") ? json.get("base_weight").getAsDouble() : 1.0;
		Map<Station, StationFinish> stations = new EnumMap<>(Station.class);
		if (json.has("stations")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("stations").entrySet()) {
				Station st = Station.CODEC.byName(e.getKey());
				if (st == null) {
					throw new IllegalArgumentException("unknown station '" + e.getKey() + "'");
				}
				JsonObject o = e.getValue().getAsJsonObject();
				stations.put(st, StationFinish.parse(o, StationFinish.defaults(st), o.has("roles") ? roles(o.getAsJsonObject("roles")) : Map.of()));
			}
		}
		Style.Region region = null;
		if (json.has("region")) {
			JsonObject r = json.getAsJsonObject("region");
			String path = id.getPath();
			String rname = r.has("name") ? r.get("name").getAsString() : path.substring(path.lastIndexOf('/') + 1);
			List<String> aliases = new ArrayList<>();
			if (r.has("aliases")) {
				r.getAsJsonArray("aliases").forEach(a -> aliases.add(a.getAsString().toLowerCase(Locale.ROOT)));
			}
			region = new Style.Region(rname, When.parse(r.get("when")), r.has("priority") ? r.get("priority").getAsInt() : 0, aliases);
		}
		List<Style.RoleOverride> overrides = new ArrayList<>();
		if (json.has("overrides")) {
			for (JsonElement e : json.getAsJsonArray("overrides")) {
				JsonObject o = e.getAsJsonObject();
				When w = When.parse(o.get("when"));
				if (w == null) {
					throw new IllegalArgumentException("an override needs a 'when'");
				}
				overrides.add(new Style.RoleOverride(w, o.has("roles") ? roles(o.getAsJsonObject("roles")) : Map.of(),
					o.has("tiers") ? tiers(o.getAsJsonObject("tiers")) : Map.of()));
			}
		}
		return new Style(id, culture, name, baseName, base, tiers, habits, habitsJson, baseWeight, baseWhen, variants, stations, region, overrides);
	}

	/**
	 * A sub-style: a whole manner of the culture (its own roles over the culture's, its own tiers and habits, its own
	 * fashions), flattened into dressings: the sub-style's base and each of its fashions, sharing its weight.
	 */
	private static void substyle(JsonObject s, Map<Role, Palette> cultureRoles, JsonObject cultureHabits, List<Style.Variant> out) {
		String name = s.has("name") ? s.get("name").getAsString() : "sub-style " + out.size();
		double w = s.has("w") ? s.get("w").getAsDouble() : 1.0;
		When when = When.parse(s.get("when"));
		Map<Role, Palette> roles = s.has("roles") ? roles(s.getAsJsonObject("roles")) : Map.of();
		Map<Integer, Map<Role, Palette>> tiers = s.has("tiers") ? tiers(s.getAsJsonObject("tiers")) : Map.of();
		JsonObject hj = s.has("habits") ? mergeHabits(cultureHabits, s.getAsJsonObject("habits")) : cultureHabits;
		Habits habits = Habits.parse(hj);
		double bw = s.has("base_weight") ? s.get("base_weight").getAsDouble() : 1.0;
		List<JsonObject> fashions = new ArrayList<>();
		double total = bw;
		if (s.has("variants")) {
			for (JsonElement e : s.getAsJsonArray("variants")) {
				JsonObject f = e.getAsJsonObject();
				fashions.add(f);
				total += f.has("w") ? f.get("w").getAsDouble() : 1.0;
			}
		}
		out.add(new Style.Variant(name, w * bw / total, roles, habits, when, name, tiers, hj));
		for (JsonObject f : fashions) {
			String fname = name + " · " + (f.has("name") ? f.get("name").getAsString() : "fashion");
			double fw = f.has("w") ? f.get("w").getAsDouble() : 1.0;
			EnumMap<Role, Palette> fr = new EnumMap<>(Role.class);
			fr.putAll(roles);
			if (f.has("roles")) {
				fr.putAll(roles(f.getAsJsonObject("roles")));
			}
			JsonObject fj = f.has("habits") ? mergeHabits(hj, f.getAsJsonObject("habits")) : hj;
			Habits fh = f.has("habits") ? Habits.parse(fj) : habits;
			out.add(new Style.Variant(fname, w * fw / total, fr, fh, When.and(when, When.parse(f.get("when"))), name, tiers, fj));
		}
	}

	private static Map<Integer, Map<Role, Palette>> tiers(JsonObject o) {
		Map<Integer, Map<Role, Palette>> tiers = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			int t = Integer.parseInt(e.getKey());
			if (t < 0 || t > 4) {
				throw new IllegalArgumentException("tier " + t + " is not 0-4");
			}
			tiers.put(t, roles(e.getValue().getAsJsonObject()));
		}
		return tiers;
	}

	/** A variant's habits over the base's: each section ("walls", "roof", ...) merged key by key, values replaced whole. */
	static JsonObject mergeHabits(JsonObject base, JsonObject over) {
		JsonObject out = base.deepCopy();
		for (Map.Entry<String, JsonElement> e : over.entrySet()) {
			if (e.getValue().isJsonObject() && out.has(e.getKey()) && out.get(e.getKey()).isJsonObject()) {
				JsonObject section = out.getAsJsonObject(e.getKey());
				for (Map.Entry<String, JsonElement> k : e.getValue().getAsJsonObject().entrySet()) {
					section.add(k.getKey(), k.getValue().deepCopy());
				}
			} else {
				out.add(e.getKey(), e.getValue().deepCopy());
			}
		}
		return out;
	}

	public static Style parse(Identifier id, Reader reader) {
		return parse(id, JsonParser.parseReader(reader).getAsJsonObject());
	}

	/** A style shipped in this mod's jar (the same file the data pack provides), or null if there is none. */
	public static @Nullable Style fromClasspath(Identifier id) {
		String path = "/data/" + id.getNamespace() + "/hearth_style/" + id.getPath() + ".json";
		try (InputStream in = StyleLoader.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			return parse(id, new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("could not read " + path, e);
		}
	}

	private static Map<Role, Palette> roles(JsonObject o) {
		EnumMap<Role, Palette> m = new EnumMap<>(Role.class);
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			Role role = Role.byKey(e.getKey());
			if (role == null) {
				throw new IllegalArgumentException("unknown role '" + e.getKey() + "'");
			}
			m.put(role, palette(e.getValue()));
		}
		return m;
	}

	private static Palette palette(JsonElement e) {
		List<Palette.Entry> entries = new ArrayList<>();
		List<Palette.Entry> patches = new ArrayList<>();
		double share = 0;
		List<JsonElement> items = new ArrayList<>();
		if (e.isJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			a.forEach(items::add);
		} else {
			items.add(e);
		}
		for (JsonElement x : items) {
			// an entry with "patch" is laid over the mix in clusters (moss on a roof), whatever material the building chose
			if (x.isJsonObject() && x.getAsJsonObject().has("patch")) {
				patches.add(entry(x));
				share = Math.max(share, x.getAsJsonObject().get("patch").getAsDouble());
			} else {
				entries.add(entry(x));
			}
		}
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("a role needs at least one material that is not a patch");
		}
		return new Palette(entries, patches, share);
	}

	private static Palette.Entry entry(JsonElement e) {
		if (e.isJsonPrimitive()) {
			return new Palette.Entry(Material.of(e.getAsString()), 1, 0, 0, 0, 0);
		}
		JsonObject o = e.getAsJsonObject();
		Map<Form, String> overrides = new EnumMap<>(Form.class);
		for (Form f : Form.values()) {
			String k = f.name().toLowerCase(Locale.ROOT);
			if (o.has(k)) {
				overrides.put(f, o.get(k).getAsString());
			}
		}
		return new Palette.Entry(Material.of(o.get("m").getAsString(), overrides),
			Habits.Json.doubleOr(o, "w", 1),
			Habits.Json.doubleOr(o, "low", 0),
			Habits.Json.doubleOr(o, "high", 0),
			Habits.Json.doubleOr(o, "north", 0),
			Habits.Json.doubleOr(o, "corner", 0));
	}
}
