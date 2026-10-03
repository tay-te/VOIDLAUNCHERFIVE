package dev.voidmc.hearth.build.kit.infra;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import org.jspecify.annotations.Nullable;

/**
 * How a road is surfaced in a culture: the course it is laid in, what wears through it, how its edges are finished,
 * what its steps are cut from, whether its carriageway is sunk between the kerbs, and whether it is lit.
 *
 * <p>Built from the style's roles (paving, path, foundation, plinth, wall accent, ground, frame, fence), so a data pack
 * restyling a culture restyles its roads; which role plays which part per culture and kind is the one thing kept here,
 * because the styles have no road habits yet (see docs/requests/arch-infra.md). Kerbs are always of a different stone
 * than the course they edge, so an edge reads.
 *
 * @param main      the body of the surface (full blocks)
 * @param wear      patches mixed into the body by world noise (worn cobbles in a paved street, setts in a dirt lane)
 * @param wearShare 0–1, how much of the body the patches take
 * @param edge      the side-edge columns' surface
 * @param edgeKind  how the side edges read
 * @param step      what half steps and stair treads are cut from where the course itself cuts none
 * @param sunken    how much of the course lies half a block below the edges on level stretches: 0 none, 1 the
 *                  centre line (a kennel), 2 everything inside the kerbs (a carriageway)
 * @param ruts      a farm track: wheel ruts on the edges, grass up the middle
 * @param lamps     lamp posts on the kerb line (roads five or more wide only)
 * @param spacing   blocks between lamps along the road (alternating sides)
 */
public record Dress(LineMat main, @Nullable LineMat wear, double wearShare, LineMat edge, EdgeKind edgeKind, LineMat step, int sunken,
	boolean ruts, boolean lamps, int spacing) {

	public enum EdgeKind {
		/** The edge in the body's own course. */
		SAME,
		/** Dressed kerb stones, flush with the road. */
		KERB,
		/** Logs laid along the edge (the northern "mud and wood" roads, bayou boardwalks, bamboo-edged karst lanes). */
		LOG,
		/** A grass verge with flowers and tufts (country roads). */
		VERGE
	}

	/** The road kinds of the planner's {@code kind} param. */
	public enum Kind {
		HIGHWAY, STREET, LANE, ALLEY, PATH;

		public static Kind of(String name) {
			return switch (name) {
				case "highway" -> HIGHWAY;
				case "street" -> STREET;
				case "alley" -> ALLEY;
				case "path" -> PATH;
				default -> LANE;
			};
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** The planner's default width for the kind. */
		public int width() {
			return switch (this) {
				case HIGHWAY, STREET -> 5;
				case PATH -> 1;
				default -> 3;
			};
		}
	}

	/** Whether a culture builds in timber where others pave (boardwalks over the mud, palm decks). */
	public static boolean timberCulture(@Nullable Culture c) {
		return c == Culture.BAYOU || c == Culture.TROPICAL;
	}

	/** Whether a culture edges its lanes with laid logs or canes. */
	private static boolean logEdged(@Nullable Culture c) {
		return c == Culture.NORTHERN || c == Culture.TUNDRA || c == Culture.KARST || c == Culture.BAYOU || c == Culture.TROPICAL;
	}

	/** Stone-built towns whose lanes are paved or rubble even in a village. */
	private static boolean stoneTown(@Nullable Culture c) {
		return c == Culture.MEDITERRANEAN || c == Culture.MOORLAND || c == Culture.MARITIME || c == Culture.DESERT || c == Culture.MESA;
	}

	/** The first candidate whose main block differs from the body's (a kerb must read against its road). */
	static LineMat contrast(LineMat body, LineMat... candidates) {
		for (LineMat m : candidates) {
			if (!m.mainName().equals(body.mainName())) {
				return m;
			}
		}
		return candidates[candidates.length - 1];
	}

	/**
	 * A stair street: treads of the culture's paving stone (cobble or rubble where the paving cuts no stairs, planks in
	 * the timber cultures), cheeks of dressed stone (or laid logs) up the edges, lamps on the landings of wide flights.
	 */
	public static Dress stairs(Lay lay, int width) {
		Culture c = lay.draft().style().culture();
		int tier = lay.draft().tier();
		LineMat paving = LineMat.of(lay, Role.PAVING).weathered(0.1);
		LineMat found = LineMat.of(lay, Role.FOUNDATION).weathered(0.2);
		LineMat plinth = LineMat.of(lay, Role.PLINTH).weathered(0.1);
		LineMat accent = LineMat.of(lay, Role.WALL_ACCENT).weathered(0.05);
		LineMat frame = LineMat.of(lay, Role.FRAME);
		if (timberCulture(c) && paving.timber()) {
			return new Dress(paving, null, 0, frame, EdgeKind.LOG, paving, 0, false, width >= 5 && tier >= 1, 8);
		}
		LineMat tread = paving.has(Form.STAIRS) && (tier >= 2 || !found.has(Form.STAIRS)) ? paving : found;
		LineMat cheek = c == Culture.MEDITERRANEAN || c == Culture.STEPPE ? contrast(tread, accent, plinth, found, paving)
			: contrast(tread, plinth, accent, paving, found);
		if (!cheek.has(Form.STAIRS)) {
			cheek = tread;
		}
		return new Dress(tread, tread == found ? null : found, 0.15, cheek, EdgeKind.KERB, tread, 0, false, width >= 5 && tier >= 1, 8);
	}

	public static Dress of(Lay lay, Kind kind, int width) {
		Culture c = lay.draft().style().culture();
		int tier = lay.draft().tier();
		// walked surfaces are kept clean of the moss a wall foot gathers
		LineMat paving = LineMat.of(lay, Role.PAVING).weathered(0.1);
		LineMat path = LineMat.of(lay, Role.PATH).weathered(0.1);
		LineMat found = LineMat.of(lay, Role.FOUNDATION).weathered(0.15);
		LineMat plinth = LineMat.of(lay, Role.PLINTH).weathered(0.1);
		LineMat accent = LineMat.of(lay, Role.WALL_ACCENT).weathered(0.05);
		LineMat ground = LineMat.of(lay, Role.GROUND);
		// the edge logs: canes for the bamboo cultures (their fence role), the frame timber otherwise
		LineMat fence = LineMat.of(lay, Role.FENCE);
		LineMat logs = fence.has(Form.LOG) && c == Culture.KARST ? fence : LineMat.of(lay, Role.FRAME);
		boolean timber = timberCulture(c) && paving.timber();
		boolean wide = width >= 5;
		// the street's setts: rubble where the foundation is cobbled, the paving otherwise; dressed kerbs against them
		// (the setts in one stone: a street of mixed rubble and moss reads as a ruin; wear patches bring the variety)
		LineMat setts = found.mainName().contains("cobble") && found.has(Form.SLAB) ? found.top(1) : paving.top(2);
		LineMat kerb = c == Culture.MEDITERRANEAN || c == Culture.STEPPE ? contrast(setts, accent, plinth, paving, found)
			: contrast(setts, plinth, paving, accent, found);
		// steps are cut from one stone, so a climbing road reads as built
		LineMat stepPaved = (setts.has(Form.STAIRS) ? setts : found).top(1);
		LineMat stepStone = (found.has(Form.STAIRS) ? found : plinth).top(1);
		return switch (kind) {
			case STREET -> {
				if (timber) {
					yield new Dress(paving, null, 0, logs, EdgeKind.LOG, stepPaved, 0, false, wide, tier >= 3 ? 10 : tier == 0 ? 20 : 14);
				}
				boolean logEdge = c == Culture.NORTHERN || c == Culture.TUNDRA;
				LineMat wear = setts == found ? paving : found;
				int sunk = tier >= 1 && setts.has(Form.SLAB) ? (wide ? 2 : 1) : 0;
				// a main street is laid out at the founding and lit from the start (the planner does not repave it later)
				yield new Dress(setts, tier <= 2 ? wear : null, tier <= 1 ? 0.2 : 0.1, logEdge ? logs : kerb, logEdge ? EdgeKind.LOG : EdgeKind.KERB,
					stepPaved, sunk, false, wide, tier >= 3 ? 10 : tier == 0 ? 20 : 14);
			}
			case HIGHWAY -> {
				if (tier >= 3 && !timber) {
					yield new Dress(setts, paving == setts ? found : paving, 0.2, ground, EdgeKind.VERGE, stepPaved, 0, false, wide, 24);
				}
				yield new Dress(path, found, tier >= 2 ? 0.3 : 0.12, ground, EdgeKind.VERGE, stepStone, 0, false, wide && tier >= 2, 28);
			}
			case ALLEY -> {
				if (timber) {
					yield new Dress(paving, null, 0, paving, EdgeKind.SAME, stepPaved, 0, false, false, 0);
				}
				LineMat body = found.has(Form.SLAB) ? found : setts;
				yield new Dress(body, paving, 0.25, body, EdgeKind.SAME, stepStone, body.has(Form.SLAB) ? 1 : 0, false, false, 0);
			}
			case PATH -> {
				if (width >= 3) {
					// a farm track: ruts and a grass crown
					yield new Dress(ground, null, 0, path, EdgeKind.SAME, stepStone, 0, true, false, 0);
				}
				if (timber && tier >= 1) {
					yield new Dress(paving, null, 0, paving, EdgeKind.SAME, stepPaved, 0, false, false, 0);
				}
				yield new Dress(path, tier >= 2 ? paving : null, 0.2, path, EdgeKind.SAME, stepStone, 0, false, false, 0);
			}
			case LANE -> {
				if (timber) {
					yield tier >= 1 ? new Dress(paving, null, 0, logs, EdgeKind.LOG, stepPaved, 0, false, false, 0)
						: new Dress(path, null, 0, logs, EdgeKind.LOG, stepStone, 0, false, false, 0);
				}
				boolean logEdge = logEdged(c);
				if (tier >= 3 || stoneTown(c) && tier >= 1) {
					LineMat body = stoneTown(c) && tier <= 2 ? found : setts;
					LineMat edge = logEdge ? logs : tier >= 3 ? kerb : body;
					EdgeKind ek = logEdge ? EdgeKind.LOG : tier >= 3 ? EdgeKind.KERB : EdgeKind.SAME;
					yield new Dress(body, body == found ? paving : found, 0.2, edge, ek, stepPaved, tier >= 3 && body.has(Form.SLAB) ? 1 : 0, false, false, 0);
				}
				if (tier == 2) {
					yield new Dress(path, found, 0.3, logEdge ? logs : path, logEdge ? EdgeKind.LOG : EdgeKind.SAME, stepStone, 0, false, false, 0);
				}
				yield new Dress(path, null, 0, logEdge ? logs : path, logEdge ? EdgeKind.LOG : EdgeKind.SAME, stepStone, 0, false, false, 0);
			}
		};
	}
}
