package dev.voidmc.hearth.social.life;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.Agenda;
import dev.voidmc.hearth.social.calendar.Festival;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The hub of town life. Listens to the sim's events for the data side (the day's business, births and deaths,
 * officials) — cheap, and run for every settlement whether anyone is near or not — and, for embodied settlements only,
 * makes the town's life visible every {@code pass_ticks}: bells, market stalls and festival dressings, attendance.
 * Folk behaviours ask it what is on and where ({@link #events}, {@link #venue}, {@link #stalls}).
 */
public final class TownLife {
	private static final Map<Long, HearthWorld> EMBODIED = new LinkedHashMap<>();
	private static final Map<Long, Day> DAYS = new HashMap<>();
	private static final Map<Long, Integer> LAST_MINUTE = new HashMap<>();
	private static final ArrayDeque<Bells.Stroke> STROKES = new ArrayDeque<>();
	private static final Set<String> ATTENDED = new HashSet<>();
	private static final Map<UUID, Long> MURDERED = new HashMap<>();
	/** Events forced by command, by settlement (kept for their day). */
	private static final Map<Long, List<TownEvent>> FORCED = new HashMap<>();
	private static long lastFolkDeath = Long.MIN_VALUE;
	private static boolean lastFolkDeathByPlayer;
	private static @Nullable MinecraftServer server;

	private TownLife() {
	}

	/** Today's agenda of one settlement, with where each event is held and the stalls set up. */
	static final class Day {
		final long day;
		final List<TownEvent> events;
		final Map<String, Venues.Venue> venues = new HashMap<>();
		final Map<String, List<Dressings.Stall>> stalls = new HashMap<>();
		Bells.@Nullable Tower tower;
		boolean towerKnown;

		Day(long day, List<TownEvent> events) {
			this.day = day;
			this.events = events;
		}
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPING.register(TownLife::stopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			EMBODIED.clear();
			DAYS.clear();
			LAST_MINUTE.clear();
			STROKES.clear();
			ATTENDED.clear();
			MURDERED.clear();
			FORCED.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(TownLife::tick);

		SettlementEvents.DAY_PASSED.register(TownLife::dayPassed);
		SettlementEvents.RESIDENT_ADDED.register((w, s, r) -> {
			if (r.age == AgeStage.CHILD && r.bornDay >= w.day() - 1 && w.day() - s.foundedDay() > 0) {
				FamilyLife.born(s, r, w.day(), cfg().family(), p -> post(w, s, p));
			}
		});
		SettlementEvents.RESIDENT_REMOVED.register(TownLife::residentRemoved);
		SettlementEvents.CRIME_COMMITTED.register((w, s, crime) -> {
			if (crime.offence() == LawService.Offence.MURDER && crime.victim() != null) {
				MURDERED.put(crime.victim(), w.level().getGameTime());
				SocialLedger ledger = s.data(SocialModule.LEDGER);
				// if the death is already on the board, say how they died
				for (SocialLedger.FamilyEvent fe : ledger.family) {
					if (fe.kind() == SocialLedger.FamilyEvent.Kind.FUNERAL && fe.people().contains(crime.victim())) {
						for (int i = 0; i < ledger.notices.size(); i++) {
							SocialLedger.Notice n = ledger.notices.get(i);
							if (n.args().equals(fe.names()) && (n.key().equals("hearth.social.notice.killed") || n.key().equals("hearth.social.notice.died"))) {
								ledger.notices.set(i, new SocialLedger.Notice(n.kind(), n.day(), n.eventDay(), "hearth.social.notice.slain", n.args(), n.cried()));
								s.markDirty();
							}
						}
					}
				}
			}
		});
		SettlementEvents.EMBODIMENT_CHANGED.register((w, s, embodied) -> {
			if (embodied) {
				EMBODIED.put(s.id(), w);
				cleanUp(w, s);
			} else {
				EMBODIED.remove(s.id());
				LAST_MINUTE.remove(s.id());
				SocialLedger ledger = s.data(SocialModule.LEDGER);
				if (Dressings.takeDown(w.level(), ledger, Long.MAX_VALUE, true, null) > 0) {
					s.markDirty();
				}
				Day d = DAYS.get(s.id());
				if (d != null) {
					d.stalls.clear();
				}
			}
		});
		SettlementEvents.TIER_CHANGED.register((w, s, from, to) -> offices(w, s, w.day(), true));
		SettlementEvents.BUILDING_COMPLETED.register((w, s, plot) -> {
			DAYS.remove(s.id()); // venues and the bell tower may have changed
			String path = plot.spec().archetype().getPath();
			if (path.startsWith("law/") || path.startsWith("noble/") || path.equals("civic/town_hall")) {
				offices(w, s, w.day(), true);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(Hearth.id("folk"))) {
				lastFolkDeath = entity.level().getGameTime();
				lastFolkDeathByPlayer = source.getEntity() instanceof Player;
			}
		});
	}

	static SocialConfig cfg() {
		return SocialModule.config();
	}

	// ---- reading, for behaviours and dialogue

	/** The Hearth world a settlement lives in, or null. */
	public static @Nullable HearthWorld world(Settlement s) {
		MinecraftServer srv = server;
		if (srv == null) {
			return null;
		}
		ServerLevel level = srv.getLevel(s.dimension());
		return level == null ? null : HearthWorld.of(level);
	}

	/** Now, in town ticks, for a settlement. */
	public static long now(Settlement s) {
		HearthWorld w = world(s);
		return w == null ? 0 : SocialTime.now(w);
	}

	/** Today's events of a settlement. */
	public static List<TownEvent> events(Settlement s) {
		HearthWorld w = world(s);
		return w == null ? List.of() : day(w, s).events;
	}

	/** The events of a settlement on any day (rumours about other towns, what's on tomorrow). */
	public static List<TownEvent> eventsOn(Settlement s, long day) {
		return Agenda.of(s, s.data(SocialModule.LEDGER), day, cfg());
	}

	public static Venues.Venue venue(Settlement s, TownEvent e) {
		HearthWorld w = world(s);
		if (w == null) {
			return Venues.of(s, e);
		}
		return day(w, s).venues.computeIfAbsent(e.key(), k -> Venues.of(s, e));
	}

	/** The stalls set up for a market (empty when none stand). */
	public static List<Dressings.Stall> stalls(Settlement s, String eventKey) {
		Day d = DAYS.get(s.id());
		return d == null ? List.of() : d.stalls.getOrDefault(eventKey, List.of());
	}

	/** The event of this kind on now, or null. */
	public static @Nullable TownEvent current(Settlement s, TownEvent.Kind kind) {
		long now = now(s);
		for (TownEvent e : events(s)) {
			if (e.kind() == kind && e.activeAt(now)) {
				return e;
			}
		}
		return null;
	}

	public static boolean isEmbodied(long settlementId) {
		return EMBODIED.containsKey(settlementId);
	}

	private static Day day(HearthWorld w, Settlement s) {
		long today = w.day();
		Day d = DAYS.get(s.id());
		if (d == null || d.day != today) {
			List<TownEvent> events = new ArrayList<>(Agenda.of(s, s.data(SocialModule.LEDGER), today, cfg()));
			List<TownEvent> forced = FORCED.get(s.id());
			if (forced != null) {
				forced.removeIf(e -> SocialTime.day(e.end()) < today);
				events.addAll(forced);
			}
			d = new Day(today, events);
			DAYS.put(s.id(), d);
		}
		return d;
	}

	// ---- the day's business (every settlement, also in catch-up)

	private static void dayPassed(HearthWorld w, Settlement s, long day) {
		SocialConfig cfg = cfg();
		if (!cfg.life().enabled()) {
			return;
		}
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		FamilyLife.day(s, ledger, day, cfg.family(), p -> post(w, s, p, day));
		offices(w, s, day, w.day() - day <= 2);
		settleFeasts(s, ledger, day, cfg);
		// a feast day three days off is announced
		Festival f = TownCalendar.festivalOn(day + 3, s.culture(), s.foundedDay(), s.tier(), cfg.festivals());
		if (f != null) {
			post(w, s, new FamilyLife.Posting("festival", day + 3, "hearth.social.notice.festival", List.of("@" + f.translationKey())), day);
		}
		ledger.rooms.removeIf(r -> r.until() + cfg.inn().roomGraceDays() < day);
		ledger.beds.removeIf(r -> SocialTime.day(r.until()) < day - 1);
		ledger.dice.removeIf(t -> t.day() < day - 1);
		DAYS.remove(s.id());
		s.markDirty();
	}

	/** What a day's feasts eat and a market day's stalls pay, once per day. */
	static void settleFeasts(Settlement s, SocialLedger ledger, long day, SocialConfig cfg) {
		if (ledger.feastDay >= day) {
			return;
		}
		ledger.feastDay = day;
		if (TownCalendar.festivalOn(day, s.culture(), s.foundedDay(), s.tier(), cfg.festivals()) != null) {
			s.stockpile().take(Goods.FOOD, (int) Math.round(s.population() * cfg.festivals().feastFoodPerHead()));
		}
		if (TownCalendar.isMarketDay(s.id(), day, cfg.market(), s.tier())) {
			s.deposit((long) stallCount(s, cfg.market()) * cfg.market().stallFee());
		}
	}

	/** How many extra stalls a market day brings to this settlement. */
	public static int stallCount(Settlement s, SocialConfig.Market cfg) {
		int above = Math.max(0, s.tier().ordinal() - cfg.minTier().ordinal());
		return Math.min(cfg.maxStalls(), cfg.stalls() + above * cfg.stallsPerTier());
	}

	private static void offices(HearthWorld w, Settlement s, long day, boolean announce) {
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		List<Officials.Change> changes = Officials.update(s, ledger, cfg().offices(), day);
		if (!announce) {
			return;
		}
		for (Officials.Change c : changes) {
			if (c.after() != null) {
				post(w, s, new FamilyLife.Posting("office", day, "hearth.social.notice.appointed",
					List.of(c.after().fullName(), "@" + c.office().translationKey())), day);
			}
		}
		DAYS.remove(s.id());
	}

	private static void residentRemoved(HearthWorld w, Settlement s, Resident r) {
		SocialConfig cfg = cfg();
		if (!cfg.life().enabled()) {
			return;
		}
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		String leftFor = r.extra.getCompoundOrEmpty("sim").getStringOr("left_for", "");
		long now = w.level().getGameTime();
		String cause;
		if (!leftFor.isEmpty()) {
			cause = "settlers";
		} else if (MURDERED.remove(r.id) != null || lastFolkDeath == now && lastFolkDeathByPlayer) {
			cause = "slain";
		} else if (lastFolkDeath == now) {
			cause = "killed";
		} else if (r.age == AgeStage.ELDER) {
			cause = "age";
		} else {
			cause = "left";
		}
		if (!cause.equals("settlers")) {
			FamilyLife.gone(s, ledger, r, cause, w.day(), cfg.family(), p -> post(w, s, p));
		}
		Office held = Officials.officeOf(ledger, r.id);
		if (held != null) {
			ledger.offices.remove(held.id());
			r.extra("social").remove("office");
			offices(w, s, w.day(), true);
		}
		s.markDirty();
	}

	private static void post(HearthWorld w, Settlement s, FamilyLife.Posting p) {
		post(w, s, p, w.day());
	}

	/** Puts a notice up (once) and tells players in the settlement, unless the day is long past (catch-up). */
	private static void post(HearthWorld w, Settlement s, FamilyLife.Posting p, long day) {
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		if (Notices.has(ledger, p.key(), p.args(), p.eventDay())) {
			return;
		}
		SocialLedger.Notice n = Notices.post(ledger, p.kind(), day, p.eventDay(), p.key(), p.args(), cfg().life().maxNotices());
		s.markDirty();
		if (cfg().life().announceInChat() && w.day() - day <= 1 && s.embodied()) {
			Component text = Component.translatable("hearth.social.notice.posted", s.name(), Notices.text(n, w.day())).withStyle(ChatFormatting.YELLOW);
			for (ServerPlayer player : w.level().players()) {
				if (s == w.settlementAt(player.blockPosition())) {
					player.sendSystemMessage(text);
				}
			}
		}
	}

	// ---- embodied settlements

	private static void tick(MinecraftServer srv) {
		long gt = srv.overworld().getGameTime();
		Bells.play(STROKES, gt);
		SocialConfig cfg = cfg();
		if (!cfg.life().enabled() || gt % cfg.life().passTicks() != 0 || EMBODIED.isEmpty()) {
			return;
		}
		for (var it = EMBODIED.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Settlement s = e.getValue().settlement(e.getKey());
			if (s == null || !s.embodied()) {
				it.remove();
				continue;
			}
			try {
				pass(e.getValue(), s, cfg);
			} catch (RuntimeException ex) {
				Hearth.LOG.warn("social: town life of {} failed", s.name(), ex);
			}
		}
	}

	private static void pass(HearthWorld w, Settlement s, SocialConfig cfg) {
		ServerLevel level = w.level();
		long now = SocialTime.now(w);
		Day d = day(w, s);
		SocialLedger ledger = s.data(SocialModule.LEDGER);

		// bells
		int minute = SocialTime.minute(now);
		Integer prev = LAST_MINUTE.put(s.id(), minute);
		if (cfg.bells().enabled() && prev != null) {
			int i = Bells.due(cfg.bells(), prev, minute);
			if (i >= 0) {
				if (!d.towerKnown) {
					d.tower = Bells.tower(s);
					d.towerKnown = true;
				}
				if (d.tower != null && level.isLoaded(d.tower.pos())) {
					Bells.ring(STROKES, level, d.tower, cfg.bells().strokesAt(i), cfg.bells().volume());
				}
			}
		}

		// dressings: up for what is on, down for what is over
		Set<String> on = new HashSet<>();
		boolean changed = false;
		for (TownEvent e : d.events) {
			if (!e.activeAt(now)) {
				continue;
			}
			on.add(e.key());
			if (e.kind() == TownEvent.Kind.MARKET && cfg.market().enabled()) {
				if (!d.stalls.containsKey(e.key())) {
					if (Dressings.isUp(ledger, e.key())) {
						Dressings.takeDownEvent(level, ledger, e.key()); // stalls from before a restart: set up anew
					}
					Venues.Venue v = venue(s, e);
					d.stalls.put(e.key(), Dressings.setUpMarket(level, s, ledger, e, v.anchor(), stallCount(s, cfg.market())));
					changed = true;
				}
			} else if (e.kind() == TownEvent.Kind.FESTIVAL && cfg.festivals().decorations() && !Dressings.isUp(ledger, e.key())
				&& !d.stalls.containsKey(e.key())) {
				Venues.Venue v = venue(s, e);
				Dressings.setUpFestival(level, s, ledger, e, v.anchor(), 4 + 2 * Math.max(0, s.tier().ordinal() - 1));
				d.stalls.put(e.key(), List.of()); // tried: do not search for room again today
				changed = true;
			}
		}
		if (!ledger.dressings.isEmpty() && Dressings.takeDown(level, ledger, now, false, on) > 0) {
			changed = true;
		}
		if (changed) {
			s.markDirty();
		}

		// attending a wedding, a funeral or a festival is noticed (once each)
		if (cfg.life().attendStanding()) {
			for (TownEvent e : d.events) {
				if (!e.activeAt(now) || e.kind() == TownEvent.Kind.MARKET || e.kind() == TownEvent.Kind.COURT) {
					continue;
				}
				Venues.Venue v = venue(s, e);
				double r2 = (double) cfg.life().attendRadius() * cfg.life().attendRadius();
				for (ServerPlayer p : level.players()) {
					if (p.blockPosition().distSqr(v.anchor()) <= r2 && ATTENDED.add(s.id() + "/" + e.key() + "/" + p.getUUID())) {
						Hooks.deed(p, s, "social:attended_" + e.kind().id(), 1, e.principals().isEmpty() ? null : e.principals().get(0), v.anchor(), true);
						p.sendSystemMessage(Component.translatable("hearth.social.attended." + e.kind().id(), s.name()).withStyle(ChatFormatting.GRAY), true);
					}
				}
			}
			if (ATTENDED.size() > 4096) {
				ATTENDED.clear();
			}
		}
	}

	/** On becoming embodied: take down whatever outlived its event while the chunks were away. */
	private static void cleanUp(HearthWorld w, Settlement s) {
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		if (ledger.dressings.isEmpty()) {
			return;
		}
		if (Dressings.takeDown(w.level(), ledger, SocialTime.now(w), false, null) > 0) {
			s.markDirty();
		}
	}

	private static void stopping(MinecraftServer srv) {
		for (var e : EMBODIED.entrySet()) {
			Settlement s = e.getValue().settlement(e.getKey());
			if (s == null) {
				continue;
			}
			SocialLedger ledger = s.data(SocialModule.LEDGER);
			if (!ledger.dressings.isEmpty() && Dressings.takeDown(e.getValue().level(), ledger, Long.MAX_VALUE, true, null) > 0) {
				s.markDirty();
			}
		}
	}

	/** For commands: what is going on in embodied settlements. */
	public static List<String> stats() {
		List<String> out = new ArrayList<>();
		out.add("embodied settlements: " + EMBODIED.size() + ", bell strokes queued: " + STROKES.size());
		for (var e : EMBODIED.entrySet()) {
			Settlement s = e.getValue().settlement(e.getKey());
			if (s != null) {
				Day d = DAYS.get(s.id());
				out.add(" " + s.name() + ": " + (d == null ? 0 : d.events.size()) + " events today, " + s.data(SocialModule.LEDGER).dressings.size()
					+ " dressing blocks");
			}
		}
		return out;
	}

	/** For commands: forget today's cached agenda (after forcing an event). */
	public static void refresh(Settlement s) {
		DAYS.remove(s.id());
	}

	/** Rings a settlement's bell now (command). */
	public static boolean ringNow(Settlement s) {
		HearthWorld w = world(s);
		Bells.Tower t = Bells.tower(s);
		if (w == null || t == null) {
			return false;
		}
		Bells.ring(STROKES, w.level(), t, 3, cfg().bells().volume());
		return true;
	}

	/** Holds an event now, for {@code hours} (command and dev harness): a market, a festival, a court. */
	public static @Nullable TownEvent force(Settlement s, TownEvent.Kind kind, @Nullable Festival festival, int hours) {
		List<UUID> principals = new ArrayList<>();
		if (kind == TownEvent.Kind.COURT) {
			UUID lord = s.data(SocialModule.LEDGER).offices.get(Office.LORD.id());
			if (lord != null) {
				principals.add(lord);
			}
		}
		return force(s, kind, festival, 0, hours, principals, List.of(), List.of());
	}

	/** Holds an event from {@code delayHours} from now for {@code hours}, within today. */
	public static @Nullable TownEvent force(Settlement s, TownEvent.Kind kind, @Nullable Festival festival, int delayHours, int hours,
		List<UUID> principals, List<UUID> family, List<String> names) {
		HearthWorld w = world(s);
		if (w == null) {
			return null;
		}
		long now = SocialTime.now(w);
		long dayEnd = (SocialTime.day(now) + 1) * SocialTime.DAY_TICKS - 1;
		long start = Math.min(now + delayHours * 1000L, dayEnd - 1);
		long end = Math.min(start + hours * 1000L, dayEnd);
		TownEvent e = new TownEvent(kind.id() + ":forced:" + start, kind, start, end, festival, principals, family, names);
		FORCED.computeIfAbsent(s.id(), k -> new ArrayList<>()).add(e);
		DAYS.remove(s.id());
		return e;
	}

	/** Runs the day's social business for a settlement now (command): matches, weddings, offices, notices. */
	public static void runDay(Settlement s) {
		HearthWorld w = world(s);
		if (w != null) {
			s.data(SocialModule.LEDGER).feastDay = Long.MIN_VALUE;
			dayPassed(w, s, w.day());
		}
	}

	/** Only for tests and the dev harness. */
	static Map<Long, HearthWorld> embodied() {
		return EMBODIED;
	}
}
