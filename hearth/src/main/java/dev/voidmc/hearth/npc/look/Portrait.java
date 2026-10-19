package dev.voidmc.hearth.npc.look;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A resident's 16×16 portrait, cut from their own composed skin ({@link SkinComposer}): head and hat over the top of the
 * body, jacket and arms, as a bust facing the viewer. Pure, so it can be made anywhere and tested; the client caches it
 * per look (docs/OVERLAY.md {@code portrait}) and hands it to a host as a PNG data URL.
 */
public final class Portrait {
	public static final int SIZE = 16;
	private static final int SKIN = 64;

	private Portrait() {
	}

	/** The portrait of a look: 16×16 ARGB pixels, row by row, transparent around the bust. */
	public static int[] of(FolkLook look) {
		return bust(SkinComposer.compose(look), look.slim());
	}

	/**
	 * Cuts a bust from a 64×64 ARGB skin: the head's front (and the hat layer over it) at the top, then the first seven
	 * rows of the body's and arms' fronts (and the jacket and sleeves over them).
	 */
	public static int[] bust(int[] skin, boolean slim) {
		if (skin.length < SKIN * SKIN) {
			throw new IllegalArgumentException("a skin is 64×64");
		}
		int[] out = new int[SIZE * SIZE];
		int arm = slim ? 3 : 4;
		// head and hat: 8×8 at (4, 1)
		copy(skin, 8, 8, 8, 8, out, 4, 1, false);
		copy(skin, 40, 8, 8, 8, out, 4, 1, true);
		// body and jacket: the top 7 rows at (4, 9)
		copy(skin, 20, 20, 8, 7, out, 4, 9, false);
		copy(skin, 20, 36, 8, 7, out, 4, 9, true);
		// the right arm (on the viewer's left) and its sleeve, flush with the body
		copy(skin, 44, 20, arm, 7, out, 4 - arm, 9, false);
		copy(skin, 44, 36, arm, 7, out, 4 - arm, 9, true);
		// the left arm and its sleeve
		copy(skin, 36, 52, arm, 7, out, 12, 9, false);
		copy(skin, 52, 52, arm, 7, out, 12, 9, true);
		return out;
	}

	private static void copy(int[] skin, int sx, int sy, int w, int h, int[] out, int dx, int dy, boolean overlay) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = skin[(sy + y) * SKIN + sx + x];
				int a = c >>> 24;
				if (overlay && a == 0) {
					continue;
				}
				int i = (dy + y) * SIZE + dx + x;
				out[i] = overlay && a < 255 ? blend(out[i], c) : overlay ? c : c | 0xFF000000;
			}
		}
	}

	private static int blend(int under, int over) {
		int a = over >>> 24;
		int r = (((over >> 16) & 0xFF) * a + ((under >> 16) & 0xFF) * (255 - a)) / 255;
		int g = (((over >> 8) & 0xFF) * a + ((under >> 8) & 0xFF) * (255 - a)) / 255;
		int b = ((over & 0xFF) * a + (under & 0xFF) * (255 - a)) / 255;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** ARGB pixels as a PNG file (8-bit RGBA, no filtering). */
	public static byte[] png(int[] argb, int width, int height) {
		ByteArrayOutputStream raw = new ByteArrayOutputStream(height * (width * 4 + 1));
		for (int y = 0; y < height; y++) {
			raw.write(0);
			for (int x = 0; x < width; x++) {
				int c = argb[y * width + x];
				raw.write(c >> 16 & 0xFF);
				raw.write(c >> 8 & 0xFF);
				raw.write(c & 0xFF);
				raw.write(c >>> 24);
			}
		}
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		deflater.setInput(raw.toByteArray());
		deflater.finish();
		ByteArrayOutputStream idat = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		while (!deflater.finished()) {
			int n = deflater.deflate(buf);
			idat.write(buf, 0, n);
		}
		deflater.end();
		ByteArrayOutputStream file = new ByteArrayOutputStream();
		file.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
		byte[] header = new byte[13];
		int32(header, 0, width);
		int32(header, 4, height);
		header[8] = 8;  // bit depth
		header[9] = 6;  // RGBA
		chunk(file, "IHDR", header);
		chunk(file, "IDAT", idat.toByteArray());
		chunk(file, "IEND", new byte[0]);
		return file.toByteArray();
	}

	/** A portrait as a {@code data:image/png;base64,...} URL, for an HTML host. */
	public static String dataUrl(int[] argb) {
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(png(argb, SIZE, SIZE));
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
		byte[] len = new byte[4];
		int32(len, 0, data.length);
		out.writeBytes(len);
		byte[] t = type.getBytes(StandardCharsets.US_ASCII);
		out.writeBytes(t);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(t);
		crc.update(data);
		byte[] c = new byte[4];
		int32(c, 0, (int) crc.getValue());
		out.writeBytes(c);
	}

	private static void int32(byte[] b, int at, int v) {
		b[at] = (byte) (v >>> 24);
		b[at + 1] = (byte) (v >>> 16);
		b[at + 2] = (byte) (v >>> 8);
		b[at + 3] = (byte) v;
	}
}
