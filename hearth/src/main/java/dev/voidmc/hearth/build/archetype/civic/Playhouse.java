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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Signboard;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/playhouse}: the town's theatre, a galleried yard open to the sky. Round three sides a timber-framed
 * (or masonry) range of galleries, two tiers where the height allows, benches facing in, posts along the yard's edge
 * holding the gallery floor and the ring roof whose ridge runs round the yard; at the back the tiring house, taller,
 * under its own gable with the company's flag on its ridge, where the players dress; out of it, through two doors, the
 * stage thrust into the yard, its boards a step above the groundlings' heads, the heavens on two posts over it, the
 * company's cloth behind. In through the double doors of the front range under the board with the house's name.
 *
 * <p>POIs: STAGE and the ENTERTAINER's JOB_SITE, SEATs in every gallery, STORAGE (the costumes in the tiring house),
 * MEETING_POINT (the yard), the DOOR. Params: {@code name} (int), {@code variant} (int).
 */
public final class Playhouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/playhouse"), ArchetypeCategory.CIVIC, Set.of(JobType.ENTERTAINER), 0, 15,
		21, 15, 23, 10, 16, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	/** Cells of a gallery wing from the outer wall: 0 the wall, 1 the walk, 2 the benches, 3 the yard's edge. */
	private static final int G = 4;

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
		boolean masonry = t.masonry() || t.flatRoofed();
		boolean flatRoof = t.flatRoofed();
		int base = 0;
		int zF = 1;
		int zB = dp - 1;
		// the tiring house across the back: its inner wall at zT, three rows of rooms behind it
		int zT = zB - 4;
		boolean tiers = maxH - base >= 12;
		int tier = 4;
		int plate = tiers ? base + 2 * tier - 1 : base + tier;
		int upY = base + tier;
		// the tiring house stands two higher than the galleries where the height allows, its gable over their ring roof
		int plateT = plate + 2 + 5 < maxH ? plate + 2 : plate;

		// --- ground: the whole plot level with the street, water embanked
		Groundwork.level(d, 0, 0, w - 1, dp - 1, base, true);
		Mat paving = d.mat(Role.PAVING);
		Mat wall = masonry ? d.mat(Role.WALL) : d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat found = d.mat(Role.FOUNDATION);
		Mat roof = d.mat(Role.ROOF);
		Mat floor = d.mat(Role.FLOOR);

		int yx0 = G;
		int yx1 = w - 1 - G;
		int yz0 = zF + G;
		int yz1 = zT - 1;

		// --- the yard: beaten earth or paving, the groundlings stand here
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = zF + 1; z < zT; z++) {
				for (int x = 1; x <= w - 2; x++) {
					d.set(x, base - 1, z, ring(x, z, w, zF) < G ? floor.full() : Noise.unit(d.seed(), x, 3, z) < 0.7 ? paving.full() : d.mat(Role.PATH).full());
				}
			}
			for (int z = zT; z < zB; z++) {
				for (int x = 1; x <= w - 2; x++) {
					d.set(x, base - 1, z, found.full());
					d.set(x, base, z, floor.full());
				}
			}
		}

		// --- the outer walls round the whole, the tiring house's inner wall, footings
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = 0; x < w; x++) {
				for (int z = zF; z <= zB; z++) {
					boolean outer = x == 0 || x == w - 1 || z == zF || z == zB;
					boolean inner = z == zT && x > 0 && x < w - 1;
					if (!outer && !inner) {
						continue;
					}
					d.set(x, base - 1, z, found.full());
					int top = z >= zT ? plateT : plate;
					for (int y = base; y < top; y++) {
						d.set(x, y, z, wallCell(d, x, y, z, w, dp, zF, zB, base, top, tier, masonry, wall, frame, beam, found));
					}
				}
			}
		}
		// small windows in the upper tier and the tiring house
		try (var s = d.stage(StageKind.OPENINGS)) {
			int wy = tiers ? upY + 1 : base + 2;
			for (int x = 2; x < w - 2; x += 4) {
				if (Math.abs(x - w / 2) > 2) {
					d.set(x, wy, zF, glass(d));
				}
				d.set(x, wy + (tiers ? 0 : 0), zB, glass(d));
			}
			for (int z = zF + 2; z < zB - 1; z += 4) {
				d.set(0, wy, z, glass(d));
				d.set(w - 1, wy, z, glass(d));
			}
		}

		// --- the posts along the yard's edge, the gallery floor over the walk
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = G - 1; x <= w - G; x++) {
				for (int z = zF + G - 1; z < zT; z++) {
					if (ring(x, z, w, zF) != G - 1 || !post(x, z, w, zF)) {
						continue;
					}
					for (int y = base; y < plate; y++) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					}
				}
			}
		}
		if (tiers) {
			for (int x = 1; x <= w - 2; x++) {
				for (int z = zF + 1; z < zT; z++) {
					if (ring(x, z, w, zF) < G) {
						Floors.boards(d, x, z, x, z, upY - 1);
					}
				}
			}
			// the railing along the upper gallery's edge
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int x = 1; x <= w - 2; x++) {
					for (int z = zF + 1; z < zT; z++) {
						if (ring(x, z, w, zF) == G - 1 && d.isEmpty(x, upY, z)) {
							d.set(x, upY, z, d.mat(Role.RAILING).fence());
						}
					}
				}
			}
		}

		// --- the ring roof over the galleries, the tiring house's gable
		ringRoof(d, w, zF, zT, plate, flatRoof, roof);
		Roofs.Roof tr = null;
		if (!flatRoof && plateT + 4 < maxH - 1) {
			Roofs.Opts o = Roofs.Opts.of(d);
			double fits = (maxH - 3 - (plateT + 1)) / 3.0;
			double p = Math.min(1.5, Math.max(0.5, fits));
			p = p >= 1.5 ? 1.5 : p >= 1.0 ? 1.0 : 0.5;
			tr = Roofs.plan(d, 0, zT, w - 1, zB, plateT, t == Tradition.KARST ? RoofForm.PAGODA : RoofForm.GABLE, Direction.Axis.X, o.withPitch(p), false,
				false).draw(d).roof();
		} else {
			Roofs.flat(d, 0, zT, w - 1, zB, plateT, plateT + 2 <= maxH - 1);
		}
		// the company's flag on the tiring house
		int fy = tr != null ? tr.ridgeY + 1 : plateT + 1;
		if (fy + 3 < maxH) {
			try (var s = d.stage(StageKind.ROOF)) {
				for (int y = fy; y < fy + 3 && y < maxH - 1; y++) {
					d.set(w / 2, y, (zT + zB) / 2, frame.fence());
				}
			}
			Venue.banner(d, w / 2 + 1, fy + 2, (zT + zB) / 2, Dirs.RIGHT, d.chance(0.5) ? "red" : "yellow", List.of(), StageKind.ROOF);
		}

		// --- the stage thrust from the tiring house into the yard
		int sx0 = yx0 + 1;
		int sx1 = yx1 - 1;
		int sdp = Math.min(3, Math.max(2, (yz1 - yz0 + 1) / 3));
		int sz0 = zT - sdp;
		int sz1 = zT - 1;
		Venue.stage(d, sx0, sz0, sx1, sz1, base, Dirs.FRONT, true);
		// the two doors from the tiring house onto the stage
		for (int x : new int[]{sx0 + 1, sx1 - 1}) {
			d.inStage(StageKind.OPENINGS, () -> d.door(x, base + 1, zT, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
				DoorHingeSide.LEFT));
		}
		// the heavens: two posts at the stage's front corners holding a canopy over it
		int hy = Math.min(plate - 1, base + 5);
		if (sx1 - sx0 >= 4 && hy > base + 3) {
			try (var s = d.stage(StageKind.FRAME)) {
				for (int x : new int[]{sx0, sx1}) {
					for (int y = base + 1; y < hy; y++) {
						d.set(x, y, sz0, frame.fence());
					}
				}
				for (int x = sx0; x <= sx1; x++) {
					for (int z = sz0; z < zT; z++) {
						d.set(x, hy, z, roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : frame.full());
					}
				}
			}
			Lighting.chandelier(d, (sx0 + sx1) / 2, sz0 + 1, hy, hy - 2);
		}
		d.poi(PoiType.MEETING_POINT, w / 2, base, Math.max(yz0, sz0 - 2), Dirs.BACK);

		// --- the galleries: a bench round each tier facing the yard, gaps to get along
		benches(d, w, zF, zT, base);
		if (tiers) {
			benches(d, w, zF, zT, upY);
			// the stair up from the walk of the left wing near the front
			Stairs.straight(d, 1, base, zF + 2, Dirs.BACK, tier, 1);
		}

		// --- the tiring house: costume chests and a table, lit
		int tz0 = zT + 1;
		int tz1 = zB - 1;
		int k = 0;
		for (int x = 2; x <= w - 3 && k < 3; x += 3) {
			if (d.isEmpty(x, base + 1, tz1)) {
				Furniture.chest(d, x, base + 1, tz1, Dirs.FRONT, List.of(new Object[]{"minecraft:red_wool", 4}, new Object[]{"minecraft:leather_helmet", 1},
					new Object[]{"minecraft:feather", 3}));
				k++;
			}
		}
		if (d.isEmpty(1, base + 1, tz0)) {
			Furniture.barrel(d, 1, base + 1, tz0, Dirs.RIGHT);
		}
		Lighting.room(d, 1, tz0, w - 2, tz1, base + 1);

		// --- light the galleries and the yard
		for (int y : tiers ? new int[]{base, upY} : new int[]{base}) {
			Lighting.room(d, 1, zF + 1, w - 2, zF + G - 1, y);
			Lighting.room(d, 1, zF + G, G - 1, zT - 1, y);
			Lighting.room(d, w - G, zF + G, w - 2, zT - 1, y);
		}
		for (int[] c : new int[][]{{yx0, yz0}, {yx1, yz0}}) {
			if (Venue.standable(d, c[0], base, c[1])) {
				Lighting.post(d, c[0], base, c[1]);
			}
		}

		// --- the way in: double doors in the front range, the board over them, lamps
		int cx = w / 2;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = cx - 1; x <= cx; x++) {
				for (int z = zF + 1; z <= zF + G - 1; z++) {
					for (int y = base; y < base + 3; y++) {
						if (d.get(x, y, z) != null && !(d.get(x, y, z).isAir())) {
							d.set(x, y, z, Paint.of(Blocks.AIR.defaultBlockState()));
						}
					}
				}
			}
			d.set(cx - 1, base + 2, zF, beam.log(Direction.Axis.X));
			d.set(cx, base + 2, zF, beam.log(Direction.Axis.X));
		}
		Block2 doorBlock = new Block2(d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR));
		d.inStage(StageKind.OPENINGS, () -> {
			d.door(cx - 1, base, zF, doorBlock.b, Dirs.FRONT, DoorHingeSide.RIGHT);
			d.door(cx, base, zF, doorBlock.b, Dirs.FRONT, DoorHingeSide.LEFT);
		});
		d.entrance(cx, base, zF - 1);
		d.poi(PoiType.DOOR, cx, base, zF - 1, Dirs.BACK);
		Lighting.doorLamp(d, cx - 2, base + 2, zF - 1, Dirs.BACK);
		Lighting.doorLamp(d, cx + 1, base + 2, zF - 1, Dirs.BACK);
		List<String[]> name = Signboard.name(d, "playhouse", Signboard.PLAYHOUSES);
		Signboard.bracket(d, cx + 2, base + 3, zF, Dirs.FRONT, name);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = 0; x < w; x++) {
				if (d.isEmpty(x, base, 0)) {
					d.set(x, base - 1, 0, d.mat(Role.PATH).full());
				}
			}
		}
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, maxH - 1);
	}

	/** A block holder (a lambda needs an effectively final block). */
	private record Block2(net.minecraft.world.level.block.Block b) {
	}

	/** How far a cell lies in from the outer walls of the three gallery wings (0 = the wall). */
	private static int ring(int x, int z, int w, int zF) {
		return Math.min(Math.min(x, w - 1 - x), z - zF);
	}

	/** Posts along the yard's edge every third cell, and at its corners. */
	private static boolean post(int x, int z, int w, int zF) {
		boolean side = x == G - 1 || x == w - G;
		if (side && z == zF + G - 1) {
			return true;
		}
		return side ? (z - (zF + G - 1)) % 3 == 0 : (x - (G - 1)) % 3 == 0 || x == w - G;
	}

	private static Paint glass(Draft d) {
		return d.mat(Role.WINDOW).has(Form.PANE) ? d.mat(Role.WINDOW).pane() : Paint.of(Blocks.GLASS_PANE.defaultBlockState());
	}

	/**
	 * A cell of the outer walls: in the timber cultures a frame of posts at the corners and every fourth cell, a sill beam
	 * at each tier's floor, the infill between; in masonry, the wall on a plinth of the footing stone, quoins at corners.
	 */
	private static Paint wallCell(Draft d, int x, int y, int z, int w, int dp, int zF, int zB, int base, int plate, int tier, boolean masonry, Mat wall,
		Mat frame, Mat beam, Mat found) {
		boolean corner = (x == 0 || x == w - 1) && (z == zF || z == zB);
		boolean onX = z == zF || z == zB;
		int along = onX ? x : z;
		if (masonry) {
			if (y == base) {
				return found.full();
			}
			if (corner) {
				return d.mat(Role.WALL_ACCENT).full();
			}
			return wall.full();
		}
		if (y == base) {
			return found.full();
		}
		if (corner || along % 4 == 0) {
			return frame.log(Direction.Axis.Y);
		}
		if (y == base + tier - 1 || y == plate - 1) {
			return beam.log(onX ? Direction.Axis.X : Direction.Axis.Z);
		}
		return wall.full();
	}

	/**
	 * The ring roof over the three gallery wings: its ridge runs round the yard over the bench row, stairs of the roofing
	 * falling to the outer eaves and to the yard; the corners meet as hips. A flat deck behind a parapet in the dry south.
	 */
	private static void ringRoof(Draft d, int w, int zF, int zT, int plate, boolean flat, Mat roof) {
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = 0; x < w; x++) {
				for (int z = zF; z < zT; z++) {
					int k = ring(x, z, w, zF);
					if (k >= G) {
						continue;
					}
					if (flat) {
						d.set(x, plate, z, roof.has(Form.SLAB) ? roof.slab(SlabType.TOP) : roof.full());
						if (k == 0 && plate + 1 < d.maxHeight()) {
							d.set(x, plate + 1, z, d.mat(Role.WALL).full());
						}
						continue;
					}
					// the direction in which k grows (toward the ridge from the outer half, toward it from the yard's half)
					Direction in;
					int dx0 = x;
					int dx1 = w - 1 - x;
					int dz = z - zF;
					if (dz <= dx0 && dz <= dx1) {
						in = Dirs.BACK;
					} else if (dx0 <= dx1) {
						in = Dirs.RIGHT;
					} else {
						in = Dirs.LEFT;
					}
					int y = plate + (k == 0 || k == G - 1 ? 0 : 1);
					Direction facing = k < G / 2 ? in : in.getOpposite();
					d.set(x, y, z, roof.has(Form.STAIRS) ? roof.stairs(facing, Half.BOTTOM) : roof.full());
					if (y > plate) {
						d.set(x, plate, z, roof.has(Form.STAIRS) ? roof.stairs(facing.getOpposite(), Half.TOP) : roof.full());
					}
				}
			}
		}
	}

	/** A bench along each wing's bench row (cell 2 from the wall) facing the yard, a gap every few seats to pass. */
	private static void benches(Draft d, int w, int zF, int zT, int y) {
		for (int x = 1; x <= w - 2; x++) {
			for (int z = zF + 1; z < zT; z++) {
				if (ring(x, z, w, zF) != 2 || !Venue.standable(d, x, y, z)) {
					continue;
				}
				int dz = z - zF;
				int dx0 = x;
				int dx1 = w - 1 - x;
				Direction toYard = dz <= dx0 && dz <= dx1 ? Dirs.BACK : dx0 <= dx1 ? Dirs.RIGHT : Dirs.LEFT;
				int along = toYard == Dirs.BACK ? x : z;
				if (along % 4 == 0 || x == w / 2 || x == w / 2 - 1) {
					continue;
				}
				Venue.bench(d, x, y, z, Dirs.right(toYard), 1, toYard.getOpposite());
			}
		}
	}
}
