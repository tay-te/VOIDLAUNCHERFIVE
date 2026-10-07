package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.gametest.plan.EarthProbe;
import dev.voidmc.hearth.gametest.plan.LayeredBlueprints;
import dev.voidmc.hearth.plan.HearthPlanner;
import dev.voidmc.hearth.plan.PlanConfig;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * A whole town in real textures (ART_REVIEW.md item 30), so an architect sees a building among its neighbours: the
 * planner grows the town offline (MiniSim, as {@code settlementPreview}), every plot is drawn from the real build
 * module's blueprint ({@link TownScene}), and the textured {@link IsoRenderer} renders an overview of the town and
 * close-ups (the heart, then the town's most prominent buildings: landmarks, civic and noble houses, inns), from two
 * corners, by day and, with {@code night}, by their own light ({@link NightLight}). Then the similarity check across
 * cultures ({@link CultureSimilarity}) runs over the archetypes the towns built.
 *
 * <pre>
 *   ./gradlew townPreview -Pculture=temperate -Pdays=200 -Pterrain=hills -Pseed=1 -Pnight=true
 *   ./gradlew townPreview -Pculture=temperate,northern,desert -Pterrain=flat -Pcloseups=3 -Psimilar=false
 *   ./gradlew townPreview -Pculture=mediterranean -Pterrain=earth:1200:-800 -Pseed=8675309
 *   args: culture(s)[/layout] | all   days   terrain   seed   night(false | true | only)   closeups   window   similar   outDir
 * </pre>
 * Terrains are the synthetic kinds or {@code earth[:x:z]} (Expanse's Earth model, the seed as world seed). Output in
 * build/previews/town/: {@code town_<culture>_<terrain>_s<seed>_d<days>_overview[_night].png} and
 * {@code ..._close<n>_<what>[_night].png} and {@code ..._floating.txt} (the floating-piece probe, {@link TownFloat}), plus
 * similarity.txt.
 */
public final class TownPreview {
	private static final int DAY_BG = 0xFFE9E4DA;

	private TownPreview() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String cultures = args.length > 0 ? args[0] : "temperate";
		int days = args.length > 1 ? Integer.parseInt(args[1]) : 200;
		String terrainName = args.length > 2 ? args[2] : "hills";
		long seed = args.length > 3 ? Long.parseLong(args[3]) : 1;
		String night = args.length > 4 ? args[4] : "false";
		int closeups = args.length > 5 ? Integer.parseInt(args[5]) : 4;
		int window = args.length > 6 ? Integer.parseInt(args[6]) : 60;
		boolean similar = args.length <= 7 || Boolean.parseBoolean(args[7]);
		File out = new File(args.length > 8 ? args[8] : "build/previews/town");
		out.mkdirs();

		BlueprintServiceImpl real = BuildModule.standalone();
		BlueprintService service = new LayeredBlueprints(real, new CatalogueDouble());
		HearthServices.setBlueprints(service);
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		boolean day = !night.equals("only");
		boolean dark = night.equals("true") || night.equals("only");

		List<String> runs = new ArrayList<>();
		if (cultures.equals("all")) {
			for (Culture c : Culture.values()) {
				runs.add(c.getSerializedName());
			}
		} else {
			Collections.addAll(runs, cultures.split(","));
		}
		TreeSet<Identifier> built = new TreeSet<>(java.util.Comparator.comparing(Identifier::toString));
		for (String cultureSpec : runs) {
			long t0 = System.nanoTime();
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
				int[] site = earth.findSite(cx, cz, 384);
				terrain = earth;
				center = new BlockPos(site[0], earth.groundY(site[0], site[1]), site[1]);
			} else {
				terrain = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrainName.toUpperCase(Locale.ROOT)), seed, temperature(culture));
				center = new BlockPos(0, terrain.groundY(0, 0), 0);
			}
			MiniSim sim = new MiniSim(culture, terrain, seed, new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS), center);
			sim.run(days);
			PlanState state = sim.settlement.data(PlanModule.STATE);
			List<PlotView> plots = new ArrayList<>(sim.settlement.plots());
			for (PlotView p : plots) {
				if (p.state() != PlotState.PLANNED) {
					built.add(p.spec().archetype());
				}
			}
			String tag = "town_" + cultureSpec.replace('/', '-') + "_" + terrainName.replace(':', '_') + "_s" + seed + "_d" + days;
			String title = String.format(Locale.ROOT, "%s on %s  ·  %s layout  ·  day %d  ·  %d folk (%s)  ·  %d plots", cap(cultureName), terrainName,
				state.layout.getSerializedName(), sim.day(), sim.settlement.population(), sim.settlement.tier().getSerializedName(), plots.size());
			System.out.println(title);

			// the overview: the bulk of the town, scaled to about 2400 px across
			int half = townHalf(plots, state.heartX, state.heartZ);
			Voxels town = TownScene.build(plots, service, terrain, state.heartX - half, state.heartZ - half, state.heartX + half, state.heartZ + half, culture);
			int scale = Math.clamp(Math.round(2400f / ((2 * half + 1) * 1.414f)), 2, 10);
			IsoRenderer.View view = facingDownhill(terrain, state.heartX, state.heartZ, half);
			if (day) {
				write(renderer.render(town, view, scale, 2, DAY_BG), title + "  ·  overview", false, new File(out, tag + "_overview.png"));
			}
			if (dark) {
				write(renderer.render(town, view, scale, 2, NightPanels.BG, NightLight.of(town)), title + "  ·  overview by night", true,
					new File(out, tag + "_overview_night.png"));
			}
			town = null;

			// the floating-piece probe (art review 2): each plot on the town's land, then the composed town
			List<String> floating = TownFloat.report(plots, real, service, terrain, culture, state.heartX - half, state.heartZ - half, state.heartX + half,
				state.heartZ + half);
			java.nio.file.Files.write(new File(out, tag + "_floating.txt").toPath(), floating, java.nio.charset.StandardCharsets.UTF_8);
			System.out.println("  " + floating.get(0) + " -> " + tag + "_floating.txt");
			for (int i = 1; i < Math.min(floating.size(), 9); i++) {
				System.out.println("    " + floating.get(i));
			}

			// close-ups: the heart, then the prominent buildings, spread apart
			List<int[]> foci = new ArrayList<>();
			List<String> labels = new ArrayList<>();
			foci.add(new int[]{state.heartX, state.heartZ});
			labels.add("heart");
			List<PlotView> ranked = new ArrayList<>(plots);
			ranked.removeIf(p -> p.state() == PlotState.PLANNED || weight(service.archetype(p.spec().archetype())) == 0);
			ranked.sort(java.util.Comparator.comparingInt((PlotView p) -> -(weight(service.archetype(p.spec().archetype())) * 10000
				+ p.spec().sizeX() * p.spec().sizeZ())));
			for (PlotView p : ranked) {
				if (foci.size() >= closeups) {
					break;
				}
				PlotSpec s = p.spec();
				int fx = s.origin().getX() + s.sizeX() / 2;
				int fz = s.origin().getZ() + s.sizeZ() / 2;
				boolean near = false;
				for (int[] f : foci) {
					near |= Math.abs(f[0] - fx) < window * 0.6 && Math.abs(f[1] - fz) < window * 0.6;
				}
				if (!near) {
					foci.add(new int[]{fx, fz});
					labels.add(s.archetype().getPath().substring(s.archetype().getPath().indexOf('/') + 1));
				}
			}
			int closeScale = Math.clamp(Math.round(900f / (window * 1.414f)), 6, 18);
			for (int i = 0; i < foci.size(); i++) {
				int[] f = foci.get(i);
				Voxels v = TownScene.build(plots, service, terrain, f[0] - window / 2, f[1] - window / 2, f[0] + (window - 1) / 2, f[1] + (window - 1) / 2,
					culture);
				String what = labels.get(i) + " at " + f[0] + "," + f[1];
				String file = tag + "_close" + i + "_" + labels.get(i);
				if (day) {
					write(pair(renderer.render(v, new IsoRenderer.View(45, 32), closeScale, 2, 0), renderer.render(v, new IsoRenderer.View(225, 32), closeScale, 2, 0)),
						title + "  ·  " + what, false, new File(out, file + ".png"));
				}
				if (dark) {
					NightLight light = NightLight.of(v);
					write(pair(renderer.render(v, new IsoRenderer.View(45, 32), closeScale, 2, 0, light),
						renderer.render(v, new IsoRenderer.View(225, 32), closeScale, 2, 0, light)), title + "  ·  " + what + "  ·  by night", true,
						new File(out, file + "_night.png"));
				}
			}
			System.out.printf(Locale.ROOT, "  %d close-ups (%.1f s)%n", foci.size(), (System.nanoTime() - t0) / 1e9);
		}
		if (similar) {
			CultureSimilarity.write(CultureSimilarity.run(real, built, 2, 0.8), 0.8, new File(out, "similarity.txt").toPath());
		}
	}

	/** How much a close-up wants a building: the town's landmarks first, then its civic, noble and law houses, its inns and markets. */
	private static int weight(ArchetypeInfo info) {
		if (info == null) {
			return 0;
		}
		return switch (info.category()) {
			case LANDMARK -> 5;
			case CIVIC, NOBLE -> 4;
			case COMMERCE, LAW -> 3;
			case DEFENSE -> 2;
			default -> 0;
		};
	}

	/** Half the side of a square on the heart holding 85 % of the town's buildings (not its farms and roads), 48 to 180. */
	private static int townHalf(List<PlotView> plots, int hx, int hz) {
		List<Double> d = new ArrayList<>();
		for (PlotView p : plots) {
			ArchetypeInfo info = HearthServices.blueprints().archetype(p.spec().archetype());
			if (info == null || info.category() == ArchetypeCategory.FARM || info.category() == ArchetypeCategory.INFRASTRUCTURE) {
				continue;
			}
			PlotSpec s = p.spec();
			d.add(Math.max(Math.abs(s.origin().getX() + s.sizeX() / 2.0 - hx), Math.abs(s.origin().getZ() + s.sizeZ() / 2.0 - hz)));
		}
		Collections.sort(d);
		double r = d.isEmpty() ? 40 : d.get((int) Math.min(d.size() - 1, Math.floor(d.size() * 0.85)));
		return (int) Math.max(48, Math.min(180, r + 14));
	}

	/** The corner the land falls toward, so a hill town is seen from below with its streets climbing away. */
	private static IsoRenderer.View facingDownhill(TerrainProbe t, int hx, int hz, int half) {
		double best = Double.MAX_VALUE;
		double yaw = 45;
		for (double y : new double[]{45, 135, 225, 315}) {
			double a = Math.toRadians(y);
			// yaw 45 looks from the south-east: the camera stands at +x +z
			int cx = hx + (int) Math.round(Math.sin(a) * half * 0.7);
			int cz = hz + (int) Math.round(Math.cos(a) * half * 0.7);
			double g = t.groundY(cx, cz);
			if (g < best) {
				best = g;
				yaw = y;
			}
		}
		return new IsoRenderer.View(yaw, 32);
	}

	private static BufferedImage pair(BufferedImage a, BufferedImage b) {
		int pad = 10;
		BufferedImage img = new BufferedImage(a.getWidth() + b.getWidth() + pad, Math.max(a.getHeight(), b.getHeight()), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.drawImage(a, 0, 0, null);
		g.drawImage(b, a.getWidth() + pad, 0, null);
		g.dispose();
		return img;
	}

	/** Writes an image under a title bar, on the day or the night background. */
	private static void write(BufferedImage body, String title, boolean night, File file) throws java.io.IOException {
		int pad = 10;
		int w = body.getWidth() + 2 * pad;
		int h = body.getHeight() + 36 + pad;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(night ? NightPanels.BG : DAY_BG));
		g.fillRect(0, 0, w, h);
		g.setColor(night ? new Color(0xD8D2C4) : new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.drawString(title, pad, 22);
		g.drawImage(body, pad, 36, null);
		g.dispose();
		ImageIO.write(img, "png", file);
		System.out.println("  -> " + file.getPath());
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
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
