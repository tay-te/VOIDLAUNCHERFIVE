package dev.voidmc.gloam.bridge;

import java.util.Arrays;
import java.util.List;

/**
 * A line of cells worked out for one or two tiles of the data texture (the generic form of {@link LandTiles.Strip},
 * for W0/W1, F0/F1 and U): a column at world cell x {@link #at} running over cells z {@code from .. from + length - 1},
 * or a row at cell z {@link #at} over x. {@link #clear} empties the tiles instead and carries no cells. Once written,
 * the tiles' centre is ({@link #centreX}, {@link #centreZ}) and, for U, the height they were sampled at is
 * {@link #base}.
 *
 * <p>Strips are made on a worker and written on the render thread ({@link #write}), which is where the header's centres
 * and the {@code void_*Centre} uniforms are set from them, so the texture and what describes it never disagree.
 *
 * @param tileA  the tile {@link #a} goes into
 * @param tileB  the tile {@link #b} goes into, or -1 for none
 * @param a      tile A's texels, packed RGBA ({@link TileCodec#pack}); null for a clear
 * @param b      tile B's texels; null without a tile B or for a clear
 */
public record TileStrip(int tileA, int tileB, boolean clear, boolean column, int at, int from, int[] a, int[] b, int centreX, int centreZ, int base) {
	/** A strip that empties tile A (and B) and sets the centre. */
	public static TileStrip clear(int tileA, int tileB, int centreX, int centreZ, int base) {
		return new TileStrip(tileA, tileB, true, false, 0, 0, null, null, centreX, centreZ, base);
	}

	/** How many cells the strip carries. */
	public int length() {
		return this.a == null ? 0 : this.a.length;
	}

	/**
	 * Strips one worker job made, to be written together: a producer that must keep its tile consistent with one centre
	 * (U, whose bytes are relative to the sampled height) has a batch written in a single frame; W's rebuild may be spread
	 * over several frames ({@link #spreadable}).
	 */
	public record Batch(List<TileStrip> strips, boolean spreadable) {
	}

	/**
	 * Writes a strip into the texture's bytes, wrapped, or clears its tiles, and tells {@code dirty} which lines it
	 * touched (a clear marks the whole tile, line -1), as {@link LandTiles#write} does for N0 and N1.
	 */
	public static void write(byte[] bytes, TileStrip s, LandTiles.Dirty dirty) {
		if (s.clear) {
			clearTile(bytes, s.tileA);
			dirty.mark(s.tileA, false, -1);
			if (s.tileB >= 0) {
				clearTile(bytes, s.tileB);
				dirty.mark(s.tileB, false, -1);
			}
			return;
		}
		int n = s.length();
		for (int i = 0; i < n; i++) {
			int cx = s.column ? s.at : s.from + i;
			int cz = s.column ? s.from + i : s.at;
			put(bytes, Contract.offset(Contract.texelX(s.tileA, cx), Contract.texelY(s.tileA, cz)), s.a[i]);
			if (s.tileB >= 0) {
				put(bytes, Contract.offset(Contract.texelX(s.tileB, cx), Contract.texelY(s.tileB, cz)), s.b[i]);
			}
		}
		int line = s.at & (Contract.TILE - 1);
		dirty.mark(s.tileA, s.column, line);
		if (s.tileB >= 0) {
			dirty.mark(s.tileB, s.column, line);
		}
	}

	/** The texel tile {@code tile} holds for cell (cx, cz), packed as {@link TileCodec#pack} packs it (for tests). */
	public static int read(byte[] bytes, int tile, int cx, int cz) {
		int o = Contract.offset(Contract.texelX(tile, cx), Contract.texelY(tile, cz));
		return bytes[o] & 255 | (bytes[o + 1] & 255) << 8 | (bytes[o + 2] & 255) << 16 | (bytes[o + 3] & 255) << 24;
	}

	private static void clearTile(byte[] bytes, int tile) {
		for (int z = 0; z < Contract.TILE; z++) {
			int o = Contract.offset(Contract.texelX(tile, 0), Contract.texelY(tile, z));
			Arrays.fill(bytes, o, o + Contract.TILE * 4, (byte) 0);
		}
	}

	private static void put(byte[] bytes, int o, int rgba) {
		bytes[o] = (byte) rgba;
		bytes[o + 1] = (byte) (rgba >>> 8);
		bytes[o + 2] = (byte) (rgba >>> 16);
		bytes[o + 3] = (byte) (rgba >>> 24);
	}
}
