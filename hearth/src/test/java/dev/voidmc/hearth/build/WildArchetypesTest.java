package dev.voidmc.hearth.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The wild's buildings (docs/LIVING_WORLD_2.md §7, lane B3) hold the blueprint invariants in every culture on every test
 * terrain (as BlueprintInvariantsTest checks every archetype; this one runs fast on its own), and are what they claim.
 */
class WildArchetypesTest {
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static {
		BuildTestSupport.init();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"civic/hospice", "wild/yurt"})
	void invariantsHoldInEveryCultureAndTerrain(String id) {
		ArchetypeInfo info = BuildTestSupport.SERVICE.archetype(Hearth.id(id));
		List<String> problems = new ArrayList<>();
		for (String terrain : TERRAINS) {
			for (Culture c : Culture.values()) {
				long seed = 7919L + c.ordinal() * 104729L + terrain.hashCode();
				Direction facing = FACINGS[(c.ordinal() + terrain.length()) % 4];
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(c), seed, facing, probe);
				BlueprintImpl bp = BuildTestSupport.SERVICE.compiled(spec, probe);
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(terrain + " " + c.getSerializedName() + " " + facing.getSerializedName() + ": " + p);
				}
			}
		}
		assertTrue(problems.isEmpty(), id + ": " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"civic/hospice", "wild/yurt"})
	void neverChosenByATown(String id) {
		ArchetypeInfo info = BuildTestSupport.SERVICE.archetype(Hearth.id(id));
		assertEquals(0.0, info.weight(), "a weight of 0 keeps it out of towns' draws");
		assertEquals(dev.voidmc.hearth.api.SettlementTier.CITY, info.minTier());
		assertEquals(1, info.cultures().size());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"temperate", "alpine", "northern", "desert"})
	void theHospiceHasItsBrothersBedsAndBell(String culture) {
		ArchetypeInfo info = BuildTestSupport.SERVICE.archetype(Hearth.id("civic/hospice"));
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(Culture.valueOf(culture.toUpperCase())), 99L, Direction.SOUTH, probe);
		BlueprintImpl bp = BuildTestSupport.SERVICE.compiled(spec, probe);
		assertTrue(bp.beds() >= 3 && bp.beds() <= 7, "a few brothers' beds: " + bp.beds());
		assertTrue(bp.pois().stream().anyMatch(p -> p.type() == PoiType.JOB_SITE && p.job() == JobType.MONK), "a monk's place");
		assertTrue(bp.pois().stream().anyMatch(p -> p.type() == PoiType.JOB_SITE && p.job() == JobType.HEALER), "the healer's bench");
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"steppe", "desert", "temperate"})
	void theYurtIsFelt(String culture) {
		ArchetypeInfo info = BuildTestSupport.SERVICE.archetype(Hearth.id("wild/yurt"));
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(Culture.valueOf(culture.toUpperCase())), 5L, Direction.SOUTH, probe);
		BlueprintImpl bp = BuildTestSupport.SERVICE.compiled(spec, probe);
		int wool = 0;
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				var st = bp.state(s, i);
				if (st.is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)) || st.is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY))) {
					wool++;
				}
			}
		}
		assertTrue(wool >= 20, "felt walls: " + wool + " wool blocks");
		assertTrue(bp.beds() >= 1, "beds");
	}
}
