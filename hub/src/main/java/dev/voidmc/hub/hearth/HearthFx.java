package dev.voidmc.hub.hearth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.hub.model.Bridge;
import dev.voidmc.hub.screen.NativeFx;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * The moving parts of Hearth's minigames, drawn natively over the still page at full frame rate (docs/HUB_UI.md: the
 * page draws the chrome and reports these rects once; nothing here animates in CSS):
 *
 * <ul>
 *   <li>{@code dice}: the six bone dice on the board, tumbling for a moment after every roll ({@code rollSerial}),
 *       the picked ones white, the scoring ones bright; {@link #dieAt} hit-tests them for the screen's own clicks.</li>
 *   <li>{@code lock} and {@code strain}: the lock face, the turning plug, the pick at the hand's angle (shaking and
 *       reddening under strain), the pins set; Hearth's own LockpickScreen keeps the game (its model, timing and packets)
 *       and the hub only draws it.</li>
 *   <li>{@code awareness}, {@code hand}, {@code liftfill}: the mark's awareness running forward between the server's
 *       updates, the hand ring closing while a lift is under way, and the lift filling its pocket well.</li>
 * </ul>
 *
 * <p>Flat, rounded, pixel-stepped shapes in the Hub's colours; colour only for meaning. Render thread.
 */
public final class HearthFx extends NativeFx {

	static final int INK = 0xEDEEEF;
	static final int INK2 = 0x9A9DA1;
	static final int INK3 = 0x626569;
	static final int WELL = 0x131315;
	static final int PANEL = 0x191A1C;
	static final int RAISED = 0x26272B;
	static final int GOOD = 0x7FAE7A;
	static final int WARY = 0xD6A85A;
	static final int BAD = 0xC2645A;
	static final int VIOLET = 0x9F8BFF;
	static final int BRASS = 0xC9A35B;

	/** The pointer, CSS px (the screen sets it every frame). */
	private double mouseX = -1;
	private double mouseY = -1;
	/** The dice drawn last frame: index and box (CSS px), for the screen's clicks. */
	private final List<double[]> dieBoxes = new ArrayList<>();
	private int lastSerial = Integer.MIN_VALUE;
	private long rolledAt;
	private static final double TUMBLE_MS = 650;

	public void pointer(double cssX, double cssY) {
		this.mouseX = cssX;
		this.mouseY = cssY;
	}

	@Override
	public void clear() {
		super.clear();
		this.dieBoxes.clear();
	}

	/** The die under a page point (CSS px) that may be picked now, or -1. */
	public int dieAt(double x, double y) {
		JsonObject dice = HearthSync.section("dice");
		if (dice == null || !"player_rolled".equals(str(dice, "phase"))) {
			return -1;
		}
		for (double[] b : this.dieBoxes) {
			if (x >= b[1] && x < b[1] + b[3] && y >= b[2] && y < b[2] + b[4]) {
				return (int) b[0];
			}
		}
		return -1;
	}

	/** The lock rect's pick centre (CSS px), or null when the page has not reported it. */
	public double[] lockCentre() {
		for (Bridge.FxItem it : this.items) {
			if ("lock".equals(it.kind())) {
				return new double[] {it.x() + it.w() / 2, it.y() + it.q()};
			}
		}
		return null;
	}

	/** Whether a page point is inside the lock rect. */
	public boolean onLock(double x, double y) {
		for (Bridge.FxItem it : this.items) {
			if ("lock".equals(it.kind()) && x >= it.x() && x < it.x() + it.w() && y >= it.y() && y < it.y() + it.h()) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void layer(GuiGraphicsExtractor g, Bridge.FxItem it, double age, double clock, double u) {
		switch (it.kind()) {
			case "dice" -> this.dice(g, it, clock, u);
			case "lock" -> this.lock(g, it, u);
			case "strain" -> this.strain(g, it, u);
			case "awareness" -> this.awareness(g, it, clock, u);
			case "hand" -> this.hand(g, it, u);
			case "liftfill" -> this.liftFill(g, it, u);
			default -> {
			}
		}
	}

	// ---- drawing helpers (physical px) ------------------------------------------------------------------------------

	static int px(double css, double u) {
		return (int) Math.round(css * u);
	}

	/** A flat rounded rect, the corners stepped on whole physical px. */
	static void round(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int argb) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
		if (r == 0) {
			g.fill(x0, y0, x1, y1, argb);
			return;
		}
		g.fill(x0, y0 + r, x1, y1 - r, argb);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, argb);
			g.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, argb);
		}
	}

	/** A 1 px rounded outline. */
	static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int w, int argb) {
		round(g, x0, y0, x1, y0 + w + r, r, argb);
		// sides and bottom as plain bars (corners from the top piece, mirrored)
		g.fill(x0, y0 + r, x0 + w, y1 - r, argb);
		g.fill(x1 - w, y0 + r, x1, y1 - r, argb);
		round(g, x0, y1 - w - r, x1, y1, r, argb);
	}

	/** A disc of {@code cell}-px pixels (pixel art at the page's grid). */
	static void disc(GuiGraphicsExtractor g, double cx, double cy, double radius, int cell, int argb) {
		int n = (int) Math.ceil(radius / cell);
		for (int j = -n; j < n; j++) {
			double yc = (j + 0.5) * cell;
			double half = Math.sqrt(Math.max(0, radius * radius - yc * yc));
			int k = (int) Math.floor(half / cell + 0.5);
			if (k <= 0) {
				continue;
			}
			int y = (int) Math.round(cy + j * cell);
			g.fill((int) Math.round(cx - k * cell), y, (int) Math.round(cx + k * cell), y + cell, argb);
		}
	}

	/** A thick line of square steps from a to b. */
	static void line(GuiGraphicsExtractor g, double ax, double ay, double bx, double by, int w, int argb) {
		double len = Math.hypot(bx - ax, by - ay);
		int steps = Math.max(1, (int) Math.ceil(len / Math.max(1, w * 0.5)));
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			int x = (int) Math.round(ax + (bx - ax) * t - w / 2.0);
			int y = (int) Math.round(ay + (by - ay) * t - w / 2.0);
			g.fill(x, y, x + w, y + w, argb);
		}
	}

	static int mix(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int gg = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bb = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
		return r << 16 | gg << 8 | bb;
	}

	static int rgba(int rgb, double a) {
		return argb(rgb, a);
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static double num(JsonObject o, String k, double d) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : d;
	}

	private static boolean bool(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsBoolean();
	}

	// ---- dice --------------------------------------------------------------------------------------------------------

	private void dice(GuiGraphicsExtractor g, Bridge.FxItem it, double clock, double u) {
		this.dieBoxes.clear();
		JsonObject dice = HearthSync.section("dice");
		if (dice == null || !(dice.get("dice") instanceof JsonArray list)) {
			return;
		}
		int serial = (int) num(dice, "rollSerial", 0);
		long now = System.nanoTime();
		if (serial != this.lastSerial) {
			// A roll the page did not see opened with: tumble only rolls that happen while the table is showing.
			this.rolledAt = this.lastSerial == Integer.MIN_VALUE ? 0 : now;
			this.lastSerial = serial;
		}
		double since = (now - this.rolledAt) / 1e6;
		boolean picking = "player_rolled".equals(str(dice, "phase"));
		double size = 56;
		// Ten resting places on the felt (two staggered rows), shuffled by the roll so every throw lands differently.
		double[][] spots = new double[10][];
		for (int i = 0; i < 10; i++) {
			int col = i % 5;
			int row = i / 5;
			spots[i] = new double[] {44 + col * 96 + (row == 1 ? 48 : 0), 56 + row * 110};
		}
		Random seat = new Random(serial * 7919L + 17);
		for (int i = spots.length - 1; i > 0; i--) {
			int j = seat.nextInt(i + 1);
			double[] t = spots[i];
			spots[i] = spots[j];
			spots[j] = t;
		}
		int place = 0;
		for (JsonElement e : list) {
			JsonObject d = e.getAsJsonObject();
			int index = (int) num(d, "index", place);
			if (bool(d, "kept")) {
				continue;  // set aside: the page's tray shows it, still
			}
			Random jitter = new Random(serial * 31L + index * 131L + 7);
			double[] spot = spots[place++ % spots.length];
			double x = it.x() + spot[0] + jitter.nextInt(21) - 10;
			double y = it.y() + spot[1] + jitter.nextInt(21) - 10;
			int face = (int) num(d, "face", 1);
			double rot = 0;
			boolean tumbling = bool(d, "rolled") && since < TUMBLE_MS;
			if (tumbling) {
				double t = since / TUMBLE_MS;
				double ease = 1 - (1 - t) * (1 - t);
				double fromX = it.x() + 8 + jitter.nextInt(40);
				double fromY = it.y() + it.h() * (0.2 + 0.6 * jitter.nextDouble());
				x = fromX + (x - fromX) * ease;
				y = fromY + (y - fromY) * ease - Math.abs(Math.sin(t * Math.PI * 3)) * 18 * (1 - t);
				rot = (1 - ease) * (jitter.nextBoolean() ? 1 : -1) * 540;
				face = 1 + (int) ((clock / 70 + index * 3) % 6);
			}
			this.dieBoxes.add(new double[] {index, x, y, size, size});
			boolean hover = picking && !tumbling && this.mouseX >= x && this.mouseX < x + size && this.mouseY >= y && this.mouseY < y + size;
			this.die(g, x, y, size, face, rot, bool(d, "selected"), picking && bool(d, "scoring"), hover, u);
		}
	}

	private void die(GuiGraphicsExtractor g, double x, double y, double size, int face, double rot, boolean selected, boolean scoring, boolean hover,
		double u) {
		int x0 = px(x, u);
		int y0 = px(y, u);
		int s = px(size, u);
		int r = px(10, u);
		g.pose().pushMatrix();
		if (rot != 0) {
			g.pose().translate(x0 + s / 2.0F, y0 + s / 2.0F);
			g.pose().rotate((float) Math.toRadians(rot));
			g.pose().translate(-(x0 + s / 2.0F), -(y0 + s / 2.0F));
		}
		int body = selected ? INK : hover ? 0x34353A : 0x2B2C30;
		int edge = selected ? VIOLET : hover ? 0x5A5C61 : 0x3A3B40;
		round(g, x0 - px(1, u), y0 - px(1, u), x0 + s + px(1, u), y0 + s + px(1, u), r + px(1, u), argb(edge, selected ? 0.95 : 1));
		round(g, x0, y0, x0 + s, y0 + s, r, argb(body, 1));
		int pip = selected ? 0x111214 : scoring ? INK : INK2;
		int p = Math.max(2, px(9, u));
		double[][] at = pips(face);
		for (double[] c : at) {
			int cx = x0 + (int) Math.round(c[0] * s);
			int cy = y0 + (int) Math.round(c[1] * s);
			round(g, cx - p / 2, cy - p / 2, cx - p / 2 + p, cy - p / 2 + p, Math.max(1, p / 3), argb(pip, 1));
		}
		g.pose().popMatrix();
	}

	static double[][] pips(int face) {
		double a = 0.27;
		double m = 0.5;
		double b = 0.73;
		return switch (face) {
			case 1 -> new double[][] {{m, m}};
			case 2 -> new double[][] {{a, a}, {b, b}};
			case 3 -> new double[][] {{a, a}, {m, m}, {b, b}};
			case 4 -> new double[][] {{a, a}, {b, a}, {a, b}, {b, b}};
			case 5 -> new double[][] {{a, a}, {b, a}, {m, m}, {a, b}, {b, b}};
			default -> new double[][] {{a, a}, {b, a}, {a, m}, {b, m}, {a, b}, {b, b}};
		};
	}

	// ---- lockpicking -------------------------------------------------------------------------------------------------

	private Object lockModel() {
		Screen ns = HearthLink.nativeScreen();
		HearthLink.LockParts parts = HearthLink.lockParts(ns);
		if (ns == null || parts == null) {
			return null;
		}
		try {
			return parts.model().get(ns);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	private static double call(Method m, Object o) {
		try {
			Object v = m.invoke(o);
			return v instanceof Boolean b ? (b ? 1 : 0) : ((Number) v).doubleValue();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	private void lock(GuiGraphicsExtractor g, Bridge.FxItem it, double u) {
		Object model = this.lockModel();
		HearthLink.LockParts parts = HearthLink.lockParts(HearthLink.nativeScreen());
		double cx = (it.x() + it.w() / 2) * u;
		double cy = (it.y() + it.q()) * u;
		double ring = it.p() * u;
		int cell = Math.max(1, px(2, u));
		// The pick's travel: a dotted half ring over the top, quiet.
		for (int deg = 0; deg <= 180; deg += 6) {
			double t = Math.toRadians(deg);
			int sx = (int) Math.round(cx - Math.cos(t) * ring);
			int sy = (int) Math.round(cy - Math.sin(t) * ring);
			g.fill(sx - cell / 2, sy - cell / 2, sx - cell / 2 + cell, sy - cell / 2 + cell, argb(INK, 0.10));
		}
		// The face: a dark disc with a hairline rim; four rivets.
		disc(g, cx, cy, ring * 0.80 + cell, cell, argb(0x2A2B2F, 1));
		disc(g, cx, cy, ring * 0.80, cell, argb(PANEL, 1));
		double rv = ring * 0.58;
		for (int[] d : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			int rx = (int) Math.round(cx + d[0] * rv * 0.72);
			int ry = (int) Math.round(cy + d[1] * rv * 0.72);
			g.fill(rx - cell, ry - cell, rx + cell, ry + cell, argb(INK, 0.16));
		}
		double pick = 90;
		double rotation = 0;
		double strain = 0;
		boolean bound = false;
		int set = 0;
		int pins = 0;
		float flash = 0;
		int flashColour = 0;
		if (model != null && parts != null) {
			pick = call(parts.pick(), model);
			rotation = call(parts.rotation(), model);
			strain = call(parts.strain(), model);
			bound = call(parts.bound(), model) > 0;
			set = (int) call(parts.setPins(), model);
			try {
				pins = (int) call(parts.pins(), parts.spec().invoke(model));
				flash = parts.flash().getFloat(HearthLink.nativeScreen());
				flashColour = parts.flashColor().getInt(HearthLink.nativeScreen());
			} catch (ReflectiveOperationException | RuntimeException e) {
				// drawn without them
			}
		}
		// The plug, turning as the pins set (a quarter turn opens it), with its keyhole.
		double plug = ring * 0.36;
		disc(g, cx, cy, plug + cell, cell, argb(0x34353A, 1));
		disc(g, cx, cy, plug, cell, argb(RAISED, 1));
		g.pose().pushMatrix();
		g.pose().translate((float) cx, (float) cy);
		g.pose().rotate((float) Math.toRadians(rotation * 90));
		int kw = px(20, u);
		int kh = px(30, u);
		round(g, -kw / 2, -kh / 2 - px(6, u), kw / 2, kh / 2 - px(6, u), kw / 2, argb(0x000000, 0.55));
		g.pose().popMatrix();
		// The pick: from outside the ring to the keyhole at the hand's angle; shaking and reddening under strain.
		double t = Math.toRadians(pick);
		double jx = 0;
		double jy = 0;
		if (bound) {
			Random shake = new Random(System.nanoTime() / 16_000_000L);
			double amp = (0.6 + strain * 2.2) * cell;
			jx = (shake.nextDouble() - 0.5) * amp;
			jy = (shake.nextDouble() - 0.5) * amp;
		}
		double ox = cx - Math.cos(t) * ring * 1.18 + jx;
		double oy = cy - Math.sin(t) * ring * 1.18 + jy;
		double mx = cx - Math.cos(t) * ring * 0.86 + jx;
		double my = cy - Math.sin(t) * ring * 0.86 + jy;
		double ix = cx - Math.cos(t) * plug * 0.2 + jx;
		double iy = cy - Math.sin(t) * plug * 0.2 + jy;
		int steel = mix(INK, BAD, strain);
		line(g, ox, oy, mx, my, cell * 3, argb(0x5C4630, 1));
		line(g, mx, my, ix, iy, cell, argb(steel, 1));
		g.fill((int) ix - cell, (int) iy - cell * 2, (int) ix + cell, (int) iy, argb(steel, 1));
		// The pins: a row of cells under the face, set ones brass, the one being worked outlined.
		if (pins > 0) {
			int pw = px(10, u);
			int ph = px(16, u);
			int gap = px(6, u);
			int total = pins * pw + (pins - 1) * gap;
			int x0 = (int) Math.round(cx - total / 2.0);
			int y0 = (int) Math.round(cy + ring * 0.80 + px(26, u));
			for (int i = 0; i < pins; i++) {
				int x = x0 + i * (pw + gap);
				boolean done = i < set;
				round(g, x, y0, x + pw, y0 + ph, px(3, u), argb(done ? BRASS : INK, done ? 0.9 : 0.10));
				if (!done && i == set) {
					outline(g, x - px(2, u), y0 - px(2, u), x + pw + px(2, u), y0 + ph + px(2, u), px(4, u), Math.max(1, px(1, u)), argb(WARY, 0.8));
				}
			}
		}
		if (flash > 0) {
			round(g, px(it.x(), u), px(it.y(), u), px(it.x() + it.w(), u), px(it.y() + it.h(), u), px(16, u), argb(flashColour, flash * 0.35));
		}
	}

	private void strain(GuiGraphicsExtractor g, Bridge.FxItem it, double u) {
		Object model = this.lockModel();
		HearthLink.LockParts parts = HearthLink.lockParts(HearthLink.nativeScreen());
		double strain = model == null || parts == null ? 0 : call(parts.strain(), model);
		int x0 = px(it.x(), u);
		int y0 = px(it.y(), u);
		int x1 = px(it.x() + it.w(), u);
		int y1 = px(it.y() + it.h(), u);
		round(g, x0, y0, x1, y1, px(8, u), argb(INK, 0.06));
		int w = (int) Math.round((x1 - x0) * Math.max(0, Math.min(1, strain)));
		if (w > 0) {
			round(g, x0, y0, x0 + w, y1, px(8, u), argb(mix(INK2, BAD, strain), 0.9));
		}
	}

	// ---- pickpocketing -----------------------------------------------------------------------------------------------

	/** The mark's awareness and the lift's progress, from Hearth's own screen (it runs them forward), else the state. */
	private double[] pocket() {
		Screen ns = HearthLink.nativeScreen();
		double aw = 0;
		double lift = 0;
		JsonObject p = HearthSync.section("pickpocket");
		if (p != null) {
			aw = num(p, "awareness", 0);
			lift = num(p, "liftProgress", 0);
		}
		if (ns != null && ns.getClass().getSimpleName().equals("PickpocketScreen")) {
			try {
				aw = ((Number) HearthLink.method(ns.getClass(), "awareness").invoke(ns)).doubleValue();
				lift = ((Number) HearthLink.method(ns.getClass(), "liftProgress").invoke(ns)).doubleValue();
			} catch (ReflectiveOperationException | RuntimeException e) {
				// the state's values
			}
		}
		return new double[] {aw, lift, p == null ? -1 : num(p, "lifting", -1)};
	}

	private void awareness(GuiGraphicsExtractor g, Bridge.FxItem it, double clock, double u) {
		double aw = Math.max(0, Math.min(1, this.pocket()[0]));
		int x0 = px(it.x(), u);
		int y0 = px(it.y(), u);
		int x1 = px(it.x() + it.w(), u);
		int y1 = px(it.y() + it.h(), u);
		int r = px(12, u);
		round(g, x0, y0, x1, y1, r, argb(WELL, 1));
		int w = (int) Math.round((x1 - x0) * aw);
		if (w > 0) {
			int c = aw < 0.5 ? mix(GOOD, WARY, aw * 2) : mix(WARY, BAD, (aw - 0.5) * 2);
			round(g, x0, y0, x0 + Math.max(w, Math.min(2 * r, x1 - x0)), y1, r, argb(c, 0.9));
		}
		if (aw > 0.75 && ((long) (clock / 150)) % 2 == 0) {
			outline(g, x0 - px(2, u), y0 - px(2, u), x1 + px(2, u), y1 + px(2, u), r + px(2, u), Math.max(1, px(1, u)), argb(BAD, 0.8));
		}
	}

	private void hand(GuiGraphicsExtractor g, Bridge.FxItem it, double u) {
		double[] p = this.pocket();
		boolean lifting = p[2] >= 0;
		double left = lifting ? 1 - Math.max(0, Math.min(1, p[1])) : 1;
		double cx = (it.x() + it.w() / 2) * u;
		double cy = (it.y() + it.h() / 2) * u;
		double rad = (Math.min(it.w(), it.h()) / 2 - 10) * u;
		int cells = 24;
		int c = Math.max(2, px(6, u));
		for (int i = 0; i < cells; i++) {
			double a = -Math.PI / 2 + i * Math.PI * 2 / cells;
			int x = (int) Math.round(cx + Math.cos(a) * rad - c / 2.0);
			int y = (int) Math.round(cy + Math.sin(a) * rad - c / 2.0);
			boolean lit = i < Math.round(left * cells);
			round(g, x, y, x + c, y + c, Math.max(1, c / 4), lit ? argb(lifting ? VIOLET : INK, lifting ? 0.95 : 0.42) : argb(INK, 0.10));
		}
	}

	private void liftFill(GuiGraphicsExtractor g, Bridge.FxItem it, double u) {
		double lift = Math.max(0, Math.min(1, this.pocket()[1]));
		int x0 = px(it.x() + 2, u);
		int y1 = px(it.y() + it.h() - 2, u);
		int x1 = px(it.x() + it.w() - 2, u);
		int h = (int) Math.round((it.h() - 4) * u * lift);
		if (h > 0) {
			round(g, x0, y1 - h, x1, y1, px(10, u), argb(VIOLET, 0.22));
		}
	}
}
