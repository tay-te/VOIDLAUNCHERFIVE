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
		// Real buildings where the build module has them, the planner's boxes for the rest of the catalogue
		// (-Dhearth.preview.real=only plans with the real catalogue alone, as the game does).
		BlueprintService real = null;
		// HEARTH_PREVIEW_BOXES=1: the planner's boxes only, coloured by category and homes by station (the social map)
		boolean boxes = System.getenv("HEARTH_PREVIEW_BOXES") != null && !forceBlocks;
		try {
			real = boxes ? null : dev.voidmc.hearth.build.BuildModule.standalone();
		} catch (Throwable t) {
			System.out.println("build module not usable offline (" + t + "); using the planner's catalogue double");
		}
		BlueprintService service;
		if (real == null) {
			service = new CatalogueDouble();
		} else if ("only".equals(System.getProperty("hearth.preview.real"))) {
			service = real;
		} else {
			service = new dev.voidmc.hearth.gametest.plan.LayeredBlueprints(real, new CatalogueDouble());
		}
		System.out.println("drawing blueprints from " + service.getClass().getSimpleName()
			+ (real == null ? "" : " (" + real.archetypes().size() + " real archetypes)"));
		HearthServices.setBlueprints(service);
		return service;
	}

	/**
	 * "earthcliff:x:z": the dry ground at the foot of the highest 8-block drop within {@code radius} of (x, z) on the Earth
	 * terrain, for a cliff town on real Expanse land.
	 */
	private static int[] cliffSite(EarthProbe earth, int cx, int cz, int radius) {
		int[] best = {cx, cz};
		int bestDrop = 0;
		int[][] dirs = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}};
		for (int dz = -radius; dz <= radius; dz += 16) {
			for (int dx = -radius; dx <= radius; dx += 16) {
				int x = cx + dx;
				int z = cz + dz;
				int g = earth.groundY(x, z);
				if (earth.waterY(x, z) != EarthProbe.NO_WATER || g < earth.seaLevel() + 2) {
					continue;
				}
				for (int[] d : dirs) {
					int drop = earth.groundY(x - d[0], z - d[1]) - g;
					if (drop > bestDrop && earth.waterY(x + 2 * d[0], z + 2 * d[1]) == EarthProbe.NO_WATER) {
						bestDrop = drop;
						best = new int[]{x + d[0], z + d[1]};
					}
				}
			}
		}
		System.out.println("cliff of " + bestDrop + " over 8 blocks at " + best[0] + ", " + best[1]);
		return best;
	}

	private static void preview(String cultureSpec, String terrainName, long seed, int[] days, File out, BlueprintService blocks) throws Exception {
		// culture[/layout]: a layout after a slash forces it (to see each layout on the same land)
		String cultureName = cultureSpec.contains("/") ? cultureSpec.substring(0, cultureSpec.indexOf('/')) : cultureSpec;
		String layoutName = cultureSpec.contains("/") ? cultureSpec.substring(cultureSpec.indexOf('/') + 1) : null;
		dev.voidmc.hearth.plan.layout.LayoutChooser.FORCED.set(layoutName == null ? null
			: dev.voidmc.hearth.plan.model.LayoutType.valueOf(layoutName.toUpperCase(Locale.ROOT)));
		Culture culture = Culture.valueOf(cultureName.toUpperCase(Locale.ROOT));
		TerrainProbe terrain;
		BlockPos center;
		if (terrainName.startsWith("earth")) {
			String[] p = terrainName.split(":");
			int cx = p.length > 2 ? Integer.parseInt(p[1]) : 0;
			int cz = p.length > 2 ? Integer.parseInt(p[2]) : 0;
			EarthProbe earth = new EarthProbe(seed);
			// the realms below, as the sim's underground probe gives them in game (deep towns)
			dev.voidmc.hearth.gametest.plan.EarthUnderground under = new dev.voidmc.hearth.gametest.plan.EarthUnderground(seed);
			PlanModule.registerUnderground(earth, under);
			int radius = 384;
			if (terrainName.startsWith("earthdeep")) {
				// a town over a realm: the nearest realm to (x, z), the town on its land
				var realms = under.realmsNear(cx, cz, 2000);
				final int fx = cx;
				final int fz = cz;
				var best = realms.stream().min(java.util.Comparator.comparingDouble(r -> Math.hypot(r.center().getX() - fx, r.center().getZ() - fz)))
					.orElse(null);
				if (best != null) {
					System.out.println("realm " + best.character() + " at " + best.center() + " radius " + best.radius());
					cx = best.center().getX();
					cz = best.center().getZ();
					radius = Math.max(64, Math.min(192, best.radius()));
				}
			}
			int[] site = terrainName.startsWith("earthcliff") ? cliffSite(earth, cx, cz, 1200) : earth.findSite(cx, cz, radius);
			terrain = earth;
			center = new BlockPos(site[0], earth.groundY(site[0], site[1]), site[1]);
		} else {
			// "steep+realm": the synthetic land with a realm under it, for a deep town
			boolean realm = terrainName.endsWith("+realm");
			String kind = realm ? terrainName.substring(0, terrainName.length() - "+realm".length()) : terrainName;
			terrain = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(kind.toUpperCase(Locale.ROOT)), seed, temperature(culture));
			center = new BlockPos(0, terrain.groundY(0, 0), 0);
			if (realm) {
				terrain = new dev.voidmc.hearth.plan.testing.CavedTerrain(terrain, 70, 30, 150, terrain.groundY(0, 0) - 70, "glowcap_wilds");
			}
		}
		HearthPlanner planner = new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS);
		long t0 = System.nanoTime();
		MiniSim sim = new MiniSim(culture, terrain, seed, planner, center);
		String tag = cultureSpec.replace('/', '-') + "_" + terrainName.replace(':', '_') + "_s" + seed;
		for (int day : days) {
			sim.run(day - sim.day());
			PlanState state = sim.settlement.data(PlanModule.STATE);
			BoundingBox b = sim.settlement.bounds();
			int margin = 24;
			int x0 = Math.max(b.minX() - margin, center.getX() - 450);
			int z0 = Math.max(b.minZ() - margin, center.getZ() - 450);
			int x1 = Math.min(b.maxX() + margin, center.getX() + 450);
			int z1 = Math.min(b.maxZ() + margin, center.getZ() + 450);
			// the territory's sites, so the workings and the land that feeds the town show
			for (var site : sim.settlement.sites()) {
				var a = site.area();
				x0 = Math.min(x0, Math.max(a.minX() - 8, center.getX() - 450));
				z0 = Math.min(z0, Math.max(a.minZ() - 8, center.getZ() - 450));
				x1 = Math.max(x1, Math.min(a.maxX() + 8, center.getX() + 450));
				z1 = Math.max(z1, Math.min(a.maxZ() + 8, center.getZ() + 450));
			}
			// at least a 160-block square round the heart, so young hamlets show their land
			x0 = Math.min(x0, center.getX() - 80);
			z0 = Math.min(z0, center.getZ() - 80);
			x1 = Math.max(x1, center.getX() + 80);
			z1 = Math.max(z1, center.getZ() + 80);
			Scene scene = new Scene(terrain, x0, z0, x1 - x0 + 1, z1 - z0 + 1);
			scene.addSettlement(sim.settlement, terrain, Catalogue.current(), blocks);
			// the isometric view keeps to the town itself: its buildings, not the fields and tracks round it
			int[] town = townBounds(sim.settlement, new BlockPos(state.heartX, 0, state.heartZ));
			// HEARTH_PREVIEW_CLOSE=r: only r blocks round the heart (a close look at the heart and what climbs behind it)
			String close = System.getenv("HEARTH_PREVIEW_CLOSE");
			if (close != null) {
				int r = Integer.parseInt(close);
				town = new int[]{state.heartX - r, state.heartZ - r, state.heartX + r, state.heartZ + r};
			}
			Scene townScene = new Scene(terrain, town[0], town[1], town[2] - town[0] + 1, town[3] - town[1] + 1);
			townScene.addSettlement(sim.settlement, terrain, Catalogue.current(), blocks);
			int roads = state.graph.builtLength();
			String title = String.format(Locale.ROOT, "%s on %s  ·  %s layout%s  ·  day %d  ·  %d folk (%s)  ·  %d plots  ·  %d blocks of road",
				cap(cultureName), terrainName, state.layout.getSerializedName(),
				state.signature.getSerializedName().equals("none") ? "" : ", " + state.signature.getSerializedName().replace('_', ' '),
				sim.day(), sim.settlement.population(), sim.settlement.tier().getSerializedName(), sim.settlement.plots().size(), roads);
			File plan = new File(out, "settlement_" + tag + "_d" + day + "_plan.png");
			File iso = new File(out, "settlement_" + tag + "_d" + day + "_iso.png");
			ImageIO.write(new PlanRenderer(scene, 1500).render(sim.settlement, state, Catalogue.current(), title, blocks != null), "png", plan);
			// HEARTH_PREVIEW_TURN=n: the isometric view turned n quarters instead of looking down the slope
			String turn = System.getenv("HEARTH_PREVIEW_TURN");
			Scene seen = turn == null ? townScene.facingDownhill() : townScene.turnedBy(Integer.parseInt(turn));
			ImageIO.write(new IsoRenderer(seen, 1900).render(title), "png", iso);
			// the town itself, close up: its quarters and who lives in them
			File townPlan = new File(out, "settlement_" + tag + "_d" + day + "_town.png");
			ImageIO.write(new PlanRenderer(townScene, 1500).render(sim.settlement, state, Catalogue.current(), title, blocks != null), "png", townPlan);
			System.out.println("  " + stations(sim));
			deep(sim, terrain, state, tag, day, title, out, blocks);
			System.out.printf(Locale.ROOT, "%-14s %-10s seed %d day %3d: %-10s %-12s pop %3d %-7s plots %3d roads %5d  planner %.1f ms total, %d calls, slowest %.2f ms -> %s%n",
				cultureName, terrainName, seed, sim.day(), state.layout.getSerializedName(), state.signature.getSerializedName(), sim.settlement.population(),
				sim.settlement.tier().getSerializedName(), sim.settlement.plots().size(), roads, sim.plannerNanos / 1e6, sim.calls, sim.slowestNext / 1e6,
				plan.getName());
		}
		// the growth log: every project and why, next to the images
		java.nio.file.Files.write(new File(out, "settlement_" + tag + "_log.txt").toPath(), sim.log());
		System.out.printf(Locale.ROOT, "  (%.1f s)%n", (System.nanoTime() - t0) / 1e9);
	}

	/**
	 * The box round the bulk of the town (its buildings, not its farms, tracks or far suburbs): a square on the heart
	 * reaching a little past where 85 % of its buildings stand, at least 60 blocks.
	 */
	private static int[] townBounds(dev.voidmc.hearth.api.Settlement s, BlockPos center) {
		java.util.List<Double> d = new java.util.ArrayList<>();
		for (PlotView p : s.plots()) {
			var info = Catalogue.current().info(p.spec().archetype());
			if (info == null || info.category() == dev.voidmc.hearth.api.ArchetypeCategory.FARM
				|| info.category() == dev.voidmc.hearth.api.ArchetypeCategory.INFRASTRUCTURE
				|| info.flags().contains(dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.OUTSKIRTS) && info.category() != dev.voidmc.hearth.api.ArchetypeCategory.DEFENSE) {
				continue;
			}
			var sp = p.spec();
			double dx = sp.origin().getX() + sp.sizeX() / 2.0 - center.getX();
			double dz = sp.origin().getZ() + sp.sizeZ() / 2.0 - center.getZ();
			d.add(Math.max(Math.abs(dx), Math.abs(dz)));
		}
		java.util.Collections.sort(d);
		double r = d.isEmpty() ? 60 : d.get((int) Math.min(d.size() - 1, Math.floor(d.size() * 0.85)));
		int half = (int) Math.max(60, Math.min(240, r * 1.15 + 16));
		return new int[]{center.getX() - half, center.getZ() - half, center.getX() + half, center.getZ() + half};
	}

	/**
	 * The deep town, if the town has one (LIVING_WORLD.md §8.2): its plan and an isometric view over the realm's floor,
	 * from the deep plan state and only the deep plots.
	 */
	private static void deep(MiniSim sim, TerrainProbe terrain, PlanState state, String tag, int day, String title, File out, BlueprintService blocks)
		throws Exception {
		if (state.deep == null) {
			return;
		}
		dev.voidmc.hearth.api.UndergroundProbe u = PlanModule.underground(terrain);
		if (u == null) {
			return;
		}
		dev.voidmc.hearth.api.UndergroundProbe.Realm realm = u.realm(state.deep.heartX, state.deep.heartZ);
		if (realm == null) {
			return;
		}
		var floor = dev.voidmc.hearth.plan.deep.RealmTerrain.of(terrain, u, realm);
		var view = dev.voidmc.hearth.plan.deep.LevelView.deep(sim.settlement, state, 10);
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		int n = 0;
		for (PlotView p : view.plots()) {
			var s = p.spec();
			x0 = Math.min(x0, s.origin().getX());
			z0 = Math.min(z0, s.origin().getZ());
			x1 = Math.max(x1, s.origin().getX() + s.sizeX());
			z1 = Math.max(z1, s.origin().getZ() + s.sizeZ());
			n++;
		}
		if (n == 0) {
			return;
		}
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		int half = Math.max(48, Math.max(x1 - x0, z1 - z0) / 2 + 20);
		Scene scene = new Scene(floor, cx - half, cz - half, half * 2, half * 2);
		scene.underground = true;
		scene.caveFloor = realm.floorY();
		scene.addSettlement(view, floor, Catalogue.current(), blocks);
		String deepTitle = title + "  ·  the deep town in the " + realm.character().replace('_', ' ') + " realm (floor y " + realm.floorY() + ")";
		ImageIO.write(new PlanRenderer(scene, 1300).render(view, state.deep, Catalogue.current(), deepTitle, blocks != null), "png",
			new File(out, "settlement_" + tag + "_d" + day + "_deep.png"));
		ImageIO.write(new IsoRenderer(scene, 1600).render(deepTitle), "png", new File(out, "settlement_" + tag + "_d" + day + "_deep_iso.png"));
		System.out.println("  deep town: " + n + " plots in realm " + realm.character() + " (radius " + realm.radius() + ")");
	}

	/** Who lives in the town, by station, and the beds of their homes: the social geography as numbers. */
	private static String stations(MiniSim sim) {
		int[] folk = new int[dev.voidmc.hearth.api.Station.values().length];
		int[] beds = new int[folk.length];
		int[] homes = new int[folk.length];
		int fitting = 0;
		for (dev.voidmc.hearth.api.Resident r : sim.settlement.residents()) {
			dev.voidmc.hearth.api.Station s = dev.voidmc.hearth.plan.society.Stations.of(r, sim.settlement.tier());
			folk[s.ordinal()]++;
			var home = r.home >= 0 ? sim.settlement.plot(r.home) : null;
			if (home != null && dev.voidmc.hearth.plan.society.Stations.home(home.spec(), Catalogue.current().info(home.spec().archetype())) == s) {
				fitting++;
			}
		}
		for (PlotView p : sim.settlement.plots()) {
			var info = Catalogue.current().info(p.spec().archetype());
			dev.voidmc.hearth.api.Station s = dev.voidmc.hearth.plan.society.Stations.home(p.spec(), info);
			if (s != null) {
				homes[s.ordinal()]++;
				beds[s.ordinal()] += dev.voidmc.hearth.plan.growth.Needs.estimateBeds(info, p.spec());
			}
		}
		StringBuilder sb = new StringBuilder("stations (folk/homes/beds):");
		for (dev.voidmc.hearth.api.Station s : dev.voidmc.hearth.api.Station.values()) {
			if (folk[s.ordinal()] + homes[s.ordinal()] > 0) {
				sb.append(' ').append(s.getSerializedName()).append(' ').append(folk[s.ordinal()]).append('/').append(homes[s.ordinal()]).append('/')
					.append(beds[s.ordinal()]);
			}
		}
		sb.append("  ·  in a home of their station: ").append(fitting).append('/').append(sim.settlement.population());
		java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
		for (var site : sim.settlement.sites()) {
			kinds.merge(site.kind().getSerializedName(), 1, Integer::sum);
		}
		sb.append("  ·  sites ").append(kinds);
		sb.append("  ·  capacity ").append(dev.voidmc.hearth.plan.resource.Territory.estimateCapacity(sim.settlement.sites(),
			dev.voidmc.hearth.plan.PlanConfig.DEFAULTS.living()));
		return sb.toString();
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
