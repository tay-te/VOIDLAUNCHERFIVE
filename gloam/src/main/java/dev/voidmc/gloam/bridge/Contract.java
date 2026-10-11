package dev.voidmc.gloam.bridge;

/**
 * The bridge contract with the VOID Welkin shader pack, version {@value #VERSION}: the layout of the data texture
 * ({@code gloam:pack/data}) and the order and ranges of the uniform mirror in its header. Pure arithmetic, no Minecraft,
 * so the encoding is unit-tested ({@code ContractTest}) and the pack's GLSL can be checked against it.
 *
 * <p>The texture is RGBA8, {@value #WIDTH} x {@value #HEIGHT}: four header rows, then a 4 x 2 grid of
 * {@value #TILE}² tiles, tile {@code t} at {@code x = (t % 4) * 256}, {@code y = 4 + (t / 4) * 256}. A tile holds the
 * cells round its centre wrapped: a cell lives at {@code cell & 255} whatever the centre, so moving the centre only
 * rewrites the strip that came into range. A cell is valid while its Chebyshev distance from the centre is under 128.
 *
 * <p>Header row 0: texel 0 the bytes {@code V O I D}; texel 1 the version, the flags ({@link #FLAG_EXPANSE} ...) and a
 * 16-bit frame counter (B low, A high); texels 2..9 the centre of tiles 0..7 as 16-bit cells (RG x, BA z, each the low
 * 16 bits of the cell, little-endian: the pack recovers the full cell against {@code cameraPositionInt}); texel 10 R
 * the mask of tiles holding data; texel 11 RGBA the 32-bit little-endian mask of the uniform slots that are live.
 * Rows 1..3: the uniform mirror, one texel column per {@link Slot} (see {@link #mirror}).
 */
public final class Contract {
	public static final int VERSION = 1;
	public static final int WIDTH = 1024, HEIGHT = 516, HEADER_ROWS = 4, TILE = 256, TILES = 8;
	/** Cells within this Chebyshev distance of a tile's centre are valid (the pack fades from 112 to 127). */
	public static final int REACH = 127;
	/** The magic in texel (0, 0). */
	public static final byte[] MAGIC = {'V', 'O', 'I', 'D'};

	// the tiles
	public static final int N0 = 0, N1 = 1, F0 = 2, F1 = 3, W0 = 4, W1 = 5, U = 6;
	/** The near tiles' cell, in blocks. */
	public static final int NEAR_CELL = 16;

	// header texel (1, 0) G: what is there
	public static final int FLAG_EXPANSE = 1, FLAG_LOD = 2, FLAG_SINGLEPLAYER = 4, FLAG_UNIFORMS = 8;

	// N1 alpha's low nibble: the cell's flags
	public static final int CELL_PRESENT = 1, CELL_EXPANSE = 2, CELL_SNOW = 4, CELL_WATER = 8;

	/** The header texels that hold the tile centres, the tile mask and the live-slot mask. */
	public static final int CENTRES_TEXEL = 2, TILE_MASK_TEXEL = 10, LIVE_MASK_TEXEL = 11;

	/** Water distance in N0 alpha: 4 blocks a step, 255 for a kilometre or more. */
	public static final float WATER_STEP = 4.0F;

	private Contract() {
	}

	// ------------------------------------------------------------------------------------------------- addressing

	/** The texel column of cell {@code cx} in tile {@code tile} (the GLSL {@code voidTexel}'s x). */
	public static int texelX(int tile, int cx) {
		return (tile & 3) * TILE + (cx & (TILE - 1));
	}

	/** The texel row of cell {@code cz} in tile {@code tile} (the GLSL {@code voidTexel}'s y). */
	public static int texelY(int tile, int cz) {
		return HEADER_ROWS + (tile >> 2) * TILE + (cz & (TILE - 1));
	}

	/** The byte offset of texel (x, y) in the whole texture's RGBA8 bytes. */
	public static int offset(int x, int y) {
		return (y * WIDTH + x) * 4;
	}

	/** Whether cell (cx, cz) is valid for a tile centred on (centreX, centreZ). */
	public static boolean valid(int cx, int cz, int centreX, int centreZ) {
		return Math.max(Math.abs(cx - centreX), Math.abs(cz - centreZ)) <= REACH;
	}

	/** The cell a world coordinate falls in, for cells {@code size} blocks across (floored, so negatives are right). */
	public static int cell(double coord, int size) {
		return (int) Math.floor(coord / size);
	}

	/** The full cell back from its low 16 bits as the header holds it, given a cell near it (the camera's). */
	public static int unwrap16(int low16, int near) {
		return near + (short) (low16 - near);
	}

	// ------------------------------------------------------------------------------------------------- encodings

	/**
	 * A height as h16: {@code (hi * 256 + lo) / 32 - 1024}, so -1024..1024 to a 32nd of a block. Returns
	 * {@code hi << 8 | lo}, clamped to 0..65535.
	 */
	public static int h16(float y) {
		return Math.clamp(Math.round((y + 1024.0F) * 32.0F), 0, 65535);
	}

	/** The height an h16 value stands for. */
	public static float fromH16(int hi, int lo) {
		return (hi * 256 + lo) / 32.0F - 1024.0F;
	}

	/** {@code v} from {@code lo..hi} as a byte 0..255 (unorm8), clamped. */
	public static int unorm8(float v, float lo, float hi) {
		return Math.clamp(Math.round((v - lo) / (hi - lo) * 255.0F), 0, 255);
	}

	/** {@code v} from {@code lo..hi} as 16 bits 0..65535, clamped. */
	public static int unorm16(float v, float lo, float hi) {
		return Math.clamp(Math.round((v - lo) / (hi - lo) * 65535.0F), 0, 65535);
	}

	/** Water distance in blocks as N0 alpha. */
	public static int waterByte(float blocks) {
		return Math.clamp(Math.round(Math.max(0, blocks) / WATER_STEP), 0, 255);
	}

	/** Haze-kind index (design table 2.4) for Expanse's haze hint; 0 for none or an unknown hint. */
	public static int hazeKind(String hint) {
		if (hint == null) {
			return 0;
		}
		return switch (hint) {
			case "temperate" -> 1;
			case "humid_lowland" -> 2;
			case "misty_lakes" -> 3;
			case "forest_damp" -> 4;
			case "dusty" -> 5;
			case "clear_alpine" -> 6;
			default -> 0;
		};
	}

	// ------------------------------------------------------------------------------------------------- the uniforms

	/** What a uniform is to GLSL. */
	public enum Type {
		INT, FLOAT, VEC4, IVEC2, IVEC3
	}

	/**
	 * The {@code void_*} uniforms in the design's order (section 2.2): the order is the mirror's texel column. Each
	 * float component has its range ({@code lo[i]..hi[i]}) for the mirror's quantising.
	 */
	public enum Slot {
		BRIDGE("void_bridge", Type.INT),
		VERSION("void_version", Type.INT),
		SEED("void_seed", Type.INT),
		YEAR("void_year", Type.FLOAT, 0, 1),
		WARMTH("void_warmth", Type.FLOAT, -1, 1),
		SEASON("void_season", Type.INT),
		SEASON_STATE("void_seasonState", Type.VEC4, 0, 1, 0, 1, 0, 1, -1024, 1024),
		WIND("void_wind", Type.VEC4, -1, 1, -1, 1, 0, 32, 0, 1),
		FRONT("void_front", Type.FLOAT, 0, 1),
		CLOUD_COVER("void_cloudCover", Type.FLOAT, 0, 1),
		RAIN("void_rain", Type.FLOAT, 0, 1),
		SHOWERS("void_showers", Type.FLOAT, 0, 1),
		STORM("void_storm", Type.FLOAT, 0, 1),
		MAX_STORM("void_maxStorm", Type.FLOAT, 0, 1),
		MIST("void_mist", Type.FLOAT, 0, 1),
		MIST_TOP("void_mistTop", Type.FLOAT, -1024, 1024),
		MIST_TIME("void_mistTime", Type.FLOAT, 0, 1),
		WETNESS("void_wetness", Type.FLOAT, 0, 1),
		HAZE("void_haze", Type.VEC4, -8, 8, 0, 1, 0, 1, 0, 2),
		HAZE_KIND("void_hazeKind", Type.INT),
		CLIMATE("void_climate", Type.VEC4, -1, 1, 0, 1, 0, 1, 0, 1),
		SHELTER("void_shelter", Type.VEC4, 0, 1, 0, 1, 0, 1, 0, 1),
		LIGHT("void_light", Type.VEC4, 0, 1, 0, 1, 0, 1, 0, 1),
		SKY("void_sky", Type.VEC4, 0, 1, 0, 1, 0, 1, 0, 4),
		CLOUD_LAYERS("void_cloudLayers", Type.VEC4, -1024, 1024, -1024, 1024, -1024, 1024, -1024, 1024),
		UNDERGROUND("void_underground", Type.VEC4, 0, 1, 0, 1, 0, 255, 0, 255),
		CAVE("void_cave", Type.VEC4, 0, 1, 0, 1, 0, 1, 0, 1),
		NEAR_CENTRE("void_nearCentre", Type.IVEC2),
		FAR_CENTRE("void_farCentre", Type.IVEC2),
		WEATHER_SHIFT("void_weatherShift", Type.VEC4, 0, 32768, 0, 32768, 0, 262144, 0, 262144),
		UG_CENTRE("void_ugCentre", Type.IVEC3);

		public final String glsl;
		public final Type type;
		private final float[] range;

		Slot(String glsl, Type type, float... range) {
			this.glsl = glsl;
			this.type = type;
			this.range = range;
		}

		/** How many components the uniform has. */
		public int components() {
			return switch (this.type) {
				case INT, FLOAT -> 1;
				case IVEC2 -> 2;
				case IVEC3 -> 3;
				case VEC4 -> 4;
			};
		}

		public float lo(int component) {
			return this.range[component * 2];
		}

		public float hi(int component) {
			return this.range[component * 2 + 1];
		}

		/** The bit of this slot in the live mask. */
		public int bit() {
			return 1 << this.ordinal();
		}
	}

	/**
	 * Writes one uniform's mirror into the header's rows 1..3 at texel column {@code slot.ordinal()}. Floats (and
	 * vectors) go as 16 bits per component from the slot's range: the high byte in row 1 (the coarse 8-bit value, enough
	 * alone), the low byte in row 2, component k in channel k. An int goes as its four little-endian bytes in row 1's RGBA;
	 * an ivec's component k as four little-endian bytes in row 1 + k. Unused bytes are zero.
	 *
	 * @param bytes  the whole texture's bytes ({@link #WIDTH} x {@link #HEIGHT} x 4)
	 * @param floats the float components (for FLOAT and VEC4)
	 * @param ints   the int components (for INT and IVECs)
	 */
	public static void mirror(byte[] bytes, Slot slot, float[] floats, int[] ints) {
		int x = slot.ordinal();
		for (int row = 1; row <= 3; row++) {
			int o = offset(x, row);
			bytes[o] = bytes[o + 1] = bytes[o + 2] = bytes[o + 3] = 0;
		}
		switch (slot.type) {
			case FLOAT, VEC4 -> {
				for (int k = 0; k < slot.components(); k++) {
					int q = unorm16(floats[k], slot.lo(k), slot.hi(k));
					bytes[offset(x, 1) + k] = (byte) (q >>> 8);
					bytes[offset(x, 2) + k] = (byte) q;
				}
			}
			case INT -> putInt(bytes, offset(x, 1), ints[0]);
			case IVEC2, IVEC3 -> {
				for (int k = 0; k < slot.components(); k++) {
					putInt(bytes, offset(x, 1 + k), ints[k]);
				}
			}
		}
	}

	/** Reads a float component back from the mirror (both bytes), as the pack would. */
	public static float mirroredFloat(byte[] bytes, Slot slot, int component) {
		int x = slot.ordinal();
		int q = (bytes[offset(x, 1) + component] & 255) << 8 | bytes[offset(x, 2) + component] & 255;
		return slot.lo(component) + q / 65535.0F * (slot.hi(component) - slot.lo(component));
	}

	/** Reads an int (or an ivec's component) back from the mirror. */
	public static int mirroredInt(byte[] bytes, Slot slot, int component) {
		return getInt(bytes, offset(slot.ordinal(), 1 + component));
	}

	public static void putInt(byte[] bytes, int o, int v) {
		bytes[o] = (byte) v;
		bytes[o + 1] = (byte) (v >>> 8);
		bytes[o + 2] = (byte) (v >>> 16);
		bytes[o + 3] = (byte) (v >>> 24);
	}

	public static int getInt(byte[] bytes, int o) {
		return bytes[o] & 255 | (bytes[o + 1] & 255) << 8 | (bytes[o + 2] & 255) << 16 | (bytes[o + 3] & 255) << 24;
	}

	/** Writes header row 0's magic, version, flags and frame counter. */
	public static void header(byte[] bytes, int flags, int frame) {
		System.arraycopy(MAGIC, 0, bytes, 0, 4);
		int o = offset(1, 0);
		bytes[o] = (byte) VERSION;
		bytes[o + 1] = (byte) flags;
		bytes[o + 2] = (byte) frame;
		bytes[o + 3] = (byte) (frame >>> 8);
	}

	/** Writes tile {@code tile}'s centre into header row 0 as two 16-bit cells. */
	public static void centre(byte[] bytes, int tile, int cx, int cz) {
		int o = offset(CENTRES_TEXEL + tile, 0);
		bytes[o] = (byte) cx;
		bytes[o + 1] = (byte) (cx >>> 8);
		bytes[o + 2] = (byte) cz;
		bytes[o + 3] = (byte) (cz >>> 8);
	}

	/** Writes the mask of tiles holding data and the mask of live uniform slots into header row 0. */
	public static void masks(byte[] bytes, int tiles, int liveSlots) {
		int o = offset(TILE_MASK_TEXEL, 0);
		bytes[o] = (byte) tiles;
		bytes[o + 1] = bytes[o + 2] = bytes[o + 3] = 0;
		putInt(bytes, offset(LIVE_MASK_TEXEL, 0), liveSlots);
	}

	// ------------------------------------------------------------------------------------------------- land cells

	/**
	 * A near-land cell's N0 texel, packed {@code R | G << 8 | B << 16 | A << 24}: R the ground's h16 high byte, G its low
	 * byte, B the lowness, A the water distance.
	 *
	 * @param ground        the ground's height (y), or the sea's surface out at sea
	 * @param lowness       0..1, Expanse's lowness through {@code smoothstep(0, 24)}
	 * @param waterDistance blocks to the nearest water
	 */
	public static int n0(float ground, float lowness, float waterDistance) {
		int h = h16(ground);
		return (h >>> 8) | (h & 255) << 8 | unorm8(lowness, 0, 1) << 16 | waterByte(waterDistance) << 24;
	}

	/**
	 * A near-land cell's N1 texel, packed as {@link #n0}: R the mist potential, G the temperature, B the humidity, A the
	 * haze kind (high nibble) and the cell flags (low nibble).
	 *
	 * @param potential   the mist potential, 0..1
	 * @param temperature -1..1
	 * @param humidity    0..1
	 * @param hazeKind    0..15 (design table 2.4)
	 * @param flags       the cell flags ({@link #CELL_PRESENT} ...), 0..15
	 */
	public static int n1(float potential, float temperature, float humidity, int hazeKind, int flags) {
		return unorm8(potential, 0, 1) | unorm8(temperature, -1, 1) << 8 | unorm8(humidity, 0, 1) << 16
			| ((hazeKind & 15) << 4 | flags & 15) << 24;
	}
}
