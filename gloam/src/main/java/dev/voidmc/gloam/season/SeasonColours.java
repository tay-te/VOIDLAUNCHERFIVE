package dev.voidmc.gloam.season;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Noise;

/**
 * What grass and leaves look like at one point of the year: pure colour maths, worked out from the colour vanilla would
 * give (the biome's own tint, which Expanse sets naturally per biome) and the biome's climate, so every biome turns in
 * its own greens. One of these is made per season step ({@link Seasons}) and read from every thread that tints blocks.
 *
 * <p>The year as the colours see it (0 the first day of spring, 0.25 summer, 0.5 autumn, 0.75 winter):
 * <ul>
 *   <li>grass: a fresh, light yellow-green in spring, deep green in summer (drying a little by its end in the drier
 *       lands), yellowing olive through autumn and browning at its end, a dull straw-brown in winter where there is no
 *       snow, greening again as spring comes;</li>
 *   <li>broadleaf trees: bare until the buds break early in spring, fresh light green, deep summer green, then a turn to
 *       gold, orange and red in mid-autumn (each tree its own, see {@link #tree}), withering brown and falling, bare
 *       twig grey-brown through winter; birches turn bright yellow;</li>
 *   <li>needle trees keep their needles, a little darker and bluer in the cold;</li>
 *   <li>the leaf litter on the ground is freshest orange-brown in autumn.</li>
 * </ul>
 * Hot, dry and tropical lands change little ({@link #seasonality}).
 */
public final class SeasonColours {
	/** No season: every colour as vanilla has it. */
	public static final SeasonColours NONE = new SeasonColours(Float.NaN);

	// the states a colour passes through; each a function of vanilla's colour (see state())
	private static final int BASE = 0, FRESH = 1, DEEP = 2, DRY = 3, TURN = 4, SERE = 5, BARE = 6, BUD = 7, STRAW = 8, EARLY = 9, YELLOW = 10,
		EARLY_TURN = 11, BROWN = 12;

	/** Grass through the year: (year, state) keyframes, eased between. */
	private static final float[] GRASS_AT = {0.00F, 0.07F, 0.20F, 0.31F, 0.43F, 0.50F, 0.58F, 0.68F, 0.77F, 0.95F, 1.00F};
	private static final int[] GRASS = {EARLY, FRESH, FRESH, DEEP, DEEP, DRY, YELLOW, BROWN, STRAW, STRAW, EARLY};
	/** Broadleaf crowns through the year. */
	private static final float[] LEAF_AT = {0.00F, 0.045F, 0.085F, 0.13F, 0.22F, 0.32F, 0.50F, 0.555F, 0.60F, 0.665F, 0.71F, 0.75F, 1.00F};
	private static final int[] LEAF = {BARE, BARE, BUD, FRESH, FRESH, DEEP, DEEP, EARLY_TURN, TURN, TURN, SERE, BARE, BARE};

	private static final int OLIVE = 0xA9A04E, STRAW_BROWN = 0x9A9068, GRASS_BROWN = 0x9C8C5A, DRY_GRASS = 0xB3A85C, YELLOW_GREEN = 0x9FC64A;
	private static final int AUTUMN = 0xC98A2C, WITHERED = 0x8C5E34, TWIGS = 0x76695D, BUDS = 0x9DBE4A;
	private static final int BIRCH_GOLD = 0xDDB935, BIRCH_SERE = 0x9A7A3E, BIRCH_TWIGS = 0x8A7D79, LITTER = 0xB46A2E;

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
		int c = this.along(GRASS_AT, GRASS, y, base, false, 1 - downfall);
		return mix(base, c, seasonality(temperature, downfall));
	}

	// ------------------------------------------------------------------------------------------------------ leaves

	/** Broadleaf crowns tinted by their biome (oak, dark oak, vines), whose vanilla colour is {@code base}. */
	public int foliage(int base, float temperature, float downfall) {
		if (!this.on) {
			return base;
		}
		int c = this.along(LEAF_AT, LEAF, this.year, base, false, 1 - downfall);
		return mix(base, c, seasonality(temperature, downfall));
	}

	/** Birch leaves, whose vanilla colour ({@code base}) is the same everywhere: bright yellow in autumn. */
	public int birch(int base, float temperature, float downfall) {
		if (!this.on) {
			return base;
		}
		int c = this.along(LEAF_AT, LEAF, this.year, base, true, 1 - downfall);
		return mix(base, c, seasonality(temperature, downfall));
	}

	/** Needle trees (spruce): green all year, a little darker and bluer in the cold, fresh tips in late spring. */
	public int conifer(int base, float temperature, float downfall) {
		if (!this.on) {
			return base;
		}
		float s = seasonality(temperature, downfall);
		int cold = saturate(scale(base, 0.8F), 0.9F);
		cold = mix(cold, 0x3E6B58, 0.12F);
		int c = mix(base, cold, this.cold * s);
		float tips = Noise.smoothstep(0.08F, 0.16F, this.year) * (1 - Noise.smoothstep(0.2F, 0.3F, this.year));
		return mix(c, this.state(FRESH, base, false, 0), 0.25F * tips * s);
	}

	/** The leaf litter on the ground (vanilla's dry foliage tint): freshest orange-brown in autumn, darker in winter. */
	public int dryFoliage(int base, float temperature, float downfall) {
		if (!this.on) {
			return base;
		}
		float s = seasonality(temperature, downfall);
		int c = mix(base, match(LITTER, base, 1.1F), 0.5F * this.turned() * s);
		return mix(c, scale(c, 0.82F), 0.5F * this.cold * s);
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

	/**
	 * One tree's own colour, from the colour its biome gives its crown this time of year ({@code seasoned}) and three
	 * numbers 0..1 of its own: through the turn trees go from red through orange and gold to a late yellow-green each by
	 * its own hue, brightness and depth; the rest of the year they differ only a little.
	 */
	public int tree(int seasoned, float v1, float v2, float v3) {
		if (!this.on) {
			return seasoned;
		}
		float turn = this.turned();
		float r = (seasoned >> 16 & 0xFF) / 255.0F, g = (seasoned >> 8 & 0xFF) / 255.0F, b = (seasoned & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float v = max, s = max <= 0 ? 0 : (max - min) / max, h = hue(r, g, b, max, min);
		// most trees within a band about the season's colour, one in ten well behind (still yellow-green)
		float spread = v1 < 0.9F ? (v1 / 0.9F - 0.5F) * 2 : 1 + (v1 - 0.9F) * 12;
		h += spread * (5 + 33 * turn);
		s = Math.clamp(s * (1 + (v2 - 0.5F) * (0.1F + 0.35F * turn)), 0, 1);
		v = Math.clamp(v * (1 + (v3 - 0.5F) * (0.1F + 0.16F * turn)), 0, 1);
		return hsv(h, s, v);
	}

	/**
	 * A crown's colour where its leaves are thinned ({@link #bare}: the sprites are the same everywhere) but its land keeps
	 * them green (a warm, wet one): the green going to twig colour as the leaves go, so no thinned crown shows green twigs.
	 */
	public int bareTwigs(int c) {
		float bare = this.bare();
		if (bare <= 0) {
			return c;
		}
		int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
		float green = Math.clamp((g - Math.max(r, b)) / 30.0F, 0, 1);
		return mix(c, this.state(BARE, c, false, 0), bare * green);
	}

	// ------------------------------------------------------------------------------------------------ the states

	/** Where {@code y} falls between the keyframes, eased, and the colour there. */
	private int along(float[] at, int[] states, float y, int base, boolean birch, float dryness) {
		int i = 0;
		while (i < at.length - 2 && y >= at[i + 1]) {
			i++;
		}
		float t = Noise.smoothstep(at[i], at[i + 1], y);
		int a = this.state(states[i], base, birch, dryness);
		return states[i + 1] == states[i] ? a : mix(a, this.state(states[i + 1], base, birch, dryness), t);
	}

	private int state(int state, int base, boolean birch, float dryness) {
		return switch (state) {
			case FRESH -> saturate(mix(scale(base, 1.12F), match(YELLOW_GREEN, base, 1.15F), 0.35F), 1.08F);
			case DEEP -> saturate(scale(base, 0.88F), 1.1F);
			case DRY -> mix(this.state(DEEP, base, birch, dryness), match(DRY_GRASS, base, 1.0F), 0.12F + 0.4F * Math.clamp(dryness, 0, 1));
			case YELLOW -> mix(base, match(OLIVE, base, 1.0F), 0.6F);
			case STRAW -> saturate(mix(base, match(STRAW_BROWN, base, 0.95F), 0.8F), 0.85F);
			case BROWN -> mix(base, match(GRASS_BROWN, base, 0.97F), 0.72F);
			case EARLY -> mix(this.state(STRAW, base, birch, dryness), this.state(FRESH, base, birch, dryness), 0.45F);
			case TURN -> birch ? match(BIRCH_GOLD, base, 1.18F) : match(AUTUMN, base, 1.12F);
			case EARLY_TURN -> mix(this.state(DEEP, base, birch, dryness), this.state(TURN, base, birch, dryness), 0.35F);
			case SERE -> match(birch ? BIRCH_SERE : WITHERED, base, 0.95F);
			case BARE -> birch ? match(BIRCH_TWIGS, base, 1.05F) : match(TWIGS, base, 1.0F);
			case BUD -> mix(match(BUDS, base, 1.15F), this.state(BARE, base, birch, dryness), 0.35F);
			default -> base;
		};
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

	/** Pushes a colour's channels away from its grey ({@code f} above 1) or toward it. */
	static int saturate(int c, float f) {
		float r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
		float l = luma(r, g, b);
		return rgb(l + (r - l) * f, l + (g - l) * f, l + (b - l) * f);
	}

	/**
	 * A fixed colour brought most of the way to {@code base}'s brightness, times {@code k}: so a dark swamp's autumn is a
	 * darker orange than a bright meadow's, as its greens were darker.
	 */
	static int match(int target, int base, float k) {
		float lt = luma(target >> 16 & 0xFF, target >> 8 & 0xFF, target & 0xFF);
		float lb = luma(base >> 16 & 0xFF, base >> 8 & 0xFF, base & 0xFF);
		float f = (lt <= 0 ? 1 : (float) Math.pow(Math.max(1, lb) / lt, 0.6)) * k;
		return scale(target, f);
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

	private static float hue(float r, float g, float b, float max, float min) {
		float d = max - min;
		if (d <= 0) {
			return 0;
		}
		float h;
		if (max == r) {
			h = (g - b) / d;
		} else if (max == g) {
			h = 2 + (b - r) / d;
		} else {
			h = 4 + (r - g) / d;
		}
		return h * 60;
	}

	private static int hsv(float h, float s, float v) {
		h = ((h % 360) + 360) % 360 / 60;
		int i = (int) h;
		float f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (i) {
			case 0 -> {
				r = v;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = v;
				b = p;
			}
			case 2 -> {
				r = p;
				g = v;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = v;
			}
			case 4 -> {
				r = t;
				g = p;
				b = v;
			}
			default -> {
				r = v;
				g = p;
				b = q;
			}
		}
		return rgb(r * 255, g * 255, b * 255);
	}
}
