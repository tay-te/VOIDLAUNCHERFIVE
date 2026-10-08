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
	/** The depth scrubber and side view show this many blocks of the world's height at a time (25 cells of 8). */
	private static final int SCRUB_SPAN = 192;
	/** The window shown: from the bottom of the world's height (-64 to 128 in a vanilla overworld at cave depth). */
	private int scrubBottom = -64;
	private int scrubTop = 128;

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
	/** The right panel's tab above ground: 0 Places, 1 Legend, 2 Journal. */
	public int rightTab;
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

	/** The world picker's list is open. */
	private boolean pickerOpen;
	/** The search's results, by group, made again when the query changes (or every half second). */
	private record SearchHit(int group, String id, MarkerGlyph glyph, boolean hollow, String title, String sub, String right, double x, double z,
		String dimension) {
	}

	private List<SearchHit> searchHits = List.of();
	private String searchHitsFor = "";
	private long searchHitsAt;
	private int[] searchCounts = new int[3];
	private int searchGroup;
	/** Waypoints found in other map mods' files for this world, offered once (null: none, or not offered). */
	private @Nullable List<dev.voidmc.atlas.core.waypoints.ImportedWaypoint> importOffer;
	private boolean importKeepColours = true;
	private boolean importLooked;
	/** The export dialog, while open. */
	private @Nullable Export export;

	private static final class Export {
		int area = 1;
		int scale = 2;
		boolean markers = true;
		boolean transparent = true;
		double bx0, bz0, bx1, bz1;
		boolean dragging;
		double dragX, dragZ;
		MapPanel preview;
		float px, py, pw, ph;
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
		this.shown();
	}

	/** Shown (again): the layer follows the player's dimension and whether they are underground, as on a first open. */
	public void shown() {
		Minecraft mc = Minecraft.getInstance();
		// the depth follows the player again on each open, as on a first open
		this.map.setBand(null);
		boolean nether = mc.level != null && mc.level.dimension().identifier().toString().equals("minecraft:the_nether");
		if (nether) {
			this.map.setKind(MapPanel.Kind.NETHER);
		} else if (this.atlas.underground()) {
			this.map.setKind(MapPanel.Kind.UNDERGROUND);
		} else if (this.map.kind != MapPanel.Kind.SURFACE) {
			this.map.setKind(MapPanel.Kind.SURFACE);
		}
	}

	private void hit(float x, float y, float w, float h, Runnable action) {
		this.hits.add(new Hit(x, y, w, h, action));
	}

	private boolean under() {
		return this.map.kind != MapPanel.Kind.SURFACE;
	}

	// --- drawing ---------------------------------------------------------------------------------------------------

	/** The header: segment bar, search and world picker as 36 px pills, 20 from the top; the map 76 down (VOID Hub's header, Figma 2e B). */
	static final float HEADER = 76;
	static final float BAR = 36;
	static final float BAR_Y = 20;

	/** Draws the page into a window rectangle (design pixels). */
	public void render(Ui ui, float wx, float wy, float ww, float wh, float mouseX, float mouseY) {
		this.hits.clear();
		this.mapX = wx + PAD;
		this.mapY = wy + HEADER;
		this.mapW = ww - PAD * 2 - 20 - LIST_W;
		this.mapH = wh - HEADER - 56;
		this.listX = this.mapX + this.mapW + 20;
		this.listY = this.mapY;
		this.listH = this.mapH;

		this.segments(ui, wx + ww / 2, wy + BAR_Y, mouseX, mouseY);
		this.searchBox(ui, wx + ww - PAD - BAR, wy + BAR_Y, mouseX, mouseY);
		this.worldPicker(ui, wx + PAD, wy + BAR_Y, mouseX, mouseY);
		this.lookForImports();

		boolean overCard = this.overCard(mouseX, mouseY);
		boolean interactive = this.menu == null && !overCard;
		MapRenderer.Frame f = this.map.render(ui, this.mapX, this.mapY, this.mapW, this.mapH, 20, mouseX, mouseY, interactive);
		this.hoverX = interactive && f.inside(mouseX, mouseY) ? mouseX : Float.NaN;
		this.hoverY = mouseY;
		this.aboveOrBelow(ui, f);
		if (this.map.biomeLook()) {
			this.biomeHover(ui, f);
		} else {
			this.landHover(ui, f);
		}
		float rw = this.readout(ui, this.mapX + 16, this.mapY + 16);
		this.lookToggle(ui, this.mapX + 16 + rw + 10, this.mapY + 16 + 8.5F, mouseX, mouseY);
		this.north(ui, this.mapX + this.mapW - 48, this.mapY + 16);
		// the chips keep left of the scale bar (at its widest) and its gap
		this.layers(ui, this.mapX + 16, this.mapY + this.mapH - 55, this.mapW - 62 - 16 - 12 - scaleBarMaxWidth(ui), mouseX, mouseY);
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
		} else if (this.map.selected != null && !this.friendSelected()) {
			Marker m = this.map.marker(this.map.selected);
			if (m == null) {
				m = this.findAnywhere(this.map.selected);
			}
			if (m != null) {
				this.card(ui, m, this.mapX + 16, this.mapY + 88, mouseX, mouseY);
			}
		}
		if (this.importOffer != null && this.map.kind != MapPanel.Kind.UNDERGROUND && this.editing == null) {
			this.importCard(ui, this.listX, this.listY, LIST_W, mouseX, mouseY);
		}
		if (this.searching && !this.search.text.isBlank()) {
			this.searchResults(ui, wx + ww - PAD - 380, wy + BAR_Y + BAR + 6, 380, mouseX, mouseY);
		}
		if (this.pickerOpen) {
			this.pickerList(ui, wx + PAD, wy + BAR_Y + BAR + 8, mouseX, mouseY);
		}
		this.layersPopover(ui, mouseX, mouseY);
		if (this.menu != null) {
			this.contextMenu(ui, mouseX, mouseY);
		}
		if (this.export != null) {
			this.exportDialog(ui, wx, wy, ww, wh, mouseX, mouseY);
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
		float total = 6;
		for (int i = 0; i < kinds.length; i++) {
			counts[i] = String.valueOf(this.placeCount(kinds[i]));
			widths[i] = 28 + ui.width(kinds[i].label, Ui.Font.F13) + 6 + ui.width(counts[i], Ui.Font.F11);
			total += widths[i] + (i > 0 ? 2 : 0);
		}
		float x = centreX - total / 2;
		ui.panel(x, y, total, BAR, BAR / 2, Theme.WELL, Theme.HAIR_6);
		float sx = x + 3;
		for (int i = 0; i < kinds.length; i++) {
			MapPanel.Kind k = kinds[i];
			boolean active = this.map.kind == k;
			float w = widths[i];
			boolean hover = !active && mx >= sx && mx <= sx + w && my >= y + 3 && my <= y + 33;
			if (active) {
				ui.panel(sx, y + 3, w, 30, 15, Theme.RAISED, Theme.HAIR_8);
			} else if (hover) {
				ui.round(sx, y + 3, w, 30, 15, Theme.ISLAND);
			}
			ui.text(k.label, sx + 14, y + 9, Ui.Font.F13, active ? Theme.INK : Theme.INK_2);
			ui.text(counts[i], sx + 14 + ui.width(k.label, Ui.Font.F13) + 6, y + 11, Ui.Font.F11, active ? Theme.INK_2 : Theme.INK_4);
			this.hit(sx, y + 3, w, 30, () -> this.map.setKind(k));
			sx += w + 2;
		}
	}

	private void iconButton(Ui ui, float x, float y, float mx, float my, Runnable icon, Runnable action) {
		boolean hover = mx >= x && mx <= x + BAR && my >= y && my <= y + BAR;
		ui.panel(x, y, BAR, BAR, BAR / 2, hover ? Theme.ISLAND : Theme.WELL, Theme.HAIR_6);
		icon.run();
		this.hit(x, y, BAR, BAR, action);
	}

	private void searchBox(Ui ui, float x, float y, float mx, float my) {
		if (!this.searching) {
			this.iconButton(ui, x, y, mx, my, () -> Icons.search(ui, x + 10.5F, y + 10.5F, Theme.INK_2), () -> {
				this.searching = true;
				this.search.focused = true;
			});
			return;
		}
		float w = 380;
		float bx = x + BAR - w;
		ui.panel(bx, y, w, BAR, BAR / 2, Theme.WELL, this.search.focused ? Theme.HAIR_16 : Theme.HAIR_6);
		Icons.search(ui, bx + 15, y + 10.5F, Theme.INK_3);
		ui.flush();
		ui.g.enableScissor((int) (bx + 40), (int) y, (int) (bx + w - 34), (int) (y + BAR));
		this.search.draw(ui, bx + 40, y + 10, w - 76, Ui.Font.F13, Theme.INK, Theme.INK_4);
		ui.flush();
		ui.g.disableScissor();
		Icons.close(ui, bx + w - 28, y + 12, Theme.INK_2);
		this.hit(bx + w - 34, y, 34, BAR, () -> {
			this.searching = false;
			this.search.set("");
			this.search.focused = false;
		});
		this.hit(bx, y, w - 34, BAR, () -> this.search.focused = true);
	}

	/** The readout: the named region (or biome) the player is in, its source, the position and the heading. */
	private float readout(Ui ui, float x, float y) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			return 0;
		}
		String[] biome = this.atlas.biomeAt(p.blockPosition());
		Place region = this.atlas.regionHere();
		if (region != null && this.map.kind == MapPanel.Kind.SURFACE && region.source().kind() == Source.Kind.PLUGIN) {
			// a mod named the land (Expanse): its name leads, with the source chip, and the biome moves to the second line
			return this.namedReadout(ui, x, y, p, region, biome[0]);
		}
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
		return w;
	}

	/** The readout of a region a mod named (atlas/readout, mod region): "Woodland Glades  [from Expanse]" over "FOREST · X 412 Y 71 Z -1288 · NE". */
	private float namedReadout(Ui ui, float x, float y, LocalPlayer p, Place region, String biome) {
		String name = region.name();
		String line = biome.toUpperCase(java.util.Locale.ROOT) + "  ·  X " + minus(p.getBlockX()) + "  Y " + minus(p.getBlockY()) + "  Z " + minus(p.getBlockZ())
			+ "  ·  " + facing(p.getYRot());
		float chip = MarkerPainter.smallSourceChipWidth(ui, region.source());
		float w1 = ui.width(name, Ui.Font.F13) + 10 + chip;
		float w2 = ui.width(line, Ui.Font.F85, 1);
		float w = Math.max(w1, w2) + 30;
		ui.panel(x, y, w, 59, 14, Theme.ISLAND, Theme.HAIR_8);
		ui.text(name, x + 15, y + 11, Ui.Font.F13, Theme.INK);
		MarkerPainter.smallSourceChip(ui, region.source(), x + 15 + ui.width(name, Ui.Font.F13) + 10, y + 9);
		ui.text(line, x + 15, y + 35, Ui.Font.F85, 1, Theme.INK_4);
		return w;
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

	/**
	 * The layer chips: Terrain, Names, Landmarks, Places, Waypoints, and Friends or, underground, Water & lava. Other
	 * mods' layers wait behind a "More · N" chip in a small popover; when even Atlas's own do not fit beside the scale bar,
	 * every layer goes into it behind one "Layers · N" chip. Never over the scale bar.
	 */
	private void layers(Ui ui, float x, float y, float maxW, float mx, float my) {
		List<MapLayer> all = this.atlas.markersRegistry().layers();
		List<MapLayer> own = new ArrayList<>();
		List<MapLayer> theirs = new ArrayList<>();
		for (MapLayer l : all) {
			if (l.id().equals(MapLayer.FLUIDS) && !this.under() || l.id().equals(MapLayer.PLAYERS) && this.under()
				|| l.id().equals(MapLayer.STRUCTURES)) {
				continue;
			}
			(l.id().startsWith("atlas:") ? own : theirs).add(l);
		}
		AtlasSettings settings = this.atlas.settings();
		float head = 12 + ui.width("LAYERS", Ui.Font.F85, 1) + 10;
		List<MapLayer> inline = own;
		List<MapLayer> popover = theirs;
		String moreLabel = theirs.isEmpty() ? null : "More  ·  " + theirs.size();
		float w = head + 4 + chipsWidth(ui, inline) + (moreLabel == null ? 0 : 2 + moreWidth(ui, moreLabel));
		if (w > maxW) {
			// not even Atlas's own fit: one chip for every layer
			inline = List.of();
			popover = new ArrayList<>(own);
			popover.addAll(theirs);
			moreLabel = "Layers  ·  " + popover.size();
			w = 4 + moreWidth(ui, moreLabel) + 4;
			head = 0;
		}
		this.layersPopover = popover;
		if (popover.isEmpty()) {
			this.layersOpen = false;
		}
		ui.panel(x, y, w, 39, 18, Theme.ISLAND, Theme.HAIR_8);
		float cx = x + 4;
		if (head > 0) {
			ui.text("LAYERS", x + 13, y + 14, Ui.Font.F85, 1, Theme.INK_4);
			cx = x + head;
		}
		for (MapLayer l : inline) {
			float cw = chipWidth(ui, l);
			boolean on = settings.layerVisible(l.id());
			boolean hover = mx >= cx && mx <= cx + cw && my >= y + 5 && my <= y + 34;
			if (on) {
				ui.panel(cx, y + 5, cw, 29, 13, hover ? 0xFF2C2D31 : Theme.RAISED, Theme.HAIR_8);
			} else if (hover) {
				ui.round(cx, y + 5, cw, 29, 13, Theme.WELL);
			}
			ui.round(cx + 9, y + 15.5F, 8, 8, 2.5F, on ? Theme.ink(0.85F) : Theme.ink(0.18F));
			ui.text(label(l), cx + 24, y + 11, Ui.Font.F12, on ? Theme.INK : Theme.INK_4);
			String id = l.id();
			this.hit(cx, y + 5, cw, 29, () -> settings.toggleLayer(id));
			cx += cw + 2;
		}
		if (moreLabel != null) {
			float cw = moreWidth(ui, moreLabel);
			boolean hover = mx >= cx && mx <= cx + cw && my >= y + 5 && my <= y + 34;
			if (this.layersOpen) {
				ui.panel(cx, y + 5, cw, 29, 13, Theme.RAISED, Theme.HAIR_16);
			} else if (hover) {
				ui.round(cx, y + 5, cw, 29, 13, Theme.WELL);
			}
			ui.text(moreLabel, cx + 12, y + 11, Ui.Font.F12, this.layersOpen || hover ? Theme.INK : Theme.INK_2);
			Icons.chevron(ui, cx + cw - 22, y + 14, Theme.INK_3);
			this.hit(cx, y + 5, cw, 29, () -> this.layersOpen = !this.layersOpen);
			this.layersAnchorX = cx;
		}
		this.layersAnchorY = y;
	}

	private static float chipWidth(Ui ui, MapLayer l) {
		return 9 + 8 + 7 + ui.width(label(l), Ui.Font.F12) + 12;
	}

	private static float chipsWidth(Ui ui, List<MapLayer> layers) {
		float w = 0;
		for (int i = 0; i < layers.size(); i++) {
			w += chipWidth(ui, layers.get(i)) + (i > 0 ? 2 : 0);
		}
		return w;
	}

	private static float moreWidth(Ui ui, String label) {
		return 12 + ui.width(label, Ui.Font.F12) + 8 + 12 + 10;
	}

	private boolean layersOpen;
	private List<MapLayer> layersPopover = List.of();

	/** Opens (or closes) the layers popover, when there is one (the tour). */
	public void openLayers(boolean open) {
		this.layersOpen = open;
	}
	private float layersAnchorX;
	private float layersAnchorY;

	/**
	 * The layers popover: a small fixed panel over the "More" (or "Layers") chip, a row with a toggle for each layer in
	 * it. A click outside it closes it, as Esc does.
	 */
	private void layersPopover(Ui ui, float mx, float my) {
		if (!this.layersOpen || this.layersPopover.isEmpty()) {
			return;
		}
		AtlasSettings settings = this.atlas.settings();
		float w = 220;
		for (MapLayer l : this.layersPopover) {
			w = Math.max(w, 16 + ui.width(label(l), Ui.Font.F12) + 16 + 32 + 16);
		}
		float rowH = 36;
		float h = 12 + 20 + this.layersPopover.size() * rowH + 8;
		float x = Math.min(this.layersAnchorX, this.mapX + this.mapW - w - 16);
		float y = this.layersAnchorY - 8 - h;
		// outside it: closes it (registered first, so the rows win)
		this.hit(this.mapX - PAD, this.mapY - 100, this.mapW + PAD * 2 + 20 + LIST_W, this.mapH + 156, () -> this.layersOpen = false);
		ui.panel(x, y, w, h, 18, Theme.ISLAND, Theme.HAIR_10);
		ui.text("LAYERS", x + 16, y + 14, Ui.Font.F85, 1, Theme.INK_4);
		float ry = y + 12 + 20;
		for (MapLayer l : this.layersPopover) {
			boolean on = settings.layerVisible(l.id());
			boolean hover = mx >= x + 6 && mx <= x + w - 6 && my >= ry && my <= ry + rowH - 4;
			if (hover) {
				ui.round(x + 6, ry, w - 12, rowH - 4, 12, Theme.WELL);
			}
			ui.text(label(l), x + 16, ry + 8, Ui.Font.F12, on ? Theme.INK : Theme.INK_3);
			toggle(ui, x + w - 16 - 32, ry + 7, on);
			String id = l.id();
			this.hit(x + 6, ry, w - 12, rowH - 4, () -> settings.toggleLayer(id));
			ry += rowH;
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

	/** The scale bar at its widest (its bar near 84 design pixels, "16K BLOCKS"). */
	private static float scaleBarMaxWidth(Ui ui) {
		return 10 + 84 + 8 + ui.width("16K BLOCKS", Ui.Font.F85, 1) + 10;
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

	/** The right panel above ground: the Places, Legend and Journal tabs (Journal while the setting is on). */
	private void placesPanel(Ui ui, float mx, float my) {
		if (this.friendSelected() && this.atlas.social.available()) {
			this.friendPanel(ui, mx, my);
			return;
		}
		float x = this.listX;
		float y = this.listY;
		ui.panel(x, y, LIST_W, 42, 14, Theme.WELL, Theme.HAIR_6);
		int total = this.placeCount(this.map.kind);
		boolean journal = this.atlas.settings().journal;
		if (!journal && this.rightTab == 2) {
			this.rightTab = 0;
		}
		String[] labels = journal ? new String[]{"Places", "Legend", "Journal"} : new String[]{"Places", "Legend"};
		int n = labels.length;
		float tw = (LIST_W - 10 - (n - 1) * 2) / n;
		for (int i = 0; i < n; i++) {
			boolean active = i == this.rightTab;
			float sx = x + 5 + i * (tw + 2);
			if (active) {
				ui.panel(sx, y + 5, tw, 32, 10, Theme.RAISED, Theme.HAIR_8);
			} else if (mx >= sx && mx <= sx + tw && my >= y + 5 && my <= y + 37) {
				ui.round(sx, y + 5, tw, 32, 10, Theme.ISLAND);
			}
			ui.text(labels[i], sx + 14, y + 12, Ui.Font.F13, active ? Theme.INK : Theme.INK_2);
			if (i == 0 && n == 2) {
				ui.text(String.valueOf(total), sx + 14 + ui.width(labels[0], Ui.Font.F13) + 6, y + 14, Ui.Font.F11, active ? Theme.INK_2 : Theme.INK_4);
			}
			int tab = i;
			this.hit(sx, y + 5, tw, 32, () -> {
				this.rightTab = tab;
				this.listScroll = 0;
			});
		}
		float top = y + 48;
		float bottom = this.listY + this.listH;
		if (this.rightTab == 1) {
			if (this.map.biomeLook()) {
				this.biomeLegend(ui, x, top, bottom, mx, my, false);
			} else {
				this.legend(ui, x, top, bottom, mx, my);
			}
			return;
		}
		if (this.rightTab == 2) {
			this.journal(ui, x, top, bottom, mx, my);
			return;
		}
		// a tracked quest step first, then friends (with a VOID transport), then the marker states key at the foot and the
		// list between
		Marker quest = this.atlas.trackedMarker();
		if (quest != null && quest.style() == Marker.Style.QUEST && this.search.text.isBlank()) {
			String from = quest.source() == null ? "" : "  ·  " + quest.source().phrase().toUpperCase(Locale.ROOT);
			ui.text("TRACKED QUEST" + from, x + 4, top + 8, Ui.Font.F85, 1, Theme.INK_4);
			top += 28;
			LocalPlayer qp = Minecraft.getInstance().player;
			boolean same = qp != null && quest.dimension().equals(qp.level().dimension().identifier().toString());
			Row row = new Row(quest.id(), MarkerGlyph.QUEST, false, quest.name(), "QUEST" + (quest.subtitle().isEmpty() ? "" : "  ·  " + quest.subtitle()),
				same ? MarkerPainter.distance(quest.distanceTo(qp.getX(), qp.getZ())) : "—", same ? MarkerPainter.direction(qp.getX(), qp.getZ(), quest.x(), quest.z()) : "",
				quest.x(), quest.z(), quest.dimension());
			top = this.section(ui, null, List.of(row), x, top - 28, top - 28, bottom, mx, my, false) + 6;
		}
		if (this.atlas.social.available() && this.search.text.isBlank()) {
			top = this.friendsSection(ui, x, top, mx, my, bottom);
		}
		float keyH = 97;
		this.statesKey(ui, x, bottom - keyH);
		this.list(ui, top, bottom - keyH - 6, mx, my, List.of("sighted", "discovered", "settlements", "waypoints"),
			List.of("SIGHTED  ·  NOT VISITED", "DISCOVERED", "SETTLEMENTS", "WAYPOINTS"));
	}

	private static final java.time.format.DateTimeFormatter CLOCK = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
		.withZone(java.time.ZoneId.systemDefault());

	/**
	 * The Journal tab (Figma 375:37081): the totals (places found, distance travelled, deepest Y), how much of each biome
	 * area the player explored themselves (atlas/area meter: 20 cells, violet only on the newest cell of the area they
	 * are in), and the first discoveries by world day with the real clock time (atlas/journal entry).
	 */
	private void journal(Ui ui, float x, float top, float bottom, float mx, float my) {
		MapSession session = this.atlas.session();
		if (session == null) {
			return;
		}
		var journal = session.journal;
		var stats = journal.stats();
		ui.flush();
		ui.g.enableScissor((int) x - 2, (int) top, (int) (x + LIST_W + 2), (int) bottom);
		float y = top - this.listScroll;
		// the totals
		float cw = (LIST_W - 2 * 7) / 3;
		String[][] totals = {
			{String.valueOf(stats.places(dev.voidmc.atlas.api.DiscoveryState.DISCOVERED)), "PLACES FOUND"},
			{stats.distance() >= 1000 ? Math.round(stats.kilometres()) + " km" : Math.round(stats.distance()) + " m", "TRAVELLED"},
			{stats.deepestY() == null ? "—" : "Y " + minus(stats.deepestY()), "DEEPEST"}};
		for (int i = 0; i < 3; i++) {
			float tx = x + i * (cw + 7);
			ui.panel(tx, y, cw, 52, 12, Theme.ISLAND, Theme.HAIR_7);
			ui.text(totals[i][0], tx + 12, y + 9, Ui.Font.F16, Theme.INK);
			ui.text(totals[i][1], tx + 12, y + 33, Ui.Font.F85, 1, Theme.INK_4);
		}
		y += 52 + 20;
		// explored, by biome area: the one the player is in first, then the most explored
		Place here = this.atlas.regionHere();
		List<dev.voidmc.atlas.core.journal.AreaStat> areas = new ArrayList<>();
		var mine = here == null ? null : journal.area(here.id());
		if (mine != null) {
			areas.add(mine);
		}
		for (var a : journal.topAreas(6)) {
			if (areas.size() >= 4) {
				break;
			}
			if (mine == null || !a.id().equals(mine.id())) {
				areas.add(a);
			}
		}
		ui.text("EXPLORED  ·  BY BIOME AREA", x + 4, y, Ui.Font.F85, 1, Theme.INK_4);
		y += 20;
		if (areas.isEmpty()) {
			ui.text("Walk the land to explore it", x + 4, y, Ui.Font.F11, Theme.INK_4);
			y += 24;
		}
		for (var a : areas) {
			boolean in = mine != null && a.id().equals(mine.id());
			String name = a.name() == null || a.name().isEmpty()
				? prettify(a.biome() == null ? "land" : a.biome().substring(a.biome().indexOf(':') + 1)) : a.name();
			String pct = a.percent() + "%";
			ui.text(ui.fit(name, Ui.Font.F12, LIST_W - 140), x + 4, y, Ui.Font.F12, Theme.INK);
			ui.textRight(pct, x + LIST_W - 6, y + 1, Ui.Font.F105, 0, Theme.INK_2);
			if (in) {
				ui.textRight("YOU ARE HERE", x + LIST_W - 6 - ui.width(pct, Ui.Font.F105) - 10, y + 2, Ui.Font.F85, 1, Theme.INK_4);
			}
			y += 18;
			int filled = Math.max(a.explored() > 0 ? 1 : 0, Math.min(20, Math.round(a.percent() / 5F)));
			float cellW = (LIST_W - 8 - 19 * 3) / 20F;
			for (int c = 0; c < 20; c++) {
				int colour = c < filled ? (in && c == filled - 1 ? Theme.VIOLET : Theme.ink(0.5F)) : Theme.HAIR_12;
				ui.round(x + 4 + c * (cellW + 3), y, cellW, 4, 1.5F, colour);
			}
			y += 4 + 18;
		}
		// the first discoveries, by world day
		y += 4;
		ui.text("DISCOVERIES", x + 4, y, Ui.Font.F85, 1, Theme.INK_4);
		y += 20;
		long today = Minecraft.getInstance().level == null ? -1 : Minecraft.getInstance().level.getOverworldClockTime() / 24000 + 1;
		long day = Long.MIN_VALUE;
		int shown = 0;
		for (var e : journal.all()) {
			if (e.kind() != dev.voidmc.atlas.core.journal.JournalEvent.Kind.DISCOVERY) {
				continue;
			}
			if (e.day() != day) {
				day = e.day();
				ui.text("Day " + day + (day == today ? "  ·  today" : ""), x + 6, y + (shown == 0 ? 0 : 6), Ui.Font.F11, Theme.INK_2);
				y += shown == 0 ? 22 : 28;
			}
			Place place = e.placeId() == null ? null : session.places.peek(e.placeId());
			MarkerGlyph glyph = place == null ? MarkerGlyph.DOT : Kinds.of(place).glyph;
			String kind = place == null ? "" : Kinds.of(place).word;
			String sub = kind + (e.y() != 0 ? "  ·  Y " + minus(e.y()) : "");
			String id = place == null ? null : AtlasClient.placeMarkerId(place);
			boolean selected = id != null && id.equals(this.map.selected);
			boolean hover = mx >= x && mx <= x + LIST_W && my >= y && my <= y + 44 && my >= top && my <= bottom;
			if (selected || hover) {
				ui.round(x, y, LIST_W, 44, 12, selected ? 0xFF1F2023 : Theme.ISLAND);
			}
			ui.text(CLOCK.format(java.time.Instant.ofEpochMilli(e.epoch())), x + 6, y + 15, Ui.Font.F95, Theme.INK_4);
			ui.panel(x + 50, y + 8, 28, 28, 9, Theme.WELL, Theme.HAIR_5);
			ui.glyph(glyph, x + 57, y + 15, 14, Theme.ink(0.85F), Theme.INK);
			String title = place == null ? e.text() : place.name();
			ui.text(ui.fit(title, Ui.Font.F12, LIST_W - 100), x + 90, y + 7, Ui.Font.F12, Theme.INK);
			ui.text(ui.fit(sub, Ui.Font.F85, 1, LIST_W - 100), x + 90, y + 26, Ui.Font.F85, 1, Theme.INK_4);
			if (place != null && y + 44 > top && y < bottom) {
				Row row = new Row(id, glyph, false, place.name(), sub, "", "", place.x() + 0.5, place.z() + 0.5, place.dimension());
				this.hit(x, Math.max(y, top), LIST_W, Math.min(y + 44, bottom) - Math.max(y, top), () -> this.select(row));
			}
			y += 48;
			if (++shown >= 200) {
				break;
			}
		}
		if (shown == 0) {
			ui.text("Your first discoveries are written here", x + 6, y, Ui.Font.F11, Theme.INK_4);
			y += 24;
		}
		this.listContent = y + this.listScroll - top;
		ui.flush();
		ui.g.disableScissor();
		this.listScroll = Math.max(0, Math.min(Math.max(0, this.listContent - (bottom - top)), this.listScroll));
	}

	// --- VOID friends (Figma 375:36429 friends, 375:36549 party) --------------------------------------------------

	/** Whether the selection is a friend (the right column then shows their card). */
	private boolean friendSelected() {
		return this.map.selected != null && this.map.selected.startsWith(dev.voidmc.atlas.client.Social.FRIEND_PREFIX);
	}

	/** The friends to list: in this world first (nearest first), then the others online, then those offline. */
	private List<dev.voidmc.atlas.api.AtlasSocialTransport.Friend> friendsListed() {
		var social = this.atlas.social;
		List<dev.voidmc.atlas.api.AtlasSocialTransport.Friend> all = new ArrayList<>(social.roster());
		LocalPlayer p = Minecraft.getInstance().player;
		all.sort(Comparator.comparingInt((dev.voidmc.atlas.api.AtlasSocialTransport.Friend f) -> social.position(f.id()) != null ? 0 : f.online() ? 1 : 2)
			.thenComparingDouble(f -> {
				var at = social.position(f.id());
				return at == null || p == null ? 0 : Math.hypot(at.x() - p.getX(), at.z() - p.getZ());
			}));
		return all;
	}

	/**
	 * A friend's row (atlas/friend row): their face (ringed when in this world), name, where they are, and how far for
	 * those in this world ("—" for those elsewhere). Returns the y under it.
	 */
	private float friendRow(Ui ui, dev.voidmc.atlas.api.AtlasSocialTransport.Friend f, float x, float y, float mx, float my, float top, float bottom,
		String note) {
		var at = this.atlas.social.position(f.id());
		LocalPlayer p = Minecraft.getInstance().player;
		String id = dev.voidmc.atlas.client.Social.FRIEND_PREFIX + f.id();
		boolean selected = id.equals(this.map.selected);
		boolean hover = mx >= x && mx <= x + LIST_W && my >= y && my <= y + 52 && my >= top && my <= bottom;
		ui.panel(x, y, LIST_W, 52, 14, selected ? 0xFF1F2023 : hover ? 0xFF1C1D20 : Theme.ISLAND, selected ? Theme.HAIR_16 : Theme.HAIR_7);
		Faces.tile(ui, f.minecraft(), x + 10, y + 10, 32, at != null, f.online() ? 1 : 0.55F);
		String where = at != null ? "SAME WORLD" + (f.inParty() ? "  ·  " + note : "") : f.online() ? "ELSEWHERE" + (f.where() == null ? "" : "  ·  "
			+ f.where().toUpperCase(Locale.ROOT)) : "OFFLINE";
		String right = at == null || p == null ? "—" : MarkerPainter.distance(Math.hypot(at.x() - p.getX(), at.z() - p.getZ()));
		String rightSub = at == null || p == null ? "" : MarkerPainter.direction(p.getX(), p.getZ(), at.x(), at.z());
		float rw = Math.max(ui.width(right, Ui.Font.F12), ui.width(rightSub, Ui.Font.F85, 1));
		float tw = LIST_W - 54 - 14 - rw - 10;
		ui.text(ui.fit(f.name(), Ui.Font.F13, tw), x + 54, y + 9, Ui.Font.F13, f.online() ? Theme.INK : Theme.INK_3);
		ui.text(ui.fit(where, Ui.Font.F85, 1, tw), x + 54, y + 31, Ui.Font.F85, 1, Theme.INK_4);
		ui.textRight(right, x + LIST_W - 14, y + 10, Ui.Font.F12, 0, at == null ? Theme.INK_4 : Theme.INK_2);
		ui.textRight(rightSub, x + LIST_W - 14, y + 31, Ui.Font.F85, 1, Theme.INK_4);
		if (y + 52 > top && y < bottom) {
			this.hit(x, Math.max(y, top), LIST_W, Math.min(y + 52, bottom) - Math.max(y, top), () -> {
				this.map.selected = id;
				this.editing = null;
				if (at != null && at.dimension().equals(this.map.dimensionOf(this.map.kind))) {
					this.map.centreOn(at.x(), at.z());
				}
			});
		}
		return y + 58;
	}

	/** The friends at the top of the Places tab (at most three); returns the y under them. */
	private float friendsSection(Ui ui, float x, float y, float mx, float my, float bottom) {
		var friends = this.friendsListed();
		if (friends.isEmpty()) {
			return y;
		}
		int online = 0;
		for (var f : friends) {
			online += f.online() ? 1 : 0;
		}
		ui.text("FRIENDS  ·  " + online + " ONLINE", x + 4, y + 8, Ui.Font.F85, 1, Theme.INK_4);
		y += 28;
		for (int i = 0; i < Math.min(3, friends.size()); i++) {
			y = this.friendRow(ui, friends.get(i), x, y, mx, my, y, bottom, "IN YOUR PARTY");
		}
		return y + 6;
	}

	/**
	 * The right column while a friend is selected: their card (atlas/friend card) over the friends list. Esc goes back.
	 * Join party, Share waypoint (where you stand, as a temporary waypoint) and Track (the compass, one beam and the
	 * hairline to them).
	 */
	private void friendPanel(Ui ui, float mx, float my) {
		String id = this.map.selected.substring(dev.voidmc.atlas.client.Social.FRIEND_PREFIX.length());
		var social = this.atlas.social;
		var f = social.friend(id);
		var at = social.position(id);
		float x = this.listX;
		float y = this.listY;
		float h = 282;
		ui.panel(x, y, LIST_W, h, 18, Theme.ISLAND, Theme.HAIR_8);
		Faces.tile(ui, f == null ? null : f.minecraft(), x + 16, y + 16, 44, at != null, 1);
		String name = f != null ? f.name() : at != null ? at.name() : id;
		ui.text(ui.fit(name, Ui.Font.F18, LIST_W - 120), x + 72, y + 18, Ui.Font.F18, Theme.INK);
		String state = f == null || f.online() ? "ONLINE" + (at != null ? "  ·  SAME WORLD" : f != null && f.where() != null ? "  ·  " + f.where().toUpperCase(Locale.ROOT) : "")
			: "OFFLINE";
		ui.text(ui.fit(state, Ui.Font.F85, 1, LIST_W - 110), x + 72, y + 44, Ui.Font.F85, 1, Theme.INK_4);
		Icons.close(ui, x + LIST_W - 30, y + 22, Theme.INK_3);
		this.hit(x + LIST_W - 40, y + 12, 32, 32, () -> this.map.selected = null);
		// the three readouts: how far (and which way), their Y, the land they stand on
		LocalPlayer p = Minecraft.getInstance().player;
		String[][] tiles;
		if (at != null && p != null) {
			double d = Math.hypot(at.x() - p.getX(), at.z() - p.getZ());
			String dist = d >= 1000 ? String.format(Locale.ROOT, "%.1f", d / 1000) : String.valueOf(Math.round(d));
			String biome = this.biomeNameAt((int) Math.floor(at.x()), (int) Math.floor(at.z()));
			tiles = new String[][]{{dist, (d >= 1000 ? "KM " : "METRES ") + MarkerPainter.direction(p.getX(), p.getZ(), at.x(), at.z())},
				{minus((int) Math.round(at.y())), "Y LEVEL"}, {biome == null ? "—" : biome, "BIOME"}};
		} else {
			tiles = new String[][]{{"—", "METRES"}, {"—", "Y LEVEL"}, {"—", "BIOME"}};
		}
		float tw = (LIST_W - 32 - 14) / 3;
		for (int i = 0; i < 3; i++) {
			float tx = x + 16 + i * (tw + 7);
			ui.panel(tx, y + 74, tw, 54, 12, Theme.WELL, Theme.HAIR_6);
			ui.text(ui.fit(tiles[i][0], Ui.Font.F16, tw - 20), tx + 11, y + 82, Ui.Font.F16, Theme.INK);
			ui.text(tiles[i][1], tx + 11, y + 108, Ui.Font.F85, 1, Theme.INK_4);
		}
		this.button(ui, x + 16, y + 140, LIST_W - 32, true, f != null && f.inParty() ? "In your party" : "Join party", MarkerGlyph.VILLAGE, mx, my, () -> {
			if (!social.joinParty(id)) {
				this.atlas.overlays().info("ATLAS  ·  FRIENDS", "Joining a party is not available here");
			}
		});
		float bw = (LIST_W - 32 - 8) / 2;
		this.button(ui, x + 16, y + 188, bw, false, "Share waypoint", MarkerGlyph.PIN, mx, my, () -> {
			if (p != null) {
				Waypoint w = Waypoint.create("Meet here", p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.level().dimension().identifier().toString(), 0);
				if (!social.shareWaypoint(w)) {
					this.atlas.overlays().info("ATLAS  ·  FRIENDS", "Sharing is off (Share my position: Nobody)");
				}
			}
		});
		boolean tracking = this.map.selected.equals(this.atlas.settings().tracked);
		this.button(ui, x + 16 + bw + 8, y + 188, bw, false, tracking ? "Tracking" : "Track", MarkerGlyph.TRACK, mx, my, () -> this.track(this.map.selected));
		// where it comes from
		dev.voidmc.atlas.api.Source via = dev.voidmc.atlas.api.Source.plugin("void:account");
		float cw = 8 + 11 + 7 + ui.width("through your VOID account", Ui.Font.F11) + 11;
		ui.round(x + 16, y + 238, cw, 26, 12, Theme.WELL);
		ui.border(x + 16, y + 238, cw, 26, 12, 0, Theme.HAIR_8);
		int[][] cells = {{0, 0}, {1, 0}, {0, 1}, {2, 1}, {1, 2}, {2, 2}};
		for (int[] c : cells) {
			ui.round(x + 24 + c[0] * 4, y + 245.5F + c[1] * 4, 3, 3, 1, Theme.ink(0.55F));
		}
		ui.text("through your VOID account", x + 16 + 26, y + 244, Ui.Font.F11, Theme.INK_2);
		// the friends list under the card
		float top = y + h + 18;
		float bottom = this.listY + this.listH;
		var friends = this.friendsListed();
		int online = 0;
		for (var fr : friends) {
			online += fr.online() ? 1 : 0;
		}
		ui.text("FRIENDS  ·  " + online + " ONLINE", x + 4, top, Ui.Font.F85, 1, Theme.INK_4);
		float ry = top + 20;
		for (var fr : friends) {
			if (ry + 52 > bottom - 24) {
				break;
			}
			ry = this.friendRow(ui, fr, x, ry, mx, my, top, bottom, "IN YOUR PARTY");
		}
		ui.text("Friends elsewhere are listed, never drawn on this map.", x + 2, ry + 2, Ui.Font.F105, Theme.INK_3);
	}

	/** The biome mapped at a column, in words, or null. */
	private @Nullable String biomeNameAt(int bx, int bz) {
		MapSession session = this.atlas.session();
		String b = session == null ? null : session.biomeAt(this.map.dimensionOf(MapPanel.Kind.SURFACE), bx, bz);
		return b == null ? null : prettify(b.substring(b.indexOf(':') + 1));
	}

	/**
	 * The Legend's party part (375:36549): who shares their map with you, and "Share my position: Friends / Party /
	 * Nobody" (Party by default). Returns the y under it.
	 */
	private float partyLegend(Ui ui, float x, float y, float top, float bottom, float mx, float my) {
		if (!this.atlas.social.available()) {
			return y;
		}
		List<dev.voidmc.atlas.api.AtlasSocialTransport.Friend> party = new ArrayList<>();
		for (var f : this.friendsListed()) {
			if (f.inParty()) {
				party.add(f);
			}
		}
		if (!party.isEmpty()) {
			ui.text("PARTY  ·  " + party.size(), x + 4, y + 6, Ui.Font.F85, 1, Theme.INK_4);
			y += 26;
			for (var f : party) {
				y = this.friendRow(ui, f, x, y, mx, my, top, bottom, this.atlas.settings().shareMap ? "SHARING THEIR MAP" : "IN YOUR PARTY");
			}
		}
		ui.panel(x, y, LIST_W, 108, 14, Theme.ISLAND, Theme.HAIR_7);
		ui.text("Share my position", x + 15, y + 14, Ui.Font.F13, Theme.INK);
		ui.text("Who sees you live on their map", x + 15, y + 33, Ui.Font.F105, Theme.INK_3);
		dev.voidmc.atlas.api.Privacy[] values = dev.voidmc.atlas.api.Privacy.values();
		String[] labels = {"Friends", "Party", "Nobody"};
		int active = this.atlas.social.privacy().ordinal();
		this.segmented(ui, x + 15, y + 56, LIST_W - 30, labels, active, i -> {
			this.atlas.settings().sharePrivacy = values[i].id();
			this.atlas.settings().save();
		}, mx, my);
		return y + 108 + 14;
	}

	// --- the biome view (Figma 395:19901 biome view, 405:29739 highlight and hover, 395:20093 underground, 395:20189 Nether) --

	/** One biome on the map: how much of it is explored (columns), and its nearest explored column to the player. */
	private record BiomeCount(int biome, String id, long columns, int nearX, int nearZ) {
	}

	private volatile List<BiomeCount> census = List.of();
	private long censusAt;
	private String censusKey = "";
	private final java.util.concurrent.atomic.AtomicBoolean censusRunning = new java.util.concurrent.atomic.AtomicBoolean();
	private final TextField biomeSearch = new TextField(40);

	/** The biomes of the layer shown, counted on a worker at most every three seconds (from the regions in memory). */
	private List<BiomeCount> census() {
		MapSession session = this.atlas.session();
		LayerData layer = this.map.layer(this.map.kind);
		long now = System.currentTimeMillis();
		String key = this.map.lookKey() + "|" + (layer == null ? "" : layer.name);
		if (session != null && layer != null && (now - this.censusAt > 3000 || !key.equals(this.censusKey)) && this.censusRunning.compareAndSet(false, true)) {
			this.censusAt = now;
			this.censusKey = key;
			LocalPlayer p = Minecraft.getInstance().player;
			double px = p == null ? 0 : p.getX();
			double pz = p == null ? 0 : p.getZ();
			try {
				session.workers.execute(() -> {
					try {
						java.util.Map<Integer, long[]> counts = new java.util.HashMap<>();
						for (long k : layer.regionKeys()) {
							RegionRaw raw = layer.rawIfLoaded(Coords.unpackX(k), Coords.unpackZ(k));
							if (raw == null) {
								continue;
							}
							int ox = raw.x << Coords.REGION_SHIFT;
							int oz = raw.z << Coords.REGION_SHIFT;
							for (int i = 0; i < RegionRaw.AREA; i++) {
								if (!raw.known(i) || (raw.flags[i] & RegionRaw.ROCK) != 0) {
									continue;
								}
								long[] c = counts.computeIfAbsent((int) raw.biome[i], b -> new long[]{0, Long.MAX_VALUE, 0, 0});
								c[0]++;
								if ((i & 63) == 0) {
									int bx = ox + (i & (RegionRaw.SIZE - 1));
									int bz = oz + (i >> Coords.REGION_SHIFT);
									long d = (long) ((bx - px) * (bx - px) + (bz - pz) * (bz - pz));
									if (d < c[1]) {
										c[1] = d;
										c[2] = bx;
										c[3] = bz;
									}
								}
							}
						}
						var looks = session.looks();
						List<BiomeCount> out = new ArrayList<>();
						for (var e : counts.entrySet()) {
							String id = looks == null ? null : looks.biomeName(e.getKey());
							if (id != null) {
								long[] c = e.getValue();
								out.add(new BiomeCount(e.getKey(), id, c[0], (int) c[2], (int) c[3]));
							}
						}
						out.sort(Comparator.comparingLong((BiomeCount b) -> -b.columns()));
						this.census = out;
					} finally {
						this.censusRunning.set(false);
					}
				});
			} catch (RuntimeException e) {
				this.censusRunning.set(false);
			}
		}
		return this.census;
	}

	private static String km2(long columns) {
		double km = columns / 1_000_000.0;
		return km >= 10 ? String.format(Locale.ROOT, "%.0f km²", km) : km >= 0.995 ? String.format(Locale.ROOT, "%.1f km²", km)
			: String.format(Locale.ROOT, "%.2f km²", km);
	}

	private static int fillOf(String biomeId, dev.voidmc.atlas.client.@Nullable BlockLooks looks, int biome) {
		int rgb = dev.voidmc.atlas.core.MapArt.vanillaFill(biomeId);
		if (rgb < 0 && looks != null) {
			rgb = dev.voidmc.atlas.core.MapArt.moddedFill(looks.grass(biome), looks.foliage(biome));
		}
		return rgb < 0 ? 0x55554C : rgb;
	}

	/**
	 * The map look toggle (atlas/map look): Terrain or Biomes, beside the readout; B switches it. Returns its width.
	 */
	private float lookToggle(Ui ui, float x, float y, float mx, float my) {
		boolean biomes = this.map.biomeLook();
		String[] labels = {"Terrain", "Biomes"};
		float w0 = 28 + ui.width(labels[0], Ui.Font.F13);
		float w1 = 28 + ui.width(labels[1], Ui.Font.F13);
		float w = 8 + w0 + 2 + w1;
		ui.panel(x, y, w, 42, 14, Theme.ISLAND, Theme.HAIR_8);
		float sx = x + 4;
		for (int i = 0; i < 2; i++) {
			float sw = i == 0 ? w0 : w1;
			boolean active = (i == 1) == biomes;
			if (active) {
				ui.panel(sx, y + 5, sw, 32, 10, Theme.RAISED, Theme.HAIR_8);
			} else if (mx >= sx && mx <= sx + sw && my >= y + 5 && my <= y + 37) {
				ui.round(sx, y + 5, sw, 32, 10, Theme.WELL);
			}
			ui.text(labels[i], sx + 14, y + 12, Ui.Font.F13, active ? Theme.INK : Theme.INK_2);
			boolean on = i == 1;
			this.hit(sx, y + 5, sw, 32, () -> this.map.setBiomeLook(on));
			sx += sw + 2;
		}
		return w;
	}

	/**
	 * The Legend in the biome view: a search ("Find: Cherry Grove"), then the biomes by family with their explored
	 * area; clicking a row highlights its family (the rest dims), clicking it again lets it go.
	 */
	private void biomeLegend(Ui ui, float x, float top, float bottom, float mx, float my, boolean caves) {
		var session = this.atlas.session();
		var looks = session == null ? null : session.looks();
		List<BiomeCount> all = this.census();
		dev.voidmc.atlas.core.MapArt.Family highlight = this.atlas.biomeHighlight;
		float y = top;
		if (!caves) {
			// the search
			boolean focused = this.biomeSearch.focused;
			ui.panel(x, y, LIST_W, 38, 12, Theme.WELL, focused ? Theme.HAIR_16 : Theme.HAIR_6);
			Icons.search(ui, x + 14, y + 11.5F, Theme.INK_3);
			if (highlight != null && this.biomeSearch.text.isEmpty() && !focused) {
				ui.text(highlight.label + " highlighted  ·  Esc clears", x + 38, y + 11, Ui.Font.F12, Theme.INK_2);
			} else {
				this.biomeSearch.placeholder = "Find: Cherry Grove";
				ui.flush();
				ui.g.enableScissor((int) (x + 36), (int) y, (int) (x + LIST_W - 36), (int) (y + 38));
				this.biomeSearch.draw(ui, x + 38, y + 11, LIST_W - 80, Ui.Font.F12, Theme.INK, Theme.INK_4);
				ui.flush();
				ui.g.disableScissor();
			}
			kitKey(ui, "/", x + LIST_W - 30, y + 10);
			this.hit(x, y, LIST_W, 38, () -> this.biomeSearch.focused = true);
			y += 38 + 14;
		}
		String query = this.biomeSearch.text.strip().toLowerCase(Locale.ROOT);
		java.util.Map<dev.voidmc.atlas.core.MapArt.Family, List<BiomeCount>> byFamily = new java.util.EnumMap<>(dev.voidmc.atlas.core.MapArt.Family.class);
		for (BiomeCount b : all) {
			var fam = dev.voidmc.atlas.core.MapArt.family(b.id());
			if (caves && fam != dev.voidmc.atlas.core.MapArt.Family.CAVES) {
				continue;
			}
			if (!query.isEmpty() && !dev.voidmc.atlas.core.MapArt.biomeLabel(b.id()).toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			byFamily.computeIfAbsent(fam, f -> new ArrayList<>()).add(b);
		}
		// families by explored area, the highlighted one first; as many as fit, the rest named in a line
		List<dev.voidmc.atlas.core.MapArt.Family> order = new ArrayList<>(byFamily.keySet());
		order.sort(Comparator.comparingInt((dev.voidmc.atlas.core.MapArt.Family f) -> f == highlight ? 0 : 1)
			.thenComparingLong(f -> -byFamily.get(f).stream().mapToLong(BiomeCount::columns).sum()));
		ui.flush();
		ui.g.enableScissor((int) x - 2, (int) top, (int) (x + LIST_W + 2), (int) bottom);
		List<String> rest = new ArrayList<>();
		for (var fam : order) {
			List<BiomeCount> rows = byFamily.get(fam);
			if (y + 22 + rows.size() * 36 > bottom - 40) {
				rest.add(fam.label);
				continue;
			}
			ui.text((caves ? "CAVE BIOMES  ·  AT THIS DEPTH" : fam.label.toUpperCase(Locale.ROOT) + "  ·  " + rows.size()), x + 4, y + 6, Ui.Font.F85, 1,
				Theme.INK_4);
			y += 24;
			boolean famOn = highlight == null || highlight == fam;
			for (BiomeCount b : rows) {
				boolean hl = highlight == fam;
				boolean hover = mx >= x && mx <= x + LIST_W && my >= y && my <= y + 32 && my >= top && my <= bottom;
				if (hl) {
					ui.panel(x, y, LIST_W, 32, 10, Theme.RAISED, Theme.HAIR_10);
				} else if (hover) {
					ui.round(x, y, LIST_W, 32, 10, Theme.ISLAND);
				}
				int fill = fillOf(b.id(), looks, b.biome());
				ui.round(x + 12, y + 9, 14, 14, 4, (famOn ? 0xFF000000 : 0x66000000) | fill);
				ui.border(x + 12, y + 9, 14, 14, 4, 0, Theme.HAIR_10);
				String name = dev.voidmc.atlas.core.MapArt.biomeLabel(b.id());
				ui.text(ui.fit(name, Ui.Font.F12, LIST_W - 150), x + 36, y + 8, Ui.Font.F12, famOn ? Theme.INK : Theme.INK_4);
				if (!b.id().startsWith("minecraft:")) {
					// a mod's biome says whose it is
					dev.voidmc.atlas.api.Source src = dev.voidmc.atlas.api.Source.plugin(b.id().substring(0, b.id().indexOf(':')));
					MarkerPainter.smallSourceChip(ui, src, x + 36 + Math.min(ui.width(name, Ui.Font.F12), LIST_W - 150) + 8, y + 6);
				}
				ui.textRight(km2(b.columns()), x + LIST_W - 12, y + 9, Ui.Font.F105, 0, famOn ? Theme.INK_2 : Theme.INK_4);
				if (y + 32 > top && y < bottom) {
					var f = fam;
					this.hit(x, Math.max(y, top), LIST_W, Math.min(y + 32, bottom) - Math.max(y, top), () -> {
						this.atlas.biomeHighlight = this.atlas.biomeHighlight == f ? null : f;
						if (!query.isEmpty() && b.columns() > 0) {
							this.map.centreOn(b.nearX(), b.nearZ());
						}
					});
				}
				y += 36;
			}
			y += 6;
		}
		if (!rest.isEmpty()) {
			String line = String.join(", ", rest.subList(0, Math.min(3, rest.size()))) + (rest.size() > 3 ? " and " + (rest.size() - 3) + " more families" : "");
			ui.text(ui.fit(line, Ui.Font.F105, LIST_W), x + 2, y + 2, Ui.Font.F105, Theme.INK_3);
			y += 20;
		}
		if (!query.isEmpty() && byFamily.isEmpty()) {
			ui.text("No " + this.biomeSearch.text.strip() + " explored yet", x + 4, y + 4, Ui.Font.F11, Theme.INK_3);
			y += 20;
		}
		if (caves) {
			ui.text("Other cave floors take the biome of the land above, drawn", x + 2, y + 2, Ui.Font.F105, Theme.INK_3);
			ui.text("quieter: half the lightness range, a third of the colour.", x + 2, y + 16, Ui.Font.F105, Theme.INK_3);
			ui.text("Walls and rock keep the cave look; only floors are filled.", x + 2, y + 32, Ui.Font.F105, Theme.INK_3);
		} else if (this.map.kind == MapPanel.Kind.NETHER) {
			ui.text("Lava seas keep their biome fill, 30% darker, so the shore", x + 2, y + 2, Ui.Font.F105, Theme.INK_3);
			ui.text("still reads.", x + 2, y + 16, Ui.Font.F105, Theme.INK_3);
		}
		ui.flush();
		ui.g.disableScissor();
	}

	private static void kitKey(Ui ui, String key, float x, float y) {
		float w = ui.width(key, Ui.Font.F85, 0.5F) + 12;
		ui.panel(x, y, w, 17, 5, 0x8C0B0B0C, Theme.HAIR_10);
		ui.text(key, x + 6, y + 3, Ui.Font.F85, 0.5F, Theme.INK_4);
	}

	/** The biome under the cursor (atlas/biome hover): its name, family, explored area and climate in words. */
	private void biomeHover(Ui ui, MapRenderer.Frame f) {
		if (Float.isNaN(this.hoverX) || this.map.hovered != null) {
			return;
		}
		MapSession session = this.atlas.session();
		LayerData layer = this.map.layer(this.map.kind);
		if (session == null || layer == null || session.looks() == null) {
			return;
		}
		int bx = (int) Math.floor(f.mapX(this.hoverX, this.hoverY));
		int bz = (int) Math.floor(f.mapZ(this.hoverX, this.hoverY));
		RegionRaw raw = layer.rawIfLoaded(Coords.region(bx), Coords.region(bz));
		int i = RegionRaw.index(Coords.inRegion(bx), Coords.inRegion(bz));
		if (raw == null || !raw.known(i)) {
			return;
		}
		int biome = raw.biome[i];
		String id = session.looks().biomeName(biome);
		if (id == null) {
			return;
		}
		String name = dev.voidmc.atlas.core.MapArt.biomeLabel(id);
		long columns = 0;
		for (BiomeCount b : this.census) {
			if (b.biome() == biome) {
				columns = b.columns();
			}
		}
		var fam = dev.voidmc.atlas.core.MapArt.family(id);
		String caps = fam.label.toUpperCase(Locale.ROOT) + "  ·  " + km2(columns).toUpperCase(Locale.ROOT) + " EXPLORED";
		float[] climate = session.looks().climate(biome);
		String words = climate == null ? "" : String.format(Locale.ROOT, "Temperature %.1f, %s", climate[0],
			dev.voidmc.atlas.core.MapArt.temperatureWord(climate[0])) + (Float.isNaN(climate[1]) ? "" : String.format(Locale.ROOT,
			"  ·  Rainfall %.1f, %s", climate[1], dev.voidmc.atlas.core.MapArt.rainfallWord(climate[1])));
		dev.voidmc.atlas.api.Source src = id.startsWith("minecraft:") ? null : dev.voidmc.atlas.api.Source.plugin(id.substring(0, id.indexOf(':')));
		float chip = src == null ? 0 : 8 + MarkerPainter.smallSourceChipWidth(ui, src);
		float w = Math.max(Math.max(ui.width(name, Ui.Font.F12) + chip, ui.width(caps, Ui.Font.F85, 1)), ui.width(words, Ui.Font.F95)) + 26;
		float tx = Math.min(this.hoverX + 14, this.mapX + this.mapW - w - 8);
		float ty = Math.min(this.hoverY + 12, this.mapY + this.mapH - 74);
		ui.panel(tx, ty, w, 66, 12, Theme.ISLAND, Theme.HAIR_10);
		ui.text(name, tx + 13, ty + 9, Ui.Font.F12, Theme.INK);
		if (src != null) {
			MarkerPainter.smallSourceChip(ui, src, tx + 13 + ui.width(name, Ui.Font.F12) + 8, ty + 7);
		}
		ui.text(caps, tx + 13, ty + 29, Ui.Font.F85, 1, Theme.INK_4);
		ui.text(words, tx + 13, ty + 45, Ui.Font.F95, Theme.INK_2);
	}

	/** The footer in the biome view: how many biomes are known, or the highlighted family's share. */
	private @Nullable String biomeFooter() {
		if (!this.map.biomeLook()) {
			return null;
		}
		List<BiomeCount> all = this.census;
		var highlight = this.atlas.biomeHighlight;
		if (highlight != null) {
			long fam = 0;
			long total = 0;
			int n = 0;
			for (BiomeCount b : all) {
				total += b.columns();
				if (dev.voidmc.atlas.core.MapArt.family(b.id()) == highlight) {
					fam += b.columns();
					n++;
				}
			}
			return highlight.label.toUpperCase(Locale.ROOT) + "  ·  " + n + " BIOMES  ·  " + km2(fam).toUpperCase(Locale.ROOT) + " OF " + km2(total)
				.toUpperCase(Locale.ROOT) + " EXPLORED";
		}
		String where = this.map.kind == MapPanel.Kind.UNDERGROUND ? "UNDERGROUND  ·  " : this.map.kind == MapPanel.Kind.NETHER ? "NETHER  ·  " : "";
		return where + "BIOMES  ·  " + all.size() + " FOUND IN EXPLORED LAND  ·  B SWITCHES THE LOOK";
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

	private float section(Ui ui, @Nullable String label, List<Row> rows, float x, float y, float top, float bottom, float mx, float my, boolean add) {
		if (label != null) {
			ui.text(label + "  ·  " + rows.size(), x + 4, y + 8, Ui.Font.F85, 1, Theme.INK_4);
		}
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
		y = this.terrainLegend(ui, x, y, top, bottom, mx, my);
		y = this.partyLegend(ui, x, y, top, bottom, mx, my);
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
		if (this.farShown()) {
			ui.text("Places show up in seen-from-afar land only once you", x + 2, y + 2, Ui.Font.F105, Theme.INK_3);
			ui.text("walk within 2 chunks.", x + 2, y + 17, Ui.Font.F105, Theme.INK_3);
			y += 36;
		}
		this.listContent = y + this.listScroll - top;
		ui.flush();
		ui.g.disableScissor();
		this.listScroll = Math.max(0, Math.min(Math.max(0, this.listContent - (bottom - top)), this.listScroll));
	}

	/** Whether VOID LOD is still drawing the land round a column: inside its reach, and not yet complete. */
	private boolean lodStillDrawing(int bx, int bz) {
		Boolean complete = dev.voidmc.atlas.client.FarSight.lodComplete();
		if (complete == null || complete) {
			return false;
		}
		LocalPlayer p = Minecraft.getInstance().player;
		double reach = dev.voidmc.atlas.client.FarSight.lodRadius();
		return p != null && reach > 0 && Math.hypot(bx + 0.5 - p.getX(), bz + 0.5 - p.getZ()) <= reach;
	}

	/** What a hovered column is, when it is not one's own: "Seen from afar" (far sight) or a party member's land. */
	private void landHover(Ui ui, MapRenderer.Frame f) {
		if (Float.isNaN(this.hoverX) || this.map.kind != MapPanel.Kind.SURFACE || this.map.hovered != null) {
			return;
		}
		MapSession session = this.atlas.session();
		DimensionData d = session == null ? null : session.dimensionIfOpen(this.map.dimensionOf(this.map.kind));
		if (d == null) {
			return;
		}
		int bx = (int) Math.floor(f.mapX(this.hoverX, this.hoverY));
		int bz = (int) Math.floor(f.mapZ(this.hoverX, this.hoverY));
		if (known(d.layerIfOpen(DimensionData.SURFACE), bx, bz)) {
			return;
		}
		String title;
		String sub;
		int biome;
		LayerData shared = this.partyShown() ? d.layerIfOpen(dev.voidmc.atlas.client.Social.LAYER) : null;
		LayerData far = this.farShown() ? d.layerIfOpen(dev.voidmc.atlas.client.FarSight.LAYER) : null;
		if (known(shared, bx, bz)) {
			String by = this.atlas.social.mappedBy(d.id, bx, bz);
			title = "From " + (by == null ? "your party" : this.atlas.social.nameOf(by));
			sub = "no places here until you visit";
			biome = biomeOf(shared, bx, bz);
		} else if (known(far, bx, bz)) {
			title = "Seen from afar";
			sub = "walk closer for detail";
			biome = biomeOf(far, bx, bz);
		} else if (this.farShown() && known(d.layerIfOpen(dev.voidmc.atlas.client.FarSight.COARSE_LAYER), Math.floorDiv(bx, dev.voidmc.atlas.client.FarSight.COARSE),
			Math.floorDiv(bz, dev.voidmc.atlas.client.FarSight.COARSE))) {
			title = "Seen from afar";
			sub = "on the horizon: walk closer for detail";
			biome = biomeOf(d.layerIfOpen(dev.voidmc.atlas.client.FarSight.COARSE_LAYER), Math.floorDiv(bx, dev.voidmc.atlas.client.FarSight.COARSE),
				Math.floorDiv(bz, dev.voidmc.atlas.client.FarSight.COARSE));
		} else if (far != null && this.lodStillDrawing(bx, bz)) {
			// inside VOID LOD's reach while it is still drawing: land on its way; once it is complete (or past its
			// reach) land not seen is plain unexplored, with no hover
			title = "Still being drawn";
			sub = "VOID LOD is drawing the far land here";
			biome = -1;
		} else {
			return;
		}
		var looks = session.looks();
		String b = looks == null || biome < 0 ? null : looks.biomeName(biome);
		LocalPlayer p = Minecraft.getInstance().player;
		StringBuilder line = new StringBuilder();
		if (b != null) {
			line.append(prettify(b.substring(b.indexOf(':') + 1))).append("  ·  ");
		}
		if (p != null) {
			line.append(MarkerPainter.distance(Math.hypot(bx + 0.5 - p.getX(), bz + 0.5 - p.getZ()))).append(' ')
				.append(MarkerPainter.direction(p.getX(), p.getZ(), bx + 0.5, bz + 0.5)).append("  ·  ");
		}
		line.append(sub);
		String text = line.toString();
		float w = Math.max(ui.width(title, Ui.Font.F12), ui.width(text, Ui.Font.F95)) + 26;
		float tx = Math.min(this.hoverX + 12, this.mapX + this.mapW - w - 8);
		float ty = Math.min(this.hoverY + 10, this.mapY + this.mapH - 58);
		ui.panel(tx, ty, w, 48, 12, Theme.ISLAND, Theme.HAIR_10);
		ui.text(title, tx + 13, ty + 8, Ui.Font.F12, Theme.INK);
		ui.text(text, tx + 13, ty + 27, Ui.Font.F95, Theme.INK_3);
	}

	private static boolean known(@Nullable LayerData layer, int bx, int bz) {
		RegionRaw raw = layer == null ? null : layer.rawIfLoaded(Coords.region(bx), Coords.region(bz));
		return raw != null && raw.known(RegionRaw.index(Coords.inRegion(bx), Coords.inRegion(bz)));
	}

	private static int biomeOf(LayerData layer, int bx, int bz) {
		RegionRaw raw = layer.rawIfLoaded(Coords.region(bx), Coords.region(bz));
		return raw == null ? -1 : raw.biome[RegionRaw.index(Coords.inRegion(bx), Coords.inRegion(bz))];
	}

	private int[] chunkCountsKept = {0, 0};
	private long chunkCountsAt;

	/** Chunks walked and chunks only seen from afar, counted at most every two seconds. */
	private int[] chunkCounts(MapSession session) {
		long now = System.currentTimeMillis();
		if (now - this.chunkCountsAt > 2000) {
			DimensionData d = session.dimensionIfOpen(this.map.dimensionOf(MapPanel.Kind.SURFACE));
			LayerData walked = d == null ? null : d.layerIfOpen(DimensionData.SURFACE);
			LayerData far = d == null ? null : d.layerIfOpen(dev.voidmc.atlas.client.FarSight.LAYER);
			this.chunkCountsKept = new int[]{walked == null ? 0 : walked.chunksKnown(), far == null ? 0 : far.chunksKnown()};
			this.chunkCountsAt = now;
		}
		return this.chunkCountsKept;
	}

	/** A layer chip's words: its label, or the translation if the label is a translation key ("hearth.map.settlements"). */
	static String label(MapLayer l) {
		net.minecraft.locale.Language language = net.minecraft.locale.Language.getInstance();
		return language.has(l.label()) ? language.getOrDefault(l.label()) : l.label();
	}

	/** Whether far sight is part of this map (VOID LOD there, the setting on, the surface shown). */
	private boolean farShown() {
		return this.map.kind == MapPanel.Kind.SURFACE && this.atlas.settings().farSight
			&& dev.voidmc.atlas.client.FarSight.lodPresent();
	}

	/** Whether party members' land is part of this map. */
	private boolean partyShown() {
		return this.map.kind == MapPanel.Kind.SURFACE && this.atlas.social.available() && this.atlas.settings().shareMap;
	}

	/**
	 * The Legend's terrain rows (atlas/terrain legend row): walked, seen from afar, from party; only those this map
	 * has. Returns the y under them.
	 */
	private float terrainLegend(Ui ui, float x, float y, float top, float bottom, float mx, float my) {
		boolean far = this.farShown();
		boolean party = this.partyShown();
		if (!far && !party) {
			return y;
		}
		ui.text("TERRAIN", x + 4, y + 6, Ui.Font.F85, 1, Theme.INK_4);
		y += 26;
		y = terrainRow(ui, x, y, 0xFF2A3A2C, party ? "Your map" : "Walked", party ? "Walked by you, full detail" : "Full detail, every place");
		if (far) {
			y = terrainRow(ui, x, y, 0xFF55595E, "Seen from afar", "From VOID LOD, no places until you go");
		}
		if (party) {
			y = terrainRow(ui, x, y, 0xFF2F3531, "From party", "Their land, quieter until you visit");
		}
		return y + 8;
	}

	private static float terrainRow(Ui ui, float x, float y, int swatch, String title, String sub) {
		ui.panel(x + 8, y + 2, 30, 30, 8, swatch, Theme.HAIR_8);
		ui.text(title, x + 50, y + 2, Ui.Font.F12, Theme.INK);
		ui.text(sub, x + 50, y + 20, Ui.Font.F95, Theme.INK_3);
		return y + 52;
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
	private void updateScrubWindow() {
		var level = Minecraft.getInstance().level;
		int minY = level == null ? -64 : level.getMinY();
		int maxY = level == null ? 319 : level.getMinY() + level.getHeight() - 1;
		int[] w = dev.voidmc.atlas.core.under.DepthWindow.of(minY, maxY, this.map.band(this.map.kind) * DimensionData.BAND, this.scrubBottom, SCRUB_SPAN);
		this.scrubBottom = w[0];
		this.scrubTop = w[1];
	}

	private void depthPanel(Ui ui, float mx, float my) {
		this.updateScrubWindow();
		float x = this.listX;
		float y = this.listY;
		this.scrubber(ui, x, y, mx, my);
		this.sideView(ui, x + 132, y);
		if (this.map.biomeLook()) {
			this.biomeLegend(ui, x, y + 396, this.listY + this.listH, mx, my, true);
			return;
		}
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
		return y + 35 + (this.scrubTop - cellY) / 8F * 12.5F;
	}

	/**
	 * The design's depth scrubber: a cell for every 8 blocks of its window on the world's height (128 down to -64 at a
	 * vanilla overworld's cave depth), brighter where the player has
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
		for (int cy = this.scrubTop; cy >= this.scrubBottom; cy -= 8) {
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
		if (surfaceBand * 8 <= this.scrubTop && surfaceBand * 8 >= this.scrubBottom) {
			ui.text("surface", x + 81, this.scrubCellY(y, surfaceBand * 8) - 0.76F, Ui.Font.F85, 0.6F, Theme.INK_4);
		}
		if (p != null && you != slice && you * 8 <= this.scrubTop && you * 8 >= this.scrubBottom) {
			ui.text("you " + minus(p.getBlockY()), x + 81, this.scrubCellY(y, you * 8) - 1.26F, Ui.Font.F9, 0.4F, Theme.INK_2);
		}
		// the handle: the slice's height
		float hy = this.scrubCellY(y, Math.max(this.scrubBottom, Math.min(this.scrubTop, slice * 8))) - 5.5F;
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
		int cy = this.scrubTop - Math.round(cells) * 8;
		cy = Math.max(this.scrubBottom, Math.min(this.scrubTop, cy));
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
		float k = h / (this.scrubTop - this.scrubBottom);
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
			int ground = d.ceiling ? this.scrubTop : Integer.MIN_VALUE;
			if (surface != null) {
				RegionRaw raw = surface.rawIfLoaded(Coords.region(bx), Coords.region(z));
				int i = RegionRaw.index(Coords.inRegion(bx), Coords.inRegion(z));
				if (raw != null && raw.known(i)) {
					ground = Math.min(this.scrubTop, raw.height[i] - raw.depth(i));
				}
			}
			if (ground != Integer.MIN_VALUE) {
				// the rock under the ground, a quiet fill
				float gy = top + (this.scrubTop - ground) * k;
				ui.rect(sx, gy, sw, top + h - gy, Theme.ink(0.07F));
				if (!d.ceiling) {
					ui.rect(sx, gy, sw, 1.5F, Theme.ink(0.22F));
				}
			}
			// the air seen underground, cut out of it
			for (int band : d.bands()) {
				int by0 = band * DimensionData.BAND;
				if (by0 > this.scrubTop || by0 < this.scrubBottom) {
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
				float fy = top + (this.scrubTop - roof) * k;
				float fh = Math.max(k, (roof - floor + 1) * k);
				ui.rect(sx, fy, sw, fh, Theme.MAP_WELL);
			}
		}
		// the slice, and the player
		int slice = this.map.band(this.map.kind);
		float sy = top + (this.scrubTop - (slice * DimensionData.BAND + DimensionData.BAND)) * k;
		ui.rect(x, sy, 172, DimensionData.BAND * k, Theme.ink(0.05F));
		ui.rect(x, sy + DimensionData.BAND * k / 2 - 1, 172, 2, Theme.alpha(Theme.VIOLET, 0.9F));
		if (p != null) {
			float py = top + (this.scrubTop - p.getBlockY()) * k;
			ui.round(x + 86 - 3.5F, py - 3.5F, 7, 7, 1.5F, Theme.INK);
		}
		ui.flush();
		ui.g.disableScissor();
		if (surface != null && p != null) {
			int s = this.loadedHeight(cx, z, 64);
			ui.textRight("surface " + s, x + 158, top + (this.scrubTop - s) * k - 14, Ui.Font.F85, 0.6F, Theme.INK_4);
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
		} else if (this.biomeFooter() != null && this.map.selected == null) {
			left = this.biomeFooter();
		} else if (this.rightTab == 1 && this.partyShown() && !this.friendSelected()
			&& !this.atlas.social.shared(this.map.dimensionOf(this.map.kind)).isEmpty()) {
			StringBuilder b = new StringBuilder("MAP FROM PARTY");
			for (var sh : this.atlas.social.shared(this.map.dimensionOf(this.map.kind))) {
				b.append("  ·  ").append(sh.name()).append(' ').append(sh.chunks()).append(" CHUNKS");
			}
			left = b.toString();
		} else if (this.rightTab == 1 && this.farShown() && session != null) {
			int[] c = this.chunkCounts(session);
			left = String.format(Locale.ROOT, "%,d CHUNKS WALKED  ·  %,d SEEN FROM AFAR  ·  FAR SIGHT ON", c[0], c[1]).replace(',', ' ');
		} else if (this.friendSelected() || this.rightTab == 1 && this.partyShown()) {
			var friends = this.friendsListed();
			int online = 0;
			int here = 0;
			for (var fr : friends) {
				online += fr.online() ? 1 : 0;
				here += this.atlas.social.position(fr.id()) != null ? 1 : 0;
			}
			left = online + " FRIENDS ONLINE  ·  " + here + " IN THIS WORLD  ·  THROUGH YOUR VOID ACCOUNT";
		} else if (this.rightTab == 2 && session != null) {
			left = "JOURNAL  ·  SINCE DAY " + Math.max(1, session.journal.stats().firstDay()) + "  ·  ONLY FIRST DISCOVERIES ARE LOGGED";
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
		Waypoint wp = this.atlas.waypointFor(m.id());
		if (wp != null) {
			this.waypointCard(ui, wp, x, y, mx, my);
			return;
		}
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
		if (sameDim && distance > 8) {
			// the distance only when it says something: standing in the place, it is left out
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
		// the height first, to size the card; a card taller than the map has room for gives up, in turn, Atlas's own
		// facts (a mod's card speaks for its place), then its last rows, then the meter: never drawn past its panel
		float room = this.mapH - 88 - 64;
		MarkerDetail.Meter shownMeter = detail == null ? null : detail.meter();
		List<List<MarkerDetail.Row>> rows = new ArrayList<>();
		if (detail != null) {
			for (MarkerDetail.Section s : detail.sections()) {
				rows.add(new ArrayList<>(s.rows()));
			}
		}
		float h = cardHeight(place != null, shownMeter != null, rows, facts.size(), source != null);
		if (h > room && !rows.isEmpty() && !facts.isEmpty()) {
			facts = List.of();
			h = cardHeight(place != null, shownMeter != null, rows, 0, source != null);
		}
		while (h > room && !facts.isEmpty()) {
			facts = facts.subList(0, facts.size() - 1);
			h = cardHeight(place != null, shownMeter != null, rows, facts.size(), source != null);
		}
		while (h > room && !rows.isEmpty()) {
			List<MarkerDetail.Row> last = rows.get(rows.size() - 1);
			if (last.size() > 1) {
				last.remove(last.size() - 1);
			} else {
				rows.remove(rows.size() - 1);
			}
			h = cardHeight(place != null, shownMeter != null, rows, facts.size(), source != null);
		}
		if (h > room && shownMeter != null) {
			shownMeter = null;
			h = cardHeight(place != null, false, rows, facts.size(), source != null);
		}
		boolean cut = h > room;
		h = Math.min(h, room);
		ui.panel(x, y, w, h, 20, Theme.ISLAND, Theme.HAIR_8);
		// the actions are a footer pinned to the card's foot; the body above them is clipped to the card
		float footY = y + h - 17 - 40;
		ui.flush();
		ui.g.enableScissor((int) x, (int) y, (int) (x + w), (int) (footY - 7));
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
			// the caps label whole: tighter, then shorter words, before it is ever cut
			String label = r.label();
			float tracking = 1;
			if (ui.width(label, Ui.Font.F85, 1) > rw - 20) {
				tracking = 0.4F;
				if (ui.width(label, Ui.Font.F85, tracking) > rw - 20) {
					label = label.replace("METRES", "M").replace("  ", " ");
				}
			}
			ui.text(ui.fit(label, Ui.Font.F85, tracking, rw - 20), rx + 13, cy + 38, Ui.Font.F85, tracking, Theme.INK_4);
		}
		cy += 60;
		if (shownMeter != null) {
			cy += 14;
			MarkerDetail.Meter meter = shownMeter;
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
			for (int si = 0; si < rows.size(); si++) {
				MarkerDetail.Section s = detail.sections().get(si);
				cy += 14;
				ui.text(s.label().toUpperCase(Locale.ROOT), cx, cy, Ui.Font.F85, 1, Theme.INK_4);
				cy += 17;
				for (MarkerDetail.Row row : rows.get(si)) {
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
		ui.flush();
		ui.g.disableScissor();
		if (cut) {
			// more than the map has room for: a hairline over the footer says the body goes on
			ui.rect(cx, footY - 7, 262, 1, Theme.HAIR_8);
		}
		cy = footY;
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
			this.button(ui, cx, cy, 127, true, tracking ? "Tracking" : "Track", MarkerGlyph.TRACK, mx, my, () -> this.track(marker));
			this.button(ui, cx + 135, cy, 127, false, "Set waypoint", MarkerGlyph.PIN, mx, my, () -> this.waypointAt(marker, place));
		}
	}

	private void track(Marker m) {
		this.atlas.track(m);
	}

	/** A card's height with what it shows: the status line, the meter, the sections' rows, the facts, the source. */
	private static float cardHeight(boolean status, boolean meter, List<List<MarkerDetail.Row>> sections, int facts, boolean source) {
		float h = 17 + 40;
		if (status) {
			h += 14 + 14;
		}
		h += 14 + 60;
		if (meter) {
			h += 14 + 51;
		}
		for (List<MarkerDetail.Row> s : sections) {
			h += 14 + 11 + s.size() * 42;
		}
		if (facts > 0) {
			h += 14 + 11 + 8 + facts * 22 - 8;
		}
		if (source) {
			h += 14 + 26;
		}
		return h + 14 + 40 + 17;
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

	/** The right-click menu (the design's 248 px, at the cursor): the spot's coordinates, then its four actions. */
	private void contextMenu(Ui ui, float mx, float my) {
		Menu m = this.menu;
		if (m == null) {
			return;
		}
		String dimHere = this.map.dimensionOf(this.map.kind);
		boolean nether = dimHere.equals("minecraft:the_nether");
		List<String> items = new ArrayList<>(List.of("Waypoint here", "Copy coordinates"));
		if (nether || dimHere.equals("minecraft:overworld")) {
			items.add(nether ? "Show in Overworld" : "Show in Nether");
		}
		items.add("Track");
		if (this.atlas.canTeleport()) {
			items.add("Teleport here");
		}
		String[] keys = {"W", "C", nether || dimHere.equals("minecraft:overworld") ? (nether ? "O" : "N") : "T", "T", ""};
		MarkerGlyph[] glyphs = {MarkerGlyph.PIN, MarkerGlyph.DOT, MarkerGlyph.PORTAL, MarkerGlyph.TRACK, MarkerGlyph.REALM};
		if (items.size() >= 3 && items.get(2).equals("Track")) {
			glyphs = new MarkerGlyph[]{MarkerGlyph.PIN, MarkerGlyph.DOT, MarkerGlyph.TRACK, MarkerGlyph.REALM};
			keys = new String[]{"W", "C", "T", ""};
		}
		int bx = (int) Math.floor(m.mapX);
		int bz = (int) Math.floor(m.mapZ);
		int by = this.atlas.heightAt(this.map.layer(this.map.kind), bx, bz);
		String head = "X " + minus(bx) + "  ·  Y " + minus(by) + "  ·  Z " + minus(bz);
		float w = 248;
		float h = 8 + 26 + items.size() * 32 + 8;
		float x = Math.min(m.x, this.mapX + this.mapW - w - 8);
		float y = Math.min(m.y, this.mapY + this.mapH - h - 8);
		// a click anywhere else closes the menu (registered first, so the items win)
		this.hit(this.mapX - 2000, this.mapY - 2000, 8000, 8000, () -> this.menu = null);
		ui.panel(x, y, w, h, 14, Theme.ISLAND, Theme.HAIR_10);
		ui.text(head, x + 14, y + 12, Ui.Font.F85, 1, Theme.INK_4);
		for (int i = 0; i < items.size(); i++) {
			float iy = y + 8 + 26 + i * 32;
			boolean hover = mx >= x + 6 && mx <= x + w - 6 && my >= iy && my <= iy + 30;
			if (i == 3 || i == items.size() - 1 && items.get(i).equals("Track")) {
				ui.rect(x + 12, iy - 1, w - 24, 1, Theme.HAIR_6);
			}
			if (hover) {
				ui.round(x + 6, iy, w - 12, 30, 9, Theme.RAISED);
			}
			int ink = hover ? Theme.INK : Theme.INK_2;
			ui.glyph(glyphs[Math.min(i, glyphs.length - 1)], x + 18, iy + 9, 12, ink, ink);
			ui.text(items.get(i), x + 40, iy + 7.5F, Ui.Font.F13, ink);
			String key = i < keys.length ? keys[i] : "";
			if (!key.isEmpty()) {
				ui.textRight(key, x + w - 18, iy + 10, Ui.Font.F9, 0.6F, Theme.INK_4);
			}
			String item = items.get(i);
			this.hit(x + 6, iy, w - 12, 30, () -> this.menuAction(item, m));
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
			case "Show in Nether", "Show in the Nether" -> {
				// the same spot in the other dimension: an eighth of the way out from the middle of the world
				this.map.setKind(MapPanel.Kind.NETHER);
				this.map.centreOn(Math.floorDiv(bx, 8) + 0.5, Math.floorDiv(bz, 8) + 0.5);
			}
			case "Show in Overworld", "Show in the Overworld" -> {
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

	// --- the waypoint card ------------------------------------------------------------------------------------------

	/**
	 * A waypoint's card (the design's 355:14024): its pin and name, then where it is in its own dimension and in the
	 * other one (the Nether's X and Z an eighth, the Overworld's eight times, Y kept), how to use that, and Show in
	 * Nether (or Overworld) and Track. The name opens the editor.
	 */
	private void waypointCard(Ui ui, Waypoint wp, float x, float y, float mx, float my) {
		float w = 296;
		String dim = wp.dimension();
		boolean overworld = dim.equals("minecraft:overworld");
		boolean nether = dim.equals("minecraft:the_nether");
		dev.voidmc.atlas.core.waypoints.NetherScale.Point other = overworld || nether
			? dev.voidmc.atlas.core.waypoints.NetherScale.convert(wp.x(), wp.y(), wp.z(), dim, overworld ? "minecraft:the_nether" : "minecraft:overworld") : null;
		String note = other == null ? null : overworld ? "Build a portal at these Nether coordinates to come out near " + wp.name() + "."
			: "Build a portal at these Overworld coordinates to come out near " + wp.name() + ".";
		float h = 17 + 40 + 14 + 64 + (other != null ? 8 + 64 + 12 + 30 : 0) + 14 + 40 + 17;
		ui.panel(x, y, w, h, 20, Theme.ISLAND, Theme.HAIR_8);
		float cx = x + 17;
		float cy = y + 17;
		ui.round(cx, cy, 40, 40, 12, Theme.WELL);
		ui.border(cx, cy, 40, 40, 12, 0, Theme.HAIR_10);
		PinShapes.pin(ui, cx + 20, cy + 32, 16, wp.colour(), this.atlas.settings().shapeMarkers, wp.rgb(), 1);
		ui.text(ui.fit(wp.name(), Ui.Font.F16, w - 34 - 52 - 30), cx + 52, cy + 1, Ui.Font.F16, Theme.INK);
		String kind = (wp.kind() == Waypoint.Kind.DEATH ? "DEATH" : wp.kind() == Waypoint.Kind.TEMPORARY ? "TEMPORARY" : "WAYPOINT") + "  ·  "
			+ dimensionName(dim).toUpperCase(Locale.ROOT);
		ui.text(kind, cx + 52, cy + 24, Ui.Font.F85, 1, Theme.INK_4);
		Waypoint editTarget = wp;
		this.hit(cx + 52, cy, w - 34 - 52 - 30, 40, () -> this.edit(editTarget, false));
		Icons.close(ui, x + w - 30, cy + 4, Theme.INK_3);
		this.hit(x + w - 38, cy - 4, 30, 30, () -> this.map.selected = null);
		cy += 40 + 14;
		this.coordBlock(ui, cx, cy, w - 34, dimensionName(dim).toUpperCase(Locale.ROOT) + "  ·  WHERE IT IS", wp.x(), wp.y(), wp.z());
		cy += 64;
		if (other != null) {
			cy += 8;
			this.coordBlock(ui, cx, cy, w - 34, overworld ? "NETHER  ·  X AND Z DIVIDED BY 8" : "OVERWORLD  ·  X AND Z TIMES 8", other.x(), other.y(), other.z());
			cy += 64 + 12;
			List<String> lines = wrap(ui, note, Ui.Font.F11, w - 34, 2);
			for (int i = 0; i < lines.size(); i++) {
				ui.text(lines.get(i), cx, cy + i * 15, Ui.Font.F11, Theme.INK_3);
			}
			cy += 30;
		}
		cy += 14;
		boolean tracking = AtlasClient.waypointMarkerId(wp).equals(this.atlas.settings().tracked);
		if (other != null) {
			String label = overworld ? "Show in Nether" : "Show in Overworld";
			this.button(ui, cx, cy, 127, true, label, MarkerGlyph.PORTAL, mx, my, () -> {
				this.map.setKind(overworld ? MapPanel.Kind.NETHER : MapPanel.Kind.SURFACE);
				this.map.centreOn(other.x() + 0.5, other.z() + 0.5);
			});
			this.button(ui, cx + 135, cy, 127, false, tracking ? "Tracking" : "Track", MarkerGlyph.TRACK, mx, my, () -> this.track(AtlasClient.waypointMarkerId(wp)));
		} else {
			this.button(ui, cx, cy, w - 34, false, tracking ? "Tracking" : "Track", MarkerGlyph.TRACK, mx, my, () -> this.track(AtlasClient.waypointMarkerId(wp)));
		}
	}

	/** Words laid into lines no wider than {@code max}, at most {@code lines} of them (the last cut short if need be). */
	static List<String> wrap(Ui ui, String text, Ui.Font font, float max, int lines) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.length() == 0 ? word : line + " " + word;
			if (ui.width(next, font) <= max || line.length() == 0) {
				line.setLength(0);
				line.append(next);
			} else {
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		if (out.size() > lines) {
			List<String> cut = new ArrayList<>(out.subList(0, lines));
			cut.set(lines - 1, ui.fit(String.join(" ", out.subList(lines - 1, out.size())), font, max));
			return cut;
		}
		return out;
	}

	/** A well with a caps line over X, Y and Z. */
	private void coordBlock(Ui ui, float x, float y, float w, String caps, int bx, int by, int bz) {
		ui.round(x, y, w, 64, 12, Theme.WELL);
		ui.border(x, y, w, 64, 12, 0, Theme.HAIR_6);
		ui.text(caps, x + 12, y + 12, Ui.Font.F85, 1, Theme.INK_4);
		float tx = x + 12;
		String[] axes = {"X", "Y", "Z"};
		int[] v = {bx, by, bz};
		for (int i = 0; i < 3; i++) {
			ui.text(axes[i], tx, y + 35, Ui.Font.F11, Theme.INK_4);
			ui.text(minus(v[i]), tx + 11, y + 32, Ui.Font.F15, Theme.INK);
			tx += 11 + ui.width(minus(v[i]), Ui.Font.F15) + 20;
		}
	}

	// --- the world picker ------------------------------------------------------------------------------------------

	/** The world picker (where the gear was): the map shown, a violet dot, a chevron; opens the list of maps. */
	private void worldPicker(Ui ui, float x, float y, float mx, float my) {
		var worlds = this.atlas.worlds;
		var current = worlds.current();
		String label;
		if (current.isPresent()) {
			var c = current.get();
			label = c.server().equals("singleplayer") ? c.name() : shortServer(c.server()) + "  ·  " + c.name();
		} else {
			label = worlds.finding() ? "Finding this world..." : "This world";
		}
		float w = 16 + 6 + 10 + ui.width(label, Ui.Font.F13) + 12 + 10 + 14;
		boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 36;
		ui.panel(x, y, w, 36, 18, hover || this.pickerOpen ? Theme.ISLAND : Theme.WELL, Theme.HAIR_8);
		ui.circle(x + 16 + 3, y + 18, 3, Theme.VIOLET);
		ui.text(label, x + 16 + 6 + 10, y + 9, Ui.Font.F13, Theme.INK);
		float chx = x + w - 14 - 8;
		ui.line(chx, y + 16, chx + 4, y + 20, 1.4F, Theme.INK_2);
		ui.line(chx + 4, y + 20, chx + 8, y + 16, 1.4F, Theme.INK_2);
		if (worlds.question().isPresent()) {
			ui.circle(x + w - 4, y + 4, 4, Theme.VIOLET);
		}
		this.hit(x, y, w, 36, () -> this.pickerOpen = !this.pickerOpen);
	}

	private static String shortServer(String address) {
		String a = address.replaceFirst(":\\d+$", "");
		String[] parts = a.split("\\.");
		if (parts.length >= 2 && !a.matches("[0-9.]+")) {
			String name = parts[parts.length - 2];
			return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
		}
		return a;
	}

	/** The picker's list: every map of this server (this one marked), the open question, the note. */
	private void pickerList(Ui ui, float x, float y, float mx, float my) {
		var worlds = this.atlas.worlds;
		var list = worlds.list();
		var question = worlds.question().orElse(null);
		float w = 272;
		float h = 12 + 20 + list.size() * 46 + (question != null ? 96 : 0) + 34 + 6;
		this.hit(this.mapX - 2000, this.mapY - 2000, 8000, 8000, () -> this.pickerOpen = false);
		ui.panel(x, y, w, h, 14, Theme.ISLAND, Theme.HAIR_10);
		String server = list.isEmpty() ? "" : list.get(0).server();
		String caps = (server.equals("singleplayer") ? "SINGLEPLAYER" : server.toUpperCase(Locale.ROOT)) + "  ·  " + list.size() + (list.size() == 1 ? " MAP" : " MAPS");
		ui.text(caps, x + 16, y + 14, Ui.Font.F85, 1, Theme.INK_4);
		float ry = y + 12 + 20;
		long now = System.currentTimeMillis();
		for (var info : list) {
			if (info.current()) {
				ui.round(x + 6, ry, w - 12, 44, 10, Theme.RAISED);
			}
			ui.circle(x + 18, ry + 15, 3, info.current() ? Theme.VIOLET : Theme.INK_4);
			ui.text(ui.fit(info.name(), Ui.Font.F13, w - 60), x + 30, ry + 6, Ui.Font.F13, info.current() ? Theme.INK : Theme.INK_2);
			String sub = info.current() ? "YOU ARE HERE" : seen(now - info.lastUsed());
			if (info.places() >= 0) {
				sub += "  ·  " + info.places() + " PLACES";
			}
			ui.text(sub, x + 30, ry + 26, Ui.Font.F85, 1, Theme.INK_4);
			ry += 46;
		}
		if (question != null) {
			ui.rect(x + 12, ry + 2, w - 24, 1, Theme.HAIR_6);
			ui.text("Same map as " + ui.fit(question.candidateName(), Ui.Font.F12, 150) + "?", x + 16, ry + 14, Ui.Font.F12, Theme.INK);
			ui.text("A new world was found here", x + 16, ry + 32, Ui.Font.F11, Theme.INK_4);
			this.button(ui, x + 12, ry + 50, 120, true, "Keep separate", null, mx, my, () -> {
				worlds.answer(false);
				this.pickerOpen = false;
			});
			this.button(ui, x + 140, ry + 50, 120, false, "Same as before", null, mx, my, () -> {
				worlds.answer(true);
				this.pickerOpen = false;
			});
			ry += 96;
		}
		ui.rect(x + 12, ry + 2, w - 24, 1, Theme.HAIR_6);
		ui.text("Merge or rename maps in Atlas settings", x + 16, ry + 14, Ui.Font.F11, Theme.INK_4);
	}

	private static String seen(long ago) {
		long days = ago / 86_400_000L;
		if (ago < 0 || days < 1) {
			return "SEEN TODAY";
		}
		return days == 1 ? "SEEN YESTERDAY" : "SEEN " + days + " DAYS AGO";
	}

	// --- search ----------------------------------------------------------------------------------------------------

	private static final String[] GROUPS = {"PLACES", "BIOMES", "WAYPOINTS"};

	private void refreshSearch() {
		String q = this.search.text.strip();
		long now = System.currentTimeMillis();
		if (q.equals(this.searchHitsFor) && now - this.searchHitsAt < 500) {
			return;
		}
		this.searchHitsFor = q;
		this.searchHitsAt = now;
		MapSession session = this.atlas.session();
		List<SearchHit> out = new ArrayList<>();
		int[] counts = new int[3];
		if (session == null) {
			this.searchHits = out;
			return;
		}
		LocalPlayer p = Minecraft.getInstance().player;
		double px = p == null ? 0 : p.getX();
		double pz = p == null ? 0 : p.getZ();
		String here = p == null ? "" : p.level().dimension().identifier().toString();
		String dim = this.map.dimensionOf(this.map.kind);
		Place regionHere = this.atlas.regionHere();
		List<SearchHit>[] groups = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
		List<Integer>[] scores = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
		for (Place pl : session.places.visible(dim)) {
			boolean biome = pl.kind().equals(Place.REGION);
			int g = biome ? 1 : 0;
			counts[g]++;
			String kind = biome ? "BIOME" : Kinds.of(pl).word;
			boolean inside = biome && regionHere != null && regionHere.id().equals(pl.id());
			String sub = kind + "  ·  " + (inside ? "YOU ARE HERE" : biome ? (pl.underground() ? "UNDERGROUND" : "NEAREST EDGE") : Kinds.origin(pl));
			int score = dev.voidmc.atlas.core.Search.score(q, pl.name(), sub);
			if (score == dev.voidmc.atlas.core.Search.NONE) {
				continue;
			}
			boolean same = pl.dimension().equals(here);
			double d = Math.max(0, Math.hypot(pl.x() + 0.5 - px, pl.z() + 0.5 - pz) - (biome ? pl.radius() : 0));
			String right = inside ? "here" : same ? MarkerPainter.distance(d) : dimensionName(pl.dimension());
			MarkerGlyph glyph = biome ? MarkerGlyph.DOT : Kinds.sighted(pl) ? MarkerGlyph.UNKNOWN : Kinds.of(pl).glyph;
			groups[g].add(new SearchHit(g, AtlasClient.placeMarkerId(pl), glyph, Kinds.sighted(pl) && !biome, pl.name(), sub, right, pl.x() + 0.5, pl.z() + 0.5,
				pl.dimension()));
			scores[g].add(score * 100_000 - (int) Math.min(99_999, d));
		}
		for (Waypoint w : session.waypoints.all()) {
			counts[2]++;
			String sub = "WAYPOINT  ·  " + (w.expires() > 0 ? "GONE IN " + Math.max(1, (w.expires() - now) / 60_000) + " MIN" : dimensionName(w.dimension()).toUpperCase(Locale.ROOT));
			int score = dev.voidmc.atlas.core.Search.score(q, w.name(), sub);
			if (score == dev.voidmc.atlas.core.Search.NONE) {
				continue;
			}
			boolean same = w.dimension().equals(here);
			double d = w.distance(px, 0, pz);
			groups[2].add(new SearchHit(2, AtlasClient.waypointMarkerId(w), w.glyph(), false, w.name(), sub, same ? MarkerPainter.distance(d) : dimensionName(w.dimension()),
				w.x() + 0.5, w.z() + 0.5, w.dimension()));
			scores[2].add(score * 100_000 - (int) Math.min(99_999, d));
		}
		for (int g = 0; g < 3; g++) {
			List<Integer> sc = scores[g];
			List<SearchHit> hits = groups[g];
			List<Integer> idx = new ArrayList<>();
			for (int i = 0; i < hits.size(); i++) {
				idx.add(i);
			}
			idx.sort((a, b) -> Integer.compare(sc.get(b), sc.get(a)));
			for (int i = 0; i < Math.min(6, idx.size()); i++) {
				out.add(hits.get(idx.get(i)));
			}
		}
		this.searchHits = out;
		this.searchCounts = counts;
	}

	/** What the search found, by group (up to 6 each), under the search box; Enter centres on the first. */
	private void searchResults(Ui ui, float x, float y, float w, float mx, float my) {
		this.refreshSearch();
		List<SearchHit> hits = new ArrayList<>(this.searchHits);
		// as many as fit above the window's foot: fewest from the biggest group first
		float room = this.mapY + this.mapH + 40 - y;
		while (!hits.isEmpty() && 10 + 3 * 24 + hits.size() * 46 + 34 > room) {
			int[] count = new int[3];
			for (SearchHit h : hits) {
				count[h.group]++;
			}
			int biggest = count[0] >= count[1] && count[0] >= count[2] ? 0 : count[1] >= count[2] ? 1 : 2;
			for (int i = hits.size() - 1; i >= 0; i--) {
				if (hits.get(i).group == biggest) {
					hits.remove(i);
					break;
				}
			}
		}
		int[] per = new int[3];
		for (SearchHit h : hits) {
			per[h.group]++;
		}
		int groups = (per[0] > 0 ? 1 : 0) + (per[1] > 0 ? 1 : 0) + (per[2] > 0 ? 1 : 0);
		float h = 10 + groups * 24 + hits.size() * 46 + 34 + (hits.isEmpty() ? 40 : 0);
		this.hit(x, y, w, h, () -> {
		});
		ui.panel(x, y, w, h, 16, Theme.ISLAND, Theme.HAIR_10);
		float ry = y + 10;
		if (hits.isEmpty()) {
			ui.text("Nothing found", x + 16, ry + 12, Ui.Font.F13, Theme.INK_3);
			ry += 40;
		}
		int lastGroup = -1;
		boolean first = true;
		for (SearchHit hit : hits) {
			if (hit.group != lastGroup) {
				lastGroup = hit.group;
				ui.text(GROUPS[hit.group] + "  ·  " + per[hit.group], x + 16, ry + 8, Ui.Font.F85, 1, Theme.INK_4);
				ry += 24;
			}
			boolean focused = first && (this.searchGroup == hit.group || per[this.searchGroup] == 0);
			boolean hover = mx >= x + 6 && mx <= x + w - 6 && my >= ry && my <= ry + 44;
			if (focused || hover) {
				ui.round(x + 6, ry, w - 12, 44, 10, Theme.RAISED);
			}
			if (this.searchGroup == hit.group) {
				first = false;
			}
			MarkerPainter.placeTile(ui, x + 14, ry + 8, 28, 8, hit.glyph, hit.hollow, 16, 0.9F);
			ui.text(ui.fit(hit.title, Ui.Font.F13, w - 140), x + 52, ry + 6, Ui.Font.F13, Theme.INK);
			ui.text(ui.fit(hit.sub, Ui.Font.F85, 1, w - 140), x + 52, ry + 26, Ui.Font.F85, 1, Theme.INK_4);
			ui.textRight(hit.right, x + w - 18, ry + 15, Ui.Font.F11, 0, Theme.INK_2);
			SearchHit target = hit;
			this.hit(x + 6, ry, w - 12, 44, () -> this.goTo(target));
			ry += 46;
		}
		ui.text("Enter centres the map on it  ·  Tab moves between groups", x + 16, ry + 10, Ui.Font.F11, Theme.INK_4);
	}

	/** Centres the map on a search result and selects it. */
	private void goTo(SearchHit hit) {
		if (hit.dimension.equals("minecraft:the_nether")) {
			this.map.setKind(MapPanel.Kind.NETHER);
		} else if (this.map.kind == MapPanel.Kind.NETHER) {
			this.map.setKind(MapPanel.Kind.SURFACE);
		}
		this.map.centreOn(hit.x, hit.z);
		this.map.selected = hit.id;
		this.searching = false;
		this.search.set("");
		this.search.focused = false;
	}

	/** Enter in the search: the focused group's best result. */
	private boolean searchEnter() {
		this.refreshSearch();
		for (SearchHit h : this.searchHits) {
			if (h.group == this.searchGroup) {
				this.goTo(h);
				return true;
			}
		}
		if (!this.searchHits.isEmpty()) {
			this.goTo(this.searchHits.get(0));
			return true;
		}
		return false;
	}

	/** Tab: the next group that has results. */
	private void searchTab() {
		for (int i = 1; i <= 3; i++) {
			int g = (this.searchGroup + i) % 3;
			for (SearchHit h : this.searchHits) {
				if (h.group == g) {
					this.searchGroup = g;
					return;
				}
			}
		}
	}

	// --- import from other map mods --------------------------------------------------------------------------------

	/** Once per world: looks (on a worker) for waypoints of this world in Xaero's and JourneyMap's files. */
	private void lookForImports() {
		if (this.importLooked || this.atlas.settings().importOffered || this.atlas.session() == null) {
			return;
		}
		this.importLooked = true;
		Minecraft mc = Minecraft.getInstance();
		java.nio.file.Path game = mc.gameDirectory.toPath();
		String key = this.atlas.session().worldKey;
		this.atlas.session().workers.execute(() -> {
			List<dev.voidmc.atlas.core.waypoints.ImportedWaypoint> found = new ArrayList<>();
			try {
				found.addAll(dev.voidmc.atlas.core.waypoints.XaeroImport.scan(game));
				found.addAll(dev.voidmc.atlas.core.waypoints.JourneyMapImport.scan(game));
			} catch (RuntimeException e) {
				return;
			}
			List<dev.voidmc.atlas.core.waypoints.ImportedWaypoint> mine = found.stream().filter(w -> sameWorld(w.world(), key)).toList();
			if (!mine.isEmpty()) {
				mc.execute(() -> this.importOffer = mine);
			}
		});
	}

	/** Whether another mod's world folder name is this world (the server's address, or the save's folder). */
	static boolean sameWorld(String folder, String key) {
		String f = folder.toLowerCase(Locale.ROOT).replace('~', '.').replace("multiplayer_", "").replace("mp/", "").replace("sp/", "");
		String[] parts = key.split("/");
		String name = parts.length > 1 ? parts[1] : key;
		String plain = name.replace('_', '.');
		return !f.isBlank() && (f.contains(name) || f.contains(plain) || name.contains(f.replaceAll("[^a-z0-9._-]", "_")));
	}

	// --- for the tour ----------------------------------------------------------------------------------------------

	/** Opens the right-click menu at a point of the map (screen design pixels). */
	public void openMenuAt(float x, float y) {
		MapRenderer.Frame f = this.map.frame();
		if (f != null) {
			this.menu = new Menu(x, y, f.mapX(x, y), f.mapZ(x, y));
		}
	}

	public void closeMenu() {
		this.menu = null;
	}

	/** The map's rectangle on screen: x, y, w, h (design pixels). */
	public float[] mapRect() {
		return new float[]{this.mapX, this.mapY, this.mapW, this.mapH};
	}

	/** Types a search. */
	public void searchFor(String text) {
		this.searching = true;
		this.search.focused = true;
		this.search.set(text);
	}

	/** Opens or closes the world picker's list. */
	public void showPicker(boolean open) {
		this.pickerOpen = open;
	}

	/** Offers the import again (from the settings). */
	public void offerImport() {
		this.importLooked = false;
		this.atlas.settings().importOffered = false;
	}

	/** The one-time import card, over the right panel (the design's 355:14293). */
	private void importCard(Ui ui, float x, float y, float w, float mx, float my) {
		var found = this.importOffer;
		if (found == null) {
			return;
		}
		long xaero = found.stream().filter(i -> i.source() == dev.voidmc.atlas.core.waypoints.ImportedWaypoint.Source.XAERO).count();
		long jm = found.size() - xaero;
		int shown = Math.min(4, found.size());
		float h = 18 + 14 + 26 + 36 + 8 + shown * 35 + (found.size() > shown ? 22 : 4) + 12 + 56 + 14 + 40 + 12 + 30 + 14;
		this.hit(x, y, w, h, () -> {
		});
		ui.panel(x, y, w, h, 20, Theme.ISLAND, Theme.HAIR_8);
		float cx = x + 17;
		float cy = y + 18;
		ui.text("ONE TIME  ·  FROM OTHER MAP MODS", cx, cy, Ui.Font.F85, 1, Theme.INK_4);
		cy += 14;
		ui.text("Found " + found.size() + " waypoints", cx, cy, Ui.Font.F16, Theme.INK);
		cy += 26;
		String from = (xaero > 0 ? "Xaero's Minimap (" + xaero + ")" : "") + (xaero > 0 && jm > 0 ? " and " : "") + (jm > 0 ? "JourneyMap (" + jm + ")" : "");
		String line = "In this instance: " + from + ".";
		List<String> lines = wrap(ui, line, Ui.Font.F11, w - 34, 2);
		for (int i = 0; i < lines.size(); i++) {
			ui.text(lines.get(i), cx, cy + i * 15, Ui.Font.F11, Theme.INK_3);
		}
		cy += 36 + 8;
		boolean shapes = this.atlas.settings().shapeMarkers;
		for (int i = 0; i < shown; i++) {
			var iw = found.get(i);
			ui.round(cx, cy, w - 34, 32, 9, Theme.WELL);
			int colour = this.importKeepColours ? iw.colour() : 0;
			PinShapes.pin(ui, cx + 14, cy + 25, 12, colour, shapes, Waypoint.COLOURS[Math.floorMod(colour, 8)], 1);
			ui.text(ui.fit(iw.name(), Ui.Font.F12, w - 34 - 120), cx + 30, cy + 9, Ui.Font.F12, Theme.INK);
			ui.textRight(iw.source() == dev.voidmc.atlas.core.waypoints.ImportedWaypoint.Source.XAERO ? "XAERO'S" : "JOURNEYMAP", cx + w - 34 - 10, cy + 12, Ui.Font.F85, 1,
				Theme.INK_4);
			cy += 35;
		}
		if (found.size() > shown) {
			ui.text("and " + (found.size() - shown) + " more", cx, cy + 2, Ui.Font.F11, Theme.INK_3);
			cy += 22;
		} else {
			cy += 4;
		}
		cy += 12;
		ui.round(cx, cy, w - 34, 56, 12, Theme.WELL);
		ui.text("Keep their colours", cx + 16, cy + 12, Ui.Font.F12, Theme.INK);
		ui.text("Matched to the nearest of the 8", cx + 16, cy + 30, Ui.Font.F11, Theme.INK_4);
		float tx = cx + w - 34 - 16 - 32;
		ui.round(tx, cy + 19, 32, 18, 9, this.importKeepColours ? Theme.INK_2 : Theme.RAISED);
		ui.circle(this.importKeepColours ? tx + 23 : tx + 9, cy + 28, 6, this.importKeepColours ? Theme.SHELL : Theme.INK_3);
		this.hit(cx, cy, w - 34, 56, () -> this.importKeepColours = !this.importKeepColours);
		cy += 56 + 14;
		float bw = (w - 34 - 8) / 2;
		this.button(ui, cx, cy, bw, true, "Import " + found.size(), null, mx, my, () -> this.doImport(found));
		this.button(ui, cx + bw + 8, cy, bw, false, "Not now", null, mx, my, () -> {
			this.importOffer = null;
			this.atlas.settings().importOffered = true;
			this.atlas.settings().save();
		});
		cy += 40 + 12;
		ui.text("Their files are only read, never changed. Later:", cx, cy, Ui.Font.F11, Theme.INK_4);
		ui.text("Settings, Data, Import.", cx, cy + 15, Ui.Font.F11, Theme.INK_4);
	}

	private void doImport(List<dev.voidmc.atlas.core.waypoints.ImportedWaypoint> found) {
		MapSession session = this.atlas.session();
		if (session != null) {
			long now = System.currentTimeMillis();
			for (var iw : found) {
				Waypoint w = iw.toWaypoint(64, now);
				if (!this.importKeepColours) {
					w = w.withColour(0);
				}
				session.waypoints.put(w);
			}
			session.saveSoon(false);
		}
		this.importOffer = null;
		this.atlas.settings().importOffered = true;
		this.atlas.settings().save();
	}

	// --- export ----------------------------------------------------------------------------------------------------

	/** Opens the export dialog on what the map shows. */
	public void openExport() {
		MapRenderer.Frame f = this.map.frame();
		Export e = new Export();
		e.preview = new MapPanel(this.atlas);
		e.preview.setKind(this.map.kind);
		if (f != null) {
			e.bx0 = Math.min(f.mapX(f.x, f.y), f.mapX(f.x + f.w, f.y + f.h));
			e.bz0 = Math.min(f.mapZ(f.x, f.y), f.mapZ(f.x + f.w, f.y + f.h));
			e.bx1 = Math.max(f.mapX(f.x, f.y), f.mapX(f.x + f.w, f.y + f.h));
			e.bz1 = Math.max(f.mapZ(f.x, f.y), f.mapZ(f.x + f.w, f.y + f.h));
		}
		this.export = e;
		this.setExportArea(e, 1);
	}

	private void setExportArea(Export e, int area) {
		e.area = area;
		var layer = this.map.layer(this.map.kind);
		if (area == 0) {
			MapRenderer.Frame f = this.map.frame();
			if (f != null) {
				e.bx0 = Math.min(f.mapX(f.x, f.y), f.mapX(f.x + f.w, f.y + f.h));
				e.bz0 = Math.min(f.mapZ(f.x, f.y), f.mapZ(f.x + f.w, f.y + f.h));
				e.bx1 = Math.max(f.mapX(f.x, f.y), f.mapX(f.x + f.w, f.y + f.h));
				e.bz1 = Math.max(f.mapZ(f.x, f.y), f.mapZ(f.x + f.w, f.y + f.h));
			}
		} else if (area == 1 && layer != null) {
			long[] keys = layer.regionKeys();
			if (keys.length > 0) {
				int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
				for (long k : keys) {
					minX = Math.min(minX, dev.voidmc.atlas.core.Coords.unpackX(k));
					minZ = Math.min(minZ, dev.voidmc.atlas.core.Coords.unpackZ(k));
					maxX = Math.max(maxX, dev.voidmc.atlas.core.Coords.unpackX(k));
					maxZ = Math.max(maxZ, dev.voidmc.atlas.core.Coords.unpackZ(k));
				}
				int rs = dev.voidmc.atlas.core.Coords.REGION_SIZE;
				e.bx0 = (double) minX * rs;
				e.bz0 = (double) minZ * rs;
				e.bx1 = (double) (maxX + 1) * rs;
				e.bz1 = (double) (maxZ + 1) * rs;
			}
		}
	}

	/** The export dialog (the design's 355:14426): a preview with the box, the area, the scale, two toggles, Save. */
	private void exportDialog(Ui ui, float wx, float wy, float ww, float wh, float mx, float my) {
		Export e = this.export;
		float w = 580;
		float h = 796;
		float x = wx + (ww - w) / 2;
		float y = wy + Math.max(-60, (wh - h) / 2);
		// everything behind is out of reach while it is open
		this.hit(wx - 4000, wy - 4000, 9000, 9000, () -> {
		});
		ui.rect(wx - 4000, wy - 4000, 9000, 9000, 0x8C000000);
		ui.panel(x, y, w, h, 24, Theme.ISLAND, Theme.HAIR_10);
		String caps = this.map.kind.label.toUpperCase(Locale.ROOT) + "  ·  " + this.atlas.worlds.current().map(c -> c.name().toUpperCase(Locale.ROOT)).orElse("");
		ui.text(caps, x + 20, y + 22, Ui.Font.F85, 1, Theme.INK_4);
		ui.text("Export map as image", x + 20, y + 36, Ui.Font.F18, Theme.INK);
		float ex = x + w - 20 - 38;
		ui.panel(ex, y + 26, 38, 26, 8, Theme.WELL, Theme.HAIR_10);
		ui.text("Esc", ex + 9, y + 31, Ui.Font.F11, Theme.INK_2);
		this.hit(ex, y + 26, 38, 26, () -> this.export = null);
		// the preview: the whole area, the box over it
		e.px = x + 20;
		e.py = y + 74;
		e.pw = w - 40;
		e.ph = 300;
		double bw = Math.max(16, e.bx1 - e.bx0);
		double bh = Math.max(16, e.bz1 - e.bz0);
		if (!e.dragging) {
			e.preview.centreOn((e.bx0 + e.bx1) / 2, (e.bz0 + e.bz1) / 2);
			e.preview.setZoom(Math.min(e.pw * ui.unit / bw, e.ph * ui.unit / bh) * 0.82);
		}
		MapRenderer.Frame f = e.preview.render(ui, e.px, e.py, e.pw, e.ph, 16, mx, my, false);
		float sx0 = f.screenX(e.bx0, e.bz0);
		float sy0 = f.screenY(e.bx0, e.bz0);
		float sx1 = f.screenX(e.bx1, e.bz1);
		float sy1 = f.screenY(e.bx1, e.bz1);
		ui.border(sx0, sy0, sx1 - sx0, sy1 - sy0, 0, 1.5F, 0xE6EDEEEF);
		for (float[] c : new float[][]{{sx0, sy0}, {sx1, sy0}, {sx0, sy1}, {sx1, sy1}}) {
			ui.round(c[0] - 4, c[1] - 4, 8, 8, 1.5F, Theme.INK);
		}
		String blocks = Math.round(bw) + " × " + Math.round(bh) + " blocks";
		ui.panel(sx0 + 8, sy0 + 8, ui.width(blocks, Ui.Font.F85, 0.5F) + 16, 20, 6, 0xD90B0B0C, Theme.HAIR_8);
		ui.text(blocks, sx0 + 16, sy0 + 13, Ui.Font.F85, 0.5F, Theme.INK_2);
		if (e.area == 2) {
			this.hit(e.px, e.py, e.pw, e.ph, () -> {
				e.dragging = true;
				e.dragX = f.mapX(mx, my);
				e.dragZ = f.mapZ(mx, my);
				e.bx0 = e.bx1 = e.dragX;
				e.bz0 = e.bz1 = e.dragZ;
			});
			if (e.dragging) {
				double cx = f.mapX(mx, my);
				double cz = f.mapZ(mx, my);
				e.bx0 = Math.min(e.dragX, cx);
				e.bx1 = Math.max(e.dragX, cx);
				e.bz0 = Math.min(e.dragZ, cz);
				e.bz1 = Math.max(e.dragZ, cz);
			}
		}
		float cy = e.py + e.ph + 22;
		ui.text("AREA", x + 20, cy, Ui.Font.F85, 1, Theme.INK_4);
		cy += 16;
		this.segmented(ui, x + 20, cy, w - 40, new String[]{"What you see", "Everything explored", "Draw a box"}, e.area, i -> this.setExportArea(e, i), mx, my);
		cy += 40 + 22;
		ui.text("SCALE", x + 20, cy, Ui.Font.F85, 1, Theme.INK_4);
		cy += 16;
		int scaleIndex = e.scale == 1 ? 0 : e.scale == 2 ? 1 : 2;
		this.segmented(ui, x + 20, cy, w - 40, new String[]{"1 px a block", "2 px", "4 px"}, scaleIndex, i -> e.scale = i == 0 ? 1 : i == 1 ? 2 : 4, mx, my);
		cy += 40 + 10;
		int[] size = dev.voidmc.atlas.core.MapExport.size((int) Math.floor(e.bx0), (int) Math.floor(e.bz0), (int) Math.ceil(e.bx1), (int) Math.ceil(e.bz1), e.scale);
		double mb = size[0] * (double) size[1] * 4 * 0.3 / 1e6;
		ui.text(String.format(Locale.ROOT, "%,d × %,d px  ·  about %d MB", size[0], size[1], Math.max(1, Math.round(mb))).replace(',', ' '), x + 20, cy, Ui.Font.F11,
			Theme.INK_3);
		cy += 26;
		this.toggleRow(ui, x + 20, cy, w - 40, "Markers and names", "As on screen", e.markers, () -> e.markers = !e.markers, mx, my);
		cy += 56 + 16;
		this.toggleRow(ui, x + 20, cy, w - 40, "Transparent outside explored land", "Off: the map well colour", e.transparent, () -> e.transparent = !e.transparent, mx,
			my);
		cy += 56 + 16;
		float half = (w - 40 - 8) / 2;
		this.button(ui, x + 20, cy, half, true, "Save PNG", null, mx, my, this::saveExport);
		this.button(ui, x + 20 + half + 8, cy, half, false, "Cancel", null, mx, my, () -> this.export = null);
		cy += 40 + 14;
		ui.text("Saves to screenshots/atlas/" + this.exportName() + " and opens the folder.", x + 20, cy, Ui.Font.F11, Theme.INK_4);
	}

	private void segmented(Ui ui, float x, float y, float w, String[] labels, int active, java.util.function.IntConsumer pick, float mx, float my) {
		ui.panel(x, y, w, 40, 12, Theme.WELL, Theme.HAIR_6);
		float sw = (w - 8) / labels.length;
		for (int i = 0; i < labels.length; i++) {
			float sx = x + 4 + i * sw;
			boolean on = i == active;
			boolean hover = !on && mx >= sx && mx <= sx + sw && my >= y + 4 && my <= y + 36;
			if (on) {
				ui.panel(sx, y + 4, sw, 32, 9, Theme.RAISED, Theme.HAIR_8);
			} else if (hover) {
				ui.round(sx, y + 4, sw, 32, 9, Theme.ISLAND);
			}
			ui.text(labels[i], sx + 14, y + 12, Ui.Font.F13, on ? Theme.INK : Theme.INK_2);
			int index = i;
			this.hit(sx, y + 4, sw, 32, () -> pick.accept(index));
		}
	}

	private void toggleRow(Ui ui, float x, float y, float w, String title, String sub, boolean on, Runnable flip, float mx, float my) {
		ui.panel(x, y, w, 56, 12, Theme.WELL, Theme.HAIR_6);
		ui.text(title, x + 16, y + 11, Ui.Font.F13, Theme.INK);
		ui.text(sub, x + 16, y + 31, Ui.Font.F11, Theme.INK_4);
		float tx = x + w - 16 - 32;
		ui.round(tx, y + 19, 32, 18, 9, on ? Theme.INK_2 : Theme.RAISED);
		ui.circle(on ? tx + 23 : tx + 9, y + 28, 6, on ? Theme.SHELL : Theme.INK_3);
		this.hit(x, y, w, 56, flip);
	}

	private String exportName() {
		String world = this.atlas.worlds.current().map(c -> c.name()).orElse("map").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-|-$)", "");
		return (world.isEmpty() ? "map" : world) + "-" + java.time.LocalDate.now() + ".png";
	}

	/** Opens a folder in the system's file browser (best effort). */
	private static void openFolder(java.nio.file.Path dir) {
		try {
			String path = dir.toAbsolutePath().normalize().toString();
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			// os.name is "Windows 11", "Mac OS X", "Linux" or a BSD; matched by prefix, since "darwin" contains "win"
			String[][] tries = os.startsWith("mac") || os.startsWith("darwin") ? new String[][]{{"open", path}}
				: os.startsWith("windows") ? new String[][]{{"explorer.exe", path}}
				: new String[][]{{"xdg-open", path}, {"gio", "open", path}, {"kde-open", path}};
			for (String[] cmd : tries) {
				Process p;
				try {
					// the browser's own output is discarded, so a chatty one never blocks on a full pipe
					p = new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD)
						.redirectError(ProcessBuilder.Redirect.DISCARD).start();
				} catch (java.io.IOException e) {
					continue; // not installed here: try the next one
				}
				try {
					p.getOutputStream().close();
				} catch (java.io.IOException ignored) {
					// it has no use for input anyway
				}
				return;
			}
		} catch (RuntimeException | java.io.IOError ignored) {
			// no file browser: the path is in the chat
		}
	}

	/** Saves the export on a worker, then opens the folder. */
	private void saveExport() {
		Export e = this.export;
		MapSession session = this.atlas.session();
		var layer = this.map.layer(this.map.kind);
		if (e == null || session == null || layer == null) {
			return;
		}
		this.export = null;
		java.nio.file.Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("atlas");
		java.nio.file.Path file = dir.resolve(this.exportName());
		if (java.nio.file.Files.exists(file)) {
			file = dir.resolve(this.exportName().replace(".png", "-" + System.currentTimeMillis() % 100000 + ".png"));
		}
		java.nio.file.Path target = file;
		int x0 = (int) Math.floor(e.bx0);
		int z0 = (int) Math.floor(e.bz0);
		int x1 = (int) Math.ceil(e.bx1);
		int z1 = (int) Math.ceil(e.bz1);
		int scale = e.scale;
		int background = e.transparent ? 0 : Theme.MAP_WELL;
		session.workers.execute(() -> {
			String message;
			try {
				var r = dev.voidmc.atlas.core.MapExport.export(layer::levelPixels, x0, z0, x1, z1, target, scale, background);
				message = "Atlas: saved " + r.file().getFileName() + " (" + r.width() + " x " + r.height() + ")";
				openFolder(dir);
			} catch (java.io.IOException | RuntimeException ex) {
				message = "Atlas: the map could not be saved: " + ex.getMessage();
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
		if (this.export != null) {
			this.export.dragging = false;
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
		var level = Minecraft.getInstance().level;
		if (level != null) {
			// anywhere in the world's height; the window follows
			this.map.setBand(Math.max(DimensionData.bandOf(level.getMinY()), Math.min(DimensionData.bandOf(level.getMinY() + level.getHeight() - 1), band)));
			return;
		}
		int top = DimensionData.bandOf(this.scrubTop);
		int bottom = DimensionData.bandOf(this.scrubBottom);
		this.map.setBand(Math.max(bottom, Math.min(top, band)));
	}

	/** Returns true if the key was used. Escape: closes the topmost card or the search first. */
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (this.layersOpen && key == InputConstants.KEY_ESCAPE) {
			this.layersOpen = false;
			return true;
		}
		if (this.export != null) {
			if (key == InputConstants.KEY_ESCAPE) {
				this.export = null;
			} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				this.saveExport();
			}
			return true;
		}
		Menu open = this.menu;
		if (open != null) {
			// the menu's keys: W, C, N (or O in the Nether), T
			String item = switch (key) {
				case InputConstants.KEY_W -> "Waypoint here";
				case InputConstants.KEY_C -> "Copy coordinates";
				case InputConstants.KEY_N -> "Show in Nether";
				case InputConstants.KEY_O -> "Show in Overworld";
				case InputConstants.KEY_T -> "Track";
				default -> null;
			};
			if (item != null) {
				this.menuAction(item, open);
				return true;
			}
		}
		if (this.pickerOpen && key == InputConstants.KEY_ESCAPE) {
			this.pickerOpen = false;
			return true;
		}
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
				// the best result of the group in focus: the map centres on it
				this.searchEnter();
				return true;
			}
			if (key == InputConstants.KEY_TAB) {
				this.searchTab();
				return true;
			}
			if (this.search.keyPressed(event)) {
				return true;
			}
		}
		if (this.biomeSearch.focused) {
			// the biome view's search: Enter centres on the nearest explored patch of the best match
			if (key == InputConstants.KEY_ESCAPE) {
				this.biomeSearch.set("");
				this.biomeSearch.focused = false;
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				String q = this.biomeSearch.text.strip().toLowerCase(Locale.ROOT);
				for (BiomeCount b : this.census) {
					if (!q.isEmpty() && dev.voidmc.atlas.core.MapArt.biomeLabel(b.id()).toLowerCase(Locale.ROOT).contains(q)) {
						this.map.centreOn(b.nearX(), b.nearZ());
						this.atlas.biomeHighlight = dev.voidmc.atlas.core.MapArt.family(b.id());
						break;
					}
				}
				this.biomeSearch.focused = false;
				return true;
			}
			if (this.biomeSearch.keyPressed(event)) {
				return true;
			}
		}
		if (key == InputConstants.KEY_ESCAPE) {
			if (this.menu != null) {
				this.menu = null;
				return true;
			}
			if (this.atlas.biomeHighlight != null && this.map.biomeLook() && this.map.selected == null) {
				this.atlas.biomeHighlight = null;
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
			case InputConstants.KEY_B -> this.map.setBiomeLook(!this.map.biomeLook());
			case InputConstants.KEY_T -> {
				if (this.map.selected == null) {
					return false;
				}
				Marker sm = this.map.marker(this.map.selected);
				if (sm != null) {
					this.track(sm);
				} else {
					this.track(this.map.selected);
				}
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
		if (this.biomeSearch.focused) {
			return this.biomeSearch.charTyped(event);
		}
		return false;
	}

	/** Whether typing goes to a field (so the map key does not close the map). */
	public boolean typing() {
		return (this.editing != null && this.name.focused) || (this.searching && this.search.focused) || this.biomeSearch.focused;
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
