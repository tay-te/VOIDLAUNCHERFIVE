package dev.voidmc.hearth.sim.lives;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The named characters' roles and the threads of their stories, from {@code data/<ns>/hearth_lives/roles/*.json} and
 * {@code data/<ns>/hearth_lives/threads/*.json} (docs/LIVING_WORLD_2.md §4.2, §4.7). A role says who may be one, their
 * personal schedule and their thread; a thread is a small state machine of beats:
 *
 * <pre>
 * { "start": "drinking",
 *   "beats": {
 *     "drinking": { "next": [ { "to": "debt", "after": 5 } ] },
 *     "debt":     { "effects": [ { "type": "news", "kind": "debt" } ],
 *                   "choices": [ { "id": "pay", "cost": 120, "to": "sworn_off" },
 *                                { "id": "priest", "quest": "hearth:lives/vow_candles", "to": "vow",
 *                                  "fallback": { "cost": 20, "to": "vowed" } },
 *                                { "id": "talk", "speech": "persuade", "difficulty": 0.3, "to": "sworn_off", "fail": "debt" } ],
 *                   "next": [ { "to": "forge_lost", "after": 10 } ] },
 *     "sworn_off": { "end": true, "effects": [ { "type": "gift", "item": "minecraft:iron_sword" } ] } } }
 * </pre>
 *
 * A transition fires on the first day all its conditions hold: {@code after} days in the beat, {@code chance} a day,
 * {@code event} ({@code rest_day}, {@code market_day}, {@code feast} or a town event's id), {@code partner}
 * ({@code present}, {@code gone}, {@code sweetheart}, {@code married}), {@code flag} (a fact the thread has
 * recorded: {@code quest:<template>} done, {@code chose:<option>}). Effects and choices are listed in {@link Effect} and
 * {@link Choice}. Every beat has lines {@code thread.<thread>.<beat>} in the dialogue files and a line
 * {@code hearth.lives.story.<thread>.<beat>} for the People page; every choice a label
 * {@code hearth.lives.option.<thread>.<id>}.
 */
public final class LivesBook {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	/** The built-in files, read when no data pack has loaded yet (tests, the dev harness). */
	public static final List<String> ROLES = List.of("drunk_smith", "widow_at_well", "young_lovers", "ambitious_apprentice", "old_soldier", "gossip",
		"miser_merchant", "healer", "runaway", "poacher", "miller_in_debt", "pious_pilgrim", "exile_come_home");

	private static volatile @Nullable LivesBook current;

	public final Map<String, Role> roles;
	public final Map<String, Thread> threads;
	/** Problems found while reading (the data test fails on any). */
	public final List<String> errors;

	LivesBook(Map<String, Role> roles, Map<String, Thread> threads, List<String> errors) {
		this.roles = roles;
		this.threads = threads;
		this.errors = errors;
	}

	public static LivesBook get() {
		LivesBook b = current;
		if (b == null) {
			b = builtin();
			current = b;
		}
		return b;
	}

	public static void install(LivesBook book) {
		current = book.roles.isEmpty() ? builtin() : book;
	}

	// ---------------------------------------------------------------- the data

	/**
	 * Who may hold a role.
	 *
	 * @param needs one of {@code ""}, {@code widowed}, {@code apprentice} (an apprentice or a journeyman), {@code master},
	 *              {@code single}, {@code newcomer} (arrived in the last 40 days), {@code young} (a child near grown or an
	 *              adult in their first 30 days grown)
	 */
	public record Who(Set<JobType> jobs, Set<AgeStage> ages, Set<String> traits, Set<Station> stations, String needs) {
	}

	/**
	 * A stretch of a named character's day (docs/LIVING_WORLD_2.md §4.3, through {@code ScheduleOverrides}).
	 *
	 * @param days  {@code all}, {@code rest} (the rest day) or {@code work} (the other days)
	 * @param beats the beats of the thread it holds in (empty: all)
	 */
	public record Window(String days, int from, int to, String activity, @Nullable String leisure, @Nullable String where, Set<String> beats) {
	}

	/**
	 * @param partner who the thread is about: {@code none}, {@code apprentice}, {@code master}, {@code sweetheart},
	 *                {@code creditor}, {@code kin}, {@code household} (a house the gossip talks about)
	 */
	public record Role(String id, String thread, int weight, SettlementTier minTier, Who who, String partner, List<Window> schedule) {
	}

	/**
	 * One step of a thread's effects.
	 *
	 * <pre>
	 *   news      kind                 the town talks of it (life news)
	 *   relation  with, delta, bond?   the notable's relation with the partner
	 *   sweethearts                    the notable and the partner become sweethearts (the banns follow)
	 *   feud      cause                a feud between the notable's house and the partner's
	 *   peace                          the feud between them is made up
	 *   master_to                      the notable's mastery passes to the partner (the forge goes to the apprentice)
	 *   master                         the notable makes master of their trade (or the apprentice's)
	 *   idle      days                 kept from their trade a while
	 *   leave                          they leave the town for good
	 *   die                            they die (only where a beat says so)
	 *   away      days                 on the road a while (a pilgrimage), then back
	 *   mourn     days                 in mourning
	 *   treasury  bits                 coin to the town's treasury (from their purse, as far as it goes)
	 *   memory    weight               the player who moved the thread is remembered (+/-)
	 *   deed      kind, weight         a deed by that player
	 *   gift      item, count          an item for that player (given now, or at their next talk)
	 *   coin      bits                 coin for that player
	 * </pre>
	 */
	public record Effect(String type, String kind, int amount, String item, @Nullable String bond) {
	}

	/** A condition-led move to another beat. */
	public record Next(String to, int after, double chance, @Nullable String event, @Nullable String partner, @Nullable String flag) {
	}

	/**
	 * A choice a player may make while the thread is at this beat (open to the first who takes it).
	 *
	 * @param cost       coin charged to the player
	 * @param speech     {@code persuade}, {@code intimidate} or {@code flatter}: a check
	 * @param fail       the beat on a failed check (null: stays)
	 * @param quest      a personal quest offered ({@code QuestService.offerPersonal}); the thread waits at {@code to}
	 * @param fallback   when no quest can be offered: the option costs {@code fallback.cost} and goes to {@code fallback.to}
	 */
	public record Choice(String id, int cost, @Nullable String speech, double difficulty, String to, @Nullable String fail, @Nullable Identifier quest,
		@Nullable Fallback fallback, List<Effect> effects) {
	}

	public record Fallback(int cost, String to) {
	}

	public record Beat(String id, boolean end, List<Effect> effects, List<Next> next, List<Choice> choices) {
	}

	public record Thread(String id, String start, Map<String, Beat> beats) {
		public @Nullable Beat beat(String id) {
			return this.beats.get(id);
		}
	}

	public @Nullable Role role(String id) {
		return this.roles.get(id);
	}

	public @Nullable Thread thread(String id) {
		return this.threads.get(id);
	}

	// ---------------------------------------------------------------- loading

	public static LivesBook builtin() {
		Map<String, Role> roles = new LinkedHashMap<>();
		Map<String, Thread> threads = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();
		for (String name : ROLES) {
			readBuiltin("/data/hearth/hearth_lives/roles/" + name + ".json", name, true, roles, threads, errors);
			readBuiltin("/data/hearth/hearth_lives/threads/" + name + ".json", name, false, roles, threads, errors);
		}
		return new LivesBook(roles, threads, errors);
	}

	private static void readBuiltin(String path, String name, boolean role, Map<String, Role> roles, Map<String, Thread> threads, List<String> errors) {
		try (InputStream in = LivesBook.class.getResourceAsStream(path)) {
			if (in == null) {
				errors.add("missing " + path);
				return;
			}
			read(new InputStreamReader(in, StandardCharsets.UTF_8), name, role, roles, threads, errors);
		} catch (IOException | RuntimeException e) {
			errors.add(path + ": " + e.getMessage());
		}
	}

	/** Every role and thread file in the data packs (later packs override by name). */
	public static LivesBook load(ResourceManager manager) {
		Map<String, Role> roles = new LinkedHashMap<>();
		Map<String, Thread> threads = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();
		for (String kind : new String[] {"roles", "threads"}) {
			for (Map.Entry<Identifier, Resource> e : manager.listResources("hearth_lives/" + kind, id -> id.getPath().endsWith(".json")).entrySet()) {
				String path = e.getKey().getPath();
				String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
				try (Reader r = e.getValue().openAsReader()) {
					read(r, name, kind.equals("roles"), roles, threads, errors);
				} catch (IOException | RuntimeException ex) {
					errors.add(e.getKey() + ": " + ex.getMessage());
				}
			}
		}
		for (String err : errors) {
			LOG.warn("lives: {}", err);
		}
		return new LivesBook(roles, threads, errors);
	}

	static void read(Reader reader, String name, boolean role, Map<String, Role> roles, Map<String, Thread> threads, List<String> errors) {
		JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
		if (role) {
			roles.put(name, role(name, o, errors));
		} else {
			threads.put(name, thread(name, o, errors));
		}
	}

	private static Role role(String id, JsonObject o, List<String> errors) {
		JsonObject w = o.has("who") ? o.getAsJsonObject("who") : new JsonObject();
		Set<JobType> jobs = EnumSet.noneOf(JobType.class);
		for (String s : strings(w, "jobs")) {
			JobType j = Life.job(s);
			if (j == null) {
				errors.add("role " + id + ": unknown job " + s);
			} else {
				jobs.add(j);
			}
		}
		Set<AgeStage> ages = EnumSet.noneOf(AgeStage.class);
		for (String s : strings(w, "ages")) {
			ages.add(AgeStage.valueOf(s.toUpperCase(Locale.ROOT)));
		}
		Set<Station> stations = EnumSet.noneOf(Station.class);
		for (String s : strings(w, "stations")) {
			stations.add(Station.valueOf(s.toUpperCase(Locale.ROOT)));
		}
		Who who = new Who(jobs, ages, Set.copyOf(strings(w, "traits")), stations, str(w, "needs", ""));
		List<Window> schedule = new ArrayList<>();
		if (o.has("schedule")) {
			for (JsonElement e : o.getAsJsonArray("schedule")) {
				JsonObject s = e.getAsJsonObject();
				schedule.add(new Window(str(s, "days", "all"), minute(s.get("from").getAsString()), minute(s.get("to").getAsString()), str(s, "activity", "leisure"),
					s.has("leisure") ? s.get("leisure").getAsString() : null, s.has("where") ? s.get("where").getAsString() : null, Set.copyOf(strings(s, "beats"))));
			}
		}
		SettlementTier tier = SettlementTier.HAMLET;
		if (o.has("min_tier")) {
			tier = SettlementTier.valueOf(o.get("min_tier").getAsString().toUpperCase(Locale.ROOT));
		}
		return new Role(id, str(o, "thread", id), o.has("weight") ? o.get("weight").getAsInt() : 1, tier, who, str(o, "partner", "none"), List.copyOf(schedule));
	}

	private static Thread thread(String id, JsonObject o, List<String> errors) {
		Map<String, Beat> beats = new LinkedHashMap<>();
		JsonObject b = o.getAsJsonObject("beats");
		for (Map.Entry<String, JsonElement> e : b.entrySet()) {
			JsonObject bo = e.getValue().getAsJsonObject();
			List<Next> next = new ArrayList<>();
			if (bo.has("next")) {
				for (JsonElement n : bo.getAsJsonArray("next")) {
					JsonObject no = n.getAsJsonObject();
					next.add(new Next(no.get("to").getAsString(), no.has("after") ? no.get("after").getAsInt() : 0, no.has("chance") ? no.get("chance").getAsDouble() : 1,
						opt(no, "event"), opt(no, "partner"), opt(no, "flag")));
				}
			}
			List<Choice> choices = new ArrayList<>();
			if (bo.has("choices")) {
				for (JsonElement c : bo.getAsJsonArray("choices")) {
					JsonObject co = c.getAsJsonObject();
					Fallback fb = null;
					if (co.has("fallback")) {
						JsonObject f = co.getAsJsonObject("fallback");
						fb = new Fallback(f.has("cost") ? f.get("cost").getAsInt() : 0, f.get("to").getAsString());
					}
					choices.add(new Choice(co.get("id").getAsString(), co.has("cost") ? co.get("cost").getAsInt() : 0, opt(co, "speech"),
						co.has("difficulty") ? co.get("difficulty").getAsDouble() : 0.2, co.get("to").getAsString(), opt(co, "fail"),
						co.has("quest") ? Identifier.parse(co.get("quest").getAsString()) : null, fb, effects(co)));
				}
			}
			beats.put(e.getKey(), new Beat(e.getKey(), bo.has("end") && bo.get("end").getAsBoolean(), effects(bo), List.copyOf(next), List.copyOf(choices)));
		}
		String start = str(o, "start", beats.isEmpty() ? "" : beats.keySet().iterator().next());
		if (!beats.containsKey(start)) {
			errors.add("thread " + id + ": no start beat " + start);
		}
		return new Thread(id, start, beats);
	}

	private static List<Effect> effects(JsonObject o) {
		List<Effect> out = new ArrayList<>();
		if (o.has("effects")) {
			for (JsonElement e : o.getAsJsonArray("effects")) {
				JsonObject eo = e.getAsJsonObject();
				int amount = eo.has("amount") ? eo.get("amount").getAsInt() : eo.has("days") ? eo.get("days").getAsInt() : eo.has("weight") ? eo.get("weight").getAsInt()
					: eo.has("delta") ? eo.get("delta").getAsInt() : eo.has("bits") ? eo.get("bits").getAsInt() : eo.has("count") ? eo.get("count").getAsInt() : 0;
				out.add(new Effect(eo.get("type").getAsString(), str(eo, "kind", str(eo, "cause", "")), amount, str(eo, "item", ""), opt(eo, "bond")));
			}
		}
		return List.copyOf(out);
	}

	static int minute(String hhmm) {
		int c = hhmm.indexOf(':');
		return c < 0 ? Integer.parseInt(hhmm) * 60 : Integer.parseInt(hhmm.substring(0, c)) * 60 + Integer.parseInt(hhmm.substring(c + 1));
	}

	private static String str(JsonObject o, String k, String def) {
		return o.has(k) ? o.get(k).getAsString() : def;
	}

	private static @Nullable String opt(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsString() : null;
	}

	private static List<String> strings(JsonObject o, String k) {
		List<String> out = new ArrayList<>();
		if (o.has(k)) {
			JsonArray a = o.getAsJsonArray(k);
			for (JsonElement e : a) {
				out.add(e.getAsString());
			}
		}
		return out;
	}
}
