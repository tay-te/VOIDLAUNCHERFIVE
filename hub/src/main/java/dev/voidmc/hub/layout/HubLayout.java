package dev.voidmc.hub.layout;

import dev.voidmc.hub.model.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one layout both halves of the screen read.
 *
 * <p>Everything is computed here, in Java, in physical pixels: the page is told where each slot
 * well is (as CSS px, i.e. {@code physical / u}) and draws it there, and Java hit-tests and draws
 * the item stacks against the very same integers. Nothing is laid out twice, so the HTML and the
 * hit-testing cannot disagree at any GUI scale or window size.
 *
 * <p>Positions are the Figma design's ({@code 3c0Wgc4ZKFSpm98fWJoARS}, frames 272:51688 and
 * 272:52131), in design units: one design unit is one CSS px in the page, and {@link #u} physical
 * pixels on screen. {@code u} follows Minecraft's GUI scale (scale 3 draws the design 1:1) and
 * shrinks to fit small windows.
 *
 * <p>Pure Java, no Minecraft types: unit-tested without the game.
 */
public final class HubLayout {

	public enum Kind {
		INVENTORY, CHEST, GEAR, CLASSES, MAP,
		/** VOID Hearth's Quests page (journal, notice board, reputation): the hub's window, no slots. */
		QUESTS,
		/** A Hearth event screen (talk, guard, market, bank, dice, lockpicking, pickpocketing): the window alone, no slots. */
		HEARTH
	}

	public enum Role {
		RESULT, CRAFT, HEAD, CHEST, LEGS, FEET, OFFHAND, STORAGE, HOTBAR, CONTAINER,
		/** VOID Mettle's worn gear (back, belt, quiver) and the pockets of each. */
		BACK, BELT, QUIVER, PACK, BELT_POCKET, QUIVER_POCKET;

		public String css() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * One visible slot well.
	 *
	 * @param slot the menu slot index (InventoryMenu / ChestMenu numbering)
	 * @param ordinal position within its role (hotbar 0-8, storage 0-26, craft 0-3, container n)
	 * @param cell the well, physical px
	 * @param icon where the item stack is drawn, physical px, a whole multiple of 16 so item
	 *     pixel art lands on whole device pixels
	 */
	public record Cell(int slot, Role role, int ordinal, Rect cell, Rect icon) {
	}

	// ---- design constants (design units) ---------------------------------------------------------
	public static final int WIN_W = 987;
	public static final int WIN_H = 606;
	/**
	 * The header was cut by this much (Figma 2e, B): page coordinates still count from the old top, 24 above the
	 * window's drawn edge, so every slot, card and page keeps its numbers.
	 */
	public static final int HEADER_CUT = 24;
	public static final int CELL = 46;
	public static final int PITCH = 52;
	static final int MARGIN = 24;
	static final int ISLAND_H = 42;
	static final int ISLAND_TOP = 18;
	static final int PAGES_W = 471;
	static final int PAGES_H = 42;
	static final int MARK_W = 121;
	static final int CLOSE_W = 108;
	static final int STRIP_W = 358;
	static final int STRIP_H = 72;
	/** Window offset above true centre in the design (164 top vs 186 bottom on a 980 frame). */
	static final int WIN_LIFT = 11;
	/** Space the shell needs around the window: 24 + 50 + gap above, 24 + 72 + gap below. */
	static final int NEED_W = WIN_W + 2 * MARGIN;
	static final int NEED_H = WIN_H + HEADER_CUT + 2 * (MARGIN + STRIP_H + 16);
	/** Smallest well, in physical px, that GUI scale alone may produce. */
	static final double MIN_CELL_PX = 20.0;
	/** Visible chest rows; longer containers scroll. */
	public static final int VISIBLE_ROWS = 3;
	/**
	 * The Map page's window: Figma 375:36239 ("Hub Map page with the real Atlas"), Atlas's shipped
	 * page at 1280 x 744, 36 below the page islands and 30 above the world strip.
	 */
	public static final int MAP_W = 1280;
	public static final int MAP_H = 744;
	static final int MAP_GAP_TOP = 36;
	static final int MAP_GAP_BOTTOM = 30;
	/** Mettle's pocket wells in the Gear page's worn card are smaller than the grid's. */
	public static final int POCKET = 40;

	/**
	 * Mettle's gear slot numbers in the InventoryMenu (mettle/docs/BURDEN.md §6), as the layout needs them; null
	 * without Mettle. The hub's {@code dev.voidmc.hub.mettle.MettleLink} holds the same numbers.
	 */
	public record GearSlots(int back, int belt, int quiver, int packFirst, int packSlots, int beltFirst, int beltSlots,
		int quiverFirst, int quiverSlots) {
		public static final GearSlots METTLE = new GearSlots(46, 47, 48, 49, 27, 76, 6, 82, 4);
	}

	public final Kind kind;
	public final int fbW;
	public final int fbH;
	public final int guiScale;
	/** Physical px per design unit (= Ultralight's device scale from 1 up; under 1 the view is drawn down instead, HubView.surfaceSize). */
	public final double u;
	public final Rect window;
	public final Rect mark;
	public final Rect pages;
	public final Rect close;
	public final Rect strip;
	/** The live player model's box (inventory only; null for chests). */
	public final Rect player;
	/** The Held card's item icon (what the hotbar's selected slot holds); null on pages without one. */
	public final Rect detailIcon;
	/** Container grid viewport (chest only; null otherwise). */
	public final Rect containerGrid;
	public final int containerRows;
	public final int scrollRow;
	public final int maxScroll;
	public final int iconPx;
	/**
	 * The Effects card's icon wells, top row first (inventory only; empty elsewhere). The card sits under Status:
	 * at 296 with Mettle (Status carries the load), at 200 without.
	 */
	public final List<Rect> effectIcons;
	/** Mettle's gear slots in this layout's menu, or null. */
	public final GearSlots gear;
	private final List<Cell> cells;

	private HubLayout(Kind kind, int fbW, int fbH, int guiScale, int containerRows, int scrollRow, GearSlots gear, AtlasScale scale) {
		this.kind = kind;
		this.fbW = Math.max(1, fbW);
		this.fbH = Math.max(1, fbH);
		this.guiScale = Math.max(1, guiScale);
		this.u = kind == Kind.CLASSES ? cardUnit(this.fbW, this.fbH, this.guiScale) : unit(this.fbW, this.fbH, this.guiScale);
		this.containerRows = containerRows;
		this.gear = gear;
		this.maxScroll = Math.max(0, containerRows - VISIBLE_ROWS);
		this.scrollRow = Math.max(0, Math.min(scrollRow, this.maxScroll));

		double u = this.u;
		// Islands.
		this.mark = Rect.design(0, 0, MARGIN, ISLAND_TOP, MARK_W, ISLAND_H, u);
		double pagesX = (this.fbW - PAGES_W * u) / 2.0;
		this.pages = Rect.design(pagesX, 0, 0, ISLAND_TOP, PAGES_W, PAGES_H, u);
		this.close = Rect.design(this.fbW - (MARGIN + CLOSE_W) * u, 0, 0, ISLAND_TOP, CLOSE_W, ISLAND_H, u);
		double stripX = (this.fbW - STRIP_W * u) / 2.0;
		this.strip = Rect.design(stripX, this.fbH - (MARGIN + STRIP_H) * u, 0, 0, STRIP_W, STRIP_H, u);

		if (kind == Kind.MAP) {
			this.window = mapWindow(this.fbW, this.fbH, this.guiScale, u, scale);
		} else {
			// Window: centred, lifted as in the design, never under the top islands.
			double winX = Math.round((this.fbW - WIN_W * u) / 2.0);
			double winY = (this.fbH - WIN_H * u) / 2.0 - WIN_LIFT * u;
			winY = Math.round(Math.max(winY, (ISLAND_TOP + PAGES_H + 8) * u));
			this.window = Rect.design(winX, winY, 0, 0, WIN_W, WIN_H, u);
		}

		this.iconPx = iconSize(CELL * u, 0.61);
		List<Cell> out = new ArrayList<>();
		if (kind == Kind.INVENTORY) {
			// Armour column, top to bottom: head, chest, legs, feet (menu slots 5-8).
			Role[] armour = {Role.HEAD, Role.CHEST, Role.LEGS, Role.FEET};
			for (int i = 0; i < 4; i++) {
				out.add(this.cell(5 + i, armour[i], i, 28, 100 + i * PITCH));
			}
			out.add(this.cell(45, Role.OFFHAND, 0, 250, 256));
			if (gear != null) {
				// Back, belt, quiver above the offhand, beside the figure (Figma 387:21432).
				out.add(this.cell(gear.back(), Role.BACK, 0, 250, 100));
				out.add(this.cell(gear.belt(), Role.BELT, 0, 250, 152));
				out.add(this.cell(gear.quiver(), Role.QUIVER, 0, 250, 204));
			}
			// 2x2 crafting grid (menu slots 1-4, row-major) and its result (slot 0).
			for (int i = 0; i < 4; i++) {
				out.add(this.cell(1 + i, Role.CRAFT, i, 318 + (i % 2) * PITCH, 168 + (i / 2) * PITCH));
			}
			out.add(this.cell(0, Role.RESULT, 0, 444, 194));
			this.addPlayerRows(out, 9, 352, 516);
			this.player = this.rect(82, 100, 160, 202);
			this.detailIcon = null;  // the column is Status, Effects and the purse; hovering shows the tooltip
			this.containerGrid = null;
		} else if (kind == Kind.GEAR) {
			// The Gear page (Figma 387:21106): pack pockets where a chest's rows go, the inventory below, and the worn
			// card on the right: back, belt and quiver with the belt's and quiver's pockets beside them.
			for (int i = 0; i < gear.packSlots(); i++) {
				out.add(this.cell(gear.packFirst() + i, Role.PACK, i, 28 + (i % 9) * PITCH, 134 + (i / 9) * PITCH));
			}
			this.addPlayerRows(out, 9, 334, 498);
			int[] worn = {gear.back(), gear.belt(), gear.quiver()};
			Role[] wornRoles = {Role.BACK, Role.BELT, Role.QUIVER};
			for (int r = 0; r < 3; r++) {
				out.add(this.cell(worn[r], wornRoles[r], 0, 528, 249 + r * 56));
			}
			this.pockets(out, gear.beltFirst(), gear.beltSlots(), Role.BELT_POCKET, 252 + 56);
			this.pockets(out, gear.quiverFirst(), gear.quiverSlots(), Role.QUIVER_POCKET, 252 + 112);
			this.player = null;
			this.detailIcon = null;  // hovering shows the tooltip; the column holds load, worn gear and the purse
			this.containerGrid = null;
		} else if (kind == Kind.CLASSES || kind == Kind.MAP || kind == Kind.QUESTS || kind == Kind.HEARTH) {
			// Mettle's Classes page and the Map page draw no slots: the menu stays open underneath, untouched.
			this.player = null;
			this.detailIcon = null;
			this.containerGrid = null;
		} else {
			for (int row = 0; row < VISIBLE_ROWS; row++) {
				int r = row + this.scrollRow;
				if (r >= containerRows) {
					break;
				}
				for (int c = 0; c < 9; c++) {
					out.add(this.cell(r * 9 + c, Role.CONTAINER, r * 9 + c, 28 + c * PITCH, 134 + row * PITCH));
				}
			}
			this.addPlayerRows(out, containerRows * 9, 334, 498);
			this.player = null;
			this.detailIcon = this.iconIn(this.rect(533, 226, 56, 56), 42);
			this.containerGrid = this.rect(28, 134, 462, VISIBLE_ROWS * PITCH - (PITCH - CELL));
		}
		this.cells = Collections.unmodifiableList(out);
		List<Rect> fx = new ArrayList<>();
		if (kind == Kind.INVENTORY) {
			int top = gear != null ? EFFECTS_TOP_METTLE : EFFECTS_TOP;
			int rows = gear != null ? 4 : 6;
			for (int i = 0; i < rows; i++) {
				fx.add(this.iconIn(this.rect(514 + 18 + 6, top + 38 + i * 38 + 4, 24, 24), 18));
			}
		}
		this.effectIcons = Collections.unmodifiableList(fx);
	}

	/** Where the Effects card starts (design px), with and without Mettle's load in Status above it. */
	public static final int EFFECTS_TOP_METTLE = 296;
	public static final int EFFECTS_TOP = 200;

	public static HubLayout inventory(int fbW, int fbH, int guiScale) {
		return inventory(fbW, fbH, guiScale, null);
	}

	/** The inventory page; with {@code gear}, Mettle's back, belt and quiver wells too. */
	public static HubLayout inventory(int fbW, int fbH, int guiScale, GearSlots gear) {
		return new HubLayout(Kind.INVENTORY, fbW, fbH, guiScale, 0, 0, gear, null);
	}

	/** Mettle's Gear page: pack pockets, the inventory, and the worn card with belt and quiver pockets. */
	public static HubLayout gear(int fbW, int fbH, int guiScale, GearSlots gear) {
		if (gear == null) {
			throw new IllegalArgumentException("the Gear page needs Mettle's gear slots");
		}
		return new HubLayout(Kind.GEAR, fbW, fbH, guiScale, 0, 0, gear, null);
	}

	/** Mettle's Classes page: no slots. */
	public static HubLayout classes(int fbW, int fbH, int guiScale) {
		return new HubLayout(Kind.CLASSES, fbW, fbH, guiScale, 0, 0, null, null);
	}

	/** VOID Hearth's Quests page: the window, no slots (the menu underneath is the player's, untouched). */
	public static HubLayout quests(int fbW, int fbH, int guiScale) {
		return new HubLayout(Kind.QUESTS, fbW, fbH, guiScale, 0, 0, null, null);
	}

	/** A Hearth event screen: the window, no slots. */
	public static HubLayout hearth(int fbW, int fbH, int guiScale) {
		return new HubLayout(Kind.HEARTH, fbW, fbH, guiScale, 0, 0, null, null);
	}

	public static HubLayout chest(int fbW, int fbH, int guiScale, int rows, int scrollRow) {
		if (rows < 1 || rows > 6) {
			throw new IllegalArgumentException("chest rows must be 1-6, not " + rows);
		}
		return new HubLayout(Kind.CHEST, fbW, fbH, guiScale, rows, scrollRow, null, null);
	}

	/** The Map page: the shell's islands as on every page, and the window Atlas's map fills. */
	public static HubLayout map(int fbW, int fbH, int guiScale) {
		return map(fbW, fbH, guiScale, AtlasScale.MIRROR);
	}

	/** The Map page, its window sized for the scale Atlas itself says it will draw at. */
	public static HubLayout map(int fbW, int fbH, int guiScale, AtlasScale scale) {
		return new HubLayout(Kind.MAP, fbW, fbH, guiScale, 0, 0, null, scale == null ? AtlasScale.MIRROR : scale);
	}

	/**
	 * The scale Atlas draws its 1280 x 744 page at in a rectangle of physical px
	 * ({@code MapWidget.unitFor}). {@link #MIRROR} is a copy of Atlas's rule, for when Atlas is not
	 * installed (the stand-in's window) and for the unit tests.
	 */
	@FunctionalInterface
	public interface AtlasScale {
		double unitFor(int width, int height);

		AtlasScale MIRROR = (w, h) -> {
			double want = Math.min(w / (double) MAP_W, h / (double) MAP_H);
			double best = ATLAS_UNITS[0];
			for (double a : ATLAS_UNITS) {
				if (a <= want + 1e-4) {
					best = a;
				}
			}
			return best;
		};
	}

	/**
	 * The scales VOID Atlas draws its page at (its {@code Ui.UNITS}: each has its own rasterisation of
	 * Outfit). Atlas picks the largest that fits the rectangle it is given. Only {@link AtlasScale#MIRROR}
	 * uses this copy; with Atlas installed the layout asks Atlas.
	 */
	public static final double[] ATLAS_UNITS = {0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0};

	/** Top of the Map page's band: the page islands' bottom edge plus the design's gap (design units). */
	static final int MAP_BAND_TOP = ISLAND_TOP + PAGES_H + MAP_GAP_TOP;

	/**
	 * The Atlas scale for the Map page: the largest of Atlas's scales at which its 1280 x 744 page
	 * fits the band between the page islands and the world strip (keeping the design's gaps) and the
	 * window's width, and is no larger than the shell's own unit (so the map never outgrows the
	 * islands around it).
	 */
	public static double atlasUnit(int fbW, int fbH, double u, AtlasScale scale) {
		double availW = fbW - 2 * MARGIN * u;
		double availH = fbH - (MAP_BAND_TOP + MARGIN + STRIP_H + MAP_GAP_BOTTOM) * u;
		int w = (int) Math.floor(Math.max(1, Math.min(availW, MAP_W * u)) + 1e-6);
		int h = (int) Math.floor(Math.max(1, Math.min(availH, MAP_H * u)) + 1e-6);
		return scale.unitFor(w, h);
	}

	public static double atlasUnit(int fbW, int fbH, double u) {
		return atlasUnit(fbW, fbH, u, AtlasScale.MIRROR);
	}

	/**
	 * The Map page's window, physical px: Atlas's page at exactly 1280 x 744 of its design units
	 * (Figma 375:36239), at {@link #atlasUnit}, centred in the band between the islands. Every edge
	 * lies on the GUI-pixel grid (the size rounded up, so Atlas, measuring the rectangle, picks that
	 * same unit), and the rectangle Atlas is given in GUI coordinates is exactly the window the page
	 * draws. A window too small for even Atlas's smallest scale gets the band, and Atlas fits into it.
	 * The Map window has no header cut: it is Atlas's page, drawn edge to edge.
	 */
	static Rect mapWindow(int fbW, int fbH, int guiScale, double u, AtlasScale scale) {
		double top = MAP_BAND_TOP * u;
		double bottom = fbH - (MARGIN + STRIP_H + MAP_GAP_BOTTOM) * u;
		double availW = fbW - 2 * MARGIN * u;
		double a = atlasUnit(fbW, fbH, u, scale);
		double w = MAP_W * a;
		double h = MAP_H * a;
		int gw;
		int gh;
		if (w <= availW && h <= bottom - top) {
			gw = (int) Math.ceil(w / guiScale - 1e-9);
			gh = (int) Math.ceil(h / guiScale - 1e-9);
		} else {
			gw = Math.max(1, (int) Math.floor(Math.min(w, availW) / guiScale));
			gh = Math.max(1, (int) Math.floor(Math.min(h, bottom - top) / guiScale));
		}
		int gx = (int) Math.round((fbW / (double) guiScale - gw) / 2.0);
		int gy = (int) Math.round(((top + bottom) / 2.0) / guiScale - gh / 2.0);
		return new Rect(gx * guiScale, gy * guiScale, gw * guiScale, gh * guiScale);
	}

	/**
	 * Physical px per design unit: GUI scale 3 is the design at 1:1, other scales follow it, and
	 * the shell always fits the window.
	 */
	public static double unit(int fbW, int fbH, int guiScale) {
		// Never smaller than a well that holds a 16 px item with a pixel to spare (GUI scale 1).
		double byScale = Math.max(guiScale / 3.0, MIN_CELL_PX / CELL);
		double byWidth = fbW / (double) NEED_W;
		double byHeight = fbH / (double) NEED_H;
		return Math.max(0.25, Math.min(byScale, Math.min(byWidth, byHeight)));
	}

	/**
	 * Smallest unit the Classes page (Mettle's cards) is drawn at when the framebuffer has room: 11 px rules text is
	 * then at least 7 device px. GUI scale 1 alone would give 0.43 (rules text under 5 px).
	 */
	public static final double CARD_MIN_UNIT = 0.75;

	/**
	 * The Classes page's unit: the shell's, raised to {@link #CARD_MIN_UNIT} where the framebuffer fits it, then
	 * snapped down to a quarter so the hand cards' 4 px texel grid (README "Scales and crispness") lands on whole
	 * device pixels: a texel is {@code 4u} px, the card art, the native burning edges and the page agree on the same
	 * grid. Below half a unit (a very small window) readability wins and the unit is not snapped.
	 */
	public static double cardUnit(int fbW, int fbH, int guiScale) {
		double fit = Math.min(fbW / (double) NEED_W, fbH / (double) NEED_H);
		double u = Math.max(unit(fbW, fbH, guiScale), Math.min(CARD_MIN_UNIT, fit));
		double snapped = Math.floor(u * 4 + 1e-9) / 4.0;
		return snapped >= 0.5 ? snapped : u;
	}

	/**
	 * Item icon size for a cell: a whole multiple of 16 px (item textures are 16 texels), the
	 * nearest to {@code fraction} of the cell, never below 16.
	 */
	public static int iconSize(double cellPx, double fraction) {
		return fitIcon(cellPx * fraction, cellPx);
	}

	/**
	 * The whole multiple of 16 px nearest {@code targetPx} that still leaves a pixel of the well
	 * around it; for a well too small for even 16 px, the largest size that fits.
	 */
	public static int fitIcon(double targetPx, double wellPx) {
		int k = Math.max(1, (int) Math.round(targetPx / 16.0));
		while (k > 1 && 16 * k > wellPx - 2) {
			k--;
		}
		if (16 * k <= wellPx - 2) {
			return 16 * k;
		}
		return Math.max(8, (int) Math.floor(wellPx - 2));
	}

	private void addPlayerRows(List<Cell> out, int firstStorageSlot, int storageTop, int hotbarTop) {
		for (int i = 0; i < 27; i++) {
			int r = i / 9;
			int c = i % 9;
			out.add(this.cell(firstStorageSlot + i, Role.STORAGE, i, 28 + c * PITCH, storageTop + r * PITCH));
		}
		for (int c = 0; c < 9; c++) {
			out.add(this.cell(firstStorageSlot + 27 + c, Role.HOTBAR, c, 28 + c * PITCH, hotbarTop));
		}
	}

	/** A row of pocket wells, right-aligned in the worn card (card x 514, w 445, 14 px in). */
	private void pockets(List<Cell> out, int first, int n, Role role, double dy) {
		for (int k = 0; k < n; k++) {
			double dx = 514 + 445 - 14 - (n - k) * (POCKET + 4) + 4;
			Rect cell = this.rect(dx, dy, POCKET, POCKET);
			out.add(new Cell(first + k, role, k, cell, this.centred(cell, iconSize(POCKET * this.u, 0.66))));
		}
	}

	private Rect rect(double dx, double dy, double dw, double dh) {
		return Rect.design(this.window.x(), this.window.y(), dx, dy - HEADER_CUT, dw, dh, this.u);
	}

	private Cell cell(int slot, Role role, int ordinal, double dx, double dy) {
		Rect cell = this.rect(dx, dy, CELL, CELL);
		return new Cell(slot, role, ordinal, cell, this.centred(cell, this.iconPx));
	}

	private Rect iconIn(Rect well, double designIcon) {
		return this.centred(well, fitIcon(designIcon * this.u, well.w()));
	}

	private Rect centred(Rect box, int size) {
		int x = box.x() + (box.w() - size) / 2;
		int y = box.y() + (box.h() - size) / 2;
		return new Rect(x, y, size, size);
	}

	// ---- queries ---------------------------------------------------------------------------------

	public List<Cell> cells() {
		return this.cells;
	}

	/** The well under a physical-pixel point, or null (gaps between wells are not slots). */
	public Cell cellAt(double px, double py) {
		for (Cell c : this.cells) {
			if (c.cell().contains(px, py)) {
				return c;
			}
		}
		return null;
	}

	public Cell cellForSlot(int slot) {
		for (Cell c : this.cells) {
			if (c.slot() == slot) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Whether a point is on any VOID surface (window or island). Off every surface is "outside":
	 * a click there with a carried stack drops it, exactly like clicking outside vanilla's panel.
	 */
	public boolean onSurface(double px, double py) {
		return this.window.contains(px, py) || this.mark.contains(px, py) || this.pages.contains(px, py)
			|| this.close.contains(px, py) || this.strip.contains(px, py);
	}

	public double physToCss(double phys) {
		return phys / this.u;
	}

	/** Physical px to Minecraft GUI px. */
	public double physToGui(double phys) {
		return phys / this.guiScale;
	}

	public double guiToPhys(double gui) {
		return gui * this.guiScale;
	}

	// ---- the page's copy -------------------------------------------------------------------------

	private String json;

	/** The layout message the page receives. Every rect is CSS px (physical / u). Cached. */
	public String toJson() {
		if (this.json == null) {
			this.json = this.encode();
		}
		return this.json;
	}

	private String encode() {
		Json.Writer w = Json.object();
		w.put("type", "layout");
		w.put("kind", this.kind.name().toLowerCase(java.util.Locale.ROOT));
		w.put("u", this.u);
		w.put("vw", this.physToCss(this.fbW));
		w.put("vh", this.physToCss(this.fbH));
		w.raw("win", this.cssRect(this.window));
		w.put("rows", this.containerRows);
		w.put("scroll", this.scrollRow);
		w.put("maxScroll", this.maxScroll);
		StringBuilder slots = new StringBuilder("[");
		for (int i = 0; i < this.cells.size(); i++) {
			Cell c = this.cells.get(i);
			if (i > 0) {
				slots.append(',');
			}
			Json.Writer s = Json.object();
			s.put("i", c.slot());
			s.put("r", c.role().css());
			s.put("n", c.ordinal());
			s.put("x", this.physToCss(c.cell().x()));
			s.put("y", this.physToCss(c.cell().y()));
			s.put("w", this.physToCss(c.cell().w()));
			s.put("h", this.physToCss(c.cell().h()));
			slots.append(s.end());
		}
		slots.append(']');
		w.raw("slots", slots.toString());
		return w.end();
	}

	private String cssRect(Rect r) {
		Json.Writer w = Json.object();
		w.put("x", this.physToCss(r.x()));
		w.put("y", this.physToCss(r.y()));
		w.put("w", this.physToCss(r.w()));
		w.put("h", this.physToCss(r.h()));
		return w.end();
	}
}
