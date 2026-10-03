package dev.voidmc.hearth.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every registered archetype, in every culture's style, on several terrains, with several seeds and all four facings:
 * the blueprint stays in its plot, nothing floats, beds and job sites can be walked to from a door, the door from the
 * street, the chunk index agrees with the steps, stages come in order, and the same spec gives the same blueprint.
 */
class BlueprintInvariantsTest {
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static {
		BuildTestSupport.init();
	}

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (ArchetypeInfo a : BuildTestSupport.SERVICE.archetypes()) {
			for (String terrain : TERRAINS) {
				out.add(Arguments.of(a, terrain));
			}
		}
		return out.stream();
	}

	@ParameterizedTest(name = "{0} on {1}")
	@MethodSource("cases")
	void invariantsHoldInEveryStyle(ArchetypeInfo info, String terrain) {
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (Culture c : Culture.values()) {
			for (int s = 0; s < 2; s++) {
				long seed = 7919L * (s + 1) + c.ordinal() * 104729L;
				Direction facing = FACINGS[(s + c.ordinal()) % 4];
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(c), seed, facing, probe);
				BlueprintImpl bp = BuildTestSupport.SERVICE.compiled(spec, probe);
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName() + ": " + p);
				}
				n++;
			}
		}
		assertTrue(problems.isEmpty(), info.id() + " on " + terrain + ", " + problems.size() + " of " + n + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void sameSpecSameBlueprint() {
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 7);
		for (ArchetypeInfo info : BuildTestSupport.SERVICE.archetypes()) {
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.KARST, Culture.MEDITERRANEAN, Culture.NORTHERN, Culture.BAYOU}) {
				PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(c), 12345L, Direction.EAST, probe);
				BlueprintImpl a = dev.voidmc.hearth.build.blueprint.Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe));
				BlueprintImpl b = dev.voidmc.hearth.build.blueprint.Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe));
				assertTrue(BlueprintChecks.same(a, b), info.id() + " in " + c + " is not deterministic");
			}
		}
	}

	@Test
	void everyArchetypeBuildsSomethingWithAnEntrance() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 1);
		for (ArchetypeInfo info : BuildTestSupport.SERVICE.archetypes()) {
			for (Culture c : Culture.values()) {
				PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(c), 99L, Direction.SOUTH, probe);
				BlueprintImpl bp = BuildTestSupport.SERVICE.compiled(spec, probe);
				assertTrue(bp.totalSteps() > 50, info.id() + " in " + c + " builds almost nothing");
				assertTrue(!bp.entrances().isEmpty(), info.id() + " in " + c + " has no entrance");
				if (info.category() == dev.voidmc.hearth.api.ArchetypeCategory.HOUSING) {
					assertTrue(bp.beds() >= 1, info.id() + " in " + c + " has no bed");
					assertEquals(bp.beds(), bp.pois().stream().filter(p -> p.type() == dev.voidmc.hearth.api.PoiType.BED).count());
				}
				for (var job : info.jobs()) {
					assertTrue(bp.pois().stream().anyMatch(p -> p.job() == job), info.id() + " in " + c + " has no job site for " + job);
				}
			}
		}
	}
}
