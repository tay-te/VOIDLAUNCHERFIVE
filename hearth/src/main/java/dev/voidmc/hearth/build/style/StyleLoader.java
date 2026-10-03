package dev.voidmc.hearth.build.style;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.Culture;
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
 *   "variants": [                           // optional: fashions chosen per building by seed
 *     {"name": "brick nogging", "w": 1, "roles": {"wall": "minecraft:bricks"}, "habits": {"roof": {"pitch": 1.2}}}
 *   ]
 * }
 * </pre>
 *
 * A role is one material (a string), or a list of entries; an entry is a material string or an object with {@code m}
 * (the material), {@code w} (weight, default 1), the biases {@code low}, {@code high}, {@code north}, {@code corner},
 * and optional per-form overrides ({@code "stairs": "minecraft:stone_brick_stairs"}, keys are {@link Form} names in
 * lower case). Material strings are described in {@link Material}.
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
		Map<Role, Palette> base = json.has("roles") ? roles(json.getAsJsonObject("roles")) : Map.of();
		Map<Integer, Map<Role, Palette>> tiers = new HashMap<>();
		if (json.has("tiers")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("tiers").entrySet()) {
				int t = Integer.parseInt(e.getKey());
				if (t < 0 || t > 4) {
					throw new IllegalArgumentException("tier " + t + " is not 0-4");
				}
				tiers.put(t, roles(e.getValue().getAsJsonObject()));
			}
		}
		JsonObject habitsJson = json.has("habits") ? json.getAsJsonObject("habits") : new JsonObject();
		Habits habits = Habits.parse(habitsJson);
		List<Style.Variant> variants = new ArrayList<>();
		if (json.has("variants")) {
			for (JsonElement e : json.getAsJsonArray("variants")) {
				JsonObject v = e.getAsJsonObject();
				String vname = v.has("name") ? v.get("name").getAsString() : "variant " + variants.size();
				double w = v.has("w") ? v.get("w").getAsDouble() : 1.0;
				Map<Role, Palette> roles = v.has("roles") ? roles(v.getAsJsonObject("roles")) : Map.of();
				Habits vh = v.has("habits") ? Habits.parse(mergeHabits(habitsJson, v.getAsJsonObject("habits"))) : habits;
				variants.add(new Style.Variant(vname, w, roles, vh));
			}
		}
		double baseWeight = json.has("base_weight") ? json.get("base_weight").getAsDouble() : 1.0;
		return new Style(id, culture, name, base, tiers, habits, baseWeight, variants);
	}

	/** A variant's habits over the base's: each section ("walls", "roof", ...) merged key by key, values replaced whole. */
	private static JsonObject mergeHabits(JsonObject base, JsonObject over) {
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
		if (e.isJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			for (JsonElement x : a) {
				entries.add(entry(x));
			}
		} else {
			entries.add(entry(e));
		}
		return new Palette(entries);
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
