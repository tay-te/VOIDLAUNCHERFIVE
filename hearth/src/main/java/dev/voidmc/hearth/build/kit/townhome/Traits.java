package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;

/**
 * What a town house needs to know about its culture's way of building, read from the style's habits (with the
 * variant a building wears applied), so a variant that turns plaster into rubble stone or adds a thatch is honoured
 * without special cases. The culture is consulted only to tell apart traditions the habits describe alike.
 *
 * @param walls     the ground storey's wall kind
 * @param upper     the upper storeys' wall kind (the same unless the style boards its upper floors)
 * @param flat      the culture roofs with flat terraces more than with pitched roofs
 * @param flatSome  flat roofs are among its forms at all (a hill town's roof terraces)
 * @param terraces  flat roofs are lived on (stairs up, furniture, gardens)
 * @param eastern   curved, tiered tile roofs on posts and lattices (the karst tradition)
 * @param pueblo    adobe terraces with ladders and vigas (the mesa tradition)
 * @param timber    framed walls (posts and beams show)
 * @param masonry   stone, brick, plaster or adobe walls (string courses, quoins, parapets)
 * @param steep     a steep pitch (1 or more)
 * @param raised    houses stand raised on posts or a podium by habit (bayou, tropical)
 */
public record Traits(WallKind walls, WallKind upper, boolean flat, boolean flatSome, boolean terraces, boolean eastern, boolean pueblo,
	boolean timber, boolean masonry, boolean steep, boolean raised, boolean thatch, boolean lattice, boolean snow, Culture culture) {

	public static Traits of(Draft d) {
		Habits h = d.habits();
		WallKind k = h.walls().kind();
		WallKind up = h.walls().kind(1);
		double flatW = h.roof().weight(RoofForm.FLAT) + h.roof().weight(RoofForm.DOME);
		double pitched = 0;
		for (RoofForm f : RoofForm.values()) {
			if (f != RoofForm.FLAT && f != RoofForm.DOME) {
				pitched += h.roof().weight(f);
			}
		}
		Culture c = d.style().culture() == null ? Culture.TEMPERATE : d.style().culture();
		boolean eastern = h.roof().weight(RoofForm.PAGODA) > 0 || k == WallKind.BAMBOO;
		boolean flat = flatW > pitched;
		boolean pueblo = k == WallKind.ADOBE && flat && h.plan().terraces() && h.windows().arched() < 0.3;
		boolean timber = k == WallKind.TIMBER_FRAME || k == WallKind.BAMBOO || k == WallKind.PLANK || k == WallKind.LOG
			|| up == WallKind.TIMBER_FRAME || up == WallKind.PLANK;
		boolean masonry = k == WallKind.STONE || k == WallKind.BRICK || k == WallKind.PLASTER || k == WallKind.ADOBE;
		boolean raised = h.plan().stilts() == Habits.Stilts.ALWAYS;
		return new Traits(k, up, flat, flatW > 0, h.plan().terraces(), eastern, pueblo, timber, masonry, h.roof().pitch() >= 1.0, raised,
			h.roof().thatch(), h.windows().lattice(), h.snow(), c);
	}

	/** Upper storeys framed in timber (jetties suit them). */
	public boolean framedUpper() {
		return this.upper == WallKind.TIMBER_FRAME || this.upper == WallKind.PLANK || this.upper == WallKind.LOG;
	}

	/** Masonry upper storeys: string courses, cornices, parapets. */
	public boolean masonryUpper() {
		return this.upper == WallKind.STONE || this.upper == WallKind.BRICK || this.upper == WallKind.PLASTER || this.upper == WallKind.ADOBE;
	}

	/** Gables of stone or brick that can be raised above the roof as parapets and crow steps. */
	public boolean stoneGables() {
		return this.upper == WallKind.STONE || this.upper == WallKind.BRICK;
	}
}
