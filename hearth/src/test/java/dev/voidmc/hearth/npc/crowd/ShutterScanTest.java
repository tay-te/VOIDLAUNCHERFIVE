package dev.voidmc.hearth.npc.crowd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.repair.DamageRules;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Window shutters found on built blueprints, and nothing else (docs/LIVING_WORLD_2.md §5.3, §5.8): the leaves beside
 * dressed windows, not doors, lattice screens, hatches or cellar doors; and a closed shutter is never damage a repair
 * puts back.
 */
class ShutterScanTest {
	private static BlueprintServiceImpl service;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		service = BuildModule.standalone();
	}

	private static BlockState trapdoor(Direction facing, boolean open) {
		return Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.HALF, Half.BOTTOM)
			.setValue(TrapDoorBlock.OPEN, open);
	}

	@Test
	void aDressedWindowsLeavesAreShutters() {
		// a wall along x at z = 0, its outside to the south (+z); a window of two panes at x = 1..2
		Long2ObjectOpenHashMap<BlockState> cells = new Long2ObjectOpenHashMap<>();
		for (int x = -1; x <= 4; x++) {
			cells.put(BlockPos.asLong(x, 1, 0), x == 1 || x == 2 ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		// leaves hung outside the wall beside the window, folded back against it (open, facing out)
		cells.put(BlockPos.asLong(0, 1, 1), trapdoor(Direction.SOUTH, true));
		cells.put(BlockPos.asLong(3, 1, 1), trapdoor(Direction.SOUTH, true));
		// a lattice screen in the wall's own plane, a hatch with nothing beside it, a closed cellar door, a door
		cells.put(BlockPos.asLong(6, 1, 0), trapdoor(Direction.NORTH, true));
		cells.put(BlockPos.asLong(8, 1, 1), trapdoor(Direction.SOUTH, true));
		cells.put(BlockPos.asLong(8, 1, 0), Blocks.SPRUCE_PLANKS.defaultBlockState());
		cells.put(BlockPos.asLong(1, 0, 3), trapdoor(Direction.SOUTH, false));
		cells.put(BlockPos.asLong(4, 1, 1), Blocks.SPRUCE_DOOR.defaultBlockState());
		long[] found = ShutterScan.scan(cells);
		java.util.Arrays.sort(found);
		long[] want = {BlockPos.asLong(0, 1, 1), BlockPos.asLong(3, 1, 1)};
		java.util.Arrays.sort(want);
		assertArrayEquals(want, found);
	}

	@Test
	void builtHousesHaveShuttersAndNoDoorAmongThem() {
		int shutters = 0;
		int buildings = 0;
		int withShutters = 0;
		for (ArchetypeInfo info : service.archetypes()) {
			if (info.category() != ArchetypeCategory.HOUSING) {
				continue;
			}
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.ALPINE, Culture.NORTHERN, Culture.MEDITERRANEAN}) {
				if (!info.suits(c)) {
					continue;
				}
				for (long seed = 1; seed <= 2; seed++) {
					Blueprint bp = blueprint(info, c, seed);
					if (bp == null) {
						continue;
					}
					buildings++;
					Long2ObjectOpenHashMap<BlockState> cells = cells(bp);
					long[] found = ShutterScan.scan(bp, bp.stageCount());
					if (found.length > 0) {
						withShutters++;
					}
					shutters += found.length;
					for (long at : found) {
						BlockState s = cells.get(at);
						assertTrue(s.getBlock() instanceof TrapDoorBlock && s.getValue(TrapDoorBlock.OPEN), info.id() + " " + BlockPos.of(at) + " " + s);
						assertFalse(s.getBlock() instanceof DoorBlock);
						// closing it is no damage, and nothing an inspection would put back
						BlockState closed = s.setValue(TrapDoorBlock.OPEN, false);
						assertFalse(DamageRules.isDamage(s, s, closed), "a closed shutter is not damage");
						assertFalse(DamageRules.missing(s, closed), "a closed shutter is not missing");
						assertTrue(DamageRules.onlyOpenDiffers(s, closed));
					}
				}
			}
		}
		System.out.printf("shutter scan: %d shutters on %d of %d housing blueprints%n", shutters, withShutters, buildings);
		assertTrue(buildings > 10, "housing blueprints were built: " + buildings);
		assertTrue(withShutters > 0 && shutters >= 4, "some houses dress their windows with shutters: " + shutters);
	}

	@Test
	void doorsAndGatesOpenOrShutAreNotDamage() {
		BlockState door = Blocks.OAK_DOOR.defaultBlockState();
		BlockState open = door.setValue(DoorBlock.OPEN, true);
		assertTrue(DamageRules.onlyOpenDiffers(door, open));
		assertFalse(DamageRules.isDamage(door, door, open));
		assertFalse(DamageRules.onlyOpenDiffers(door, Blocks.SPRUCE_DOOR.defaultBlockState()), "another door is another block");
		assertFalse(DamageRules.onlyOpenDiffers(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState()), "no open state at all");
		BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState();
		assertTrue(DamageRules.onlyOpenDiffers(gate, gate.setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN, true)));
		assertEquals(false, DamageRules.missing(trapdoor(Direction.EAST, true), trapdoor(Direction.EAST, false)));
	}

	private static Long2ObjectOpenHashMap<BlockState> cells(Blueprint bp) {
		Long2ObjectOpenHashMap<BlockState> cells = new Long2ObjectOpenHashMap<>();
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				bp.step(s, i, (x, y, z, state, tag) -> cells.put(BlockPos.asLong(x, y, z), state));
			}
		}
		return cells;
	}

	private static Blueprint blueprint(ArchetypeInfo info, Culture c, long seed) {
		SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 42, c == Culture.ALPINE || c == Culture.NORTHERN ? 0.3F : 0.8F);
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		int floor = probe.groundY(0, depth / 2 + 1);
		PlotSpec spec = new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(-frontage / 2, floor, -depth / 2), frontage, depth,
			Direction.SOUTH, Math.max(info.maxHeight(), 8), 2, new CompoundTag());
		try {
			return service.blueprint(spec, probe);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
