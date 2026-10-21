package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.person.Trait;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * The companions' rules (docs/LIVING_WORLD_2.md §8.1–§8.2), pure: who will come along, what they cost, the daily wage, and
 * how they take what the player does. The service applies them to the world.
 */
public final class CompanionRules {
	private CompanionRules() {
	}

	// ---------------------------------------------------------------- hiring

	/**
	 * Whether a resident would hire on at all: an adult, not an office holder, not serving anyone already (a companion, a
	 * servant), not away on other business, and brave or greedy.
	 */
	public static boolean willing(Settlement s, Resident r) {
		if (r.age != AgeStage.ADULT || office(r) || CompanionState.hired(r) || r.extra.getCompoundOrEmpty("servant").getString("player").isPresent()
			|| s.isResidentAway(r.id)) {
			return false;
		}
		Set<Trait> traits = NpcData.of(r).traits();
		return traits.contains(Trait.BRAVE) || traits.contains(Trait.GREEDY);
	}

	/** Whether the resident holds an office (the reeve, the guard captain...): office holders do not leave their post. */
	static boolean office(Resident r) {
		String o = r.extra.getCompoundOrEmpty("social").getStringOr("office", "");
		return !o.isEmpty() && !o.equals("none") || r.job == JobType.LORD || r.job == JobType.STEWARD || r.job == JobType.REEVE
			|| r.job == JobType.JUDGE || r.job == JobType.JAILER;
	}

	/** Who an innkeeper names when asked about hands for hire: guards and hunters first, the brave before the greedy. */
	public static List<Resident> candidates(Settlement s, int max) {
		List<Resident> out = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (willing(s, r)) {
				out.add(r);
			}
		}
		out.sort(Comparator.comparingInt(CompanionRules::rank).thenComparing(r -> r.id));
		return out.size() > max ? List.copyOf(out.subList(0, max)) : out;
	}

	private static int rank(Resident r) {
		int job = r.job == JobType.GUARD ? 0 : r.job == JobType.HUNTER ? 1 : 2;
		return job * 2 + (NpcData.of(r).traits().contains(Trait.BRAVE) ? 0 : 1);
	}

	/** Their daily wage: twice their station's (at least a bit). */
	public static long wage(Resident r, PlaceConfig cfg) {
		return Math.max(1, Math.round(cfg.wage(r.station) * cfg.companions().wageFactor()));
	}

	/** The hiring fee: five days of that wage. */
	public static long fee(Resident r, PlaceConfig cfg) {
		return wage(r, cfg) * cfg.companions().feeDays();
	}

	// ---------------------------------------------------------------- the wage

	public enum Payday {
		/** Paid for the day. */
		PAID,
		/** Could not be paid; they stay a while yet. */
		UNPAID,
		/** Unpaid too long: they go home. */
		LEFT,
		/** The player was not about (offline): the day is neither charged nor worked. */
		SKIPPED,
		/** Already settled for that day. */
		SETTLED
	}

	/**
	 * A day of the home town passed ({@code DAY_PASSED}): the wage is taken at dawn from the coin the player carries.
	 * Offline days are not charged and not worked. Unpaid {@code unpaid_days} days running, they go home.
	 *
	 * @param pay takes the wage from the player; false if they could not pay
	 */
	public static Payday payday(CompanionState st, long day, boolean online, LongPredicate pay, PlaceConfig cfg) {
		if (day <= st.paidDay()) {
			return Payday.SETTLED;
		}
		if (!online) {
			// not worked: the next day the player is about is the next one owed
			st.setPaidDay(day);
			return Payday.SKIPPED;
		}
		st.setPaidDay(day);
		if (pay.test(st.wage())) {
			st.setUnpaid(0);
			st.addLoyalty(1);
			return Payday.PAID;
		}
		st.setUnpaid(st.unpaid() + 1);
		st.addLoyalty(-10);
		return st.unpaid() >= cfg.companions().unpaidDays() ? Payday.LEFT : Payday.UNPAID;
	}

	// ---------------------------------------------------------------- where they are

	/** What becomes of an online player's companion this pass. */
	public enum Presence {
		/** Made flesh beside the player (logged in, folded by a chunk unloading, left behind). */
		EMBODY_NEAR,
		/** Made flesh where they were told to wait, the player having come near it again. */
		EMBODY_AT_WAIT,
		/** Folded to wait as a record: the server is struggling and they are far from the player. */
		SHED,
		/** Left behind in another dimension too long: they go home. */
		GO_HOME,
		/** As they are (at the player's side, waiting folded where told, waiting for room, apart for now). */
		KEEP
	}

	/**
	 * Where an online player's companion should be (logged-off players' companions are folded at logout and wait as
	 * records until the next login). Folk never cross dimensions: with the player in another one they wait, and after
	 * {@code wait_ticks} go home.
	 *
	 * @param apartFor ticks the player has been in another dimension (0 when here)
	 * @param shedding the server is over its tick budget and over its companion cap
	 * @param far      embodied and more than 48 blocks from the player
	 * @param room     the companion cap (shrunk with the tick time) has room for one more
	 */
	public static Presence presence(boolean sameDimension, boolean embodied, boolean toldToWait, boolean nearWaitSpot, long apartFor,
		boolean shedding, boolean far, boolean room, PlaceConfig cfg) {
		if (!sameDimension) {
			return apartFor > cfg.companions().waitTicks() ? Presence.GO_HOME : Presence.KEEP;
		}
		if (embodied) {
			return shedding && far ? Presence.SHED : Presence.KEEP;
		}
		if (!room) {
			return Presence.KEEP;
		}
		if (toldToWait) {
			return nearWaitSpot ? Presence.EMBODY_AT_WAIT : Presence.KEEP;
		}
		return Presence.EMBODY_NEAR;
	}

	/** The resident's kin in their town (spouse, parents, children, by the links their records keep): who mourns them. */
	public static List<Resident> kin(Settlement s, Resident r) {
		NpcData me = NpcData.of(r);
		List<Resident> out = new ArrayList<>();
		for (Resident o : s.residents()) {
			if (o.id.equals(r.id)) {
				continue;
			}
			NpcData d = NpcData.of(o);
			if (d.spouse().filter(r.id::equals).isPresent() || d.parents().contains(r.id) || me.parents().contains(o.id)
				|| me.spouse().filter(o.id::equals).isPresent() || me.children().contains(o.id)) {
				out.add(o);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- how they take what the player does

	/**
	 * What a crime of the player's does to a companion's loyalty: theft or worse in their own town costs a good deal;
	 * elsewhere it costs a little, and the greedy do not mind at all.
	 *
	 * @param severity the crime's weight, 1 (trespass) … 10 (murder)
	 */
	public static int crimeLoyalty(boolean ownTown, boolean greedy, int severity) {
		int s = Math.max(1, Math.min(10, severity));
		if (ownTown) {
			return -Math.min(60, 10 + 5 * s);
		}
		return greedy ? 0 : -Math.min(20, 2 * s);
	}

	/** Whether a companion of this loyalty leaves. */
	public static boolean leaves(CompanionState st, PlaceConfig cfg) {
		return st.loyalty() < cfg.companions().leaveBelow();
	}
}
