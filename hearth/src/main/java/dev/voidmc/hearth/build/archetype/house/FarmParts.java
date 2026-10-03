package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The house of a farmstead: a body of one or two storeys with its door on its front, drawn with the shared house steps
 * (walls, openings, a pitched roof with its cross gable or dormers, or a flat roof terrace), its hearth and chimney, a
 * stair and rooms. The farmhouse's layouts place one of these and build the barn and the yard round it.
 */
final class FarmParts {
	private FarmParts() {
	}

	/** What a farm house drew. */
	record House(Body body, Facade facade, Roofs.Roof roof, int deck, Integer door) {
	}

	/** Roof forms a farmhouse can take. */
	static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.CROSS_GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.SALTBOX,
		RoofForm.PAGODA, RoofForm.FLAT};

	/**
	 * A house over {@code [x0..x1] × [z0..z1]} at floor level y: {@code storeys} full storeys (plus an attic under a steep
	 * roof), the ridge along {@code ridge}, the door in its front near {@code doorCol}. Its footings reach the ground;
	 * returns what it drew.
	 */
	static House house(Draft d, int x0, int z0, int x1, int z1, int y, int storeys, RoofForm form, Direction.Axis ridge, int doorCol, boolean porch) {
		Habits h = d.habits();
		boolean flat = form == RoofForm.FLAT;
		boolean low = !flat && h.roof().pitch() < 0.9;
		int sh = Math.max(4, h.walls().storeyHeight());
		boolean attic = !flat && !low;
		int[] heights = new int[storeys + (attic ? 1 : 0)];
		java.util.Arrays.fill(heights, sh);
		if (attic) {
			heights[storeys] = 1;
		}
		Body body = new Body(x0, z0, x1, z1, y, heights, false, false);
		if (storeys >= 2 && d.chance(h.walls().jetty()) && z0 >= 1) {
			body.jetty(1, Dirs.FRONT);
		}
		d.body(x0, z0, x1, z1);
		int bw = x1 - x0 + 1;
		int windowWidth = h.windows().tall() && bw >= 8 && d.chance(0.35) ? 2 : 1;
		Facade facade = Facade.plan(d, body, doorCol, 1, windowWidth, 0.45);
		Integer door = HouseBuilder.doorColumn(facade);
		Groundwork.footings(d, x0, z0, x1, z1, y - 1);
		HouseBuilder.shell(d, body, facade);
		dev.voidmc.hearth.build.kit.homestead.Logs.sill(d, x0, z0, x1, z1, y);
		HouseBuilder.openings(d, body, facade, true);
		Side front = body.side(0, Dirs.FRONT);
		if (porch && door != null && z0 >= 2 && d.chance(Math.max(0.35, h.porch()))) {
			Porches.porch(d, front, Math.max(x0, door - 1), Math.min(x1, door + 1), 2, y, 3, door);
		}
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			int[] tr = body.topRect();
			deck = Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], body.plateY(), true);
			HouseBuilder.terraceAccess(d, body, deck);
		} else {
			roof = HouseBuilder.pitchedRoof(d, body, form, ridge, facade, body.storeys() - 1);
		}
		// the hearth on a gable end (or the back wall), its stack through the roof
		Direction chimneySide = ridge == Direction.Axis.X ? (d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT) : Dirs.BACK;
		int[] hearth = HouseBuilder.hearthSpot(d, body, chimneySide, facade);
		if (hearth != null && d.chance(Math.max(0.6, h.chimney()))) {
			Furniture.hearth(d, hearth[0], y, hearth[1], hearth[2] == 0 ? Dirs.RIGHT : hearth[2] == 1 ? Dirs.LEFT : Dirs.FRONT, false);
			if (roof != null) {
				Chimneys.stack(d, hearth[0], hearth[1], y + 2, roof, roof.ridgeY + 1);
			} else {
				HouseBuilder.chimneyFlat(d, hearth[0], hearth[1], deck);
			}
		} else {
			hearth = null;
		}
		HouseBuilder.interior(d, body, facade, roof, hearth, null, stairRunsRight(d, body, hearth));
		ensureBeds(d, body, door, 2);
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, y, door);
		}
		return new House(body, facade, roof, deck, door);
	}

	/**
	 * Makes sure a house sleeps at least {@code min}: counts the beds drawn inside its body and, if short, furnishes a
	 * bedroom corner on its ground floor, keeping the door's inside cell and every stair's foot and head free.
	 */
	static void ensureBeds(Draft d, Body b, Integer door, int min) {
		ensureBeds(d, b, door, min, null);
	}

	/** {@link #ensureBeds} keeping one more cell free (a back door's inside cell). */
	static void ensureBeds(Draft d, Body b, Integer door, int min, int[] keep) {
		int[] r = b.rect(0);
		int count = 0;
		for (int k = 0; k < b.storeys(); k++) {
			int y = b.floorY(k);
			for (int z = r[1]; z <= r[3]; z++) {
				for (int x = r[0]; x <= r[2]; x++) {
					var st = d.get(x, y, z);
					if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.BedBlock
						&& st.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
						count++;
					}
				}
			}
		}
		if (count >= min) {
			return;
		}
		int y = b.floorY(0);
		dev.voidmc.hearth.build.kit.Rooms.Room room = new dev.voidmc.hearth.build.kit.Rooms.Room(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, y, b.height(0) - 1);
		if (door != null) {
			room.keep(door, r[1] + 1);
		}
		if (keep != null) {
			room.keep(keep[0], keep[1]);
		}
		// every stair's neighbours along its run stay free (its foot and the cell beyond its head)
		for (int z = r[1] + 1; z <= r[3] - 1; z++) {
			for (int x = r[0] + 1; x <= r[2] - 1; x++) {
				var st = d.get(x, y, z);
				if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
					Direction f = st.getValue(net.minecraft.world.level.block.StairBlock.FACING);
					room.keep(x - Dirs.dx(f), z - Dirs.dz(f));
				}
				if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.LadderBlock) {
					for (Direction dir : Dirs.HORIZONTAL) {
						if (room.inside(x + Dirs.dx(dir), z + Dirs.dz(dir)) && d.isEmpty(x + Dirs.dx(dir), y, z + Dirs.dz(dir))) {
							room.keep(x + Dirs.dx(dir), z + Dirs.dz(dir));
							break;
						}
					}
				}
			}
		}
		dev.voidmc.hearth.build.kit.Rooms.furnish(d, room, dev.voidmc.hearth.build.kit.Rooms.Type.BEDROOM, min - count, JobType.NONE);
	}

	/**
	 * Which way the back-wall stair climbs (the cottage's rule: its foot away from the hearth, so the way onto it is
	 * clear; the house must be deep enough that its landing does not meet the chimney breast): true to climb right.
	 */
	static boolean stairRunsRight(Draft d, Body b, int[] hearth) {
		return hearth != null ? hearth[2] == 1 : d.chance(0.5);
	}

	/** The roof form a farm house takes in this culture (a gambrel and a saltbox are farmhouse forms too). */
	static RoofForm form(Draft d) {
		RoofForm f = HouseBuilder.roofParam(d, d.habits().roof().pick(d.random(), FORMS));
		return f;
	}

	/** Storeys that fit under the plot's height: two where they fit with their roof, else one. */
	static int storeys(Draft d, int bw, int bd, RoofForm form, int want) {
		int sh = Math.max(4, d.habits().walls().storeyHeight());
		double pitch = Math.max(0.5, d.habits().roof().pitch());
		int span = Math.min(bw, bd);
		int roofH = form == RoofForm.FLAT ? 3 : (int) Math.ceil(pitch * (span / 2.0 + 1)) + 2;
		for (int s = Math.max(1, want); s >= 1; s--) {
			if (s * sh + (form == RoofForm.FLAT ? 0 : 1) + roofH < d.maxHeight()) {
				return s;
			}
		}
		return 1;
	}

	/** A woodpile against a wall run, under its own little roof (where the ground is level and free). */
	static void woodpile(Draft d, int x, int y, int z, Direction run, int len) {
		for (int i = 0; i < len; i++) {
			int px = x + Dirs.dx(run) * i;
			int pz = z + Dirs.dz(run) * i;
			if (!Spots.standable(d, px, y, pz) || Spots.level(d, px, pz) != y) {
				return;
			}
		}
		Garden.woodpile(d, x, y, z, run, len);
	}

	/** A cell is free yard: standable at y on ground drawn as yard. */
	static boolean freeYard(Draft d, int x, int y, int z) {
		return x >= 1 && z >= 1 && x < d.frontage() - 1 && z < d.depth() - 1 && Spots.standable(d, x, y, z) && d.isAbsent(x, y, z);
	}

	/** A kitchen garden over a rectangle: levelled to its own ground (a little terrace on a slope), the culture's garden manner. */
	static void kitchenGarden(Draft d, int x0, int z0, int x1, int z1, int unused) {
		if (x1 - x0 < 1 || z1 - z0 < 1 || x0 < 1 || z0 < 1 || x1 > d.frontage() - 2 || z1 > d.depth() - 2) {
			return;
		}
		int y = d.site().meanGround(x0, z0, x1, z1);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int lv = Spots.level(d, x, z);
				if (!d.isAbsent(x, lv, z) || !d.isAbsent(x, y, z) || Math.abs(lv - y) > 2 || d.site().isWater(x, z)) {
					return;
				}
			}
		}
		Groundwork.level(d, x0, z0, x1, z1, y);
		Habits.GardenKind kind = d.habits().yard().garden();
		if (kind != Habits.GardenKind.KITCHEN && kind != Habits.GardenKind.COTTAGE || x1 - x0 < 2 || z1 - z0 < 2) {
			Garden.garden(d, x0, z0, x1, z1, y);
			return;
		}
		// rows of the culture's crops on a watered bed, the classic potager
		Garden.kitchenRows(d, x0, z0, x1, z1, y, x1 - x0 >= z1 - z0 ? Dirs.RIGHT : Dirs.BACK);
	}

	/** The farmyard's furniture scattered over free yard cells: a well or trough, a chopping block, barrels, a cart of hay. */
	static void yardThings(Draft d, int x0, int z0, int x1, int z1, int y, boolean well) {
		int placed = 0;
		if (well && x1 - x0 >= 4 && z1 - z0 >= 4) {
			int cx = (x0 + x1) / 2;
			int cz = (z0 + z1) / 2;
			boolean ok = true;
			for (int dz = -1; dz <= 1 && ok; dz++) {
				for (int dx = -1; dx <= 1 && ok; dx++) {
					ok = freeYard(d, cx + dx, y, cz + dz) && Spots.level(d, cx + dx, cz + dz) == y;
				}
			}
			if (ok && y + 5 < d.maxHeight()) {
				Yard.well(d, cx, y, cz, Dirs.FRONT);
				placed++;
			}
		}
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!freeYard(d, x, y, z) || Spots.level(d, x, z) != y) {
					continue;
				}
				double u = Noise.unit(d.seed() ^ 0xFA27, x, 0, z);
				// against a wall only, so the yard stays open
				boolean byWall = false;
				for (Direction dir : Dirs.HORIZONTAL) {
					byWall |= d.isSolid(x + Dirs.dx(dir), y, z + Dirs.dz(dir)) && d.isSolid(x + Dirs.dx(dir), y + 1, z + Dirs.dz(dir));
				}
				if (!byWall || u > 0.14) {
					continue;
				}
				int px = x;
				int pz = z;
				if (u < 0.05) {
					d.inStage(StageKind.EXTERIOR, () -> d.set(px, y, pz, States.facing(Blocks.BARREL, Direction.UP)));
				} else if (u < 0.08) {
					Yard.choppingBlock(d, px, y, pz, Dirs.FRONT);
				} else if (u < 0.11) {
					d.inStage(StageKind.EXTERIOR, () -> d.set(px, y, pz, Blocks.HAY_BLOCK.defaultBlockState()));
				} else {
					Garden.waterButt(d, px, y, pz);
				}
			}
		}
	}

	/** A paved or trodden yard surface over free cells (packed earth, gravel and the culture's paving in patches). */
	static void yardSurface(Draft d, int x0, int z0, int x1, int z1, int y) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (!Spots.standable(d, x, y, z) || Spots.level(d, x, z) != y || !Garden.groundLike(d, x, y - 1, z)) {
						continue;
					}
					double u = Noise.value(d.seed() ^ 0xA4D, x / 2.2, 0, z / 2.2);
					if (u < 0.3) {
						d.set(x, y - 1, z, d.mat(Role.PAVING).full());
					} else if (u < 0.55) {
						d.set(x, y - 1, z, d.mat(Role.PATH).full());
					} else if (u < 0.7) {
						d.set(x, y - 1, z, Blocks.COARSE_DIRT.defaultBlockState());
					}
				}
			}
		}
	}

	/** Whether the culture's walls are timber (frame, plank, log, bamboo): its barns are boarded. */
	static boolean timber(Draft d) {
		WallKind k = d.habits().walls().kind();
		return k == WallKind.TIMBER_FRAME || k == WallKind.PLANK || k == WallKind.LOG || k == WallKind.BAMBOO;
	}

	/** A lantern on a post at a gate. */
	static void gateLamp(Draft d, int x, int y, int z) {
		if (Spots.standable(d, x, y, z) && d.isAbsent(x, y, z) && y + 3 < d.maxHeight()) {
			Garden.lampPost(d, x, y, z, 2, null);
		}
	}

	/** A bench by a door. */
	static void bench(Draft d, int x, int y, int z, Direction back) {
		if (freeYard(d, x, y, z)) {
			Yard.bench(d, x, y, z, back);
		}
	}

	/** Registers a farm worker's stand in the yard (FARMER), if the cell is free. */
	static void stand(Draft d, int x, int y, int z, Direction facing, JobType job) {
		Spots.at(d, PoiType.JOB_SITE, x, y, z, facing, job);
	}

	/** A window in a plain wall cell at y (a little opening for a byre or a store). */
	static void slit(Draft d, Side s, int i, int y) {
		if (i > 0 && i < s.last() && Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
			Openings.window(d, s, s.column(i), y, 1, 1, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, true, true));
		}
	}

	/** Unused here: a material helper. */
	static Mat roof(Draft d) {
		return d.mat(Role.ROOF);
	}

	/** Stairs facing a direction, for steps at doors. */
	static void step(Draft d, int x, int y, int z, Direction up) {
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, d.mat(Role.PAVING).stairsOrSlab(up, Half.BOTTOM)));
	}
}
