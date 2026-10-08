package dev.voidmc.hearth.build.kit.landmark;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Eaves for tiered buildings: a curved skirt round a tier's walls with lifted, lantern-hung corners and brackets under
 * it (each tier of a pagoda, the lower eave of a double-eaved hall), and a full hip carried up to a point or a short
 * ridge (the top of a pagoda, the upper roof of a temple hall) with a spire of rings.
 */
public final class Eaves {
	private Eaves() {
	}

	/**
	 * A skirt {@code e} blocks out from the walls of {@code [x0..x1] × [z0..z1]}, its eave at {@code y} rising to the
	 * wall line, the corners lifted (and lanterns hung under them) when {@code flare}, brackets under its foot.
	 */
	public static Roofs.Roof skirt(Draft d, int x0, int z0, int x1, int z1, int y, int e, boolean flare) {
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		Roofs.Field hip = Roofs.hip(x0 - e, z0 - e, x1 + e, z1 + e, d2 -> 1 + 0.5 * Math.min(d2, e));
		Roofs.Field f = flare ? Roofs.flared(hip, x0 - e, z0 - e, x1 + e, z1 + e, 1, 2.5) : hip;
		Roofs.Field skirt = Shapes.outside(f, x0, z0, x1, z1);
		int ex0 = Math.max(0, x0 - e);
		int ez0 = Math.max(-1, z0 - e);
		int ex1 = Math.min(d.frontage() - 1, x1 + e);
		int ez1 = Math.min(d.depth() - 1, z1 + e);
		Roofs.Roof r = Roofs.raster(d, skirt, y, ex0, ez0, ex1, ez1, roof, trim, Habits.Ridge.SLAB);
		if (flare) {
			Crowns.tips(d, r, ex0, ez0, ex1, ez1, Crowns.Mats.of(d, Stone.dressing(d)));
		}
		if (trim.has(Form.STAIRS)) {
			try (var s = d.stage(StageKind.ROOF)) {
				for (Side sd : Side.of(x0, z0, x1, z1)) {
					for (int i = 1; i < sd.length() - 1; i += 2) {
						int ox = sd.outX(i, 1);
						int oz = sd.outZ(i, 1);
						// (none over a floor two below, where it would catch the head of whoever walks round the
						// tier: a pagoda's podium, a hall's terrace before its door)
						if (d.allowed(ox, y - 1, oz) && d.isEmpty(ox, y - 1, oz) && !d.isSolid(ox, y - 3, oz)) {
							d.set(ox, y - 1, oz, trim.stairs(sd.out().getOpposite(), Half.TOP));
						}
					}
				}
			}
		}
		return r;
	}

	/**
	 * A hip roof over walls {@code r = {x0, z0, x1, z1}} from eave row y, {@code e} out, curved and lifted at the
	 * corners when {@code flare}, a ceiling under it, and (when {@code spire}) a spire of rings on a gilded boss.
	 * Returns the highest row used.
	 */
	public static int hipped(Draft d, int[] r, int y, int top, int e, boolean flare, boolean spire) {
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		double half = Math.min(r[2] - r[0], r[3] - r[1]) / 2.0 + e;
		double pitch = Math.max(0.75, Math.min(1.6, (top - y - (spire ? 8 : 2)) / Math.max(1.0, half - 2)));
		Roofs.Field base = flare ? Roofs.hip(r[0] - e, r[1] - e, r[2] + e, r[3] + e, Roofs.Profile.curved(pitch))
			: Roofs.hip(r[0] - e, r[1] - e, r[2] + e, r[3] + e, Roofs.Profile.straight(pitch));
		Roofs.Field f = flare ? Roofs.flared(base, r[0] - e, r[1] - e, r[2] + e, r[3] + e, 1, 2.5) : base;
		int ex0 = Math.max(0, r[0] - e);
		int ez0 = Math.max(-1, r[1] - e);
		int ex1 = Math.min(d.frontage() - 1, r[2] + e);
		int ez1 = Math.min(d.depth() - 1, r[3] + e);
		Roofs.Roof rr = Roofs.raster(d, f, y, ex0, ez0, ex1, ez1, roof, trim, Habits.Ridge.FULL);
		Shapes.support(d, rr, roof.full());
		if (flare) {
			Crowns.tips(d, rr, ex0, ez0, ex1, ez1, Crowns.Mats.of(d, Stone.dressing(d)));
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = r[1] + 1; z < r[3]; z++) {
				for (int x = r[0] + 1; x < r[2]; x++) {
					d.set(x, y, z, d.mat(Role.CEILING).full());
				}
			}
		}
		int xc = (r[0] + r[2]) / 2;
		int zc = (r[1] + r[3]) / 2;
		int yy = rr.ridgeY + 1;
		if (!spire) {
			return rr.ridgeY;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			if (yy < top) {
				d.set(xc, yy++, zc, Blocks.GOLD_BLOCK.defaultBlockState());
			}
			int rings = Math.min(4, top - yy - 1);
			for (int k = 0; k < rings; k++) {
				d.set(xc, yy++, zc, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
			if (yy <= top) {
				d.set(xc, yy, zc, Details.rod());
			}
		}
		return Math.min(top, yy);
	}
}
