package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/**
 * The goods a settlement gathers, makes, stores, builds with, eats and trades. The abstract simulation trades in these
 * units rather than in individual items, so a settlement nobody is near costs a few array operations to run.
 *
 * <p>Phase 3 (docs/LIVING_WORLD.md §7.5) split the first ten coarse kinds into raw, processed and finished goods so that
 * production chains matter: logs become timber at the sawmill, ore and charcoal become metal at the smelter, clay becomes
 * brick at the kiln. The first ten constants keep their names and order (saved stockpiles index by ordinal); their
 * meaning narrowed to the processed form, and the new constants are appended after them.
 *
 * <p>Items map to goods through the item tags {@code #hearth:goods/<name>} (economy module data); blocks in a blueprint
 * map to goods through the build module's material bill (dressed stone and brick for masonry, timber for joinery, glass,
 * metal for lanterns and chains...). One unit is roughly one block or one item of the material.
 */
public enum Goods implements StringRepresentable {
	/** Prepared food: bread, cooked meat and fish, stews — what folk eat. */
	FOOD("food", Kind.FINISHED),
	/** Sawn timber: planks, beams, boards. (Raw logs are {@link #LOGS}.) */
	WOOD("wood", Kind.PROCESSED),
	/** Rough stone: cobble, rubble, field stone. (Cut stone is {@link #DRESSED_STONE}.) */
	STONE("stone", Kind.RAW),
	/** Raw clay and mud. (Fired brick and terracotta are {@link #BRICK}.) */
	CLAY("clay", Kind.RAW),
	GLASS("glass", Kind.PROCESSED),
	/** Worked metal: ingots, nails, chains, fittings. (Raw ore is {@link #ORE}.) */
	METAL("metal", Kind.PROCESSED),
	/** Woven cloth and finished textiles: carpets, banners, bedding. */
	CLOTH("cloth", Kind.PROCESSED),
	/** Coal and charcoal. */
	FUEL("fuel", Kind.PROCESSED),
	/** Tools; they wear out with use, which keeps the smith busy. */
	TOOLS("tools", Kind.FINISHED),
	/** Fine things: glazed ware, jewellery, spices, wine — what a town wants once it has what it needs. */
	LUXURY("luxury", Kind.FINISHED),
	// ---- phase 3: appended so saved ordinals of the ten above stay valid
	LOGS("logs", Kind.RAW),
	ORE("ore", Kind.RAW),
	DRESSED_STONE("dressed_stone", Kind.PROCESSED),
	/** Fired brick and terracotta, roof tiles. */
	BRICK("brick", Kind.PROCESSED),
	SAND("sand", Kind.RAW),
	/** Wheat and other grain, unmilled. */
	GRAIN("grain", Kind.RAW),
	FLOUR("flour", Kind.PROCESSED),
	/** Raw meat from herds and the hunt. */
	MEAT("meat", Kind.RAW),
	FISH("fish", Kind.RAW),
	WOOL("wool", Kind.RAW),
	LEATHER("leather", Kind.PROCESSED),
	/** Honeycomb and beeswax: candles, polish. */
	WAX("wax", Kind.RAW),
	/** Candles: what lights every interior (docs/LIVING_WORLD.md §1, §7.5). */
	CANDLES("candles", Kind.FINISHED),
	PAPER("paper", Kind.PROCESSED),
	/** Books, maps, ledgers. */
	BOOKS("books", Kind.FINISHED),
	DYE("dye", Kind.PROCESSED),
	SALT("salt", Kind.RAW),
	/** Fruit, vegetables, honey: fresh produce eaten or cooked. */
	PRODUCE("produce", Kind.RAW),
	/** A land's rarities: Expanse amber, prismite, sulfur, glowcaps, mammoth wool; bamboo, cocoa, kelp. */
	SPECIALTY("specialty", Kind.RAW);

	public static final StringRepresentable.EnumCodec<Goods> CODEC = StringRepresentable.fromEnum(Goods::values);
	public static final Goods[] VALUES = values();

	private final String name;
	private final Kind kind;

	Goods(String name, Kind kind) {
		this.name = name;
		this.kind = kind;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public Kind kind() {
		return this.kind;
	}

	public String translationKey() {
		return "hearth.goods." + this.name;
	}

	/** Where a good sits in the chains: gathered from the land, made in a workshop, or ready to use. */
	public enum Kind {
		RAW, PROCESSED, FINISHED
	}
}
