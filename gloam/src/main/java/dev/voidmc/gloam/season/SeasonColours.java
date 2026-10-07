package dev.voidmc.gloam.season;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Noise;

/**
 * What grass and leaves look like at one point of the year, drawn from small hand-picked palettes (docs/STYLE.md: clean,
 * slightly saturated colours like Minecraft's own textures, chosen, never blended freely). One of these is made per
 * season step ({@link Seasons}) and read from every thread that tints blocks.
 *
 * <p>The year runs through keyframes (0 the first day of spring, 0.25 summer, 0.5 autumn, 0.75 winter), each a palette
 * entry: between two keyframes a colour moves from one entry to the next, and nowhere else.
 * <ul>
 *   <li>grass: thaw, a fresh clear green in spring, vanilla's own green in summer (the biome's colour is Minecraft's),
 *       drying, olive-gold, a warm clean brown, then clean straw through winter; each in three tones, light, mid and
 *       dark, taken by how light the biome's own grass is;</li>
 *   <li>broadleaf crowns: bare twigs, buds, fresh green, vanilla's summer green, then the turn and the withering, each in
 *       four tree variants (red, orange, gold and a late green in autumn), which near and far take by the same hash, so a
 *       wood's trees differ as palette entries do;</li>
 *   <li>birches turn bright gold; needle trees keep their needles, darker and bluer in the cold; the leaf litter is
 *       freshest orange in autumn.</li>
 * </ul>
 * Hot, dry and tropical lands keep vanilla's colours ({@link #seasonality}).
 */
public final class SeasonColours {
	/** No season: every colour as vanilla has it. */
	public static final SeasonColours NONE = new SeasonColours(Float.NaN);
	/** Tree variants a crown takes by where it stands ({@link Hash#variant}, VOID LOD's {@code seasonVariant}). */
	public static final int VARIANTS = 4;

	// grass's states: palettes of three tones, light, mid, dark (SUMMER: vanilla's own)
	private static final int THAW = 0, FRESH = 1, SUMMER = 2, DRY = 3, OLIVE = 4, BROWN = 5, STRAW = 6;
	private static final int[][] GRASS_PALETTE = {
		{0xA8BE62, 0x94AA55, 0x7E934A},
		{0x98D24C, 0x82C03E, 0x6CA734},
		null,
		{0xAEBE52, 0x9AAB48, 0x82923E},
		{0xBDB24C, 0xA89D42, 0x8E8538},
		{0xC49C52, 0xAE8848, 0x92723E},
		{0xD6C68C, 0xC2B076, 0xAA9762}};
	private static final float[] GRASS_AT = {0.00F, 0.07F, 0.20F, 0.31F, 0.43F, 0.50F, 0.58F, 0.68F, 0.77F, 0.95F, 1.00F};
	private static final int[] GRASS = {THAW, FRESH, FRESH, SUMMER, SUMMER, DRY, OLIVE, BROWN, STRAW, STRAW, THAW};

	// crowns' states: palettes of four tree variants (SUMMER: vanilla's own, a shade apart per variant)
	private static final int BARE = 0, BUD = 1, LEAF = 2, GREEN = 3, TURN = 4, SERE = 5;
	private static final int[][] LEAF_PALETTE = {
		{0x7E6C5A, 0x766554, 0x86745F, 0x705F4F},
		{0xA8CC56, 0x9CC24E, 0xB0D060, 0x98BE4A},
		{0x86C442, 0x7CBA3C, 0x92CC48, 0x74B238},
		null,
		{0xB8421E, 0xD8761E, 0xE2AE2A, 0x9CA436},
		{0x9A5A2C, 0xA86A30, 0xB8843A, 0x8E7036}};
	private static final int[][] BIRCH_PALETTE = {
		{0x908479, 0x887C72, 0x988C80, 0x84786E},
		{0xB2D466, 0xA8CC5E, 0xBAD86E, 0xA2C658},
		{0x9ACE58, 0x92C652, 0xA2D25E, 0x8CC04E},
		null,
		{0xEEC63A, 0xE4BA34, 0xF2D046, 0xD8BE40},
		{0xB88C3E, 0xAC8238, 0xC09644, 0xA48A40}};
	/** A summer crown's shade, variant by variant, so even a green wood is not one flat colour. */
	private static final float[] SUMMER_SHADE = {0.94F, 1.0F, 1.06F, 0.97F};
	private static final float[] LEAF_AT = {0.00F, 0.045F, 0.085F, 0.13F, 0.22F, 0.32F, 0.50F, 0.555F, 0.60F, 0.665F, 0.71F, 0.75F, 1.00F};
	private static final int[] CROWN = {BARE, BARE, BUD, LEAF, LEAF, GREEN, GREEN, GREEN, TURN, TURN, SERE, BARE, BARE};

	/** Needles in the cold, and their fresh tips in late spring. */
	private static final int NEEDLES_COLD = 0x4A7856, NEEDLE_TIPS = 0x78AE5A;
	/** The leaf litter: fresh in autumn, sodden in winter. */
	private static final int LITTER = 0xC8742E, LITTER_WINTER = 0x8C5C34;

	private final float year;
	private final boolean on;
	/** -1 midwinter .. 1 midsummer. */
	private final float warmth;
	/** How deep into the cold the year is: 0 from late spring to mid-autumn, 1 in deep winter. */
	private final float cold;

	private SeasonColours(float year) {
		this.on = !Float.isNaN(year);
		this.year = this.on ? year - (float) Math.floor(year) : 0;
		this.warmth = this.on ? Air.warmth(this.year) : 0;
		this.cold = this.on ? Noise.smoothstep(0.3F, -0.85F, this.warmth) : 0;
	}

	/** The colours at {@code year} (0..1, 0 the first day of spring). */
	public static SeasonColours at(float year) {
		return new SeasonColours(year);
	}

	/** Whether these colours change anything (false: vanilla's own). */
	public boolean on() {
		return this.on;
	}

	public float year() {
		return this.year;
	}

	/** -1 midwinter .. 1 midsummer. */
	public float warmth() {
		return this.warmth;
	}

	/** How deep into the cold the year is, 0..1 (1 in deep winter): what lowers the snowline. */
	public float cold() {
		return this.cold;
	}

	/**
	 * How much a land of this climate changes through the year, 0.1..1: fully in the temperate and the cold lands, little
	 * in the hot ones (above about 1.0, a savanna or a desert) and in the wet tropics (warm and rainy: a jungle).
	 */
	public static float seasonality(float temperature, float downfall) {
		float s = 1 - Noise.smoothstep(0.95F, 1.6F, temperature);
		s *= 1 - 0.85F * Noise.smoothstep(0.9F, 0.97F, temperature) * Noise.smoothstep(0.75F, 0.9F, downfall);
		return Math.max(0.1F, s);
	}

	/**
	 * How far a land of this seasonality takes the palettes over vanilla's colours: all the way in a seasonal land, not at
	 * all in a hot or tropical one, the two clean colours meeting only across the lands between.
	 */
	private static float reach(float temperature, float downfall) {
		return Noise.smoothstep(0.3F, 0.6F, seasonality(temperature, downfall));
	}

	// ------------------------------------------------------------------------------------------------------ grass

	/**
	 * Grass (and everything vanilla tints like it: tall grass, ferns, sugar cane) whose vanilla colour is {@code base}, in a
	 * land of {@code temperature} and {@code downfall}; {@code shift} moves this spot's year a little (patches of the
	 * field turning before the rest).
	 */
	public int grass(int base, float temperature, float downfall, float shift) {
		if (!this.on) {
			return base;
		}
		float y = this.year + shift;
		y -= (float) Math.floor(y);
		int i = segment(GRASS_AT, y);
		float t = Noise.smoothstep(GRASS_AT[i], GRASS_AT[i + 1], y);
		float tone = tone(base);
		int c = mix(grassState(GRASS[i], base, tone), grassState(GRASS[i + 1], base, tone), t);
		return mix(base, c, reach(temperature, downfall));
	}

	/** How light a grass is, as a place along its palette's tones: 0 light .. 2 dark. */
	private static float tone(int base) {
		float l = luma(base >> 16 & 0xFF, base >> 8 & 0xFF, base & 0xFF);
		return 2 * (1 - Math.clamp((l - 95) / 75.0F, 0, 1));
	}

	private static int grassState(int state, int base, float tone) {
		int[] p = GRASS_PALETTE[state];
		if (p == null) {
			return base;
		}
		int i = Math.min(1, (int) tone);
		return mix(p[i], p[i + 1], tone - i);
	}

	// ------------------------------------------------------------------------------------------------------ leaves

	/** Broadleaf crowns tinted by their biome (oak, dark oak, vines), whose vanilla colour is {@code base}; tree {@code variant} (0..3). */
	public int foliage(int base, float temperature, float downfall, int variant) {
		if (!this.on) {
			return base;
		}
		return mix(base, this.crown(LEAF_PALETTE, base, variant), reach(temperature, downfall));
	}

	/** Birch leaves, whose vanilla colour ({@code base}) is the same everywhere: bright gold in autumn. */
	public int birch(int base, float temperature, float downfall, int variant) {
		if (!this.on) {
			return base;
		}
		return mix(base, this.crown(BIRCH_PALETTE, base, variant), reach(temperature, downfall));
	}

	private int crown(int[][] palette, int base, int variant) {
		int i = segment(LEAF_AT, this.year);
		float t = Noise.smoothstep(LEAF_AT[i], LEAF_AT[i + 1], this.year);
		int v = variant & 3;
		return mix(crownState(palette, CROWN[i], base, v), crownState(palette, CROWN[i + 1], base, v), t);
	}

	private static int crownState(int[][] palette, int state, int base, int v) {
		int[] p = palette[state];
		return p == null ? scale(base, SUMMER_SHADE[v]) : p[v];
	}

	/** Needle trees (spruce): green all year, darker and bluer in the cold, fresh tips in late spring. */
	public int conifer(int base, float temperature, float downfall) {
		if (!this.on) {
			return base;
		}
		float r = reach(temperature, downfall);
		int c = mix(base, NEEDLES_COLD, this.cold * r);
		float tips = Noise.smoothstep(0.08F, 0.16F, this.year) * (1 - Noise.smoothstep(0.2F, 0.3F, this.year));
		return mix(c, NEEDLE_TIPS, 0.35F * tips * r);
	}

	/** The leaf litter on the ground (vanilla's dry foliage tint): freshest orange in autumn, sodden brown in winter. */
	public int dryFoliage(int base, float temperature, float downfall) {
		if (!this.on) {
			return base;
		}
		float r = reach(temperature, downfall);
		int c = mix(base, LITTER, this.turned() * r);
		return mix(c, LITTER_WINTER, this.cold * r);
	}

	/**
	 * How far through their turn the broadleaf crowns are, 0..1: 0 green, 1 in full autumn colour (and still 1 as they
	 * wither); 0 again once bare.
	 */
	public float turned() {
		if (!this.on) {
			return 0;
		}
		return Noise.smoothstep(0.53F, 0.6F, this.year) * (1 - Noise.smoothstep(0.71F, 0.76F, this.year));
	}

	/**
	 * How bare the broadleaf crowns are, 0 in full leaf .. 1 bare twigs: thinning from the height of the turn, bare by the
	 * start of winter and until the buds break in early spring, thin again while the new leaves are small.
	 */
	public float bare() {
		if (!this.on) {
			return 0;
		}
		float y = this.year;
		if (y < 0.5F) {
			return 1 - Noise.smoothstep(0.03F, 0.12F, y);
		}
		return Noise.smoothstep(0.63F, 0.755F, y);
	}

	/** Which keyframe segment {@code y} falls in. */
	private static int segment(float[] at, float y) {
		int i = 0;
		while (i < at.length - 2 && y >= at[i + 1]) {
			i++;
		}
		return i;
	}

	// ------------------------------------------------------------------------------------------------ colour maths

	/** {@code a} toward {@code b} by {@code t}, per channel. */
	static int mix(int a, int b, float t) {
		if (t <= 0) {
			return a;
		}
		if (t >= 1) {
			return b;
		}
		int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
		int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return r << 16 | g << 8 | bl;
	}

	static int scale(int c, float f) {
		return rgb((c >> 16 & 0xFF) * f, (c >> 8 & 0xFF) * f, (c & 0xFF) * f);
	}

	static float luma(float r, float g, float b) {
		return 0.2126F * r + 0.7152F * g + 0.0722F * b;
	}

	static int rgb(float r, float g, float b) {
		return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
	}

	private static int clamp(float v) {
		return Math.clamp(Math.round(v), 0, 255);
	}
}
