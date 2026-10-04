package dev.voidmc.hearth.sim.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** What caravans carry, and what it fetches (the road's arithmetic; the live world sends them). */
class CaravansTest {
	@Test
	void surplusGoesWhereItIsShort() {
		SimRules r = SimRules.defaults();
		SimSettlement timber = TestSupport.founded("flat", 61);
		SimSettlement stone = TestSupport.founded("flat", 62);
		timber.stockpile().add(Goods.WOOD, 1800);
		stone.stockpile().take(Goods.WOOD, 100_000);
		int[] surplus = new int[Goods.VALUES.length];
		for (int g = 0; g < surplus.length; g++) {
			surplus[g] = Caravans.surplus(timber, g, r);
		}
		assertTrue(surplus[Goods.WOOD.ordinal()] > 0, "timber to spare");
		assertTrue(Caravans.shortage(stone, Goods.WOOD.ordinal(), r) > 0, "and a town short of it");
		assertTrue(Caravans.price(stone, Goods.WOOD.ordinal(), r) > Caravans.price(timber, Goods.WOOD.ordinal(), r), "dearer where it is short");
		int[] cargo = new int[surplus.length];
		double value = Caravans.value(timber, stone, surplus, 640, r, cargo);
		assertTrue(value > 0 && cargo[Goods.WOOD.ordinal()] > 0, "the caravan carries timber, worth " + value);
		int units = 0;
		for (int c : cargo) {
			units += c;
		}
		assertTrue(units <= 640, "no more than a caravan carries");
		// the other way there is nothing worth the road
		int[] none = new int[surplus.length];
		for (int g = 0; g < none.length; g++) {
			none[g] = Caravans.surplus(stone, g, r);
		}
		assertEquals(0, none[Goods.WOOD.ordinal()]);
	}

	@Test
	void roughRoadsAreSlower() {
		assertEquals(0, Caravans.roughness(SyntheticTerrain.named("flat", 1), new BlockPos(0, 64, 0), new BlockPos(2000, 64, 0)), 1e-9);
		assertTrue(Caravans.roughness(SyntheticTerrain.named("river", 1), new BlockPos(-400, 64, 0), new BlockPos(400, 64, 0)) > 0,
			"a river to ford");
		assertTrue(Caravans.roughness(SyntheticTerrain.named("steep", 1), new BlockPos(0, 64, 0), new BlockPos(400, 64, 0)) > 0.5, "a climb");
	}

	@Test
	void aCaravanIsWhereItsRoadSays() {
		Caravans.Caravan c = new Caravans.Caravan(1, 1, 2, new int[Goods.VALUES.length], 10, 1000, 3000, false, new BlockPos(0, 64, 0),
			new BlockPos(1000, 64, 0));
		assertEquals(new BlockPos(500, 64, 0), Caravans.positionAt(c, 2000));
		assertEquals(new BlockPos(1000, 64, 0), Caravans.positionAt(c, 9000));
	}
}
