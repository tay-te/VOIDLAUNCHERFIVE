package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.sim.apply.AppliedLedger;
import dev.voidmc.hearth.sim.apply.StepWriter;
import dev.voidmc.hearth.sim.settlement.Cursor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Roads written into land that is already there (docs/LIVING_WORLD_2.md §12, risk 1): over natural ground the road goes
 * in; where a player has built across its way the plot is refused whole — nothing of it written — and a lone block of
 * someone's is simply left standing while the road goes round it.
 */
class RoadWriterTest {
	static BlueprintServiceImpl service;
	static final TerrainProbe FLAT = new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 2);
	static BlockState NATURAL;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		service = BuildModule.standalone();
		NATURAL = Blocks.SHORT_GRASS.defaultBlockState();
	}

	/**
	 * A chunk of natural land as a map. (Unit tests have no data packs, so the {@code hearth:buildable_over} tag that marks
	 * grass and dirt as natural ground is empty: the ground here is a block that is replaceable by itself, which the writer
	 * treats the same.)
	 */
	static final class Land implements StepWriter.Blocks {
		final Map<Long, BlockState> placed = new HashMap<>();
		int sets;

		@Override
		public BlockState get(int x, int y, int z) {
			BlockState s = this.placed.get(BlockPos.asLong(x, y, z));
			if (s != null) {
				return s;
			}
			int g = FLAT.groundY(x, z);
			return y < g ? NATURAL : Blocks.AIR.defaultBlockState();
		}

		@Override
		public void set(int x, int y, int z, BlockState state, @Nullable CompoundTag tag) {
			this.placed.put(BlockPos.asLong(x, y, z), state);
			this.sets++;
		}

		@Override
		public boolean naturalTree(int x, int y, int z) {
			return false;
		}
	}

	/** A track's plots straight across chunk 0,0 and on. */
	static List<RoadWriter.Job> jobs() {
		Route r = RoadRouter.route(FLAT, FLAT, 1, 8, 60, 8, RoadRouter.Avoid.NONE);
		assertNotNull(r);
		RoadGraph.Edge e = new RoadGraph.Edge(42, 1, 2, RoadClass.TRACK, true, r.length());
		RoadSegments.Dressing dress = new RoadSegments.Dressing(Hearth.id("temperate"), Hearth.id("temperate"), "", "", false);
		Way w = Way.of(e, 1, 8, 60, 8, "A", "B", r, RoadSegments.cut(e.id(), RoadClass.TRACK, r, dress), false);
		return List.of(new RoadWriter.Job(w, 0, Long.MAX_VALUE));
	}

	@Test
	void overNaturalGroundTheRoadGoesIn() {
		List<RoadWriter.Job> jobs = jobs();
		Land land = new Land();
		RoadWriter.Outcome o = RoadWriter.write(0, 0, AppliedLedger.EMPTY, jobs, land, service, FLAT, true, null, System.nanoTime(), Long.MAX_VALUE,
			Integer.MAX_VALUE);
		assertTrue(o.refused.isEmpty(), "nothing in the way");
		assertTrue(o.placed > 20, "the road went in: " + o.placed + " blocks");
		Blueprint bp = service.blueprint(jobs.getFirst().segment().spec(), FLAT);
		assertEquals(Cursor.pack(bp.stageCount(), 0), o.ledger.get(RoadWorld.ledgerId(42), 0), "the ledger has all of it here");
		// the surface along the centreline is the road's, not grass
		int g = FLAT.groundY(8, 8);
		assertFalse(land.get(8, g - 1, 8) == NATURAL && land.get(9, g - 1, 8) == NATURAL && land.get(10, g - 1, 8) == NATURAL, "the road's surface is down");
	}

	@Test
	void aPlayersHouseAcrossTheWayRefusesThePlotWhole() {
		List<RoadWriter.Job> jobs = jobs();
		Land land = new Land();
		int g = FLAT.groundY(8, 8);
		// a cobblestone cottage across the road, three high
		for (int x = 5; x <= 11; x++) {
			for (int z = 4; z <= 12; z++) {
				for (int y = g - 1; y < g + 3; y++) {
					land.placed.put(BlockPos.asLong(x, y, z), Blocks.COBBLESTONE.defaultBlockState());
				}
			}
		}
		int before = land.sets;
		RoadWriter.Outcome o = RoadWriter.write(0, 0, AppliedLedger.EMPTY, jobs, land, service, FLAT, true, null, System.nanoTime(), Long.MAX_VALUE,
			Integer.MAX_VALUE);
		assertEquals(1, o.refused.size(), "refused");
		assertEquals(before, land.sets, "nothing of it written");
		assertEquals(Cursor.ZERO, o.ledger.get(RoadWorld.ledgerId(42), 0));
		assertTrue(land.get(8, g, 8).is(Blocks.COBBLESTONE), "the house stands");
	}

	@Test
	void aLoneBlockOfSomeonesIsLeftAndTheRoadGoesOn() {
		List<RoadWriter.Job> jobs = jobs();
		Land land = new Land();
		int g = FLAT.groundY(8, 8);
		land.placed.put(BlockPos.asLong(8, g, 8), Blocks.OAK_FENCE.defaultBlockState());
		RoadWriter.Outcome o = RoadWriter.write(0, 0, AppliedLedger.EMPTY, jobs, land, service, FLAT, true, null, System.nanoTime(), Long.MAX_VALUE,
			Integer.MAX_VALUE);
		assertTrue(o.refused.isEmpty(), "one fence post does not stop a road");
		assertTrue(o.placed > 20);
		assertTrue(land.get(8, g, 8).is(Blocks.OAK_FENCE), "but it is not taken down either");
	}

	@Test
	void writingTwiceWritesNothingMore() {
		List<RoadWriter.Job> jobs = jobs();
		Land land = new Land();
		RoadWriter.Outcome first = RoadWriter.write(0, 0, AppliedLedger.EMPTY, jobs, land, service, FLAT, true, null, System.nanoTime(), Long.MAX_VALUE,
			Integer.MAX_VALUE);
		int sets = land.sets;
		RoadWriter.Outcome again = RoadWriter.write(0, 0, first.ledger, jobs, land, service, FLAT, true, null, System.nanoTime(), Long.MAX_VALUE,
			Integer.MAX_VALUE);
		assertEquals(0, again.placed);
		assertEquals(sets, land.sets, "the ledger says it is all there");
	}
}
