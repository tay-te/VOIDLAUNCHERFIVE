package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.CrowdService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SceneService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.Bells;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The scene runner (docs/LIVING_WORLD_2.md §0.2 "Scenes", §3.3): stages a town event in an embodied town — casts its
 * roles from the residents, gives each a {@link SceneRoleBehaviour} order (priority 230) that walks, stands, poses and
 * barks on the timeline, draws the crowd through the crowds' spectacle, plays its sounds and puts up its dressings — and
 * when it ends resolves it through the same {@link Outcomes} as an abstract town, with what players did. One major scene
 * a town at a time; beats every 20 ticks; nothing of it is saved (a scene running at a restart resolves abstractly).
 * Also the {@link SceneService} other lanes stage through (an oath, a dubbing, a named character's wedding).
 */
public final class Scenes implements SceneService {
	public static final Scenes SERVICE = new Scenes();
	private static final Map<Long, List<SceneRun>> RUNNING = new HashMap<>();
	private static final Map<UUID, Order> ORDERS = new HashMap<>();
	private static final ArrayDeque<Bells.Stroke> STROKES = new ArrayDeque<>();
	static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("events");
	/** Most principals a scene embodies itself (the couple, the accused) when they are only records. */
	static final int EMBODY_PRINCIPALS = 4;
	/** Scenes not staged because the server was slow (their events resolved by the books). */
	static long refusedForLoad;

	private Scenes() {
	}

	/** A cast member's part now: where to be, what to face, what to do there. */
	static final class Order {
		final SceneRun run;
		final String role;
		@Nullable BlockPos target;
		@Nullable BlockPos focus;
		double speed = 0.6;
		String pose = "";
		int poseTicks;
		boolean poseDone = true;
		ItemStack hold = ItemStack.EMPTY;
		boolean holdDone = true;
		boolean music;
		boolean carry;
		/** Circles round its target (dancing at the feast, children after the pig). */
		boolean dance;
		long nextMove;
		boolean moved = true;

		Order(SceneRun run, String role) {
			this.run = run;
			this.role = role;
		}
	}

	// ---------------------------------------------------------------- staging

	/** Whether this event may be staged now: the town is embodied and, for a major one, no other major scene is on. */
	static boolean canStage(Settlement s, Scheduled e) {
		if (!s.embodied()) {
			return false;
		}
		if (e.kind().major && TownEvents.config().events().oneMajorScene()) {
			for (SceneRun r : runs(s.id())) {
				if (r.event.kind().major) {
					return false;
				}
			}
		}
		return run(s.id(), e.id()) == null;
	}

	/** Stages an event: casts it, plays the beats already due (a scene joined late), starts its director. */
	static @Nullable SceneRun begin(HearthWorld w, Settlement s, Scheduled e) {
		long t0 = System.nanoTime();
		try {
			return begin0(w, s, e);
		} finally {
			COSTS.merge("begin " + e.kind().id(), System.nanoTime() - t0, Math::max);
		}
	}

	/** The worst a scene's start and each kind of beat has cost (ns), for the status and the tours. */
	static final Map<String, Long> COSTS = new java.util.TreeMap<>();

	private static @Nullable SceneRun begin0(HearthWorld w, Settlement s, Scheduled e) {
		EventRule rule = e.kind() == EventKind.SCENE ? EventRules.scene(e.args().getStringOr("scene", "")) : EventRules.of(e.kind());
		if (rule == null || !canStage(s, e)) {
			return null;
		}
		ServerLevel level = w.level();
		// a slow server stages nothing new: the event is decided by the books at its end, as in an abstract town (§9.2)
		if (!dev.voidmc.hearth.sim.LoadCaps.admitting(dev.voidmc.hearth.sim.LoadCaps.mspt(level.getServer()), dev.voidmc.hearth.sim.LoadCaps.highMspt())) {
			refusedForLoad++;
			return null;
		}
		BlockPos anchor = SceneVenues.at(s, e, rule.venue().isEmpty() ? "square" : rule.venue());
		if (!level.isLoaded(anchor)) {
			return null;
		}
		NpcService npc = HearthServices.npc();
		Map<String, List<UUID>> cast = SceneCast.cast(s, e, rule, anchor, id -> npc != null && npc.folk(id) != null);
		SceneRun run = new SceneRun(w, s, e, rule, cast, anchor, SceneDirector.of(e.kind()), level.getGameTime());
		RUNNING.computeIfAbsent(s.id(), k -> new ArrayList<>()).add(run);
		int embodied = 0;
		for (Map.Entry<String, List<UUID>> role : cast.entrySet()) {
			for (UUID id : role.getValue()) {
				ORDERS.put(id, new Order(run, role.getKey()));
				// the couple, the accused, the dead's kin: brought out if they are only records (within the npc module's caps)
				if (npc != null && embodied < EMBODY_PRINCIPALS && e.principals().contains(id) && npc.folk(id) == null) {
					Resident r = s.resident(id);
					if (r != null && !s.isResidentAway(id) && npc.embodyNear(level, s, r, anchor) != null) {
						embodied++;
					}
				}
			}
		}
		for (EventRule.RoleSpec spec : rule.roles()) {
			if (!spec.place().isEmpty() || !spec.hold().isEmpty()) {
				for (UUID id : run.role(spec.name())) {
					Order o = ORDERS.get(id);
					if (o == null) {
						continue;
					}
					if (!spec.place().isEmpty()) {
						place(run, o, id, spec.place(), 0.6, 0);
					}
					if (!spec.hold().isEmpty()) {
						o.hold = item(spec.hold());
						o.holdDone = false;
					}
				}
			}
		}
		try {
			run.director.begin(run, s);
		} catch (RuntimeException ex) {
			Hearth.LOG.warn("events: the {} scene in {} could not begin", e.kind().id(), s.name(), ex);
		}
		advance(run, s, TownLife.now(w, s), true);
		return run;
	}

	// ---------------------------------------------------------------- running

	/** Every tick: the toll's strokes; every 20 ticks, every running scene (timed for the server cost). */
	static void tick(MinecraftServer server) {
		long gt = server.overworld().getGameTime();
		Bells.play(STROKES, gt);
		if (gt % 20 != 0 || RUNNING.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		List<SceneRun> all = new ArrayList<>();
		for (List<SceneRun> l : RUNNING.values()) {
			all.addAll(l);
		}
		for (SceneRun run : all) {
			Settlement s = run.settlement();
			if (s == null) {
				end(run, false);
				continue;
			}
			try {
				if (!s.embodied()) {
					end(run, true); // the town folded: what was done so far stands, the rest resolves as the books say
					continue;
				}
				long now = TownLife.now(run.world, s);
				advance(run, s, now, false);
				run.director.tick(run, s, gt);
				if (now >= run.event.end() || run.director.done(run, s) || Boolean.TRUE.equals(run.get("end_now"))) {
					end(run, true);
				}
			} catch (RuntimeException ex) {
				Hearth.LOG.warn("events: the {} scene in {} failed", run.event.kind().id(), s.name(), ex);
				end(run, true);
			}
		}
		TIMER.add(System.nanoTime() - t0);
	}

	/** Plays the beats that fell due since the last pass. */
	private static void advance(SceneRun run, Settlement s, long now, boolean first) {
		int minute = Timeline.minutes(run.event.start(), now);
		if (minute <= run.minute) {
			return;
		}
		List<EventRule.Beat> due = Timeline.due(run.rule.timeline(), first ? -1 : run.minute, minute);
		run.minute = minute;
		for (EventRule.Beat b : due) {
			// joining late: where folk stand and what they hold still matter; a toll or a cry long past does not
			boolean stale = first && b.at() < minute - 3;
			long t0 = System.nanoTime();
			try {
				beat(run, s, b, stale);
			} catch (RuntimeException ex) {
				Hearth.LOG.debug("events: beat {} of {} failed", b.action(), run.event.kind().id(), ex);
			}
			COSTS.merge("beat " + b.action() + (b.action().equals("dress") ? " " + b.arg() : ""), System.nanoTime() - t0, Math::max);
		}
	}

	private static void beat(SceneRun run, Settlement s, EventRule.Beat b, boolean stale) {
		ServerLevel level = run.level();
		switch (b.action()) {
			case "walk", "stand", "carry" -> {
				double speed = b.action().equals("walk") ? 0.6 : b.action().equals("carry") ? 0.35 : 0.5;
				for (UUID id : members(run, b.role())) {
					Order o = ORDERS.get(id);
					if (o != null) {
						place(run, o, id, b.to(), speed, b.ticks());
						o.carry = b.action().equals("carry");
						o.dance = b.arg().equals("dance");
					}
				}
			}
			case "face" -> {
				BlockPos f = run.venue(b.to());
				for (UUID id : members(run, b.role())) {
					Order o = ORDERS.get(id);
					if (o != null) {
						o.focus = f;
					}
				}
			}
			case "pose" -> {
				for (UUID id : members(run, b.role())) {
					Order o = ORDERS.get(id);
					if (o != null) {
						o.pose = b.arg();
						o.poseTicks = b.ticks();
						o.poseDone = false;
					}
				}
			}
			case "hold" -> {
				ItemStack it = b.arg().isEmpty() || b.arg().equals("none") ? ItemStack.EMPTY : item(b.arg());
				for (UUID id : members(run, b.role())) {
					Order o = ORDERS.get(id);
					if (o != null) {
						o.hold = it;
						o.holdDone = false;
					}
				}
			}
			case "music" -> {
				boolean on = !b.arg().equals("off");
				for (UUID id : members(run, b.role())) {
					Order o = ORDERS.get(id);
					if (o != null) {
						o.music = on;
					}
				}
			}
			case "bark" -> {
				if (stale) {
					return;
				}
				NpcService npc = HearthServices.npc();
				List<UUID> who = members(run, b.role());
				if (npc == null || who.isEmpty()) {
					return;
				}
				String cat = b.arg().isEmpty() ? run.rule.barks() : b.arg();
				// one of them speaks (the cooldowns keep the street from talking all at once)
				int start = (int) Math.floorMod(TownCalendar.mix(run.event.seed() + b.at()), (long) who.size());
				for (int i = 0; i < who.size(); i++) {
					NpcService.FolkHandle f = npc.folk(who.get((start + i) % who.size()));
					if (f != null && f.awake() && EventBarks.say(f, cat)) {
						break;
					}
				}
			}
			case "sound" -> {
				if (!stale) {
					SoundEvent ev = sound(b.arg());
					BlockPos at = b.to().isEmpty() ? run.anchor : run.venue(b.to());
					if (ev != null && level.isLoaded(at)) {
						level.playSound(null, at, ev, SoundSource.NEUTRAL, b.ticks() > 0 ? b.ticks() / 10F : 1.5F, 1.0F);
					}
				}
			}
			case "toll" -> {
				if (!stale) {
					toll(level, s, Math.max(1, b.ticks()));
				}
			}
			case "spectacle" -> {
				CrowdService crowds = HearthServices.crowds();
				if (crowds != null && !stale) {
					crowds.spectacle(s, run.venue(b.to().isEmpty() ? run.rule.venue() : b.to()), b.arg().isEmpty() ? run.event.kind().id() : b.arg(),
						Math.max(10, b.ticks()), Math.min(24, Math.max(1, run.rule.crowd())));
				}
			}
			case "dress" -> EventDressings.dress(run, s, b.arg(), b.to().isEmpty() ? run.anchor : run.venue(b.to()));
			case "undress" -> EventDressings.undress(run, s, b.arg());
			case "particles" -> {
				if (!stale) {
					EventDressings.particles(level, b.arg(), b.to().isEmpty() ? run.anchor : run.venue(b.to()), Math.max(4, b.ticks()));
				}
			}
			case "end" -> run.put("end_now", Boolean.TRUE);
			default -> run.director.beat(run, s, b);
		}
	}

	/** Sends a cast member to a venue: their own spot round it (spread on a ring, onto standable ground). */
	static void place(SceneRun run, Order o, UUID id, String venue, double speed, int ring) {
		BlockPos at = run.venue(venue);
		int members = run.role(o.role).size();
		int r = ring > 0 ? ring : members <= 2 ? 1 : Math.min(6, 2 + members / 3);
		BlockPos spot = Venues.standFor(run.level(), new Venues.Venue(at, at, List.of(at), r), id, o.role.hashCode());
		o.target = spot;
		o.focus = run.venue(SceneVenues.focusOf(venue));
		o.speed = speed;
		o.moved = false;
		o.nextMove = 0;
	}

	static List<UUID> members(SceneRun run, String role) {
		return role.equals("all") ? run.everyone() : run.role(role);
	}

	/** A slow toll: one stroke every three seconds, a little lower than the hours (docs/LIVING_WORLD_2.md §2.3, §3.1). */
	static void toll(ServerLevel level, Settlement s, int strokes) {
		Bells.Tower t = Bells.tower(s);
		if (t == null || !level.isLoaded(t.pos())) {
			return;
		}
		long now = level.getGameTime();
		for (int i = 0; i < strokes; i++) {
			STROKES.add(new Bells.Stroke(now + i * 60L, level, t.pos(), t.pitch() * 0.79F, 2.0F));
		}
	}

	/** Ends a scene: its director tidies up, its orders and beasts go, and (unless it is being dropped) it resolves. */
	static void end(SceneRun run, boolean resolve) {
		if (run.ended) {
			return;
		}
		run.ended = true;
		List<SceneRun> l = RUNNING.get(run.settlementId);
		if (l != null) {
			l.remove(run);
			if (l.isEmpty()) {
				RUNNING.remove(run.settlementId);
			}
		}
		Settlement s = run.settlement();
		try {
			if (s != null) {
				run.director.end(run, s);
			}
		} catch (RuntimeException ex) {
			Hearth.LOG.warn("events: the {} scene could not end cleanly", run.event.kind().id(), ex);
		}
		for (Iterator<Map.Entry<UUID, Order>> it = ORDERS.entrySet().iterator(); it.hasNext();) {
			if (it.next().getValue().run == run) {
				it.remove();
			}
		}
		for (Entity e : run.spawned) {
			if (!e.isRemoved()) {
				e.discard();
			}
		}
		run.spawned.clear();
		if (s != null) {
			EventDressings.endOf(run, s);
		}
		if (resolve && s != null) {
			EventDirector.resolve(run.world, s, run.event, run.iv);
		}
	}

	/** The town folded: every scene in it resolves with what was done so far. */
	static void fold(Settlement s) {
		for (SceneRun run : List.copyOf(runs(s.id()))) {
			end(run, true);
		}
	}

	/** The server stops: scenes are dropped (they resolve abstractly when the books are next read). */
	static void clear() {
		for (List<SceneRun> l : List.copyOf(RUNNING.values())) {
			for (SceneRun run : List.copyOf(l)) {
				end(run, false);
			}
		}
		RUNNING.clear();
		ORDERS.clear();
		STROKES.clear();
	}

	/** Every scene running anywhere. */
	static List<SceneRun> allRuns() {
		List<SceneRun> out = new ArrayList<>();
		for (List<SceneRun> l : RUNNING.values()) {
			out.addAll(l);
		}
		return out;
	}

	static List<SceneRun> runs(long settlement) {
		List<SceneRun> l = RUNNING.get(settlement);
		return l == null ? List.of() : l;
	}

	public static @Nullable SceneRun run(long settlement, int event) {
		for (SceneRun r : runs(settlement)) {
			if (r.event.id() == event) {
				return r;
			}
		}
		return null;
	}

	/** The scene of a kind running in a town, or null. */
	public static @Nullable SceneRun running(long settlement, EventKind kind) {
		for (SceneRun r : runs(settlement)) {
			if (r.event.kind() == kind) {
				return r;
			}
		}
		return null;
	}

	static @Nullable Order order(UUID resident) {
		return ORDERS.get(resident);
	}

	/** Whether a resident has a part in a scene now (crowds and other behaviours may leave them be). */
	public static boolean inScene(UUID resident) {
		return ORDERS.containsKey(resident);
	}

	// ---------------------------------------------------------------- the api

	@Override
	public boolean stage(Settlement s, Identifier scene, List<UUID> principals, @Nullable ServerPlayer player, CompoundTag args) {
		EventRule rule = EventRules.scene(scene.getPath());
		HearthWorld w = TownLife.world(s);
		if (rule == null || w == null || !TownEvents.config().events().enabled()) {
			return false;
		}
		EventBook book = s.data(EventBook.KEY);
		long now = TownLife.now(w, s);
		long day = SocialTime.day(now);
		CompoundTag a = args.copy();
		a.putString("scene", scene.getPath());
		if (player != null) {
			a.putString("player", player.getUUID().toString());
		}
		EventKind kind = rule.kind();
		Scheduled e = new Scheduled(book.newId(), kind, day, now, now + EventScheduler.ticks(rule.duration()), List.copyOf(principals), List.of(), -1,
			TownCalendar.mix(s.id() ^ now ^ scene.hashCode()), a, Scheduled.Phase.SCHEDULED);
		if (!canStage(s, e) || !book.add(e)) {
			return false;
		}
		s.markDirty();
		SceneRun run = begin(w, s, e);
		if (run == null) {
			book.remove(e.id());
			return false;
		}
		book.replace(e.withPhase(Scheduled.Phase.BEGUN));
		run.event = e.withPhase(Scheduled.Phase.BEGUN);
		EventDirector.watch(w, s);
		SettlementEvents.TOWN_EVENT.invoker().onTownEvent(w, s, kind == EventKind.SCENE ? "scene:" + scene.getPath() : kind.id(),
			SettlementEvents.TownEvent.Phase.BEGUN, "");
		return true;
	}

	// ---------------------------------------------------------------- small helpers

	static ItemStack item(String id) {
		try {
			return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	static @Nullable SoundEvent sound(String id) {
		try {
			return BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(id)).orElse(null);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** What the runner is doing (command, tours). */
	static List<String> stats() {
		List<String> out = new ArrayList<>();
		int n = 0;
		for (List<SceneRun> l : RUNNING.values()) {
			n += l.size();
		}
		out.add(String.format(Locale.ROOT, "scenes running: %d, cast with orders: %d, scene passes %.3f ms avg, %d not staged for the server's load", n,
			ORDERS.size(), TIMER.averageMs(), refusedForLoad));
		if (!COSTS.isEmpty()) {
			StringBuilder b = new StringBuilder("worst (ms):");
			COSTS.forEach((k, v) -> b.append(' ').append(k).append(' ').append(String.format(Locale.ROOT, "%.2f", v / 1e6)).append(';'));
			out.add(b.toString());
		}
		for (List<SceneRun> l : RUNNING.values()) {
			for (SceneRun r : l) {
				Settlement s = r.settlement();
				int cast = 0;
				int about = 0;
				NpcService npc = HearthServices.npc();
				for (UUID id : r.everyone()) {
					cast++;
					if (npc != null && npc.folk(id) != null) {
						about++;
					}
				}
				out.add(String.format(Locale.ROOT, " %s: %s #%d minute %d, cast %d (%d about), %s", s == null ? "?" : s.name(), r.event.kind().id(), r.event.id(),
					r.minute, cast, about, r.iv));
			}
		}
		return out;
	}
}
