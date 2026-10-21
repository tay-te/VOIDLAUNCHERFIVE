package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.lives.Life.Bond;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * Friendships, courtships and rivalries (docs/LIVING_WORLD_2.md §4.1–4.2). Each grown resident's day brings at most
 * three contacts — a workmate, the neighbour, the evening's company (a seeded draw that favours old friends and the
 * sociable) — and each contact warms both of them; without contact a friendship fades (lazily, by the last day they
 * met). Two unmarried adults who have grown fond become sweethearts (the weddings then prefer them), and a third who
 * was fond of one of them may take it badly. Pure apart from the residents it changes.
 */
final class Bonds {
	/** Residents take their contacts every {@code STRIDE} days, with that many days' gains. */
	static final int STRIDE = 4;
	static final int WORKMATE = 4 * STRIDE;
	static final int NEIGHBOUR = 3 * STRIDE;
	static final int EVENING = 5 * STRIDE;

	private Bonds() {
	}

	/** Who sweethearts may be: whether two men or two women may court (the social module's {@code same_sex}). */
	interface Rules {
		boolean sameSex();
	}

	static void day(Settlement s, Workplaces wp, long day, LivesConfig cfg, Rules rules, SplittableRandom random, BiConsumer<Resident, String> event,
		TownLives town) {
		LivesConfig.Relations rc = cfg.relations();
		Groups g = town.groups;
		if (g == null || g.population != s.population() || day - g.day >= 7 || day < g.day) {
			g = Groups.of(s, day);
			town.groups = g;
		}
		int n = g.byWork.length;
		if (n < 2) {
			return;
		}
		Resident[] people = g.byWork;
		Resident[] byWork = g.byWork;
		Resident[] byHome = g.byHome;
		int[] runStart = g.runStart;
		int[] runEnd = g.runEnd;
		it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap firstAtHome = g.firstAtHome;
		int updates = rc.updatesPerDay();
		// a quarter of the town takes its contacts each day (each resident every fourth day, the gains four days' worth),
		// so the pass costs a quarter as much and comes out the same over a week
		int slice = (int) Math.floorMod(day, (long) STRIDE);
		for (int i = 0; i < n && updates > 0; i++) {
			if (i % STRIDE != slice) {
				continue;
			}
			Resident r = byWork[i];
			if (s.resident(r.id) != r) {
				continue; // gone since the town was grouped
			}
			int nature = g.nature[i];
			boolean sociable = (nature & Kin.SOCIABLE) != 0;
			boolean shy = (nature & Kin.SHY) != 0;
			int done = 0;
			// a workmate
			int run = runEnd[i] - runStart[i];
			if (r.work != Resident.NO_PLOT && run > 1 && random.nextDouble() < 0.5) {
				int j = runStart[i] + random.nextInt(run - 1);
				if (j >= i) {
					j++;
				}
				meet(r, byWork[j], WORKMATE, day, rc);
				done++;
			}
			// the neighbour
			if (done < updates && r.home != Resident.NO_PLOT && random.nextDouble() < 0.35) {
				int nh = wp.neighbour(r.home);
				int k = nh == Resident.NO_PLOT ? -1 : firstAtHome.get(nh);
				if (k >= 0) {
					int count = 0;
					while (k + count < n && byHome[k + count].home == nh) {
						count++;
					}
					// (a week-old grouping: someone may have moved house since)
					if (count > 0) {
						meet(r, byHome[k + random.nextInt(count)], NEIGHBOUR, day, rc);
						done++;
					}
				}
			}
			// the evening's company: an old friend half the time, else anyone about
			double evening = 0.4 * (sociable ? 1.5 : 1) * (shy ? 0.5 : 1);
			if (done < updates && random.nextDouble() < evening) {
				Resident other = null;
				long[] rel = Life.rel(r);
				if (rel != null && rel.length >= 3 && random.nextBoolean()) {
					int pick = random.nextInt(rel.length / 3) * 3;
					other = s.resident(new UUID(rel[pick], rel[pick + 1]));
				}
				if (other == null || other.age == AgeStage.CHILD) {
					other = byWork[random.nextInt(n)];
				}
				if (other != r) {
					int gain = EVENING;
					int at = Life.find(rel, other.id);
					if (at >= 0) {
						Bond b = Life.bond(rel[at + 2]);
						if (b == Bond.FEUD || b == Bond.RIVAL) {
							gain = STRIDE; // the same tavern, not the same table
						}
					}
					meet(r, other, gain, day, rc);
				}
			}
		}
		courtships(s, people, n, day, cfg, rules, random, event, town);
	}

	/**
	 * The grown folk grouped by workplace (for the workmates) and by home (for the neighbours), kept a week (or until the
	 * town's numbers change): jobs and homes change slowly, and grouping is the dear part of the pass. In the town's
	 * saved order, so ties keep it.
	 */
	static final class Groups {
		final long day;
		final int population;
		final Resident[] byWork;
		final Resident[] byHome;
		final int[] runStart;
		final int[] runEnd;
		final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap firstAtHome;
		/** Each one's nature ({@link Kin#nature}), in the work order. */
		final int[] nature;

		private Groups(long day, int population, Resident[] byWork, Resident[] byHome, int[] runStart, int[] runEnd,
			it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap firstAtHome, int[] nature) {
			this.day = day;
			this.population = population;
			this.byWork = byWork;
			this.byHome = byHome;
			this.runStart = runStart;
			this.runEnd = runEnd;
			this.firstAtHome = firstAtHome;
			this.nature = nature;
		}

		static Groups of(Settlement s, long day) {
			Resident[] people = new Resident[s.population()];
			int n = 0;
			for (Resident r : s.residents()) {
				if (r.age != AgeStage.CHILD && n < people.length) {
					people[n++] = r;
				}
			}
			Resident[] byWork = new Resident[n];
			long[] keys = new long[n];
			for (int i = 0; i < n; i++) {
				keys[i] = (long) (people[i].work + 1) << 32 | i;
			}
			Arrays.sort(keys);
			for (int i = 0; i < n; i++) {
				byWork[i] = people[(int) keys[i]];
			}
			int[] runStart = new int[n];
			int[] runEnd = new int[n];
			for (int i = 0; i < n; ) {
				int j = i;
				while (j < n && byWork[j].work == byWork[i].work) {
					j++;
				}
				for (int k = i; k < j; k++) {
					runStart[k] = i;
					runEnd[k] = j;
				}
				i = j;
			}
			it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap firstAtHome = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(n);
			firstAtHome.defaultReturnValue(-1);
			Resident[] byHome = new Resident[n];
			for (int i = 0; i < n; i++) {
				keys[i] = (long) (byWork[i].home + 1) << 32 | i;
			}
			Arrays.sort(keys);
			for (int i = 0; i < n; i++) {
				byHome[i] = byWork[(int) keys[i]];
			}
			for (int i = 0; i < n; i++) {
				if (byHome[i].home != Resident.NO_PLOT && !firstAtHome.containsKey(byHome[i].home)) {
					firstAtHome.put(byHome[i].home, i);
				}
			}
			int[] nature = new int[n];
			for (int i = 0; i < n; i++) {
				nature[i] = Kin.nature(byWork[i]);
			}
			return new Groups(day, s.population(), byWork, byHome, runStart, runEnd, firstAtHome, nature);
		}
	}

	/** Two residents spent time together: both warm to each other (quarrels thaw a little). */
	static void meet(Resident a, Resident b, int gain, long day, LivesConfig.Relations rc) {
		if (a == b || b.age == AgeStage.CHILD) {
			return;
		}
		Life.touch(a, b.id, gain, null, day, rc);
		Life.touch(b, a.id, gain, null, day, rc);
	}

	/** Sweethearts from fond friends; weddings of sweethearts counted; the jilted. */
	private static void courtships(Settlement s, Resident[] people, int n, long day, LivesConfig cfg, Rules rules, SplittableRandom random,
		BiConsumer<Resident, String> event, TownLives town) {
		LivesConfig.Relations rc = cfg.relations();
		int slice = (int) Math.floorMod(day + 1, (long) STRIDE);
		for (int i = 0; i < n; i++) {
			Resident r = people[i];
			if (r.age != AgeStage.ADULT || i % STRIDE != slice || s.resident(r.id) != r) {
				continue;
			}
			boolean married = Kin.married(r);
			boolean counted = Life.flag(r, "wed");
			if (married && counted) {
				continue;
			}
			if (counted) {
				Life.flag(r, "wed", false); // widowed: free to court again
			}
			UUID spouse = married ? Kin.spouse(r) : null;
			if (spouse != null) {
				if (!counted) {
					Life.flag(r, "wed", true);
					Life.Relation sh = Life.relation(r, spouse, day, rc);
					// counted once a couple: by the one of the two whose id sorts first
					if (r.id.compareTo(spouse) < 0) {
						town.stats.add("weddings");
						if (sh != null && sh.bond() == Bond.SWEETHEART) {
							town.stats.add("sweetheart_weddings");
							town.news("wed", day, r.id, List.of(r.givenName, nameOf(s, spouse)));
						}
					}
					// a sweetheart other than the spouse is left behind
					UUID other = Life.sweetheart(r);
					if (other != null && !other.equals(spouse)) {
						Resident jilted = s.resident(other);
						Life.touch(r, other, -30, Bond.FRIEND, day, rc);
						if (jilted != null) {
							Life.touch(jilted, r.id, -40, Bond.RIVAL, day, rc);
							Resident sp = s.resident(spouse);
							if (sp != null) {
								Life.touch(jilted, sp.id, -40, Bond.RIVAL, day, rc);
								Feuds.maybe(s, town, jilted, sp, "love", day, cfg, random, event);
							}
						}
					}
					Life.touch(r, spouse, 100, Bond.FRIEND, day, rc);
				}
				continue;
			}
			// the day's chance first (four days' worth, a quarter of the town a day): the search below is the dear part
			if (random.nextDouble() >= 1 - Math.pow(1 - rc.sweetheartChance(), STRIDE)) {
				continue;
			}
			long[] a = Life.rel(r);
			if (a == null || Life.sweetheart(r) != null) {
				continue;
			}
			// the fondest unmarried friend of an age to court
			Resident best = null;
			int bestS = rc.sweetheartAt() - 1;
			for (int k = 0; k + 2 < a.length; k += 3) {
				if (Life.bond(a[k + 2]) != Bond.FRIEND) {
					continue;
				}
				int st = Life.effective(a[k + 2], day, rc);
				if (st <= bestS) {
					continue;
				}
				Resident o = s.resident(new UUID(a[k], a[k + 1]));
				if (o == null || o.age != AgeStage.ADULT || Kin.married(o) || Life.sweetheart(o) != null || Kin.close(r, o)) {
					continue;
				}
				if (o.female == r.female && !(rules.sameSex() && sameSexPair(r, o, rc.sameSexShare()))) {
					continue;
				}
				if (Math.abs(o.bornDay - r.bornDay) > 120) {
					continue;
				}
				best = o;
				bestS = st;
			}
			if (best == null) {
				continue;
			}
			Life.touch(r, best.id, 10, Bond.SWEETHEART, day, rc);
			Life.touch(best, r.id, 10, Bond.SWEETHEART, day, rc);
			town.stats.add("sweethearts");
			town.news("sweethearts", day, r.id, List.of(r.givenName, best.givenName));
			event.accept(r, "sweethearts");
			jilted(s, r, best, day, cfg, random, event, town);
			jilted(s, best, r, day, cfg, random, event, town);
		}
	}

	/** Whether this pair of the same sex is one of the share who court (seeded by the pair, so it does not flicker). */
	static boolean sameSexPair(Resident a, Resident b, double share) {
		long h = a.id.getLeastSignificantBits() ^ b.id.getLeastSignificantBits() ^ a.id.getMostSignificantBits() ^ b.id.getMostSignificantBits();
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53 < share;
	}

	/** Someone fond of {@code lover} who is not {@code beloved} may take it badly. */
	private static void jilted(Settlement s, Resident lover, Resident beloved, long day, LivesConfig cfg, SplittableRandom random,
		BiConsumer<Resident, String> event, TownLives town) {
		LivesConfig.Relations rc = cfg.relations();
		Resident rival = null;
		int bestS = rc.sweetheartAt() - 10;
		for (Life.Relation rel : Life.relations(lover, day, rc)) {
			if (rel.bond() != Bond.FRIEND || rel.strength() <= bestS || rel.other().equals(beloved.id)) {
				continue;
			}
			Resident o = s.resident(rel.other());
			if (o == null || o.age != AgeStage.ADULT || Kin.married(o) || Life.sweetheart(o) != null || Kin.close(o, lover)) {
				continue;
			}
			if (o.female == lover.female) {
				continue;
			}
			rival = o;
			bestS = rel.strength();
		}
		if (rival == null || random.nextDouble() >= rc.rivalChance()) {
			return;
		}
		Life.touch(rival, beloved.id, -45, Bond.RIVAL, day, rc);
		Life.touch(beloved, rival.id, -25, Bond.RIVAL, day, rc);
		town.stats.add("rivals");
		event.accept(rival, "jilted");
		Feuds.maybe(s, town, rival, beloved, "love", day, cfg, random, event);
	}

	/** Every sweetheart pair of the town, once each. */
	static List<UUID[]> sweethearts(Settlement s) {
		List<UUID[]> out = new ArrayList<>();
		for (Resident r : s.residents()) {
			UUID o = Life.sweetheart(r);
			if (o != null && r.id.compareTo(o) < 0) {
				Resident other = s.resident(o);
				if (other != null && r.id.equals(Life.sweetheart(other))) {
					out.add(new UUID[] {r.id, o});
				}
			}
		}
		return out;
	}

	static String nameOf(Settlement s, @Nullable UUID id) {
		Resident r = id == null ? null : s.resident(id);
		return r == null ? "" : r.givenName;
	}
}
