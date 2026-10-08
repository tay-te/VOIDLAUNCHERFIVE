package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.PriceModifier;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Presence, Speech and the Merchant's Coin Sense at Hearth's market counters ({@link HearthRules#priceEdge}): the player
 * buys a little cheaper and sells a little dearer. Hearth multiplies this with any other mod's factor, keeps the product
 * within 0.5–1.5 and the town's buying price below 95 % of its selling price. Called for every row a market shows, so it
 * only reads the synced character.
 */
final class MettlePrices implements PriceModifier {
	static final ResourceKey<Skill> SPEECH = Progress.skill("speech");

	@Override
	public double factor(ServerPlayer player, Settlement settlement, ItemStack one, boolean playerBuys) {
		return HearthRules.priceFactor(edge(player), playerBuys);
	}

	static double edge(ServerPlayer player) {
		CharacterState state = CharacterStore.get(player);
		int presence = state.attribute(CharacterAttribute.PRESENCE, CharacterStore.background(player.level().registryAccess(), state));
		int speech = state.level(SPEECH);
		return HearthRules.priceEdge(presence, speech, SignaturePerks.has(player, SignaturePerks.COIN_SENSE));
	}
}
