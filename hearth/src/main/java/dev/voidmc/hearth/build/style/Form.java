package dev.voidmc.hearth.build.style;

/**
 * The cut of a material: which member of a block family a component asks for. A {@link Material} knows, for each form,
 * the block that cuts it (spruce: planks, stairs, slab, log, fence, door...), and a style role answers in any form its
 * materials have.
 */
public enum Form {
	/** The plain full block (planks for a wood, the block itself for a stone, wool for a dye). */
	FULL,
	STAIRS,
	SLAB,
	/** A wall post block (cobblestone wall, brick wall). */
	WALL,
	FENCE,
	FENCE_GATE,
	DOOR,
	TRAPDOOR,
	PRESSURE_PLATE,
	BUTTON,
	/** A log with bark ends: posts, beams. */
	LOG,
	/** Bark on all six sides. */
	WOOD,
	STRIPPED_LOG,
	STRIPPED_WOOD,
	PLANKS,
	SIGN,
	WALL_SIGN,
	HANGING_SIGN,
	WALL_HANGING_SIGN,
	SHELF,
	/** Glass pane (of a glass) or the pane of a dye's stained glass. */
	PANE,
	/** Glass block (of a glass) or a dye's stained glass. */
	GLASS,
	CARPET,
	BED,
	BANNER,
	WALL_BANNER,
	CANDLE,
	TERRACOTTA,
	CONCRETE
}
