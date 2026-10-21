package dev.voidmc.hearth.npc.crowd;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
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
import java.util.UUID;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Barks (docs/LIVING_WORLD_2.md §0.2): one-line remarks folk say aloud, from {@code data/<ns>/hearth_dialogue/barks/*.json}.
 * A file maps categories to lines, as dialogue files do (so the dialogue loader reading the same folder takes them as
 * unused categories and no harm):
 *
 * <pre>
 * { "crowd_blade": [
 *     "Put that away, there are children here!",
 *     { "text": "Sheathe it, {player}.", "roles": ["guard"], "titles": ["known"], "cultures": ["temperate"],
 *       "time": "night", "traits": ["grumpy"], "weight": 2 } ] }
 * </pre>
 *
 * Conditions are all optional: {@code roles} (guard, merchant, mother, child, elder, folk), {@code titles} (the player's
 * title in town), {@code cultures}, {@code time} (morning, day, evening, night as dialogue), {@code traits} (any of).
 * Placeholders: {@code {player} {settlement} {given}}. Folk bark at most once a {@code per_folk_seconds}, a player hears a
 * bark in a town at most once a {@code per_player_town_seconds}, the same line is said at most once a
 * {@code per_line_seconds} ({@link CrowdConfig.Barks}). Other lanes' bark files (road, rhythm, work) load the same way
 * and say through {@link #say}.
 */
public final class Barks {
	private static final String FOLDER = "hearth_dialogue/barks";
	/** The bark files in the mod jar, read when no data pack has loaded yet (tests, the dev sandbox). */
	public static final String[] BUILTIN = {"crowd"};

	private static volatile @Nullable Map<String, List<Line>> lines;
	private static final Limiter LIMITER = new Limiter();

	private Barks() {
	}

	/** One line and when it fits. */
	public record Line(String key, String text, Set<Role> roles, Set<Title> titles, Set<Culture> cultures, @Nullable String time, Set<Trait> traits,
		int weight) {
		public boolean fits(Role role, @Nullable Title title, Culture culture, int minute, Set<Trait> folkTraits) {
			if (!this.roles.isEmpty() && !this.roles.contains(role)) {
				return false;
			}
			if (!this.titles.isEmpty() && (title == null || !this.titles.contains(title))) {
				return false;
			}
			if (!this.cultures.isEmpty() && !this.cultures.contains(culture)) {
				return false;
			}
			if (this.time != null && !this.time.equals(DialogueTemplates.timeOf(minute))) {
				return false;
			}
			if (!this.traits.isEmpty()) {
				boolean any = false;
				for (Trait t : this.traits) {
					if (folkTraits.contains(t)) {
						any = true;
						break;
					}
				}
				return any;
			}
			return true;
		}

		int specificity() {
			return (this.roles.isEmpty() ? 0 : 2) + (this.titles.isEmpty() ? 0 : 2) + (this.cultures.isEmpty() ? 0 : 2) + (this.time == null ? 0 : 1)
				+ (this.traits.isEmpty() ? 0 : 1);
		}
	}

	// ---------------------------------------------------------------- data

	public static Map<String, List<Line>> all() {
		Map<String, List<Line>> m = lines;
		if (m == null) {
			m = loadBuiltin();
			lines = m;
		}
		return m;
	}

	public static List<Line> lines(String category) {
		return all().getOrDefault(category, List.of());
	}

	public static void replace(Map<String, List<Line>> loaded) {
		lines = loaded;
	}

	public static Map<String, List<Line>> loadBuiltin() {
		Map<String, List<Line>> out = new HashMap<>();
		for (String name : BUILTIN) {
			String path = "/data/hearth/" + FOLDER + "/" + name + ".json";
			try (InputStream in = Barks.class.getResourceAsStream(path)) {
				if (in != null) {
					parseInto(new InputStreamReader(in, StandardCharsets.UTF_8), out);
				}
			} catch (IOException | RuntimeException e) {
				Hearth.LOG.warn("could not read barks {}", path, e);
			}
		}
		return out;
	}

	public static void parseInto(Reader reader, Map<String, List<Line>> out) {
		JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
		for (Map.Entry<String, JsonElement> cat : json.entrySet()) {
			List<Line> list = out.computeIfAbsent(cat.getKey(), k -> new ArrayList<>());
			for (JsonElement e : cat.getValue().getAsJsonArray()) {
				String key = cat.getKey() + "#" + list.size();
				if (e.isJsonPrimitive()) {
					list.add(new Line(key, e.getAsString(), Set.of(), Set.of(), Set.of(), null, Set.of(), 1));
				} else {
					list.add(line(key, e.getAsJsonObject()));
				}
			}
		}
	}

	private static Line line(String key, JsonObject o) {
		Set<Role> roles = EnumSet.noneOf(Role.class);
		for (String s : strings(o, "roles")) {
			roles.add(Role.byId(s));
		}
		Set<Title> titles = EnumSet.noneOf(Title.class);
		for (String s : strings(o, "titles")) {
			titles.add(Title.valueOf(s.toUpperCase(Locale.ROOT)));
		}
		Set<Culture> cultures = EnumSet.noneOf(Culture.class);
		for (String s : strings(o, "cultures")) {
			cultures.add(Culture.valueOf(s.toUpperCase(Locale.ROOT)));
		}
		Set<Trait> traits = EnumSet.noneOf(Trait.class);
		for (String s : strings(o, "traits")) {
			traits.add(Trait.valueOf(s.toUpperCase(Locale.ROOT)));
		}
		String time = o.has("time") ? o.get("time").getAsString() : null;
		int weight = o.has("weight") ? o.get("weight").getAsInt() : 1;
		return new Line(key, o.get("text").getAsString(), roles, titles, cultures, time, traits, weight);
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

	/** Data packs add bark files under {@code hearth_dialogue/barks/}. */
	public static void registerReloader() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Hearth.id("npc/barks"), new SimpleReloadListener<Map<String, List<Line>>>() {
			@Override
			protected Map<String, List<Line>> prepare(PreparableReloadListener.SharedState state) {
				Map<String, List<Line>> out = new HashMap<>();
				for (Map.Entry<Identifier, Resource> e : state.resourceManager().listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
					try (Reader r = e.getValue().openAsReader()) {
						parseInto(r, out);
					} catch (IOException | RuntimeException ex) {
						Hearth.LOG.warn("could not read barks {}", e.getKey(), ex);
					}
				}
				if (out.isEmpty()) {
					out.putAll(loadBuiltin());
				}
				return out;
			}

			@Override
			protected void apply(Map<String, List<Line>> prepared, PreparableReloadListener.SharedState state) {
				replace(prepared);
			}
		});
	}

	// ---------------------------------------------------------------- choosing and saying

	/**
	 * A line of the category that fits and has not been said lately, favouring the most specific, or null. Pure apart
	 * from the random.
	 */
	public static @Nullable Line pick(List<Line> all, Role role, @Nullable Title title, Culture culture, int minute, Set<Trait> traits,
		java.util.function.Predicate<Line> fresh, RandomSource random) {
		List<Line> fit = new ArrayList<>();
		int best = 0;
		for (Line l : all) {
			if (l.fits(role, title, culture, minute, traits) && fresh.test(l)) {
				fit.add(l);
				best = Math.max(best, l.specificity());
			}
		}
		if (fit.isEmpty()) {
			return null;
		}
		// the most specific lines mostly, a general one now and then for variety
		int threshold = random.nextInt(4) == 0 ? 0 : Math.max(0, best - 1);
		int total = 0;
		for (Line l : fit) {
			if (l.specificity() >= threshold) {
				total += Math.max(1, l.weight());
			}
		}
		int roll = random.nextInt(Math.max(1, total));
		for (Line l : fit) {
			if (l.specificity() >= threshold) {
				roll -= Math.max(1, l.weight());
				if (roll < 0) {
					return l;
				}
			}
		}
		return fit.get(0);
	}

	/**
	 * Has a folk say a bark of the category about {@code player} (who may be null: a bark to the street), if the
	 * cooldowns allow it. Returns whether anything was said.
	 */
	public static boolean say(FolkHandle folk, String category, Role role, @Nullable ServerPlayer player, @Nullable Title title) {
		if (!(folk.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		CrowdConfig.Barks cfg = Crowds.config().barks();
		long now = level.getGameTime();
		UUID me = folk.resident().id;
		UUID about = player == null ? null : player.getUUID();
		long town = folk.settlement().id();
		if (!LIMITER.may(me, about, town, now, cfg)) {
			return false;
		}
		List<Line> all = lines(category);
		if (all.isEmpty()) {
			return false;
		}
		Set<Trait> traits = HearthTraits.of(folk);
		Line line = pick(all, role, title, folk.settlement().culture(), NpcTime.minuteOfDay(level), traits, l -> LIMITER.fresh(l.key(), now, cfg),
			folk.entity().getRandom());
		if (line == null) {
			return false;
		}
		Map<String, String> values = new HashMap<>();
		values.put("player", player == null ? "stranger" : player.getScoreboardName());
		values.put("settlement", folk.settlement().name());
		values.put("given", folk.resident().givenName);
		String text = DialogueTemplates.fill(line.text(), values);
		folk.say(Component.literal(text), null);
		LIMITER.said(me, about, town, line.key(), now);
		CrowdStats.barked(folk.resident().fullName(), role, category, text);
		return true;
	}

	/** Forgets every cooldown (server stop, tests). */
	public static void clear() {
		LIMITER.clear();
	}

	/** The traits of the folk's resident, through the npc module's personality. */
	static final class HearthTraits {
		private HearthTraits() {
		}

		static Set<Trait> of(FolkHandle folk) {
			return dev.voidmc.hearth.npc.person.NpcData.of(folk.resident()).traits();
		}
	}

	/**
	 * The three bark cooldowns of §0.2: per folk, per player in a town, per line. Pure (ticks in, answers out).
	 */
	public static final class Limiter {
		private final Map<UUID, Long> folk = new HashMap<>();
		private final Map<String, Long> playerTown = new HashMap<>();
		private final Map<String, Long> line = new HashMap<>();

		public boolean may(UUID folk, @Nullable UUID player, long town, long now, CrowdConfig.Barks cfg) {
			Long f = this.folk.get(folk);
			if (f != null && now - f < cfg.perFolkSeconds() * 20L) {
				return false;
			}
			if (player != null) {
				Long p = this.playerTown.get(player + "|" + town);
				return p == null || now - p >= cfg.perPlayerTownSeconds() * 20L;
			}
			return true;
		}

		public boolean fresh(String key, long now, CrowdConfig.Barks cfg) {
			Long l = this.line.get(key);
			return l == null || now - l >= cfg.perLineSeconds() * 20L;
		}

		public void said(UUID folk, @Nullable UUID player, long town, String key, long now) {
			this.folk.put(folk, now);
			if (player != null) {
				this.playerTown.put(player + "|" + town, now);
			}
			this.line.put(key, now);
			if (this.line.size() > 4096 || this.folk.size() > 4096) {
				prune(now);
			}
		}

		private void prune(long now) {
			// a day is longer than any cooldown
			this.folk.values().removeIf(t -> now - t > 24000);
			this.playerTown.values().removeIf(t -> now - t > 24000);
			this.line.values().removeIf(t -> now - t > 24000);
		}

		public void clear() {
			this.folk.clear();
			this.playerTown.clear();
			this.line.clear();
		}
	}
}
