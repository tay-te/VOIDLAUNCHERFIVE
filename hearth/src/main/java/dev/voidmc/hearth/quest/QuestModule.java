package dev.voidmc.hearth.quest;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.quest.command.QuestCommands;
import dev.voidmc.hearth.quest.dev.QuestDevHarness;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.net.QuestNetwork;
import dev.voidmc.hearth.quest.service.QuestDialogue;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.service.QuestMarkers;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.quest.service.QuestTracker;
import dev.voidmc.hearth.quest.template.QuestTemplates;
import dev.voidmc.hearth.quest.world.SandboxSettlements;
import dev.voidmc.hearth.quest.world.StructureLocator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * The quest module: quests and side quests that come from what settlements need, the notice board, the journal, and
 * reputation. See docs/QUESTS.md.
 *
 * <p>Wiring: templates load from data packs; settlement events (stalls, finished buildings, departures) feed the
 * boards; a bounded per-tick tracker and the death event drive objectives; the notice board, the conversation
 * ({@link QuestDialogue}), the journal and commands are how players act; {@link QuestMarkers} is the
 * {@link dev.voidmc.hearth.api.QuestService} the NPC module asks for markers.
 */
public final class QuestModule {
	private QuestModule() {
	}

	public static void init() {
		QuestManager.setConfig(HearthConfig.load("quest", QuestConfig.CODEC, QuestConfig.DEFAULTS));
		QuestStore.init();
		QuestRegistry.init();
		QuestNetwork.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(QuestTemplates.ID, new QuestTemplates());

		HearthServices.setQuests(new QuestMarkers());
		DialogueProvider.register(new QuestDialogue());

		SettlementEvents.CONSTRUCTION_STALLED.register((world, settlement, plot, goods, missing) ->
			QuestManager.onStalled(world.level(), settlement, new SettlementQuests.StalledNeed(plot.id(), goods, missing, world.day(),
				readable(plot.spec().archetype()))));
		SettlementEvents.BUILDING_COMPLETED.register((world, settlement, plot) -> {
			SettlementQuests data = QuestStore.board(settlement);
			boolean removed = data.needs.removeIf(n -> n.plot() == plot.id());
			removed |= data.offers.removeIf(q -> q.type == QuestType.SUPPLY && q.takenBy.isEmpty() && q.objectives.stream()
				.anyMatch(o -> o.data.getIntOr(dev.voidmc.hearth.quest.model.Objective.Keys.PLOT, -1) == plot.id()));
			if (removed) {
				settlement.markDirty();
			}
		});
		SettlementEvents.RESIDENT_REMOVED.register((world, settlement, resident) -> {
			SettlementQuests data = QuestStore.board(settlement);
			if (data.offers.removeIf(q -> q.takenBy.isEmpty() && q.giver.is(resident.id))) {
				settlement.markDirty();
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(QuestTracker::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(QuestTracker::onDeath);
		ServerPlayerEvents.LEAVE.register(player -> QuestDialogue.forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SandboxSettlements.clear();
			StructureLocator.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> QuestCommands.register(dispatcher));
		QuestDevHarness.install();
	}

	/** {@code hearth:house/cottage} → {@code cottage}, for quest text. */
	static String readable(Identifier archetype) {
		String path = archetype.getPath();
		return path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
	}
}
