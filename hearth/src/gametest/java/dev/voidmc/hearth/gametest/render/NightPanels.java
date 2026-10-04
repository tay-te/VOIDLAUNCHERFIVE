package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.LightCheck;
import dev.voidmc.hearth.build.kit.light.LightRules;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The night panel of a building: its two corners by night, then per storey a plan and a cutaway by night with the light
 * check's unmet needs marked on the floor (red: a room under 8, orange: an entrance under 8, magenta: dark ground or a
 * dark pocket under the roofs). Written as {@code <culture>_s<n>_<terrain>_night.png} by {@code -Pterrain="...;night=true"}.
 */
public final class NightPanels {
	public static final int BG = 0xFF10131C;

	private NightPanels() {
	}

	public static void write(IsoRenderer r, Voxels world, BlueprintImpl bp, PlotSpec spec, TerrainProbe probe, double yaw, int storeyHeight, Path file,
		String title) throws IOException {
		Files.createDirectories(file.getParent());
		NightLight light = NightLight.of(world);
		LightCheck.Result res = LightCheck.evaluate(bp, probe);
		BufferedImage front = r.render(world, new View(yaw, 30), 16, 2, BG, light);
		BufferedImage back = r.render(world, new View(yaw + 180, 30), 16, 2, BG, light);
		List<BufferedImage> cuts = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		// the floors: standing levels with rooms on them (or the doors' level), merged within two blocks (stairs)
		java.util.TreeMap<Integer, Integer> levels = new java.util.TreeMap<>();
		for (LightRules.Need n : res.needs()) {
			if (n.kind() != LightRules.Kind.STREET) {
				levels.merge(n.y(), n.kind() == LightRules.Kind.ROOM ? 3 : 1, Integer::sum);
			}
		}
		List<Integer> floors = new ArrayList<>();
		for (var e : levels.entrySet()) {
			if (e.getValue() < 6) {
				continue;
			}
			if (!floors.isEmpty() && e.getKey() - floors.get(floors.size() - 1) <= 2) {
				if (e.getValue() > levels.get(floors.get(floors.size() - 1))) {
					floors.set(floors.size() - 1, e.getKey());
				}
				continue;
			}
			floors.add(e.getKey());
		}
		if (floors.isEmpty()) {
			floors.add(spec.floorY());
		}
		while (floors.size() > 5) {
			floors.remove(floors.size() / 2);
		}
		for (int k = 0; k < floors.size(); k++) {
			int floor = floors.get(k);
			int next = k + 1 < floors.size() ? floors.get(k + 1) : Integer.MAX_VALUE;
			int ceilingCap = Math.min(next - 1, floor + 7);
			// the floor's walls to head height, and its lights hung above (lanterns, chains, the brackets and wheels they hang from)
			Voxels c = world.cut(floor + 1, (x, y, z, st) -> y <= ceilingCap && (st.getLightEmission() > 0
				&& !(st.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock)
				|| st.getBlock() instanceof net.minecraft.world.level.block.ChainBlock
				|| st.getBlock() instanceof net.minecraft.world.level.block.FenceBlock && hangsLight(world, x, y, z)));
			// hidden light blocks shown as lime markers
			for (var e : new ArrayList<>(c.all().long2ObjectEntrySet())) {
				if (e.getValue().is(Blocks.LIGHT)) {
					long key = e.getLongKey();
					c.set(Voxels.kx(key), Voxels.ky(key), Voxels.kz(key), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIME).defaultBlockState());
				}
			}
			int marked = 0;
			for (LightRules.Need n : res.unmet()) {
				if (n.y() < floor - 2 || n.y() >= Math.min(next - 2, floor + 4)) {
					continue;
				}
				BlockState m = switch (n.kind()) {
					case ROOM -> Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState();
					case DOOR -> Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.ORANGE).defaultBlockState();
					default -> Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.MAGENTA).defaultBlockState();
				};
				c.set(n.x(), n.y() - 1, n.z(), m);
				marked++;
			}
			cuts.add(r.render(c, View.TOP, 14, 2, BG, light));
			BufferedImage iso = r.render(c, new View(yaw, 40), 16, 2, BG, light);
			cuts.add(iso);
			String base = file.getFileName().toString().replace(".png", "");
			ImageIO.write(r.render(c, new View(yaw, 58), 30, 2, BG, light), "png", file.resolveSibling(base + "_L" + (floor - spec.floorY()) + ".png").toFile());
			String name = "level " + (floor - spec.floorY());
			labels.add(name + " plan" + (marked > 0 ? " (" + marked + " dark)" : ""));
			labels.add(name + " cut");
			cuts.add(levels(res, spec, floor, next));
			labels.add(name + " light levels");
		}
		Map<LightRules.Kind, Integer> by = new EnumMap<>(LightRules.Kind.class);
		for (LightRules.Need n : res.unmet()) {
			by.merge(n.kind(), 1, Integer::sum);
		}
		String status = (res.unmet().isEmpty() ? "light check: all " + res.needs().size() + " cells lit" : "light check: unmet " + by) + "   fixtures: "
			+ fixtures(bp) + "   (lime: hidden light blocks; red/orange/magenta: dark room/door/ground cells)";
		Files.createDirectories(file.getParent());
		ImageIO.write(panel(front, back, cuts, labels, title, status), "png", file.toFile());
	}

	/**
	 * The light-level map of one floor, front (the street) at the bottom: per column of the plot the level at its
	 * standing cell (the one nearest to failing the rule in the floor's band). Rooms are full squares, yellow at 12 and
	 * up, amber 8 to 11; the ground, porches and pockets smaller squares, pale at 8 and up, slate below; entrances
	 * ringed in orange; anything under its need red (a room or a door) or magenta (never-dark ground).
	 */
	static BufferedImage levels(LightCheck.Result res, PlotSpec spec, int floor, int next) {
		dev.voidmc.hearth.build.blueprint.LocalFrame f = new dev.voidmc.hearth.build.blueprint.LocalFrame(spec);
		int cell = 18;
		int pad = 4;
		int fw = f.frontage();
		int fd = f.depth();
		java.util.Map<Long, LightRules.Need> show = new java.util.HashMap<>();
		int lo = floor - 2;
		int hi = Math.min(next - 2, floor + 4);
		for (LightRules.Need n : res.needs()) {
			if (n.y() < lo || n.y() >= hi || n.kind() == LightRules.Kind.STREET) {
				continue;
			}
			int lx = f.localX(n.x(), n.z());
			int lz = f.localZ(n.x(), n.z());
			long key = net.minecraft.world.level.ChunkPos.pack(lx, lz);
			LightRules.Need was = show.get(key);
			if (was == null || res.light().at(n.x(), n.y(), n.z()) - n.min() < res.light().at(was.x(), was.y(), was.z()) - was.min()) {
				show.put(key, n);
			}
		}
		int gx0 = -1;
		int gz0 = -2;
		int gw = fw + 2;
		int gd = fd + 3;
		BufferedImage img = new BufferedImage(gw * cell + pad * 2, gd * cell + pad * 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.setColor(new Color(0x2A2F3C));
		g.drawRect(pad + (0 - gx0) * cell, pad + (gd - 1 - (fd - 1 - gz0)) * cell, fw * cell, fd * cell);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		for (int lz = gz0; lz < gz0 + gd; lz++) {
			for (int lx = gx0; lx < gx0 + gw; lx++) {
				int px = pad + (lx - gx0) * cell;
				// front (local z = -1, the street) at the bottom; the plot's left as seen from the street at the left
				int pz = pad + (gd - 1 - (lz - gz0)) * cell;
				int wx = f.worldX(lx, lz);
				int wz = f.worldZ(lx, lz);
				LightRules.Need n = show.get(net.minecraft.world.level.ChunkPos.pack(lx, lz));
				if (n == null) {
					// a wall at head height, or nothing
					var st = res.grid().contains(wx, floor + 1, wz) ? res.grid().at(wx, floor + 1, wz) : null;
					if (st != null && !st.isAir() && res.grid().placed(wx, floor + 1, wz) && !dev.voidmc.hearth.build.kit.light.Spaces.passable(st)) {
						g.setColor(new Color(0x3A3F4C));
						g.fillRect(px + 1, pz + 1, cell - 2, cell - 2);
					}
					continue;
				}
				int l = res.light().at(n.x(), n.y(), n.z());
				boolean inside = n.kind() == LightRules.Kind.ROOM || n.kind() == LightRules.Kind.DOOR;
				// rooms and doors warm (yellow 12+, amber 8+), the ground and pockets cool (pale 8+, slate 1+); unmet red or magenta
				Color col = inside ? (l >= 12 ? new Color(0xF2C14E) : new Color(0xD98E32)) : l >= 8 ? new Color(0x9DB0CC) : new Color(0x5C6B86);
				if (l < n.min()) {
					col = inside ? new Color(0xE53935) : new Color(0xC2185B);
				}
				int inset = n.kind() == LightRules.Kind.ROOM ? 1 : 3;
				g.setColor(col);
				g.fillRect(px + inset, pz + inset, cell - inset * 2, cell - inset * 2);
				if (n.kind() == LightRules.Kind.DOOR) {
					g.setColor(new Color(0xFF8F00));
					g.drawRect(px, pz, cell - 1, cell - 1);
				}
				g.setColor(new Color(0x1A1A1A));
				String t = Integer.toString(l);
				g.drawString(t, px + (cell - g.getFontMetrics().stringWidth(t)) / 2, pz + cell / 2 + 4);
			}
		}
		g.dispose();
		return img;
	}

	/** A bracket or a wheel's arm: a fence with a lantern under it, or beside one that has. */
	private static boolean hangsLight(Voxels w, int x, int y, int z) {
		for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			BlockState below = w.get(x + o[0], y - 1, z + o[1]);
			if (below != null && below.getLightEmission() > 0 && (o[0] == 0 && o[1] == 0 || below.getBlock() instanceof net.minecraft.world.level.block.LanternBlock)) {
				return true;
			}
		}
		return false;
	}

	/** What gives light in a blueprint, counted by block. */
	static String fixtures(BlueprintImpl bp) {
		java.util.TreeMap<String, Integer> n = new java.util.TreeMap<>();
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				BlockState s = bp.state(st, i);
				if (s.getLightEmission() > 0) {
					String key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
					if (s.is(Blocks.LIGHT)) {
						key = "light" + s.getValue(net.minecraft.world.level.block.LightBlock.LEVEL);
					}
					n.merge(key, 1, Integer::sum);
				}
			}
		}
		return n.toString();
	}

	private static BufferedImage panel(BufferedImage a, BufferedImage b, List<BufferedImage> cuts, List<String> labels, String title, String status) {
		int pad = 12;
		int maxW = Math.max(1800, a.getWidth() + b.getWidth() + pad * 3);
		// lay the cuts out in rows no wider than maxW
		List<int[]> pos = new ArrayList<>();
		int x = pad;
		int rowTop = 0;
		int rowH = 0;
		int widest = 0;
		for (BufferedImage c : cuts) {
			if (x + c.getWidth() + pad > maxW && x > pad) {
				x = pad;
				rowTop += rowH + 22 + pad;
				rowH = 0;
			}
			pos.add(new int[]{x, rowTop});
			x += c.getWidth() + pad;
			widest = Math.max(widest, x);
			rowH = Math.max(rowH, c.getHeight());
		}
		int row1h = Math.max(a.getHeight(), b.getHeight());
		int w = Math.max(a.getWidth() + b.getWidth() + pad * 3, widest);
		int top = 52 + row1h + pad;
		int h = top + (cuts.isEmpty() ? 0 : rowTop + rowH + 22 + pad);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0xE8DCC0));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 22);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(status, pad, 40);
		g.drawImage(a, pad, 52 + row1h - a.getHeight(), null);
		g.drawImage(b, pad * 2 + a.getWidth(), 52 + row1h - b.getHeight(), null);
		for (int i = 0; i < cuts.size(); i++) {
			int[] p = pos.get(i);
			g.drawString(labels.get(i), p[0], top + p[1] + 12);
			g.drawImage(cuts.get(i), p[0], top + p[1] + 18, null);
		}
		g.dispose();
		return img;
	}
}
