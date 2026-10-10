package dev.voidmc.mettle.character.cards;

import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** The Card Shrine (docs/CARDS.md §7.3, §7.5): rebuilds, releases and fusion. Built in wave B. */
public final class CardShrine {
	private CardShrine() {
	}

	/** Releases binder cards for a reroll; only at a shrine (§7.5). */
	public static Optional<CardRules.Refusal> release(ServerPlayer player, List<Long> ids) {
		return Optional.of(CardRules.Refusal.NOT_AT_SHRINE);
	}
}
