package dev.voidmc.hearth.build.archetype.infra;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.infra.Course;
import dev.voidmc.hearth.build.kit.infra.Demo;
import dev.voidmc.hearth.build.kit.infra.Lay;
import dev.voidmc.hearth.build.kit.infra.Spans;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * {@code hearth:infra/bridge}: a bridge from bank to bank over the corridor the planner reserved (PLANNING.md §7), its
 * ends at the approach roads' levels on the banks, its deck at {@code deck_y} or above, rising from the banks on its
 * own ends. The make is chosen by culture, span, height and wealth, and every make is drawn whole:
 *
 * <ul>
 *   <li><b>stone arch</b>: one arch over a short span (a semicircle, the karst and wisteria moon bridge), segmental
 *   arches on piers over a long one; voussoirs in a dressed stone round every arch, spandrels, a string course, coped
 *   parapets with newels over the piers and lamps at the ends. The deck humps over the crowns.</li>
 *   <li><b>timber beam</b> and <b>trestle</b>: bents of posts down to the bed with caps and knee braces, stringers,
 *   a plank deck, a railing with posts at the bents; trestles cross-braced when tall.</li>
 *   <li><b>covered</b> (alpine, northern, temperate, wisteria, karst; straight, five wide): a timber house on the deck,
 *   boarded sides with a window band, a gable roof, portals with gable boards, on stone abutments.</li>
 *   <li><b>rope</b> (gorges: a deck far above the water or ground): towers at both banks, the deck slung between them,
 *   cables and hangers.</li>
 *   <li><b>clapper</b> (moorland, maritime, steppe; short and low): rough stone piers carrying great flat slabs.</li>
 *   <li><b>piers</b> (a stone culture's crossing on a slant or a bend): stone piers the width of the deck carrying a
 *   timber deck, newels of dressed stone with lamps over them.</li>
 *   <li><b>boardwalk</b> ({@code kind=boardwalk}): a plank walk just over the water on piles, bending as it likes, open
 *   at the edges (stilt houses front it).</li>
 * </ul>
 *
 * <p>Points of interest: a VIEWPOINT at the middle of the deck. Entrances at both ends.
 *
 * <p>Params: {@code points} (int array x,y,z,..., world: first and last on the banks at the approach roads' walking
 * levels, the rest at {@code deck_y}; default: straight across the plot), {@code width} (odd, default 3), {@code deck_y}
 * (walking level on the deck), {@code water_y} (the water's surface: the first non-water y), {@code kind} ({@code span}
 * or {@code boardwalk}), {@code road_kind} (the road it carries), {@code make} (force a make: arch, beam, trestle,
 * covered, rope, clapper, piers).
 */
public final class Bridge implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("infra/bridge"), ArchetypeCategory.INFRASTRUCTURE, Set.of(), 0,
		1, 56, 1, 56, 1, 32, SettlementTier.CAMP, Set.of(), Set.of(), List.of(), 0, 1.0);

	public enum Make {
		ARCH, BEAM, TRESTLE, COVERED, ROPE, CLAPPER, BOARDWALK, PIERS
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Lay lay = new Lay(d);
		int[] pts = Demo.points(d);
		boolean boardwalk = "boardwalk".equals(d.param("kind", "span"));
		int width;
		if (pts == null) {
			width = Demo.odd(Math.min(d.param("width", d.chance(0.5) ? 5 : 3), d.frontage()));
			pts = demo(lay, width, boardwalk);
			// without the planner's roads, a lane carries on from the bridge's front end to the street
			int n = pts.length / 3;
			int ex = pts[(n - 1) * 3];
			int ez = pts[(n - 1) * 3 + 2];
			int lz = lay.lz(ex, ez);
			if (lz > 0) {
				int lx = lay.lx(ex, ez);
				int[][] r = Course.run(ex, ez, lay.wx(lx, 0), lay.wz(lx, 0));
				int street = lay.surface(lay.wx(lx, -1), lay.wz(lx, -1));
				int[] ys = Demo.grade(lay, r[0], r[1], 1.0, street);
				ys[0] = pts[(n - 1) * 3 + 1];
				for (int i = 1; i < ys.length; i++) {
					ys[i] = Math.max(ys[i - 1] - 1, Math.min(ys[i - 1] + 1, ys[i]));
				}
				Road.draw(lay, new Course(Course.denseOf(r[0], ys, r[1]), width), dev.voidmc.hearth.build.kit.infra.Dress.Kind.LANE);
			}
		} else {
			width = Demo.odd(d.param("width", 3));
		}
		Course c = new Course(pts, width);
		int deckDefault = c.topLevel();
		if (Demo.points(d) == null) {
			boolean wet = false;
			for (int k = 0; k < c.steps(); k++) {
				wet |= lay.wet(c.dx(k), c.dz(k));
			}
			// a demo on dry land follows the land: its own points are its levels
			deckDefault = wet ? deckDefault : c.bottomLevel();
		}
		Spans s = new Spans(lay, c, d.param("deck_y", deckDefault), d.param("water_y", Site.NONE));
		Make make = boardwalk ? Make.BOARDWALK : choose(d, s, c);
		if (d.hasParam("make")) {
			try {
				make = Make.valueOf(d.param("make", "arch").toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				// keep the chosen make
			}
		}
		if (make == Make.COVERED && (!c.straight() || width < 5)) {
			make = Make.BEAM;
		}
		// on a slant an arch comes out jagged: stone piers carry a timber deck instead
		if (make == Make.ARCH && !c.straight() && !d.hasParam("make")) {
			make = Make.PIERS;
		}
		int last = c.steps() - 1;
		lay.entrance(c.dx(0), c.dy(0), c.dz(0));
		lay.entrance(c.dx(last), c.dy(last), c.dz(last));
		switch (make) {
			case ARCH -> s.arch(Spans.ArchStyle.of(d, s));
			case COVERED -> s.covered();
			case ROPE -> s.rope();
			case CLAPPER -> s.clapper();
			case TRESTLE -> s.timber(true);
			case PIERS -> s.piers();
			case BOARDWALK -> s.boardwalk();
			default -> s.timber(false);
		}
		int mid = c.steps() / 2;
		Direction across = c.cardinal(mid).getClockWise();
		lay.poi(PoiType.VIEWPOINT, c.dx(mid), s.walk(mid), c.dz(mid), across);
	}

	/** The make for a culture, span, height and tier. */
	static Make choose(Draft d, Spans s, Course c) {
		Culture culture = d.style().culture();
		int tier = d.tier();
		int span = s.gapLength();
		boolean gorge = s.drop() >= 9 && span >= 6;
		boolean wide = c.width >= 5 && c.straight();
		double r = d.random().nextDouble();
		if (culture == null) {
			return span <= 9 ? Make.ARCH : Make.BEAM;
		}
		switch (culture) {
			case BAYOU, TROPICAL -> {
				return gorge ? Make.ROPE : span > 10 ? Make.TRESTLE : Make.BEAM;
			}
			case NORTHERN, TUNDRA -> {
				if (wide && span >= 8 && tier >= 1 && r < 0.5) {
					return Make.COVERED;
				}
				return s.drop() >= 5 ? Make.TRESTLE : Make.BEAM;
			}
			case ALPINE -> {
				if (gorge) {
					return Make.ROPE;
				}
				if (wide && span >= 7 && r < 0.6) {
					return Make.COVERED;
				}
				return tier >= 3 ? Make.ARCH : s.drop() >= 5 ? Make.TRESTLE : Make.BEAM;
			}
			case KARST -> {
				if (gorge) {
					return Make.ROPE;
				}
				return wide && span >= 10 && r < 0.4 ? Make.COVERED : Make.ARCH;
			}
			case WISTERIA -> {
				return wide && span >= 9 && r < 0.35 ? Make.COVERED : Make.ARCH;
			}
			case MOORLAND, MARITIME -> {
				if (span <= 7 && s.drop() <= 4 && (tier <= 2 || culture == Culture.MOORLAND)) {
					return Make.CLAPPER;
				}
				return Make.ARCH;
			}
			case STEPPE -> {
				if (tier >= 3) {
					return Make.ARCH;
				}
				return span <= 6 && s.drop() <= 4 ? Make.CLAPPER : Make.BEAM;
			}
			case MESA -> {
				return gorge ? (r < 0.5 ? Make.ROPE : Make.TRESTLE) : Make.ARCH;
			}
			case TEMPERATE -> {
				if (tier == 0) {
					return span <= 8 ? Make.BEAM : Make.TRESTLE;
				}
				if (wide && span >= 8 && tier <= 2 && r < 0.3) {
					return Make.COVERED;
				}
				return Make.ARCH;
			}
			default -> {
				return tier == 0 && span <= 8 ? Make.BEAM : Make.ARCH;
			}
		}
	}

	/**
	 * Default geometry: straight across the plot from the back to the front, its ends on the land at the street's level in
	 * front and the land's at the back, the deck two over the water (or the higher bank), as the planner lays a bridge.
	 */
	static int[] demo(Lay lay, int width, boolean boardwalk) {
		Draft d = lay.draft();
		int w = d.frontage();
		int dp = d.depth();
		int cx = Math.clamp(w / 2, 0, w - 1);
		int[][] full = Course.run(lay.wx(cx, dp - 1), lay.wz(cx, dp - 1), lay.wx(cx, 0), lay.wz(cx, 0));
		// over the water where the plot crosses it: two columns of bank either side
		int first = -1;
		int lastWet = -1;
		for (int i = 0; i < full[0].length; i++) {
			if (lay.wet(full[0][i], full[1][i])) {
				if (first < 0) {
					first = i;
				}
				lastWet = i;
			}
		}
		int a = first < 0 ? 0 : Math.max(0, first - 2);
		int b = first < 0 ? full[0].length - 1 : Math.min(full[0].length - 1, lastWet + 2);
		if (b - a < 2) {
			a = 0;
			b = full[0].length - 1;
		}
		int[] xs = java.util.Arrays.copyOfRange(full[0], a, b + 1);
		int[] zs = java.util.Arrays.copyOfRange(full[1], a, b + 1);
		int n = xs.length;
		int[] ys = new int[n];
		int water = Integer.MIN_VALUE;
		for (int i = 0; i < n; i++) {
			int wy = lay.water(xs[i], zs[i]);
			if (wy != Site.NONE) {
				water = Math.max(water, wy);
			}
		}
		boolean atFront = b == full[0].length - 1;
		int front = atFront ? lay.surface(lay.wx(cx, -1), lay.wz(cx, -1)) : lay.surface(xs[n - 1], zs[n - 1]);
		// (a demo over falling land keeps its ends within reach of each other)
		int back = Math.clamp(lay.surface(xs[0], zs[0]), front - (n - 1) / 2, front + (n - 1) / 2);
		back = Math.min(back, lay.floorY() + d.maxHeight() - 9);
		if (water == Integer.MIN_VALUE && !boardwalk) {
			// dry land: a causeway following it, graded a block a step at most
			int[] g = Demo.grade(lay, xs, zs, 1.0, front);
			return Course.denseOf(xs, g, zs);
		}
		int top = lay.floorY() + d.maxHeight() - 8;
		int deck = boardwalk ? (water == Integer.MIN_VALUE ? front : water + 1)
			: Math.max(Math.max(front, back), water == Integer.MIN_VALUE ? Math.max(front, back) + 2 : water + 2);
		deck = Math.min(deck, Math.max(Math.max(front, back), top));
		for (int i = 0; i < n; i++) {
			ys[i] = deck;
		}
		if (!boardwalk) {
			ys[0] = back;
			ys[n - 1] = front;
		}
		return Course.denseOf(xs, ys, zs);
	}

}
