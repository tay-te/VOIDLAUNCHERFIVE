package dev.voidmc.hearth.build.style;

import dev.voidmc.hearth.api.StageKind;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * What a block does in a building. A style says which materials play each role (2–4 of them, mixed by noise), and
 * components ask for roles, never for block ids, so one cottage generator builds a timber cottage in the temperate style,
 * a log cabin in the northern one and a whitewashed house in the mediterranean one. Each role has a default
 * {@link StageKind} for what it places, so a builder raises footings, then frame, then walls, then roof.
 */
public enum Role {
	/** Footings and foundation courses down to the ground; retaining walls. */
	FOUNDATION(StageKind.FOUNDATION),
	/** The base course of a wall above the ground (a stone sill under timber, a darker band under plaster). */
	PLINTH(StageKind.WALLS),
	/** The main wall: plaster, stone, brick, planks, logs. */
	WALL(StageKind.WALLS),
	/** Quoins, string courses, a second plaster colour, decorative bands. */
	WALL_ACCENT(StageKind.WALLS),
	/** Upright posts and corner posts (logs). */
	FRAME(StageKind.FRAME),
	/** Horizontal beams, wall plates, joists, brackets (logs, stripped logs). */
	BEAM(StageKind.FRAME),
	FLOOR(StageKind.FLOORS),
	/** Ceiling boards and the underside of upper floors. */
	CEILING(StageKind.FLOORS),
	/** The roof covering: stairs, slabs, full blocks. */
	ROOF(StageKind.ROOF),
	/** Ridge caps, barge boards, eave trim. */
	ROOF_TRIM(StageKind.ROOF),
	/** The fill of a gable end. */
	GABLE(StageKind.WALLS),
	/** Window frames, sills and lintels. */
	WINDOW(StageKind.OPENINGS),
	GLASS(StageKind.OPENINGS),
	DOOR(StageKind.OPENINGS),
	/** Shutters, lattices, flower-box boards (trapdoors). */
	SHUTTER(StageKind.OPENINGS),
	/** Yard fences and gates. */
	FENCE(StageKind.EXTERIOR),
	/** Balcony, stair and gallery railings. */
	RAILING(StageKind.EXTERIOR),
	/** Garden paths and yard tracks. */
	PATH(StageKind.EXTERIOR),
	/** Paved courts, terraces and thresholds. */
	PAVING(StageKind.EXTERIOR),
	/** Lanterns and torches. */
	LIGHT(StageKind.EXTERIOR),
	CHIMNEY(StageKind.ROOF),
	/** Interior joinery: tables, chairs, shelves, stairs, cupboards. */
	FURNITURE(StageKind.INTERIOR),
	/** The house's colours: beds, carpets, banners, awnings (dyes). */
	CLOTH(StageKind.INTERIOR),
	/** A decorative accent block (glazed tile, a carved stone, copper). */
	ACCENT(StageKind.EXTERIOR),
	/** Flowers and bushes for gardens and window boxes. */
	GARDEN(StageKind.EXTERIOR),
	/** What the kitchen garden grows. */
	CROPS(StageKind.EXTERIOR),
	/** Hedges and planted leaves. */
	HEDGE(StageKind.EXTERIOR),
	/** Logs of the local garden tree. */
	TREE_LOG(StageKind.EXTERIOR),
	/** Leaves of the local garden tree. */
	TREE_LEAVES(StageKind.EXTERIOR),
	/** The yard's surface (grass, sand, coarse dirt). */
	GROUND(StageKind.EARTHWORK),
	/** Earth fill under the yard surface. */
	FILL(StageKind.EARTHWORK),
	/** Stilts and piles over water or steep ground. */
	STILT(StageKind.FOUNDATION),
	/** Thatch for thatched roofs (hay, dried kelp...). */
	THATCH(StageKind.ROOF),
	/**
	 * The sheathing of domes, spires, onions and turret caps where the roof's own covering would not do (a wooden onion
	 * reads as a pinecone): patinated copper, lead-grey slate, glazed tile.
	 */
	SHEET(StageKind.ROOF),
	/**
	 * The culture's building stone for masonry works its houses may not be built of (a lighthouse, a bridge's piers, a
	 * cathedral in a timber town): never wood, so styles whose houses are all timber still say what their stone is.
	 */
	MASONRY(StageKind.WALLS);

	private final StageKind stage;

	Role(StageKind stage) {
		this.stage = stage;
	}

	/** The stage a component places this role in unless told otherwise. */
	public StageKind stage() {
		return this.stage;
	}

	/**
	 * How much a role's materials come in patches rather than block by block: walls weather in patches, roofs and
	 * floors are laid shingle by shingle and board by board (patches there read as streaks along the slope).
	 */
	public double patchiness() {
		return switch (this) {
			case ROOF, ROOF_TRIM, FLOOR, CEILING, PATH, PAVING, THATCH, GROUND, SHEET -> 0.15;
			default -> 0.6;
		};
	}

	/**
	 * Whether a role's materials are chosen once per building rather than mixed block by block: one house is roofed in
	 * spruce and the next in dark oak, but no roof is speckled with both. Walls, footings, paths and gardens mix.
	 */
	public boolean perBuilding() {
		return switch (this) {
			case ROOF, ROOF_TRIM, FRAME, BEAM, DOOR, SHUTTER, WINDOW, FENCE, RAILING, FURNITURE, CHIMNEY, STILT, LIGHT, FLOOR, CEILING, TREE_LOG,
				TREE_LEAVES, HEDGE, SHEET -> true;
			default -> false;
		};
	}

	/** The role's key in style JSON ({@code roof_trim}). */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable Role byKey(String key) {
		for (Role r : values()) {
			if (r.key().equals(key)) {
				return r;
			}
		}
		return null;
	}
}
