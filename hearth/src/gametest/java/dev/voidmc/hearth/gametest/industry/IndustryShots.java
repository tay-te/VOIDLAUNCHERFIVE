package dev.voidmc.hearth.gametest.industry;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
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
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/**
 * Close-ups and culture grids of the heavy workshops (docs/buildings/industry.md), larger than the contact sheets of
 * {@link BlueprintPreview}: what an architect looks at before calling a building done.
 *
 * <pre>
 *   ./gradlew -I docs/buildings/industry_preview.gradle industryShots -Pids=work/smelter -Pstyle=temperate -Pseeds=2 -Pterrain=hills -Pscale=28
 *   ./gradlew -I docs/buildings/industry_preview.gradle industryShots -Pids=work/smelter -Pstyle=all -Pmode=grid
 * </pre>
 *
 * Modes: {@code close} (per building, two opposite corners at {@code scale} pixels a block), {@code grid} (every style
 * in the list, one seed each, front view, in a grid). Params after semicolons in the terrain go to the spec as in the
 * blueprint preview (frontage, depth, facing, seed, tier, variant and anything the archetype reads). Output:
 * build/previews/industry/.
 */
public final class IndustryShots {
	private IndustryShots() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String ids = args[0];
		String styleArg = args[1];
		int seeds = Integer.parseInt(args[2]);
		String terrainArg = args[3];
		Path out = Path.of(args[4]);
		String mode = args.length > 5 ? args[5] : "close";
		int scale = args.length > 6 ? Integer.parseInt(args[6]) : 28;
		CompoundTag extra = new CompoundTag();
		if (terrainArg.contains(";")) {
			for (String kv : terrainArg.substring(terrainArg.indexOf(';') + 1).split(";")) {
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
			terrainArg = terrainArg.substring(0, terrainArg.indexOf(';'));
		}
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		List<Style> styles = new ArrayList<>();
		for (Culture c : Culture.values()) {
			Style s = service.styles().get(StyleRegistry.styleId(c));
			if (s != null && (styleArg.equals("all") || List.of(styleArg.split(",")).contains(c.getSerializedName()))) {
				styles.add(s);
			}
		}
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		for (String id : ids.split(",")) {
			ArchetypeInfo info = null;
			for (var a : registry.all()) {
				if (a.info().id().getPath().equals(id) || a.info().id().toString().equals(id)) {
					info = a.info();
				}
			}
			if (info == null) {
				System.out.println("no archetype " + id);
				continue;
			}
			for (String terrain : terrainArg.split(",")) {
				List<BufferedImage> cells = new ArrayList<>();
				List<String> labels = new ArrayList<>();
				for (Style style : styles) {
					Culture culture = style.culture() == null ? Culture.TEMPERATE : style.culture();
					for (int s = 0; s < seeds; s++) {
						long seed = extra.contains("seed") ? extra.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
						float temperature = culture == Culture.TUNDRA ? -0.3F : culture == Culture.ALPINE || culture == Culture.NORTHERN ? 0.3F : 0.8F;
						SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42, temperature);
						PlotSpec spec = BlueprintPreview.spec(info, style, seed, s, probe, extra);
						BlueprintImpl bp = service.compiled(spec, probe);
						List<String> problems = BlueprintChecks.all(bp, probe);
						String label = culture.getSerializedName() + " s" + s + " " + spec.frontage() + "x" + spec.depth() + " h" + spec.maxHeight() + " "
							+ spec.facing().getSerializedName() + " " + bp.totalSteps() + " steps";
						if (!problems.isEmpty()) {
							System.out.println("CHECK " + id + " " + label + ": " + String.join(" | ", problems));
						}
						Voxels world = PreviewScene.build(bp, spec, probe, 3, culture);
						double yaw = switch (spec.facing()) {
							case SOUTH -> 45;
							case EAST -> 135;
							case NORTH -> 225;
							default -> 315;
						};
						if (mode.equals("grid")) {
							cells.add(renderer.render(world, new View(yaw - 10, 28), scale, 2, 0));
							labels.add(label);
						} else {
							BufferedImage a = renderer.render(world, new View(yaw - 12, 30), scale, 2, 0);
							BufferedImage b = renderer.render(world, new View(yaw + 168, 30), scale, 2, 0);
							BufferedImage street = renderer.render(world, new View(yaw - 45, 12), scale, 2, 0);
							BufferedImage plan = renderer.render(world, View.TOP, Math.max(8, scale / 2), 1, 0);
							BufferedImage img = row(List.of(a, b, street, plan), id + "  " + label + "  " + terrain);
							String name = id.replace('/', '_') + "_" + culture.getSerializedName() + "_s" + s + "_" + terrain + ".png";
							ImageIO.write(img, "png", out.resolve(name).toFile());
						}
					}
				}
				if (mode.equals("grid")) {
					BufferedImage g = grid(cells, labels, Math.min(4, cells.size()), id + " on " + terrain);
					ImageIO.write(g, "png", out.resolve("grid_" + id.replace('/', '_') + "_" + terrain + ".png").toFile());
				}
			}
		}
		System.out.println("industry shots written to " + out.toAbsolutePath());
	}

	private static BufferedImage row(List<BufferedImage> imgs, String title) {
		int pad = 10;
		int w = pad;
		int h = 0;
		for (BufferedImage i : imgs) {
			w += i.getWidth() + pad;
			h = Math.max(h, i.getHeight());
		}
		BufferedImage img = new BufferedImage(w, h + 34, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0xFFE9E4DA, true));
		g.fillRect(0, 0, w, h + 34);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 22);
		int x = pad;
		for (BufferedImage i : imgs) {
			g.drawImage(i, x, 30 + h - i.getHeight(), null);
			x += i.getWidth() + pad;
		}
		g.dispose();
		return img;
	}

	private static BufferedImage grid(List<BufferedImage> cells, List<String> labels, int cols, String title) {
		int cell = 0;
		for (BufferedImage c : cells) {
			cell = Math.max(cell, Math.max(c.getWidth(), c.getHeight()));
		}
		cell = Math.min(cell, 520);
		int pad = 8;
		int rows = (cells.size() + cols - 1) / cols;
		int w = cols * (cell + pad) + pad;
		int h = 30 + rows * (cell + 18 + pad) + pad;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0xFFE9E4DA, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 20);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		for (int i = 0; i < cells.size(); i++) {
			BufferedImage c = cells.get(i);
			int x = pad + (i % cols) * (cell + pad);
			int y = 30 + (i / cols) * (cell + 18 + pad);
			g.drawString(labels.get(i), x, y + 12);
			double sc = Math.min(1.0, Math.min((double) cell / c.getWidth(), (double) cell / c.getHeight()));
			int iw = (int) (c.getWidth() * sc);
			int ih = (int) (c.getHeight() * sc);
			g.drawImage(c, x + (cell - iw) / 2, y + 16 + (cell - ih), iw, ih, null);
		}
		g.dispose();
		return img;
	}
}
