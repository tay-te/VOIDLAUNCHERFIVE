package dev.voidmc.hearth.sim.world;

import java.util.Arrays;

/**
 * Extra "is this column planned?" sources (docs/LIVING_WORLD_2.md §11.2): roads' strips (lane A1), the Great Mine's
 * square (A4), posts (B3). {@link SimWorld#isPlanned} and {@link SimWorld#isPlannedNow} consult them after their own
 * plans, and so does everything that asks those (world generation's guard, {@code ExpanseClaims}).
 *
 * <p>Sources are asked from world generation workers as well as the server thread, many times per chunk: they must be
 * thread-safe, cheap (a lookup, never a plan) and must not load chunks. Register during mod init.
 */
public final class Claims {
	/** A source of planned columns. */
	@FunctionalInterface
	public interface Source {
		/**
		 * @param now true on the server thread, where a source must never work anything out that is not ready (answer
		 *            true, "planned", for a column it cannot yet say about, as {@link SimWorld#isPlannedNow} does)
		 */
		boolean isPlanned(SimWorld world, int x, int z, boolean now);
	}

	private static volatile Source[] sources = new Source[0];

	private Claims() {
	}

	public static synchronized void register(Source source) {
		Source[] next = Arrays.copyOf(sources, sources.length + 1);
		next[next.length - 1] = source;
		sources = next;
	}

	/** Whether any registered source plans this column. */
	public static boolean isPlanned(SimWorld world, int x, int z, boolean now) {
		for (Source s : sources) {
			if (s.isPlanned(world, x, z, now)) {
				return true;
			}
		}
		return false;
	}
}
