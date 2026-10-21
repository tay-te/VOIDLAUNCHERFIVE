package dev.voidmc.hearth.social.burgher;

import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The burghers' rules (docs/LIVING_WORLD_2.md §8.1–§8.2), pure: who may swear the oath, the fee, the price a burgher pays
 * at market, who holds property since when, and the council's sitting and choice.
 */
public final class BurgherRules {
	/** Bits in a crown (the economy's gold coin). */
	public static final long CROWN = 100;

	private BurgherRules() {
	}

	public enum Oath {
		OK, ALREADY, NOT_OWNER, STANDING, NOT_LONG_ENOUGH, NOT_MARKET_DAY
	}

	/**
	 * Whether a player may swear the burgher's oath today.
	 *
	 * @param ownedSince  the first day they were seen holding property here, or null if they hold none
	 * @param marketToday whether today is the town's market day
	 * @param keepsMarket whether the town keeps a market day at all (a hamlet without one swears any day)
	 */
	public static Oath check(BurgherBook book, UUID player, int standing, Long ownedSince, long today, boolean marketToday, boolean keepsMarket,
		PlaceConfig.Burghers cfg) {
		if (book.isSworn(player)) {
			return Oath.ALREADY;
		}
		if (ownedSince == null) {
			return Oath.NOT_OWNER;
		}
		if (standing < cfg.minStanding()) {
			return Oath.STANDING;
		}
		if (today - ownedSince < cfg.heldDays()) {
			return Oath.NOT_LONG_ENOUGH;
		}
		if (cfg.marketDayOnly() && keepsMarket && !marketToday) {
			return Oath.NOT_MARKET_DAY;
		}
		return Oath.OK;
	}

	/** The burgher's fee: a crown a tier (a camp one, a hamlet two … a city five, times the config's crowns). */
	public static long fee(SettlementTier tier, PlaceConfig.Burghers cfg) {
		return CROWN * cfg.feeCrowns() * (tier.ordinal() + 1);
	}

	/** A sworn burgher's market dues are waived: the factor on what they pay (buying) or are paid (selling). */
	public static double priceFactor(boolean sworn, boolean playerBuys, PlaceConfig.Burghers cfg) {
		if (!sworn) {
			return 1.0;
		}
		return playerBuys ? 1.0 - cfg.dues() : 1.0 + cfg.dues();
	}

	/**
	 * Keeps the book's owners in step with the town's deeds: new owners from {@code day}, those who sold out forgotten.
	 * Returns whether anything changed.
	 */
	public static boolean owners(BurgherBook book, Settlement s, long day) {
		Set<UUID> now = new HashSet<>();
		for (PlotView p : s.plots()) {
			if (p.owner() != null) {
				now.add(p.owner());
			}
		}
		boolean changed = book.owners.keySet().retainAll(now);
		for (UUID u : now) {
			if (book.owners.putIfAbsent(u, day) == null) {
				changed = true;
			}
		}
		return changed;
	}

	/** Whether the council sits today: a rest day at least {@code council_days} after its last sitting, with burghers sworn. */
	public static boolean councilDue(BurgherBook book, long day, boolean restDay, PlaceConfig.Burghers cfg) {
		if (book.sworn.isEmpty() || !restDay) {
			return false;
		}
		return book.lastSitting == Long.MIN_VALUE || day - book.lastSitting >= cfg.councilDays();
	}

	/** Opens the council's sitting with these public works on the table. */
	public static void sit(BurgherBook book, long day, List<Integer> table) {
		book.sitting = day;
		book.table.clear();
		book.table.addAll(table);
		book.votes.clear();
	}

	/** A sworn burgher's vote at the sitting; false if they may not vote (not sworn, not sitting, not on the table). */
	public static boolean vote(BurgherBook book, UUID player, int plot, long day) {
		if (!book.isSworn(player) || !book.sittingOn(day) || !book.table.contains(plot)) {
			return false;
		}
		book.votes.put(player, plot);
		return true;
	}

	/**
	 * Closes the sitting: the work with the most votes (ties to the earliest on the table) is the council's choice, or -1
	 * if nobody voted.
	 */
	public static int close(BurgherBook book) {
		Map<Integer, Integer> tally = new LinkedHashMap<>();
		for (int plot : book.table) {
			tally.put(plot, 0);
		}
		for (int plot : book.votes.values()) {
			tally.merge(plot, 1, Integer::sum);
		}
		int best = -1;
		int bestVotes = 0;
		for (Map.Entry<Integer, Integer> e : tally.entrySet()) {
			if (e.getValue() > bestVotes) {
				best = e.getKey();
				bestVotes = e.getValue();
			}
		}
		book.lastSitting = book.sitting;
		book.sitting = Long.MIN_VALUE;
		book.table.clear();
		book.votes.clear();
		if (best >= 0) {
			book.chosen = best;
		}
		return best;
	}
}
