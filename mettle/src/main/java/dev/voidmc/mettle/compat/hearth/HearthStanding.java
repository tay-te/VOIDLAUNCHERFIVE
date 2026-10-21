package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.mettle.character.cards.Mythics;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hearth's standing bands (request 9, {@code SettlementEvents.STANDING_CHANGED}): the first player Honoured ({@code HERO})
 * in a city earns The Open Hand. Only loaded when the running Hearth has the event (HearthCompat checks first).
 */
final class HearthStanding {
	private HearthStanding() {
	}

	static void init() {
		SettlementEvents.STANDING_CHANGED.register((world, settlement, player, from, to) -> {
			if (to != StandingService.Title.HERO || from == StandingService.Title.HERO || settlement.tier() != SettlementTier.CITY) {
				return;
			}
			ServerPlayer p = HearthCompat.player(player);
			if (p != null) {
				Mythics.honouredInCity(p);
			}
		});
	}
}
