package dev.voidmc.hearth.sim.roads;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementCalendar;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.TravelService;
import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who is on the roads of one level (docs/LIVING_WORLD_2.md §1.2, "Traffic" and "Bandits"), world data
 * {@code hearth:sim/traffic}: every journey as a record whose place at any tick is a pure function of it
 * ({@link Journey#along}), and the bandit bands on the lonely roads.
 *
 * <p>Journeys are sent on the sending town's {@code DAY_PASSED} from its state (§1.7): carters between a hamlet and
 * its parent, pedlars to the next market, pilgrims on feast days, patrols from towns with guards, market goers to a
 * neighbour's market; caravans, messengers and settlers come from the sim's own books (its caravans, the standing
 * module's news, a hamlet's founding) and keep the books' times. Residents on a journey are away from their town until
 * it ends. Once a day each band may waylay one journey on its road: the outcome is decided here, in the books, and the
 * party on the road only enacts it. Cheap: arrivals and ambushes are checked when the soonest one is due; nothing is
 * done per tick per traveller.
 */
public final class Traffic {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");

	/** The books. */
	public static final class Book {
		final List<Journey> journeys = new ArrayList<>();
		final List<Band> bands = new ArrayList<>();
		/** Road id to the last day a patrol walked it. */
		final Map<Long, Long> patrolled = new HashMap<>();
		/** Road id to the day a band was broken on it. */
		final Map<Long, Long> broken = new HashMap<>();
		long nextId = 1;
		long lastDay = Long.MIN_VALUE;
		long arrived;
		long waylaid;
		long killed;

		static final Codec<Map<Long, Long>> DAYS = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(m -> {
			Map<Long, Long> out = new HashMap<>();
			m.forEach((k, v) -> {
				try {
					out.put(Long.parseUnsignedLong(k, 16), v);
				} catch (NumberFormatException ignored) {
					// a hand-edited key
				}
			});
			return out;
		}, m -> {
			Map<String, Long> out = new HashMap<>();
			m.forEach((k, v) -> out.put(Long.toHexString(k), v));
			return out;
		});

		public static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Journey.CODEC.listOf().optionalFieldOf("journeys", List.of()).forGetter(b -> b.journeys),
			Band.CODEC.listOf().optionalFieldOf("bands", List.of()).forGetter(b -> b.bands),
			DAYS.optionalFieldOf("patrolled", Map.of()).forGetter(b -> b.patrolled),
			DAYS.optionalFieldOf("broken", Map.of()).forGetter(b -> b.broken),
			Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(b -> b.nextId),
			Codec.LONG.optionalFieldOf("last_day", Long.MIN_VALUE).forGetter(b -> b.lastDay),
			Codec.LONG.optionalFieldOf("arrived", 0L).forGetter(b -> b.arrived),
			Codec.LONG.optionalFieldOf("waylaid", 0L).forGetter(b -> b.waylaid),
			Codec.LONG.optionalFieldOf("killed", 0L).forGetter(b -> b.killed)
		).apply(i, (j, b, p, br, next, last, arr, way, kill) -> {
			Book book = new Book();
			book.journeys.addAll(j);
			book.bands.addAll(b);
			book.patrolled.putAll(p);
			book.broken.putAll(br);
			book.nextId = next;
			book.lastDay = last;
			book.arrived = arr;
			book.waylaid = way;
			book.killed = kill;
			return book;
		}));
	}

	public static final HearthDataKey<Book> KEY = HearthDataKey.register(Hearth.id("sim/traffic"), HearthDataKey.Scope.WORLD, Book.CODEC, Book::new);

	/** The api's travel service over every level's traffic. */
	public static final TravelService SERVICE = new Service();

	private final SimWorld world;
	private final RoadWorld net;
	private final LiveRoads live;
	private long nextDue = Long.MIN_VALUE;
	private final Map<Long, Geometry> geometry = new HashMap<>();
	private final Map<Long, Float> cover = new HashMap<>();
	private final Map<Long, Boolean> holy = new HashMap<>();
	private long holyDay = Long.MIN_VALUE;
	private final EnumMap<TravelKind, Integer> sent = new EnumMap<>(TravelKind.class);
	private int refusedForCaps;

	public Traffic(SimWorld world, RoadWorld net, LiveRoads live) {
		this.world = world;
		this.net = net;
		this.live = live;
		net.setDanger(this::danger);
	}

	public Book book() {
		return this.world.data(KEY);
	}

	public SimWorld world() {
		return this.world;
	}

	public RoadWorld net() {
		return this.net;
	}

	public List<Journey> journeys() {
		return List.copyOf(book().journeys);
	}

	public List<Band> bands() {
		return List.copyOf(book().bands);
	}

	public @Nullable Journey journey(long id) {
		for (Journey j : book().journeys) {
			if (j.id() == id) {
				return j;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- the tick

	/** Every tick: a new day's rolls, and whatever is due (an ambush, an arrival) when the soonest of them is. */
	public void tick() {
		long now = this.world.clock();
		Book b = book();
		long day = Math.floorDiv(now, SimRules.DAY);
		if (day != b.lastDay) {
			b.lastDay = day;
			newDay(day, now);
			this.nextDue = Long.MIN_VALUE;
			this.world.markDirty();
		}
		if (this.nextDue == Long.MIN_VALUE) {
			this.nextDue = soonest();
		}
		if (now < this.nextDue) {
			return;
		}
		for (Journey j : List.copyOf(b.journeys)) {
			if (j.waylay() <= now && !j.outcome().isEmpty()) {
				waylaid(j, now);
			}
		}
		for (Journey j : List.copyOf(b.journeys)) {
			if (j.arrive() <= now && b.journeys.contains(j)) {
				arrive(j);
			}
		}
		this.nextDue = soonest();
	}

	private long soonest() {
		long best = Long.MAX_VALUE;
		for (Journey j : book().journeys) {
			best = Math.min(best, j.arrive());
			if (!j.outcome().isEmpty()) {
				best = Math.min(best, j.waylay());
			}
		}
		return best;
	}

	private void changed() {
		this.nextDue = Long.MIN_VALUE;
		this.world.markDirty();
	}

	// ---------------------------------------------------------------- sending

	/** How important a kind is (0 most): when the roads are full, the least important are not sent. */
	static int rank(TravelKind k) {
		return switch (k) {
			case CARAVAN, MESSENGER, SETTLERS -> 0;
			case TAX_COLLECTOR, DIGNITARY, WEDDING_GUESTS, TROUPE, ROAD_CREW -> 1;
			case PATROL, CARTER -> 2;
			case PILGRIMS, MARKET_GOERS -> 3;
			case PEDLAR, BANDITS -> 4;
		};
	}

	/** Whether there is room on the roads for a journey of this kind. */
	boolean room(TravelKind kind) {
		int cap = Roads.config().traffic().maxJourneys();
		return book().journeys.size() < cap * (1.0 - 0.1 * rank(kind));
	}

	/** The pace of a kind (blocks a tick). */
	static double pace(TravelKind kind) {
		return switch (kind) {
			case MESSENGER, DIGNITARY -> Journey.RIDE;
			case CARAVAN, CARTER, SETTLERS, MARKET_GOERS, TAX_COLLECTOR -> Journey.CART;
			default -> Journey.WALK;
		};
	}

	/** The way from one place to another over the roads known now: the path (signed ids) and its length, or null. */
	@Nullable Object[] path(long from, long to, BlockPos a, BlockPos b) {
		List<Way> ways = this.net.path(from, to, 6);
		if (ways == null || ways.isEmpty()) {
			return null;
		}
		long[] ids = new long[ways.size()];
		double len = 0;
		long at = from;
		for (int i = 0; i < ways.size(); i++) {
			Way w = ways.get(i);
			ids[i] = w.a() == at ? w.id() : -w.id();
			len += w.length();
			at = w.other(at);
		}
		return new Object[]{ids, len};
	}

	/**
	 * Sends a journey. Residents named go away from their town until it ends.
	 *
	 * @param arrive   when it must arrive (the books' time: a caravan, a messenger), or 0 to walk it at the kind's pace
	 * @param outLength for a round trip that turns before the far end (a patrol), how far it walks out; else 0
	 */
	public long send(TravelKind kind, SimSettlement from, @Nullable Settlement to, List<UUID> residents, long depart, long arrive, boolean roundTrip,
		double outLength, long stay, CompoundTag details) {
		if (!room(kind)) {
			this.refusedForCaps++;
			return 0;
		}
		long toId = to == null ? from.id() : to.id();
		BlockPos b = to == null ? from.center() : to.center();
		long[] ids;
		double len;
		Object[] p = to == null || to.id() == from.id() ? null : path(from.id(), toId, from.center(), b);
		if (p != null) {
			ids = (long[]) p[0];
			len = (double) p[1];
		} else if (to != null && to.id() != from.id()) {
			// no road known between them (yet): across country, a quarter further than the crow flies
			ids = new long[0];
			len = Math.sqrt(from.center().distSqr(b)) * 1.25;
		} else {
			// out along one of its own roads and back (a patrol)
			List<Way> own = this.net.waysFrom(from.id());
			if (own.isEmpty()) {
				return 0;
			}
			Way w = own.get((int) Math.floorMod(depart / SimRules.DAY + kind.ordinal() + (depart % SimRules.DAY > 6000 ? 1 : 0), own.size()));
			ids = new long[]{w.a() == from.id() ? w.id() : -w.id()};
			len = Math.min(w.length(), Math.max(80, outLength));
		}
		if (outLength > 0 && to != null && to.id() != from.id()) {
			len = Math.min(len, outLength);
		}
		double pace = pace(kind);
		long end;
		long turn = 0;
		if (arrive > depart) {
			pace = Journey.paceFor(depart, arrive, roundTrip ? len * 2 : len);
			end = arrive;
			turn = roundTrip ? Journey.afterWalking(depart, (long) Math.ceil(len / pace)) : 0;
		} else if (roundTrip) {
			long out = Journey.arrival(depart, len, pace);
			turn = out + Math.max(0, stay);
			end = Journey.arrival(turn, len, pace);
		} else {
			end = Journey.arrival(depart, len, pace);
		}
		Book book = book();
		long id = book.nextId++;
		Journey j = new Journey(id, kind, from.id(), toId, ids, len, depart, pace, end, List.copyOf(residents), roundTrip, turn, Long.MAX_VALUE, 0, "",
			details.copy());
		book.journeys.add(j);
		for (UUID r : residents) {
			from.setResidentAway(r, end, "travel:" + kind.name().toLowerCase(Locale.ROOT));
		}
		this.sent.merge(kind, 1, Integer::sum);
		changed();
		SettlementEvents.TRAVELLER.invoker().onTraveller(this.world, kind, id, from.id(), toId, SettlementEvents.Traveller.Phase.DEPARTED);
		return id;
	}

	/** Adults of a town free to go: not embodied, not away already, of the trades asked for first. */
	static List<UUID> pick(SimSettlement s, int count, JobType... prefer) {
		List<Resident> free = new ArrayList<>();
		for (Resident r : s.residentList()) {
			if (r.age == AgeStage.ADULT && !s.isResidentOut(r.id)) {
				free.add(r);
			}
		}
		free.sort((x, y) -> Integer.compare(rankJob(x.job, prefer), rankJob(y.job, prefer)));
		List<UUID> out = new ArrayList<>();
		for (Resident r : free) {
			if (out.size() >= count) {
				break;
			}
			if (prefer.length > 0 && rankJob(r.job, prefer) == prefer.length && out.size() >= Math.max(1, count / 2)) {
				break;
			}
			out.add(r.id);
		}
		return out;
	}

	private static int rankJob(JobType j, JobType[] prefer) {
		for (int i = 0; i < prefer.length; i++) {
			if (prefer[i] == j) {
				return i;
			}
		}
		return prefer.length;
	}

	/** A day passed for a settlement: the journeys its state sends (§1.7). */
	public void dayPassed(SimSettlement s, long day) {
		if (s.population() == 0 || !this.net.enabled()) {
			return;
		}
		RoadsConfig.Traffic cfg = Roads.config().traffic();
		long dayStart = day * SimRules.DAY;
		long now = this.world.clock();
		if (now - dayStart > SimRules.DAY * 2) {
			// a town catching up on days long past sends nobody into them
			return;
		}
		RandomSource random = RandomSource.create(s.id() * 31 + day);
		SimSettlement parent = s.clockState().parent() == 0 ? null : this.world.settlement(s.clockState().parent());
		// carters: a hamlet sends its goods to the town that founded it, every day
		if (parent != null && s.tier().ordinal() <= SettlementTier.HAMLET.ordinal() && !onRoad(TravelKind.CARTER, s.id())) {
			List<UUID> carter = pick(s, 1, JobType.CARTER, JobType.HAULER, JobType.FARMER);
			if (!carter.isEmpty()) {
				send(TravelKind.CARTER, s, parent, carter, Math.max(now, dayStart + 600 + random.nextInt(2400)), 0, true, 0, 2400, new CompoundTag());
			}
		}
		// patrols: two a day, at dawn and in the afternoon, from towns with guards enough
		int guards = s.jobSlots(JobType.GUARD);
		if (guards >= cfg.patrols()) {
			for (long at : new long[]{dayStart + 200, dayStart + 7000}) {
				List<UUID> walk = pick(s, 2 + random.nextInt(2), JobType.GUARD);
				if (walk.size() >= 2) {
					send(TravelKind.PATROL, s, null, walk, Math.max(now, at), 0, true, s.territoryRadius() + 150, 600, new CompoundTag());
				}
			}
		}
		// pilgrims on a feast day, to a holy place within a long walk
		SettlementCalendar cal = HearthServices.calendar();
		String feast = cal == SettlementCalendar.NONE ? fallbackFeast(day) : cal.feast(s, day);
		if (cfg.pilgrims() && feast != null && s.population() >= 12 && random.nextInt(3) == 0) {
			SimSettlement shrine = holyPlace(s, day);
			if (shrine != null) {
				List<UUID> band = pick(s, 2 + random.nextInt(5));
				if (band.size() >= 2) {
					CompoundTag d = new CompoundTag();
					d.putString("feast", feast);
					send(TravelKind.PILGRIMS, s, shrine, band, Math.max(now, dayStart + 400), 0, true, 0, 6000, d);
				}
			}
		}
		// market goers to a neighbour's market day
		if (s.population() >= 8 && random.nextInt(2) == 0) {
			for (SimSettlement t : this.world.settlements()) {
				if (t == s || t.population() < 20 || t.center().distSqr(s.center()) > (double) cfg.marketGoers() * cfg.marketGoers()) {
					continue;
				}
				if (cal == SettlementCalendar.NONE ? fallbackMarket(t, day) : cal.marketDay(t, day)) {
					List<UUID> go = pick(s, 2 + random.nextInt(3), JobType.FARMER, JobType.BAKER, JobType.FISHER);
					if (!go.isEmpty() && !onRoad(TravelKind.MARKET_GOERS, s.id())) {
						send(TravelKind.MARKET_GOERS, s, t, go, Math.max(now, dayStart + 300), 0, true, 0, 3000, new CompoundTag());
					}
					break;
				}
			}
		}
		// a pedlar sets out from a market town for the next market
		if (s.tier().ordinal() >= SettlementTier.VILLAGE.ordinal() && random.nextInt(4) == 0 && !onRoad(TravelKind.PEDLAR, s.id())) {
			SimSettlement next = null;
			double best = Double.MAX_VALUE;
			for (Way w : this.net.waysFrom(s.id())) {
				SimSettlement t = this.world.settlement(w.other(s.id()));
				if (t == null || t.population() == 0) {
					continue;
				}
				boolean market = cal == SettlementCalendar.NONE ? fallbackMarket(t, day + 1) : cal.marketDay(t, day + 1);
				double score = w.length() * (market ? 0.5 : 1) + random.nextInt(400);
				if (score < best) {
					best = score;
					next = t;
				}
			}
			if (next != null) {
				CompoundTag d = new CompoundTag();
				d.putLong("seed", random.nextLong());
				send(TravelKind.PEDLAR, s, next, List.of(), Math.max(now, dayStart + 500 + random.nextInt(3000)), 0, false, 0, 0, d);
			}
		}
	}

	private boolean onRoad(TravelKind kind, long from) {
		for (Journey j : book().journeys) {
			if (j.kind() == kind && j.from() == from) {
				return true;
			}
		}
		return false;
	}

	/** Until the rhythm lane's calendar is in: a feast every fourth Sunday, the same everywhere. */
	static @Nullable String fallbackFeast(long day) {
		return Math.floorMod(day, 28) == 6 ? "hearth:feast" : null;
	}

	/** Until the rhythm lane's calendar is in: each town's market on its own day of the week. */
	static boolean fallbackMarket(Settlement s, long day) {
		return Math.floorMod(day + Math.floorMod(s.id() * 0x9E3779B97F4A7C15L >>> 40, 7), 7) == 3;
	}

	/** The nearest live settlement within 3 000 blocks with a church, monastery, cathedral or shrine. */
	private @Nullable SimSettlement holyPlace(SimSettlement s, long day) {
		if (day / 7 != this.holyDay) {
			this.holy.clear();
			this.holyDay = day / 7;
		}
		SimSettlement best = null;
		double bestD = 3000.0 * 3000.0;
		for (SimSettlement t : this.world.settlements()) {
			if (t == s) {
				continue;
			}
			double d = t.center().distSqr(s.center());
			if (d > bestD) {
				continue;
			}
			boolean h = this.holy.computeIfAbsent(t.id(), k -> {
				for (PlotView p : t.plots()) {
					String path = p.spec().archetype().getPath();
					if (path.contains("monastery") || path.contains("cathedral") || path.contains("church") || path.contains("chapel") || path.contains("shrine")) {
						return true;
					}
				}
				return false;
			});
			if (h) {
				best = t;
				bestD = d;
			}
		}
		return best;
	}

	/** The sim sent a caravan (its books keep its cargo and its fate): it goes on the roads as a journey arriving when they say. */
	public void onCaravan(Settlement from, Settlement to, SettlementEvents.CaravanEvent.Kind kind, long caravanId, int[] cargo) {
		if (kind == SettlementEvents.CaravanEvent.Kind.DEPARTED) {
			HearthWorld.CaravanView view = caravanId > 0 ? this.world.caravan(caravanId) : null;
			if (!(from instanceof SimSettlement s) || view == null) {
				return;
			}
			CompoundTag d = new CompoundTag();
			d.putLong("caravan", caravanId);
			d.putIntArray("cargo", cargo.clone());
			long id = send(TravelKind.CARAVAN, s, to, List.of(), view.departTick(), view.arriveTick(), false, 0, 0, d);
			if (id != 0 && this.world.caravanTrade().caravan(caravanId) != null && this.world.caravanTrade().caravan(caravanId).doomed()) {
				// the sim decided at departure that this one does not get through: it is waylaid at the loneliest point of its way
				Journey j = journey(id);
				if (j != null) {
					long at = loneliestTick(j);
					replace(j, j.withWaylay(at, bandOn(j), "robbed"));
				}
			}
			return;
		}
		// arrived, robbed or lost in the sim's books: the journey ends with it
		for (Journey j : List.copyOf(book().journeys)) {
			if (j.kind() == TravelKind.CARAVAN && j.details().getLongOr("caravan", 0) == caravanId) {
				end(j, kind == SettlementEvents.CaravanEvent.Kind.ARRIVED ? SettlementEvents.Traveller.Phase.ARRIVED
					: kind == SettlementEvents.CaravanEvent.Kind.ROBBED ? SettlementEvents.Traveller.Phase.WAYLAID : SettlementEvents.Traveller.Phase.LOST);
				return;
			}
		}
	}

	/** When a caravan leaving now arrives by road at its wagon's pace (night stops included), or 0 without a road known. */
	public long caravanArrival(SimSettlement from, SimSettlement to, long now) {
		Object[] p = path(from.id(), to.id(), from.center(), to.center());
		if (p == null) {
			return 0;
		}
		return Journey.arrival(now, (double) p[1], Journey.CART);
	}

	/** The risk of a caravan's way: the bandits of the roads it crosses ({@link Bandits#caravanRisk}), or NaN without a road known. */
	public double caravanRisk(SimSettlement from, SimSettlement to, int guards) {
		Object[] p = path(from.id(), to.id(), from.center(), to.center());
		if (p == null) {
			return Double.NaN;
		}
		long[] ids = (long[]) p[0];
		float[] peaks = new float[ids.length];
		boolean[] banded = new boolean[ids.length];
		long day = this.world.day();
		for (int i = 0; i < ids.length; i++) {
			long road = Math.abs(ids[i]);
			Way w = this.net.known(road);
			peaks[i] = w == null ? 0 : Bandits.peak(w.length(), cover(w), patrolled(road, day), brokenLately(road, day));
			for (Band b : book().bands) {
				banded[i] |= b.road() == road;
			}
		}
		return Bandits.caravanRisk(peaks, banded, guards);
	}

	/** A messenger rides with news the standing module sent between neighbours; the news arrives on its day either way. */
	public void messenger(long from, long to, int delayDays) {
		SimSettlement a = this.world.settlement(from);
		SimSettlement b = this.world.settlement(to);
		if (a == null || b == null || delayDays <= 0) {
			return;
		}
		long now = this.world.clock();
		long arrive = (this.world.day() + delayDays) * SimRules.DAY + 2000;
		List<UUID> rider = pick(a, 1, JobType.COURIER);
		if (!rider.isEmpty() && a.resident(rider.getFirst()) != null && a.resident(rider.getFirst()).job != JobType.COURIER) {
			rider = List.of();
		}
		send(TravelKind.MESSENGER, a, b, rider, now + 200, arrive, false, 0, 0, new CompoundTag());
	}

	/** Settlers leaving for a new hamlet walk there; they are away from it until they arrive. */
	public void settlers(SimSettlement parent, SimSettlement hamlet) {
		List<UUID> who = new ArrayList<>();
		for (Resident r : hamlet.residentList()) {
			who.add(r.id);
		}
		long now = this.world.clock();
		// (they belong to the hamlet now: the journey is the hamlet's, from the parent's place)
		Object[] p = path(parent.id(), hamlet.id(), parent.center(), hamlet.center());
		long[] ids = p == null ? new long[0] : (long[]) p[0];
		double len = p == null ? Math.sqrt(parent.center().distSqr(hamlet.center())) * 1.25 : (double) p[1];
		if (!room(TravelKind.SETTLERS)) {
			return;
		}
		long end = Journey.arrival(now, len, Journey.CART);
		Book book = book();
		long id = book.nextId++;
		book.journeys.add(new Journey(id, TravelKind.SETTLERS, parent.id(), hamlet.id(), ids, len, now, Journey.CART, end, List.copyOf(who), false, 0,
			Long.MAX_VALUE, 0, "", new CompoundTag()));
		for (UUID r : who) {
			hamlet.setResidentAway(r, end, "travel:settlers");
		}
		changed();
		SettlementEvents.TRAVELLER.invoker().onTraveller(this.world, TravelKind.SETTLERS, id, parent.id(), hamlet.id(), SettlementEvents.Traveller.Phase.DEPARTED);
	}

	// ---------------------------------------------------------------- ending

	private void replace(Journey old, Journey next) {
		List<Journey> js = book().journeys;
		int i = js.indexOf(old);
		if (i >= 0) {
			js.set(i, next);
			this.geometry.remove(old.id());
			changed();
		}
	}

	/** The journey's settlement whose residents travel with it (a settlers' journey: the hamlet's). */
	private @Nullable SimSettlement home(Journey j) {
		return this.world.settlement(j.kind() == TravelKind.SETTLERS ? j.to() : j.from());
	}

	private void arrive(Journey j) {
		book().arrived++;
		if (j.kind() == TravelKind.PILGRIMS) {
			// alms to the holy place
			SimSettlement shrine = this.world.settlement(j.to());
			SimSettlement home = this.world.settlement(j.from());
			long alms = 10L * Math.max(1, j.residents().size());
			if (shrine != null && home != null && home.withdraw(Math.min(alms, home.treasury()))) {
				shrine.deposit(Math.min(alms, home.treasury() + alms));
				shrine.markDirty();
			}
		}
		if (j.kind() == TravelKind.PATROL) {
			for (long id : j.path()) {
				book().patrolled.put(Math.abs(id), this.world.day());
			}
		}
		if (j.kind() == TravelKind.CARAVAN) {
			// the sim's books close a caravan (its CARAVAN event ends this journey); one they forgot ends here
			if (this.world.caravanTrade().caravan(j.details().getLongOr("caravan", 0)) != null) {
				return;
			}
		}
		end(j, SettlementEvents.Traveller.Phase.ARRIVED);
	}

	/** A journey ends: its residents are home (or arrived), the event fires. */
	void end(Journey j, SettlementEvents.Traveller.Phase phase) {
		if (!book().journeys.remove(j)) {
			return;
		}
		this.geometry.remove(j.id());
		SimSettlement home = home(j);
		if (home != null) {
			for (UUID r : j.residents()) {
				home.setResidentAway(r, Long.MIN_VALUE, "");
			}
			home.markDirty();
		}
		changed();
		SettlementEvents.TRAVELLER.invoker().onTraveller(this.world, j.kind(), j.id(), j.from(), j.to(), phase);
	}

	/** Recalls a journey: its people come home now. */
	public void recall(long id) {
		Journey j = journey(id);
		if (j != null) {
			end(j, SettlementEvents.Traveller.Phase.LOST);
		}
	}

	// ---------------------------------------------------------------- bandits

	/** How much of a road runs through cover (woods, jungle, taiga; broken ground), 0–1. Cached. */
	float cover(Way w) {
		Float c = this.cover.get(w.id());
		if (c != null) {
			return c;
		}
		Route r = w.route();
		int samples = 0;
		int covered = 0;
		if (r != null) {
			for (int i = 0; i < r.size(); i += Math.max(1, r.size() / 16)) {
				samples++;
				int x = r.xs()[i];
				int z = r.zs()[i];
				Holder<Biome> b = this.world.probe().biomeHolder(x, z);
				boolean wooded = b != null && (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_JUNGLE) || b.is(BiomeTags.IS_TAIGA));
				int rough = Math.abs(this.world.probe().groundY(x + 12, z) - this.world.probe().groundY(x - 12, z))
					+ Math.abs(this.world.probe().groundY(x, z + 12) - this.world.probe().groundY(x, z - 12));
				if (wooded || rough > 14) {
					covered++;
				}
			}
		}
		float out = samples == 0 ? 0.3F : covered / (float) samples;
		if (this.cover.size() > 4096) {
			this.cover.clear();
		}
		this.cover.put(w.id(), out);
		return out;
	}

	private boolean patrolled(long road, long day) {
		Long p = book().patrolled.get(road);
		return p != null && day - p < Bandits.PATROL_DAYS;
	}

	private boolean brokenLately(long road, long day) {
		Long p = book().broken.get(road);
		return p != null && day - p < Bandits.BROKEN_DAYS;
	}

	/** {@link dev.voidmc.hearth.api.RoadNetwork#danger}: by the road's loneliness, cover, patrols and bands. */
	public float danger(long roadId, double along) {
		Way w = this.net.known(roadId);
		if (w == null) {
			return 0;
		}
		long day = this.world.day();
		float d = Bandits.danger(along, w.length(), cover(w), patrolled(roadId, day), brokenLately(roadId, day));
		for (Band b : book().bands) {
			if (b.road() == roadId && Math.abs(b.along() - along) < 300) {
				d = Math.max(d, 0.6F + 0.05F * b.strength());
			}
		}
		return Mth.clamp(d, 0, 1);
	}

	/** A band on any road of a journey's way, or 0. */
	private long bandOn(Journey j) {
		for (long id : j.path()) {
			for (Band b : book().bands) {
				if (b.road() == Math.abs(id)) {
					return b.id();
				}
			}
		}
		return 0;
	}

	/** When a journey passes the loneliest point of its way (the middle of its longest road). */
	private long loneliestTick(Journey j) {
		double before = 0;
		double bestLen = -1;
		double at = j.length() / 2;
		for (long id : j.path()) {
			Way w = this.net.known(Math.abs(id));
			double len = w == null ? j.length() / Math.max(1, j.path().length) : w.length();
			if (len > bestLen) {
				bestLen = len;
				at = before + len / 2;
			}
			before += len;
		}
		at = Math.min(at, j.length() * 0.9);
		return Journey.afterWalking(j.depart(), (long) Math.ceil(at / Math.max(1e-6, j.pace())));
	}

	/** The world's day: bands take to the dangerous roads, break, and choose the journeys they will waylay. */
	private void newDay(long day, long now) {
		Book b = book();
		RoadsConfig.Bandits cfg = Roads.config().bandits();
		RandomSource random = RandomSource.create(this.world.level().getSeed() ^ day * 0x9E3779B97F4A7C15L);
		// the roads people use: those out of live settlements
		Map<Long, Way> used = new HashMap<>();
		for (SimSettlement s : this.world.settlements()) {
			for (Way w : this.net.waysFrom(s.id())) {
				used.putIfAbsent(w.id(), w);
			}
		}
		List<Way> dangerous = new ArrayList<>();
		for (Way w : used.values()) {
			if (Bandits.peak(w.length(), cover(w), patrolled(w.id(), day), brokenLately(w.id(), day)) >= Bandits.DANGEROUS) {
				dangerous.add(w);
			}
		}
		dangerous.sort((x, y) -> Double.compare(y.length(), x.length()));
		int want = Math.min(cfg.maxBands(), dangerous.size() / 3 + (dangerous.isEmpty() ? 0 : 1));
		if (b.bands.size() < want && random.nextInt(4) == 0) {
			for (Way w : dangerous) {
				boolean taken = false;
				for (Band band : b.bands) {
					taken |= band.road() == w.id();
				}
				if (!taken && w.route() != null) {
					b.bands.add(newBand(w, random, day));
					LOG.info("hearth roads: bandits take to the road between {} and {}", w.nameA(), w.nameB());
					break;
				}
			}
		}
		// each band may choose one journey on its road for the coming day
		for (int i = 0; i < b.bands.size(); i++) {
			Band band = b.bands.get(i);
			Way w = this.net.known(band.road());
			if (w == null) {
				continue;
			}
			float danger = Bandits.danger(band.along(), w.length(), cover(w), patrolled(w.id(), day), brokenLately(w.id(), day));
			if (random.nextDouble() >= Bandits.attackOdds(band.morale(), danger)) {
				continue;
			}
			for (Journey j : List.copyOf(b.journeys)) {
				if (j.kind() == TravelKind.CARAVAN || j.kind() == TravelKind.PATROL || !j.outcome().isEmpty()) {
					// (the sim decides its caravans at their departure; patrols are what bands avoid)
					continue;
				}
				long at = passes(j, band, w);
				if (at < now || at > now + SimRules.DAY) {
					continue;
				}
				int guards = j.kind() == TravelKind.PATROL ? j.residents().size() : j.kind() == TravelKind.TAX_COLLECTOR || j.kind() == TravelKind.DIGNITARY ? 2 : 0;
				String outcome;
				if (random.nextDouble() < Bandits.robOdds(band.strength(), guards)) {
					outcome = cfg.mayKill() && !j.residents().isEmpty() && random.nextDouble() < cfg.killChance() ? "killed" : "robbed";
				} else {
					outcome = "driven_off";
				}
				replace(j, j.withWaylay(at, band.id(), outcome));
				break;
			}
		}
		// patrols that pass a band's spot drive it off now and then
		b.bands.removeIf(band -> {
			if (patrolled(band.road(), day) && random.nextInt(5) == 0) {
				b.broken.put(band.road(), day);
				LOG.info("hearth roads: patrols drove the bandits off their road");
				return true;
			}
			return band.broken();
		});
		b.patrolled.values().removeIf(d -> day - d > 30);
		b.broken.values().removeIf(d -> day - d > 60);
	}

	/** A new band on a road: at its loneliest point, its camp off the road in cover. */
	Band newBand(Way w, RandomSource random, long day) {
		double along = w.length() * (0.35 + 0.3 * random.nextDouble());
		Route r = w.route();
		int i = r == null ? 0 : indexAt(r, along);
		int x = r == null ? (w.ax() + w.bx()) / 2 : r.xs()[i];
		int z = r == null ? (w.az() + w.bz()) / 2 : r.zs()[i];
		// off the road, square to it, on the side chosen by the band
		int j = r == null ? i : Math.min(r.size() - 1, i + 4);
		double dx = r == null ? 1 : r.xs()[j] - x;
		double dz = r == null ? 0 : r.zs()[j] - z;
		double n = Math.max(1e-6, Math.hypot(dx, dz));
		double side = random.nextBoolean() ? 1 : -1;
		double off = 40 + random.nextInt(81);
		int cx = (int) Math.round(x - dz / n * off * side);
		int cz = (int) Math.round(z + dx / n * off * side);
		int cy = this.world.probe().groundY(cx, cz);
		long id = book().nextId++;
		return new Band(id, w.id(), new BlockPos(cx, cy, cz), along, 3 + random.nextInt(5), 0.5 + 0.3 * random.nextDouble(), new int[0], 0, day);
	}

	static int indexAt(Route r, double along) {
		double acc = 0;
		for (int i = 1; i < r.size(); i++) {
			acc += Math.hypot(r.xs()[i] - r.xs()[i - 1], r.zs()[i] - r.zs()[i - 1]);
			if (acc >= along) {
				return i;
			}
		}
		return r.size() - 1;
	}

	/** When a journey passes a band's spot (or {@link Long#MAX_VALUE} if its way does not). */
	private long passes(Journey j, Band band, Way w) {
		double before = 0;
		for (long id : j.path()) {
			Way p = this.net.known(Math.abs(id));
			double len = p == null ? 0 : p.length();
			if (Math.abs(id) == band.road()) {
				double at = before + (id > 0 ? band.along() : len - band.along());
				if (at > j.length()) {
					return Long.MAX_VALUE;
				}
				return Journey.afterWalking(j.depart(), (long) Math.ceil(at / Math.max(1e-6, j.pace())));
			}
			before += len;
		}
		return Long.MAX_VALUE;
	}

	/** The band's deed comes due (nobody was there to change it): its outcome stands. */
	private void waylaid(Journey j, long now) {
		Book b = book();
		Band band = null;
		int bi = -1;
		for (int i = 0; i < b.bands.size(); i++) {
			if (b.bands.get(i).id() == j.band()) {
				band = b.bands.get(i);
				bi = i;
			}
		}
		String outcome = j.outcome();
		b.waylaid++;
		if (j.kind() == TravelKind.CARAVAN) {
			int[] cargo = this.world.robCaravan(j.details().getLongOr("caravan", 0));
			if (band != null && cargo != null) {
				b.bands.set(bi, band.plunder(cargo, 0));
			}
			// the sim's ROBBED ended the journey
			end(j, SettlementEvents.Traveller.Phase.WAYLAID);
			return;
		}
		switch (outcome) {
			case "driven_off" -> {
				if (band != null) {
					b.bands.set(bi, Bandits.repulsed(band, false));
				}
				replace(j, j.withWaylay(Long.MAX_VALUE, 0, ""));
			}
			case "killed" -> {
				SimSettlement home = home(j);
				if (home != null && !j.residents().isEmpty()) {
					UUID victim = j.residents().getFirst();
					Resident r = home.resident(victim);
					if (r != null) {
						r.extra("travel").putString("died", "bandits");
						home.setResidentAway(victim, Long.MIN_VALUE, "");
						home.removeResident(victim);
						b.killed++;
						LOG.info("hearth roads: {} of {} was killed by bandits on the road", r.fullName(), home.name());
					}
				}
				if (band != null) {
					b.bands.set(bi, band.plunder(new int[0], 20));
				}
				List<UUID> rest = new ArrayList<>(j.residents());
				if (!rest.isEmpty()) {
					rest.removeFirst();
				}
				replace(j, new Journey(j.id(), j.kind(), j.from(), j.to(), j.path(), j.length(), j.depart(), j.pace(), j.arrive(), rest, j.roundTrip(), j.turn(),
					Long.MAX_VALUE, 0, "", j.details()));
			}
			default -> {
				if (band != null) {
					b.bands.set(bi, band.plunder(new int[0], 10L + 10L * j.residents().size()));
				}
				replace(j, j.withWaylay(Long.MAX_VALUE, 0, ""));
			}
		}
		SettlementEvents.TRAVELLER.invoker().onTraveller(this.world, j.kind(), j.id(), j.from(), j.to(), SettlementEvents.Traveller.Phase.WAYLAID);
	}

	/**
	 * A player changed what a band does (the npc module's ambush played out live): {@code won} the party got through, so
	 * the band's deed is undone; the band loses {@code lost} men (a band below two breaks and its road is quiet).
	 */
	public void foiled(long journeyId, long bandId, boolean won, int lost) {
		Book b = book();
		Journey j = journey(journeyId);
		if (j != null && won) {
			if (j.kind() == TravelKind.CARAVAN) {
				this.world.escortCaravan(j.details().getLongOr("caravan", 0));
			}
			replace(j, j.withWaylay(Long.MAX_VALUE, 0, ""));
		}
		hurtBand(bandId, lost);
	}

	/** A band lost men to a player or a patrol; a band below two breaks. */
	public void hurtBand(long bandId, int lost) {
		Book b = book();
		for (int i = 0; i < b.bands.size(); i++) {
			Band band = b.bands.get(i);
			if (band.id() == bandId) {
				Band next = band.withStrength(band.strength() - lost, band.morale() - 0.15 * lost);
				if (next.broken()) {
					b.bands.remove(i);
					b.broken.put(band.road(), this.world.day());
					LOG.info("hearth roads: the band on the road is broken");
				} else {
					b.bands.set(i, next);
				}
				changed();
				return;
			}
		}
	}

	/** A band's loot was taken (its camp found and cleared). */
	public void lootTaken(long bandId) {
		Book b = book();
		for (int i = 0; i < b.bands.size(); i++) {
			Band band = b.bands.get(i);
			if (band.id() == bandId) {
				b.bands.set(i, new Band(band.id(), band.road(), band.camp(), band.along(), band.strength(), band.morale() - 0.3, new int[0], 0, band.since()));
				changed();
				return;
			}
		}
	}

	public @Nullable Band band(long id) {
		for (Band b : book().bands) {
			if (b.id() == id) {
				return b;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- where they are

	/** A journey's way as one polyline: its roads end to end (their routes, or straight between their ends). */
	public record Geometry(int[] xs, int[] ys, int[] zs, double[] cum) {
		public double length() {
			return this.cum.length == 0 ? 0 : this.cum[this.cum.length - 1];
		}

		/** The point {@code along} blocks from the start (x, y, z and the direction of travel as dx, dz). */
		public double[] at(double along) {
			int n = this.xs.length;
			if (n == 0) {
				return new double[]{0, 0, 0, 1, 0};
			}
			if (along <= 0 || n == 1) {
				return new double[]{this.xs[0], this.ys[0], this.zs[0], n > 1 ? this.xs[1] - this.xs[0] : 1, n > 1 ? this.zs[1] - this.zs[0] : 0};
			}
			int lo = 0;
			int hi = n - 1;
			while (lo < hi) {
				int mid = (lo + hi) >>> 1;
				if (this.cum[mid] < along) {
					lo = mid + 1;
				} else {
					hi = mid;
				}
			}
			int i = Math.max(1, lo);
			double seg = this.cum[i] - this.cum[i - 1];
			double t = seg <= 0 ? 1 : Mth.clamp((along - this.cum[i - 1]) / seg, 0, 1);
			return new double[]{Mth.lerp(t, this.xs[i - 1], this.xs[i]), Mth.lerp(t, this.ys[i - 1], this.ys[i]), Mth.lerp(t, this.zs[i - 1], this.zs[i]),
				this.xs[i] - this.xs[i - 1], this.zs[i] - this.zs[i - 1]};
		}
	}

	/** The geometry of a journey (cached; made again when a road on it is worked out since). */
	public Geometry geometry(Journey j) {
		Geometry g = this.geometry.get(j.id());
		if (g != null) {
			return g;
		}
		List<int[]> pts = new ArrayList<>();
		boolean complete = true;
		SimSettlement from = this.world.settlement(j.from());
		SimSettlement to = this.world.settlement(j.to());
		for (long id : j.path()) {
			Way w = this.net.known(Math.abs(id));
			if (w == null || w.route() == null) {
				complete = w != null;
				if (w != null) {
					addPoint(pts, id > 0 ? w.ax() : w.bx(), 0, id > 0 ? w.az() : w.bz());
					addPoint(pts, id > 0 ? w.bx() : w.ax(), 0, id > 0 ? w.bz() : w.az());
				}
				continue;
			}
			Route r = w.route();
			if (id > 0) {
				for (int i = 0; i < r.size(); i += 2) {
					addPoint(pts, r.xs()[i], r.ys()[i], r.zs()[i]);
				}
				addPoint(pts, r.xs()[r.size() - 1], r.ys()[r.size() - 1], r.zs()[r.size() - 1]);
			} else {
				for (int i = r.size() - 1; i >= 0; i -= 2) {
					addPoint(pts, r.xs()[i], r.ys()[i], r.zs()[i]);
				}
				addPoint(pts, r.xs()[0], r.ys()[0], r.zs()[0]);
			}
		}
		if (pts.isEmpty()) {
			// across country
			BlockPos a = from != null ? from.center() : BlockPos.ZERO;
			BlockPos b = to != null ? to.center() : a;
			addPoint(pts, a.getX(), a.getY(), a.getZ());
			addPoint(pts, b.getX(), b.getY(), b.getZ());
		}
		int n = pts.size();
		int[] xs = new int[n];
		int[] ys = new int[n];
		int[] zs = new int[n];
		double[] cum = new double[n];
		for (int i = 0; i < n; i++) {
			xs[i] = pts.get(i)[0];
			ys[i] = pts.get(i)[1];
			zs[i] = pts.get(i)[2];
			cum[i] = i == 0 ? 0 : cum[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
		}
		g = new Geometry(xs, ys, zs, cum);
		if (complete) {
			this.geometry.put(j.id(), g);
		}
		return g;
	}

	private static void addPoint(List<int[]> pts, int x, int y, int z) {
		if (!pts.isEmpty()) {
			int[] last = pts.getLast();
			if (last[0] == x && last[2] == z) {
				return;
			}
		}
		pts.add(new int[]{x, y, z});
	}

	/** Where a journey is at a tick: x, y, z and its heading (dx, dz). y is the road's walking level (0 where unknown). */
	public double[] place(Journey j, long tick) {
		Geometry g = geometry(j);
		double along = j.along(tick);
		// the books' length and the drawn way agree in proportion, whichever was known first
		double f = j.length() <= 0 ? 0 : along / j.length();
		double[] p = g.at(f * g.length());
		if (j.returning(tick)) {
			p[3] = -p[3];
			p[4] = -p[4];
		}
		return p;
	}

	public BlockPos position(Journey j, long tick) {
		double[] p = place(j, tick);
		int y = (int) Math.round(p[1]);
		if (y == 0) {
			y = this.world.probe().groundY((int) Math.floor(p[0]), (int) Math.floor(p[2]));
		}
		return BlockPos.containing(p[0], y, p[2]);
	}

	/** Journeys within {@code radius} of a point now, nearest first. */
	public List<Journey> near(BlockPos at, int radius) {
		long now = this.world.clock();
		List<double[]> found = new ArrayList<>();
		List<Journey> js = book().journeys;
		double r2 = (double) radius * radius;
		for (int i = 0; i < js.size(); i++) {
			double[] p = place(js.get(i), now);
			double dx = p[0] - at.getX();
			double dz = p[2] - at.getZ();
			double d = dx * dx + dz * dz;
			if (d <= r2) {
				found.add(new double[]{d, i});
			}
		}
		found.sort((a, b) -> Double.compare(a[0], b[0]));
		List<Journey> out = new ArrayList<>(found.size());
		for (double[] f : found) {
			out.add(js.get((int) f[1]));
		}
		return out;
	}

	public String describe() {
		Book b = book();
		EnumMap<TravelKind, Integer> on = new EnumMap<>(TravelKind.class);
		for (Journey j : b.journeys) {
			on.merge(j.kind(), 1, Integer::sum);
		}
		StringBuilder kinds = new StringBuilder();
		on.forEach((k, n) -> kinds.append(kinds.isEmpty() ? "" : ", ").append(n).append(' ').append(k.name().toLowerCase(Locale.ROOT)));
		return String.format(Locale.ROOT, "%d journeys on the roads (%s); %d arrived, %d waylaid, %d killed (all time); %d bands; %d refused for the caps",
			b.journeys.size(), kinds.isEmpty() ? "none" : kinds, b.arrived, b.waylaid, b.killed, b.bands.size(), this.refusedForCaps);
	}

	// ---------------------------------------------------------------- the api

	private static final class Service implements TravelService {
		private static @Nullable Traffic of(@Nullable Settlement s) {
			if (s == null || SimServer.current() == null) {
				return null;
			}
			for (Roads.State st : Roads.states()) {
				if (st.world().settlement(s.id()) == s) {
					return st.traffic();
				}
			}
			return null;
		}

		private static @Nullable Object[] find(long id) {
			for (Roads.State st : Roads.states()) {
				Journey j = st.traffic().journey(id);
				if (j != null) {
					return new Object[]{st.traffic(), j};
				}
			}
			return null;
		}

		@Override
		public long dispatch(TravelKind kind, Settlement from, @Nullable Settlement to, @Nullable BlockPos destination, List<UUID> residents, long departTick,
			CompoundTag details) {
			Traffic t = of(from);
			if (t == null || !(from instanceof SimSettlement s)) {
				return 0;
			}
			boolean round = to == null || details.getBooleanOr("round_trip", false);
			double out = destination == null ? 0 : Math.sqrt(from.center().distSqr(destination)) * 1.15;
			return t.send(kind, s, to, residents, Math.max(departTick, t.world.clock()), 0, round, out, details.getIntOr("stay", 2400), details);
		}

		@Override
		public @Nullable TravellerView traveller(long id) {
			Object[] f = find(id);
			return f == null ? null : view((Traffic) f[0], (Journey) f[1]);
		}

		private static TravellerView view(Traffic t, Journey j) {
			return new TravellerView(j.id(), j.kind(), j.from(), j.to(), j.depart(), j.arrive(), j.residents(), t.embodied.test(j.id()));
		}

		@Override
		public List<TravellerView> near(ServerLevel level, BlockPos at, int radius) {
			Roads.State st = Roads.state(SimServer.world(level));
			if (st == null) {
				return List.of();
			}
			List<TravellerView> out = new ArrayList<>();
			for (Journey j : st.traffic().near(at, radius)) {
				out.add(view(st.traffic(), j));
			}
			return out;
		}

		@Override
		public @Nullable BlockPos positionAt(long id, long tick) {
			Object[] f = find(id);
			return f == null ? null : ((Traffic) f[0]).position((Journey) f[1], tick);
		}

		@Override
		public boolean waylay(long id, @Nullable UUID by) {
			Object[] f = find(id);
			if (f == null) {
				return false;
			}
			Traffic t = (Traffic) f[0];
			Journey j = (Journey) f[1];
			if (j.kind() == TravelKind.CARAVAN) {
				t.world.caravanLost(j.details().getLongOr("caravan", 0), by);
			} else {
				t.end(j, SettlementEvents.Traveller.Phase.WAYLAID);
			}
			return true;
		}

		@Override
		public void recall(long id) {
			Object[] f = find(id);
			if (f != null) {
				((Traffic) f[0]).recall(id);
			}
		}
	}

	/** Whether a journey's party is on the road near a player now (the npc module's travel sets it). */
	private java.util.function.LongPredicate embodied = id -> false;

	public void setEmbodied(java.util.function.LongPredicate embodied) {
		this.embodied = embodied;
	}
}
