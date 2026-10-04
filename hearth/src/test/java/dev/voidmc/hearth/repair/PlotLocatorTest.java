package dev.voidmc.hearth.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlotLocatorTest {
	@BeforeAll
	static void boot() {
		RepairTestKit.boot();
	}

	/** A small house: floor, a wall that is carved for a door in the next stage, a lantern on top. */
	private static RepairTestKit.StepBlueprint house(SimpleSettlement s, RepairTestKit.Source src, SimpleSettlement.SimplePlot plot) {
		RepairTestKit.StepBlueprint bp = new RepairTestKit.StepBlueprint(plot.spec());
		bp.stage().put(0, 64, 0, Blocks.COBBLESTONE.defaultBlockState()).put(1, 64, 0, Blocks.COBBLESTONE.defaultBlockState())
			.put(0, 65, 0, Blocks.OAK_PLANKS.defaultBlockState());
		bp.stage().put(0, 65, 0, Blocks.AIR.defaultBlockState()).put(20, 65, 0, Blocks.GLASS.defaultBlockState());
		bp.stage().put(1, 66, 0, Blocks.LANTERN.defaultBlockState());
		src.blueprints.put(plot.id(), bp);
		return bp;
	}

	@Test
	void findsTheBlockThatStandsAndItsStage() {
		SimpleSettlement s = RepairTestKit.settlement();
		RepairTestKit.Source src = new RepairTestKit.Source();
		SimpleSettlement.SimplePlot p = s.addPlot(RepairTestKit.spec("house/cottage", 0, 64, 0, 24, 8));
		house(s, src, p);
		p.state = PlotState.BUILT;
		p.stage = 3;
		PlotLocator loc = new PlotLocator(10_000, 600);
		PlotLocator.Hit hit = loc.locate(s, src, new BlockPos(0, 64, 0), 0);
		assertNotNull(hit);
		assertEquals(0, hit.stage());
		assertEquals(Blocks.COBBLESTONE, hit.expected().getBlock());
		PlotLocator.Hit carved = loc.locate(s, src, new BlockPos(0, 65, 0), 0);
		assertNotNull(carved);
		assertTrue(carved.expected().isAir(), "the last write wins: the doorway was carved out of the wall");
		assertEquals(1, carved.stage());
		PlotLocator.Hit other = loc.locate(s, src, new BlockPos(20, 65, 0), 0);
		assertNotNull(other, "a cell in another chunk of the plot");
		assertEquals(Blocks.GLASS, other.expected().getBlock());
		assertNull(loc.locate(s, src, new BlockPos(5, 64, 5), 0), "inside the footprint, but no step there");
	}

	@Test
	void onlyFinishedStagesStand() {
		SimpleSettlement s = RepairTestKit.settlement();
		RepairTestKit.Source src = new RepairTestKit.Source();
		SimpleSettlement.SimplePlot p = s.addPlot(RepairTestKit.spec("house/cottage", 0, 64, 0, 24, 8));
		house(s, src, p);
		p.state = PlotState.BUILDING;
		p.stage = 1;
		PlotLocator loc = new PlotLocator(10_000, 600);
		assertNotNull(loc.locate(s, src, new BlockPos(0, 64, 0), 0));
		assertEquals(Blocks.OAK_PLANKS, loc.locate(s, src, new BlockPos(0, 65, 0), 0).expected().getBlock(), "the carving is not done yet");
		assertNull(loc.locate(s, src, new BlockPos(1, 66, 0), 0), "the lantern's stage is still to come");
		p.stage = 0;
		assertNull(loc.locate(s, src, new BlockPos(0, 64, 0), 0), "nothing stands before the first stage is done");
		p.state = PlotState.DEMOLISHING;
		p.stage = 3;
		assertNull(loc.locate(s, src, new BlockPos(0, 64, 0), 0), "a plot coming down is nobody's to mend");
	}

	@Test
	void laterPlotsWinAndExcavationsAreNeverDamage() {
		SimpleSettlement s = RepairTestKit.settlement();
		RepairTestKit.Source src = new RepairTestKit.Source();
		SimpleSettlement.SimplePlot terrace = s.addPlot(RepairTestKit.spec("infra/terrace", 0, 64, 0, 8, 8));
		RepairTestKit.StepBlueprint t = new RepairTestKit.StepBlueprint(terrace.spec());
		t.stage().put(2, 63, 2, Blocks.STONE_BRICKS.defaultBlockState()).put(3, 63, 2, Blocks.STONE_BRICKS.defaultBlockState());
		src.blueprints.put(terrace.id(), t);
		terrace.state = PlotState.BUILT;
		terrace.stage = 1;
		SimpleSettlement.SimplePlot house = s.addPlot(RepairTestKit.spec("house/hut", 2, 64, 2, 4, 4));
		RepairTestKit.StepBlueprint h = new RepairTestKit.StepBlueprint(house.spec());
		h.stage().put(2, 63, 2, Blocks.OAK_PLANKS.defaultBlockState());
		src.blueprints.put(house.id(), h);
		house.state = PlotState.BUILT;
		house.stage = 1;
		SimpleSettlement.SimplePlot quarry = s.addPlot(RepairTestKit.spec("extract/quarry_pit", 0, 64, 0, 8, 8));
		RepairTestKit.StepBlueprint qb = new RepairTestKit.StepBlueprint(quarry.spec());
		qb.stage().put(5, 60, 5, Blocks.AIR.defaultBlockState()).put(3, 63, 2, Blocks.AIR.defaultBlockState());
		src.blueprints.put(quarry.id(), qb);
		quarry.state = PlotState.BUILT;
		quarry.stage = 1;
		PlotLocator loc = new PlotLocator(10_000, 600);
		PlotLocator.Hit hit = loc.locate(s, src, new BlockPos(2, 63, 2), 0);
		assertEquals(house.id(), hit.plot().id(), "the house on the terrace stands where they overlap");
		assertEquals(Blocks.OAK_PLANKS, hit.expected().getBlock());
		PlotLocator.Hit under = loc.locate(s, src, new BlockPos(3, 63, 2), 0);
		assertEquals(terrace.id(), under.plot().id(), "the excavation's cut does not count; the terrace's block does");
		assertNull(loc.locate(s, src, new BlockPos(5, 60, 5), 0), "a quarry's cuts are never damage");
	}

	@Test
	void sectionsAreCachedAndBounded() {
		SimpleSettlement s = RepairTestKit.settlement();
		RepairTestKit.Source src = new RepairTestKit.Source();
		SimpleSettlement.SimplePlot p = s.addPlot(RepairTestKit.spec("house/cottage", 0, 64, 0, 24, 8));
		RepairTestKit.StepBlueprint bp = house(s, src, p);
		p.state = PlotState.BUILT;
		p.stage = 3;
		PlotLocator loc = new PlotLocator(3, 600);
		loc.locate(s, src, new BlockPos(0, 64, 0), 0);
		int calls = bp.chunkCalls;
		for (int i = 0; i < 50; i++) {
			loc.locate(s, src, new BlockPos(1, 64, 0), 0);
		}
		assertEquals(calls, bp.chunkCalls, "a section is read once");
		loc.locate(s, src, new BlockPos(20, 65, 0), 0);
		assertTrue(loc.cells() <= 3, "the cache stays within its bound: " + loc.cells());
	}

	@Test
	void newPlotsAppearAfterTheGridExpires() {
		SimpleSettlement s = RepairTestKit.settlement();
		RepairTestKit.Source src = new RepairTestKit.Source();
		PlotLocator loc = new PlotLocator(10_000, 100);
		assertNull(loc.locate(s, src, new BlockPos(0, 64, 0), 0));
		SimpleSettlement.SimplePlot p = s.addPlot(RepairTestKit.spec("house/cottage", 0, 64, 0, 24, 8));
		house(s, src, p);
		p.state = PlotState.BUILT;
		p.stage = 3;
		assertNull(loc.locate(s, src, new BlockPos(0, 64, 0), 50), "the grid is trusted for a while");
		assertNotNull(loc.locate(s, src, new BlockPos(0, 64, 0), 150));
		loc.invalidate(s.id());
		assertNotNull(loc.locate(s, src, new BlockPos(0, 64, 0), 151));
	}

	@Test
	void resolvesTheLastStepWritingACell() {
		SimpleSettlement s = RepairTestKit.settlement();
		RepairTestKit.Source src = new RepairTestKit.Source();
		SimpleSettlement.SimplePlot p = s.addPlot(RepairTestKit.spec("house/cottage", 0, 64, 0, 24, 8));
		RepairTestKit.StepBlueprint bp = new RepairTestKit.StepBlueprint(p.spec());
		bp.stage().put(0, 64, 0, Blocks.DIRT.defaultBlockState()).put(1, 64, 0, Blocks.DIRT.defaultBlockState()).put(0, 64, 0, Blocks.COBBLESTONE.defaultBlockState());
		assertEquals(2, PlotLocator.resolveIndex(bp, 0, new BlockPos(0, 64, 0)));
		assertEquals(1, PlotLocator.resolveIndex(bp, 0, new BlockPos(1, 64, 0)));
		assertEquals(-1, PlotLocator.resolveIndex(bp, 0, new BlockPos(9, 64, 0)));
		assertEquals(-1, PlotLocator.resolveIndex(bp, 3, new BlockPos(0, 64, 0)));
	}

	@Test
	void walksEveryCellOfAPlot() {
		SimpleSettlement s = RepairTestKit.settlement();
		RepairTestKit.Source src = new RepairTestKit.Source();
		SimpleSettlement.SimplePlot p = s.addPlot(RepairTestKit.spec("house/cottage", 0, 64, 0, 24, 8));
		house(s, src, p);
		p.state = PlotState.BUILT;
		p.stage = 3;
		PlotLocator loc = new PlotLocator(10_000, 600);
		int[] n = {0};
		assertTrue(loc.forEachCell(s, src, p, (pos, stage, expected) -> n[0]++));
		assertEquals(5, n[0], "five distinct cells: the carved doorway counted once");
	}
}
