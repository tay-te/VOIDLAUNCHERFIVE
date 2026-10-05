package dev.voidmc.atlas.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * An area of the map as a PNG: the region images at the level that keeps it under a size limit, stitched, with the
 * unexplored left transparent. Worker thread; the PNG is written by hand (no AWT), so it works wherever the game does.
 */
public final class MapExport {
	/** The longest side an export may have, in pixels. */
	public static final int MAX_SIDE = 8192;

	/** The pixels (ARGB) of a region at a level, or null if it was never seen. */
	@FunctionalInterface
	public interface Pixels {
		int[] region(int rx, int rz, int level);
	}

	/** What was written: the file, its size in pixels, and how many blocks each pixel is. */
	public record Result(Path file, int width, int height, int blocksPerPixel) {
	}

	private MapExport() {
	}

	/** The level (0 = a pixel a block) that fits an area of {@code w} x {@code h} blocks into {@link #MAX_SIDE}. */
	public static int levelFor(int w, int h) {
		int level = 0;
		while (level < Coords.MAX_LEVEL && (Math.max(w, h) >> level) > MAX_SIDE) {
			level++;
		}
		return level;
	}

	/** Writes the area from (x0, z0) to (x1, z1) in blocks (inclusive, exclusive) to {@code file}, a pixel a block or less. */
	public static Result export(Pixels pixels, int x0, int z0, int x1, int z1, Path file) throws IOException {
		return export(pixels, x0, z0, x1, z1, file, 1, 0);
	}

	/** The size in pixels an area comes out at with {@code pixelsPerBlock} (1, 2 or 4), kept under {@link #MAX_SIDE}. */
	public static int[] size(int x0, int z0, int x1, int z1, int pixelsPerBlock) {
		int up = upscale(x1 - x0, z1 - z0, pixelsPerBlock);
		if (up > 1) {
			return new int[]{(x1 - x0) * up, (z1 - z0) * up};
		}
		int scale = 1 << levelFor(x1 - x0, z1 - z0);
		return new int[]{Math.max(1, (x1 - x0) / scale), Math.max(1, (z1 - z0) / scale)};
	}

	private static int upscale(int w, int h, int pixelsPerBlock) {
		int up = Math.max(1, pixelsPerBlock);
		while (up > 1 && (long) Math.max(w, h) * up > MAX_SIDE) {
			up /= 2;
		}
		return up;
	}

	/**
	 * Writes an area at {@code pixelsPerBlock} (1, 2 or 4: each block a square of pixels; less than one if the area is
	 * too big for {@link #MAX_SIDE}), the unexplored filled with {@code background} (ARGB; 0 for transparent).
	 */
	public static Result export(Pixels pixels, int x0, int z0, int x1, int z1, Path file, int pixelsPerBlock, int background) throws IOException {
		if (x1 <= x0 || z1 <= z0) {
			throw new IllegalArgumentException("an empty area");
		}
		int up = upscale(x1 - x0, z1 - z0, pixelsPerBlock);
		int level = up > 1 ? 0 : levelFor(x1 - x0, z1 - z0);
		int scale = 1 << level;
		int w = Math.max(1, (x1 - x0) / scale);
		int h = Math.max(1, (z1 - z0) / scale);
		int[] out = new int[w * h];
		if (background != 0) {
			java.util.Arrays.fill(out, background);
		}
		int size = Coords.regionImageSize(level);
		int rx0 = Math.floorDiv(x0, Coords.REGION_SIZE);
		int rz0 = Math.floorDiv(z0, Coords.REGION_SIZE);
		int rx1 = Math.floorDiv(x1 - 1, Coords.REGION_SIZE);
		int rz1 = Math.floorDiv(z1 - 1, Coords.REGION_SIZE);
		for (int rz = rz0; rz <= rz1; rz++) {
			for (int rx = rx0; rx <= rx1; rx++) {
				int[] region = pixels.region(rx, rz, level);
				if (region == null) {
					continue;
				}
				// where this region's pixels land in the picture
				int ox = Math.floorDiv(rx * Coords.REGION_SIZE - x0, scale);
				int oz = Math.floorDiv(rz * Coords.REGION_SIZE - z0, scale);
				for (int py = Math.max(0, -oz); py < size && oz + py < h; py++) {
					int src = py * size;
					int dst = (oz + py) * w;
					for (int px = Math.max(0, -ox); px < size && ox + px < w; px++) {
						int c = region[src + px];
						if (c >>> 24 != 0 || background == 0) {
							out[dst + ox + px] = c;
						}
					}
				}
			}
		}
		if (up > 1) {
			// each block a square of pixels
			int[] big = new int[w * up * h * up];
			for (int y = 0; y < h * up; y++) {
				int srcRow = (y / up) * w;
				int dstRow = y * w * up;
				for (int x = 0; x < w * up; x++) {
					big[dstRow + x] = out[srcRow + x / up];
				}
			}
			out = big;
			w *= up;
			h *= up;
		}
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (OutputStream o = Files.newOutputStream(tmp)) {
			writePng(o, out, w, h);
		}
		Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return new Result(file, w, h, scale);
	}

	/** A PNG of ARGB pixels: 8-bit RGBA, no interlacing. */
	public static void writePng(OutputStream out, int[] argb, int w, int h) throws IOException {
		out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
		ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
		writeInt(ihdr, w);
		writeInt(ihdr, h);
		ihdr.write(new byte[]{8, 6, 0, 0, 0});
		chunk(out, "IHDR", ihdr.toByteArray());
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		Deflater deflater = new Deflater(6);
		try (DeflaterOutputStream z = new DeflaterOutputStream(raw, deflater, 1 << 16)) {
			byte[] row = new byte[1 + w * 4];
			for (int y = 0; y < h; y++) {
				row[0] = 0;
				for (int x = 0; x < w; x++) {
					int p = argb[y * w + x];
					int i = 1 + x * 4;
					row[i] = (byte) (p >> 16);
					row[i + 1] = (byte) (p >> 8);
					row[i + 2] = (byte) p;
					row[i + 3] = (byte) (p >>> 24);
				}
				z.write(row);
			}
		} finally {
			deflater.end();
		}
		chunk(out, "IDAT", raw.toByteArray());
		chunk(out, "IEND", new byte[0]);
	}

	private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		writeInt(b, data.length);
		byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		b.write(t);
		b.write(data);
		CRC32 crc = new CRC32();
		crc.update(t);
		crc.update(data);
		writeInt(b, (int) crc.getValue());
		b.writeTo(out);
	}

	private static void writeInt(ByteArrayOutputStream out, int v) {
		out.write(v >>> 24);
		out.write(v >>> 16);
		out.write(v >>> 8);
		out.write(v);
	}
}
