package dev.voidmc.atlas.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * One region drawn: its pixels at every mip level, 512 x 512 at level 0 down to 16 x 16 at level 5. A pixel is
 * {@code coverage << 24 | 0xRRGGBB}, where coverage (0..255) is how much of it is explored land: 0 unexplored, 255 all
 * of it, and between at the dithered edge. Levels are box-filtered from the one below, weighted by coverage, so the
 * explored edge stays an edge (and is dithered again) when zoomed out.
 *
 * <p>Levels load on their own ({@link #decode(byte[], int, int, int)} with a level mask): a zoomed-out tile needs only
 * the small levels of the hundreds of regions it covers.
 */
public final class RegionImage {
	public static final int LEVELS = Coords.MAX_LEVEL + 1;
	private static final int MAGIC = 0x41544C49; // ATLI
	private static final int VERSION = 1;

	public final int x;
	public final int z;
	/** The palette the pixels were drawn with: an image from an older palette is redrawn from its raw region. */
	public final int palette;
	private final int[][] levels = new int[LEVELS][];
	private volatile long version;
	private long savedVersion;

	public RegionImage(int x, int z, int palette) {
		this.x = x;
		this.z = z;
		this.palette = palette;
	}

	/** A level's pixels, or null if that level was not loaded. */
	public int[] level(int level) {
		return this.levels[level];
	}

	/** A level's pixels, made (empty) if it is not there yet. */
	public int[] levelOrCreate(int level) {
		int[] pixels = this.levels[level];
		if (pixels == null) {
			int size = Coords.regionImageSize(level);
			pixels = new int[size * size];
			this.levels[level] = pixels;
		}
		return pixels;
	}

	public boolean hasLevel(int level) {
		return this.levels[level] != null;
	}

	public boolean complete() {
		for (int[] level : this.levels) {
			if (level == null) {
				return false;
			}
		}
		return true;
	}

	public void touch() {
		this.version++;
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

	/**
	 * Rebuilds levels 1..5 over a level-0 rectangle {@code [x0, x1) x [z0, z1)} (region pixels): each pixel the
	 * coverage-weighted mean of the four below it. Returns nothing; every level must be present.
	 */
	public void downsample(int x0, int z0, int x1, int z1) {
		for (int level = 1; level < LEVELS; level++) {
			int[] src = this.levelOrCreate(level - 1);
			int[] dst = this.levelOrCreate(level);
			int srcSize = Coords.regionImageSize(level - 1);
			int size = Coords.regionImageSize(level);
			x0 >>= 1;
			z0 >>= 1;
			x1 = (x1 + 1) >> 1;
			z1 = (z1 + 1) >> 1;
			for (int z = z0; z < z1; z++) {
				for (int x = x0; x < x1; x++) {
					int i = (z << 1) * srcSize + (x << 1);
					dst[z * size + x] = average(src[i], src[i + 1], src[i + srcSize], src[i + srcSize + 1]);
				}
			}
		}
	}

	/** The coverage-weighted mean of four pixels, with the mean coverage. */
	public static int average(int p0, int p1, int p2, int p3) {
		int c0 = p0 >>> 24, c1 = p1 >>> 24, c2 = p2 >>> 24, c3 = p3 >>> 24;
		int c = c0 + c1 + c2 + c3;
		if (c == 0) {
			return 0;
		}
		int r = ((p0 >> 16 & 0xFF) * c0 + (p1 >> 16 & 0xFF) * c1 + (p2 >> 16 & 0xFF) * c2 + (p3 >> 16 & 0xFF) * c3 + c / 2) / c;
		int g = ((p0 >> 8 & 0xFF) * c0 + (p1 >> 8 & 0xFF) * c1 + (p2 >> 8 & 0xFF) * c2 + (p3 >> 8 & 0xFF) * c3 + c / 2) / c;
		int b = ((p0 & 0xFF) * c0 + (p1 & 0xFF) * c1 + (p2 & 0xFF) * c2 + (p3 & 0xFF) * c3 + c / 2) / c;
		return ((c + 2) / 4) << 24 | r << 16 | g << 8 | b;
	}

	/** Every level, each deflated on its own behind a table of lengths and checksums. */
	public byte[] encode() {
		byte[][] blocks = new byte[LEVELS][];
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		try {
			for (int level = 0; level < LEVELS; level++) {
				int[] pixels = this.levelOrCreate(level);
				ByteBuffer raw = ByteBuffer.allocate(pixels.length * 4);
				raw.asIntBuffer().put(pixels);
				deflater.reset();
				deflater.setInput(raw.array());
				deflater.finish();
				ByteArrayOutputStream out = new ByteArrayOutputStream(pixels.length);
				byte[] buf = new byte[1 << 16];
				while (!deflater.finished()) {
					int n = deflater.deflate(buf);
					out.write(buf, 0, n);
				}
				blocks[level] = out.toByteArray();
			}
		} finally {
			deflater.end();
		}
		int total = 12 + LEVELS * 8;
		for (byte[] b : blocks) {
			total += b.length;
		}
		ByteBuffer out = ByteBuffer.allocate(total);
		out.putInt(MAGIC).putInt(VERSION).putInt(this.palette);
		for (byte[] b : blocks) {
			CRC32 crc = new CRC32();
			crc.update(b);
			out.putInt(b.length).putInt((int) crc.getValue());
		}
		for (byte[] b : blocks) {
			out.put(b);
		}
		return out.array();
	}

	/** The palette an encoded image was drawn with, or -1 if the bytes are not an image. */
	public static int paletteOf(byte[] data) {
		if (data.length < 12) {
			return -1;
		}
		ByteBuffer in = ByteBuffer.wrap(data);
		if (in.getInt() != MAGIC || in.getInt() != VERSION) {
			return -1;
		}
		return in.getInt();
	}

	/**
	 * Reads the levels in {@code levelMask} (bit {@code L} for level L) of an encoded image; throws if the file is
	 * cut short or a level's checksum is wrong.
	 */
	public static RegionImage decode(byte[] data, int x, int z, int levelMask) throws IOException {
		if (data.length < 12 + LEVELS * 8) {
			throw new IOException("truncated image header");
		}
		ByteBuffer in = ByteBuffer.wrap(data);
		if (in.getInt() != MAGIC) {
			throw new IOException("bad magic");
		}
		if (in.getInt() != VERSION) {
			throw new IOException("unsupported version");
		}
		RegionImage image = new RegionImage(x, z, in.getInt());
		int[] lengths = new int[LEVELS];
		int[] sums = new int[LEVELS];
		long total = 12 + LEVELS * 8L;
		for (int level = 0; level < LEVELS; level++) {
			lengths[level] = in.getInt();
			sums[level] = in.getInt();
			if (lengths[level] < 0) {
				throw new IOException("negative length");
			}
			total += lengths[level];
		}
		if (total != data.length) {
			throw new IOException("length mismatch: " + total + " != " + data.length);
		}
		int offset = 12 + LEVELS * 8;
		Inflater inflater = new Inflater();
		try {
			for (int level = 0; level < LEVELS; level++) {
				if ((levelMask & (1 << level)) != 0) {
					CRC32 crc = new CRC32();
					crc.update(data, offset, lengths[level]);
					if ((int) crc.getValue() != sums[level]) {
						throw new IOException("checksum mismatch at level " + level);
					}
					int size = Coords.regionImageSize(level);
					byte[] raw = new byte[size * size * 4];
					inflater.reset();
					inflater.setInput(data, offset, lengths[level]);
					int n = 0;
					while (n < raw.length && !inflater.finished()) {
						int got = inflater.inflate(raw, n, raw.length - n);
						if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
							break;
						}
						n += got;
					}
					if (n != raw.length) {
						throw new IOException("level " + level + " is short: " + n + " of " + raw.length);
					}
					int[] pixels = new int[size * size];
					ByteBuffer.wrap(raw).asIntBuffer().get(pixels);
					image.levels[level] = pixels;
				}
				offset += lengths[level];
			}
		} catch (DataFormatException e) {
			throw new IOException(e);
		} finally {
			inflater.end();
		}
		return image;
	}
}
