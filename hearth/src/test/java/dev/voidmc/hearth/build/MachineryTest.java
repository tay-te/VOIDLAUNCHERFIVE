package dev.voidmc.hearth.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.blueprint.MaterialBill;
import dev.voidmc.hearth.build.blueprint.ShapeResolver;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.junit.jupiter.api.Test;

/** The local frame, shape resolution, build order, packing and the materials bill. */
class MachineryTest {
	static {
		BuildTestSupport.init();
	}

	private static PlotSpec spec(Direction facing, int sizeX, int sizeZ) {
		return new PlotSpec(Hearth.id("house/cottage"), StyleRegistry.styleId(dev.voidmc.hearth.api.Culture.TEMPERATE), 1L, new BlockPos(100, 64, -50),
			sizeX, sizeZ, facing, 16, 2, new CompoundTag());
	}

	private static Draft draft(PlotSpec spec) {
		SyntheticTerrain flat = SyntheticTerrain.named("flat", 1);
		return new Draft(spec, BuildTestSupport.SERVICE.styles().getOrDefault(spec.style()), new Site(spec, flat));
	}

	@Test
	void localFrameCoversTheFootprintInEveryFacing() {
		for (Direction f : Direction.Plane.HORIZONTAL) {
			PlotSpec spec = spec(f, 7, 11);
			LocalFrame frame = new LocalFrame(spec);
			java.util.Set<Long> seen = new java.util.HashSet<>();
			for (int lz = 0; lz < frame.depth(); lz++) {
				for (int lx = 0; lx < frame.frontage(); lx++) {
					int wx = frame.worldX(lx, lz);
					int wz = frame.worldZ(lx, lz);
					assertTrue(wx >= 100 && wx < 107 && wz >= -50 && wz < -39, f + ": local " + lx + "," + lz + " outside the footprint");
					assertTrue(seen.add(BlockPos.asLong(wx, 0, wz)), f + ": two local cells map to one world cell");
					assertEquals(lx, frame.localX(wx, wz));
					assertEquals(lz, frame.localZ(wx, wz));
				}
			}
			// the front row is on the street side: one step toward the facing leaves the footprint
			int wx = frame.worldX(0, 0) + f.getStepX();
			int wz = frame.worldZ(0, 0) + f.getStepZ();
			assertEquals(-1, frame.localZ(wx, wz), f + ": the front row does not face the street");
			assertEquals(f, frame.toWorld(Dirs.FRONT), f + ": local FRONT does not turn to the facing");
			// local +x is the right hand of someone in the street looking at the building
			int rx = frame.worldX(1, 0) - frame.worldX(0, 0);
			int rz = frame.worldZ(1, 0) - frame.worldZ(0, 0);
			assertEquals(f.getOpposite().getClockWise(), Direction.getApproximateNearest(rx, 0, rz), f + ": local +x is not the viewer's right");
			assertEquals(frame.toWorld(Dirs.RIGHT), Direction.getApproximateNearest(rx, 0, rz));
		}
	}

	@Test
	void stairsTurnCornersFencesAndWallsConnect() {
		Draft d = draft(spec(Direction.SOUTH, 9, 9));
		// a square of stairs facing inward: the corners must be inner or outer corners
		for (int x = 1; x <= 4; x++) {
			d.set(x, 0, 1, States.stairs(Blocks.OAK_STAIRS, Dirs.BACK, Half.BOTTOM));
		}
		for (int z = 2; z <= 4; z++) {
			d.set(1, 0, z, States.stairs(Blocks.OAK_STAIRS, Dirs.RIGHT, Half.BOTTOM));
		}
		for (int x = 6; x <= 8; x++) {
			d.set(x, 0, 6, Blocks.OAK_FENCE);
		}
		d.set(5, 0, 6, Blocks.STONE_BRICKS);
		d.set(1, 0, 8, Blocks.COBBLESTONE_WALL);
		d.set(2, 0, 8, Blocks.COBBLESTONE_WALL);
		d.set(3, 0, 8, Blocks.COBBLESTONE_WALL);
		d.set(2, 1, 8, Blocks.STONE_BRICKS);
		d.set(6, 0, 3, States.door(Blocks.OAK_DOOR, Dirs.BACK, DoorHingeSide.LEFT, DoubleBlockHalf.LOWER));
		ShapeResolver.resolve(d, d.site());
		BlockState corner = d.get(1, 0, 1);
		assertTrue(corner.getValue(StairBlock.SHAPE) != StairsShape.STRAIGHT, "the stair corner stayed straight: " + corner);
		assertEquals(StairsShape.STRAIGHT, d.get(3, 0, 1).getValue(StairBlock.SHAPE));
		BlockState fence = d.get(6, 0, 6);
		assertTrue(fence.getValue(FenceBlock.WEST) && fence.getValue(FenceBlock.EAST), "fence did not join the wall and the next fence: " + fence);
		assertTrue(!d.get(8, 0, 6).getValue(FenceBlock.EAST), "the last fence joined nothing");
		BlockState wallMid = d.get(2, 0, 8);
		assertEquals(WallSide.TALL, wallMid.getValue(BlockStateProperties.WEST_WALL), "a wall under a block has tall sides: " + wallMid);
		assertTrue(d.get(1, 0, 8).getValue(WallBlock.UP), "a wall end has a post");
		BlockState upper = d.get(6, 1, 3);
		assertNotNull(upper, "the door got no upper half");
		assertEquals(DoubleBlockHalf.UPPER, upper.getValue(DoorBlock.HALF));
	}

	@Test
	void stairsAreRotatedWithThePlot() {
		for (Direction f : Direction.Plane.HORIZONTAL) {
			PlotSpec spec = spec(f, 7, 7);
			Draft d = draft(spec);
			d.set(3, 0, 3, States.stairs(Blocks.OAK_STAIRS, Dirs.BACK, Half.BOTTOM));
			BlueprintImpl bp = Compiler.compile(d);
			BlockState s = bp.state(0, 0);
			assertEquals(f.getOpposite(), s.getValue(StairBlock.FACING), "a stair rising toward the back faces away from the street when facing " + f);
		}
	}

	@Test
	void hangingLanternsComeAfterTheirCeilingAndClearingGoesTopDown() {
		Draft d = draft(spec(Direction.SOUTH, 7, 7));
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(3, 2, 3, States.lantern(Blocks.LANTERN, true));
			d.set(3, 3, 3, Blocks.OAK_PLANKS);
			d.set(4, 0, 3, Blocks.OAK_PLANKS);
		}
		try (var s = d.stage(StageKind.CLEAR)) {
			for (int y = 0; y < 5; y++) {
				d.set(0, y, 0, Blocks.AIR.defaultBlockState());
			}
		}
		BlueprintImpl bp = Compiler.compile(d);
		int interior = bp.stageIndex(StageKind.INTERIOR);
		List<String> order = new ArrayList<>();
		for (int i = 0; i < bp.stepCount(interior); i++) {
			order.add(bp.state(interior, i).getBlock() + "@" + (bp.pos(interior, i).getY() - 64));
		}
		assertEquals(List.of("Block{minecraft:oak_planks}@0", "Block{minecraft:oak_planks}@3", "Block{minecraft:lantern}@2"), order);
		int clear = bp.stageIndex(StageKind.CLEAR);
		for (int i = 1; i < bp.stepCount(clear); i++) {
			assertTrue(bp.pos(clear, i).getY() < bp.pos(clear, i - 1).getY(), "clearing is not top-down");
		}
	}

	@Test
	void materialsBillMapsBlocksToGoods() {
		assertGoods(Blocks.OAK_PLANKS.defaultBlockState(), Goods.WOOD);
		assertGoods(Blocks.SPRUCE_STAIRS.defaultBlockState(), Goods.WOOD);
		assertGoods(Blocks.STONE_BRICKS.defaultBlockState(), Goods.DRESSED_STONE);
		assertGoods(Blocks.COBBLESTONE_WALL.defaultBlockState(), Goods.STONE);
		assertGoods(Blocks.BRICKS.defaultBlockState(), Goods.BRICK);
		assertGoods(Blocks.TERRACOTTA.defaultBlockState(), Goods.BRICK);
		assertGoods(Blocks.GLASS_PANE.defaultBlockState(), Goods.GLASS);
		assertGoods(Blocks.IRON_BARS.defaultBlockState(), Goods.METAL);
		assertGoods(Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState(), Goods.CLOTH);
		assertEquals(0, MaterialBill.of(Blocks.AIR).length);
		assertEquals(0, MaterialBill.of(Blocks.POPPY).length);
		assertEquals(0, MaterialBill.of(Blocks.GRASS_BLOCK).length);
		int[] lantern = new int[Goods.VALUES.length];
		MaterialBill.add(Blocks.LANTERN.defaultBlockState(), lantern);
		assertEquals(1, lantern[Goods.METAL.ordinal()]);
		assertEquals(1, lantern[Goods.CANDLES.ordinal()]);
	}

	private static void assertGoods(BlockState s, Goods g) {
		int[] goods = new int[Goods.VALUES.length];
		MaterialBill.add(s, goods);
		assertEquals(1, goods[g.ordinal()], s + " should cost one " + g);
	}

	@Test
	void serviceCachesAndRejectsUnknownArchetypes() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		var info = BuildTestSupport.SERVICE.archetype(Hearth.id("house/cottage"));
		assertNotNull(info);
		PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(dev.voidmc.hearth.api.Culture.TEMPERATE), 5L, Direction.SOUTH, probe);
		assertSame(BuildTestSupport.SERVICE.blueprint(spec, probe), BuildTestSupport.SERVICE.blueprint(spec, probe));
		PlotSpec bad = new PlotSpec(Hearth.id("house/nonesuch"), spec.style(), 1L, spec.origin(), 7, 7, Direction.SOUTH, 12, 2, new CompoundTag());
		assertThrows(IllegalArgumentException.class, () -> BuildTestSupport.SERVICE.blueprint(bad, probe));
		assertEquals(Hearth.id("karst"), BuildTestSupport.SERVICE.style(dev.voidmc.hearth.api.Culture.KARST));
	}

	@Test
	void workAndGoodsAreCountedPerStage() {
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 3);
		var info = BuildTestSupport.SERVICE.archetype(Hearth.id("house/cottage"));
		PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(dev.voidmc.hearth.api.Culture.TEMPERATE), 77L, Direction.WEST, probe);
		BlueprintImpl bp = BuildTestSupport.SERVICE.compiled(spec, probe);
		int work = 0;
		int[] goods = bp.totalGoods();
		for (int s = 0; s < bp.stageCount(); s++) {
			work += bp.work(s);
		}
		assertTrue(work > 500, "a cottage is more than 500 blocks of work: " + work);
		assertTrue(goods[Goods.WOOD.ordinal()] > 50 && goods[Goods.STONE.ordinal()] > 20, "a timber cottage on stone needs wood and stone");
		assertEquals(0, bp.goods(bp.stageIndex(StageKind.CLEAR))[Goods.WOOD.ordinal()], "clearing costs no goods");
	}
}
