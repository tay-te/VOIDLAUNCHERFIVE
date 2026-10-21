package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Jobs and homes other modules settle for a resident before the clock's daily assignment (docs/LIVING_WORLD_2.md §11.2):
 * apprentices at their master's workshop and masters kept to it (lane B2), servants in a player's house and companions
 * (B4). Consulted by {@code SettlementClock.assignJobs/assignHomes}; with no source registered they cost nothing.
 *
 * <p>Server thread (and the history writer's thread for histories): sources must be cheap lookups. Register during mod
 * init.
 */
public final class ResidentClaims {
	/** A job settled for a resident: the trade and the plot they do it at ({@link Resident#NO_PLOT} for none). */
	public record Job(JobType job, int work) {
	}

	public interface Source {
		/** The job settled for this adult, or null to leave them to the daily assignment. */
		default @Nullable Job job(Settlement settlement, Resident resident) {
			return null;
		}

		/** The home plot settled for this resident, or null to leave them to the daily assignment. */
		default @Nullable Integer home(Settlement settlement, Resident resident) {
			return null;
		}

		/** Whether the resident is kept out of the daily assignment altogether: their job and home stay as they are. */
		default boolean exempt(Settlement settlement, Resident resident) {
			return false;
		}
	}

	private static volatile Source[] sources = new Source[0];

	private ResidentClaims() {
	}

	public static synchronized void register(Source source) {
		Source[] next = Arrays.copyOf(sources, sources.length + 1);
		next[next.length - 1] = source;
		sources = next;
	}

	/** Whether any source is registered (the clock skips the lookups when none is). */
	public static boolean active() {
		return sources.length > 0;
	}

	public static @Nullable Job job(Settlement settlement, Resident resident) {
		for (Source s : sources) {
			Job j = s.job(settlement, resident);
			if (j != null) {
				return j;
			}
		}
		return null;
	}

	public static @Nullable Integer home(Settlement settlement, Resident resident) {
		for (Source s : sources) {
			Integer h = s.home(settlement, resident);
			if (h != null) {
				return h;
			}
		}
		return null;
	}

	public static boolean exempt(Settlement settlement, Resident resident) {
		for (Source s : sources) {
			if (s.exempt(settlement, resident)) {
				return true;
			}
		}
		return false;
	}

	/** Test hook: forgets every source. */
	static synchronized void clear() {
		sources = new Source[0];
	}
}
