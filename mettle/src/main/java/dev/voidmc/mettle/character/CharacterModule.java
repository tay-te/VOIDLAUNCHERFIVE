package dev.voidmc.mettle.character;

import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.AthleticsSkill;
import dev.voidmc.mettle.character.skill.BladesSkill;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.DefenseSkill;
import dev.voidmc.mettle.character.skill.HeavyWeaponsSkill;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import net.minecraft.network.chat.Component;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Backgrounds today; skills, perks and character levels as their phases land (ARCHITECTURE.md §9). */
public final class CharacterModule {
	private CharacterModule() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.ChooseBackground.TYPE, (payload, context) -> Backgrounds.choose(context.player(), payload));
		// Until a background is chosen the picker greets the player on every join; it can always be closed.
		ServerPlayerEvents.JOIN.register(player -> {
			if (!CharacterStore.get(player).hasBackground()) {
				ServerPlayNetworking.send(player, new MettlePayloads.OpenPicker(false));
			}
		});
		// Perks come from drafts now (docs/DRAFT.md): an older host asking to buy one is told so.
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.LearnPerk.TYPE, (payload, context) ->
			SignaturePerks.feedback(context.player(), payload.perk().identifier(), Component.translatable("mettle.draft.no_buying")));
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.SpendAttribute.TYPE, (payload, context) -> Perks.spendAttribute(context.player(), payload.attribute()));
		ServerPlayerEvents.LEAVE.register(player -> {
			Progress.forget(player.getUUID());
			MiningSkill.forget(player.getUUID());
		});
		dev.voidmc.mettle.character.cards.Cards.init();
		dev.voidmc.mettle.character.draft.Drafts.init();
		dev.voidmc.mettle.character.draft.CardSounds.init();
		SignaturePerks.init();
		PlacedBlocks.init();
		FeatCounters.init();
		Progress.init();
		MiningSkill.init();
		Combat.init();
		BladesSkill.init();
		HeavyWeaponsSkill.init();
		MarksmanshipSkill.init();
		DefenseSkill.init();
		AthleticsSkill.init();
		SurvivalSkill.init();
		SmithingSkill.init();
		Tomes.init();
		dev.voidmc.mettle.character.respec.Draughts.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			CharacterCommands.register(dispatcher, context);
			TreeCommands.register(dispatcher);
			dev.voidmc.mettle.character.draft.DraftCommands.register(dispatcher);
			dev.voidmc.mettle.character.cards.CardCommands.register(dispatcher);
			dev.voidmc.mettle.character.cards.BuildCommands.register(dispatcher);
		});
	}
}
