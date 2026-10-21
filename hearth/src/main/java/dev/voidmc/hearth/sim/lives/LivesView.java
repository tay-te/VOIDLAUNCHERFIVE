package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Quick reads of a town's lives for the embodied side (behaviours ask about once a second, so these are lookups in what
 * the day's pass left, not searches): feuds by resident, the named characters, a resident's house head.
 */
public final class LivesView {
	private LivesView() {
	}

	public static TownLives town(Settlement s) {
		return s.data(Lives.TOWN);
	}

	/** The heads of the houses this resident's house feuds with, or null if none. */
	public static @Nullable Set<UUID> foes(Settlement s, UUID resident) {
		TownLives town = s.data(Lives.TOWN);
		if (town.feuds.isEmpty()) {
			return null;
		}
		return Feuds.feuders(s, town).get(resident);
	}

	/** Whether these two are of houses at feud. */
	public static boolean feuding(Settlement s, Resident a, Resident b) {
		Set<UUID> foes = foes(s, a.id);
		return foes != null && foes.contains(Feuds.headOf(s, b).id);
	}

	public static Resident head(Settlement s, Resident r) {
		return Feuds.headOf(s, r);
	}

	public static TownLives.@Nullable Feud feudBetween(Settlement s, Resident a, Resident b) {
		return Feuds.between(s, s.data(Lives.TOWN), a, b);
	}

	/** Makes up a feud (a player's mediation); fires the life events. */
	public static void makePeace(Settlement s, TownLives.Feud f, String why) {
		TownLives town = s.data(Lives.TOWN);
		Feuds.peace(s, town, f, Math.max(0, town.lastDay), Lives.config(), why, Lives.events(s));
		s.markDirty();
	}

	public static TownLives.@Nullable Notable notable(Settlement s, UUID resident) {
		TownLives town = s.data(Lives.TOWN);
		return town.notables.isEmpty() ? null : town.notable(resident);
	}

	public static List<TownLives.Notable> notables(Settlement s) {
		return s.data(Lives.TOWN).notables;
	}

	public static List<TownLives.News> news(Settlement s) {
		return s.data(Lives.TOWN).news;
	}

	public static Map<String, Integer> stats(Settlement s) {
		return s.data(Lives.TOWN).stats.all();
	}

	/** The relations of a resident as they stand today. */
	public static List<Life.Relation> relations(Settlement s, Resident r) {
		return Life.relations(r, Math.max(0, s.data(Lives.TOWN).lastDay), Lives.config().relations());
	}

	public static Life.@Nullable Relation relation(Settlement s, Resident r, UUID other) {
		return Life.relation(r, other, Math.max(0, s.data(Lives.TOWN).lastDay), Lives.config().relations());
	}

	/** Two friends met (embodied: they sat together at the tavern, walked out together). */
	public static void met(Settlement s, Resident a, Resident b, int gain) {
		Bonds.meet(a, b, gain, Math.max(0, s.data(Lives.TOWN).lastDay), Lives.config().relations());
		s.markDirty();
	}

	/** The thread's beat now, its definition, and the choices open at it. */
	public static LivesBook.@Nullable Beat beat(TownLives.Notable n) {
		LivesBook.Thread t = LivesBook.get().thread(n.thread);
		return t == null ? null : t.beat(n.beat);
	}
}
