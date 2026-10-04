package dev.voidmc.hearth.gametest.plan;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.road.Polyline;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/**
 * The top-down plan: the land hill-shaded with contour lines and water by depth; roads by kind (stairs striped,
 * bridges in timber, walls in stone); plots filled by category with their footprints outlined — solid when built,
 * pale and dashed while planned or under way; terraces hatched; labels on the buildings that make the place (the
 * chapel, the inn, the hall, the market, the landmark, the builders' yard); district names; a title and a legend.
 * In block mode the fill is the real blueprints' top blocks in their map colours.
 */
public final class PlanRenderer {
	private final Scene scene;
	private final int s;
	private final int header = 34;
	private final int footer = 62;

	public PlanRenderer(Scene scene, int maxSize) {
		this.scene = scene;
		this.s = Math.max(2, Math.min(8, maxSize / Math.max(scene.w, scene.d)));
	}

	public BufferedImage render(Settlement settlement, PlanState state, Catalogue catalogue, String title, boolean blocks) {
		Scene sc = this.scene;
		int width = sc.w * this.s;
		int height = sc.d * this.s + this.header + this.footer;
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		// land
		for (int z = 0; z < sc.d; z++) {
			for (int x = 0; x < sc.w; x++) {
				int rgb = landColor(x, z);
				if (blocks) {
					rgb = topBlock(x, z, rgb);
				}
				fillCell(img, x, z, rgb);
			}
		}
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		List<PlotView> plots = new ArrayList<>(settlement.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		// what the plan holds for later: the square and kept lots (hatched), planned streets (dotted)
		for (dev.voidmc.hearth.plan.model.Reservation r : state.reservations) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				for (int x = r.x0(); x <= r.x1(); x++) {
					if (((x + z) & 3) == 0 || x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1()) {
						cell(img, x, z, r.tag().equals("square") ? 0xD8D2C2 : 0xB8B0D0);
					}
				}
			}
		}
		for (dev.voidmc.hearth.plan.model.RoadEdge e : state.graph.edges()) {
			Polyline.Dense dd = e.dense();
			boolean wall = e.role == dev.voidmc.hearth.plan.model.RoadEdge.Role.WALL_LINE;
			if (!e.emits() && !wall) {
				continue;
			}
			for (int i = wall ? 0 : Math.max(0, e.built); i < dd.length(); i += 2) {
				cell(img, dd.xs()[i], dd.zs()[i], wall ? 0x8A8478 : 0xE8E0C8);
			}
		}
		drawSites(g, settlement);
		if (!blocks) {
			// ground layer: terraces, farms, plazas, roads; then buildings
			for (PlotView p : plots) {
				drawGround(img, g, p, catalogue);
			}
			for (PlotView p : plots) {
				drawRoad(img, p);
			}
			for (PlotView p : plots) {
				drawBuilding(img, g, p, catalogue);
			}
			for (PlotView p : plots) {
				if (p.spec().archetype().equals(Archetypes.WALKWAY)) {
					drawRoad(img, p);
				}
			}
		} else {
			g.setColor(new Color(0, 0, 0, 90));
			for (PlotView p : plots) {
				ArchetypeInfo info = catalogue.info(p.spec().archetype());
				if (info != null && info.category() != ArchetypeCategory.INFRASTRUCTURE && !Archetypes.isLinear(p.spec().archetype())) {
					outline(g, p.spec(), p.state() == PlotState.BUILT);
					// who lives there: a station tab in the middle of each home's roof
					dev.voidmc.hearth.api.Station st = dev.voidmc.hearth.plan.society.Stations.home(p.spec(), info);
					if (st != null) {
						PlotSpec sp = p.spec();
						int cx = (sp.origin().getX() + sp.sizeX() / 2 - this.scene.x0) * this.s;
						int cz = (sp.origin().getZ() + sp.sizeZ() / 2 - this.scene.z0) * this.s + this.header;
						int r = Math.max(3, this.s * 2);
						g.setColor(new Color(Palette.station(st)));
						g.fillRect(cx - r, cz - r, 2 * r, 2 * r);
						g.setColor(new Color(0x2A2018));
						g.drawRect(cx - r, cz - r, 2 * r, 2 * r);
					}
				}
			}
		}
		// district names
		g.setFont(new Font(Font.SERIF, Font.ITALIC, 12));
		for (District d : state.districts) {
			if (d.kind() == District.Kind.FARMS || d.kind() == District.Kind.MARKET || !sc.inside(d.x(), d.z())
				|| Math.hypot(d.x() - state.heartX, d.z() - state.heartZ) < state.plazaHalf + 14) {
				continue;
			}
			label(g, d.kind().getSerializedName().toUpperCase(), d.x(), d.z(), new Color(0x4A3A2A), new Color(255, 255, 255, 120));
		}
		// labels on the buildings that make the place
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, Math.min(13, 8 + this.s))));
		for (PlotView p : plots) {
			ArchetypeInfo info = catalogue.info(p.spec().archetype());
			if (info != null && Palette.labelled(info)) {
				PlotSpec sp = p.spec();
				String name = name(info);
				label(g, name, sp.origin().getX() + sp.sizeX() / 2, sp.origin().getZ() + sp.sizeZ() / 2, Color.WHITE, new Color(20, 16, 12, 170));
			}
		}
		// header and legend
		g.setColor(new Color(0xF2EDE2));
		g.fillRect(0, 0, width, this.header);
		g.fillRect(0, height - this.footer, width, this.footer);
		g.setColor(new Color(0x2A2018));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.drawString(title, 10, 22);
		legend(g, height - this.footer + 6, width);
		scaleBar(g, width - 140, height - this.footer + 40);
		g.dispose();
		return img;
	}

	private int landColor(int x, int z) {
		Scene sc = this.scene;
		int i = z * sc.w + x;
		int h = sc.ground[i];
		int w = sc.water[i];
		if (w != TerrainProbe.NO_WATER && w > h) {
			return Palette.lerp(Palette.WATER_SHALLOW, Palette.WATER_DEEP, Math.min(1, (w - h) / 8.0));
		}
		int hx = sc.ground[z * sc.w + Math.min(sc.w - 1, x + 1)] - sc.ground[z * sc.w + Math.max(0, x - 1)];
		int hz = sc.ground[Math.min(sc.d - 1, z + 1) * sc.w + x] - sc.ground[Math.max(0, z - 1) * sc.w + x];
		// lit from the north-west
		double shade = 1.0 + (-hx - hz) * 0.07;
		shade = Math.max(0.6, Math.min(1.3, shade));
		int rgb = Palette.shade(Palette.land(h, sc.sea, sc.temperature[i]), shade);
		boolean contour = Math.floorDiv(h, 4) != Math.floorDiv(sc.ground[z * sc.w + Math.min(sc.w - 1, x + 1)], 4)
			|| Math.floorDiv(h, 4) != Math.floorDiv(sc.ground[Math.min(sc.d - 1, z + 1) * sc.w + x], 4);
		return contour ? Palette.shade(rgb, 0.86) : rgb;
	}

	private int topBlock(int x, int z, int land) {
		Scene sc = this.scene;
		int i = z * sc.w + x;
		LongArrayList b = sc.blocks[i];
		if (b != null && !b.isEmpty()) {
			long best = Long.MIN_VALUE;
			for (int k = 0; k < b.size(); k++) {
				best = Math.max(best, b.getLong(k));
			}
			int y = (int) (best >>> 24) - 1024;
			double shade = 0.85 + Math.min(0.3, (y - sc.ground[i]) * 0.02);
			return Palette.shade((int) (best & 0xFFFFFF), shade);
		}
		return sc.surface[i] != 0 ? sc.surface[i] : land;
	}

	private void fillCell(BufferedImage img, int x, int z, int rgb) {
		int px = x * this.s;
		int py = z * this.s + this.header;
		for (int dy = 0; dy < this.s; dy++) {
			for (int dx = 0; dx < this.s; dx++) {
				img.setRGB(px + dx, py + dy, rgb);
			}
		}
	}

	private void cell(BufferedImage img, int wx, int wz, int rgb) {
		if (this.scene.inside(wx, wz)) {
			fillCell(img, wx - this.scene.x0, wz - this.scene.z0, rgb);
		}
	}

	private void drawRoad(BufferedImage img, PlotView p) {
		PlotSpec sp = p.spec();
		var id = sp.archetype();
		if (!Archetypes.isLinear(id) && !id.equals(Archetypes.WALKWAY)) {
			return;
		}
		CompoundTag params = sp.params();
		int[] pts = params.getIntArray("points").orElse(null);
		if (pts == null) {
			return;
		}
		boolean planned = p.state() == PlotState.PLANNED;
		int rgb = Palette.road(params.getStringOr("kind", ""), id);
		if (planned) {
			rgb = Palette.lerp(rgb, 0xFFFFFF, 0.45);
		}
		Polyline.Dense dense = new Polyline(pts).dense();
		boolean stairs = id.equals(Archetypes.STAIRS);
		boolean walkway = id.equals(Archetypes.WALKWAY);
		int width = params.getIntOr("width", 1);
		int base = rgb;
		Polyline.corridor(pts, width, (x, z) -> {
			int c = base;
			if (stairs) {
				int k = dense.nearest(x, z);
				c = (k & 1) == 0 ? base : Palette.shade(base, 0.72);
			}
			if (walkway && ((x + z) & 1) == 0) {
				return;
			}
			if (id.equals(Archetypes.BRIDGE) || id.equals(Archetypes.DOCK)) {
				int k = dense.nearest(x, z);
				long d2 = dense.distance2(k, x, z);
				if (d2 * 4 >= (long) (width - 1) * (width - 1)) {
					c = Palette.shade(base, 0.7);
				}
			}
			cell(img, x, z, c);
		});
	}

	private void drawGround(BufferedImage img, Graphics2D g, PlotView p, Catalogue catalogue) {
		PlotSpec sp = p.spec();
		var id = sp.archetype();
		ArchetypeInfo info = catalogue.info(id);
		if (info == null) {
			return;
		}
		boolean planned = p.state() != PlotState.BUILT;
		int x0 = sp.origin().getX();
		int z0 = sp.origin().getZ();
		int x1 = x0 + sp.sizeX() - 1;
		int z1 = z0 + sp.sizeZ() - 1;
		if (id.equals(Archetypes.TERRACE)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					cell(img, x, z, edge ? Palette.TERRACE_EDGE : ((x - z) & 3) == 0 ? Palette.shade(Palette.TERRACE, 0.9) : Palette.TERRACE);
				}
			}
			return;
		}
		if (info.category() == ArchetypeCategory.EXTRACTION) {
			drawWorking(img, p, info);
			return;
		}
		if (info.category() != ArchetypeCategory.FARM && !id.equals(Archetypes.PLAZA) && !id.equals(Archetypes.PARK) && !id.equals(Archetypes.GARDEN)
			&& !id.equals(Archetypes.FESTIVAL_GROUND) && !id.equals(Archetypes.CEMETERY)) {
			return;
		}
		int roof = Palette.roof(id, info);
		if (id.equals(Archetypes.FESTIVAL_GROUND)) {
			roof = 0xA8C878;
		} else if (id.equals(Archetypes.CEMETERY)) {
			roof = 0x6E8A5E;
		}
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int c = roof;
				if (info.category() == ArchetypeCategory.FARM) {
					boolean rows = sp.sizeX() >= sp.sizeZ() ? (z - z0) % 2 == 1 : (x - x0) % 2 == 1;
					c = rows ? Palette.shade(roof, 0.84) : roof;
					if (id.equals(Archetypes.ORCHARD)) {
						c = (x - x0) % 3 == 1 && (z - z0) % 3 == 1 ? 0x2E5A22 : 0x7AA858;
					}
					if (x == x0 || x == x1 || z == z0 || z == z1) {
						c = 0x7A6A4A;
					}
				} else if (id.equals(Archetypes.PLAZA)) {
					c = ((x + z) & 1) == 0 ? roof : Palette.shade(roof, 0.95);
				} else if (id.equals(Archetypes.GARDEN) && ((x * 7 + z * 3) % 5 == 0)) {
					c = 0xE08AB0;
				}
				if (planned) {
					c = Palette.lerp(c, 0xFFFFFF, 0.5);
				}
				cell(img, x, z, c);
			}
		}
	}

	private void drawBuilding(BufferedImage img, Graphics2D g, PlotView p, Catalogue catalogue) {
		PlotSpec sp = p.spec();
		var id = sp.archetype();
		ArchetypeInfo info = catalogue.info(id);
		if (info == null || Archetypes.isLinear(id) || id.equals(Archetypes.WALKWAY) || id.equals(Archetypes.TERRACE)
			|| info.category() == ArchetypeCategory.FARM || id.equals(Archetypes.PLAZA) || id.equals(Archetypes.PARK) || id.equals(Archetypes.GARDEN)
			|| info.category() == ArchetypeCategory.EXTRACTION || id.equals(Archetypes.FESTIVAL_GROUND) || id.equals(Archetypes.CEMETERY)) {
			return;
		}
		boolean built = p.state() == PlotState.BUILT;
		int roof = Palette.roof(id, info);
		dev.voidmc.hearth.api.Station station = dev.voidmc.hearth.plan.society.Stations.home(sp, info);
		if (station != null) {
			// homes by who lives in them: the social geography at a glance
			roof = Palette.station(station);
		}
		int x0 = sp.origin().getX();
		int z0 = sp.origin().getZ();
		int x1 = x0 + sp.sizeX() - 1;
		int z1 = z0 + sp.sizeZ() - 1;
		boolean alongX = sp.facing().getAxis() == net.minecraft.core.Direction.Axis.Z;
		// taller roofs a little lighter, so the skyline shows on the plan
		double lift = 0.9 + Math.min(0.35, sp.maxHeight() / 60.0);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int c = Palette.shade(roof, lift);
				// a ridge line along the building, the roof falling to the street and the back
				boolean ridge = alongX ? z == (z0 + z1) / 2 : x == (x0 + x1) / 2;
				if (info.category() == ArchetypeCategory.HOUSING && ridge) {
					c = Palette.shade(c, 0.8);
				}
				if (!built) {
					c = Palette.lerp(c, 0xFFFFFF, 0.55);
				}
				cell(img, x, z, c);
			}
		}
		outline(g, sp, built);
		// the front door
		int[] e = dev.voidmc.hearth.plan.grid.PlotGeometry.entrance(sp);
		int dx = e[0] + sp.facing().getOpposite().getStepX();
		int dz = e[1] + sp.facing().getOpposite().getStepZ();
		cell(img, dx, dz, 0x2A1A10);
	}

	/** The territory's resource sites: dashed outlines in the kind's colour, the kind named. */
	private void drawSites(Graphics2D g, Settlement settlement) {
		Font font = g.getFont();
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		for (dev.voidmc.hearth.api.ResourceSite site : settlement.sites()) {
			var a = site.area();
			if (!this.scene.inside(a.minX(), a.minZ()) && !this.scene.inside(a.maxX(), a.maxZ())) {
				continue;
			}
			int px = (a.minX() - this.scene.x0) * this.s;
			int py = (a.minZ() - this.scene.z0) * this.s + this.header;
			Color c = new Color(Palette.site(site.kind()));
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));
			g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{6, 4}, 0));
			g.drawRect(px + 2, py + 2, a.getXSpan() * this.s - 4, a.getZSpan() * this.s - 4);
			String name = site.kind().getSerializedName() + " " + site.id();
			g.setColor(new Color(255, 255, 255, 170));
			int tw = g.getFontMetrics().stringWidth(name);
			g.fillRect(px + 4, py + 4, tw + 4, 12);
			g.setColor(c.darker());
			g.drawString(name, px + 6, py + 14);
		}
		g.setFont(font);
		g.setStroke(new BasicStroke(1));
	}

	/** A working on the land: a quarry's benches, a mine's adit, a shaft's headframe, a pit, a woodlot's rows. */
	private void drawWorking(BufferedImage img, PlotView p, ArchetypeInfo info) {
		PlotSpec sp = p.spec();
		var id = sp.archetype();
		boolean planned = p.state() != PlotState.BUILT && p.step() == 0 && p.stage() == 0;
		int x0 = sp.origin().getX();
		int z0 = sp.origin().getZ();
		int x1 = x0 + sp.sizeX() - 1;
		int z1 = z0 + sp.sizeZ() - 1;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int ring = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
				int c;
				if (id.equals(Archetypes.QUARRY_PIT)) {
					c = Palette.shade(0xB0ACA4, 1.0 - Math.min(0.45, ring / 2 * 0.09));
				} else if (id.equals(Archetypes.MINE)) {
					boolean axis = sp.facing().getAxis() == net.minecraft.core.Direction.Axis.Z ? Math.abs(x - (x0 + x1) / 2) <= 1 : Math.abs(z - (z0 + z1) / 2) <= 1;
					c = axis ? 0x2A2420 : ring == 0 ? 0x6A5A48 : 0x8C7C66;
				} else if (id.equals(Archetypes.SHAFT)) {
					c = ring <= 1 ? 0x5A4630 : 0x141210;
				} else if (id.equals(Archetypes.CLAY_PIT)) {
					c = ring == 0 ? 0x8A6A50 : ((x + z) & 3) == 0 ? 0x7A9AB0 : 0xB07A5A;
				} else if (id.equals(Archetypes.SAND_PIT)) {
					c = ring == 0 ? 0xB0A070 : 0xE4D49C;
				} else {
					c = (x - x0) % 3 == 1 && (z - z0) % 3 == 1 ? 0x1E4A1A : 0x6A8A4A;
				}
				if (planned) {
					c = Palette.lerp(c, 0xFFFFFF, 0.5);
				}
				cell(img, x, z, c);
			}
		}
	}

	private void outline(Graphics2D g, PlotSpec sp, boolean built) {
		int px = (sp.origin().getX() - this.scene.x0) * this.s;
		int py = (sp.origin().getZ() - this.scene.z0) * this.s + this.header;
		g.setColor(built ? new Color(0x2A2018) : new Color(0x806040));
		g.setStroke(built ? new BasicStroke(1) : new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 3}, 0));
		g.drawRect(px, py, sp.sizeX() * this.s - 1, sp.sizeZ() * this.s - 1);
	}

	private void label(Graphics2D g, String text, int wx, int wz, Color fg, Color bg) {
		int px = (wx - this.scene.x0) * this.s;
		int py = (wz - this.scene.z0) * this.s + this.header;
		FontMetrics fm = g.getFontMetrics();
		int tw = fm.stringWidth(text);
		g.setColor(bg);
		g.fillRoundRect(px - tw / 2 - 3, py - fm.getAscent() / 2 - 3, tw + 6, fm.getAscent() + 4, 6, 6);
		g.setColor(fg);
		g.drawString(text, px - tw / 2, py + fm.getAscent() / 2 - 2);
	}

	private static String name(ArchetypeInfo info) {
		String path = info.id().getPath();
		String n = path.substring(path.indexOf('/') + 1).replace('_', ' ');
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	private void legend(Graphics2D g, int y, int width) {
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		String[] names = {"poor", "peasant", "artisan", "merchant", "clergy", "scholar", "official", "gentry", "lord", "work", "trade", "civic", "law",
			"landmark", "working", "farm", "street", "lane", "path", "bridge", "wall"};
		int[] colors = {Palette.station(dev.voidmc.hearth.api.Station.POOR), Palette.station(dev.voidmc.hearth.api.Station.PEASANT),
			Palette.station(dev.voidmc.hearth.api.Station.ARTISAN), Palette.station(dev.voidmc.hearth.api.Station.MERCHANT),
			Palette.station(dev.voidmc.hearth.api.Station.CLERGY), Palette.station(dev.voidmc.hearth.api.Station.SCHOLAR),
			Palette.station(dev.voidmc.hearth.api.Station.OFFICIAL), Palette.station(dev.voidmc.hearth.api.Station.GENTRY),
			Palette.station(dev.voidmc.hearth.api.Station.LORD), 0x6A5236, 0x8E3B66, 0x3E5A82, 0x4A3A5A, 0xD0A030, 0xB0ACA4, 0xDDBE55, 0xC4BEB0, 0xCCBC9C,
			0xB89C6C, 0x84643E, 0x6C6C66};
		int x = 10;
		for (int i = 0; i < names.length; i++) {
			int tw = g.getFontMetrics().stringWidth(names[i]);
			if (x + tw + 22 > width - 150) {
				// a second row
				x = 10;
				y += 17;
			}
			g.setColor(new Color(colors[i]));
			g.fillRect(x, y, 12, 12);
			g.setColor(new Color(0x2A2018));
			g.drawRect(x, y, 12, 12);
			g.drawString(names[i], x + 16, y + 11);
			x += tw + 26;
		}
	}

	private void scaleBar(Graphics2D g, int x, int y) {
		int len = 32 * this.s;
		g.setColor(new Color(0x2A2018));
		g.fillRect(x, y, len, 4);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		g.drawString("32 blocks", x, y - 3);
	}
}
