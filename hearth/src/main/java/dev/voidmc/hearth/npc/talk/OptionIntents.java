package dev.voidmc.hearth.npc.talk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Options from different {@link dev.voidmc.hearth.api.DialogueProvider}s that ask the same thing ("What's new in town?"
 * from the folk, "Anything on in town?" from town life) are shown once: the better one stays where it was and the other
 * goes. An intent is named by id in {@link #KNOWN} (with each id's rank), else it is the option's words, so two providers
 * offering the very same line also collapse. Better: open over locked, then the higher rank, then the earlier. Pure.
 */
public final class OptionIntents {
	private OptionIntents() {
	}

	/** An intent several providers offer, and how good each one's answer is (higher is better). */
	record Intent(String key, int rank) {
	}

	static final Map<String, Intent> KNOWN = Map.of(
		// town news: the life module's answer has the coming events and market day, the folk's only the latest gossip
		NpcDialogue.NEWS, new Intent("town_news", 1),
		"social:whats_on", new Intent("town_news", 2),
		// selling stolen goods: the fence's shop (the economy's) over the law module's sell-it-all
		"law:fence", new Intent("fence", 1),
		"economy:fence", new Intent("fence", 2));

	/** The intent an option stands for: a known one by id, else its words (lower case, letters and digits only). */
	public static String intent(String id, String label) {
		Intent known = KNOWN.get(id);
		if (known != null) {
			return known.key();
		}
		StringBuilder sb = new StringBuilder("words:");
		for (char c : label.toLowerCase(Locale.ROOT).toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				sb.append(c);
			}
		}
		return sb.length() == "words:".length() ? "id:" + id : sb.toString();
	}

	static int rank(String id) {
		Intent known = KNOWN.get(id);
		return known == null ? 0 : known.rank();
	}

	/**
	 * The options with every intent shown once (the better kept, in its own place); the order of the rest is unchanged.
	 * Options matched only by their words fold only across providers (one provider's two quests may share a title).
	 */
	public static <T> List<T> dedupe(List<T> options, Function<T, String> id, Function<T, String> label, Predicate<T> locked,
		Function<T, Object> provider) {
		String[] keys = new String[options.size()];
		Map<String, java.util.Set<Object>> seen = new HashMap<>();
		for (int i = 0; i < options.size(); i++) {
			T o = options.get(i);
			String key = intent(id.apply(o), label.apply(o));
			if (key.startsWith("words:") && !seen.computeIfAbsent(key, k -> new java.util.HashSet<>()).add(provider.apply(o))) {
				key = "own:" + i;
			}
			keys[i] = key;
		}
		Map<String, Integer> best = new HashMap<>();
		for (int i = 0; i < options.size(); i++) {
			Integer had = best.get(keys[i]);
			if (had == null || better(options.get(i), options.get(had), id, locked)) {
				best.put(keys[i], i);
			}
		}
		List<T> out = new ArrayList<>(options.size());
		for (int i = 0; i < options.size(); i++) {
			if (best.get(keys[i]) == i) {
				out.add(options.get(i));
			}
		}
		return out;
	}

	private static <T> boolean better(T a, T b, Function<T, String> id, Predicate<T> locked) {
		boolean la = locked.test(a);
		boolean lb = locked.test(b);
		if (la != lb) {
			return !la;
		}
		return rank(id.apply(a)) > rank(id.apply(b));
	}
}
