package dev.voidmc.hearth.npc.routine;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Windows of a resident's day that other modules settle before the schedule's own (docs/LIVING_WORLD_2.md §11.2): a named
 * character's courting walk or market errand (lane B2), a servant's duties (B4). The schedule's callers ({@code FolkBrain},
 * {@code RoutinePlacement}) pass every day through {@link #apply}; with no source registered it is the day unchanged.
 *
 * <p>Sources are asked once per resident per day (when their schedule is made): keep them cheap and pure of the day, so
 * the embodied and the placed day agree. Register during mod init.
 */
public final class ScheduleOverrides {
	/**
	 * A stretch of the day a source settles: from {@code start} to {@code end} (minutes of the day; {@code end} may be past
	 * midnight, below {@code start}).
	 */
	public record Window(int start, int end, Activity activity, @Nullable Leisure leisure, Schedule.Where where) {
	}

	@FunctionalInterface
	public interface Source {
		/** The windows this resident's day takes from this source today (empty for none). */
		List<Window> windows(Settlement settlement, Resident resident, long day);
	}

	private static volatile Source[] sources = new Source[0];

	private ScheduleOverrides() {
	}

	public static synchronized void register(Source source) {
		Source[] next = Arrays.copyOf(sources, sources.length + 1);
		next[next.length - 1] = source;
		sources = next;
	}

	/** The day with every source's windows laid over it (earlier sources win where windows overlap). */
	public static Schedule apply(Schedule base, Settlement settlement, Resident resident, long day) {
		Source[] all = sources;
		if (all.length == 0) {
			return base;
		}
		List<Window> windows = new ArrayList<>();
		for (Source s : all) {
			windows.addAll(s.windows(settlement, resident, day));
		}
		return windows.isEmpty() ? base : overlay(base, windows);
	}

	/**
	 * {@code base} with each window in place of what it covers; the base resumes where a window ends. Pure. A merge of the
	 * windows' ends with the base's block starts (a few dozen cuts, ~1 µs), not a walk of the day's minutes.
	 */
	public static Schedule overlay(Schedule base, List<Window> windows) {
		int nw = windows.size();
		int[] ws = new int[nw];
		int[] wl = new int[nw];
		List<Schedule.Block> blocks = base.blocks();
		int[] cuts = new int[1 + nw * 2 + blocks.size()];
		int nc = 0;
		cuts[nc++] = 0;
		for (int w = 0; w < nw; w++) {
			Window win = windows.get(w);
			ws[w] = Math.floorMod(win.start(), Schedule.DAY);
			wl[w] = Math.floorMod(win.end() - win.start(), Schedule.DAY);
			if (wl[w] > 0) {
				cuts[nc++] = ws[w];
				cuts[nc++] = (ws[w] + wl[w]) % Schedule.DAY;
			}
		}
		for (Schedule.Block b : blocks) {
			if (b.start() >= 0 && b.start() < Schedule.DAY) {
				cuts[nc++] = b.start();
			}
		}
		Arrays.sort(cuts, 0, nc);
		List<Schedule.Block> out = new ArrayList<>(nc);
		Object last = null;
		int prev = -1;
		for (int k = 0; k < nc; k++) {
			int m = cuts[k];
			if (m == prev) {
				continue;
			}
			prev = m;
			// the earliest window covering this stretch wins; else the base's block there
			int owner = -1;
			for (int w = 0; w < nw; w++) {
				if (wl[w] > 0 && Math.floorMod(m - ws[w], Schedule.DAY) < wl[w]) {
					owner = w;
					break;
				}
			}
			Object here;
			Schedule.Block block;
			if (owner >= 0) {
				Window win = windows.get(owner);
				here = win;
				block = new Schedule.Block(m, win.activity(), win.leisure(), win.where());
			} else {
				Schedule.Block b = base.at(m);
				here = b;
				block = new Schedule.Block(m, b.activity(), b.leisure(), b.where());
			}
			if (here != last) {
				out.add(block);
				last = here;
			}
		}
		return Schedule.ofBlocks(out);
	}
}
