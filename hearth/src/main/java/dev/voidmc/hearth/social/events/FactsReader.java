package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementCalendar;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.office.Office;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Gathers a town's {@link TownFacts} when its day turns: its people, its places (from its plots, read once and cached
 * until a building is finished), its calendar, the lord's domain it lies in and the bands on its roads. The domains and
 * the bands are worked out once a sim day per world and shared by every town in it.
 */
public final class FactsReader {
	/** Places by settlement, until a building is finished or the town is forgotten. */
	private static final Map<Long, Set<String>> PLACES = new HashMap<>();
	private static final Map<Long, int[]> DEFENCE = new HashMap<>();
	/** Domain seats by world (dimension key) and the day they were worked out. */
	private static final Map<String, Object[]> SEATS = new HashMap<>();
	private static final Map<Long, Long> SEAT_OF = new HashMap<>();

	/** Time spent finding domains and bands (the bench's breakdown). */
	static long SEAT_NANOS;
	static long THREAT_NANOS;

	private FactsReader() {
	}

	static void clear() {
		ROAD.clear();
		YEAR_DAYS.clear();
		PLACES.clear();
		DEFENCE.clear();
		SEATS.clear();
		SEAT_OF.clear();
	}

	/** A building was finished (or lost): the town's places are read again. */
	static void forget(long settlementId) {
		PLACES.remove(settlementId);
		DEFENCE.remove(settlementId);
	}

	public static TownFacts read(HearthWorld w, Settlement s, long day) {
		SocialConfig cfg = SocialModule.config();
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		int adults = 0;
		int guards = 0;
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.CHILD) {
				adults++;
			}
			if (r.job == JobType.GUARD && r.age == AgeStage.ADULT) {
				guards++;
			}
		}
		boolean lord = ledger.offices.containsKey(Office.LORD.id());
		boolean judge = ledger.offices.containsKey(Office.JUDGE.id()) || ledger.offices.containsKey(Office.REEVE.id());
		SettlementCalendar cal = HearthServices.calendar();
		boolean market = cal.marketDay(s, day);
		boolean festival = TownCalendar.festivalOn(day, s.culture(), s.foundedDay(), s.tier(), cfg.festivals()) != null;
		boolean rest = cal.restDay(s, day);
		// the lord holds court on any day without a market or a feast (as the agenda has it); a judge or reeve once a week
		boolean court = lord ? !market && !festival : judge && TownCalendar.weekday(s.id(), day, cfg.market().weekDays()) == 2 && !festival;
		boolean fair = fairDay(s, day);
		boolean tourney = tourneyDay(s, day);
		Set<String> places = places(s);
		int[] def = DEFENCE.computeIfAbsent(s.id(), k -> defenceWorks(s));
		int defence = RaidRules.defence(guards, def[0] > 0, def[1] > 0);
		long t0 = System.nanoTime();
		long seat = seatOf(w, s, day);
		long t1 = System.nanoTime();
		RaidRules.BandView band = threat(w, s, defence, day);
		SEAT_NANOS += t1 - t0;
		THREAT_NANOS += System.nanoTime() - t1;
		int year = Math.max(8, cfg.festivals().yearDays());
		int season = (int) Math.min(3, Math.floorMod(day, (long) year) * 4 / year);
		return new TownFacts(s.id(), s.tier(), s.population(), adults, s.prosperity(), s.culture(), timber(s.culture()), lord, court, festival, market, rest,
			fair, tourney, seat, band == null ? 0 : band.strength(), band == null ? 0 : band.id(), defence, places, season);
	}

	/** How much a culture builds in timber and thatch (fires catch there), 0–1. */
	public static double timber(Culture c) {
		return switch (c) {
			case NORTHERN, TROPICAL, BAYOU -> 0.7;
			case TEMPERATE, ALPINE, WISTERIA -> 0.6;
			case TUNDRA, MARITIME, KARST -> 0.5;
			case MOORLAND, STEPPE -> 0.4;
			case MEDITERRANEAN -> 0.2;
			case MESA -> 0.15;
			case DESERT -> 0.1;
		};
	}

	static Set<String> places(Settlement s) {
		return PLACES.computeIfAbsent(s.id(), k -> {
			Set<String> out = new HashSet<>();
			for (PlotView p : s.plots()) {
				if (p.state() != PlotState.BUILT) {
					continue;
				}
				String path = p.spec().archetype().getPath();
				switch (path) {
					case "civic/chapel", "civic/monastery", "civic/convent", "civic/shrine" -> out.add("chapel");
					case "civic/cemetery" -> out.add("cemetery");
					case "civic/tilt_yard" -> out.add("tilt_yard");
					case "civic/town_hall" -> out.add("town_hall");
					case "civic/playhouse" -> out.add("stage");
					case "law/courthouse" -> out.add("courthouse");
					case "law/pillory" -> out.add("pillory");
					case "law/gallows" -> out.add("gallows");
					case "landscape/well", "landscape/fountain" -> out.add("well");
					case "landscape/festival_ground" -> out.add("festival_ground");
					case "landscape/plaza" -> out.add("plaza");
					case "work/barn" -> out.add("barn");
					case "storage/storehouse", "storage/granary", "storage/warehouse" -> out.add("storehouse");
					case "defense/gatehouse" -> out.add("gate");
					default -> {
					}
				}
			}
			if (!s.pois(PoiType.STAGE).isEmpty()) {
				out.add("stage");
			}
			if (!s.pois(PoiType.ALTAR).isEmpty()) {
				out.add("chapel");
			}
			if (!s.pois(PoiType.PILLORY).isEmpty()) {
				out.add("pillory");
			}
			if (!s.pois(PoiType.GALLOWS).isEmpty()) {
				out.add("gallows");
			}
			if (!s.pois(PoiType.THRONE).isEmpty()) {
				out.add("throne");
			}
			return out;
		});
	}

	/** {walls, keep}: whether the town has a wall (or palisade, or gatehouse) and a keep (or castle, or palace). */
	private static int[] defenceWorks(Settlement s) {
		int walls = 0;
		int keep = 0;
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.BUILT) {
				continue;
			}
			String path = p.spec().archetype().getPath();
			if (path.equals("defense/wall") || path.equals("defense/palisade") || path.equals("defense/gatehouse")) {
				walls++;
			} else if (path.equals("noble/keep") || path.equals("noble/castle") || path.equals("noble/palace")) {
				keep++;
			}
		}
		return new int[]{walls, keep};
	}

	// ---------------------------------------------------------------- domains

	/** The seat whose domain a town is in, worked out for every town of the world once a week. */
	static long seatOf(HearthWorld w, Settlement s, long day) {
		String world = w.level().dimension().identifier().toString();
		Object[] cached = SEATS.get(world);
		long week = Math.floorDiv(day, 7);
		if (cached == null || (long) cached[0] != week) {
			List<Domains.Seat> seats = new ArrayList<>();
			for (Settlement t : w.settlements()) {
				if (t.population() > 0 && t.data(SocialModule.LEDGER).offices.containsKey(Office.LORD.id())) {
					seats.add(new Domains.Seat(t.id(), t.center()));
				}
			}
			// the towns' seats are worked out again only when the lords' seats have changed, or once a season
			boolean same = cached != null && seats.equals(cached[1]) && week - (long) cached[2] < 8;
			SEATS.put(world, new Object[]{week, seats, same ? (long) cached[2] : week});
			if (!same) {
				for (Settlement t : w.settlements()) {
					SEAT_OF.remove(t.id());
				}
			}
			cached = SEATS.get(world);
		}
		Long known = SEAT_OF.get(s.id());
		if (known != null) {
			return known;
		}
		@SuppressWarnings("unchecked")
		List<Domains.Seat> seats = (List<Domains.Seat>) cached[1];
		RoadNetwork roads = w.roads();
		boolean roadsKnown = roads != RoadNetwork.NONE;
		List<Domains.Seat> near = new ArrayList<>();
		for (Domains.Seat seat : seats) {
			// a road is never shorter than the crow's flight: only seats within reach that way are asked about
			if (seat.id() == s.id() || seat.pos().distSqr(s.center()) <= Domains.REACH * Domains.REACH) {
				near.add(seat);
			}
		}
		long seat = Domains.seatOf(s.id(), s.center(), near, (a, b) -> road(w, roads, a, b, day), roadsKnown);
		SEAT_OF.put(s.id(), seat);
		return seat;
	}

	// ---------------------------------------------------------------- bands

	private static final Map<String, Object[]> BANDS = new HashMap<>();
	/** Road distances asked this week, by the pair (the router's search is not free; roads change slowly). */
	private static final Map<Long, Double> ROAD = new HashMap<>();
	private static long roadWeek = Long.MIN_VALUE;

	static double road(HearthWorld w, RoadNetwork roads, long a, long b, long day) {
		long week = Math.floorDiv(day, 7);
		if (week != roadWeek || ROAD.size() > 8192) {
			ROAD.clear();
			roadWeek = week;
		}
		long key = Math.min(a, b) * 0x9E3779B97F4A7C15L ^ Math.max(a, b);
		Double d = ROAD.get(key);
		if (d == null) {
			d = roads.roadDistance(a, b);
			ROAD.put(key, d);
		}
		return d;
	}

	/** A town's fair and tournament days of the year, worked out once (they never move). */
	private static final Map<Long, int[]> YEAR_DAYS = new HashMap<>();

	static boolean fairDay(Settlement s, long day) {
		int[] d = yearDays(s);
		int year = Math.max(8, SocialModule.config().festivals().yearDays());
		int at = (int) Math.floorMod(day, (long) year);
		return at == d[0] || at == Math.floorMod(d[0] + 1, year);
	}

	static boolean tourneyDay(Settlement s, long day) {
		int year = Math.max(8, SocialModule.config().festivals().yearDays());
		return (int) Math.floorMod(day, (long) year) == yearDays(s)[1];
	}

	private static int[] yearDays(Settlement s) {
		long key = s.id() * 8 + s.tier().ordinal();
		return YEAR_DAYS.computeIfAbsent(key, k -> {
			var cfg = SocialModule.config().festivals();
			return new int[]{TownCalendar.fairYearDay(s.id(), s.culture(), s.foundedDay(), s.tier(), cfg),
				TownCalendar.tourneyYearDay(s.id(), s.culture(), s.foundedDay(), s.tier(), cfg)};
		});
	}

	/** The boldest band within reach of the town, stronger than its defence, or null. */
	static RaidRules.@Nullable BandView threat(HearthWorld w, Settlement s, int defence, long day) {
		List<RaidRules.BandView> bands = bands(w, day);
		if (bands.isEmpty()) {
			return null;
		}
		RoadNetwork roads = w.roads();
		List<RaidRules.BandView> near = new ArrayList<>();
		double edge = Math.max(32, s.territoryRadius() / 2.0);
		// a road is never shorter than the crow's flight (the camp is off the road by a little): far bands are not asked about
		double reach = RaidRules.REACH + edge + 200;
		for (RaidRules.BandView b : bands) {
			double dx = b.x() - s.center().getX();
			double dz = b.z() - s.center().getZ();
			if (b.strength() > defence && dx * dx + dz * dz <= reach * reach) {
				near.add(b);
			}
		}
		if (near.isEmpty()) {
			return null;
		}
		return RaidRules.threat(s.id(), edge, defence, near, (a, c) -> road(w, roads, a, c, day));
	}

	static List<RaidRules.BandView> bands(HearthWorld w, long day) {
		String world = w.level().dimension().identifier().toString();
		Object[] cached = BANDS.get(world);
		if (cached != null && (long) cached[0] == day) {
			@SuppressWarnings("unchecked")
			List<RaidRules.BandView> list = (List<RaidRules.BandView>) cached[1];
			return list;
		}
		List<RaidRules.BandView> out = RoadBands.of(w);
		BANDS.put(world, new Object[]{day, out});
		return out;
	}
}
