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
 * {@code path}, {@code highway}; default lane, or a seeded choice without {@code points}).
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
		draw(lay, c, kind);
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
