package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * Where quest state lives: per player in a persistent, owner-synced attachment ({@code hearth:quest/player}); per
 * settlement under a {@code SETTLEMENT}-scope data key ({@code hearth:quest/board}) the sim saves with the settlement.
 */
public final class QuestStore {
	public static final AttachmentType<PlayerQuests> PLAYER = AttachmentRegistry.create(Hearth.id("quest/player"), b -> b
		.persistent(PlayerQuests.CODEC)
		.initializer(PlayerQuests::new)
		.copyOnDeath()
		.syncWith(ByteBufCodecs.fromCodecTrusted(PlayerQuests.CODEC), AttachmentSyncPredicate.targetOnly()));

	public static final HearthDataKey<SettlementQuests> BOARD = HearthDataKey.register(Hearth.id("quest/board"), HearthDataKey.Scope.SETTLEMENT,
		SettlementQuests.CODEC, SettlementQuests::new);

	private QuestStore() {
	}

	/** Forces the class (and so the attachment and data key) to register during init. */
	public static void init() {
	}

	/** The player's quests, read-only use (the client reads its synced copy the same way). */
	public static PlayerQuests get(Player player) {
		PlayerQuests q = player.getAttached(PLAYER);
		return q == null ? new PlayerQuests() : q;
	}

	/**
	 * Changes a player's quests: the edit runs on a copy, which is then attached, so Fabric sees a new value and syncs it
	 * to the player's client.
	 */
	public static void edit(Player player, Consumer<PlayerQuests> edit) {
		PlayerQuests copy = get(player).copy();
		edit.accept(copy);
		player.setAttached(PLAYER, copy);
	}

	/** Like {@link #edit} with a result. */
	public static <R> R editAndGet(Player player, Function<PlayerQuests, R> edit) {
		PlayerQuests copy = get(player).copy();
		R result = edit.apply(copy);
		player.setAttached(PLAYER, copy);
		return result;
	}

	public static SettlementQuests board(Settlement settlement) {
		return settlement.data(BOARD);
	}
}
