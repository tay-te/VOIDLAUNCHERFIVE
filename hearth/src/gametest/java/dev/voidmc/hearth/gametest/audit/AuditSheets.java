package dev.voidmc.hearth.gametest.audit;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.LightCheck;
import dev.voidmc.hearth.build.kit.light.LightRules;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.BlueprintPreview;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.NightLight;
import dev.voidmc.hearth.gametest.render.PreviewScene;
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
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Contact sheets for the visual audit, per archetype: {@code <name>.webp}, every culture from the front-right corner (seed 0,
 * flat land, or the archetype's own for waterside works, workings and the deep), and {@code <name>_cut.webp} for buildings
 * with rooms: per culture a row of storey cutaways by day and the main floor by night, its unmet light needs marked (red: a
 * room under 8, orange: a door, magenta: dark ground). Written as JPEG and converted to WebP with Pillow when python is
 * there ({@code -Daudit.python=python}); the JPEG stays when it is not.
 */
final class AuditSheets {
	private static final int BG = 0xFFE9E4DA;
	private static final int NIGHT = 0xFF10131C;
	private static final int MAX_FLOORS = 4;

	private AuditSheets() {
	}

	private record Shot(Culture culture, boolean suits, String label, BufferedImage front, List<BufferedImage> cuts, List<String> cutLabels,
		BufferedImage night, String nightLabel, boolean rooms) {
	}

	static void run(BlueprintServiceImpl service, List<StructureAudit.Entry> entries, Path out, ExecutorService pool) throws Exception {
		Files.createDirectories(out);
		Assets assets = new Assets();
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(assets));
		Path script = out.resolve(".towebp.py");
		Files.writeString(script, """
			import sys, os
			from PIL import Image
			src, dst = sys.argv[1], sys.argv[2]
			Image.open(src).save(dst, 'WEBP', quality=80, method=4)
			os.remove(src)
			""");
		long t0 = System.currentTimeMillis();
		long bytes = 0;
		int n = 0;
		for (StructureAudit.Entry e : entries) {
			String terrain = e.terrain().equals("hills") ? "flat" : e.terrain();
			List<Future<Shot>> fs = new ArrayList<>();
			for (Culture c : Culture.values()) {
				Style style = service.styles().get(StyleRegistry.styleId(c));
				if (style != null) {
					fs.add(pool.submit(() -> shoot(service, renderer, e, c, style, terrain)));
				}
			}
			List<Shot> shots = new ArrayList<>();
			for (Future<Shot> f : fs) {
				try {
					shots.add(f.get());
				} catch (java.util.concurrent.ExecutionException ex) {
					System.out.println("SHEET " + e.info().id().getPath() + ": " + ex.getCause());
				}
			}
			Path dir = out.resolve(e.category());
			Files.createDirectories(dir);
			String title = e.info().id() + "  on " + terrain + ", seed 1007 facing south  (" + e.group() + ")";
			bytes += save(cultures(shots, title), dir.resolve(e.name()), script);
			// the cutaways show the cultures that build the archetype (all of them when none is marked)
			List<Shot> built = shots.stream().filter(Shot::suits).toList();
			if (built.stream().anyMatch(Shot::rooms)) {
				bytes += save(cutaways(built, e.info().id() + "  storey cutaways by day, main floor by night (" + terrain + ", seed 1007)"),
					dir.resolve(e.name() + "_cut"), script);
			}
			n++;
			System.out.printf(Locale.ROOT, "[%d/%d] sheets %s  (%.0f MB so far, %.0f s)%n", n, entries.size(), e.info().id().getPath(), bytes / 1e6,
				(System.currentTimeMillis() - t0) / 1000.0);
		}
		Files.deleteIfExists(script);
		System.out.printf(Locale.ROOT, "audit sheets: %d archetypes, %.0f MB -> %s%n", n, bytes / 1e6, out.toAbsolutePath());
	}

	private static Shot shoot(BlueprintServiceImpl service, IsoRenderer renderer, StructureAudit.Entry e, Culture c, Style style, String terrain) {
		SyntheticTerrain probe = StructureAudit.probe(terrain, c);
		PlotSpec spec = BlueprintPreview.spec(e.info(), style, StructureAudit.seed(0), 0, probe, new CompoundTag());
		BlueprintImpl bp = service.compiled(spec, probe);
		Voxels world = PreviewScene.build(bp, spec, probe, 3, c);
		double yaw = switch (spec.facing()) {
			case SOUTH -> 45;
			case EAST -> 135;
			case NORTH -> 225;
			default -> 315;
		};
		int tall = Math.max(0, (bp.bounds().maxY() - bp.bounds().minY()) / 2);
		int px = Math.max(3, Math.min(24, 600 / (spec.sizeX() + spec.sizeZ() + 6 + tall)));
		BufferedImage front = renderer.render(world, new View(yaw, 30), px, 2, BG);
		boolean suits = e.info().suits(c);
		String label = c.getSerializedName() + "  " + spec.frontage() + "x" + spec.depth() + (suits ? "" : "  (not built in this culture)");

		// the floors: standing levels with rooms on them, as the night panels find them
		LightCheck.Result res = LightCheck.evaluate(bp, probe);
		TreeMap<Integer, Integer> levels = new TreeMap<>();
		int roomNeeds = 0;
		for (LightRules.Need nd : res.needs()) {
			if (nd.kind() == LightRules.Kind.ROOM) {
				levels.merge(nd.y(), 1, Integer::sum);
				roomNeeds++;
			}
		}
		List<Integer> floors = new ArrayList<>();
		for (var en : levels.entrySet()) {
			if (en.getValue() < 6) {
				continue;
			}
			if (!floors.isEmpty() && en.getKey() - floors.get(floors.size() - 1) <= 2) {
				if (en.getValue() > levels.get(floors.get(floors.size() - 1))) {
					floors.set(floors.size() - 1, en.getKey());
				}
				continue;
			}
			floors.add(en.getKey());
		}
		while (floors.size() > MAX_FLOORS) {
			floors.remove(floors.size() / 2);
		}
		List<BufferedImage> cuts = new ArrayList<>();
		List<String> cutLabels = new ArrayList<>();
		BufferedImage night = null;
		String nightLabel = "";
		int cpx = Math.max(3, Math.min(20, 470 / (spec.sizeX() + spec.sizeZ() + 2)));
		// the cutaways show the plot and a block round it
		Voxels inner = PreviewScene.build(bp, spec, probe, 1, c);
		if (roomNeeds >= 6 && !floors.isEmpty()) {
			int main = floors.get(0);
			for (int fl : floors) {
				if (levels.get(fl) > levels.get(main)) {
					main = fl;
				}
			}
			for (int k = 0; k < floors.size(); k++) {
				int fl = floors.get(k);
				Voxels cut = inner.cut(fl + 1, (x, y, z, s) -> false);
				cuts.add(renderer.render(cut, new View(yaw, 52), cpx, 2, BG));
				cutLabels.add("level " + (fl - spec.floorY()));
			}
			int next = Integer.MAX_VALUE;
			for (int fl : floors) {
				if (fl > main) {
					next = Math.min(next, fl);
				}
			}
			int cap = Math.min(next - 1, main + 7);
			int floor = main;
			Voxels cut = inner.cut(main + 1, (x, y, z, st) -> y <= cap && (st.getLightEmission() > 0
				&& !(st.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock) || st.getBlock() instanceof ChainBlock
				|| st.getBlock() instanceof FenceBlock && hangsLight(inner, x, y, z)));
			int dark = 0;
			for (LightRules.Need nd : res.unmet()) {
				if (nd.y() < floor - 2 || nd.y() >= Math.min(next - 2, floor + 4)) {
					continue;
				}
				BlockState mark = switch (nd.kind()) {
					case ROOM -> Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState();
					case DOOR -> Blocks.CONCRETE.pick(DyeColor.ORANGE).defaultBlockState();
					default -> Blocks.CONCRETE.pick(DyeColor.MAGENTA).defaultBlockState();
				};
				cut.set(nd.x(), nd.y() - 1, nd.z(), mark);
				dark++;
			}
			night = renderer.render(cut, new View(yaw, 52), cpx, 2, NIGHT, NightLight.of(inner));
			nightLabel = "night, level " + (main - spec.floorY()) + (dark > 0 ? "  (" + dark + " dark)" : "");
		}
		return new Shot(c, suits, label, front, cuts, cutLabels, night, nightLabel, roomNeeds >= 6);
	}

	private static boolean hangsLight(Voxels w, int x, int y, int z) {
		for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			BlockState below = w.get(x + o[0], y - 1, z + o[1]);
			if (below != null && below.getLightEmission() > 0 && (o[0] == 0 && o[1] == 0 || below.getBlock() instanceof LanternBlock)) {
				return true;
			}
		}
		return false;
	}

	/** Every culture's front view, seven to a row. */
	private static BufferedImage cultures(List<Shot> shots, String title) {
		int cols = 7;
		int cw = 440;
		int ch = 400;
		int pad = 8;
		int lh = 18;
		int rows = (shots.size() + cols - 1) / cols;
		BufferedImage img = new BufferedImage(cols * (cw + pad) + pad, 34 + rows * (ch + lh + pad) + pad, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = start(img, BG, title);
		for (int i = 0; i < shots.size(); i++) {
			Shot s = shots.get(i);
			int x = pad + (i % cols) * (cw + pad);
			int y = 34 + (i / cols) * (ch + lh + pad);
			g.setColor(s.suits() ? new Color(0x3A3226) : new Color(0x9A9080));
			g.drawString(s.label(), x, y + 13);
			fit(g, s.front(), x, y + lh, cw, ch, true);
		}
		g.dispose();
		return img;
	}

	/** A row per culture: its storey cutaways by day, then the main floor by night. */
	private static BufferedImage cutaways(List<Shot> shots, String title) {
		int floors = 1;
		for (Shot s : shots) {
			floors = Math.max(floors, s.cuts().size());
		}
		int cols = floors + 1;
		int cw = 330;
		int ch = 270;
		int pad = 8;
		int lh = 16;
		BufferedImage img = new BufferedImage(cols * (cw + pad) + pad, 34 + shots.size() * (ch + lh + pad) + pad, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = start(img, BG, title);
		for (int r = 0; r < shots.size(); r++) {
			Shot s = shots.get(r);
			int y = 34 + r * (ch + lh + pad);
			for (int k = 0; k < s.cuts().size(); k++) {
				int x = pad + k * (cw + pad);
				g.setColor(s.suits() ? new Color(0x3A3226) : new Color(0x9A9080));
				g.drawString(s.culture().getSerializedName() + "  " + s.cutLabels().get(k), x, y + 12);
				fit(g, s.cuts().get(k), x, y + lh, cw, ch, false);
			}
			if (s.cuts().isEmpty()) {
				g.setColor(new Color(0x9A9080));
				g.drawString(s.culture().getSerializedName() + "  no rooms", pad, y + 12);
			}
			if (s.night() != null) {
				int x = pad + floors * (cw + pad);
				g.setColor(new Color(NIGHT));
				g.fillRect(x, y + lh, cw, ch);
				g.setColor(new Color(0x3A3226));
				g.drawString(s.culture().getSerializedName() + "  " + s.nightLabel(), x, y + 12);
				fit(g, s.night(), x, y + lh, cw, ch, false);
			}
		}
		g.dispose();
		return img;
	}

	private static Graphics2D start(BufferedImage img, int bg, String title) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(bg));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, 8, 22);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		return g;
	}

	private static void fit(Graphics2D g, BufferedImage im, int x, int y, int w, int h, boolean bottom) {
		double sc = Math.min(1.0, Math.min((double) w / im.getWidth(), (double) h / im.getHeight()));
		int iw = (int) (im.getWidth() * sc);
		int ih = (int) (im.getHeight() * sc);
		g.drawImage(im, x + (w - iw) / 2, bottom ? y + h - ih : y + (h - ih) / 2, iw, ih, null);
	}

	/** Writes a JPEG and converts it to WebP (deleting the JPEG); returns the bytes left on disk. */
	private static long save(BufferedImage img, Path base, Path script) throws IOException, InterruptedException {
		Path jpg = base.resolveSibling(base.getFileName() + ".jpg");
		Path webp = base.resolveSibling(base.getFileName() + ".webp");
		ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam p = w.getDefaultWriteParam();
		p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		p.setCompressionQuality(0.93f);
		Files.deleteIfExists(jpg);
		try (ImageOutputStream os = ImageIO.createImageOutputStream(jpg.toFile())) {
			w.setOutput(os);
			w.write(null, new IIOImage(img, null, null), p);
		} finally {
			w.dispose();
		}
		String python = System.getProperty("audit.python", "python");
		if (!python.isEmpty()) {
			Process pr = new ProcessBuilder(python, script.toString(), jpg.toString(), webp.toString()).redirectErrorStream(true)
				.redirectOutput(new File(System.getProperty("java.io.tmpdir"), "audit_webp.log")).start();
			if (pr.waitFor(120, TimeUnit.SECONDS) && pr.exitValue() == 0 && Files.exists(webp)) {
				return Files.size(webp);
			}
			pr.destroyForcibly();
		}
		return Files.size(jpg);
	}

	static {
		// keep Direction loaded for the switch above on worker threads
		Direction.values();
	}
}
