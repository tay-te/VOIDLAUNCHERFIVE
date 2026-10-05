package dev.voidmc.atlas.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * What is known about one region's columns, raw: the height of the top block the map shows, which block that is
 * (an id in the world's block {@link NamePalette}), how deep the water over it is, its biome, and flags. Colours are
 * not stored: they are made from this when a tile is drawn, so a better palette applies to every map already made.
 *
 * <p>Columns are written by the chunk scanners and read by the tile renderer from different threads without locks:
 * every field is a primitive array element, and a column read half-way through an update is drawn again when the
 * update's own render comes.
 */
public final class RegionRaw {
	public static final int SIZE = Coords.REGION_SIZE;
	public static final int AREA = SIZE * SIZE;
	public static final short UNKNOWN = Short.MIN_VALUE;
	/** The column is known (it has been seen). */
	public static final byte KNOWN = 1;
	/** Underground and Nether slices: solid rock all through the slice, no floor to show. */
	public static final byte ROCK = 2;

	private static final int MAGIC = 0x41544C52; // ATLR
	private static final int VERSION = 1;

	public final int x;
	public final int z;
	public final short[] height = new short[AREA];
	public final char[] block = new char[AREA];
	public final byte[] depth = new byte[AREA];
	public final char[] biome = new char[AREA];
	public final byte[] flags = new byte[AREA];
	/** Bumped by every write, so savers can tell whether there is anything new to save. */
	private volatile long version;
	private long savedVersion;

	public RegionRaw(int x, int z) {
		this.x = x;
		this.z = z;
		Arrays.fill(this.height, UNKNOWN);
	}

	public static int index(int lx, int lz) {
		return lz << Coords.REGION_SHIFT | lx;
	}

	public boolean known(int i) {
		return (this.flags[i] & KNOWN) != 0;
	}

	/**
	 * Writes a column; returns whether it changed. {@code depth} is the water over the block (0 for none);
	 * {@code flags} includes {@link #KNOWN}.
	 */
	public boolean set(int lx, int lz, int height, int block, int depth, int biome, int flags) {
		int i = index(lx, lz);
		short h = (short) height;
		char b = (char) block;
		byte d = (byte) Math.min(255, Math.max(0, depth));
		char bi = (char) biome;
		byte f = (byte) flags;
		if (this.height[i] == h && this.block[i] == b && this.depth[i] == d && this.biome[i] == bi && this.flags[i] == f) {
			return false;
		}
		this.height[i] = h;
		this.block[i] = b;
		this.depth[i] = d;
		this.biome[i] = bi;
		this.flags[i] = f;
		this.version++;
		return true;
	}

	public int depth(int i) {
		return this.depth[i] & 0xFF;
	}

	public long version() {
		return this.version;
	}

	public boolean dirty() {
		return this.version != this.savedVersion;
	}

	public void markSaved(long version) {
		this.savedVersion = version;
	}

	/** Whether any column is known. */
	public boolean isEmpty() {
		for (byte f : this.flags) {
			if ((f & KNOWN) != 0) {
				return false;
			}
		}
		return true;
	}

	/** The region as bytes: a header, then its arrays deflated. */
	public byte[] encode() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(AREA * 2);
			Deflater deflater = new Deflater(Deflater.BEST_SPEED);
			try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, deflater, 1 << 16))) {
				for (short h : this.height) {
					out.writeShort(h);
				}
				for (char b : this.block) {
					out.writeChar(b);
				}
				out.write(this.depth);
				for (char b : this.biome) {
					out.writeChar(b);
				}
				out.write(this.flags);
			} finally {
				deflater.end();
			}
			return Frame.wrap(MAGIC, VERSION, bytes.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Reads what {@link #encode} wrote; throws {@link IOException} if it is not a whole, valid region. */
	public static RegionRaw decode(int x, int z, byte[] data) throws IOException {
		byte[] payload = Frame.unwrap(MAGIC, VERSION, data);
		RegionRaw raw = new RegionRaw(x, z);
		try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(payload), new java.util.zip.Inflater(), 1 << 16))) {
			for (int i = 0; i < AREA; i++) {
				raw.height[i] = in.readShort();
			}
			for (int i = 0; i < AREA; i++) {
				raw.block[i] = in.readChar();
			}
			in.readFully(raw.depth);
			for (int i = 0; i < AREA; i++) {
				raw.biome[i] = in.readChar();
			}
			in.readFully(raw.flags);
		}
		raw.savedVersion = raw.version;
		return raw;
	}
}
