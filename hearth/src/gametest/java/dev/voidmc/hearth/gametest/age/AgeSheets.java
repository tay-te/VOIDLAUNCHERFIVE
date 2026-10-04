package dev.voidmc.hearth.gametest.age;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.age.Dressings;
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
 * Contact sheets for the age systems (docs/buildings/age.md), offline: a row per building, a column per state.
 *
 * <pre>
 *   decay      columns: decay 0, 0.15, 0.3, 0.5, 0.75, 1 (or levels=0/0.5/1)
 *   dress      columns: as built, market, festival, wedding, mourning, winter
 *   grow       columns: additions 0, 1, 2, 3, 4
 *   args: mode  archetypes(a,b|all-housing)  styles(a,b|all)  terrain  seeds  outDir  [params k=v,k=v]
 *   params: levels=0/0.5/1, back=true (the view from behind), yaw=..., scale=..., cut=N (cut away above local y N),
 *           anything else goes to the spec (frontage, depth, tier, facing, seed, storeys...)
 * </pre>
 * Run through {@code docs/buildings/age_preview.gradle}. Every blueprint is checked and problems print as CHECK lines.
 */
public final class AgeSheets {
	private static final int BG = 0xFFE9E4DA;

	private AgeSheets() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String mode = args.length > 0 ? args[0] : "decay";
		String archArg = args.length > 1 ? args[1] : "house/cottage";
		String styleArg = args.length > 2 ? args[2] : "temperate";
		String terrain = args.length > 3 ? args[3] : "flat";
		int seeds = args.length > 4 ? Integer.parseInt(args[4]) : 1;
		Path out = Path.of(args.length > 5 ? args[5] : "build/previews/age");
		CompoundTag extra = args.length > 6 ? parse(args[6]) : new CompoundTag();
		if (terrain.contains(";")) {
			extra.merge(parse(terrain.substring(terrain.indexOf(';') + 1).replace(';', ',')));
			terrain = terrain.substring(0, terrain.indexOf(';'));
		}
		Files.createDirectories(out);
		BlueprintServiceImpl service = BuildModule.standalone();
		List<ArchetypeInfo> archetypes = new ArrayList<>();
		for (ArchetypeInfo a : service.archetypes()) {
			boolean housing = a.category() == dev.voidmc.hearth.api.ArchetypeCategory.HOUSING;
			if (archArg.equals("all-housing") ? housing : archArg.equals("all") || matches(archArg, a)) {
				archetypes.add(a);
			}
		}
		archetypes.sort((a, b) -> a.id().toString().compareTo(b.id().toString()));
		List<Style> styles = new ArrayList<>();
		for (Culture c : Culture.values()) {
			if (styleArg.equals("all") || Arrays.asList(styleArg.split(",")).contains(c.getSerializedName())) {
				styles.add(service.styles().get(StyleRegistry.styleId(c)));
			}
		}
		List<String> columns = new ArrayList<>();
		switch (mode) {
			case "decay" -> {
				for (String l : extra.getStringOr("levels", "0/0.15/0.3/0.5/0.75/1").split("/")) {
					columns.add(l);
				}
			}
			case "dress" -> {
				columns.add("none");
				for (Dressings.Kind k : Dressings.Kind.values()) {
					columns.add(k.getSerializedName());
				}
			}
			case "grow" -> {
				for (String l : extra.getStringOr("levels", "0/1/2/3/4").split("/")) {
					columns.add(l);
				}
			}
			default -> throw new IllegalArgumentException("mode " + mode);
		}
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
		List<String> rowLabels = new ArrayList<>();
		List<List<Future<BufferedImage>>> rows = new ArrayList<>();
		String fterrain = terrain;
		for (ArchetypeInfo a : archetypes) {
			for (Style style : styles) {
				Culture culture = style.culture() == null ? Culture.TEMPERATE : style.culture();
				if (!a.cultures().isEmpty() && !a.cultures().contains(culture) && !extra.getBooleanOr("anyCulture", false)) {
					continue;
				}
				for (int s = 0; s < seeds; s++) {
					long seed = extra.contains("seed") ? extra.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
					int index = s;
					rowLabels.add(a.id().getPath() + "  " + culture.getSerializedName() + " s" + s);
					List<Future<BufferedImage>> row = new ArrayList<>();
					for (String col : columns) {
						row.add(pool.submit(() -> cell(service, renderer, mode, a, style, culture, seed, index, fterrain, extra, col)));
					}
					rows.add(row);
				}
			}
		}
		List<List<BufferedImage>> done = new ArrayList<>();
		for (List<Future<BufferedImage>> r : rows) {
			List<BufferedImage> imgs = new ArrayList<>();
			for (Future<BufferedImage> f : r) {
				try {
					imgs.add(f.get());
				} catch (java.util.concurrent.ExecutionException e) {
					pool.shutdownNow();
					e.getCause().printStackTrace();
					System.exit(1);
				}
			}
			done.add(imgs);
		}
		pool.shutdown();
		String name = extra.getStringOr("name", "age_" + mode + "_" + archArg.replace('/', '_').replace(',', '-') + "_" + styleArg.replace(',', '-') + "_" + terrain);
		Path file = out.resolve(name + ".png");
		List<String> heads = new ArrayList<>();
		for (String c : columns) {
			heads.add(switch (mode) {
				case "decay" -> "decay " + c;
				case "grow" -> "additions " + c;
				default -> c;
			});
		}
		sheet(done, rowLabels, heads, file, mode + " · " + terrain, extra.getIntOr("cell", 330));
		System.out.println("wrote " + file.toAbsolutePath());
	}

	private static boolean matches(String arg, ArchetypeInfo a) {
		for (String s : arg.split(",")) {
			if (a.id().getPath().equals(s) || a.id().getPath().endsWith("/" + s) || a.id().toString().equals(s)) {
				return true;
			}
		}
		return false;
	}

	private static BufferedImage cell(BlueprintServiceImpl service, IsoRenderer renderer, String mode, ArchetypeInfo a, Style style, Culture culture,
		long seed, int index, String terrain, CompoundTag extra, String col) {
		float temperature = culture == Culture.TUNDRA ? -0.3F : culture == Culture.ALPINE || culture == Culture.NORTHERN ? 0.3F : 0.8F;
		SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42, temperature);
		CompoundTag params = extra.copy();
		for (String k : new String[]{"levels", "back", "yaw", "scale", "name", "cell", "anyCulture", "cut", "pitch"}) {
			params.remove(k);
		}
		if (mode.equals("decay")) {
			params.putDouble("decay", Double.parseDouble(col));
		} else if (mode.equals("grow")) {
			params.putInt("additions", Integer.parseInt(col));
		}
		PlotSpec spec = BlueprintPreview.spec(a, style, seed, index, probe, params);
		BlueprintImpl bp = service.compiled(spec, probe);
		List<String> problems = BlueprintChecks.all(bp, probe);
		if (!problems.isEmpty()) {
			synchronized (AgeSheets.class) {
				System.out.println("CHECK " + a.id().getPath() + " " + culture.getSerializedName() + " s" + index + " " + terrain + " " + mode + "=" + col + ": "
					+ String.join(" | ", problems));
			}
		}
		Voxels world = PreviewScene.build(bp, spec, probe, 3, culture);
		if (mode.equals("dress") && !col.equals("none")) {
			Dressings.Kind kind = Dressings.Kind.byName(col);
			for (Dressings.Step st : Dressings.steps(spec, bp, kind, 0)) {
				world.set(st.pos().getX(), st.pos().getY(), st.pos().getZ(), st.state());
			}
		}
		if (extra.contains("cut")) {
			int cut = spec.floorY() + extra.getIntOr("cut", 3);
			world = world.cut(cut, (x, y, z, s) -> false);
		}
		double yaw = switch (spec.facing()) {
			case SOUTH -> 45;
			case EAST -> 135;
			case NORTH -> 225;
			default -> 315;
		};
		if (extra.getBooleanOr("back", false)) {
			yaw += 180;
		}
		yaw += extra.getIntOr("yaw", 0);
		return renderer.render(world, new View(yaw, extra.getIntOr("pitch", 30)), extra.getIntOr("scale", 12), 2, 0);
	}

	static CompoundTag parse(String s) {
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

	static void sheet(List<List<BufferedImage>> rows, List<String> rowLabels, List<String> heads, Path file, String title, int cell) throws java.io.IOException {
		int pad = 8;
		int labelH = 16;
		int cols = heads.size();
		int w = cols * (cell + pad) + pad;
		int h = 30 + labelH + rows.size() * (cell + labelH + pad) + pad;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 20);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		for (int c = 0; c < cols; c++) {
			g.drawString(heads.get(c), pad + c * (cell + pad), 30 + 12);
		}
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		for (int r = 0; r < rows.size(); r++) {
			int y = 30 + labelH + r * (cell + labelH + pad);
			g.drawString(rowLabels.get(r), pad, y + 12);
			List<BufferedImage> row = rows.get(r);
			// one scale per row so the columns compare
			double sc = 1.0;
			for (BufferedImage im : row) {
				sc = Math.min(sc, Math.min((double) cell / im.getWidth(), (double) cell / im.getHeight()));
			}
			for (int c = 0; c < row.size(); c++) {
				BufferedImage im = row.get(c);
				int iw = (int) (im.getWidth() * sc);
				int ih = (int) (im.getHeight() * sc);
				int x = pad + c * (cell + pad);
				g.drawImage(im, x + (cell - iw) / 2, y + labelH + (cell - ih), iw, ih, null);
			}
		}
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
	}
}
