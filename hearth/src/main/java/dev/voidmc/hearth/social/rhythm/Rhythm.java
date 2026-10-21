package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.office.Office;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jspecify.annotations.Nullable;

/**
 * A visible daily rhythm: services, wash day, play, tavern nights, watch rounds and lights, the calendar (§2).
 * Lane A2 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 *
 * <p>Abstract: the calendar ({@link RhythmCalendar}, published as {@code HearthServices.calendar()}), the rest day's
 * offering once a rest day ({@link RhythmDirector#dayPassed}); nothing per tick. Embodied: the {@link RhythmDirector}
 * every {@code pass_ticks} per embodied town (bells, laundry, chimney smoke, winter, wash-place scan, lights' upkeep) and
 * the folk behaviours (service 85, watch 70, tavern hands 55, wash 55, tavern night 45, play 25). Schedules take the
 * week through {@link RhythmSchedule}.
 */
public final class Rhythm {
	private static RhythmConfig config = RhythmConfig.DEFAULTS;
	private static boolean behavioursRegistered;
	private static boolean scheduleRegistered;
	private static volatile @Nullable MinecraftServer server;

	private Rhythm() {
	}

	public static RhythmConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(RhythmConfig newConfig) {
		config = newConfig;
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	/** Called once from {@code SocialModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("rhythm", RhythmConfig.CODEC, RhythmConfig.DEFAULTS);
		RhythmBook.touch();
		HearthServices.setCalendar(RhythmCalendar.INSTANCE);
		DialogueProvider.register(new RhythmDialogue());

		ResourceLoader loader = ResourceLoader.get(PackType.SERVER_DATA);
		loader.registerReloadListener(Hearth.id("rhythm/data"), new SimpleReloadListener<RhythmData.Loaded>() {
			@Override
			protected RhythmData.Loaded prepare(PreparableReloadListener.SharedState state) {
				return RhythmData.load(state.resourceManager());
			}

			@Override
			protected void apply(RhythmData.Loaded data, PreparableReloadListener.SharedState state) {
				RhythmData.install(data);
			}
		});
		loader.registerReloadListener(Hearth.id("rhythm/barks"), new SimpleReloadListener<Map<String, List<RhythmBarks.Line>>>() {
			@Override
			protected Map<String, List<RhythmBarks.Line>> prepare(PreparableReloadListener.SharedState state) {
				return RhythmBarks.load(state.resourceManager());
			}

			@Override
			protected void apply(Map<String, List<RhythmBarks.Line>> data, PreparableReloadListener.SharedState state) {
				RhythmBarks.install(data);
			}
		});

		registerBehaviours();
		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			server = s;
			registerBehaviours();
			registerSchedule();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			clearCaches();
		});
		RhythmDirector.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> RhythmCommands.register(dispatcher));
	}

	private static synchronized void registerBehaviours() {
		NpcService npc = HearthServices.npc();
		if (behavioursRegistered || npc == null) {
			return;
		}
		npc.registerBehaviour(new ServiceBehaviour());
		npc.registerBehaviour(new WatchRounds());
		npc.registerBehaviour(new TavernNight.Hands());
		npc.registerBehaviour(new WashBehaviour());
		npc.registerBehaviour(new TavernNight());
		npc.registerBehaviour(new PlayBehaviour());
		behavioursRegistered = true;
	}

	/**
	 * The week's windows go in after every other module's (registered as the server starts), so a named character's own
	 * plans (lane B2) win where they overlap the rest day.
	 */
	private static synchronized void registerSchedule() {
		if (!scheduleRegistered) {
			ScheduleOverrides.register(RhythmSchedule.INSTANCE);
			scheduleRegistered = true;
		}
	}

	static void clearCaches() {
		RhythmSchedule.clear();
		Service.clear();
		ServiceBehaviour.clearPews();
		WashBehaviour.clear();
		WashPlaces.clear();
		WatchLights.clear();
		TavernNight.clear();
		RhythmBarks.clear();
		RhythmCalendar.clearForced();
		RhythmDirector.clear();
	}

	/** Holds a rest or wash day (or neither; null: the calendar again) in a town for today (commands, tours). */
	public static void holdDay(Settlement s, RhythmCalendar.@Nullable Forced kind) {
		RhythmCommands.hold(s, kind);
	}

	/** Today's washers and tonight's drinkers of a town (tours, the dev harness). */
	public static java.util.Set<UUID> washers(Settlement s, long day) {
		return WashBehaviour.washers(s, day).keySet();
	}

	public static java.util.Set<UUID> drinkers(Settlement s, long day) {
		return RhythmSchedule.drinkers(s, day);
	}

	/** Taverns of a town where a drinker took up the instrument tonight (tours). */
	public static int musicians(Settlement s, long day) {
		int n = 0;
		for (var e : TavernNight.PLAYING.entrySet()) {
			if (Math.floorDiv(e.getKey(), 4096L) == s.id() && (long) e.getValue()[0] == day) {
				n++;
			}
		}
		return n;
	}

	/** Tonight's drinkers of a town by the tavern each goes to (plot id), the fullest first (tours). */
	public static List<java.util.Set<UUID>> drinkersByTavern(Settlement s, long day) {
		List<dev.voidmc.hearth.api.PlotView> taverns = RhythmSchedule.taverns(s);
		java.util.Map<Integer, java.util.Set<UUID>> by = new java.util.HashMap<>();
		if (!taverns.isEmpty()) {
			for (Resident r : TavernNight.order(s, day)) {
				by.computeIfAbsent(TavernNight.nearest(s, taverns, r).id(), k -> new java.util.LinkedHashSet<>()).add(r.id);
			}
		}
		List<java.util.Set<UUID>> out = new java.util.ArrayList<>(by.values());
		out.sort((a, b) -> b.size() - a.size());
		return out;
	}

	/** The town's minute and the part of the service at it (tours). */
	public static String serviceNow(Settlement s) {
		int m = RhythmFolk.minute(s);
		return String.format(java.util.Locale.ROOT, "%02d:%02d %s", m / 60, m % 60, Service.part(RhythmData.of(s.culture()).service(), m));
	}

	/** Who leads today's service now: the best of the town's leaders who is embodied (tours). */
	public static @Nullable UUID leader(Settlement s) {
		return Service.leader(s, Service.day(s, RhythmFolk.today(s)));
	}

	/** Who leads the rest day's service in a town (its priest, a monk, an elder), or null (tours). */
	public static @Nullable UUID officiant(Settlement s) {
		Resident r = Service.officiant(s);
		return r == null ? null : r.id;
	}

	/** What the rhythm is doing in embodied towns, and its timings (command, tours). */
	public static List<String> stats() {
		return RhythmDirector.stats();
	}

	// ---- small lookups shared by the behaviours

	/** The town's lord's given name (for the toasts), or "the lord". */
	static String lordName(Settlement s) {
		UUID lord = s.data(SocialModule.LEDGER).offices.get(Office.LORD.id());
		if (lord != null) {
			for (Resident r : s.residents()) {
				if (r.id.equals(lord)) {
					return r.givenName;
				}
			}
		}
		return "the lord";
	}

	/** The town's entertainer who plays in this tavern of a night, or null. */
	static @Nullable Resident musician(Settlement s, PlotView tavern) {
		for (Resident r : s.residents()) {
			if (r.job == JobType.ENTERTAINER && tavern.equals(TavernNight.Hands.tavernOf(s, r))) {
				return r;
			}
		}
		return null;
	}

	/** Counts a player's attendance with the priest; returns how often they have come. */
	static int attended(Settlement s, Resident priest, ServerPlayer player) {
		CompoundTag regulars = priest.extra("social").getCompoundOrEmpty("regulars");
		String k = player.getUUID().toString();
		int n = regulars.getIntOr(k, 0) + 1;
		if (regulars.size() >= 32 && !regulars.contains(k)) {
			return n; // a small book: the priest remembers the regulars, not every passer-by
		}
		regulars.putInt(k, n);
		priest.extra("social").put("regulars", regulars);
		s.markDirty();
		return n;
	}

	/** Whether the priest counts this player among the regulars (two services or more). */
	static boolean regular(Settlement s, Resident priest, ServerPlayer player) {
		return priest.extra("social").getCompoundOrEmpty("regulars").getIntOr(player.getUUID().toString(), 0) >= 2;
	}
}
