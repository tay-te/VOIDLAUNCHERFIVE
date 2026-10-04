package dev.voidmc.hearth.social.dice;

import java.util.Collection;
import java.util.Locale;

/**
 * How a folk plays dice, from their traits. Boldness scales what they think pressing on is worth against banking (1 is
 * a level head); perceptiveness is their extra chance per roll to notice loaded dice; the stake factor scales the most
 * they will put on the table.
 */
public enum DicePersonality {
	/** Banks early, plays small: the cautious and the shy. */
	CAREFUL("careful", 0.78, 0.03, 0.7),
	/** Plays the odds and adapts to the score: the curious and industrious. */
	SHREWD("shrewd", 1.0, 0.05, 1.0),
	/** Presses on and laughs at a bust: the brave and cheerful. */
	BOLD("bold", 1.18, 0.01, 1.2),
	/** Cannot stop rolling: the greedy and dreamy. */
	RECKLESS("reckless", 1.4, -0.01, 1.6),
	/** Grumbles at every roll, watches yours like a hawk. */
	SOUR("sour", 0.92, 0.06, 0.9);

	private final String name;
	public final double boldness;
	public final double perceptiveness;
	public final double stakeFactor;

	DicePersonality(String name, double boldness, double perceptiveness, double stakeFactor) {
		this.name = name;
		this.boldness = boldness;
		this.perceptiveness = perceptiveness;
		this.stakeFactor = stakeFactor;
	}

	public String id() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.social.dice.personality." + this.name;
	}

	/**
	 * From npc traits (lower-case names, docs/FOLK.md §3); {@code seed} breaks ties so two folk with the same traits do
	 * not always play alike.
	 */
	public static DicePersonality of(Collection<String> traits, long seed) {
		boolean greedy = has(traits, "greedy");
		boolean dreamy = has(traits, "dreamy");
		boolean cautious = has(traits, "cautious") || has(traits, "shy");
		boolean brave = has(traits, "brave") || has(traits, "cheerful");
		boolean sharp = has(traits, "curious") || has(traits, "industrious");
		boolean grumpy = has(traits, "grumpy");
		if (greedy && !cautious || dreamy && brave) {
			return RECKLESS;
		}
		if (grumpy) {
			return SOUR;
		}
		if (cautious && !brave) {
			return CAREFUL;
		}
		if (brave && !sharp) {
			return BOLD;
		}
		if (sharp) {
			return SHREWD;
		}
		DicePersonality[] all = values();
		return all[(int) Math.floorMod(seed, (long) all.length)];
	}

	private static boolean has(Collection<String> traits, String t) {
		for (String s : traits) {
			if (s.toLowerCase(Locale.ROOT).equals(t)) {
				return true;
			}
		}
		return false;
	}
}
