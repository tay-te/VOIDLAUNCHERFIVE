package dev.voidmc.atlas.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerDetail;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.client.render.MapRenderer;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The map window's contents, as the design draws them (Figma section "6 · Atlas map — discovery, landmarks,
 * underground"): the Surface / Underground / Nether segments with their counts, search, the rounded map with its
 * readout, north mark, layer chips, zoom and scale; on the right the Places and Legend tabs (places sighted and
 * discovered, the legend's toggles, the marker states) or, underground, the depth scrubber, the side view through the
 * player and the places at that depth; the footer; and the cards over the map (a place's detail card, the waypoint
 * editor, the right-click menu). Laid out in design pixels inside whatever rectangle it is given, so the full map
 * screen and the VOID Hub can both show it.
 */
public final class MapPage {
	private static final float LIST_W = 304;
	/** SDL's scancode for the keypad minus (InputConstants has no name for it). */
	private static final int KEY_NUMPAD_MINUS = 86;
	private static final float PAD = 28;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ROOT).withZone(ZoneId.systemDefault());
	/** The depth scrubber's range, one cell per slice. */
	private static final int SCRUB_TOP = 128;
	private static final int SCRUB_BOTTOM = -64;

	private final AtlasClient atlas;
	public final MapPanel map;
	private final List<Hit> hits = new ArrayList<>();
	private final TextField search = new TextField(40);
	private boolean searching;
	private final TextField name = new TextField(48);
	/** The waypoint being made or edited, or null. */
	private @Nullable Waypoint editing;
	private boolean editingIsNew;
	private @Nullable Menu menu;
	private float listScroll;
	private float listContent;
	private float listX, listY, listH;
	private float mapX, mapY, mapW, mapH;
	private float hoverX = Float.NaN;
	private float hoverY;
	private long lastClick;
	private float lastClickX;
	private float lastClickY;
	/** The right panel's tab: places (false) or the legend (true). */
	public boolean legendTab;
	private boolean scrubbing;
	private float scrubY0;
	private float pressY;

	private record Hit(float x, float y, float w, float h, Runnable action) {
		boolean contains(float px, float py) {
			return px >= this.x && px <= this.x + this.w && py >= this.y && py <= this.y + this.h;
		}
	}

	private record Menu(float x, float y, double mapX, double mapZ) {
	}

	/** A row in the right-hand list. */
	private record Row(String id, MarkerGlyph glyph, boolean hollow, String title, String subtitle, String right, String rightSub, double x, double z,
		String dimension) {
	}

	/** The list's rows, made again at most a few times a second (or when the search changes), not every frame. */
	private final Map<String, List<Row>> rowCache = new java.util.HashMap<>();
	private long rowsAt;
	private String rowsQuery = "";

	public MapPage(AtlasClient atlas, Runnable onSettings) {
		this.atlas = atlas;
		this.map = new MapPanel(atlas);
		this.search.placeholder = "Search places and waypoints";
		this.name.placeholder = "Name";
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.level.dimension().identifier().toString().equals("minecraft:the_nether")) {
			this.map.kind = MapPanel.Kind.NETHER;
		} else if (atlas.underground()) {
			this.map.kind = MapPanel.Kind.UNDERGROUND;
		}
	}

	private void hit(float x, float y, float w, float h, Runnable action) {
		this.hits.add(new Hit(x, y, w, h, action));
	}

	private boolean under() {
		return this.map.kind != MapPanel.Kind.SURFACE;
	}

	// --- drawing ---------------------------------------------------------------------------------------------------

	/** Draws the page into a window rectangle (design pixels). */
	public void render(Ui ui, float wx, float wy, float ww, float wh, float mouseX, float mouseY) {
		this.hits.clear();
		this.mapX = wx + PAD;
		this.mapY = wy + 100;
		this.mapW = ww - PAD * 2 - 20 - LIST_W;
		this.mapH = wh - 100 - 56;
		this.listX = this.mapX + this.mapW + 20;
		this.listY = this.mapY;
		this.listH = this.mapH;

		this.segments(ui, wx + ww / 2, wy + 31, mouseX, mouseY);
		this.searchBox(ui, wx + ww - PAD - 42, wy + 31, mouseX, mouseY);

		boolean overCard = this.overCard(mouseX, mouseY);
		boolean interactive = this.menu == null && !overCard;
		MapRenderer.Frame f = this.map.render(ui, this.mapX, this.mapY, this.mapW, this.mapH, 20, mouseX, mouseY, interactive);
		this.hoverX = interactive && f.inside(mouseX, mouseY) ? mouseX : Float.NaN;
		this.hoverY = mouseY;
		this.aboveOrBelow(ui, f);
		this.readout(ui, this.mapX + 16, this.mapY + 16);
		this.north(ui, this.mapX + this.mapW - 48, this.mapY + 16);
		this.layers(ui, this.mapX + 16, this.mapY + this.mapH - 55, mouseX, mouseY);
		this.zoomControl(ui, this.mapX + this.mapW - 52, this.mapY + this.mapH - 116, mouseX, mouseY);
		this.scaleBar(ui, this.mapX + this.mapW - 62, this.mapY + this.mapH - 43, f);

		if (this.map.kind == MapPanel.Kind.UNDERGROUND) {
			this.depthPanel(ui, mouseX, mouseY);
		} else {
			this.placesPanel(ui, mouseX, mouseY);
		}
		this.footer(ui, wx, wy + wh - 36, ww);

		if (this.editing != null) {
			this.editor(ui, this.mapX + 16, this.mapY + 88, mouseX, mouseY);
		} else if (this.map.selected != null) {
			Marker m = this.map.marker(this.map.selected);
			if (m == null) {
				m = this.findAnywhere(this.map.selected);
			}
			if (m != null) {
				this.card(ui, m, this.mapX + 16, this.mapY + 88, mouseX, mouseY);
			}
		}
		if (this.menu != null) {
			this.contextMenu(ui, mouseX, mouseY);
		}
	}

	private boolean overCard(float x, float y) {
		return (this.editing != null || this.map.selected != null) && x >= this.mapX + 16 && x <= this.mapX + 16 + 296 && y >= this.mapY + 88
			&& y <= this.mapY + this.mapH - 64;
	}

	/** The places on a view: the count on its segment. */
	private int placeCount(MapPanel.Kind kind) {
		MapSession session = this.atlas.session();
		if (session == null) {
			return 0;
		}
		String dim = this.map.dimensionOf(kind);
		int n = 0;
		for (Place p : session.places.visible(dim)) {
			if (!p.isLabel() && (kind == MapPanel.Kind.NETHER || p.underground() == (kind == MapPanel.Kind.UNDERGROUND))) {
				n++;
			}
		}
		return n;
	}

	private void segments(Ui ui, float centreX, float y, float mx, float my) {
		MapPanel.Kind[] kinds = MapPanel.Kind.values();
		float[] widths = new float[kinds.length];
		String[] counts = new String[kinds.length];
		float total = 8;
		for (int i = 0; i < kinds.length; i++) {
			counts[i] = String.valueOf(this.placeCount(kinds[i]));
			widths[i] = 28 + ui.width(kinds[i].label, Ui.Font.F13) + 6 + ui.width(counts[i], Ui.Font.F11);
			total += widths[i] + (i > 0 ? 2 : 0);
		}
		float x = centreX - total / 2;
		ui.panel(x, y, total, 42, 14, Theme.WELL, Theme.HAIR_6);
		float sx = x + 4;
		for (int i = 0; i < kinds.length; i++) {
			MapPanel.Kind k = kinds[i];
			boolean active = this.map.kind == k;
			float w = widths[i];
			boolean hover = !active && mx >= sx && mx <= sx + w && my >= y + 5 && my <= y + 37;
			if (active) {
				ui.panel(sx, y + 5, w, 32, 10, Theme.RAISED, Theme.HAIR_8);
			} else if (hover) {
				ui.round(sx, y + 5, w, 32, 10, Theme.ISLAND);
			}
			ui.text(k.label, sx + 14, y + 12, Ui.Font.F13, active ? Theme.INK : Theme.INK_2);
			ui.text(counts[i], sx + 14 + ui.width(k.label, Ui.Font.F13) + 6, y + 14, Ui.Font.F11, active ? Theme.INK_2 : Theme.INK_4);
			this.hit(sx, y + 5, w, 32, () -> this.map.setKind(k));
			sx += w + 2;
		}
	}

	private void iconButton(Ui ui, float x, float y, float mx, float my, Runnable icon, Runnable action) {
		boolean hover = mx >= x && mx <= x + 42 && my >= y && my <= y + 42;
		ui.panel(x, y, 42, 42, 21, hover ? Theme.ISLAND : Theme.WELL, Theme.HAIR_6);
		icon.run();
		this.hit(x, y, 42, 42, action);
	}

	private void searchBox(Ui ui, float x, float y, float mx, float my) {
		if (!this.searching) {
			this.iconButton(ui, x, y, mx, my, () -> Icons.search(ui, x + 13.5F, y + 13.5F, Theme.INK_2), () -> {
				this.searching = true;
				this.search.focused = true;
			});
			return;
		}
		float w = 260;
		float bx = x + 42 - w;
		ui.panel(bx, y, w, 42, 21, Theme.WELL, this.search.focused ? Theme.HAIR_16 : Theme.HAIR_6);
		Icons.search(ui, bx + 15, y + 13.5F, Theme.INK_3);
		ui.flush();
		ui.g.enableScissor((int) (bx + 40), (int) y, (int) (bx + w - 34), (int) (y + 42));
		this.search.draw(ui, bx + 40, y + 13, w - 76, Ui.Font.F13, Theme.INK, Theme.INK_4);
		ui.flush();
		ui.g.disableScissor();
		Icons.close(ui, bx + w - 28, y + 15, Theme.INK_2);
		this.hit(bx + w - 34, y, 34, 42, () -> {
			this.searching = false;
			this.search.set("");
			this.search.focused = false;
		});
		this.hit(bx, y, w - 34, 42, () -> this.search.focused = true);
	}

	/** The readout: the named region (or biome) the player is in, its source, the position and the heading. */
	private void readout(Ui ui, float x, float y) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		String[] biome = this.atlas.biomeAt(p.blockPosition());
		Place region = this.atlas.regionHere();
		String name = region != null && this.map.kind == MapPanel.Kind.SURFACE ? region.name() : biome[0];
		String tag = region != null && region.source().kind() == Source.Kind.PLUGIN ? region.source().label() : biome[1];
		String bx = minus(p.getBlockX());
		String by = minus(p.getBlockY());
		String bz = minus(p.getBlockZ());
		String facing = facing(p.getYRot());
		float w1 = ui.width(name, Ui.Font.F13) + (tag.isEmpty() ? 0 : 8 + ui.width(tag, Ui.Font.F85, 1));
		float w2 = 8 + 5 + ui.width(bx, Ui.Font.F12) + 12 + 8 + 5 + ui.width(by, Ui.Font.F12) + 12 + 7 + 5 + ui.width(bz, Ui.Font.F12) + 12 + 6 + 5
			+ ui.width(facing, Ui.Font.F11);
		float w = Math.max(w1, w2) + 30;
		ui.panel(x, y, w, 59, 14, Theme.ISLAND, Theme.HAIR_8);
		ui.text(name, x + 15, y + 11, Ui.Font.F13, Theme.INK);
		if (!tag.isEmpty()) {
			ui.text(tag, x + 15 + ui.width(name, Ui.Font.F13) + 8, y + 14, Ui.Font.F85, 1, Theme.INK_4);
		}
		float cx = x + 15;
		float ty = y + 33;
		cx = this.coord(ui, "X", bx, cx, ty);
		cx = this.coord(ui, "Y", by, cx, ty);
		cx = this.coord(ui, "Z", bz, cx, ty);
		ui.round(cx, ty + 4.5F, 6, 6, 1.8F, Theme.alpha(Theme.VIOLET, 0.95F));
		ui.text(facing, cx + 11, ty + 0.5F, Ui.Font.F11, Theme.INK_2);
	}

	private float coord(Ui ui, String axis, String value, float x, float y) {
		ui.text(axis, x, y + 0.5F, Ui.Font.F11, Theme.INK_4);
		float ax = x + ui.width(axis, Ui.Font.F11) + 5;
		ui.text(value, ax, y, Ui.Font.F12, Theme.INK);
		return ax + ui.width(value, Ui.Font.F12) + 12;
	}

	private void north(Ui ui, float x, float y) {
		ui.panel(x, y, 32, 32, 16, Theme.ISLAND, Theme.HAIR_8);
		ui.textCentred("N", x + 16, y + 8, Ui.Font.F11, 0, Theme.INK);
	}

	/** Underground: how far the player is above or below the slice shown, beside their arrow ("13 above"). */
	private void aboveOrBelow(Ui ui, MapRenderer.Frame f) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (!this.under() || p == null || !this.map.dimensionOf(this.map.kind).equals(p.level().dimension().identifier().toString())) {
			return;
		}
		int bottom = this.map.band(this.map.kind) * DimensionData.BAND;
		int y = p.getBlockY();
		int off = y >= bottom + DimensionData.BAND ? y - (bottom + DimensionData.BAND - 1) : y < bottom ? y - bottom : 0;
		if (off == 0) {
			return;
		}
		float sx = f.screenX(p.getX(), p.getZ());
		float sy = f.screenY(p.getX(), p.getZ());
		if (!f.inside(sx, sy)) {
			return;
		}
		String text = Math.abs(off) + (off > 0 ? " above" : " below");
		float w = ui.width(text, Ui.Font.F10) + 16;
		ui.panel(sx + 26, sy - 10.5F, w, 21, 8, Theme.ISLAND, Theme.HAIR_8);
		ui.text(text, sx + 34, sy - 6, Ui.Font.F10, Theme.INK_2);
	}

	/** The layer chips: Terrain, Names, Landmarks, Places, (other mods'), Waypoints, and Friends or, underground, Water & lava. */
	private void layers(Ui ui, float x, float y, float mx, float my) {
		List<MapLayer> all = this.atlas.markersRegistry().layers();
		List<MapLayer> layers = new ArrayList<>();
		for (MapLayer l : all) {
			if (l.id().equals(MapLayer.FLUIDS) && !this.under() || l.id().equals(MapLayer.PLAYERS) && this.under()
				|| l.id().equals(MapLayer.STRUCTURES)) {
				continue;
			}
			layers.add(l);
		}
		AtlasSettings settings = this.atlas.settings();
		float w = 12 + ui.width("LAYERS", Ui.Font.F85, 1) + 10 + 4;
		float[] cw = new float[layers.size()];
		for (int i = 0; i < layers.size(); i++) {
			cw[i] = 9 + 8 + 7 + ui.width(layers.get(i).label(), Ui.Font.F12) + 12;
			w += cw[i] + (i > 0 ? 2 : 0);
		}
		ui.panel(x, y, w, 39, 18, Theme.ISLAND, Theme.HAIR_8);
		ui.text("LAYERS", x + 13, y + 14, Ui.Font.F85, 1, Theme.INK_4);
		float cx = x + 12 + ui.width("LAYERS", Ui.Font.F85, 1) + 10;
		for (int i = 0; i < layers.size(); i++) {
			MapLayer l = layers.get(i);
			boolean on = settings.layerVisible(l.id());
			boolean hover = mx >= cx && mx <= cx + cw[i] && my >= y + 5 && my <= y + 34;
			if (on) {
				ui.panel(cx, y + 5, cw[i], 29, 13, hover ? 0xFF2C2D31 : Theme.RAISED, Theme.HAIR_8);
			} else if (hover) {
				ui.round(cx, y + 5, cw[i], 29, 13, Theme.WELL);
			}
			ui.round(cx + 9, y + 15.5F, 8, 8, 2.5F, on ? Theme.ink(0.85F) : Theme.ink(0.18F));
			ui.text(l.label(), cx + 24, y + 11, Ui.Font.F12, on ? Theme.INK : Theme.INK_4);
			String id = l.id();
			this.hit(cx, y + 5, cw[i], 29, () -> settings.toggleLayer(id));
			cx += cw[i] + 2;
		}
	}

	private void zoomControl(Ui ui, float x, float y, float mx, float my) {
		ui.panel(x, y, 38, 100, 18, Theme.ISLAND, Theme.HAIR_8);
		boolean hIn = mx >= x && mx <= x + 38 && my >= y && my <= y + 37;
		boolean hOut = mx >= x && mx <= x + 38 && my >= y + 63 && my <= y + 100;
		if (hIn) {
			ui.round(x + 3, y + 3, 32, 32, 15, Theme.RAISED);
		}
		if (hOut) {
			ui.round(x + 3, y + 65, 32, 32, 15, Theme.RAISED);
		}
		Icons.plus(ui, x + 11, y + 12, 16, 1.17F, Theme.INK);
		ui.rect(x + 1, y + 37, 36, ui.hairline(), Theme.HAIR_6);
		ui.textCentred(scaleLabel(this.map.targetZoom()), x + 19, y + 44, Ui.Font.F9, 0.6F, Theme.INK_2);
		ui.rect(x + 1, y + 62, 36, ui.hairline(), Theme.HAIR_6);
		Icons.minus(ui, x + 11, y + 72, 16, 1.17F, Theme.INK);
		float cx = this.mapX + this.mapW / 2;
		float cy = this.mapY + this.mapH / 2;
		this.hit(x, y, 38, 37, () -> this.map.zoomBy(1, cx, cy));
		this.hit(x, y + 63, 38, 37, () -> this.map.zoomBy(-1, cx, cy));
	}

	/** "1:4" when a pixel is four blocks, "4:1" when a block is four pixels. */
	public static String scaleLabel(double pixelsPerBlock) {
		if (pixelsPerBlock >= 1) {
			return Math.round(pixelsPerBlock) + ":1";
		}
		return "1:" + Math.round(1 / pixelsPerBlock);
	}

	private void scaleBar(Ui ui, float right, float y, MapRenderer.Frame f) {
		// a "nice" length near 62 design pixels
		double blocksPer62 = 62 / f.k;
		int[] nice = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768};
		int blocks = nice[0];
		for (int n : nice) {
			if (n <= blocksPer62 * 1.35) {
				blocks = n;
			}
		}
		float barW = (float) (blocks * f.k);
		String label = blocks >= 1024 ? (blocks / 1024) + "K BLOCKS" : blocks + " BLOCKS";
		float lw = ui.width(label, Ui.Font.F85, 1);
		float w = 10 + barW + 8 + lw + 10;
		float x = right - w;
		ui.panel(x, y, w, 27, 12, Theme.ISLAND, Theme.HAIR_8);
		float cell = (barW - 6) / 4;
		for (int i = 0; i < 4; i++) {
			ui.round(x + 10 + i * (cell + 2), y + 11, cell, 5, 1.5F, Theme.ink(i % 2 == 0 ? 0.5F : 0.16F));
		}
		ui.text(label, x + 10 + barW + 8, y + 8, Ui.Font.F85, 1, Theme.INK_2);
	}

	// --- the places panel ------------------------------------------------------------------------------------------

	private List<Row> rows(String section) {
		MapSession session = this.atlas.session();
		List<Row> rows = new ArrayList<>();
		if (session == null) {
			return rows;
		}
		LocalPlayer p = Minecraft.getInstance().player;
		double px = p == null ? 0 : p.getX();
		double pz = p == null ? 0 : p.getZ();
		String here = Minecraft.getInstance().level == null ? "" : Minecraft.getInstance().level.dimension().identifier().toString();
		String query = this.search.text.strip().toLowerCase(Locale.ROOT);
		String dim = this.map.dimensionOf(this.map.kind);
		switch (section) {
			case "waypoints" -> {
				for (Waypoint w : session.waypoints.all()) {
					String sub = w.kind() == Waypoint.Kind.DEATH ? "DEATH  ·  " + DATE.format(Instant.ofEpochMilli(w.created())).toUpperCase(Locale.ROOT)
						: "WAYPOINT" + (w.y() < 40 ? "  ·  Y " + minus(w.y()) : "");
					boolean same = w.dimension().equals(here);
					rows.add(new Row(AtlasClient.waypointMarkerId(w), w.kind() == Waypoint.Kind.DEATH ? MarkerGlyph.CROSS : MarkerGlyph.PIN, false, w.name(),
						same ? sub : sub + "  ·  " + dimensionName(w.dimension()), same ? MarkerPainter.distance(w.distance(px, 0, pz)) : "—",
						same ? MarkerPainter.direction(px, pz, w.x() + 0.5, w.z() + 0.5) : "", w.x() + 0.5, w.z() + 0.5, w.dimension()));
				}
				rows.sort(Comparator.comparingDouble(r -> r.dimension.equals(here) ? Math.hypot(r.x - px, r.z - pz) : 1e12));
			}
			case "settlements" -> {
				MarkerQuery q = new MarkerQuery(here, -3e7, -3e7, 3e7, 3e7, px, pz, MarkerQuery.Purpose.LIST);
				for (Marker m : this.atlas.collect(q)) {
					if (m.style() == Marker.Style.SETTLEMENT) {
						rows.add(new Row(m.id(), m.glyph(), false, m.name(), m.subtitle(), MarkerPainter.distance(m.distanceTo(px, pz)),
							MarkerPainter.direction(px, pz, m.x(), m.z()), m.x(), m.z(), m.dimension()));
					}
				}
				rows.sort(Comparator.comparingDouble(r -> Math.hypot(r.x - px, r.z - pz)));
			}
			default -> {
				// sighted: seen and not visited yet; discovered: been there; depth: underground places at the slice shown
				boolean depth = section.equals("depth");
				boolean wantDiscovered = section.equals("discovered");
				int band = this.map.band(this.map.kind);
				for (Place d : session.places.visible(dim)) {
					// names of the land (biomes' areas) only while searching: then "forest" finds the forests
					if (d.isLabel() && !d.kind().equals(Place.LAKE) && query.isEmpty()) {
						continue;
					}
					if (depth) {
						if (!d.underground() || Math.abs(d.y() - (band * DimensionData.BAND + DimensionData.BAND / 2)) > 16) {
							continue;
						}
					} else if ((wantDiscovered ? !d.isDiscovered() : !Kinds.sighted(d)) || this.map.kind != MapPanel.Kind.NETHER && d.underground()) {
						continue;
					}
					boolean same = d.dimension().equals(here);
					String kind = Kinds.of(d).word;
					String sub = depth ? kind + "  ·  Y " + minus(d.y()) : kind + "  ·  " + Kinds.origin(d);
					rows.add(new Row(AtlasClient.placeMarkerId(d), Kinds.sighted(d) ? MarkerGlyph.UNKNOWN : Kinds.of(d).glyph, Kinds.sighted(d), d.name(), sub,
						same ? MarkerPainter.distance(Math.hypot(d.x() + 0.5 - px, d.z() + 0.5 - pz)) : dimensionName(d.dimension()),
						same ? MarkerPainter.direction(px, pz, d.x() + 0.5, d.z() + 0.5) : "", d.x() + 0.5, d.z() + 0.5, d.dimension()));
				}
				rows.sort(Comparator.comparingDouble(r -> r.dimension.equals(here) ? Math.hypot(r.x - px, r.z - pz) : 1e12));
			}
		}
		if (!query.isEmpty()) {
			// the best matches first (names before the lines under them), then the nearest
			java.util.Map<Row, Integer> scores = new java.util.HashMap<>();
			for (Row r : rows) {
				scores.put(r, dev.voidmc.atlas.core.Search.score(query, r.title, r.subtitle));
			}
			rows.removeIf(r -> scores.get(r) == dev.voidmc.atlas.core.Search.NONE);
			rows.sort(Comparator.comparingInt((Row r) -> -scores.get(r)));
		}
		return rows;
	}

	/**
	 * A section's rows, from the cache while it is fresh: made again when the places or waypoints change, the search or
	 * the view does, the player has moved a few blocks (the distances), or two seconds have passed.
	 */
	private List<Row> cachedRows(String section) {
		long now = System.currentTimeMillis();
		MapSession session = this.atlas.session();
		LocalPlayer p = Minecraft.getInstance().player;
		String key = this.search.text + "|" + this.map.kind + "|" + this.map.band(this.map.kind) + "|"
			+ (session == null ? 0 : session.places.version() + 31 * session.waypoints.version()) + "|"
			+ (p == null ? "" : (p.getBlockX() >> 2) + "," + (p.getBlockZ() >> 2));
		if (now - this.rowsAt > 2000 || !key.equals(this.rowsQuery)) {
			this.rowCache.clear();
			this.rowsAt = now;
			this.rowsQuery = key;
		}
		return this.rowCache.computeIfAbsent(section, this::rows);
	}

	/** The right panel above ground: the Places and Legend tabs. */
	private void placesPanel(Ui ui, float mx, float my) {
		float x = this.listX;
		float y = this.listY;
		ui.panel(x, y, LIST_W, 42, 14, Theme.WELL, Theme.HAIR_6);
		int total = this.placeCount(this.map.kind);
		String[] labels = {"Places", "Legend"};
		for (int i = 0; i < 2; i++) {
			boolean active = (i == 1) == this.legendTab;
			float sx = x + 5 + i * 149;
			float sw = i == 0 ? 147 : 145;
			if (active) {
				ui.panel(sx, y + 5, sw, 32, 10, Theme.RAISED, Theme.HAIR_8);
			} else if (mx >= sx && mx <= sx + sw && my >= y + 5 && my <= y + 37) {
				ui.round(sx, y + 5, sw, 32, 10, Theme.ISLAND);
			}
			ui.text(labels[i], sx + 14, y + 12, Ui.Font.F13, active ? Theme.INK : Theme.INK_2);
			if (i == 0) {
				ui.text(String.valueOf(total), sx + 14 + ui.width(labels[0], Ui.Font.F13) + 6, y + 14, Ui.Font.F11, active ? Theme.INK_2 : Theme.INK_4);
			}
			boolean legend = i == 1;
			this.hit(sx, y + 5, sw, 32, () -> {
				this.legendTab = legend;
				this.listScroll = 0;
			});
		}
		float top = y + 48;
		float bottom = this.listY + this.listH;
		if (this.legendTab) {
			this.legend(ui, x, top, bottom, mx, my);
			return;
		}
		// the marker states key, at the foot of the panel
		float keyH = 97;
		this.statesKey(ui, x, bottom - keyH);
		this.list(ui, top, bottom - keyH - 6, mx, my, List.of("sighted", "discovered", "settlements", "waypoints"),
			List.of("SIGHTED  ·  NOT VISITED", "DISCOVERED", "SETTLEMENTS", "WAYPOINTS"));
	}

	/** The design's "marker states" key: a discovered major and minor tile, and their sighted hollow twins. */
	private void statesKey(Ui ui, float x, float y) {
		ui.panel(x, y, LIST_W, 97, 14, Theme.ISLAND, Theme.HAIR_7);
		ui.text("MARKER STATES", x + 15, y + 13, Ui.Font.F85, 1, Theme.INK_4);
		for (int i = 0; i < 2; i++) {
			boolean sighted = i == 1;
			float sx = x + 15 + i * 143;
			float sy = y + 34;
			MarkerGlyph g = sighted ? MarkerGlyph.UNKNOWN : MarkerGlyph.VILLAGE;
			ui.round(sx, sy, 26, 26, 8, sighted ? 0x8C0B0B0C : Theme.ISLAND);
			ui.border(sx, sy, 26, 26, 8, 0, sighted ? 0x4DFFFFFF : Theme.HAIR_16);
			ui.glyph(g, sx + 5, sy + 5, 16, Theme.ink(sighted ? 0.7F : 1), Theme.ink(sighted ? 0.7F : 1));
			MarkerGlyph small = sighted ? MarkerGlyph.UNKNOWN : MarkerGlyph.WRECK;
			ui.round(sx + 34, sy + 5, 16, 16, 5, sighted ? 0x8C0B0B0C : Theme.WELL);
			ui.border(sx + 34, sy + 5, 16, 16, 5, 0, sighted ? 0x3DFFFFFF : Theme.HAIR_12);
			ui.glyph(small, sx + 37, sy + 8, 10, Theme.ink(sighted ? 0.6F : 0.62F), Theme.ink(sighted ? 0.6F : 0.62F));
			ui.text(sighted ? "Sighted, not visited" : "Discovered", sx, sy + 34, Ui.Font.F11, Theme.INK_2);
		}
	}

	/** A scrolling list of sections between {@code top} and {@code bottom}. */
	private void list(Ui ui, float top, float bottom, float mx, float my, List<String> sections, List<String> headings) {
		float x = this.listX;
		float h = bottom - top;
		ui.flush();
		ui.g.enableScissor((int) x - 2, (int) top, (int) (x + LIST_W + 2), (int) bottom);
		float y = top - this.listScroll;
		boolean first = true;
		for (int i = 0; i < sections.size(); i++) {
			List<Row> rows = this.cachedRows(sections.get(i));
			if (rows.isEmpty() && !(sections.get(i).equals("discovered") || sections.get(i).equals("depth"))) {
				continue;
			}
			y = this.section(ui, headings.get(i), rows, x, first ? y : y + 8, top, bottom, mx, my, sections.get(i).equals("waypoints"));
			first = false;
		}
		this.listContent = y + this.listScroll - top;
		ui.flush();
		ui.g.disableScissor();
		float max = Math.max(0, this.listContent - h);
		this.listScroll = Math.max(0, Math.min(max, this.listScroll));
		if (max > 0) {
			// a slim, rounded scroll thumb
			float th = Math.max(24, h * h / this.listContent);
			float ty = top + (h - th) * (this.listScroll / max);
			ui.round(x + LIST_W + 8, ty, 3, th, 1.5F, Theme.HAIR_16);
		}
	}

	private float section(Ui ui, String label, List<Row> rows, float x, float y, float top, float bottom, float mx, float my, boolean add) {
		ui.text(label + "  ·  " + rows.size(), x + 4, y + 8, Ui.Font.F85, 1, Theme.INK_4);
		if (add) {
			boolean hover = mx >= x + 282 && mx <= x + 304 && my >= y + 2 && my <= y + 24;
			ui.panel(x + 282, y + 2, 22, 22, 11, hover ? Theme.ISLAND : Theme.WELL, Theme.HAIR_6);
			Icons.plus(ui, x + 287, y + 7, 12, 1.1F, Theme.INK_2);
			this.hit(x + 282, y + 2, 22, 22, this::newWaypointAtPlayer);
		}
		y += 28;
		if (rows.isEmpty()) {
			String empty = switch (label) {
				case "DISCOVERED" -> "Explore to find places";
				case "AT THIS DEPTH" -> "Nothing found at this depth yet";
				default -> "None yet";
			};
			ui.panel(x, y, LIST_W, 40, 14, 0, Theme.HAIR_6);
			ui.text(empty, x + 14, y + 13, Ui.Font.F12, Theme.INK_4);
			return y + 46;
		}
		for (Row r : rows) {
			if (y + 52 < top || y > bottom) {
				// off the list's window: only its space is counted (a long list costs nothing to scroll)
				y += 58;
				continue;
			}
			boolean selected = r.id.equals(this.map.selected);
			boolean hover = mx >= x && mx <= x + LIST_W && my >= y && my <= y + 52 && my >= top && my <= bottom;
			ui.panel(x, y, LIST_W, 52, 14, selected ? 0xFF1F2023 : hover ? 0xFF1C1D20 : Theme.ISLAND, selected ? Theme.HAIR_16 : Theme.HAIR_7);
			if (r.hollow) {
				MarkerPainter.placeTile(ui, x + 10, y + 10, 32, 10, r.glyph, true, 16, 0.75F);
			} else {
				ui.panel(x + 10, y + 10, 32, 32, 10, Theme.WELL, Theme.HAIR_5);
				ui.glyph(r.glyph, x + 18, y + 18, 16, Theme.ink(0.85F), Theme.INK);
			}
			// the right column first: the kind line is cut to fit before it, never under it
			float rw = Math.max(ui.width(r.right, Ui.Font.F12), ui.width(r.rightSub, Ui.Font.F85, 1));
			float tw = LIST_W - 54 - 14 - rw - 10;
			ui.text(ui.fit(r.title, Ui.Font.F13, tw), x + 54, y + 9, Ui.Font.F13, Theme.INK);
			ui.text(ui.fit(r.subtitle, Ui.Font.F85, 1, tw), x + 54, y + 31, Ui.Font.F85, 1, Theme.INK_4);
			ui.textRight(r.right, x + LIST_W - 14, y + 10, Ui.Font.F12, 0, Theme.INK_2);
			ui.textRight(r.rightSub, x + LIST_W - 14, y + 31, Ui.Font.F85, 1, Theme.INK_4);
			if (y + 52 > top && y < bottom) {
				Row row = r;
				this.hit(x, Math.max(y, top), LIST_W, Math.min(y + 52, bottom) - Math.max(y, top), () -> this.select(row));
			}
			y += 58;
		}
		return y;
	}

	/** The Legend tab: every kind of place on this map with its count and a toggle, in the design's three groups. */
	private void legend(Ui ui, float x, float top, float bottom, float mx, float my) {
		MapSession session = this.atlas.session();
		Map<Kinds.Kind, Integer> counts = new EnumMap<>(Kinds.Kind.class);
		if (session != null) {
			for (Place p : session.places.visible(this.map.dimensionOf(this.map.kind))) {
				if (!p.isLabel() || p.kind().equals(Place.LAKE)) {
					counts.merge(Kinds.legend(p), 1, Integer::sum);
				}
			}
		}
		// the kinds the design always lists, and any other with places on this map
		java.util.Set<Kinds.Kind> always = java.util.EnumSet.of(Kinds.Kind.VILLAGES, Kinds.Kind.TEMPLES, Kinds.Kind.MONUMENTS, Kinds.Kind.MANSIONS,
			Kinds.Kind.PEAKS, Kinds.Kind.LAKES, Kinds.Kind.WATERFALLS, Kinds.Kind.PORTALS, Kinds.Kind.WRECKS, Kinds.Kind.CAVES, Kinds.Kind.GEODES,
			Kinds.Kind.DUNGEONS, Kinds.Kind.TRAIL_RUINS, Kinds.Kind.SIGHTED);
		AtlasSettings settings = this.atlas.settings();
		ui.flush();
		ui.g.enableScissor((int) x - 2, (int) top, (int) (x + LIST_W + 2), (int) bottom);
		float y = top - this.listScroll;
		for (Kinds.Group group : Kinds.Group.values()) {
			ui.text(group.heading, x + 4, y + 6, Ui.Font.F85, 1, Theme.INK_4);
			y += 22;
			for (Kinds.Kind k : Kinds.Kind.values()) {
				if (k.group != group || k == Kinds.Kind.REGIONS || k == Kinds.Kind.RIVERS || !always.contains(k) && !counts.containsKey(k)) {
					continue;
				}
				boolean on = settings.kindVisible(k.name());
				float rx = x;
				float ry = y;
				boolean hover = mx >= rx && mx <= rx + LIST_W && my >= ry && my <= ry + 30 && my >= top && my <= bottom;
				if (hover) {
					ui.round(rx, ry, LIST_W, 30, 8, Theme.ISLAND);
				}
				ui.panel(rx + 4, ry + 3, 24, 24, 7, Theme.WELL, Theme.HAIR_8);
				ui.glyph(k.glyph, rx + 10, ry + 9, 12, Theme.ink(on ? 0.95F : 0.35F), Theme.ink(on ? 0.95F : 0.35F));
				ui.text(k.label, rx + 38, ry + 7.5F, Ui.Font.F12, on ? Theme.INK : Theme.INK_4);
				String n = String.valueOf(counts.getOrDefault(k, 0));
				ui.textRight(n, rx + LIST_W - 4 - 32 - 10, ry + 8, Ui.Font.F105, 0, on ? Theme.INK_3 : Theme.INK_4);
				toggle(ui, rx + LIST_W - 4 - 32, ry + 6, on);
				if (ry + 30 > top && ry < bottom) {
					this.hit(rx, Math.max(ry, top), LIST_W, Math.min(ry + 30, bottom) - Math.max(ry, top), () -> settings.toggleKind(k.name()));
				}
				y += 32;
			}
			y += 6;
		}
		this.listContent = y + this.listScroll - top;
		ui.flush();
		ui.g.disableScissor();
		this.listScroll = Math.max(0, Math.min(Math.max(0, this.listContent - (bottom - top)), this.listScroll));
	}

	/** The design's cell-grip toggle (32 x 18). */
	private static void toggle(Ui ui, float x, float y, boolean on) {
		if (on) {
			ui.round(x, y, 32, 18, 6, Theme.ink(0.88F));
			ui.round(x + 17, y + 3, 12, 12, 4, Theme.SHELL);
			for (int i = 0; i < 3; i++) {
				ui.round(x + 18.5F + i * 3.5F, y + 8, 2, 2, 1, Theme.ink(0.3F));
			}
		} else {
			ui.panel(x, y, 32, 18, 6, Theme.WELL, Theme.HAIR_16);
			ui.round(x + 3, y + 3, 12, 12, 4, Theme.INK_4);
			for (int i = 0; i < 3; i++) {
				ui.round(x + 4.5F + i * 3.5F, y + 8, 2, 2, 1, 0x4D0B0B0C);
			}
		}
	}

	// --- the underground panel -------------------------------------------------------------------------------------

	/** Underground: the depth scrubber, the side view through the player, and the places at this depth. */
	private void depthPanel(Ui ui, float mx, float my) {
		float x = this.listX;
		float y = this.listY;
		this.scrubber(ui, x, y, mx, my);
		this.sideView(ui, x + 132, y);
		this.list(ui, y + 390, this.listY + this.listH, mx, my, List.of("depth"), List.of("AT THIS DEPTH"));
	}

	/** The surface's height at a column from what is in memory (never a disk read on the render thread), or a default. */
	private int loadedHeight(int x, int z, int fallback) {
		MapSession session = this.atlas.session();
		DimensionData d = session == null ? null : session.dimensionIfOpen(this.map.dimensionOf(MapPanel.Kind.SURFACE));
		LayerData surface = d == null ? null : d.layerIfOpen(DimensionData.SURFACE);
		RegionRaw raw = surface == null ? null : surface.rawIfLoaded(Coords.region(x), Coords.region(z));
		int i = RegionRaw.index(Coords.inRegion(x), Coords.inRegion(z));
		return raw == null || !raw.known(i) ? fallback : raw.height[i] + 1;
	}

	private float scrubCellY(float y, int cellY) {
		return y + 35 + (SCRUB_TOP - cellY) / 8F * 12.5F;
	}

	/**
	 * The design's depth scrubber: a cell for every 8 blocks from 128 down to -64, brighter where the player has
	 * explored, violet for the slice shown (live), outlined for the player's own height; click or drag to choose a slice,
	 * the handle beside it shows its height.
	 */
	private void scrubber(Ui ui, float x, float y, float mx, float my) {
		ui.panel(x, y, 124, 380, 18, Theme.ISLAND, Theme.HAIR_8);
		ui.text("DEPTH  ·  Y", x + 13, y + 13, Ui.Font.F85, 1, Theme.INK_4);
		NavigableSet<Integer> explored = this.map.bands(this.map.kind);
		int slice = this.map.band(this.map.kind);
		LocalPlayer p = Minecraft.getInstance().player;
		int you = p == null ? Integer.MIN_VALUE : DimensionData.bandOf(p.getBlockY());
		int surface = p == null ? 64 : this.loadedHeight(p.getBlockX(), p.getBlockZ(), 64);
		int surfaceBand = DimensionData.bandOf(surface);
		for (int cy = SCRUB_TOP; cy >= SCRUB_BOTTOM; cy -= 8) {
			int band = DimensionData.bandOf(cy);
			float ty = this.scrubCellY(y, cy);
			int fill;
			if (band == slice) {
				fill = Theme.alpha(Theme.VIOLET, 0.95F);
			} else if (explored.contains(band)) {
				fill = Theme.ink(0.24F);
			} else {
				fill = band > surfaceBand ? Theme.ink(0.05F) : Theme.ink(0.10F);
			}
			ui.round(x + 45, ty, 30, 8, 3, fill);
			if (band == you && band != slice) {
				ui.border(x + 45, ty, 30, 8, 3, 0, Theme.ink(0.85F));
			}
			if (cy % 32 == 0) {
				ui.textRight(minus(cy), x + 37, ty - 0.76F, Ui.Font.F9, 0.6F, Theme.INK_4);
			}
		}
		if (surfaceBand * 8 <= SCRUB_TOP && surfaceBand * 8 >= SCRUB_BOTTOM) {
			ui.text("surface", x + 81, this.scrubCellY(y, surfaceBand * 8) - 0.76F, Ui.Font.F85, 0.6F, Theme.INK_4);
		}
		if (p != null && you != slice && you * 8 <= SCRUB_TOP && you * 8 >= SCRUB_BOTTOM) {
			ui.text("you " + minus(p.getBlockY()), x + 81, this.scrubCellY(y, you * 8) - 1.26F, Ui.Font.F9, 0.4F, Theme.INK_2);
		}
		// the handle: the slice's height
		float hy = this.scrubCellY(y, Math.max(SCRUB_BOTTOM, Math.min(SCRUB_TOP, slice * 8))) - 5.5F;
		String label = minus(slice * DimensionData.BAND);
		float hw = ui.width(label, Ui.Font.F105) + 14;
		ui.panel(x + 81, hy, hw, 19, 7, Theme.RAISED, 0x24FFFFFF);
		ui.text(label, x + 88, hy + 3, Ui.Font.F105, Theme.INK);
		ui.text("SHIFT + SCROLL", x + 13, y + 355, Ui.Font.F85, 1, Theme.INK_4);
		this.scrubY0 = y;
		this.hit(x + 30, y + 30, 90, 315, () -> {
			this.scrubbing = true;
			this.scrubTo(this.pressY);
		});
	}

	private void scrubTo(float my) {
		float cells = (my - (this.scrubY0 + 35 + 4)) / 12.5F;
		int cy = SCRUB_TOP - Math.round(cells) * 8;
		cy = Math.max(SCRUB_BOTTOM, Math.min(SCRUB_TOP, cy));
		this.map.setBand(DimensionData.bandOf(cy));
	}

	/**
	 * The side view: a cross-section through the player along the x axis, square pixels of 1.5 design pixels per block:
	 * rock as a quiet fill, the air the player has seen cut out of it, the surface line, the slice shown as a band with
	 * its violet line, and the player as a small square.
	 */
	private void sideView(Ui ui, float x, float y) {
		LocalPlayer p = Minecraft.getInstance().player;
		ui.panel(x, y, 172, 380, 18, Theme.ISLAND, Theme.HAIR_8);
		int z = p == null ? (int) this.map.centreZ() : p.getBlockZ();
		ui.text("SIDE VIEW  ·  Z " + minus(z), x + 14, y + 14, Ui.Font.F85, 1, Theme.INK_4);
		float top = y + 48.5F;
		float h = 287.5F;
		float k = h / (SCRUB_TOP - SCRUB_BOTTOM);
		int cx = p == null ? (int) this.map.centreX() : p.getBlockX();
		int half = (int) (172 / k / 2);
		MapSession session = this.atlas.session();
		if (session == null) {
			return;
		}
		DimensionData d = session.dimension(this.map.dimensionOf(this.map.kind), this.map.kind == MapPanel.Kind.NETHER);
		LayerData surface = d.ceiling ? null : d.layerIfOpen(DimensionData.SURFACE);
		ui.flush();
		ui.g.enableScissor((int) x + 1, (int) top, (int) (x + 171), (int) (top + h));
		int step = 2;
		for (int bx = cx - half; bx < cx + half; bx += step) {
			float sx = x + 86 + (bx - cx) * k;
			float sw = step * k + 0.01F;
			int ground = d.ceiling ? SCRUB_TOP : Integer.MIN_VALUE;
			if (surface != null) {
				RegionRaw raw = surface.rawIfLoaded(Coords.region(bx), Coords.region(z));
				int i = RegionRaw.index(Coords.inRegion(bx), Coords.inRegion(z));
				if (raw != null && raw.known(i)) {
					ground = Math.min(SCRUB_TOP, raw.height[i] - raw.depth(i));
				}
			}
			if (ground != Integer.MIN_VALUE) {
				// the rock under the ground, a quiet fill
				float gy = top + (SCRUB_TOP - ground) * k;
				ui.rect(sx, gy, sw, top + h - gy, Theme.ink(0.07F));
				if (!d.ceiling) {
					ui.rect(sx, gy, sw, 1.5F, Theme.ink(0.22F));
				}
			}
			// the air seen underground, cut out of it
			for (int band : d.bands()) {
				int by0 = band * DimensionData.BAND;
				if (by0 > SCRUB_TOP || by0 < SCRUB_BOTTOM) {
					continue;
				}
				LayerData layer = d.layerIfOpen(DimensionData.underLayer(band));
				if (layer == null) {
					continue;
				}
				RegionRaw raw = layer.rawIfLoaded(Coords.region(bx), Coords.region(z));
				int i = RegionRaw.index(Coords.inRegion(bx), Coords.inRegion(z));
				if (raw == null || !raw.known(i) || (raw.flags[i] & RegionRaw.ROCK) != 0) {
					continue;
				}
				int floor = Math.max(raw.height[i], by0 - 24);
				int roof = by0 + DimensionData.BAND - 1;
				float fy = top + (SCRUB_TOP - roof) * k;
				float fh = Math.max(k, (roof - floor + 1) * k);
				ui.rect(sx, fy, sw, fh, Theme.MAP_WELL);
			}
		}
		// the slice, and the player
		int slice = this.map.band(this.map.kind);
		float sy = top + (SCRUB_TOP - (slice * DimensionData.BAND + DimensionData.BAND)) * k;
		ui.rect(x, sy, 172, DimensionData.BAND * k, Theme.ink(0.05F));
		ui.rect(x, sy + DimensionData.BAND * k / 2 - 1, 172, 2, Theme.alpha(Theme.VIOLET, 0.9F));
		if (p != null) {
			float py = top + (SCRUB_TOP - p.getBlockY()) * k;
			ui.round(x + 86 - 3.5F, py - 3.5F, 7, 7, 1.5F, Theme.INK);
		}
		ui.flush();
		ui.g.disableScissor();
		if (surface != null && p != null) {
			int s = this.loadedHeight(cx, z, 64);
			ui.textRight("surface " + s, x + 158, top + (SCRUB_TOP - s) * k - 14, Ui.Font.F85, 0.6F, Theme.INK_4);
		}
		ui.text(d.ceiling ? "lava sea" : "bedrock", x + 14, y + 356, Ui.Font.F85, 0.6F, Theme.INK_4);
	}

	// --- selection and the footer ----------------------------------------------------------------------------------

	private void select(Row r) {
		Minecraft mc = Minecraft.getInstance();
		String here = mc.level == null ? "" : mc.level.dimension().identifier().toString();
		if (r.dimension.equals("minecraft:the_nether")) {
			this.map.setKind(MapPanel.Kind.NETHER);
		} else if (this.map.kind == MapPanel.Kind.NETHER && !r.dimension.equals("minecraft:the_nether")) {
			this.map.setKind(MapPanel.Kind.SURFACE);
		}
		if (r.dimension.equals(this.map.dimensionOf(this.map.kind)) || r.dimension.equals(here)) {
			this.map.centreOn(r.x, r.z);
		}
		this.map.selected = r.id;
		this.editing = null;
	}

	private void footer(Ui ui, float wx, float y, float ww) {
		String left;
		String right;
		MapSession session = this.atlas.session();
		Place selected = this.map.selected == null ? null : this.atlas.placeFor(this.map.selected);
		LocalPlayer p = Minecraft.getInstance().player;
		if (!Float.isNaN(this.hoverX) && this.map.frame() != null && this.map.selected == null) {
			MapRenderer.Frame f = this.map.frame();
			int bx = (int) Math.floor(f.mapX(this.hoverX, this.hoverY));
			int bz = (int) Math.floor(f.mapZ(this.hoverX, this.hoverY));
			left = "X " + minus(bx) + "  ·  Z " + minus(bz);
			String place = this.describe(bx, bz);
			if (place != null) {
				left += "  ·  " + place;
			}
		} else if (this.map.kind == MapPanel.Kind.UNDERGROUND) {
			int bottom = this.map.band(this.map.kind) * DimensionData.BAND;
			left = "SLICE Y " + minus(bottom);
			if (p != null) {
				int dy = p.getBlockY() - bottom;
				left += dy >= DimensionData.BAND ? "  ·  YOU ARE " + (p.getBlockY() - (bottom + DimensionData.BAND - 1)) + " ABOVE IT"
					: dy < 0 ? "  ·  YOU ARE " + (-dy) + " BELOW IT" : "  ·  YOU ARE IN IT";
			}
			left += "  ·  ONLY CAVES YOU HAVE SEEN ARE DRAWN";
		} else if (selected != null) {
			String seen = "";
			if (p != null && selected.dimension().equals(p.level().dimension().identifier().toString())) {
				seen = "  ·  " + (Kinds.sighted(selected) ? "SIGHTED " : "") + MarkerPainter.distance(selected.distanceTo(p.getX(), p.getZ())) + " AWAY";
			}
			left = selected.name() + seen + "  ·  " + (Kinds.sighted(selected) ? "NOT VISITED" : "VISITED");
		} else {
			int places = this.placeCount(this.map.kind);
			int sighted = 0;
			int regions = 0;
			if (session != null) {
				for (Place pl : session.places.visible(this.map.dimensionOf(this.map.kind))) {
					sighted += Kinds.sighted(pl) && !pl.isLabel() && !pl.underground() ? 1 : 0;
					regions += pl.kind().equals(Place.REGION) ? 1 : 0;
				}
			}
			left = places + " PLACES  ·  " + sighted + " SIGHTED, NOT VISITED  ·  " + regions + " REGIONS";
			if (this.atlas.busy() > 0) {
				left += "  ·  MAPPING " + this.atlas.busy();
			}
		}
		if (this.editing != null) {
			right = "ENTER SAVES  ·  ESC CANCELS";
		} else if (this.map.selected != null) {
			right = "T TRACKS  ·  W SETS A WAYPOINT  ·  ESC CLEARS THE SELECTION";
		} else if (this.map.kind == MapPanel.Kind.UNDERGROUND) {
			right = "SHIFT + SCROLL CHANGES DEPTH  ·  F FOLLOWS YOUR Y";
		} else {
			right = "SCROLL TO ZOOM  ·  DRAG TO PAN  ·  CLICK A PLACE FOR DETAILS";
		}
		ui.text(left.toUpperCase(Locale.ROOT), wx + PAD, y, Ui.Font.F85, 1, Theme.INK_4);
		ui.textRight(right, wx + ww - PAD, y, Ui.Font.F85, 1, Theme.INK_4);
	}

	/** What the map knows of a block column: "Y 64, sand". */
	private @Nullable String describe(int bx, int bz) {
		LayerData layer = this.map.layer(this.map.kind);
		if (layer == null) {
			return null;
		}
		RegionRaw raw = layer.rawIfLoaded(Coords.region(bx), Coords.region(bz));
		if (raw == null) {
			return null;
		}
		int i = RegionRaw.index(Coords.inRegion(bx), Coords.inRegion(bz));
		if (!raw.known(i)) {
			return "UNEXPLORED";
		}
		if ((raw.flags[i] & RegionRaw.ROCK) != 0) {
			return "ROCK";
		}
		var looks = this.atlas.session() == null ? null : this.atlas.session().looks();
		String biome = looks == null ? null : looks.biomeName(raw.biome[i]);
		String text = "Y " + minus(raw.height[i]);
		if (raw.depth(i) > 0) {
			text += "  ·  WATER " + raw.depth(i) + " DEEP";
		}
		if (biome != null) {
			text += "  ·  " + prettify(biome.substring(biome.indexOf(':') + 1));
		}
		return text;
	}

	// --- the detail card -------------------------------------------------------------------------------------------

	private @Nullable Marker findAnywhere(String id) {
		Place place = this.atlas.placeFor(id);
		if (place != null) {
			return AtlasClient.placeMarker(place);
		}
		LocalPlayer p = Minecraft.getInstance().player;
		double px = p == null ? 0 : p.getX();
		double pz = p == null ? 0 : p.getZ();
		for (String dim : new String[]{this.map.dimensionOf(this.map.kind), "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}) {
			MarkerQuery q = new MarkerQuery(dim, -3e7, -3e7, 3e7, 3e7, px, pz, MarkerQuery.Purpose.LIST);
			for (Marker m : this.atlas.collect(q)) {
				if (m.id().equals(id)) {
					return m;
				}
			}
		}
		return null;
	}

	/** The facts a card lists under WHAT'S KNOWN: what was seen, where, and what is still unknown. */
	private List<String> facts(Place place, @Nullable String region) {
		List<String> out = new ArrayList<>();
		String seen = switch (Kinds.of(place)) {
			case VILLAGES -> "A bell, beds and places of work";
			case WRECKS -> "A drowned hull of planks round a chest";
			case PORTALS -> "Crying obsidian set in an obsidian frame";
			case DUNGEONS -> "A spawner in a room of mossy cobblestone";
			case GEODES -> "Amethyst in a shell of calcite";
			case TRAIL_RUINS -> "Suspicious gravel among old terracotta";
			case MINESHAFTS -> "Rails, fences and cobwebs in the rock";
			case CAVES -> "An opening that runs into the hill";
			case PEAKS -> "A summit at " + place.y() + ", well above the land round it";
			case WATERFALLS -> "Water falling from a ledge";
			case LAKES -> "Standing water, about " + Math.max(1, place.radius() * 2) + " blocks across";
			case RAVINES -> "A deep, narrow cut in the ground";
			default -> place.source().kind() == Source.Kind.EXACT ? "Known from the world itself" : "Seen in the land";
		};
		out.add(seen);
		if (place.underground()) {
			out.add("Under the ground, at Y " + minus(place.y()));
		} else if (region != null) {
			out.add("In " + region);
		}
		if (Kinds.sighted(place)) {
			out.add(place.isNatural() ? "Not visited yet" : "Chests and loot stay unknown until you go");
		} else if (place.discovered() != 0) {
			out.add("Visited " + DATE.format(Instant.ofEpochMilli(place.discovered())));
		}
		return out;
	}

	/**
	 * The detail card (the design's {@code atlas/detail card}): glyph tile (hollow while only sighted), name and kind ·
	 * region, close; the status ("Sighted 212 m away · not visited yet", or visited); three stats (metres and bearing,
	 * level, the day sighted or found); what is known; the source chip; Track and Set waypoint. Waypoints keep Edit and
	 * Delete; another mod's marker shows its own readouts, meter and sections.
	 */
	private void card(Ui ui, Marker m, float x, float y, float mx, float my) {
		float w = 296;
		LocalPlayer p = Minecraft.getInstance().player;
		double px = p == null ? m.x() : p.getX();
		double pz = p == null ? m.z() : p.getZ();
		boolean sameDim = Minecraft.getInstance().level != null && Minecraft.getInstance().level.dimension().identifier().toString().equals(m.dimension());
		Waypoint waypoint = this.atlas.waypointFor(m.id());
		Place place = this.atlas.placeFor(m.id());
		MapSession session = this.atlas.session();
		boolean sighted = place != null && Kinds.sighted(place);
		double distance = m.distanceTo(px, pz);
		String region = null;
		if (place != null && session != null) {
			DimensionData d = session.dimensionIfOpen(place.dimension());
			String id = d == null ? null : d.areas.regionAt(place.x(), place.z());
			Place r = id == null ? null : session.places.peek(id);
			region = r == null ? null : r.name();
		}
		// what goes in
		List<MarkerDetail.Readout> readouts = new ArrayList<>();
		if (sameDim) {
			readouts.add(new MarkerDetail.Readout(String.valueOf(Math.round(distance)), "METRES  " + MarkerPainter.direction(px, pz, m.x(), m.z())));
		}
		MarkerDetail detail = m.detail();
		if (detail != null) {
			readouts.addAll(0, detail.readouts());
		}
		if (readouts.size() < 3) {
			readouts.add(new MarkerDetail.Readout(minus((int) Math.round(m.y())), "LEVEL"));
		}
		if (readouts.size() < 3 && place != null && session != null) {
			long[] days = session.places.peekDays(place.id());
			long day = sighted ? days[0] : days[1];
			if (day > 0) {
				readouts.add(new MarkerDetail.Readout("Day " + day, sighted ? "SIGHTED" : "FOUND"));
			} else {
				long when = sighted ? place.sighted() : place.discovered();
				if (when != 0) {
					readouts.add(new MarkerDetail.Readout(DATE.format(Instant.ofEpochMilli(when)), sighted ? "SIGHTED" : "FOUND"));
				}
			}
		}
		if (readouts.size() < 3 && waypoint != null) {
			readouts.add(new MarkerDetail.Readout(DATE.format(Instant.ofEpochMilli(waypoint.created())), waypoint.kind() == Waypoint.Kind.DEATH ? "DIED" : "MADE"));
		}
		while (readouts.size() > 3) {
			readouts.remove(readouts.size() - 1);
		}
		List<String> facts = place == null ? List.of() : this.facts(place, region);
		Source source = place != null ? place.source() : m.source();
		// the height first, to size the card
		float h = 17 + 40;
		if (place != null) {
			h += 14 + 14;
		}
		h += 14 + 60;
		if (detail != null && detail.meter() != null) {
			h += 14 + 51;
		}
		if (detail != null) {
			for (MarkerDetail.Section s : detail.sections()) {
				h += 14 + 11 + s.rows().size() * 42;
			}
		}
		if (!facts.isEmpty()) {
			h += 14 + 11 + 8 + facts.size() * 22 - 8;
		}
		if (source != null) {
			h += 14 + 26;
		}
		h += 14 + 40 + 17;
		h = Math.min(h, this.mapH - 88 - 64);
		ui.panel(x, y, w, h, 20, Theme.ISLAND, Theme.HAIR_8);
		float cx = x + 17;
		float cy = y + 17;
		// header: the tile, the name and kind, the close button
		MarkerGlyph glyph = place != null ? Kinds.glyph(place) : m.glyph();
		if (sighted) {
			MarkerPainter.placeTile(ui, cx, cy, 40, 12, glyph, true, 16, 0.75F);
		} else {
			ui.panel(cx, cy, 40, 40, 12, Theme.WELL, Theme.HAIR_16);
			ui.glyph(glyph, cx + 12, cy + 12, 16, Theme.INK, Theme.INK);
		}
		String title = place != null ? place.name() : m.name();
		String type = place != null ? (Kinds.of(place).word + (region != null && !place.underground() ? "  ·  " + region.toUpperCase(Locale.ROOT) : ""))
			: (m.subtitle().isEmpty() ? (waypoint != null ? "WAYPOINT" : "PLACE") : m.subtitle());
		ui.text(ui.fit(title, Ui.Font.F17, 176), cx + 52, cy + 2.5F, Ui.Font.F17, Theme.INK);
		ui.text(ui.fit(type, Ui.Font.F85, 1, 176), cx + 52, cy + 26.5F, Ui.Font.F85, 1, Theme.INK_4);
		boolean hClose = mx >= cx + 240 && mx <= cx + 262 && my >= cy + 9 && my <= cy + 31;
		ui.panel(cx + 240, cy + 9, 22, 22, 11, hClose ? Theme.ISLAND : Theme.WELL, Theme.HAIR_6);
		Icons.close(ui, cx + 246, cy + 15, Theme.INK_2);
		this.hit(cx + 240, cy + 9, 22, 22, () -> this.map.selected = null);
		cy += 40;
		// the status
		if (place != null) {
			cy += 14;
			if (sighted) {
				ui.border(cx, cy + 3, 8, 8, 2.5F, 0, Theme.ink(0.45F));
			} else {
				ui.round(cx, cy + 3, 8, 8, 2.5F, Theme.ink(0.45F));
			}
			String status;
			if (sighted) {
				status = "Sighted " + MarkerPainter.distance(distance) + " away  ·  not visited yet";
			} else {
				long[] days = session == null ? new long[]{-1, -1} : session.places.peekDays(place.id());
				status = days[1] > 0 ? "Visited on day " + days[1]
					: place.discovered() != 0 ? "Visited " + DATE.format(Instant.ofEpochMilli(place.discovered())) : "Visited";
			}
			ui.text(status, cx + 16, cy - 0.5F, Ui.Font.F115, Theme.INK_2);
			cy += 14;
		}
		// the stats
		cy += 14;
		float rw = (262 - 8 * (readouts.size() - 1)) / Math.max(1, readouts.size());
		for (int i = 0; i < readouts.size(); i++) {
			MarkerDetail.Readout r = readouts.get(i);
			float rx = cx + i * (rw + 8);
			ui.panel(rx, cy, rw, 60, 12, Theme.WELL, Theme.HAIR_5);
			ui.text(ui.fit(r.value(), Ui.Font.F18, rw - 20), rx + 13, cy + 10.5F, Ui.Font.F18, Theme.INK);
			ui.text(ui.fit(r.label(), Ui.Font.F85, 1, rw - 20), rx + 13, cy + 38, Ui.Font.F85, 1, Theme.INK_4);
		}
		cy += 60;
		if (detail != null && detail.meter() != null) {
			cy += 14;
			MarkerDetail.Meter meter = detail.meter();
			ui.text(meter.label().toUpperCase(Locale.ROOT), cx, cy + 2, Ui.Font.F85, 1, Theme.INK_4);
			ui.textRight(meter.valueText(), cx + 262, cy, Ui.Font.F12, 0, Theme.INK);
			int cells = 20;
			double t = (meter.value() - meter.min()) / Math.max(1e-9, meter.max() - meter.min());
			int at = (int) Math.round(t * (cells - 1));
			int mid = cells / 2;
			for (int i = 0; i < cells; i++) {
				boolean filled = at >= mid ? i >= mid && i < at : i < mid && i > at;
				int c = i == at ? Theme.alpha(Theme.VIOLET, 0.95F) : filled ? Theme.ink(0.42F) : Theme.ink(0.1F);
				ui.round(cx + i * 12.75F + (i >= mid ? 2.8F : 0), cy + 23, 9.6F, 9, 2.7F, c);
			}
			ui.text(meter.caption().toUpperCase(Locale.ROOT), cx, cy + 40, Ui.Font.F85, 1, Theme.INK_2);
			cy += 51;
		}
		if (detail != null) {
			for (MarkerDetail.Section s : detail.sections()) {
				cy += 14;
				ui.text(s.label().toUpperCase(Locale.ROOT), cx, cy, Ui.Font.F85, 1, Theme.INK_4);
				cy += 17;
				for (MarkerDetail.Row row : s.rows()) {
					ui.panel(cx, cy, 262, 36, 12, Theme.WELL, Theme.HAIR_5);
					ui.text(ui.fit(row.text(), Ui.Font.F12, 180), cx + 11, cy + 10.5F, Ui.Font.F12, row.active() ? Theme.INK : Theme.INK_2);
					if (row.tag() != null && !row.tag().isEmpty()) {
						String tag = row.tag().toUpperCase(Locale.ROOT);
						float tw = ui.width(tag, Ui.Font.F85, 1) + 14;
						float tx = cx + 262 - 10 - tw;
						if (row.active()) {
							ui.panel(tx, cy + 9, tw, 18, 8, Theme.ink(0.1F), Theme.HAIR_8);
						} else {
							ui.border(tx, cy + 9, tw, 18, 8, 0, Theme.HAIR_8);
						}
						ui.text(tag, tx + 7, cy + 12.5F, Ui.Font.F85, 1, row.active() ? Theme.INK : Theme.INK_4);
					}
					cy += 42;
				}
				cy -= 6;
			}
		}
		// what is known
		if (!facts.isEmpty()) {
			cy += 14;
			ui.text("WHAT'S KNOWN", cx, cy, Ui.Font.F85, 1, Theme.INK_4);
			cy += 19;
			for (String fact : facts) {
				ui.round(cx, cy + 4.5F, 5, 5, 1.5F, Theme.ink(0.35F));
				ui.text(ui.fit(fact, Ui.Font.F11, 248), cx + 14, cy, Ui.Font.F11, Theme.INK_2);
				cy += 22;
			}
			cy -= 8;
		}
		// where it comes from
		if (source != null) {
			cy += 14;
			ui.text("SOURCE", cx, cy + 7.5F, Ui.Font.F85, 1, Theme.INK_4);
			MarkerPainter.sourceChip(ui, source, cx + 49, cy);
			cy += 26;
		}
		cy += 14;
		// actions
		if (waypoint != null) {
			this.button(ui, cx, cy, 127, true, "Edit", MarkerGlyph.PIN, mx, my, () -> this.edit(waypoint, false));
			this.button(ui, cx + 135, cy, 127, false, "Delete", null, mx, my, () -> {
				if (session != null) {
					session.waypoints.remove(waypoint.id());
					session.saveSoon(false);
				}
				this.map.selected = null;
			});
		} else {
			Marker marker = m;
			boolean tracking = m.id().equals(this.atlas.settings().tracked);
			this.button(ui, cx, cy, 127, true, tracking ? "Tracking" : "Track", MarkerGlyph.TRACK, mx, my, () -> this.track(marker.id()));
			this.button(ui, cx + 135, cy, 127, false, "Set waypoint", MarkerGlyph.PIN, mx, my, () -> this.waypointAt(marker, place));
		}
	}

	private void track(String id) {
		AtlasSettings settings = this.atlas.settings();
		settings.tracked = id.equals(settings.tracked) ? null : id;
		settings.save();
	}

	private void waypointAt(Marker marker, @Nullable Place place) {
		String n = place != null ? place.name() : marker.name();
		this.edit(Waypoint.create(n, (int) Math.floor(marker.x()), (int) Math.round(marker.y()), (int) Math.floor(marker.z()), marker.dimension(), 0), true);
	}

	/** A card button: primary (raised) or secondary (well), with an optional 12 px glyph before its label. */
	private void button(Ui ui, float x, float y, float w, boolean primary, String label, @Nullable MarkerGlyph glyph, float mx, float my, Runnable action) {
		boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 40;
		if (primary) {
			ui.panel(x, y, w, 40, 19, hover ? 0xFF2C2D31 : Theme.RAISED, Theme.HAIR_10);
		} else {
			ui.panel(x, y, w, 40, 19, hover ? Theme.ISLAND : Theme.WELL, Theme.HAIR_7);
		}
		float tw = ui.width(label, Ui.Font.F13) + (glyph != null ? 20 : 0);
		float tx = x + (w - tw) / 2;
		if (glyph != null) {
			int ink = primary ? Theme.INK : Theme.ink(0.7F);
			ui.glyph(glyph, tx, y + 14, 12, ink, ink);
			tx += 20;
		}
		ui.text(label, tx, y + 12, Ui.Font.F13, primary ? Theme.INK : Theme.INK_2);
		this.hit(x, y, w, 40, action);
	}

	private void editor(Ui ui, float x, float y, float mx, float my) {
		Waypoint w = this.editing;
		if (w == null) {
			return;
		}
		float cw = 296;
		float h = 17 + 40 + 14 + 36 + 14 + 11 + 8 + 24 + 14 + 40 + 17;
		ui.panel(x, y, cw, h, 20, Theme.ISLAND, Theme.HAIR_8);
		float cx = x + 17;
		float cy = y + 17;
		ui.panel(cx, cy, 40, 40, 12, Theme.WELL, Theme.HAIR_16);
		ui.glyph(MarkerGlyph.PIN, cx + 12, cy + 12, 16, Theme.alpha(0xFF000000 | Waypoint.COLOURS[w.colour()], 0.9F), Theme.INK);
		ui.text(this.editingIsNew ? "New waypoint" : "Edit waypoint", cx + 52, cy + 2.5F, Ui.Font.F17, Theme.INK);
		ui.text("X " + minus(w.x()) + "  ·  Y " + minus(w.y()) + "  ·  Z " + minus(w.z()), cx + 52, cy + 26.5F, Ui.Font.F85, 1, Theme.INK_4);
		boolean hClose = mx >= cx + 240 && mx <= cx + 262 && my >= cy + 9 && my <= cy + 31;
		ui.panel(cx + 240, cy + 9, 22, 22, 11, hClose ? Theme.ISLAND : Theme.WELL, Theme.HAIR_6);
		Icons.close(ui, cx + 246, cy + 15, Theme.INK_2);
		this.hit(cx + 240, cy + 9, 22, 22, this::cancelEdit);
		cy += 54;
		ui.panel(cx, cy, 262, 36, 12, Theme.WELL, this.name.focused ? Theme.HAIR_16 : Theme.HAIR_5);
		ui.flush();
		ui.g.enableScissor((int) cx + 8, (int) cy, (int) (cx + 254), (int) (cy + 36));
		this.name.draw(ui, cx + 11, cy + 10, 240, Ui.Font.F13, Theme.INK, Theme.INK_4);
		ui.flush();
		ui.g.disableScissor();
		this.hit(cx, cy, 262, 36, () -> this.name.focused = true);
		cy += 36 + 14;
		ui.text("COLOUR", cx, cy, Ui.Font.F85, 1, Theme.INK_4);
		cy += 19;
		for (int i = 0; i < Waypoint.COLOURS.length; i++) {
			float sx = cx + 2 + i * 32;
			boolean on = w.colour() == i;
			if (on) {
				ui.ring(sx + 11, cy + 11, 13, 1.2F, Theme.ink(0.7F));
			}
			ui.circle(sx + 11, cy + 11, 9, 0xFF000000 | Waypoint.COLOURS[i]);
			int index = i;
			this.hit(sx - 2, cy - 2, 26, 26, () -> this.editing = this.editing == null ? null : this.editing.withColour(index));
		}
		cy += 24 + 14;
		this.button(ui, cx, cy, 127, true, "Save", MarkerGlyph.PIN, mx, my, this::saveEdit);
		if (this.editingIsNew) {
			this.button(ui, cx + 135, cy, 127, false, "Cancel", null, mx, my, this::cancelEdit);
		} else {
			Waypoint target = w;
			this.button(ui, cx + 135, cy, 127, false, "Delete", null, mx, my, () -> {
				var session = this.atlas.session();
				if (session != null) {
					session.waypoints.remove(target.id());
					session.saveSoon(false);
				}
				this.editing = null;
				this.map.selected = null;
			});
		}
	}

	private void contextMenu(Ui ui, float mx, float my) {
		Menu m = this.menu;
		if (m == null) {
			return;
		}
		List<String> items = new ArrayList<>(List.of("Waypoint here", "Copy coordinates", "Track", "Centre here", "Export view as PNG"));
		String dimHere = this.map.dimensionOf(this.map.kind);
		if (dimHere.equals("minecraft:overworld")) {
			items.add(2, "Show in the Nether");
		} else if (dimHere.equals("minecraft:the_nether")) {
			items.add(2, "Show in the Overworld");
		}
		if (this.atlas.canTeleport()) {
			items.add(1, "Teleport here");
		}
		float w = 188;
		float h = 12 + items.size() * 34;
		float x = Math.min(m.x, this.mapX + this.mapW - w - 8);
		float y = Math.min(m.y, this.mapY + this.mapH - h - 8);
		// a click anywhere else closes the menu (registered first, so the items win)
		this.hit(this.mapX - 2000, this.mapY - 2000, 8000, 8000, () -> this.menu = null);
		ui.panel(x, y, w, h, 14, Theme.ISLAND, Theme.HAIR_10);
		for (int i = 0; i < items.size(); i++) {
			float iy = y + 6 + i * 34;
			boolean hover = mx >= x + 6 && mx <= x + w - 6 && my >= iy && my <= iy + 32;
			if (hover) {
				ui.round(x + 6, iy, w - 12, 32, 10, Theme.RAISED);
			}
			ui.text(items.get(i), x + 16, iy + 9, Ui.Font.F12, hover ? Theme.INK : Theme.INK_2);
			String item = items.get(i);
			this.hit(x + 6, iy, w - 12, 32, () -> this.menuAction(item, m));
		}
	}

	private void menuAction(String item, Menu m) {
		this.menu = null;
		String dim = this.map.dimensionOf(this.map.kind);
		int bx = (int) Math.floor(m.mapX);
		int bz = (int) Math.floor(m.mapZ);
		int by = this.atlas.heightAt(this.map.layer(this.map.kind), bx, bz);
		switch (item) {
			case "Waypoint here" -> this.edit(Waypoint.create("Waypoint", bx, by, bz, dim, 0), true);
			case "Teleport here" -> this.atlas.teleport(dim, bx, by, bz);
			case "Copy coordinates" -> Minecraft.getInstance().keyboardHandler.setClipboard(bx + " " + by + " " + bz);
			case "Show in the Nether" -> {
				// the same spot in the other dimension: an eighth of the way out from the middle of the world
				this.map.setKind(MapPanel.Kind.NETHER);
				this.map.centreOn(Math.floorDiv(bx, 8) + 0.5, Math.floorDiv(bz, 8) + 0.5);
			}
			case "Show in the Overworld" -> {
				this.map.setKind(MapPanel.Kind.SURFACE);
				this.map.centreOn(bx * 8.0 + 4, bz * 8.0 + 4);
			}
			case "Track" -> {
				// a point is tracked by a temporary waypoint on it (it goes by itself)
				var session = this.atlas.session();
				if (session != null) {
					Waypoint w = Waypoint.temporary("Tracked point", bx, by, bz, dim, 2, this.atlas.settings().temporaryMinutes * 60_000L);
					session.waypoints.put(w);
					session.saveSoon(false);
					this.track(dev.voidmc.atlas.client.AtlasClient.waypointMarkerId(w));
				}
			}
			case "Export view as PNG" -> this.exportView();
			default -> this.map.centreOn(m.mapX, m.mapZ);
		}
	}

	/** The map as shown, to a PNG in {@code <world map>/exports/}, on a worker; the result in the chat. */
	private void exportView() {
		var session = this.atlas.session();
		MapRenderer.Frame f = this.map.frame();
		var layer = this.map.layer(this.map.kind);
		if (session == null || f == null || layer == null) {
			return;
		}
		int x0 = (int) Math.floor(Math.min(f.mapX(f.x, f.y), f.mapX(f.x + f.w, f.y + f.h)));
		int z0 = (int) Math.floor(Math.min(f.mapZ(f.x, f.y), f.mapZ(f.x + f.w, f.y + f.h)));
		int x1 = (int) Math.ceil(Math.max(f.mapX(f.x, f.y), f.mapX(f.x + f.w, f.y + f.h)));
		int z1 = (int) Math.ceil(Math.max(f.mapZ(f.x, f.y), f.mapZ(f.x + f.w, f.y + f.h)));
		String name = "atlas-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png";
		java.nio.file.Path file = session.dir.resolve("exports").resolve(name);
		session.workers.execute(() -> {
			String message;
			try {
				var r = dev.voidmc.atlas.core.MapExport.export(layer::levelPixels, x0, z0, x1, z1, file);
				message = "Atlas: the map was saved as " + r.file().getFileName() + " (" + r.width() + " x " + r.height() + ")";
			} catch (java.io.IOException | RuntimeException e) {
				message = "Atlas: the map could not be saved: " + e.getMessage();
			}
			String text = message;
			Minecraft.getInstance().execute(() -> {
				if (Minecraft.getInstance().player != null) {
					Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
				}
			});
		});
	}

	// --- waypoints -------------------------------------------------------------------------------------------------

	private void newWaypointAtPlayer() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		this.edit(Waypoint.create("Waypoint", p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.level().dimension().identifier().toString(), 0), true);
	}

	/** Opens the editor on a waypoint (new or existing). */
	public void edit(Waypoint w, boolean isNew) {
		this.editing = w;
		this.editingIsNew = isNew;
		this.name.set(isNew && w.name().equals("Waypoint") ? "" : w.name());
		this.name.focused = true;
		this.search.focused = false;
		this.menu = null;
	}

	private void saveEdit() {
		Waypoint w = this.editing;
		var session = this.atlas.session();
		if (w == null || session == null) {
			return;
		}
		String n = this.name.text.isBlank() ? "Waypoint" : this.name.text;
		Waypoint saved = w.withName(n);
		session.waypoints.put(saved);
		session.saveSoon(false);
		this.editing = null;
		this.name.focused = false;
		this.map.selected = AtlasClient.waypointMarkerId(saved);
	}

	private void cancelEdit() {
		this.editing = null;
		this.name.focused = false;
	}

	// --- input -----------------------------------------------------------------------------------------------------

	public boolean mouseClicked(float x, float y, int button) {
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			this.pressY = y;
			for (int i = this.hits.size() - 1; i >= 0; i--) {
				Hit h = this.hits.get(i);
				if (h.contains(x, y)) {
					h.action.run();
					this.name.focused = this.editing != null && this.name.focused;
					return true;
				}
			}
			this.search.focused = false;
			if (this.map.inside(x, y)) {
				this.map.press(x, y);
				return true;
			}
		} else if (button == InputConstants.MOUSE_BUTTON_RIGHT && this.map.inside(x, y) && this.map.frame() != null) {
			MapRenderer.Frame f = this.map.frame();
			this.menu = new Menu(x, y, f.mapX(x, y), f.mapZ(x, y));
			return true;
		}
		this.menu = null;
		return false;
	}

	public boolean mouseReleased(float x, float y, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (this.scrubbing) {
			this.scrubbing = false;
			return true;
		}
		boolean dragged = this.map.release();
		if (dragged || !this.map.inside(x, y)) {
			return dragged;
		}
		long now = System.currentTimeMillis();
		boolean doubleClick = now - this.lastClick < 350 && Math.hypot(x - this.lastClickX, y - this.lastClickY) < 6;
		this.lastClick = now;
		this.lastClickX = x;
		this.lastClickY = y;
		Marker m = this.map.markerAt(x, y);
		if (doubleClick && m == null && this.map.frame() != null) {
			MapRenderer.Frame f = this.map.frame();
			int bx = (int) Math.floor(f.mapX(x, y));
			int bz = (int) Math.floor(f.mapZ(x, y));
			int by = this.atlas.heightAt(this.map.layer(this.map.kind), bx, bz);
			this.edit(Waypoint.create("Waypoint", bx, by, bz, this.map.dimensionOf(this.map.kind), 0), true);
			return true;
		}
		if (m != null && MarkerPainter.isCluster(m)) {
			// a count tile opens up: two steps in, round it
			this.map.zoomBy(2, x, y);
		} else if (m != null) {
			// a name on the land selects its place
			this.map.selected = m.id().startsWith("atlas:label:") ? "atlas:place:" + m.id().substring("atlas:label:".length()) : m.id();
			this.editing = null;
		} else if (!doubleClick) {
			this.map.selected = null;
		}
		return true;
	}

	public boolean mouseDragged(float x, float y) {
		if (this.scrubbing) {
			this.scrubTo(y);
			return true;
		}
		this.map.drag(x, y);
		return this.map.isDragging();
	}

	public boolean mouseScrolled(float x, float y, double amount) {
		if (x >= this.listX && x <= this.listX + LIST_W + 12 && y >= this.listY && y <= this.listY + this.listH) {
			if (this.map.kind == MapPanel.Kind.UNDERGROUND && y < this.listY + 380) {
				this.stepBand(amount > 0 ? 1 : -1);
				return true;
			}
			this.listScroll -= (float) (amount * 40);
			return true;
		}
		if (this.map.inside(x, y)) {
			if (this.under() && shiftDown()) {
				this.stepBand(amount > 0 ? 1 : -1);
				return true;
			}
			this.map.zoomBy(amount > 0 ? 1 : -1, x, y);
			return true;
		}
		return false;
	}

	private static boolean shiftDown() {
		return Minecraft.getInstance().hasShiftDown();
	}

	/** Moves the underground slice a step (8 blocks) up or down; the view stops following the player. */
	private void stepBand(int steps) {
		int band = this.map.band(this.map.kind) + steps;
		int top = DimensionData.bandOf(SCRUB_TOP);
		int bottom = DimensionData.bandOf(SCRUB_BOTTOM);
		this.map.setBand(Math.max(bottom, Math.min(top, band)));
	}

	/** Returns true if the key was used. Escape: closes the topmost card or the search first. */
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (this.editing != null) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				this.saveEdit();
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				this.cancelEdit();
				return true;
			}
			if (this.name.keyPressed(event)) {
				return true;
			}
		}
		if (this.searching && this.search.focused) {
			if (key == InputConstants.KEY_ESCAPE) {
				this.searching = false;
				this.search.set("");
				this.search.focused = false;
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				for (String section : new String[]{"sighted", "discovered", "settlements", "waypoints"}) {
					List<Row> rows = this.rows(section);
					if (!rows.isEmpty()) {
						this.select(rows.get(0));
						break;
					}
				}
				this.search.focused = false;
				return true;
			}
			if (this.search.keyPressed(event)) {
				return true;
			}
		}
		if (key == InputConstants.KEY_ESCAPE) {
			if (this.menu != null) {
				this.menu = null;
				return true;
			}
			if (this.map.selected != null) {
				this.map.selected = null;
				return true;
			}
			if (this.searching) {
				this.searching = false;
				this.search.set("");
				return true;
			}
			return false;
		}
		float cx = this.mapX + this.mapW / 2;
		float cy = this.mapY + this.mapH / 2;
		double step = 48 / Math.max(1e-6, this.map.zoom() / Minecraft.getInstance().getWindow().getGuiScale());
		switch (key) {
			case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> this.map.zoomBy(1, cx, cy);
			case InputConstants.KEY_MINUS, KEY_NUMPAD_MINUS -> this.map.zoomBy(-1, cx, cy);
			case InputConstants.KEY_LEFT -> this.map.pan(-step, 0);
			case InputConstants.KEY_RIGHT -> this.map.pan(step, 0);
			case InputConstants.KEY_UP -> this.map.pan(0, -step);
			case InputConstants.KEY_DOWN -> this.map.pan(0, step);
			case InputConstants.KEY_SPACE, InputConstants.KEY_C -> this.map.centreOnPlayer();
			case InputConstants.KEY_B -> this.newWaypointAtPlayer();
			case InputConstants.KEY_T -> {
				if (this.map.selected == null) {
					return false;
				}
				this.track(this.map.selected);
			}
			case InputConstants.KEY_W -> {
				Marker m = this.map.selected == null ? null : this.map.marker(this.map.selected);
				if (m == null && this.map.selected != null) {
					m = this.findAnywhere(this.map.selected);
				}
				if (m == null || this.atlas.waypointFor(m.id()) != null) {
					return false;
				}
				this.waypointAt(m, this.atlas.placeFor(m.id()));
			}
			case InputConstants.KEY_F -> {
				if (event.hasControlDown()) {
					this.searching = true;
					this.search.focused = true;
				} else if (this.under()) {
					this.map.setBand(null);
				} else {
					return false;
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	public boolean charTyped(CharacterEvent event) {
		if (this.editing != null && this.name.focused) {
			return this.name.charTyped(event);
		}
		if (this.searching && this.search.focused) {
			return this.search.charTyped(event);
		}
		return false;
	}

	/** Whether typing goes to a field (so the map key does not close the map). */
	public boolean typing() {
		return (this.editing != null && this.name.focused) || (this.searching && this.search.focused);
	}

	// --- text helpers ----------------------------------------------------------------------------------------------

	/** A number with a real minus sign, as the design writes them. */
	public static String minus(int v) {
		return v < 0 ? "–" + (-(long) v) : String.valueOf(v);
	}

	public static String facing(float yaw) {
		String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		return names[Math.floorMod(Math.round(yaw / 45), 8)];
	}

	/** "dark_forest" to "Dark forest". */
	public static String prettify(String path) {
		String s = path.replace('_', ' ').replace('/', ' ').strip();
		if (s.isEmpty()) {
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public static String dimensionName(String id) {
		return switch (id) {
			case "minecraft:overworld" -> "OVERWORLD";
			case "minecraft:the_nether" -> "NETHER";
			case "minecraft:the_end" -> "THE END";
			default -> prettify(id.substring(id.indexOf(':') + 1)).toUpperCase(Locale.ROOT);
		};
	}
}
