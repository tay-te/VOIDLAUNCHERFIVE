package dev.voidmc.atlas.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * The envelope every Atlas file is written in: magic, version, length and a CRC-32 of the payload, so a file cut short
 * by a crash, or garbled on disk, is recognised as such and rebuilt instead of read as nonsense.
 */
public final class Frame {
	public static final int HEADER = 16;

	private Frame() {
	}

	public static byte[] wrap(int magic, int version, byte[] payload) {
		CRC32 crc = new CRC32();
		crc.update(payload);
		ByteBuffer out = ByteBuffer.allocate(HEADER + payload.length);
		out.putInt(magic).putInt(version).putInt(payload.length).putInt((int) crc.getValue()).put(payload);
		return out.array();
	}

	public static byte[] unwrap(int magic, int version, byte[] data) throws IOException {
		if (data.length < HEADER) {
			throw new IOException("truncated header (" + data.length + " bytes)");
		}
		ByteBuffer in = ByteBuffer.wrap(data);
		int m = in.getInt();
		int v = in.getInt();
		int length = in.getInt();
		int sum = in.getInt();
		if (m != magic) {
			throw new IOException(String.format("bad magic %08x", m));
		}
		if (v != version) {
			throw new IOException("unsupported version " + v);
		}
		if (length < 0 || length != data.length - HEADER) {
			throw new IOException("length " + length + " but " + (data.length - HEADER) + " bytes follow");
		}
		byte[] payload = new byte[length];
		in.get(payload);
		CRC32 crc = new CRC32();
		crc.update(payload);
		if ((int) crc.getValue() != sum) {
			throw new IOException("checksum mismatch");
		}
		return payload;
	}
}
