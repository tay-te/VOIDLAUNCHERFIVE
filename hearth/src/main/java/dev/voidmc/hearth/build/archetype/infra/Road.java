package dev.voidmc.hearth.build.archetype.infra;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.infra.Course;
import dev.voidmc.hearth.build.kit.infra.Demo;
import dev.voidmc.hearth.build.kit.infra.Dress;
import dev.voidmc.hearth.build.kit.infra.Lay;
import dev.voidmc.hearth.build.kit.infra.Roadbed;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.station.Shrines;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:infra/road}: a stretch of the settlement's road network, built exactly over the corridor the planner
 * reserved (PLANNING.md §7): every column at the walking level of its nearest centreline step, cut and filled to it,
 * surfaced by kind and culture, smoothed where it climbs, headroom cleared, lit by tier.
 *
 * <ul>
 *   <li><b>street</b> (5): paved in the culture's setts with worn cobbles in a village, kerbed edges (laid logs in the
 *   north and on the bayou boardwalks), a kennel down the middle of an old stone street, iron grates in the gutters of a
 *   town's, lamps on the kerb line alternating sides.</li>
 *   <li><b>lane</b> (3): a dirt lane in a hamlet, half-cobbled in a village, paved in a town; rubble in the stone towns,
 *   log- or cane-edged in the north and the karst, planks in the bayou.</li>
 *   <li><b>alley</b> (3): rubble setts, dark and worn, with a kennel.</li>
 *   <li><b>path</b>: one wide to a door (the culture's path, stepping stones in a town); three wide a farm track, wheel
 *   ruts and a grass crown.</li>
 *   <li><b>highway</b> (5): a metalled country road with grass verges, tufts and flowers, milestones, lamps only near
 *   a city.</li>
 * </ul>
 *
 * <p>Where the land is uneven the bed is cut and filled; fill that shows beside the road is faced in the foundation
 * stone (any bank in a town, banks over two blocks in the country), columns in water are built up in masonry. A lone
 * rise of one block is a half step of slabs; a run of rises becomes a flight of stairs. Overlapping road plots compose
 * into junctions (see {@link Roadbed}).
 *
 * <p>Params: {@code points} (int array x,y,z,..., world centreline at walking level; default: a course across the plot
 * graded to the land), {@code width} (odd, default by kind), {@code kind} ({@code street}, {@code lane}, {@code alley},
 * {@code path}, {@code highway}; default lane, or a seeded choice without {@code points}), {@code country} (a road between
 * settlements: see {@link #country}).
 */
public final class Road implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("infra/road"), ArchetypeCategory.INFRASTRUCTURE, Set.of(), 0,
		1, 56, 1, 56, 1, 24, SettlementTier.CAMP, Set.of(), Set.of(), List.of(), 0, 1.0);

	/** Steps at each end kept plain (no kerbs or sunken carriageway; lamps a step further), where a lane runs into the road it joins. */
	public static final int END_ZONE = 2;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Lay lay = new Lay(d);
		int[] pts = Demo.points(d);
		Dress.Kind kind;
		int width;
		if (pts == null) {
			kind = d.hasParam("kind") ? Dress.Kind.of(d.param("kind", "lane")) : d.pick(Dress.Kind.values());
			width = Demo.odd(Math.min(d.param("width", kind.width()), d.frontage()));
			if (kind == Dress.Kind.PATH && !d.hasParam("width") && d.frontage() >= 3 && d.chance(0.5)) {
				width = 3;
			}
			pts = Demo.course(lay, width, 0.3, d.chance(0.6));
		} else {
			kind = Dress.Kind.of(d.param("kind", "lane"));
			width = Demo.odd(d.param("width", kind.width()));
		}
		Course c = new Course(pts, width);
		if (d.param("country", false)) {
			country(lay, c, kind);
			return;
		}
		draw(lay, c, kind);
	}

	/**
	 * A stretch of country road between settlements (docs/LIVING_WORLD_2.md §1.1, param {@code country}): never lit,
	 * a king's road metalled with gravel and packed earth between its verges, a track rutted with grass up its crown; a
	 * signpost where it leaves a town naming the town it leads to and how far ({@code sign_start}, {@code sign_end}: two
	 * lines, the name and the distance), and now and then a wayside cross or shrine beside it ({@code shrine}).
	 */
	static void country(Lay lay, Course c, Dress.Kind kind) {
		Draft d = lay.draft();
		Dress dress = Dress.of(lay, kind, c.width).country();
		Roadbed bed = new Roadbed(lay, c, dress, END_ZONE, false);
		int last = c.steps() - 1;
		lay.entrance(c.dx(0), c.dy(0), c.dz(0));
		lay.entrance(c.dx(last), c.dy(last), c.dz(last));
		bed.earthwork(false);
		bed.surface();
		if (kind == Dress.Kind.HIGHWAY) {
			bed.metal();
		}
		bed.details(kind);
		bed.clear(4);
		String start = d.param("sign_start", "");
		if (!start.isEmpty()) {
			signpost(lay, c, Math.min(3, last), start, true);
		}
		String end = d.param("sign_end", "");
		if (!end.isEmpty()) {
			signpost(lay, c, Math.max(0, last - 3), end, false);
		}
		if (d.param("shrine", false)) {
			wayside(lay, c, last / 2);
		}
	}

	/** The column beside the road at dense step {@code k} on its right ({@code side} 1) or left (-1), just off the corridor. */
	private static int[] beside(Course c, int k, int side) {
		Direction travel = c.cardinal(k);
		Direction out = side > 0 ? travel.getClockWise() : travel.getCounterClockWise();
		int off = c.width / 2 + 1;
		return new int[]{c.dx(k) + out.getStepX() * off, c.dz(k) + out.getStepZ() * off};
	}

	/** Where something may stand beside the road: inside the plot, on dry natural ground within a block of the road's level. */
	private static int standing(Lay lay, Course c, int k, int x, int z) {
		if (!lay.allowed(x, c.dy(k), z) || lay.wet(x, z) || c.contains(x, z)) {
			return Integer.MIN_VALUE;
		}
		int g = lay.ground(x, z);
		return Math.abs(g - c.dy(k)) <= 1 ? g : Integer.MIN_VALUE;
	}

	/**
	 * A signpost on the verge: a post, a sign with the town the road leads to and how far, readable both ways. It stands
	 * at the first place near {@code k} (looking a few steps on) where the ground beside the road is at the road's level.
	 */
	private static void signpost(Lay lay, Course c, int k, String text, boolean atStart) {
		String[] lines = text.split("\n", 2);
		java.util.List<String[]> sign = java.util.List.of(new String[]{"", ""}, new String[]{"", lines[0]}, new String[]{"", lines.length > 1 ? lines[1] : ""});
		int last = c.steps() - 1;
		for (int step = 0; step <= 12; step += 2) {
			int kk = atStart ? Math.min(last, k + step) : Math.max(0, k - step);
			for (int side : new int[]{1, -1}) {
				int[] at = beside(c, kk, side);
				int y = standing(lay, c, kk, at[0], at[1]);
				if (y == Integer.MIN_VALUE || !lay.allowed(at[0], y + 1, at[1]) || !lay.absent(at[0], y, at[1]) || !lay.absent(at[0], y + 1, at[1])) {
					continue;
				}
				// the sign faces along the road, toward those coming to it
				Direction facing = atStart ? c.cardinal(kk).getOpposite() : c.cardinal(kk);
				Draft d = lay.draft();
				Signs.postSign(d, lay.lx(at[0], at[1]), y - lay.floorY(), lay.lz(at[0], at[1]), lay.local(facing), sign);
				return;
			}
		}
	}

	/** A wayside cross or shrine beside the road, facing it (the culture's kind: {@link Shrines}). */
	private static void wayside(Lay lay, Course c, int k) {
		for (int side : new int[]{-1, 1}) {
			int[] at = beside(c, k, side);
			int y = standing(lay, c, k, at[0], at[1]);
			if (y == Integer.MIN_VALUE) {
				continue;
			}
			Direction travel = c.cardinal(k);
			Direction face = side > 0 ? travel.getCounterClockWise() : travel.getClockWise();
			Draft d = lay.draft();
			if (Shrines.wayside(d, lay.lx(at[0], at[1]), y - lay.floorY(), lay.lz(at[0], at[1]), lay.local(face))) {
				return;
			}
		}
	}

	/** Lays a road along a course (shared with the other linear works that carry a road on to their ends). */
	public static void draw(Lay lay, Course c, Dress.Kind kind) {
		Draft d = lay.draft();
		Dress dress = Dress.of(lay, kind, c.width);
		Roadbed bed = new Roadbed(lay, c, dress, END_ZONE, false);
		// folk come in where it leaves the road it starts from (builders work outward from there), and at its far end
		int last = c.steps() - 1;
		lay.entrance(c.dx(0), c.dy(0), c.dz(0));
		lay.entrance(c.dx(last), c.dy(last), c.dz(last));
		bed.earthwork(kind == Dress.Kind.STREET || kind == Dress.Kind.ALLEY || d.tier() >= 3);
		bed.surface();
		bed.details(kind);
		bed.clear(4);
	}
}
