package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;

/**
 * A bell turret three square standing on a roof: carried on tie beams across the hall at the wall plate, a short shaft
 * up through the roof, an open belfry with its bell, and a cap (a spire, an onion, a pyramid). The law's bell rings
 * for the court's sittings and for the watch.
 */
public final class Turret {
	private Turret() {
	}

	/**
	 * A turret whose square is {@code x0..x0+2 × z0..z0+2}, its beams at {@code plate}, its belfry clear of
	 * {@code ridge}. Returns the top.
	 */
	public static int onRidge(Draft d, Tradition t, int x0, int z0, int plate, int ridge, int maxH, Towers.Cap cap) {
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = z0; z <= z0 + 2; z++) {
				for (int x = x0; x <= x0 + 2; x++) {
					d.set(x, plate, z, d.mat(Role.BEAM).log(Direction.Axis.X));
				}
			}
		}
		int yb = ridge + 1;
		int bh = 3;
		int capRoom = cap == Towers.Cap.SPIRE ? 6 : 4;
		if (yb + bh + capRoom > maxH - 1) {
			yb = Math.max(plate + 2, maxH - 1 - bh - capRoom);
		}
		if (yb + bh + 2 > maxH - 1) {
			bh = Math.max(2, maxH - 1 - 2 - yb);
		}
		Towers.shaft(d, x0, z0, 3, plate + 1, yb - 1, t, -1);
		Towers.belfry(d, x0, z0, 3, yb, bh, t, true);
		return Towers.cap(d, x0, z0, 3, yb + bh, cap, maxH);
	}
}
