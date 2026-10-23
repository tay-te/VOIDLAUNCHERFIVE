package dev.voidmc.bestiary.ai.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.fray.api.DamageKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A creature profile ({@code data/<ns>/bestiary/creature/<name>.json}, BESTIARY.md §2.3): which entity type it takes
 * over, its family, poise and resistances, perception numbers, roles, morale, positioning weights, codex name and its
 * moveset. Numbers here are Bestiary's (CONTRACTS.md §2); danger and elites scale them at spawn.
 */
public record CreatureProfile(Identifier id, Identifier entity, String family, String rank, float poise, Map<DamageKind, Float> resist, Senses senses,
	List<String> roles, Morale morale, Map<String, Float> tactics, float ringMin, float ringMax, float keepMin, float keepMax, boolean dayNeutral,
	boolean ignoresTokens, boolean guards, boolean shieldable, String codexName, String codexText, List<MoveDef> moves) {

	/** Perception numbers (BESTIARY.md §3). */
	public record Senses(float sight, float nightSight, float fov, boolean seesInDark, float hearing, int memory, float alert, boolean calls) {
		public static final Senses DEFAULT = new Senses(24, 16, 120, false, 1, 600, 16, true);

		static Senses parse(@Nullable JsonObject o) {
			if (o == null) {
				return DEFAULT;
			}
			Senses d = DEFAULT;
			return new Senses(f(o, "sight", d.sight), f(o, "night_sight", d.nightSight), f(o, "fov", d.fov), b(o, "sees_in_dark", d.seesInDark),
				f(o, "hearing", d.hearing), (int) f(o, "memory", d.memory), f(o, "alert", d.alert), b(o, "calls", d.calls));
		}
	}

	/** Morale (BESTIARY.md §5): whether it ever flees, alone below this health share, in a pack below this share alive. */
	public record Morale(boolean flees, float aloneBelow, float packBelow) {
		public static final Morale NEVER = new Morale(false, 0, 0);

		static Morale parse(@Nullable JsonObject o) {
			if (o == null) {
				return NEVER;
			}
			return new Morale(b(o, "flees", false), f(o, "alone_below", 0.2F), f(o, "pack_below", 0.3F));
		}
	}

	public @Nullable MoveDef move(String id) {
		for (MoveDef m : this.moves) {
			if (m.id().equals(id)) {
				return m;
			}
		}
		return null;
	}

	public @Nullable MoveDef moveForClip(Identifier clip) {
		for (MoveDef m : this.moves) {
			if (m.clip().equals(clip)) {
				return m;
			}
		}
		return null;
	}

	public float tactic(String name) {
		return this.tactics.getOrDefault(name, 0F);
	}

	public static CreatureProfile parse(Identifier id, JsonObject o) {
		Map<DamageKind, Float> resist = new EnumMap<>(DamageKind.class);
		if (o.has("resist")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("resist").entrySet()) {
				resist.put(DamageKind.byId(e.getKey()), e.getValue().getAsFloat());
			}
		}
		List<String> roles = new ArrayList<>();
		if (o.has("roles")) {
			for (JsonElement e : o.getAsJsonArray("roles")) {
				roles.add(e.getAsString());
			}
		}
		Map<String, Float> tactics = new LinkedHashMap<>();
		if (o.has("tactics")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("tactics").entrySet()) {
				tactics.put(e.getKey(), e.getValue().getAsFloat());
			}
		}
		float[] ring = pair(o, "ring", 3.5F, 6F);
		float[] keep = pair(o, "keep", 0F, 2.2F);
		List<MoveDef> moves = new ArrayList<>();
		if (o.has("moves")) {
			for (JsonElement e : o.getAsJsonArray("moves")) {
				moves.add(MoveDef.parse(e.getAsJsonObject()));
			}
		}
		JsonObject codex = o.has("codex") ? o.getAsJsonObject("codex") : new JsonObject();
		return new CreatureProfile(id, Identifier.parse(o.get("entity").getAsString()), s(o, "family", "none"), s(o, "rank", "common"),
			f(o, "poise", Float.NaN), Map.copyOf(resist), Senses.parse(o.has("senses") ? o.getAsJsonObject("senses") : null), List.copyOf(roles),
			Morale.parse(o.has("morale") ? o.getAsJsonObject("morale") : null), Map.copyOf(tactics), ring[0], ring[1], keep[0], keep[1],
			b(o, "day_neutral", false), b(o, "ignores_tokens", false), b(o, "guards", false), b(o, "shieldable", false), s(codex, "name", id.getPath()),
			s(codex, "text", ""),
			List.copyOf(moves));
	}

	private static float[] pair(JsonObject o, String key, float a, float b) {
		if (!o.has(key)) {
			return new float[] {a, b};
		}
		return new float[] {o.getAsJsonArray(key).get(0).getAsFloat(), o.getAsJsonArray(key).get(1).getAsFloat()};
	}

	static float f(JsonObject o, String key, float fallback) {
		return o.has(key) ? o.get(key).getAsFloat() : fallback;
	}

	static boolean b(JsonObject o, String key, boolean fallback) {
		return o.has(key) ? o.get(key).getAsBoolean() : fallback;
	}

	static String s(JsonObject o, String key, String fallback) {
		return o.has(key) ? o.get(key).getAsString() : fallback;
	}
}
