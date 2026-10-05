package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Shell;
import dev.voidmc.hearth.build.kit.sociallife.Signboard;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.kit.sociallife.Wards;
import dev.voidmc.hearth.build.kit.sociallife.Yardwork;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/infirmary}: the hospital where the healer tends the sick. One lofty storey, a long ward under tie
 * beams with the ridge along the street: down both long walls the cots of the sick (a white bolster, a blanket in the
 * house's cloth), the healer's stand of physic between each pair; at the far end of the ward a little altar so the sick
 * may see the candles from their beds; a stove for warmth. Beside the door, behind a partition, the healer's
 * dispensary: the brewing stand where the remedies are made, shelves of jars and a chest of herbs. Outside, the
 * hospital's board over the door, a bench, and behind it the physic garden of herb beds and a well.
 *
 * <p>Not housing: cots, not beds. POIs: SICKBEDs, the HEALER's JOB_SITE, STORAGE, the ALTAR, the HEARTH, SEATs, the
 * DOOR. Params: {@code variant} (int).
 */
public final class Infirmary implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/infirmary"), ArchetypeCategory.CIVIC, Set.of(JobType.HEALER), 0, 11,
		15, 11, 17, 9, 14, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();

		// --- the plan: the ward across the front, the physic garden behind
		int md = Math.min(dp - 2, dp >= 15 ? 10 : 9);
		int zF = raised ? Math.max(2, Math.min(dp - md, 1 + Grounds.stiltBase(d, 0, 1, w - 1, md))) : 2;
		int zM = Math.min(dp - 1, zF + md - 1);
		int base = raised ? Grounds.stiltBase(d, 0, zF, w - 1, zM) : 0;
		int h0 = maxH - base >= 9 ? 5 : 4;
		Body body = new Body(0, zF, w - 1, zM, base, new int[]{h0}, false, false);
		d.body(0, zF, w - 1, zM);
		d.eave(body.plateY());
		boolean dispLeft = d.chance(0.5);
		// the door near one end, into the corridor before the dispensary
		int doorCol = dispLeft ? 4 : w - 5;
		Grounds.forecourt(d, 0, w - 1, zF - 1, !raised);
		Shell.Built sh = Shell.build(d, body, Direction.Axis.X, Dirs.FRONT, doorCol, 1, 0.75, false, true, false);
		doorCol = sh.doorCol();
		int ix0 = 1;
		int ix1 = w - 2;
		int iz0 = zF + 1;
		int iz1 = zM - 1;
		int plate = body.plateY();

		// tie beams across the ward
		if (sh.roof() != null) {
			Mat beam = d.mat(Role.BEAM);
			try (var s = d.stage(StageKind.FRAME)) {
				for (int x = ix0 + 2; x <= ix1 - 2; x += 3) {
					for (int z = iz0; z <= iz1; z++) {
						if (d.isEmpty(x, plate, z)) {
							d.set(x, plate, z, beam.log(Direction.Axis.Z));
						}
					}
				}
			}
		}

		// --- the dispensary behind a partition at the door's end: the door into it from the ward
		// (beside the street door on the ward's side; where the door came too near the end, beyond it, the door then
		// opening into the dispensary)
		int px = dispLeft ? (doorCol - 1 >= ix0 + 2 ? doorCol - 1 : doorCol + 1) : (doorCol + 1 <= ix1 - 2 ? doorCol + 1 : doorCol - 1);
		int dx0 = dispLeft ? ix0 : px + 1;
		int dx1 = dispLeft ? px - 1 : ix1;
		boolean doorInDisp = doorCol >= dx0 && doorCol <= dx1;
		int pdz = doorInDisp ? iz1 - 1 : (iz0 + iz1) / 2;
		Yardwork.partition(d, px, iz0, iz1, base, h0 - 1, pdz, dispLeft ? Dirs.LEFT : Dirs.RIGHT);
		Floorplan disp = new Floorplan(dx0, iz0, dx1, iz1, base, h0 - 1);
		disp.keep(px + (dispLeft ? -1 : 1), pdz);
		if (doorInDisp) {
			disp.keep(sh.inside()[0], sh.inside()[1]);
		}
		Direction toWall = dispLeft ? Dirs.LEFT : Dirs.RIGHT;
		int ex = dispLeft ? dx0 : dx1;
		int sz = (iz0 + iz1) / 2;
		// the brewing stand on the end wall, the healer before it
		boolean job = false;
		for (int z : new int[]{sz, sz + 1, sz - 1, iz1, iz0}) {
			if (disp.free(ex, z) && disp.inside(ex - Dirs.dx(toWall), z) && disp.take(ex, z, toWall.getOpposite())) {
				final int fz = z;
				d.inStage(StageKind.INTERIOR, () -> d.set(ex, base, fz, Blocks.BREWING_STAND.defaultBlockState()));
				d.poi(PoiType.JOB_SITE, ex - Dirs.dx(toWall), base, z, toWall, JobType.HEALER);
				job = true;
				break;
			}
		}
		// shelves of jars and a chest of herbs along the end wall beside it
		boolean chest = false;
		for (int z = iz0; z <= iz1; z++) {
			if (!disp.free(ex, z) || !disp.take(ex, z, toWall.getOpposite())) {
				continue;
			}
			if (!chest) {
				Furniture.chest(d, ex, base, z, toWall.getOpposite(), List.of(new Object[]{"minecraft:glass_bottle", 8}, new Object[]{"minecraft:honey_bottle",
					2}, new Object[]{"minecraft:sweet_berries", 6}, new Object[]{"minecraft:paper", 4}));
				chest = true;
			} else {
				Furniture.barrel(d, ex, base, z, toWall.getOpposite());
			}
			final int fz = z;
			d.inStage(StageKind.INTERIOR, () -> {
				if (d.isEmpty(ex, base + 1, fz)) {
					d.set(ex, base + 1, fz, Blocks.DECORATED_POT.defaultBlockState());
				}
			});
		}
		if (!job) {
			// a narrow room: the stand against the partition
			int jx = px + (dispLeft ? -1 : 1);
			for (int z = iz0; z <= iz1 && !job; z++) {
				if (z != pdz && disp.free(jx, z) && disp.take(jx, z, null)) {
					final int fz = z;
					d.inStage(StageKind.INTERIOR, () -> d.set(jx, base, fz, Blocks.BREWING_STAND.defaultBlockState()));
					int[] f = freeBeside(disp, jx, z);
					if (f != null) {
						d.poi(PoiType.JOB_SITE, f[0], base, f[1], Dirs.of(jx - f[0], z - f[1]), JobType.HEALER);
						job = true;
					}
				}
			}
		}
		Lighting.room(d, dx0, iz0, dx1, iz1, base);

		// --- the ward: cots down the long walls, the healer's stands between, the altar at the far end, a stove
		int wx0 = dispLeft ? px + 1 : ix0;
		int wx1 = dispLeft ? ix1 : px - 1;
		Floorplan ward = new Floorplan(wx0, iz0, wx1, iz1, base, h0 - 1);
		if (!doorInDisp) {
			ward.keep(sh.inside()[0], sh.inside()[1]);
		}
		ward.keep(px + (dispLeft ? 1 : -1), pdz);
		int ax = dispLeft ? wx1 : wx0;
		Direction toSick = dispLeft ? Dirs.LEFT : Dirs.RIGHT;
		int az = (iz0 + iz1) / 2;
		if (ward.free(ax, az) && ward.take(ax, az, toSick)) {
			Sacred.altar(d, ax, base, az, toSick, false, Integer.MIN_VALUE);
			Venue.banner(d, ax, base + 2, az, toSick, "white", List.<String[]>of(new String[]{"minecraft:straight_cross", "red"}), StageKind.INTERIOR);
		}
		// a stove for warmth: on the far end wall beside the altar, else on any wall of the ward
		boolean warm = false;
		for (int z : new int[]{az + 2, az - 2, az + 1, az - 1}) {
			if (ward.free(ax, z) && ward.inside(ax + Dirs.dx(toSick), z) && ward.take(ax, z, toSick)) {
				Furniture.hearth(d, ax, base, z, toSick, false);
				warm = true;
				break;
			}
		}
		for (Direction wall : Dirs.HORIZONTAL) {
			for (int[] c : ward.along(wall)) {
				if (warm) {
					break;
				}
				int fx = c[0] - Dirs.dx(wall);
				int fz = c[1] - Dirs.dz(wall);
				if (ward.free(c[0], c[1]) && ward.inside(fx, fz) && ward.take(c[0], c[1], wall.getOpposite())) {
					Furniture.hearth(d, c[0], base, c[1], wall.getOpposite(), false);
					warm = true;
				}
			}
		}
		for (Direction wall : new Direction[]{Dirs.BACK, Dirs.FRONT}) {
			Wards.row(d, ward, wall, 2, true, 12, null);
		}
		// a bench for those who sit with the sick, along the middle
		int bx = (wx0 + wx1) / 2;
		if (ward.free(bx, az) && ward.free(bx + 1, az) && ward.take(new int[][]{{bx, az}, {bx + 1, az}}, null)) {
			Venue.bench(d, bx, base, az, Dirs.RIGHT, 2, Dirs.FRONT);
		}
		Lighting.room(d, wx0, iz0, wx1, iz1, base);

		// --- outside: the board, a bench by the door
		Side front = body.side(0, Dirs.FRONT);
		int di = front.index(doorCol);
		List<String[]> name = Signboard.name(d, "infirmary", Signboard.INFIRMARIES);
		for (int k : new int[]{2, -2, 3, -3}) {
			int i = di + k;
			if (i >= 1 && i <= front.last() - 1 && Signboard.bracket(d, front.x(i), base + 3, front.z(i), Dirs.FRONT, name)) {
				break;
			}
		}
		Grounds.approach(d, sh.outside()[0], base, sh.outside()[1]);
		if (!raised) {
			Bohemia.bench(d, doorCol, zF, base);
		}

		// --- behind: the physic garden and a well
		int yz0 = zM + 1;
		if (dp - 1 - yz0 >= 3 && !raised && Grounds.gentle(d, 0, yz0, w - 1, dp - 1, base)) {
			Groundwork.level(d, 0, yz0, w - 1, dp - 1, base);
			int gx0 = dispLeft ? 4 : 1;
			int gx1 = dispLeft ? w - 2 : w - 5;
			Yardwork.herbBeds(d, gx0, yz0 + 1, gx1, dp - 2, base, Dirs.RIGHT);
			Yard.well(d, dispLeft ? 2 : w - 3, base, Math.min(dp - 2, yz0 + 2), Dirs.FRONT);
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, zF, w - 1, zM, base, doorCol);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, sh.skyline() + 3));
	}

	private static int[] freeBeside(Floorplan p, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			int fx = x + Dirs.dx(dir);
			int fz = z + Dirs.dz(dir);
			if (p.open(fx, fz)) {
				return new int[]{fx, fz};
			}
		}
		return null;
	}
}
