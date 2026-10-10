package dev.voidmc.mettle.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.CardConfig;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.character.skill.MiningSkill;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mythics (docs/CARDS.md §9, wave D): a drawn Mythic offered by a Blades draft (the roll forced for the test) and kept,
 * claimed once in the world; slotted in another skill's deck and working; the limit of two slotted; the Lone Banner's
 * effect; a world first from a boss killed for real (only once); the Hall of Firsts in the overlay. Put back after.
 */
final class MythicJourney {
	private static final Identifier TWIN = Identifier.fromNamespaceAndPath("mettle", "twin_edge");
	private static final Identifier EYE = Identifier.fromNamespaceAndPath("mettle", "elders_eye");

	private MythicJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		CharacterState[] saved = new CharacterState[1];

		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			saved[0] = Cards.ensure(p);
			Cards.clear(p);
			Cards.ledger(s).unclaim("twin_edge");
			Cards.ledger(s).unclaim("elders_eye");
			var blades = Progress.skill("blades");
			if (CharacterStore.get(p).level(blades) < 16) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:blades 20000 @a");
			}
			// Force the 0.5 % roll for the test, then deal a Blades draft: its third card is The Twin Edge.
			CardConfig config = CardConfig.get();
			double chance = config.mythic_draw_chance;
			config.mythic_draw_chance = 1.0;
			try {
				CharacterState st = CharacterStore.get(p);
				CharacterStore.set(p, st.withDraft(st.drafts().withQueued(blades, 0).withHand(blades, List.of())));
				Drafts.give(p, blades, 1);
			} finally {
				config.mythic_draw_chance = chance;
			}
			List<DraftState.Card> hand = CharacterStore.get(p).drafts().hand(blades);
			int at = -1;
			for (int i = 0; i < hand.size(); i++) {
				if (hand.get(i).rarity() == DraftState.Rarity.MYTHIC) {
					at = i;
				}
			}
			if (at < 0) {
				return "no Mythic offer in the Blades hand: " + Drafts.describe(hand);
			}
			// A reroll keeps the Mythic offer.
			Drafts.setRerolls(p, 1);
			Drafts.reroll(p, blades);
			hand = CharacterStore.get(p).drafts().hand(blades);
			if (hand.stream().noneMatch(c -> c.rarity() == DraftState.Rarity.MYTHIC)) {
				return "a reroll lost the Mythic offer";
			}
			at = hand.indexOf(hand.stream().filter(c -> c.rarity() == DraftState.Rarity.MYTHIC).findFirst().orElseThrow());
			var kept = Drafts.keep(p, blades, at, Cards.Keep.BINDER);
			if (kept.isPresent()) {
				return "keeping the Mythic was refused: " + kept.get();
			}
			CardData twin = CardStore.get(p).cards().stream().filter(c -> c.mythicId().map(TWIN::equals).orElse(false)).findFirst().orElse(null);
			if (twin == null || Cards.ledger(s).first("twin_edge").isEmpty() || Mythics.drawable(s, TWIN)) {
				return "The Twin Edge was not claimed: " + CardStore.get(p).cards();
			}
			// It fits any deck: slot it in Mining.
			if (CharacterStore.get(p).level(MiningSkill.MINING) < 2) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:mining 200 @a");
			}
			var slotted = Cards.slot(p, twin.id(), -1, MiningSkill.MINING);
			if (slotted.isPresent() || !Mythics.active(p, "twin_edge")) {
				return "The Twin Edge in the Mining deck: " + slotted;
			}
			// Two at most: the Lone Banner goes in Blades, the Unclad is refused.
			CardData banner = Mythics.grant(p, Identifier.fromNamespaceAndPath("mettle", "lone_banner"), true).orElseThrow();
			CardData unclad = Mythics.grant(p, Identifier.fromNamespaceAndPath("mettle", "unclad"), true).orElseThrow();
			if (Cards.slot(p, banner.id(), -1, blades).isPresent()) {
				return "the Lone Banner could not be slotted";
			}
			var third = Cards.slot(p, unclad.id(), -1, Progress.skill("defense"));
			if (third.isEmpty() || third.get() != CardRules.Refusal.TWO_MYTHICS) {
				return "a third Mythic slotted gave " + third;
			}
			// The Lone Banner: alone, 15 % less damage taken.
			p.setHealth(p.getMaxHealth());
			float before = p.getHealth();
			p.hurtServer(s.overworld(), p.damageSources().magic(), 10f);
			float taken = before - p.getHealth();
			if (Math.abs(taken - 8.5f) > 0.01f) {
				return "the Lone Banner: a 10 magic hit took " + taken + ", expected 8.5";
			}
			p.setHealth(p.getMaxHealth());
			// A world first, for real: an Elder Guardian that spawned on its own, killed by the player.
			LivingEntity guardian = EntityTypes.ELDER_GUARDIAN.spawn(s.overworld(), p.blockPosition().offset(3, 0, 0), EntitySpawnReason.NATURAL);
			if (guardian == null) {
				return "no Elder Guardian spawned";
			}
			guardian.hurtServer(s.overworld(), p.damageSources().playerAttack(p), 1000f);
			if (CardStore.get(p).cards().stream().noneMatch(c -> c.mythicId().map(EYE::equals).orElse(false)) || Cards.ledger(s).first("elders_eye").isEmpty()) {
				return "the first Elder Guardian kill gave no Elder's Eye";
			}
			LivingEntity second = EntityTypes.ELDER_GUARDIAN.spawn(s.overworld(), p.blockPosition().offset(3, 0, 0), EntitySpawnReason.NATURAL);
			second.hurtServer(s.overworld(), p.damageSources().playerAttack(p), 1000f);
			long eyes = CardStore.get(p).cards().stream().filter(c -> c.mythicId().map(EYE::equals).orElse(false)).count();
			return eyes == 1 ? null : "a world first was earned twice: " + eyes;
		}));

		// The Hall of Firsts and the reveal in the overlay.
		context.waitTicks(5);
		check(context.computeOnClient(mc -> {
			JsonObject cards = MettleState.build(mc).getAsJsonObject("cards");
			JsonObject mythics = cards.getAsJsonObject("mythics");
			boolean eye = false;
			for (JsonElement e : mythics.getAsJsonArray("all")) {
				JsonObject m = e.getAsJsonObject();
				if (m.get("id").getAsString().equals(EYE.toString())) {
					eye = m.get("claimed").getAsBoolean() && !m.get("holder").getAsString().isEmpty();
				}
			}
			if (!eye) {
				return "the Hall of Firsts doesn't show the Elder's Eye's holder: " + mythics;
			}
			if (mythics.get("drawnWaiting").getAsInt() != 7) {
				return "drawn Mythics still waiting: " + mythics.get("drawnWaiting") + ", expected 7";
			}
			return cards.get("reveal").isJsonNull() ? "no reveal" : null;
		}));
		context.runOnClient(mc -> mc.player.connection.sendCommand("mettle cards"));
		context.waitTicks(10);
		context.takeScreenshot("mettle_cards_mythics");

		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			Cards.ledger(s).unclaim("twin_edge");
			Cards.ledger(s).unclaim("elders_eye");
			CharacterStore.set(p, saved[0]);
			EffectsApplier.apply(p);
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		context.waitTicks(3);
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
