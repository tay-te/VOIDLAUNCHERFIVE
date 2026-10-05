package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.apply.StepWriter;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

/**
 * Buildings changed in place (docs/buildings/age.md): a growing household's additions and a left house's decay redraw
 * the same building on the same ground, which the sim builds or weathers over what stands instead of rebuilding.
 */
class InPlaceTest {
	@org.junit.jupiter.api.BeforeAll
	static void boot() {
		TestSupport.boot();
	}

	private static PlotSpec house(CompoundTag params, int tier, long seed) {
		return new PlotSpec(Hearth.id("house/cottage"), Hearth.id("temperate"), seed, new BlockPos(4, 64, 4), 11, 13, Direction.SOUTH, 12, tier, params);
	}

	private static CompoundTag params(String key, double v) {
		CompoundTag t = new CompoundTag();
		t.putString("zone", "core");
		if (key != null) {
			t.putDouble(key, v);
		}
		return t;
	}

	@Test
	void onlyTimesWorkChangesABuildingInPlace() {
		CompoundTag base = params(null, 0);
		base.putInt(SimSettlement.ADDITIONS, 0);
		CompoundTag grown = base.copy();
		grown.putInt(SimSettlement.ADDITIONS, 1);
		grown.putInt("replaces", 7);
		CompoundTag left = base.copy();
		left.putDouble(SimSettlement.DECAY, 0.25);
		CompoundTag other = base.copy();
		other.putString("zone", "edge");
		assertTrue(SimSettlement.inPlace(house(base, 2, 1), house(grown, 2, 1)), "an addition");
		assertTrue(SimSettlement.inPlace(house(base, 2, 1), house(left, 2, 1)), "decay");
		assertFalse(SimSettlement.inPlace(house(base, 2, 1), house(grown, 3, 1)), "a finer finish is a rebuild");
		assertFalse(SimSettlement.inPlace(house(base, 2, 1), house(grown, 2, 2)), "another seed is another house");
		assertFalse(SimSettlement.inPlace(house(base, 2, 1), house(other, 2, 1)), "another param is another house");
		assertFalse(SimSettlement.inPlace(null, house(base, 2, 1)));
	}

	@Test
	void earlierSelvesAreEveryCountAndEveryStepOfDecay() {
		CompoundTag p = params(SimSettlement.DECAY, 0.75);
		p.putInt(SimSettlement.ADDITIONS, 2);
		List<PlotSpec> earlier = SimSettlement.earlierSelves(house(p, 2, 1));
		// additions 0 and 1 (lived in), then the whole house with 2, then decay 0.25 and 0.5
		assertEquals(5, earlier.size(), earlier.toString());
		assertEquals(0, earlier.get(0).params().getIntOr(SimSettlement.ADDITIONS, -1));
		assertEquals(0, SimSettlement.decay(earlier.get(2)));
		assertEquals(0.5, SimSettlement.decay(earlier.get(4)));
		for (PlotSpec e : earlier) {
			assertTrue(SimSettlement.inPlace(e, house(p, 2, 1)), e.toString());
		}
	}

	@Test
	void whatAnEarlierSelfPutThereIsOurs() {
		StepWriter.Owned owned = StepWriter.lazyOwned(sink -> {
			sink.accept(1, 2, 3, Blocks.OAK_STAIRS.defaultBlockState(), null);
			sink.accept(1, 2, 3, Blocks.STONE_BRICKS.defaultBlockState(), null);
		});
		long k = BlockPos.asLong(1, 2, 3);
		assertSame(Blocks.STONE_BRICKS.defaultBlockState(), owned.at(k));
		assertTrue(owned.owns(k, Blocks.OAK_STAIRS.defaultBlockState()), "the roof that stood there before the storey");
		assertTrue(owned.owns(k, Blocks.STONE_BRICKS.defaultBlockState()));
		assertFalse(owned.owns(k, Blocks.GOLD_BLOCK.defaultBlockState()), "a player's block is not");
	}

	@Test
	void housesLeftInADeclineWeatherAStepAtATime() {
		SimSettlement s = TestSupport.founded("flat", 52);
		for (int d = 1; d <= 120; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		SimPlot house = null;
		for (SimPlot p : s.plots.values()) {
			if (p.state == PlotState.BUILT && p.beds > 0 && p.owner == null) {
				house = p;
				break;
			}
		}
		assertTrue(house != null, "a house was built");
		house.state = PlotState.ABANDONED;
		s.land().desert(house.id());
		long day = Math.floorDiv(s.clock.simTick, SimRules.DAY);
		var cfg = s.ctx().rules().resources.prosperity();
		Prosperity.weather(s, cfg, s.land(), day);
		int id = s.land().deserted().iterator().next();
		SimPlot left = s.plot(id);
		assertNotEquals(house.id(), id, "the weathered house is a new record");
		assertEquals(PlotState.ABANDONED, left.state);
		assertEquals(0.25, SimSettlement.decay(left.spec()));
		assertTrue(SimSettlement.inPlace(left.replacedSpec, left.spec()), "weathered in place");
		assertEquals(house.id(), left.replaces);
		// a quarter more every decay_step_days, to a ruin
		Prosperity.weather(s, cfg, s.land(), day + 1);
		assertEquals(0.25, SimSettlement.decay(s.plot(s.land().deserted().iterator().next()).spec()), "not before its time");
		for (int k = 1; k <= 4; k++) {
			Prosperity.weather(s, cfg, s.land(), day + (long) k * cfg.decayStepDays());
		}
		SimPlot ruin = s.plot(s.land().deserted().iterator().next());
		assertEquals(1.0, SimSettlement.decay(ruin.spec()));
		assertEquals(s.facts(ruin).stageCount(), ruin.stage, "standing as it is, not to be built");
	}
}
