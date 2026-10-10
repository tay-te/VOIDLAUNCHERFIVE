package dev.voidmc.mettle.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The perk draft end to end (docs/DRAFT.md), as a player meets it: Athletics levels up from real XP and a hand is dealt,
 * the overlay state shows it, a reroll from the overlay's action spends a reroll and deals new cards, keeping a card
 * through the overlay's action learns its perk and deals the next hand, the hand survives a refresh (a reconnect deals
 * nothing new), the old "learn" action is answered with a chip, and an old save's unspent points become drafts. The
 * character is put back as it was.
 */
final class DraftJourney {
	private static final ResourceKey<Skill> ATHLETICS = Progress.skill("athletics");
	private static final String ID = ATHLETICS.identifier().toString();

	private DraftJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		CharacterState[] saved = new CharacterState[1];
		List<DraftState.Card>[] first = cardList();

		// A clean Athletics: no XP, no Athletics perks, no Athletics drafts; two rerolls.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			saved[0] = Drafts.ensure(p);
			Map<ResourceKey<Perk>, Integer> kept = new HashMap<>();
			saved[0].perks().forEach((k, v) -> {
				if (!Perks.registry(p).getOptional(k).map(x -> x.skill().equals(ATHLETICS)).orElse(false)) {
					kept.put(k, v);
				}
			});
			// No recently used skills either, so no wildcard can join the first hand.
			DraftState o = saved[0].drafts().withQueued(ATHLETICS, 0).withHand(ATHLETICS, List.of());
			DraftState d = new DraftState(o.queued(), o.hands(), 2, o.seed(), o.dealt(), List.of());
			CharacterStore.set(p, saved[0].withXp(ATHLETICS, 0).withPerks(kept).withDraft(d));
			EffectsApplier.apply(p);

			// Level 2 from real XP: two drafts (levels 1 and 2), a hand of the two roots.
			CharacterState state = CharacterStore.get(p);
			var background = CharacterStore.background(p.level().registryAccess(), state);
			Progress.award(p, ATHLETICS, Curves.skillXpFor(2) / Progress.multiplier(state, background, ATHLETICS) + 0.5);
			// A character level may have come with it (and a reroll): back to two.
			Drafts.setRerolls(p, 2);
			DraftState after = CharacterStore.get(p).drafts();
			if (CharacterStore.get(p).level(ATHLETICS) != 2) {
				return "Athletics is level " + CharacterStore.get(p).level(ATHLETICS) + ", expected 2";
			}
			if (after.queued(ATHLETICS) != 2) {
				return "Athletics drafts " + after.queued(ATHLETICS) + " after reaching level 2, expected 2";
			}
			List<DraftState.Card> hand = after.hand(ATHLETICS);
			Set<ResourceKey<Perk>> distinct = new HashSet<>();
			hand.forEach(c -> distinct.add(c.perk()));
			if (hand.size() != 2 || distinct.size() != 2) {
				return "expected a hand of the two Athletics roots, got " + Drafts.describe(hand);
			}
			for (DraftState.Card c : hand) {
				if (c.rank() != 1 || c.rarity() != DraftState.Rarity.COMMON || c.wildcard()) {
					return "unexpected card " + Drafts.describe(List.of(c));
				}
			}
			// A reconnect (join refresh) deals nothing new: the stored hand stands.
			Drafts.refresh(p);
			if (!CharacterStore.get(p).drafts().hand(ATHLETICS).equals(hand)) {
				return "a refresh re-dealt the hand";
			}
			first[0] = hand;
			return null;
		}));

		// The overlay sees the hand.
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject skill = skill(MettleState.build(mc), ID);
			JsonArray hand = skill.getAsJsonArray("hand");
			if (skill.get("drafts").getAsInt() != 2 || hand.size() != 2) {
				return "overlay state: " + skill.get("drafts") + " drafts, hand " + hand;
			}
			JsonObject card = hand.get(0).getAsJsonObject();
			for (String field : new String[] {"perk", "name", "description", "rank", "rarity", "frame", "cursed", "wildcard", "skill", "icon", "colour"}) {
				if (!card.has(field)) {
					return "a card in the overlay state has no " + field + ": " + card;
				}
			}
			return MettleState.build(mc).getAsJsonObject("draft").get("rerolls").getAsInt() == 2 ? null : "overlay shows the wrong rerolls";
		}));

		// /mettle draft lists the hand in chat (for players until the overlay ships).
		context.runOnClient(mc -> mc.player.connection.sendCommand("mettle draft"));
		context.waitTicks(10);
		context.takeScreenshot("mettle_draft_chat");

		// A reroll from the overlay: one reroll spent, a fresh hand (only two roots are open, so the same two, re-dealt).
		context.runOnClient(mc -> MettleActions.reroll(ID));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			DraftState d = CharacterStore.get(player(s)).drafts();
			if (d.rerolls() != 1) {
				return "rerolls " + d.rerolls() + " after a reroll, expected 1";
			}
			return d.hand(ATHLETICS).size() == 2 ? null : "the reroll dealt " + Drafts.describe(d.hand(ATHLETICS));
		}));

		// Keep the first card from the overlay: its perk is learned, one draft is left, and its hand is dealt.
		ResourceKey<Perk>[] kept = perkRef();
		check(server.computeOnServer(s -> {
			kept[0] = CharacterStore.get(player(s)).drafts().hand(ATHLETICS).getFirst().perk();
			return null;
		}));
		context.runOnClient(mc -> MettleActions.pickCard(ID, 0));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			if (state.rank(kept[0]) != 1) {
				return "keeping " + kept[0].identifier() + " did not learn it: " + state.perks();
			}
			DraftState d = state.drafts();
			if (d.queued(ATHLETICS) != 1 || d.hand(ATHLETICS).isEmpty()) {
				return "after keeping a card: " + d.queued(ATHLETICS) + " drafts, hand " + Drafts.describe(d.hand(ATHLETICS));
			}
			for (DraftState.Card c : d.hand(ATHLETICS)) {
				if (c.perk().equals(kept[0]) && c.rank() != 2) {
					return "the kept root came back at the wrong rank: " + Drafts.describe(List.of(c));
				}
			}
			// The one-string form a page bridge uses, and a refusal for a card that is not there.
			Optional<Drafts.Refusal> none = Drafts.pick(p, ATHLETICS, 5);
			return none.equals(Optional.of(Drafts.Refusal.NO_CARD)) ? null : "picking card 6 gave " + none;
		}));
		context.runOnClient(mc -> MettleActions.pickCard(ID + "#0"));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			DraftState d = CharacterStore.get(player(s)).drafts();
			if (d.queued(ATHLETICS) != 0 || !d.hand(ATHLETICS).isEmpty()) {
				return "the last draft was not taken: " + d.queued(ATHLETICS) + " " + Drafts.describe(d.hand(ATHLETICS));
			}
			return Drafts.reroll(player(s), ATHLETICS).equals(Optional.of(Drafts.Refusal.NO_DRAFT)) ? null : "a reroll with no draft was not refused";
		}));

		// The old "learn a perk" action is answered, not obeyed.
		context.runOnClient(mc -> MettleActions.learnPerk("mettle:athletics/runner"));
		check(context.computeOnClient(mc -> MettleState.build(mc).getAsJsonArray("feedback").toString().contains("Perks come from drafts now")
			? null : "no chip for the old learn action"));

		// An old save, from before drafts: Mining 9 with Quarryman III and Tunneler (5 points) leaves 4 points → 4 drafts.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState now = CharacterStore.get(p);
			Map<ResourceKey<Perk>, Integer> perks = new HashMap<>();
			perks.put(Perks.key(MiningSkill.QUARRYMAN), 3);
			perks.put(Perks.key(MiningSkill.TUNNELER), 1);
			Map<ResourceKey<Skill>, Double> xp = new HashMap<>();
			xp.put(MiningSkill.MINING, Curves.skillXpFor(9));
			CharacterState legacy = new CharacterState(now.background(), now.spent(), now.favoured(), xp, perks);
			CharacterStore.set(p, legacy);
			Drafts.refresh(p);
			DraftState d = CharacterStore.get(p).drafts();
			if (d.queued(MiningSkill.MINING) != 4) {
				return "an old save's 4 unspent Mining points became " + d.queued(MiningSkill.MINING) + " drafts";
			}
			if (d.rerolls() != DraftRules.START_REROLLS || d.hand(MiningSkill.MINING).isEmpty()) {
				return "migration: rerolls " + d.rerolls() + ", hand " + Drafts.describe(d.hand(MiningSkill.MINING));
			}
			return CharacterStore.get(p).rank(Perks.key(MiningSkill.QUARRYMAN)) == 3 ? null : "migration lost a learned perk";
		}));

		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterStore.set(p, saved[0]);
			EffectsApplier.apply(p);
			return null;
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

	@SuppressWarnings("unchecked")
	private static List<DraftState.Card>[] cardList() {
		return (List<DraftState.Card>[]) new List<?>[1];
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
