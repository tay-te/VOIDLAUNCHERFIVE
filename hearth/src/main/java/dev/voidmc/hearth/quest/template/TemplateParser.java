package dev.voidmc.hearth.quest.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.QuestType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.TurnIn;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Reads a quest template from JSON. Hand-written rather than a codec so a writer gets errors that name the field
 * ({@code objectives[1].count: ...}) and so a template can grow fields without a codec's field limit.
 */
public final class TemplateParser {
	private TemplateParser() {
	}

	/** @throws TemplateException with the path of the bad field */
	public static QuestTemplate parse(Identifier id, JsonObject json) {
		QuestType type = enumValue(json, "type", QuestType.class, null, "type");
		double weight = number(json, "weight", 10.0, "weight");
		if (weight < 0) {
			throw new TemplateException("weight", "must not be negative");
		}
		QuestTemplate.Conditions when = conditions(type, json.has("when") ? object(json.get("when"), "when") : new JsonObject());

		List<JobType> jobs = new ArrayList<>();
		boolean councilOnly = false;
		if (json.has("giver")) {
			JsonObject giver = object(json.get("giver"), "giver");
			for (String s : strings(giver, "jobs", "giver.jobs")) {
				jobs.add(enumByName(JobType.class, s, "giver.jobs"));
			}
			councilOnly = bool(giver, "council_only", false, "giver.council_only");
		}

		if (!json.has("objectives")) {
			throw new TemplateException("objectives", "required");
		}
		JsonArray objArray = array(json.get("objectives"), "objectives");
		List<ObjectiveSpec> objectives = new ArrayList<>();
		for (int i = 0; i < objArray.size(); i++) {
			objectives.add(objective(object(objArray.get(i), "objectives[" + i + "]"), "objectives[" + i + "]"));
		}

		if (!json.has("text")) {
			throw new TemplateException("text", "required");
		}
		JsonObject t = object(json.get("text"), "text");
		QuestTemplate.Text text = new QuestTemplate.Text(variants(t, "title", true), variants(t, "summary", false), variants(t, "offer", false),
			variants(t, "progress", false), variants(t, "complete", false));

		QuestTemplate.RewardSpec reward = reward(json.has("reward") ? object(json.get("reward"), "reward") : new JsonObject());
		int deadline = integer(json, "deadline_days", 0, "deadline_days");
		int cooldown = integer(json, "cooldown_days", 3, "cooldown_days");
		boolean repeatable = bool(json, "repeatable", true, "repeatable");
		TurnIn turnIn = enumValue(json, "turn_in", TurnIn.class, TurnIn.GIVER, "turn_in");
		boolean sequential = bool(json, "sequential", false, "sequential");
		return new QuestTemplate(id, type, weight, when, jobs, councilOnly, objectives, text, reward, Math.max(0, deadline), Math.max(0, cooldown),
			repeatable, turnIn, sequential);
	}

	private static QuestTemplate.Conditions conditions(QuestType type, JsonObject json) {
		QuestType.Trigger trigger = enumValue(json, "trigger", QuestType.Trigger.class, type.defaultTrigger(), "when.trigger");
		Map<Culture, Double> cultures = new EnumMap<>(Culture.class);
		double other = 0.0;
		if (json.has("cultures")) {
			JsonElement c = json.get("cultures");
			if (c.isJsonArray()) {
				for (String s : strings(json, "cultures", "when.cultures")) {
					cultures.put(enumByName(Culture.class, s, "when.cultures"), 1.0);
				}
			} else {
				JsonObject map = object(c, "when.cultures");
				for (Map.Entry<String, JsonElement> e : map.entrySet()) {
					double w = asNumber(e.getValue(), "when.cultures." + e.getKey());
					if (e.getKey().equals("*")) {
						other = w;
					} else {
						cultures.put(enumByName(Culture.class, e.getKey(), "when.cultures"), w);
					}
				}
			}
		}
		SettlementTier minTier = enumValue(json, "min_tier", SettlementTier.class, SettlementTier.CAMP, "when.min_tier");
		SettlementTier maxTier = enumValue(json, "max_tier", SettlementTier.class, SettlementTier.CITY, "when.max_tier");
		if (minTier.ordinal() > maxTier.ordinal()) {
			throw new TemplateException("when.min_tier", "is above max_tier");
		}
		int minRep = integer(json, "min_reputation", -1000, "when.min_reputation");
		List<Identifier> prereqs = new ArrayList<>();
		for (String s : strings(json, "prerequisites", "when.prerequisites")) {
			prereqs.add(identifier(s, "when.prerequisites"));
		}
		List<String> mods = strings(json, "requires_mods", "when.requires_mods");
		Set<Goods> goods = EnumSet.noneOf(Goods.class);
		for (String s : strings(json, "goods", "when.goods")) {
			goods.add(enumByName(Goods.class, s, "when.goods"));
		}
		StandingService.Title minTitle = json.has("min_title") ? enumValue(json, "min_title", StandingService.Title.class, null, "when.min_title") : null;
		QuestTemplate.Wanted wanted = enumValue(json, "wanted", QuestTemplate.Wanted.class, QuestTemplate.Wanted.REFUSE, "when.wanted");
		List<String> requires = strings(json, "requires", "when.requires");
		List<String> events = strings(json, "events", "when.events");
		return new QuestTemplate.Conditions(trigger, cultures, other, minTier, maxTier, minRep, prereqs, mods, goods, minTitle, wanted, requires,
			events);
	}

	private static ObjectiveSpec objective(JsonObject json, String path) {
		ObjectiveKind kind = enumValue(json, "kind", ObjectiveKind.class, null, path + ".kind");
		String label = string(json, "label", "", path + ".label");
		List<String> items = oneOrMany(json, "item", "items", path);
		List<String> entities = oneOrMany(json, "entity", "entities", path);
		Amount count = json.has("count") ? amount(json.get("count"), path + ".count") : Amount.ONE;
		int radius = integer(json, "radius", 0, path + ".radius");
		int minDistance = 0;
		int maxDistance = 0;
		if (json.has("distance")) {
			Amount d = amount(json.get("distance"), path + ".distance");
			if (d.isVariable()) {
				throw new TemplateException(path + ".distance", "must be a number or [min, max]");
			}
			minDistance = d.min();
			maxDistance = d.max();
		}
		List<String> structures = oneOrMany(json, "structure", "structures", path);
		List<String> biomes = oneOrMany(json, "biome", "biomes", path);
		List<String> names = oneOrMany(json, "name", "names", path);
		boolean night = bool(json, "night", false, path + ".night");
		double health = number(json, "health", 1.0, path + ".health");
		ObjectiveSpec.Grant grant = null;
		if (json.has("grant")) {
			JsonObject g = object(json.get("grant"), path + ".grant");
			grant = new ObjectiveSpec.Grant(string(g, "item", "minecraft:paper", path + ".grant.item"), string(g, "name", "", path + ".grant.name"),
				string(g, "lore", "", path + ".grant.lore"), bool(g, "required", false, path + ".grant.required"));
		}
		boolean recipient = bool(json, "recipient", false, path + ".recipient");
		int clearance = integer(json, "clearance", 0, path + ".clearance");
		String targetName = string(json, "target_name", "", path + ".target_name");
		String place = string(json, "place", "", path + ".place");
		double along = number(json, "along", 0.5, path + ".along");
		if (along < 0 || along > 1) {
			throw new TemplateException(path + ".along", "must be between 0 and 1");
		}
		int hold = integer(json, "hold", 0, path + ".hold");
		String resource = string(json, "resource", "", path + ".resource");
		String crime = string(json, "crime", "", path + ".crime");
		int caravan = integer(json, "caravan", 1, path + ".caravan");
		if (caravan < 1 || caravan > 6) {
			throw new TemplateException(path + ".caravan", "must be 1 to 6");
		}
		boolean cargo = bool(json, "cargo", false, path + ".cargo");
		boolean lethal = bool(json, "lethal", false, path + ".lethal");
		return new ObjectiveSpec(kind, label, items, entities, count, Math.max(0, radius), Math.max(0, minDistance), Math.max(0, maxDistance),
			structures, biomes, names, night, health, grant, recipient, Math.max(0, clearance), targetName, place, along, Math.max(0, hold), resource,
			crime, caravan, cargo, lethal);
	}

	private static QuestTemplate.RewardSpec reward(JsonObject json) {
		int min = 0;
		int max = 0;
		if (json.has("coin")) {
			Amount coin = amount(json.get("coin"), "reward.coin");
			if (coin.isVariable()) {
				throw new TemplateException("reward.coin", "must be a number or [min, max]");
			}
			min = coin.min();
			max = coin.max();
		}
		double premium = number(json, "market_premium", 0.0, "reward.market_premium");
		List<QuestTemplate.ItemSpec> items = new ArrayList<>();
		if (json.has("items")) {
			JsonArray arr = array(json.get("items"), "reward.items");
			for (int i = 0; i < arr.size(); i++) {
				String p = "reward.items[" + i + "]";
				JsonObject o = object(arr.get(i), p);
				String item = string(o, "item", null, p + ".item");
				Amount count = o.has("count") ? amount(o.get("count"), p + ".count") : Amount.ONE;
				if (count.isVariable()) {
					throw new TemplateException(p + ".count", "must be a number or [min, max]");
				}
				items.add(new QuestTemplate.ItemSpec(item, count));
			}
		}
		int reputation = integer(json, "reputation", 0, "reward.reputation");
		String deed = string(json, "deed", "", "reward.deed");
		if (!deed.isEmpty() && Identifier.tryParse(deed) == null) {
			throw new TemplateException("reward.deed", "not a valid id: " + deed);
		}
		double aliveBonus = number(json, "alive_bonus", 1.5, "reward.alive_bonus");
		if (aliveBonus < 1) {
			throw new TemplateException("reward.alive_bonus", "must be at least 1");
		}
		return new QuestTemplate.RewardSpec(Math.max(0, min), Math.max(0, max), Math.max(0, premium), items, reputation, deed, aliveBonus);
	}

	// ------------------------------------------------------------------ helpers

	static Amount amount(JsonElement e, String path) {
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			int n = e.getAsInt();
			if (n < 0) {
				throw new TemplateException(path, "must not be negative");
			}
			return Amount.of(n);
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			try {
				return Amount.parseVariable(e.getAsString());
			} catch (IllegalArgumentException ex) {
				throw new TemplateException(path, ex.getMessage());
			}
		}
		if (e.isJsonArray() && e.getAsJsonArray().size() == 2) {
			int a = (int) asNumber(e.getAsJsonArray().get(0), path + "[0]");
			int b = (int) asNumber(e.getAsJsonArray().get(1), path + "[1]");
			if (a < 0 || b < 0) {
				throw new TemplateException(path, "must not be negative");
			}
			return Amount.range(a, b);
		}
		throw new TemplateException(path, "expected a number, [min, max] or \"$variable\"");
	}

	private static List<String> variants(JsonObject json, String key, boolean required) {
		if (!json.has(key)) {
			if (required) {
				throw new TemplateException("text." + key, "required");
			}
			return List.of();
		}
		JsonElement e = json.get(key);
		List<String> out = e.isJsonArray() ? strings(json, key, "text." + key) : List.of(asString(e, "text." + key));
		if (required && out.isEmpty()) {
			throw new TemplateException("text." + key, "needs at least one variant");
		}
		return out;
	}

	private static List<String> oneOrMany(JsonObject json, String one, String many, String path) {
		List<String> out = new ArrayList<>();
		if (json.has(one)) {
			out.add(asString(json.get(one), path + "." + one));
		}
		out.addAll(strings(json, many, path + "." + many));
		return out;
	}

	private static List<String> strings(JsonObject json, String key, String path) {
		if (!json.has(key)) {
			return List.of();
		}
		JsonElement e = json.get(key);
		if (e.isJsonPrimitive()) {
			return List.of(asString(e, path));
		}
		JsonArray arr = array(e, path);
		List<String> out = new ArrayList<>(arr.size());
		for (int i = 0; i < arr.size(); i++) {
			out.add(asString(arr.get(i), path + "[" + i + "]"));
		}
		return out;
	}

	private static String string(JsonObject json, String key, @Nullable String def, String path) {
		if (!json.has(key)) {
			if (def == null) {
				throw new TemplateException(path, "required");
			}
			return def;
		}
		return asString(json.get(key), path);
	}

	private static String asString(JsonElement e, String path) {
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			return e.getAsString();
		}
		throw new TemplateException(path, "expected a string");
	}

	private static int integer(JsonObject json, String key, int def, String path) {
		return json.has(key) ? (int) asNumber(json.get(key), path) : def;
	}

	private static double number(JsonObject json, String key, double def, String path) {
		return json.has(key) ? asNumber(json.get(key), path) : def;
	}

	private static double asNumber(JsonElement e, String path) {
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsDouble();
		}
		throw new TemplateException(path, "expected a number");
	}

	private static boolean bool(JsonObject json, String key, boolean def, String path) {
		if (!json.has(key)) {
			return def;
		}
		JsonElement e = json.get(key);
		if (e instanceof JsonPrimitive p && p.isBoolean()) {
			return p.getAsBoolean();
		}
		throw new TemplateException(path, "expected true or false");
	}

	private static JsonObject object(JsonElement e, String path) {
		if (e.isJsonObject()) {
			return e.getAsJsonObject();
		}
		throw new TemplateException(path, "expected an object");
	}

	private static JsonArray array(JsonElement e, String path) {
		if (e.isJsonArray()) {
			return e.getAsJsonArray();
		}
		throw new TemplateException(path, "expected a list");
	}

	private static Identifier identifier(String s, String path) {
		Identifier id = Identifier.tryParse(s);
		if (id == null) {
			throw new TemplateException(path, "not a valid id: " + s);
		}
		return id;
	}

	private static <E extends Enum<E>> E enumValue(JsonObject json, String key, Class<E> type, @Nullable E def, String path) {
		if (!json.has(key)) {
			if (def == null) {
				throw new TemplateException(path, "required");
			}
			return def;
		}
		return enumByName(type, asString(json.get(key), path), path);
	}

	static <E extends Enum<E>> E enumByName(Class<E> type, String name, String path) {
		try {
			return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			List<String> names = new ArrayList<>();
			for (E v : type.getEnumConstants()) {
				names.add(v.name().toLowerCase(Locale.ROOT));
			}
			throw new TemplateException(path, "unknown value '" + name + "' (one of " + String.join(", ", names) + ")");
		}
	}

	/** A template file that cannot be read, with the path of the field at fault. */
	public static final class TemplateException extends RuntimeException {
		private final String path;

		public TemplateException(String path, String message) {
			super(path + ": " + message);
			this.path = path;
		}

		public String path() {
			return this.path;
		}
	}
}
