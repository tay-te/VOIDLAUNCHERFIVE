package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Barns and byres: a tall boarded (or walled) hall on a stone plinth, its gable to the yard with wide cart doors and a
 * hayloft door above them under a hoist beam, a loft over stalls on either side of a threshing aisle, a roof that is a
 * gambrel where the culture builds in timber and its own form elsewhere. The barn archetype and the farmhouse's byre
 * wing both draw with it.
 *
 * <p>The hall's long axis runs along z (back from the yard); its front gable faces {@link Dirs#FRONT}.
 */
public final class Barns {
	private Barns() {
	}

	/** How a barn is walled in this culture. */
	public enum Build {
		/** Boards between posts on a stone plinth (timber-frame and plank cultures). */
		BOARDS,
		/** Horizontal logs, notched at the corners. */
		LOGS,
		/** The culture's masonry (stone, plaster, adobe, brick) with dressed corners. */
		MASONRY
	}

	public static Build build(Draft d) {
		return switch (d.habits().walls().kind()) {
			case LOG -> Build.LOGS;
			case STONE, PLASTER, ADOBE, BRICK -> Build.MASONRY;
			default -> Build.BOARDS;
		};
	}

	/** What a barn shell drew: its rectangle, levels and roof (null when flat-roofed, then {@code deck} is set). */
	public record Hall(int x0, int z0, int x1, int z1, int y, int loftY, int plateY, Roofs.Roof roof, RoofForm form, int deck) {
		public int width() {
			return this.x1 - this.x0 + 1;
		}

		public int depth() {
			return this.z1 - this.z0 + 1;
		}

		public int midX() {
			return (this.x0 + this.x1) / 2;
		}
	}

	/** The roof a barn takes: a gambrel where the culture builds in timber (often), its own forms otherwise. */
	public static RoofForm form(Draft d, boolean big) {
		Habits h = d.habits();
		RoofForm own = h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT);
		if (own == RoofForm.FLAT) {
			return RoofForm.FLAT;
		}
		Build b = build(d);
		boolean timber = b != Build.MASONRY || h.walls().upper() != null;
		if (big && timber && !h.roof().thatch() && own != RoofForm.PAGODA && d.chance(0.55)) {
			return RoofForm.GAMBREL;
		}
		return own;
	}

	/**
	 * The shell of a hall over {@code [x0..x1] × [z0..z1]}: footings, a floor at standing level y (packed earth down the
	 * aisle, boards elsewhere), walls {@code groundH + loftH} high in the culture's barn build, posts at every bay, a loft
	 * floor girt, the roof in {@code form} with its ridge along z, gable ends re-boarded, and the front gable dressed with
	 * cart doors {@code doorW} wide (odd) under a lintel and a loft door with its hoist beam. {@code party} sides (left,
	 * right) are left blind and carry no eaves.
	 */
	public static Hall shell(Draft d, int x0, int z0, int x1, int z1, int y, int groundH, int loftH, RoofForm form, int doorW, boolean partyL,
		boolean partyR) {
		Build b = build(d);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat plinth = d.mat(Role.PLINTH);
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		int loftY = y + groundH;
		int plate = loftY + Math.max(0, loftH) - 1;
		Groundwork.footings(d, x0, z0, x1, z1, y - 1);
		// floors: the aisle of beaten earth, the bays boarded
		int mid = (x0 + x1) / 2;
		int half = doorW / 2;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					boolean aisle = Math.abs(x - mid) <= half;
					d.set(x, y - 1, z, aisle ? (Noise.unit(d.seed() ^ 0xA15, x, 0, z) < 0.5 ? Blocks.PACKED_MUD : Blocks.COARSE_DIRT).defaultBlockState()
						: d.mat(Role.FLOOR).full().at(x, y - 1, z));
				}
			}
		}
		// walls
		int bay = 3;
		for (Side side : Side.of(x0, z0, x1, z1)) {
			boolean party = side.out() == Dirs.LEFT && partyL || side.out() == Dirs.RIGHT && partyR;
			Direction.Axis along = side.axis();
			for (int i = 0; i <= side.last(); i++) {
				int x = side.x(i);
				int z = side.z(i);
				boolean corner = side.isCorner(i);
				boolean post = corner || !party && i % bay == 0;
				for (int yy = y; yy <= plate; yy++) {
					Paint p;
					boolean girt = yy == loftY - 1 || yy == plate;
					switch (b) {
						case LOGS -> {
							if (corner) {
								p = frame.log(((yy - y) & 1) == 0 ? along : other(along));
							} else if (yy == y && d.habits().walls().plinth() > 0) {
								p = plinth.full();
							} else {
								p = wall.log(along).orElse(wall.full());
							}
						}
						case MASONRY -> {
							boolean upperBoards = yy >= loftY && d.habits().walls().upper() != null;
							if (upperBoards) {
								p = corner || post ? frame.log(Direction.Axis.Y) : girt ? beam.log(along) : Sheds.boards(d, along);
							} else if (yy == y && !corner) {
								p = plinth.full();
							} else if (corner || i == 1 && ((yy - y) & 1) == 0 || i == side.last() - 1 && ((yy - y) & 1) == 0) {
								p = accent.full();
							} else {
								p = wall.full();
							}
						}
						default -> {
							if (yy == y) {
								p = plinth.full();
							} else if (post) {
								p = frame.log(Direction.Axis.Y);
							} else if (girt) {
								p = beam.log(along);
							} else {
								p = boardsFor(d, along, x, yy, z);
							}
						}
					}
					d.set(x, yy, z, p);
				}
			}
			// log corners run past on alternate courses
			if (b == Build.LOGS) {
				for (int i : new int[]{0, side.last()}) {
					int ox = side.x(i) + (i == 0 ? -Dirs.dx(side.along()) : Dirs.dx(side.along()));
					int oz = side.z(i) + (i == 0 ? -Dirs.dz(side.along()) : Dirs.dz(side.along()));
					if (ox >= 0 && oz >= 0 && ox < d.frontage() && oz < d.depth()) {
						for (int yy = y + 1; yy <= plate; yy += 2) {
							if (d.isEmpty(ox, yy, oz)) {
								d.set(ox, yy, oz, frame.log(along));
							}
						}
					}
				}
			}
		}
		d.carve(x0 + 1, y, z0 + 1, x1 - 1, plate, z1 - 1);
		// the roof, its ridge along z (none when the hall is part of a range the caller roofs)
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (form == null) {
			return new Hall(x0, z0, x1, z1, y, loftY, plate, null, null, deck);
		}
		if (form == RoofForm.FLAT) {
			deck = Roofs.flat(d, x0, z0, x1, z1, plate, true);
		} else {
			Roofs.Opts o = Roofs.Opts.of(d);
			if (form == RoofForm.GAMBREL) {
				o = o.withPitch(Math.max(0.9, o.pitch()));
			} else if (o.pitch() < 0.75 && b != Build.MASONRY) {
				o = o.withPitch(0.75);
			}
			o = o.withEaves(Math.max(1, Math.min(2, o.eaves())), Math.max(1, Math.min(1, o.gableOverhang())));
			RoofForm draw = form == RoofForm.CROSS_GABLE ? RoofForm.GABLE : form;
			roof = Roofs.build(d, x0, z0, x1, z1, plate, draw, Direction.Axis.Z, o, partyL, partyR).roof();
			// the gable ends boarded like the walls (a timber barn has no plaster gables)
			if (b != Build.MASONRY || d.habits().walls().upper() != null) {
				for (Side side : new Side[]{Side.of(x0, z0, x1, z1, Dirs.FRONT), Side.of(x0, z0, x1, z1, Dirs.BACK)}) {
					if (form == RoofForm.HIP || form == RoofForm.PAGODA) {
						break;
					}
					for (int i = 1; i < side.last(); i++) {
						int x = side.x(i);
						int z = side.z(i);
						int under = roof.bottomY(x, z);
						for (int yy = plate + 1; yy < under; yy++) {
							d.set(x, yy, z, b == Build.LOGS ? wall.log(side.axis()).orElse(wall.full()) : boardsFor(d, side.axis(), x, yy, z));
						}
					}
				}
			}
		}
		// the loft open to the roof (a barn dug into a bank has earth to take out up there too)
		if (roof != null) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					int under = roof.bottomY(x, z);
					if (under != Integer.MIN_VALUE && under - 1 > plate) {
						d.carve(x, plate + 1, z, x, under - 1, z);
					}
				}
			}
		}
		Hall hall = new Hall(x0, z0, x1, z1, y, loftY, plate, roof, form, deck);
		frontGable(d, hall, doorW, b);
		return hall;
	}

	private static Direction.Axis other(Direction.Axis a) {
		return a == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
	}

	/** Boards of a barn wall: the culture's planks, a weathered board here and there. */
	private static Paint boardsFor(Draft d, Direction.Axis along, int x, int y, int z) {
		return Sheds.boards(d, along);
	}

	/**
	 * Cart doors in a long wall: an opening {@code doorW} wide and three high in the middle of {@code side}, posts and a
	 * lintel, the leaves hung open against the wall, the entrance registered outside.
	 */
	public static void cartDoor(Draft d, Side side, int y, int doorW) {
		Mat beam = d.mat(Role.BEAM);
		Mat frame = d.mat(Role.FRAME);
		Mat leaf = d.mat(Role.DOOR);
		int mid = side.middle();
		int half = doorW / 2;
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int i = mid - half; i <= mid + half; i++) {
				for (int yy = y; yy <= y + 2; yy++) {
					d.set(side.x(i), yy, side.z(i), Blocks.AIR.defaultBlockState());
				}
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int i : new int[]{mid - half - 1, mid + half + 1}) {
				if (i <= 0 || i >= side.last()) {
					continue;
				}
				for (int yy = y; yy <= y + 3; yy++) {
					d.set(side.x(i), yy, side.z(i), frame.log(Direction.Axis.Y));
				}
			}
			for (int i = mid - half - 1; i <= mid + half + 1; i++) {
				if (i > 0 && i < side.last()) {
					d.set(side.x(i), y + 3, side.z(i), beam.log(side.axis()));
				}
			}
		}
		if (leaf.has(Form.TRAPDOOR)) {
			try (var s = d.stage(StageKind.OPENINGS)) {
				for (int sgn : new int[]{-1, 1}) {
					for (int k = 0; k <= half; k++) {
						int i = mid + sgn * (half + 2 + k);
						if (i <= 0 || i >= side.last()) {
							continue;
						}
						int ox = side.outX(i, 1);
						int oz = side.outZ(i, 1);
						if (ox < 0 || oz < 0 || ox >= d.frontage() || oz >= d.depth()) {
							continue;
						}
						for (int yy = y; yy <= y + 2; yy++) {
							if (d.isAbsent(ox, yy, oz)) {
								d.set(ox, yy, oz, leaf.trapdoor(side.out(), Half.BOTTOM, true));
							}
						}
					}
				}
			}
		}
		int ex = side.outX(mid, 1);
		int ez = side.outZ(mid, 1);
		d.entrance(ex, y, ez);
		d.poi(PoiType.DOOR, ex, y, ez, side.out().getOpposite());
	}

	/**
	 * The front gable: cart doors in the middle under a lintel beam, the leaves hung open against the wall either side,
	 * a hayloft door in the gable above with a hoist beam over it and a bale on its chain, a lantern by the doors.
	 */
	static void frontGable(Draft d, Hall h, int doorW, Build b) {
		Side front = Side.of(h.x0, h.z0, h.x1, h.z1, Dirs.FRONT);
		Mat beam = d.mat(Role.BEAM);
		Mat frame = d.mat(Role.FRAME);
		Mat shutter = d.mat(Role.DOOR);
		int mid = h.midX();
		int half = doorW / 2;
		int doorTop = h.y + Math.min(3, h.loftY - h.y - 1) - 1;
		// the opening
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int x = mid - half; x <= mid + half; x++) {
				for (int yy = h.y; yy <= doorTop; yy++) {
					d.set(x, yy, h.z0, Blocks.AIR.defaultBlockState());
				}
			}
		}
		// posts either side and a lintel over it
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{mid - half - 1, mid + half + 1}) {
				for (int yy = h.y; yy <= doorTop + 1; yy++) {
					d.set(x, yy, h.z0, frame.log(Direction.Axis.Y));
				}
			}
			for (int x = mid - half - 1; x <= mid + half + 1; x++) {
				d.set(x, doorTop + 1, h.z0, beam.log(Direction.Axis.X));
			}
		}
		// the leaves, swung open against the wall (trapdoors standing flat on the face)
		if (shutter.has(Form.TRAPDOOR)) {
			try (var s = d.stage(StageKind.OPENINGS)) {
				for (int side : new int[]{-1, 1}) {
					for (int k = 0; k <= half; k++) {
						int x = mid + side * (half + 2 + k);
						if (x <= h.x0 || x >= h.x1 || h.z0 - 1 < 0) {
							continue;
						}
						for (int yy = h.y; yy <= doorTop; yy++) {
							if (d.isAbsent(x, yy, h.z0 - 1)) {
								d.set(x, yy, h.z0 - 1, shutter.trapdoor(Dirs.FRONT, Half.BOTTOM, true));
							}
						}
					}
				}
			}
		}
		// threshold: a ramp of earth or a stone sill where the yard meets the floor
		d.entrance(mid, h.y, h.z0 - 1);
		d.poi(PoiType.DOOR, mid, h.y, h.z0 - 1, Dirs.BACK);
		Openings.wallLantern(d, front, front.index(mid + half + 1) + 1 <= front.last() ? front.index(mid + half + 1) + 1 : front.index(mid - half - 1) - 1,
			h.y + 1);
		// the hayloft door in the gable: an opening two high at the loft floor, a beam over it, the hoist above
		if (h.roof() != null && h.form() != RoofForm.HIP && h.form() != RoofForm.PAGODA) {
			int ly = h.loftY;
			int under = h.roof().bottomY(mid, h.z0);
			if (under - ly >= 3) {
				try (var s = d.stage(StageKind.OPENINGS)) {
					d.set(mid, ly, h.z0, Blocks.AIR.defaultBlockState());
					d.set(mid, ly + 1, h.z0, Blocks.AIR.defaultBlockState());
					if (shutter.has(Form.TRAPDOOR)) {
						d.set(mid, ly, h.z0, d.mat(Role.SHUTTER).trapdoor(Dirs.BACK, Half.BOTTOM, true));
					}
				}
				try (var s = d.stage(StageKind.FRAME)) {
					d.set(mid, ly + 2, h.z0, beam.log(Direction.Axis.X));
				}
				// the hoist: a beam out from the apex with a chain and a bale
				int hy = Math.min(under - 1, ly + 3);
				if (hy > ly + 2 && h.z0 - 1 >= 0) {
					try (var s = d.stage(StageKind.FRAME)) {
						d.set(mid, hy, h.z0, beam.log(Direction.Axis.Z));
						if (d.isAbsent(mid, hy, h.z0 - 1)) {
							d.set(mid, hy, h.z0 - 1, beam.log(Direction.Axis.Z));
							d.set(mid, hy - 1, h.z0 - 1, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
							if (hy - 2 > doorTop + 2) {
								d.set(mid, hy - 2, h.z0 - 1, Blocks.HAY_BLOCK.defaultBlockState());
							}
						}
					}
				}
			} else {
				// too low for a door: a vent of slats in the gable
				int vy = h.plateY + 2;
				if (h.roof().bottomY(mid, h.z0) > vy) {
					d.inStage(StageKind.OPENINGS, () -> d.set(mid, vy, h.z0, d.mat(Role.SHUTTER).trapdoor(Dirs.BACK, Half.BOTTOM, true)));
				}
			}
		}
	}

	/**
	 * Inside: stalls along both long walls (partitions of fence, a gate to the aisle, a manger), the loft floor over
	 * them with hay stacked against the walls and a walkway along its edge, tie beams across the aisle joining the two
	 * lofts, a straight stair up the back wall, lanterns, the herder's feed cauldron and chest. Registers the job sites
	 * and stores.
	 */
	public static void interior(Draft d, Hall h, int doorW, boolean backDoor) {
		int mid = h.midX();
		int half = doorW / 2;
		int ax0 = mid - half;
		int ax1 = mid + half;
		Mat fence = d.mat(Role.FENCE);
		Mat railing = d.mat(Role.RAILING);
		Mat partition = railing.has(Form.FENCE) ? railing : fence.has(Form.FENCE) ? fence : d.mat(Role.FRAME);
		int iz0 = h.z0 + 1;
		int iz1 = h.z1 - 1;
		// the stair: along the back wall, climbing toward the left wall from the aisle, arriving on the left loft
		int rise = h.loftY - h.y;
		// the flight's foot in the aisle (entered from it), its top arriving on the left loft
		int stairFoot = ax1;
		int stairTop = stairFoot - rise + 1;
		boolean stair = stairTop - 1 >= h.x0 + 1 && stairTop <= ax0;
		int stairZ = iz1;
		// stalls: bays along each side, from the front, the back row kept for the stair and the herder
		int stallsTo = iz1 - 2;
		for (int side : new int[]{-1, 1}) {
			int wx = side < 0 ? h.x0 + 1 : h.x1 - 1;
			int ex = side < 0 ? ax0 - 1 : ax1 + 1;
			if (side < 0 ? ex < wx : ex > wx) {
				continue;
			}
			int stallLen = 3;
			for (int z = iz0 + 1; z + 1 <= stallsTo; z += stallLen) {
				int za = z;
				int zb = Math.min(stallsTo, z + stallLen - 2);
				try (var s = d.stage(StageKind.INTERIOR)) {
					// the partition in front of the stall's first row, wall to aisle
					for (int x = Math.min(wx, ex); x <= Math.max(wx, ex); x++) {
						if (d.isEmpty(x, h.y, za - 1)) {
							d.set(x, h.y, za - 1, partition.fence());
						}
					}
					// the stall front: a gate to the aisle
					for (int zz = za; zz <= zb; zz++) {
						d.set(ex, h.y, zz, zz == za ? partition.gate(side < 0 ? Dirs.RIGHT : Dirs.LEFT, false) : partition.fence());
					}
					// bedding underfoot and a manger against the wall
					for (int zz = za; zz <= zb; zz++) {
						for (int x = Math.min(wx, ex - side); x <= Math.max(wx, ex - side); x++) {
							d.set(x, h.y - 1, zz, Noise.unit(d.seed() ^ 0x57A, x, 0, zz) < 0.6 ? Blocks.COARSE_DIRT.defaultBlockState()
								: Blocks.ROOTED_DIRT.defaultBlockState());
						}
					}
					d.set(wx, h.y, zb, Noise.unit(d.seed(), wx, 0, zb) < 0.5 ? Blocks.COMPOSTER.defaultBlockState()
						: Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3));
				}
			}
			// close the last stall's back
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int x = Math.min(wx, ex); x <= Math.max(wx, ex); x++) {
					if (d.isEmpty(x, h.y, stallsTo + 1)) {
						d.set(x, h.y, stallsTo + 1, partition.fence());
					}
				}
			}
		}
		boolean loft = h.roof() != null ? h.plateY() >= h.loftY : h.plateY() - h.loftY >= 2;
		if (!loft) {
			herder(d, h, ax0, ax1, iz0, iz1, Integer.MIN_VALUE);
			return;
		}
		// the loft over the stalls, open over the aisle
		Floors.boards(d, h.x0 + 1, iz0, ax0 - 1, iz1, h.loftY - 1);
		Floors.boards(d, ax1 + 1, iz0, h.x1 - 1, iz1, h.loftY - 1);
		// tie beams across the aisle every few blocks join the two lofts (and carry the lanterns)
		java.util.Set<Integer> ties = new java.util.HashSet<>();
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = iz0 + 1; z < iz1 - 1; z += 4) {
				ties.add(z);
				for (int x = ax0; x <= ax1; x++) {
					d.set(x, h.loftY - 1, z, d.mat(Role.BEAM).log(Direction.Axis.X));
				}
			}
		}
		// hay in the loft: stacked against the walls, the walkway along the edge over the aisle kept clear
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = iz0; z <= iz1; z++) {
				for (int x = h.x0 + 1; x <= h.x1 - 1; x++) {
					boolean inLoft = x < ax0 - 1 || x > ax1 + 1;
					if (!inLoft || !d.isEmpty(x, h.loftY, z) || stair && z == stairZ && x <= stairFoot + 1) {
						continue;
					}
					boolean wallSide = x == h.x0 + 1 || x == h.x1 - 1;
					double u = Noise.unit(d.seed() ^ 0x4A7, x, 0, z);
					if (wallSide && u < 0.8 || !wallSide && u < 0.3 && !ties.contains(z)) {
						d.set(x, h.loftY, z, States.axis(Blocks.HAY_BLOCK, Direction.Axis.Z));
						if (wallSide && u < 0.45 && h.roof() != null && h.roof().bottomY(x, z) > h.loftY + 1) {
							d.set(x, h.loftY + 1, z, States.axis(Blocks.HAY_BLOCK, Direction.Axis.X));
						}
					}
				}
			}
		}
		// the stair to the loft along the back wall
		if (stair) {
			for (int x = stairTop - 1; x <= stairFoot + 1; x++) {
				for (int yy = h.y; yy <= h.loftY + 1; yy++) {
					d.air(x, yy, stairZ);
				}
			}
			Stairs.straight(d, stairFoot, h.y, stairZ, Dirs.LEFT, rise, 1);
			// the well through the loft floor over the flight
			for (int x = stairTop + 1; x <= stairFoot; x++) {
				for (int yy = h.loftY - 1; yy <= h.loftY + 1; yy++) {
					var st = d.get(x, yy, stairZ);
					if (st != null && !st.isAir() && !(st.getBlock() instanceof net.minecraft.world.level.block.StairBlock)) {
						d.set(x, yy, stairZ, Blocks.AIR.defaultBlockState());
					}
				}
			}
		} else {
			Stairs.ladder(d, h.x0 + 1, iz1, h.y, h.loftY - 1, Dirs.FRONT);
			d.air(h.x0 + 1, h.loftY, iz1);
			d.air(h.x0 + 1, h.loftY + 1, iz1);
		}
		herder(d, h, ax0, ax1, iz0, iz1, stair ? stairFoot : Integer.MIN_VALUE);
		for (int z : ties) {
			Furniture.hangingLantern(d, mid, h.loftY - 1, z, 0);
		}
		// a job site in the loft too (forking hay down), on the walkway
		Spots.at(d, PoiType.JOB_SITE, ax1 + 1, h.loftY, (iz0 + iz1) / 2, Dirs.LEFT, JobType.HERDER);
	}

	/** The herder's feed cauldron and chest at the back of the right-hand stalls (or by the doors). */
	private static void herder(Draft d, Hall h, int ax0, int ax1, int iz0, int iz1, int stairFoot) {
		int[][] places = {{h.x1 - 1, iz1}, {h.x1 - 2, iz1}, {h.x1 - 1, iz1 - 1}};
		boolean placed = false;
		for (int[] p : places) {
			if (p[0] > stairFoot + 1 && d.isEmpty(p[0], h.y, p[1]) && d.isEmpty(p[0], h.y, p[1] - 1)) {
				Furniture.workstation(d, JobType.HERDER, p[0], h.y, p[1], Dirs.FRONT);
				placed = true;
				if (d.isEmpty(p[0] - 1, h.y, p[1]) && p[0] - 1 > stairFoot + 1) {
					Furniture.chest(d, p[0] - 1, h.y, p[1], Dirs.FRONT, List.of(new Object[]{"minecraft:wheat", 32}, new Object[]{"minecraft:lead", 3},
						new Object[]{"minecraft:shears", 1}, new Object[]{"minecraft:bucket", 1}));
				}
				break;
			}
		}
		if (!placed) {
			// against the front wall beside the doors
			Furniture.workstation(d, JobType.HERDER, ax1 + 1 <= h.x1 - 1 ? ax1 + 1 : ax0, h.y, iz0, Dirs.BACK);
		}
		if (h.roof() == null) {
			int mid = h.midX();
			for (int z = iz0 + 2; z < iz1; z += 4) {
				if (d.isSolid(mid, h.plateY(), z)) {
					Furniture.hangingLantern(d, mid, h.plateY(), z, 0);
				}
			}
		}
	}

	/**
	 * Small windows down the long walls, one in every other bay at the stalls' height and slatted vents under the eaves,
	 * so the long sides are not blank.
	 */
	public static void sideWindows(Draft d, Hall h) {
		Build b = build(d);
		for (Side side : new Side[]{Side.of(h.x0, h.z0, h.x1, h.z1, Dirs.LEFT), Side.of(h.x0, h.z0, h.x1, h.z1, Dirs.RIGHT)}) {
			for (int i = 2; i < side.last() - 1; i += 3) {
				if ((i / 3) % 2 == 0) {
					Openings.window(d, side, side.column(i), h.y + 2, 1, 1, b == Build.MASONRY ? Openings.WindowStyle.SLIT : Openings.WindowStyle.PLAIN,
						new Openings.WindowOpts(false, false, b != Build.MASONRY, true));
				} else if (h.plateY() - 1 > h.loftY) {
					int x = side.x(i);
					int z = side.z(i);
					d.inStage(StageKind.OPENINGS, () -> d.set(x, h.plateY() - 1, z, d.mat(Role.SHUTTER).trapdoor(side.out().getOpposite(), Half.BOTTOM, true)));
				}
			}
		}
	}

	/** A cupola on the ridge of a big barn: a little louvred box under its own roof with a vane. */
	public static void cupola(Draft d, Hall h) {
		if (h.roof() == null) {
			return;
		}
		int x = h.midX();
		int z = (h.z0 + h.z1) / 2;
		int ry = h.roof().surfaceY(x, z);
		if (ry + 4 >= d.maxHeight()) {
			return;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.ROOF)) {
			d.set(x, ry + 1, z, frame.log(Direction.Axis.Y));
			d.set(x, ry + 2, z, d.mat(Role.SHUTTER).trapdoor(Dirs.FRONT, Half.BOTTOM, false).orElse(frame.fence()));
			d.set(x, ry + 2, z, frame.fence());
			d.set(x, ry + 3, z, roof.slab(SlabType.BOTTOM).orElse(roof.full()));
			d.set(x, ry + 4, z, States.block("minecraft:weathered_lightning_rod", States.block("minecraft:lightning_rod", Blocks.IRON_BARS)).defaultBlockState());
		}
	}
}
