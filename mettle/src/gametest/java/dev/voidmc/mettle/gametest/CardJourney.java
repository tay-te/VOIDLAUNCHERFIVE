package dev.voidmc.mettle.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Cards v2 end to end (docs/CARDS.md §16 wave A), as a player meets it: a level deals a hand of rolled cards, the overlay
 * shows them, "Keep" puts one in the binder, "Slot" puts it in the deck and its perk is active, an attribute trait and a
 * behaviour trait change real numbers, two Swift cards make the Swift 2 set, a free swap takes a card out, a slot lost to
 * Wits goes dormant, and an old v1 save becomes cards. Screenshots: the binder in chat. The character is put back.
 */
final class CardJourney {
	private static final ResourceKey<Skill> ATHLETICS = Progress.skill("athletics");
	private static final ResourceKey<Skill> DEFENSE = Progress.skill("defense");
	private static final String ID = ATHLETICS.identifier().toString();

	private CardJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		CharacterState[] saved = new CharacterState[1];
		long[] keptId = new long[1];
		ResourceKey<Perk>[] keptPerk = perkRef();

		// A clean slate: no cards, Athletics at 0 with nothing waiting, no recent skills (no wildcard).
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			saved[0] = Cards.ensure(p);
			Cards.clear(p);
			DraftState o = saved[0].drafts().withQueued(ATHLETICS, 0).withHand(ATHLETICS, List.of());
			DraftState d = new DraftState(o.queued(), o.hands(), 2, o.seed(), o.dealt(), List.of());
			CharacterStore.set(p, saved[0].withXp(ATHLETICS, 0).withDraft(d));
			EffectsApplier.apply(p);
			CharacterState state = CharacterStore.get(p);
			var background = CharacterStore.background(p.level().registryAccess(), state);
			Progress.award(p, ATHLETICS, Curves.skillXpFor(2) / Progress.multiplier(state, background, ATHLETICS) + 0.5);
			List<DraftState.Card> hand = CharacterStore.get(p).drafts().hand(ATHLETICS);
			if (hand.isEmpty() || !hand.stream().allMatch(DraftState.Card::rolled)) {
				return "Athletics 2 dealt no rolled hand: " + hand;
			}
			if (!CardStore.get(p).cards().isEmpty()) {
				return "cards before keeping one: " + CardStore.get(p).cards();
			}
			return null;
		}));

		// The overlay shows the rolled hand and an empty binder with Athletics' deck of three slots.
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject state = MettleState.build(mc);
			JsonObject card = skill(state, ID).getAsJsonArray("hand").get(0).getAsJsonObject();
			if (!card.has("keywords") || card.getAsJsonArray("keywords").isEmpty() || !card.has("traits") || !card.has("needs")) {
				return "a hand card in the overlay has no rolled parts: " + card;
			}
			JsonObject cards = state.getAsJsonObject("cards");
			if (cards.getAsJsonObject("binder").get("count").getAsInt() != 0) {
				return "binder not empty: " + cards.getAsJsonObject("binder");
			}
			JsonObject deck = deck(cards, ID);
			return deck.get("slots").getAsInt() >= 3 ? null : "Athletics 2 deck slots: " + deck;
		}));

		// Keep (to the binder) from the overlay: a card with an id, in the binder, nothing active yet.
		context.runOnClient(mc -> MettleActions.keepCard(ID, 0, false));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CardsState cards = CardStore.get(p);
			if (cards.cards().size() != 1) {
				return "keeping made " + cards.cards().size() + " cards";
			}
			CardData c = cards.cards().getFirst();
			keptId[0] = c.id();
			keptPerk[0] = c.perk();
			if (c.slotted() || Perks.rank(p, c.perk()) != 0) {
				return "a kept card was slotted or active: " + c;
			}
			if (Cards.ledger(s).get(c.id()).isEmpty() || !c.owner().equals(p.getUUID()) || c.drawn().how() != CardData.How.DRAFT) {
				return "the ledger doesn't hold the kept card as drawn by the player: " + c;
			}
			return null;
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject binder = MettleState.build(mc).getAsJsonObject("cards").getAsJsonObject("binder");
			if (binder.get("count").getAsInt() != 1) {
				return "the overlay binder shows " + binder.get("count");
			}
			JsonObject card = binder.getAsJsonArray("cards").get(0).getAsJsonObject();
			for (String field : new String[] {"id", "code", "name", "rarity", "frame", "keywords", "traits", "needs", "place", "canSlot", "drawn", "skill"}) {
				if (!card.has(field)) {
					return "a binder card has no " + field + ": " + card;
				}
			}
			return "binder".equals(card.get("place").getAsString()) ? null : "the kept card is " + card.get("place");
		}));

		// Slot it from the overlay (its rarity may need a higher Athletics: raise it first so any root fits).
		server.runCommand("mettle xp mettle:athletics 400 @a");
		context.runOnClient(mc -> MettleActions.slotCard(String.valueOf(keptId[0])));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CardData c = CardStore.get(p).card(keptId[0]).orElseThrow();
			if (!c.slotted()) {
				return "the card was not slotted: " + c + " (Athletics " + CharacterStore.get(p).level(ATHLETICS) + ")";
			}
			return Perks.rank(p, keptPerk[0]) == c.rank() ? null : "slotted " + keptPerk[0].identifier() + " is not active at rank " + c.rank();
		}));

		// An attribute trait: a Defense card with Stalwart (+1.5 armour, boosted by Vitality) adds armour.
		double[] armour = new double[2];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (CharacterStore.get(p).level(DEFENSE) < 2) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:defense 120 @a");
			}
			armour[0] = p.getAttributeValue(Attributes.ARMOR);
			Identifier stalwart = Identifier.fromNamespaceAndPath("mettle", "defense/stalwart");
			CardData card = Cards.giveExact(p, new DraftState.Card(Perks.key(Identifier.fromNamespaceAndPath("mettle", "defense/thick_skin")), 1,
				DraftState.Rarity.COMMON, false, List.of(Identifier.fromNamespaceAndPath("mettle", "iron")), List.of(new CardData.TraitRoll(stalwart, 1.5)), 2));
			var no = Cards.slot(p, card.id(), -1);
			if (no.isPresent()) {
				return "could not slot the Stalwart card: " + no.get();
			}
			Trait def = p.level().registryAccess().lookupOrThrow(dev.voidmc.mettle.api.MettleRegistries.TRAIT).getValue(stalwart);
			double want = CardRules.effective(def, 1.5, Cards.attributes(p.level().registryAccess(), CharacterStore.get(p)));
			armour[1] = p.getAttributeValue(Attributes.ARMOR);
			// Thick Skin I adds its own armour; the trait's modifier is the check.
			var modifier = p.getAttribute(Attributes.ARMOR).getModifier(Identifier.fromNamespaceAndPath("mettle", "trait/defense/stalwart"));
			if (modifier == null || Math.abs(modifier.amount() - want) > 1e-6) {
				return "Stalwart modifier " + modifier + ", expected " + want;
			}
			return armour[1] > armour[0] ? null : "armour " + armour[1] + " after Stalwart, was " + armour[0];
		}));

		// A behaviour trait: a Mining card with Rockbreaker 10 % digs stone faster.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (CharacterStore.get(p).level(MiningSkill.MINING) < 2) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:mining 120 @a");
			}
			ItemStack held = p.getMainHandItem().copy();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			float before = p.getDestroySpeed(Blocks.STONE.defaultBlockState());
			Identifier rock = Identifier.fromNamespaceAndPath("mettle", "mining/rockbreaker");
			CardData card = Cards.giveExact(p, new DraftState.Card(Perks.key(MiningSkill.PROSPECTOR), 1, DraftState.Rarity.COMMON, false,
				List.of(Identifier.fromNamespaceAndPath("mettle", "hoard")), List.of(new CardData.TraitRoll(rock, 10)), 2));
			var no = Cards.slot(p, card.id(), -1);
			float after = p.getDestroySpeed(Blocks.STONE.defaultBlockState());
			p.setItemInHand(InteractionHand.MAIN_HAND, held);
			if (no.isPresent()) {
				return "could not slot the Rockbreaker card: " + no.get();
			}
			double total = CardStore.get(p).trait(rock) / 100.0;
			return Math.abs(after - before * (1 + total)) < 0.01 ? null : "stone speed " + after + ", expected " + before + " × " + (1 + total);
		}));

		// A set: two Swift cards (Runner and Steady Pace) make Swift 2, +3 % movement speed.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (CharacterStore.get(p).level(ATHLETICS) < 6) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:athletics 900 @a");
			}
			for (String perk : new String[] {"athletics/runner", "athletics/steady_pace"}) {
				CardData c = Cards.giveExact(p, new DraftState.Card(Perks.key(Identifier.fromNamespaceAndPath("mettle", perk)), 1, DraftState.Rarity.COMMON, false,
					List.of(Identifier.fromNamespaceAndPath("mettle", "swift")), List.of(), 6));
				if (CardStore.get(p).deck(ATHLETICS).stream().anyMatch(x -> x.perk().equals(c.perk()))) {
					continue;
				}
				var no = Cards.slot(p, c.id(), -1);
				if (no.isPresent()) {
					return "could not slot " + perk + ": " + no.get() + " deck " + CardStore.get(p).deck(ATHLETICS);
				}
			}
			Identifier swift = Identifier.fromNamespaceAndPath("mettle", "swift");
			if (CardStore.get(p).tier(swift) < 2) {
				return "Swift set not active: " + CardStore.get(p).sets() + " active " + CardStore.get(p).tiers();
			}
			var m = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Identifier.fromNamespaceAndPath("mettle", "set/swift/2"));
			return m != null && Math.abs(m.amount() - 0.03) < 1e-9 ? null : "Swift 2 modifier " + m;
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject cards = MettleState.build(mc).getAsJsonObject("cards");
			for (JsonElement e : cards.getAsJsonArray("sets")) {
				JsonObject set = e.getAsJsonObject();
				if (set.get("id").getAsString().equals("mettle:swift")) {
					return set.get("active").getAsBoolean() && set.get("count").getAsInt() >= 2 ? null : "overlay Swift set: " + set;
				}
			}
			return "no Swift in the overlay's sets";
		}));
		context.runOnClient(mc -> mc.player.connection.sendCommand("mettle cards"));
		context.waitTicks(10);
		context.takeScreenshot("mettle_cards_binder_chat");

		// A free swap takes a card out; with none left, unslotting is refused (no trainer or shrine here).
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.setFreeSwaps(p, 1);
			CardsState before = CardStore.get(p);
			CardData runner = before.deck(ATHLETICS).stream().filter(c -> c.perk().identifier().getPath().equals("athletics/runner")).findFirst().orElseThrow();
			boolean lowLevel = CharacterStore.get(p).characterLevel() < 5;
			var no = Cards.unslot(p, List.of(runner.id()), false);
			if (no.isPresent()) {
				return "a free swap was refused: " + no.get();
			}
			if (CardStore.get(p).card(runner.id()).orElseThrow().slotted()) {
				return "the swapped card is still slotted";
			}
			if (!lowLevel && !before.freeRebuilds().contains(ATHLETICS) && CardStore.get(p).freeSwaps() != 0) {
				return "the free swap was not used up: " + CardStore.get(p).freeSwaps();
			}
			if (p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Identifier.fromNamespaceAndPath("mettle", "set/swift/2")) != null) {
				return "Swift 2 stayed after a Swift card came out";
			}
			if (lowLevel) {
				return null;
			}
			Cards.setFreeSwaps(p, 0);
			CardStore.set(p, CardStore.get(p).withFreeRebuilds(List.of()));
			CardData any = CardStore.get(p).slotted().getFirst();
			return Cards.unslot(p, List.of(any.id()), false).isPresent() ? null : "unslotting with no free way was not refused";
		}));

		// Dormancy: Wits below 8 takes the Wits slot away; its card goes dormant and its perk stops.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			Map<dev.voidmc.mettle.api.CharacterAttribute, Integer> spent = new HashMap<>(state.spent());
			int witsBase = state.attribute(dev.voidmc.mettle.api.CharacterAttribute.WITS, CharacterStore.background(p.level().registryAccess(), state))
				- spent.getOrDefault(dev.voidmc.mettle.api.CharacterAttribute.WITS, 0);
			if (witsBase >= 8) {
				return null;
			}
			spent.put(dev.voidmc.mettle.api.CharacterAttribute.WITS, 8 - witsBase);
			CharacterStore.set(p, state.withSpent(spent));
			EffectsApplier.apply(p);
			int level = CharacterStore.get(p).level(ATHLETICS);
			int slots = CardRules.slots(level, 8);
			// Fill the deck up to its Wits slot.
			String[] fill = {"athletics/pack_mule", "athletics/strong_back", "athletics/swimmer", "athletics/soft_landing", "athletics/steady_pace", "athletics/runner",
				"athletics/beast_of_burden", "athletics/haulers_stride", "athletics/vaulter", "athletics/spring"};
			for (String perk : fill) {
				if (CardStore.get(p).deck(ATHLETICS).size() >= slots) {
					break;
				}
				CardData c = Cards.giveExact(p, new DraftState.Card(Perks.key(Identifier.fromNamespaceAndPath("mettle", perk)), 1, DraftState.Rarity.COMMON, false,
					List.of(Identifier.fromNamespaceAndPath("mettle", "hoard")), List.of(), 2));
				Cards.slot(p, c.id(), -1);
			}
			CardData last = CardStore.get(p).deck(ATHLETICS).getLast();
			if (last.place().index() != slots - 1) {
				return "the deck did not fill to its Wits slot: " + CardStore.get(p).deck(ATHLETICS).size() + " of " + slots;
			}
			spent.put(dev.voidmc.mettle.api.CharacterAttribute.WITS, 7 - witsBase);
			CharacterStore.set(p, CharacterStore.get(p).withSpent(spent));
			EffectsApplier.apply(p);
			if (!CardStore.get(p).isDormant(last.id())) {
				return "the card in the lost Wits slot is not dormant: " + CardStore.get(p).dormant();
			}
			return Perks.rank(p, last.perk()) == 0 || CharacterStore.get(p).rank(last.perk()) > 0 ? null : "a dormant card's perk is still active";
		}));

		// An old v1 save becomes cards: Mining 9 with Quarryman III, Prospector I, Rock Hauler and Tunneler.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			CharacterState now = CharacterStore.get(p);
			Map<ResourceKey<Perk>, Integer> perks = new HashMap<>(now.perks());
			perks.keySet().removeIf(k -> Perks.registry(p).getOptional(k).map(x -> x.kind().drafted()).orElse(false));
			perks.put(Perks.key(MiningSkill.QUARRYMAN), 3);
			perks.put(Perks.key(MiningSkill.PROSPECTOR), 1);
			perks.put(Perks.key(MiningSkill.ROCK_HAULER), 1);
			perks.put(Perks.key(MiningSkill.TUNNELER), 1);
			DraftState d = now.drafts().withQueued(MiningSkill.MINING, 1);
			CharacterStore.set(p, now.withXp(MiningSkill.MINING, Curves.skillXpFor(9)).withPerks(perks).withDraft(d));
			CardStore.set(p, CardStore.get(p).withVersion(0).withFreeSwaps(0));
			Cards.ensure(p);
			CharacterState after = CharacterStore.get(p);
			CardsState cards = CardStore.get(p);
			if (cards.version() != 1 || cards.cards().size() != 4) {
				return "migration made " + cards.cards().size() + " cards (version " + cards.version() + "): " + cards.cards();
			}
			if (cards.cards().stream().anyMatch(c -> !c.slotted() || c.drawn().how() != CardData.How.MIGRATED)) {
				return "Mining 9 has 5 slots: all four migrated cards should be slotted: " + cards.cards();
			}
			if (after.drafts().queued(MiningSkill.MINING) != 3) {
				return "drafts after migration " + after.drafts().queued(MiningSkill.MINING) + ", expected 1 + 2 from Quarryman's ranks";
			}
			if (after.rank(Perks.key(MiningSkill.QUARRYMAN)) != 0 || Perks.rank(p, MiningSkill.QUARRYMAN) != 3) {
				return "Quarryman III should now come from an Epic card: record " + after.rank(Perks.key(MiningSkill.QUARRYMAN)) + ", active "
					+ Perks.rank(p, MiningSkill.QUARRYMAN);
			}
			if (cards.freeSwaps() != 3 || !cards.freeRebuilds().contains(MiningSkill.MINING)) {
				return "migration gives three free swaps and a free first rebuild: " + cards.freeSwaps() + " " + cards.freeRebuilds();
			}
			return null;
		}));

		// Put everything back: no cards, the character as it was.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			CharacterStore.set(p, saved[0]);
			CardStore.set(p, CardStore.get(p).withFreeSwaps(0).withPins(List.of(), -1));
			EffectsApplier.apply(p);
			return CardStore.get(p).cards().isEmpty() ? null : "cards left after the journey";
		}));
		context.waitTicks(5);
	}

	private static JsonObject skill(JsonObject state, String id) {
		for (var e : state.getAsJsonArray("skills")) {
			if (e.getAsJsonObject().get("id").getAsString().equals(id)) {
				return e.getAsJsonObject();
			}
		}
		throw new AssertionError("no skill " + id + " in the overlay state");
	}

	private static JsonObject deck(JsonObject cards, String id) {
		JsonArray decks = cards.getAsJsonArray("decks");
		for (var e : decks) {
			if (e.getAsJsonObject().get("skill").getAsString().equals(id)) {
				return e.getAsJsonObject();
			}
		}
		throw new AssertionError("no deck " + id + " in the overlay state");
	}

	@SuppressWarnings("unchecked")
	private static ResourceKey<Perk>[] perkRef() {
		return (ResourceKey<Perk>[]) new ResourceKey<?>[1];
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
