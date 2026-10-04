package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.model.SimRules;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/**
 * A household's place in society follows its means (docs/LIVING_WORLD.md §8.1): a family whose trade does well moves up
 * a station (peasant to artisan, artisan to merchant, a rich merchant house in a town to the gentry) and one fallen on
 * hard times moves down, slowly — a household is looked at every {@code period_days} (staggered), and moves one step only
 * after two looks agree. Clergy, scholars, officials and the lord keep their calling.
 *
 * <p>Means come from the purse the economy module keeps for each resident ({@code Resident.extra("economy").purse}, in
 * bits) when there is one; otherwise from what the household's trades earn, scaled by the town's prosperity.
 */
public final class Stations {
	/** Where the economy module keeps a resident's purse, in bits. */
	public static final String PURSE_MODULE = "economy";
	public static final String PURSE_KEY = "purse";
	/** The stations a purse moves a household along, poorest first. */
	private static final Station[] LADDER = {Station.POOR, Station.PEASANT, Station.ARTISAN, Station.MERCHANT, Station.GENTRY};
	/** The purse a household of each rung keeps per adult, in bits (the economy's wages and prices make these typical). */
	private static final long[] TYPICAL_PURSE = {15, 60, 150, 450, 1500};
	private static final String PUSH = "station_push";
	private static final String BASE = "station_base";

	private Stations() {
	}

	static void daily(SimSettlement s, SimRules r, long day) {
		SimConfig.Stations cfg = r.resources.stations();
		if (!cfg.enabled() || s.ctx().economyHandlesWages()) {
			// with the economy module running, its purse rule (economy/purse/StationRule) moves stations: one owner
			return;
		}
		Int2ObjectLinkedOpenHashMap<List<Resident>> homes = new Int2ObjectLinkedOpenHashMap<>();
		List<List<Resident>> households = new ArrayList<>();
		for (Resident res : s.residents.values()) {
			if (res.home == Resident.NO_PLOT) {
				households.add(List.of(res));
			} else {
				homes.computeIfAbsent(res.home, k -> new ArrayList<>()).add(res);
			}
		}
		households.addAll(homes.values());
		double prosperity = s.land().prosperity();
		for (List<Resident> household : households) {
			Resident head = household.get(0);
			// staggered: each household comes round once a period
			if (Math.floorMod(head.id.hashCode() + day, cfg.periodDays()) != 0) {
				continue;
			}
			review(s, household, cfg, r, prosperity);
		}
	}

	/** One household's look: where its means put it, and one step toward that if the last look said the same. */
	static void review(SimSettlement s, List<Resident> household, SimConfig.Stations cfg, SimRules r, double prosperity) {
		Station base = Station.POOR;
		int adults = 0;
		long purse = 0;
		boolean purses = false;
		double earnings = 0;
		double typical = 0;
		Resident head = null;
		for (Resident res : household) {
			if (res.age == AgeStage.CHILD) {
				continue;
			}
			adults++;
			Station st = Station.of(res.job);
			if (movable(st) && rank(st) > rank(base)) {
				base = st;
			}
			if (head == null && movable(res.station)) {
				head = res;
			}
			CompoundTag eco = res.extra.getCompoundOrEmpty(PURSE_MODULE);
			if (eco.contains(PURSE_KEY)) {
				purses = true;
				purse += eco.getLongOr(PURSE_KEY, 0);
			}
			earnings += r.coin[res.job.ordinal()];
			typical += Math.max(1, r.coin[res.job.ordinal()]);
		}
		if (head == null) {
			head = household.get(0);
		}
		Station now = movable(head.station) ? head.station : base;
		int lastBase = head.extra("sim").getIntOr(BASE, -1);
		if (lastBase != rank(base)) {
			// the household's trades changed (a labourer became a smith): it takes the station they bring at once; only
			// its means move it from there, slowly
			now = base;
			head.extra("sim").putInt(BASE, rank(base));
			head.extra("sim").remove(PUSH);
		}
		Station target = now;
		if (adults > 0) {
			boolean homeless = head.home == Resident.NO_PLOT;
			double ratio;
			if (purses) {
				ratio = purse / (double) adults / TYPICAL_PURSE[rank(base)];
			} else {
				ratio = earnings * (0.5 + prosperity) / Math.max(1, typical);
				if (homeless) {
					ratio *= 0.4;
				}
			}
			int at = rank(base);
			if (ratio >= cfg.gentryAt() && at >= rank(Station.MERCHANT) && s.tier().ordinal() >= SettlementTier.TOWN.ordinal()) {
				at = rank(Station.GENTRY);
			} else if (ratio >= cfg.upAt()) {
				at = Math.min(rank(Station.MERCHANT), at + 1);
			} else if (ratio <= cfg.downAt() || homeless && base == Station.POOR) {
				at = Math.max(0, at - 1);
			}
			target = LADDER[at];
		}
		Station next = now;
		if (rank(target) != rank(now)) {
			int push = head.extra("sim").getIntOr(PUSH, 0);
			int dir = Integer.signum(rank(target) - rank(now));
			if (push * dir > 0) {
				// the second look in a row that says so: one step
				next = LADDER[rank(now) + dir];
				head.extra("sim").remove(PUSH);
			} else {
				head.extra("sim").putInt(PUSH, dir);
			}
		} else {
			head.extra("sim").remove(PUSH);
		}
		for (Resident res : household) {
			// a calling stays: the priest is clergy, the reeve an official, whatever the household's means
			Station st = res.age != AgeStage.CHILD && !movable(Station.of(res.job)) ? Station.of(res.job) : next;
			if (res.station != st) {
				res.station = st;
				s.markDirty();
			}
		}
	}

	private static boolean movable(Station st) {
		return st == Station.POOR || st == Station.PEASANT || st == Station.ARTISAN || st == Station.MERCHANT || st == Station.GENTRY;
	}

	private static int rank(Station st) {
		for (int i = 0; i < LADDER.length; i++) {
			if (LADDER[i] == st) {
				return i;
			}
		}
		return 0;
	}
}
