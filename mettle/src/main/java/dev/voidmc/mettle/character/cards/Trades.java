package dev.voidmc.mettle.character.cards;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Face-to-face trading (docs/CARDS.md §8.1, §8.3): a request (sneak and use an empty hand on a player, or
 * {@code /mettle trade <player>}), then each side offers up to nine binder cards, nine item stacks and coin; any change
 * unlocks both; when both have locked, Confirm starts a 3 s countdown that moving apart, damage or a disconnect stops;
 * then everything changes hands in one server tick, after room is checked on both sides. Offered cards wait in the
 * ledger as {@code TRADE}; offered items wait in a saved escrow ({@code mettle:character/escrow}) so a crash or a
 * disconnect never loses them. A received card attunes for one in-game day and remembers its holders.
 */
public final class Trades {
	/** Items a player put up in a trade, saved with the player so they come back after a crash. */
	public static final AttachmentType<List<ItemStack>> ESCROW = AttachmentRegistry.create(Mettle.id("character/escrow"), b -> b
		.persistent(Codec.list(ItemStack.CODEC))
		.copyOnDeath());

	static final class Side {
		final UUID player;
		final List<Long> cards = new ArrayList<>();
		final List<ItemStack> items = new ArrayList<>();
		long coin;
		boolean locked;

		Side(UUID player) {
			this.player = player;
		}
	}

	static final class Session {
		final long id;
		final Side a;
		final Side b;
		long countdownEnd = -1;

		Session(long id, UUID a, UUID b) {
			this.id = id;
			this.a = new Side(a);
			this.b = new Side(b);
		}

		Side side(UUID player) {
			return this.a.player.equals(player) ? this.a : this.b;
		}

		Side other(UUID player) {
			return this.a.player.equals(player) ? this.b : this.a;
		}
	}

	private record Request(UUID from, long at) {
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, Request> REQUESTS = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	private static long nextSession = 1;

	private Trades() {
	}

	static void init() {
		ESCROW.toString();
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
				&& player instanceof ServerPlayer from && entity instanceof ServerPlayer to) {
				request(from, to).ifPresent(r -> refused(from, r));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Trades::tick);
		ServerPlayerEvents.JOIN.register(Trades::returnEscrow);
		// Items offered by a player who died mid-trade waited in the escrow (kept on death); they come back on respawn.
		ServerPlayerEvents.AFTER_RESPAWN.register(dev.voidmc.mettle.core.CoreModule.AFTER_ATTACHMENTS, (oldPlayer, newPlayer, alive) -> returnEscrow(newPlayer));
		ServerPlayerEvents.LEAVE.register(p -> {
			Session s = SESSIONS.get(p.getUUID());
			if (s != null) {
				cancel(p.level().getServer(), s, "mettle.trade.cancelled.left");
			}
			REQUESTS.remove(p.getUUID());
			LAST_HURT.remove(p.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > 0) {
				LAST_HURT.put(p.getUUID(), p.level().getGameTime());
				Session s = SESSIONS.get(p.getUUID());
				if (s != null && (s.countdownEnd >= 0 || s.a.locked || s.b.locked)) {
					unlock(p.level().getServer(), s, "mettle.trade.unlocked.hurt");
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.TradeAction.TYPE, (payload, context) -> act(context.player(), payload));
	}

	// ---------------------------------------------------------------- starting

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** Whether a player has gone unhurt long enough to trade (TradeRules' calm window). */
	public static boolean calm(ServerPlayer p) {
		return now(p.level().getServer()) - LAST_HURT.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) >= TradeRules.CALM_TICKS;
	}

	private static Optional<TradeRules.Refusal> meet(ServerPlayer a, ServerPlayer b) {
		long now = now(a.level().getServer());
		double distance = a.level() == b.level() ? a.distanceTo(b) : Double.MAX_VALUE;
		return TradeRules.canMeet(a.getUUID(), b.getUUID(), distance, a.isAlive() && b.isAlive(), now,
			LAST_HURT.getOrDefault(a.getUUID(), Long.MIN_VALUE / 2), LAST_HURT.getOrDefault(b.getUUID(), Long.MIN_VALUE / 2));
	}

	/** Asks {@code to} to trade; they get a chip with Accept. */
	public static Optional<TradeRules.Refusal> request(ServerPlayer from, ServerPlayer to) {
		Optional<TradeRules.Refusal> no = meet(from, to);
		if (no.isPresent()) {
			return no;
		}
		if (SESSIONS.containsKey(from.getUUID()) || SESSIONS.containsKey(to.getUUID())) {
			return Optional.of(TradeRules.Refusal.BUSY);
		}
		REQUESTS.put(to.getUUID(), new Request(from.getUUID(), now(from.level().getServer())));
		to.sendSystemMessage(Component.translatable("mettle.trade.asked", from.getName())
			.append(" ").append(Component.translatable("mettle.trade.accept_button").withStyle(s -> s.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/mettle trade accept")).withHoverEvent(new HoverEvent.ShowText(Component.translatable("mettle.trade.accept_hover"))))));
		SignaturePerks.feedback(to, Mettle.id("trade"), Component.translatable("mettle.trade.asked", from.getName()));
		SignaturePerks.feedback(from, Mettle.id("trade"), Component.translatable("mettle.trade.asking", to.getName()));
		view(from, null, to.getName().getString());
		return Optional.empty();
	}

	/** Accepts the waiting request: the trade opens for both. */
	public static Optional<TradeRules.Refusal> accept(ServerPlayer to) {
		MinecraftServer server = to.level().getServer();
		Request r = REQUESTS.remove(to.getUUID());
		if (r == null || now(server) - r.at() > TradeRules.REQUEST_TICKS) {
			return Optional.of(TradeRules.Refusal.NO_REQUEST);
		}
		ServerPlayer from = server.getPlayerList().getPlayer(r.from());
		if (from == null) {
			return Optional.of(TradeRules.Refusal.NO_REQUEST);
		}
		return open(from, to);
	}

	/** Opens a trade between two players directly (an accepted request; ops and tests). */
	public static Optional<TradeRules.Refusal> open(ServerPlayer a, ServerPlayer b) {
		Optional<TradeRules.Refusal> no = meet(a, b);
		if (no.isPresent()) {
			return no;
		}
		if (SESSIONS.containsKey(a.getUUID()) || SESSIONS.containsKey(b.getUUID())) {
			return Optional.of(TradeRules.Refusal.BUSY);
		}
		Session s = new Session(nextSession++, a.getUUID(), b.getUUID());
		SESSIONS.put(a.getUUID(), s);
		SESSIONS.put(b.getUUID(), s);
		views(a.level().getServer(), s);
		Mettle.LOGGER.info("trade {} opened: {} and {}", s.id, a.getName().getString(), b.getName().getString());
		return Optional.empty();
	}

	public static boolean trading(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	// ---------------------------------------------------------------- offering

	private static Optional<Session> session(ServerPlayer player) {
		return Optional.ofNullable(SESSIONS.get(player.getUUID()));
	}

	public static Optional<TradeRules.Refusal> offerCard(ServerPlayer player, long id) {
		Optional<Session> s = session(player);
		if (s.isEmpty()) {
			return Optional.of(TradeRules.Refusal.NOT_TRADING);
		}
		CardLedger ledger = Cards.ledger(player.level().getServer());
		Optional<CardData> card = ledger.get(id);
		if (card.isEmpty()) {
			return Optional.of(TradeRules.Refusal.NOT_YOURS);
		}
		Side side = s.get().side(player.getUUID());
		Optional<TradeRules.Refusal> no = TradeRules.canOffer(card.get(), player.getUUID(), now(player.level().getServer()), side.cards.size());
		if (no.isPresent()) {
			return no;
		}
		ledger.update(id, c -> c.withPlace(new CardData.Place(CardData.PlaceKind.TRADE, Optional.empty(), 0, s.get().id)));
		side.cards.add(id);
		Cards.sync(player);
		unlock(player.level().getServer(), s.get(), null);
		return Optional.empty();
	}

	public static Optional<TradeRules.Refusal> takeBackCard(ServerPlayer player, long id) {
		Optional<Session> s = session(player);
		if (s.isEmpty()) {
			return Optional.of(TradeRules.Refusal.NOT_TRADING);
		}
		Side side = s.get().side(player.getUUID());
		if (!side.cards.remove(id)) {
			return Optional.of(TradeRules.Refusal.NOT_YOURS);
		}
		Cards.ledger(player.level().getServer()).update(id, c -> c.withPlace(CardData.Place.BINDER));
		Cards.sync(player);
		unlock(player.level().getServer(), s.get(), null);
		return Optional.empty();
	}

	/** Puts the stack in inventory slot {@code slot} (-1: the main hand) up for trade; it waits in escrow. */
	public static Optional<TradeRules.Refusal> offerItem(ServerPlayer player, int slot) {
		Optional<Session> s = session(player);
		if (s.isEmpty()) {
			return Optional.of(TradeRules.Refusal.NOT_TRADING);
		}
		Side side = s.get().side(player.getUUID());
		if (side.items.size() >= TradeRules.MAX_ITEMS) {
			return Optional.of(TradeRules.Refusal.TOO_MANY_ITEMS);
		}
		int at = slot < 0 ? player.getInventory().getSelectedSlot() : slot;
		if (at >= player.getInventory().getContainerSize()) {
			return Optional.of(TradeRules.Refusal.EMPTY_HAND);
		}
		ItemStack stack = player.getInventory().getItem(at);
		if (stack.isEmpty()) {
			return Optional.of(TradeRules.Refusal.EMPTY_HAND);
		}
		player.getInventory().setItem(at, ItemStack.EMPTY);
		side.items.add(stack);
		saveEscrow(player, side);
		player.containerMenu.broadcastChanges();
		unlock(player.level().getServer(), s.get(), null);
		return Optional.empty();
	}

	public static Optional<TradeRules.Refusal> takeBackItem(ServerPlayer player, int index) {
		Optional<Session> s = session(player);
		if (s.isEmpty()) {
			return Optional.of(TradeRules.Refusal.NOT_TRADING);
		}
		Side side = s.get().side(player.getUUID());
		if (index < 0 || index >= side.items.size()) {
			return Optional.of(TradeRules.Refusal.EMPTY_HAND);
		}
		give(player, side.items.remove(index));
		saveEscrow(player, side);
		unlock(player.level().getServer(), s.get(), null);
		return Optional.empty();
	}

	/** Offers {@code bits} of Hearth coin (without Hearth, emeralds go in as items). */
	public static Optional<TradeRules.Refusal> setCoin(ServerPlayer player, long bits) {
		Optional<Session> s = session(player);
		if (s.isEmpty()) {
			return Optional.of(TradeRules.Refusal.NOT_TRADING);
		}
		CardFees fees = Rebuilds.fees();
		if (bits > 0 && !fees.coin()) {
			return Optional.of(TradeRules.Refusal.NO_COIN);
		}
		if (bits < 0 || bits > fees.balance(player)) {
			return Optional.of(TradeRules.Refusal.CANT_PAY);
		}
		s.get().side(player.getUUID()).coin = bits;
		unlock(player.level().getServer(), s.get(), null);
		return Optional.empty();
	}

	public static Optional<TradeRules.Refusal> lock(ServerPlayer player) {
		Optional<Session> s = session(player);
		if (s.isEmpty()) {
			return Optional.of(TradeRules.Refusal.NOT_TRADING);
		}
		s.get().side(player.getUUID()).locked = true;
		views(player.level().getServer(), s.get());
		return Optional.empty();
	}

	/** With both sides locked: the 3 s countdown starts. */
	public static Optional<TradeRules.Refusal> confirm(ServerPlayer player) {
		Optional<Session> s = session(player);
		if (s.isEmpty()) {
			return Optional.of(TradeRules.Refusal.NOT_TRADING);
		}
		if (!s.get().a.locked || !s.get().b.locked) {
			return Optional.of(TradeRules.Refusal.NOT_LOCKED);
		}
		if (s.get().countdownEnd < 0) {
			s.get().countdownEnd = now(player.level().getServer()) + 20L * CardConfig.get().trade_countdown_s;
			views(player.level().getServer(), s.get());
		}
		return Optional.empty();
	}

	public static Optional<TradeRules.Refusal> cancel(ServerPlayer player) {
		Optional<Session> s = session(player);
		if (s.isEmpty()) {
			REQUESTS.values().removeIf(r -> r.from().equals(player.getUUID()));
			return Optional.of(TradeRules.Refusal.NOT_TRADING);
		}
		cancel(player.level().getServer(), s.get(), "mettle.trade.cancelled");
		return Optional.empty();
	}

	// ---------------------------------------------------------------- the swap

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		long now = now(server);
		REQUESTS.values().removeIf(r -> now - r.at() > TradeRules.REQUEST_TICKS);
		for (Session s : List.copyOf(new java.util.HashSet<>(SESSIONS.values()))) {
			ServerPlayer a = server.getPlayerList().getPlayer(s.a.player);
			ServerPlayer b = server.getPlayerList().getPlayer(s.b.player);
			if (a == null || b == null) {
				// A side that isn't a listed player (a test's stand-in) is checked by whoever drives it.
				if (!(a != null && b == null && fake(s.b.player)) && !(b != null && a == null && fake(s.a.player))) {
					cancel(server, s, "mettle.trade.cancelled.left");
				}
				continue;
			}
			if (a.level() != b.level() || a.distanceTo(b) > CardConfig.get().trade_range || !a.isAlive() || !b.isAlive()) {
				cancel(server, s, "mettle.trade.cancelled.apart");
				continue;
			}
			if (s.countdownEnd >= 0 && now >= s.countdownEnd) {
				swap(server, s, a, b);
			}
		}
	}

	/** Test stand-ins (Fabric fake players) registered for a trade: they are not in the player list. */
	private static final Map<UUID, ServerPlayer> STAND_INS = new HashMap<>();

	public static void standIn(ServerPlayer fake) {
		STAND_INS.put(fake.getUUID(), fake);
	}

	private static boolean fake(UUID id) {
		return STAND_INS.containsKey(id);
	}

	private static @Nullable ServerPlayer player(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		return p != null ? p : STAND_INS.get(id);
	}

	/** Runs the swap now if the countdown is over (tests drive a stand-in's trade this way). */
	public static void finishNow(MinecraftServer server, ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		if (s == null || s.countdownEnd < 0) {
			return;
		}
		ServerPlayer a = player(server, s.a.player);
		ServerPlayer b = player(server, s.b.player);
		if (a != null && b != null) {
			swap(server, s, a, b);
		}
	}

	private static void swap(MinecraftServer server, Session s, ServerPlayer a, ServerPlayer b) {
		CardLedger ledger = Cards.ledger(server);
		// Everything offered must still be in this trade, as offered: anything else and nothing changes hands.
		if (!stillOffered(ledger, s, s.a) || !stillOffered(ledger, s, s.b)) {
			cancel(server, s, "mettle.trade.cancelled.changed");
			return;
		}
		Optional<TradeRules.Refusal> room = room(a, s.a, s.b, ledger).or(() -> room(b, s.b, s.a, ledger));
		CardFees fees = Rebuilds.fees();
		if (room.isEmpty() && (fees.balance(a) < s.a.coin || fees.balance(b) < s.b.coin)) {
			room = Optional.of(TradeRules.Refusal.CANT_PAY);
		}
		if (room.isPresent()) {
			unlock(server, s, room.get().translationKey());
			return;
		}
		// Coin first: when a transfer fails, the one already made goes back and nothing else moves.
		if (s.a.coin > 0 && !fees.transfer(a, b, s.a.coin)) {
			unlock(server, s, TradeRules.Refusal.CANT_PAY.translationKey());
			return;
		}
		if (s.b.coin > 0 && !fees.transfer(b, a, s.b.coin)) {
			if (s.a.coin > 0 && !fees.transfer(b, a, s.a.coin)) {
				Mettle.LOGGER.error("trade {}: {} bits from {} to {} could not be given back after the other side's transfer failed", s.id, s.a.coin,
					a.getName().getString(), b.getName().getString());
			}
			unlock(server, s, TradeRules.Refusal.CANT_PAY.translationKey());
			return;
		}
		long now = now(server);
		long day = Cards.day(server);
		for (long id : s.a.cards) {
			ledger.update(id, c -> TradeRules.received(c, b.getUUID(), b.getGameProfile().name(), day, now));
		}
		for (long id : s.b.cards) {
			ledger.update(id, c -> TradeRules.received(c, a.getUUID(), a.getGameProfile().name(), day, now));
		}
		s.a.items.forEach(st -> give(b, st));
		s.b.items.forEach(st -> give(a, st));
		s.a.items.clear();
		s.b.items.clear();
		a.removeAttached(ESCROW);
		b.removeAttached(ESCROW);
		end(s);
		Cards.sync(a);
		Cards.sync(b);
		// Both sides and the ledger on disk now, ledger first: a crash can't leave one side traded and the other not.
		LedgerFile.commit(server, a, b);
		CardApi.traded(a, b, List.copyOf(s.a.cards));
		CardApi.traded(b, a, List.copyOf(s.b.cards));
		for (ServerPlayer p : List.of(a, b)) {
			view(p, null, null);
			SignaturePerks.feedback(p, Mettle.id("trade"), Component.translatable("mettle.trade.done", (p == a ? b : a).getName()));
		}
		Mettle.LOGGER.info("trade {} done: {} gave {} cards, {} items, {} bits; {} gave {} cards, {} items, {} bits", s.id, a.getName().getString(),
			s.a.cards.size(), s.a.items.size(), s.a.coin, b.getName().getString(), s.b.cards.size(), s.b.items.size(), s.b.coin);
	}

	/** Whether each card a side offered is still in this trade, theirs, as offered (X7: re-checked at the swap). */
	private static boolean stillOffered(CardLedger ledger, Session s, Side side) {
		for (long id : side.cards) {
			Optional<CardData> c = ledger.get(id);
			if (c.isEmpty() || !c.get().owner().equals(side.player) || c.get().place().kind() != CardData.PlaceKind.TRADE || c.get().place().ref() != s.id) {
				Mettle.LOGGER.warn("trade {}: card {} is no longer offered in it ({}); the trade is cancelled", s.id, id, c.map(x -> x.place().toString()).orElse("gone"));
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether {@code player} can take what the other side offers: binder room for the cards (Mythics always fit),
	 * inventory room for the items.
	 */
	private static Optional<TradeRules.Refusal> room(ServerPlayer player, Side mine, Side theirs, CardLedger ledger) {
		int owned = CardRules.binderCount(CardStore.get(player).cards());
		int giving = (int) mine.cards.stream().filter(id -> ledger.get(id).map(c -> !c.mythic()).orElse(false)).count();
		int receiving = (int) theirs.cards.stream().filter(id -> ledger.get(id).map(c -> !c.mythic()).orElse(false)).count();
		if (receiving > 0 && !TradeRules.binderRoom(owned, giving, receiving)) {
			return Optional.of(TradeRules.Refusal.BINDER_FULL);
		}
		List<ItemStack> slots = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
			slots.add(player.getInventory().getNonEquipmentItems().get(i));
		}
		return TradeRules.fits(slots, theirs.items) ? Optional.empty() : Optional.of(TradeRules.Refusal.NO_ROOM);
	}

	private static void end(Session s) {
		SESSIONS.remove(s.a.player);
		SESSIONS.remove(s.b.player);
	}

	/** Ends a trade: offered cards back to their binders, offered items back to their owners. */
	private static void cancel(MinecraftServer server, Session s, String why) {
		end(s);
		CardLedger ledger = Cards.ledger(server);
		for (Side side : List.of(s.a, s.b)) {
			side.cards.forEach(id -> ledger.update(id, c -> c.withPlace(CardData.Place.BINDER)));
			ServerPlayer p = player(server, side.player);
			if (p != null && !p.isAlive()) {
				// Dead mid-trade: what they offered stays in the escrow, which is kept on death, and comes back on respawn
				// (given to the corpse now, it would be lost with keepInventory off).
				side.items.clear();
				Cards.sync(p);
				view(p, null, null);
				SignaturePerks.feedback(p, Mettle.id("trade"), Component.translatable(why));
			} else if (p != null) {
				side.items.forEach(st -> give(p, st));
				side.items.clear();
				p.removeAttached(ESCROW);
				Cards.sync(p);
				view(p, null, null);
				SignaturePerks.feedback(p, Mettle.id("trade"), Component.translatable(why));
			}
			// An offline side's items stay in its saved escrow and come back when it joins.
		}
		Mettle.LOGGER.info("trade {} cancelled ({})", s.id, why);
	}

	/** Any change unlocks both sides and stops the countdown. */
	private static void unlock(MinecraftServer server, Session s, @Nullable String why) {
		boolean was = s.a.locked || s.b.locked || s.countdownEnd >= 0;
		s.a.locked = false;
		s.b.locked = false;
		s.countdownEnd = -1;
		views(server, s);
		if (was && why != null) {
			for (Side side : List.of(s.a, s.b)) {
				ServerPlayer p = player(server, side.player);
				if (p != null) {
					SignaturePerks.feedback(p, Mettle.id("trade"), Component.translatable(why));
				}
			}
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.containerMenu.broadcastChanges();
	}

	private static void saveEscrow(ServerPlayer player, Side side) {
		List<ItemStack> copy = new ArrayList<>();
		side.items.forEach(st -> copy.add(st.copy()));
		if (copy.isEmpty()) {
			player.removeAttached(ESCROW);
		} else {
			player.setAttached(ESCROW, copy);
		}
	}

	/** On join: items left in escrow by a crash or a disconnect come back. Public for the tour. */
	public static void returnEscrow(ServerPlayer player) {
		List<ItemStack> left = player.getAttached(ESCROW);
		// A player who joins dead gets it after respawning (AFTER_RESPAWN), not into the inventory death empties.
		if (left != null && !SESSIONS.containsKey(player.getUUID()) && player.isAlive()) {
			player.removeAttached(ESCROW);
			left.forEach(st -> give(player, st));
			Mettle.LOGGER.info("{}: {} stacks from an unfinished trade came back", player.getName().getString(), left.size());
		}
	}

	// ---------------------------------------------------------------- the view each side sees

	private static void views(MinecraftServer server, Session s) {
		for (Side side : List.of(s.a, s.b)) {
			ServerPlayer p = player(server, side.player);
			if (p != null) {
				view(p, s, null);
			}
		}
	}

	private static void view(ServerPlayer player, @Nullable Session s, @Nullable String asking) {
		if (s == null) {
			ServerPlayNetworking.send(player, new MettlePayloads.TradeView(false, asking == null ? "" : asking, MettlePayloads.TradeSide.EMPTY,
				MettlePayloads.TradeSide.EMPTY, -1, Rebuilds.fees().coin()));
			return;
		}
		MinecraftServer server = player.level().getServer();
		Side mine = s.side(player.getUUID());
		Side theirs = s.other(player.getUUID());
		ServerPlayer other = player(server, theirs.player);
		long left = s.countdownEnd < 0 ? -1 : Math.max(0, s.countdownEnd - now(server));
		ServerPlayNetworking.send(player, new MettlePayloads.TradeView(true, other == null ? "?" : other.getGameProfile().name(), side(server, mine),
			side(server, theirs), (int) left, Rebuilds.fees().coin()));
	}

	private static MettlePayloads.TradeSide side(MinecraftServer server, Side side) {
		List<CardData> cards = new ArrayList<>();
		side.cards.forEach(id -> Cards.ledger(server).get(id).ifPresent(cards::add));
		List<ItemStack> items = new ArrayList<>();
		side.items.forEach(st -> items.add(st.copy()));
		return new MettlePayloads.TradeSide(cards, items, side.coin, side.locked);
	}

	// ---------------------------------------------------------------- requests from the client

	private static void act(ServerPlayer player, MettlePayloads.TradeAction a) {
		Optional<TradeRules.Refusal> no = switch (a.action()) {
			case REQUEST -> {
				ServerPlayer to = player.level().getServer().getPlayerList().getPlayerByName(a.text());
				yield to == null ? Optional.of(TradeRules.Refusal.NO_REQUEST) : request(player, to);
			}
			case ACCEPT -> accept(player);
			case OFFER_CARD -> offerCard(player, a.number());
			case TAKE_CARD -> takeBackCard(player, a.number());
			case OFFER_ITEM -> offerItem(player, (int) a.number());
			case TAKE_ITEM -> takeBackItem(player, (int) a.number());
			case COIN -> setCoin(player, a.number());
			case LOCK -> lock(player);
			case CONFIRM -> confirm(player);
			case CANCEL -> cancel(player);
		};
		no.ifPresent(r -> refused(player, r));
	}

	static void refused(ServerPlayer player, TradeRules.Refusal r) {
		SignaturePerks.feedback(player, Mettle.id("trade"), Component.translatable(r.translationKey()));
	}
}
