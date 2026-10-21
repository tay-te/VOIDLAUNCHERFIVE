package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.sim.lives.Life.Bond;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * A town's named characters (docs/LIVING_WORLD_2.md §4.1–4.2): a hamlet keeps one or two, a village three, a town five, a
 * city six, beside its officials. Each is drawn from the roles whose conditions some resident meets (job, age, traits,
 * station, and the role's need: a widow, an apprentice, a newcomer...), one role of each kind a town, and kept until they
 * die or leave; a new one is drawn after. Officials stay officials.
 */
final class Notables {
	private Notables() {
	}

	/** How many named characters a town of this tier and size keeps. */
	static int target(SettlementTier tier, int population, int max) {
		int t = switch (tier) {
			case CAMP -> 0;
			case HAMLET -> population >= 12 ? 2 : 1;
			case VILLAGE -> 3;
			case TOWN -> 5;
			case CITY -> 6;
		};
		return Math.min(t, max);
	}

	/** Forgets the gone, draws one new character if the town is short. */
	static void day(Settlement s, TownLives town, LivesBook book, long day, int childhoodDays, LivesConfig cfg, SplittableRandom random,
		BiConsumer<Resident, String> event) {
		town.notables.removeIf(n -> s.resident(n.resident) == null);
		int want = target(s.tier(), s.population(), cfg.lives().maxNotablesPerTown());
		// a town short of a character looks for one every few days (the search reads the whole town)
		if (town.notables.size() >= want || book.roles.isEmpty() || Math.floorMod(day + s.id(), 3L) != 0 || day < town.searchAfter) {
			return;
		}
		Set<String> used = new HashSet<>();
		Set<UUID> taken = new HashSet<>();
		for (TownLives.Notable n : town.notables) {
			used.add(n.role);
			taken.add(n.resident);
			if (n.partner != null) {
				taken.add(n.partner);
			}
		}
		List<Resident> residents = new ArrayList<>(s.residents());
		residents.sort(Comparator.comparing(r -> r.id));
		List<LivesBook.Role> roles = new ArrayList<>();
		List<List<Resident>> fits = new ArrayList<>();
		int total = 0;
		for (LivesBook.Role role : book.roles.values()) {
			if (used.contains(role.id()) || s.tier().ordinal() < role.minTier().ordinal() || book.thread(role.thread()) == null) {
				continue;
			}
			List<Resident> who = new ArrayList<>();
			for (Resident r : residents) {
				if (!taken.contains(r.id) && fits(s, r, role.who(), day, childhoodDays)) {
					who.add(r);
				}
			}
			if (!who.isEmpty()) {
				roles.add(role);
				fits.add(who);
				total += Math.max(1, role.weight());
			}
		}
		if (roles.isEmpty()) {
			// nobody fits a role the town lacks: look again in a month (folk grow up, arrive, are widowed)
			town.searchAfter = day + 30;
			return;
		}
		int roll = random.nextInt(total);
		int pick = 0;
		for (int i = 0; i < roles.size(); i++) {
			roll -= Math.max(1, roles.get(i).weight());
			if (roll < 0) {
				pick = i;
				break;
			}
		}
		LivesBook.Role role = roles.get(pick);
		List<Resident> who = fits.get(pick);
		// those with a trait the role names are likelier
		List<Resident> keen = new ArrayList<>();
		for (Resident r : who) {
			for (String t : role.who().traits()) {
				if (Kin.has(r, t)) {
					keen.add(r);
					break;
				}
			}
		}
		List<Resident> from = !keen.isEmpty() && random.nextInt(4) != 0 ? keen : who;
		Resident r = from.get(random.nextInt(from.size()));
		LivesBook.Thread thread = book.thread(role.thread());
		TownLives.Notable n = new TownLives.Notable(r.id, role.id(), thread.id(), thread.start(), day);
		Resident partner = partner(s, town, r, role.partner(), taken, day, cfg, random);
		n.partner = partner == null ? null : partner.id;
		town.notables.add(n);
		town.stats.add("notables");
		event.accept(r, "notable");
		Threads.enter(s, town, n, thread, day, cfg, random, event);
	}

	static boolean fits(Settlement s, Resident r, LivesBook.Who who, long day, int childhoodDays) {
		if (!who.ages().isEmpty() && !who.ages().contains(r.age)) {
			return false;
		}
		if (!who.jobs().isEmpty() && !who.jobs().contains(r.job)) {
			return false;
		}
		if (!who.stations().isEmpty() && !who.stations().contains(r.station)) {
			return false;
		}
		if (r.extra.get("social") instanceof net.minecraft.nbt.CompoundTag soc && !soc.getStringOr("office", "").isEmpty()) {
			return false;
		}
		return switch (who.needs()) {
			case "widowed" -> !Kin.married(r) && (Life.flag(r, "widowed") || r.age == AgeStage.ELDER && !Kin.children(r).isEmpty());
			case "apprentice" -> Life.master(r) != null || Life.journeyman(r) != null;
			case "master" -> Life.masterOf(r) != null;
			case "single" -> r.age == AgeStage.ADULT && !Kin.married(r);
			case "newcomer" -> r.age == AgeStage.ADULT && Life.arrived(r) >= day - 40;
			case "young" -> r.age == AgeStage.CHILD && day - r.bornDay >= childhoodDays - 10 || r.age == AgeStage.ADULT && day - r.bornDay < childhoodDays + 30;
			case "parent" -> r.age == AgeStage.ADULT && !Kin.children(r).isEmpty();
			default -> true;
		};
	}

	/** The other person the thread is about. */
	static @Nullable Resident partner(Settlement s, TownLives town, Resident r, String kind, Set<UUID> taken, long day, LivesConfig cfg,
		SplittableRandom random) {
		List<Resident> all = new ArrayList<>(s.residents());
		all.sort(Comparator.comparing(x -> x.id));
		switch (kind) {
			case "apprentice" -> {
				JobType trade = Life.masterOf(r) != null ? Life.masterOf(r) : r.job;
				for (Resident o : all) {
					if (r.id.equals(Life.master(o))) {
						return o;
					}
				}
				for (Resident o : all) {
					if (o != r && Life.journeyman(o) == trade && trade != null) {
						return o;
					}
				}
				return null;
			}
			case "master" -> {
				UUID m = Life.master(r);
				if (m != null) {
					return s.resident(m);
				}
				JobType trade = Life.journeyman(r);
				for (Resident o : all) {
					if (o != r && trade != null && Life.masterOf(o) == trade && o.age == AgeStage.ADULT) {
						return o;
					}
				}
				return null;
			}
			case "sweetheart" -> {
				UUID sw = Life.sweetheart(r);
				if (sw != null) {
					return s.resident(sw);
				}
				// a single grown one of another house, near in age
				Resident best = null;
				long gap = Long.MAX_VALUE;
				for (Resident o : all) {
					if (o == r || o.age != AgeStage.ADULT || o.female == r.female || Kin.married(o) || Life.sweetheart(o) != null || taken.contains(o.id)
						|| Kin.close(o, r) || o.home == r.home && r.home != Resident.NO_PLOT) {
						continue;
					}
					long g = Math.abs(o.bornDay - r.bornDay) + random.nextInt(20);
					if (g < gap) {
						gap = g;
						best = o;
					}
				}
				if (best != null) {
					Life.touch(r, best.id, 70, Bond.SWEETHEART, day, cfg.relations());
					Life.touch(best, r.id, 70, Bond.SWEETHEART, day, cfg.relations());
					town.stats.add("sweethearts");
				}
				return best;
			}
			case "creditor" -> {
				Resident best = null;
				for (Resident o : all) {
					if (o == r || o.age != AgeStage.ADULT || o.home == r.home && r.home != Resident.NO_PLOT) {
						continue;
					}
					boolean lender = o.job == JobType.MERCHANT || o.job == JobType.TRADER || o.job == JobType.STEWARD || o.job == JobType.LORD
						|| o.job == JobType.MINTER;
					if (lender && (best == null || o.station.ordinal() > best.station.ordinal())) {
						best = o;
					}
				}
				return best;
			}
			case "kin" -> {
				for (UUID p : Kin.parents(r)) {
					Resident o = s.resident(p);
					if (o != null) {
						return o;
					}
				}
				UUID sp = Kin.spouse(r);
				return sp == null ? null : s.resident(sp);
			}
			case "household" -> {
				List<Resident> heads = new ArrayList<>(Feuds.heads(s).values());
				heads.removeIf(h -> h.home == r.home || taken.contains(h.id));
				heads.sort(Comparator.comparing(x -> x.id));
				return heads.isEmpty() ? null : heads.get(random.nextInt(heads.size()));
			}
			default -> {
				return null;
			}
		}
	}
}
