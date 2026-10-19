package dev.voidmc.hearth.sim.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.atlas.History;
import dev.voidmc.hearth.sim.atlas.PlotColumns;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Save, quit and reload: what the store writes is what comes back (one file per settlement, a broken file set aside
 * without costing the others), ids are never handed out twice, and the world generation guard's planned ground stays
 * whole when two settlements plan in the same chunk.
 */
class PersistenceTest {
	private static SimSettlement founded(long id, String terrain, long seed) {
		TestSupport.boot();
		TerrainProbe t = SyntheticTerrain.named(terrain, seed);
		History.Context ctx = TestSupport.context(t);
		SimSettlement s = new SimSettlement(id, "Town " + id, Culture.TEMPERATE, Level.OVERWORLD, new BlockPos(0, t.groundY(0, 0), 0), 0,
			SimSettlement.NO_REGION, seed);
		s.bind(ctx);
		var f = ctx.rules().config.founding();
		SettlementClock.found(s, ctx, f.residents(), f.goodsMap(), f.treasury());
		return s;
	}

	@Test
	void storeRoundTripsEverySettlement(@TempDir Path dir) {
		SettlementStore store = new SettlementStore(dir);
		List<SimSettlement> towns = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			SimSettlement s = founded(i, "hills", 40 + i);
			SettlementClock.advance(s, (10L + i) * 24000L);
			s.addReputation(new UUID(9, i), 5 * i);
			s.deposit(100 * i);
			store.save(s, NbtOps.INSTANCE);
			// saved twice before the first write lands: the later snapshot wins
			s.deposit(1);
			s.markDirty();
			store.save(s, NbtOps.INSTANCE);
			assertFalse(s.isDirty());
			towns.add(s);
		}
		store.flush();
		List<SimSettlement> back = store.loadAll(NbtOps.INSTANCE);
		assertEquals(3, back.size());
		for (SimSettlement s : towns) {
			SimSettlement b = back.stream().filter(x -> x.id() == s.id()).findFirst().orElseThrow();
			assertEquals(s.name(), b.name());
			assertEquals(s.treasury(), b.treasury());
			assertEquals(s.population(), b.population());
			assertEquals(s.simTick(), b.simTick());
			assertEquals(5 * (int) s.id(), b.reputation(new UUID(9, s.id())));
			assertEquals(s.plots().size(), b.plots().size());
			for (SimPlot p : s.plots()) {
				SimPlot q = b.plot(p.id());
				assertNotNull(q);
				assertEquals(p.state(), q.state());
				assertEquals(p.cursor(), q.cursor());
			}
			for (Goods g : Goods.VALUES) {
				assertEquals(s.stockpile().get(g), b.stockpile().get(g), g.getSerializedName());
			}
		}
	}

	@Test
	void aBrokenFileIsSetAsideAndTheRestLoad(@TempDir Path dir) throws Exception {
		SettlementStore store = new SettlementStore(dir);
		store.save(founded(1, "flat", 3), NbtOps.INSTANCE);
		store.save(founded(2, "flat", 4), NbtOps.INSTANCE);
		store.flush();
		Files.writeString(dir.resolve("ff.dat"), "not nbt at all");
		List<SimSettlement> back = store.loadAll(NbtOps.INSTANCE);
		assertEquals(2, back.size());
		assertTrue(Files.exists(dir.resolve("ff.dat.bad")));
		assertFalse(Files.exists(dir.resolve("ff.dat")));
		// and a deleted settlement's file is gone after the writes in flight
		store.delete(2);
		store.flush();
		assertEquals(1, store.loadAll(NbtOps.INSTANCE).size());
	}

	@Test
	void idsAreNeverHandedOutTwice() {
		SimIndex index = new SimIndex();
		assertEquals(1, index.allocateId());
		// a settlement file numbered past what the index remembers (the index was saved before it, then a crash)
		index.reserveId(9);
		assertEquals(10, index.allocateId());
		index.reserveId(4);
		assertEquals(11, index.allocateId());
	}

	@Test
	void sharedChunksKeepBothTownsGuarded() {
		// two towns founded on the same ground plan the same columns: the worst case of margins sharing chunks
		SimSettlement a = founded(1, "flat", 5);
		SimSettlement b = founded(2, "flat", 5);
		SpatialIndex index = new SpatialIndex(160, 3);
		index.update(a);
		index.update(b);
		SimPlot plot = b.plots().stream().filter(p -> p.state() != PlotState.ABANDONED).findFirst().orElseThrow();
		int[] column = new int[2];
		PlotColumns.forEach(plot.spec(), 0, (x, z) -> {
			column[0] = x;
			column[1] = z;
		});
		assertTrue(index.isPlanned(column[0], column[1]));
		// re-indexing one (it planned a plot) and then losing it altogether leave the other's ground planned
		index.update(a);
		assertTrue(index.isPlanned(column[0], column[1]));
		index.remove(a.id());
		assertTrue(index.isPlanned(column[0], column[1]));
		index.remove(b.id());
		assertFalse(index.isPlanned(column[0], column[1]));
	}
}
