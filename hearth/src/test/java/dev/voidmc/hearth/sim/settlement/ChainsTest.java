package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.chains.Recipe;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.List;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

/** Production chains, tools, eating, spoilage and the stores (docs/SIM.md §16). */
class ChainsTest {
	private static final int N = Goods.VALUES.length;

	private static Recipe saw(double share, double target) {
		double[] in = new double[N];
		double[] out = new double[N];
		in[Goods.LOGS.ordinal()] = 40;
		out[Goods.WOOD.ordinal()] = 32;
		return new Recipe(Hearth.id("test_saw"), JobType.FORESTER, null, in, out, 1, -1, share, target, 0);
	}

	private static int[] caps(int c) {
		int[] a = new int[N];
		java.util.Arrays.fill(a, c);
		return a;
	}

	@Test
	void aRecipeTurnsRawGoodsIntoProcessedOnes() {
		int[] stock = new int[N];
		stock[Goods.LOGS.ordinal()] = 100;
		double[] frac = new double[N];
		double left = Production.recipes(List.of(saw(1, 0.9)), 2, 2, frac, stock, caps(1000));
		assertEquals(0, left, 1e-9);
		assertEquals(64, frac[Goods.WOOD.ordinal()], 1e-9);
		assertEquals(-80, frac[Goods.LOGS.ordinal()], 1e-9);
	}

	@Test
	void shortInputsLeaveTheWorkersFree() {
		int[] stock = new int[N];
		stock[Goods.LOGS.ordinal()] = 20;
		double[] frac = new double[N];
		double left = Production.recipes(List.of(saw(1, 0.9)), 2, 2, frac, stock, caps(1000));
		// half a batch of logs: half a day sawn, a day and a half left for felling
		assertEquals(1.5, left, 1e-9);
		assertEquals(16, frac[Goods.WOOD.ordinal()], 1e-9);
	}

	@Test
	void fullStoresAndSharesLimitARecipe() {
		int[] stock = new int[N];
		stock[Goods.LOGS.ordinal()] = 1000;
		stock[Goods.WOOD.ordinal()] = 950;
		double[] frac = new double[N];
		assertEquals(2, Production.recipes(List.of(saw(1, 0.9)), 2, 2, frac, stock, caps(1000)), 1e-9, "the stores want no more timber");
		stock[Goods.WOOD.ordinal()] = 0;
		assertEquals(1.5, Production.recipes(List.of(saw(0.25, 0.9)), 2, 2, frac, stock, caps(1000)), 1e-9, "hand work takes a quarter of the day");
	}

	@Test
	void withoutToolsEveryoneManagesLess() {
		SimSettlement a = TestSupport.founded("flat", 30);
		SimSettlement b = TestSupport.founded("flat", 30);
		b.stockpile().take(Goods.TOOLS, 10_000);
		int grain = a.stockpile().get(Goods.GRAIN);
		for (SimSettlement s : new SimSettlement[] {a, b}) {
			// four farmers' day on the town's fields
			SettlementClock.Workforce wf = new SettlementClock.Workforce();
			int k = Production.crewOf(wf.crews, s, JobType.FARMER, Resident.NO_PLOT, s.ctx().data(), s.ctx().rules());
			wf.crews.counts[k] = 4;
			wf.workers[JobType.FARMER.ordinal()] = 4;
			Production.produce(s, wf, 1.0, s.ctx().rules(), s.ctx());
		}
		int grainA = a.stockpile().get(Goods.GRAIN) - grain;
		int grainB = b.stockpile().get(Goods.GRAIN) - grain;
		assertTrue(grainA > 0 && grainB > 0 && grainA > grainB * 1.4, "with tools " + grainA + ", without " + grainB);
		int tools = a.stockpile().get(Goods.TOOLS);
		for (int d = 0; d < 30; d++) {
			SettlementClock.Workforce wf = new SettlementClock.Workforce();
			int k = Production.crewOf(wf.crews, a, JobType.FARMER, Resident.NO_PLOT, a.ctx().data(), a.ctx().rules());
			wf.crews.counts[k] = 4;
			Production.produce(a, wf, 1.0, a.ctx().rules(), a.ctx());
		}
		assertTrue(a.stockpile().get(Goods.TOOLS) < tools, "tools wear out: " + tools + " -> " + a.stockpile().get(Goods.TOOLS));
	}

	@Test
	void withoutBreadFolkEatWhatTheLandGives() {
		SimSettlement s = TestSupport.founded("flat", 31);
		s.stockpile().take(Goods.FOOD, 100_000);
		s.stockpile().take(Goods.GRAIN, 100_000);
		s.stockpile().add(Goods.PRODUCE, 500);
		int before = s.stockpile().get(Goods.PRODUCE);
		SettlementClock.advance(s, SimRules.DAY / 2);
		assertTrue(s.stockpile().get(Goods.PRODUCE) < before + 20, "produce eaten: " + before + " -> " + s.stockpile().get(Goods.PRODUCE));
		SettlementClock.advance(s, 2 * SimRules.DAY);
		assertEquals(0, s.clockState().hungerDays(), "nobody went hungry");
	}

	@Test
	void foodSpoilsOutsideAGranary() {
		SimSettlement s = TestSupport.founded("flat", 32);
		s.stockpile().take(Goods.MEAT, 100_000);
		s.stockpile().add(Goods.MEAT, 1000);
		Spoilage.daily(s, s.ctx().rules(), s.ctx().data());
		int meat = s.stockpile().get(Goods.MEAT);
		assertTrue(meat <= 945 && meat >= 930, "a day's spoilage of meat: " + meat);
		// salt cures it
		s.stockpile().add(Goods.SALT, 200);
		Spoilage.daily(s, s.ctx().rules(), s.ctx().data());
		int lost = meat - s.stockpile().get(Goods.MEAT);
		assertTrue(lost < (int) (meat * 0.06) - 5, "salted meat keeps: lost " + lost);
		assertTrue(s.stockpile().get(Goods.SALT) < 200, "and the salt is used");
	}

	@Test
	void oldSavesWithTenGoodsLoad() {
		IntArrayTag ten = new IntArrayTag(new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
		SimStockpile p = SimStockpile.CODEC.parse(NbtOps.INSTANCE, ten).getOrThrow();
		assertEquals(10, p.get(Goods.LUXURY));
		assertEquals(1, p.get(Goods.FOOD));
		assertEquals(0, p.get(Goods.LOGS));
		assertEquals(0, p.get(Goods.SPECIALTY));
	}

	@Test
	void buildingsStoreTheirOwnGoods() {
		SimRules r = SimRules.defaults();
		int[] granary = r.storageFor(Hearth.id("storage/granary"));
		assertTrue(granary[Goods.GRAIN.ordinal()] > 0 && granary[Goods.FOOD.ordinal()] > 0);
		assertEquals(0, granary[Goods.STONE.ordinal()], "a granary holds no stone");
		int[] sawmill = r.storageFor(Hearth.id("work/sawmill"));
		assertTrue(sawmill[Goods.LOGS.ordinal()] > 0 && sawmill[Goods.WOOD.ordinal()] > 0);
		SimSettlement s = TestSupport.founded("flat", 33);
		for (int d = 1; d <= 40; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		s.recomputeDerived();
		// lofts and cellars of every building, plus the storehouse's room for everything
		assertTrue(s.stockpile().capacity(Goods.STONE) > r.storage.base(), "capacity " + s.stockpile().capacity(Goods.STONE));
	}
}
