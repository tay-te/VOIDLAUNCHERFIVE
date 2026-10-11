package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.air.Noise;

/**
 * The encodings of the bridge's other tiles (contract v1, design section 2.3): W0 and W1 (the weather), F0 and F1 (the
 * far land) and U (the underground), next to {@link Contract}'s near-land ones. Pure arithmetic, no Minecraft, so every
 * byte the pack reads is unit-tested ({@code TileCodecTest}).
 *
 * <p>Every texel is packed {@code R | G << 8 | B << 16 | A << 24}, as {@link Contract#n0} packs them.
 *
 * <table>
 *   <caption>The layouts</caption>
 *   <tr><th>Tile</th><th>R</th><th>G</th><th>B</th><th>A</th></tr>
 *   <tr><td>F0</td><td>floor height hi</td><td>floor height lo</td><td>lowness 0..1</td><td>water fraction 0..1</td></tr>
 *   <tr><td>F1</td><td>mist potential</td><td>temperature -1..1</td><td>humidity</td><td>haze kind &lt;&lt; 4 | cell flags</td></tr>
 *   <tr><td>W0, W1</td><td>front</td><td>cloud cover</td><td>rain incl. showers</td><td>storm</td></tr>
 *   <tr><td>U</td><td>openness: space / 0.5 blocks (half blocks, 0 rock)</td><td>floor - base + 128</td><td>ceiling - base + 128</td><td>water kind &lt;&lt; 4 | U flags</td></tr>
 * </table>
 */
public final class TileCodec {
	/** The far tiles' cell (F0, F1) and the far weather's (W1), in blocks. */
	public static final int FAR_CELL = 1024;
	/** The near weather's cell (W0), in blocks. */
	public static final int WEATHER_CELL = 128;
	/** The underground tile's cell (U), in blocks. */
	public static final int UG_CELL = 4;
	/**
	 * The U tile uses 128 x 128 of its 256 x 256 texels: cells within this Chebyshev distance of its centre are valid
	 * (the window is {@code centre - 63 .. centre + 64}; the pack fades from 56 to 63).
	 */
	public static final int UG_REACH = 63;
	/** The cells along one side of the U window. */
	public static final int UG_SIZE = 128;
	/**
	 * What {@code void_weatherShift} wraps by: one whole tile of cells, so wrapping it never moves a texel (32768 = 256 x
	 * 128 for W0, 262144 = 256 x 1024 for W1).
	 */
	public static final double WEATHER_NEAR_WRAP = 256.0 * WEATHER_CELL, WEATHER_FAR_WRAP = 256.0 * FAR_CELL;

	// U alpha, high nibble: the cavern water that is nearest. The same ids as gloam_sound's CaveKinds (river 1, sea 2,
	// lake 3, lava 4), which the pack's underground module and void_underground.w use: never renumber.
	public static final int WATER_NONE = 0, WATER_RIVER = 1, WATER_SEA = 2, WATER_LAKE = 3, WATER_LAVA = 4;
	// U alpha, low nibble
	public static final int UG_GLOWWORMS = 1, UG_DRIP = 2, UG_RIVER = 4, UG_PRESENT = 8;

	/**
	 * The realms underground, as {@code void_underground.z}: 0 none, then Expanse's realms in its own order, and 13 under
	 * a buried sea. Stable: the pack keys its underground palettes by these numbers.
	 */
	public static final String[] REALMS = {"none", "wilds", "lush", "dripstone", "crystal", "ember", "mere", "frozen", "roots", "fossil", "tidal",
		"amber", "brimstone", "sea"};

	/** How much open space one step of U's red channel stands for, in blocks (255 = 127.5 blocks or more): half blocks,
	 * as the pack's underground module reads it (ug_common.glsl, U tile v1). */
	public static final float SPACE_STEP = 0.5F;

	private TileCodec() {
	}

	public static int pack(int r, int g, int b, int a) {
		return (r & 255) | (g & 255) << 8 | (b & 255) << 16 | (a & 255) << 24;
	}

	public static int r(int texel) {
		return texel & 255;
	}

	public static int g(int texel) {
		return texel >>> 8 & 255;
	}

	public static int b(int texel) {
		return texel >>> 16 & 255;
	}

	public static int a(int texel) {
		return texel >>> 24;
	}

	// ------------------------------------------------------------------------------------------------- the far land

	/**
	 * An F0 texel.
	 *
	 * @param floor         the cell's valley floor (y): a low quantile of its ground, not its mean, so the pack's mist
	 *                      slab sits in the cell's hollows (see {@link FarTiles})
	 * @param lowness       0..1, how far the cell lies below the cells round it
	 * @param waterFraction 0..1, the share of the cell that is open water
	 */
	public static int f0(float floor, float lowness, float waterFraction) {
		int h = Contract.h16(floor);
		return pack(h >>> 8, h, Contract.unorm8(lowness, 0, 1), Contract.unorm8(waterFraction, 0, 1));
	}

	/** An F1 texel: the same layout as N1 ({@link Contract#n1}). */
	public static int f1(float potential, float temperature, float humidity, int hazeKind, int flags) {
		return Contract.n1(potential, temperature, humidity, hazeKind, flags);
	}

	// ------------------------------------------------------------------------------------------------- the weather

	/** A W0 or W1 texel: the front, the cloud cover, the rain (showers included) and the storm, each 0..1. */
	public static int weather(float front, float cover, float rain, float storm) {
		return pack(Contract.unorm8(front, 0, 1), Contract.unorm8(cover, 0, 1), Contract.unorm8(rain, 0, 1), Contract.unorm8(storm, 0, 1));
	}

	/**
	 * {@code void_weatherShift} for a moment {@code seconds} into the world (game time / 20): what the pack adds to a
	 * world x, z (themselves taken modulo the same wrap, from {@code cameraPositionInt & 32767} or {@code & 262143}) to land
	 * in the fronts' drifting frame. {x, z} wrapped by {@link #WEATHER_NEAR_WRAP}, then {x, z} by {@link #WEATHER_FAR_WRAP}.
	 */
	public static float[] weatherShift(double seconds, float[] out) {
		double sx = -dev.voidmc.gloam.air.Air.FRONT_DRIFT * seconds, sz = -dev.voidmc.gloam.air.Air.FRONT_DRIFT * 0.2 * seconds;
		out[0] = (float) wrap(sx, WEATHER_NEAR_WRAP);
		out[1] = (float) wrap(sz, WEATHER_NEAR_WRAP);
		out[2] = (float) wrap(sx, WEATHER_FAR_WRAP);
		out[3] = (float) wrap(sz, WEATHER_FAR_WRAP);
		return out;
	}

	/** {@code v} modulo {@code m}, in {@code 0 .. m}. */
	public static double wrap(double v, double m) {
		return v - Math.floor(v / m) * m;
	}

	// ------------------------------------------------------------------------------------------------- the underground

	/**
	 * A U texel.
	 *
	 * @param space   how far (blocks) the cell's point is inside open space (Expanse's {@code space})
	 * @param floor   the floor under it (y)
	 * @param ceiling the roof over it (y)
	 * @param base    the height the tile was sampled at ({@code void_ugCentre.y}); floor and ceiling are stored
	 *                against it, one block a step, 128 meaning level with it
	 * @param water   {@link #WATER_NONE} ...
	 * @param flags   {@link #UG_GLOWWORMS} ... ({@link #UG_PRESENT} for a cell with data)
	 */
	public static int underground(float space, int floor, int ceiling, int base, int water, int flags) {
		return pack(spaceByte(space), signed(floor - base), signed(ceiling - base), (water & 15) << 4 | flags & 15);
	}

	/** Open space as U's red channel. */
	public static int spaceByte(float space) {
		// 0 is rock; any open space is at least one step, so a sliver of air never reads as rock
		return space <= 0 ? 0 : Math.clamp(Math.round(space / SPACE_STEP), 1, 255);
	}

	/** The space U's red channel stands for, in blocks. */
	public static float space(int r) {
		return r * SPACE_STEP;
	}

	/** A signed offset in blocks as an offset-binary byte (128 = 0), clamped to -128..127. */
	public static int signed(int v) {
		return Math.clamp(v, -128, 127) + 128;
	}

	/** The offset an offset-binary byte stands for. */
	public static int unsigned(int b) {
		return (b & 255) - 128;
	}

	/** The number of a cavern water kind as Expanse names it ({@code river}, {@code lake}, {@code sea}, {@code lava}). */
	public static int waterKind(String kind) {
		if (kind == null) {
			return WATER_NONE;
		}
		return switch (kind) {
			case "river" -> WATER_RIVER;
			case "lake" -> WATER_LAKE;
			case "sea" -> WATER_SEA;
			case "lava" -> WATER_LAVA;
			default -> WATER_NONE;
		};
	}

	/** The number of a realm as Expanse names it ({@link #REALMS}), 0 for none or one this contract does not know. */
	public static int realm(String name) {
		if (name != null) {
			for (int i = 1; i < REALMS.length; i++) {
				if (REALMS[i].equals(name)) {
					return i;
				}
			}
		}
		return 0;
	}

	// ------------------------------------------------------------------------------------------------- climate

	/**
	 * A vanilla biome's base temperature (about -0.7 frozen peaks, 0 snowy plains, 0.8 plains, 2 desert) on the
	 * contract's -1..1 scale, which is Expanse's own (the biome source's temperature parameter): temperate land near 0,
	 * the hottest near 0.9, the frozen at -1. Used wherever a tile or uniform falls back on biomes.
	 */
	public static float temperature(float biomeBase) {
		return Math.clamp((biomeBase - 0.8F) * 0.75F, -1.0F, 1.0F);
	}

	/** The biome base temperature a contract temperature stands for (the inverse of {@link #temperature}). */
	public static float biomeTemperature(float temperature) {
		return 0.8F + temperature / 0.75F;
	}

	/**
	 * How hot and dry a land is, 0..1 (1 a desert, which has no mist), from its temperature (contract scale) and its
	 * humidity: the same ramp the near tiles use ({@code LandTiles.encode}), so the mist potential has no seam where the
	 * near tiles hand over to the far ones.
	 */
	public static float dry(float temperature, float humidity) {
		return Noise.smoothstep(0.2F, 0.55F, temperature) * (1 - Noise.smoothstep(0.3F, 0.5F, humidity));
	}

	/**
	 * The haze kind of a land with no Expanse hint (design table 2.4), from its climate: dust over hot dry land, clear
	 * mountain air high in the cold, misty lakes by water, a humid lowland in wet low ground, a damp forest in wet woods,
	 * else temperate.
	 *
	 * @param temperature -1..1 (contract scale)
	 * @param humidity    0..1
	 * @param water       0..1, how much of the place is water or how near it is
	 * @param lowness     0..1
	 * @param altitude    the ground's height above sea level, in blocks
	 * @param wooded      whether the land is a forest (a biome tagged as one, or LOD's leaves)
	 */
	public static int hazeKind(float temperature, float humidity, float water, float lowness, float altitude, boolean wooded) {
		if (humidity < 0.3F && temperature > 0.2F) {
			return 5;
		}
		if (temperature < -0.35F && altitude > 90) {
			return 6;
		}
		if (water > 0.5F && humidity > 0.4F) {
			return 3;
		}
		if (humidity > 0.65F && lowness > 0.35F && altitude < 40) {
			return 2;
		}
		if (wooded && humidity > 0.55F) {
			return 4;
		}
		return 1;
	}
}
