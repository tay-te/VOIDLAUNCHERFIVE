package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Appraiser at a Hearth market (docs/BUILD_FEEL.md §14.2): what the settlement the player stands in pays and asks for a
 * stack, as its market counter would quote this player ({@code EconomyService.price}: standing and every price modifier).
 * Mettle-only: a tooltip, nothing Hearth reads.
 */
final class HearthAppraiser {
	/** How near a settlement's centre counts as "at its market". */
	static final int RANGE = 128;

	private HearthAppraiser() {
	}

	static Optional<Component> appraise(ServerPlayer player, ItemStack stack) {
		EconomyService economy = HearthServices.economy();
		HearthWorld world = HearthServices.world(player.level());
		if (economy == null || world == null) {
			return Optional.empty();
		}
		Settlement here = world.nearest(player.blockPosition(), RANGE);
		if (here == null) {
			return Optional.empty();
		}
		long pays = economy.price(player, here, stack, false);
		long asks = economy.price(player, here, stack, true);
		return Optional.of(Component.translatable("mettle.speech.appraise", here.name(), economy.format(pays), economy.format(asks)));
	}
}
