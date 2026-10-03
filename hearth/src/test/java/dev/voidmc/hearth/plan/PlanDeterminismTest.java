package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * The planner is a pure function of the settlement's state and the random: the same seed grows the same town, plot for
 * plot, on any thread, and its state survives the codec unchanged (so a reloaded world plans on exactly as before).
 */
class PlanDeterminismTest {
	@Test
	void sameSeedSameTown() {
		for (LayoutType layout : new LayoutType[]{LayoutType.CROSSROADS, LayoutType.HILLSIDE, LayoutType.STILT}) {
			List<PlotSpec> a = specs(grow(layout, 21));
			List<PlotSpec> b = specs(grow(layout, 21));
			assertEquals(a, b, layout + ": the same seed must plan the same plots");
		}
	}

	@Test
	void differentSeedsDiffer() {
		assertNotEquals(specs(grow(LayoutType.CROSSROADS, 21)), specs(grow(LayoutType.CROSSROADS, 22)));
	}

	@Test
	void sameOnAnotherThread() throws Exception {
		List<PlotSpec> here = specs(grow(LayoutType.WALLED, 31));
		List<List<PlotSpec>> there = new ArrayList<>();
		Thread t = new Thread(() -> there.add(specs(grow(LayoutType.WALLED, 31))));
		t.start();
		t.join();
		assertEquals(here, there.get(0));
	}

	@Test
	void stateSurvivesTheCodec() {
		MiniSim sim = grow(LayoutType.CROSSROADS, 41);
		PlanState state = sim.settlement.data(PlanModule.STATE);
		Tag once = PlanState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
		PlanState back = PlanState.CODEC.parse(NbtOps.INSTANCE, once).getOrThrow();
		Tag twice = PlanState.CODEC.encodeStart(NbtOps.INSTANCE, back).getOrThrow();
		assertEquals(once, twice, "NBT round trip");
		// and through JSON, as a config-style dump would
		var json = PlanState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
		PlanState fromJson = PlanState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(once, PlanState.CODEC.encodeStart(NbtOps.INSTANCE, fromJson).getOrThrow(), "JSON round trip");
		assertEquals(state.graph.edges().size(), back.graph.edges().size());
		assertEquals(state.layout, back.layout);
	}

	private static MiniSim grow(LayoutType layout, long seed) {
		LayoutChooser.FORCED.set(layout);
		try {
			return Plans.grow(layout == LayoutType.STILT ? Culture.BAYOU : Culture.TEMPERATE,
				layout == LayoutType.STILT ? "lake" : layout == LayoutType.HILLSIDE ? "slope" : "hills", seed, 90);
		} finally {
			LayoutChooser.FORCED.remove();
		}
	}

	private static List<PlotSpec> specs(MiniSim sim) {
		List<PlotSpec> out = new ArrayList<>();
		for (PlotView p : PlanChecks.sorted(sim.settlement)) {
			out.add(p.spec());
		}
		return out;
	}
}
