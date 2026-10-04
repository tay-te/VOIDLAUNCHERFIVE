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
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Who marries whom: two unmarried grown residents of the settlement, not close kin and not already promised, of an
 * age with each other. Pure (the caller supplies the random), so it is tested without a server.
 */
public final class Matchmaker {
	private Matchmaker() {
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
		// a stable order first, then a seeded shuffle, so the same random gives the same match
		singles.sort(Comparator.comparing(r -> r.id));
		for (int i = singles.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			Resident t = singles.get(i);
			singles.set(i, singles.get(j));
			singles.set(j, t);
		}
		boolean wantSameSex = sameSex && random.nextDouble() < sameSexShare;
		for (Resident a : singles) {
			Resident best = null;
			long bestGap = Long.MAX_VALUE;
			for (Resident b : singles) {
				if (b == a || (a.female == b.female) != wantSameSex || FolkFacts.kin(a, b)) {
					continue;
				}
				long gap = Math.abs(a.bornDay - b.bornDay);
				if (gap < bestGap) {
					bestGap = gap;
					best = b;
				}
			}
			if (best != null) {
				return new Match(a, best);
			}
		}
		return null;
	}
}
