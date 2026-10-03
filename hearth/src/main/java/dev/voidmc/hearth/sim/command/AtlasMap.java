package dev.voidmc.hearth.sim.command;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.PlotColumns;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * A map of the settlement atlas round a point, drawn from the terrain probe and the region plans without generating a
 * chunk: shaded ground and water, the 1024-block region grid, every historic site (ringed in its culture's colour, named,
 * with its age), the plots of its history, and live settlements on top (built plots solid, planned ones outlined).
 */
public final class AtlasMap {
	private static final Map<Culture, Integer> COLOURS = new EnumMap<>(Culture.class);

	static {
		int[] c = {0xE8C060, 0xC596F0, 0x8E3B2A, 0x7FD060, 0xBFE3F0, 0x9A6FB0, 0x4A7AC0, 0x30C0A0, 0xF0D890, 0xF07850, 0xE0B040, 0x3FBF8F,
			0x5E8A3C, 0xD06A2A};
		for (Culture culture : Culture.values()) {
			COLOURS.put(culture, c[culture.ordinal() % c.length]);
		}
	}

	/** A live settlement, copied on the server thread so drawing never touches the live record. */
	public record LiveTown(String name, Culture culture, int cx, int cz, int population, List<PlotSpec> built, List<PlotSpec> planned) {
		public static LiveTown of(SimSettlement s) {
			List<PlotSpec> built = new ArrayList<>();
			List<PlotSpec> planned = new ArrayList<>();
			for (SimPlot p : s.plots()) {
				(p.state() == PlotState.BUILT ? built : planned).add(p.spec());
			}
			return new LiveTown(s.name(), s.culture(), s.center().getX(), s.center().getZ(), s.population(), built, planned);
		}
	}

	public record Result(Path image, int regions, int sites, long millis) {
	}

	private AtlasMap() {
	}

	public static Result render(Atlas atlas, TerrainProbe t, Path dir, int centreX, int centreZ, int radius, int step, List<LiveTown> live)
		throws IOException {
		long start = System.nanoTime();
		int size = Math.max(16, radius * 2 / step);
		int x0 = centreX - radius;
		int z0 = centreZ - radius;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		int[][] h = new int[size][size];
		for (int px = 0; px < size; px++) {
			for (int pz = 0; pz < size; pz++) {
				h[px][pz] = t.groundY(x0 + px * step, z0 + pz * step);
			}
		}
		for (int px = 0; px < size; px++) {
			for (int pz = 0; pz < size; pz++) {
				int x = x0 + px * step;
				int z = z0 + pz * step;
				int g = h[px][pz];
				int rgb;
				if (t.isWater(x, z)) {
					int depth = t.waterY(x, z) - g;
					rgb = scale(0x3A6AD0, 1.0F - Math.min(0.5F, depth / 30.0F));
				} else {
					float hw = h[Math.max(0, px - 1)][Math.max(0, pz - 1)];
					float shade = 1.0F + Math.max(-0.4F, Math.min(0.4F, (g - hw) / step * 0.6F));
					int base = g > t.seaLevel() + 110 ? 0xB0B0A8 : g > t.seaLevel() + 50 ? 0x7A9A5A : t.temperature(x, z) < 0.15F ? 0xE8EEF0 : 0x6FA050;
					rgb = scale(base, shade);
				}
				img.setRGB(px, pz, rgb);
			}
		}
		Graphics2D gfx = img.createGraphics();
		gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		gfx.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, size / 70)));
		int regions = 0;
		int sites = 0;
		int r0x = Atlas.region(x0);
		int r0z = Atlas.region(z0);
		int r1x = Atlas.region(x0 + size * step);
		int r1z = Atlas.region(z0 + size * step);
		List<Runnable> labels = new ArrayList<>();
		List<LiveTown> liveTowns = live;
		for (int rx = r0x; rx <= r1x; rx++) {
			for (int rz = r0z; rz <= r1z; rz++) {
				regions++;
				RegionPlan plan = atlas.plan(rx, rz);
				gfx.setColor(new Color(255, 255, 255, 60));
				int gx = ((rx << Atlas.REGION_BITS) - x0) / step;
				int gz = ((rz << Atlas.REGION_BITS) - z0) / step;
				gfx.drawRect(gx, gz, Atlas.REGION_SIZE / step, Atlas.REGION_SIZE / step);
				RegionPlan.Site site = plan.site();
				if (site == null) {
					continue;
				}
				sites++;
				int colour = COLOURS.get(site.culture());
				for (RegionPlan.HistoricPlot p : plan.plots()) {
					fill(img, p.spec(), x0, z0, step, p.completed() >= p.stageCount() ? colour : scale(colour, 0.6F));
				}
				int sx = (site.center().getX() - x0) / step;
				int sz = (site.center().getZ() - z0) / step;
				gfx.setColor(new Color(colour));
				gfx.setStroke(new BasicStroke(2));
				int ring = Math.max(6, 160 / step);
				gfx.drawOval(sx - ring, sz - ring, ring * 2, ring * 2);
				String label = site.name() + " (" + site.culture().getSerializedName() + ", " + site.age() + "d"
					+ (plan.history() == null ? "" : ", " + plan.history().population()) + ")";
				boolean adopted = false;
				for (LiveTown town : liveTowns) {
					adopted |= town.name().equals(site.name()) && town.cx() == site.center().getX() && town.cz() == site.center().getZ();
				}
				if (!adopted) {
					// adopted towns are labelled with their live figures below
					labels.add(() -> text(gfx, label, sx + ring + 2, sz + 4));
				}
			}
		}
		for (LiveTown town : live) {
			int colour = COLOURS.get(town.culture());
			for (PlotSpec s : town.built()) {
				fill(img, s, x0, z0, step, 0xFFFFFF);
			}
			for (PlotSpec s : town.planned()) {
				fill(img, s, x0, z0, step, scale(colour, 1.3F));
			}
			int sx = (town.cx() - x0) / step;
			int sz = (town.cz() - z0) / step;
			labels.add(() -> text(gfx, town.name() + " (" + town.culture().getSerializedName() + ", " + town.population() + " souls)", sx + 10, sz + 4));
		}
		labels.forEach(Runnable::run);
		gfx.dispose();
		Files.createDirectories(dir);
		Path file = dir.resolve("hearth_atlas_" + centreX + "_" + centreZ + "_r" + radius + ".png");
		ImageIO.write(img, "png", file.toFile());
		return new Result(file, regions, sites, (System.nanoTime() - start) / 1_000_000);
	}

	private static void text(Graphics2D g, String s, int x, int y) {
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(Color.WHITE);
		g.drawString(s, x, y);
	}

	/** Marks every pixel a column of the plot falls in. */
	private static void fill(BufferedImage img, PlotSpec spec, int x0, int z0, int step, int rgb) {
		PlotColumns.forEach(spec, 0, (x, z) -> {
			int px = Math.floorDiv(x - x0, step);
			int pz = Math.floorDiv(z - z0, step);
			if (px >= 0 && pz >= 0 && px < img.getWidth() && pz < img.getHeight()) {
				img.setRGB(px, pz, rgb);
			}
		});
	}

	private static int scale(int rgb, float f) {
		int r = Math.min(255, Math.max(0, Math.round(((rgb >> 16) & 0xFF) * f)));
		int g = Math.min(255, Math.max(0, Math.round(((rgb >> 8) & 0xFF) * f)));
		int b = Math.min(255, Math.max(0, Math.round((rgb & 0xFF) * f)));
		return r << 16 | g << 8 | b;
	}
}
