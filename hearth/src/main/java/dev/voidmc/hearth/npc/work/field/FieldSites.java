package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The remote sites of every settlement, worked out when first asked and again when the town changes (its people, its
 * plots, its sites) or a sim hour has passed, and the working windows they give their workers' days. Server thread.
 */
public final class FieldSites {
	/** Game ticks a settlement's plan is kept before it is worked out again (a sim hour). */
	static final long KEEP_TICKS = 1000;

	private static final class Cached {
		final RemoteSites.Plan plan;
		final long at;
		final int stamp;
		long checked;

		Cached(RemoteSites.Plan plan, long at, int stamp) {
			this.plan = plan;
			this.at = at;
			this.stamp = stamp;
			this.checked = at;
		}
	}

	private static final Map<Long, Cached> PLANS = new HashMap<>();
	private static long clock = Long.MIN_VALUE;

	private FieldSites() {
	}

	/** Moves the cache's clock (the server's game time, from the field work tick). */
	static void setClock(long gameTime) {
		clock = gameTime;
	}

	/** A settlement's remote sites now. */
	public static RemoteSites.Plan plan(Settlement s) {
		Cached c = PLANS.get(s.id());
		if (c != null && c.checked == clock && clock != Long.MIN_VALUE) {
			// looked at already this tick
			return c.plan;
		}
		int stamp = stamp(s);
		if (c != null) {
			c.checked = clock;
		}
		if (c == null || c.stamp != stamp || (c.at == Long.MIN_VALUE) != (clock == Long.MIN_VALUE) || clock - c.at > KEEP_TICKS || clock < c.at) {
			c = new Cached(RemoteSites.plan(s, FieldWork.config().sites()), clock, stamp);
			PLANS.put(s.id(), c);
			if (PLANS.size() > 2048) {
				PLANS.clear();
			}
		}
		return c.plan;
	}

	/** The site a resident works at, or null. */
	public static RemoteSites.@Nullable Site siteOf(Settlement s, Resident r) {
		RemoteSites.Plan p = plan(s);
		return p.workers().isEmpty() ? null : p.of(r.id);
	}

	/** Forgets every plan (the server stopped, the config changed). */
	public static void clear() {
		PLANS.clear();
	}

	/** What changes a town's sites: its people, their trades and work places, its plots and its surveyed sites. */
	private static int stamp(Settlement s) {
		int h = s.population() * 31 + s.plots().size();
		h = h * 31 + s.sites().size();
		for (Resident r : s.residents()) {
			if (r.work != Resident.NO_PLOT) {
				h = h * 17 + r.work * 7 + r.job.ordinal();
			}
		}
		return h;
	}

	/** How many minutes the way out to a site takes a worker (a flock from its gate, a miner from home). */
	public static int travelMinutes(BlockPos from, BlockPos to) {
		return Commute.minutes(Commute.distance(from, to), FieldWork.config().sites().walkSpeed());
	}

	/**
	 * The windows remote work takes from a day (docs/LIVING_WORLD_2.md §6.1): a herder with a far meadow is out with the
	 * flock from 07:00 to 18:00 and eats there; miners at the Great Mine are at the pithead from 06:00 to 18:00.
	 */
	static final ScheduleOverrides.Source WINDOWS = (settlement, resident, day) -> {
		if (!FieldWork.config().sites().enabled()) {
			return List.of();
		}
		RemoteSites.Site site = siteOf(settlement, resident);
		int[] w = site == null ? null : site.window();
		if (w == null) {
			return List.of();
		}
		return List.of(new ScheduleOverrides.Window(w[0], w[1], Activity.WORK, null, Schedule.Where.WORK));
	};
}
