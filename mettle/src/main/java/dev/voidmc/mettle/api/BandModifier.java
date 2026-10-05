package dev.voidmc.mettle.api;

import net.minecraft.world.entity.player.Player;

/** Adjusts a player's load bands; applied in registration order to the defaults. Both sides. */
@FunctionalInterface
public interface BandModifier {
	BandRules adjust(Player player, BandRules rules);
}
