package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.PreviewScene;
import dev.voidmc.hearth.gametest.render.Sampler;
import dev.voidmc.hearth.gametest.render.Voxels;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
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
 * Renders blueprints to PNG without starting the game: real block textures from the client jar, isometric views from
 * the front-right and back-left corners, a cut-away plan and isometric cut per storey, and a contact sheet per
 * archetype and terrain with every style and seed. Expanse blocks fall back to their vanilla stand-ins (the game is
 * not running, so only vanilla registries exist).
 *
 * <pre>
 *   ./gradlew blueprintPreview -Parchetype=hearth:house/cottage -Pstyle=temperate -Pseeds=4 -Pterrain=slope
 *   ./gradlew blueprintPreview -Parchetype=cottage -Pstyle=desert -Pseeds=2 -Pterrain="flat;roof=flat;frontage=9;tier=3"
 *   args: archetype|all  style|all|a,b,c  seeds  terrain|all|a,b[;k=v;...]  outDir  [params as k=v,k=v]
 *   params: frontage, depth, tier (spec) and anything else (passed to the archetype's params)
 * </pre>
 */
public final class BlueprintPreview {
	private static final int BG = 0xFFE9E4DA;

	private BlueprintPreview() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String archetypeArg = args.length > 0 ? args[0] : "all";
		String styleArg = args.length > 1 ? args[1] : "all";
		int seeds = args.length > 2 ? Integer.parseInt(args[2]) : 3;
		String terrainArg = args.length > 3 ? args[3] : "flat";
		Path out = Path.of(args.length > 4 ? args[4] : "build/previews");
		CompoundTag extra = args.length > 5 ? parseParams(args[5]) : new CompoundTag();
		// the terrain argument may carry params after a semicolon: -Pterrain="slope;roof=gable;frontage=9"
		if (terrainArg.contains(";")) {
			extra.merge(parseParams(terrainArg.substring(terrainArg.indexOf(';') + 1).replace(';', ',')));
			terrainArg = terrainArg.substring(0, terrainArg.indexOf(';'));
		}
		boolean panels = !Boolean.getBoolean("hearth.preview.sheetsOnly");
		Files.createDirectories(out);

		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		registry.register(new Sampler());
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		List<Archetype> archetypes = new ArrayList<>();
		for (Archetype a : registry.all()) {
			boolean sampler = a.info().id().getPath().startsWith("debug/");
			if (archetypeArg.equals("all") ? !sampler : matches(archetypeArg, a.info().id())) {
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
		List<String> terrains = terrainArg.equals("all") ? List.of(SyntheticTerrain.KINDS) : Arrays.asList(terrainArg.split(","));

		Assets assets = new Assets();
		ModelLibrary models = new ModelLibrary(assets);
		IsoRenderer renderer = new IsoRenderer(models);
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
		long t0 = System.currentTimeMillis();
		int count = 0;
		for (Archetype a : archetypes) {
			for (String terrain : terrains) {
				List<Future<Cell>> cells = new ArrayList<>();
				for (Style style : styles) {
					for (int s = 0; s < seeds; s++) {
						long seed = extra.contains("seed") ? extra.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
						int index = s;
						cells.add(pool.submit(() -> renderOne(service, renderer, a.info(), style, seed, index, terrain, extra, out, panels)));
					}
				}
				List<Cell> done = new ArrayList<>();
				for (Future<Cell> f : cells) {
					try {
						done.add(f.get());
					} catch (java.util.concurrent.ExecutionException e) {
						// never leave the pool's threads holding the JVM open
						pool.shutdownNow();
						e.getCause().printStackTrace();
						System.exit(1);
					}
				}
				count += done.size();
				writeSheet(done, styles.size(), seeds, out.resolve("sheet_" + slug(a.info().id()) + "_" + terrain + ".png"),
					a.info().id() + " on " + terrain);
			}
		}
		pool.shutdown();
		System.out.printf(Locale.ROOT, "rendered %d blueprints in %.1f s to %s%n", count, (System.currentTimeMillis() - t0) / 1000.0, out.toAbsolutePath());
	}

	private static boolean matches(String arg, Identifier id) {
		for (String a : arg.split(",")) {
			if (a.equals(id.toString()) || a.equals(id.getPath()) || id.getPath().endsWith("/" + a)) {
				return true;
			}
		}
		return false;
	}

	private static CompoundTag parseParams(String s) {
		CompoundTag t = new CompoundTag();
		for (String kv : s.split(",")) {
			int eq = kv.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String k = kv.substring(0, eq);
			String v = kv.substring(eq + 1);
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

	static String slug(Identifier id) {
		return id.getPath().replace('/', '_');
	}

	private record Cell(BufferedImage image, String label) {
	}

	/** The spec a planner might give this archetype on this land: a size from its range, floor level with the street. */
	public static PlotSpec spec(ArchetypeInfo info, Style style, long seed, int index, TerrainProbe probe, CompoundTag extra) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		if (extra.contains("frontage")) {
			frontage = extra.getIntOr("frontage", frontage);
		}
		if (extra.contains("depth")) {
			depth = extra.getIntOr("depth", depth);
		}
		Direction facing = new Direction[]{Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST}[index % 4];
		if (extra.contains("facing")) {
			facing = Direction.byName(extra.getStringOr("facing", "south"));
		}
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		// the street runs along the front: the floor meets the ground there
		int fx;
		int fz;
		switch (facing) {
			case SOUTH -> {
				fx = ox + sizeX / 2;
				fz = oz + sizeZ;
			}
			case NORTH -> {
				fx = ox + sizeX / 2;
				fz = oz - 1;
			}
			case EAST -> {
				fx = ox + sizeX;
				fz = oz + sizeZ / 2;
			}
			default -> {
				fx = ox - 1;
				fz = oz + sizeZ / 2;
			}
		}
		int floor = probe.groundY(fx, fz);
		if (probe.waterY(fx, fz) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(fx, fz) + 1);
		}
		CompoundTag params = extra.copy();
		params.remove("frontage");
		params.remove("depth");
		params.remove("facing");
		params.remove("seed");
		params.remove("stage");
		int tier = params.contains("tier") ? params.getIntOr("tier", 2) : 2;
		params.remove("tier");
		// height=N: the plot's height (default the archetype's most)
		int height = params.contains("height") ? params.getIntOr("height", info.maxHeight()) : Math.max(info.maxHeight(), 8);
		params.remove("height");
		return new PlotSpec(info.id(), style.id(), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, height, tier, params);
	}

	private static Cell renderOne(BlueprintServiceImpl service, IsoRenderer renderer, ArchetypeInfo info, Style style, long seed, int index, String terrain,
		CompoundTag extra, Path out, boolean panels) throws IOException {
		Culture culture = style.culture() == null ? Culture.TEMPERATE : style.culture();
		float temperature = culture == Culture.TUNDRA ? -0.3F : culture == Culture.ALPINE || culture == Culture.NORTHERN ? 0.3F : 0.8F;
		SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42, temperature);
		PlotSpec spec = spec(info, style, seed, index, probe, extra);
		BlueprintImpl bp = service.compiled(spec, probe);
		List<String> problems = dev.voidmc.hearth.build.check.BlueprintChecks.all(bp, probe);
		if (!problems.isEmpty()) {
			synchronized (BlueprintPreview.class) {
				System.out.println("CHECK " + info.id().getPath() + " " + style.id().getPath() + " s" + index + " " + terrain + ": " + String.join(" | ", problems));
			}
		}
		if (extra.contains("reach")) {
			synchronized (BlueprintPreview.class) {
				for (String ys : extra.getStringOr("reach", "0").split("/")) {
					System.out.println(dev.voidmc.hearth.build.check.BlueprintChecks.reachMap(bp, probe, spec.floorY() + Integer.parseInt(ys)));
				}
			}
		}
		if (extra.getBooleanOr("slices", false)) {
			synchronized (BlueprintPreview.class) {
				System.out.println("== " + spec.archetype() + " " + style.id() + " seed " + seed + " facing " + spec.facing());
				dev.voidmc.hearth.gametest.render.Slices.print(service.draw(spec, probe), spec);
			}
		}
		// stage=<kind> (preview only): the site as built through the last stage of that kind, the next one waiting
		String upTo = extra.getStringOr("stage", "");
		int stages = Integer.MAX_VALUE;
		if (!upTo.isEmpty()) {
			StageKind last = StageKind.valueOf(upTo.toUpperCase(Locale.ROOT));
			stages = 0;
			while (stages < bp.stageCount() && bp.stageKind(stages).ordinal() <= last.ordinal()) {
				stages++;
			}
			terrain = terrain + "_" + upTo;
		}
		Voxels world = PreviewScene.build(bp, spec, probe, 3, culture, stages);
		double yaw = switch (spec.facing()) {
			case SOUTH -> 45;
			case EAST -> 135;
			case NORTH -> 225;
			default -> 315;
		};
		int variant = spec.params().contains("variant") ? spec.params().getIntOr("variant", -1)
			: style.pickVariant(dev.voidmc.hearth.build.draft.Noise.hash(spec.seed(), 0x7A1A));
		String label = culture.getSerializedName() + " s" + index + " " + spec.frontage() + "x" + spec.depth() + " " + spec.facing().getSerializedName()
			+ (variant >= 0 ? " · " + style.variants().get(variant).name() : "");
		if (extra.getBooleanOr("night", false)) {
			// night renders: the light the building gives, outside and storey by storey, with unmet light needs marked
			dev.voidmc.hearth.gametest.render.NightPanels.write(renderer, world, bp, spec, probe, yaw, style.habits().walls().storeyHeight(),
				out.resolve(slug(info.id())).resolve(culture.getSerializedName() + "_s" + index + "_" + terrain + "_night.png"),
				info.id() + "  " + label + "  " + terrain + "  by night");
		}
		// -Dhearth.preview.px=24 renders the corner views larger (pixels per block; default 16)
		int px = Integer.getInteger("hearth.preview.px", 16);
		BufferedImage front = renderer.render(world, new View(yaw, 30), px, 2, 0);
		if (panels) {
			BufferedImage back = renderer.render(world, new View(yaw + 180, 30), px, 2, 0);
			List<BufferedImage> cuts = new ArrayList<>();
			List<String> cutLabels = new ArrayList<>();
			int sh = style.habits().walls().storeyHeight();
			int top = bp.bounds().maxY();
			for (int k = 0; spec.floorY() + k * sh <= top - 1 && k < 6; k++) {
				int cut = spec.floorY() + k * sh + 1;
				Voxels c = world.cut(cut, (x, y, z, s) -> false);
				cuts.add(renderer.render(c, View.TOP, 12, 1, 0));
				cuts.add(renderer.render(c, new View(yaw, 30), 10, 1, 0));
				cutLabels.add("storey " + k + " plan");
				cutLabels.add("storey " + k + " cut");
			}
			Path dir = out.resolve(slug(info.id()));
			Files.createDirectories(dir);
			BufferedImage panel = panel(front, back, cuts, cutLabels, info.id() + "  " + label + "  " + terrain + "  "
				+ bp.totalSteps() + " steps, " + bp.beds() + " beds");
			ImageIO.write(panel, "png", dir.resolve(culture.getSerializedName() + "_s" + index + "_" + terrain + ".png").toFile());
			ImageIO.write(front, "png", dir.resolve(culture.getSerializedName() + "_s" + index + "_" + terrain + "_front.png").toFile());
		}
		return new Cell(front, label);
	}

	private static BufferedImage panel(BufferedImage a, BufferedImage b, List<BufferedImage> cuts, List<String> labels, String title) {
		int pad = 12;
		int row1h = Math.max(a.getHeight(), b.getHeight());
		int row1w = a.getWidth() + b.getWidth() + pad * 3;
		int cutW = 0;
		int cutH = 0;
		for (BufferedImage c : cuts) {
			cutW += c.getWidth() + pad;
			cutH = Math.max(cutH, c.getHeight());
		}
		int w = Math.max(row1w, cutW + pad);
		int h = 34 + row1h + pad + (cuts.isEmpty() ? 0 : cutH + 22 + pad);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 22);
		g.drawImage(a, pad, 34 + row1h - a.getHeight(), null);
		g.drawImage(b, pad * 2 + a.getWidth(), 34 + row1h - b.getHeight(), null);
		int x = pad;
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

	private static void writeSheet(List<Cell> cells, int rows, int cols, Path file, String title) throws IOException {
		int cell = 300;
		int pad = 8;
		int labelH = 16;
		int w = cols * (cell + pad) + pad;
		int h = 30 + rows * (cell + labelH + pad) + pad;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
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
			BufferedImage im = c.image;
			double sc = Math.min((double) cell / im.getWidth(), (double) cell / im.getHeight());
			sc = Math.min(sc, 1.0);
			int iw = (int) (im.getWidth() * sc);
			int ih = (int) (im.getHeight() * sc);
			g.drawImage(im, x + (cell - iw) / 2, y + labelH + (cell - ih), iw, ih, null);
		}
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
	}
}
