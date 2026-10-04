package dev.voidmc.hearth.gametest.station;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.BlueprintPreview;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.PreviewScene;
import dev.voidmc.hearth.gametest.render.Voxels;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/**
 * Contact sheets of the homes by station at a scale where their detail reads: for each archetype, one row per culture,
 * and per seed the front corner, the back corner and an isometric cut at the ground floor (the furnished room). Every
 * blueprint is checked; problems print as {@code CHECK ...}. Run through the init script
 * {@code docs/buildings/station_preview.gradle}:
 *
 * <pre>
 *   ./gradlew -I docs/buildings/station_preview.gradle stationPreview -Parchetype=hovel,croft -Pstyle=temperate,desert -Pseeds=2 -Pterrain="slope;station=poor"
 *   args: archetypes  styles|all  seeds  terrain[;k=v...]  outDir  scale(px per block, default 20)  cuts(true/false)
 * </pre>
 *
 * Output: {@code build/previews/station/station_<archetype>_<terrain>.png}.
 */
public final class StationPreview {
	private static final int BG = 0xFFE9E4DA;

	private StationPreview() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String archetypeArg = args.length > 0 ? args[0] : "hovel";
		String styleArg = args.length > 1 ? args[1] : "temperate";
		int seeds = args.length > 2 ? Integer.parseInt(args[2]) : 2;
		String terrainArg = args.length > 3 ? args[3] : "flat";
		Path out = Path.of(args.length > 4 ? args[4] : "build/previews/station");
		int scale = args.length > 5 && !args[5].isBlank() ? Integer.parseInt(args[5]) : 20;
		boolean cuts = args.length <= 6 || args[6].isBlank() || Boolean.parseBoolean(args[6]);
		CompoundTag extra = new CompoundTag();
		if (terrainArg.contains(";")) {
			extra = parse(terrainArg.substring(terrainArg.indexOf(';') + 1).replace(';', ','));
			terrainArg = terrainArg.substring(0, terrainArg.indexOf(';'));
		}
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		List<Archetype> archetypes = new ArrayList<>();
		for (String a : archetypeArg.split(",")) {
			for (Archetype x : registry.all()) {
				if (x.info().id().getPath().equals(a) || x.info().id().getPath().endsWith("/" + a)) {
					archetypes.add(x);
				}
			}
		}
		List<Style> styles = new ArrayList<>();
		for (Culture c : Culture.values()) {
			Style s = service.styles().get(StyleRegistry.styleId(c));
			if (s != null && (styleArg.equals("all") || Arrays.asList(styleArg.split(",")).contains(c.getSerializedName()))) {
				styles.add(s);
			}
		}
		Assets assets = new Assets();
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(assets));
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
		final CompoundTag params = extra;
		final String terrain = terrainArg;
		for (Archetype a : archetypes) {
			List<List<Future<BufferedImage[]>>> rows = new ArrayList<>();
			List<String> labels = new ArrayList<>();
			for (Style style : styles) {
				List<Future<BufferedImage[]>> row = new ArrayList<>();
				for (int s = 0; s < seeds; s++) {
					long seed = params.contains("seed") ? params.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
					int index = s;
					row.add(pool.submit(() -> render(service, renderer, a.info(), style, seed, index, terrain, params, scale, cuts)));
				}
				rows.add(row);
				labels.add(style.culture() == null ? style.id().toString() : style.culture().getSerializedName());
			}
			List<List<BufferedImage[]>> done = new ArrayList<>();
			for (List<Future<BufferedImage[]>> row : rows) {
				List<BufferedImage[]> r = new ArrayList<>();
				for (Future<BufferedImage[]> f : row) {
					r.add(f.get());
				}
				done.add(r);
			}
			BufferedImage sheet = sheet(done, labels, a.info().id() + " on " + terrain + (params.isEmpty() ? "" : " " + params));
			Path file = out.resolve("station_" + a.info().id().getPath().replace('/', '_') + "_" + terrain + ".png");
			ImageIO.write(sheet, "png", file.toFile());
			System.out.println("wrote " + file.toAbsolutePath());
		}
		pool.shutdown();
	}

	private static BufferedImage[] render(BlueprintServiceImpl service, IsoRenderer renderer, ArchetypeInfo info, Style style, long seed, int index,
		String terrain, CompoundTag extra, int scale, boolean cuts) {
		Culture culture = style.culture() == null ? Culture.TEMPERATE : style.culture();
		float temperature = culture == Culture.TUNDRA ? -0.3F : culture == Culture.ALPINE || culture == Culture.NORTHERN ? 0.3F : 0.8F;
		SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42, temperature);
		CompoundTag p = extra.copy();
		PlotSpec base = BlueprintPreview.spec(info, style, seed, index, probe, p);
		int height = extra.contains("height") ? extra.getIntOr("height", base.maxHeight()) : base.maxHeight();
		PlotSpec spec = new PlotSpec(base.archetype(), base.style(), base.seed(), base.origin(), base.sizeX(), base.sizeZ(), base.facing(), height, base.tier(),
			base.params());
		BlueprintImpl bp = service.compiled(spec, probe);
		List<String> problems = BlueprintChecks.all(bp, probe);
		if (!problems.isEmpty()) {
			synchronized (StationPreview.class) {
				System.out.println("CHECK " + info.id().getPath() + " " + culture.getSerializedName() + " s" + index + " seed " + seed + " " + spec.facing() + " "
					+ spec.frontage() + "x" + spec.depth() + " h" + height + ": " + String.join(" | ", problems));
			}
		}
		Voxels world = PreviewScene.build(bp, spec, probe, 2, culture);
		double yaw = switch (spec.facing()) {
			case SOUTH -> 45;
			case EAST -> 135;
			case NORTH -> 225;
			default -> 315;
		};
		List<BufferedImage> imgs = new ArrayList<>();
		imgs.add(label(renderer.render(world, new View(yaw, 30), scale, 1, 0),
			culture.getSerializedName() + " s" + index + " " + spec.frontage() + "x" + spec.depth() + " h" + height + " " + bp.beds() + " beds"));
		imgs.add(renderer.render(world, new View(yaw + 180, 30), scale, 1, 0));
		if (cuts) {
			int k = 0;
			for (int y = spec.floorY() + 1; y <= bp.bounds().maxY() && k < (extra.getIntOr("cutsMax", 2)); y += extra.getIntOr("cutStep", 4), k++) {
				Voxels c = world.cut(y, (x, yy, z, s) -> false);
				imgs.add(renderer.render(c, new View(yaw, 50), Math.max(8, scale * 3 / 4), 1, 0));
			}
		}
		// each view on its own too, for a close look
		try {
			Path dir = Path.of(System.getProperty("hearth.station.out", "build/previews/station")).resolve(info.id().getPath().replace('/', '_'));
			Files.createDirectories(dir);
			String stem = culture.getSerializedName() + "_s" + index + "_" + terrain;
			String[] names = {"front", "back", "cut0", "cut1", "cut2", "cut3"};
			for (int i = 0; i < imgs.size() && i < names.length; i++) {
				ImageIO.write(imgs.get(i), "png", dir.resolve(stem + "_" + names[i] + ".png").toFile());
			}
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		return imgs.toArray(new BufferedImage[0]);
	}

	private static BufferedImage label(BufferedImage img, String text) {
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight() + 18, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		g.drawImage(img, 0, 18, null);
		g.setColor(new Color(0x30, 0x30, 0x30));
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(text, 4, 13);
		g.dispose();
		return out;
	}

	private static BufferedImage sheet(List<List<BufferedImage[]>> rows, List<String> labels, String title) {
		int pad = 6;
		int w = 0;
		int h = 26;
		int[] rowH = new int[rows.size()];
		for (int r = 0; r < rows.size(); r++) {
			int rw = 0;
			for (BufferedImage[] cell : rows.get(r)) {
				for (BufferedImage i : cell) {
					rw += i.getWidth() + pad;
					rowH[r] = Math.max(rowH[r], i.getHeight());
				}
				rw += pad * 2;
			}
			w = Math.max(w, rw);
			h += rowH[r] + pad;
		}
		BufferedImage out = new BufferedImage(Math.max(400, w), h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x20, 0x20, 0x20));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.drawString(title, 6, 18);
		int y = 26;
		for (int r = 0; r < rows.size(); r++) {
			int x = pad;
			for (BufferedImage[] cell : rows.get(r)) {
				for (BufferedImage i : cell) {
					g.drawImage(i, x, y, null);
					x += i.getWidth() + pad;
				}
				x += pad * 2;
			}
			y += rowH[r] + pad;
		}
		g.dispose();
		return out;
	}

	private static CompoundTag parse(String s) {
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
}
