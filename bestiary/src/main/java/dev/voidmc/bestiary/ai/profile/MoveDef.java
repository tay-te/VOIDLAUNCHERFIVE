package dev.voidmc.bestiary.ai.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.bestiary.ai.math.Curves;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.Identifier;

/**
 * One move of a creature's moveset (BESTIARY.md §2.3): the clip that carries its timing and tell, the behaviour code
 * that runs on its events, when it may be chosen (range, cooldown, danger, tokens) and how it scores (weight,
 * considerations). The tell's colour and windup are the clip's (the baked timeline), never the move's.
 *
 * <pre>
 * { "id": "claw", "clip": "bestiary:zombie/claw", "behaviour": "melee", "range": [0, 2.6], "cooldown": 24,
 *   "weight": 1.0, "damage": 1.0, "min_danger": 1, "tokens": "melee", "speed": 1.0, "tags": ["guard_break"],
 *   "considerations": [ { "input": "distance", "curve": "band", "a": 0, "b": 2.4 } ], "params": { ... } }
 * </pre>
 */
public record MoveDef(String id, Identifier clip, String behaviour, float minRange, float maxRange, int cooldown, float weight, float damage,
	float minDanger, Tokens tokens, float speed, List<String> tags, List<Consideration> considerations, JsonObject params) {
	public enum Tokens {
		MELEE, RANGED, NONE
	}

	/** One consideration: an input read from the situation, a response curve and a floor (the lowest it may score). */
	public record Consideration(String input, Curves.Curve curve, float a, float b, float floor) {
		public float eval(float x) {
			float v = Curves.eval(this.curve, x, this.a, this.b);
			return this.floor + (1 - this.floor) * v;
		}

		static Consideration parse(JsonObject o) {
			return new Consideration(o.get("input").getAsString(), Curves.Curve.byId(o.has("curve") ? o.get("curve").getAsString() : "linear"),
				o.has("a") ? o.get("a").getAsFloat() : 0, o.has("b") ? o.get("b").getAsFloat() : 1, o.has("floor") ? o.get("floor").getAsFloat() : 0);
		}
	}

	public boolean tagged(String tag) {
		return this.tags.contains(tag);
	}

	public static MoveDef parse(JsonObject o) {
		String id = o.get("id").getAsString();
		float min = 0, max = 3;
		if (o.has("range")) {
			min = o.getAsJsonArray("range").get(0).getAsFloat();
			max = o.getAsJsonArray("range").get(1).getAsFloat();
		}
		List<String> tags = new ArrayList<>();
		if (o.has("tags")) {
			for (JsonElement e : o.getAsJsonArray("tags")) {
				tags.add(e.getAsString());
			}
		}
		List<Consideration> cons = new ArrayList<>();
		if (o.has("considerations")) {
			for (JsonElement e : o.getAsJsonArray("considerations")) {
				cons.add(Consideration.parse(e.getAsJsonObject()));
			}
		}
		Tokens tokens = Tokens.valueOf((o.has("tokens") ? o.get("tokens").getAsString() : "melee").toUpperCase(Locale.ROOT));
		return new MoveDef(id, Identifier.parse(o.get("clip").getAsString()), o.has("behaviour") ? o.get("behaviour").getAsString() : "melee", min, max,
			o.has("cooldown") ? o.get("cooldown").getAsInt() : 20, o.has("weight") ? o.get("weight").getAsFloat() : 1,
			o.has("damage") ? o.get("damage").getAsFloat() : 1, o.has("min_danger") ? o.get("min_danger").getAsFloat() : 1, tokens,
			o.has("speed") ? o.get("speed").getAsFloat() : 1, List.copyOf(tags), List.copyOf(cons),
			o.has("params") ? o.getAsJsonObject("params") : new JsonObject());
	}

	public float param(String key, float fallback) {
		return this.params.has(key) ? this.params.get(key).getAsFloat() : fallback;
	}
}
