package dev.voidmc.mettle.character.draft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.Mettle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * The card draft's sound cues (docs/SOUNDS.md), read from {@code assets/mettle/card_cues.json}: which layers each moment
 * of the draft plays, when, at what pitch and volume. The same file drives the game ({@code client.draft.CardAudio}) and
 * the offline previews ({@code tools/sounds/preview.py}), so a preview is what the game plays. Plain Java and Gson:
 * unit-tested in {@code CardCuesTest}.
 *
 * <p>For tuning in a running game, {@code -Dmettle.cues=<path to a card_cues.json>} reads that file instead and reads
 * it again whenever it changes.
 */
public final class CardCues {
	/** One layer of a cue: a sound event played {@code atMs} after the cue starts. */
	public record Layer(int atMs, String sound, float pitch, float volume, float jitter) {
		/** The sound event; a name without a namespace is Mettle's. */
		public Identifier id() {
			return this.sound.indexOf(':') >= 0 ? Identifier.parse(this.sound) : Mettle.id(this.sound);
		}
	}

	/** A looping layer held while something lasts (the Mythic card's idle shimmer while hovered). */
	public record Loop(String sound, float volume, int fadeInMs, int fadeOutMs) {
		public Identifier id() {
			return this.sound.indexOf(':') >= 0 ? Identifier.parse(this.sound) : Mettle.id(this.sound);
		}
	}

	/** The timing rules that string cues together. */
	public record Rules(int hoverGapMs, int stingerAtMs, int burnAfterPickMs, List<String> stingerOrder, int nudgeDelayMs,
		int nudgeGapMs, int pickHushesMomentMs) {
	}

	public static final String RESOURCE = "/assets/mettle/card_cues.json";

	private static CardCues loaded;
	private static long loadedStamp = Long.MIN_VALUE;

	private final Map<String, List<Layer>> cues;
	private final Map<String, Loop> loops;
	private final JsonObject markers;
	private final Rules rules;

	private CardCues(Map<String, List<Layer>> cues, Map<String, Loop> loops, JsonObject markers, Rules rules) {
		this.cues = cues;
		this.loops = loops;
		this.markers = markers;
		this.rules = rules;
	}

	public static CardCues parse(Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		Map<String, List<Layer>> cues = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("cues").entrySet()) {
			List<Layer> layers = new ArrayList<>();
			for (JsonElement l : e.getValue().getAsJsonArray()) {
				JsonArray a = l.getAsJsonArray();
				layers.add(new Layer(a.get(0).getAsInt(), a.get(1).getAsString(), a.get(2).getAsFloat(), a.get(3).getAsFloat(),
					a.size() > 4 ? a.get(4).getAsFloat() : 0f));
			}
			layers.sort((x, y) -> Integer.compare(x.atMs(), y.atMs()));
			cues.put(e.getKey(), List.copyOf(layers));
		}
		Map<String, Loop> loops = new LinkedHashMap<>();
		if (root.has("loops")) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("loops").entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				loops.put(e.getKey(), new Loop(o.get("sound").getAsString(), o.get("volume").getAsFloat(), o.get("fadeInMs").getAsInt(),
					o.get("fadeOutMs").getAsInt()));
			}
		}
		JsonObject markers = root.has("markers") ? root.getAsJsonObject("markers") : new JsonObject();
		JsonObject r = root.getAsJsonObject("rules");
		List<String> order = new ArrayList<>();
		r.getAsJsonArray("stingerOrder").forEach(e -> order.add(e.getAsString()));
		Rules rules = new Rules(r.get("hoverGapMs").getAsInt(), r.get("stingerAtMs").getAsInt(), r.get("burnAfterPickMs").getAsInt(),
			List.copyOf(order), r.get("nudgeDelayMs").getAsInt(), r.get("nudgeGapMs").getAsInt(), r.get("pickHushesMomentMs").getAsInt());
		return new CardCues(Map.copyOf(cues), Map.copyOf(loops), markers, rules);
	}

	/** The cues the game plays: the mod's own file, or {@code -Dmettle.cues} while tuning. */
	public static synchronized CardCues get() {
		String override = System.getProperty("mettle.cues");
		if (override != null && !override.isBlank()) {
			Path path = Path.of(override);
			try {
				long stamp = Files.getLastModifiedTime(path).toMillis();
				if (loaded == null || stamp != loadedStamp) {
					try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
						loaded = parse(r);
						loadedStamp = stamp;
					}
				}
				return loaded;
			} catch (IOException | RuntimeException e) {
				Mettle.LOGGER.warn("mettle.cues: could not read {} ({}); using the built-in cues", override, e.toString());
			}
		}
		if (loaded == null || loadedStamp != Long.MIN_VALUE) {
			try (InputStream in = CardCues.class.getResourceAsStream(RESOURCE)) {
				if (in == null) {
					throw new IllegalStateException(RESOURCE + " is missing from the mod");
				}
				loaded = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
				loadedStamp = Long.MIN_VALUE;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return loaded;
	}

	public List<Layer> cue(String name) {
		return this.cues.getOrDefault(name, List.of());
	}

	public boolean has(String name) {
		return this.cues.containsKey(name);
	}

	public Set<String> names() {
		return this.cues.keySet();
	}

	public Optional<Loop> loop(String name) {
		return Optional.ofNullable(this.loops.get(name));
	}

	public Collection<Loop> loops() {
		return this.loops.values();
	}

	/** Moments in a cue the overlay can time its animation to ({@code markers.mythic_reveal}: cracks, flip...). */
	public JsonObject markers() {
		return this.markers.deepCopy();
	}

	public Rules rules() {
		return this.rules;
	}

	/** The cue for a rarity ({@code hover.epic}), or the Common one when that rarity has none of its own. */
	public String forRarity(String prefix, String rarityKey) {
		String name = prefix + "." + rarityKey;
		return has(name) ? name : prefix + ".common";
	}

	/** The one stinger a freshly dealt hand earns: the highest of its rarities in {@code stingerOrder}, if any. */
	public Optional<String> stinger(Collection<String> rarityKeys) {
		for (String r : this.rules.stingerOrder()) {
			if (rarityKeys.contains(r) && has("stinger." + r)) {
				return Optional.of("stinger." + r);
			}
		}
		return Optional.empty();
	}

	/**
	 * How a card sounds: its rarity's name, except that a wildcard dealt at Common, Rare or Epic sounds like a wildcard
	 * (a Cursed, Mastery, Earned or Mythic card keeps its own voice when it comes as a wildcard).
	 */
	public static String rarityKey(String rarity, boolean wildcard) {
		String r = rarity == null || rarity.isBlank() ? "common" : rarity.toLowerCase(Locale.ROOT);
		if (wildcard && (r.equals("common") || r.equals("rare") || r.equals("epic"))) {
			return "wildcard";
		}
		return r;
	}
}
