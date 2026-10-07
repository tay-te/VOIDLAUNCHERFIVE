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
		INVENTORY, CHEST, MAP
	}

	public enum Role {
		RESULT, CRAFT, HEAD, CHEST, LEGS, FEET, OFFHAND, STORAGE, HOTBAR, CONTAINER;

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
	public static final int WIN_H = 630;
	public static final int CELL = 46;
	public static final int PITCH = 52;
	static final int MARGIN = 24;
	static final int ISLAND_H = 48;
	static final int PAGES_W = 471;
	static final int PAGES_H = 50;
	static final int MARK_W = 121;
	static final int CLOSE_W = 108;
	static final int STRIP_W = 358;
	static final int STRIP_H = 72;
	/** Window offset above true centre in the design (164 top vs 186 bottom on a 980 frame). */
	static final int WIN_LIFT = 11;
	/** Space the shell needs around the window: 24 + 50 + gap above, 24 + 72 + gap below. */
	static final int NEED_W = WIN_W + 2 * MARGIN;
	static final int NEED_H = WIN_H + 2 * (MARGIN + STRIP_H + 16);
	/** Smallest well, in physical px, that GUI scale alone may produce. */
	static final double MIN_CELL_PX = 20.0;
	/** Visible chest rows; longer containers scroll. */
	public static final int VISIBLE_ROWS = 3;
	/**
	 * The Map page's window: the host rectangle of Figma 333:32995 ("Hub · Map (R-Shift) with the
	 * new map"), 1280 x 744, 36 below the page islands and 30 above the world strip. Atlas's page
	 * fills it in its own design units.
	 */
	public static final int MAP_W = 1280;
	public static final int MAP_H = 744;
	static final int MAP_GAP_TOP = 36;
	static final int MAP_GAP_BOTTOM = 30;

	public final Kind kind;
	public final int fbW;
	public final int fbH;
	public final int guiScale;
	/** Physical px per design unit (= Ultralight's device scale). */
	public final double u;
	public final Rect window;
	public final Rect mark;
	public final Rect pages;
	public final Rect close;
	public final Rect strip;
	/** The live player model's box (inventory only; null for chests). */
	public final Rect player;
	/** The hovered-item card's item well icon. */
	public final Rect detailIcon;
	/** Container grid viewport (chest only; null otherwise). */
	public final Rect containerGrid;
	public final int containerRows;
	public final int scrollRow;
	public final int maxScroll;
	public final int iconPx;
	private final List<Cell> cells;

	private HubLayout(Kind kind, int fbW, int fbH, int guiScale, int containerRows, int scrollRow) {
		this.kind = kind;
		this.fbW = Math.max(1, fbW);
		this.fbH = Math.max(1, fbH);
		this.guiScale = Math.max(1, guiScale);
		this.u = unit(this.fbW, this.fbH, this.guiScale);
		this.containerRows = containerRows;
		this.maxScroll = Math.max(0, containerRows - VISIBLE_ROWS);
		this.scrollRow = Math.max(0, Math.min(scrollRow, this.maxScroll));

		double u = this.u;
		// Islands.
		this.mark = Rect.design(0, 0, MARGIN, MARGIN, MARK_W, ISLAND_H, u);
		double pagesX = (this.fbW - PAGES_W * u) / 2.0;
		this.pages = Rect.design(pagesX, 0, 0, MARGIN, PAGES_W, PAGES_H, u);
		this.close = Rect.design(this.fbW - (MARGIN + CLOSE_W) * u, 0, 0, MARGIN, CLOSE_W, ISLAND_H, u);
		double stripX = (this.fbW - STRIP_W * u) / 2.0;
		this.strip = Rect.design(stripX, this.fbH - (MARGIN + STRIP_H) * u, 0, 0, STRIP_W, STRIP_H, u);

		if (kind == Kind.MAP) {
			this.window = mapWindow(this.fbW, this.fbH, this.guiScale, u);
		} else {
			// Window: centred, lifted as in the design, never under the top islands.
			double winX = Math.round((this.fbW - WIN_W * u) / 2.0);
			double winY = (this.fbH - WIN_H * u) / 2.0 - WIN_LIFT * u;
			winY = Math.round(Math.max(winY, (MARGIN + PAGES_H + 8) * u));
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
			// 2x2 crafting grid (menu slots 1-4, row-major) and its result (slot 0).
			for (int i = 0; i < 4; i++) {
				out.add(this.cell(1 + i, Role.CRAFT, i, 318 + (i % 2) * PITCH, 118 + (i / 2) * PITCH));
			}
			out.add(this.cell(0, Role.RESULT, 0, 444, 144));
			this.addPlayerRows(out, 9, 352, 516);
			this.player = this.rect(82, 100, 160, 202);
			this.detailIcon = this.iconIn(this.rect(533, 117, 72, 72), 64);
			this.containerGrid = null;
		} else if (kind == Kind.MAP) {
			this.player = null;
			this.detailIcon = new Rect(0, 0, 0, 0);
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
	}

	public static HubLayout inventory(int fbW, int fbH, int guiScale) {
		return new HubLayout(Kind.INVENTORY, fbW, fbH, guiScale, 0, 0);
	}

	/** The Map page: the shell's islands as on every page, and the window Atlas's map fills. */
	public static HubLayout map(int fbW, int fbH, int guiScale) {
		return new HubLayout(Kind.MAP, fbW, fbH, guiScale, 0, 0);
	}

	/**
	 * The Map page's window, physical px: the design's 1280 x 744 at the shell's unit, shrunk to the
	 * band between the page islands and the world strip (keeping the design's gaps) and to the
	 * window's width, centred in that band. Every edge lies on the GUI-pixel grid, so the rectangle
	 * Atlas is given in GUI coordinates is exactly the one the page draws the window at.
	 */
	static Rect mapWindow(int fbW, int fbH, int guiScale, double u) {
		double top = (MARGIN + PAGES_H + MAP_GAP_TOP) * u;
		double bottom = fbH - (MARGIN + STRIP_H + MAP_GAP_BOTTOM) * u;
		double w = Math.min(MAP_W * u, fbW - 2 * MARGIN * u);
		double h = Math.min(MAP_H * u, bottom - top);
		int gw = Math.max(1, (int) Math.floor(w / guiScale));
		int gh = Math.max(1, (int) Math.floor(h / guiScale));
		int gx = (int) Math.round((fbW / (double) guiScale - gw) / 2.0);
		int gy = (int) Math.round(((top + bottom) / 2.0) / guiScale - gh / 2.0);
		return new Rect(gx * guiScale, gy * guiScale, gw * guiScale, gh * guiScale);
	}

	public static HubLayout chest(int fbW, int fbH, int guiScale, int rows, int scrollRow) {
		if (rows < 1 || rows > 6) {
			throw new IllegalArgumentException("chest rows must be 1-6, not " + rows);
		}
		return new HubLayout(Kind.CHEST, fbW, fbH, guiScale, rows, scrollRow);
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

	private Rect rect(double dx, double dy, double dw, double dh) {
		return Rect.design(this.window.x(), this.window.y(), dx, dy, dw, dh, this.u);
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
