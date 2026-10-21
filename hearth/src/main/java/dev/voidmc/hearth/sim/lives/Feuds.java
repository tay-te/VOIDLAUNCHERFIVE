package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.lives.Life.Bond;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * Feuds between households (docs/LIVING_WORLD_2.md §4.1–4.2): over love (a jilted rival), a theft blamed, a boundary in
 * a crowded town, a brawl, a thread's turn. A household is the folk of one home, led by its eldest grown member; a feud
 * passes to the next head when the head dies, and ends when a house is gone. Feuds cool with time and make up by the
 * odds (more likely the older they are, and with generous or cheerful heads), at a wedding between the houses, or
 * through a player's mediation.
 */
public final class Feuds {
	private Feuds() {
	}

	/** The heads of the town's households, by home plot (the homeless are each their own house). */
	static Map<Integer, Resident> heads(Settlement s) {
		Map<Integer, Resident> heads = new HashMap<>();
		for (Resident r : s.residents()) {
			if (r.home == Resident.NO_PLOT) {
				continue;
			}
			Resident cur = heads.get(r.home);
			if (cur == null || better(r, cur)) {
				heads.put(r.home, r);
			}
		}
		return heads;
	}

	private static boolean better(Resident a, Resident b) {
		boolean ga = a.age != AgeStage.CHILD;
		boolean gb = b.age != AgeStage.CHILD;
		if (ga != gb) {
			return ga;
		}
		return a.bornDay < b.bornDay || a.bornDay == b.bornDay && a.id.compareTo(b.id) < 0;
	}

	/** The head of a resident's household. */
	public static Resident headOf(Settlement s, Resident r) {
		if (r.home == Resident.NO_PLOT) {
			return r;
		}
		Resident best = r;
		for (Resident o : s.residents()) {
			if (o.home == r.home && better(o, best)) {
				best = o;
			}
		}
		return best;
	}

	/** The feud between the houses of these two, or null. */
	public static TownLives.@Nullable Feud between(Settlement s, TownLives town, Resident a, Resident b) {
		UUID ha = headOf(s, a).id;
		UUID hb = headOf(s, b).id;
		for (TownLives.Feud f : town.feuds) {
			if (f.between(ha, hb)) {
				return f;
			}
		}
		return null;
	}

	/** A feud between the houses of {@code a} and {@code b} begins, if one may (not kin, not one house, room in the town). */
	static TownLives.@Nullable Feud start(Settlement s, TownLives town, Resident a, Resident b, String cause, long day, LivesConfig cfg,
		BiConsumer<Resident, String> event) {
		Resident ha = headOf(s, a);
		Resident hb = headOf(s, b);
		if (ha == hb || ha.home != Resident.NO_PLOT && ha.home == hb.home || Kin.close(ha, hb) || town.feuds.size() >= cfg.feuds().maxPerTown()) {
			return null;
		}
		for (TownLives.Feud f : town.feuds) {
			if (f.between(ha.id, hb.id)) {
				f.heat = Math.min(100, f.heat + 15);
				return f;
			}
		}
		TownLives.Feud f = new TownLives.Feud(ha.id, hb.id, cause, day);
		town.feuds.add(f);
		town.feuders = null;
		Life.touch(ha, hb.id, -50, Bond.FEUD, day, cfg.relations());
		Life.touch(hb, ha.id, -50, Bond.FEUD, day, cfg.relations());
		town.stats.add("feuds");
		town.news("feud", day, ha.id, List.of(ha.familyName.isEmpty() ? ha.givenName : ha.familyName, hb.familyName.isEmpty() ? hb.givenName : hb.familyName,
			cause));
		event.accept(ha, "feud");
		return f;
	}

	/** A feud with the town's odds for its cause. */
	static void maybe(Settlement s, TownLives town, Resident a, Resident b, String cause, long day, LivesConfig cfg, SplittableRandom random,
		BiConsumer<Resident, String> event) {
		double odds = switch (cause) {
			case "love" -> cfg.feuds().jiltedChance();
			case "theft" -> cfg.feuds().blameChance();
			default -> 1;
		};
		if (random.nextDouble() < odds) {
			start(s, town, a, b, cause, day, cfg, event);
		}
	}

	/** Ends a feud: the heads are reconciled. */
	static void peace(Settlement s, TownLives town, TownLives.Feud f, long day, LivesConfig cfg, String why, BiConsumer<Resident, String> event) {
		town.feuds.remove(f);
		town.feuders = null;
		Resident a = s.resident(f.headA);
		Resident b = s.resident(f.headB);
		if (a != null) {
			Life.touch(a, f.headB, 50, Bond.ACQUAINTANCE, day, cfg.relations());
		}
		if (b != null) {
			Life.touch(b, f.headA, 50, Bond.ACQUAINTANCE, day, cfg.relations());
		}
		town.stats.add("feuds_ended");
		town.stats.add("peace_" + why);
		if (a != null && b != null) {
			town.news("peace", day, a.id, List.of(a.familyName.isEmpty() ? a.givenName : a.familyName, b.familyName.isEmpty() ? b.givenName : b.familyName, why));
			event.accept(a, "peace");
		}
	}

	/** The day's feuds: heads passed on, quarrels over boundaries, cooling, peace. */
	static void day(Settlement s, TownLives town, Workplaces wp, long day, LivesConfig cfg, SplittableRandom random, BiConsumer<Resident, String> event) {
		LivesConfig.Feuds fc = cfg.feuds();
		for (int i = town.feuds.size() - 1; i >= 0; i--) {
			TownLives.Feud f = town.feuds.get(i);
			Resident a = s.resident(f.headA);
			Resident b = s.resident(f.headB);
			if (a == null || b == null) {
				// a head is dead or gone: the feud passes to the kin who lead the house now
				Map<Integer, Resident> heads = heads(s);
				a = a != null ? a : passOn(s, heads, f.headA);
				b = b != null ? b : passOn(s, heads, f.headB);
				if (a == null || b == null || a == b) {
					town.feuds.remove(i);
					town.feuders = null;
					town.stats.add("feuds_ended");
					town.stats.add("peace_gone");
					continue;
				}
				f.headA = a.id;
				f.headB = b.id;
				town.feuders = null;
				Life.touch(a, b.id, -40, Bond.FEUD, day, cfg.relations());
				Life.touch(b, a.id, -40, Bond.FEUD, day, cfg.relations());
			}
			f.heat = Math.max(1, f.heat - 1);
			// a wedding between the houses ends it (looked for once a week)
			if (Math.floorMod(day + i, 7L) == 0 && wedBetween(s, a, b)) {
				peace(s, town, f, day, cfg, "wedding", event);
				continue;
			}
			double odds = fc.peacePerDay() * (1 + (day - f.since) / 60.0) * (100 - f.heat + 20) / 70.0;
			odds *= temper(a) * temper(b);
			if (random.nextDouble() < odds) {
				peace(s, town, f, day, cfg, "time", event);
			}
		}
		// neighbours in a crowded town fall out over the line between their plots
		if (fc.boundaryChance() > 0 && s.population() > s.beds() && random.nextDouble() < fc.boundaryChance() * 10) {
			Map<Integer, Resident> heads = heads(s);
			List<Resident> hs = new ArrayList<>(heads.values());
			if (hs.size() >= 2) {
				Resident a = hs.get(random.nextInt(hs.size()));
				int nh = wp.neighbour(a.home);
				Resident b = nh == Resident.NO_PLOT ? null : heads.get(nh);
				if (b != null) {
					start(s, town, a, b, "boundary", day, cfg, event);
				}
			}
		}
	}

	private static double temper(Resident head) {
		double t = 1;
		if (Kin.has(head, "generous") || Kin.has(head, "cheerful")) {
			t *= 1.5;
		}
		if (Kin.has(head, "grumpy") || Kin.has(head, "greedy")) {
			t *= 0.6;
		}
		return t;
	}

	/** The house's head now: the old head while they live and are in the house; else whoever leads that house now. */
	/** The kin who lead the house now of a head who is dead or gone (a spouse, a grown child), or null. */
	private static @Nullable Resident passOn(Settlement s, Map<Integer, Resident> heads, UUID head) {
		for (Resident o : s.residents()) {
			if (head.equals(Kin.spouse(o)) || o.age != AgeStage.CHILD && Kin.parents(o).contains(head)) {
				Resident h = o.home == Resident.NO_PLOT ? o : heads.get(o.home);
				return h == null ? o : h;
			}
		}
		return null;
	}

	private static boolean wedBetween(Settlement s, Resident headA, Resident headB) {
		if (headA.home == Resident.NO_PLOT || headB.home == Resident.NO_PLOT) {
			return false;
		}
		for (Resident r : s.residents()) {
			if (r.home != headA.home) {
				continue;
			}
			UUID sp = Kin.spouse(r);
			Resident o = sp == null ? null : s.resident(sp);
			if (o != null && o.home == headB.home) {
				return true;
			}
		}
		return false;
	}

	/** Residents of feuding houses, each with the heads of the houses they are at odds with (cached until a feud changes). */
	static Map<UUID, Set<UUID>> feuders(Settlement s, TownLives town) {
		Map<UUID, Set<UUID>> m = town.feuders;
		if (m != null) {
			return m;
		}
		m = new HashMap<>();
		if (!town.feuds.isEmpty()) {
			Map<UUID, Integer> homeOfHead = new HashMap<>();
			for (TownLives.Feud f : town.feuds) {
				for (UUID h : new UUID[] {f.headA, f.headB}) {
					Resident r = s.resident(h);
					homeOfHead.put(h, r == null ? Resident.NO_PLOT : r.home);
				}
			}
			for (Resident r : s.residents()) {
				for (TownLives.Feud f : town.feuds) {
					boolean inA = r.id.equals(f.headA) || r.home != Resident.NO_PLOT && r.home == homeOfHead.get(f.headA);
					boolean inB = r.id.equals(f.headB) || r.home != Resident.NO_PLOT && r.home == homeOfHead.get(f.headB);
					if (inA) {
						m.computeIfAbsent(r.id, k -> new HashSet<>()).add(f.headB);
					}
					if (inB) {
						m.computeIfAbsent(r.id, k -> new HashSet<>()).add(f.headA);
					}
				}
			}
		}
		town.feuders = m;
		return m;
	}

	/** Whether these two are of houses at feud. */
	public static boolean feuding(Settlement s, TownLives town, Resident a, Resident b) {
		Set<UUID> foes = feuders(s, town).get(a.id);
		if (foes == null) {
			return false;
		}
		return foes.contains(headOf(s, b).id);
	}
}
