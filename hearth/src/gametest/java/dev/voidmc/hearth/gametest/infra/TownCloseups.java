package dev.voidmc.hearth.gametest.infra;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.gametest.plan.EarthProbe;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.Voxels;
import dev.voidmc.hearth.plan.HearthPlanner;
import dev.voidmc.hearth.plan.PlanConfig;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Close-ups of a town grown by the real planner, in real textures, centred on its infrastructure: where roads meet,
 * bridges land, terraces carry houses, stair streets climb, docks reach out and walkways cross. Every plot touching
 * the window is drawn from its blueprint in id order with the sim's writer rule (a later plot replaces only natural
 * ground, air, plants and its own blocks; what an earlier plot built stays), so junctions look as they will in game.
 *
 * <pre>
 *   ./gradlew -I tools/infra-preview.gradle infraCloseups -Pculture=temperate -Pterrain=river -Pseed=2 -Pdays=200 -Pfocus=bridge
 *   args: culture[/layout]  terrain  seed  days  focus(heart|road|bridge|terrace|stairs|dock|walkway|all)  window  count  outDir
 * </pre>
 */
public final class TownCloseups {
	private TownCloseups() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String cultureSpec = args.length > 0 ? args[0] : "temperate";
		String terrainName = args.length > 1 ? args[1] : "hills";
		long seed = args.length > 2 ? Long.parseLong(args[2]) : 1;
		int days = args.length > 3 ? Integer.parseInt(args[3]) : 200;
		String focus = args.length > 4 ? args[4] : "all";
		int window = args.length > 5 ? Integer.parseInt(args[5]) : 36;
		int count = args.length > 6 ? Integer.parseInt(args[6]) : 3;
		File out = new File(args.length > 7 ? args[7] : "build/previews/closeups");
		out.mkdirs();

		BlueprintService real = dev.voidmc.hearth.build.BuildModule.standalone();
		BlueprintService service = new dev.voidmc.hearth.gametest.plan.LayeredBlueprints(real, new CatalogueDouble());
		HearthServices.setBlueprints(service);

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
			float t = switch (culture) {
				case TUNDRA -> 0.0F;
				case NORTHERN, ALPINE -> 0.3F;
				case DESERT, MESA, STEPPE -> 2.0F;
				default -> 0.8F;
			};
			terrain = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrainName.toUpperCase(Locale.ROOT)), seed, t);
			center = new BlockPos(0, terrain.groundY(0, 0), 0);
		}
		MiniSim sim = new MiniSim(culture, terrain, seed, new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS), center);
		sim.run(days);
		PlanState state = sim.settlement.data(PlanModule.STATE);
		List<PlotView> plots = new ArrayList<>(sim.settlement.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		System.out.printf(Locale.ROOT, "%s on %s seed %d day %d: %s, %d folk, %d plots%n", cultureSpec, terrainName, seed, days,
			state.layout.getSerializedName(), sim.settlement.population(), plots.size());

		List<int[]> foci = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		if (focus.equals("heart") || focus.equals("all")) {
			foci.add(new int[]{state.heartX, state.heartZ});
			labels.add("heart");
		}
		for (String kind : new String[]{"road", "bridge", "terrace", "stairs", "dock", "walkway"}) {
			if (!focus.equals(kind) && !focus.equals("all")) {
				continue;
			}
			int n = 0;
			for (PlotView p : plots) {
				if (!p.spec().archetype().getPath().equals("infra/" + kind) || p.state() == PlotState.PLANNED) {
					continue;
				}
				PlotSpec s = p.spec();
				// spread the picks out: skip one near an earlier focus
				int fx = s.origin().getX() + s.sizeX() / 2;
				int fz = s.origin().getZ() + s.sizeZ() / 2;
				boolean near = false;
				for (int[] f : foci) {
					near |= Math.abs(f[0] - fx) < window / 2 && Math.abs(f[1] - fz) < window / 2;
				}
				if (near) {
					continue;
				}
				foci.add(new int[]{fx, fz});
				labels.add(kind + " #" + p.id());
				if (++n >= count) {
					break;
				}
			}
		}
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		String tag = cultureSpec.replace('/', '-') + "_" + terrainName.replace(':', '_') + "_s" + seed + "_d" + days;
		for (int i = 0; i < foci.size(); i++) {
			int[] f = foci.get(i);
			Voxels v = scene(plots, service, terrain, f[0] - window / 2, f[1] - window / 2, window, culture);
			BufferedImage a = renderer.render(v, new IsoRenderer.View(45, 32), 18, 2, 0);
			BufferedImage b = renderer.render(v, new IsoRenderer.View(225, 32), 18, 2, 0);
			File file = new File(out, "closeup_" + tag + "_" + labels.get(i).replace(" #", "") + ".png");
			ImageIO.write(pair(a, b, tag + "  ·  " + labels.get(i) + " at " + f[0] + "," + f[1]), "png", file);
			System.out.println("  -> " + file.getPath());
		}
	}

	/** The land of a window and every plot over it, applied in id order with the writer's first-built-wins rule. */
	static Voxels scene(List<PlotView> plots, BlueprintService service, TerrainProbe terrain, int x0, int z0, int w, Culture culture) {
		Voxels v = new Voxels();
		int x1 = x0 + w - 1;
		int z1 = z0 + w - 1;
		boolean snowy = terrain.temperature(x0 + w / 2, z0 + w / 2) < 0.15F;
		int low = Integer.MAX_VALUE;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				low = Math.min(low, terrain.groundY(x, z));
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int g = terrain.groundY(x, z);
				int water = terrain.waterY(x, z);
				for (int y = low - 3; y < g; y++) {
					v.set(x, y, z, ground(culture, g - 1 - y, water != TerrainProbe.NO_WATER));
				}
				if (water != TerrainProbe.NO_WATER) {
					for (int y = g; y < water; y++) {
						v.set(x, y, z, Blocks.WATER.defaultBlockState());
					}
				} else if (snowy) {
					v.set(x, g, z, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1));
				}
			}
		}
		// who placed each position (0: nature)
		Long2IntOpenHashMap owner = new Long2IntOpenHashMap();
		for (PlotView p : plots) {
			if (p.state() == PlotState.PLANNED) {
				continue;
			}
			PlotSpec s = p.spec();
			if (s.origin().getX() > x1 + 1 || s.origin().getZ() > z1 + 1 || s.origin().getX() + s.sizeX() < x0 - 1 || s.origin().getZ() + s.sizeZ() < z0 - 1) {
				continue;
			}
			Blueprint bp;
			try {
				bp = service.blueprint(s, terrain);
			} catch (RuntimeException e) {
				continue;
			}
			int id = p.id() + 1;
			int stages = p.state() == PlotState.BUILT ? bp.stageCount() : Math.max(1, p.stage());
			for (int st = 0; st < stages; st++) {
				for (int i = 0; i < bp.stepCount(st); i++) {
					bp.step(st, i, (x, y, z, state, tag) -> {
						if (x < x0 || x > x1 || z < z0 || z > z1) {
							return;
						}
						long k = Voxels.key(x, y, z);
						int who = owner.getOrDefault(k, 0);
						BlockState cur = v.get(x, y, z);
						boolean replaceable = who == 0 || who == id || cur == null || cur.isAir() || cur.canBeReplaced();
						if (!replaceable) {
							return;
						}
						if (state.isAir() && cur == null) {
							return;
						}
						v.set(x, y, z, state);
						owner.put(k, id);
					});
				}
			}
		}
		return v;
	}

	private static BlockState ground(Culture c, int depth, boolean wet) {
		if (wet) {
			return depth == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		}
		if (depth >= 4) {
			return Blocks.STONE.defaultBlockState();
		}
		return switch (c) {
			case DESERT -> Blocks.SAND.defaultBlockState();
			case MESA -> depth == 0 ? Blocks.RED_SAND.defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState();
			default -> depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		};
	}

	private static BufferedImage pair(BufferedImage a, BufferedImage b, String title) {
		int pad = 10;
		int w = a.getWidth() + b.getWidth() + pad * 3;
		int h = Math.max(a.getHeight(), b.getHeight()) + 36;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0xFFE9E4DA, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.drawString(title, pad, 20);
		g.drawImage(a, pad, 30, null);
		g.drawImage(b, pad * 2 + a.getWidth(), 30, null);
		g.dispose();
		return img;
	}
}
