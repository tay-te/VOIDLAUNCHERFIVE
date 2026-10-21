package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimModule;
import dev.voidmc.hearth.sim.settlement.ResidentClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jspecify.annotations.Nullable;

/**
 * People with lives: relations, apprentices and masters, named characters, threads (docs/LIVING_WORLD_2.md §4).
 * Lane B2 (§11.3) owns this package; the embodied side (behaviours, talk, the People list) is {@code social/lives}.
 *
 * <p>Abstract only: {@link LivesDay} on each settlement's {@code DAY_PASSED} (≤ 30 µs for a town of 150 people, measured
 * by {@code LivesBench} and the {@code lives} timer of {@code /hearth sim stats}); nothing per tick. Saved per resident in
 * {@code extra("lives")} ({@link Life}) and per town under {@code hearth:lives/town} ({@link TownLives}).
 */
public final class Lives {
	public static final HearthDataKey<TownLives> TOWN = HearthDataKey.register(Hearth.id("lives/town"), HearthDataKey.Scope.SETTLEMENT, TownLives.CODEC,
		TownLives::new);

	private static LivesConfig config = LivesConfig.DEFAULTS;
	private static volatile BooleanSupplier sameSex = () -> true;
	private static volatile @Nullable MinecraftServer server;
	private static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("lives");
	private static final Bonds.Rules RULES = () -> sameSex.getAsBoolean();

	private Lives() {
	}

	public static LivesConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(LivesConfig newConfig) {
		config = newConfig;
	}

	/** Whether two men or two women may court (the social module hands in its {@code same_sex} setting). */
	public static void setSameSex(BooleanSupplier allowed) {
		sameSex = allowed;
	}

	/** Called once from {@code SimModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("lives", LivesConfig.CODEC, LivesConfig.DEFAULTS);
		HearthServices.setLives(LivesServiceImpl.INSTANCE);
		ResidentClaims.register(LivesClaims.INSTANCE);
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Hearth.id("lives/book"), new SimpleReloadListener<LivesBook>() {
			@Override
			protected LivesBook prepare(PreparableReloadListener.SharedState state) {
				return LivesBook.load(state.resourceManager());
			}

			@Override
			protected void apply(LivesBook book, PreparableReloadListener.SharedState state) {
				LivesBook.install(book);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		SettlementEvents.DAY_PASSED.register(Lives::dayPassed);
		SettlementEvents.RESIDENT_ADDED.register((w, s, r) -> {
			// a grown newcomer (settlers, a stranger moving in): not born here
			if (config.lives().enabled() && r.age == AgeStage.ADULT && Kin.parents(r).isEmpty() && w.day() > s.foundedDay() + 1) {
				Life.arrived(r, w.day());
			}
		});
		SettlementEvents.CRIME_COMMITTED.register(Lives::crime);
	}

	/** The sim's childhood, in days (children are grown after it). */
	public static int childhoodDays() {
		try {
			return SimModule.rules().config.population().childhoodDays();
		} catch (RuntimeException e) {
			return 48;
		}
	}

	private static void dayPassed(HearthWorld w, Settlement s, long day) {
		if (!config.lives().enabled()) {
			return;
		}
		long t0 = System.nanoTime();
		List<LivesDay.Happening> out = LivesDay.day(s, day, config, LivesBook.get(), childhoodDays(), RULES);
		TIMER.add(System.nanoTime() - t0);
		LivesStats.day(s.population(), System.nanoTime() - t0);
		fire(w, s, out);
	}

	/** Runs one settlement's day without a world (the harness, tests): what happened, not fired. */
	public static List<LivesDay.Happening> step(Settlement s, long day) {
		return LivesDay.day(s, day, config, LivesBook.get(), childhoodDays(), RULES);
	}

	static void fire(@Nullable HearthWorld w, Settlement s, List<LivesDay.Happening> out) {
		if (w == null) {
			return;
		}
		for (LivesDay.Happening h : out) {
			SettlementEvents.LIFE_EVENT.invoker().onLifeEvent(w, s, h.resident(), h.kind(), h.player());
		}
	}

	static @Nullable HearthWorld world(Settlement s) {
		MinecraftServer srv = server;
		if (srv == null) {
			return null;
		}
		ServerLevel level = srv.getLevel(s.dimension());
		return level == null ? null : HearthServices.world(level);
	}

	/** Fires each happening at once (choices made in conversation, events other modules report). */
	static BiConsumer<Resident, String> events(Settlement s) {
		return (r, kind) -> fire(world(s), s, List.of(new LivesDay.Happening(r.id, kind, null)));
	}

	/** A resident left the town; {@code died} by death (the social module, which knows how they went, says). */
	public static void gone(Settlement s, Resident r, boolean died, long day) {
		fire(world(s), s, LivesDay.gone(s, r, died, day, config));
	}

	/**
	 * Something another module reports in a resident's life ({@code LivesService.lifeEvent}): {@code brawl} heats their
	 * house's feuds, {@code helped} and {@code wronged} mark a named character's thread with the player, the rest pass on.
	 */
	static void lifeEvent(Settlement s, UUID resident, String kind, @Nullable UUID player) {
		TownLives town = s.data(TOWN);
		Resident r = s.resident(resident);
		if (r != null) {
			switch (kind) {
				case "brawl" -> {
					Resident head = Feuds.headOf(s, r);
					for (TownLives.Feud f : town.feuds) {
						if (f.involves(head.id)) {
							f.heat = Math.min(100, f.heat + 20);
						}
					}
				}
				case "helped", "wronged", "shamed" -> {
					TownLives.Notable n = town.notable(resident);
					if (n != null) {
						n.flags.add(kind);
					}
				}
				default -> {
				}
			}
			s.markDirty();
		}
		HearthWorld w = world(s);
		if (w != null) {
			SettlementEvents.LIFE_EVENT.invoker().onLifeEvent(w, s, resident, kind, player);
		}
	}

	/** An unwitnessed theft from a resident may be laid at a neighbouring house's door (§4.1). */
	private static void crime(HearthWorld w, Settlement s, LawService.Crime crime) {
		if (!config.lives().enabled() || crime.victim() == null || crime.witnessed()) {
			return;
		}
		if (crime.offence() != LawService.Offence.THEFT && crime.offence() != LawService.Offence.LOCKPICKING && crime.offence() != LawService.Offence.PICKPOCKETING) {
			return;
		}
		Resident victim = s.resident(crime.victim());
		if (victim == null || victim.home == Resident.NO_PLOT) {
			return;
		}
		TownLives town = s.data(TOWN);
		long day = Math.max(w.day(), town.lastDay);
		SplittableRandom random = new SplittableRandom(LivesDay.seed(s.id(), day) ^ crime.gameTime());
		int nh = Workplaces.of(s).neighbour(victim.home);
		Resident suspect = null;
		if (nh != Resident.NO_PLOT) {
			List<Resident> there = new ArrayList<>();
			for (Resident o : s.residents()) {
				if (o.home == nh && o.age != AgeStage.CHILD) {
					there.add(o);
				}
			}
			there.sort((a, b) -> a.id.compareTo(b.id));
			suspect = there.isEmpty() ? null : there.get(random.nextInt(there.size()));
		}
		Resident offender = s.resident(crime.offender());
		if (offender != null) {
			suspect = offender; // a resident caught at it: no blame needed
		}
		if (suspect == null) {
			return;
		}
		List<LivesDay.Happening> out = new ArrayList<>();
		Feuds.maybe(s, town, victim, suspect, "theft", day, config, random, (r, kind) -> out.add(new LivesDay.Happening(r.id, kind, null)));
		s.markDirty();
		fire(w, s, out);
	}
}
