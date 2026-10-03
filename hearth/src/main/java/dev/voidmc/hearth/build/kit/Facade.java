package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Habits;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;

/**
 * Where the openings and the posts of a building's walls go, decided once for all storeys so windows stand over windows
 * and timber posts frame them. Positions are kept as coordinates along each wall's axis (x for the front and back, z for
 * the sides), so a jettied storey whose wall has moved out still lines up with the one below.
 *
 * <p>Typical use: {@code Facade f = Facade.plan(d, body, doorX, windowWidth)}, then {@link Walls#storey} draws the walls
 * with posts at {@link #posts(Direction)} and {@link Openings} cuts the openings listed by {@link #openings(Direction)}.
 */
public final class Facade {
	/** What fills a column of a wall. */
	public enum Kind {
		DOOR, WINDOW, BLANK
	}

	/**
	 * An opening: a run of columns along a wall.
	 *
	 * @param at    first column (coordinate along the wall's axis: x for front/back, z for left/right)
	 * @param width columns
	 */
	public record Opening(Kind kind, int at, int width) {
		public boolean covers(int c) {
			return c >= this.at && c < this.at + this.width;
		}
	}

	private final Map<Direction, List<Opening>> openings = new EnumMap<>(Direction.class);
	private final Map<Direction, List<Integer>> posts = new EnumMap<>(Direction.class);
	private final Map<Direction, List<int[]>> braces = new EnumMap<>(Direction.class);

	public List<Opening> openings(Direction side) {
		return this.openings.getOrDefault(side, List.of());
	}

	/** Intermediate post columns (corners excluded) along a side. */
	public List<Integer> posts(Direction side) {
		return this.posts.getOrDefault(side, List.of());
	}

	/** Braced panels along a side, each {from, to} (inclusive columns). */
	public List<int[]> braces(Direction side) {
		return this.braces.getOrDefault(side, List.of());
	}

	public void add(Direction side, Opening o) {
		this.openings.computeIfAbsent(side, k -> new ArrayList<>()).add(o);
	}

	public void post(Direction side, int at) {
		List<Integer> p = this.posts.computeIfAbsent(side, k -> new ArrayList<>());
		if (!p.contains(at)) {
			p.add(at);
		}
	}

	/** The opening covering a column, or null. */
	public Opening at(Direction side, int c) {
		for (Opening o : openings(side)) {
			if (o.covers(c)) {
				return o;
			}
		}
		return null;
	}

	/** The coordinate along a side's axis of local (x, z). */
	public static int along(Direction side, int x, int z) {
		return side.getAxis() == Direction.Axis.Z ? x : z;
	}

	/**
	 * Plans the walls of a body. Timber, bamboo and plank walls get a regular grid of posts ({@code postSpacing} apart,
	 * evened out over the wall) and openings sit centred in the bays between them, at most one per bay; log walls frame
	 * each opening with a post either side; masonry walls have no posts. The front door goes in the bay nearest
	 * {@code doorAt} (none if negative), windows go in bays spread symmetrically about the middle of each non-party wall,
	 * and some blank timber bays get braces.
	 *
	 * @param doorWidth   1 or 2 (double doors)
	 * @param windowWidth 1 or 2 (narrowed to fit a bay)
	 * @param density     share of bays that get a window (0–1)
	 */
	public static Facade plan(Draft d, Body b, int doorAt, int doorWidth, int windowWidth, double density) {
		Facade f = new Facade();
		Habits.WallKind kind = d.habits().walls().kind();
		boolean grid = kind == Habits.WallKind.TIMBER_FRAME || kind == Habits.WallKind.BAMBOO || kind == Habits.WallKind.PLANK;
		boolean flank = kind == Habits.WallKind.LOG;
		int spacing = Math.max(2, d.habits().walls().postSpacing());
		double braces = d.habits().walls().braces();
		for (Side s : b.sides(0)) {
			Direction out = s.out();
			if (b.isParty(out)) {
				continue;
			}
			int lo = along(out, s.x(0), s.z(0));
			int hi = along(out, s.x(s.last()), s.z(s.last()));
			int a = Math.min(lo, hi);
			int c = Math.max(lo, hi);
			int n = c - a - 1;
			if (n <= 0) {
				continue;
			}
			// bays: a grid of posts evened out over the wall (masonry walls: one bay per window slot of ~spacing)
			int bays = Math.max(1, (int) Math.round((n + 1.0) / (spacing + 1.0)));
			List<int[]> bayList = new ArrayList<>();
			int prev = a;
			for (int j = 1; j <= bays; j++) {
				int post = j == bays ? c : a + (int) Math.round(j * (c - a) / (double) bays);
				if (post - prev - 1 >= 1) {
					bayList.add(new int[]{prev + 1, post - 1});
				}
				if (j < bays && grid) {
					f.post(out, post);
				}
				prev = post;
			}
			// the door in the bay nearest doorAt
			int doorBay = -1;
			if (out == Dirs.FRONT && doorAt >= 0) {
				int best = Integer.MAX_VALUE;
				for (int k = 0; k < bayList.size(); k++) {
					int[] bay = bayList.get(k);
					int dist = doorAt < bay[0] ? bay[0] - doorAt : doorAt > bay[1] ? doorAt - bay[1] : 0;
					if (bay[1] - bay[0] + 1 >= doorWidth && dist < best) {
						best = dist;
						doorBay = k;
					}
				}
				if (doorBay >= 0) {
					int[] bay = bayList.get(doorBay);
					int at = grid ? centre(bay, doorWidth) : Math.clamp(doorAt, bay[0], bay[1] - doorWidth + 1);
					f.add(out, new Opening(Kind.DOOR, at, doorWidth));
				}
			}
			// windows in bays, symmetric about the wall's middle
			int m = bayList.size();
			boolean[] win = new boolean[m];
			for (int k = 0; k < (m + 1) / 2; k++) {
				boolean want = d.chance(density) || (m <= 2 && k == 0);
				win[k] = want;
				win[m - 1 - k] = want;
			}
			for (int k = 0; k < m; k++) {
				if (k == doorBay || !win[k]) {
					continue;
				}
				int[] bay = bayList.get(k);
				int bw = bay[1] - bay[0] + 1;
				int w = Math.min(windowWidth, grid ? bw : Math.max(1, bw - 2));
				if (bw < 1 || !grid && bw < 3 && bw - w < 1) {
					w = 1;
				}
				int at = centre(bay, w);
				if (!grid && (at <= a || at + w - 1 >= c)) {
					continue;
				}
				f.add(out, new Opening(Kind.WINDOW, at, w));
			}
			if (flank) {
				for (Opening o : f.openings(out)) {
					if (o.at - 1 > a) {
						f.post(out, o.at - 1);
					}
					if (o.at + o.width < c) {
						f.post(out, o.at + o.width);
					}
				}
			}
			// braces in blank timber bays two or three wide
			if (grid) {
				for (int k = 0; k < m; k++) {
					int[] bay = bayList.get(k);
					int bw = bay[1] - bay[0] + 1;
					if (bw >= 2 && bw <= 3 && noOpening(f, out, bay[0], bay[1]) && d.chance(braces)) {
						f.braces.computeIfAbsent(out, key -> new ArrayList<>()).add(new int[]{bay[0], bay[1]});
					}
				}
			}
		}
		return f;
	}

	private static int centre(int[] bay, int w) {
		return bay[0] + (bay[1] - bay[0] + 1 - w) / 2;
	}

	private static boolean noOpening(Facade f, Direction out, int from, int to) {
		for (int c = from; c <= to; c++) {
			if (f.at(out, c) != null) {
				return false;
			}
		}
		return true;
	}

}
