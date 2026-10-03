package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.gametest.plan.EarthProbe;
import dev.voidmc.hearth.gametest.plan.IsoRenderer;
import dev.voidmc.hearth.gametest.plan.PlanRenderer;
import dev.voidmc.hearth.gametest.plan.Scene;
import dev.voidmc.hearth.plan.HearthPlanner;
import dev.voidmc.hearth.plan.PlanConfig;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Plans a settlement offline and grows it with a minimal sim, then renders it: a top-down plan and an isometric
 * overview per requested age. No game needed.
 *
 * <pre>
 *   ./gradlew settlementPreview -Pculture=temperate -Pdays=30,120,400 -Pterrain=hills -Pseed=1
 *   ./gradlew settlementPreview -Pculture=suite                       (a set of cultures × terrains at 3 ages)
 *   ./gradlew settlementPreview -Pculture=mediterranean -Pterrain=earth:1200:-800 -Pseed=8675309
 * </pre>
 *
 * <p>Arguments: culture(s), days, terrain(s), seed(s), output directory; lists are comma-separated. Terrains are the
 * synthetic kinds ({@link SyntheticTerrain}) or {@code earth[:x:z]} for Expanse's Earth terrain model near (x, z) with
 * the seed as world seed. Buildings are drawn as boxes of their footprint and height unless a real build module is
 * registered (or {@code -Dhearth.preview.blocks=true}), when every blueprint block is drawn in its map colour.
 */
public final class SettlementPreview {
	private static final String[][] SUITE = {
		{"temperate", "hills"}, {"temperate", "river"}, {"mediterranean", "slope"}, {"alpine", "steep"}, {"mesa", "cliff"}, {"bayou", "lake"},
		{"maritime", "coast"}, {"desert", "flat"}, {"steppe", "flat"}, {"moorland", "hills"}, {"karst", "cliff"}, {"wisteria", "river"},
		{"tundra", "flat"}, {"northern", "hills"}
	};

	public static void main(String[] args) throws Exception {
		String cultures = args.length > 0 ? args[0] : "temperate";
		int[] days = Arrays.stream((args.length > 1 ? args[1] : "120").split(",")).mapToInt(Integer::parseInt).sorted().toArray();
		String terrains = args.length > 2 ? args[2] : "hills";
		String seeds = args.length > 3 ? args[3] : "1";
		File out = new File(args.length > 4 ? args[4] : "build/previews");
		out.mkdirs();
		boolean blocks = Boolean.getBoolean("hearth.preview.blocks");
		BlueprintService service = setupBlueprints(blocks);
		boolean blockMode = blocks || !(service instanceof CatalogueDouble);
		List<String[]> runs = new ArrayList<>();
		if (cultures.equals("suite")) {
			runs.addAll(Arrays.asList(SUITE));
			if (args.length <= 1) {
				days = new int[]{30, 120, 400};
			}
		} else {
			for (String c : cultures.split(",")) {
				for (String t : terrains.split(",")) {
					runs.add(new String[]{c, t});
				}
			}
		}
		for (String[] run : runs) {
			for (String seedText : seeds.split(",")) {
				preview(run[0], run[1], Long.parseLong(seedText), days, out, blockMode ? service : null);
			}
		}
	}

	private static BlueprintService setupBlueprints(boolean forceBlocks) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		try {
			dev.voidmc.hearth.build.BuildModule.init();
		} catch (Throwable t) {
			System.out.println("build module not usable offline (" + t + "); using the planner's catalogue double");
		}
		if (HearthServices.hasBlueprints()) {
			System.out.println("drawing real blueprints from " + HearthServices.blueprints().getClass().getName());
			return HearthServices.blueprints();
		}
		CatalogueDouble d = new CatalogueDouble();
		HearthServices.setBlueprints(d);
		return d;
	}

	private static void preview(String cultureName, String terrainName, long seed, int[] days, File out, BlueprintService blocks) throws Exception {
		Culture culture = Culture.valueOf(cultureName.toUpperCase(Locale.ROOT));
		TerrainProbe terrain;
		BlockPos center;
		if (terrainName.startsWith("earth")) {
			String[] p = terrainName.split(":");
			int cx = p.length > 2 ? Integer.parseInt(p[1]) : 0;
			int cz = p.length > 2 ? Integer.parseInt(p[2]) : 0;
			EarthProbe earth = new EarthProbe(seed);
			int[] site = earth.findSite(cx, cz, 384);
			terrain = earth;
			center = new BlockPos(site[0], earth.groundY(site[0], site[1]), site[1]);
		} else {
			terrain = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrainName.toUpperCase(Locale.ROOT)), seed, temperature(culture));
			center = new BlockPos(0, terrain.groundY(0, 0), 0);
		}
		HearthPlanner planner = new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS);
		long t0 = System.nanoTime();
		MiniSim sim = new MiniSim(culture, terrain, seed, planner, center);
		String tag = cultureName + "_" + terrainName.replace(':', '_') + "_s" + seed;
		for (int day : days) {
			sim.run(day - sim.day());
			PlanState state = sim.settlement.data(PlanModule.STATE);
			BoundingBox b = sim.settlement.bounds();
			int margin = 24;
			int x0 = Math.max(b.minX() - margin, center.getX() - 450);
			int z0 = Math.max(b.minZ() - margin, center.getZ() - 450);
			int x1 = Math.min(b.maxX() + margin, center.getX() + 450);
			int z1 = Math.min(b.maxZ() + margin, center.getZ() + 450);
			// at least a 160-block square round the heart, so young hamlets show their land
			x0 = Math.min(x0, center.getX() - 80);
			z0 = Math.min(z0, center.getZ() - 80);
			x1 = Math.max(x1, center.getX() + 80);
			z1 = Math.max(z1, center.getZ() + 80);
			Scene scene = new Scene(terrain, x0, z0, x1 - x0 + 1, z1 - z0 + 1);
			scene.addSettlement(sim.settlement, terrain, Catalogue.current(), blocks);
			int roads = state.graph.builtLength();
			String title = String.format(Locale.ROOT, "%s on %s  ·  %s layout%s  ·  day %d  ·  %d folk (%s)  ·  %d plots  ·  %d blocks of road",
				cap(cultureName), terrainName, state.layout.getSerializedName(),
				state.signature.getSerializedName().equals("none") ? "" : ", " + state.signature.getSerializedName().replace('_', ' '),
				sim.day(), sim.settlement.population(), sim.settlement.tier().getSerializedName(), sim.settlement.plots().size(), roads);
			File plan = new File(out, "settlement_" + tag + "_d" + day + "_plan.png");
			File iso = new File(out, "settlement_" + tag + "_d" + day + "_iso.png");
			ImageIO.write(new PlanRenderer(scene, 1400).render(sim.settlement, state, Catalogue.current(), title, blocks != null), "png", plan);
			ImageIO.write(new IsoRenderer(scene, 1800).render(title), "png", iso);
			System.out.printf(Locale.ROOT, "%-14s %-10s seed %d day %3d: %-10s %-12s pop %3d %-7s plots %3d roads %5d  planner %.1f ms total, %d calls, slowest %.2f ms -> %s%n",
				cultureName, terrainName, seed, sim.day(), state.layout.getSerializedName(), state.signature.getSerializedName(), sim.settlement.population(),
				sim.settlement.tier().getSerializedName(), sim.settlement.plots().size(), roads, sim.plannerNanos / 1e6, sim.calls, sim.slowestNext / 1e6,
				plan.getName());
		}
		// the growth log: every project and why, next to the images
		java.nio.file.Files.write(new File(out, "settlement_" + tag + "_log.txt").toPath(), sim.log());
		System.out.printf(Locale.ROOT, "  (%.1f s)%n", (System.nanoTime() - t0) / 1e9);
	}

	private static float temperature(Culture c) {
		return switch (c) {
			case TUNDRA -> 0.0F;
			case NORTHERN, ALPINE -> 0.3F;
			case DESERT, MESA, STEPPE -> 2.0F;
			case TROPICAL, BAYOU, KARST -> 0.95F;
			default -> 0.8F;
		};
	}

	private static String cap(String s) {
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	static int count(Iterable<? extends PlotView> plots) {
		int n = 0;
		for (PlotView ignored : plots) {
			n++;
		}
		return n;
	}
}
