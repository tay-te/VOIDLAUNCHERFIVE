package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.mixin.burden.PlayerSprintGate;
import net.minecraft.world.entity.player.Player;

/**
 * Whether the load stops a sprint. Burden's own hook on the sprint gate says no from Overburdened on; other hooks on the
 * same gate may say yes again (Athletics' Hauler's Stride lets an Overburdened player sprint), so the server asks the
 * gate rather than the band, and only when the band is heavy enough to matter.
 */
public final class Sprint {
	private Sprint() {
	}

	public static boolean blockedByLoad(Player player) {
		return Bands.atLeast(LoadState.band(player), LoadBand.OVERBURDENED) && !((PlayerSprintGate) player).mettle$mayExert();
	}
}
