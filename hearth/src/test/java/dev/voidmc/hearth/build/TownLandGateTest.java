package dev.voidmc.hearth.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.Gate;
import dev.voidmc.hearth.build.check.PlayChecks;
import dev.voidmc.hearth.plan.HearthPlanner;
import dev.voidmc.hearth.plan.PlanConfig;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The gate's play rules on the specs a planner makes (ART_REVIEW_2.md item 30): a town grown 300 days on hills, every
 * built plot's blueprint on the town's own land. The invariant tests set a plot's floor at the street on synthetic
 * land; the planner floors plots on real slopes, which is where review 2 found festival-ground trees, tilt-yard fences
 * and walkway decks hanging three or four blocks up. Broken rules go through the {@link Gate} like the invariant tests'
 * (reported now; a failure once the switch is hard, unless the archetype is listed in hearth/gate/known/).
 */
class TownLandGateTest {
	static {
		BuildTestSupport.init();
	}

	@ParameterizedTest(name = "{0} on hills, seed {1}")
	@CsvSource({"ALPINE, 1", "MEDITERRANEAN, 1", "MESA, 1"})
	void townPlotsKeepThePlayRules(Culture culture, long seed) {
		// the planner reads archetypes from the services: lend it the layered catalogue and put back what was there
		BlueprintService before = HearthServices.hasBlueprints() ? HearthServices.blueprints() : null;
		BlueprintServiceImpl real = BuildTestSupport.SERVICE;
		HearthServices.setBlueprints(new Layered(real, new CatalogueDouble()));
		List<String> problems = new ArrayList<>();
		int checked = 0;
		try {
			float temperature = culture == Culture.MESA || culture == Culture.DESERT ? 2.0F : culture == Culture.ALPINE ? 0.3F : 0.8F;
			TerrainProbe terrain = new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, seed, temperature);
			MiniSim sim = new MiniSim(culture, terrain, seed, new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS));
			sim.run(300);
			for (PlotView p : sim.settlement.plots()) {
				PlotSpec spec = p.spec();
				if (p.state() == PlotState.PLANNED || real.archetype(spec.archetype()) == null) {
					continue;
				}
				BlueprintImpl bp = real.compiled(spec, terrain);
				List<String> out = new ArrayList<>();
				Gate.apply(spec, PlayChecks.evaluate(bp, terrain), out);
				for (String s : out) {
					problems.add("plot #" + p.id() + " " + spec.archetype().getPath() + " at " + spec.origin().toShortString() + ": " + s);
				}
				checked++;
			}
		} finally {
			HearthServices.setBlueprints(before);
		}
		assertTrue(checked > 50, "the town built only " + checked + " real buildings");
		assertTrue(problems.isEmpty(), problems.size() + " of " + checked + " plots break the gate:\n  " + String.join("\n  ", problems));
	}

	/** The real archetypes over the planner's catalogue double (as townPreview plans towns). */
	private static final class Layered implements BlueprintService {
		private final BlueprintService real;
		private final BlueprintService fill;
		private final Map<Identifier, ArchetypeInfo> infos = new LinkedHashMap<>();

		Layered(BlueprintService real, BlueprintService fill) {
			this.real = real;
			this.fill = fill;
			for (ArchetypeInfo i : fill.archetypes()) {
				this.infos.put(i.id(), i);
			}
			for (ArchetypeInfo i : real.archetypes()) {
				this.infos.put(i.id(), i);
			}
		}

		@Override
		public Blueprint blueprint(PlotSpec spec, TerrainProbe terrain) {
			return (this.real.archetype(spec.archetype()) != null ? this.real : this.fill).blueprint(spec, terrain);
		}

		@Override
		public @Nullable ArchetypeInfo archetype(Identifier id) {
			return this.infos.get(id);
		}

		@Override
		public Collection<ArchetypeInfo> archetypes() {
			return this.infos.values();
		}

		@Override
		public Identifier style(Culture culture) {
			return this.real.style(culture);
		}
	}
}
