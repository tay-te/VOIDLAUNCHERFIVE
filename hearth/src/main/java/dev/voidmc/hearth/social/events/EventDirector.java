package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.law.justice.Trials;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.office.Office;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The event book's keeper (docs/LIVING_WORLD_2.md §3.2): when a town's day turns, its day of events is planned
 * ({@link EventScheduler}) from its facts and what its books already hold; every second the towns with events coming are
 * looked at — an embodied town's event is staged when its time comes ({@link Scenes}), an abstract town's resolved when
 * it ends ({@link Outcomes}, {@link EventEffects}). Cheap: the day's work is one pass over a town's residents and a few
 * cached lookups; the sweep reads only towns with something in the book.
 */
public final class EventDirector {
	/** Towns with events not yet ended, by settlement id → their world. */
	private static final Map<Long, HearthWorld> WATCH = new LinkedHashMap<>();
	/** Events offered for a later day (an execution at dawn, a topping-out, a christening): by settlement. */
	private static final Map<Long, List<Later>> LATER = new HashMap<>();
	static final dev.voidmc.hearth.sim.LivingWorldStats.Timer DAY_TIMER = dev.voidmc.hearth.sim.LivingWorldStats.timer("events days");
	static long daysPlanned;
	static long dayNanos;
	static long resolvedAbstract;
	static long resolvedEmbodied;

	private record Later(long day, EventScheduler.Offer offer) {
	}

	private EventDirector() {
	}

	static void clear() {
		WATCH.clear();
		LATER.clear();
	}

	static void watch(HearthWorld w, Settlement s) {
		WATCH.put(s.id(), w);
	}

	// ---------------------------------------------------------------- the day

	static void dayPassed(HearthWorld w, Settlement s, long day) {
		if (!TownEvents.config().events().enabled() || s.population() == 0) {
			return;
		}
		long t0 = System.nanoTime();
		try {
			planDay(w, s, day);
		} catch (RuntimeException ex) {
			Hearth.LOG.warn("events: the day of {} could not be planned", s.name(), ex);
		}
		long dt = System.nanoTime() - t0;
		daysPlanned++;
		dayNanos += dt;
		DAY_TIMER.add(dt);
	}

	static void planDay(HearthWorld w, Settlement s, long day) {
		EventBook book = s.data(EventBook.KEY);
		if (book.lastDay != Long.MIN_VALUE && day <= book.lastDay) {
			return; // a day is planned once
		}
		book.lastDay = day;
		SocialConfig cfg = SocialModule.config();
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		// what has had its day goes: ended events two days on, plots rebuilt, takings that never came home
		book.scheduled.removeIf(e -> e.day() < day - 2 && e.phase() == Scheduled.Phase.ENDED || e.day() < day - 6);
		book.burnt.removeIf(b -> b.until() < day && (!b.applied() || b.until() < day - 4));
		book.defendants.removeIf(d -> s.resident(d.resident()) == null || d.day() < day - 28);

		TownFacts facts = FactsReader.read(w, s, day);
		// a crime among the townsfolk, perhaps (they wait for the court day)
		Set<UUID> waiting = new HashSet<>();
		for (Trials.Defendant d : book.defendants) {
			waiting.add(d.resident());
		}
		if (book.defendants.size() < EventBook.MAX_DEFENDANTS) {
			int feuds = 0;
			try {
				var lives = HearthServices.lives();
				feuds = lives == null ? 0 : lives.feuds(s).size();
			} catch (RuntimeException ignored) {
				feuds = 0;
			}
			Trials.Defendant d = Trials.roll(s.id(), day, s.residents(), facts.prosperity(), feuds, 0, new HashSet<>(ledger.offices.values()), waiting,
				witnesses(w, s));
			if (d != null) {
				book.defendants.add(d);
			}
		}

		List<EventScheduler.Offer> offers = offers(w, s, book, ledger, cfg, facts, day);
		List<Scheduled> planned = EventScheduler.plan(facts, day, EventRules.all(), offers, book.lastTax, book.raidQuietUntil,
			(kind, draft) -> fill(w, s, book, facts, kind, draft), book::newId);
		for (Scheduled e : planned) {
			if (!book.add(e)) {
				continue;
			}
			switch (e.kind()) {
				case TAX -> {
					book.lastTax = day;
					Scheduled timed = TaxRounds.dispatch(w, s, e);
					if (timed != e) {
						book.replace(timed);
					}
				}
				case RAID -> book.raidQuietUntil = day + 6;
				case TRIAL -> book.defendants.removeIf(d -> e.principals().contains(d.resident()));
				default -> {
				}
			}
			announce(w, s, e);
			try {
				SettlementEvents.TOWN_EVENT.invoker().onTownEvent(w, s, e.kind().id(), SettlementEvents.TownEvent.Phase.SCHEDULED, "");
			} catch (RuntimeException ex) {
				Hearth.LOG.warn("events: a listener of {} failed", e.kind().id(), ex);
			}
		}
		// the fair and the tournament are cried three days ahead
		if (TownCalendar.isFairDay(s.id(), day + 3, s.culture(), s.foundedDay(), s.tier(), cfg.festivals())
			&& !TownCalendar.isFairDay(s.id(), day + 2, s.culture(), s.foundedDay(), s.tier(), cfg.festivals()) && rule(EventKind.FAIR, facts)) {
			EventEffects.post(w, s, "fair", day + 3, "hearth.events.notice.fair_coming", List.of());
		}
		if (TownCalendar.isTourneyDay(s.id(), day + 3, s.culture(), s.foundedDay(), s.tier(), cfg.festivals()) && facts.lord() && facts.has("tilt_yard")) {
			EventEffects.post(w, s, "tournament", day + 3, "hearth.events.notice.tournament_coming", List.of());
		}
		s.markDirty();
		if (hasOpen(book)) {
			WATCH.put(s.id(), w);
		}
	}

	private static boolean rule(EventKind k, TownFacts f) {
		EventRule r = EventRules.of(k);
		return r != null && f.tier().ordinal() >= r.minTier().ordinal();
	}

	/** What the town's books bring about today. */
	private static List<EventScheduler.Offer> offers(HearthWorld w, Settlement s, EventBook book, SocialLedger ledger, SocialConfig cfg, TownFacts facts,
		long day) {
		List<EventScheduler.Offer> out = new ArrayList<>(familyOffers(ledger, cfg, day));
		// the lord's own wedding brings a tournament the day after, where there are lists to hold it in
		UUID lord = ledger.offices.get(Office.LORD.id());
		for (EventScheduler.Offer o : out) {
			if (o.kind() == EventKind.WEDDING && lord != null && o.principals().contains(lord) && facts.has("tilt_yard")) {
				later(s, day + 1, EventScheduler.Offer.of(EventKind.TOURNAMENT, List.of(), List.of()));
			}
		}
		UUID judge = judge(ledger);
		Resident jr = judge == null ? null : s.resident(judge);
		EventScheduler.Offer trial = trialOffer(facts, book.defendants, s::resident, jr == null ? Set.of() : new HashSet<>(FolkFacts.traits(jr)),
			cfg.offices().courtStart(), TownEvents.config().events().executions(), day);
		if (trial != null) {
			out.add(trial);
		}
		List<Later> later = LATER.get(s.id());
		if (later != null) {
			for (Later l : List.copyOf(later)) {
				if (l.day() == day) {
					out.add(l.offer());
				}
			}
			later.removeIf(l -> l.day() <= day);
			if (later.isEmpty()) {
				LATER.remove(s.id());
			}
		}
		return out;
	}

	/** The weddings and funerals the social ledger holds for a day, as offers (pure). */
	static List<EventScheduler.Offer> familyOffers(SocialLedger ledger, SocialConfig cfg, long day) {
		List<EventScheduler.Offer> out = new ArrayList<>();
		for (SocialLedger.FamilyEvent fe : ledger.family) {
			if (fe.day() != day) {
				continue;
			}
			switch (fe.kind()) {
				case WEDDING -> {
					// the procession half an hour before the ceremony, then the vows and the feast
					int at = Math.floorMod(cfg.family().weddingTime() - 30, SocialTime.MINUTES);
					CompoundTag a = new CompoundTag();
					a.putString("names", String.join(" & ", fe.names()));
					out.add(new EventScheduler.Offer(EventKind.WEDDING, at, 30 + 60 + 180, fe.people(), fe.family(), -1, a));
				}
				case FUNERAL -> {
					int at = Math.floorMod(cfg.family().funeralTime() - 20, SocialTime.MINUTES);
					CompoundTag a = new CompoundTag();
					a.putString("names", fe.names().isEmpty() ? "" : fe.names().get(0));
					a.putString("cause", fe.cause());
					out.add(new EventScheduler.Offer(EventKind.FUNERAL, at, 20 + 60, fe.people(), fe.family(), -1, a));
				}
			}
		}
		return out;
	}

	/**
	 * The trial a court day holds: the longest-waiting defendant still in town, or null on any other day, or with nobody
	 * to try (pure).
	 */
	static EventScheduler.@Nullable Offer trialOffer(TownFacts facts, List<Trials.Defendant> waiting, java.util.function.Function<UUID, Resident> resident,
		Set<String> judgeTraits, int courtStart, String executions, long day) {
		if (!facts.courtDay()) {
			return null;
		}
		for (Trials.Defendant d : waiting) {
			Resident r = resident.apply(d.resident());
			if (r == null) {
				continue;
			}
			CompoundTag a = new CompoundTag();
			a.putString("offence", d.offence());
			long seed = TownCalendar.mix(facts.id() * 7 + day);
			a.putDouble("evidence", Trials.evidence(seed));
			a.putDouble("leniency", Trials.leniency(judgeTraits, seed));
			a.putBoolean("poor", Trials.poor(r));
			a.putLong("fine", Trials.fine(d.offence()));
			a.putString("executions", executions);
			a.put("witnesses", Outcomes.uuidList(d.witnesses()));
			a.putString("accused", r.fullName());
			if (d.victim() != null) {
				Resident v = resident.apply(d.victim());
				a.putString("victim", v == null ? "" : v.fullName());
			}
			int at = Math.floorMod(courtStart + 30, SocialTime.MINUTES);
			return new EventScheduler.Offer(EventKind.TRIAL, at, 60, List.of(d.resident()), List.of(), -1, a);
		}
		return null;
	}

	static @Nullable UUID judge(SocialLedger ledger) {
		UUID j = ledger.offices.get(Office.JUDGE.id());
		if (j == null) {
			j = ledger.offices.get(Office.LORD.id());
		}
		return j == null ? ledger.offices.get(Office.REEVE.id()) : j;
	}

	/** Players in the town now (commands, tours). */
	static List<UUID> playersIn(HearthWorld w, Settlement s) {
		return witnesses(w, s);
	}

	/** Players in the embodied town now: they saw what was done today. */
	private static List<UUID> witnesses(HearthWorld w, Settlement s) {
		if (!s.embodied()) {
			return List.of();
		}
		List<UUID> out = new ArrayList<>();
		for (ServerPlayer p : w.level().players()) {
			if (w.settlementAt(p.blockPosition()) == s) {
				out.add(p.getUUID());
			}
		}
		return out;
	}

	/** Completes an event's facts: the plot that burns, the band's strength and the stores, the contest's competitors. */
	static boolean fill(HearthWorld w, Settlement s, EventBook book, TownFacts f, EventKind kind, EventScheduler.Draft d) {
		CompoundTag a = d.args;
		switch (kind) {
			case FIRE -> {
				PlotView p = burnable(s, book, d.seed, false);
				if (p == null) {
					return false;
				}
				d.plot = p.id();
				a.putString("household", EventEffects.household(s, p.id()));
			}
			case RAID -> {
				a.putInt("strength", f.threat());
				a.putInt("defence", f.defence());
				a.putLong("band", f.threatBand());
				int[] stores = new int[Goods.values().length];
				for (Goods g : Goods.values()) {
					stores[g.ordinal()] = s.stockpile().get(g);
				}
				a.putIntArray("stores", stores);
				a.putLong("treasury", s.treasury());
				PlotView barn = burnable(s, book, d.seed, true);
				a.putInt("barn", barn == null ? -1 : barn.id());
				d.plot = barn == null ? -1 : barn.id();
				a.putBoolean("deaths_allowed", TownEvents.config().events().raidDeaths());
				List<UUID> victims = new ArrayList<>();
				for (Resident r : s.residents()) {
					if (r.age == AgeStage.ADULT && victims.size() < 12) {
						victims.add(r.id);
					}
				}
				a.put("victims", Outcomes.uuidList(victims));
			}
			case TAX -> {
				a.putLong("treasury", s.treasury());
				a.putInt("population", s.population());
				a.putLong("seat", f.seat());
				Settlement seat = w.settlement(f.seat());
				a.putString("seat_name", seat == null ? "" : seat.name());
				Resident poor = poorest(s, d.seed);
				if (poor != null) {
					a.putString("poor_family", poor.id.toString());
					a.putString("poor_name", poor.familyName);
				}
			}
			case FAIR -> {
				ListTag comps = new ListTag();
				for (UUID id : SceneCast.pool(s, new Scheduled(0, kind, 0, 0, 0, List.of(), List.of(), -1, d.seed, a, Scheduled.Phase.SCHEDULED),
					"competitors", s.center(), Set.of())) {
					Resident r = s.resident(id);
					if (r == null || comps.size() >= 3) {
						continue;
					}
					CompoundTag c = new CompoundTag();
					c.putString("name", r.fullName());
					c.putString("id", r.id.toString());
					c.putDouble("skill", skill(r));
					comps.add(c);
				}
				a.put("competitors", comps);
				a.putInt("stalls", 3 * TownLife.stallCount(s, SocialModule.config().market()));
				a.putBoolean("contest", true);
			}
			case TOURNAMENT -> {
				ListTag knights = new ListTag();
				for (UUID id : SceneCast.pool(s, new Scheduled(0, kind, 0, 0, 0, List.of(), List.of(), -1, d.seed, a, Scheduled.Phase.SCHEDULED), "knights",
					s.center(), Set.of())) {
					Resident r = s.resident(id);
					if (r == null || knights.size() >= 4) {
						continue;
					}
					CompoundTag c = new CompoundTag();
					c.putString("name", r.fullName());
					c.putString("id", r.id.toString());
					c.putDouble("skill", skill(r));
					knights.add(c);
				}
				if (knights.size() < 2) {
					return false;
				}
				a.put("knights", knights);
			}
			default -> {
			}
		}
		return true;
	}

	/** A folk's hand with a bow or a lance, 0–1: hunters and guards best, the young and the old worst. */
	static double skill(Resident r) {
		double s = switch (r.job) {
			case HUNTER -> 0.75;
			case GUARD, LORD -> 0.6;
			case FORESTER, HERDER -> 0.45;
			default -> 0.3;
		};
		if (r.station == Station.GENTRY || r.station == Station.LORD) {
			s += 0.1;
		}
		s += (Math.floorMod(r.id.getLeastSignificantBits(), 100L) - 50) / 500.0;
		return Math.max(0.05, Math.min(0.95, s));
	}

	/** A plot that can burn: a lived-in house of timber (or, for a raid, a barn or storehouse), not burnt already. */
	static @Nullable PlotView burnable(Settlement s, EventBook book, long seed, boolean stores) {
		List<PlotView> c = new ArrayList<>();
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.BUILT || book.burnt(p.id()) != null || s.deserted(p.id())) {
				continue;
			}
			String path = p.spec().archetype().getPath();
			boolean ok = stores ? path.equals("work/barn") || path.startsWith("storage/")
				: path.startsWith("house/") && !path.equals("house/manor") && !path.equals("house/tower_house") || path.equals("work/barn");
			if (ok) {
				c.add(p);
			}
		}
		if (c.isEmpty()) {
			return null;
		}
		c.sort((x, y) -> Integer.compare(x.id(), y.id()));
		return c.get((int) Math.floorMod(seed >>> 3, (long) c.size()));
	}

	/** The head of the town's poorest household (the one that cannot pay), or null. */
	static @Nullable Resident poorest(Settlement s, long seed) {
		List<Resident> poor = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && r.station == Station.POOR && r.home != Resident.NO_PLOT) {
				poor.add(r);
			}
		}
		if (poor.isEmpty()) {
			return null;
		}
		poor.sort((x, y) -> x.id.compareTo(y.id));
		return poor.get((int) Math.floorMod(seed >>> 11, (long) poor.size()));
	}

	/** Tells the town what is coming, for the events with a notice of their own (weddings and funerals have theirs). */
	private static void announce(HearthWorld w, Settlement s, Scheduled e) {
		switch (e.kind()) {
			case TRIAL -> EventEffects.post(w, s, "trial", e.day(), "hearth.events.notice.trial",
				List.of(e.args().getStringOr("accused", ""), "@hearth.events.offence." + e.args().getStringOr("offence", "theft")));
			case EXECUTION -> EventEffects.post(w, s, "execution", e.day(), "hearth.events.notice.execution", List.of(EventEffects.name(s, e.principals())));
			case TAX -> EventEffects.post(w, s, "tax", e.day(), "hearth.events.notice.tax_day", List.of(e.args().getStringOr("seat_name", "")));
			case FAIR -> EventEffects.post(w, s, "fair", e.day(), "hearth.events.notice.fair", List.of());
			case TOURNAMENT -> EventEffects.post(w, s, "tournament", e.day(), "hearth.events.notice.tournament_day", List.of());
			case TOPPING_OUT -> EventEffects.post(w, s, "topping_out", e.day(), "hearth.events.notice.topping_out", List.of(e.args().getStringOr("building", "")));
			case CHRISTENING -> EventEffects.post(w, s, "christening", e.day(), "hearth.events.notice.christening", List.of(e.args().getStringOr("child", "")));
			case DECREE -> EventEffects.post(w, s, "decree", e.day(), "hearth.events.notice.decree", List.of());
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- offers from elsewhere

	/** An event to be held today (a pillory after the trial): straight into the book and the watch. */
	static void offerNow(HearthWorld w, Settlement s, EventScheduler.Offer offer, long day) {
		EventRule rule = EventRules.of(offer.kind());
		EventBook book = s.data(EventBook.KEY);
		if (rule == null) {
			return;
		}
		Scheduled e = EventScheduler.single(s.id(), day, offer, rule, book.newId());
		if (book.add(e)) {
			announce(w, s, e);
			s.markDirty();
			WATCH.put(s.id(), w);
		}
	}

	/** An event to be held on a later day (an execution at dawn, a topping-out tomorrow). */
	static void later(Settlement s, long day, EventScheduler.Offer offer) {
		List<Later> l = LATER.computeIfAbsent(s.id(), k -> new ArrayList<>());
		if (l.size() < 8) {
			l.add(new Later(day, offer));
		}
	}

	/** A large building's roof is on: the crew and the neighbours drink to it tomorrow evening. */
	static void buildingCompleted(HearthWorld w, Settlement s, PlotView plot) {
		FactsReader.forget(s.id());
		String path = plot.spec().archetype().getPath();
		int area = plot.spec().sizeX() * plot.spec().sizeZ();
		if (area < 90 || path.startsWith("infra/") || path.startsWith("landscape/") || path.startsWith("farm/") || path.startsWith("extract/")
			|| s.data(SocialModule.LEDGER).lastDay < w.day() - 1) {
			return;
		}
		CompoundTag a = new CompoundTag();
		String what = path.startsWith("house/") ? "house" : path.startsWith("civic/") || path.startsWith("noble/") || path.startsWith("commerce/")
			|| path.startsWith("law/") ? "hall" : path.startsWith("storage/") ? "storehouse" : path.equals("work/barn") ? "barn" : "workshop";
		a.putString("building", "@hearth.events.building." + what);
		later(s, TownLife.today(w, s) + 1, new EventScheduler.Offer(EventKind.TOPPING_OUT, -1, -1, List.of(), List.of(), plot.id(), a));
	}

	/** A child was born: christened at the chapel on the next rest day (or within the week). */
	static void born(HearthWorld w, Settlement s, Resident child) {
		long today = TownLife.today(w, s);
		long when = today + 1;
		for (long d = today + 1; d <= today + 7; d++) {
			if (HearthServices.calendar().restDay(s, d)) {
				when = d;
				break;
			}
		}
		List<UUID> who = new ArrayList<>();
		who.add(child.id);
		who.addAll(FolkFacts.parents(child));
		CompoundTag a = new CompoundTag();
		a.putString("child", child.givenName);
		later(s, when, new EventScheduler.Offer(EventKind.CHRISTENING, -1, -1, who, List.of(), -1, a));
	}

	// ---------------------------------------------------------------- the sweep

	/** Every second: towns with events coming are looked at; every minute the books of every town are read again. */
	static void tick(MinecraftServer server) {
		long gt = server.overworld().getGameTime();
		if (gt % 1200 == 7) {
			reconcile(server);
		}
		if (gt % 20 != 3 || WATCH.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		for (var it = WATCH.entrySet().iterator(); it.hasNext();) {
			var en = it.next();
			Settlement s = en.getValue().settlement(en.getKey());
			if (s == null) {
				it.remove();
				continue;
			}
			try {
				if (!sweep(en.getValue(), s)) {
					it.remove();
				}
			} catch (RuntimeException ex) {
				Hearth.LOG.warn("events: the book of {} could not be read", s.name(), ex);
				it.remove();
			}
		}
		Scenes.TIMER.add(System.nanoTime() - t0);
	}

	/** Reads every town's book (after a restart, a town loaded): those with events not yet ended are watched. */
	static void reconcile(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			HearthWorld w = HearthWorld.of(level);
			if (w == null) {
				continue;
			}
			for (Settlement s : w.settlements()) {
				if (!WATCH.containsKey(s.id()) && hasOpen(s.data(EventBook.KEY))) {
					WATCH.put(s.id(), w);
				}
			}
		}
	}

	private static boolean hasOpen(EventBook book) {
		for (Scheduled e : book.scheduled) {
			if (e.phase() != Scheduled.Phase.ENDED) {
				return true;
			}
		}
		return false;
	}

	/** One town's book: stage what has come (embodied), resolve what is over (abstract). Returns whether to keep watching. */
	static boolean sweep(HearthWorld w, Settlement s) {
		EventBook book = s.data(EventBook.KEY);
		long now = TownLife.now(w, s);
		boolean open = false;
		for (Scheduled e : List.copyOf(book.scheduled)) {
			switch (e.phase()) {
				case ENDED -> {
				}
				case SCHEDULED -> {
					if (now >= e.end()) {
						resolve(w, s, e, Interventions.NONE);
					} else if (now >= e.start() && s.embodied()) {
						SceneRun run = Scenes.begin(w, s, e);
						if (run != null) {
							Scheduled begun = e.withPhase(Scheduled.Phase.BEGUN);
							book.replace(begun);
							run.event = begun;
							s.markDirty();
							SettlementEvents.TOWN_EVENT.invoker().onTownEvent(w, s, e.kind().id(), SettlementEvents.TownEvent.Phase.BEGUN, "");
						}
						open = true;
					} else {
						open = true;
					}
				}
				case BEGUN -> {
					SceneRun run = Scenes.run(s.id(), e.id());
					if (run == null) {
						// its scene is gone (a restart, the town folded before the scene could say so): the books decide
						resolve(w, s, e, Interventions.NONE);
					} else {
						open = true;
					}
				}
			}
		}
		return open;
	}

	/** Decides an event and applies it (once). */
	static void resolve(HearthWorld w, Settlement s, Scheduled e, Interventions iv) {
		EventBook book = s.data(EventBook.KEY);
		Scheduled now = book.get(e.id());
		if (now == null || now.phase() == Scheduled.Phase.ENDED) {
			return;
		}
		EventRule rule = now.kind() == EventKind.SCENE ? EventRules.scene(now.args().getStringOr("scene", "")) : EventRules.of(now.kind());
		if (rule == null) {
			book.replace(now.withPhase(Scheduled.Phase.ENDED));
			return;
		}
		Outcome o = Outcomes.resolve(now, rule, iv);
		if (iv.any()) {
			resolvedEmbodied++;
		} else {
			resolvedAbstract++;
		}
		EventEffects.apply(w, s, now, o);
	}

	static List<String> stats() {
		List<String> out = new ArrayList<>();
		out.add(String.format(Locale.ROOT, "events: %d towns watched, %d days planned (%.1f µs avg), %d resolved by the books, %d with players' say",
			WATCH.size(), daysPlanned, daysPlanned == 0 ? 0 : dayNanos / 1e3 / daysPlanned, resolvedAbstract, resolvedEmbodied));
		return out;
	}

	/** Players in the scene's town who are near a point (for the scene directors). */
	static List<ServerPlayer> playersNear(ServerLevel level, BlockPos at, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().distSqr(at) <= radius * radius) {
				out.add(p);
			}
		}
		return out;
	}

	static NpcService.@Nullable FolkHandle folk(UUID id) {
		NpcService npc = HearthServices.npc();
		return npc == null ? null : npc.folk(id);
	}

	static boolean adultOfTrade(Resident r, JobType job) {
		return r.age == AgeStage.ADULT && r.job == job;
	}
}
