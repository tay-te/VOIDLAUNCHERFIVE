package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;

/**
 * How a culture fortifies and crowns its lords' seats: the forms the style JSON has no habit for. Materials still come
 * from the style (so a data pack's style dresses these buildings in its own stone); this says whether towers stand round
 * or square, what caps them, how the parapets are cut, whether walls are rendered, carry timber galleries, step their
 * gables or hang turrets at their corners, and how steep the halls are roofed.
 *
 * <ul>
 *   <li><b>temperate</b> (Bohemia, the user's KCD II look): rendered stone, round and square towers under tall cones and
 *   pyramids of tile or shingle, timber hoardings on the tower heads, a round bergfried, steep palace roofs.</li>
 *   <li><b>alpine</b> (Tyrol): white render, slender square towers under needle-steep pyramids, corner oriels, onion
 *   on the keep.</li>
 *   <li><b>moorland</b> (Scotland): harled walls, crow-stepped gables, corbelled bartizans with conical caps.</li>
 *   <li><b>maritime</b> (Brittany, Cornwall): bare granite, round towers with machicolated open tops, slate.</li>
 *   <li><b>northern</b>, <b>tundra</b>: fieldstone walls carrying timber: hoarded tower heads under shingle, a timber
 *   great hall; no merlons, a timber breastwork.</li>
 *   <li><b>wisteria</b> (Alsace): rendered château, round towers under tall cones, bartizans.</li>
 *   <li><b>mediterranean</b>: square towers, swallowtail (Ghibelline) merlons, low tile roofs.</li>
 *   <li><b>desert</b>, <b>mesa</b>: flat roofs, open crenellated towers with pointed merlons, slot windows.</li>
 *   <li><b>steppe</b>: the ordu: a round ward, round towers and halls under cones, pointed earthen merlons.</li>
 *   <li><b>karst</b>: square towers under curved hip roofs on posts, plain parapets, lattice windows.</li>
 *   <li><b>tropical</b>: coral-stone forts with domed sentry turrets (garitas) at the corners.</li>
 *   <li><b>bayou</b>: brick and timber forts, square blockhouse towers under hipped roofs, galleries.</li>
 * </ul>
 *
 * @param round       share of towers built round
 * @param cap         how ordinary towers are capped
 * @param keepCap     how the keep (the bergfried, the donjon) is crowned
 * @param merlon      how parapets are cut
 * @param hoardings   chance a tower head carries a timber gallery under its cap
 * @param machicolate parapets corbelled out a block (on towers, and on curtains where the plot allows)
 * @param timberTops  tower heads and the hall built in timber over stone
 * @param crowSteps   gables stepped (the moor's corbie steps)
 * @param bartizans   corbelled corner turrets on square towers and the keep
 * @param pitch       the halls' and palace's roof pitch (0: flat roofs behind a parapet)
 * @param roundPlan   wards laid out round (the steppe's ordu)
 * @param window      how the halls are lit
 */
public record Manner(Culture culture, double round, Cap cap, Cap keepCap, Merlon merlon, double hoardings, boolean machicolate, boolean timberTops,
	boolean crowSteps, boolean bartizans, double pitch, boolean roundPlan, Window window) {

	/** How a tower is finished at the top. */
	public enum Cap {
		/** A tall cone (round) or steep pyramid (square) straight off the parapet line, on a ring of eaves. */
		CONE,
		/** A tent roof over the tower: a pyramid of the roof covering, as steep as the culture likes. */
		PYRAMID,
		/** A timber gallery corbelled out on beams over the masonry, slits in its boards, under a steep cap. */
		HOARD,
		/** An open battlemented deck: crenellations, a flag. */
		CRENEL,
		/** An onion dome on a drum (alpine, the keep). */
		ONION,
		/** A curved hip roof on posts over an open lookout (karst). */
		PAGODA,
		/** A small dome over the deck (the tropics' garitas, the desert). */
		DOME
	}

	/** How a parapet is cut. */
	public enum Merlon {
		/** Square merlons a block wide, crenels between. */
		SQUARE,
		/** Swallowtail (Ghibelline) merlons, two wide with a notch. */
		SWALLOW,
		/** A merlon with a post on it: pointed, as the earthen and Moorish parapets are. */
		POINTED,
		/** A plain parapet without crenels (karst, a timber breastwork). */
		PLAIN,
		/** A timber breastwork of logs and rails. */
		TIMBER
	}

	/** How the halls' windows are made. */
	public enum Window {
		/** Square-headed, two lights parted by a stone mullion. */
		MULLION,
		/** Pointed, tall (the Gothic hall). */
		LANCET,
		/** Round-headed. */
		ARCHED,
		/** Tall narrow slots. */
		SLOT,
		/** A wooden lattice. */
		LATTICE
	}

	public static Manner of(Draft d) {
		Culture c = d.style().culture();
		return of(c == null ? Culture.TEMPERATE : c);
	}

	public static Manner of(Culture c) {
		return switch (c) {
			case TEMPERATE -> new Manner(c, 0.55, Cap.CONE, Cap.HOARD, Merlon.SQUARE, 0.45, true, false, false, false, 1.25, false, Window.MULLION);
			case ALPINE -> new Manner(c, 0.25, Cap.PYRAMID, Cap.ONION, Merlon.SQUARE, 0.2, false, false, false, true, 1.5, false, Window.MULLION);
			case MOORLAND -> new Manner(c, 0.45, Cap.CONE, Cap.CRENEL, Merlon.SQUARE, 0, false, false, true, true, 1.25, false, Window.ARCHED);
			case MARITIME -> new Manner(c, 0.75, Cap.CRENEL, Cap.CRENEL, Merlon.SQUARE, 0, true, false, false, false, 1.0, false, Window.ARCHED);
			case NORTHERN -> new Manner(c, 0.3, Cap.HOARD, Cap.HOARD, Merlon.TIMBER, 1.0, false, true, false, false, 1.5, false, Window.SLOT);
			case TUNDRA -> new Manner(c, 0.25, Cap.HOARD, Cap.HOARD, Merlon.TIMBER, 1.0, false, true, false, false, 1.25, false, Window.SLOT);
			case WISTERIA -> new Manner(c, 0.8, Cap.CONE, Cap.CONE, Merlon.SQUARE, 0.15, true, false, false, true, 1.25, false, Window.MULLION);
			case MEDITERRANEAN -> new Manner(c, 0.2, Cap.CRENEL, Cap.CRENEL, Merlon.SWALLOW, 0, true, false, false, false, 0.5, false, Window.ARCHED);
			case DESERT -> new Manner(c, 0.6, Cap.CRENEL, Cap.CRENEL, Merlon.POINTED, 0, false, false, false, false, 0, false, Window.SLOT);
			case MESA -> new Manner(c, 0.5, Cap.CRENEL, Cap.CRENEL, Merlon.PLAIN, 0, false, false, false, false, 0, false, Window.SLOT);
			case STEPPE -> new Manner(c, 1.0, Cap.CONE, Cap.CONE, Merlon.POINTED, 0, false, false, false, false, 1.0, true, Window.SLOT);
			case KARST -> new Manner(c, 0.0, Cap.PAGODA, Cap.PAGODA, Merlon.PLAIN, 0, false, false, false, false, 1.0, false, Window.LATTICE);
			case TROPICAL -> new Manner(c, 0.4, Cap.DOME, Cap.PYRAMID, Merlon.SQUARE, 0, false, false, false, true, 1.0, false, Window.ARCHED);
			case BAYOU -> new Manner(c, 0.0, Cap.PYRAMID, Cap.PYRAMID, Merlon.PLAIN, 0, false, false, false, false, 1.0, false, Window.MULLION);
		};
	}

	/** Whether the halls stand under flat roofs behind parapets. */
	public boolean flat() {
		return this.pitch <= 0.01;
	}
}
