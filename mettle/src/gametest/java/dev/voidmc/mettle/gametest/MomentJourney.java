package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.moment.MomentKind;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.client.moment.MomentClient;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Progress moments (UI.md §6b), on a fresh character: real Mining XP up to level 2 (which opens Tier I and deals a
 * draft) and Quarryman kept from the hand reach the client as moments, queue as toasts at the top of the screen (screenshots {@code mettle_moment_toast}
 * and {@code mettle_moment_toast_learned}) and wake the points nudge by the hotbar. The character is put back exactly
 * as it was afterwards, so the journeys after it start from the same fresh Ranger.
 */
final class MomentJourney {
	private static final String TIER = "tier mettle:mining";
	private static final String LEARNED = "learned mettle:mining/quarryman";

	private MomentJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		context.setScreen(() -> null);
		// Let anything still in flight land first, then start clean.
		context.waitTicks(10);
		context.runOnClient(mc -> MomentClient.resetForTest());

		CharacterState[] saved = new CharacterState[1];
		check(world.getServer().computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			CharacterState state = CharacterStore.get(p);
			saved[0] = state;
			Background background = CharacterStore.background(p.level().registryAccess(), state);
			ResourceKey<Skill> mining = MiningSkill.MINING;
			if (state.level(mining) >= Curves.tierSkillGate(1)) {
				return "expected a fresh character, Mining is already " + state.level(mining);
			}
			double need = Curves.skillXpFor(Curves.tierSkillGate(1)) - state.xp(mining);
			Progress.award(p, mining, need / Progress.multiplier(state, background, mining) + 0.5);
			if (CharacterStore.get(p).level(mining) != Curves.tierSkillGate(1)) {
				return "Mining reached " + CharacterStore.get(p).level(mining) + ", expected " + Curves.tierSkillGate(1);
			}
			// Tier I deals a draft; Quarryman is kept from it.
			var hand = CharacterStore.get(p).drafts().hand(mining);
			int index = -1;
			for (int i = 0; i < hand.size(); i++) {
				if (hand.get(i).perk().equals(Perks.key(MiningSkill.QUARRYMAN))) {
					index = i;
				}
			}
			if (index < 0) {
				return "no Quarryman card in the Mining hand: " + dev.voidmc.mettle.character.draft.Drafts.describe(hand);
			}
			return dev.voidmc.mettle.character.draft.Drafts.pick(p, mining, index).map(r -> "Quarryman not drafted: " + r).orElse(null);
		}));

		// The tier toast comes first (with the points nudge by the hotbar).
		context.waitFor(mc -> TIER.equals(MomentClient.showingKey()), 100);
		context.waitTicks(8);
		context.takeScreenshot("mettle_moment_toast");
		check(context.computeOnClient(mc -> {
			List<String> got = MomentClient.received();
			if (!got.contains("tier mettle:level/mining") || !got.contains(LEARNED)) {
				return "moments received: " + got + ", expected the Mining tier and Quarryman";
			}
			Set<MomentKind> seen = MomentClient.seenKinds();
			if (!seen.contains(MomentKind.TIER) || !seen.contains(MomentKind.LEARNED)) {
				return "the toast queue saw " + seen + ", expected TIER and LEARNED";
			}
			if (MomentClient.toastsDrawn() == 0) {
				return "no toast was drawn";
			}
			if (!MomentClient.nudgeShowing()) {
				return "the nudge is not showing after a level-up (" + MomentClient.unspentPoints(mc.player) + " waiting)";
			}
			return null;
		}));

		// Then the perk's own toast, once the tier one (and a character level, if one came) gives way.
		context.waitFor(mc -> LEARNED.equals(MomentClient.showingKey()), 300);
		context.waitTicks(8);
		context.takeScreenshot("mettle_moment_toast_learned");

		check(world.getServer().computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			CharacterStore.set(p, saved[0]);
			// The Quarryman card kept here leaves the world, so later journeys start without cards.
			dev.voidmc.mettle.character.cards.Cards.clear(p);
			EffectsApplier.apply(p);
			return null;
		}));
		context.waitTicks(5);
		context.runOnClient(mc -> MomentClient.resetForTest());
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
