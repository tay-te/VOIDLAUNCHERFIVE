package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.core.CoreModule;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * Burden (BURDEN.md §1–§4): every item weighs, what you carry is your load, and load against capacity sets your band.
 * Weights are derived on the server whenever datapacks load and sent to clients; loads are tracked per player on change
 * with a slow fallback; band effects are attribute modifiers plus a few small mixins (hunger, sprint, elytra, swimming).
 */
public final class BurdenModule {
	private static volatile WeightOverrides overrides = WeightOverrides.EMPTY;
	/** The overrides changed since the table was last computed (a reload is under way). */
	private static volatile boolean stale = true;

	private BurdenModule() {
	}

	public static void init() {
		LoadState.ATTACHMENT.identifier();
		PayloadTypeRegistry.clientboundPlay().register(WeightsPayload.TYPE, WeightsPayload.STREAM_CODEC);

		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Mettle.id("burden/weights"), new SimpleReloadListener<WeightOverrides>() {
			@Override
			protected WeightOverrides prepare(PreparableReloadListener.SharedState state) {
				return WeightOverrides.load(state.resourceManager());
			}

			@Override
			protected void apply(WeightOverrides data, PreparableReloadListener.SharedState state) {
				overrides = data;
				stale = true;
			}
		});

		// The table needs recipes and bound tags: both are in place when the server starts and when a reload syncs its
		// contents to players, which happens before END_DATA_PACK_RELOAD.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Weights.setRunning(server);
			table(server);
		});
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			table(player.level().getServer());
			if (ServerPlayNetworking.canSend(player, WeightsPayload.TYPE)) {
				ServerPlayNetworking.send(player, WeightsPayload.of(Weights.server()));
			}
			LoadTracker.markDirty(player);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			table(server);
			LoadTracker.markAllDirty();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LoadTracker.clear();
			Weights.setRunning(null);
			stale = true;
		});

		ServerTickEvents.END_SERVER_TICK.register(LoadTracker::tick);
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, from, to) -> {
			if (entity instanceof ServerPlayer player) {
				LoadTracker.markDirty(player);
			}
		});
		ServerPlayerEvents.JOIN.register(LoadTracker::markDirty);
		ServerPlayerEvents.LEAVE.register(LoadTracker::leave);
		// Respawning drops every modifier; recompute after the attachments have moved to the new player.
		ServerPlayerEvents.AFTER_RESPAWN.register(CoreModule.AFTER_ATTACHMENTS, (oldPlayer, newPlayer, alive) -> LoadTracker.markDirty(newPlayer));

		WeightPerks.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> BurdenCommands.register(dispatcher, context));
		MettleServices.setBurden(new BurdenServiceImpl());
	}

	/** The server's table, recomputed first if datapacks changed since. */
	static WeightTable table(MinecraftServer server) {
		if (stale || Weights.server().size() == 0) {
			stale = false;
			Weights.compute(server, overrides);
		}
		return Weights.server();
	}
}
