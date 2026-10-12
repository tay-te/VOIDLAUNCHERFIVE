package dev.voidmc.mettle.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.cards.CardBlocks;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.Rebuilds;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.character.respec.DraughtItem;
import dev.voidmc.mettle.character.respec.Draughts;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rebuilding at a Card Shrine (docs/CARDS.md §7, wave B): taking a card out is refused without a free way; the shrine opens
 * a sitting; the overlay shows fees and the scar each card would take; without a Draught of Forgetting the rebuild is
 * refused; with one (and emeralds, no Hearth here) the card comes out scarred exactly as previewed, the draught is spent
 * once for the sitting; three binder cards release for a reroll; walking away ends the sitting; a set of six Iron cards
 * from two decks makes Ironskin take 3 off a hit. Everything is put back.
 */
final class ShrineJourney {
	private ShrineJourney() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("mettle", path);
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		// The shrine's fees in emeralds, as without Hearth, whatever is loaded: with Hearth (-Phearth) they would be coin,
		// which HearthJourney's trainer rebuild checks. Put back afterwards.
		var server = world.getServer();
		dev.voidmc.mettle.character.cards.CardFees[] fees = new dev.voidmc.mettle.character.cards.CardFees[1];
		server.runOnServer(s -> {
			fees[0] = Rebuilds.fees();
			Rebuilds.setFees(dev.voidmc.mettle.character.cards.CardFees.EMERALDS);
		});
		try {
			play(context, world);
		} finally {
			server.runOnServer(s -> Rebuilds.setFees(fees[0]));
		}
	}

	private static void play(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		CharacterState[] saved = new CharacterState[1];
		BlockPos[] shrine = new BlockPos[1];
		net.minecraft.world.level.block.state.BlockState[] was = new net.minecraft.world.level.block.state.BlockState[1];
		long[] tunneler = new long[1];
		CardData.TraitRoll[] previewed = new CardData.TraitRoll[1];

		// A Mining deck of three, nothing free left (character level 5+, no swaps, no free rebuild).
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			saved[0] = Cards.ensure(p);
			Cards.clear(p);
			if (CharacterStore.get(p).level(MiningSkill.MINING) < 9) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:mining 4000 @a");
			}
			if (CharacterStore.get(p).characterLevel() < 5) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:survival 2500 @a");
			}
			CardStore.set(p, CardStore.get(p).withFreeSwaps(0).withFreeRebuilds(List.of()));
			CardData t = Cards.giveExact(p, new DraftState.Card(Perks.key(MiningSkill.TUNNELER), 1, DraftState.Rarity.EPIC, false,
				List.of(id("echo"), id("iron")), List.of(new CardData.TraitRoll(id("mining/deepborn"), 9), new CardData.TraitRoll(id("mining/echoing"), 8),
					new CardData.TraitRoll(id("mining/lucky"), 5)), 9));
			CardData q = Cards.giveExact(p, new DraftState.Card(Perks.key(MiningSkill.QUARRYMAN), 1, DraftState.Rarity.COMMON, false,
				List.of(id("brutal")), List.of(), 2));
			CardData h = Cards.giveExact(p, new DraftState.Card(Perks.key(MiningSkill.HARD_HAT), 1, DraftState.Rarity.RARE, false,
				List.of(id("iron")), List.of(new CardData.TraitRoll(id("mining/stonehide"), 15)), 7));
			for (CardData c : List.of(t, q, h)) {
				var no = Cards.slot(p, c.id(), -1);
				if (no.isPresent()) {
					return "could not slot " + c.perk().identifier() + ": " + no.get();
				}
			}
			tunneler[0] = t.id();
			var refused = Cards.unslot(p, List.of(t.id()), false);
			if (refused.isEmpty() || refused.get() != CardRules.Refusal.NO_FREE_WAY) {
				return "unslotting with no free way and no shrine gave " + refused;
			}
			// The draught is a key now: drunk away from a shrine it is refused, and says where to take it.
			ItemStack draught = Draughts.forgetting(id("mining"));
			if (((DraughtItem) Draughts.FORGETTING).refusal(p, draught).isEmpty()) {
				return "a Draught of Forgetting could be drunk with no perks outside the deck";
			}
			// A shrine two blocks ahead, used as a player would.
			shrine[0] = p.blockPosition().relative(p.getDirection(), 2);
			was[0] = s.overworld().getBlockState(shrine[0]);
			s.overworld().setBlockAndUpdate(shrine[0], CardBlocks.CARD_SHRINE.defaultBlockState());
			s.overworld().getBlockState(shrine[0]).useWithoutItem(s.overworld(), p,
				new BlockHitResult(Vec3.atCenterOf(shrine[0]), Direction.UP, shrine[0], false));
			return Rebuilds.atShrine(p) ? null : "using the Card Shrine opened no sitting";
		}));

		// The overlay knows it is at a shrine, and shows each slotted card's fee and the scar it would take.
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject cards = MettleState.build(mc).getAsJsonObject("cards");
			if (!cards.get("atShrine").getAsBoolean() || cards.get("sitting").isJsonNull()) {
				return "overlay: atShrine " + cards.get("atShrine") + ", sitting " + cards.get("sitting");
			}
			for (JsonElement e : cards.getAsJsonObject("binder").getAsJsonArray("cards")) {
				JsonObject c = e.getAsJsonObject();
				if (c.get("id").getAsString().equals(String.valueOf(tunneler[0]))) {
					JsonObject rb = c.getAsJsonObject("rebuild");
					if (rb == null || rb.get("emeralds").getAsInt() != 3 || rb.get("loses").isJsonNull()) {
						return "Tunneler's rebuild preview: " + rb;
					}
					return null;
				}
			}
			return "no Tunneler in the overlay binder";
		}));
		context.waitTicks(10);
		context.takeScreenshot("mettle_cards_shrine_sitting");

		// No draught: refused. With one (and emeralds), from the overlay: out, scarred as previewed, draught spent once.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clear(p);
			var no = Rebuilds.rebuild(p, List.of(tunneler[0]));
			if (no.isEmpty() || no.get() != CardRules.Refusal.NO_DRAUGHT) {
				return "a rebuild with no draught gave " + no;
			}
			p.getInventory().add(Draughts.forgetting(id("mining")));
			p.getInventory().add(new ItemStack(Items.EMERALD, 2));
			no = Rebuilds.rebuild(p, List.of(tunneler[0]));
			if (no.isEmpty() || no.get() != CardRules.Refusal.CANT_PAY) {
				return "a rebuild with 2 emeralds for a 3-emerald card gave " + no;
			}
			p.getInventory().add(new ItemStack(Items.EMERALD, 18));
			CardData before = CardStore.get(p).card(tunneler[0]).orElseThrow();
			previewed[0] = CardRules.scar(before, false).loses().orElseThrow();
			return null;
		}));
		context.runOnClient(mc -> MettleActions.unslotCards(String.valueOf(tunneler[0])));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CardData after = CardStore.get(p).card(tunneler[0]).orElseThrow();
			if (after.slotted() || after.scars() != 1 || after.traits().contains(previewed[0]) || after.traits().size() != 2) {
				return "after the rebuild: " + after + " (previewed loss " + previewed[0] + ")";
			}
			int emeralds = p.getInventory().countItem(Items.EMERALD);
			if (emeralds != 17) {
				return "emeralds after a 3-emerald fee: " + emeralds;
			}
			if (p.getInventory().countItem(Draughts.FORGETTING) != 0 || p.getInventory().countItem(Items.GLASS_BOTTLE) != 1) {
				return "the draught was not spent (bottle back)";
			}
			// The same sitting takes another Mining card without a second draught.
			CardData quarry = CardStore.get(p).deck(MiningSkill.MINING).stream().filter(c -> c.perk().identifier().equals(MiningSkill.QUARRYMAN)).findFirst().orElseThrow();
			var no = Rebuilds.rebuild(p, List.of(quarry.id()));
			if (no.isPresent()) {
				return "a second card in the same sitting: " + no.get();
			}
			return p.getInventory().countItem(Items.EMERALD) == 16 ? null : "a 2-requirement card should cost 1 emerald";
		}));

		// Release three binder Mining cards for a reroll.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Drafts.setRerolls(p, 2);
			CardData extra = Cards.giveExact(p, new DraftState.Card(Perks.key(MiningSkill.PROSPECTOR), 1, DraftState.Rarity.COMMON, false,
				List.of(id("hoard")), List.of(), 2));
			List<Long> three = CardStore.get(p).cards().stream().filter(c -> !c.slotted() && c.skill().equals(MiningSkill.MINING)).map(CardData::id).toList();
			if (three.size() != 3) {
				return "expected three binder Mining cards, got " + three.size();
			}
			var no = Rebuilds.release(p, three);
			if (no.isPresent()) {
				return "release refused: " + no.get();
			}
			if (CharacterStore.get(p).drafts().rerolls() != 3 || CardStore.get(p).card(extra.id()).isPresent()
				|| Cards.ledger(s).get(extra.id()).orElseThrow().place().kind() != CardData.PlaceKind.RELEASED) {
				return "after release: rerolls " + CharacterStore.get(p).drafts().rerolls() + ", cards " + CardStore.get(p).cards().size();
			}
			// Fusion (CARDS.md §18.1): three Common Tunnelers become one Rare, for its rebuild fee (3 emeralds).
			List<Long> trio = new java.util.ArrayList<>();
			for (int i = 0; i < 3; i++) {
				trio.add(Cards.giveExact(p, new DraftState.Card(Perks.key(MiningSkill.TUNNELER), 1, DraftState.Rarity.COMMON, false, List.of(id("echo")),
					List.of(new CardData.TraitRoll(id("mining/lucky"), 3 + i)), 6)).id());
			}
			int emeraldsBefore = p.getInventory().countItem(Items.EMERALD);
			var fused = Rebuilds.fuse(p, trio);
			if (fused.isPresent()) {
				return "fusing three Tunnelers was refused: " + fused.get();
			}
			CardData rare = CardStore.get(p).cards().stream().filter(c -> c.drawn().how() == CardData.How.FUSED).findFirst().orElse(null);
			if (rare == null || rare.rarity() != DraftState.Rarity.RARE || trio.stream().anyMatch(t -> CardStore.get(p).card(t).isPresent())
				|| !rare.traits().contains(new CardData.TraitRoll(id("mining/lucky"), 5)) || p.getInventory().countItem(Items.EMERALD) != emeraldsBefore - 3) {
				return "after fusing: " + rare + ", emeralds " + p.getInventory().countItem(Items.EMERALD) + " of " + emeraldsBefore;
			}
			// Walking away ends the sitting.
			double x = p.getX();
			p.teleportTo(x + 20, p.getY(), p.getZ());
			boolean closed = Rebuilds.sitting(p).isEmpty();
			// Back onto the ground it stood on (20 blocks out may be open air: a fall would end the journeys after this).
			p.teleportTo(x, p.getY(), p.getZ());
			p.fallDistance = 0;
			return closed ? null : "the sitting stayed open 20 blocks away";
		}));

		// Ironskin (Iron 6 from two decks): the first hit every 8 s is 3 lighter.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			if (CharacterStore.get(p).level(dev.voidmc.mettle.character.Progress.skill("defense")) < 8) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:defense 4000 @a");
			}
			String[][] iron = {{"mining/hard_hat"}, {"mining/tunneler"}, {"mining/quarryman"}, {"defense/thick_skin"}, {"defense/armour_training"}, {"defense/mender"}};
			for (String[] perk : iron) {
				CardData c = Cards.giveExact(p, new DraftState.Card(Perks.key(id(perk[0])), 1, DraftState.Rarity.COMMON, false, List.of(id("iron")), List.of(), 2));
				var no = Cards.slot(p, c.id(), -1);
				if (no.isPresent()) {
					return "could not slot " + perk[0] + ": " + no.get();
				}
			}
			if (CardStore.get(p).tier(id("iron")) != 6) {
				return "Iron set tier " + CardStore.get(p).tier(id("iron")) + " from six cards in two decks";
			}
			p.setHealth(p.getMaxHealth());
			float before = p.getHealth();
			p.hurtServer(s.overworld(), p.damageSources().magic(), 5f);
			float taken = before - p.getHealth();
			return Math.abs(taken - 2f) < 0.01f ? null : "Ironskin: a 5 magic hit took " + taken + ", expected 2";
		}));

		// Put everything back.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			Rebuilds.close(p);
			s.overworld().setBlockAndUpdate(shrine[0], was[0]);
			CharacterStore.set(p, saved[0]);
			CardStore.set(p, CardStore.get(p).withFreeSwaps(0));
			clear(p);
			EffectsApplier.apply(p);
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		context.waitTicks(5);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}

	/** Takes this journey's items (draughts, emeralds, bottles) out of the inventory. */
	private static void clear(ServerPlayer p) {
		p.getInventory().clearOrCountMatchingItems(st -> st.is(Draughts.FORGETTING) || st.is(Items.EMERALD) || st.is(Items.GLASS_BOTTLE), false, -1,
			p.inventoryMenu.getCraftSlots());
	}
}
