package dev.voidmc.mettle.core;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.net.MettlePayloads;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

/**
 * The core: the character record, the data registries, Mettle's attributes, the effects applier and the packet types.
 * Other modules build on these (ARCHITECTURE.md §3).
 */
public final class CoreModule {
	/** Runs after Fabric's own respawn listener has copied attachments to the new player. */
	public static final Identifier AFTER_ATTACHMENTS = Mettle.id("after_attachments");

	private CoreModule() {
	}

	public static void init() {
		DynamicRegistries.registerSynced(MettleRegistries.SKILL, Skill.CODEC);
		DynamicRegistries.registerSynced(MettleRegistries.BACKGROUND, Background.CODEC);
		DynamicRegistries.registerSynced(MettleRegistries.PERK, Perk.CODEC);
		// Cards v2 (docs/CARDS.md): traits, keywords and each skill's card data.
		DynamicRegistries.registerSynced(MettleRegistries.TRAIT, dev.voidmc.mettle.api.Trait.CODEC);
		DynamicRegistries.registerSynced(MettleRegistries.KEYWORD, dev.voidmc.mettle.api.Keyword.CODEC);
		DynamicRegistries.registerSynced(MettleRegistries.CARD_SKILL, dev.voidmc.mettle.api.CardSkill.CODEC);
		DynamicRegistries.registerSynced(MettleRegistries.MYTHIC, dev.voidmc.mettle.api.Mythic.CODEC);
		// Build feel (docs/BUILD_FEEL.md §2.1): the words bynames are made of.
		DynamicRegistries.registerSynced(MettleRegistries.BYNAME, dev.voidmc.mettle.api.BynameWords.CODEC);
		CharacterStore.init();
		CardStore.init();
		MettleAttributes.init();

		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.OpenPicker.TYPE, MettlePayloads.OpenPicker.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.BackgroundChosen.TYPE, MettlePayloads.BackgroundChosen.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.BackgroundRejected.TYPE, MettlePayloads.BackgroundRejected.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.Feedback.TYPE, MettlePayloads.Feedback.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.Moment.TYPE, MettlePayloads.Moment.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.CardSitting.TYPE, MettlePayloads.CardSitting.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.ChooseBackground.TYPE, MettlePayloads.ChooseBackground.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.LearnPerk.TYPE, MettlePayloads.LearnPerk.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.SpendAttribute.TYPE, MettlePayloads.SpendAttribute.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.PickCard.TYPE, MettlePayloads.PickCard.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.Reroll.TYPE, MettlePayloads.Reroll.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.KeepCard.TYPE, MettlePayloads.KeepCard.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.CardAction.TYPE, MettlePayloads.CardAction.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.Updraft.TYPE, MettlePayloads.Updraft.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.TradeAction.TYPE, MettlePayloads.TradeAction.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.TradeView.TYPE, MettlePayloads.TradeView.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.MythicReveal.TYPE, MettlePayloads.MythicReveal.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.HallOfFirsts.TYPE, MettlePayloads.HallOfFirsts.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MettlePayloads.Dash.TYPE, MettlePayloads.Dash.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MettlePayloads.CardsConfig.TYPE, MettlePayloads.CardsConfig.STREAM_CODEC);
		MettleHandshake.init();

		ServerPlayerEvents.JOIN.register(EffectsApplier::apply);
		ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_ATTACHMENTS);
		ServerPlayerEvents.AFTER_RESPAWN.register(AFTER_ATTACHMENTS, (oldPlayer, newPlayer, alive) -> {
			// Respawning keeps only base values, so every modifier is rebuilt; a death respawn then fills the new,
			// possibly larger, health bar.
			EffectsApplier.apply(newPlayer);
			if (!alive) {
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}
		});
	}
}
