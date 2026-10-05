package dev.voidmc.mettle.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;

/** Weight and load (BURDEN.md §1–§3). Implemented by the burden module; works on both sides for the local player. */
public interface BurdenService {
	/** A whole stack's weight in kg, nested contents included, before any player's modifiers. */
	float weight(ItemInstance stack);

	/** The same, after this player's {@link WeightModifier}s. */
	float weight(Player player, ItemInstance stack);

	/** Everything the player carries, in kg. */
	float load(Player player);

	/** The player's carry capacity (the {@code mettle:carry_capacity} attribute). */
	float capacity(Player player);

	LoadBand band(Player player);

	/** The player's band thresholds after every {@link BandModifier}. */
	BandRules rules(Player player);
}
