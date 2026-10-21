package dev.voidmc.hearth.social.life;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.social.FolkFacts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Who marries whom: two unmarried grown residents of the settlement, not close kin and not already promised, of an
 * age with each other. Sweethearts (docs/LIVING_WORLD_2.md §4.1, the lives' courtships) are married first. Pure (the
 * caller supplies the random), so it is tested without a server.
 */
public final class Matchmaker {
	/** How many singles a match is sought among at once (a seeded draw from all of them). */
	static final int MET = 8;
	/** Each resident's sweetheart, if any (the lives register it; none by default). */
	private static volatile Function<Resident, @Nullable UUID> sweethearts = r -> null;

	private Matchmaker() {
	}

	/** Who courts whom, so the banns go to sweethearts first (lane B2's lives register it at init). */
	public static void sweethearts(Function<Resident, @Nullable UUID> lookup) {
		sweethearts = lookup;
	}

	public record Match(Resident a, Resident b) {
	}

	/**
	 * @param promised     residents already engaged (a wedding announced)
	 * @param sameSex      whether two men or two women may marry
	 * @param sameSexShare when allowed, roughly this share of matches
	 */
	public static @Nullable Match find(Collection<Resident> residents, Set<UUID> promised, boolean sameSex, double sameSexShare, RandomGenerator random) {
		List<Resident> singles = new ArrayList<>();
		for (Resident r : residents) {
			if (r.age == AgeStage.ADULT && !promised.contains(r.id) && FolkFacts.spouse(r) == null) {
				singles.add(r);
			}
		}
		if (singles.size() < 2) {
			return null;
		}
		Match courting = sweethearts(singles, random);
		if (courting != null) {
			return courting;
		}
		// a stable order first, then a seeded shuffle, so the same random gives the same match
		singles.sort(Comparator.comparing(r -> r.id));
		for (int i = singles.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			Resident t = singles.get(i);
			singles.set(i, singles.get(j));
			singles.set(j, t);
		}
		boolean wantSameSex = sameSex && random.nextDouble() < sameSexShare;
		// among a handful met this season, the two nearest in age who may marry
		List<Resident> met = singles.subList(0, Math.min(MET, singles.size()));
		Match best = null;
		long bestGap = Long.MAX_VALUE;
		for (int i = 0; i < met.size(); i++) {
			for (int j = i + 1; j < met.size(); j++) {
				Resident a = met.get(i);
				Resident b = met.get(j);
				if ((a.female == b.female) != wantSameSex || FolkFacts.kin(a, b)) {
					continue;
				}
				long gap = Math.abs(a.bornDay - b.bornDay);
				if (gap < bestGap) {
					bestGap = gap;
					best = new Match(a, b);
				}
			}
		}
		return best;
	}

	/** Two of these singles who are each other's sweethearts (a seeded pick among the pairs), or null. */
	static @Nullable Match sweethearts(List<Resident> singles, RandomGenerator random) {
		Function<Resident, @Nullable UUID> lookup = sweethearts;
		java.util.Map<UUID, Resident> byId = new java.util.HashMap<>();
		for (Resident r : singles) {
			byId.put(r.id, r);
		}
		List<Match> pairs = new ArrayList<>();
		for (Resident r : singles) {
			UUID o = lookup.apply(r);
			Resident other = o == null ? null : byId.get(o);
			if (other != null && r.id.compareTo(other.id) < 0 && r.id.equals(lookup.apply(other)) && !FolkFacts.kin(r, other)) {
				pairs.add(new Match(r, other));
			}
		}
		if (pairs.isEmpty()) {
			return null;
		}
		pairs.sort(Comparator.comparing(m -> m.a().id));
		return pairs.get(random.nextInt(pairs.size()));
	}
}
