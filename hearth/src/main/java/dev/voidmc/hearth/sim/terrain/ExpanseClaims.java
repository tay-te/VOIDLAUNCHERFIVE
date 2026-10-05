package dev.voidmc.hearth.sim.terrain;

import dev.voidmc.expanse.api.LandClaims;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.world.SimWorld;

/**
 * Tells Expanse which ground Hearth has planned, so its area features (little things, wayside sights, hedgerows,
 * fishponds) stay off plots and roads (docs/ARCHITECTURE.md §11). Only class-loaded when Expanse is present. Expanse asks
 * from world generation workers several times per footprint; {@link SimWorld#isPlanned} is a cheap per-chunk bitmask
 * lookup and safe from any thread.
 */
public final class ExpanseClaims {
	private ExpanseClaims() {
	}

	public static void register() {
		LandClaims.register((level, x, z) -> {
			SimWorld w = SimServer.world(level);
			return w != null && w.isPlanned(x, z);
		});
	}
}
