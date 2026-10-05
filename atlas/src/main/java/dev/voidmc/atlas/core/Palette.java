package dev.voidmc.atlas.core;

/**
 * The map's look: how a column's raw colour, height and depth become one muted pixel that sits on the dark UI.
 *
 * <ul>
 *   <li><b>Tone.</b> Every block colour is pulled to its material's anchor ({@link Material}) in OKLab: the pixel is the
 *   anchor, moved by a fraction of how far the block's own colour is from a typical block of its kind. Chroma is
 *   capped, so nothing on the map is ever loud.</li>
 *   <li><b>Relief.</b> A flat-stepped hillshade lit from the north-west: the height difference to the north-west
 *   neighbour, quantised to five steps, each a fixed step of lightness. It reads as crafted pixel art, not noise.</li>
 *   <li><b>Elevation.</b> Stone, gravel and snow brighten in bands of {@value #BAND_HEIGHT} blocks above sea level,
 *   so mountains are grey stepped terraces up to their snow caps.</li>
 *   <li><b>Water.</b> Depth in six steps from a lighter shallows band to deep teal-navy, plus a one-pixel shoreline
 *   where water meets land; the biome's water colour only tints it.</li>
 *   <li><b>Edges.</b> Unexplored land is transparent (the map's well shows through); the explored edge is softened
 *   by an ordered dither over two pixels, at every zoom level, never by a gradient.</li>
 * </ul>
 */
public final class Palette {
	/**
	 * The palette's version: images on disk drawn with another are drawn again from their raw regions. It hashes the
	 * tables below and the materials' anchors; bump {@link #CODE} when the drawing itself changes.
	 */
	private static final int CODE = 3;
	public static final int SEA_LEVEL = 63;
	public static final int BAND_HEIGHT = 10;
	public static final int MAX_BANDS = 12;
	/** The most colourful a map pixel may be (OKLab chroma). */
	public static final float MAX_CHROMA = 0.052F;
	/** Rock with no air in the slice (underground and Nether layers). */
	public static final int ROCK = 0x141517;
	/** What unexplored land shows: the map's well. */
	public static final int UNEXPLORED = 0x0E0F11;

	private static final int[] WATER = {0x1C2834, 0x19232E, 0x162029, 0x141C25, 0x12191F, 0x10151C};
	private static final int SHORE = 0x1F2C38;
	private static final float[][] WATER_LAB = new float[WATER.length][];
	private static final float[] SHORE_LAB = Oklab.fromRgb(SHORE);
	private static final float[] WATER_REF = Oklab.fromRgb(0x3F76E4);

	static {
		for (int i = 0; i < WATER.length; i++) {
			WATER_LAB[i] = Oklab.fromRgb(WATER[i]);
		}
	}

	public static final int VERSION = version();

	private static int version() {
		int h = CODE;
		for (Material m : Material.values()) {
			h = h * 31 + m.anchor;
			h = h * 31 + m.reference;
			h = h * 31 + Float.floatToIntBits(m.variation);
			h = h * 31 + Float.floatToIntBits(m.shade);
			h = h * 31 + Float.floatToIntBits(m.band);
		}
		for (int w : WATER) {
			h = h * 31 + w;
		}
		h = h * 31 + SHORE;
		h = h * 31 + ROCK;
		h = h * 31 + BAND_HEIGHT;
		h = h * 31 + Float.floatToIntBits(MAX_CHROMA);
		return h;
	}

	/** A 4 x 4 Bayer matrix, thresholds 0..15. */
	private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

	private Palette() {
	}

	/**
	 * A block's colour toned into the palette, OKLab {L, a, b}: its material's anchor, moved by
	 * {@link Material#variation} of the block's difference from the material's reference colour.
	 */
	public static float[] tone(Material material, int rawRgb) {
		float[] raw = Oklab.fromRgb(rawRgb);
		float[] out = new float[3];
		float v = material.variation;
		if (material == Material.OTHER) {
			// no anchor that fits: keep the block's hue, pressed into the palette's range of lightness
			out[0] = 0.20F + raw[0] * 0.22F;
			out[1] = raw[1] * 0.30F;
			out[2] = raw[2] * 0.30F;
		} else {
			out[0] = material.anchorLab[0] + (raw[0] - material.referenceLab[0]) * v * 0.55F;
			out[1] = material.anchorLab[1] + (raw[1] - material.referenceLab[1]) * v;
			out[2] = material.anchorLab[2] + (raw[2] - material.referenceLab[2]) * v;
		}
		clampChroma(out, MAX_CHROMA);
		out[0] = Math.max(0.12F, Math.min(0.68F, out[0]));
		return out;
	}

	/** The hillshade step for a height difference to the north-west neighbour: -2..2. */
	public static int shadeStep(int dh) {
		if (dh >= 4) {
			return 2;
		}
		if (dh >= 1) {
			return 1;
		}
		if (dh == 0) {
			return 0;
		}
		if (dh >= -3) {
			return -1;
		}
		return -2;
	}

	/** The elevation band of a height: 0 at and below sea level, one more every {@value #BAND_HEIGHT} blocks. */
	public static int band(int height) {
		if (height <= SEA_LEVEL) {
			return 0;
		}
		return Math.min(MAX_BANDS, (height - SEA_LEVEL) / BAND_HEIGHT);
	}

	/** A land pixel: the toned colour, lit by its hillshade step and its elevation band. */
	public static int land(float[] toned, Material material, int shadeStep, int band) {
		float L = toned[0] + shadeStep * material.shade + band * material.band;
		return Oklab.toRgb(Math.max(0.08F, Math.min(0.80F, L)), toned[1], toned[2]);
	}

	/** The water depth step: 0 (one block deep) to 5 (twenty and deeper). */
	public static int depthStep(int depth) {
		if (depth <= 1) {
			return 0;
		}
		if (depth <= 3) {
			return 1;
		}
		if (depth <= 6) {
			return 2;
		}
		if (depth <= 11) {
			return 3;
		}
		if (depth <= 19) {
			return 4;
		}
		return 5;
	}

	/**
	 * A water pixel: its depth step, or the shoreline where it touches land, faintly tinted towards the biome's own
	 * water colour (swamps a little greener, warm seas a little more teal).
	 */
	public static int water(int depth, boolean shore, int biomeWaterRgb) {
		float[] base = shore ? SHORE_LAB : WATER_LAB[depthStep(depth)];
		float[] biome = Oklab.fromRgb(biomeWaterRgb);
		float a = base[1] + (biome[1] - WATER_REF[1]) * 0.10F;
		float b = base[2] + (biome[2] - WATER_REF[2]) * 0.10F;
		float[] out = {base[0], a, b};
		clampChroma(out, 0.045F);
		return Oklab.toRgb(out);
	}

	/** A cave floor (underground and Nether layers): darker than the surface, so the slice reads as below ground. */
	public static int caveFloor(float[] toned, Material material, int shadeStep) {
		float L = toned[0] * 0.92F + 0.02F + shadeStep * material.shade * 1.2F;
		return Oklab.toRgb(Math.max(0.08F, Math.min(0.7F, L)), toned[1] * 0.9F, toned[2] * 0.9F);
	}

	/**
	 * How much of a known pixel shows, 0..255, by its distance (Chebyshev, in pixels) to the nearest unknown one:
	 * the two-pixel dithered edge of the explored land.
	 */
	public static int coverage(int distanceToUnknown) {
		if (distanceToUnknown <= 0) {
			return 0;
		}
		if (distanceToUnknown == 1) {
			return 128;
		}
		if (distanceToUnknown == 2) {
			return 192;
		}
		return 255;
	}

	/**
	 * Whether a pixel with this coverage shows at global pixel {@code (gx, gz)}: an ordered (Bayer) dither, so half
	 * coverage is a checkerboard and the pattern lines up across tiles.
	 */
	public static boolean shows(int coverage, int gx, int gz) {
		if (coverage >= 255) {
			return true;
		}
		if (coverage <= 0) {
			return false;
		}
		int threshold = BAYER[(gz & 3) << 2 | (gx & 3)];
		return coverage * 16 > threshold * 255 + 127;
	}

	private static void clampChroma(float[] lab, float max) {
		float c = (float) Math.hypot(lab[1], lab[2]);
		if (c > max) {
			float k = max / c;
			lab[1] *= k;
			lab[2] *= k;
		}
	}
}
