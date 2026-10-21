package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Apprentices, journeymen and masters (docs/LIVING_WORLD_2.md §4.1–4.2): an older child is apprenticed to a master (the
 * parent, if the parent masters a workshop; else a master with room), grows up a journeyman of that trade, and after
 * {@code master_days} at it makes the masterpiece and is a master; a town's standing workshops get their masters the
 * first day the lives see it. Pure apart from the residents it changes; the claims ({@link LivesClaims}) see that the
 * sim's job assignment honours it.
 */
final class Crafts {
	private Crafts() {
	}

	/**
	 * The senior hand of each trade at each workplace with no master there is its master (the first day the lives see a
	 * town, and once a week after, for workshops newly at work). Journeymen serve their time first.
	 */
	static void adopt(Settlement s, Workplaces wp, long day, LivesConfig.Crafts cfg) {
		Map<Long, Resident> senior = new HashMap<>();
		java.util.Set<Long> mastered = new java.util.HashSet<>();
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.ADULT || r.work == Resident.NO_PLOT && r.job != JobType.BUILDER || !Workplaces.CRAFTS.contains(r.job)) {
				continue;
			}
			long key = (long) r.work << 8 | r.job.ordinal();
			if (Life.masterOf(r) == r.job) {
				mastered.add(key);
				continue;
			}
			if (Life.journeyman(r) != null || Life.idle(r, day)) {
				continue;
			}
			Resident cur = senior.get(key);
			if (cur == null || r.bornDay < cur.bornDay || r.bornDay == cur.bornDay && r.id.compareTo(cur.id) < 0) {
				senior.put(key, r);
			}
		}
		for (Map.Entry<Long, Resident> e : senior.entrySet()) {
			Resident r = e.getValue();
			if (!mastered.contains(e.getKey()) && Life.masterOf(r) == null) {
				// made master some while before the lives began, so their own apprentices' story is not all new
				Life.master(r, r.job, day - cfg.masterDays());
			}
		}
	}

	/**
	 * The day's crafts: masters gone, apprentices taken on, grown apprentices become journeymen, journeymen who have
	 * served their time make master. {@code event} hears each change (resident, kind).
	 */
	static void day(Settlement s, TownLives town, Workplaces wp, long day, int childhoodDays, LivesConfig cfg, SplittableRandom random,
		BiConsumer<Resident, String> event) {
		LivesConfig.Crafts c = cfg.crafts();
		List<Resident> masters = new ArrayList<>();
		Map<UUID, Integer> taken = new HashMap<>();
		List<Resident> learners = new ArrayList<>();
		List<Resident> unplaced = new ArrayList<>();
		List<Resident> journeymen = new ArrayList<>();
		List<UUID> idle = new ArrayList<>();
		// who masters, learns, or is of an age to be apprenticed: the whole town once a week (children come of age to be
		// apprenticed within a week of it), the known crafters on the other days
		Iterable<Resident> who = s.residents();
		List<UUID> known = town.crafters;
		boolean full = known == null || Math.floorMod(day + s.id(), 7L) == 0;
		if (full) {
			// a workshop that has come to employ folk with no master among them: its senior hand is its master
			adopt(s, wp, day, c);
		}
		if (!full) {
			quick(s, town, known, day, c, event);
			return;
		}
		for (Resident r : who) {
			net.minecraft.nbt.CompoundTag t = Life.peek(r);
			boolean child = r.age == AgeStage.CHILD;
			if (t == null) {
				if (child && day - r.bornDay >= c.apprenticeAge()) {
					unplaced.add(r);
				}
				continue;
			}
			if (!child && r.age == AgeStage.ADULT && t.contains("mt")) {
				masters.add(r);
			}
			if (t.contains("am")) {
				learners.add(r);
			} else if (child && day - r.bornDay >= c.apprenticeAge() && !t.contains("jt")) {
				unplaced.add(r);
			}
			if (r.age == AgeStage.ADULT && t.contains("jt")) {
				journeymen.add(r);
			}
			if (t.getLongOr("idle", Long.MIN_VALUE) >= day) {
				idle.add(r.id);
			}
		}
		List<UUID> crafters = new ArrayList<>(masters.size() + learners.size() + journeymen.size());
		for (Resident r : learners) {
			UUID master = Life.master(r);
			JobType trade = Life.apprenticeTrade(r);
			if (r.age != AgeStage.CHILD) {
				// grown: a journeyman of the trade
				Life.endApprenticeship(r);
				if (trade != null) {
					Life.journeyman(r, trade, day);
					journeymen.add(r);
					town.stats.add("journeymen");
					event.accept(r, "journeyman");
				}
				continue;
			}
			Resident m = master == null ? null : s.resident(master);
			if (m == null || Life.masterOf(m) != trade || m.age != AgeStage.ADULT) {
				// the master is dead, gone, retired or no master now: to be placed again
				Life.endApprenticeship(r);
				if (trade != null) {
					unplaced.add(r);
				}
				continue;
			}
			taken.merge(m.id, 1, Integer::sum);
			crafters.add(r.id);
		}
		if (!unplaced.isEmpty() && !masters.isEmpty()) {
			for (Resident child : unplaced) {
				Resident master = masterFor(s, child, masters, taken, c.apprenticesPerMaster(), random);
				if (master == null) {
					continue;
				}
				JobType trade = Life.masterOf(master);
				Life.apprentice(child, master.id, trade, day);
				taken.merge(master.id, 1, Integer::sum);
				crafters.add(child.id);
				town.stats.add("apprenticed");
				event.accept(child, "apprenticed");
			}
		}
		// journeymen who have served their time
		for (Resident r : journeymen) {
			if (Life.journeyman(r) != null && Life.masterOf(r) == null) {
				crafters.add(r.id);
			}
			serve(r, town, day, c, event);
		}
		for (Resident m : masters) {
			crafters.add(m.id);
		}
		crafters.addAll(idle);
		town.crafters = crafters;
	}

	/**
	 * The days between the weekly look at the whole town: only the known crafters — an apprentice grown is a journeyman,
	 * one whose master is gone waits to be placed again, a journeyman who has served the time makes master. The list is
	 * left as it is (the weekly pass makes it afresh).
	 */
	private static void quick(Settlement s, TownLives town, List<UUID> known, long day, LivesConfig.Crafts c, BiConsumer<Resident, String> event) {
		for (int i = 0; i < known.size(); i++) {
			Resident r = s.resident(known.get(i));
			net.minecraft.nbt.CompoundTag t = r == null ? null : Life.peek(r);
			if (t == null) {
				continue;
			}
			if (t.contains("am")) {
				JobType trade = Life.apprenticeTrade(r);
				if (r.age != AgeStage.CHILD) {
					Life.endApprenticeship(r);
					if (trade != null) {
						Life.journeyman(r, trade, day);
						town.stats.add("journeymen");
						event.accept(r, "journeyman");
					}
					continue;
				}
				UUID master = Life.master(r);
				Resident m = master == null ? null : s.resident(master);
				if (m == null || Life.masterOf(m) != trade || m.age != AgeStage.ADULT) {
					Life.endApprenticeship(r); // placed again at the week's look
				}
			} else if (r.age == AgeStage.ADULT && t.contains("jt")) {
				serve(r, town, day, c, event);
			}
		}
	}

	/** A journeyman's day: time at the trade counts toward the masterpiece. */
	private static void serve(Resident r, TownLives town, long day, LivesConfig.Crafts c, BiConsumer<Resident, String> event) {
		JobType trade = Life.journeyman(r);
		if (trade == null) {
			return;
		}
		if (Life.masterOf(r) != null) {
			Life.journeyman(r, null, day);
			return;
		}
		if (r.job != trade) {
			Held.hold(r, day);
			if (Held.held(r) >= c.masterDays()) {
				// a whole season kept from the trade by a full town: they give it up for the work they have
				Life.journeyman(r, null, day);
				town.stats.add("journeymen_gave_up");
			}
			return;
		}
		long total = day - Life.journeymanSince(r);
		long atTrade = total - Held.held(r);
		if (atTrade >= c.masterDays() || total >= 2L * c.masterDays() && atTrade >= c.masterDays() / 2) {
			Life.master(r, trade, day);
			Life.journeyman(r, null, day);
			town.stats.add("masters");
			town.stats.add("masters_from_apprentices");
			town.news("master", day, r.id, List.of(r.givenName, trade.getSerializedName()));
			event.accept(r, "master");
		}
	}

	/** The parent who masters a trade with room; else a master with room, seeded. */
	private static Resident masterFor(Settlement s, Resident child, List<Resident> masters, Map<UUID, Integer> taken, int perMaster,
		SplittableRandom random) {
		for (UUID p : Kin.parents(child)) {
			Resident parent = s.resident(p);
			if (parent != null && parent.age == AgeStage.ADULT && Life.masterOf(parent) != null && taken.getOrDefault(parent.id, 0) < perMaster) {
				return parent;
			}
		}
		List<Resident> room = new ArrayList<>();
		for (Resident m : masters) {
			if (taken.getOrDefault(m.id, 0) < perMaster) {
				room.add(m);
			}
		}
		return room.isEmpty() ? null : room.get(random.nextInt(room.size()));
	}

	/**
	 * Days a journeyman spent off their trade (kept in their lives as {@code jh}, so the 60 days are 60 at the trade).
	 */
	static final class Held {
		private Held() {
		}

		static void hold(Resident r, long day) {
			var t = Life.of(r);
			if (t.getLongOr("jd", Long.MIN_VALUE) != day) {
				t.putLong("jd", day);
				t.putInt("jh", t.getIntOr("jh", 0) + 1);
			}
		}

		static int held(Resident r) {
			var t = Life.peek(r);
			return t == null ? 0 : t.getIntOr("jh", 0);
		}
	}
}
