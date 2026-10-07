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
			case WAYPOINT, QUEST -> 4;
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
		List<Marker> order = new ArrayList<>(compact ? markers : declutter(ui, f, markers, selected, hovered, zoom, playerX, playerZ));
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
		// and the same solid hairline to a tracked friend (never dashed)
		String tracked = dev.voidmc.atlas.client.AtlasClient.get().settings().tracked;
		if (tracked != null && !tracked.equals(selected) && tracked.startsWith(dev.voidmc.atlas.client.Social.FRIEND_PREFIX)) {
			for (Marker m : order) {
				if (m.id().equals(tracked)) {
					ui.line(f.screenX(playerX, playerZ), f.screenY(playerX, playerZ), f.screenX(m.x(), m.z()), f.screenY(m.x(), m.z()), ui.hairline(), 0x38FFFFFF);
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
				float rx = isCluster(m) ? clusterWidth(ui, m.name()) / 2 : r;
				(minor ? small : taken).add(m.style() == Marker.Style.STRUCTURE ? new float[]{ax - rx, ay - r, ax + rx, ay + r} : box(m, ax, ay));
			}
		}
		// and the player's own arrow, with its halo
		float selfX = f.screenX(playerX, playerZ);
		float selfY = f.screenY(playerX, playerZ);
		if (f.inside(selfX, selfY)) {
			taken.add(new float[]{selfX - 20, selfY - 20, selfX + 20, selfY + 20});
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
		// of the natural landmarks (peaks, lakes, waterfalls: plain words, alike), only the biggest of each kind on screen
		// is labelled; the others say their names when hovered
		java.util.Map<MarkerGlyph, Marker> biggest = new java.util.EnumMap<>(MarkerGlyph.class);
		for (Marker m : tiles) {
			if (m.style() == Marker.Style.STRUCTURE && isNatural(m.glyph())) {
				Marker best = biggest.get(m.glyph());
				if (best == null || size(m) > size(best)) {
					biggest.put(m.glyph(), m);
				}
			}
		}
		if (!compact) {
			for (Marker m : labelOrder) {
				boolean force = m.id().equals(selected) || m.id().equals(hovered);
				if (!force && m.style() == Marker.Style.STRUCTURE && isNatural(m.glyph()) && biggest.get(m.glyph()) != m) {
					continue;
				}
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
			// the biggest areas first; a name already on the map nearby is not written again (plain biome names repeat:
			// two forests either side of a river are both "Forest")
			List<Marker> names = new ArrayList<>();
			for (Marker m : order) {
				if (m.style() == Marker.Style.LABEL && f.inside(f.screenX(m.x(), m.z()), f.screenY(m.x(), m.z()))) {
					names.add(m);
				}
			}
			names.sort(Comparator.comparingDouble((Marker m) -> -extent(m)).thenComparing(Marker::id));
			List<Placed> written = new ArrayList<>();
			for (Marker m : names) {
				float ax = f.screenX(m.x(), m.z());
				float ay = f.screenY(m.x(), m.z());
				if (repeats(written, m.name(), ax, ay)) {
					continue;
				}
				Placed p = landName(ui, m, ax, ay, taken, small, zoom);
				if (p != null) {
					placed.add(p);
					written.add(p);
					if (m.id().equals(hovered) && m.source() != null && m.source().kind() == dev.voidmc.atlas.api.Source.Kind.PLUGIN) {
						// a name a mod gave says so when hovered: "from Expanse" under it
						float cw = smallSourceChipWidth(ui, m.source());
						smallSourceChip(ui, m.source(), (p.x0() + p.x1()) / 2 - cw / 2, p.y1() + 4);
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
				case STRUCTURE -> isCluster(m) ? cluster(ui, m, ax, ay) : tile(ui, m, ax, ay, s, sel, chip);
				case SETTLEMENT -> settlement(ui, m, ax, ay, s, sel, chip);
				case QUEST -> quest(ui, m, ax, ay, s, sel, chip);
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

	// --- decluttering -----------------------------------------------------------------------------------------------

	/** The id prefix of a count tile: it stands for the minor places and "?"s too close together to show one by one. */
	public static final String CLUSTER = "atlas:cluster:";

	public static boolean isCluster(Marker m) {
		return m.id().startsWith(CLUSTER);
	}

	private static final class Cluster {
		final List<Marker> members = new ArrayList<>();
		float x0, y0, x1, y1;
	}

	/**
	 * The markers as they can be shown without any two tiles touching: players and the selected or hovered place first;
	 * then waypoints and settlements; then the major landmarks (structures before peaks, lakes and waterfalls, nearest first), a
	 * landmark that would touch one already down joining the minor places; then the minor places and the "?"s, nearest
	 * first. Zoomed out (below 1:1) those are gathered on a screen grid (56 px from 1:4 out, 40 px in to 1:1): a cell with one place shows it, a cell
	 * with more shows a count tile at their middle. Zoomed in, a place shows unless it would touch another. Whatever
	 * touches something joins the count tile it touches, or else lives in the list only. So the land shows through, the
	 * landmarks stay, and the "?"s thin out.
	 */
	static List<Marker> declutter(Ui ui, MapRenderer.Frame f, List<Marker> markers, String selected, String hovered, double zoom, double px,
		double pz) {
		List<Marker> out = new ArrayList<>();
		List<float[]> taken = new ArrayList<>();
		List<Marker> majors = new ArrayList<>();
		List<Marker> pool = new ArrayList<>();
		List<Marker> pins = new ArrayList<>();
		for (Marker m : markers) {
			if (m.style() == Marker.Style.LABEL) {
				out.add(m);
				continue;
			}
			float ax = f.screenX(m.x(), m.z());
			float ay = f.screenY(m.x(), m.z());
			if (!f.inside(ax, ay)) {
				continue;
			}
			boolean pinned = m.id().equals(selected) || m.id().equals(hovered);
			if (m.style() == Marker.Style.WAYPOINT || m.style() == Marker.Style.SETTLEMENT || m.style() == Marker.Style.QUEST) {
				if (pinned) {
					out.add(m);
					float[] b = box(m, ax, ay);
					free(taken, b[0], b[1], b[2], b[3], true);
				} else {
					pins.add(m);
				}
			} else if (m.style() != Marker.Style.STRUCTURE || pinned) {
				out.add(m);
				float[] b = box(m, ax, ay);
				free(taken, b[0], b[1], b[2], b[3], true);
			} else if (m.glyph() == MarkerGlyph.UNKNOWN || m.tier() == Tier.MINOR) {
				pool.add(m);
			} else {
				majors.add(m);
			}
		}
		// waypoints and settlements, nearest first: one that would touch another joins the count tiles
		pins.sort(Comparator.comparingDouble(m -> m.distanceTo(px, pz)));
		for (Marker m : pins) {
			float[] b = box(m, f.screenX(m.x(), m.z()), f.screenY(m.x(), m.z()));
			if (free(taken, b[0], b[1], b[2], b[3], false)) {
				out.add(m);
			} else {
				pool.add(m);
			}
		}
		majors.sort(Comparator.comparingInt(MarkerPainter::landmarkRank).thenComparingDouble(m -> m.distanceTo(px, pz)));
		for (Marker m : majors) {
			float[] b = box(m, f.screenX(m.x(), m.z()), f.screenY(m.x(), m.z()));
			if (free(taken, b[0], b[1], b[2], b[3], false)) {
				out.add(m);
			} else {
				pool.add(m);
			}
		}
		pool.sort(Comparator.comparingDouble(m -> m.distanceTo(px, pz)));
		int caves = 0;
		for (java.util.Iterator<Marker> it = pool.iterator(); it.hasNext(); ) {
			if (it.next().glyph() == MarkerGlyph.CAVE && ++caves > CAVES_PER_SCREEN) {
				it.remove();
			}
		}
		java.util.Map<Long, List<Marker>> groups = new java.util.LinkedHashMap<>();
		float cell = zoom <= 0.25 ? 56 : 40;
		long unique = 0;
		for (Marker m : pool) {
			long key;
			if (zoom < 1) {
				long gx = (long) Math.floor((f.screenX(m.x(), m.z()) - f.x) / cell);
				long gy = (long) Math.floor((f.screenY(m.x(), m.z()) - f.y) / cell);
				key = gx << 32 ^ (gy & 0xFFFFFFFFL);
			} else {
				key = Long.MIN_VALUE + unique++;
			}
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
		}
		List<Cluster> clusters = new ArrayList<>();
		for (List<Marker> g : groups.values()) {
			float[] b;
			if (g.size() == 1) {
				Marker m = g.get(0);
				b = box(m, f.screenX(m.x(), m.z()), f.screenY(m.x(), m.z()));
				if (free(taken, b[0], b[1], b[2], b[3], false)) {
					out.add(m);
					continue;
				}
			} else {
				double mx = 0;
				double mz = 0;
				for (Marker m : g) {
					mx += m.x();
					mz += m.z();
				}
				float ax = f.screenX(mx / g.size(), mz / g.size());
				float ay = f.screenY(mx / g.size(), mz / g.size());
				float hw = clusterWidth(ui, String.valueOf(g.size())) / 2;
				b = new float[]{ax - hw, ay - 9, ax + hw, ay + 9};
				if (free(taken, b[0], b[1], b[2], b[3], false, false)) {
					Cluster c = new Cluster();
					c.members.addAll(g);
					c.x0 = b[0];
					c.y0 = b[1];
					c.x1 = b[2];
					c.y1 = b[3];
					clusters.add(c);
					// room for the count to grow a digit
					taken.add(new float[]{b[0] - 5, b[1] - 2, b[2] + 5, b[3] + 2});
					continue;
				}
			}
			// it touches something: it joins the count tile it touches, if any; if not it is in the list only
			for (Cluster c : clusters) {
				if (b[0] < c.x1 + 5 && b[2] > c.x0 - 5 && b[1] < c.y1 + 2 && b[3] > c.y0 - 2) {
					c.members.addAll(g);
					break;
				}
			}
		}
		int n = 0;
		for (Cluster c : clusters) {
			boolean allSighted = true;
			for (Marker m : c.members) {
				allSighted &= m.glyph() == MarkerGlyph.UNKNOWN;
			}
			// the tile stays where it was first put: its members may have grown, its place on screen not
			double cx = f.mapX((c.x0 + c.x1) / 2, (c.y0 + c.y1) / 2);
			double cz = f.mapZ((c.x0 + c.x1) / 2, (c.y0 + c.y1) / 2);
			Marker first = c.members.get(0);
			out.add(new Marker(CLUSTER + n++, first.layer(), String.valueOf(c.members.size()), "", "", cx, first.y(), cz, first.dimension(),
				allSighted ? MarkerGlyph.UNKNOWN : MarkerGlyph.DOT, Marker.Style.STRUCTURE, 0, null, null, Tier.MINOR));
		}
		return out;
	}

	private static boolean isNatural(MarkerGlyph g) {
		return g == MarkerGlyph.PEAK || g == MarkerGlyph.LAKE || g == MarkerGlyph.WATERFALL;
	}

	/** How big a natural landmark is, to pick the one to label: a peak by its height, a lake by its size. */
	private static double size(Marker m) {
		if (m.glyph() == MarkerGlyph.LAKE) {
			Place p = dev.voidmc.atlas.client.AtlasClient.get().placeOf(m);
			return p == null ? 0 : p.radius();
		}
		return m.y();
	}

	/** Cave mouths shown on one screen at most (the nearest); the rest are in the list. */
	private static final int CAVES_PER_SCREEN = 3;

	/** Which landmarks keep their place first when two would touch: structures, then peaks, lakes, waterfalls. */
	private static int landmarkRank(Marker m) {
		return switch (m.glyph()) {
			case PEAK -> 1;
			case LAKE -> 2;
			case WATERFALL -> 3;
			default -> 0;
		};
	}

	/** A marker's tile on screen, as a box. */
	private static float[] box(Marker m, float ax, float ay) {
		return switch (m.style()) {
			case STRUCTURE -> {
				float r = m.tier() == Tier.MINOR ? 8 : 13;
				yield new float[]{ax - r, ay - r, ax + r, ay + r};
			}
			case WAYPOINT -> new float[]{ax - 10, ay - 30, ax + 10, ay + 2};
			case SETTLEMENT, QUEST -> new float[]{ax - 13, ay - 13, ax + 13, ay + 13};
			default -> new float[]{ax - 11, ay - 11, ax + 11, ay + 11};
		};
	}

	private static float clusterWidth(Ui ui, String count) {
		return Math.max(18, ui.width(count, Ui.Font.F10) + 10);
	}

	/**
	 * A count tile, in the house style of a minor tile: 18 px high, as wide as its number needs, the well's fill with a
	 * 12% hairline (hollow with a 24% hairline when everything in it is only sighted), the number at 62%.
	 */
	private static Placed cluster(Ui ui, Marker m, float ax, float ay) {
		boolean sighted = m.glyph() == MarkerGlyph.UNKNOWN;
		float w = clusterWidth(ui, m.name());
		float h = 18;
		float x = ax - w / 2;
		float y = ay - h / 2;
		ui.round(x, y, w, h, 6, sighted ? HOLLOW : Theme.WELL);
		ui.border(x, y, w, h, 6, 0, sighted ? HAIR_24 : Theme.HAIR_12);
		float tw = ui.width(m.name(), Ui.Font.F10);
		ui.text(m.name(), ax - tw / 2, y + (h - Ui.Font.F10.size * 1.26F) / 2, Ui.Font.F10, Theme.ink(0.62F));
		return new Placed(m, ax, ay, x, y, x + w, y + h);
	}

	// --- labels ----------------------------------------------------------------------------------------------------

	/** What a marker's label chip says: its name and a dim meta (a height, a distance). Null for no label. */
	private static String[] labelText(Marker m, double zoom, boolean force, double px, double pz) {
		if (m.name().isEmpty() || isCluster(m)) {
			return null;
		}
		boolean sighted = m.glyph() == MarkerGlyph.UNKNOWN;
		boolean major = m.tier() == Tier.MAJOR;
		if (m.style() == Marker.Style.STRUCTURE && !force) {
			// a "?" is labelled only when hovered or selected (the list carries the distances); a minor place from 2:1
			if (sighted || !major && zoom < 2 || major && zoom < 0.25) {
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
		float w = ui.width(text[0], font) + (text[1].isEmpty() ? 0 : 6 + ui.width(text[1], Ui.Font.F9, 0.6F)) + pad * 2
			+ (m.style() == Marker.Style.QUEST ? 14 : 0);
		float half = switch (m.style()) {
			case STRUCTURE -> (minor ? 8 : 13) * s;
			case SETTLEMENT, QUEST -> 13 * s;
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
		float tx = box.x0 + pad;
		if (m.style() == Marker.Style.QUEST) {
			// the quest chip: the "!" first
			ui.glyph(MarkerGlyph.QUEST, tx - 2, box.y0 + (h - 10) / 2, 10, Theme.INK, Theme.INK);
			tx += 14;
		}
		ui.text(box.name, tx, top, font, ink);
		if (!box.meta.isEmpty()) {
			ui.text(box.meta, tx + ui.width(box.name, font) + 6, top + (font.size - 9) / 2 + 0.5F, Ui.Font.F9, 0.6F, Theme.INK_4);
		}
	}

	/**
	 * A name on the land, as the design's region label: XL (24, tracking 10, ink 42%) for regions over 600 blocks
	 * across, L (16, tracking 7, 36%) over 250, M (11.5, tracking 4.5, 34%) below; water names a cool grey at 40%,
	 * Regular, tracking 6. Shown from far out to 1:1 (only XL and L from 1:8 out), fading to nothing by 2:1. Left off
	 * where it would cover a tile or a label.
	 */
	private static Placed landName(Ui ui, Marker m, float ax, float ay, List<float[]> taken, List<float[]> small, double zoom) {
		if (m.name().isEmpty() || zoom >= 2) {
			return null;
		}
		boolean water = m.subtitle().startsWith("LAKE") || m.subtitle().startsWith("RIVER");
		double across = m.subtitle().isEmpty() ? 0 : extent(m);
		// the sizes to try, largest first: the area's own size, then a step down (a name that cannot find room at its
		// size may still fit a size smaller); M and the small water size only from 1:8 in
		int size = water ? (across > 250 ? 3 : 4) : across > 600 ? 0 : across > 250 ? 1 : 2;
		int last = water ? 4 : 2;
		String text = m.name().toUpperCase(Locale.ROOT);
		for (int k = size; k <= last; k++) {
			if ((k == 2 || k == 4) && zoom <= 1.0 / 8) {
				break;
			}
			Ui.Font font = switch (k) {
				case 0 -> Ui.Font.F24;
				case 1 -> Ui.Font.F16;
				case 2 -> Ui.Font.F115;
				case 3 -> Ui.Font.R13;
				default -> Ui.Font.R11;
			};
			float tracking = switch (k) {
				case 0 -> 10;
				case 1 -> 7;
				case 2 -> 4.5F;
				case 3 -> 6;
				default -> 5;
			};
			float alpha = switch (k) {
				case 0 -> 0.42F;
				case 1 -> 0.36F;
				case 2 -> 0.34F;
				default -> 0.40F;
			};
			if (zoom > 1) {
				alpha *= (float) (2 - zoom);
			}
			float w = ui.width(text, font, tracking);
			float h = font.size * 1.26F;
			float x0 = ax - w / 2;
			// where it would cover a tile or a label, a step above or below will do; failing that a size smaller; failing
			// that it gives way. A name never passes over a tile, however small.
			for (float dy : new float[]{0, -h * 1.4F, h * 1.4F}) {
				float y0 = ay - h / 2 + dy;
				if (free(small, x0, y0, x0 + w, y0 + h, false, false) && free(taken, x0, y0, x0 + w, y0 + h, false)) {
					// in the biome view, a light fill takes the dark ink (#0E0F11 at 62%)
					int ink = "ink:dark".equals(m.meta()) ? 0x9E0E0F11 : water ? Theme.alpha(WATER_INK, alpha) : Theme.ink(alpha);
					ui.text(text, x0, y0, font, tracking, ink);
					return new Placed(m, ax, ay, x0, y0, x0 + w, y0 + h);
				}
			}
		}
		return null;
	}

	/** How far apart (px) two of the same name must be to both be written. */
	private static final float REPEAT_PX = 360;

	/** Whether the same name is already written within {@link #REPEAT_PX} of a point. */
	private static boolean repeats(List<Placed> written, String name, float ax, float ay) {
		for (Placed p : written) {
			if (p.marker().name().equalsIgnoreCase(name) && Math.hypot((p.x0() + p.x1()) / 2 - ax, (p.y0() + p.y1()) / 2 - ay) < REPEAT_PX) {
				return true;
			}
		}
		return false;
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
		double night = dev.voidmc.atlas.client.render.MapLight.night();
		if (night > 0.05 && m.glyph() == MarkerGlyph.VILLAGE) {
			// by night lit places stay warm: a soft glow under a village
			for (int i = 3; i >= 1; i--) {
				ui.circle(ax, ay, t * 0.55F * i, Theme.alpha(0xFFC9A66B, (float) (0.07 * night)));
			}
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

	/** A quest step's target (atlas/quest target): the "!" in a 26 px tile with a solid hairline (never dashed). */
	private static Placed quest(Ui ui, Marker m, float ax, float ay, float s, boolean sel, Chip chip) {
		float t = 26 * s;
		float x = ax - t / 2;
		float y = ay - t / 2;
		ui.round(x, y, t, t, 8 * s, sel ? Theme.ink(0.92F) : 0xB30B0B0C);
		ui.border(x, y, t, t, 8 * s, 1, sel ? Theme.ink(0.9F) : Theme.ink(0.7F));
		ui.glyph(MarkerGlyph.QUEST, ax - 7 * s, ay - 7 * s, 14 * s, sel ? Theme.SHELL : Theme.INK, sel ? Theme.SHELL : Theme.INK);
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
		// the head: the colour's own shape when colour-blind shapes are on (the design's 355:14556)
		int index = 0;
		int[] palette = dev.voidmc.atlas.api.Waypoint.COLOURS;
		for (int i = 1; i < palette.length; i++) {
			if (palette[i] == (colour & 0xFFFFFF)) {
				index = i;
				break;
			}
		}
		boolean shapes = dev.voidmc.atlas.client.AtlasClient.get().settings().shapeMarkers;
		PinShapes.head(ui, x + w / 2, y + w / 2, w * 0.9F, index, shapes, head);
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
		if (m.id().startsWith(dev.voidmc.atlas.client.Social.FRIEND_PREFIX)) {
			return friend(ui, m, ax, ay, s, chip);
		}
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
	 * Friends in this world beyond the map's edge: their face pinned 18 px inside the edge, towards them (the design's
	 * edge marker), with a small chevron pointing out. {@code x, y, w, h} is the map's box.
	 */
	public static void friendEdges(Ui ui, MapRenderer.Frame f, String dimension, float x, float y, float w, float h, boolean round) {
		var social = dev.voidmc.atlas.client.AtlasClient.get().social;
		if (!social.available()) {
			return;
		}
		float cx = x + w / 2;
		float cy = y + h / 2;
		for (var fr : social.friends()) {
			if (!fr.dimension().equals(dimension)) {
				continue;
			}
			float sx = f.screenX(fr.x(), fr.z());
			float sy = f.screenY(fr.x(), fr.z());
			if (f.inside(sx, sy)) {
				continue;
			}
			float dx = sx - cx;
			float dy = sy - cy;
			float t;
			if (round) {
				float r = Math.min(w, h) / 2 - 18;
				t = (float) (r / Math.hypot(dx, dy));
			} else {
				t = Math.min((w / 2 - 18) / Math.max(1e-3F, Math.abs(dx)), (h / 2 - 18) / Math.max(1e-3F, Math.abs(dy)));
			}
			float ex = cx + dx * t;
			float ey = cy + dy * t;
			float d = 22;
			Faces.tile(ui, social.profileOf(fr.player()), ex - d / 2, ey - d / 2, d, !fr.faded(), fr.faded() ? 0.6F : 1);
			// the chevron, two short strokes outside the tile towards them
			double a = Math.atan2(dy, dx);
			float ox = ex + (float) Math.cos(a) * (d / 2 + 6);
			float oy = ey + (float) Math.sin(a) * (d / 2 + 6);
			float bx = (float) Math.cos(a + 2.4), by = (float) Math.sin(a + 2.4);
			float qx = (float) Math.cos(a - 2.4), qy = (float) Math.sin(a - 2.4);
			ui.line(ox, oy, ox + bx * 4, oy + by * 4, 1.5F, Theme.alpha(Faces.RING, 0.8F));
			ui.line(ox, oy, ox + qx * 4, oy + qy * 4, 1.5F, Theme.alpha(Faces.RING, 0.8F));
		}
	}

	/** "from Kestrel_  ·  2 min ago" over each party member's land, and under it "No places here until you visit". */
	public static void sharedChips(Ui ui, MapRenderer.Frame f, List<dev.voidmc.atlas.client.Social.Shared> shared) {
		long now = System.currentTimeMillis();
		for (var s : shared) {
			float sx = f.screenX(s.x(), s.z());
			float sy = f.screenY(s.x(), s.z());
			if (!f.inside(sx, sy)) {
				continue;
			}
			long min = Math.max(0, (now - s.at()) / 60_000L);
			String text = "from " + s.name() + "  ·  " + (min < 1 ? "just now" : min + " min ago");
			float w = 7 + 9 + 6 + ui.width(text, Ui.Font.F95) + 9;
			float x = sx - w / 2;
			float y = sy - 60;
			ui.round(x, y, w, 20, 10, Theme.alpha(Theme.WELL, 0.92F));
			ui.border(x, y, w, 20, 10, 0, Theme.HAIR_8);
			int[][] cells = {{0, 0}, {1, 0}, {0, 1}, {2, 1}, {1, 2}, {2, 2}};
			for (int[] c : cells) {
				ui.round(x + 7 + c[0] * 3.2F, y + 5.5F + c[1] * 3.2F, 2.4F, 2.4F, 0.8F, Theme.ink(0.55F));
			}
			ui.text(text, x + 22, y + 4, Ui.Font.F95, Theme.INK_2);
			String note = "No places here until you visit";
			ui.text(note, sx - ui.width(note, Ui.Font.F95) / 2, sy + 8, Ui.Font.F95, Theme.INK_4);
		}
	}

	/** A friend in this world (atlas/friend marker): their face in the green ring, and the chip with name and distance. */
	private static Placed friend(Ui ui, Marker m, float ax, float ay, float s, Chip chip) {
		float d = 24 * s;
		String id = m.id().substring(dev.voidmc.atlas.client.Social.FRIEND_PREFIX.length());
		boolean faded = !m.meta().isEmpty() && m.meta().startsWith("a while");
		Faces.tile(ui, dev.voidmc.atlas.client.AtlasClient.get().social.profileOf(id), ax - d / 2, ay - d / 2, d, !faded, faded ? 0.6F : 1);
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

	/** The small source chip (20 high) beside a name: the readout's "from Expanse", a region label's on hover. */
	public static float smallSourceChip(Ui ui, Source source, float x, float y) {
		String text = source.phrase();
		float w = smallSourceChipWidth(ui, source);
		ui.round(x, y, w, 20, 10, Theme.WELL);
		ui.border(x, y, w, 20, 10, 0, Theme.HAIR_8);
		int[][] cells = {{0, 0}, {1, 0}, {0, 1}, {2, 1}, {1, 2}, {2, 2}};
		for (int[] c : cells) {
			ui.round(x + 7 + c[0] * 3.2F, y + 5.5F + c[1] * 3.2F, 2.4F, 2.4F, 0.8F, Theme.ink(0.55F));
		}
		ui.text(text, x + 7 + 9 + 6, y + 4, Ui.Font.F95, Theme.INK_2);
		return w;
	}

	public static float smallSourceChipWidth(Ui ui, Source source) {
		return 7 + 9 + 6 + ui.width(source.phrase(), Ui.Font.F95) + 9;
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
