package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The planner proposes only archetypes the build module registers: with half the catalogue missing it falls back to the
 * nearest registered one of the same kind, or skips the need, and still grows a town.
 */
class CatalogueFallbackTest {
	@Test
	void onlyRegisteredArchetypes() {
		List<Identifier> hidden = List.of(Archetypes.TOWNHOUSE, Archetypes.ROWHOUSE, Archetypes.SHOPHOUSE, Archetypes.BAKERY, Archetypes.SMITHY,
			Archetypes.STAIRS, Archetypes.TERRACE, Archetypes.MARKET_HALL, Archetypes.FIELD, Archetypes.WELL, Archetypes.TOWN_HALL);
		CatalogueDouble partial = CatalogueDouble.without(hidden);
		var previous = Plans.PLANNER != null ? HearthServices.blueprints() : null;
		HearthServices.setBlueprints(partial);
		try {
			HearthPlanner planner = new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS);
			MiniSim sim = new MiniSim(Culture.TEMPERATE, new SyntheticTerrain(SyntheticTerrain.Kind.SLOPE, 61), 61, planner);
			sim.run(150);
			for (PlotView p : sim.settlement.plots()) {
				assertFalse(hidden.contains(p.spec().archetype()), "proposed an unregistered " + p.spec().archetype());
				assertTrue(partial.archetype(p.spec().archetype()) != null, "proposed unknown " + p.spec().archetype());
			}
			assertTrue(sim.settlement.population() > 40, "still grew: " + sim.settlement.population());
		} finally {
			HearthServices.setBlueprints(previous);
		}
	}

	@Test
	void resolveFallsBackWithinTheCategory() {
		Catalogue c = new Catalogue(CatalogueDouble.without(List.of(Archetypes.BAKERY)).archetypes(), null);
		var baker = c.resolve(Archetypes.BAKERY, Culture.TEMPERATE, SettlementTier.TOWN);
		assertTrue(baker != null && baker.jobs().contains(dev.voidmc.hearth.api.JobType.BAKER), "a bakery falls back to a mill: " + baker);
		// a culture's own house stays with the culture
		assertTrue(c.resolve(Archetypes.LONGHOUSE, Culture.DESERT, SettlementTier.TOWN) == null
			|| !c.resolve(Archetypes.LONGHOUSE, Culture.DESERT, SettlementTier.TOWN).id().equals(Archetypes.LONGHOUSE));
	}
}
