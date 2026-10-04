package dev.voidmc.hearth.npc.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The rules gatherers and haulers work by: who works which excavation, what the chains carry, which trees fall. */
class WorkRulesTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static PlotSpec spec(String archetype, CompoundTag params) {
		return new PlotSpec(Hearth.id(archetype), Hearth.id("temperate"), 1, BlockPos.ZERO, 9, 9, Direction.NORTH, 8, 1, params);
	}

	@Test
	void excavationsAreKnownByTheirArchetype() {
		SimpleSettlement s = new SimpleSettlement(1, "Pitton", Culture.TEMPERATE, BlockPos.ZERO, 0);
		var quarry = s.addPlot(spec("extract/quarry_pit", new CompoundTag()));
		var house = s.addPlot(spec("house/cottage", new CompoundTag()));
		quarry.state = PlotState.BUILDING;
		house.state = PlotState.BUILDING;
		assertTrue(Extraction.is(quarry));
		assertFalse(Extraction.is(house));
		assertTrue(Extraction.workable(quarry));
		assertFalse(Extraction.workable(house));
		quarry.waitingForGoods = true;
		assertFalse(Extraction.workable(quarry), "a bench waiting for its goods is left alone");
		// and builders never take an excavation
		quarry.waitingForGoods = false;
		assertFalse(BuilderWork.buildable(quarry));
		assertTrue(BuilderWork.buildable(house));
	}

	@Test
	void theKindParamWinsOverTheName() {
		SimpleSettlement s = new SimpleSettlement(1, "Pitton", Culture.TEMPERATE, BlockPos.ZERO, 0);
		CompoundTag p = new CompoundTag();
		p.putString("kind", "clay");
		assertEquals(ResourceSite.Kind.CLAY, Extraction.kind(s.addPlot(spec("extract/quarry_pit", p))));
		assertEquals(ResourceSite.Kind.STONE, Extraction.kind(s.addPlot(spec("extract/quarry_pit", new CompoundTag()))));
		assertEquals(ResourceSite.Kind.ORE, Extraction.kindOf("extract/mine"));
		assertEquals(ResourceSite.Kind.ORE, Extraction.kindOf("extract/shaft"));
		assertEquals(ResourceSite.Kind.SAND, Extraction.kindOf("extract/sand_pit"));
		assertEquals(ResourceSite.Kind.WOODS, Extraction.kindOf("extract/plantation"));
	}

	@Test
	void eachTradeWorksItsOwnExcavationFirst() {
		assertEquals(2, Extraction.fit(JobType.MINER, ResourceSite.Kind.ORE));
		assertEquals(1, Extraction.fit(JobType.MINER, ResourceSite.Kind.STONE));
		assertEquals(2, Extraction.fit(JobType.QUARRIER, ResourceSite.Kind.STONE));
		assertEquals(1, Extraction.fit(JobType.QUARRIER, ResourceSite.Kind.ORE));
		assertEquals(2, Extraction.fit(JobType.CLAY_DIGGER, ResourceSite.Kind.SAND));
		assertEquals(0, Extraction.fit(JobType.CLAY_DIGGER, ResourceSite.Kind.ORE));
		assertEquals(2, Extraction.fit(JobType.FORESTER, ResourceSite.Kind.WOODS));
		assertEquals(0, Extraction.fit(JobType.BUILDER, ResourceSite.Kind.STONE));
		assertEquals(0, Extraction.fit(JobType.FARMER, ResourceSite.Kind.FERTILE));
	}

	@Test
	void everyTradeHasWorkAndTheGatherersHaveTheirs() {
		for (JobType job : JobType.values()) {
			for (AgeStage age : AgeStage.values()) {
				assertNotNull(WorkBehaviour.of(job, age), job + " " + age);
			}
		}
		assertInstanceOf(ExcavationWork.class, WorkBehaviour.of(JobType.MINER, AgeStage.ADULT));
		assertInstanceOf(ExcavationWork.class, WorkBehaviour.of(JobType.QUARRIER, AgeStage.ADULT));
		assertInstanceOf(ExcavationWork.class, WorkBehaviour.of(JobType.CLAY_DIGGER, AgeStage.ADULT));
		assertInstanceOf(ForesterWork.class, WorkBehaviour.of(JobType.FORESTER, AgeStage.ADULT));
		assertInstanceOf(HerderWork.class, WorkBehaviour.of(JobType.HERDER, AgeStage.ADULT));
		assertInstanceOf(FisherWork.class, WorkBehaviour.of(JobType.FISHER, AgeStage.ADULT));
		assertInstanceOf(BeekeeperWork.class, WorkBehaviour.of(JobType.BEEKEEPER, AgeStage.ADULT));
		assertInstanceOf(HaulerWork.class, WorkBehaviour.of(JobType.HAULER, AgeStage.ADULT));
		assertInstanceOf(LordWork.class, WorkBehaviour.of(JobType.LORD, AgeStage.ADULT));
		assertInstanceOf(OfficialWork.class, WorkBehaviour.of(JobType.REEVE, AgeStage.ADULT));
		assertInstanceOf(ChoresWork.class, WorkBehaviour.of(JobType.MINER, AgeStage.CHILD), "children do chores, not mining");
	}

	@Test
	void theChainsCarryWhatIsMadeToWhereItIsWanted() {
		assertEquals(HaulerWork.Kind.LOGS, HaulerWork.produces("work/logging_camp"));
		assertEquals(HaulerWork.Kind.STONE, HaulerWork.produces("work/quarry"));
		assertEquals(HaulerWork.Kind.GRAIN, HaulerWork.produces("farm/field"));
		assertEquals(HaulerWork.Kind.CHARCOAL, HaulerWork.produces("work/charcoal_kiln"));
		assertEquals(null, HaulerWork.produces("house/cottage"));
		assertTrue(HaulerWork.wants("work/smelter").containsAll(Set.of(HaulerWork.Kind.ORE, HaulerWork.Kind.CHARCOAL)));
		assertTrue(HaulerWork.wants("work/charcoal_kiln").contains(HaulerWork.Kind.LOGS));
		assertTrue(HaulerWork.wants("work/brick_kiln").contains(HaulerWork.Kind.CLAY));
		assertTrue(HaulerWork.wants("storage/granary").contains(HaulerWork.Kind.GOODS));
		assertEquals(HaulerWork.Kind.ORE, HaulerWork.fromSite(ResourceSite.Kind.ORE));
		assertEquals(HaulerWork.Kind.LOGS, HaulerWork.fromSite(ResourceSite.Kind.WOODS));
		for (HaulerWork.Kind k : HaulerWork.Kind.values()) {
			assertNotNull(HaulerWork.loadItem(k, Culture.NORTHERN), k + " has nothing to carry");
			assertFalse(HaulerWork.loadItem(k, Culture.NORTHERN) == Items.AIR, k + " has nothing to carry");
		}
		assertEquals(Items.SPRUCE_LOG, HaulerWork.loadItem(HaulerWork.Kind.LOGS, Culture.NORTHERN));
	}

	@Test
	void saplingsFollowTheTrunk() {
		assertEquals(Blocks.OAK_SAPLING, Trees.saplingFor(Blocks.OAK_LOG).orElseThrow());
		assertEquals(Blocks.SPRUCE_SAPLING, Trees.saplingFor(Blocks.SPRUCE_LOG).orElseThrow());
		assertEquals(Blocks.DARK_OAK_SAPLING, Trees.saplingFor(Blocks.DARK_OAK_LOG).orElseThrow());
		assertEquals(Blocks.CHERRY_SAPLING, Trees.saplingFor(Blocks.CHERRY_LOG).orElseThrow());
		assertEquals(Blocks.MANGROVE_PROPAGULE, Trees.saplingFor(Blocks.MANGROVE_LOG).orElseThrow());
		assertEquals(Blocks.CRIMSON_FUNGUS, Trees.saplingFor(Blocks.CRIMSON_STEM).orElseThrow());
		assertEquals(Blocks.BIRCH_SAPLING, Trees.saplingFor(Blocks.STRIPPED_BIRCH_LOG).orElseThrow());
	}

	@Test
	void onlyNaturalCrownsMakeATree() {
		assertTrue(Trees.isNaturalLeaves(Blocks.OAK_LEAVES.defaultBlockState()));
		assertFalse(Trees.isNaturalLeaves(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)),
			"placed leaves (a hedge, a roof garden) are not a tree");
		assertFalse(Trees.isNaturalLeaves(Blocks.OAK_LOG.defaultBlockState()));
		// (tags are not loaded in unit tests: planks, fences and the like are covered by their tags in game)
		assertTrue(Trees.built(Blocks.CHEST.defaultBlockState()));
		assertTrue(Trees.built(Blocks.GLASS.defaultBlockState()));
		assertTrue(Trees.built(Blocks.LANTERN.defaultBlockState()));
		assertFalse(Trees.built(Blocks.GRASS_BLOCK.defaultBlockState()));
		assertFalse(Trees.built(Blocks.VINE.defaultBlockState()));
	}

	@Test
	void treesInsideTheTownOrBesideAPlotAreLeftStanding() {
		SimpleSettlement s = new SimpleSettlement(1, "Ashby", Culture.TEMPERATE, BlockPos.ZERO, 0);
		var house = s.addPlot(new PlotSpec(Hearth.id("house/cottage"), Hearth.id("temperate"), 1, new BlockPos(0, 64, 0), 9, 9, Direction.NORTH, 8, 1,
			new CompoundTag()));
		house.state = PlotState.BUILT;
		// inside the town's bounds (and right by the cottage)
		assertFalse(Trees.allowedAt(s, new BlockPos(4, 64, 4), java.util.List.of(), 8));
		// well outside
		assertTrue(Trees.allowedAt(s, new BlockPos(60, 64, 60), java.util.List.of(), 8));
		// a deeded plot keeps foresters further off
		var deeded = s.addPlot(new PlotSpec(Hearth.id("house/cottage"), Hearth.id("temperate"), 2, new BlockPos(80, 64, 80), 9, 9, Direction.NORTH, 8, 1,
			new CompoundTag()));
		deeded.owner = java.util.UUID.randomUUID();
		assertFalse(Trees.allowedAt(s, new BlockPos(94, 64, 84), java.util.List.of(), 8));
		assertTrue(Trees.allowedAt(s, new BlockPos(100, 64, 84), java.util.List.of(), 8));
	}
}
