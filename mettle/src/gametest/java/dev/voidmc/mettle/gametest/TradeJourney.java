package dev.voidmc.mettle.gametest;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.TradeRules;
import dev.voidmc.mettle.character.cards.Trades;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.DraftState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Trading face to face (docs/CARDS.md §8, wave C), with a second player stood in by a Fabric fake player beside the real
 * one: a request and accept, cards and items in escrow, any change unlocking both, the 3 s countdown and the swap (owners,
 * history, attunement), an attuning card refused a slot and a new trade, a cancel that returns everything, and items left
 * in escrow by a crash coming back on join. Everything is put back.
 */
final class TradeJourney {
	private static FakePlayer standIn;

	private TradeJourney() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("mettle", path);
	}

	private static CardData card(ServerPlayer p, Identifier perk, String keyword) {
		return Cards.giveExact(p, new DraftState.Card(Perks.key(perk), 1, DraftState.Rarity.COMMON, false, List.of(id(keyword)), List.of(), 2));
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		long[] mine = new long[1];
		long[] theirs = new long[1];
		// Nobody trades within 5 s of being hurt (the journeys before this one hurt the player).
		context.waitTicks(105);

		check(server.computeOnServer(s -> {
			ServerPlayer a = player(s);
			ServerPlayer b = bree(s, a);
			if (!a.isAlive() || !b.isAlive()) {
				return "before trading: player alive " + a.isAlive() + " (health " + a.getHealth() + "), stand-in alive " + b.isAlive() + " (health "
					+ b.getHealth() + ", removed " + b.isRemoved() + ")";
			}
			Cards.clear(a);
			Cards.clear(b);
			mine[0] = card(a, MiningSkill.QUARRYMAN, "brutal").id();
			theirs[0] = card(b, MiningSkill.PROSPECTOR, "hoard").id();
			a.getInventory().add(new ItemStack(Items.EMERALD, 5));
			b.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
			// A request, then the other side accepts.
			var asked = Trades.request(a, b);
			if (asked.isPresent()) {
				return "the request was refused: " + asked.get();
			}
			var accepted = Trades.accept(b);
			if (accepted.isPresent() || !Trades.trading(a) || !Trades.trading(b)) {
				return "accepting opened no trade: " + accepted;
			}
			// Each side offers a card and an item; the card waits as TRADE, the items in escrow.
			int emeraldSlot = a.getInventory().findSlotMatchingItem(new ItemStack(Items.EMERALD));
			List<Optional<TradeRules.Refusal>> steps = List.of(Trades.offerCard(a, mine[0]), Trades.offerItem(a, emeraldSlot),
				Trades.offerCard(b, theirs[0]), Trades.offerItem(b, 0));
			if (steps.stream().anyMatch(Optional::isPresent)) {
				return "an offer was refused: " + steps;
			}
			if (Cards.ledger(s).get(mine[0]).orElseThrow().place().kind() != CardData.PlaceKind.TRADE
				|| a.getInventory().countItem(Items.EMERALD) != 0 || a.getAttached(Trades.ESCROW) == null) {
				return "offered things are not in escrow";
			}
			if (Trades.confirm(a).isEmpty()) {
				return "confirm worked before both sides locked";
			}
			Trades.lock(a);
			Trades.lock(b);
			// Any change unlocks both: Bree takes the sword back and offers it again.
			Trades.takeBackItem(b, 0);
			Trades.offerItem(b, b.getInventory().findSlotMatchingItem(new ItemStack(Items.IRON_SWORD)));
			if (Trades.confirm(a).isEmpty()) {
				return "a change did not unlock the trade";
			}
			Trades.lock(a);
			Trades.lock(b);
			return Trades.confirm(a).map(r -> "confirm refused: " + r).orElse(null);
		}));

		// The real player's overlay shows the trade, with the partner's card and its warning.
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonElement trade = MettleState.build(mc).getAsJsonObject("cards").get("trade");
			if (trade == null || trade.isJsonNull() || !trade.getAsJsonObject().get("partner").getAsString().equals("Bree")) {
				return "overlay trade: " + trade;
			}
			var them = trade.getAsJsonObject().getAsJsonObject("them");
			return them.getAsJsonArray("cards").size() == 1 && them.getAsJsonArray("cards").get(0).getAsJsonObject().has("warning")
				&& them.getAsJsonArray("items").size() == 1 ? null : "overlay trade sides: " + trade;
		}));

		// The countdown ends: everything changes hands.
		check(server.computeOnServer(s -> {
			ServerPlayer a = player(s);
			ServerPlayer b = bree(s, a);
			Trades.finishNow(s, a);
			if (Trades.trading(a)) {
				return "the trade is still open after the swap";
			}
			CardData got = Cards.ledger(s).get(theirs[0]).orElseThrow();
			CardData gave = Cards.ledger(s).get(mine[0]).orElseThrow();
			if (!got.owner().equals(a.getUUID()) || !gave.owner().equals(b.getUUID()) || got.history().owners() != 2 || got.attuned(s.overworld().getGameTime())) {
				return "after the swap: got " + got + ", gave " + gave;
			}
			if (a.getInventory().countItem(Items.IRON_SWORD) != 1 || b.getInventory().countItem(Items.EMERALD) != 5 || a.getAttached(Trades.ESCROW) != null) {
				return "items did not change hands";
			}
			if (CardStore.get(a).card(theirs[0]).isEmpty() || CardStore.get(b).card(mine[0]).isEmpty()) {
				return "the caches were not rebuilt from the ledger";
			}
			// Attuning: it can't be slotted or traded on for a day.
			var slot = Cards.slot(a, theirs[0], -1);
			if (slot.isEmpty() || slot.get() != CardRules.Refusal.ATTUNING) {
				return "slotting a just-traded card gave " + slot;
			}
			Trades.open(a, b);
			var onward = Trades.offerCard(a, theirs[0]);
			if (onward.isEmpty() || onward.get() != TradeRules.Refusal.ATTUNING) {
				return "offering an attuning card gave " + onward;
			}
			// A cancel returns everything: an item and a card offered, then called off.
			CardData spare = card(a, MiningSkill.TUNNELER, "echo");
			a.getInventory().add(new ItemStack(Items.DIAMOND, 3));
			Trades.offerCard(a, spare.id());
			Trades.offerItem(a, a.getInventory().findSlotMatchingItem(new ItemStack(Items.DIAMOND)));
			Trades.cancel(a);
			if (Trades.trading(a) || a.getInventory().countItem(Items.DIAMOND) != 3
				|| Cards.ledger(s).get(spare.id()).orElseThrow().place().kind() != CardData.PlaceKind.BINDER) {
				return "a cancelled trade lost something";
			}
			// A crash mid-trade: items left in escrow come back when the player joins.
			a.setAttached(Trades.ESCROW, List.of(new ItemStack(Items.GOLD_INGOT, 2)));
			Trades.returnEscrow(a);
			if (a.getInventory().countItem(Items.GOLD_INGOT) != 2 || a.getAttached(Trades.ESCROW) != null) {
				return "escrowed items did not come back on join";
			}
			// Clean up.
			a.getInventory().clearOrCountMatchingItems(st -> st.is(Items.IRON_SWORD) || st.is(Items.DIAMOND) || st.is(Items.GOLD_INGOT), false, -1,
				a.inventoryMenu.getCraftSlots());
			Cards.clear(a);
			Cards.clear(b);
			return null;
		}));
		context.waitTicks(3);
	}

	private static ServerPlayer bree(MinecraftServer s, ServerPlayer near) {
		// A fresh stand-in each run (Fabric caches fake players per profile, and a cached one may have been hurt or removed).
		if (standIn == null || standIn.isRemoved() || !standIn.isAlive()) {
			standIn = FakePlayer.get(s.overworld(), new GameProfile(UUID.randomUUID(), "Bree"));
		}
		FakePlayer b = standIn;
		b.snapTo(near.getX() + 2, near.getY(), near.getZ(), 0, 0);
		b.setHealth(b.getMaxHealth());
		near.setHealth(near.getMaxHealth());
		Trades.standIn(b);
		return b;
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
