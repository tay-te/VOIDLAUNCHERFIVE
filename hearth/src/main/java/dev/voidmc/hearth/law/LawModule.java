package dev.voidmc.hearth.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.law.command.LawCommands;
import dev.voidmc.hearth.law.crime.CrimeHooks;
import dev.voidmc.hearth.law.crime.LawDays;
import dev.voidmc.hearth.law.crime.LawStatusSender;
import dev.voidmc.hearth.law.crime.Presence;
import dev.voidmc.hearth.law.crime.TheftWatch;
import dev.voidmc.hearth.law.crime.TrespassWatch;
import dev.voidmc.hearth.law.folk.GuardBehaviour;
import dev.voidmc.hearth.law.folk.GuardDispatch;
import dev.voidmc.hearth.law.folk.WitnessBehaviour;
import dev.voidmc.hearth.law.item.LawComponents;
import dev.voidmc.hearth.law.item.LawItems;
import dev.voidmc.hearth.law.item.StolenGoods;
import dev.voidmc.hearth.law.justice.Confrontations;
import dev.voidmc.hearth.law.justice.Jail;
import dev.voidmc.hearth.law.justice.PlayerLaw;
import dev.voidmc.hearth.law.lock.LockpickSessions;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.law.own.LookHints;
import dev.voidmc.hearth.law.own.Ownership;
import dev.voidmc.hearth.law.own.PlotIndex;
import dev.voidmc.hearth.law.pocket.PickpocketSessions;
import dev.voidmc.hearth.law.stealth.StealthTracker;
import dev.voidmc.hearth.law.stealth.Visibility;
import dev.voidmc.hearth.law.talk.LawDialogue;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The law module (docs/LAW.md, docs/LIVING_WORLD.md §4): ownership and the red hand, crimes and their witnesses, guards
 * and justice (fines, the cell, the pillory, bounties that follow you, outlaws), stolen goods and fences, locks and keys,
 * and the lockpicking and pickpocketing minigames. Registers the {@link dev.voidmc.hearth.api.LawService}.
 */
public final class LawModule {
	private static final GuardBehaviour GUARDS = new GuardBehaviour();
	private static final WitnessBehaviour WITNESSES = new WitnessBehaviour();
	private static boolean behavioursRegistered;

	private LawModule() {
	}

	public static void init() {
		reloadConfig();
		LawComponents.init();
		LawItems.init();
		Ownership.init();
		PlayerLaw.init();
		Hearth.LOG.debug("law data key {}", Law.STATE.id());
		LawPayloads.register();
		registerReceivers();
		CrimeHooks.register();
		DialogueProvider.register(new LawDialogue());
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> LawCommands.register(dispatcher));
		registerBehaviours();
		registerEvents();
		HearthServices.setLaw(new LawServiceImpl());
		dev.voidmc.hearth.law.dev.LawDevHarness.init();
	}

	/** Reads {@code config/hearth/law.json} again. */
	public static void reloadConfig() {
		Law.setConfig(HearthConfig.load("law", LawConfig.CODEC, LawConfig.DEFAULTS));
	}

	/** Guards and witnesses join every folk's repertoire once the npc module is there (now, or when the server starts). */
	private static void registerBehaviours() {
		NpcService npc = HearthServices.npc();
		if (npc != null) {
			addBehaviours(npc);
		}
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			NpcService late = HearthServices.npc();
			if (late != null) {
				addBehaviours(late);
			}
		});
	}

	private static synchronized void addBehaviours(NpcService npc) {
		if (behavioursRegistered) {
			return;
		}
		behavioursRegistered = true;
		npc.registerBehaviour(GUARDS);
		npc.registerBehaviour(WITNESSES);
	}

	private static void registerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(LawPayloads.LockpickEvent.TYPE,
			(payload, ctx) -> ctx.server().execute(() -> LockpickSessions.event(ctx.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(LawPayloads.PocketAction.TYPE,
			(payload, ctx) -> ctx.server().execute(() -> PickpocketSessions.action(ctx.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(LawPayloads.ConfrontChoice.TYPE,
			(payload, ctx) -> ctx.server().execute(() -> Confrontations.choose(ctx.player(), payload.entity(), payload.choice())));
	}

	private static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(LawServiceImpl::setServer);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TheftWatch.clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LawServiceImpl.setServer(null);
			clearAll();
		});
		ServerTickEvents.END_SERVER_TICK.register(LawModule::tick);
		SettlementEvents.DAY_PASSED.register(LawDays::dayPassed);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Jail.returnToPlace(newPlayer));
		ServerPlayerEvents.JOIN.register(player -> {
			LawStatusSender.forget(player.getUUID());
			LawStatusSender.refresh(player);
		});
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		TheftWatch.tick(server);
		PickpocketSessions.tick(server);
		LockpickSessions.tick(server);
		CrimeHooks.tick(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Jail.hold(player);
		}
		if (t % 4 == 0) {
			LookHints.tick(server);
		}
		if (t % 5 == 0) {
			StealthTracker.tick(server);
		}
		if (t % 10 == 0) {
			Presence.update(server);
		}
		if (t % 20 == 0) {
			second(server, t);
		}
	}

	/** Once a second: sentences, trespass, owned marks gone astray, report deadlines in towns with players. */
	private static void second(MinecraftServer server, int t) {
		Confrontations.tick(server);
		GuardDispatch.tick(server);
		Set<Long> towns = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Jail.tick(player, 20);
			Settlement s = Presence.of(player);
			if (s == null) {
				continue;
			}
			towns.add(s.id());
			TrespassWatch.check(player);
			TheftWatch.takenNow(player, s.id());
			if (t % 200 == 0) {
				StolenGoods.fade(player.getInventory(), Law.day(player.level()), Law.config().stealth().stolenFadeDays());
			}
		}
		HearthWorld world = HearthWorld.of(server.overworld());
		if (world != null) {
			for (long id : towns) {
				Settlement s = world.settlement(id);
				if (s != null) {
					LawDays.deadlines(world.level(), s);
				}
			}
		}
		CrimeHooks.sweep(server.overworld().getGameTime());
	}

	private static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		TheftWatch.forget(id);
		Presence.forget(id);
		StealthTracker.forget(id);
		LookHints.forget(id);
		LawStatusSender.forget(id);
		TrespassWatch.forget(id);
		LockpickSessions.forget(id);
		PickpocketSessions.forget(id);
		Confrontations.forget(id);
		GuardDispatch.forget(id);
		Visibility.forget(id);
	}

	private static void clearAll() {
		TheftWatch.clear();
		Presence.clear();
		StealthTracker.clear();
		LookHints.clear();
		LawStatusSender.clear();
		TrespassWatch.clear();
		LockpickSessions.clear();
		PickpocketSessions.clear();
		Confrontations.clear();
		GuardDispatch.clear();
		Visibility.clear();
		CrimeHooks.clear();
		PlotIndex.clear();
		GUARDS.clear();
		WITNESSES.clear();
	}
}
