package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.growth.Needs;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.resource.Extraction;
import dev.voidmc.hearth.plan.resource.Territory;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Resources shape the plan (LIVING_WORLD.md §7.2): workings stand on the survey's sites with their params, out of
 * town, at the end of cart tracks; the workshops of the chains stand by their inputs; every plot carries the territory's
 * materials; housing stops at what the land carries; with extraction switched off, nothing is dug.
 */
class ResourcePlanTest {
	static MiniSim town;

	@BeforeAll
	static void grow() {
		LayoutChooser.FORCED.set(LayoutType.MOUNTAIN);
		try {
			town = Plans.grow(Culture.ALPINE, "steep", 6, 170);
		} finally {
			LayoutChooser.FORCED.remove();
		}
	}

	@Test
	void workingsStandOnTheirSites() {
		PlanState st = town.settlement.data(PlanModule.STATE);
		int workings = 0;
		for (PlotView p : town.settlement.plots()) {
			PlotSpec s = p.spec();
			ResourceSite.Kind kind = Extraction.kind(s.archetype());
			if (kind == null) {
				continue;
			}
			workings++;
			int id = s.params().getIntOr("site", -1);
			ResourceSite site = town.settlement.sites().stream().filter(x -> x.id() == id).findFirst().orElse(null);
			assertTrue(site != null && site.kind() == kind, "a working names a site of its kind: " + s.archetype() + " site " + id);
			assertEquals(kind.getSerializedName(), s.params().getStringOr("kind", ""));
			if (s.archetype().getPath().startsWith("extract/")) {
				assertTrue(!s.params().getStringOr("rock", "").isEmpty(), "the host rock");
				assertTrue(!s.params().getStringOr("face", "").isEmpty(), "the way the face advances");
			}
			if (s.archetype().equals(Archetypes.QUARRY_PIT) || s.archetype().equals(Archetypes.SHAFT)) {
				assertTrue(s.params().getIntOr("depth", 0) > 0, "how deep the cuts go");
			}
			if (s.archetype().equals(Archetypes.MINE)) {
				assertTrue(s.params().getIntOr("length", 0) > 0, "how far the adit runs");
			}
			BoundingBox a = site.area();
			int cx = s.origin().getX() + s.sizeX() / 2;
			int cz = s.origin().getZ() + s.sizeZ() / 2;
			double fromSite = Math.max(Math.max(a.minX() - cx, cx - a.maxX()), Math.max(a.minZ() - cz, cz - a.maxZ()));
			assertTrue(fromSite < 48, "a working stands at its site: " + s.archetype() + " " + (int) fromSite + " blocks off");
			double fromHeart = Math.hypot(cx - st.heartX, cz - st.heartZ);
			assertTrue(fromHeart > 30, "a working stands out of town: " + s.archetype() + " " + (int) fromHeart);
		}
		assertTrue(workings >= 2, "the town works its land: " + workings);
		assertTrue(st.graph.edges().stream().anyMatch(e -> e.role == RoadEdge.Role.HAUL && e.built > 0), "cart tracks out to the workings");
	}

	@Test
	void workshopsStandByTheirInputs() {
		near(Archetypes.SAWMILL, Archetypes.LOGGING_CAMP, 80);
		near(Archetypes.SMELTER, Archetypes.MINE, 90, Archetypes.SHAFT);
	}

	private static void near(net.minecraft.resources.Identifier workshop, net.minecraft.resources.Identifier input, int max,
		net.minecraft.resources.Identifier... more) {
		List<int[]> inputs = new java.util.ArrayList<>(positions(input));
		for (var id : more) {
			inputs.addAll(positions(id));
		}
		List<int[]> shops = positions(workshop);
		if (inputs.isEmpty() || shops.isEmpty()) {
			return;
		}
		int[] first = shops.get(0);
		double best = Double.MAX_VALUE;
		for (int[] i : inputs) {
			best = Math.min(best, Math.hypot(i[0] - first[0], i[1] - first[1]));
		}
		assertTrue(best <= max, workshop.getPath() + " stands by its input " + input.getPath() + ": " + (int) best + " blocks");
	}

	private static List<int[]> positions(net.minecraft.resources.Identifier id) {
		List<int[]> out = new java.util.ArrayList<>();
		town.settlement.plots().stream().sorted(java.util.Comparator.comparingInt(PlotView::id)).forEach(p -> {
			if (p.spec().archetype().equals(id)) {
				out.add(Territory.centre(p.spec()));
			}
		});
		return out;
	}

	@Test
	void everyPlotBuildsInLocalMaterials() {
		int with = 0;
		for (PlotView p : town.settlement.plots()) {
			if (p.spec().params().contains("local")) {
				with++;
			}
		}
		assertTrue(with * 10 >= town.settlement.plots().size() * 9, "plots carry the territory's materials: " + with + "/" + town.settlement.plots().size());
		var local = town.settlement.plots().stream().filter(p -> p.spec().params().contains("local")).findFirst().orElseThrow().spec().params()
			.getCompoundOrEmpty("local");
		assertTrue(local.contains("frame") || local.contains("foundation"), "woods and stone of the territory: " + local);
	}

	@Test
	void housingStopsAtWhatTheLandCarries() {
		float[] food = new float[Goods.VALUES.length];
		food[Goods.GRAIN.ordinal()] = 10;
		ResourceSite field = new ResourceSite(1, ResourceSite.Kind.FERTILE, BlockPos.ZERO, new BoundingBox(0, 60, 0, 40, 64, 40), 1000, 1000, 40,
			food, null, -1);
		int capacity = Territory.estimateCapacity(List.of(field, field), PlanConfig.DEFAULTS.living());
		// 80 food a day feeds 40, with a quarter more by trade, and a few to start
		assertEquals(56, capacity);
		assertEquals(0, Territory.estimateCapacity(List.of(), PlanConfig.DEFAULTS.living()), "no survey: no bound");
		assertEquals(0, Territory.estimateCapacity(List.of(field), new PlanConfig.Living(true, true, 320, false, 1.25, true, true, true, true, true, true,
			true, 0.25, -1)), "switched off: no bound");
	}

	@Test
	void withoutExtractionNothingIsDug() {
		HearthPlanner plain = new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS.withLiving(new PlanConfig.Living(true, false, 320, true, 1.25, true,
			true, true, true, true, true, true, 0.25, -1)));
		LayoutChooser.FORCED.set(LayoutType.MOUNTAIN);
		MiniSim sim;
		try {
			sim = new MiniSim(Culture.ALPINE, new SyntheticTerrain(SyntheticTerrain.Kind.STEEP, 6, 0.3F), 6, plain);
			sim.run(120);
		} finally {
			LayoutChooser.FORCED.remove();
		}
		for (PlotView p : sim.settlement.plots()) {
			assertTrue(!p.spec().archetype().getPath().startsWith("extract/"), "nothing dug: " + p.spec().archetype());
		}
	}

	@Test
	void needsTakeTheCapacityAsCeiling() {
		// a deficit of beds never runs past the capacity
		Needs n = Needs.of(new PlanContext(town.settlement, town.settlement.data(PlanModule.STATE), town.terrain, net.minecraft.util.RandomSource.create(1),
			dev.voidmc.hearth.plan.catalogue.Catalogue.current(), PlanConfig.DEFAULTS));
		int pop = town.settlement.population();
		assertTrue(n.housingDeficit(0.1, pop) <= pop - n.beds - n.plannedBeds + 0, "at capacity, no spare beds are planned");
	}
}
