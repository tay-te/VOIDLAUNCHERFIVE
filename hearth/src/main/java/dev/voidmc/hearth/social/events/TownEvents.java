package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.repair.fire.FireLeash;
import dev.voidmc.hearth.social.life.TownLife;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * Town events you stumble into: the event book, scenes, tax, raids, fairs, trials (docs/LIVING_WORLD_2.md §3). Lane B1
 * owns this package.
 *
 * <p>Abstract, for every town whether anyone is near: when its day turns ({@code DAY_PASSED}) the {@link EventDirector}
 * plans the day from the town's state ({@link EventScheduler}: at most one major event and three minor), each with an
 * outcome seed; an event nobody sees is decided by its {@link Outcomes} rule when it ends and applied through the
 * existing calls ({@link EventEffects}). Embodied: when its time comes in a town a player stands in, the {@link Scenes}
 * runner stages it — roles for the residents (priority 230), the crowd through the crowds' spectacle (lane A3), the
 * bailiff's and the raiders' parties from the roads (lane A1), the dressings through the ledger — and resolves it by the
 * same rule, with what players did. Live fires are held on the {@link FireLeash}.
 */
public final class TownEvents {
	private static EventsConfig config = EventsConfig.DEFAULTS;
	private static boolean behavioursRegistered;
	private static boolean calendarWrapped;

	private TownEvents() {
	}

	public static EventsConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(EventsConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code SocialModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("events", EventsConfig.CODEC, EventsConfig.DEFAULTS);
		EventBook.touch();
		HearthServices.setScenes(Scenes.SERVICE);
		DialogueProvider.register(new EventDialogue());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Hearth.id("events/rules"), new SimpleReloadListener<EventRules.Loaded>() {
			@Override
			protected EventRules.Loaded prepare(PreparableReloadListener.SharedState state) {
				return EventRules.load(state.resourceManager());
			}

			@Override
			protected void apply(EventRules.Loaded data, PreparableReloadListener.SharedState state) {
				EventRules.install(data);
			}
		});
		wrapCalendar();
		registerBehaviours();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			registerBehaviours();
			wrapCalendar();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(EventDirector::reconcile);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Scenes.clear();
			FireLeash.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			EventDirector.clear();
			FactsReader.clear();
			Arrivals.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(TownEvents::tick);

		SettlementEvents.DAY_PASSED.register(EventDirector::dayPassed);
		SettlementEvents.BUILDING_COMPLETED.register(EventDirector::buildingCompleted);
		SettlementEvents.PLOT_ABANDONED.register((w, s, p, why) -> FactsReader.forget(s.id()));
		SettlementEvents.RESIDENT_ADDED.register((w, s, r) -> {
			long today = TownLife.today(w, s);
			if (config.events().enabled() && r.age == AgeStage.CHILD && r.bornDay >= today - 1 && today - s.foundedDay() > 0) {
				EventDirector.born(w, s, r);
			}
		});
		SettlementEvents.TRAVELLER.register(TaxRounds::traveller);
		SettlementEvents.FIRE_OUT.register((w, s, burned, helpers) -> FireScene.fireOut(s, helpers));
		SettlementEvents.EMBODIMENT_CHANGED.register((w, s, embodied) -> {
			if (embodied) {
				EventDirector.watch(w, s);
				applyShells(w, s);
			} else {
				Scenes.fold(s);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) {
				FairScene.arrowLoaded(entity);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(TourneyScene::allowDamage);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> EventCommands.register(dispatcher));
	}

	/** The calendar's events include the book's (the rhythm's calendar registers first: it is wrapped, not replaced). */
	private static synchronized void wrapCalendar() {
		if (calendarWrapped || HearthServices.calendar() instanceof EventsCalendar) {
			return;
		}
		if (HearthServices.calendar() == dev.voidmc.hearth.api.SettlementCalendar.NONE && !calendarWrapped) {
			// the rhythm not up yet: try again as the server starts
			return;
		}
		HearthServices.setCalendar(new EventsCalendar(HearthServices.calendar()));
		calendarWrapped = true;
	}

	private static synchronized void registerBehaviours() {
		NpcService npc = HearthServices.npc();
		if (behavioursRegistered || npc == null) {
			return;
		}
		npc.registerBehaviour(new SceneRoleBehaviour());
		npc.registerBehaviour(new RaidDefence());
		behavioursRegistered = true;
	}

	private static void tick(MinecraftServer server) {
		if (!config.events().enabled()) {
			return;
		}
		try {
			Scenes.tick(server);
			EventDirector.tick(server);
			FireLeash.tick(server);
			Arrivals.tick(server);
			if (server.overworld().getGameTime() % 1200 == 31) {
				for (var level : server.getAllLevels()) {
					HearthWorld w = HearthWorld.of(level);
					if (w == null) {
						continue;
					}
					for (Settlement s : w.settlements()) {
						if (s.embodied() && !s.data(EventBook.KEY).burnt.isEmpty()) {
							applyShells(w, s);
						}
					}
				}
			}
		} catch (RuntimeException e) {
			Hearth.LOG.warn("events: a tick failed", e);
		}
	}

	// ---- for tours and the dev tools

	/** Holds an event of a kind in a town now ({@code /hearth events force}); returns its key, or null if it cannot be. */
	public static @org.jspecify.annotations.Nullable String force(Settlement s, String kind) {
		EventKind k = EventKind.byId(kind);
		Scheduled e = k == null ? null : EventCommands.force(s, k);
		return e == null ? null : e.key();
	}

	/** Where the scene of a kind running in a town is held, or null when none runs. */
	public static net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos venue(Settlement s, String kind, String venue) {
		EventKind k = EventKind.byId(kind);
		SceneRun run = k == null ? null : Scenes.running(s.id(), k);
		return run == null ? null : venue.isEmpty() ? run.anchor() : run.venue(venue);
	}

	/** The cast of a running scene who are about as folk, by role (tours). */
	public static java.util.Map<String, Integer> castAbout(Settlement s, String kind) {
		java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
		EventKind k = EventKind.byId(kind);
		SceneRun run = k == null ? null : Scenes.running(s.id(), k);
		if (run == null) {
			return out;
		}
		for (var e : run.cast.entrySet()) {
			int n = 0;
			for (java.util.UUID id : e.getValue()) {
				if (EventDirector.folk(id) != null) {
					n++;
				}
			}
			out.put(e.getKey(), n);
		}
		return out;
	}

	/** A running scene's own state by name (the fair's {@code stalls}, {@code targets}; tours), or null. */
	public static @org.jspecify.annotations.Nullable Object sceneState(Settlement s, String kind, String key) {
		EventKind k = EventKind.byId(kind);
		SceneRun run = k == null ? null : Scenes.running(s.id(), k);
		return run == null ? null : run.state.get(key);
	}

	/** Whether a scene of a kind runs in a town now. */
	public static boolean running(Settlement s, String kind) {
		EventKind k = EventKind.byId(kind);
		return k != null && Scenes.running(s.id(), k) != null;
	}

	/** The town's book in words: its events and the last outcomes (tours). */
	public static java.util.List<String> book(Settlement s) {
		java.util.List<String> out = new java.util.ArrayList<>();
		EventBook b = s.data(EventBook.KEY);
		for (Scheduled e : b.scheduled) {
			out.add(e.key() + " day " + e.day() + " " + e.phase().getSerializedName());
		}
		for (Outcome o : b.recent) {
			out.add("> " + o.kind().id() + ": " + o.result() + (o.sentence().isEmpty() ? "" : " (" + o.sentence() + ")") + (o.plot() >= 0 ? " plot " + o.plot()
				: "") + (o.goodsLost() != 0 ? " goods " + o.goodsLost() : "") + (o.coin() != 0 ? " coin " + o.coin() : ""));
		}
		for (EventBook.Burnt br : b.burnt) {
			out.add("burnt plot " + br.plot() + " until day " + br.until() + (br.applied() ? " (shell laid: " + br.blocks() + " blocks)" : ""));
		}
		return out;
	}

	/** What the events are doing and what they cost (command, tours). */
	public static java.util.List<String> stats() {
		java.util.List<String> lines = new java.util.ArrayList<>(EventDirector.stats());
		lines.addAll(Scenes.stats());
		lines.add(FireLeash.stats());
		return lines;
	}

	/**
	 * The abstract cost of a day in the world's towns as it stands (tours, {@code /hearth events bench}): every town's facts
	 * read, its crime rolled and its day planned, {@code days} days ahead, nothing applied, after a warm-up of the same work
	 * (a cold pass measures the JIT). Returns µs per town-day.
	 */
	public static double bench(HearthWorld w, int days) {
		java.util.List<Settlement> towns = new java.util.ArrayList<>();
		for (Settlement s : w.settlements()) {
			if (s.population() > 0) {
				towns.add(s);
			}
		}
		if (towns.isEmpty()) {
			return 0;
		}
		long today = w.day();
		// warmed first (as a server that has planned a few hundred town-days is): a cold pass measures the JIT, not the work
		int warm = Math.max(1, Math.min(400, 8000 / towns.size()));
		for (int d = 1; d <= warm; d++) {
			for (Settlement s : towns) {
				TownFacts f = FactsReader.read(w, s, today + days + d);
				dev.voidmc.hearth.law.justice.Trials.roll(s.id(), today + days + d, s.residents(), f.prosperity(), 0, 0, java.util.Set.of(), java.util.Set.of(),
					java.util.List.of());
				int[] ids = {1};
				EventScheduler.plan(f, today + days + d, EventRules.all(), java.util.List.of(), Long.MIN_VALUE, Long.MIN_VALUE, EventScheduler.Filler.NONE,
					() -> ids[0]++);
			}
		}
		long t0 = System.nanoTime();
		long facts = 0;
		long seat0 = FactsReader.SEAT_NANOS;
		long threat0 = FactsReader.THREAT_NANOS;
		int n = 0;
		for (int d = 1; d <= days; d++) {
			for (Settlement s : towns) {
				long f0 = System.nanoTime();
				TownFacts f = FactsReader.read(w, s, today + d);
				facts += System.nanoTime() - f0;
				dev.voidmc.hearth.law.justice.Trials.roll(s.id(), today + d, s.residents(), f.prosperity(), 0, 0, java.util.Set.of(), java.util.Set.of(),
					java.util.List.of());
				int[] ids = {1};
				EventScheduler.plan(f, today + d, EventRules.all(), java.util.List.of(), Long.MIN_VALUE, Long.MIN_VALUE, EventScheduler.Filler.NONE,
					() -> ids[0]++);
				n++;
			}
		}
		double us = (System.nanoTime() - t0) / 1e3 / n;
		benchLine = String.format(java.util.Locale.ROOT, "%.1f µs a town-day over %d towns × %d days: facts %.1f (domains %.1f, bands %.1f), the rest %.1f",
			us, towns.size(), days, facts / 1e3 / n, (FactsReader.SEAT_NANOS - seat0) / 1e3 / n, (FactsReader.THREAT_NANOS - threat0) / 1e3 / n,
			us - facts / 1e3 / n);
		return us;
	}

	/** The last bench's breakdown. */
	public static String benchLine = "";

	/** Every scene in a town ends now and resolves (tours: to see the outcome without waiting out the hours). */
	public static void endScenes(Settlement s) {
		Scenes.fold(s);
	}

	/** Whether scripted fires burn for real here: "auto" is on in single player and off on servers (§13). */
	public static boolean liveFires(MinecraftServer server) {
		return switch (config.events().liveFires()) {
			case "on" -> true;
			case "off" -> false;
			default -> server.isSingleplayer();
		};
	}

	/** Lays the shells of a town's burnt plots that are not yet shown (and not rebuilt); returns the blocks changed. */
	public static int applyShells(HearthWorld w, Settlement s) {
		EventBook book = s.data(EventBook.KEY);
		long today = TownLife.today(w, s);
		int n = 0;
		for (int i = 0; i < book.burnt.size(); i++) {
			EventBook.Burnt b = book.burnt.get(i);
			if (b.applied() || b.until() < today) {
				continue;
			}
			PlotView p = s.plot(b.plot());
			if (p == null) {
				continue;
			}
			double share = 0.5;
			for (Outcome o : book.recent) {
				if (o.plot() == b.plot()) {
					share = o.burned();
				}
			}
			int changed = BurntShell.apply(w, s, p, share, dev.voidmc.hearth.social.calendar.TownCalendar.mix(s.id() * 31 + b.plot()));
			if (changed > 0) {
				book.burnt.set(i, b.asApplied(changed));
				s.markDirty();
				n += changed;
			}
		}
		return n;
	}
}
