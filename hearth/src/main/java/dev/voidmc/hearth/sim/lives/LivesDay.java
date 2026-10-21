package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * One settlement's lives for one sim day (docs/LIVING_WORLD_2.md §4.2), run on {@code DAY_PASSED} (also in catch-up;
 * histories leave it out): crafts, relations and courtships, feuds, named characters, their threads. Pure apart from the
 * settlement it changes and seeded by the town and the day, so the same town on the same day comes out the same. Nothing
 * runs per tick.
 */
public final class LivesDay {
	private LivesDay() {
	}


	/** Something that happened in a resident's life today (fired as {@code LIFE_EVENT} by the caller). */
	public record Happening(UUID resident, String kind, @Nullable UUID player) {
	}

	static long seed(long settlement, long day) {
		long h = settlement * 0x9E3779B97F4A7C15L + day * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0x7FB5D329728EA185L;
		return h ^ h >>> 27;
	}

	/** Runs the day if it has not run yet; returns what happened. */
	public static List<Happening> day(Settlement s, long day, LivesConfig cfg, LivesBook book, int childhoodDays, Bonds.Rules rules) {
		return day(s, Workplaces.of(s), day, cfg, book, childhoodDays, rules);
	}

	/** The day with the town's workplaces as given (the tests hand in a made-up town's). */
	static List<Happening> day(Settlement s, Workplaces wp, long day, LivesConfig cfg, LivesBook book, int childhoodDays, Bonds.Rules rules) {
		TownLives town = s.data(Lives.TOWN);
		if (!cfg.lives().enabled() || town.lastDay >= day) {
			return List.of();
		}
		town.lastDay = day;
		List<Happening> out = new ArrayList<>();
		BiConsumer<Resident, String> event = (r, kind) -> out.add(new Happening(r.id, kind, null));
		SplittableRandom random = new SplittableRandom(seed(s.id(), day));
		if (!town.adopted) {
			for (Resident r : s.residents()) {
				if (r.age == AgeStage.ELDER && !Kin.married(r) && !Kin.children(r).isEmpty()) {
					Life.flag(r, "widowed", true);
				}
			}
			town.adopted = true;
		}
		Crafts.day(s, town, wp, day, childhoodDays, cfg, random, event);
		Bonds.day(s, wp, day, cfg, rules, random, event, town);
		Feuds.day(s, town, wp, day, cfg, random, event);
		Notables.day(s, town, book, day, childhoodDays, cfg, random, event);
		Threads.day(s, town, book, day, cfg, random, event);
		town.invalidate();
		s.markDirty();
		// a thread's beat names the player who moved it
		for (int i = 0; i < out.size(); i++) {
			Happening h = out.get(i);
			if (h.kind().equals("thread")) {
				TownLives.Notable n = town.notable(h.resident());
				if (n != null && n.by != null) {
					out.set(i, new Happening(h.resident(), h.kind(), n.by));
				}
			}
		}
		return out;
	}

	/**
	 * A resident left the town ({@code died}: by death). A widow or widower mourns, and their book notes it; the rest the
	 * day's pass sorts out (apprentices placed again, a feud passed on, a named character replaced).
	 */
	public static List<Happening> gone(Settlement s, Resident r, boolean died, long day, LivesConfig cfg) {
		if (!cfg.lives().enabled()) {
			return List.of();
		}
		List<Happening> out = new ArrayList<>();
		TownLives town = s.data(Lives.TOWN);
		if (died) {
			UUID sp = Kin.spouse(r);
			Resident spouse = sp == null ? null : s.resident(sp);
			if (spouse != null) {
				Life.mourn(spouse, day + cfg.lives().griefDays());
				Life.flag(spouse, "widowed", true);
				town.stats.add("widowed");
				town.news("widowed", day, spouse.id, List.of(spouse.givenName, r.givenName));
				out.add(new Happening(spouse.id, "widowed", null));
			}
			for (Resident o : s.residents()) {
				List<UUID> parents = Kin.parents(o);
				if (!parents.contains(r.id)) {
					continue;
				}
				if (o.age != AgeStage.CHILD) {
					Life.mourn(o, Math.max(day + 3, o.extra.getCompoundOrEmpty(Life.KEY).getLongOr("gu", Long.MIN_VALUE)));
				} else {
					boolean left = false;
					for (UUID p : parents) {
						left |= !p.equals(r.id) && s.resident(p) != null;
					}
					if (!left && !town.orphans.contains(o.id)) {
						town.orphans.add(o.id);
						town.stats.add("orphans");
					}
				}
			}
		}
		town.notables.removeIf(n -> n.resident.equals(r.id));
		town.invalidate();
		s.markDirty();
		return out;
	}
}
