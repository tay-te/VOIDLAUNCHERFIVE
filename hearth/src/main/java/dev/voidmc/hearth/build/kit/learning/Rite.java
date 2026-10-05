package dev.voidmc.hearth.build.kit.learning;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;

/**
 * How a culture raises the buildings of its religious houses and its schools: the walls of the ranges round a cloister,
 * the arcades of the cloister walk, the ranges' roofs, the church's east end, its windows and its bell. The materials
 * still come from the style's roles; this decides the geometry the roles cannot describe. One per draft.
 *
 * <ul>
 *   <li>temperate, wisteria: rendered stone between dressed quoins, pointed arcades, steep gables, a polygonal apse,
 *   lancets of coloured glass, a slender turret on the ridge (the Cistercian manner) or a tower;</li>
 *   <li>alpine: whitewashed baroque, round arches, an onion tower over the west front;</li>
 *   <li>moorland, maritime: rubble and dressed stone, round arches, slate, a square east end; a round tower apart on the
 *   moor, a west tower with a pyramid by the sea;</li>
 *   <li>northern, tundra: log ranges, braced timber galleries, steep shingles, a stave belfry or an onion cupola;</li>
 *   <li>mediterranean: whitewash and terracotta, round arches, low roofs, a campanile;</li>
 *   <li>desert, mesa: adobe under flat roofs and parapets, pointed or round arches, a bell wall and a dome;</li>
 *   <li>steppe: earthen halls under flat roofs with pinnacles and timber stakes, posted galleries, an earthen tower;</li>
 *   <li>karst: halls of columns and lattice under curved roofs on a stone podium, covered galleries, a bell pavilion;</li>
 *   <li>bayou, tropical: the creole gallery house's boards and posted galleries, or the jungle shrine's mossy stone.</li>
 * </ul>
 */
public final class Rite {
	public final Tradition tradition;
	public final Culture culture;
	/** The walls of the ranges. */
	public final WallKind ranges;
	/** The cloister walk's arcade. */
	public final Arches.Kind arcade;
	/** The ranges' roofs (FLAT for the dry lands, PAGODA in the karst). */
	public final RoofForm roof;
	/** The ranges' pitch (blocks per block). */
	public final double pitch;
	/** The church ends in a polygonal apse (else a square end). */
	public final boolean apse;
	/** The church's windows are pointed lancets (else round-headed lights). */
	public final boolean lancets;
	/** Buttresses along the church. */
	public final boolean buttresses;
	/** What carries the church's bell. */
	public final Bell bell;

	public enum Bell {
		/** A slender turret on the ridge (Cistercian, the timber traditions). */
		RIDGE,
		/** A tower over the west front, capped in the culture's manner. */
		WEST,
		/** A campanile standing apart at a corner of the church. */
		CAMPANILE,
		/** A pierced bell wall over the west gable. */
		WALL,
		/** A tall round tower apart, under a cone (the Celtic moor). */
		ROUND,
		/** A bell pavilion in the court (the karst). */
		PAVILION
	}

	private Rite(Tradition t, Culture c, WallKind ranges, Arches.Kind arcade, RoofForm roof, double pitch, boolean apse, boolean lancets,
		boolean buttresses, Bell bell) {
		this.tradition = t;
		this.culture = c;
		this.ranges = ranges;
		this.arcade = arcade;
		this.roof = roof;
		this.pitch = pitch;
		this.apse = apse;
		this.lancets = lancets;
		this.buttresses = buttresses;
		this.bell = bell;
	}

	public static Rite of(Draft d) {
		Tradition t = Tradition.of(d);
		Culture c = d.style().culture();
		Habits h = d.habits();
		return switch (t) {
			case TIMBER -> new Rite(t, c, WallKind.STONE, Arches.Kind.POINTED, RoofForm.GABLE, 1.0, true, true, true, Bell.RIDGE);
			case ALPINE -> new Rite(t, c, WallKind.PLASTER, Arches.Kind.ROUND, RoofForm.GABLE, 1.0, true, false, false, Bell.WEST);
			case STONE -> new Rite(t, c, WallKind.STONE, Arches.Kind.ROUND, RoofForm.GABLE, 1.0, false, c != Culture.MOORLAND, true,
				c == Culture.MOORLAND ? Bell.ROUND : Bell.WEST);
			case LOG -> new Rite(t, c, c == Culture.TUNDRA ? WallKind.LOG : h.walls().kind() == WallKind.LOG ? WallKind.LOG : WallKind.PLANK,
				Arches.Kind.BRACED, RoofForm.GABLE, 1.0, false, false, false, Bell.RIDGE);
			case MEDITERRANEAN -> new Rite(t, c, WallKind.PLASTER, Arches.Kind.ROUND, RoofForm.GABLE, 0.5, true, false, false, Bell.CAMPANILE);
			case ADOBE -> new Rite(t, c, WallKind.ADOBE, c == Culture.MESA ? Arches.Kind.ROUND : Arches.Kind.POINTED, RoofForm.FLAT, 0.5, false, false,
				true, Bell.WALL);
			case STEPPE -> new Rite(t, c, WallKind.ADOBE, Arches.Kind.POSTED, RoofForm.FLAT, 0.5, false, false, true, Bell.WEST);
			case KARST -> new Rite(t, c, WallKind.PLASTER, Arches.Kind.BRACKETED, RoofForm.PAGODA, 1.0, false, false, false, Bell.PAVILION);
			case STILT -> c == Culture.TROPICAL
				? new Rite(t, c, WallKind.STONE, Arches.Kind.ROUND, RoofForm.HIP, 1.0, false, false, false, Bell.WEST)
				: new Rite(t, c, WallKind.PLANK, Arches.Kind.POSTED, RoofForm.HIP, 1.0, false, false, false, Bell.WEST);
		};
	}

	/** Flat roofs behind parapets. */
	public boolean flat() {
		return this.roof == RoofForm.FLAT;
	}

	/** Curved roofs with lifted corners. */
	public boolean curved() {
		return this.roof == RoofForm.PAGODA;
	}

	/** Builds in timber at heart (posts and boards, logs). */
	public boolean timber() {
		return this.ranges == WallKind.PLANK || this.ranges == WallKind.LOG;
	}
}
