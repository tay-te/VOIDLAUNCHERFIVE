package dev.voidmc.hearth.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LitterAndLinesTest {
	@BeforeAll
	static void boot() {
		RepairTestKit.boot();
	}

	@Test
	void litterIsWhatBlocksAWay() {
		SimpleSettlement s = RepairTestKit.settlement();
		RepairTestKit.Source src = new RepairTestKit.Source();
		SimpleSettlement.SimplePlot house = s.addPlot(RepairTestKit.spec("house/cottage", 0, 64, 0, 8, 8));
		RepairTestKit.StepBlueprint bp = new RepairTestKit.StepBlueprint(house.spec());
		bp.stage().put(3, 64, 0, Blocks.OAK_DOOR.defaultBlockState()).put(3, 65, 0, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))
			.put(3, 64, 1, Blocks.AIR.defaultBlockState()).put(6, 64, 6, Blocks.AIR.defaultBlockState()).put(6, 64, -1, Blocks.AIR.defaultBlockState());
		bp.entrances.add(new BlockPos(6, 64, -1));
		src.blueprints.put(house.id(), bp);
		house.state = PlotState.BUILT;
		house.stage = 1;
		PlotLocator loc = new PlotLocator(10_000, 600);
		assertTrue(Litter.blocksWay(loc, s, src, house, new BlockPos(3, 64, 1)), "inside the door");
		assertTrue(Litter.blocksWay(loc, s, src, house, new BlockPos(6, 64, -1)), "on the doorstep");
		assertFalse(Litter.blocksWay(loc, s, src, house, new BlockPos(6, 64, 6)), "a corner of the room is the owner's business");

		SimpleSettlement.SimplePlot road = s.addPlot(RepairTestKit.spec("infra/road", 20, 64, 0, 3, 16));
		RepairTestKit.StepBlueprint rb = new RepairTestKit.StepBlueprint(road.spec());
		rb.stage().put(21, 63, 4, Blocks.DIRT_PATH.defaultBlockState()).put(21, 64, 4, Blocks.AIR.defaultBlockState()).put(21, 65, 4, Blocks.AIR.defaultBlockState())
			.put(21, 66, 4, Blocks.AIR.defaultBlockState());
		src.blueprints.put(road.id(), rb);
		road.state = PlotState.BUILT;
		road.stage = 1;
		assertTrue(Litter.blocksWay(loc, s, src, road, new BlockPos(21, 64, 4)), "on the street");
		assertTrue(Litter.blocksWay(loc, s, src, road, new BlockPos(21, 65, 4)), "at head height over it");
		assertFalse(Litter.blocksWay(loc, s, src, road, new BlockPos(21, 66, 4)), "well overhead");
	}

	@Test
	void buildersNameWhatWasBroken() {
		var spec = RepairTestKit.spec("civic/chapel", 0, 64, 0, 9, 15);
		assertEquals("chapel", RepairLines.building(spec));
		assertEquals("builders yard", RepairLines.building(RepairTestKit.spec("work/builders_yard", 0, 64, 0, 9, 9)));
		assertEquals("window", RepairLines.part(Blocks.GLASS_PANE.defaultBlockState(), 66, 64, 12));
		assertEquals("door", RepairLines.part(Blocks.OAK_DOOR.defaultBlockState(), 64, 64, 12));
		assertEquals("lamp", RepairLines.part(Blocks.LANTERN.defaultBlockState(), 66, 64, 12));
		assertEquals("roof", RepairLines.part(Blocks.OAK_STAIRS.defaultBlockState(), 70, 64, 12));
		assertEquals("wall", RepairLines.part(Blocks.OAK_PLANKS.defaultBlockState(), 65, 64, 12));
		assertEquals("floor", RepairLines.part(Blocks.COBBLESTONE.defaultBlockState(), 63, 64, 12));
		assertNotNull(RepairLines.line(dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause.PLAYER, spec, Blocks.GLASS.defaultBlockState(), 66,
			net.minecraft.util.RandomSource.create(1)));
	}

	@Test
	void labourFollowsBuildersAndTime() {
		assertEquals(16.0, AbstractRepairs.labour(1, 16, 1000, 0), 1e-9, "a builder mends 16 blocks a sim hour");
		assertEquals(3 * 16 * 24 + 0.5, AbstractRepairs.labour(3, 16, 24000, 0.5), 1e-9);
		assertEquals(0.0, AbstractRepairs.labour(0, 16, 24000, 0), 1e-9, "no builders, no mending");
		assertEquals(0.0, AbstractRepairs.labour(2, 16, -5, 0), 1e-9, "a clock that went back gives nothing");
		assertEquals(16 * 24, AbstractRepairs.carry(1e9, 1, 16), 1e-9, "at most a day banked");
		assertEquals(0.75, AbstractRepairs.carry(0.75, 1, 16), 1e-9);
	}

	@Test
	void configDefaultsFromAnEmptyFile() {
		var parsed = RepairConfig.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
		assertEquals(RepairConfig.DEFAULTS, parsed);
		var json = RepairConfig.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, RepairConfig.DEFAULTS).getOrThrow();
		assertEquals(RepairConfig.DEFAULTS, RepairConfig.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
	}
}
