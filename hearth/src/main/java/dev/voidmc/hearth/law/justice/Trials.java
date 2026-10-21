package dev.voidmc.hearth.law.justice;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Trials of residents (docs/LIVING_WORLD_2.md §3.2, §3.3): petty crimes among the townsfolk arise from the town's state —
 * poverty, feuds, the crime about — one or two a fortnight in a poor town, none in a contented one, and a rare killing
 * where feuds run deep. The accused waits for the lord's court day; the judge's mercy and the evidence decide, unless a
 * player testifies (a speech check whose odds weigh what they really saw). Pure: the town's social events
 * ({@code social/events/TownEvents}) keep the defendants in their book and stage the trial.
 */
public final class Trials {
	/** The offences residents are tried for. */
	public static final List<String> OFFENCES = List.of("theft", "brawling", "poaching", "debt", "murder");

	private Trials() {
	}

	/**
	 * A resident awaiting trial: what they did, when, the players who were in town to see it, and whom it was done to.
	 */
	public record Defendant(UUID resident, String offence, long day, List<UUID> witnesses, @Nullable UUID victim) {
		public static final Codec<Defendant> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("resident").forGetter(Defendant::resident),
			Codec.STRING.fieldOf("offence").forGetter(Defendant::offence),
			Codec.LONG.fieldOf("day").forGetter(Defendant::day),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("witnesses", List.of()).forGetter(Defendant::witnesses),
			UUIDUtil.STRING_CODEC.optionalFieldOf("victim").forGetter(d -> java.util.Optional.ofNullable(d.victim))
		).apply(i, (r, o, d, w, v) -> new Defendant(r, o, d, w, v.orElse(null))));

		public boolean sawIt(UUID player) {
			return this.witnesses.contains(player);
		}
	}

	/**
	 * A day's chance of a petty crime in a town: none at 0.6 prosperity and up, about one in eight days at 0.2 and down
	 * (one or two a fortnight).
	 */
	public static double pettyOdds(double prosperity, int feuds, int recentCrimes) {
		double poverty = Mth.clamp((0.6 - prosperity) / 0.4, 0, 1);
		if (poverty <= 0 && feuds == 0) {
			return 0;
		}
		return Math.min(0.3, 0.12 * Math.pow(poverty, 1.2) + 0.015 * Math.min(4, feuds) + 0.01 * Math.min(5, recentCrimes) * poverty);
	}

	/** A day's chance of a killing among residents: rare, and only where feuds run (or poverty is bitter). */
	public static double murderOdds(double prosperity, int feuds, int population) {
		if (population < 20) {
			return 0;
		}
		return 0.0002 * Math.min(4, feuds) + (prosperity < 0.2 ? 0.0002 : 0);
	}

	/**
	 * Whether a crime is done today, and by whom: an adult of the town who holds no office (the poorer likelier), the
	 * victim another household's. Null on most days.
	 *
	 * @param officials residents holding office (never accused)
	 * @param waiting   residents already awaiting trial
	 */
	public static @Nullable Defendant roll(long town, long day, java.util.Collection<Resident> residents, double prosperity, int feuds, int recentCrimes,
		Set<UUID> officials, Set<UUID> waiting, List<UUID> witnesses) {
		SplittableRandom r = new SplittableRandom(town * 0x9E3779B97F4A7C15L + day * 0x2545F4914F6CDD1DL);
		double roll = r.nextDouble();
		boolean murder = roll < murderOdds(prosperity, feuds, residents.size());
		if (!murder && roll >= pettyOdds(prosperity, feuds, recentCrimes)) {
			return null;
		}
		List<Resident> able = new ArrayList<>();
		for (Resident res : residents) {
			if (res.age == AgeStage.ADULT && !officials.contains(res.id) && !waiting.contains(res.id) && res.job != JobType.GUARD && res.job != JobType.JUDGE
				&& res.job != JobType.LORD && res.job != JobType.PRIEST) {
				able.add(res);
			}
		}
		if (able.size() < 2) {
			return null;
		}
		able.sort(Comparator.comparing(res -> res.id));
		// the poor are tempted more: a second draw among the poorer half
		Resident accused = able.get(r.nextInt(able.size()));
		if (!poor(accused) && r.nextDouble() < 0.6) {
			Resident other = able.get(r.nextInt(able.size()));
			if (poor(other)) {
				accused = other;
			}
		}
		Resident victim = null;
		for (int tries = 0; tries < 8 && victim == null; tries++) {
			Resident v = able.get(r.nextInt(able.size()));
			if (!v.id.equals(accused.id) && v.home != accused.home) {
				victim = v;
			}
		}
		String offence;
		if (murder) {
			offence = "murder";
		} else {
			double o = r.nextDouble();
			offence = o < 0.4 ? "theft" : o < 0.7 ? "brawling" : o < 0.85 ? "poaching" : "debt";
		}
		return new Defendant(accused.id, offence, day, List.copyOf(witnesses), victim == null ? null : victim.id);
	}

	public static boolean poor(Resident r) {
		return r.station == Station.POOR || r.station == Station.PEASANT;
	}

	/** What an offence costs in fines, in bits. */
	public static long fine(String offence) {
		return switch (offence) {
			case "theft" -> 15;
			case "brawling" -> 8;
			case "poaching" -> 12;
			case "debt" -> 20;
			default -> 30;
		};
	}

	/** How grave an offence is, on the law's scale ({@link Offences#severity}). */
	public static int severity(String offence) {
		return switch (offence) {
			case "brawling" -> 1;
			case "poaching", "debt" -> 2;
			case "theft" -> 3;
			case "murder" -> 10;
			default -> 2;
		};
	}

	/** The evidence against the accused, 0.3–0.9: how clear the case is, from the day's seed. */
	public static double evidence(long seed) {
		return 0.3 + 0.6 * new SplittableRandom(seed).nextDouble();
	}

	/** A judge's mercy, −0.1 to 0.15: kind and generous judges lean to it, grumpy ones from it. */
	public static double leniency(Set<String> traits, long seed) {
		double l = (new SplittableRandom(seed * 31).nextDouble() - 0.5) * 0.1;
		if (traits.contains("kind") || traits.contains("generous") || traits.contains("pious")) {
			l += 0.08;
		}
		if (traits.contains("grumpy") || traits.contains("greedy")) {
			l -= 0.06;
		}
		return Mth.clamp(l, -0.1, 0.15);
	}

	/**
	 * The odds a player's testimony is believed: their standing, the judge's opinion of them and how they come across —
	 * and above all whether they were there to see it (a witness is believed far more readily than a stranger with a tale).
	 */
	public static double testimonyOdds(int standing, int opinion, float charisma, boolean witnessed, String offence) {
		double p = SpeechCheck.persuade(standing, opinion, charisma, severity(offence), SpeechCheck.Listener.PLAIN) + (witnessed ? 0.3 : -0.2);
		return Mth.clamp(p, 0.05, 0.92);
	}
}
