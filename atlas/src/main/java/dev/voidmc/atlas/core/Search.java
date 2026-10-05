package dev.voidmc.atlas.core;

import java.util.Locale;

/**
 * How well a search matches a row (a place, a waypoint, a biome's area): the name counts more than the line under it,
 * a whole name more than its start, a word's start more than the middle of one, and the letters in order ("dpdk" for
 * "Deep Dark") least. Case and accents of the usual kind are ignored.
 */
public final class Search {
	/** No match. */
	public static final int NONE = 0;

	private Search() {
	}

	/** A score for a query against a title and a subtitle: higher is better, {@link #NONE} for no match. */
	public static int score(String query, String title, String subtitle) {
		String q = fold(query).strip();
		if (q.isEmpty()) {
			return 1;
		}
		int t = scoreOne(q, fold(title));
		int s = scoreOne(q, fold(subtitle));
		if (t == NONE && s == NONE) {
			return NONE;
		}
		return Math.max(t * 2, s);
	}

	private static int scoreOne(String q, String text) {
		if (text.isEmpty()) {
			return NONE;
		}
		if (text.equals(q)) {
			return 100;
		}
		if (text.startsWith(q)) {
			return 80;
		}
		int at = text.indexOf(q);
		while (at > 0) {
			if (!Character.isLetterOrDigit(text.charAt(at - 1))) {
				return 60;
			}
			at = text.indexOf(q, at + 1);
		}
		if (text.contains(q)) {
			return 40;
		}
		// the letters in order, each word's first letter worth more ("dd" or "dpdk" for "deep dark")
		int i = 0;
		int bonus = 0;
		for (int k = 0; k < text.length() && i < q.length(); k++) {
			if (text.charAt(k) == q.charAt(i)) {
				if (k == 0 || !Character.isLetterOrDigit(text.charAt(k - 1))) {
					bonus++;
				}
				i++;
			}
		}
		if (i < q.length() || q.length() < 2) {
			return NONE;
		}
		return 10 + Math.min(9, bonus);
	}

	/** Lower case, the common accents taken off ("Forêt" matches "foret"). */
	static String fold(String s) {
		if (s == null) {
			return "";
		}
		String lower = s.toLowerCase(Locale.ROOT);
		String n = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD);
		StringBuilder b = new StringBuilder(n.length());
		for (int i = 0; i < n.length(); i++) {
			char c = n.charAt(i);
			if (Character.getType(c) != Character.NON_SPACING_MARK) {
				b.append(c);
			}
		}
		return b.toString();
	}
}
