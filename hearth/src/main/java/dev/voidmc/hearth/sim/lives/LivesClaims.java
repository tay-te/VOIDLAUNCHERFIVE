package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.settlement.ResidentClaims;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The crafts in the sim's daily assignment (docs/LIVING_WORLD_2.md §4.2, {@link ResidentClaims}): a master keeps to the
 * trade at a workplace of it (one with no other master first, so a second master moves to a new workshop when the planner
 * has built one), a journeyman (and an apprentice the day they are grown) takes the trade first, while the trade's
 * workplaces have room; a master kept from the trade by their thread has no work. Orphans with a grown brother or sister
 * go to live with them. The claims are worked out once a day (on first use after the day's lives) and looked up after.
 */
public final class LivesClaims implements ResidentClaims.Source {
	public static final LivesClaims INSTANCE = new LivesClaims();

	private LivesClaims() {
	}

	@Override
	public ResidentClaims.@Nullable Job job(Settlement settlement, Resident resident) {
		if (!Lives.config().lives().enabled()) {
			return null;
		}
		TownLives town = settlement.data(Lives.TOWN);
		TownLives.Claim c = claims(settlement, town).get(resident.id);
		return c == null ? null : new ResidentClaims.Job(c.job(), c.work());
	}

	@Override
	public @Nullable Integer home(Settlement settlement, Resident resident) {
		if (resident.age != AgeStage.CHILD || !Lives.config().lives().enabled()) {
			return null;
		}
		TownLives town = settlement.data(Lives.TOWN);
		claims(settlement, town);
		Map<UUID, Integer> homes = town.homes;
		return homes == null ? null : homes.get(resident.id);
	}

	/** Works out the day's claims for the town (cached on it until the next day's lives). */
	static Map<UUID, TownLives.Claim> claims(Settlement s, TownLives town) {
		Map<UUID, TownLives.Claim> m = town.claims;
		if (m != null) {
			return m;
		}
		long t0 = System.nanoTime();
		m = claims(s, town, Workplaces.of(s));
		TIMER.add(System.nanoTime() - t0);
		return m;
	}

	private static final dev.voidmc.hearth.sim.LivingWorldStats.Timer TIMER = dev.voidmc.hearth.sim.LivingWorldStats.timer("lives claims");

	/** Works the claims out afresh against these workplaces (and caches them on the town). */
	static Map<UUID, TownLives.Claim> claims(Settlement s, TownLives town, Workplaces wp) {
		Map<UUID, TownLives.Claim> m = new HashMap<>();
		Map<UUID, Integer> homes = new HashMap<>();
		long today = Math.max(town.lastDay, 0);
		int grownAt = Lives.childhoodDays();
		List<Resident> masters = new ArrayList<>();
		List<Resident> learners = new ArrayList<>();
		// the day's crafts named who to look at; after a load (or in a town the lives have not stepped) everyone
		Iterable<Resident> who = s.residents();
		if (town.crafters != null) {
			List<Resident> list = new ArrayList<>(town.crafters.size());
			for (UUID id : town.crafters) {
				Resident r = s.resident(id);
				if (r != null) {
					list.add(r);
				}
			}
			who = list;
		}
		for (UUID id : List.copyOf(town.orphans)) {
			Resident r = s.resident(id);
			if (r == null || r.age != AgeStage.CHILD) {
				town.orphans.remove(id);
			} else {
				orphan(s, r, homes);
			}
		}
		for (Resident r : who) {
			if (Life.peek(r) == null) {
				continue;
			}
			if (r.age == AgeStage.ADULT && Life.idle(r, today)) {
				m.put(r.id, new TownLives.Claim(JobType.NONE, Resident.NO_PLOT));
				continue;
			}
			if (r.age == AgeStage.ADULT && Life.masterOf(r) != null) {
				masters.add(r);
			} else if (r.age == AgeStage.ADULT && Life.journeyman(r) != null) {
				learners.add(r);
			} else if (Life.apprenticeTrade(r) != null && (r.age == AgeStage.ADULT || today + 1 - r.bornDay >= grownAt)) {
				// an apprentice grown (or grown tomorrow): the trade first, before the crafts' next look makes them journeymen
				learners.add(r);
			}
		}
		// seniority: masters by the day they made master, then learners by how long they have been at it
		masters.sort(Comparator.<Resident>comparingLong(Life::masterDay).thenComparing(r -> r.id));
		learners.sort(Comparator.<Resident>comparingLong(r -> Life.journeyman(r) != null ? Life.journeymanSince(r) : Life.apprenticedDay(r))
			.thenComparing(r -> r.id));
		Map<Long, Integer> used = new HashMap<>();
		Map<Integer, Integer> mastersAt = new HashMap<>();
		for (Resident r : masters) {
			JobType trade = Life.masterOf(r);
			Workplaces.Place place = place(wp.places(trade), trade, r.work, used, mastersAt, true);
			if (place != null) {
				m.put(r.id, new TownLives.Claim(trade, place.plot()));
				used.merge(key(place.plot(), trade), 1, Integer::sum);
				mastersAt.merge(place.plot(), 1, Integer::sum);
			}
		}
		for (Resident r : learners) {
			JobType trade = Life.journeyman(r) != null ? Life.journeyman(r) : Life.apprenticeTrade(r);
			if (trade == null) {
				continue;
			}
			int prefer = r.work;
			if (prefer == Resident.NO_PLOT) {
				UUID master = Life.master(r);
				Resident mr = master == null ? null : s.resident(master);
				prefer = mr == null ? Resident.NO_PLOT : mr.work;
			}
			Workplaces.Place place = place(wp.places(trade), trade, prefer, used, mastersAt, false);
			if (place != null) {
				m.put(r.id, new TownLives.Claim(trade, place.plot()));
				used.merge(key(place.plot(), trade), 1, Integer::sum);
			}
		}
		town.claims = m;
		town.homes = homes;
		return m;
	}

	private static long key(int plot, JobType trade) {
		return (long) plot << 8 | trade.ordinal();
	}

	/**
	 * A place of the trade with room: the preferred one, else (for a master) one with no master yet, else the emptiest.
	 */
	private static Workplaces.@Nullable Place place(List<Workplaces.Place> places, JobType trade, int prefer, Map<Long, Integer> used,
		Map<Integer, Integer> mastersAt, boolean master) {
		Workplaces.Place best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Workplaces.Place p : places) {
			int u = used.getOrDefault(key(p.plot(), trade), 0);
			if (u >= p.slots()) {
				continue;
			}
			int score = (p.slots() - u) + (p.plot() == prefer ? 4 : 0);
			if (master && mastersAt.getOrDefault(p.plot(), 0) == 0) {
				score += 8;
			}
			if (score > bestScore) {
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	/** A child whose parents are all gone and who has no grown one at home goes to a grown brother or sister. */
	private static void orphan(Settlement s, Resident child, Map<UUID, Integer> homes) {
		List<UUID> parents = Kin.parents(child);
		if (parents.isEmpty()) {
			return;
		}
		for (UUID p : parents) {
			if (s.resident(p) != null) {
				return;
			}
		}
		Resident sibling = null;
		for (Resident o : s.residents()) {
			if (o.age == AgeStage.CHILD || o.home == Resident.NO_PLOT) {
				continue;
			}
			if (o.home == child.home) {
				return; // someone grown keeps the house
			}
			for (UUID p : Kin.parents(o)) {
				if (parents.contains(p) && (sibling == null || o.bornDay < sibling.bornDay)) {
					sibling = o;
				}
			}
		}
		if (sibling != null) {
			homes.put(child.id, sibling.home);
		}
	}
}
