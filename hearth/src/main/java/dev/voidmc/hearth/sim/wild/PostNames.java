package dev.voidmc.hearth.sim.wild;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.sim.settlement.Names;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;

/**
 * Names of posts (docs/LIVING_WORLD_2.md §7.7) from {@code data/hearth/hearth_names/posts.json}: per type a list of names
 * and patterns ({@code {town}} a place name of the culture, {@code {saint}}, {@code {beast}}, {@code {tree}},
 * {@code {colour}} from the file's word lists). Read once from the mod's resources; any thread.
 */
public final class PostNames {
	private static volatile Map<String, List<String>> lists;

	private PostNames() {
	}

	public static String name(PostType type, Culture culture, RandomSource random) {
		Map<String, List<String>> l = lists();
		List<String> names = l.getOrDefault(type.getSerializedName(), List.of());
		if (names.isEmpty()) {
			return Names.settlement(culture, random) + " " + type.getSerializedName().replace('_', ' ');
		}
		String n = names.get(random.nextInt(names.size()));
		n = fill(n, "{saint}", l.get("saint"), random);
		n = fill(n, "{beast}", l.get("beast"), random);
		n = fill(n, "{tree}", l.get("tree"), random);
		n = fill(n, "{colour}", l.get("colour"), random);
		if (n.contains("{town}")) {
			n = n.replace("{town}", Names.settlement(culture, random));
		}
		return n;
	}

	private static String fill(String n, String key, List<String> words, RandomSource random) {
		while (n.contains(key) && words != null && !words.isEmpty()) {
			n = n.replaceFirst(java.util.regex.Pattern.quote(key), words.get(random.nextInt(words.size())));
		}
		return n;
	}

	private static Map<String, List<String>> lists() {
		Map<String, List<String>> l = lists;
		if (l == null) {
			l = load();
			lists = l;
		}
		return l;
	}

	private static Map<String, List<String>> load() {
		Map<String, List<String>> out = new HashMap<>();
		try (InputStream in = PostNames.class.getResourceAsStream("/data/hearth/hearth_names/posts.json")) {
			if (in == null) {
				return out;
			}
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (e.getValue() instanceof JsonArray a) {
					List<String> words = new ArrayList<>();
					a.forEach(w -> words.add(w.getAsString()));
					out.put(e.getKey(), List.copyOf(words));
				}
			}
		} catch (Exception e) {
			dev.voidmc.hearth.Hearth.LOG.warn("hearth wild: the post names could not be read", e);
		}
		return out;
	}
}
