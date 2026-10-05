package dev.voidmc.mettle.character;

import dev.voidmc.mettle.api.Perk;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** A perk's sentence at a given rank: ranked perks fill their %s with that rank's value ("20 %"). */
public final class PerkText {
	private PerkText() {
	}

	public static Component description(Perk perk, int rank) {
		if (perk.rankValues().isEmpty() || !(perk.description().getContents() instanceof TranslatableContents t)) {
			return perk.description();
		}
		String value = perk.rankValues().get(Math.clamp(rank, 1, perk.rankValues().size()) - 1);
		return Component.translatable(t.getKey(), value);
	}
}
