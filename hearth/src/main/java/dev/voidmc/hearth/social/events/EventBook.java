package dev.voidmc.hearth.social.events;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.law.justice.Trials;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A town's event book, {@code hearth:social/events} (docs/LIVING_WORLD_2.md §3.5, §9.3): at most sixteen events scheduled
 * and sixteen recent outcomes; the plots burnt and not yet rebuilt; residents awaiting trial. Scene progress is never
 * saved: an event in progress at a restart resolves abstractly.
 */
public final class EventBook {
	public static final int MAX_SCHEDULED = 16;
	public static final int MAX_RECENT = 16;
	public static final int MAX_DEFENDANTS = 8;

	/**
	 * A plot burnt by an event: its blackened shell stands from {@code day} (put up in the world when the town is next
	 * embodied, {@code applied}) and the builders put it back; abstractly it is whole again from {@code until}.
	 */
	public record Burnt(int plot, long day, long until, boolean applied, int blocks) {
		public static final Codec<Burnt> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("plot").forGetter(Burnt::plot),
			Codec.LONG.fieldOf("day").forGetter(Burnt::day),
			Codec.LONG.fieldOf("until").forGetter(Burnt::until),
			Codec.BOOL.optionalFieldOf("applied", false).forGetter(Burnt::applied),
			Codec.INT.optionalFieldOf("blocks", 0).forGetter(Burnt::blocks)
		).apply(i, Burnt::new));

		public Burnt asApplied(int blocks) {
			return new Burnt(this.plot, this.day, this.until, true, blocks);
		}
	}

	public static final Codec<EventBook> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("next_id", 1).forGetter(b -> b.nextId),
		Scheduled.CODEC.listOf().optionalFieldOf("scheduled", List.of()).forGetter(b -> b.scheduled),
		Outcome.CODEC.listOf().optionalFieldOf("recent", List.of()).forGetter(b -> b.recent),
		Codec.LONG.optionalFieldOf("last_day", Long.MIN_VALUE).forGetter(b -> b.lastDay),
		Codec.LONG.optionalFieldOf("last_tax", Long.MIN_VALUE).forGetter(b -> b.lastTax),
		Burnt.CODEC.listOf().optionalFieldOf("burnt", List.of()).forGetter(b -> b.burnt),
		Trials.Defendant.CODEC.listOf().optionalFieldOf("defendants", List.of()).forGetter(b -> b.defendants),
		Codec.LONG.optionalFieldOf("raid_quiet", Long.MIN_VALUE).forGetter(b -> b.raidQuietUntil),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("takings", java.util.Map.of()).forGetter(b -> b.takings)
	).apply(i, EventBook::new));

	public static final HearthDataKey<EventBook> KEY = HearthDataKey.register(Hearth.id("social/events"), HearthDataKey.Scope.SETTLEMENT, CODEC,
		EventBook::new);

	public int nextId;
	public final List<Scheduled> scheduled;
	public final List<Outcome> recent;
	/** The last day whose events were scheduled. */
	public long lastDay;
	/** The last day the bailiff came. */
	public long lastTax;
	public final List<Burnt> burnt;
	public final List<Trials.Defendant> defendants;
	/** No raid before this day (a raid just came; the band licks its wounds). */
	public long raidQuietUntil;
	/** At a lord's seat: the tax the bailiff is bringing home, by journey id (as text), in bits. */
	public final java.util.Map<String, Long> takings;

	public EventBook() {
		this(1, List.of(), List.of(), Long.MIN_VALUE, Long.MIN_VALUE, List.of(), List.of(), Long.MIN_VALUE, java.util.Map.of());
	}

	EventBook(int nextId, List<Scheduled> scheduled, List<Outcome> recent, long lastDay, long lastTax, List<Burnt> burnt, List<Trials.Defendant> defendants,
		long raidQuietUntil, java.util.Map<String, Long> takings) {
		this.nextId = nextId;
		this.scheduled = new ArrayList<>(scheduled);
		this.recent = new ArrayList<>(recent);
		this.lastDay = lastDay;
		this.lastTax = lastTax;
		this.burnt = new ArrayList<>(burnt);
		this.defendants = new ArrayList<>(defendants);
		this.raidQuietUntil = raidQuietUntil;
		this.takings = new java.util.HashMap<>(takings);
	}

	/** Forces the key to register (class init) during mod init. */
	static void touch() {
	}

	/** Adds an event if there is room (oldest ended ones make way); returns whether it went in. */
	public boolean add(Scheduled e) {
		if (this.scheduled.size() >= MAX_SCHEDULED) {
			// the oldest event that has ended makes way (its outcome is in the recent list)
			for (int i = 0; i < this.scheduled.size(); i++) {
				if (this.scheduled.get(i).phase() == Scheduled.Phase.ENDED) {
					this.scheduled.remove(i);
					break;
				}
			}
		}
		if (this.scheduled.size() >= MAX_SCHEDULED) {
			return false;
		}
		this.scheduled.add(e);
		return true;
	}

	public int newId() {
		return this.nextId++;
	}

	public @Nullable Scheduled get(int id) {
		for (Scheduled e : this.scheduled) {
			if (e.id() == id) {
				return e;
			}
		}
		return null;
	}

	public void replace(Scheduled e) {
		for (int i = 0; i < this.scheduled.size(); i++) {
			if (this.scheduled.get(i).id() == e.id()) {
				this.scheduled.set(i, e);
				return;
			}
		}
	}

	public void remove(int id) {
		this.scheduled.removeIf(e -> e.id() == id);
	}

	public void remember(Outcome o) {
		this.recent.add(o);
		while (this.recent.size() > MAX_RECENT) {
			this.recent.remove(0);
		}
	}

	public @Nullable Burnt burnt(int plot) {
		for (Burnt b : this.burnt) {
			if (b.plot() == plot) {
				return b;
			}
		}
		return null;
	}

	/** Whether an event of this kind is in the book for a day. */
	public boolean has(EventKind kind, long day) {
		for (Scheduled e : this.scheduled) {
			if (e.kind() == kind && e.day() == day) {
				return true;
			}
		}
		return false;
	}

	/** Whether an event of this kind is in the book for any day from {@code from}. */
	public boolean hasFrom(EventKind kind, long from) {
		for (Scheduled e : this.scheduled) {
			if (e.kind() == kind && e.day() >= from) {
				return true;
			}
		}
		return false;
	}
}
