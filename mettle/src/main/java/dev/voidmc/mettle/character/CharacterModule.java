package dev.voidmc.mettle.character;

import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.MiningSkill;
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
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.LearnPerk.TYPE, (payload, context) ->
			Perks.learn(context.player(), payload.perk()).ifPresent(reason ->
				SignaturePerks.feedback(context.player(), payload.perk().identifier(), Component.translatable(reason.translationKey()))));
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.SpendAttribute.TYPE, (payload, context) -> Perks.spendAttribute(context.player(), payload.attribute()));
		ServerPlayerEvents.LEAVE.register(player -> {
			Progress.forget(player.getUUID());
			MiningSkill.forget(player.getUUID());
		});
		SignaturePerks.init();
		PlacedBlocks.init();
		FeatCounters.init();
		Progress.init();
		MiningSkill.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			CharacterCommands.register(dispatcher, context);
			TreeCommands.register(dispatcher);
		});
	}
}
