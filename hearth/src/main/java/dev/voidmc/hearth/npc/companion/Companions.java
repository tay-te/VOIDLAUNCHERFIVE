package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.QuestEvents;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.npc.entity.Folk;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * The player's place: companions (docs/LIVING_WORLD_2.md §8). Hands hired at an inn or tavern for a fee and a daily wage
 * ({@link CompanionService}), at the player's side as folk ({@link CompanionBehaviour}), given orders and gear in
 * conversation ({@link CompanionDialogue}). Settings in {@code config/hearth/place.json} ({@link PlaceConfig}), which the
 * household, the burghers and the commissions read too.
 *
 * <p>Lane B4 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 */
public final class Companions {
	private static PlaceConfig config = PlaceConfig.DEFAULTS;

	private Companions() {
	}

	public static PlaceConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(PlaceConfig newConfig) {
		config = newConfig;
	}

	/** Called once from {@code NpcModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("place", PlaceConfig.CODEC, PlaceConfig.DEFAULTS);
		CompanionRoster.init();
		PlaceSummary.register();
		HearthServices.npc().registerBehaviour(new CompanionBehaviour());
		DialogueProvider.register(new CompanionDialogue());

		ServerTickEvents.END_SERVER_TICK.register(CompanionService::tick);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// the Household page's data (F2 until designed: the hub state's household section), once a minute
			if (server.getTickCount() % 1200 == 600) {
				for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
					if (PlaceSummary.any(p)) {
						PlaceSummary.send(p);
					}
				}
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			CompanionService.joined(handler.player);
			PlaceSummary.send(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			CompanionService.left(handler.player);
			CompanionDialogue.forget(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CompanionService.clear());

		SettlementEvents.DAY_PASSED.register(CompanionService::dayPassed);
		SettlementEvents.CRIME_COMMITTED.register(CompanionService::crime);
		SettlementEvents.RESIDENT_REMOVED.register((world, s, r) -> {
			if (CompanionState.hired(r)) {
				CompanionService.removed(world, s, r);
			}
		});
		QuestEvents.COMPLETED.register((player, s, type, template, tier) -> CompanionService.questDone(player));
		// a companion killed: fired inside the folk's death, before the embodiment manager removes the resident
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Folk f && f.brain() != null && f.level() instanceof ServerLevel level) {
				Resident r = f.brain().resident;
				if (CompanionState.hired(r)) {
					CompanionService.died(level, f, f.brain().settlement, r);
				}
			}
		});
	}

	/** Whether a resident is someone's companion. */
	public static boolean hired(Resident r) {
		return CompanionState.hired(r);
	}
}
