package dev.voidmc.hearth.quest.net;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.block.NoticeBoardBlock;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Registers the quest packets and handles what clients send. Handlers run on the server thread. */
public final class QuestNetwork {
	/** How near a board a player must stand to use it (squared blocks). */
	private static final double BOARD_REACH_SQ = 8.0 * 8.0;

	private QuestNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(QuestPayloads.Board.TYPE, QuestPayloads.Board.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(QuestPayloads.Toast.TYPE, QuestPayloads.Toast.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(QuestPayloads.BoardAction.TYPE, QuestPayloads.BoardAction.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(QuestPayloads.JournalAction.TYPE, QuestPayloads.JournalAction.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuestPayloads.BoardAction.TYPE, (payload, context) -> onBoardAction(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(QuestPayloads.JournalAction.TYPE, (payload, context) -> onJournalAction(context.player(), payload));
	}

	private static void onBoardAction(ServerPlayer player, QuestPayloads.BoardAction action) {
		ServerLevel level = player.level();
		if (player.blockPosition().distSqr(action.boardPos()) > BOARD_REACH_SQ
			|| !(level.getBlockState(action.boardPos()).getBlock() instanceof NoticeBoardBlock)) {
			return;
		}
		Settlement s = QuestWorlds.at(level, action.boardPos());
		if (s == null || s.id() != action.settlementId()) {
			return;
		}
		switch (action.action()) {
			case ACCEPT -> action.quest().ifPresent(id -> QuestManager.accept(player, s, id));
			case HAND_IN -> QuestManager.handIn(player, s, null);
			case REFRESH -> {
			}
		}
		NoticeBoardBlock.open(player, level, s, action.boardPos());
	}

	private static void onJournalAction(ServerPlayer player, QuestPayloads.JournalAction action) {
		switch (action.action()) {
			case TRACK -> QuestManager.track(player, action.quest());
			case ABANDON -> QuestManager.abandon(player, action.quest());
		}
	}
}
