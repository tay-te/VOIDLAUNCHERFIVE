package dev.voidmc.hearth.build.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Doors;
import dev.voidmc.hearth.build.kit.Guards;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.kit.refine.Lofts;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.build.terrain.Site;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;

/**
 * The access kit on rooms drawn by hand (the audit's rules S1-S4): {@link Doors} keeps both fronts of a door clear and
 * bricks up a door that leads nowhere, {@link Guards} rails a deck's edge, {@link Porches#balcony} leaves the row before
 * its door free, {@link Stairs} turn on landings with head room, {@link Lofts#ladder} lands on a floor, and
 * {@link Rooms#furnish} keeps a door's inside cell free.
 */
class AccessKitTest {
	static final BlueprintServiceImpl SERVICE;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private static final int W = 11;
	private static final int D = 11;

	private static Draft draft(Culture c) {
		SyntheticTerrain flat = SyntheticTerrain.named("flat", 1);
		int floor = flat.groundY(0, 0);
		CompoundTag params = new CompoundTag();
		PlotSpec spec = new PlotSpec(Hearth.id("house/cottage"), StyleRegistry.styleId(c), 1L, new BlockPos(-W / 2, floor, -D / 2), W, D, Direction.SOUTH,
			16, 2, params);
		return new Draft(spec, SERVICE.styles().getOrDefault(spec.style()), new Site(spec, flat));
	}

	/** A stone box (x 1..9, z 1..9) with a floor at y -1, walls 0..3, a slab of boards on top at 4, the inside carved. */
	private static Draft box(Culture c) {
		Draft d = draft(c);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			d.fill(1, -1, 1, 9, -1, 9, Blocks.COBBLESTONE.defaultBlockState());
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 0; y <= 3; y++) {
				for (int i = 1; i <= 9; i++) {
					d.set(i, y, 1, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(i, y, 9, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(1, y, i, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(9, y, i, Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			d.fill(1, 4, 1, 9, 4, 9, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		d.carve(2, 0, 2, 8, 3, 8);
		return d;
	}

	private static void door(Draft d, int x, int y, int z, Direction out) {
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y, z, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, out));
			d.set(x, y + 1, z, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, out).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}
	}

	@Test
	void aDoorKeepsBothFrontsClear() {
		Draft d = box(Culture.TEMPERATE);
		door(d, 5, 0, 1, Dirs.FRONT);
		d.entrance(5, 0, 0);
		// a chest inside the door and a potted plant outside it
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(5, 0, 2, Blocks.CHEST.defaultBlockState());
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(5, 0, 0, Blocks.POTTED_FERN.defaultBlockState());
		}
		assertFalse(Doors.ok(d, 5, 0, 1, Dirs.FRONT));
		Doors.Report r = Doors.finish(d);
		assertEquals(2, r.cleared());
		assertTrue(Doors.ok(d, 5, 0, 1, Dirs.FRONT));
		assertEquals(0, r.bricked());
	}

	@Test
	void aDoorIntoAWallIsBrickedUpButAnEntranceIsNot() {
		Draft d = box(Culture.TEMPERATE);
		// a partition door whose inside is a pier, and the front door (an entrance) with a pier inside it too
		door(d, 3, 0, 1, Dirs.FRONT);
		door(d, 7, 0, 1, Dirs.FRONT);
		d.entrance(7, 0, 0);
		try (var s = d.stage(StageKind.FRAME)) {
			d.set(3, 0, 2, Blocks.OAK_LOG.defaultBlockState());
			d.set(3, 1, 2, Blocks.OAK_LOG.defaultBlockState());
			d.set(7, 0, 2, Blocks.OAK_LOG.defaultBlockState());
			d.set(7, 1, 2, Blocks.OAK_LOG.defaultBlockState());
		}
		Doors.Report r = Doors.finish(d);
		assertEquals(1, r.bricked());
		assertFalse(d.get(3, 0, 1).getBlock() instanceof DoorBlock);
		assertTrue(d.get(7, 0, 1).getBlock() instanceof DoorBlock);
	}

	@Test
	void anUpperDoorOverADropBecomesAWindow() {
		Draft d = box(Culture.TEMPERATE);
		door(d, 5, 5, 1, Dirs.FRONT);
		// a floor inside it at y 4 (the roof boards) and nothing outside: a drop of five or more
		d.carve(2, 5, 2, 8, 6, 8);
		Doors.Report r = Doors.finish(d);
		assertEquals(1, r.bricked());
		assertFalse(d.get(5, 5, 1).getBlock() instanceof DoorBlock);
		assertFalse(d.get(5, 6, 1).getBlock() instanceof DoorBlock);
	}

	@Test
	void aChestUnderASolidBecomesABarrel() {
		Draft d = box(Culture.TEMPERATE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(4, 0, 4, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Dirs.BACK));
			d.set(4, 1, 4, Blocks.BOOKSHELF.defaultBlockState());
			d.set(6, 0, 4, Blocks.CHEST.defaultBlockState());
		}
		Doors.Report r = Doors.finish(d);
		assertEquals(1, r.lids());
		assertTrue(d.get(4, 0, 4).getBlock() instanceof BarrelBlock);
		assertEquals(Dirs.BACK, d.get(4, 0, 4).getValue(BarrelBlock.FACING));
		assertTrue(d.get(6, 0, 4).getBlock() instanceof ChestBlock);
	}

	@Test
	void aFlatRoofReachedByALadderIsRailed() {
		Draft d = box(Culture.TEMPERATE);
		door(d, 5, 0, 1, Dirs.FRONT);
		d.entrance(5, 0, 0);
		// a ladder up the back wall through the roof boards: the roof is a deck folk stand on, five above the ground
		assertTrue(Lofts.ladder(d, 5, 8, 0, 5, Dirs.BACK, null));
		int guards = Guards.finish(d);
		assertTrue(guards > 0, "rails on the deck's edge");
		Spaces sp = Spaces.of(d);
		// the deck's edge cells over the walls are guarded, the cells inside them still walked to
		assertFalse(Spaces.passable(d.get(1, 5, 5)), "a guard on the deck's edge over the wall");
		assertTrue(sp.reached(5, 5, 5));
		assertTrue(sp.reached(5, 5, 7), "the ladder's head stays open");
	}

	@Test
	void aBalconyLeavesTheRowBeforeItsDoorFree() {
		Draft d = box(Culture.TEMPERATE);
		// an upper storey over the front wall (z 1), standing at 5: the plot allows two rows out over the street up there
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 5; y <= 7; y++) {
				for (int i = 1; i <= 9; i++) {
					d.set(i, y, 1, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(i, y, 9, Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
		}
		Side front = Side.of(1, 1, 9, 9, Dirs.FRONT);
		assertEquals(2, Porches.balcony(d, front, front.column(3), front.column(5), 5));
		for (int c = 3; c <= 5; c++) {
			int i = front.index(front.column(c));
			assertTrue(d.isEmpty(front.outX(i, 1), 5, front.outZ(i, 1)), "the row before the wall is clear");
			assertFalse(Spaces.passable(d.get(front.outX(i, 2), 5, front.outZ(i, 2))), "the rail is on the outer row");
		}
		// behind the house the plot ends one row out: one deep, its rail a leaf on the deck's outer edge
		Side back = Side.of(1, 1, 9, 9, Dirs.BACK);
		assertEquals(1, Porches.balcony(d, back, back.column(3), back.column(5), 5));
		int i = back.index(back.column(4));
		BlockState before = d.get(back.outX(i, 1), 5, back.outZ(i, 1));
		assertTrue(before == null || Spaces.passable(before), "nothing a person cannot pass before the door");
	}

	@Test
	void aSwitchbackTurnsOnALandingWithHeadRoom() {
		Draft d = draft(Culture.TEMPERATE);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			d.fill(0, -1, 0, W - 1, -1, D - 1, Blocks.COBBLESTONE.defaultBlockState());
		}
		Stairs.switchback(d, 2, 0, 2, Dirs.BACK, 5, Dirs.RIGHT);
		// every tread has two blocks of air over it
		int treads = 0;
		for (int y = 0; y < 6; y++) {
			for (int z = 0; z < D; z++) {
				for (int x = 0; x < W; x++) {
					BlockState s = d.get(x, y, z);
					if (s != null && s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM) {
						treads++;
						assertTrue(d.isEmpty(x, y + 1, z) && d.isEmpty(x, y + 2, z), "head room over the tread at " + x + "," + y + "," + z);
					}
				}
			}
		}
		assertEquals(5, treads);
	}

	@Test
	void furnishingKeepsADoorsInsideCellFree() {
		Draft d = box(Culture.TEMPERATE);
		door(d, 2, 0, 1, Dirs.FRONT);
		Rooms.Room r = new Rooms.Room(2, 2, 8, 8, 0, 3);
		Rooms.furnish(d, r, Rooms.Type.STORAGE, 0, dev.voidmc.hearth.api.JobType.NONE);
		assertTrue(d.isEmpty(2, 0, 2), "the door's inside cell");
		assertTrue(d.isEmpty(2, 1, 2));
	}

	@Test
	void aLadderOnlyGoesWhereItLandsOnAFloor() {
		Draft d = box(Culture.TEMPERATE);
		// the roof boards over the box are the loft's floor: a ladder against the back wall lands on them
		assertTrue(Lofts.ladder(d, 5, 8, 0, 5, Dirs.BACK, null));
		assertTrue(d.isSolid(5, 4, 7) || d.isSolid(4, 4, 8) || d.isSolid(6, 4, 8), "a floor cell beside the hatch to step off onto");
	}

	/** The box with a door in its front and a loft over it (boards at 4, walls 5..7, a roof at 8) no way climbs to. */
	private static Draft lofted(boolean whole) {
		Draft d = box(Culture.TEMPERATE);
		door(d, 5, 0, 1, Dirs.FRONT);
		try (var s = d.stage(StageKind.WALLS)) {
			d.clearBox(2, 4, 2, 8, 4, 8);
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			if (whole) {
				d.fill(2, 4, 2, 8, 4, 8, Blocks.SPRUCE_PLANKS.defaultBlockState());
			} else {
				d.fill(2, 4, 8, 8, 4, 8, Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 5; y <= 7; y++) {
				for (int i = 1; i <= 9; i++) {
					d.set(i, y, 1, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(i, y, 9, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(1, y, i, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(9, y, i, Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			d.fill(1, 8, 1, 9, 8, 9, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		d.carve(2, 5, 2, 8, 7, 8);
		return d;
	}

	@Test
	void anUnreachedLoftIsGivenALadder() {
		Draft d = lofted(true);
		assertFalse(Spaces.of(d).reached(5, 5, 5), "nobody reaches the loft as drawn");
		Lofts.Report r = Lofts.finish(d);
		assertEquals(1, r.ladders());
		assertTrue(Spaces.of(d).reached(5, 5, 5), "the loft is reached by its ladder");
	}

	@Test
	void aRimOfBoardsNobodyReachesIsOpened() {
		Draft d = lofted(false);
		Lofts.Report r = Lofts.finish(d);
		assertEquals(0, r.ladders());
		assertEquals(7, r.opened());
		assertTrue(d.isEmpty(5, 4, 8), "the rim's boards are taken up, the room open to its roof");
	}
}
