package dev.voidmc.hearth.gametest.sociallife;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/**
 * The social-life architect's renders, larger than the shared preview's: for each archetype a wide contact sheet (four
 * buildings to a row, one per culture and seed) and, per building, both corners at a big scale with a plan and a cut at
 * every floor, the cellar included. Run through {@code docs/buildings/sociallife_preview.gradle}:
 *
 * <pre>
 *   ./gradlew -I docs/buildings/sociallife_preview.gradle socialPreview -Parchetype=commerce/tavern -Pstyle=temperate,desert -Pseeds=2 -Pterrain=flat
 * </pre>
 *
 * Optional {@code -Pparams=k=v,...} go to the spec (and the preview keys {@code frontage}, {@code depth}, {@code facing},
 * {@code seed}, {@code tier}, {@code height}); {@code -Pscale=} sets the big views' pixels per block (default 26).
 * Output in {@code build/previews/sociallife/}.
 */
public final class SocialPreview {
	private static final int BG = 0xFFE9E4DA;

	private SocialPreview() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String archetypeArg = args[0];
		String styleArg = args[1];
		int seeds = Integer.parseInt(args[2]);
		String terrain = args[3];
		Path out = Path.of(args[4]);
		CompoundTag extra = args.length > 5 ? parse(args[5]) : new CompoundTag();
		int scale = args.length > 6 && !args[6].isEmpty() ? Integer.parseInt(args[6]) : 26;
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		int height = extra.getIntOr("height", -1);
		extra.remove("height");
		String reach = extra.getStringOr("reach", "");
		extra.remove("reach");
		boolean slices = extra.getBooleanOr("slices", false);
		String slice = extra.getStringOr("slice", "");
		extra.remove("slices");
		for (Archetype a : registry.all()) {
			if (!Arrays.asList(archetypeArg.split(",")).contains(a.info().id().getPath())) {
				continue;
			}
			List<BufferedImage> fronts = new ArrayList<>();
			List<String> labels = new ArrayList<>();
			for (Culture c : Culture.values()) {
				Style style = service.styles().get(StyleRegistry.styleId(c));
				if (style == null || !(styleArg.equals("all") || Arrays.asList(styleArg.split(",")).contains(c.getSerializedName()))) {
					continue;
				}
				for (int s = 0; s < seeds; s++) {
					long seed = extra.contains("seed") ? extra.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
					SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42,
						c == Culture.TUNDRA ? -0.3F : c == Culture.ALPINE || c == Culture.NORTHERN ? 0.3F : 0.8F);
					PlotSpec spec = BlueprintPreview.spec(a.info(), style, seed, s, probe, extra);
					if (height > 0) {
						spec = new PlotSpec(spec.archetype(), spec.style(), spec.seed(), spec.origin(), spec.sizeX(), spec.sizeZ(), spec.facing(), height,
							spec.tier(), spec.params());
					}
					BlueprintImpl bp = service.compiled(spec, probe);
					List<String> problems = dev.voidmc.hearth.build.check.BlueprintChecks.all(bp, probe);
					String label = c.getSerializedName() + " s" + s + " " + spec.frontage() + "x" + spec.depth() + " h" + spec.maxHeight() + " "
						+ spec.facing().getSerializedName() + "  " + bp.totalSteps() + " steps";
					java.util.Map<String, Integer> pois = new java.util.TreeMap<>();
					for (var poi : bp.pois()) {
						pois.merge(poi.type().getSerializedName() + (poi.job() != null && poi.job() != dev.voidmc.hearth.api.JobType.NONE ? ":" + poi.job().getSerializedName() : ""), 1, Integer::sum);
					}
					System.out.println("POIS " + a.info().id().getPath() + " " + c.getSerializedName() + " s" + s + ": " + pois);
					if (!problems.isEmpty()) {
						System.out.println("CHECK " + a.info().id().getPath() + " " + c.getSerializedName() + " s" + s + " " + terrain + ": "
							+ String.join(" | ", problems));
						label += "  [" + problems.size() + " CHECK]";
					}
					if (!reach.isEmpty()) {
						for (String ys : reach.split("/")) {
							System.out.println("reach y" + ys + ":" + System.lineSeparator()
								+ dev.voidmc.hearth.build.check.BlueprintChecks.reachMap(bp, probe, spec.floorY() + Integer.parseInt(ys)));
						}
					}
					if (slices) {
						dev.voidmc.hearth.gametest.render.Slices.print(service.draw(spec, probe), spec);
					}
					Voxels world = PreviewScene.build(bp, spec, probe, 2, c);
					double yaw = switch (spec.facing()) {
						case SOUTH -> 45;
						case EAST -> 135;
						case NORTH -> 225;
						default -> 315;
					};
					fronts.add(renderer.render(world, new View(yaw, 30), 14, 2, 0));
					labels.add(label);
					BufferedImage f = renderer.render(world, new View(yaw, 30), scale, 2, 0);
					BufferedImage b = renderer.render(world, new View(yaw + 180, 30), scale, 2, 0);
					BufferedImage side = renderer.render(world, new View(yaw - 45, 15), scale, 2, 0);
					List<BufferedImage> cuts = new ArrayList<>();
					List<String> cutLabels = new ArrayList<>();
					int floor = spec.floorY();
					int min = bp.bounds().minY();
					int top = bp.bounds().maxY();
					List<Integer> levels = new ArrayList<>();
					if (min <= floor - 4) {
						levels.add(floor - 3);
					}
					int sh = style.habits().walls().storeyHeight();
					for (int k = 0; floor + k * sh <= top - 1 && k < 5; k++) {
						levels.add(floor + k * sh + 1);
					}
					for (int lv : levels) {
						Voxels cut = world.cut(lv, (x, y, z, st) -> false);
						cuts.add(renderer.render(cut, View.TOP, Math.max(12, scale * 2 / 3), 1, 0));
						cuts.add(renderer.render(cut, new View(yaw, 55), scale, 2, 0));
						cutLabels.add("plan y" + (lv - floor - 1));
						cutLabels.add("cut y" + (lv - floor - 1));
					}
					if (extra.getBooleanOr("night", false)) {
						dev.voidmc.hearth.gametest.render.NightPanels.write(renderer, world, bp, spec, probe, yaw, sh,
							out.resolve(a.info().id().getPath().replace('/', '_') + "_" + c.getSerializedName() + "_s" + s + "_" + terrain + "_night.png"),
							a.info().id() + "  " + label + " by night");
					}
					BufferedImage panel = panel(List.of(side, f, b), cuts, cutLabels, a.info().id() + "  " + label + "  " + terrain);
					ImageIO.write(panel, "png", out.resolve(a.info().id().getPath().replace('/', '_') + "_" + c.getSerializedName() + "_s" + s + "_"
						+ terrain + ".png").toFile());
				}
			}
			ImageIO.write(sheet(fronts, labels, a.info().id() + " on " + terrain), "png",
				out.resolve("sheet_" + a.info().id().getPath().replace('/', '_') + "_" + terrain + ".png").toFile());
		}
		System.out.println("social previews written to " + out.toAbsolutePath());
		System.exit(0);
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

	private static BufferedImage panel(List<BufferedImage> views, List<BufferedImage> cuts, List<String> labels, String title) {
		int pad = 12;
		int row1w = pad;
		int row1h = 0;
		for (BufferedImage v : views) {
			row1w += v.getWidth() + pad;
			row1h = Math.max(row1h, v.getHeight());
		}
		// cuts in rows of four
		int perRow = 2;
		int cutRows = (cuts.size() + perRow - 1) / perRow;
		int cutW = 0;
		int cutH = 0;
		for (BufferedImage c : cuts) {
			cutW = Math.max(cutW, c.getWidth());
			cutH = Math.max(cutH, c.getHeight());
		}
		int w = Math.max(row1w, pad + perRow * (cutW + pad));
		int h = 34 + row1h + pad + cutRows * (cutH + 22 + pad);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 22);
		int x = pad;
		for (BufferedImage v : views) {
			g.drawImage(v, x, 34 + row1h - v.getHeight(), null);
			x += v.getWidth() + pad;
		}
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		for (int i = 0; i < cuts.size(); i++) {
			int r = i / perRow;
			int col = i % perRow;
			int cx = pad + col * (cutW + pad);
			int cy = 34 + row1h + pad + r * (cutH + 22 + pad);
			g.drawString(labels.get(i), cx, cy + 12);
			g.drawImage(cuts.get(i), cx, cy + 18, null);
		}
		g.dispose();
		return img;
	}

	private static BufferedImage sheet(List<BufferedImage> imgs, List<String> labels, String title) {
		int cols = 4;
		int cell = 0;
		for (BufferedImage i : imgs) {
			cell = Math.max(cell, Math.max(i.getWidth(), i.getHeight()));
		}
		cell = Math.min(cell, 520);
		int pad = 8;
		int labelH = 16;
		int rows = (imgs.size() + cols - 1) / cols;
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
		for (int i = 0; i < imgs.size(); i++) {
			int r = i / cols;
			int c = i % cols;
			int x = pad + c * (cell + pad);
			int y = 30 + r * (cell + labelH + pad);
			g.drawString(labels.get(i), x, y + 12);
			BufferedImage im = imgs.get(i);
			double sc = Math.min(1.0, Math.min((double) cell / im.getWidth(), (double) cell / im.getHeight()));
			int iw = (int) (im.getWidth() * sc);
			int ih = (int) (im.getHeight() * sc);
			g.drawImage(im, x + (cell - iw) / 2, y + labelH + (cell - ih), iw, ih, null);
		}
		g.dispose();
		return img;
	}
}
