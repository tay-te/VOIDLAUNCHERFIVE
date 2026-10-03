package dev.voidmc.hearth.gametest.infra;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.BlueprintPreview;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.PreviewScene;
import dev.voidmc.hearth.gametest.render.Voxels;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/**
 * Larger shots of one infrastructure archetype per style: a three-quarter view from each side and a low side elevation
 * (where a bridge's arches and a terrace's walls show), water drawn see-through. Same spec rules as the blueprint
 * preview (its {@code frontage}, {@code depth}, {@code facing}, {@code tier}, {@code seed} keys and archetype params).
 *
 * <pre>
 *   ./gradlew -I tools/infra/preview.gradle infraShots -Parchetype=infra/bridge -Pstyle=temperate,karst -Pseeds=1 -Pterrain="river;facing=east;depth=30"
 * </pre>
 */
public final class InfraShots {
	private InfraShots() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String archetype = args.length > 0 ? args[0] : "infra/bridge";
		String styles = args.length > 1 ? args[1] : "temperate";
		int seeds = args.length > 2 ? Integer.parseInt(args[2]) : 1;
		String terrainArg = args.length > 3 ? args[3] : "flat";
		File out = new File(args.length > 4 ? args[4] : "build/previews/shots");
		int scale = args.length > 5 ? Integer.parseInt(args[5]) : 20;
		out.mkdirs();
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
		BlueprintServiceImpl service = BuildModule.standalone();
		ArchetypeInfo info = null;
		for (ArchetypeInfo i : service.archetypes()) {
			if (i.id().getPath().equals(archetype) || i.id().toString().equals(archetype)) {
				info = i;
			}
		}
		if (info == null) {
			throw new IllegalArgumentException("no archetype " + archetype);
		}
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		for (Culture c : Culture.values()) {
			if (!styles.equals("all") && !Arrays.asList(styles.split(",")).contains(c.getSerializedName())) {
				continue;
			}
			Style style = service.styles().get(StyleRegistry.styleId(c));
			for (int s = 0; s < seeds; s++) {
				long seed = extra.contains("seed") ? extra.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
				float temperature = c == Culture.TUNDRA ? -0.3F : c == Culture.ALPINE || c == Culture.NORTHERN ? 0.3F : 0.8F;
				SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrainArg.toUpperCase(Locale.ROOT)), 42, temperature);
				PlotSpec spec = BlueprintPreview.spec(info, style, seed, s, probe, extra);
				BlueprintImpl bp = service.compiled(spec, probe);
				List<String> problems = BlueprintChecks.all(bp, probe);
				if (!problems.isEmpty()) {
					System.out.println("CHECK " + archetype + " " + c.getSerializedName() + " s" + s + ": " + String.join(" | ", problems));
				}
				Voxels v = PreviewScene.build(bp, spec, probe, 4, c);
				double base = switch (spec.facing()) {
					case SOUTH -> 0;
					case EAST -> 90;
					case NORTH -> 180;
					default -> 270;
				};
				List<BufferedImage> shots = new ArrayList<>();
				shots.add(renderer.render(v, new IsoRenderer.View(base + 45, 30), scale, 2, 0));
				shots.add(renderer.render(v, new IsoRenderer.View(base + 225, 30), scale, 2, 0));
				// side elevations, low: across the plot's depth
				shots.add(renderer.render(v, new IsoRenderer.View(base + 90 + 8, 12), scale, 2, 0));
				shots.add(renderer.render(v, new IsoRenderer.View(base - 90 + 8, 12), scale, 2, 0));
				String title = info.id() + "  " + c.getSerializedName() + " s" + s + "  " + spec.frontage() + "x" + spec.depth() + " " + spec.facing().getSerializedName()
					+ " tier " + spec.tier() + "  " + terrainArg + "  " + bp.totalSteps() + " steps";
				File f = new File(out, info.id().getPath().replace('/', '_') + "_" + c.getSerializedName() + "_s" + s + "_" + terrainArg + ".png");
				ImageIO.write(grid(shots, title), "png", f);
				System.out.println("  -> " + f.getPath());
			}
		}
	}

	private static BufferedImage grid(List<BufferedImage> shots, String title) {
		int pad = 10;
		int colW = 0;
		int rowH0 = 0;
		int rowH1 = 0;
		for (int i = 0; i < shots.size(); i++) {
			colW = Math.max(colW, shots.get(i).getWidth());
			if (i < 2) {
				rowH0 = Math.max(rowH0, shots.get(i).getHeight());
			} else {
				rowH1 = Math.max(rowH1, shots.get(i).getHeight());
			}
		}
		int w = colW * 2 + pad * 3;
		int h = 30 + rowH0 + rowH1 + pad * 3;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0xFFE9E4DA, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.drawString(title, pad, 20);
		for (int i = 0; i < shots.size(); i++) {
			BufferedImage s = shots.get(i);
			int x = pad + (i % 2) * (colW + pad);
			int y = 30 + (i < 2 ? 0 : rowH0 + pad);
			g.drawImage(s, x, y, null);
		}
		g.dispose();
		return img;
	}
}
