package dev.voidmc.atlas.core;

/**
 * What a map pixel is made of, for the palette: each kind has an <em>anchor</em> (the colour the design gives it,
 * on the dark UI), the colour a typical vanilla block of the kind has (its <em>reference</em>), and how strongly the
 * relief shows on it. A block's own colour moves its pixel away from the anchor only by how much it differs from the
 * reference, scaled down, so a savanna's grass is a little yellower than a plain's and red sand a little warmer than
 * sand, but everything stays inside the muted palette.
 */
public enum Material {
	/** Nothing known: transparent on the map. */
	NONE(0x0E0F11, 0x000000, 0, 0, 0),
	WATER(0x16202B, 0x3F76E4, 0, 0, 0),
	LAVA(0x5C3420, 0xCF5B13, 0.10F, 0, 0),
	GRASS(0x1F261F, 0x6E9C47, 0.30F, 0.016F, 0.005F),
	FOLIAGE(0x19211B, 0x4C7A2E, 0.28F, 0.018F, 0.004F),
	PLANT(0x212820, 0x6E9C47, 0.30F, 0.016F, 0.005F),
	SAND(0x2C2A25, 0xDBD3A0, 0.36F, 0.018F, 0.006F),
	GRAVEL(0x2B2B2C, 0x837E7C, 0.40F, 0.020F, 0.013F),
	STONE(0x292B2F, 0x7E7E7E, 0.42F, 0.022F, 0.0125F),
	SNOW(0x575B61, 0xF0FAFA, 0.20F, 0.020F, 0.005F),
	ICE(0x34404B, 0x91B7FD, 0.30F, 0.016F, 0.008F),
	DIRT(0x2A2620, 0x866043, 0.38F, 0.022F, 0.008F),
	MUD(0x221F1C, 0x3C3A3D, 0.36F, 0.020F, 0.006F),
	CLAY(0x2C2F33, 0xA0A6B3, 0.40F, 0.020F, 0.008F),
	TERRACOTTA(0x33271F, 0x985E43, 0.46F, 0.024F, 0.014F),
	WOOD(0x37322C, 0xA2824E, 0.42F, 0.026F, 0.004F),
	BUILT(0x34353A, 0x7A7A7A, 0.46F, 0.026F, 0.004F),
	NETHER(0x35221F, 0x6F3634, 0.42F, 0.024F, 0.010F),
	NYLIUM(0x30211F, 0x8B1F1F, 0.46F, 0.022F, 0.008F),
	BASALT(0x2A2A2E, 0x505055, 0.40F, 0.024F, 0.010F),
	SOUL(0x2B2520, 0x514032, 0.40F, 0.022F, 0.008F),
	ENDSTONE(0x3F3E35, 0xDBDE9E, 0.36F, 0.022F, 0.008F),
	OTHER(0x2E3033, 0x808080, 0.42F, 0.022F, 0.008F);

	/** The anchor, 0xRRGGBB. */
	public final int anchor;
	/** The colour a typical vanilla block of the kind has, 0xRRGGBB. */
	public final int reference;
	/** How much of the block's difference from the reference shows (OKLab, lightness and hue alike). */
	public final float variation;
	/** OKLab lightness per hillshade step. */
	public final float shade;
	/** OKLab lightness per elevation band. */
	public final float band;

	final float[] anchorLab;
	final float[] referenceLab;

	Material(int anchor, int reference, float variation, float shade, float band) {
		this.anchor = anchor;
		this.reference = reference;
		this.variation = variation;
		this.shade = shade;
		this.band = band;
		this.anchorLab = Oklab.fromRgb(anchor);
		this.referenceLab = Oklab.fromRgb(reference);
	}

	private static final Material[] VALUES = values();

	public static Material byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : OTHER;
	}

	public boolean isFluid() {
		return this == WATER || this == LAVA;
	}
}
