package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.client.render.MapRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws markers the way the design does: structures as small dark tiles with a dim label, settlements as larger tiles
 * with a label chip, waypoints as pins whose tip is the spot, players as a ring with an initial. Labels that would
 * overlap a label already placed are left off (the selected and hovered marker always keep theirs).
 */
public final class MarkerPainter {
	/** Where a marker ended up on screen: its anchor and the box that answers clicks. */
	public record Placed(Marker marker, float ax, float ay, float x0, float y0, float x1, float y1) {
		public boolean hit(float x, float y) {
			return x >= this.x0 && x <= this.x1 && y >= this.y0 && y <= this.y1;
		}
	}

	private static int rank(Marker m) {
		return switch (m.style()) {
			case STRUCTURE -> 0;
			case SETTLEMENT -> 1;
			case WAYPOINT -> 2;
			case PLAYER -> 3;
		};
	}

	/**
	 * Draws the markers over a drawn map; {@code compact} for the minimap (tiles only, smaller, no labels but the
	 * hovered one).
	 */
	public static List<Placed> paint(Ui ui, MapRenderer.Frame f, List<Marker> markers, String selected, String hovered, boolean compact,
		double playerX, double playerZ) {
		List<Marker> order = new ArrayList<>(markers);
		order.sort(Comparator.<Marker>comparingInt(m -> m.id().equals(selected) ? 9 : rank(m)));
		List<float[]> labels = new ArrayList<>();
		List<Placed> placed = new ArrayList<>();
		float s = compact ? 0.72F : 1;
		for (Marker m : order) {
			float ax = f.screenX(m.x(), m.z());
			float ay = f.screenY(m.x(), m.z());
			if (!f.inside(ax, ay)) {
				continue;
			}
			boolean sel = m.id().equals(selected);
			boolean hov = m.id().equals(hovered);
			boolean label = !compact || hov || sel;
			switch (m.style()) {
				case STRUCTURE -> placed.add(structure(ui, m, ax, ay, s, sel, label, labels, sel || hov));
				case SETTLEMENT -> placed.add(settlement(ui, m, ax, ay, s, sel, label, labels, sel || hov));
				case WAYPOINT -> placed.add(waypoint(ui, m, ax, ay, s, sel, label, labels, sel || hov, playerX, playerZ));
				case PLAYER -> placed.add(player(ui, m, ax, ay, s, label, labels, sel || hov));
			}
		}
		ui.flush();
		return placed;
	}

	private static boolean free(List<float[]> labels, float x0, float y0, float x1, float y1, boolean force) {
		if (!force) {
			for (float[] r : labels) {
				if (x0 < r[2] && x1 > r[0] && y0 < r[3] && y1 > r[1]) {
					return false;
				}
			}
		}
		labels.add(new float[]{x0 - 2, y0 - 2, x1 + 2, y1 + 2});
		return true;
	}

	private static Placed structure(Ui ui, Marker m, float ax, float ay, float s, boolean sel, boolean label, List<float[]> labels, boolean force) {
		float t = 20 * s;
		float x = ax - t / 2;
		float y = ay - t / 2;
		ui.round(x, y, t, t, 6 * s, sel ? Theme.INK : Theme.WELL);
		ui.border(x, y, t, t, 6 * s, 0, sel ? Theme.HAIR_16 : Theme.HAIR_12);
		ui.glyph(m.glyph(), x + 3.5F * s, y + 3.5F * s, 13 * s, sel ? Theme.SHELL : Theme.ink(0.6F), sel ? Theme.SHELL : Theme.ink(0.95F));
		float x1 = x + t;
		if (label && !m.name().isEmpty()) {
			float tw = ui.width(m.name(), Ui.Font.F10);
			float cx = x + t + 4;
			float cw = tw + 14;
			float ch = 19;
			float cy = ay - ch / 2;
			if (free(labels, cx, cy, cx + cw, cy + ch, force)) {
				ui.round(cx, cy, cw, ch, 7, Theme.WELL);
				ui.border(cx, cy, cw, ch, 7, 0, Theme.HAIR_7);
				ui.text(m.name(), cx + 7, cy + 3, Ui.Font.F10, Theme.INK_2);
				x1 = cx + cw;
			}
		}
		return new Placed(m, ax, ay, x, y, x1, y + t);
	}

	private static Placed settlement(Ui ui, Marker m, float ax, float ay, float s, boolean sel, boolean label, List<float[]> labels, boolean force) {
		float t = 26 * s;
		float x = ax - t / 2;
		float y = ay - t / 2;
		ui.round(x, y, t, t, 8 * s, sel ? Theme.INK : Theme.ISLAND);
		ui.border(x, y, t, t, 8 * s, 0, Theme.HAIR_16);
		ui.glyph(m.glyph(), x + 4.5F * s, y + 4.5F * s, 17 * s, sel ? Theme.SHELL : Theme.ink(0.85F), sel ? Theme.SHELL : Theme.INK);
		float x1 = x + t;
		if (label && !m.name().isEmpty()) {
			x1 = chip(ui, m.name(), m.meta(), x + t + 5, ay - 11, labels, force, x1);
		}
		return new Placed(m, ax, ay, x, y, x1, y + t);
	}

	/** A label chip with an optional dim meta; returns its right edge (or {@code fallback} if it did not fit). */
	private static float chip(Ui ui, String name, String meta, float cx, float cy, List<float[]> labels, boolean force, float fallback) {
		float nw = ui.width(name, Ui.Font.F11);
		float mw = meta.isEmpty() ? 0 : ui.width(meta, Ui.Font.F9, 0.6F) + 6;
		float cw = nw + mw + 16;
		float ch = 22;
		if (!free(labels, cx, cy, cx + cw, cy + ch, force)) {
			return fallback;
		}
		ui.round(cx, cy, cw, ch, 8, Theme.ISLAND);
		ui.border(cx, cy, cw, ch, 8, 0, Theme.HAIR_8);
		ui.text(name, cx + 8, cy + 4, Ui.Font.F11, Theme.INK);
		if (!meta.isEmpty()) {
			ui.text(meta, cx + 8 + nw + 6, cy + 6.5F, Ui.Font.F9, 0.6F, Theme.INK_4);
		}
		return cx + cw;
	}

	private static Placed waypoint(Ui ui, Marker m, float ax, float ay, float s, boolean sel, boolean label, List<float[]> labels, boolean force,
		double px, double pz) {
		// the tip of the stem is the spot
		float w = 20 * s;
		float x = ax - w / 2;
		float y = ay - 29 * s;
		int colour = m.colour() == 0 ? 0xEDEEEF : m.colour();
		int head = Theme.alpha(0xFF000000 | colour, 0.92F);
		if (sel) {
			ui.ring(x + w / 2, y + w / 2, w / 2 + 3 * s, 1.5F, Theme.VIOLET);
		}
		ui.circle(x + w / 2, y + w / 2, w / 2, head);
		ui.round(x + 7 * s, y + 7 * s, 6 * s, 6 * s, 2 * s, Theme.SHELL);
		ui.round(x + 9 * s, y + 21 * s, 2 * s, 8 * s, 1 * s, head);
		float x1 = x + w;
		if (label && !m.name().isEmpty()) {
			String meta = m.meta().isEmpty() ? distance(m.distanceTo(px, pz)) : m.meta();
			x1 = chip(ui, m.name(), meta, x + w + 5, y, labels, force, x1);
		}
		return new Placed(m, ax, ay, x, y, x1, ay);
	}

	private static Placed player(Ui ui, Marker m, float ax, float ay, float s, boolean label, List<float[]> labels, boolean force) {
		float d = 22 * s;
		int ring = m.colour() == 0 ? Theme.GREEN : 0xE6000000 | m.colour();
		ui.circle(ax, ay, d / 2, Theme.ISLAND);
		ui.ring(ax, ay, d / 2, 1.5F, ring);
		String initial = m.name().isEmpty() ? "?" : m.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
		if (s >= 1) {
			ui.textCentred(initial, ax, ay - 6.5F, Ui.Font.F10, 0, Theme.INK);
		}
		float x1 = ax + d / 2;
		if (label && !m.name().isEmpty()) {
			x1 = chip(ui, m.name(), m.meta(), ax + d / 2 + 5, ay - 11, labels, force, x1);
		}
		return new Placed(m, ax, ay, ax - d / 2, ay - d / 2, x1, ay + d / 2);
	}

	/**
	 * The player: a violet arrow pointing the way they face (radians, 0 = up, clockwise), in a faint violet range
	 * ring, as in the design. {@code size} is the ring's diameter.
	 */
	public static void self(Ui ui, float cx, float cy, float angle, float size) {
		float r = size / 2;
		ui.circle(cx, cy, r, Theme.alpha(Theme.VIOLET, 0.10F));
		ui.ring(cx, cy, r, 0, Theme.alpha(Theme.VIOLET, 0.35F));
		float k = size / 44F;
		// the arrow, in the design's 44 px box, centred on its middle (22, 22)
		float[][] pts = {{22, 11.5F}, {30, 29.5F}, {22, 25.2F}, {14, 29.5F}};
		float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
		float[] p = new float[8];
		float[] o = new float[8];
		for (int i = 0; i < 4; i++) {
			float dx = (pts[i][0] - 22) * k, dy = (pts[i][1] - 21) * k;
			p[i * 2] = cx + dx * cos - dy * sin;
			p[i * 2 + 1] = cy + dx * sin + dy * cos;
			float ox = (pts[i][0] - 22) * k * 1.22F, oy = (pts[i][1] - 21) * k * 1.22F;
			o[i * 2] = cx + ox * cos - oy * sin;
			o[i * 2 + 1] = cy + ox * sin + oy * cos;
		}
		// the dark outline (the design's 1.6 px stroke), then the two halves of the arrow
		ui.convex(new float[]{o[0], o[1], o[2], o[3], o[4], o[5]}, Theme.SHELL);
		ui.convex(new float[]{o[0], o[1], o[4], o[5], o[6], o[7]}, Theme.SHELL);
		ui.convex(new float[]{p[0], p[1], p[2], p[3], p[4], p[5]}, Theme.VIOLET);
		ui.convex(new float[]{p[0], p[1], p[4], p[5], p[6], p[7]}, Theme.VIOLET);
	}

	public static String distance(double blocks) {
		if (blocks >= 1000) {
			return String.format(java.util.Locale.ROOT, "%.1f km", blocks / 1000);
		}
		return Math.round(blocks) + " m";
	}

	/** The compass direction from one point to another ("NW"). */
	public static String direction(double fromX, double fromZ, double toX, double toZ) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		if (Math.abs(dx) < 1 && Math.abs(dz) < 1) {
			return "HERE";
		}
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		return names[(int) Math.floorMod(Math.round(angle / 45), 8)];
	}

	/** The glyph for a structure category. */
	public static MarkerGlyph glyphFor(String category, boolean underground) {
		if (underground) {
			return MarkerGlyph.REALM;
		}
		return switch (category) {
			case "VILLAGE" -> MarkerGlyph.VILLAGE;
			case "DUNGEON" -> MarkerGlyph.VAULT;
			case "SIGHT" -> MarkerGlyph.DOT;
			default -> MarkerGlyph.TOWER;
		};
	}
}
