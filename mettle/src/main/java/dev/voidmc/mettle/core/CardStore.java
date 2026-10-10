package dev.voidmc.mettle.core;

import dev.voidmc.mettle.Mettle;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * Where a player's card cache lives ({@code mettle:character/cards}, docs/CARDS.md §2.1): persistent, kept on death,
 * synced to its owner only. The world ledger is the truth; this is rebuilt from it on join.
 */
public final class CardStore {
	public static final AttachmentType<CardsState> CARDS = AttachmentRegistry.create(Mettle.id("character/cards"), b -> b
		.persistent(CardsState.CODEC)
		.copyOnDeath()
		.syncWith(ByteBufCodecs.fromCodecTrusted(CardsState.CODEC), AttachmentSyncPredicate.targetOnly()));

	private CardStore() {
	}

	public static void init() {
	}

	public static CardsState get(Player player) {
		CardsState state = player.getAttached(CARDS);
		return state == null ? CardsState.EMPTY : state;
	}

	public static void set(Player player, CardsState state) {
		player.setAttached(CARDS, state);
	}
}
