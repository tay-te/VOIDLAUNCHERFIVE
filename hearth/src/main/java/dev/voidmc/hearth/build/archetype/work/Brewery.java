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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/brewery}: the brewhouse and its kiln. Beside the brewhouse rises the malt kiln — an oast with a
 * steep pyramid or cone roof and a white cowl turned to the wind, a landmark of the brewing country — while the
 * brewhouse holds the copper (cauldrons on a slow fire), the brewing stands on their bench and the barrel racks in two
 * tiers along the cool back wall. Out in the yard the hop bines climb their poles. Where the culture brews wine or rice
 * wine instead, the yard holds rows of great jars under a pergola of vines.
 *
 * <p>Job sites (BREWER): the brewing stands, the coppers. Storage: the barrel racks and the chest.
 *
 * <p>Params: {@code roof}, {@code kiln} (boolean), {@code variant}.
 */
public final class Brewery implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/brewery"), ArchetypeCategory.WORKPLACE, Set.of(JobType.BREWER), 0,
		9, 13, 9, 13, 11, 16, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.SALTBOX,
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
		boolean wine = Hall.flatCulture(d) || h.windows().lattice() || h.walls().kind() == Habits.WallKind.ADOBE
			|| h.roof().weight(RoofForm.FLAT) >= 1 && h.yard().garden() == Habits.GardenKind.KITCHEN;
		boolean kiln = d.param("kiln", !wine && !flat && d.maxHeight() >= 11);

		// the brewhouse across the back, the kiln at its front corner, the yard on the other side
		int fz = 1;
		boolean kilnLeft = d.chance(0.5);
		int ks = w >= 12 && d.chance(0.5) ? 5 : 4;
		int hz0 = dp >= 11 ? 5 : 4;
		int hz1 = dp - 1;
		int hx0 = 0;
		int hx1 = w - 1;
		int sh = 5;
		// the brewhouse keeps low, so its kiln stands over it
		int[] heights = new int[]{sh};
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, heights, false, false);
		int[] in = hall.inner(0);
		int kx0 = kilnLeft ? 0 : w - ks;
		int kx1 = kx0 + ks - 1;
		int door = kilnLeft ? Math.max(kx1 + 2, (in[0] + in[2]) / 2) : Math.min(kx0 - 3, (in[0] + in[2]) / 2);
		door = Math.clamp(door, in[0], in[2] - 1);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		hall.facade(door, 2, 1, 0.4);
		door = hall.door != null ? hall.door : door;
		// the bays may have moved the door: the kiln goes on the far side of it, or not at all
		if (kilnLeft ? kx1 + 1 >= door : kx0 - 1 <= door + 1) {
			kilnLeft = !kilnLeft;
			kx0 = kilnLeft ? 0 : w - ks;
			kx1 = kx0 + ks - 1;
			if (kilnLeft ? kx1 + 1 >= door : kx0 - 1 <= door + 1) {
				kiln = false;
			}
		}
		int ex = Yard.entrance(d);
		hall.ground(false).shell().openings(true);
		Direction.Axis ridge = Direction.Axis.X;
		hall.roof(form, ridge, 1, null);
		if (flat) {
			hall.crown();
		}
		// the copper's flue
		int cx = kilnLeft ? in[2] : in[0];
		interior(d, hall, door, cx, wine);
		hall.chimney(cx, in[3], 2);

		if (kiln) {
			oast(d, kx0, kx1, Math.max(0, hz0 - ks + 1 > 0 ? hz0 - ks : 0), hz0 - 1, hall);
		}
		int yx0 = kilnLeft ? (kiln ? kx1 + 1 : 0) : 0;
		int yx1 = kilnLeft ? w - 1 : (kiln ? kx0 - 1 : w - 1);
		if (wine) {
			jars(d, yx0, yx1, fz, hz0 - 1, door);
		} else {
			hops(d, yx0, yx1, fz, hz0 - 1, door);
		}
		Side front = hall.side(0, Dirs.FRONT);
		if (!Signs.bracket(d, front, front.index(door + 2), 3, "brewery", "Brewery")) {
			Signs.bracket(d, front, front.index(door - 1), 3, "brewery", "Brewery");
		}
		List<int[]> gaps = new ArrayList<>();
		gaps.add(new int[]{door, 0});
		gaps.add(new int[]{door + 1, 0});
		if (!kiln) {
			Yard.enclose(d, 0, 0, w - 1, hz0 - 1, gaps, Dirs.BACK);
		}
		Yard.connect(d, ex, door, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(hall.top, d.maxHeight() - 2) + 1);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** The brewhouse: coppers on their fire, the stands on a bench, barrel racks along the back wall, the chest. */
	private static void interior(Draft d, Hall hall, int door, int flueX, boolean wine) {
		int[] in = hall.inner(0);
		hall.openLoft(1);
		Floorplan fp = hall.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		fp.keep(door + 1, in[1]);
		// the copper: two cauldrons over a fire bed by the flue (the stack rises from the corner behind)
		int dir = flueX == in[0] ? 1 : -1;
		fp.block(flueX, in[3]);
		for (int k = 0; k < 2; k++) {
			int x = flueX;
			int z = in[3] - 1 - k;
			if (fp.take(x, z, flueX == in[0] ? Dirs.RIGHT : Dirs.LEFT)) {
				Props.station(d, Blocks.WATER_CAULDRON, x, 0, z, flueX == in[0] ? Dirs.RIGHT : Dirs.LEFT, JobType.BREWER);
				d.inStage(StageKind.INTERIOR, () -> d.set(x, 0, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
			}
		}
		// the barrel racks along the back wall, two tiers
		List<int[]> back = fp.along(Dirs.BACK);
		int racked = 0;
		for (int[] c : back) {
			if (Math.abs(c[0] - flueX) <= 1) {
				continue;
			}
			if (racked < 5 && fp.take(c[0], c[1], null)) {
				Props.barrelRack(d, c[0], 0, c[1], Dirs.RIGHT, 1, Math.min(2, hall.body.height(0) - 2), Dirs.FRONT);
				racked++;
			}
		}
		if (racked > 0) {
			// the cellarman draws from the rack's end
			int[] c = back.get(dir > 0 ? back.size() - 1 : 0);
			d.poi(PoiType.STORAGE, c[0], 0, c[1] - 1, Dirs.BACK);
		}
		// the stands on a bench against the front wall
		int stands = 0;
		for (int[] c : fp.along(Dirs.FRONT)) {
			if (stands >= 2 || Math.abs(c[0] - door) <= 1 || c[0] == door + 1) {
				continue;
			}
			if (fp.take(c[0], c[1], Dirs.BACK)) {
				int x = c[0];
				int z = c[1];
				d.inStage(StageKind.INTERIOR, () -> {
					d.set(x, 0, z, d.mat(Role.FURNITURE).slab(SlabType.TOP));
					d.set(x, 1, z, Blocks.BREWING_STAND.defaultBlockState());
				});
				d.poi(PoiType.JOB_SITE, x, 0, z + 1, Dirs.FRONT, JobType.BREWER);
				stands++;
			}
		}
		for (int[] c : fp.along(flueX == in[0] ? Dirs.RIGHT : Dirs.LEFT)) {
			Direction into = flueX == in[0] ? Dirs.LEFT : Dirs.RIGHT;
			if (c[1] > in[1] && fp.take(c[0], c[1], into)) {
				Props.store(d, c[0], 0, c[1], into, true, List.of(new Object[]{"minecraft:wheat", 32}, new Object[]{"minecraft:sugar", 12},
					new Object[]{"minecraft:glass_bottle", 16}, new Object[]{wine ? "minecraft:sweet_berries" : "minecraft:honey_bottle", 8}));
				break;
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
	}

	/**
	 * The oast: a square kiln of masonry (round on broad plots) rising above the brewhouse, a steep pyramid or cone roof
	 * and the white cowl on top, a door at its foot.
	 */
	private static void oast(Draft d, int x0, int x1, int z0, int z1, Hall hall) {
		Mat wall = d.mat(Role.PLINTH);
		Mat upper = d.mat(Role.WALL);
		Mat roofMat = d.mat(Role.ROOF);
		int size = x1 - x0 + 1;
		if (z1 - z0 + 1 < size) {
			z0 = z1 - size + 1;
		}
		if (z0 < 0) {
			return;
		}
		boolean round = size >= 5 && d.chance(0.6);
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		double r = (size - 1) / 2.0;
		int wallTop = Math.max(hall.plateY() + 2, d.maxHeight() - 9);
		if (wallTop < 4) {
			return;
		}
		Groundwork.footings(d, x0, z0, x1, z1, -1);
		Floors.boards(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, -1);
		List<int[]> ring = round ? Draft.ellipseCells(cx, cz, r, r, false) : Draft.ring(x0, z0, x1, z1);
		List<int[]> disc = round ? Draft.ellipseCells(cx, cz, r, r, true) : null;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : ring) {
				for (int y = 0; y <= wallTop; y++) {
					d.set(c[0], y, c[1], y < 3 ? wall.full() : upper.full());
				}
			}
		}
		if (round) {
			for (int[] c : disc) {
				boolean edge = false;
				for (int[] e : ring) {
					edge |= e[0] == c[0] && e[1] == c[1];
				}
				if (!edge) {
					d.carve(c[0], 0, c[1], c[0], wallTop, c[1]);
				}
			}
		} else {
			d.carve(x0 + 1, 0, z0 + 1, x1 - 1, wallTop, z1 - 1);
		}
		// a door at its foot toward the street, a little window high up
		int dx = (int) Math.round(cx);
		int dz = z0;
		for (int[] c : ring) {
			if (c[0] == dx && c[1] < dz + 1) {
				dz = c[1];
			}
		}
		int fdz = dz;
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, 0, fdz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
			net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		if (fdz - 1 >= 0) {
			d.entrance(dx, 0, fdz - 1);
		}
		d.inStage(StageKind.OPENINGS, () -> d.set(dx, wallTop - 1, fdz, Blocks.AIR.defaultBlockState()));
		// the roof: a steep cone or pyramid, the cowl on top
		Roofs.Field f = round ? Roofs.cone(cx, cz, r + 1, 2.0) : Roofs.hip(x0 - 1, z0 - 1, x1 + 1, z1 + 1, Roofs.Profile.straight(2.0));
		Roofs.Roof roof = Roofs.raster(d, f, wallTop, Math.max(0, x0 - 1), Math.max(-1, z0 - 1), Math.min(d.frontage() - 1, x1 + 1), z1 + 1, roofMat,
			d.mat(Role.ROOF_TRIM), Habits.Ridge.FULL);
		dev.voidmc.hearth.build.kit.trade.Shed.solidify(d, roof, roofMat);
		for (int[] c : ring) {
			int under = roof.bottomY(c[0], c[1]);
			for (int y = wallTop + 1; y < under; y++) {
				d.set(c[0], y, c[1], upper.full());
			}
		}
		int ax = (int) Math.round(cx);
		int az = (int) Math.round(cz);
		int top = roof.ridgeY + 1;
		Block white = Blocks.WOOL.pick(DyeColor.WHITE);
		if (top + 1 < d.maxHeight()) {
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(ax, top, az, white.defaultBlockState());
				d.set(ax, top + 1, az, Props.block("minecraft:white_wool_slab", white).defaultBlockState()
					.trySetValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
			}
			// the cowl's vane
			if (top + 2 < d.maxHeight()) {
				d.inStage(StageKind.ROOF, () -> d.set(ax, top + 2, az, Hall.woodFence(d).fence()));
			}
		}
		// inside: the drying floor's slatted ceiling and sacks of malt
		Props.sacks(d, (int) Math.round(cx) - 1, 0, (int) Math.round(cz) + 1, Dirs.RIGHT, 1, false);
	}

	/** Hop bines on their poles in rows across the yard, a path between. */
	private static void hops(Draft d, int x0, int x1, int z0, int z1, int door) {
		Yard.trodden(d, x0, z0, x1, z1, 0);
		Mat pole = Hall.woodFence(d);
		Block leaves = Blocks.AZALEA_LEAVES;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = x0 + 1; x <= x1 - 1; x += 2) {
				if (Math.abs(x - door) <= 1 || x == door + 1 || x == door + 2) {
					continue;
				}
				for (int z = z0 + 1; z <= z1 - 1; z += 2) {
					d.set(x, -1, z, Blocks.GRASS_BLOCK.defaultBlockState());
					d.set(x, 0, z, pole.fence());
					d.set(x, 1, z, pole.fence());
					d.set(x, 2, z, States.leaves(Noise.unit(d.seed(), x, 2, z) < 0.5 ? Blocks.FLOWERING_AZALEA_LEAVES : leaves));
					d.set(x, 3, z, States.leaves(leaves));
				}
			}
		}
	}

	/** Great jars in rows under a pergola of vines (wine and rice-wine country). */
	private static void jars(Draft d, int x0, int x1, int z0, int z1, int door) {
		Yard.pave(d, x0, z0, x1, z1, 0);
		Mat post = Hall.woodFence(d);
		Mat beam = d.mat(Role.BEAM);
		int px0 = x0 + 1;
		int px1 = x1 - 1;
		if (px1 - px0 < 2 || z1 - z0 < 2) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = px0; x <= px1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edgeX = x == px0 || x == px1;
					boolean edgeZ = z == z0 || z == z1;
					if (edgeX && edgeZ) {
						d.set(x, 0, z, post.fence());
						d.set(x, 1, z, post.fence());
						d.set(x, 2, z, beam.log(Direction.Axis.Y));
					}
					if (edgeZ || (x - px0) % 2 == 0) {
						d.set(x, 3, z, States.leaves(Noise.unit(d.seed(), x, 3, z) < 0.3 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
					}
				}
			}
			// the beams the vines rest on
			for (int x = px0; x <= px1; x++) {
				d.set(x, 2, z0, beam.log(Direction.Axis.X));
				d.set(x, 2, z1, beam.log(Direction.Axis.X));
			}
		}
		for (int x = px0 + 1; x < px1; x++) {
			if (Math.abs(x - door) <= 1 || x == door + 1) {
				continue;
			}
			Props.jars(d, x, 0, z0 + 1, Dirs.BACK, Math.max(1, z1 - z0 - 2));
		}
	}
}
