package dev.voidmc.hearth.gametest.craft;

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
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/**
 * The craft architect's review renders: for each archetype, a large close view of every building from the front corner
 * and a low street view, a far view (the silhouette as it reads from a hundred blocks), and a grid sheet of every style
 * and seed. Run through the init script {@code docs/buildings/craft_preview.gradle}:
 *
 * <pre>
 *   ./gradlew -I docs/buildings/craft_preview.gradle craftPreview -Parchetype=work/tannery -Pstyle=temperate,desert -Pseeds=2 -Pterrain=flat
 *   args: archetypes  styles|all  seeds  terrain  outDir  [params k=v;k=v (frontage, depth, tier, facing, seed, scale, ...)]
 * </pre>
 * Output: {@code build/previews/craft/<archetype>/<culture>_s<n>_<terrain>_{close,street,back}.png}, the far view
 * {@code _far.png}, and {@code build/previews/craft/grid_<archetype>_<terrain>.png}.
 */
public final class CraftPreview {
	private static final int BG = 0xFFE9E4DA;

	private CraftPreview() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String archetypeArg = args[0];
		String styleArg = args.length > 1 ? args[1] : "all";
		int seeds = args.length > 2 ? Integer.parseInt(args[2]) : 2;
		String terrain = args.length > 3 ? args[3] : "flat";
		Path out = Path.of(args.length > 4 ? args[4] : "build/previews/craft");
		CompoundTag extra = new CompoundTag();
		if (args.length > 5 && !args[5].isBlank()) {
			for (String kv : args[5].split("[;,]")) {
				int eq = kv.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				String k = kv.substring(0, eq);
				String v = kv.substring(eq + 1);
				if (v.equals("true") || v.equals("false")) {
					extra.putBoolean(k, Boolean.parseBoolean(v));
				} else {
					try {
						extra.putInt(k, Integer.parseInt(v));
					} catch (NumberFormatException e) {
						extra.putString(k, v);
					}
				}
			}
		}
		int scale = extra.getIntOr("scale", 30);
		extra.remove("scale");
		boolean gridOnly = extra.getBooleanOr("grid_only", false);
		extra.remove("grid_only");
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		List<Style> styles = new ArrayList<>();
		for (Culture c : Culture.values()) {
			Style s = service.styles().get(StyleRegistry.styleId(c));
			if (s != null && (styleArg.equals("all") || Arrays.asList(styleArg.split(",")).contains(c.getSerializedName()))) {
				styles.add(s);
			}
		}
		Assets assets = new Assets();
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(assets));
		for (String a : archetypeArg.split(",")) {
			Archetype arch = null;
			for (Archetype r : registry.all()) {
				if (r.info().id().getPath().equals(a) || r.info().id().toString().equals(a)) {
					arch = r;
				}
			}
			if (arch == null) {
				System.out.println("no archetype " + a);
				continue;
			}
			ArchetypeInfo info = arch.info();
			String slug = info.id().getPath().replace('/', '_');
			Path dir = out.resolve(slug);
			Files.createDirectories(dir);
			List<BufferedImage> grid = new ArrayList<>();
			List<String> labels = new ArrayList<>();
			for (Style style : styles) {
				Culture culture = style.culture() == null ? Culture.TEMPERATE : style.culture();
				float temperature = culture == Culture.TUNDRA ? -0.3F : culture == Culture.ALPINE || culture == Culture.NORTHERN ? 0.3F : 0.8F;
				SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42, temperature);
				for (int s = 0; s < seeds; s++) {
					long seed = extra.contains("seed") ? extra.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
					CompoundTag params = extra.copy();
					if (!params.contains("facing")) {
						params.putString("facing", "south");
					}
					PlotSpec spec = BlueprintPreview.spec(info, style, seed, s, probe, params);
					BlueprintImpl bp = service.compiled(spec, probe);
					List<String> problems = BlueprintChecks.all(bp, probe);
					if (!problems.isEmpty()) {
						System.out.println("CHECK " + info.id().getPath() + " " + culture.getSerializedName() + " s" + s + " " + terrain + ": "
							+ String.join(" | ", problems));
					}
					Voxels world = PreviewScene.build(bp, spec, probe, 3, culture);
					double yaw = switch (spec.facing()) {
						case SOUTH -> 0;
						case EAST -> 90;
						case NORTH -> 180;
						default -> 270;
					};
					String label = culture.getSerializedName() + " s" + s + " " + spec.frontage() + "x" + spec.depth() + " h" + spec.maxHeight();
					grid.add(renderer.render(world, new View(yaw + 35, 28), 12, 2, 0));
					labels.add(label);
					if (gridOnly) {
						continue;
					}
					String base = culture.getSerializedName() + "_s" + s + "_" + terrain;
					ImageIO.write(renderer.render(world, new View(yaw + 35, 28), scale, 2, BG), "png", dir.resolve(base + "_close.png").toFile());
					ImageIO.write(renderer.render(world, new View(yaw - 30, 14), scale, 2, BG), "png", dir.resolve(base + "_street.png").toFile());
					ImageIO.write(renderer.render(world, new View(yaw + 160, 32), Math.max(12, scale * 2 / 3), 2, BG), "png",
						dir.resolve(base + "_back.png").toFile());
					ImageIO.write(renderer.render(world, new View(yaw + 25, 8), 4, 4, BG), "png", dir.resolve(base + "_far.png").toFile());
				}
			}
			writeGrid(grid, labels, Math.min(styles.size() * seeds, seeds == 1 ? 5 : seeds * 2), out.resolve("grid_" + slug + "_" + terrain + ".png"),
				info.id() + " on " + terrain);
		}
		System.out.println("craft previews in " + out.toAbsolutePath());
	}

	private static void writeGrid(List<BufferedImage> cells, List<String> labels, int cols, Path file, String title) throws java.io.IOException {
		int cw = 0;
		int ch = 0;
		for (BufferedImage b : cells) {
			cw = Math.max(cw, b.getWidth());
			ch = Math.max(ch, b.getHeight());
		}
		int pad = 8;
		int rows = (cells.size() + cols - 1) / cols;
		int w = cols * (cw + pad) + pad;
		int h = 30 + rows * (ch + 18 + pad) + pad;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 20);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		for (int i = 0; i < cells.size(); i++) {
			int r = i / cols;
			int c = i % cols;
			int x = pad + c * (cw + pad);
			int y = 30 + r * (ch + 18 + pad);
			g.drawString(labels.get(i), x, y + 12);
			BufferedImage b = cells.get(i);
			g.drawImage(b, x + (cw - b.getWidth()) / 2, y + 18 + (ch - b.getHeight()), null);
		}
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
	}
}
