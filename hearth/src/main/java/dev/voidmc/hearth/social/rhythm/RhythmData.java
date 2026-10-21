package dev.voidmc.hearth.social.rhythm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

/**
 * How a culture keeps its day and week (docs/LIVING_WORLD_2.md §2.1), from {@code data/<ns>/hearth_social/rhythm/<culture>.json}
 * with {@code default.json} for cultures without a file. Every field is optional; a culture's file overrides the default
 * field by field.
 *
 * <pre>
 * { "rest_weekday": 3,
 *   "service": { "name": "mass", "start": 540, "minutes": 90, "peal": 20, "share": 0.6, "chant": [12, 14, 16, -1] },
 *   "wash": { "places": ["river", "lake", "well", "fountain"], "start": 480, "end": 720 },
 *   "curfew": 1260,
 *   "tavern": { "open": 1110, "close": 1410, "instrument": "" },
 *   "watch": { "from": 1260, "to": 360 },
 *   "meals": [360, 720, 1110],
 *   "rest_day_work": ["innkeeper", "guard", "jailer", "priest", "monk", "entertainer"],
 *   "first_snow": { "day": -1, "winter_days": 10 } }
 * </pre>
 *
 * Minutes are of the day (0 = midnight). The service's {@code name} picks its words ({@code hearth.rhythm.service.<name>}).
 */
public record RhythmData(int restWeekday, Service service, Wash wash, int curfew, Tavern tavern, Watch watch, List<Integer> meals,
	Set<JobType> restDayWork, Snow snow) {

	public record Service(String name, int start, int minutes, int peal, double share, List<Integer> chant) {
		public int end() {
			return this.start + this.minutes;
		}
	}

	public record Wash(List<String> places, int start, int end) {
	}

	public record Tavern(int open, int close, String instrument) {
	}

	public record Watch(int from, int to) {
		/** Whether a minute of the day is in the watch's hours (which run past midnight). */
		public boolean covers(int minute) {
			return this.from <= this.to ? minute >= this.from && minute < this.to : minute >= this.from || minute < this.to;
		}
	}

	/** {@code day}: the year day of the first snow, -1 where it never snows; winter lasts {@code winterDays} from it. */
	public record Snow(int day, int winterDays) {
		public boolean cold() {
			return this.day >= 0;
		}
	}

	public static final RhythmData DEFAULT = new RhythmData(3, new Service("mass", 9 * 60, 90, 20, 0.6, List.of(12, 14, 16, 14, 12, -1, 11, 12, 9, 7, -1,
		-1)), new Wash(List.of("river", "lake", "well", "fountain"), 8 * 60, 12 * 60), 21 * 60, new Tavern(18 * 60 + 30, 23 * 60 + 30, ""), new Watch(21 * 60,
			6 * 60), List.of(6 * 60, 12 * 60, 18 * 60 + 30), EnumSet.of(JobType.INNKEEPER, JobType.GUARD, JobType.JAILER, JobType.PRIEST, JobType.MONK,
				JobType.ENTERTAINER, JobType.STABLEMASTER), new Snow(-1, 10));

	/** The wash places a culture knows, in its order of preference. */
	public static final List<String> WASH_PLACES = List.of("river", "lake", "well", "fountain");

	private static volatile Map<Culture, RhythmData> byCulture = new EnumMap<>(Culture.class);
	private static volatile RhythmData fallback = DEFAULT;

	/** A culture's rhythm (its own file, else the default). */
	public static RhythmData of(Culture culture) {
		Map<Culture, RhythmData> m = byCulture;
		if (m.isEmpty()) {
			synchronized (RhythmData.class) {
				if (byCulture.isEmpty()) {
					install(loadBuiltin());
				}
				m = byCulture;
			}
		}
		RhythmData d = m.get(culture);
		return d != null ? d : fallback;
	}

	/** Which cultures have their own file (the rest use the default). */
	public static Set<Culture> withOwnFile() {
		of(Culture.TEMPERATE);
		return EnumSet.copyOf(byCulture.isEmpty() ? EnumSet.noneOf(Culture.class) : byCulture.keySet());
	}

	/** What was read: the default (key null) and the cultures' files. */
	public record Loaded(@Nullable JsonObject defaults, Map<Culture, JsonObject> cultures) {
	}

	public static synchronized void install(Loaded loaded) {
		RhythmData base = loaded.defaults() == null ? DEFAULT : parse(loaded.defaults(), DEFAULT);
		Map<Culture, RhythmData> m = new EnumMap<>(Culture.class);
		for (Map.Entry<Culture, JsonObject> e : loaded.cultures().entrySet()) {
			try {
				m.put(e.getKey(), parse(e.getValue(), base));
			} catch (RuntimeException ex) {
				Hearth.LOG.warn("rhythm: could not read {}'s file", e.getKey().getSerializedName(), ex);
			}
		}
		fallback = base;
		byCulture = m;
	}

	/** Reads the data pack's files. */
	public static Loaded load(ResourceManager manager) {
		JsonObject defaults = null;
		Map<Culture, JsonObject> cultures = new EnumMap<>(Culture.class);
		for (Map.Entry<Identifier, Resource> e : manager.listResources("hearth_social/rhythm", id -> id.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
			try (Reader r = e.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
				if (name.equals("default")) {
					defaults = json;
				} else {
					Culture c = culture(name);
					if (c != null) {
						cultures.put(c, json);
					} else {
						Hearth.LOG.warn("rhythm: {} names no culture", e.getKey());
					}
				}
			} catch (IOException | RuntimeException ex) {
				Hearth.LOG.warn("rhythm: could not read {}", e.getKey(), ex);
			}
		}
		Loaded l = new Loaded(defaults, cultures);
		return defaults == null && cultures.isEmpty() ? loadBuiltin() : l;
	}

	/** The mod's own files, read from the jar (tests, and before the first data load). */
	public static Loaded loadBuiltin() {
		JsonObject defaults = read("default");
		Map<Culture, JsonObject> cultures = new EnumMap<>(Culture.class);
		for (Culture c : Culture.values()) {
			JsonObject j = read(c.getSerializedName());
			if (j != null) {
				cultures.put(c, j);
			}
		}
		return new Loaded(defaults, cultures);
	}

	private static @Nullable JsonObject read(String name) {
		String path = "/data/hearth/hearth_social/rhythm/" + name + ".json";
		try (InputStream in = RhythmData.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			Hearth.LOG.warn("rhythm: could not read {}", path, e);
			return null;
		}
	}

	static @Nullable Culture culture(String name) {
		for (Culture c : Culture.values()) {
			if (c.getSerializedName().equals(name)) {
				return c;
			}
		}
		return null;
	}

	/** One file over a base: each field present replaces the base's. */
	public static RhythmData parse(JsonObject j, RhythmData base) {
		int rest = intOr(j, "rest_weekday", base.restWeekday);
		Service s = base.service;
		if (j.has("service")) {
			JsonObject o = j.getAsJsonObject("service");
			s = new Service(strOr(o, "name", s.name()), minute(o, "start", s.start()), intOr(o, "minutes", s.minutes()), intOr(o, "peal", s.peal()),
				o.has("share") ? o.get("share").getAsDouble() : s.share(), o.has("chant") ? ints(o.getAsJsonArray("chant")) : s.chant());
		}
		Wash w = base.wash;
		if (j.has("wash")) {
			JsonObject o = j.getAsJsonObject("wash");
			List<String> places = w.places();
			if (o.has("places")) {
				places = new ArrayList<>();
				for (JsonElement e : o.getAsJsonArray("places")) {
					String p = e.getAsString().toLowerCase(Locale.ROOT);
					if (!WASH_PLACES.contains(p)) {
						throw new IllegalArgumentException("unknown wash place " + p);
					}
					places.add(p);
				}
			}
			w = new Wash(List.copyOf(places), minute(o, "start", w.start()), minute(o, "end", w.end()));
		}
		int curfew = minute(j, "curfew", base.curfew);
		Tavern t = base.tavern;
		if (j.has("tavern")) {
			JsonObject o = j.getAsJsonObject("tavern");
			t = new Tavern(minute(o, "open", t.open()), minute(o, "close", t.close()), strOr(o, "instrument", t.instrument()));
		}
		Watch watch = base.watch;
		if (j.has("watch")) {
			JsonObject o = j.getAsJsonObject("watch");
			watch = new Watch(minute(o, "from", watch.from()), minute(o, "to", watch.to()));
		}
		List<Integer> meals = j.has("meals") ? ints(j.getAsJsonArray("meals")) : base.meals;
		Set<JobType> work = base.restDayWork;
		if (j.has("rest_day_work")) {
			work = EnumSet.noneOf(JobType.class);
			for (JsonElement e : j.getAsJsonArray("rest_day_work")) {
				JobType job = JobType.CODEC.byName(e.getAsString());
				if (job == null) {
					throw new IllegalArgumentException("unknown trade " + e.getAsString());
				}
				work.add(job);
			}
		}
		Snow snow = base.snow;
		if (j.has("first_snow")) {
			JsonObject o = j.getAsJsonObject("first_snow");
			snow = new Snow(intOr(o, "day", snow.day()), intOr(o, "winter_days", snow.winterDays()));
		}
		return new RhythmData(rest, s, w, curfew, t, watch, List.copyOf(meals), work.isEmpty() ? EnumSet.noneOf(JobType.class) : EnumSet.copyOf(work), snow);
	}

	private static int intOr(JsonObject o, String k, int d) {
		return o.has(k) ? o.get(k).getAsInt() : d;
	}

	private static int minute(JsonObject o, String k, int d) {
		int m = intOr(o, k, d);
		if (m < 0 || m >= 24 * 60) {
			throw new IllegalArgumentException(k + " is not a minute of the day: " + m);
		}
		return m;
	}

	private static String strOr(JsonObject o, String k, String d) {
		return o.has(k) ? o.get(k).getAsString() : d;
	}

	private static List<Integer> ints(JsonArray a) {
		List<Integer> out = new ArrayList<>();
		for (JsonElement e : a) {
			out.add(e.getAsInt());
		}
		return List.copyOf(out);
	}
}
