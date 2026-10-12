package dev.voidmc.mettle.core;

import dev.voidmc.mettle.Mettle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Where a player's card cache lives ({@code mettle:character/cards}, docs/CARDS.md §2.1): persistent and kept on death.
 * The world ledger is the truth; this is rebuilt from it on join.
 *
 * <p>The owner's client gets it through a second, synced attachment ({@code mettle:character/cards_view}) that is set
 * once at the end of a server tick when the cache changed: one action that changes the cards, then the deck's derived
 * state (two sets) sends the binder once, and so do many changes in one tick. {@link #get} reads the cache on the server
 * and the view on the client.
 */
public final class CardStore {
	public static final AttachmentType<CardsState> CARDS = AttachmentRegistry.create(Mettle.id("character/cards"), b -> b
		.persistent(CardsState.CODEC)
		.copyOnDeath());

	/** What the owner's client sees: the cache as it was at the end of the last server tick it changed in. */
	public static final AttachmentType<CardsState> VIEW = AttachmentRegistry.create(Mettle.id("character/cards_view"), b -> b
		.syncWith(ByteBufCodecs.fromCodecTrusted(CardsState.CODEC), AttachmentSyncPredicate.targetOnly()));

	private static final Set<ServerPlayer> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());

	private CardStore() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> flush());
		ServerPlayerEvents.JOIN.register(CardStore::changed);
		// The respawned player is a new entity: its view is sent again (the client keeps its copy meanwhile).
		ServerPlayerEvents.AFTER_RESPAWN.register(CoreModule.AFTER_ATTACHMENTS, (oldPlayer, newPlayer, alive) -> changed(newPlayer));
	}

	public static CardsState get(Player player) {
		CardsState state = player.getAttached(player.level().isClientSide() ? VIEW : CARDS);
		return state == null ? CardsState.EMPTY : state;
	}

	public static void set(Player player, CardsState state) {
		player.setAttached(CARDS, state);
		if (player instanceof ServerPlayer p) {
			changed(p);
		}
	}

	private static void changed(ServerPlayer player) {
		DIRTY.add(player);
	}

	/** Sends every changed cache to its owner, once (end of the server tick; tests may call it to send at once). */
	public static void flush() {
		if (DIRTY.isEmpty()) {
			return;
		}
		for (ServerPlayer p : List.copyOf(DIRTY)) {
			CardsState now = p.getAttached(CARDS);
			if (!p.hasDisconnected() && !Objects.equals(p.getAttached(VIEW), now)) {
				p.setAttached(VIEW, now);
			}
		}
		DIRTY.clear();
	}
}
