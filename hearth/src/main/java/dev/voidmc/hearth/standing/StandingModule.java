package dev.voidmc.hearth.standing;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.standing.appearance.Appearances;
import dev.voidmc.hearth.standing.command.StandingCommands;
import dev.voidmc.hearth.standing.model.StandingKeys;
import dev.voidmc.hearth.standing.net.StandingNetwork;
import dev.voidmc.hearth.standing.rules.StandingData;
import dev.voidmc.hearth.standing.speech.StandingDialogue;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;

/**
 * The standing module: reputation in three layers (personal, settlement, regional), deeds and gossip, titles, charisma
 * and menace, speech checks, and companies. See docs/STANDING.md and docs/LIVING_WORLD.md §3 and §5.
 *
 * <p>Wiring: the {@link StandingEngine} is registered as the {@code StandingService}; deed tables, gossip lines and
 * local dress load from data packs ({@link StandingData}); settlement days drift standing, adoption releases waiting
 * news; a staggered per-player check sends the standing line on arrival, notices washing and title changes, and
 * delivers gossip; the conversation gets the standing module's options ({@link StandingDialogue}).
 */
public final class StandingModule {
	private static StandingConfig config = StandingConfig.DEFAULTS;
	private static final GameHost HOST = new GameHost();
	private static final PlayerTracker TRACKER = new PlayerTracker();
	private static @Nullable StandingEngine engine;

	private StandingModule() {
	}

	public static void init() {
		config = HearthConfig.load("standing", StandingConfig.CODEC, StandingConfig.DEFAULTS);
		StandingKeys.init();
		Appearances.init();
		StandingNetwork.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(StandingData.ID, new StandingData());

		StandingEngine e = new StandingEngine(HOST, StandingModule::config);
		engine = e;
		HearthServices.setStanding(e);
		DialogueProvider.register(new StandingDialogue());

		SettlementEvents.DAY_PASSED.register((world, settlement, day) -> e.dayPassed(settlement, day));
		SettlementEvents.FOUNDED.register((world, settlement) -> e.settled(settlement));
		SettlementEvents.STANDING_CHANGED.register((world, settlement, player, from, to) -> {
			MinecraftServer server = HOST.server();
			ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(player);
			if (p != null && TRACKER.presence(player) == settlement.id()) {
				StandingNetwork.sendHere(p, settlement, false);
			}
		});

		ServerLifecycleEvents.SERVER_STARTING.register(HOST::setServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOST.setServer(null);
			TRACKER.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> TRACKER.tick(server, e));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			TRACKER.forget(handler.player.getUUID());
			StandingDialogue.forget(handler.player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> PlayerTracker.afterDamage(entity, source, taken));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> StandingCommands.register(dispatcher));
		StandingDevHarness.install();
	}

	public static StandingConfig config() {
		return config;
	}

	/** The running service, or null before init (other modules should use {@code HearthServices.standing()}). */
	public static @Nullable StandingEngine engine() {
		return engine;
	}

	/** The Hearth world that keeps the standing module's world state, if a server is running. */
	static @Nullable HearthWorld mainWorld() {
		return HOST.main();
	}
}
