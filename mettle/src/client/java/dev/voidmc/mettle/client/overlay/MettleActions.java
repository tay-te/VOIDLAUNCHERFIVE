package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.client.draft.CardAudio;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The calls the overlay makes (docs/OVERLAY.md), shaped like its JSON so the bridge is a thin shim. The answer to
 * {@link #chooseBackground} arrives later as a {@code mettleResult} event: the server decides.
 */
public final class MettleActions {
	private MettleActions() {
	}

	/** {@code {background: "mettle:ranger", spent: {strength: 1}, favoured: ["mettle:mining"]}} */
	public static void chooseBackground(JsonObject request) {
		var background = ResourceKey.create(MettleRegistries.BACKGROUND, Identifier.parse(request.get("background").getAsString()));
		Map<CharacterAttribute, Integer> spent = new HashMap<>();
		if (request.has("spent")) {
			for (Map.Entry<String, JsonElement> e : request.getAsJsonObject("spent").entrySet()) {
				CharacterAttribute a = CharacterAttribute.CODEC.byName(e.getKey());
				if (a != null) {
					spent.put(a, e.getValue().getAsInt());
				}
			}
		}
		List<ResourceKey<Skill>> favoured = new ArrayList<>();
		if (request.has("favoured")) {
			request.getAsJsonArray("favoured").forEach(e -> favoured.add(ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(e.getAsString()))));
		}
		ClientPlayNetworking.send(new MettlePayloads.ChooseBackground(background, spent, favoured));
	}

	/**
	 * The old "buy the next rank of a perk". Perks come from drafts now (docs/DRAFT.md): nothing is sent, and a feedback
	 * chip ({@code mettle:draft/no_buying}) says so. Kept so a host that still calls it gets an answer, not a crash; use
	 * {@link #pickCard} and {@link #reroll}.
	 */
	public static void learnPerk(String id) {
		MettleState.remember("mettle:draft/no_buying", Component.translatable("mettle.draft.no_buying").getString());
	}

	/**
	 * Keeps the {@code index}-th card (0–2) of a skill's draft hand ({@code skills[].hand}); the server checks it and
	 * answers with a chip, and the next hand (if any) arrives with the synced character.
	 */
	public static void pickCard(String skill, int index) {
		// The flare, the thunk and the other two burning away play at once (docs/SOUNDS.md); the overlay plays nothing.
		// A Mythic claimed this way leaves the hand open (no burn): its reveal arrives as cards.reveal.
		if (index != mythicIndex(skill)) {
			CardAudio.pick(skill, index);
		}
		ClientPlayNetworking.send(new MettlePayloads.PickCard(ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(skill)), index));
	}

	/** {@link #pickCard(String, int)} for hosts that pass one string: {@code "mettle:mining#1"} (skill, {@code #}, index 0–2). */
	public static void pickCard(String skillAndIndex) {
		int hash = skillAndIndex.lastIndexOf('#');
		if (hash > 0) {
			try {
				pickCard(skillAndIndex.substring(0, hash), Integer.parseInt(skillAndIndex.substring(hash + 1).trim()));
			} catch (NumberFormatException ignored) {
				// A malformed call from a page: nothing to send.
			}
		}
	}

	/** Spends a reroll on a fresh hand for a skill's draft ({@code draft.rerolls} says how many are left). */
	public static void reroll(String skill) {
		CardAudio.reroll(skill);
		ClientPlayNetworking.send(new MettlePayloads.Reroll(ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(skill))));
	}

	// ---------------------------------------------------------------- cards v2 (docs/CARDS.md, OVERLAY.md "Cards v2")

	/**
	 * Keeps a hand's card into the binder ({@code slot} false, "Keep") or straight into a free legal slot of its deck
	 * ({@code slot} true, "Keep &amp; slot"). {@link #pickCard} is the old keep: it slots when a legal slot is free.
	 */
	public static void keepCard(String skill, int index, boolean slot) {
		ClientPlayNetworking.send(new MettlePayloads.KeepCard(ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(skill)), index, slot));
	}

	/**
	 * Claims the Mythic offer in a skill's draft hand ({@code skills[].mythicOffer} ≥ 0; docs/CARDS.md §3.5): it goes to
	 * the binder and the Hall of Firsts, leaves the hand, and the hand stays open, since claiming a Mythic does not use the
	 * draft's keep: the player still keeps one of the other cards. The reveal arrives as {@code cards.reveal}. Nothing is
	 * sent when the hand holds no Mythic. {@link #keepCard} / {@link #pickCard} at the Mythic's index do the same.
	 */
	public static void claimMythic(String skill) {
		int at = mythicIndex(skill);
		if (at >= 0) {
			ClientPlayNetworking.send(new MettlePayloads.KeepCard(ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(skill)), at, false));
		}
	}

	/** Where the synced hand of a skill holds its Mythic offer, or -1. */
	private static int mythicIndex(String skill) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		Identifier id = skill == null ? null : Identifier.tryParse(skill.trim());
		if (mc.player == null || id == null) {
			return -1;
		}
		return dev.voidmc.mettle.character.cards.MythicRules.offerIndex(
			dev.voidmc.mettle.core.CharacterStore.get(mc.player).drafts().hand(ResourceKey.create(MettleRegistries.SKILL, id)));
	}

	/** Puts a binder card ({@code cards.binder.cards[].id}) in the first free slot of its deck. Free, anywhere. */
	public static void slotCard(String card) {
		slotCard(card, -1);
	}

	/** Puts a binder card in slot {@code slot} (0-based, {@code decks[].row[].index}; -1 = first free) of its deck. */
	public static void slotCard(String card, int slot) {
		cardAction(MettlePayloads.CardActionKind.SLOT, ids(card), slot, List.of());
	}

	/** Puts a Mythic in any deck ({@code "mettle:blades"}): at most two slotted, never two in one deck. */
	public static void slotCard(String card, int slot, String deck) {
		cardAction(MettlePayloads.CardActionKind.SLOT, ids(card), slot, List.of(Identifier.parse(deck)));
	}

	/**
	 * Takes slotted cards of one deck back to the binder ({@code "12"} or {@code "12,15"}): free below character level 5,
	 * for dormant cards, on a deck's first rebuild after the migration, or with free swaps ({@code cards.costs}). Away
	 * from a trainer or Card Shrine nothing else pays; the server answers with a chip either way.
	 */
	public static void unslotCards(String cards) {
		cardAction(MettlePayloads.CardActionKind.UNSLOT, ids(cards), 0, List.of());
	}

	public static void unslotCard(String card) {
		unslotCards(card);
	}

	/** Pins up to three keywords as the active sets ({@code "mettle:swift,mettle:keen"}; empty = automatic). Once a day. */
	public static void pinSets(String keywords) {
		List<Identifier> out = new ArrayList<>();
		for (String k : keywords.split(",")) {
			if (!k.isBlank()) {
				out.add(Identifier.parse(k.trim()));
			}
		}
		cardAction(MettlePayloads.CardActionKind.PIN, List.of(), 0, out);
	}

	/** At a Card Shrine: releases three binder cards of one skill for a reroll ({@code "3,8,9"}). Elsewhere a chip says where. */
	public static void releaseCards(String cards) {
		cardAction(MettlePayloads.CardActionKind.RELEASE, ids(cards), 0, List.of());
	}

	/** At a Card Shrine or trainer: rebuilds (takes out) these slotted cards of one deck, paying the draught, fees and scars. */
	public static void rebuildCards(String cards) {
		cardAction(MettlePayloads.CardActionKind.REBUILD, ids(cards), 0, List.of());
	}

	/** At a Card Shrine: fuses three binder cards of one perk and rarity into one of the next rarity. */
	public static void fuseCards(String cards) {
		cardAction(MettlePayloads.CardActionKind.FUSE, ids(cards), 0, List.of());
	}

	/** Ends a shrine or trainer sitting (the draught is spent once anything was taken out). */
	public static void closeSitting() {
		cardAction(MettlePayloads.CardActionKind.CLOSE, List.of(), 0, List.of());
	}

	// ---------------------------------------------------------------- trading (docs/CARDS.md §8.1)

	/** Asks the named player to trade (they must be within 8 blocks); they get a chip with Accept. */
	public static void tradeRequest(String player) {
		trade(MettlePayloads.TradeActionKind.REQUEST, 0, player);
	}

	public static void tradeAccept() {
		trade(MettlePayloads.TradeActionKind.ACCEPT, 0, "");
	}

	/** Offers a binder card (attuned, not slotted). Any change unlocks both sides. */
	public static void tradeOfferCard(String card) {
		trade(MettlePayloads.TradeActionKind.OFFER_CARD, first(card), "");
	}

	public static void tradeTakeBackCard(String card) {
		trade(MettlePayloads.TradeActionKind.TAKE_CARD, first(card), "");
	}

	/** Offers the stack in inventory slot {@code slot} (-1: the main hand); it waits in escrow. */
	public static void tradeOfferItem(int slot) {
		trade(MettlePayloads.TradeActionKind.OFFER_ITEM, slot, "");
	}

	/** Takes back offered stack {@code index} ({@code trade.me.items[index]}). */
	public static void tradeTakeBackItem(int index) {
		trade(MettlePayloads.TradeActionKind.TAKE_ITEM, index, "");
	}

	/** Offers Hearth coin, in bits (refused without Hearth: emeralds go in as items). */
	public static void tradeCoin(long bits) {
		trade(MettlePayloads.TradeActionKind.COIN, bits, "");
	}

	public static void tradeLock() {
		trade(MettlePayloads.TradeActionKind.LOCK, 0, "");
	}

	/** With both sides locked: starts the 3 s countdown. */
	public static void tradeConfirm() {
		trade(MettlePayloads.TradeActionKind.CONFIRM, 0, "");
	}

	public static void tradeCancel() {
		trade(MettlePayloads.TradeActionKind.CANCEL, 0, "");
	}

	private static void trade(MettlePayloads.TradeActionKind kind, long number, String text) {
		ClientPlayNetworking.send(new MettlePayloads.TradeAction(kind, number, text));
	}

	private static long first(String ids) {
		List<Long> all = ids(ids);
		return all.isEmpty() ? -1 : all.getFirst();
	}

	private static void cardAction(MettlePayloads.CardActionKind kind, List<Long> cards, int index, List<Identifier> keywords) {
		ClientPlayNetworking.send(new MettlePayloads.CardAction(kind, cards, index, keywords));
	}

	private static List<Long> ids(String text) {
		List<Long> out = new ArrayList<>();
		for (String part : text.split(",")) {
			try {
				if (!part.isBlank()) {
					out.add(Long.parseLong(part.trim()));
				}
			} catch (NumberFormatException ignored) {
				// A malformed id from a page: skipped.
			}
		}
		return out;
	}

	/**
	 * The card sounds the overlay times to its animations (docs/SOUNDS.md §5): {@code cardSound("deal", "mettle:mining")}
	 * as a hand fans out (its rarity stinger follows), {@code ("hover", "epic")} or {@code ("hover", "mettle:mining#2")}
	 * as the pointer reaches a card, {@code ("unhover", "")}, {@code ("mythic_reveal", "")} as the reveal starts (its
	 * markers are in {@code draft.sound}), {@code ("stop", "")} when the draft closes, and {@code ("slot", "")} /
	 * {@code ("set_complete", "")}. Picks and rerolls sound by themselves through {@link #pickCard} and {@link #reroll}.
	 */
	public static void cardSound(String cue, String arg) {
		CardAudio.call(cue, arg);
	}

	/** {@link #cardSound(String, String)} for hosts that pass one string: {@code "hover#epic"}, {@code "deal#mettle:mining"}. */
	public static void cardSound(String cueAndArg) {
		if (cueAndArg == null) {
			return;
		}
		int hash = cueAndArg.indexOf('#');
		CardAudio.call(hash < 0 ? cueAndArg : cueAndArg.substring(0, hash), hash < 0 ? "" : cueAndArg.substring(hash + 1));
	}

	public static void spendAttribute(String attribute) {
		CharacterAttribute a = CharacterAttribute.CODEC.byName(attribute);
		if (a != null) {
			ClientPlayNetworking.send(new MettlePayloads.SpendAttribute(a));
		}
	}

	public static void closeMenu() {
		Minecraft.getInstance().gui.setScreen(null);
	}
}
