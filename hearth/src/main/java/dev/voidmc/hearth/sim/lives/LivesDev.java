package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.HashSet;
import java.util.SplittableRandom;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Game masters' and tours' handles on a town's lives ({@code /hearth lives ...}): make a character, move a thread. */
public final class LivesDev {
	private LivesDev() {
	}

	/** Whether the resident could hold the role (its conditions). */
	public static boolean fits(Settlement s, Resident r, String role) {
		LivesBook.Role def = LivesBook.get().role(role);
		return def != null && Notables.fits(s, r, def.who(), Math.max(0, s.data(Lives.TOWN).lastDay), Lives.childhoodDays());
	}

	/** Makes the resident the town's character of this role (the role's old holder stands down). Null if no such role. */
	public static TownLives.@Nullable Notable make(Settlement s, Resident r, String role) {
		LivesBook book = LivesBook.get();
		LivesBook.Role def = book.role(role);
		LivesBook.Thread thread = def == null ? null : book.thread(def.thread());
		if (thread == null) {
			return null;
		}
		TownLives town = s.data(Lives.TOWN);
		long day = Math.max(0, town.lastDay);
		town.notables.removeIf(n -> n.role.equals(role) || n.resident.equals(r.id));
		TownLives.Notable n = new TownLives.Notable(r.id, role, thread.id(), thread.start(), day);
		SplittableRandom random = new SplittableRandom(LivesDay.seed(s.id(), day) ^ r.id.getLeastSignificantBits());
		Resident partner = Notables.partner(s, town, r, def.partner(), new HashSet<>(java.util.List.of(r.id)), day, Lives.config(), random);
		n.partner = partner == null ? null : partner.id;
		town.notables.add(n);
		Threads.enter(s, town, n, thread, day, Lives.config(), random, Lives.events(s));
		s.markDirty();
		return n;
	}

	/** Moves a character's thread to a beat (its effects apply, nobody moved it). */
	public static boolean beat(Settlement s, TownLives.Notable n, String beat) {
		LivesBook.Thread t = LivesBook.get().thread(n.thread);
		if (t == null || t.beat(beat) == null) {
			return false;
		}
		TownLives town = s.data(Lives.TOWN);
		long day = Math.max(0, town.lastDay);
		Threads.move(s, town, n, t, beat, day, Lives.config(), new SplittableRandom(day), Lives.events(s), null, "", null);
		s.markDirty();
		return true;
	}

	/** Makes two residents sweethearts. */
	public static void sweethearts(Settlement s, Resident a, Resident b) {
		long day = Math.max(0, s.data(Lives.TOWN).lastDay);
		Life.touch(a, b.id, 70, Life.Bond.SWEETHEART, day, Lives.config().relations());
		Life.touch(b, a.id, 70, Life.Bond.SWEETHEART, day, Lives.config().relations());
		s.markDirty();
	}

	/** Starts a feud between two residents' houses; null if they may not feud (one house, kin). */
	public static TownLives.@Nullable Feud feud(Settlement s, Resident a, Resident b, String cause) {
		TownLives town = s.data(Lives.TOWN);
		TownLives.Feud f = Feuds.start(s, town, a, b, cause, Math.max(0, town.lastDay), Lives.config(), Lives.events(s));
		s.markDirty();
		return f;
	}

	public static @Nullable UUID sweetheartOf(Resident r) {
		return Life.sweetheart(r);
	}
}
