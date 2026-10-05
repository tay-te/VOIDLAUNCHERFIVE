package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerDetail;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.render.MapRenderer;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The map window's contents (the design's "Hub · Map" page): the Surface / Underground / Nether segments, search,
 * the rounded map with its readout, north mark, layer chips, zoom and scale, the list of waypoints, settlements and
 * discoveries on the right, the footer, and the cards (a marker's detail, the waypoint editor, the right-click menu).
 * Laid out in design pixels inside whatever rectangle it is given, so the full map screen and the VOID Hub can both
 * show it.
 */
public final class MapPage {
	private static final float LIST_W = 304;
	/** SDL's scancode for the keypad minus (InputConstants has no name for it). */
	private static final int KEY_NUMPAD_MINUS = 86;
	private static final float PAD = 28;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ROOT).withZone(ZoneId.systemDefault());

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
	private final Runnable onSettings;

	private record Hit(float x, float y, float w, float h, Runnable action) {
		boolean contains(float px, float py) {
			return px >= this.x && px <= this.x + this.w && py >= this.y && py <= this.y + this.h;
		}
	}

	private record Menu(float x, float y, double mapX, double mapZ) {
	}

	/** A row in the right-hand list. */
	private record Row(String id, MarkerGlyph glyph, String title, String subtitle, String right, String rightSub, double x, double z, String dimension,
		@Nullable Marker marker, @Nullable Source source) {
	}

	/** The list's rows, made again at most a few times a second (or when the search changes), not every frame. */
	private final java.util.Map<String, List<Row>> rowCache = new java.util.HashMap<>();
	private long rowsAt;
	private String rowsQuery = "";

	public MapPage(AtlasClient atlas, Runnable onSettings) {
		this.atlas = atlas;
		this.map = new MapPanel(atlas);
		this.onSettings = onSettings;
		this.search.placeholder = "Search waypoints and places";
		this.name.placeholder = "Name";
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.level.dimension().identifier().toString().equals("minecraft:the_nether")) {
			this.map.kind = MapPanel.Kind.NETHER;
		}
	}

	private void hit(float x, float y, float w, float h, Runnable action) {
		this.hits.add(new Hit(x, y, w, h, action));
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
		this.iconButton(ui, wx + PAD, wy + 31, mouseX, mouseY, () -> Icons.gear(ui, wx + PAD + 13, wy + 31 + 13, Theme.INK_2), this.onSettings);

		boolean overCard = this.overCard(mouseX, mouseY);
		boolean interactive = this.menu == null && !overCard;
		MapRenderer.Frame f = this.map.render(ui, this.mapX, this.mapY, this.mapW, this.mapH, 20, mouseX, mouseY, interactive);
		this.hoverX = interactive && f.inside(mouseX, mouseY) ? mouseX : Float.NaN;
		this.hoverY = mouseY;
		this.readout(ui, this.mapX + 16, this.mapY + 16);
		this.north(ui, this.mapX + this.mapW - 48, this.mapY + 16);
		this.layers(ui, this.mapX + 16, this.mapY + this.mapH - 55, mouseX, mouseY);
		this.zoomControl(ui, this.mapX + this.mapW - 52, this.mapY + this.mapH - 116, mouseX, mouseY);
		if (this.map.kind != MapPanel.Kind.SURFACE) {
			this.depthControl(ui, this.mapX + this.mapW - 52, this.mapY + this.mapH - 116 - 108, mouseX, mouseY);
		}
		this.scaleBar(ui, this.mapX + this.mapW - 62, this.mapY + this.mapH - 43, f);

		this.list(ui, mouseX, mouseY);
		this.footer(ui, wx, wy + wh - 36, ww);

		if (this.editing != null) {
			this.editor(ui, this.mapX + 16, this.mapY + 120, mouseX, mouseY);
		} else if (this.map.selected != null) {
			Marker m = this.map.marker(this.map.selected);
			if (m == null) {
				m = this.findAnywhere(this.map.selected);
			}
			if (m != null) {
				this.card(ui, m, this.mapX + 16, this.mapY + 120, mouseX, mouseY);
			}
		}
		if (this.menu != null) {
			this.contextMenu(ui, mouseX, mouseY);
		}
	}

	private boolean overCard(float x, float y) {
		boolean card = (this.editing != null || this.map.selected != null) && x >= this.mapX + 16 && x <= this.mapX + 16 + 288 && y >= this.mapY + 120
			&& y <= this.mapY + this.mapH - 64;
		return card;
	}

	private void segments(Ui ui, float centreX, float y, float mx, float my) {
		MapPanel.Kind[] kinds = MapPanel.Kind.values();
		float[] widths = new float[kinds.length];
		String[] counts = new String[kinds.length];
		float total = 8;
		for (int i = 0; i < kinds.length; i++) {
			counts[i] = String.valueOf(this.discoveryCount(kinds[i]));
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

	/** The places discovered on a view: the count on its segment. */
	private int discoveryCount(MapPanel.Kind kind) {
		var session = this.atlas.session();
		if (session == null) {
			return 0;
		}
		String dim = this.map.dimensionOf(kind);
		int n = 0;
		for (Place p : session.places.visible(dim)) {
			if (p.isDiscovered() && !p.isLabel() && (kind == MapPanel.Kind.NETHER || p.underground() == (kind == MapPanel.Kind.UNDERGROUND))) {
				n++;
			}
		}
		return n;
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

	private void readout(Ui ui, float x, float y) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		String[] biome = this.atlas.biomeAt(p.blockPosition());
		String bx = String.valueOf(p.getBlockX());
		String by = String.valueOf(p.getBlockY());
		String bz = minus(p.getBlockZ());
		bx = minus(p.getBlockX());
		String facing = facing(p.getYRot());
		float w1 = ui.width(biome[0], Ui.Font.F13) + (biome[1].isEmpty() ? 0 : 8 + ui.width(biome[1], Ui.Font.F85, 1));
		float w2 = 8 + 5 + ui.width(bx, Ui.Font.F12) + 12 + 8 + 5 + ui.width(by, Ui.Font.F12) + 12 + 7 + 5 + ui.width(bz, Ui.Font.F12) + 12 + 6 + 5
			+ ui.width(facing, Ui.Font.F11);
		float w = Math.max(w1, w2) + 28;
		ui.panel(x, y, w, 59, 14, Theme.ISLAND, Theme.HAIR_8);
		ui.text(biome[0], x + 15, y + 11, Ui.Font.F13, Theme.INK);
		if (!biome[1].isEmpty()) {
			ui.text(biome[1], x + 15 + ui.width(biome[0], Ui.Font.F13) + 8, y + 14, Ui.Font.F85, 1, Theme.INK_4);
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

	private void layers(Ui ui, float x, float y, float mx, float my) {
		List<MapLayer> layers = this.atlas.markersRegistry().layers();
		AtlasSettings settings = this.atlas.settings();
		float w = 12 + ui.width("LAYERS", Ui.Font.F85, 1) + 6 + 4;
		float[] cw = new float[layers.size()];
		for (int i = 0; i < layers.size(); i++) {
			cw[i] = 9 + 8 + 7 + ui.width(layers.get(i).label(), Ui.Font.F12) + 12;
			w += cw[i] + (i > 0 ? 2 : 0);
		}
		ui.panel(x, y, w, 39, 18, Theme.ISLAND, Theme.HAIR_8);
		ui.text("LAYERS", x + 13, y + 14, Ui.Font.F85, 1, Theme.INK_4);
		float cx = x + 12 + ui.width("LAYERS", Ui.Font.F85, 1) + 6;
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
		ui.panel(x, y + 0, 38, 100, 18, Theme.ISLAND, Theme.HAIR_8);
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
		String level = scaleLabel(this.map.targetZoom());
		ui.textCentred(level, x + 19, y + 44, Ui.Font.F9, 0.6F, Theme.INK_2);
		ui.rect(x + 1, y + 62, 36, ui.hairline(), Theme.HAIR_6);
		Icons.minus(ui, x + 11, y + 72, 16, 1.17F, Theme.INK);
		float cx = this.mapX + this.mapW / 2;
		float cy = this.mapY + this.mapH / 2;
		this.hit(x, y, 38, 37, () -> this.map.zoomBy(1, cx, cy));
		this.hit(x, y + 63, 38, 37, () -> this.map.zoomBy(-1, cx, cy));
	}

	/**
	 * The underground's depth: the band shown (its lowest block's height), and steps up and down through the bands the
	 * player has explored. A violet dot while it follows the player (a live value); stepping lets go of the player.
	 */
	private void depthControl(Ui ui, float x, float y, float mx, float my) {
		java.util.NavigableSet<Integer> bands = this.map.bands(this.map.kind);
		int band = this.map.band(this.map.kind);
		Integer up = bands.higher(band);
		Integer down = bands.lower(band);
		ui.panel(x, y, 38, 100, 18, Theme.ISLAND, Theme.HAIR_8);
		boolean hUp = up != null && mx >= x && mx <= x + 38 && my >= y && my <= y + 37;
		boolean hDown = down != null && mx >= x && mx <= x + 38 && my >= y + 63 && my <= y + 100;
		if (hUp) {
			ui.round(x + 3, y + 3, 32, 32, 15, Theme.RAISED);
		}
		if (hDown) {
			ui.round(x + 3, y + 65, 32, 32, 15, Theme.RAISED);
		}
		int upInk = up != null ? Theme.INK : Theme.INK_4;
		int downInk = down != null ? Theme.INK : Theme.INK_4;
		ui.convex(new float[]{x + 19, y + 14, x + 25, y + 22, x + 13, y + 22}, upInk);
		ui.convex(new float[]{x + 13, y + 78, x + 25, y + 78, x + 19, y + 86}, downInk);
		ui.rect(x + 1, y + 37, 36, ui.hairline(), Theme.HAIR_6);
		ui.textCentred("Y", x + 19, y + 40, Ui.Font.F9, 0.6F, Theme.INK_4);
		ui.textCentred(minus(band * 16), x + 19, y + 49, Ui.Font.F9, 0.6F, Theme.INK);
		if (this.map.followsPlayer()) {
			ui.round(x + 17, y + 58.5F, 4, 4, 1.2F, Theme.alpha(Theme.VIOLET, 0.95F));
		}
		ui.rect(x + 1, y + 62, 36, ui.hairline(), Theme.HAIR_6);
		if (up != null) {
			int to = up;
			this.hit(x, y, 38, 37, () -> this.map.setBand(to));
		}
		if (down != null) {
			int to = down;
			this.hit(x, y + 63, 38, 37, () -> this.map.setBand(to));
		}
		// the middle: back to following the player
		this.hit(x, y + 38, 38, 24, () -> this.map.setBand(null));
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

	// --- the list --------------------------------------------------------------------------------------------------

	private List<Row> rows(String section) {
		var session = this.atlas.session();
		List<Row> rows = new ArrayList<>();
		if (session == null) {
			return rows;
		}
		LocalPlayer p = Minecraft.getInstance().player;
		double px = p == null ? 0 : p.getX();
		double pz = p == null ? 0 : p.getZ();
		String here = Minecraft.getInstance().level == null ? "" : Minecraft.getInstance().level.dimension().identifier().toString();
		String query = this.search.text.strip().toLowerCase(Locale.ROOT);
		switch (section) {
			case "waypoints" -> {
				for (Waypoint w : session.waypoints.all()) {
					String sub = w.kind() == Waypoint.Kind.DEATH ? "DEATH  ·  " + DATE.format(Instant.ofEpochMilli(w.created())).toUpperCase(Locale.ROOT)
						: "WAYPOINT" + (w.y() < 40 ? "  ·  Y " + minus(w.y()) : "");
					boolean same = w.dimension().equals(here);
					rows.add(new Row(AtlasClient.waypointMarkerId(w), w.kind() == Waypoint.Kind.DEATH ? MarkerGlyph.CROSS : MarkerGlyph.PIN, w.name(),
						same ? sub : sub + "  ·  " + dimensionName(w.dimension()), same ? MarkerPainter.distance(w.distance(px, 0, pz)) : "—",
						same ? MarkerPainter.direction(px, pz, w.x() + 0.5, w.z() + 0.5) : "", w.x() + 0.5, w.z() + 0.5, w.dimension(), null, null));
				}
				rows.sort(Comparator.comparingDouble(r -> r.dimension.equals(here) ? Math.hypot(r.x - px, r.z - pz) : 1e12));
			}
			case "settlements" -> {
				MarkerQuery q = new MarkerQuery(here, -3e7, -3e7, 3e7, 3e7, px, pz, MarkerQuery.Purpose.LIST);
				for (Marker m : this.atlas.collect(q)) {
					if (m.style() == Marker.Style.SETTLEMENT) {
						rows.add(new Row(m.id(), m.glyph(), m.name(), m.subtitle(), MarkerPainter.distance(m.distanceTo(px, pz)),
							MarkerPainter.direction(px, pz, m.x(), m.z()), m.x(), m.z(), m.dimension(), m, m.source()));
					}
				}
				rows.sort(Comparator.comparingDouble(r -> Math.hypot(r.x - px, r.z - pz)));
			}
			default -> {
				// discovered: everything the player has been to; sighted: what they have seen and not yet visited
				boolean wantDiscovered = section.equals("discovered");
				for (Place d : session.places.all()) {
					if (d.state() == DiscoveryState.UNKNOWN || d.isDiscovered() != wantDiscovered || !wantDiscovered && d.isLabel()) {
						continue;
					}
					boolean same = d.dimension().equals(here);
					boolean unknown = !wantDiscovered && !d.isNatural();
					String title = unknown ? "Unexplored place" : d.name();
					String sub = AtlasClient.subtitle(d);
					MarkerGlyph glyph = unknown ? MarkerGlyph.UNKNOWN : MarkerPainter.glyphFor(d);
					rows.add(new Row(AtlasClient.placeMarkerId(d), glyph, title, sub,
						same ? MarkerPainter.distance(Math.hypot(d.x() - px, d.z() - pz)) : dimensionName(d.dimension()),
						same ? (d.underground() && p != null ? "↓ " + Math.max(0, p.getBlockY() - d.y()) : MarkerPainter.direction(px, pz, d.x(), d.z())) : "",
						d.x() + 0.5, d.z() + 0.5, d.dimension(), null, d.source()));
				}
				rows.sort(Comparator.comparingDouble(r -> r.dimension.equals(here) ? Math.hypot(r.x - px, r.z - pz) : 1e12));
			}
		}
		if (!query.isEmpty()) {
			rows.removeIf(r -> !r.title.toLowerCase(Locale.ROOT).contains(query) && !r.subtitle.toLowerCase(Locale.ROOT).contains(query));
		}
		return rows;
	}

	/** A section's rows, from the cache when it is fresh (a quarter of a second, the same search). */
	private List<Row> cachedRows(String section) {
		long now = System.currentTimeMillis();
		String query = this.search.text;
		if (now - this.rowsAt > 250 || !query.equals(this.rowsQuery)) {
			this.rowCache.clear();
			this.rowsAt = now;
			this.rowsQuery = query;
		}
		return this.rowCache.computeIfAbsent(section, this::rows);
	}

	private void list(Ui ui, float mx, float my) {
		List<Row> waypoints = this.cachedRows("waypoints");
		List<Row> settlements = this.cachedRows("settlements");
		List<Row> discovered = this.cachedRows("discovered");
		List<Row> sighted = this.cachedRows("sighted");
		float x = this.listX;
		ui.flush();
		ui.g.enableScissor((int) x - 2, (int) this.listY, (int) (x + LIST_W + 2), (int) (this.listY + this.listH));
		float y = this.listY - this.listScroll;
		y = this.section(ui, "WAYPOINTS", waypoints, x, y, mx, my, true);
		if (!settlements.isEmpty()) {
			y = this.section(ui, "SETTLEMENTS", settlements, x, y + 8, mx, my, false);
		}
		y = this.section(ui, "DISCOVERED", discovered, x, y + 8, mx, my, false);
		if (!sighted.isEmpty()) {
			y = this.section(ui, "SIGHTED", sighted, x, y + 8, mx, my, false);
		}
		this.listContent = y + this.listScroll - this.listY;
		ui.flush();
		ui.g.disableScissor();
		float max = Math.max(0, this.listContent - this.listH);
		this.listScroll = Math.max(0, Math.min(max, this.listScroll));
		if (max > 0) {
			// a slim, rounded scroll thumb
			float th = Math.max(24, this.listH * this.listH / this.listContent);
			float ty = this.listY + (this.listH - th) * (this.listScroll / max);
			ui.round(x + LIST_W + 8, ty, 3, th, 1.5F, Theme.HAIR_16);
		}
	}

	private float section(Ui ui, String label, List<Row> rows, float x, float y, float mx, float my, boolean add) {
		ui.text(label + "  ·  " + rows.size(), x + 4, y + 6, Ui.Font.F9, 1.1F, Theme.INK_4);
		if (add) {
			boolean hover = mx >= x + 282 && mx <= x + 304 && my >= y && my <= y + 22;
			ui.panel(x + 282, y, 22, 22, 11, hover ? Theme.ISLAND : Theme.WELL, Theme.HAIR_6);
			Icons.plus(ui, x + 287, y + 5, 12, 1.1F, Theme.INK_2);
			this.hit(x + 282, y, 22, 22, this::newWaypointAtPlayer);
		}
		y += 28;
		if (rows.isEmpty()) {
			String empty = switch (label) {
				case "WAYPOINTS" -> "Press B or double-click the map";
				case "DISCOVERED" -> "Explore to find places";
				default -> "None yet";
			};
			ui.panel(x, y, LIST_W, 40, 14, 0, Theme.HAIR_6);
			ui.text(empty, x + 14, y + 13, Ui.Font.F12, Theme.INK_4);
			return y + 46;
		}
		for (Row r : rows) {
			if (y + 52 < this.listY || y > this.listY + this.listH) {
				// off the list's window: only its space is counted (a long list costs nothing to scroll)
				y += 58;
				continue;
			}
			boolean selected = r.id.equals(this.map.selected);
			boolean hover = mx >= x && mx <= x + LIST_W && my >= y && my <= y + 52 && my >= this.listY && my <= this.listY + this.listH;
			ui.panel(x, y, LIST_W, 52, 14, selected ? 0xFF1F2023 : hover ? 0xFF1C1D20 : Theme.ISLAND, selected ? Theme.HAIR_16 : Theme.HAIR_7);
			ui.panel(x + 10, y + 10, 32, 32, 10, Theme.WELL, Theme.HAIR_5);
			ui.glyph(r.glyph, x + 17.5F, y + 17.5F, 17, Theme.ink(0.7F), Theme.INK);
			float rw = Math.max(ui.width(r.right, Ui.Font.F12), ui.width(r.rightSub, Ui.Font.F85, 1));
			float tw = LIST_W - 10 - 32 - 12 - 14 - rw - 10;
			ui.text(ui.fit(r.title, Ui.Font.F13, tw), x + 54, y + 9, Ui.Font.F13, Theme.INK);
			float chip = r.source == null ? 0 : MarkerPainter.sourceChipWidth(ui, r.source) + 6;
			String sub = ui.fit(r.subtitle, Ui.Font.F85, 1, Math.max(24, tw - chip));
			ui.text(sub, x + 54, y + 31, Ui.Font.F85, 1, Theme.INK_4);
			float subW = ui.width(sub, Ui.Font.F85, 1);
			if (r.source != null && subW + chip <= tw) {
				MarkerPainter.sourceChip(ui, r.source, x + 54 + subW + 6, y + 28.5F);
			}
			ui.textRight(r.right, x + LIST_W - 14, y + 10, Ui.Font.F12, 0, Theme.INK_2);
			ui.textRight(r.rightSub, x + LIST_W - 14, y + 31, Ui.Font.F85, 1, Theme.INK_4);
			if (y + 52 > this.listY && y < this.listY + this.listH) {
				Row row = r;
				this.hit(x, Math.max(y, this.listY), LIST_W, Math.min(y + 52, this.listY + this.listH) - Math.max(y, this.listY), () -> this.select(row));
			}
			y += 58;
		}
		return y;
	}

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
		if (!Float.isNaN(this.hoverX) && this.map.frame() != null) {
			MapRenderer.Frame f = this.map.frame();
			int bx = (int) Math.floor(f.mapX(this.hoverX, this.hoverY));
			int bz = (int) Math.floor(f.mapZ(this.hoverX, this.hoverY));
			left = "X " + minus(bx) + "  ·  Z " + minus(bz);
			String place = this.describe(bx, bz);
			if (place != null) {
				left += "  ·  " + place;
			}
		} else {
			var session = this.atlas.session();
			int d = session == null ? 0 : (int) session.places.all().stream().filter(pl -> pl.isDiscovered() && !pl.isLabel()).count();
			int w = session == null ? 0 : session.waypoints.all().size();
			left = d + " DISCOVERED  ·  " + w + " WAYPOINTS";
			if (this.atlas.busy() > 0) {
				left += "  ·  MAPPING " + this.atlas.busy();
			}
		}
		ui.text(left.toUpperCase(Locale.ROOT), wx + PAD, y, Ui.Font.F9, 1.1F, Theme.INK_4);
		String right = this.editing != null ? "ENTER SAVES  ·  ESC CANCELS"
			: this.map.selected != null ? "ESC CLEARS THE SELECTION" : "SCROLL TO ZOOM  ·  DRAG TO PAN  ·  DOUBLE-CLICK ADDS A WAYPOINT";
		ui.textRight(right, wx + ww - PAD, y, Ui.Font.F9, 1.1F, Theme.INK_4);
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

	// --- cards -----------------------------------------------------------------------------------------------------

	private @Nullable Marker findAnywhere(String id) {
		var session = this.atlas.session();
		if (session == null) {
			return null;
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

	private void card(Ui ui, Marker m, float x, float y, float mx, float my) {
		float w = 288;
		LocalPlayer p = Minecraft.getInstance().player;
		double px = p == null ? m.x() : p.getX();
		double pz = p == null ? m.z() : p.getZ();
		boolean sameDim = Minecraft.getInstance().level != null && Minecraft.getInstance().level.dimension().identifier().toString().equals(m.dimension());
		List<MarkerDetail.Readout> readouts = new ArrayList<>();
		if (sameDim) {
			readouts.add(new MarkerDetail.Readout(String.valueOf(Math.round(m.distanceTo(px, pz))), "METRES " + MarkerPainter.direction(px, pz, m.x(), m.z())));
		}
		MarkerDetail detail = m.detail();
		if (detail != null) {
			readouts.addAll(0, detail.readouts());
		}
		if (readouts.size() < 3) {
			readouts.add(new MarkerDetail.Readout(minus((int) Math.round(m.y())), "LEVEL"));
		}
		Waypoint waypoint = this.atlas.waypointFor(m.id());
		Place place = this.atlas.placeFor(m.id());
		if (readouts.size() < 3 && place != null) {
			if (place.isDiscovered()) {
				readouts.add(new MarkerDetail.Readout(DATE.format(Instant.ofEpochMilli(place.discovered())), "FOUND"));
			} else if (place.sighted() != 0) {
				readouts.add(new MarkerDetail.Readout(DATE.format(Instant.ofEpochMilli(place.sighted())), "SIGHTED"));
			}
		}
		if (readouts.size() < 3 && waypoint != null) {
			readouts.add(new MarkerDetail.Readout(DATE.format(Instant.ofEpochMilli(waypoint.created())), waypoint.kind() == Waypoint.Kind.DEATH ? "DIED" : "MADE"));
		}
		while (readouts.size() > 3) {
			readouts.remove(readouts.size() - 1);
		}
		// height first, to size the card
		float h = 16 + 40 + 14 + 55;
		if (detail != null && detail.meter() != null) {
			h += 14 + 51;
		}
		if (detail != null) {
			for (MarkerDetail.Section s : detail.sections()) {
				h += 14 + 11 + s.rows().size() * 42;
			}
		}
		h += 14 + 40 + 16;
		h = Math.min(h, this.mapH - 120 - 64);
		ui.panel(x, y, w, h, 20, Theme.ISLAND, Theme.HAIR_10);
		float cx = x + 16;
		float cy = y + 16;
		// head
		ui.panel(cx, cy, 40, 40, 12, Theme.WELL, Theme.HAIR_6);
		ui.glyph(m.glyph(), cx + 9.5F, cy + 9.5F, 21, Theme.ink(0.8F), Theme.INK);
		String title = m.name().isEmpty() ? (place != null && !place.isDiscovered() ? "Unexplored place" : "Place") : m.name();
		ui.text(ui.fit(title, Ui.Font.F16, w - 32 - 52 - 30), cx + 52, cy + 3, Ui.Font.F16, Theme.INK);
		String subtitle = m.subtitle().isEmpty() ? (waypoint != null ? "WAYPOINT" : "PLACE") : m.subtitle();
		// where it comes from, as a chip after the caps line (seen, exact, a VOID server, the mod that added it)
		Source source = m.source();
		float chip = source == null ? 0 : MarkerPainter.sourceChipWidth(ui, source) + 6;
		String sub = ui.fit(subtitle, Ui.Font.F85, 1, w - 32 - 52 - 30 - chip);
		ui.text(sub, cx + 52, cy + 26, Ui.Font.F85, 1, Theme.INK_4);
		if (source != null) {
			MarkerPainter.sourceChip(ui, source, cx + 52 + ui.width(sub, Ui.Font.F85, 1) + 6, cy + 23.5F);
		}
		boolean hClose = mx >= cx + 230 && mx <= cx + 254 && my >= cy + 8 && my <= cy + 32;
		ui.panel(cx + 230, cy + 8, 24, 24, 12, hClose ? Theme.ISLAND : Theme.WELL, Theme.HAIR_6);
		Icons.close(ui, cx + 236, cy + 14, Theme.INK_2);
		this.hit(cx + 230, cy + 8, 24, 24, () -> this.map.selected = null);
		cy += 40 + 14;
		// readouts
		float rw = (254 - 8 * (readouts.size() - 1)) / Math.max(1, readouts.size());
		for (int i = 0; i < readouts.size(); i++) {
			MarkerDetail.Readout r = readouts.get(i);
			float rx = cx + i * (rw + 8);
			ui.panel(rx, cy, rw, 55, 12, Theme.WELL, Theme.HAIR_5);
			ui.text(ui.fit(r.value(), Ui.Font.F20, rw - 20), rx + 11, cy + 8, Ui.Font.F20, Theme.INK);
			ui.text(ui.fit(r.label(), Ui.Font.F85, 1, rw - 20), rx + 11, cy + 36, Ui.Font.F85, 1, Theme.INK_4);
		}
		cy += 55;
		if (detail != null && detail.meter() != null) {
			cy += 14;
			MarkerDetail.Meter meter = detail.meter();
			ui.text(meter.label().toUpperCase(Locale.ROOT), cx, cy + 2, Ui.Font.F85, 1, Theme.INK_4);
			ui.textRight(meter.valueText(), cx + 254, cy, Ui.Font.F12, 0, Theme.INK);
			int cells = 20;
			double t = (meter.value() - meter.min()) / Math.max(1e-9, meter.max() - meter.min());
			int at = (int) Math.round(t * (cells - 1));
			int mid = cells / 2;
			for (int i = 0; i < cells; i++) {
				boolean filled = at >= mid ? i >= mid && i < at : i < mid && i > at;
				int c = i == at ? Theme.alpha(Theme.VIOLET, 0.95F) : filled ? Theme.ink(0.42F) : Theme.ink(0.1F);
				ui.round(cx + i * 12.35F + (i >= mid ? 2.8F : 0), cy + 23, 9.3F, 9, 2.7F, c);
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
					ui.panel(cx, cy, 254, 36, 12, Theme.WELL, Theme.HAIR_5);
					ui.text(ui.fit(row.text(), Ui.Font.F12, 180), cx + 11, cy + 10.5F, Ui.Font.F12, row.active() ? Theme.INK : Theme.INK_2);
					if (row.tag() != null && !row.tag().isEmpty()) {
						String tag = row.tag().toUpperCase(Locale.ROOT);
						float tw = ui.width(tag, Ui.Font.F85, 1) + 14;
						float tx = cx + 254 - 10 - tw;
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
		cy += 14;
		// actions
		if (waypoint != null) {
			this.button(ui, cx, cy, 163, true, "Edit", mx, my, () -> this.edit(waypoint, false), true);
			this.button(ui, cx + 171, cy, 83, false, "Delete", mx, my, () -> {
				var session = this.atlas.session();
				if (session != null) {
					session.waypoints.remove(waypoint.id());
					session.saveSoon(false);
				}
				this.map.selected = null;
			}, false);
		} else {
			Marker marker = m;
			this.button(ui, cx, cy, 163, true, "Set waypoint", mx, my, () -> {
				Waypoint w2 = Waypoint.create(marker.name(), (int) Math.floor(marker.x()), (int) Math.round(marker.y()), (int) Math.floor(marker.z()),
					marker.dimension(), 0);
				this.edit(w2, true);
			}, true);
			this.button(ui, cx + 171, cy, 83, false, "Centre", mx, my, () -> {
				if (marker.dimension().equals(this.map.dimensionOf(this.map.kind))) {
					this.map.centreOn(marker.x(), marker.z());
				}
			}, false);
		}
	}

	private void button(Ui ui, float x, float y, float w, boolean primary, String label, float mx, float my, Runnable action, boolean pin) {
		boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 40;
		if (primary) {
			ui.panel(x, y, w, 40, 20, hover ? 0xFF2A2B2F : Theme.BUTTON, Theme.HAIR_8);
			float tw = ui.width(label, Ui.Font.F13) + (pin ? 24 : 0);
			float tx = x + (w - tw) / 2;
			if (pin) {
				Icons.pin(ui, tx, y + 12, Theme.INK);
				tx += 24;
			}
			ui.text(label, tx, y + 12, Ui.Font.F13, Theme.INK);
		} else {
			ui.panel(x, y, w, 40, 20, hover ? Theme.ISLAND : Theme.WELL, Theme.HAIR_8);
			ui.textCentred(label, x + w / 2, y + 12, Ui.Font.F13, 0, Theme.INK_2);
		}
		this.hit(x, y, w, 40, action);
	}

	private void editor(Ui ui, float x, float y, float mx, float my) {
		Waypoint w = this.editing;
		if (w == null) {
			return;
		}
		float cw = 288;
		float h = 16 + 40 + 14 + 36 + 14 + 11 + 8 + 24 + 14 + 40 + 16;
		ui.panel(x, y, cw, h, 20, Theme.ISLAND, Theme.HAIR_10);
		float cx = x + 16;
		float cy = y + 16;
		ui.panel(cx, cy, 40, 40, 12, Theme.WELL, Theme.HAIR_6);
		ui.glyph(MarkerGlyph.PIN, cx + 9.5F, cy + 9.5F, 21, Theme.alpha(0xFF000000 | Waypoint.COLOURS[w.colour()], 0.9F), Theme.INK);
		ui.text(this.editingIsNew ? "New waypoint" : "Edit waypoint", cx + 52, cy + 3, Ui.Font.F16, Theme.INK);
		ui.text("X " + minus(w.x()) + "  ·  Y " + minus(w.y()) + "  ·  Z " + minus(w.z()), cx + 52, cy + 26, Ui.Font.F85, 1, Theme.INK_4);
		boolean hClose = mx >= cx + 230 && mx <= cx + 254 && my >= cy + 8 && my <= cy + 32;
		ui.panel(cx + 230, cy + 8, 24, 24, 12, hClose ? Theme.ISLAND : Theme.WELL, Theme.HAIR_6);
		Icons.close(ui, cx + 236, cy + 14, Theme.INK_2);
		this.hit(cx + 230, cy + 8, 24, 24, this::cancelEdit);
		cy += 54;
		ui.panel(cx, cy, 254, 36, 12, Theme.WELL, this.name.focused ? Theme.HAIR_16 : Theme.HAIR_5);
		ui.flush();
		ui.g.enableScissor((int) cx + 8, (int) cy, (int) (cx + 246), (int) (cy + 36));
		this.name.draw(ui, cx + 11, cy + 10, 232, Ui.Font.F13, Theme.INK, Theme.INK_4);
		ui.flush();
		ui.g.disableScissor();
		this.hit(cx, cy, 254, 36, () -> this.name.focused = true);
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
		this.button(ui, cx, cy, 163, true, "Save", mx, my, this::saveEdit, true);
		if (this.editingIsNew) {
			this.button(ui, cx + 171, cy, 83, false, "Cancel", mx, my, this::cancelEdit, false);
		} else {
			Waypoint target = w;
			this.button(ui, cx + 171, cy, 83, false, "Delete", mx, my, () -> {
				var session = this.atlas.session();
				if (session != null) {
					session.waypoints.remove(target.id());
					session.saveSoon(false);
				}
				this.editing = null;
				this.map.selected = null;
			}, false);
		}
	}

	private void contextMenu(Ui ui, float mx, float my) {
		Menu m = this.menu;
		if (m == null) {
			return;
		}
		List<String> items = new ArrayList<>(List.of("Add waypoint here", "Centre here"));
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
			case "Add waypoint here" -> this.edit(Waypoint.create("Waypoint", bx, by, bz, dim, 0), true);
			case "Teleport here" -> this.atlas.teleport(dim, bx, by, bz);
			default -> this.map.centreOn(m.mapX, m.mapZ);
		}
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
		if (m != null) {
			this.map.selected = m.id();
			this.editing = null;
		} else if (!doubleClick) {
			this.map.selected = null;
		}
		return true;
	}

	public boolean mouseDragged(float x, float y) {
		this.map.drag(x, y);
		return this.map.isDragging();
	}

	public boolean mouseScrolled(float x, float y, double amount) {
		if (x >= this.listX && x <= this.listX + LIST_W + 12 && y >= this.listY && y <= this.listY + this.listH) {
			this.listScroll -= (float) (amount * 40);
			return true;
		}
		if (this.map.inside(x, y)) {
			this.map.zoomBy(amount > 0 ? 1 : -1, x, y);
			return true;
		}
		return false;
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
				for (String section : new String[]{"waypoints", "settlements", "discovered", "sighted"}) {
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
			case InputConstants.KEY_F -> {
				if (event.hasControlDown()) {
					this.searching = true;
					this.search.focused = true;
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
