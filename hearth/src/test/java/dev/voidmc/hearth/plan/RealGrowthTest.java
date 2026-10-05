package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.sim.chains.SimData;
import dev.voidmc.hearth.sim.land.Land;
import dev.voidmc.hearth.sim.land.Survey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Towns grown on the build module's real archetypes (not the planner's stand-ins), as the game plans them. A tracing
 * tool with {@code HEARTH_REAL_DEBUG=1} (and {@code _CULTURE}, {@code _TERRAIN}, {@code _SEED}, {@code _DAYS},
 * {@code _CAPACITY}): prints the town every twenty days and why the planner answered nothing.
 */
class RealGrowthTest {
	static BlueprintServiceImpl real;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		real = BuildModule.standalone();
		HearthServices.setBlueprints(real);
	}

	@AfterAll
	static void restore() {
		HearthServices.setBlueprints(new CatalogueDouble());
	}

	/** A town on synthetic land with the sim's own survey of its sites (as the game gives them), growing toward {@code capacity}. */
	static MiniSim grow(Culture culture, String terrain, long seed, int capacity) {
		SyntheticTerrain t = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase()), seed,
			culture == Culture.DESERT || culture == Culture.MESA ? 2.0F : 0.8F);
		BlockPos heart = new BlockPos(0, t.groundY(0, 0), 0);
		return new MiniSim(culture, t, seed, new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS), heart, (h, r) -> survey(t, h, r)).capacity(capacity);
	}

	/** The sim's survey ({@code sim.land.Survey}) of the land round a heart, as the sites a settlement shows the planner. */
	static List<ResourceSite> survey(TerrainProbe t, BlockPos heart, int radius) {
		List<Survey.Found> found = Survey.run(Land.of(t, null), heart, radius, SimData.builtin(), Survey.Params.DEFAULTS, (x, z) -> false);
		List<ResourceSite> out = new ArrayList<>();
		for (Survey.Found f : found) {
			int cap = (int) Math.round(f.capacity());
			out.add(new ResourceSite(out.size() + 1, f.kind(), f.center(), f.area(), cap, cap, (float) f.regen(), f.yields().clone(), f.material(),
				f.kind() == ResourceSite.Kind.SPECIALTY ? f.material() : null, PlotView.NONE, f.ore(), f.fuel()));
		}
		return out;
	}

	/**
	 * A town of sixty with ore, rock and woods in reach opens a mine (or a shaft), a quarry pit and a logging camp from the
	 * program's thresholds, with the real archetypes and the sim's survey (requests/w5-tour.md §3: no settlement of the
	 * live tour worked its land).
	 */
	@Test
	void aTownOfSixtyWorksItsLand() {
		MiniSim sim = grow(Culture.ALPINE, "steep", 3, 220);
		Map<ResourceSite.Kind, Integer> kinds = new TreeMap<>();
		for (ResourceSite s : sim.settlement.sites()) {
			kinds.merge(s.kind(), 1, Integer::sum);
		}
		assertTrue(kinds.containsKey(ResourceSite.Kind.ORE) && kinds.containsKey(ResourceSite.Kind.STONE) && kinds.containsKey(ResourceSite.Kind.WOODS),
			"the land has ore, rock and woods: " + kinds);
		for (int d = 0; d < 300 && sim.settlement.population() < 60; d += 10) {
			sim.run(10);
		}
		sim.run(40);
		assertTrue(sim.settlement.population() >= 60, "the town reaches sixty: " + summary(sim));
		int mines = count(sim, Archetypes.MINE) + count(sim, Archetypes.SHAFT);
		int quarries = count(sim, Archetypes.QUARRY_PIT);
		int camps = count(sim, Archetypes.LOGGING_CAMP);
		assertTrue(mines >= 1 && quarries >= 1 && camps >= 1, "a mine " + mines + ", a quarry pit " + quarries + ", a logging camp " + camps + "; sites "
			+ kinds + "; " + summary(sim) + "\n" + String.join("\n", sim.log()));
	}

	private static int count(MiniSim sim, Identifier id) {
		int n = 0;
		for (PlotView p : sim.settlement.plots()) {
			if (p.spec().archetype().equals(id)) {
				n++;
			}
		}
		return n;
	}

	@Test
	void trace() {
		if (System.getenv("HEARTH_REAL_DEBUG") == null) {
			return;
		}
		Growth.trace = System.getenv("HEARTH_REAL_TRACE") != null;
		PlanStats.everyCall = true;
		PlanStats.reset();
		MiniSim sim = grow(Culture.valueOf(System.getenv().getOrDefault("HEARTH_REAL_CULTURE", "TEMPERATE")),
			System.getenv().getOrDefault("HEARTH_REAL_TERRAIN", "hills"), Long.parseLong(System.getenv().getOrDefault("HEARTH_REAL_SEED", "1")),
			Integer.parseInt(System.getenv().getOrDefault("HEARTH_REAL_CAPACITY", "600")));
		int days = Integer.parseInt(System.getenv().getOrDefault("HEARTH_REAL_DAYS", "300"));
		for (int d = 0; d < days; d += 20) {
			sim.run(20);
			System.out.println(summary(sim));
		}
		for (String s : PlanStats.lines()) {
			System.out.println(s);
		}
		if (System.getenv("HEARTH_REAL_MAP") != null) {
			map(sim, Integer.parseInt(System.getenv("HEARTH_REAL_MAP")));
		}
		if (System.getenv("HEARTH_REAL_LOG") != null) {
			for (String s : sim.log()) {
				System.out.println(s);
			}
		}
		PlanStats.everyCall = false;
		Growth.trace = false;
	}

	/** The occupancy grid round the heart, a character per two blocks: roads #, plaza +, buildings B, farms f, planned roads :. */
	static void map(MiniSim sim, int r) {
		dev.voidmc.hearth.plan.model.PlanState st = sim.settlement.data(PlanModule.STATE);
		dev.voidmc.hearth.plan.grid.Occupancy occ = st.occupancy;
		System.out.println("layout " + st.layout + " heart " + st.heartX + "," + st.heartZ + " roads " + st.graph.townLength() + " haul " + st.graph.haulLength());
		for (var e : st.graph.edges()) {
			System.out.println("edge " + e.id + " " + e.role + " " + e.kind + " built " + e.built + "/" + e.length() + " from " + e.dense().xs()[0] + ","
				+ e.dense().zs()[0] + " to " + e.dense().xs()[e.length() - 1] + "," + e.dense().zs()[e.length() - 1]);
		}
		for (int z = st.heartZ - r; z <= st.heartZ + r; z += 2) {
			StringBuilder sb = new StringBuilder();
			for (int x = st.heartX - r; x <= st.heartX + r; x += 2) {
				byte c = occ.get(x, z);
				sb.append(switch (c) {
					case dev.voidmc.hearth.plan.grid.Cell.FREE -> sim.terrain.groundY(x, z) < sim.terrain.seaLevel() ? '~' : '.';
					case dev.voidmc.hearth.plan.grid.Cell.ROAD -> '#';
					case dev.voidmc.hearth.plan.grid.Cell.PLAZA -> '+';
					case dev.voidmc.hearth.plan.grid.Cell.BUILDING -> 'B';
					case dev.voidmc.hearth.plan.grid.Cell.FARM -> 'f';
					case dev.voidmc.hearth.plan.grid.Cell.PLANNED_ROAD -> ':';
					case dev.voidmc.hearth.plan.grid.Cell.SETBACK -> ',';
					case dev.voidmc.hearth.plan.grid.Cell.RESERVED -> 'r';
					case dev.voidmc.hearth.plan.grid.Cell.WALL -> 'W';
					case dev.voidmc.hearth.plan.grid.Cell.LANDSCAPE -> 'l';
					default -> '?';
				});
			}
			System.out.println(sb);
		}
	}

	static String summary(MiniSim sim) {
		Map<String, Integer> kinds = new TreeMap<>();
		int built = 0;
		for (PlotView p : sim.settlement.plots()) {
			String path = p.spec().archetype().getPath();
			kinds.merge(path.equals("work/logging_camp") || path.equals("work/carpentry") ? path : path.contains("/") ? path.substring(0, path.indexOf('/')) : path, 1, Integer::sum);
			if (p.state() == PlotState.BUILT) {
				built++;
			}
		}
		return "day " + sim.day() + ": " + sim.settlement.population() + " souls, " + sim.settlement.beds() + " beds, " + sim.settlement.plots().size()
			+ " plots (" + built + " built) " + kinds + " last: " + PlanStats.last(sim.settlement.id());
	}
}
