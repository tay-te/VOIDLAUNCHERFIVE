package dev.voidmc.hearth.npc.person;

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
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given and family names for each culture, from {@code data/hearth/hearth_names/<culture>.json}:
 *
 * <pre>
 *   { "male": ["..."], "female": ["..."], "family": ["..."] }
 * </pre>
 *
 * The mod's own files are read from the classpath at first use, so names exist before any data pack is loaded (and in
 * unit tests); data packs can replace a culture's lists through the server reload ({@link #replace}).
 */
public final class NameBank {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	private static volatile Map<Culture, Names> names;

	private NameBank() {
	}

	/** One culture's names. Lists are never empty (a placeholder fills a missing one). */
	public record Names(List<String> male, List<String> female, List<String> family) {
		public String given(boolean female, RandomSource random) {
			List<String> list = female ? this.female : this.male;
			return list.get(random.nextInt(list.size()));
		}

		public String family(RandomSource random) {
			return this.family.get(random.nextInt(this.family.size()));
		}
	}

	public static Names of(Culture culture) {
		Map<Culture, Names> map = names;
		if (map == null) {
			map = loadBuiltin();
			names = map;
		}
		return map.get(culture);
	}

	/** Installs names read from data packs; cultures missing from {@code loaded} keep the built-in lists. */
	public static void replace(Map<Culture, Names> loaded) {
		Map<Culture, Names> map = new EnumMap<>(loadBuiltin());
		map.putAll(loaded);
		names = map;
	}

	public static Names parse(Reader reader) {
		JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
		return new Names(list(json, "male"), list(json, "female"), list(json, "family"));
	}

	private static Map<Culture, Names> loadBuiltin() {
		Map<Culture, Names> map = new EnumMap<>(Culture.class);
		for (Culture culture : Culture.values()) {
			String path = "/data/hearth/hearth_names/" + culture.getSerializedName() + ".json";
			try (InputStream in = NameBank.class.getResourceAsStream(path)) {
				if (in == null) {
					LOG.warn("no built-in names for culture {}", culture.getSerializedName());
					map.put(culture, placeholder());
					continue;
				}
				map.put(culture, parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
			} catch (IOException | RuntimeException e) {
				LOG.warn("could not read {}", path, e);
				map.put(culture, placeholder());
			}
		}
		return map;
	}

	private static List<String> list(JsonObject json, String key) {
		List<String> out = new ArrayList<>();
		JsonArray array = json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
		for (JsonElement e : array) {
			String s = e.getAsString().trim();
			if (!s.isEmpty() && !out.contains(s)) {
				out.add(s);
			}
		}
		if (out.isEmpty()) {
			out.add(key.equals("family") ? "" : "Wren");
		}
		return List.copyOf(out);
	}

	private static Names placeholder() {
		return new Names(List.of("Tom"), List.of("Wren"), List.of("Hearth"));
	}
}
