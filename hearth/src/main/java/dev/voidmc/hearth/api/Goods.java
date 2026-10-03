package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/**
 * The coarse kinds of goods a settlement stores, produces and builds with. The abstract simulation trades in these
 * units rather than in individual items, so a settlement nobody is near costs a few array operations to run.
 *
 * <p>Items map to goods through the item tags {@code #hearth:goods/<name>} (economy module data); blocks in a blueprint
 * map to goods through the build module's material bill. One unit is roughly one block or one stack-sixty-fourth of a
 * raw material — the exact exchange is each module's business, the unit is shared.
 */
public enum Goods implements StringRepresentable {
	FOOD("food"),
	WOOD("wood"),
	STONE("stone"),
	/** Clay, bricks, terracotta, mud. */
	CLAY("clay"),
	GLASS("glass"),
	/** Iron, copper and gold, as ore, ingots or nuggets. */
	METAL("metal"),
	/** Wool, carpet, leather, banners, beds. */
	CLOTH("cloth"),
	/** Coal and charcoal. */
	FUEL("fuel"),
	TOOLS("tools"),
	/** Books, candles, glazed things, dyes, spices: what a town wants once it has what it needs. */
	LUXURY("luxury");

	public static final StringRepresentable.EnumCodec<Goods> CODEC = StringRepresentable.fromEnum(Goods::values);
	public static final Goods[] VALUES = values();

	private final String name;

	Goods(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.goods." + this.name;
	}
}
