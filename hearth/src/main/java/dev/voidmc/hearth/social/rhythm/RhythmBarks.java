package dev.voidmc.hearth.social.rhythm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

/**
 * The rhythm's one-line remarks (docs/LIVING_WORLD_2.md §0.2, §2.7), from {@code data/<ns>/hearth_dialogue/barks/rhythm.json}
 * in the dialogue templates' format (a category of lines, each a string or {@code {"text", "cultures", "time", "weight"}}).
 * Said aloud through {@link NpcService.FolkHandle#say} to players within 16 blocks, with the shared cooldowns: one per
 * folk a minute, one per player per town every 4 seconds, the same line in a town not again for 10 minutes. Scripted
 * lines (the sermon, the watch's hourly call) skip the folk's own minute but keep the others.
 */
public final class RhythmBarks {
	public static final String SERMON = "rhythm.sermon";
	public static final String PORCH = "rhythm.porch";
	public static final String REGULAR = "rhythm.priest_regular";
	public static final String WATCH_CALL = "rhythm.watch_call";
	public static final String CHALLENGE = "rhythm.watch_challenge";
	public static final String CURFEW = "rhythm.curfew";
	public static final String TOAST = "rhythm.toast";
	public static final String CHEER = "rhythm.cheer";
	public static final String ARM_WRESTLE = "rhythm.arm_wrestle";
	public static final String ROUND = "rhythm.round";
	public static final String CLOSING = "rhythm.closing";
	public static final String HOMEWARD = "rhythm.homeward";
	public static final String CALL_CHILD = "rhythm.call_child";
	public static final String WASHER = "rhythm.washer";
	public static final String SEEK_COUNT = "rhythm.seek_count";
	public static final String SEEK_FOUND = "rhythm.seek_found";
	public static final String PLAY = "rhythm.play";
	public static final String SNOW = "rhythm.snow";
	public static final String WINTER = "rhythm.winter";

	/** Every category the code says (the data test checks each has lines). */
	public static final List<String> CATEGORIES = List.of(SERMON, PORCH, REGULAR, WATCH_CALL, CHALLENGE, CURFEW, TOAST, CHEER, ARM_WRESTLE, ROUND,
		CLOSING, HOMEWARD, CALL_CHILD, WASHER, SEEK_COUNT, SEEK_FOUND, PLAY, SNOW, WINTER);

	static final double RANGE = 16.0;
	static final long FOLK_COOLDOWN = 60 * 20;
	static final long PLAYER_COOLDOWN = 4 * 20;
	static final long LINE_COOLDOWN = 10 * 60 * 20;

	public record Line(String text, Set<Culture> cultures, @Nullable String time, int weight) {
		boolean fits(Culture c, int minute) {
			return (this.cultures.isEmpty() || this.cultures.contains(c)) && (this.time == null || this.time.equals(timeOf(minute)));
		}
	}

	private static volatile Map<String, List<Line>> lines = Map.of();
	private static final Map<UUID, Long> FOLK = new HashMap<>();
	private static final Map<String, Long> HEARD = new HashMap<>();
	private static final Map<String, Long> LINES = new HashMap<>();

	private RhythmBarks() {
	}

	public static Map<String, List<Line>> all() {
		Map<String, List<Line>> m = lines;
		if (m.isEmpty()) {
			m = loadBuiltin();
			lines = m;
		}
		return m;
	}

	public static void install(Map<String, List<Line>> loaded) {
		lines = loaded.isEmpty() ? loadBuiltin() : loaded;
	}

	public static Map<String, List<Line>> load(ResourceManager manager) {
		Map<String, List<Line>> out = new HashMap<>();
		for (Map.Entry<Identifier, Resource> e : manager.listResources("hearth_dialogue/barks", id -> id.getPath().endsWith("rhythm.json")).entrySet()) {
			try (Reader r = e.getValue().openAsReader()) {
				parseInto(r, out);
			} catch (IOException | RuntimeException ex) {
				Hearth.LOG.warn("rhythm: could not read barks {}", e.getKey(), ex);
			}
		}
		return out;
	}

	public static Map<String, List<Line>> loadBuiltin() {
		Map<String, List<Line>> out = new HashMap<>();
		try (InputStream in = RhythmBarks.class.getResourceAsStream("/data/hearth/hearth_dialogue/barks/rhythm.json")) {
			if (in != null) {
				parseInto(new InputStreamReader(in, StandardCharsets.UTF_8), out);
			}
		} catch (IOException | RuntimeException e) {
			Hearth.LOG.warn("rhythm: could not read the built-in barks", e);
		}
		return out;
	}

	static void parseInto(Reader reader, Map<String, List<Line>> out) {
		JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
		for (Map.Entry<String, JsonElement> cat : json.entrySet()) {
			if (!cat.getValue().isJsonArray()) {
				continue;
			}
			List<Line> list = out.computeIfAbsent(cat.getKey(), k -> new ArrayList<>());
			for (JsonElement e : cat.getValue().getAsJsonArray()) {
				if (e.isJsonPrimitive()) {
					list.add(new Line(e.getAsString(), Set.of(), null, 1));
					continue;
				}
				JsonObject o = e.getAsJsonObject();
				Set<Culture> cultures = EnumSet.noneOf(Culture.class);
				if (o.has("cultures")) {
					for (JsonElement c : o.getAsJsonArray("cultures")) {
						Culture culture = RhythmData.culture(c.getAsString());
						if (culture == null) {
							throw new IllegalArgumentException("unknown culture " + c.getAsString());
						}
						cultures.add(culture);
					}
				}
				list.add(new Line(o.get("text").getAsString(), cultures.isEmpty() ? Set.of() : cultures, o.has("time") ? o.get("time").getAsString() : null,
					o.has("weight") ? o.get("weight").getAsInt() : 1));
			}
		}
	}

	/** Morning 5–11, day 11–17, evening 17–21, night (as the dialogue templates' {@code time}). */
	static String timeOf(int minute) {
		int h = minute / 60;
		return h >= 5 && h < 11 ? "morning" : h >= 11 && h < 17 ? "day" : h >= 17 && h < 21 ? "evening" : "night";
	}

	/**
	 * A line of a category for this culture and time, filled; {@code salt} varies the pick; null if none fits or every
	 * fitting line was said in this town in the last ten minutes ({@code town} &lt; 0: no line cooldown, for tests).
	 */
	public static @Nullable String pick(String category, Culture culture, int minute, long salt, Map<String, String> vars, long town, long now) {
		List<Line> all = all().getOrDefault(category, List.of());
		List<Line> fit = new ArrayList<>();
		List<Integer> index = new ArrayList<>();
		int total = 0;
		for (int i = 0; i < all.size(); i++) {
			Line l = all.get(i);
			if (!l.fits(culture, minute)) {
				continue;
			}
			if (town >= 0) {
				Long last = LINES.get(town + "/" + category + "/" + i);
				if (last != null && now - last < LINE_COOLDOWN) {
					continue;
				}
			}
			fit.add(l);
			index.add(i);
			total += Math.max(1, l.weight());
		}
		if (fit.isEmpty()) {
			return null;
		}
		int r = (int) Math.floorMod(TownCalendar.mix(salt), (long) total);
		for (int i = 0; i < fit.size(); i++) {
			r -= Math.max(1, fit.get(i).weight());
			if (r < 0) {
				if (town >= 0) {
					LINES.put(town + "/" + category + "/" + index.get(i), now);
				}
				return fill(fit.get(i).text(), vars);
			}
		}
		return null;
	}

	public static String fill(String text, Map<String, String> vars) {
		String out = text;
		for (Map.Entry<String, String> e : vars.entrySet()) {
			out = out.replace("{" + e.getKey() + "}", e.getValue());
		}
		return out;
	}

	/**
	 * Says a line of {@code category} aloud if anyone is near to hear it and the cooldowns allow. Returns whether it was
	 * said.
	 */
	public static boolean say(NpcService.FolkHandle folk, String category, Map<String, String> vars, boolean scripted) {
		if (!(folk.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		long now = folk.gameTime();
		UUID id = folk.resident().id;
		if (!scripted) {
			Long last = FOLK.get(id);
			if (last != null && now - last < FOLK_COOLDOWN) {
				return false;
			}
		}
		long town = folk.settlement().id();
		List<ServerPlayer> hearers = new ArrayList<>();
		boolean fresh = false;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(folk.entity()) > RANGE * RANGE) {
				continue;
			}
			hearers.add(p);
			Long heard = HEARD.get(town + "/" + p.getUUID());
			if (heard == null || now - heard >= PLAYER_COOLDOWN) {
				fresh = true;
			}
		}
		if (hearers.isEmpty() || !fresh) {
			return false;
		}
		int minute = dev.voidmc.hearth.social.calendar.SocialTime.minute(dev.voidmc.hearth.social.life.TownLife.now(folk.settlement()));
		Map<String, String> v = new HashMap<>(vars);
		v.putIfAbsent("name", folk.resident().givenName);
		v.putIfAbsent("settlement", folk.settlement().name());
		v.putIfAbsent("player", hearers.get(0).getName().getString());
		String text = pick(category, folk.settlement().culture(), minute, id.getLeastSignificantBits() ^ now, v, town, now);
		if (text == null) {
			return false;
		}
		folk.say(Component.literal(text), null);
		FOLK.put(id, now);
		for (ServerPlayer p : hearers) {
			HEARD.put(town + "/" + p.getUUID(), now);
		}
		if (FOLK.size() > 4096 || HEARD.size() > 4096 || LINES.size() > 8192) {
			FOLK.clear();
			HEARD.clear();
			LINES.clear();
		}
		return true;
	}

	static void clear() {
		FOLK.clear();
		HEARD.clear();
		LINES.clear();
	}
}
