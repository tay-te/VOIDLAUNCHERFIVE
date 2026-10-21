package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.LoadCaps;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.Bells;
import dev.voidmc.hearth.social.life.Dressings;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The rhythm's town-wide side (docs/LIVING_WORLD_2.md §2.3–§2.4). For every settlement, once a day (also in catch-up),
 * the rest day's offering. For embodied settlements, every {@code pass_ticks}: the curfew bell, the rest-day peal, the
 * laundry lines of wash day, winter on the ledges, chimney smoke at meal times, the parents' call at supper, the players
 * at the service, the wash-place scan and the watch lights' upkeep. A pass is a clock check and a few cached lookups.
 */
public final class RhythmDirector {
	static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("rhythm");
	static final LivingWorldStats.Timer SCAN_TIMER = LivingWorldStats.timer("rhythm wash-place scan");
	static final LivingWorldStats.Timer LAUNDRY_TIMER = LivingWorldStats.timer("rhythm laundry");
	static final LivingWorldStats.Timer SMOKE_TIMER = LivingWorldStats.timer("rhythm smoke");
	static final int RECONCILE_TICKS = 200;

	private static final Map<Long, HearthWorld> EMBODIED = new LinkedHashMap<>();
	private static final Map<Long, Integer> LAST_MINUTE = new HashMap<>();
	private static final ArrayDeque<Bells.Stroke> STROKES = new ArrayDeque<>();
	/** Chimneys of each town: plot → the block above its hearth's column top (found once a plot). */
	private static final Map<Long, Chimneys> CHIMNEYS = new HashMap<>();
	private static final Set<String> ATTENDED = new HashSet<>();
	/** Wash day's laundry search per town: {day, next home (MAX: done), lines up}. */
	private static final Map<Long, int[]> LAUNDRY = new HashMap<>();
	/** Wash day's homes per town (the day's washers' homes, found once a day, not every pass). */
	private static final Map<Long, List<PlotView>> LAUNDRY_HOMES = new HashMap<>();
	static final int LAUNDRY_YARDS_A_PASS = 1;
	/** What a town's day is, worked out once a day (the pass reads it every 20 ticks). */
	private static final Map<Long, TownDay> DAYS = new HashMap<>();
	/** The wash-place scan's time a pass, at most (spread over passes: it used to take 0.15 ms a pass, then 0.06 ms). */
	static final long SCAN_NANOS = 15_000;

	/** A town's day: the calendar's answers and the dressings' keys, and whether winter's dressing is seen to. */
	static final class TownDay {
		final long day;
		final boolean rest;
		final boolean wash;
		final boolean winter;
		final boolean firstSnow;
		final String laundryKey;
		final String winterKey;
		boolean winterDone;

		TownDay(Settlement s, long day, RhythmData data, int yearDays) {
			this.day = day;
			this.rest = HearthServices.calendar().restDay(s, day);
			this.wash = HearthServices.calendar().washDay(s, day);
			this.winter = RhythmCalendar.winter(data, day, yearDays);
			this.firstSnow = RhythmCalendar.firstSnow(data, day, yearDays);
			this.laundryKey = Dressings.RHYTHM + "laundry:" + day;
			this.winterKey = Dressings.RHYTHM + "winter:" + day;
		}
	}

	/** The town's day facts, made afresh when its day turns (or a command holds a different day). */
	private static TownDay townDay(Settlement s, long day, RhythmData data, int yearDays) {
		TownDay d = DAYS.get(s.id());
		if (d == null || d.day != day || RhythmCalendar.forced(s.id(), day) != null && d.rest != HearthServices.calendar().restDay(s, day)
			|| RhythmCalendar.forced(s.id(), day) != null && d.wash != HearthServices.calendar().washDay(s, day)) {
			d = new TownDay(s, day, data, yearDays);
			DAYS.put(s.id(), d);
		}
		return d;
	}

	private RhythmDirector() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RhythmDirector::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(RhythmDirector::stopping);
		SettlementEvents.DAY_PASSED.register(RhythmDirector::dayPassed);
		SettlementEvents.EMBODIMENT_CHANGED.register((w, s, embodied) -> {
			if (embodied) {
				EMBODIED.put(s.id(), w);
				WatchLights.clearTown(w.level(), s);
			} else {
				EMBODIED.remove(s.id());
				LAST_MINUTE.remove(s.id());
				DAYS.remove(s.id());
				LAUNDRY_HOMES.remove(s.id());
				WatchLights.clearTown(w.level(), s);
				CHIMNEYS.remove(s.id());
			}
		});
		SettlementEvents.BUILDING_COMPLETED.register((w, s, plot) -> {
			CHIMNEYS.remove(s.id());
			Service.forget(s.id());
		});
	}

	static void clear() {
		EMBODIED.clear();
		LAST_MINUTE.clear();
		STROKES.clear();
		CHIMNEYS.clear();
		ATTENDED.clear();
		LAUNDRY.clear();
		LAUNDRY_HOMES.clear();
		DAYS.clear();
	}

	// ---- abstract: once a day for every settlement

	/** The rest day's offering: a bit a head of those at the service, to the treasury, once a rest day. */
	static void dayPassed(HearthWorld w, Settlement s, long day) {
		RhythmConfig cfg = Rhythm.config();
		if (!cfg.rhythm().enabled() || !cfg.service().enabled() || cfg.service().offering() <= 0) {
			return;
		}
		long t0 = System.nanoTime();
		if (offering(s, day, cfg)) {
			s.markDirty();
		}
		TIMER.add(System.nanoTime() - t0);
	}

	/** Pays a rest day's offering once (pure of the world; tests call it). Returns whether it paid. */
	public static boolean offering(Settlement s, long day, RhythmConfig cfg) {
		RhythmBook book = s.data(RhythmBook.KEY);
		if (book.offeringDay >= day || !HearthServices.calendar().restDay(s, day)) {
			return false;
		}
		book.offeringDay = day;
		// those grown who come put in their bit; the children come with them and put in nothing
		double share = RhythmData.of(s.culture()).service().share();
		int heads = 0;
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.CHILD && Service.goes(r, day, share)) {
				heads++;
			}
		}
		long bits = (long) heads * cfg.service().offering();
		if (bits > 0) {
			s.deposit(bits);
		}
		return true;
	}

	// ---- embodied

	private static void tick(MinecraftServer srv) {
		long gt = srv.overworld().getGameTime();
		Bells.play(STROKES, gt);
		RhythmConfig cfg = Rhythm.config();
		if (!cfg.rhythm().enabled()) {
			return;
		}
		if (gt % RECONCILE_TICKS == 7) {
			reconcile(srv);
		}
		if (gt % cfg.rhythm().passTicks() != 3 % cfg.rhythm().passTicks() || EMBODIED.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		for (var it = EMBODIED.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Settlement s = e.getValue().settlement(e.getKey());
			if (s == null || !s.embodied()) {
				it.remove();
				continue;
			}
			long p0 = System.nanoTime();
			try {
				pass(e.getValue(), s, cfg, gt);
			} catch (RuntimeException ex) {
				Hearth.LOG.warn("rhythm: the day of {} failed", s.name(), ex);
			}
			long took = System.nanoTime() - p0;
			TOWN_PASSES.increment();
			TOWN_NANOS.add(took);
			TOWN_MAX.accumulateAndGet(took, Math::max);
			sample(took);
		}
		TIMER.add(System.nanoTime() - t0);
	}

	/** The last {@code SAMPLES} passes' times (ns, a player's share), for the median: a mean on a busy machine is a few stalls. */
	private static final int SAMPLES = 4096;
	private static final long[] RING = new long[SAMPLES];
	private static int ringNext;
	private static int ringSize;

	static synchronized void sample(long nanos) {
		RING[ringNext] = nanos;
		ringNext = (ringNext + 1) % SAMPLES;
		ringSize = Math.min(SAMPLES, ringSize + 1);
	}

	/** The median and the 90th percentile of the sampled passes, in microseconds ({0, 0} with none). */
	public static synchronized double[] percentiles() {
		if (ringSize == 0) {
			return new double[2];
		}
		long[] a = java.util.Arrays.copyOf(RING, ringSize);
		java.util.Arrays.sort(a);
		return new double[]{a[a.length / 2] / 1e3, a[Math.min(a.length - 1, a.length * 9 / 10)] / 1e3};
	}

	private static synchronized void resetSamples() {
		ringNext = 0;
		ringSize = 0;
	}

	/** A town's pass (one embodied town, every {@code pass_ticks}): how many, their time, the dearest (ns). */
	public static final java.util.concurrent.atomic.LongAdder TOWN_PASSES = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder TOWN_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.AtomicLong TOWN_MAX = new java.util.concurrent.atomic.AtomicLong();

	/** Starts the town-pass figures afresh (the tours' bench measures a warm stretch on its own). */
	public static void resetTimes() {
		TOWN_PASSES.reset();
		TOWN_NANOS.reset();
		TOWN_MAX.set(0);
		resetSamples();
	}

	/** One line: a town's pass on average and at worst since the last reset. */
	public static String timesLine() {
		long n = TOWN_PASSES.sum();
		double[] p = percentiles();
		return String.format(java.util.Locale.ROOT, "rhythm director: %d town passes, %.1f us avg, median %.1f us, p90 %.1f us, %.0f us the dearest", n,
			n == 0 ? 0.0 : TOWN_NANOS.sum() / 1e3 / n, p[0], p[1], TOWN_MAX.get() / 1e3);
	}

	/**
	 * For the tours' bench (a warm JIT): every embodied town's pass {@code n} times at a smoke tick and {@code n} times
	 * between, as in play (the smoke's particles go out; the bells do not ring again: the clock does not move). Microseconds.
	 */
	public static String bench(MinecraftServer srv, int n) {
		RhythmConfig cfg = Rhythm.config();
		long gt = srv.overworld().getGameTime();
		int every = Math.max(1, cfg.smoke().everyTicks());
		long smokeTick = gt - Math.floorMod(gt, every);
		long plainTick = smokeTick + cfg.rhythm().passTicks();
		long smoke = 0;
		long plain = 0;
		int towns = 0;
		for (var e : EMBODIED.entrySet()) {
			Settlement s = e.getValue().settlement(e.getKey());
			if (s == null || !s.embodied()) {
				continue;
			}
			towns++;
			for (int i = 0; i < n; i++) {
				long t0 = System.nanoTime();
				pass(e.getValue(), s, cfg, smokeTick);
				long t1 = System.nanoTime();
				pass(e.getValue(), s, cfg, plainTick);
				plain += System.nanoTime() - t1;
				smoke += t1 - t0;
			}
		}
		double per = Math.max(1, (double) towns * n);
		return String.format(java.util.Locale.ROOT, "rhythm bench (%d towns x %d): a pass %.2f us with the smoke, %.2f us without; wash places %s", towns, n,
			smoke / 1e3 / per, plain / 1e3 / per, EMBODIED.isEmpty() ? "-" : "scanned " + EMBODIED.keySet().stream().allMatch(id -> {
				Settlement s = EMBODIED.get(id).settlement(id);
				return s == null || WashPlaces.scanned(s);
			}));
	}

	private static void reconcile(MinecraftServer srv) {
		for (ServerLevel level : srv.getAllLevels()) {
			HearthWorld w = HearthWorld.of(level);
			if (w == null) {
				continue;
			}
			for (Settlement s : w.settlements()) {
				if (s.embodied() && !EMBODIED.containsKey(s.id())) {
					EMBODIED.put(s.id(), w);
					WatchLights.clearTown(level, s);
				}
			}
		}
	}

	private static void pass(HearthWorld w, Settlement s, RhythmConfig cfg, long gt) {
		ServerLevel level = w.level();
		long now = TownLife.now(w, s);
		long day = SocialTime.day(now);
		int minute = SocialTime.minute(now);
		Integer prev = LAST_MINUTE.put(s.id(), minute);
		RhythmData data = RhythmData.of(s.culture());
		int yearDays = SocialModule.config().festivals().yearDays();
		TownDay today = townDay(s, day, data, yearDays);
		boolean rest = today.rest;
		boolean wash = today.wash;
		boolean admitting = LoadCaps.admitting(LoadCaps.mspt(level.getServer()), LoadCaps.highMspt());

		// bells: the curfew, the rest-day peal
		if (prev != null && SocialModule.config().bells().enabled()) {
			if (crossed(prev, minute, data.curfew())) {
				ring(level, s, 5, 1.0F);
			}
			if (rest && cfg.service().enabled() && crossed(prev, minute, data.service().start() - data.service().peal())) {
				peal(level, s, data.service().peal());
			}
			if (crossed(prev, minute, 18 * 60 + 30)) {
				callChildren(level, s);
			}
		}

		SocialLedger ledger = s.data(SocialModule.LEDGER);
		// wash day: laundry lines in the yards from the morning till the work-end bell
		if (wash && cfg.wash().enabled() && admitting && RhythmFolk.within(minute, data.wash().start(), 17 * 60)) {
			String key = today.laundryKey;
			int[] progress = LAUNDRY.get(s.id());
			if (progress == null || progress[0] != (int) day) {
				// a fresh wash day (or one already seen to before a restart: its lines are in the ledger)
				progress = new int[] {(int) day, Dressings.isUp(ledger, key) ? Integer.MAX_VALUE : 0, 0};
				LAUNDRY.put(s.id(), progress);
				LAUNDRY_HOMES.remove(s.id());
			}
			if (progress[1] != Integer.MAX_VALUE) {
				List<PlotView> homes = LAUNDRY_HOMES.get(s.id());
				if (homes == null) {
					homes = WashBehaviour.homes(s, day);
					if (homes.isEmpty()) {
						homes = Dressings.plotsNear(s, Venues.square(s), 64, cfg.wash().lines() * 3);
					}
					LAUNDRY_HOMES.put(s.id(), homes);
				}
				// a few yards a pass, so the search never makes a slow tick
				int from = progress[1];
				int to = Math.min(homes.size(), from + LAUNDRY_YARDS_A_PASS);
				if (from < to) {
					long t0 = System.nanoTime();
					progress[2] += Dressings.setUpLaundry(level, s, ledger, key, SocialTime.at(day, 17 * 60), homes.subList(from, to),
						cfg.wash().lines() - progress[2]);
					LAUNDRY_TIMER.add(System.nanoTime() - t0);
					s.markDirty();
				}
				progress[1] = to;
				if (to >= homes.size() || progress[2] >= cfg.wash().lines()) {
					if (progress[2] == 0) {
						// a mark in the ledger: this wash day was seen to, though no yard had room
						ledger.dressings.add(new SocialLedger.Dressing(Venues.square(s).below(64).asLong(), "minecraft:air", key, SocialTime.at(day, 17 * 60)));
						s.markDirty();
					}
					progress[1] = Integer.MAX_VALUE;
					LAUNDRY_HOMES.remove(s.id());
				}
			}
		}
		// the cold cultures' winter: snow on the ledges round the square
		if (today.winter && !today.winterDone && admitting) {
			String key = today.winterKey;
			today.winterDone = Dressings.isUp(ledger, key);
			if (!today.winterDone) {
				today.winterDone = true;
				int n = Dressings.dressPlots(level, s, ledger, key, (day + 1) * SocialTime.DAY_TICKS - 1, "winter",
					Dressings.plotsNear(s, Venues.square(s), 96, 30));
				if (n == 0) {
					ledger.dressings.add(new SocialLedger.Dressing(Venues.square(s).below(64).asLong(), "minecraft:air", key, (day + 1) * SocialTime.DAY_TICKS - 1));
				}
				s.markDirty();
				if (today.firstSnow) {
					for (ServerPlayer p : level.players()) {
						if (s == w.settlementAt(p.blockPosition())) {
							p.sendSystemMessage(Component.translatable("hearth.rhythm.first_snow", s.name()).withStyle(ChatFormatting.AQUA), true);
						}
					}
				}
			}
		}

		// chimney smoke at meal times (and the bakers' ovens before dawn)
		if (cfg.smoke().enabled() && gt % cfg.smoke().everyTicks() < cfg.rhythm().passTicks()) {
			long t0 = System.nanoTime();
			smoke(level, s, minute, data, cfg);
			SMOKE_TIMER.add(System.nanoTime() - t0);
		}

		// players at the service
		if (rest && cfg.service().enabled() && Service.part(data.service(), minute) != Service.Part.NONE
			&& Service.part(data.service(), minute) != Service.Part.GATHER) {
			attendance(level, w, s, day, data);
		}

		// the wash-place scan, a little each pass, while the town is embodied
		if (!WashPlaces.scanned(s)) {
			long t0 = System.nanoTime();
			WashPlaces.step(level, s, cfg.wash().reach(), cfg.wash().scanReads(), t0 + SCAN_NANOS);
			SCAN_TIMER.add(System.nanoTime() - t0);
		}

		// lights of watchmen no longer walking
		WatchLights.sweep(level, s, WatchRounds.walking());
	}

	/** Whether the clock passed {@code at} going from {@code prev} to {@code now} (not a jump of hours). */
	static boolean crossed(int prev, int now, int at) {
		if (prev == now) {
			return false;
		}
		int span = Math.floorMod(now - prev, 24 * 60);
		if (span > 30) {
			return false;
		}
		int into = Math.floorMod(at - prev, 24 * 60);
		return into > 0 && into <= span;
	}

	private static void ring(ServerLevel level, Settlement s, int strokes, float pitchScale) {
		Bells.Tower t = Bells.tower(s);
		if (t != null && level.isLoaded(t.pos())) {
			Bells.ring(STROKES, level, new Bells.Tower(t.pos(), t.pitch() * pitchScale), strokes, SocialModule.config().bells().volume());
		}
	}

	/** The rest-day peal: two bells by turns, a stroke a second, for {@code minutes} of the town's clock. */
	private static void peal(ServerLevel level, Settlement s, int minutes) {
		Bells.Tower t = Bells.tower(s);
		if (t == null || !level.isLoaded(t.pos())) {
			return;
		}
		int strokes = Math.max(6, Math.min(40, minutes * SocialTime.DAY_TICKS / SocialTime.MINUTES / 20));
		long g = level.getGameTime();
		float vol = SocialModule.config().bells().volume();
		for (int i = 0; i < strokes; i++) {
			STROKES.add(new Bells.Stroke(g + i * 20L, level, t.pos(), i % 2 == 0 ? t.pitch() : t.pitch() * 1.19F, vol));
		}
	}

	/** At supper, a parent calls their children in by name from the door. */
	private static void callChildren(ServerLevel level, Settlement s) {
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return;
		}
		int calls = 0;
		for (Resident child : s.residents()) {
			if (calls >= 3 || child.age != AgeStage.CHILD) {
				continue;
			}
			NpcService.FolkHandle c = npc.folk(child.id);
			if (c == null || !level.canSeeSky(c.entity().blockPosition())) {
				continue;
			}
			for (UUID parent : FolkFacts.parents(child)) {
				NpcService.FolkHandle p = npc.folk(parent);
				if (p != null && p.awake()) {
					p.lookAt(c.entity());
					if (RhythmBarks.say(p, RhythmBarks.CALL_CHILD, Map.of("child", child.givenName), true)) {
						calls++;
					}
					break;
				}
			}
		}
	}

	/** Campfire smoke from the chimneys of homes whose family is in, at meal times; the bakery's before dawn. */
	private static void smoke(ServerLevel level, Settlement s, int minute, RhythmData data, RhythmConfig cfg) {
		boolean meal = false;
		for (int m : data.meals()) {
			if (RhythmFolk.within(minute, m, m + 60)) {
				meal = true;
				break;
			}
		}
		boolean ovens = RhythmFolk.within(minute, 4 * 60, 11 * 60);
		if (!meal && !ovens) {
			return;
		}
		int most = LoadCaps.cap(cfg.smoke().chimneys(), 0, LoadCaps.mspt(level.getServer()), LoadCaps.highMspt());
		if (most <= 0) {
			return;
		}
		Chimneys found = CHIMNEYS.get(s.id());
		if (found == null) {
			found = new Chimneys(s);
			CHIMNEYS.put(s.id(), found);
		}
		found.step(level, s, CHIMNEY_PLOTS_A_PASS);
		List<Chimney> chimneys = found.list;
		if (chimneys.isEmpty()) {
			return;
		}
		// smoke is seen within 32 blocks (the particle packet goes no further): only chimneys a player is near
		List<ServerPlayer> watchers = new ArrayList<>(2);
		for (ServerPlayer p : level.players()) {
			if (s.bounds().inflatedBy(SMOKE_SEEN).isInside(p.blockPosition())) {
				watchers.add(p);
			}
		}
		if (watchers.isEmpty()) {
			return;
		}
		Set<Integer> homesIn = null;
		int puffs = 0;
		for (Chimney c : chimneys) {
			if (puffs >= most) {
				break;
			}
			if (!seen(watchers, c.top()) || !level.isLoaded(c.top())) {
				continue;
			}
			if (meal && homesIn == null) {
				homesIn = homesIn(s);
			}
			if (!(meal && homesIn.contains(c.plot()) || ovens && c.bakery())) {
				continue;
			}
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.top().getX() + 0.5, c.top().getY() + 0.3, c.top().getZ() + 0.5, 0, 0, 0.07, 0, 1);
			puffs++;
		}
	}

	/** How far off a chimney's smoke is seen (blocks): the particle packet's own reach. */
	static final int SMOKE_SEEN = 32;

	private static boolean seen(List<ServerPlayer> watchers, BlockPos top) {
		for (ServerPlayer p : watchers) {
			if (p.distanceToSqr(top.getX() + 0.5, top.getY() + 0.3, top.getZ() + 0.5) <= SMOKE_SEEN * SMOKE_SEEN) {
				return true;
			}
		}
		return false;
	}

	/** The home plots with someone of the family about (the embodied are at home or near it at meal times). */
	private static Set<Integer> homesIn(Settlement s) {
		Set<Integer> homesIn = new HashSet<>();
		for (Resident r : s.residents()) {
			if (r.home != Resident.NO_PLOT && s.isResidentEmbodied(r.id)) {
				homesIn.add(r.home);
			}
		}
		return homesIn;
	}

	/** A built plot's chimney: the top of the column over its hearth, and whether it is a bakery's (smoking before dawn). */
	record Chimney(int plot, boolean bakery, BlockPos top) {
	}

	/** Plots looked at for a chimney a smoke pass (a city's 400 plots at once cost 86 ms in the work tour). */
	static final int CHIMNEY_PLOTS_A_PASS = 24;

	/**
	 * The town's chimneys, found while it is embodied a few plots a pass (again when a building is finished): the hearths
	 * read once, each plot's first hearth inside it, the top of its column.
	 */
	static final class Chimneys {
		final List<Chimney> list = new ArrayList<>();
		final List<BlockPos> hearths = new ArrayList<>();
		final List<PlotView> plots;
		int next;

		Chimneys(Settlement s) {
			for (Poi p : s.pois(PoiType.HEARTH)) {
				this.hearths.add(p.pos());
			}
			this.plots = this.hearths.isEmpty() ? List.of() : new ArrayList<>(s.plots());
		}

		void step(ServerLevel level, Settlement s, int most) {
			for (int n = 0; n < most && this.next < this.plots.size(); n++) {
				PlotView p = this.plots.get(this.next++);
				if (p.state() != PlotState.BUILT) {
					continue;
				}
				BlockPos o = p.spec().origin();
				for (BlockPos h : this.hearths) {
					if (h.getX() >= o.getX() && h.getX() < o.getX() + p.spec().sizeX() && h.getZ() >= o.getZ() && h.getZ() < o.getZ() + p.spec().sizeZ()) {
						if (level.isLoaded(h)) {
							int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, h.getX(), h.getZ());
							this.list.add(new Chimney(p.id(), p.spec().archetype().getPath().contains("bake"), new BlockPos(h.getX(), y, h.getZ())));
						}
						break;
					}
				}
			}
		}
	}

	/** Players inside the church (or by the open-air altar) during the service: noticed once a week a town. */
	private static void attendance(ServerLevel level, HearthWorld w, Settlement s, long day, RhythmData data) {
		Service.Day d = Service.day(s, day);
		Service.Church c = d.church();
		if (c == null) {
			return;
		}
		long week = Math.floorDiv(day, 7);
		for (ServerPlayer p : level.players()) {
			boolean in = c.plot() != null ? Venues.inside(c.plot().spec(), p.blockPosition()) : p.blockPosition().distSqr(c.altar()) <= 14 * 14;
			if (!in || !ATTENDED.add(s.id() + "/" + week + "/" + p.getUUID())) {
				continue;
			}
			Resident priest = Service.officiant(s);
			if (priest != null) {
				Rhythm.attended(s, priest, p);
			}
			int weight = Rhythm.config().service().standing();
			if (weight > 0) {
				Hooks.deed(p, s, "social:attended_mass", weight, priest == null ? null : priest.id, c.altar(), true);
			}
			p.sendSystemMessage(Component.translatable("hearth.rhythm.attended", ServiceBehaviour.serviceName(data), s.name()).withStyle(ChatFormatting.GRAY),
				true);
		}
		if (ATTENDED.size() > 4096) {
			ATTENDED.clear();
		}
	}

	private static void stopping(MinecraftServer srv) {
		for (var e : EMBODIED.entrySet()) {
			Settlement s = e.getValue().settlement(e.getKey());
			if (s != null) {
				WatchLights.clearTown(e.getValue().level(), s);
			}
		}
	}

	/** For the command: what the rhythm is doing in embodied towns. */
	static List<String> stats() {
		List<String> out = new ArrayList<>();
		out.add(String.format(java.util.Locale.ROOT, "rhythm: %d embodied towns, %d strokes queued, director %.3f ms avg, folk %.4f ms avg a step",
			EMBODIED.size(), STROKES.size(), TIMER.averageMs(), RhythmFolk.FOLK_TIMER.averageMs()));
		if (!Dressings.LAUNDRY_MISSES.isEmpty()) {
			out.add(" laundry lines not put up: " + Dressings.LAUNDRY_MISSES);
		}
		for (var e : EMBODIED.entrySet()) {
			Settlement s = e.getValue().settlement(e.getKey());
			if (s == null) {
				continue;
			}
			long day = TownLife.today(e.getValue(), s);
			long laundry = s.data(SocialModule.LEDGER).dressings.stream().filter(d -> d.event().startsWith(Dressings.RHYTHM)).count();
			out.add(String.format(java.util.Locale.ROOT, " %s day %d (world %d): rest %s, wash %s, market %s, washers %d, drinkers %d, at service %d, wash places %d%s, lights %d, "
				+ "rhythm dressing blocks %d", s.name(), day, e.getValue().day(), HearthServices.calendar().restDay(s, day), HearthServices.calendar().washDay(s, day),
				HearthServices.calendar().marketDay(s, day), WashBehaviour.washers(s, day).size(), RhythmSchedule.drinkers(s, day).size(),
				Service.day(s, day).attending().size(), WashPlaces.of(s).size(), WashPlaces.scanned(s) ? "" : " (scanning)", WatchLights.standing(s), laundry));
		}
		return out;
	}

	static @Nullable HearthWorld worldOf(long settlement) {
		return EMBODIED.get(settlement);
	}
}
