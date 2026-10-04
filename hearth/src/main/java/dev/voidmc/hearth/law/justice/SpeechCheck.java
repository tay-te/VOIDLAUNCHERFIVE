package dev.voidmc.hearth.law.justice;

/**
 * The odds of talking one's way out (docs/LIVING_WORLD.md §4–§5): persuading a guard, bribing a guard or a witness,
 * threatening a witness into silence. Decided by standing, the folk's opinion, the player's appearance and the folk's
 * traits; shown to the player before they choose. Pure.
 */
public final class SpeechCheck {
	private static final double MIN = 0.05;
	private static final double MAX = 0.92;

	private SpeechCheck() {
	}

	/**
	 * The folk being talked to, as far as speech goes.
	 *
	 * @param brave    shrugs off threats
	 * @param cautious gives way to them
	 * @param greedy   takes bribes gladly
	 * @param generous above taking them
	 * @param pious    ashamed to
	 * @param guard    an officer of the law (harder to talk round, and bound to report)
	 */
	public record Listener(boolean brave, boolean cautious, boolean greedy, boolean generous, boolean pious, boolean guard) {
		public static final Listener PLAIN = new Listener(false, false, false, false, false, false);
	}

	/**
	 * Persuading a guard to let it go (or halve the fine).
	 *
	 * @param standing the player's standing in the settlement, −100 to 100
	 * @param opinion  the guard's own opinion of the player, −100 to 100
	 * @param charisma how the player comes across, −1 to 1
	 * @param severity the worst offence's severity ({@link Offences#severity})
	 */
	public static double persuade(int standing, int opinion, float charisma, int severity, Listener who) {
		double p = 0.38 + standing / 250.0 + opinion / 400.0 + charisma * 0.2 - severity * 0.045;
		if (who.guard) {
			p -= 0.05;
		}
		return clamp(p);
	}

	/**
	 * Bribing: the more of the stake offered and the greedier the listener, the likelier.
	 *
	 * @param offered bits offered
	 * @param stake   what is at stake (the bounty, or what the crime is worth to the witness)
	 */
	public static double bribe(long offered, long stake, int severity, Listener who) {
		double ratio = stake <= 0 ? 1.5 : Math.min(1.5, offered / (double) stake);
		double p = 0.2 + 0.45 * ratio / 1.5 - severity * 0.04;
		if (who.greedy) {
			p += 0.25;
		}
		if (who.generous) {
			p -= 0.15;
		}
		if (who.pious) {
			p -= 0.1;
		}
		if (who.guard) {
			p -= 0.05;
		}
		return clamp(p);
	}

	/**
	 * Threatening a witness into keeping quiet.
	 *
	 * @param menace how intimidating the player is, 0–1 (armour, a drawn weapon, a violent name)
	 */
	public static double threaten(float menace, Listener who) {
		double p = 0.2 + menace * 0.6;
		if (who.cautious) {
			p += 0.15;
		}
		if (who.brave) {
			p -= 0.3;
		}
		if (who.guard) {
			p -= 0.25;
		}
		return clamp(p);
	}

	/** What a guard asks to look the other way: a share of the bounty. */
	public static long guardBribe(long bounty) {
		return Math.max(5, Math.round(bounty * 0.6));
	}

	/** What a witness asks to forget what they saw. */
	public static long witnessBribe(long fine) {
		return Math.max(3, Math.round(fine * 0.4));
	}

	/** A percentage for the label: "Persuade (62%)". */
	public static int percent(double chance) {
		return (int) Math.round(chance * 100);
	}

	private static double clamp(double p) {
		return Math.clamp(p, MIN, MAX);
	}
}
