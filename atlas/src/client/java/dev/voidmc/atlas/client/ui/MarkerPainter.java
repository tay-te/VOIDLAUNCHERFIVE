package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.Place;
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
			case LABEL -> -1;
			case STRUCTURE -> m.glyph() == MarkerGlyph.UNKNOWN ? 0 : 1;
			case SETTLEMENT -> 2;
			case WAYPOINT -> 3;
			case PLAYER -> 4;
		};
	}

	/** Water names are a muted blue; everything else on the land is ink. */
	public static final int WATER_INK = 0xFF8095A6;

	/**
	 * Draws the markers over a drawn map; {@code compact} for the minimap (tiles only, smaller, no labels but the
	 * hovered one).
	 */
	public static List<Placed> paint(Ui ui, MapRenderer.Frame f, List<Marker> markers, String selected, String hovered, boolean compact,
		double playerX, double playerZ) {
		List<Marker> order = new ArrayList<>(markers);
		// labels first (they give way to the markers drawn over them), then by rank, major before minor
		order.sort(Comparator.<Marker>comparingInt(m -> m.id().equals(selected) ? 9 : rank(m)).thenComparing(m -> m.tier().ordinal()));
		List<float[]> labels = new ArrayList<>();
		List<Placed> placed = new ArrayList<>();
		float s = compact ? 0.72F : 1;
		// markers first claim their tiles, so the land's names keep clear of them
		for (Marker m : order) {
			if (m.style() == Marker.Style.LABEL) {
				continue;
			}
			float ax = f.screenX(m.x(), m.z());
			float ay = f.screenY(m.x(), m.z());
			if (f.inside(ax, ay)) {
				labels.add(new float[]{ax - 11 * s, ay - 11 * s, ax + 11 * s, ay + 11 * s});
			}
		}
		for (Marker m : order) {
			if (m.style() != Marker.Style.LABEL || compact) {
				continue;
			}
			float ax = f.screenX(m.x(), m.z());
			float ay = f.screenY(m.x(), m.z());
			if (f.inside(ax, ay)) {
				Placed p = label(ui, m, ax, ay, labels, m.id().equals(selected) || m.id().equals(hovered));
				if (p != null) {
					placed.add(p);
				}
			}
		}
		labels.clear();
		for (Marker m : order) {
			if (m.style() == Marker.Style.LABEL) {
				continue;
			}
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
				case LABEL -> {
				}
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

	/**
	 * A name on the land: a region's in spaced caps, a lake's or river's in a muted blue, centred on its label point;
	 * left off where it would cover a marker or another name.
	 */
	private static Placed label(Ui ui, Marker m, float ax, float ay, List<float[]> labels, boolean force) {
		if (m.name().isEmpty()) {
			return null;
		}
		boolean region = m.subtitle().contains("REGION");
		boolean sighted = m.subtitle().startsWith("SIGHTED");
		Ui.Font font = region && m.tier() == dev.voidmc.atlas.api.Tier.MAJOR ? Ui.Font.F11 : Ui.Font.F10;
		String text = region ? m.name().toUpperCase(java.util.Locale.ROOT) : m.name();
		float tracking = region ? 1.6F : 0.2F;
		float w = ui.width(text, font, tracking);
		float h = font.size + 4;
		float x0 = ax - w / 2;
		float y0 = ay - h / 2;
		if (!free(labels, x0 - 4, y0 - 2, x0 + w + 4, y0 + h + 2, force)) {
			return null;
		}
		int ink = region ? (sighted ? Theme.ink(0.30F) : Theme.ink(0.46F)) : Theme.alpha(WATER_INK, sighted ? 0.6F : 0.9F);
		ui.text(text, x0, y0 + 1, font, tracking, ink);
		return new Placed(m, ax, ay, x0, y0, x0 + w, y0 + h);
	}

	private static Placed structure(Ui ui, Marker m, float ax, float ay, float s, boolean sel, boolean label, List<float[]> labels, boolean force) {
		float t = 20 * s;
		float x = ax - t / 2;
		float y = ay - t / 2;
		// a place only sighted is a dimmer tile with a "?": something to come back to
		boolean unknown = m.glyph() == MarkerGlyph.UNKNOWN;
		ui.round(x, y, t, t, 6 * s, sel ? Theme.INK : unknown ? Theme.alpha(Theme.WELL, 0.85F) : Theme.WELL);
		ui.border(x, y, t, t, 6 * s, 0, sel ? Theme.HAIR_16 : unknown ? Theme.HAIR_8 : Theme.HAIR_12);
		ui.glyph(m.glyph(), x + 3.5F * s, y + 3.5F * s, 13 * s, sel ? Theme.SHELL : Theme.ink(unknown ? 0.42F : 0.6F), sel ? Theme.SHELL : Theme.ink(0.95F));
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

	/** The glyph for a place, by what it is. */
	public static MarkerGlyph glyphFor(Place p) {
		return switch (p.kind()) {
			case Place.PEAK -> MarkerGlyph.PEAK;
			case Place.LAKE, Place.RIVER, Place.WATERFALL -> MarkerGlyph.WATER;
			case Place.CAVE_MOUTH, Place.RAVINE -> MarkerGlyph.CAVE;
			case "minecraft:ruined_portal" -> MarkerGlyph.PORTAL;
			case "minecraft:shipwreck" -> MarkerGlyph.SHIP;
			case "atlas:geode" -> MarkerGlyph.GEM;
			default -> {
				String k = p.kind();
				if (k.startsWith("minecraft:ruined_portal")) {
					yield MarkerGlyph.PORTAL;
				}
				if (k.startsWith("minecraft:shipwreck")) {
					yield MarkerGlyph.SHIP;
				}
				yield glyphFor(p.category(), p.underground());
			}
		};
	}

	/** Whether a place is only sighted (drawn as a "?"). */
	public static boolean sighted(Place p) {
		return p.state() != DiscoveryState.DISCOVERED;
	}

	/**
	 * A source chip: the source's caps label in a hairline pill ("SEEN", "EXACT", "VOID SERVER", "HEARTH"). Returns its
	 * width.
	 */
	public static float sourceChip(Ui ui, dev.voidmc.atlas.api.Source source, float x, float y) {
		String text = source.label();
		float w = ui.width(text, Ui.Font.F85, 1) + 12;
		ui.border(x, y, w, 15, 7.5F, 0, Theme.HAIR_10);
		ui.text(text, x + 6, y + 2.5F, Ui.Font.F85, 1, Theme.INK_3);
		return w;
	}

	public static float sourceChipWidth(Ui ui, dev.voidmc.atlas.api.Source source) {
		return ui.width(source.label(), Ui.Font.F85, 1) + 12;
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
