package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import net.minecraft.world.level.block.Blocks;

/** What every deep hall shares outside its walls, whatever its culture's plan: the paved apron, its lamps, the notice board. */
final class HallParts {
	private HallParts() {
	}

	/**
	 * Fills a rasterised cap (a dome, an onion) solid from {@code fromY} up under its surface, so its shell stands on what
	 * is under it rather than on its own rim.
	 */
	static void solid(Draft d, dev.voidmc.hearth.build.kit.Roofs.Roof r, int fromY, dev.voidmc.hearth.build.draft.Paint fill) {
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = r.z0; z <= r.z1; z++) {
				for (int x = r.x0; x <= r.x1; x++) {
					if (!r.covers(x, z)) {
						continue;
					}
					for (int y = fromY; y < r.surfaceY(x, z); y++) {
						if (d.isAbsent(x, y, z)) {
							d.set(x, y, z, fill);
						}
					}
				}
			}
		}
	}

	/** The apron levelled and paved in front of the building's front row {@code zf}. */
	static void ground(Draft d, int zf) {
		Groundwork.level(d, 0, 0, d.frontage() - 1, Math.max(1, zf - 1), 0, true);
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < zf; z++) {
				for (int x = 0; x < d.frontage(); x++) {
					d.set(x, -1, z, paving.full());
				}
			}
		}
	}

	/**
	 * A lamp post either side of the way in, {@code off} from its middle {@code c}, on the street row; the notice board
	 * on the right one's outer face.
	 */
	static void apron(Draft d, int c, Glow g, int off) {
		for (int x : new int[]{c - off, c + off}) {
			if (x >= 0 && x < d.frontage() && d.isAbsent(x, 0, 0)) {
				Rock.lampPost(d, x, 0, 0, Noise.unit(d.seed(), x, 0, 0) < 0.5, g);
			}
		}
		int nx = c + off + 1;
		if (nx < d.frontage() && d.isAbsent(nx, 1, 0) && !d.isAbsent(nx - 1, 1, 0)) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(nx, 1, 0, States.facing(d.mat(Role.FURNITURE).mainOr(Form.WALL_SIGN, Blocks.SPRUCE_WALL_SIGN), Dirs.RIGHT),
					BlockEntities.sign(List.of("", "Moot today", "at the bell", ""), "black", false));
			}
			d.poi(PoiType.NOTICE_BOARD, nx, 0, 0, Dirs.LEFT);
		}
	}
}
