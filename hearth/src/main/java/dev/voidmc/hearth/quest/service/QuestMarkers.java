package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.QuestService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The quest marker over each resident's head, as the NPC module asks for it: TURN_IN beats OFFER beats ACTIVE. Cheap —
 * it walks the player's few active quests and the settlement's few offers; the board refreshes at most once a day.
 */
public final class QuestMarkers implements QuestService {
	@Override
	public Marker marker(ServerPlayer player, Settlement settlement, Resident resident) {
		PlayerQuests pq = QuestStore.get(player);
		boolean active = false;
		for (QuestInstance q : pq.active) {
			if (q.status == QuestStatus.READY && q.settlementId == settlement.id() && q.giver.is(resident.id)) {
				return Marker.TURN_IN;
			}
			for (Objective o : q.currentObjectives()) {
				if ((o.kind == ObjectiveKind.TALK || (o.kind == ObjectiveKind.COURIER && o.entity != null)) && resident.id.equals(o.entity)) {
					return Marker.TURN_IN;
				}
			}
			if (q.giver.is(resident.id) && q.status == QuestStatus.ACTIVE) {
				active = true;
			}
		}
		ServerLevel level = QuestWorlds.level(player.level().getServer(), settlement);
		if (level != null) {
			SettlementQuests board = QuestManager.board(level, settlement, false);
			for (QuestInstance offer : board.offers) {
				if (offer.giver.is(resident.id) && QuestManager.lock(player, settlement, offer, pq).isEmpty()) {
					return Marker.OFFER;
				}
			}
		}
		return active ? Marker.ACTIVE : Marker.NONE;
	}
}
