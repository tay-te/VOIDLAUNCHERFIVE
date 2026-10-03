package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.sim.apply.AppliedLedger;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimIndex;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class SettlementCodecTest {
	/** Another module's counter, persisted under its own key. */
	static final class Counter {
		final AtomicInteger n;

		Counter(int n) {
			this.n = new AtomicInteger(n);
		}
	}

	static final HearthDataKey<Counter> COUNTER = HearthDataKey.register(Hearth.id("test/counter"), HearthDataKey.Scope.SETTLEMENT,
		Codec.INT.xmap(Counter::new, c -> c.n.get()), () -> new Counter(0));

	private static SimSettlement roundTrip(SimSettlement s) {
		Tag tag = SimSettlement.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
		return SimSettlement.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
	}

	@Test
	void everythingSurvivesARoundTrip() {
		SimSettlement s = TestSupport.founded("hills", 11);
		SettlementClock.advance(s, 20 * 24000L);
		UUID player = new UUID(1, 2);
		s.addReputation(player, 17);
		s.deposit(555);
		s.data(COUNTER).n.set(42);
		SimPlot first = s.plots().iterator().next();
		s.setPlotOwner(first.id(), player);
		SimSettlement back = roundTrip(s);

		assertEquals(s.id(), back.id());
		assertEquals(s.name(), back.name());
		assertEquals(s.culture(), back.culture());
		assertEquals(s.center(), back.center());
		assertEquals(s.foundedDay(), back.foundedDay());
		assertEquals(s.tier(), back.tier());
		assertEquals(s.simTick(), back.simTick());
		assertEquals(s.population(), back.population());
		assertEquals(s.treasury(), back.treasury());
		assertEquals(17, back.reputation(player));
		assertEquals(42, back.data(COUNTER).n.get());
		for (Goods g : Goods.VALUES) {
			assertEquals(s.stockpile().get(g), back.stockpile().get(g), g.getSerializedName());
		}
		assertEquals(s.plots().size(), back.plots().size());
		for (SimPlot p : s.plots()) {
			SimPlot q = back.plot(p.id());
			assertNotNull(q);
			assertEquals(p.spec(), q.spec());
			assertEquals(p.state(), q.state());
			assertEquals(p.cursor(), q.cursor());
			assertEquals(p.beds(), q.beds());
			assertEquals(p.owner(), q.owner());
		}
		s.residents().forEach(r -> {
			var q = back.resident(r.id);
			assertNotNull(q);
			assertEquals(r.fullName(), q.fullName());
			assertEquals(r.job, q.job);
			assertEquals(r.home, q.home);
			assertEquals(r.bornDay, q.bornDay);
		});
		// a copy is just as faithful, and independent
		SimSettlement copy = s.copy();
		copy.deposit(1);
		assertEquals(s.treasury() + 1, copy.treasury());
	}

	@Test
	void unknownModuleDataIsKept() {
		SimSettlement s = TestSupport.founded("flat", 3);
		CompoundTag tag = (CompoundTag) SimSettlement.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
		CompoundTag foreign = new CompoundTag();
		foreign.putString("note", "from a module that is not installed");
		CompoundTag data = tag.getCompoundOrEmpty("data");
		data.put("othermod:state", foreign);
		tag.put("data", data);
		SimSettlement back = SimSettlement.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		CompoundTag again = (CompoundTag) SimSettlement.CODEC.encodeStart(NbtOps.INSTANCE, back).getOrThrow();
		assertEquals("from a module that is not installed",
			again.getCompoundOrEmpty("data").getCompoundOrEmpty("othermod:state").getStringOr("note", ""));
	}

	@Test
	void ledgerAndIndexRoundTrip() {
		AppliedLedger l = AppliedLedger.EMPTY.with(5, 1, Cursor.pack(3, 0)).with(5, 2, Cursor.pack(1, 17)).with(9, 1, Cursor.pack(7, 0));
		AppliedLedger back = AppliedLedger.CODEC.parse(NbtOps.INSTANCE, AppliedLedger.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow()).getOrThrow();
		assertEquals(l, back);
		assertEquals(Cursor.pack(1, 17), back.get(5, 2));

		SimIndex index = new SimIndex();
		index.allocateId();
		index.put(new SimIndex.Entry(1, "Ashbrook", new BlockPos(1, 2, 3), new BoundingBox(0, 0, 0, 10, 10, 10)));
		index.markAdopted(77);
		SimIndex i2 = SimIndex.CODEC.parse(NbtOps.INSTANCE, SimIndex.CODEC.encodeStart(NbtOps.INSTANCE, index).getOrThrow()).getOrThrow();
		assertEquals(2, i2.allocateId());
		assertTrue(i2.isAdopted(77));
		assertEquals("Ashbrook", i2.entries().getFirst().name());
	}

	@Test
	void plotStatesSurvive() {
		SimSettlement s = TestSupport.founded("flat", 5);
		SettlementClock.advance(s, 3 * 24000L);
		SimPlot p = s.plots().iterator().next();
		s.abandonPlot(p);
		assertEquals(PlotState.ABANDONED, roundTrip(s).plot(p.id()).state());
	}
}
