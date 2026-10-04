package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Roofs that skirt a building rather than cap it: a pent roof all round a hall whose core rises above it (the lower
 * eave of a two-tiered temple, the svalgang of a stave church, the aisle roofs of a basilica), its corners lifted on
 * eastern buildings.
 */
public final class Eaves {
	private Eaves() {
	}

	/**
	 * A pent roof over the walls {@code x0..x1 × z0..z1} (their top course at {@code plate}) leaning against the core
	 * {@code cx0..cx1 × cz0..cz1}, whose walls must rise above it: eaves {@code eaves} blocks out, rising {@code pitch} per
	 * block toward the core (curved, sweeping up at the eaves, when {@code curved}); with {@code tips} the four corners
	 * lift and hang a lantern. Returns the rasterised roof.
	 */
	public static Roofs.Roof skirt(Draft d, int x0, int z0, int x1, int z1, int cx0, int cz0, int cx1, int cz1, int plate, int eaves, double pitch,
		boolean curved, boolean tips) {
		int ex0 = x0 - eaves;
		int ez0 = z0 - eaves;
		int ex1 = x1 + eaves;
		int ez1 = z1 + eaves;
		Roofs.Profile p = curved ? Roofs.Profile.curved(pitch) : Roofs.Profile.straight(pitch);
		Roofs.Field f = (x, z) -> {
			if (x < ex0 || x > ex1 || z < ez0 || z > ez1) {
				return Double.NaN;
			}
			if (x >= cx0 && x <= cx1 && z >= cz0 && z <= cz1) {
				return Double.NaN;
			}
			double dd = Math.min(Math.min(x - ex0, ex1 - x), Math.min(z - ez0, ez1 - z));
			return p.at(dd);
		};
		int tWall = Math.max(1, (int) Math.round(p.at(eaves) * 2));
		int y0 = plate + 1 - ((tWall + 1) / 2 - 1);
		int rx0 = Math.max(0, ex0);
		int rz0 = Math.max(-1, ez0);
		int rx1 = Math.min(d.frontage() - 1, ex1);
		int rz1 = Math.min(d.depth() - 1, ez1);
		Roofs.Roof r = Roofs.raster(d, f, y0, rx0, rz0, rx1, rz1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		if (tips) {
			tips(d, r, rx0, rz0, rx1, rz1);
		}
		return r;
	}

	/** Lifted corners: at each corner of a roof's eave a block of the roof and a stair rising outward, a lantern under it. */
	public static void tips(Draft d, Roofs.Roof r, int x0, int z0, int x1, int z1) {
		Mat roof = d.mat(Role.ROOF);
		for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			int x = c[0];
			int z = c[1];
			if (!r.covers(x, z) || z < 0 && r.surfaceY(x, z) < 3) {
				continue;
			}
			int y = r.surfaceY(x, z);
			Direction out = x == x0 ? Dirs.LEFT : Dirs.RIGHT;
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(x, y, z, roof.full());
				if (d.allowed(x, y + 1, z)) {
					d.set(x, y + 1, z, roof.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
				}
			}
			if (d.isEmpty(x, y - 1, z) && y - 1 > d.site().ground(x, z) + 2 && d.allowed(x, y - 1, z)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, y - 1, z, States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), true)));
			}
		}
	}
}
