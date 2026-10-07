package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.moment.MomentKind;
import dev.voidmc.mettle.client.moment.MomentClient;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.Comparator;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Progress moments (UI.md §6b): a real skill level-up and a real perk learned on the server reach the client as
 * moments, queue as toasts at the top of the screen (screenshots {@code mettle_moment_toast} and
 * {@code mettle_moment_toast_learned}) and wake the points nudge by the hotbar. The character is put back afterwards.
 */
final class MomentJourney {
	private MomentJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		context.setScreen(() -> null);
		context.runOnClient(mc -> {
			MomentClient.resetForTest();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.waitTicks(2);

		CharacterState[] saved = new CharacterState[1];
		String[] learned = new String[1];
		check(world.getServer().computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			CharacterState state = CharacterStore.get(p);
			saved[0] = state;
			Background background = CharacterStore.background(p.level().registryAccess(), state);
			// The lowest released skill below the cap gets exactly enough XP for its next level.
			var skill = p.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).listElements()
				.filter(r -> r.value().released() && state.level(r.key()) < Curves.SKILL_CAP)
				.min(Comparator.comparingInt(r -> state.level(r.key())));
			if (skill.isEmpty()) {
				return "every skill is at the cap";
			}
			ResourceKey<Skill> key = skill.get().key();
			int before = state.level(key);
			double need = Curves.skillXpFor(before + 1) - state.xp(key);
			Progress.award(p, key, need / Progress.multiplier(state, background, key) + 1);
			if (CharacterStore.get(p).level(key) <= before) {
				return "no level-up in " + key.identifier() + " (still " + before + ")";
			}
			// Then buy the first perk the rules allow, in any tree.
			for (Holder.Reference<Perk> ref : Perks.registry(p).listElements()
				.filter(r -> r.value().kind().bought()).sorted(Comparator.comparingInt(r -> r.value().order())).toList()) {
				if (Perks.learn(p, ref.key()).isEmpty()) {
					learned[0] = ref.key().identifier().toString();
					return null;
				}
			}
			return "no perk could be learned (points: " + Perks.points(CharacterStore.get(p), Perks.registry(p), key) + ")";
		}));

		// The level toast comes first; the nudge sits by the hotbar.
		context.waitFor(mc -> MomentClient.showing() != null, 60);
		context.waitTicks(8);
		context.takeScreenshot("mettle_moment_toast");
		String problem = context.computeOnClient(mc -> {
			Set<MomentKind> seen = MomentClient.seenKinds();
			if (!(seen.contains(MomentKind.LEVEL) || seen.contains(MomentKind.TIER))) {
				return "the toast queue saw no level moment: " + seen;
			}
			if (!(seen.contains(MomentKind.LEARNED) || seen.contains(MomentKind.MASTERY))) {
				return "the toast queue saw no perk moment for " + learned[0] + ": " + seen;
			}
			if (MomentClient.toastsDrawn() == 0) {
				return "no toast was drawn";
			}
			if (!MomentClient.nudgeShowing()) {
				return "the points nudge is not showing after a level-up (" + MomentClient.unspentPoints(mc.player) + " points)";
			}
			return null;
		});
		check(problem);

		// Then the perk's own toast, once the level one gives way.
		context.waitFor(mc -> MomentClient.showing() == MomentKind.LEARNED || MomentClient.showing() == MomentKind.MASTERY, 200);
		context.waitTicks(8);
		context.takeScreenshot("mettle_moment_toast_learned");

		check(world.getServer().computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			CharacterStore.set(p, saved[0]);
			EffectsApplier.apply(p);
			return null;
		}));
		context.runOnClient(mc -> MomentClient.resetForTest());
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
