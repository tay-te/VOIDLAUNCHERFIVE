package dev.voidmc.hearth.npc.look;

import dev.voidmc.hearth.api.Culture;

/**
 * Colours for skin, hair, eyes and each culture's clothes. Cloth palettes are what make a karst village read as jade
 * and indigo and a desert town as linen and indigo from across the square, so each culture keeps to a few dyes it can
 * plausibly make, plus one accent and a trim.
 */
final class Palette {
	/** Light to dark. */
	static final int[] SKIN = {
		0xF6DCC8, 0xEFCBB0, 0xE7BC9B, 0xDCAA86, 0xCF9A74, 0xC08763, 0xAD7553, 0x9A6446, 0x85543A, 0x70452F, 0x5E3A28, 0x4C2E20
	};
	static final int[] HAIR = {
		0x1E1916, 0x2C221C, 0x3F2B20, 0x5A3D2A, 0x74462B, 0x7E3B24, 0xA4552C, 0x8F6D45, 0xC49A57, 0xDCC08A, 0xA79A88, 0xC47A4E
	};
	static final int[] EYES = {0x3B2416, 0x5C3A1E, 0x7A6230, 0x9A6A1E, 0x4E7A3A, 0x3F6EA8, 0x6E7C88};
	static final int GREY_HAIR = 0xB5B1AA;
	static final int WHITE_HAIR = 0xE4E1DA;

	/**
	 * A culture's dyes.
	 *
	 * @param cloth   everyday cloth colours (shirts, dresses, tunics)
	 * @param dark    trousers, skirts, coats
	 * @param accent  the colour the culture is known by (sashes, tabards, embroidery)
	 * @param trim    hems, buttons, embroidery thread
	 * @param leather belts, shoes, aprons
	 * @param pattern how cloth is decorated: see {@link Pattern}
	 */
	record Dyes(int[] cloth, int[] dark, int[] accent, int trim, int leather, Pattern pattern, Sleeves sleeves, Headwear[] headwear) {
	}

	enum Pattern {
		PLAIN,
		/** Breton stripes on shirts. */
		STRIPES,
		/** Embroidered band at hems and collars. */
		EMBROIDERY,
		/** Woven geometric band (steppe, mesa). */
		WOVEN,
		/** Little flowers on cotton. */
		FLORAL,
		/** A wide sash wrapped at the waist. */
		SASH,
		/** Tartan-like check. */
		CHECK
	}

	enum Sleeves {
		LONG, ROLLED, SHORT
	}

	enum Headwear {
		NONE, KERCHIEF, FUR_HAT, WOOL_CAP, WRAP, SCARF, STRAW, BAND, FELT_HAT
	}

	static Dyes dyes(Culture culture) {
		return switch (culture) {
			case TEMPERATE -> new Dyes(
				c(0xE4D7B6, 0xD9CBA4, 0x9DB07A, 0xB9C4CF, 0xE8E0CC), c(0x6B4E36, 0x5B6F86, 0x5E7A3E, 0x4F4339),
				c(0x8C4A2F, 0x5E7A3E, 0x3F5F86), 0xC9A86A, 0x6A4A30, Pattern.PLAIN, Sleeves.LONG,
				h(Headwear.NONE, Headwear.NONE, Headwear.KERCHIEF, Headwear.FELT_HAT));
			case WISTERIA -> new Dyes(
				c(0xF1E7D2, 0xE6D4E8, 0xD9C8EC, 0xE8C9D4, 0xDDE5D0), c(0x5B3F6E, 0x3C2E48, 0x6E5A8E, 0x4A4A5C),
				c(0x8E6CC0, 0xB490D8, 0xC98BAA), 0xE0CC8E, 0x4A3A30, Pattern.SASH, Sleeves.LONG,
				h(Headwear.NONE, Headwear.NONE, Headwear.BAND, Headwear.KERCHIEF));
			case NORTHERN -> new Dyes(
				c(0xCFC6B2, 0xB9B2A2, 0x9A968C, 0xC8B89A), c(0x4A4744, 0x5A4632, 0x3E5B3A, 0x3E4656),
				c(0x7A2E2A, 0x3E5B3A, 0x2E4A6E), 0xC0924E, 0x6E4E32, Pattern.EMBROIDERY, Sleeves.LONG,
				h(Headwear.NONE, Headwear.FUR_HAT, Headwear.WOOL_CAP, Headwear.NONE));
			case ALPINE -> new Dyes(
				c(0xF2EEE4, 0xEAE2D0, 0xE6D8C4, 0xD8E0E6), c(0x2C2A28, 0x4F6B47, 0x5A4632, 0x3E4A5E),
				c(0xB23A32, 0x4B6E9E, 0x4F6B47), 0xE6C86A, 0x6B4A2E, Pattern.EMBROIDERY, Sleeves.ROLLED,
				h(Headwear.NONE, Headwear.FELT_HAT, Headwear.NONE, Headwear.KERCHIEF));
			case TUNDRA -> new Dyes(
				c(0x2F4E7E, 0x3A5A8C, 0x8E8C88, 0xE8E2D6), c(0x2A3E62, 0x4A3A2E, 0x3A3A40),
				c(0xA8342C, 0xD8B24A, 0x2F4E7E), 0xD8B24A, 0x6A4C36, Pattern.EMBROIDERY, Sleeves.LONG,
				h(Headwear.FUR_HAT, Headwear.FUR_HAT, Headwear.WOOL_CAP, Headwear.NONE));
			case MOORLAND -> new Dyes(
				c(0xCFC2A0, 0xB8AE96, 0xC4B8C8, 0xA8B49A), c(0x6E5B44, 0x5E6670, 0x4E6644, 0x5A4A5E),
				c(0x7A5A7E, 0x4E6644, 0x9A5032), 0xC8A860, 0x5E4632, Pattern.CHECK, Sleeves.LONG,
				h(Headwear.NONE, Headwear.WOOL_CAP, Headwear.KERCHIEF, Headwear.NONE));
			case MARITIME -> new Dyes(
				c(0xEDEAE2, 0xE4E2DA, 0xCDBE9C, 0xD8DEE2), c(0x2E3F5E, 0x3E4A56, 0x4A4034),
				c(0x2E3F5E, 0x9E3A30, 0x3E6E6A), 0xD8C890, 0x5A4430, Pattern.STRIPES, Sleeves.LONG,
				h(Headwear.NONE, Headwear.WOOL_CAP, Headwear.NONE, Headwear.KERCHIEF));
			case TROPICAL -> new Dyes(
				c(0xF2EEE4, 0x7FD0C8, 0xF2C341, 0xF29A80, 0x9FD48A), c(0x2E78B8, 0x2BA6A0, 0x6A5236, 0xD8384A),
				c(0xE8735A, 0xD8384A, 0x2BA6A0, 0xF2C341), 0xFFF2C8, 0x7A5A3A, Pattern.FLORAL, Sleeves.SHORT,
				h(Headwear.NONE, Headwear.NONE, Headwear.BAND, Headwear.STRAW));
			case DESERT -> new Dyes(
				c(0xE8DCC2, 0xF0EBE0, 0xD8C8A8, 0xDCD2C0), c(0x2E3A6E, 0x8A6A44, 0x3A3E5A),
				c(0x2E3A6E, 0xD89A2A, 0xB4583A), 0xD4B04A, 0x8A5E36, Pattern.SASH, Sleeves.LONG,
				h(Headwear.WRAP, Headwear.WRAP, Headwear.SCARF, Headwear.NONE));
			case MEDITERRANEAN -> new Dyes(
				c(0xF2EFE8, 0xEAE4D6, 0xC8D8E8, 0xE8DEB8), c(0x2A2826, 0x7A7A44, 0x5E4A36, 0x3E5470),
				c(0xB8603E, 0x5E90C0, 0x7A7A44), 0xE8D25A, 0x7A5232, Pattern.PLAIN, Sleeves.ROLLED,
				h(Headwear.NONE, Headwear.NONE, Headwear.SCARF, Headwear.STRAW));
			case STEPPE -> new Dyes(
				c(0xE2D6BA, 0xC88A3A, 0xD8B888, 0x3E9A9A), c(0x4E3828, 0x962E2A, 0x2E4E5A),
				c(0x962E2A, 0x3E9A9A, 0xC88A3A), 0xD2A84A, 0x5E4230, Pattern.WOVEN, Sleeves.LONG,
				h(Headwear.FELT_HAT, Headwear.NONE, Headwear.SCARF, Headwear.FUR_HAT));
			case KARST -> new Dyes(
				c(0xEEEDE6, 0xA9C7A6, 0xD8E2D8, 0xC4D2E0), c(0x2E3E6A, 0x232323, 0x2E5A4A, 0x3A4A5E),
				c(0x3E8E72, 0xB23A2E, 0x2E3E6A), 0xD8BC6A, 0x4A3A2A, Pattern.SASH, Sleeves.LONG,
				h(Headwear.NONE, Headwear.NONE, Headwear.BAND, Headwear.STRAW));
			case BAYOU -> new Dyes(
				c(0xE2D8BE, 0xD8CFB4, 0xC2B48A, 0xB8C8D8), c(0x5A6E8A, 0x6A4E36, 0x5E6E3E, 0x4A4A44),
				c(0xA8473A, 0xC2A04A, 0x5E6E3E), 0xD8C49A, 0x6A4E36, Pattern.CHECK, Sleeves.ROLLED,
				h(Headwear.NONE, Headwear.STRAW, Headwear.KERCHIEF, Headwear.FELT_HAT));
			case MESA -> new Dyes(
				c(0xE8DEC6, 0xDCCDB0, 0x3AA6A0, 0xE0B88A), c(0x2B2724, 0x6A3E2A, 0x8A5A36),
				c(0xC8642E, 0x3AA6A0, 0xA8402E), 0xE8C060, 0x7A4E30, Pattern.WOVEN, Sleeves.ROLLED,
				h(Headwear.NONE, Headwear.BAND, Headwear.FELT_HAT, Headwear.NONE));
		};
	}

	private static int[] c(int... colours) {
		return colours;
	}

	private static Headwear[] h(Headwear... options) {
		return options;
	}

	private Palette() {
	}
}
