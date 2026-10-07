package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Adobe;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.station.Mending;
import dev.voidmc.hearth.build.kit.station.Roofwork;
import dev.voidmc.hearth.build.kit.station.Shrines;
import dev.voidmc.hearth.build.kit.station.Study;
import dev.voidmc.hearth.build.kit.station.Verdure;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * {@code hearth:house/hermit_hut}: the hut of one who lives apart (docs/LIVING_WORLD.md §8.1), out past the fields. A
 * tiny cell under a low, mossy roof come down close to the ground, one shuttered window; inside a bed, a fire against
 * the back wall and a lectern with the book always open. Before it a wayside shrine of the culture's kind by the path
 * (a stone pillar with its lamp on the Bohemian roads, a carved post, a stone lantern, a mud niche of candles, a cairn),
 * where passers-by stop; beside it the hermit's garden of herbs and roots with a hive at its end, fenced against the
 * deer; a bench at the door, a woodpile, a rain barrel, ferns and grass grown up all round. The north lays its roof in
 * turf, the dry lands build a mud cell with a flat roof, raised lands lift it on piles.
 *
 * <p>Params: {@code station}, {@code household} (shared); {@code roof} (RoofForm name); {@code variant} (int).
 */
public final class HermitHut implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/hermit_hut"), ArchetypeCategory.HOUSING, Set.of(), 1, 6, 8, 7, 9, 7, 9,
		SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 1, 0.5);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Traits t = Traits.of(d);
		boolean hutLeft = d.chance(0.5);
		int bw = 5;
		int bd = 5;
		// a raised hut stands in the middle of its plot, its broad eaves clear of the edges
		boolean raisedHabit = StationHomes.raised(d, 0, dp - bd, w - 1, dp - 1);
		int bx0 = raisedHabit ? (w - bw) / 2 : hutLeft ? 0 : w - bw;
		int bx1 = bx0 + bw - 1;
		int bz0 = dp - bd;
		int bz1 = dp - 1;
		boolean raised = StationHomes.raised(d, bx0, bz0, bx1, bz1);
		boolean flat = t.flat();
		int base = raised ? StationHomes.raisedBase(d, bx0, bz0, bx1, bz1, Math.max(1, bz0 - 1)) : 0;
		Body body = new Body(bx0, bz0, bx1, bz1, base, new int[]{flat ? 4 : 3}, false, false);
		d.body(bx0, bz0, bx1, bz1);
		int plate = body.plateY();
		d.eave(plate);
		int door = bx0 + 2;

		if (raised) {
			Groundwork.stilts(d, bx0, bz0, bx1, bz1, base - 1, 2);
			Floors.boards(d, bx0, bz0, bx1, bz1, base - 1);
		} else {
			Groundwork.footings(d, bx0, bz0, bx1, bz1, base - 1);
			StationHomes.earthFloor(d, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, base - 1);
			Groundwork.apron(d, bx0, bz0, bx1, bz1, base, 2);
		}
		StationHomes.lowWalls(d, body, flat ? 0 : 0.12);
		d.carve(bx0 + 1, base, bz0 + 1, bx1 - 1, plate - (flat ? 1 : 0), bz1 - 1);
		Side front = body.side(0, Dirs.FRONT);
		Openings.door(d, front, door, base, 1, new Openings.DoorOpts(false, false, true));
		Side garden = body.side(0, hutLeft ? Dirs.RIGHT : Dirs.LEFT);
		Hovel.hatch(d, garden, garden.middle(), base + 1);

		// the roof: low and mossy, or a flat roof of mud
		int[] r = body.rect(0);
		Roofs.Roof roof = null;
		int top;
		if (flat) {
			int deck = Roofs.flat(d, bx0, bz0, bx1, bz1, plate, false);
			Adobe.parapet(d, bx0, bz0, bx1, bz1, deck - 1, Adobe.Parapet.ROUNDED);
			top = deck + 2;
		} else {
			RoofForm form = HouseBuilder.roofParam(d, d.pick(RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP));
			if (t.eastern()) {
				form = RoofForm.HIP;
			}
			Direction.Axis ridge = d.chance(0.5) ? Direction.Axis.Z : Direction.Axis.X;
			boolean turf = t.walls() == WallKind.LOG && d.mat(Role.GROUND).main(Form.FULL) == Blocks.GRASS_BLOCK && d.chance(0.6);
			if (turf) {
				roof = HouseBuilder.turfRoof(d, body, ridge);
			} else {
				Mat thatch = d.mat(Role.THATCH);
				Mat cover = d.habits().roof().thatch() ? d.mat(Role.ROOF)
					: thatch.main(Form.FULL) == Blocks.HAY_BLOCK && thatch.has(Form.STAIRS) && d.chance(0.6) ? thatch : d.mat(Role.ROOF);
				int eaves = raised ? 2 : 1;
				double pitch = StationHomes.hutPitch(d, bx0, bz0, bx1, bz1, plate, form, ridge, 1.0, eaves, 1);
				roof = StationHomes.hutRoof(d, bx0, bz0, bx1, bz1, plate, form, ridge, pitch, eaves, cover, null, 0);
				Bohemia.carryRidge(d, roof);
				if (cover.main(Form.FULL) == Blocks.HAY_BLOCK) {
					Roofwork.thick(d, roof, cover);
				}
				if (form != RoofForm.HIP) {
					Paint boards = t.walls() == WallKind.STONE || t.walls() == WallKind.ADOBE || t.walls() == WallKind.BRICK ? d.mat(Role.WALL).full()
						: t.walls() == WallKind.LOG ? d.mat(Role.WALL).log(ridge == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X)
						: d.mat(Role.GABLE).full();
					StationHomes.gableFill(d, roof, ridge, r, plate, boards);
					if (Bohemia.is(d)) {
						Bohemia.gables(d, roof, ridge, r, plate);
					}
					Roofwork.verges(d, roof, ridge, d.mat(Role.ROOF_TRIM));
				}
				// moss come over the roof of one who lives alone
				mossy(d, roof);
			}
			for (int z = bz0 + 1; z <= bz1 - 1; z++) {
				for (int x = bx0 + 1; x <= bx1 - 1; x++) {
					int under = roof.covers(x, z) ? roof.bottomY(x, z) : plate + 1;
					for (int y = plate; y < under; y++) {
						if (d.isAbsent(x, y, z)) {
							d.air(x, y, z);
						}
					}
				}
			}
			top = roof.ridgeY + 2;
		}

		// inside: the bed, the fire, the lectern with its book, a chest
		Rooms.Room room = new Rooms.Room(bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, base, 2);
		StationHomes.fireAndBeds(d, room, door, 1, true);
		int lx = door + 1;
		int lz = room.z1;
		if (room.claim(lx, lz)) {
			Study.lectern(d, lx, base, lz, Dirs.FRONT, "Psalter", List.of("Blessed is the man that walketh not in the counsel of the ungodly.",
				"He shall be like a tree planted by the rivers of water."));
		}
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		Household.dress(d, room, room, null);

		// the way to the street, the shrine by it, the garden beside the hut
		if (raised) {
			Groundwork.approach(d, door, base, bz0 - 1);
		} else {
			if (!d.site().anyWater(0, 0, w - 1, bz0 - 1)) {
				Groundwork.level(d, 0, 0, w - 1, bz0 - 1, 0);
			}
			Groundwork.approach(d, door, base, bz0 - 1);
			Garden.path(d, Garden.line(door, bz0 - 1, door, 0), 0);
		}
		if (flat && !raised) {
			// the dry lands' cell: a lean-to of poles and reeds along its front, shading the door and the bench
			if (!Adobe.leanTo(d, bx0, bz0 - 2, bx1, bz0 - 1, base, Dirs.BACK)) {
				Adobe.doorShade(d, door, bz0, base);
			}
		}
		Hovel.doorLight(d, door, base, bz0 - 1, !hutLeft);
		shrine(d, door, bz0, hutLeft);
		garden(d, body, hutLeft);
		yard(d, body, door, hutLeft);
		if (!raised) {
			Verdure.foot(d, bx0, bz0, bx1, bz1, 0.8);
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, base, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}

	/** Moss come over the shingles: full moss and moss carpet on the roof's surface in patches, never on its eave row. */
	private static void mossy(Draft d, Roofs.Roof roof) {
		long seed = Noise.hash(d.seed(), 0x3055);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = roof.z0; z <= roof.z1; z++) {
				for (int x = roof.x0; x <= roof.x1; x++) {
					int y = roof.surfaceY(x, z);
					if (y == Integer.MIN_VALUE || Roofwork.edge(roof, x, z) || Noise.value(seed, x * 0.5, y * 0.5, z * 0.5) < 0.62) {
						continue;
					}
					var st = d.get(x, y, z);
					if (st != null && st.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
						&& !st.is(Blocks.HAY_BLOCK) && d.isEmpty(x, y + 1, z) && y + 1 < d.maxHeight()) {
						d.set(x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
					}
				}
			}
		}
	}

	/**
	 * The wayside shrine near the street beside the path, on the side away from the hut (else wherever the yard has
	 * ground for it), facing the path or the street.
	 */
	private static void shrine(Draft d, int door, int bz0, boolean hutLeft) {
		int w = d.frontage();
		int side = hutLeft ? 1 : -1;
		List<int[]> tries = new ArrayList<>();
		for (int dz = 1; dz <= Math.max(1, bz0 - 1); dz++) {
			tries.add(new int[]{door + side * 2, dz, hutLeft ? 3 : 1});
			tries.add(new int[]{door + side * 3, dz, 0});
			tries.add(new int[]{door - side * 2, dz, hutLeft ? 1 : 3});
		}
		tries.add(new int[]{hutLeft ? w - 1 : 0, 1, hutLeft ? 3 : 1});
		for (int[] c : tries) {
			if (c[0] < 0 || c[0] >= w || c[1] < 0 || c[1] >= d.depth()) {
				continue;
			}
			Direction face = Dirs.HORIZONTAL[c[2]];
			int y = Spots.level(d, c[0], c[1]);
			if (Shrines.wayside(d, c[0], y, c[1], face)) {
				return;
			}
		}
		// no ground there: anywhere in the yard it can stand, facing any way with a clear cell before it
		for (int z = 0; z < d.depth(); z++) {
			for (int x = 0; x < w; x++) {
				for (Direction face : Dirs.HORIZONTAL) {
					if (Shrines.wayside(d, x, Spots.level(d, x, z), z, face)) {
						return;
					}
				}
			}
		}
	}

	/**
	 * The hermit's garden in the strip beside the hut: rows of roots and herbs on tilled ground (the culture's crops, its
	 * flowers among them), a hive at the far end, fenced on its open side.
	 */
	private static void garden(Draft d, Body b, boolean hutLeft) {
		int[] r = b.rect(0);
		int x0 = hutLeft ? r[2] + 1 : 0;
		int x1 = hutLeft ? d.frontage() - 1 : r[0] - 1;
		if (x1 < x0) {
			return;
		}
		int z0 = r[1];
		int z1 = r[3];
		Mat crops = d.mat(Role.CROPS);
		long seed = Noise.hash(d.seed(), 0x6A2D);
		boolean fenced = x1 - x0 >= 1;
		int fenceX = hutLeft ? x1 : x0;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (fenced && x == fenceX && x1 - x0 >= 2) {
					continue;
				}
				int y = Spots.level(d, x, z);
				if (y != b.baseY || !Spots.standable(d, x, y, z) || Spots.claimed(d, x, y, z) || d.site().isWater(x, z)) {
					continue;
				}
				if (z == z1 && x == (hutLeft ? x0 : x1)) {
					if (Bohemia.is(d)) {
						Bohemia.hive(d, x, y, z, hutLeft ? Dirs.RIGHT : Dirs.LEFT);
					} else {
						Garden.beehive(d, x, y, z, hutLeft ? Dirs.RIGHT : Dirs.LEFT);
					}
					continue;
				}
				double u = Noise.unit(seed, x, 0, z);
				if ((z - z0) % 3 == 2) {
					Garden.flower(d, x, y, z);
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, y - 1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					Block crop = crops.block(Form.FULL, 0, y, z);
					d.set(x, y, z, crop instanceof CropBlock ? States.crop(crop, 3 + (int) (u * 5)) : crop.defaultBlockState());
				}
			}
		}
		if (Traits.of(d).flat() && b.baseY == 0) {
			// the dry lands wall a garden in mud: along its open side and across its street end, a gap by the hut
			List<int[]> wall = new ArrayList<>();
			if (x1 - x0 >= 2) {
				for (int z = z0; z <= z1; z++) {
					wall.add(new int[]{fenceX, z});
				}
			}
			int gate = hutLeft ? x0 : x1;
			for (int x = x0; x <= x1 && z0 >= 1; x++) {
				if (x != gate) {
					wall.add(new int[]{x, z0 - 1});
				}
			}
			Adobe.yardWall(d, wall, b.baseY);
		} else if (fenced && x1 - x0 >= 2) {
			List<int[]> line = new ArrayList<>();
			for (int z = z0; z <= z1; z++) {
				line.add(new int[]{fenceX, z});
			}
			Garden.fence(d, line, 0, hutLeft ? Dirs.RIGHT : Dirs.LEFT);
			if (Bohemia.is(d)) {
				Bohemia.wattle(d, fenceX, z0, fenceX, z1);
			}
		}
	}

	/** A bench by the door, a woodpile against the hut's back corner, a rain barrel. */
	private static void yard(Draft d, Body b, int door, boolean hutLeft) {
		int[] r = b.rect(0);
		int y = b.baseY;
		int z = r[1] - 1;
		if (z >= 0) {
			int bx = door + (hutLeft ? -1 : 1);
			if (bx > r[0] && bx < r[2] && Spots.standable(d, bx, y, z) && !Spots.claimed(d, bx, y, z) && d.isEmpty(bx, y + 1, z)) {
				Furniture.chair(d, bx, y, z, Dirs.BACK);
			}
		}
		int wx = hutLeft ? r[0] : r[2];
		int sx = hutLeft ? r[2] + 1 : r[0] - 1;
		if (sx >= 0 && sx < d.frontage() && z >= 0 && Spots.standable(d, sx, y, r[1]) && !Spots.claimed(d, sx, y, r[1])) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(sx, y, r[1], Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
		}
		if (wx >= 0) {
			int ox = hutLeft ? -1 : 1;
			int px = wx + ox;
			if (px >= 0 && px < d.frontage()) {
				Garden.woodpile(d, px, y, r[3] - 1, Dirs.FRONT, 2);
			}
		}
	}
}
