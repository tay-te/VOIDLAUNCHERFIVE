package dev.voidmc.hearth.standing.speech;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.standing.logic.Curves;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The odds of a speech check, as a pure function of who is talking to whom: the player's standing in the town, the
 * resident's opinion of them, how the player comes across (charisma and menace), the resident's traits, mood, station
 * and trade, how hard the ask is and — for bribes — how the coin offered compares with what they expect. The greedy take
 * bribes, the brave shrug off threats, the grumpy are hard to flatter, a lord expects more than a labourer.
 *
 * <p>Every factor is kept with its contribution, so screens can show why the odds are what they are.
 */
public final class SpeechOdds {
	private SpeechOdds() {
	}

	/**
	 * Who is being talked to and how.
	 *
	 * @param standing   the player's standing in the town, −100…100
	 * @param opinion    the resident's opinion of the player, −100…100
	 * @param charisma   the player's charisma, −1…1
	 * @param menace     the player's menace, 0…1
	 * @param traits     the resident's traits by name (npc module: {@code Resident.extra("npc").traits})
	 * @param mood       the resident's mood, −100…100
	 * @param difficulty how hard the ask is: 0 an easy favour … 0.5 asking them to break the law
	 * @param bribeRatio the coin offered over what they expect (bribes only)
	 * @param repeats    times the player tried this on them recently (each try makes the next harder)
	 * @param speech     the player's Speech skill, 0–20 (a character mod's, through {@code PlayerSkills}; 0 without one)
	 * @param presence   the player's Presence attribute, 1–20 (3, the starting value, adds nothing)
	 */
	public record Inputs(int standing, int opinion, double charisma, double menace, Set<String> traits, int mood, Station station, JobType job,
		double difficulty, double bribeRatio, int repeats, int speech, int presence) {
		/** Without a character's skills: Speech 0 and Presence 3, which leave the odds as Hearth alone sets them. */
		public Inputs(int standing, int opinion, double charisma, double menace, Set<String> traits, int mood, Station station, JobType job,
			double difficulty, double bribeRatio, int repeats) {
			this(standing, opinion, charisma, menace, traits, mood, station, job, difficulty, bribeRatio, repeats, 0, 3);
		}
	}

	/**
	 * What a character's Speech level and Presence add to a check of this kind: up to +0.30 from Speech 20 for persuading
	 * and flattering (+0.16 for threats and bribes, where the coin and the steel speak louder), and a little per point of
	 * Presence above 3 (most for threats). Speech 0 and Presence 3 add nothing.
	 */
	public static void skills(List<Factor> f, SpeechKind kind, int speech, int presence) {
		int level = Math.clamp(speech, 0, 20);
		int above = Math.clamp(presence, 1, 20) - 3;
		double perLevel = switch (kind) {
			case PERSUADE, FLATTER -> 0.015;
			case INTIMIDATE, BRIBE -> 0.008;
		};
		double perPoint = switch (kind) {
			case PERSUADE -> 0.010;
			case INTIMIDATE -> 0.015;
			case FLATTER -> 0.012;
			case BRIBE -> 0.0;
		};
		add(f, "speech", level * perLevel);
		add(f, "presence", above * perPoint);
	}

	/** One reason the odds are what they are: a translation key under {@code hearth.standing.factor.} and its share. */
	public record Factor(String key, double delta) {
	}

	public record Odds(SpeechKind kind, double chance, List<Factor> factors) {
		public int percent() {
			return (int) Math.round(this.chance * 100);
		}
	}

	public static Odds of(SpeechKind kind, Inputs in, double min, double max) {
		List<Factor> f = new ArrayList<>();
		double base = switch (kind) {
			case PERSUADE -> 0.40;
			case INTIMIDATE -> 0.25;
			case BRIBE -> 0.30;
			case FLATTER -> 0.45;
		};
		switch (kind) {
			case PERSUADE -> {
				add(f, "standing", in.standing * 0.0015);
				add(f, "opinion", in.opinion * 0.003);
				add(f, "charisma", in.charisma * 0.25);
				add(f, "menace", -in.menace * 0.10);
				add(f, "mood", in.mood * 0.001);
				trait(f, in, "sociable", 0.05);
				trait(f, in, "cheerful", 0.05);
				trait(f, in, "curious", 0.04);
				trait(f, in, "shy", -0.03);
				trait(f, in, "grumpy", -0.08);
				trait(f, in, "cautious", -0.04);
				add(f, "station", switch (in.station) {
					case OFFICIAL -> -0.08;
					case GENTRY -> -0.10;
					case LORD -> -0.15;
					case CLERGY -> -0.03;
					default -> 0;
				});
			}
			case INTIMIDATE -> {
				add(f, "menace", in.menace * 0.55);
				// An outlaw's threat is believed; a hero's sounds hollow.
				add(f, "standing", -in.standing * 0.001);
				add(f, "charisma", -Math.max(0, in.charisma) * 0.05);
				trait(f, in, "brave", -0.25);
				trait(f, in, "cautious", 0.15);
				trait(f, in, "shy", 0.10);
				trait(f, in, "grumpy", -0.05);
				add(f, "station", switch (in.station) {
					case POOR -> 0.05;
					case OFFICIAL -> -0.20;
					case GENTRY -> -0.15;
					case LORD -> -0.30;
					default -> 0;
				});
				if (in.job == JobType.GUARD || in.job == JobType.JAILER) {
					add(f, "job", -0.20);
				}
			}
			case BRIBE -> {
				double ratio = Math.max(0, in.bribeRatio);
				add(f, "coin", Math.min(0.6, 0.35 * Math.log1p(ratio) / Math.log(2)) - (ratio < 0.25 ? 0.15 : 0));
				add(f, "opinion", in.opinion * 0.0015);
				trait(f, in, "greedy", 0.25);
				trait(f, in, "generous", -0.05);
				trait(f, in, "pious", -0.15);
				trait(f, in, "cautious", -0.05);
				add(f, "station", switch (in.station) {
					case POOR -> 0.10;
					case CLERGY -> -0.15;
					case OFFICIAL -> -0.05;
					case LORD -> -0.20;
					default -> 0;
				});
			}
			case FLATTER -> {
				add(f, "charisma", in.charisma * 0.30);
				add(f, "opinion", in.opinion * 0.002);
				add(f, "mood", in.mood * 0.001);
				trait(f, in, "cheerful", 0.10);
				trait(f, in, "sociable", 0.05);
				trait(f, in, "dreamy", 0.05);
				trait(f, in, "shy", 0.03);
				trait(f, in, "grumpy", -0.15);
				add(f, "station", in.station == Station.GENTRY || in.station == Station.LORD ? 0.05 : 0);
			}
		}
		skills(f, kind, in.speech, in.presence);
		add(f, "difficulty", -in.difficulty);
		add(f, "repeats", -0.2 * Math.max(0, in.repeats));
		double chance = base;
		for (Factor x : f) {
			chance += x.delta;
		}
		return new Odds(kind, Curves.clamp(chance, min, max), List.copyOf(f));
	}

	private static void add(List<Factor> f, String key, double delta) {
		if (Math.abs(delta) >= 0.005) {
			f.add(new Factor(key, delta));
		}
	}

	private static void trait(List<Factor> f, Inputs in, String trait, double delta) {
		if (in.traits.contains(trait)) {
			f.add(new Factor("trait." + trait, delta));
		}
	}

	/** What a resident expects to be paid to look the other way, in bits, before the multiplier and difficulty. */
	public static long expectedBribe(Station station) {
		return switch (station) {
			case POOR -> 10;
			case PEASANT -> 15;
			case ARTISAN -> 25;
			case CLERGY, SCHOLAR -> 35;
			case MERCHANT -> 50;
			case OFFICIAL -> 60;
			case GENTRY -> 150;
			case LORD -> 500;
		};
	}
}
