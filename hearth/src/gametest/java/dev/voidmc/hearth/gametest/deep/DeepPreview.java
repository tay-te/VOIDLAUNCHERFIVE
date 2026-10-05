package dev.voidmc.hearth.gametest.deep;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.kit.deep.DeepSite;
import dev.voidmc.hearth.build.kit.deep.DeepTerrain;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.NightLight;
import dev.voidmc.hearth.gametest.render.NightPanels;
import dev.voidmc.hearth.gametest.render.Voxels;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Renders the deep town's buildings in a realm (LIVING_WORLD.md §8.2): each archetype on a synthetic realm floor
 * ({@link DeepTerrain}: open floor, rubble, the foot of the realm's wall, a ledge), with the params the planner gives a
 * deep plot ({@code realm}, {@code ceiling_y}, {@code character}, the realm's {@code local} materials), by day (to read
 * the forms) and lit only by its own light under the vault (as it stands in the game: no sky reaches a realm). Every
 * blueprint is checked. Run through {@code docs/buildings/deep_preview.gradle}:
 *
 * <pre>
 *   ./gradlew -I docs/buildings/deep_preview.gradle deepPreview -Parchetype=deep/dwelling -Pstyle=temperate,karst -Pseeds=3 -Pterrain=wall
 *   args: archetype|all  style|all|a,b  seeds  terrain[,terrain][;k=v;...]  outDir
 * </pre>
 *
 * Terrains: the {@link DeepTerrain} kinds ({@code floor}, {@code rubble}, {@code wall}, {@code ledge}), {@code surface}
 * (a deep plot drawn on the surface's probe: the lift's fallback, and what a caller who forgets the realm gives) and the
 * synthetic surface kinds ({@code flat}, {@code slope}... with no realm params: {@code /hearth build} by hand). Params
 * after a semicolon go to the spec; preview keys: {@code character} (else each seed takes the next realm), {@code seed},
 * {@code facing}, {@code frontage}, {@code depth}, {@code tier}, {@code height}, {@code ceiling=false} (no {@code ceiling_y}),
 * {@code depthbelow} (the lift: how far under the street the realm floor lies, default 56), {@code night=true} (the light
 * check's night panels), {@code lit=false} (skip the lit renders).
 */
public final class DeepPreview {
	private static final int BG = 0xFFE9E4DA;

	private DeepPreview() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String archetypeArg = args.length > 0 ? args[0] : "all";
		String styleArg = args.length > 1 ? args[1] : "temperate";
		int seeds = args.length > 2 ? Integer.parseInt(args[2]) : 2;
		String terrainArg = args.length > 3 ? args[3] : "floor";
		Path out = Path.of(args.length > 4 ? args[4] : "build/previews/deep");
		CompoundTag extra = new CompoundTag();
		if (terrainArg.contains(";")) {
			extra = parse(terrainArg.substring(terrainArg.indexOf(';') + 1));
			terrainArg = terrainArg.substring(0, terrainArg.indexOf(';'));
		}
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		List<Archetype> archetypes = new ArrayList<>();
		for (Archetype a : registry.all()) {
			if (!a.info().id().getPath().startsWith("deep/")) {
				continue;
			}
			if (archetypeArg.equals("all") || matches(archetypeArg, a.info().id())) {
				archetypes.add(a);
			}
		}
		List<Style> styles = new ArrayList<>();
		for (Culture c : Culture.values()) {
			Style s = service.styles().get(StyleRegistry.styleId(c));
			if (s != null && (styleArg.equals("all") || Arrays.asList(styleArg.split(",")).contains(c.getSerializedName()))) {
				styles.add(s);
			}
		}
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
		long t0 = System.currentTimeMillis();
		int count = 0;
		final CompoundTag params = extra;
		for (Archetype a : archetypes) {
			for (String terrain : terrainArg.split(",")) {
				List<Future<Cell>> cells = new ArrayList<>();
				for (Style style : styles) {
					for (int s = 0; s < seeds; s++) {
						int index = s;
						long seed = params.contains("seed") ? params.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
						cells.add(pool.submit(() -> renderOne(service, renderer, a.info(), style, seed, index, terrain, params, out)));
					}
				}
				List<Cell> done = new ArrayList<>();
				for (Future<Cell> f : cells) {
					try {
						done.add(f.get());
					} catch (java.util.concurrent.ExecutionException e) {
						pool.shutdownNow();
						e.getCause().printStackTrace();
						System.exit(1);
					}
				}
				count += done.size();
				String slug = a.info().id().getPath().replace('/', '_');
				sheet(done, styles.size(), seeds, out.resolve("sheet_" + slug + "_" + terrain + ".png"), a.info().id() + " on " + terrain, false);
				if (params.getBooleanOr("lit", true)) {
					sheet(done, styles.size(), seeds, out.resolve("sheet_" + slug + "_" + terrain + "_lit.png"), a.info().id() + " on " + terrain
						+ ", lit by its own light under the vault", true);
				}
			}
		}
		pool.shutdown();
		System.out.printf(Locale.ROOT, "rendered %d deep blueprints in %.1f s to %s%n", count, (System.currentTimeMillis() - t0) / 1000.0, out.toAbsolutePath());
	}

	private record Cell(BufferedImage image, BufferedImage lit, String label) {
	}

	/** The spec the deep planner would give: a size from the ranges, the floor where the street meets the front. */
	public static PlotSpec spec(ArchetypeInfo info, Identifier style, long seed, int index, String terrain, CompoundTag extra, TerrainProbe[] probeOut) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = extra.getIntOr("frontage", info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1));
		int depth = extra.getIntOr("depth", info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1));
		Direction facing = new Direction[]{Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST}[index % 4];
		if (extra.contains("facing")) {
			facing = Direction.byName(extra.getStringOr("facing", "south"));
		}
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		int fx = switch (facing) {
			case EAST -> ox + sizeX;
			case WEST -> ox - 1;
			default -> ox + sizeX / 2;
		};
		int fz = switch (facing) {
			case SOUTH -> oz + sizeZ;
			case NORTH -> oz - 1;
			default -> oz + sizeZ / 2;
		};
		CompoundTag params = extra.copy();
		for (String k : new String[]{"frontage", "depth", "facing", "seed", "tier", "height", "ceiling", "night", "lit", "depthbelow"}) {
			params.remove(k);
		}
		String character = extra.contains("character") ? extra.getStringOr("character", "wilds")
			: DeepSite.CHARACTERS[(int) Math.floorMod(seed / 1000 + index * 5L + style.getPath().hashCode(), DeepSite.CHARACTERS.length)];
		TerrainProbe probe;
		boolean deep = DeepTerrain.isKind(terrain);
		boolean lift = info.id().getPath().equals("deep/lift");
		int height = extra.getIntOr("height", Math.max(info.maxHeight(), 8));
		int floor;
		if (deep) {
			// the wall lies behind the plot, its face cutting the plot's last two rows
			DeepTerrain dt = DeepTerrain.named(terrain, 42, facing.getOpposite(), depth / 2 - 2);
			probe = dt;
			floor = dt.groundY(fx, fz);
			params.putLong("realm", 7L);
			params.putString("character", character);
			params.put("local", dev.voidmc.hearth.plan.resource.Materials.realm(character));
			if (extra.getBooleanOr("ceiling", true)) {
				params.putInt("ceiling_y", dt.ceilingOver(ox, oz, ox + sizeX - 1, oz + sizeZ - 1));
			}
			if (lift) {
				int below = extra.getIntOr("depthbelow", 56);
				params.putInt("bottom_y", floor);
				params.putInt("top_y", floor + below);
				params.putInt("street_y", floor + below);
				params.putInt("deep_street_y", floor);
				params.putString("deep_facing", (index % 2 == 0 ? facing : facing.getClockWise()).getSerializedName());
				height = below + 10;
			} else if (!extra.contains("height")) {
				height = Math.min(height, Math.max(info.minHeight(), params.getIntOr("ceiling_y", floor + 999) - floor - 1));
			}
		} else if (terrain.equals("surface")) {
			// a deep plot handed the surface's probe: the realm floor lies far under the ground the probe reads
			probe = new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 42);
			int below = extra.getIntOr("depthbelow", 56);
			floor = probe.groundY(fx, fz) - below;
			params.putLong("realm", 7L);
			params.putString("character", character);
			params.put("local", dev.voidmc.hearth.plan.resource.Materials.realm(character));
			if (lift) {
				params.putInt("bottom_y", floor);
				params.putInt("top_y", floor + below);
				params.putInt("street_y", floor + below);
				params.putInt("deep_street_y", floor);
				params.putString("deep_facing", facing.getSerializedName());
				height = below + 10;
			}
		} else {
			probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42);
			floor = probe.groundY(fx, fz);
			if (probe.waterY(fx, fz) != TerrainProbe.NO_WATER) {
				floor = Math.max(floor, probe.waterY(fx, fz) + 1);
			}
		}
		probeOut[0] = probe;
		int tier = extra.getIntOr("tier", 2);
		return new PlotSpec(info.id(), style, seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, height, tier, params);
	}

	private static Cell renderOne(BlueprintServiceImpl service, IsoRenderer renderer, ArchetypeInfo info, Style style, long seed, int index, String terrain,
		CompoundTag extra, Path out) throws IOException {
		Culture culture = style.culture() == null ? Culture.TEMPERATE : style.culture();
		TerrainProbe[] holder = new TerrainProbe[1];
		PlotSpec spec = spec(info, style.id(), seed, index, terrain, extra, holder);
		TerrainProbe probe = holder[0];
		BlueprintImpl bp = service.compiled(spec, probe);
		List<String> problems = BlueprintChecks.all(bp, probe);
		String character = spec.params().getStringOr("character", "-");
		if (!problems.isEmpty()) {
			synchronized (DeepPreview.class) {
				System.out.println("CHECK " + info.id().getPath() + " " + culture.getSerializedName() + " s" + index + " " + terrain + " (" + character + "): "
					+ String.join(" | ", problems));
			}
		}
		if (extra.contains("reach")) {
			synchronized (DeepPreview.class) {
				for (String ys : extra.getStringOr("reach", "0").split("/")) {
					System.out.println(BlueprintChecks.reachMap(bp, probe, spec.floorY() + Integer.parseInt(ys)));
				}
			}
		}
		if (extra.getBooleanOr("slices", false)) {
			synchronized (DeepPreview.class) {
				System.out.println("== " + spec.archetype() + " " + style.id() + " seed " + seed + " facing " + spec.facing());
				dev.voidmc.hearth.gametest.render.Slices.print(service.draw(spec, probe), spec);
			}
		}
		var b = bp.bounds();
		int m = 4;
		DeepTerrain dt = probe instanceof DeepTerrain t ? t : null;
		int top = b.maxY();
		DeepScene scene = DeepScene.build(probe, b.minX() - m, b.minZ() - m, b.maxX() + m, b.maxZ() + m, spec.floorY() - 3, character,
			dt == null ? null : (x, z) -> Math.min(dt.ceilingY(x, z), top + 3));
		scene.apply(bp);
		double yaw = switch (spec.facing()) {
			case SOUTH -> 45;
			case EAST -> 135;
			case NORTH -> 225;
			default -> 315;
		};
		int tall = b.maxY() - b.minY();
		int scale = tall > 120 ? 4 : tall > 60 ? 7 : tall > 30 ? 11 : 16;
		BufferedImage front = renderer.render(scene.world, new View(yaw, 30), scale, 2, 0);
		BufferedImage back = renderer.render(scene.world, new View(yaw + 180, 30), scale, 2, 0);
		BufferedImage lit = front;
		BufferedImage litBack = back;
		boolean doLit = extra.getBooleanOr("lit", true);
		if (doLit) {
			NightLight light = NightLight.of(dt == null ? scene.world : scene.sealed);
			lit = renderer.render(scene.world, new View(yaw, 30), scale, 2, NightPanels.BG, light);
			litBack = renderer.render(scene.world, new View(yaw + 180, 30), scale, 2, NightPanels.BG, light);
		}
		String label = culture.getSerializedName() + " s" + index + " " + spec.frontage() + "x" + spec.depth() + "x" + spec.maxHeight() + " "
			+ spec.facing().getSerializedName() + " · " + character + (problems.isEmpty() ? "" : " · CHECK");
		String slug = info.id().getPath().replace('/', '_');
		Path dir = out.resolve(slug);
		Files.createDirectories(dir);
		String base = culture.getSerializedName() + "_s" + index + "_" + terrain;
		// cutaways: a plan and a cut per storey of four, up to six
		List<BufferedImage> cuts = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		int step = Math.max(4, (tall + 3) / 4);
		for (int k = 0; spec.floorY() + k * step <= b.maxY() - 1 && k < 6; k++) {
			int cut = spec.floorY() + k * step + 1;
			Voxels c = scene.world.cut(cut, (x, y, z, s) -> false);
			cuts.add(renderer.render(c, View.TOP, tall > 60 ? 9 : 12, 1, 0));
			cuts.add(renderer.render(c, new View(yaw, 30), tall > 60 ? 6 : 10, 1, 0));
			labels.add("level " + (k * step) + " plan");
			labels.add("level " + (k * step) + " cut");
		}
		String title = info.id() + "  " + label + "  " + terrain + "  " + bp.totalSteps() + " steps, " + bp.beds() + " beds";
		ImageIO.write(panel(List.of(front, back), cuts, labels, title, BG), "png", dir.resolve(base + ".png").toFile());
		if (doLit) {
			ImageIO.write(panel(List.of(lit, litBack), List.of(), List.of(), title + "  (lit by its own light)", NightPanels.BG), "png",
				dir.resolve(base + "_lit.png").toFile());
		}
		if (extra.getBooleanOr("night", false)) {
			NightPanels.write(renderer, scene.world, bp, spec, probe, yaw, 4, dir.resolve(base + "_night.png"), title + " by night");
		}
		return new Cell(front, lit, label);
	}

	private static boolean matches(String arg, Identifier id) {
		for (String a : arg.split(",")) {
			if (a.equals(id.toString()) || a.equals(id.getPath()) || id.getPath().endsWith("/" + a)) {
				return true;
			}
		}
		return false;
	}

	static CompoundTag parse(String s) {
		CompoundTag t = new CompoundTag();
		for (String kv : s.split("[;,]")) {
			int eq = kv.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String k = kv.substring(0, eq).trim();
			String v = kv.substring(eq + 1).trim();
			if (v.equals("true") || v.equals("false")) {
				t.putBoolean(k, Boolean.parseBoolean(v));
			} else {
				try {
					t.putInt(k, Integer.parseInt(v));
				} catch (NumberFormatException e) {
					t.putString(k, v);
				}
			}
		}
		return t;
	}

	static BufferedImage panel(List<BufferedImage> top, List<BufferedImage> cuts, List<String> labels, String title, int bg) {
		int pad = 12;
		int row1h = 0;
		int row1w = pad;
		for (BufferedImage i : top) {
			row1h = Math.max(row1h, i.getHeight());
			row1w += i.getWidth() + pad;
		}
		int cutW = pad;
		int cutH = 0;
		for (BufferedImage c : cuts) {
			cutW += c.getWidth() + pad;
			cutH = Math.max(cutH, c.getHeight());
		}
		int w = Math.max(Math.max(row1w, cutW), 900);
		int h = 34 + row1h + pad + (cuts.isEmpty() ? 0 : cutH + 22 + pad);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(bg, true));
		g.fillRect(0, 0, w, h);
		g.setColor(bg == BG ? new Color(0x3A3226) : new Color(0xD8D0C0));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 22);
		int x = pad;
		for (BufferedImage i : top) {
			g.drawImage(i, x, 34 + row1h - i.getHeight(), null);
			x += i.getWidth() + pad;
		}
		x = pad;
		int y = 34 + row1h + pad;
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		for (int i = 0; i < cuts.size(); i++) {
			BufferedImage c = cuts.get(i);
			g.drawString(labels.get(i), x, y + 12);
			g.drawImage(c, x, y + 18, null);
			x += c.getWidth() + pad;
		}
		g.dispose();
		return img;
	}

	private static void sheet(List<Cell> cells, int rows, int cols, Path file, String title, boolean lit) throws IOException {
		int cell = 340;
		int pad = 8;
		int labelH = 16;
		int w = cols * (cell + pad) + pad;
		int h = 30 + rows * (cell + labelH + pad) + pad;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(lit ? NightPanels.BG : BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(lit ? new Color(0xD8D0C0) : new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 20);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		for (int i = 0; i < cells.size(); i++) {
			Cell c = cells.get(i);
			int r = i / cols;
			int col = i % cols;
			int x = pad + col * (cell + pad);
			int y = 30 + r * (cell + labelH + pad);
			g.drawString(c.label, x, y + 12);
			BufferedImage im = lit ? c.lit : c.image;
			double sc = Math.min(1.0, Math.min((double) cell / im.getWidth(), (double) cell / im.getHeight()));
			int iw = (int) (im.getWidth() * sc);
			int ih = (int) (im.getHeight() * sc);
			g.drawImage(im, x + (cell - iw) / 2, y + labelH + (cell - ih), iw, ih, null);
		}
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
	}
}
