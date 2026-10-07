package dev.voidmc.mettle.character.moment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * The toast queue behind the HUD's progress moments (UI.md §6b): one toast on screen at a time, the rest waiting in
 * order. Rapid repeats merge instead of stacking: a skill that levels twice while its toast is up shows the higher
 * level, a perk bought rank after rank shows the highest rank, and a level that opens a tier upgrades to the tier toast.
 *
 * <p>Time is the queue's own clock, advanced by the client while the HUD is visible, so toasts wait (rather than expire
 * unseen) while a screen is open. Plain Java: unit-tested in {@code MomentQueueTest}.
 */
public final class MomentQueue {
	/** On screen when nothing else waits. */
	public static final long SHOW_MS = 3200;
	/** On screen when others wait behind it. */
	public static final long BUSY_MS = 2000;
	public static final long IN_MS = 220;
	public static final long OUT_MS = 380;
	/** Waiting toasts beyond this drop the oldest (bought and level ones before earned ones). */
	public static final int MAX_PENDING = 5;

	/** One toast. Fields grow as repeats merge into it. */
	public static final class Entry {
		private MomentKind kind;
		private final String skill;
		private final String perk;
		private int level;
		private int tier;
		private int rank;
		private int repeats;
		private long shownAt = -1;
		private long endsAt;

		Entry(MomentKind kind, String skill, String perk, int level, int tier, int rank) {
			this.kind = kind;
			this.skill = skill;
			this.perk = perk;
			this.level = level;
			this.tier = tier;
			this.rank = rank;
		}

		public MomentKind kind() {
			return this.kind;
		}

		/** The skill id ({@code "mettle:mining"}), or null for the character level. */
		public String skill() {
			return this.skill;
		}

		/** The perk id, for perk moments. */
		public String perk() {
			return this.perk;
		}

		public int level() {
			return this.level;
		}

		/** The tier opened (1–4), for {@link MomentKind#TIER}. */
		public int tier() {
			return this.tier;
		}

		public int rank() {
			return this.rank;
		}

		/** How many later moments merged into this one. */
		public int repeats() {
			return this.repeats;
		}

		String mergeKey() {
			return mergeKey(this.kind, this.skill, this.perk);
		}

		static String mergeKey(MomentKind kind, String skill, String perk) {
			return switch (kind) {
				case LEVEL, TIER -> "skill:" + skill;
				case CHARACTER -> "character";
				default -> "perk:" + perk;
			};
		}

		void absorb(MomentKind kind, int level, int tier, int rank) {
			this.level = Math.max(this.level, level);
			this.rank = Math.max(this.rank, rank);
			this.tier = Math.max(this.tier, tier);
			if (kind == MomentKind.TIER) {
				this.kind = MomentKind.TIER;
			}
			this.repeats++;
		}
	}

	private final Deque<Entry> pending = new ArrayDeque<>();
	private final Set<MomentKind> seen = EnumSet.noneOf(MomentKind.class);
	private Entry current;
	private long clock;
	private int offered;

	/**
	 * Adds a moment, merging it into the toast on screen (unless that one is already fading) or a waiting one with the
	 * same subject. Returns the entry it landed in; {@code repeats() > 0} means it merged.
	 */
	public Entry offer(MomentKind kind, String skill, String perk, int level, int tier, int rank) {
		Objects.requireNonNull(kind);
		this.offered++;
		this.seen.add(kind);
		String key = Entry.mergeKey(kind, skill, perk);
		if (this.current != null && this.current.mergeKey().equals(key) && this.clock < this.current.endsAt - OUT_MS) {
			this.current.absorb(kind, level, tier, rank);
			// Fully visible again, and on screen long enough to read the new number.
			this.current.endsAt = Math.max(this.current.endsAt, this.clock + (this.pending.isEmpty() ? SHOW_MS : BUSY_MS) - IN_MS);
			return this.current;
		}
		for (Entry e : this.pending) {
			if (e.mergeKey().equals(key)) {
				e.absorb(kind, level, tier, rank);
				return e;
			}
		}
		Entry entry = new Entry(kind, skill, perk, level, tier, rank);
		this.pending.addLast(entry);
		if (this.pending.size() > MAX_PENDING) {
			dropOne();
		}
		// Something now waits: the toast on screen gives way sooner, but never cuts off mid-fade.
		if (this.current != null) {
			this.current.endsAt = Math.min(this.current.endsAt, Math.max(this.current.shownAt + BUSY_MS, this.clock + OUT_MS));
		}
		return entry;
	}

	private void dropOne() {
		for (Iterator<Entry> it = this.pending.iterator(); it.hasNext(); ) {
			if (!it.next().kind().earned()) {
				it.remove();
				return;
			}
		}
		this.pending.removeFirst();
	}

	/** Moves the queue's clock on; the toast on screen ends and the next one starts when it is due. */
	public void advance(long ms) {
		this.clock += Math.max(0, ms);
		if (this.current != null && this.clock >= this.current.endsAt) {
			this.current = null;
		}
		if (this.current == null && !this.pending.isEmpty()) {
			this.current = this.pending.removeFirst();
			this.current.shownAt = this.clock;
			this.current.endsAt = this.clock + (this.pending.isEmpty() ? SHOW_MS : BUSY_MS);
		}
	}

	/** The toast on screen, or null. */
	public Entry current() {
		return this.current;
	}

	/** 0–1: how far the current toast has slid in (and, near its end, faded out). */
	public float visibility() {
		if (this.current == null) {
			return 0f;
		}
		float in = Math.min(1f, (this.clock - this.current.shownAt) / (float) IN_MS);
		float out = Math.min(1f, (this.current.endsAt - this.clock) / (float) OUT_MS);
		return Math.max(0f, Math.min(in, out));
	}

	public int pendingCount() {
		return this.pending.size();
	}

	/** Every moment offered since the last {@link #clear} (merged ones included). */
	public int offered() {
		return this.offered;
	}

	/** The kinds offered since the last {@link #clear}. */
	public Set<MomentKind> seen() {
		return EnumSet.copyOf(this.seen);
	}

	public void clear() {
		this.pending.clear();
		this.current = null;
		this.seen.clear();
		this.offered = 0;
	}
}
