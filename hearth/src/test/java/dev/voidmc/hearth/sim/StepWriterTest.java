package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.apply.AppliedLedger;
import dev.voidmc.hearth.sim.apply.StepWriter;
import dev.voidmc.hearth.sim.dev.PlaceholderBlueprints;
import dev.voidmc.hearth.sim.settlement.Cursor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StepWriterTest {
	/** A chunk as a map: air wherever nothing was set. */
	static final class MapWorld implements StepWriter.Blocks {
		final Map<Long, BlockState> blocks = new HashMap<>();
		int sets;

		@Override
		public BlockState get(int x, int y, int z) {
			return this.blocks.getOrDefault(BlockPos.asLong(x, y, z), Blocks.AIR.defaultBlockState());
		}

		@Override
		public void set(int x, int y, int z, BlockState state, @Nullable CompoundTag tag) {
			this.blocks.put(BlockPos.asLong(x, y, z), state);
			this.sets++;
		}

		@Override
		public boolean naturalTree(int x, int y, int z) {
			return false;
		}
	}

	private static Blueprint cottage() {
		TestSupport.boot();
		PlotSpec spec = new PlotSpec(PlaceholderBlueprints.COTTAGE, Hearth.id("temperate"), 5, new BlockPos(2, 64, 2), 9, 9, Direction.SOUTH, 8, 0,
			new CompoundTag());
		return TestSupport.blueprints.blueprint(spec, new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 1));
	}

	private static StepWriter.Owned none() {
		return StepWriter.lazyOwned(sink -> {
		});
	}

	@Test
	void rangesAddUp() {
		Blueprint bp = cottage();
		int total = 0;
		for (int s = 0; s < bp.stageCount(); s++) {
			total += bp.stepCount(s);
		}
		int[] whole = {0};
		StepWriter.forRange(bp, 0, 0, Cursor.ZERO, Cursor.pack(bp.stageCount(), 0), (x, y, z, st, t) -> whole[0]++);
		assertEquals(total, whole[0], "the whole plot lies in chunk 0,0");
		// cut anywhere, the pieces are the whole
		long mid = Cursor.pack(2, bp.stepCount(2) / 2);
		int[] parts = {0};
		StepWriter.forRange(bp, 0, 0, Cursor.ZERO, mid, (x, y, z, st, t) -> parts[0]++);
		StepWriter.forRange(bp, 0, 0, mid, Cursor.pack(bp.stageCount(), 0), (x, y, z, st, t) -> parts[0]++);
		assertEquals(total, parts[0]);
		int[] other = {0};
		StepWriter.forRange(bp, 1, 0, Cursor.ZERO, Cursor.pack(bp.stageCount(), 0), (x, y, z, st, t) -> other[0]++);
		assertEquals(0, other[0], "nothing in a chunk the plot does not touch");
	}

	@Test
	void writesOverNatureButNeverOverAPlayer() {
		Blueprint bp = cottage();
		MapWorld world = new MapWorld();
		// grass on the ground, and a player's brick wall standing where the house's walls go
		for (int x = 2; x < 11; x++) {
			for (int z = 2; z < 11; z++) {
				world.blocks.put(BlockPos.asLong(x, 64, z), Blocks.SHORT_GRASS.defaultBlockState());
			}
		}
		for (int z = 3; z < 10; z++) {
			world.blocks.put(BlockPos.asLong(3, 65, z), Blocks.BRICKS.defaultBlockState());
		}
		world.blocks.put(BlockPos.asLong(5, 64, 5), Blocks.CHEST.defaultBlockState());
		StepWriter.Result r = StepWriter.write(bp, 0, 0, Cursor.ZERO, Cursor.pack(bp.stageCount(), 0), world, none());
		assertTrue(r.placed > 100, "placed " + r.placed);
		assertTrue(r.conflicts > 0, "the bricks and the chest are refused");
		for (int z = 3; z < 10; z++) {
			assertEquals(Blocks.BRICKS, world.get(3, 65, z).getBlock(), "the player's wall stands");
		}
		assertEquals(Blocks.CHEST, world.get(5, 64, 5).getBlock(), "block entities are never replaced");
		assertTrue(world.get(4, 64, 4).isAir() || !world.get(4, 64, 4).is(Blocks.SHORT_GRASS), "grass inside the house is cleared");
	}

	@Test
	void laterStagesReplaceEarlierOnesOfTheSamePlot() {
		Blueprint bp = cottage();
		MapWorld world = new MapWorld();
		long half = Cursor.pack(bp.stageCount() / 2, 0);
		StepWriter.write(bp, 0, 0, Cursor.ZERO, half, world, none());
		// what the first half put there, as the applier would rebuild it from the ledger
		StepWriter.Owned owned = StepWriter.lazyOwned(sink -> StepWriter.forApplied(bp, 0, 0, half, sink));
		StepWriter.Result r = StepWriter.write(bp, 0, 0, half, Cursor.pack(bp.stageCount(), 0), world, owned);
		assertEquals(0, r.conflicts, "nothing of our own is ever in our way");
		// and writing again changes nothing
		int sets = world.sets;
		StepWriter.Result again = StepWriter.write(bp, 0, 0, Cursor.ZERO, Cursor.pack(bp.stageCount(), 0), world,
			StepWriter.lazyOwned(sink -> StepWriter.forApplied(bp, 0, 0, Cursor.pack(bp.stageCount(), 0), sink)));
		assertEquals(0, again.conflicts);
		assertTrue(world.sets - sets <= again.placed);
	}

	@Test
	void ledgerNeverMovesBack() {
		AppliedLedger l = AppliedLedger.EMPTY.with(1, 1, Cursor.pack(4, 0));
		assertEquals(Cursor.pack(4, 0), l.with(1, 1, Cursor.pack(2, 9)).get(1, 1));
		assertEquals(Cursor.pack(5, 3), l.with(1, 1, Cursor.pack(5, 3)).get(1, 1));
		assertEquals(Cursor.ZERO, l.get(1, 2));
		assertEquals(Cursor.ZERO, l.get(2, 1));
		assertTrue(l.with(2, 1, 1).without(1).has(2, 1));
		assertTrue(!l.without(1).has(1, 1));
	}
}
