package dev.voidmc.hearth.build.archetype.work;

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
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Forge;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Pentice;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/smithy}: the smith's forge. A tall single-storey hall of the culture's walls opens to the street
 * through a wide bay, so the glow of the forge shows; inside, against a gable wall, the forge itself — stone cheeks, a
 * hearth of glowing coals, the blast furnace, a hood — sends a broad stack up past the ridge, smoking. The anvil stands
 * before the fire with the quench trough beside it, tools hang in racks along the walls, coal and iron wait in bins.
 * The hall's eave runs on over the street front as a pentice on posts (a cloth awning on poles where roofs are flat),
 * sheltering the grindstone and the customers' bench; a side yard holds charcoal, scrap and a woodpile.
 *
 * <p>Job sites (SMITH): the anvil, the blast furnace, the smithing table, the grindstone. Storage: the chest and the
 * barrel of iron.
 *
 * <p>Params: {@code roof} (a roof form name), {@code variant}.
 */
public final class Smithy implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/smithy"), ArchetypeCategory.WORKPLACE, Set.of(JobType.SMITH), 0,
		8, 12, 8, 12, 8, 13, SettlementTier.HAMLET, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.SALTBOX, RoofForm.PAGODA, RoofForm.SHED,
		RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		RoofForm form = Hall.pick(d, FORMS);
		boolean flat = form == RoofForm.FLAT;

		// the plot: a work strip along the street, the hall behind it, a side yard where the plot is broad, and a margin
		// on the forge's side for the chimney breast
		int fz = dp >= 10 ? 3 : 2;
		int side = w >= 11 ? 3 : w >= 10 ? 2 : 0;
		boolean yardLeft = d.chance(0.5);
		boolean forgeLeft = side > 0 ? !yardLeft : d.chance(0.5);
		int hx0 = (yardLeft && side > 0 ? side : 0) + (forgeLeft ? 1 : 0);
		int hx1 = w - 1 - (!yardLeft && side > 0 ? side : 0) - (forgeLeft ? 0 : 1);
		int hz0 = fz;
		int hz1 = dp - 1;
		int sh = d.maxHeight() >= 12 && d.chance(0.5) ? 6 : 5;
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);

		// the bay opens toward the forge, which stands against the gable wall on its side
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, new int[]{sh}, false, false);
		int[] in = hall.inner(0);
		int bayW = in[2] - in[0] + 1 >= 6 ? 3 : 2;
		int bay = forgeLeft ? in[0] + 1 : in[2] - bayW;
		bay = Math.clamp(bay, in[0], in[2] - bayW + 1);
		int entryX = bay + bayW / 2;
		int ex = Yard.entrance(d);
		hall.facade(-1, 1, 1, 0.3).ground(false).shell().openings(false);
		hall.bay(Dirs.FRONT, bay, bayW, 3);
		d.poi(PoiType.DOOR, entryX, 0, hz0 - 1, Dirs.BACK);

		// roof: the ridge across the plot so the forge's wall is a gable end (the culture may turn it to face the street)
		Direction.Axis ridge = d.chance(h.roof().frontGable()) ? Direction.Axis.Z : Direction.Axis.X;
		hall.roof(form, ridge, 2, null);

		// the forge: four cells along the side wall, mid-depth, its breast outside and the stack up past the ridge
		Direction into = forgeLeft ? Dirs.RIGHT : Dirs.LEFT;
		int fx = forgeLeft ? in[0] : in[2];
		int depthIn = in[3] - in[1] + 1;
		int fz0 = in[1] + Math.max(0, (depthIn - 4) / 2);
		if (depthIn >= 5 && fz0 == in[1]) {
			fz0++;
		}
		fz0 = Math.min(fz0, in[3] - 3);
		int stackTop = hall.roof != null ? hall.roof.ridgeY + 2 : hall.top + 3;
		int[] forge = Forge.smith(d, fx, 0, fz0, Dirs.BACK, into, stackTop, hall.roof, true, hall.plateY());
		d.poi(PoiType.JOB_SITE, forge[2] + Dirs.dx(into), 0, forge[3], into.getOpposite(), JobType.SMITH);
		// a woodpile of charcoal wood along the outside of the forge wall, behind the breast
		int mx = forgeLeft ? hx0 - 1 : hx1 + 1;
		if (hz1 - (fz0 + 4) >= 2) {
			Garden.woodpile(d, mx, 0, hz1, Dirs.FRONT, Math.min(3, hz1 - (fz0 + 4)));
		}

		interior(d, hall, in, fx, fz0, into, bay, bayW);
		front(d, hall, w, fz, hz0, bay, bayW, flat, entryX, forgeLeft);
		if (side > 0) {
			sideYard(d, yardLeft ? 0 : hx1 + 1, yardLeft ? side - 1 : w - 1, fz, dp - 1, yardLeft);
		}
		Yard.connect(d, ex, entryX, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(hall.top, forge[4]) + 2);
	}

	/** Inside: the anvil before the fire, the quench trough, the smithing table, stores, racks of tools, lanterns. */
	private static void interior(Draft d, Hall hall, int[] in, int fx, int fz0, Direction into, int bay, int bayW) {
		Floorplan fp = hall.floor(0).blockHoles(d);
		for (int c = bay; c < bay + bayW; c++) {
			fp.keep(c, in[1]);
		}
		for (int k = 0; k < 4; k++) {
			fp.block(fx, fz0 + k);
		}
		// the smith works the blast furnace from in front of it (registered by the forge)
		int furnaceZ = fz0 + 2;
		fp.keep(fx + Dirs.dx(into), furnaceZ);
		fp.keep(fx + Dirs.dx(into), fz0 + 1);
		// the anvil two steps from the fire, worked from the side toward the front
		int ax = fx + Dirs.dx(into) * 2;
		boolean anvil = false;
		for (int az : new int[]{fz0 + 1, fz0 + 2, fz0, fz0 + 3}) {
			for (Direction user : new Direction[]{Dirs.FRONT, Dirs.BACK, into}) {
				if (!anvil && fp.take(ax, az, user)) {
					Props.anvil(d, ax, 0, az, user, JobType.SMITH);
					// the quench trough on the anvil's far side from the fire
					int tx = ax + Dirs.dx(into);
					if (fp.take(tx, az, null)) {
						Props.trough(d, tx, 0, az, Dirs.BACK, 1);
					}
					anvil = true;
				}
			}
		}
		// along the back wall: the smithing table, the chest, a barrel of iron, a bin of coal
		List<int[]> back = fp.along(Dirs.BACK);
		if (into == Dirs.LEFT) {
			java.util.Collections.reverse(back);
		}
		String[] wants = {"table", "chest", "iron", "coal"};
		int w = 0;
		for (int[] c : back) {
			if (w >= wants.length) {
				break;
			}
			if (!fp.take(c[0], c[1], Dirs.FRONT)) {
				continue;
			}
			switch (wants[w]) {
				case "table" -> Furniture.workstation(d, JobType.SMITH, c[0], 0, c[1], Dirs.FRONT);
				case "chest" -> Props.store(d, c[0], 0, c[1], Dirs.FRONT, true, List.of(new Object[]{"minecraft:iron_ingot", 12},
					new Object[]{"minecraft:iron_nugget", 20}, new Object[]{"minecraft:iron_horse_armor", 1}, new Object[]{"minecraft:shears", 2},
					new Object[]{"minecraft:iron_axe", 1}, new Object[]{"minecraft:bucket", 2}));
				case "iron" -> Props.store(d, c[0], 0, c[1], Dirs.FRONT, false, List.of(new Object[]{"minecraft:raw_iron", 24},
					new Object[]{"minecraft:coal", 32}));
				default -> Forge.bin(d, c[0], 0, c[1], Blocks.COAL_BLOCK.defaultBlockState());
			}
			// tools hanging over it
			if (hall.body.height(0) >= 5) {
				Props.toolRack(d, c[0], 2, c[1], Dirs.FRONT, rack(w));
			}
			w++;
		}
		// racks along the wall opposite the forge too
		List<int[]> far = fp.along(into);
		int racks = 0;
		for (int[] c : far) {
			if (racks >= 2 || c[1] == in[1] || c[1] == in[3]) {
				continue;
			}
			if (fp.free(c[0], c[1]) && d.isEmpty(c[0], 2, c[1]) && d.isSolid(c[0] + Dirs.dx(into), 2, c[1])) {
				Props.toolRack(d, c[0], 2, c[1], into.getOpposite(), rack(racks + 2));
				racks++;
			}
		}
		// tie beams across the hall at the plate, lanterns hung from them
		int plate = hall.plateY();
		boolean alongX = in[2] - in[0] <= in[3] - in[1];
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			if (alongX) {
				for (int z = in[1] + 1; z < in[3]; z += 3) {
					for (int x = in[0]; x <= in[2]; x++) {
						if (d.isAbsent(x, plate, z) || d.isEmpty(x, plate, z)) {
							d.set(x, plate, z, beam.log(Direction.Axis.X));
						}
					}
				}
			} else {
				for (int x = in[0] + 1; x < in[2]; x += 3) {
					for (int z = in[1]; z <= in[3]; z++) {
						if (d.isAbsent(x, plate, z) || d.isEmpty(x, plate, z)) {
							d.set(x, plate, z, beam.log(Direction.Axis.Z));
						}
					}
				}
			}
		}
		int lx = (in[0] + in[2]) / 2;
		int lz = (in[1] + in[3]) / 2;
		for (int[] c : new int[][]{{lx, lz}, {lx, lz + 1}, {lx + 1, lz}, {lx - 1, lz}}) {
			if (d.isSolid(c[0], plate, c[1]) && d.isEmpty(c[0], plate - 1, c[1])) {
				Furniture.hangingLantern(d, c[0], plate, c[1], plate - 1 >= 4 ? 1 : 0);
				break;
			}
		}
	}

	private static List<Object[]> rack(int i) {
		return switch (i % 4) {
			case 0 -> List.of(new Object[]{"minecraft:iron_pickaxe", 1, 0}, new Object[]{"minecraft:iron_shovel", 1, 1}, new Object[]{"minecraft:iron_hoe", 1, 2});
			case 1 -> List.of(new Object[]{"minecraft:iron_sword", 1, 0}, new Object[]{"minecraft:shield", 1, 1}, new Object[]{"minecraft:iron_axe", 1, 2});
			case 2 -> List.of(new Object[]{"minecraft:shears", 1, 0}, new Object[]{"minecraft:bucket", 1, 1}, new Object[]{"minecraft:iron_chain", 1, 2});
			default -> List.of(new Object[]{"minecraft:iron_helmet", 1, 0}, new Object[]{"minecraft:iron_ingot", 1, 1}, new Object[]{"minecraft:lantern", 1, 2});
		};
	}

	/**
	 * The street front: a pentice or an awning over the work strip, the anvil and the quench trough beside the bay where
	 * the street can watch, the grindstone at the far end, a bench, the sign.
	 */
	private static void front(Draft d, Hall hall, int w, int fz, int hz0, int bay, int bayW, boolean flat, int entryX, boolean forgeLeft) {
		int[] r = hall.rect(0);
		Side wall = hall.side(0, Dirs.FRONT);
		Yard.trodden(d, 0, 0, w - 1, fz - 1, 0);
		Yard.pave(d, bay - 1, hz0 - 1, bay + bayW, hz0 - 1, 0);
		int[] open = new int[bayW];
		for (int i = 0; i < bayW; i++) {
			open[i] = bay + i;
		}
		Roofs.Roof pent = null;
		if (!flat && hall.roof != null) {
			pent = Pentice.over(d, hall.roof, wall, r[0], r[2], fz + 1, 0, open);
		}
		if (pent == null) {
			Pentice.awning(d, wall, r[0], r[2], Math.min(2, fz), 0, 3, d.chance(0.6), open);
		}
		// the anvil beside the bay on the far side from the forge, worked from the street side, the trough next to it
		int step = forgeLeft ? 1 : -1;
		int ax = forgeLeft ? bay + bayW : bay - 1;
		int az = hz0 - 1;
		if (ax >= r[0] && ax <= r[2] && d.isAbsent(ax, 0, az) && d.isAbsent(ax, 0, az - 1)) {
			Props.anvil(d, ax, 0, az, Dirs.FRONT, JobType.SMITH);
			int tx = ax + step;
			if (tx >= r[0] && tx <= r[2] && d.isAbsent(tx, 0, az)) {
				Props.trough(d, tx, 0, az, Dirs.FRONT, 1);
			}
		}
		// the grindstone at the street edge at the far end, a bench against the wall by the bay
		int gx = forgeLeft ? r[2] : r[0];
		int gz = 0;
		for (int[] g : new int[][]{{gx, 0, 1}, {gx, 1, 0}, {gx - (forgeLeft ? 1 : -1), 0, 1}}) {
			int ux = g[0];
			int uz = g[2] == 1 ? g[1] + 1 : g[1] - 1;
			if (uz >= 0 && d.isEmpty(g[0], 0, g[1]) && d.isEmpty(ux, 0, uz) && d.isEmpty(ux, 1, uz) && Math.abs(g[0] - entryX) > 1) {
				Props.grindstone(d, g[0], 0, g[1], g[2] == 1 ? Dirs.BACK : Dirs.FRONT, JobType.SMITH);
				break;
			}
		}
		int bx = forgeLeft ? bay - 1 : bay + bayW;
		if (bx >= r[0] && bx <= r[2] && d.isAbsent(bx, 0, hz0 - 1)) {
			Furniture.chair(d, bx, 0, hz0 - 1, Dirs.BACK);
		}
		// a barrel of quenching water and a crate of horseshoes by the posts
		int cx = forgeLeft ? r[0] : r[2];
		if (fz >= 3 && d.isAbsent(cx, 0, 1) && Math.abs(cx - entryX) > 1) {
			Props.crates(d, cx, 0, 1, Dirs.FRONT, 1);
		}
		// the sign: hung from the pentice beside the bay, or on the wall
		int sx = forgeLeft ? bay + bayW + 1 : bay - 2;
		boolean signed = false;
		if (pent != null) {
			for (int z = 0; z < hz0 && !signed; z++) {
				if (pent.covers(sx, z) && d.isEmpty(sx, pent.bottomY(sx, z) - 1, z) && pent.bottomY(sx, z) - 1 >= 2) {
					signed = Signs.hang(d, sx, pent.bottomY(sx, z) - 1, z, Dirs.FRONT, "smithy", "Smithy");
				}
			}
		}
		if (!signed) {
			Signs.bracket(d, wall, wall.index(sx), 3, "smithy", "Smithy");
		}
	}

	/** The side yard: charcoal under a little roof, a scrap heap, a chopping block, a woodpile, fenced. */
	private static void sideYard(Draft d, int x0, int x1, int z0, int z1, boolean left) {
		Yard.trodden(d, x0, z0, x1, z1, 0);
		int outer = left ? x0 : x1;
		int inner = left ? x1 : x0;
		int len = Math.min(3, z1 - z0 - 1);
		if (len >= 2) {
			Garden.woodpile(d, inner, 0, z1 - 1, Dirs.FRONT, len);
		}
		Props.choppingBlock(d, outer, 0, z0 + 1);
		if (z1 - z0 >= 5 && x1 - x0 >= 1) {
			Props.heap(d, outer, z1 - 1, outer, z1, 0,
				new net.minecraft.world.level.block.Block[]{Blocks.COAL_BLOCK, Blocks.COBBLESTONE, Blocks.GRAVEL}, null);
		}
		if (x1 - x0 >= 2 && z1 - z0 >= 4) {
			// the ore come in from the mine: a cart of raw iron by the street, its shaft to the gate
			int cxm = (x0 + x1) / 2;
			if (d.isEmpty(cxm - 1, 0, z0 + 2) && d.isEmpty(cxm, 0, z0 + 2) && d.isEmpty(cxm + 1, 0, z0 + 2) && d.isEmpty(cxm, 0, z0 + 1)) {
				Props.cart(d, cxm, 0, z0 + 2, Dirs.FRONT, Blocks.RAW_IRON_BLOCK.defaultBlockState());
			} else {
				Props.crates(d, outer, 0, z0 + 3, Dirs.BACK, 1);
			}
		} else if (x1 - x0 >= 1 && z1 - z0 >= 3) {
			// a heap of ore and coal where the yard is narrow
			Props.heap(d, outer, z0 + 2, outer, z0 + 2, 0, new net.minecraft.world.level.block.Block[]{Blocks.RAW_IRON_BLOCK, Blocks.COAL_BLOCK}, null);
		}
		List<int[]> gaps = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			gaps.add(new int[]{x, z0});
		}
		Yard.enclose(d, x0, z0, x1, z1, gaps, left ? Dirs.RIGHT : Dirs.LEFT);
	}
}
