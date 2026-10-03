package dev.voidmc.hearth.npc.talk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.person.Trait;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lines folk say, from {@code data/hearth/hearth_dialogue/*.json}: categories of lines, each with optional conditions,
 * merged across files (a data pack adds lines by adding a file, or replaces ours by overriding one).
 *
 * <pre>
 * { "greeting": [
 *     { "text": "Morning, {player}. Ground's wet but the bread's warm.", "time": "morning", "weather": "rain",
 *       "rep": [10, 100], "traits": ["cheerful"], "jobs": ["baker"], "cultures": ["temperate"], "age": "adult",
 *       "mood": [-100, 100], "met": 2, "weight": 2 } ] }
 * </pre>
 *
 * Conditions: {@code time} morning (5–11) / day (11–17) / evening (17–21) / night; {@code weather} clear / rain / storm;
 * {@code rep} the player's reputation range; {@code traits} any of; {@code jobs}, {@code cultures}; {@code age};
 * {@code mood}; {@code met} at least this many earlier conversations. Placeholders in text: {player} {name} {given}
 * {settlement} {job} {building} {goods} {amount} {tier} {other} {direction} {distance} {landmark} {spouse} {culture}.
 */
public final class DialogueTemplates {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	public static final String[] BUILTIN = {"greetings", "news", "rumours", "about", "passing", "farewell"};
	private static volatile Map<String, List<Line>> lines;

	private DialogueTemplates() {
	}

	/** The situation a line is chosen for. */
	public record Context(int minute, String weather, int reputation, Set<Trait> traits, JobType job, Culture culture, AgeStage age, int mood,
		int met) {
	}

	public record Line(String text, @Nullable String time, @Nullable String weather, int repMin, int repMax, Set<Trait> traits, Set<JobType> jobs,
		Set<Culture> cultures, @Nullable AgeStage age, int moodMin, int moodMax, int met, int weight) {
		boolean fits(Context c) {
			if (this.time != null && !this.time.equals(timeOf(c.minute))) {
				return false;
			}
			if (this.weather != null && !this.weather.equals(c.weather)) {
				return false;
			}
			if (c.reputation < this.repMin || c.reputation > this.repMax || c.mood < this.moodMin || c.mood > this.moodMax || c.met < this.met) {
				return false;
			}
			if (!this.traits.isEmpty() && this.traits.stream().noneMatch(c.traits::contains)) {
				return false;
			}
			if (!this.jobs.isEmpty() && !this.jobs.contains(c.job)) {
				return false;
			}
			if (!this.cultures.isEmpty() && !this.cultures.contains(c.culture)) {
				return false;
			}
			return this.age == null || this.age == c.age;
		}

		/** How specific the line is: specific lines are preferred over general ones when both fit. */
		int specificity() {
			int s = 0;
			if (this.time != null) {
				s++;
			}
			if (this.weather != null) {
				s += 2;
			}
			if (!this.traits.isEmpty()) {
				s += 2;
			}
			if (!this.jobs.isEmpty()) {
				s += 2;
			}
			if (!this.cultures.isEmpty()) {
				s += 2;
			}
			if (this.repMin > -100 || this.repMax < 100) {
				s++;
			}
			return s;
		}
	}

	public static String timeOf(int minute) {
		int h = minute / 60;
		if (h >= 5 && h < 11) {
			return "morning";
		}
		if (h >= 11 && h < 17) {
			return "day";
		}
		if (h >= 17 && h < 21) {
			return "evening";
		}
		return "night";
	}

	/**
	 * A line of the category that fits, favouring the most specific (a line for grumpy smiths in the rain beats "Hello"),
	 * with a little chance of a general one for variety. Null if the category has nothing that fits.
	 */
	public static @Nullable String pick(String category, Context context, RandomSource random) {
		List<Line> all = all().getOrDefault(category, List.of());
		List<Line> fit = new ArrayList<>();
		int best = 0;
		for (Line l : all) {
			if (l.fits(context)) {
				fit.add(l);
				best = Math.max(best, l.specificity());
			}
		}
		if (fit.isEmpty()) {
			return null;
		}
		int threshold = random.nextInt(4) == 0 ? 0 : Math.max(0, best - 2);
		int total = 0;
		for (Line l : fit) {
			if (l.specificity() >= threshold) {
				total += l.weight;
			}
		}
		int roll = random.nextInt(Math.max(1, total));
		for (Line l : fit) {
			if (l.specificity() >= threshold) {
				roll -= l.weight;
				if (roll < 0) {
					return l.text;
				}
			}
		}
		return fit.get(0).text;
	}

	public static Map<String, List<Line>> all() {
		Map<String, List<Line>> m = lines;
		if (m == null) {
			m = loadBuiltin();
			lines = m;
		}
		return m;
	}

	/** Installs lines read from data packs. */
	public static void replace(Map<String, List<Line>> loaded) {
		lines = loaded;
	}

	public static Map<String, List<Line>> loadBuiltin() {
		Map<String, List<Line>> out = new HashMap<>();
		for (String name : BUILTIN) {
			String path = "/data/hearth/hearth_dialogue/" + name + ".json";
			try (InputStream in = DialogueTemplates.class.getResourceAsStream(path)) {
				if (in != null) {
					parseInto(new InputStreamReader(in, StandardCharsets.UTF_8), out);
				}
			} catch (IOException | RuntimeException e) {
				LOG.warn("could not read {}", path, e);
			}
		}
		return out;
	}

	public static void parseInto(Reader reader, Map<String, List<Line>> out) {
		JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
		for (Map.Entry<String, JsonElement> cat : json.entrySet()) {
			List<Line> list = out.computeIfAbsent(cat.getKey(), k -> new ArrayList<>());
			for (JsonElement e : cat.getValue().getAsJsonArray()) {
				list.add(e.isJsonPrimitive() ? simple(e.getAsString()) : line(e.getAsJsonObject()));
			}
		}
	}

	private static Line simple(String text) {
		return new Line(text, null, null, -100, 100, Set.of(), Set.of(), Set.of(), null, -100, 100, 0, 1);
	}

	private static Line line(JsonObject o) {
		int[] rep = range(o, "rep");
		int[] mood = range(o, "mood");
		Set<Trait> traits = EnumSet.noneOf(Trait.class);
		for (String s : strings(o, "traits")) {
			traits.add(Trait.valueOf(s.toUpperCase(Locale.ROOT)));
		}
		Set<JobType> jobs = EnumSet.noneOf(JobType.class);
		for (String s : strings(o, "jobs")) {
			jobs.add(JobType.valueOf(s.toUpperCase(Locale.ROOT)));
		}
		Set<Culture> cultures = EnumSet.noneOf(Culture.class);
		for (String s : strings(o, "cultures")) {
			cultures.add(Culture.valueOf(s.toUpperCase(Locale.ROOT)));
		}
		AgeStage age = o.has("age") ? AgeStage.valueOf(o.get("age").getAsString().toUpperCase(Locale.ROOT)) : null;
		return new Line(o.get("text").getAsString(), str(o, "time"), str(o, "weather"), rep[0], rep[1], traits, jobs, cultures, age, mood[0], mood[1],
			o.has("met") ? o.get("met").getAsInt() : 0, o.has("weight") ? o.get("weight").getAsInt() : 1);
	}

	private static @Nullable String str(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsString() : null;
	}

	private static int[] range(JsonObject o, String k) {
		if (!o.has(k)) {
			return new int[]{-100, 100};
		}
		JsonArray a = o.getAsJsonArray(k);
		return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
	}

	private static List<String> strings(JsonObject o, String k) {
		List<String> out = new ArrayList<>();
		if (o.has(k)) {
			for (JsonElement e : o.getAsJsonArray(k)) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	/** Fills {placeholders}; unknown ones are left as they are. */
	public static String fill(String text, Map<String, String> values) {
		StringBuilder sb = new StringBuilder(text.length() + 16);
		int i = 0;
		while (i < text.length()) {
			char ch = text.charAt(i);
			if (ch == '{') {
				int end = text.indexOf('}', i);
				if (end > i) {
					String key = text.substring(i + 1, end);
					String v = values.get(key);
					if (v != null) {
						sb.append(v);
						i = end + 1;
						continue;
					}
				}
			}
			sb.append(ch);
			i++;
		}
		return sb.toString();
	}
}
