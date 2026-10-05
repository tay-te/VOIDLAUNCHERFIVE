package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.atlas.client.render.MapRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Draws markers as the design does ({@code atlas/marker}, {@code atlas/region label}):
 *
 * <ul>
 *   <li><b>major</b> places on a 26 px tile, labelled from 1:4 inwards; <b>minor</b> places on a 16 px tile with the
 *   glyph at 62%, labelled from 1:1 inwards or when hovered;</li>
 *   <li><b>sighted</b> places as a hollow tile with a "?", labelled "Sighted" and their distance from 1:1;</li>
 *   <li>the <b>selected</b> place as an ink tile with a dark glyph, and a hairline from the player to it;</li>
 *   <li>the <b>names</b> of regions, lakes and rivers as quiet caps over the land, sized by the region, never boxed,
 *   fading out between 1:1 and 2:1;</li>
 *   <li>settlements, waypoints and other players as before (a pin whose tip is the spot, a ring with an initial).</li>
 * </ul>
 *
 * Labels never overlap: majors are placed first, then minors, and the land's names give way to everything.
 */
public final class MarkerPainter {
	/** Where a marker ended up on screen: its anchor and the box that answers clicks. */
	public record Placed(Marker marker, float ax, float ay, float x0, float y0, float x1, float y1) {
		public boolean hit(float x, float y) {
			return x >= this.x0 && x <= this.x1 && y >= this.y0 && y <= this.y1;
		}
	}

	/** The names of water: a cool grey, Regular weight. */
	public static final int WATER_INK = 0xFFB4BDC6;
	/** A sighted tile's fill: the shell at 55%. */
	private static final int HOLLOW = 0x8C0B0B0C;
	private static final int HAIR_24 = 0x3DFFFFFF;
	private static final int HAIR_30 = 0x4DFFFFFF;
	private static final int HAIR_14 = 0x24FFFFFF;

	private MarkerPainter() {
	}

	/** A label chip's box and words. */
	private record Chip(float x0, float y0, float x1, float y1, boolean minor, String name, String meta) {
	}

	private static int rank(Marker m) {
		return switch (m.style()) {
			case LABEL -> 0;
			case STRUCTURE -> m.tier() == Tier.MAJOR ? 2 : 1;
			case SETTLEMENT -> 3;
			case WAYPOINT -> 4;
			case PLAYER -> 5;
		};
	}

	/**
	 * Draws the markers over a drawn map at a zoom (physical pixels per block); {@code compact} for the minimap (tiles
	 * only, a little smaller, no labels but the hovered one). {@code playerX/Z} for distances and the hairline to the
	 * selected place.
	 */
	public static List<Placed> paint(Ui ui, MapRenderer.Frame f, List<Marker> markers, String selected, String hovered, boolean compact,
		double playerX, double playerZ, double zoom) {
		List<Marker> order = new ArrayList<>(markers);
		order.sort(Comparator.comparingInt(MarkerPainter::rank));
		List<float[]> taken = new ArrayList<>();
		List<Placed> placed = new ArrayList<>();
		float s = compact ? 0.8F : 1;
		// the hairline from the player to the selected place, under everything
		if (selected != null) {
			for (Marker m : order) {
				if (m.id().equals(selected) && m.style() != Marker.Style.LABEL) {
					float ax = f.screenX(m.x(), m.z());
					float ay = f.screenY(m.x(), m.z());
					float px = f.screenX(playerX, playerZ);
					float py = f.screenY(playerX, playerZ);
					ui.line(px, py, ax, ay, ui.hairline(), 0x38FFFFFF);
					break;
				}
			}
		}
		// every tile claims its square first, so no label covers a tile (the land's names give way to every tile but the
		// small ones, which a quiet name may pass over)
		List<float[]> small = new ArrayList<>();
		for (Marker m : order) {
			if (m.style() == Marker.Style.LABEL) {
				continue;
			}
			float ax = f.screenX(m.x(), m.z());
			float ay = f.screenY(m.x(), m.z());
			if (f.inside(ax, ay)) {
				boolean minor = m.tier() == Tier.MINOR && m.style() == Marker.Style.STRUCTURE;
				float r = (minor ? 8 : 13) * s;
				(minor ? small : taken).add(new float[]{ax - r, ay - r, ax + r, ay + r});
			}
		}
		// tiles and their labels: minors under majors, labels of majors placed before those of minors
		List<Marker> tiles = new ArrayList<>();
		for (Marker m : order) {
			if (m.style() != Marker.Style.LABEL && f.inside(f.screenX(m.x(), m.z()), f.screenY(m.x(), m.z()))) {
				tiles.add(m);
			}
		}
		List<Marker> labelOrder = new ArrayList<>(tiles);
		labelOrder.sort(Comparator.comparingInt((Marker m) -> m.id().equals(selected) || m.id().equals(hovered) ? 0 : 1)
			.thenComparingInt(m -> -rank(m)));
		java.util.Map<String, Chip> chips = new java.util.HashMap<>();
		if (!compact) {
			for (Marker m : labelOrder) {
				boolean force = m.id().equals(selected) || m.id().equals(hovered);
				float ax = f.screenX(m.x(), m.z());
				float ay = f.screenY(m.x(), m.z());
				Chip chip = labelBox(ui, m, ax, ay, s, zoom, force, playerX, playerZ);
				if (chip != null && (force || free(small, chip.x0, chip.y0, chip.x1, chip.y1, false, false)) && free(taken, chip.x0, chip.y0, chip.x1, chip.y1, force)) {
					chips.put(m.id(), chip);
				}
			}
		} else if (hovered != null) {
			for (Marker m : tiles) {
				if (m.id().equals(hovered)) {
					Chip chip = labelBox(ui, m, f.screenX(m.x(), m.z()), f.screenY(m.x(), m.z()), s, zoom, true, playerX, playerZ);
					if (chip != null) {
						chips.put(m.id(), chip);
					}
				}
			}
		}
		// the land's names go down before any tile, and only where nothing else is
		if (!compact) {
			for (Marker m : order) {
				if (m.style() == Marker.Style.LABEL) {
					float ax = f.screenX(m.x(), m.z());
					float ay = f.screenY(m.x(), m.z());
					if (f.inside(ax, ay)) {
						Placed p = landName(ui, m, ax, ay, taken, zoom);
						if (p != null) {
							placed.add(p);
						}
					}
				}
			}
		}
		for (Marker m : tiles) {
			float ax = f.screenX(m.x(), m.z());
			float ay = f.screenY(m.x(), m.z());
			boolean sel = m.id().equals(selected);
			Chip chip = chips.get(m.id());
			placed.add(switch (m.style()) {
				case STRUCTURE -> tile(ui, m, ax, ay, s, sel, chip);
				case SETTLEMENT -> settlement(ui, m, ax, ay, s, sel, chip);
				case WAYPOINT -> waypoint(ui, m, ax, ay, s, sel, chip);
				case PLAYER -> player(ui, m, ax, ay, s, chip);
				case LABEL -> null;
			});
		}
		placed.removeIf(java.util.Objects::isNull);
		ui.flush();
		return placed;
	}

	private static boolean free(List<float[]> taken, float x0, float y0, float x1, float y1, boolean force) {
		return free(taken, x0, y0, x1, y1, force, true);
	}

	/** Whether a box overlaps nothing taken; {@code claim}: take it if so (or if forced). */
	private static boolean free(List<float[]> taken, float x0, float y0, float x1, float y1, boolean force, boolean claim) {
		if (!force) {
			for (float[] r : taken) {
				if (x0 < r[2] && x1 > r[0] && y0 < r[3] && y1 > r[1]) {
					return false;
				}
			}
		}
		if (claim) {
			taken.add(new float[]{x0 - 2, y0 - 2, x1 + 2, y1 + 2});
		}
		return true;
	}

	// --- labels ----------------------------------------------------------------------------------------------------

	/** What a marker's label chip says: its name and a dim meta (a height, a distance). Null for no label. */
	private static String[] labelText(Marker m, double zoom, boolean force, double px, double pz) {
		if (m.name().isEmpty()) {
			return null;
		}
		boolean sighted = m.glyph() == MarkerGlyph.UNKNOWN;
		boolean major = m.tier() == Tier.MAJOR;
		if (m.style() == Marker.Style.STRUCTURE && !force) {
			if (sighted && zoom < 1 || !major && zoom < 1 || major && zoom < 0.25) {
				return null;
			}
		}
		String meta = m.meta();
		if (meta.isEmpty() && (sighted || zoom >= 1 || m.style() == Marker.Style.WAYPOINT)) {
			meta = distance(m.distanceTo(px, pz));
		}
		return new String[]{m.name(), meta};
	}

	/** Where a marker's label chip goes, and what it says; null for none. */
	private static Chip labelBox(Ui ui, Marker m, float ax, float ay, float s, double zoom, boolean force, double px, double pz) {
		String[] text = labelText(m, zoom, force, px, pz);
		if (text == null) {
			return null;
		}
		boolean minor = m.style() == Marker.Style.STRUCTURE && m.tier() == Tier.MINOR;
		Ui.Font font = minor ? Ui.Font.F10 : Ui.Font.F11;
		float pad = minor ? 7 : 8;
		float h = minor ? 19 : 22;
		float w = ui.width(text[0], font) + (text[1].isEmpty() ? 0 : 6 + ui.width(text[1], Ui.Font.F9, 0.6F)) + pad * 2;
		float half = switch (m.style()) {
			case STRUCTURE -> (minor ? 8 : 13) * s;
			case SETTLEMENT -> 13 * s;
			case WAYPOINT -> 10 * s;
			default -> 11 * s;
		};
		float x0 = ax + half + (minor ? 4 : 5);
		float y0 = m.style() == Marker.Style.WAYPOINT ? ay - 29 * s : ay - h / 2;
		return new Chip(x0, y0, x0 + w, y0 + h, minor, text[0], text[1]);
	}

	private static void chip(Ui ui, Marker m, Chip box, boolean sel) {
		boolean minor = box.minor;
		boolean sighted = m.glyph() == MarkerGlyph.UNKNOWN;
		Ui.Font font = minor ? Ui.Font.F10 : Ui.Font.F11;
		float pad = minor ? 7 : 8;
		float w = box.x1 - box.x0;
		float h = box.y1 - box.y0;
		float r = minor ? 6 : 8;
		int fill = sel ? Theme.RAISED : minor ? Theme.WELL : Theme.ISLAND;
		int hair = sel ? HAIR_14 : minor ? Theme.HAIR_7 : Theme.HAIR_8;
		ui.round(box.x0, box.y0, w, h, r, fill);
		ui.border(box.x0, box.y0, w, h, r, 0, hair);
		int ink = minor || sighted ? Theme.INK_2 : Theme.INK;
		float top = box.y0 + (h - font.size * 1.26F) / 2;
		ui.text(box.name, box.x0 + pad, top, font, ink);
		if (!box.meta.isEmpty()) {
			ui.text(box.meta, box.x0 + pad + ui.width(box.name, font) + 6, top + (font.size - 9) / 2 + 0.5F, Ui.Font.F9, 0.6F, Theme.INK_4);
		}
	}

	/**
	 * A name on the land, as the design's region label: XL (24, tracking 10, ink 42%) for regions over 600 blocks
	 * across, L (16, tracking 7, 36%) over 250, M (11.5, tracking 4.5, 34%) below; water names a cool grey at 40%,
	 * Regular, tracking 6. Shown from far out to 1:1 (only XL and L from 1:8 out), fading to nothing by 2:1. Left off
	 * where it would cover a tile or a label.
	 */
	private static Placed landName(Ui ui, Marker m, float ax, float ay, List<float[]> taken, double zoom) {
		if (m.name().isEmpty() || zoom >= 2) {
			return null;
		}
		boolean water = m.subtitle().startsWith("LAKE") || m.subtitle().startsWith("RIVER");
		double across = m.subtitle().isEmpty() ? 0 : extent(m);
		Ui.Font font;
		float tracking;
		float alpha;
		if (water) {
			font = Ui.Font.R13;
			tracking = 6;
			alpha = 0.40F;
		} else if (across > 600) {
			font = Ui.Font.F24;
			tracking = 10;
			alpha = 0.42F;
		} else if (across > 250) {
			font = Ui.Font.F16;
			tracking = 7;
			alpha = 0.36F;
		} else {
			if (zoom <= 1.0 / 8) {
				return null;
			}
			font = Ui.Font.F115;
			tracking = 4.5F;
			alpha = 0.34F;
		}
		if (water && zoom <= 1.0 / 8 && across <= 250) {
			return null;
		}
		if (zoom > 1) {
			alpha *= (float) (2 - zoom);
		}
		String text = m.name().toUpperCase(Locale.ROOT);
		float w = ui.width(text, font, tracking);
		float h = font.size * 1.26F;
		float x0 = ax - w / 2;
		float y0 = ay - h / 2;
		// where it would cover a landmark or a label, a step above or below will do; failing that it gives way
		boolean placed = false;
		for (float dy : new float[]{0, -h * 1.4F, h * 1.4F}) {
			if (free(taken, x0, ay - h / 2 + dy, x0 + w, ay + h / 2 + dy, false)) {
				y0 = ay - h / 2 + dy;
				placed = true;
				break;
			}
		}
		if (!placed) {
			return null;
		}
		ui.text(text, x0, y0, font, tracking, water ? Theme.alpha(WATER_INK, alpha) : Theme.ink(alpha));
		return new Placed(m, ax, ay, x0, y0, x0 + w, y0 + h);
	}

	/** How far across a named area is, from the marker's place (the label markers carry it in their detail-less id). */
	private static double extent(Marker m) {
		Place p = dev.voidmc.atlas.client.AtlasClient.get().placeOf(m);
		return p == null ? 0 : p.radius() * 2.0;
	}

	// --- tiles -----------------------------------------------------------------------------------------------------

	/**
	 * A place's tile: major 26 px (fill #191A1C, hairline 16%), minor 16 px (fill #131315, hairline 12%, glyph at 62%),
	 * sighted hollow (shell at 55%, hairline 30% (24% minor), the "?" at 70% (60%)), selected ink with a dark glyph.
	 */
	private static Placed tile(Ui ui, Marker m, float ax, float ay, float s, boolean sel, Chip chip) {
		boolean major = m.tier() == Tier.MAJOR;
		boolean sighted = m.glyph() == MarkerGlyph.UNKNOWN;
		float t = (major ? 26 : 16) * s;
		float r = (major ? 8 : 5) * s;
		float x = ax - t / 2;
		float y = ay - t / 2;
		int fill;
		int hair;
		int ink;
		if (sel) {
			fill = Theme.ink(0.92F);
			hair = Theme.ink(0.9F);
			ink = Theme.SHELL;
		} else if (sighted) {
			fill = HOLLOW;
			hair = major ? HAIR_30 : HAIR_24;
			ink = Theme.ink(major ? 0.70F : 0.60F);
		} else {
			fill = major ? Theme.ISLAND : Theme.WELL;
			hair = major ? Theme.HAIR_16 : Theme.HAIR_12;
			ink = major ? Theme.INK : Theme.ink(0.62F);
		}
		ui.round(x, y, t, t, r, fill);
		ui.border(x, y, t, t, r, 0, hair);
		float g = (major ? 16 : 10) * s;
		ui.glyph(m.glyph(), ax - g / 2, ay - g / 2, g, ink, ink);
		float x1 = x + t;
		if (chip != null) {
			chip(ui, m, chip, sel);
			x1 = chip.x1;
		}
		return new Placed(m, ax, ay, x, y, x1, y + t);
	}

	private static Placed settlement(Ui ui, Marker m, float ax, float ay, float s, boolean sel, Chip chip) {
		float t = 26 * s;
		float x = ax - t / 2;
		float y = ay - t / 2;
		ui.round(x, y, t, t, 8 * s, sel ? Theme.ink(0.92F) : Theme.ISLAND);
		ui.border(x, y, t, t, 8 * s, 0, sel ? Theme.ink(0.9F) : Theme.HAIR_16);
		ui.glyph(m.glyph(), ax - 8 * s, ay - 8 * s, 16 * s, sel ? Theme.SHELL : Theme.INK, sel ? Theme.SHELL : Theme.INK);
		float x1 = x + t;
		if (chip != null) {
			chip(ui, m, chip, sel);
			x1 = chip.x1;
		}
		return new Placed(m, ax, ay, x, y, x1, y + t);
	}

	private static Placed waypoint(Ui ui, Marker m, float ax, float ay, float s, boolean sel, Chip chip) {
		// the tip of the stem is the spot
		float w = 20 * s;
		float x = ax - w / 2;
		float y = ay - 29 * s;
		int colour = m.colour() == 0 ? 0xEDEEEF : m.colour();
		int head = Theme.alpha(0xFF000000 | colour, 0.92F);
		if (sel) {
			ui.ring(x + w / 2, y + w / 2, w / 2 + 3 * s, 1.5F, Theme.ink(0.6F));
		}
		ui.circle(x + w / 2, y + w / 2, w / 2, head);
		ui.round(x + 7 * s, y + 7 * s, 6 * s, 6 * s, 2 * s, Theme.SHELL);
		ui.round(x + 9 * s, y + 21 * s, 2 * s, 8 * s, 1 * s, head);
		float x1 = x + w;
		if (chip != null) {
			chip(ui, m, chip, sel);
			x1 = chip.x1;
		}
		return new Placed(m, ax, ay, x, y, x1, ay);
	}

	private static Placed player(Ui ui, Marker m, float ax, float ay, float s, Chip chip) {
		float d = 22 * s;
		int ring = m.colour() == 0 ? Theme.GREEN : 0xE6000000 | m.colour();
		ui.circle(ax, ay, d / 2, Theme.ISLAND);
		ui.ring(ax, ay, d / 2, 1.5F, ring);
		String initial = m.name().isEmpty() ? "?" : m.name().substring(0, 1).toUpperCase(Locale.ROOT);
		if (s >= 1) {
			ui.textCentred(initial, ax, ay - 6.5F, Ui.Font.F10, 0, Theme.INK);
		}
		float x1 = ax + d / 2;
		if (chip != null) {
			chip(ui, m, chip, false);
			x1 = chip.x1;
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

	// --- shared bits -----------------------------------------------------------------------------------------------

	/**
	 * A hollow or filled place tile of any size with its glyph, for lists, cards, the toast and the compass: hollow
	 * (sighted) is the shell at 55% with a 30% hairline and the "?" at 75%.
	 */
	public static void placeTile(Ui ui, float x, float y, float size, float radius, MarkerGlyph glyph, boolean hollow, float glyphSize, float glyphAlpha) {
		ui.round(x, y, size, size, radius, hollow ? HOLLOW : Theme.WELL);
		ui.border(x, y, size, size, radius, 0, hollow ? HAIR_30 : Theme.HAIR_8);
		ui.glyph(glyph, x + (size - glyphSize) / 2, y + (size - glyphSize) / 2, glyphSize, Theme.ink(glyphAlpha), Theme.ink(glyphAlpha));
	}

	/**
	 * A source chip (the design's {@code source chip}): a small mod mark and the source in words ("Seen by you", "from
	 * the VOID server", "from Hearth") in a well pill. Returns its width.
	 */
	public static float sourceChip(Ui ui, Source source, float x, float y) {
		String text = source.phrase();
		float w = sourceChipWidth(ui, source);
		ui.round(x, y, w, 26, 12, Theme.WELL);
		ui.border(x, y, w, 26, 12, 0, Theme.HAIR_8);
		// the mod mark: six cells of a 3 x 3 grid
		int[][] cells = {{0, 0}, {1, 0}, {0, 1}, {2, 1}, {1, 2}, {2, 2}};
		for (int[] c : cells) {
			ui.round(x + 8 + c[0] * 4, y + 7.5F + c[1] * 4, 3, 3, 1, Theme.ink(0.55F));
		}
		ui.text(text, x + 8 + 11 + 7, y + 6, Ui.Font.F11, Theme.INK_2);
		return w;
	}

	public static float sourceChipWidth(Ui ui, Source source) {
		return 8 + 11 + 7 + ui.width(source.phrase(), Ui.Font.F11) + 11;
	}

	public static String distance(double blocks) {
		if (blocks >= 1000) {
			return String.format(Locale.ROOT, "%.1f km", blocks / 1000);
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
		return Kinds.glyph(p);
	}

	/** The glyph for a structure category (other mods' markers, old discoveries). */
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
