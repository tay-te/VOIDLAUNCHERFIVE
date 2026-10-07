package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import dev.voidmc.atlas.api.AtlasSocialTransport;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.waypoints.CoordParser;
import dev.voidmc.atlas.core.waypoints.DimensionIds;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Explored terrain shared within a party. A player's map is cut into small deltas, each one rectangle of one region,
 * sent under a bytes-per-second budget ({@link Outbox}); friends keep what arrives in a separate {@link SharedLayer}
 * per region ({@link Inbox}), and {@link #merge} lays it under the player's own map. The rule that matters most:
 * <b>shared terrain never covers a column the player has seen themselves.</b>
 *
 * <p>A delta goes to topic {@code tiles:<dimension>:<rx>,<rz>} and is at most {@value #MAX_MESSAGE_BYTES} bytes.
 * Block and biome ids are per-map palette indices, so a delta carries <em>names</em> in its own small table. Layout,
 * big-endian; {@code str} is a u8 byte length then that many bytes of UTF-8:
 *
 * <pre>
 * u32  magic 0x41545354 ("ATST")
 * u8   format version (1)
 * u8   level L (0..4): one sample stands for 2^L x 2^L columns
 * u16  reserved (0; ignored when read)
 * str  sender player id (at most 64 bytes)
 * str  server hash (at most 128 bytes)
 * str  dimension id
 * i32  region x
 * i32  region z
 * i64  content version (the sender's region version when it was read)
 * u16  x0, u16 z0, u16 width, u16 height: the rectangle, in samples at level L inside the region
 *      (the region is 512 &gt;&gt; L samples across; width and height at least 1)
 * u32  body length once inflated (at most MAX_BODY_BYTES)
 * u32  CRC-32 of every byte before this field, then of the inflated body
 * ...  the body, a zlib (RFC 1950) deflate stream, to the end of the message
 *
 * body:
 * u16  name count N (at most MAX_NAMES), then N x str: block and biome names in one table
 *      ("" means none; others look like namespace:path)
 * bitmap of width*height bits, LSB first, sample i = dz * width + dx: set when the sample is known
 * then for the K known samples, in order, column by column:
 *   K x i16 height   K x u16 block name index   K x u16 biome name index   K x u8 water depth   K x u8 flags (2 = rock)
 * </pre>
 *
 * Anything else (wrong magic, a newer version, a rectangle outside the region, a bad name or index, a short or long
 * body, a checksum mismatch, bytes after the stream) is refused whole.
 */
public final class SharedTiles {
	public static final int MAGIC = 0x41545354;
	public static final int VERSION = 1;
	/** The largest delta sent or accepted, in bytes. */
	public static final int MAX_MESSAGE_BYTES = 48 * 1024;
	/** The largest body accepted once inflated, so a small message cannot inflate into a large one. */
	public static final int MAX_BODY_BYTES = 512 * 1024;
	/** The most samples one delta may cover. */
	public static final int MAX_SAMPLES = 128 * 128;
	public static final int MAX_NAMES = 1024;
	public static final int MAX_NAME_BYTES = 255;
	public static final int MAX_LEVEL = 4;
	/** The side of the pieces {@link Outbox} cuts a region into, in samples. */
	public static final int PIECE = 64;
	public static final int DEFAULT_BYTES_PER_SECOND = 64 * 1024;

	private static final Pattern NAME = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_.\\-/]+");
	private static final int REGION_LIMIT = (CoordParser.XZ_LIMIT >> Coords.REGION_SHIFT) + 1;

	private SharedTiles() {
	}

	/**
	 * One delta: a rectangle of samples of one region. Arrays are per sample, row by row ({@code i = dz * width + dx});
	 * unknown samples have no names and zeros.
	 */
	public record Delta(String sender, String server, String dimension, int rx, int rz, long version, int level,
		int x0, int z0, int width, int height,
		BitSet known, short[] heights, String[] blocks, String[] biomes, byte[] depths, byte[] flags) {
		public Delta {
			Objects.requireNonNull(sender, "sender");
			Objects.requireNonNull(server, "server");
			Objects.requireNonNull(dimension, "dimension");
			if (level < 0 || level > MAX_LEVEL) {
				throw new IllegalArgumentException("level " + level);
			}
			int side = Coords.REGION_SIZE >> level;
			if (x0 < 0 || z0 < 0 || width < 1 || height < 1 || x0 + width > side || z0 + height > side) {
				throw new IllegalArgumentException("rectangle " + x0 + "," + z0 + " " + width + "x" + height + " outside the region");
			}
			int n = width * height;
			if (heights.length != n || blocks.length != n || biomes.length != n || depths.length != n || flags.length != n) {
				throw new IllegalArgumentException("arrays do not match the rectangle");
			}
		}

		public int samples() {
			return this.width * this.height;
		}

		public int knownCount() {
			return this.known.cardinality();
		}

		/** The topic this delta goes to. */
		public String topic() {
			return AtlasSocialTransport.tilesTopic(this.dimension, this.rx, this.rz);
		}
	}

	/**
	 * Reads a rectangle of a region into a delta: at level 0 every column; at level L each sample is the first known
	 * column (row by row) of its 2^L x 2^L cell. The rectangle is in samples at that level.
	 */
	public static Delta capture(String sender, String server, String dimension, RegionRaw raw, NamePalette blocks, NamePalette biomes,
		int level, int x0, int z0, int width, int height) {
		int n = width * height;
		BitSet known = new BitSet(n);
		short[] hs = new short[n];
		String[] bs = new String[n];
		String[] bis = new String[n];
		byte[] ds = new byte[n];
		byte[] fs = new byte[n];
		Delta d = new Delta(sender, server, dimension, raw.x, raw.z, raw.version(), level, x0, z0, width, height, known, hs, bs, bis, ds, fs);
		int cell = 1 << level;
		for (int dz = 0; dz < height; dz++) {
			for (int dx = 0; dx < width; dx++) {
				int col = find(raw, (x0 + dx) << level, (z0 + dz) << level, cell);
				if (col < 0) {
					continue;
				}
				int s = dz * width + dx;
				known.set(s);
				hs[s] = raw.height[col];
				bs[s] = nameOf(blocks, raw.block[col]);
				bis[s] = nameOf(biomes, raw.biome[col]);
				ds[s] = raw.depth[col];
				fs[s] = (byte) (raw.flags[col] & RegionRaw.ROCK);
			}
		}
		return d;
	}

	private static int find(RegionRaw raw, int cx, int cz, int cell) {
		for (int z = cz; z < cz + cell; z++) {
			for (int x = cx; x < cx + cell; x++) {
				int i = RegionRaw.index(x, z);
				if (raw.known(i) && raw.height[i] != RegionRaw.UNKNOWN) {
					return i;
				}
			}
		}
		return -1;
	}

	private static String nameOf(NamePalette palette, char id) {
		String name = id == NamePalette.NONE ? null : palette.name(id);
		return name != null && validName(name) ? name : "";
	}

	static boolean validName(String name) {
		return name.isEmpty() || name.length() <= MAX_NAME_BYTES && NAME.matcher(name).matches();
	}

	/** The distinct names a delta uses, in first-use order ("" for none). */
	static Map<String, Integer> nameTable(Delta d) {
		Map<String, Integer> table = new LinkedHashMap<>();
		for (int s = d.known.nextSetBit(0); s >= 0; s = d.known.nextSetBit(s + 1)) {
			table.putIfAbsent(d.blocks[s] == null ? "" : d.blocks[s], table.size());
			table.putIfAbsent(d.biomes[s] == null ? "" : d.biomes[s], table.size());
		}
		return table;
	}

	/**
	 * The message for a delta. It may be over {@link #MAX_MESSAGE_BYTES} (receivers refuse those); {@link Outbox}
	 * splits rectangles until each fits. Throws {@link IllegalArgumentException} for more than {@value #MAX_SAMPLES}
	 * samples or {@value #MAX_NAMES} names, or a name or id that cannot be sent.
	 */
	public static byte[] encode(Delta d) {
		if (d.samples() > MAX_SAMPLES) {
			throw new IllegalArgumentException(d.samples() + " samples; the most is " + MAX_SAMPLES);
		}
		Map<String, Integer> table = nameTable(d);
		if (table.size() > MAX_NAMES) {
			throw new IllegalArgumentException(table.size() + " names; the most is " + MAX_NAMES);
		}
		try {
			ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(1024 + d.samples() * 8);
			DataOutputStream body = new DataOutputStream(bodyBytes);
			body.writeShort(table.size());
			for (String name : table.keySet()) {
				if (!validName(name)) {
					throw new IllegalArgumentException("bad name " + name);
				}
				writeString(body, name, MAX_NAME_BYTES);
			}
			byte[] bitmap = new byte[(d.samples() + 7) >> 3];
			for (int s = d.known.nextSetBit(0); s >= 0 && s < d.samples(); s = d.known.nextSetBit(s + 1)) {
				bitmap[s >> 3] |= (byte) (1 << (s & 7));
			}
			body.write(bitmap);
			int[] known = d.known.stream().filter(s -> s < d.samples()).toArray();
			for (int s : known) {
				body.writeShort(d.heights[s]);
			}
			for (int s : known) {
				body.writeShort(table.get(d.blocks[s] == null ? "" : d.blocks[s]));
			}
			for (int s : known) {
				body.writeShort(table.get(d.biomes[s] == null ? "" : d.biomes[s]));
			}
			for (int s : known) {
				body.writeByte(d.depths[s]);
			}
			for (int s : known) {
				body.writeByte(d.flags[s] & RegionRaw.ROCK);
			}
			body.flush();
			byte[] plain = bodyBytes.toByteArray();

			ByteArrayOutputStream bytes = new ByteArrayOutputStream(256 + plain.length / 3);
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(MAGIC);
			out.writeByte(VERSION);
			out.writeByte(d.level);
			out.writeShort(0);
			writeString(out, d.sender, FriendPositions.MAX_ID);
			writeString(out, d.server, 128);
			writeString(out, d.dimension, 255);
			out.writeInt(d.rx);
			out.writeInt(d.rz);
			out.writeLong(d.version);
			out.writeShort(d.x0);
			out.writeShort(d.z0);
			out.writeShort(d.width);
			out.writeShort(d.height);
			out.writeInt(plain.length);
			out.flush();
			CRC32 crc = new CRC32();
			crc.update(bytes.toByteArray());
			crc.update(plain);
			out.writeInt((int) crc.getValue());
			Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
			try {
				deflater.setInput(plain);
				deflater.finish();
				byte[] buf = new byte[8192];
				while (!deflater.finished()) {
					int n = deflater.deflate(buf);
					out.write(buf, 0, n);
				}
			} finally {
				deflater.end();
			}
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void writeString(DataOutputStream out, String s, int max) throws IOException {
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		if (b.length > max) {
			throw new IllegalArgumentException("too long: " + s);
		}
		out.writeByte(b.length);
		out.write(b);
	}

	/** Reads a delta; throws {@link IOException} for anything but a whole, valid one of at most {@value #MAX_MESSAGE_BYTES} bytes. */
	public static Delta decode(byte[] message) throws IOException {
		if (message == null) {
			throw new IOException("no message");
		}
		if (message.length > MAX_MESSAGE_BYTES) {
			throw new IOException("too large (" + message.length + " bytes)");
		}
		Inflater inflater = new Inflater();
		try {
			ByteBuffer in = ByteBuffer.wrap(message);
			if (in.getInt() != MAGIC) {
				throw new IOException("not a shared tile");
			}
			int version = in.get() & 0xFF;
			if (version != VERSION) {
				throw new IOException(version > VERSION ? "from a newer Atlas (format " + version + ")" : "bad version " + version);
			}
			int level = in.get() & 0xFF;
			if (level > MAX_LEVEL) {
				throw new IOException("bad level " + level);
			}
			in.getShort();
			String sender = readString(in, FriendPositions.MAX_ID);
			String server = readString(in, 128);
			String dimension = readString(in, 255);
			if (sender.isBlank() || server.isBlank() || !dimension.equals(DimensionIds.normalise(dimension))) {
				throw new IOException("bad sender, server or dimension");
			}
			int rx = in.getInt();
			int rz = in.getInt();
			if (Math.abs((long) rx) > REGION_LIMIT || Math.abs((long) rz) > REGION_LIMIT) {
				throw new IOException("region outside the world");
			}
			long contentVersion = in.getLong();
			int x0 = in.getShort() & 0xFFFF;
			int z0 = in.getShort() & 0xFFFF;
			int width = in.getShort() & 0xFFFF;
			int height = in.getShort() & 0xFFFF;
			int side = Coords.REGION_SIZE >> level;
			if (width < 1 || height < 1 || x0 + width > side || z0 + height > side || width * height > MAX_SAMPLES) {
				throw new IOException("bad rectangle");
			}
			long bodyLength = in.getInt() & 0xFFFFFFFFL;
			int headerLength = in.position();
			int sum = in.getInt();
			if (bodyLength > MAX_BODY_BYTES) {
				throw new IOException("body too large (" + bodyLength + ")");
			}
			byte[] plain = new byte[(int) bodyLength];
			inflater.setInput(message, in.position(), in.remaining());
			int got = 0;
			while (got < plain.length) {
				int n = inflater.inflate(plain, got, plain.length - got);
				if (n == 0) {
					break;
				}
				got += n;
			}
			if (got != plain.length) {
				throw new IOException("body cut short");
			}
			if (inflater.inflate(new byte[1]) != 0 || !inflater.finished() || inflater.getRemaining() != 0) {
				throw new IOException("body longer than it says");
			}
			CRC32 crc = new CRC32();
			crc.update(message, 0, headerLength);
			crc.update(plain);
			if ((int) crc.getValue() != sum) {
				throw new IOException("checksum mismatch");
			}
			return readBody(ByteBuffer.wrap(plain), sender, server, dimension, rx, rz, contentVersion, level, x0, z0, width, height);
		} catch (BufferUnderflowException | DataFormatException e) {
			throw new IOException("damaged: " + e);
		} catch (RuntimeException e) {
			throw new IOException("damaged: " + e, e);
		} finally {
			inflater.end();
		}
	}

	private static Delta readBody(ByteBuffer body, String sender, String server, String dimension, int rx, int rz, long version,
		int level, int x0, int z0, int width, int height) throws IOException {
		int names = body.getShort() & 0xFFFF;
		if (names > MAX_NAMES) {
			throw new IOException(names + " names");
		}
		String[] table = new String[names];
		for (int k = 0; k < names; k++) {
			table[k] = readString(body, MAX_NAME_BYTES);
			if (!validName(table[k])) {
				throw new IOException("bad name");
			}
		}
		int n = width * height;
		byte[] bitmap = new byte[(n + 7) >> 3];
		body.get(bitmap);
		BitSet known = BitSet.valueOf(bitmap);
		if (known.length() > n) {
			throw new IOException("bits past the rectangle");
		}
		int count = known.cardinality();
		if (body.remaining() != count * 8) {
			throw new IOException("body is " + body.remaining() + " bytes, " + count * 8 + " expected");
		}
		short[] hs = new short[n];
		String[] bs = new String[n];
		String[] bis = new String[n];
		byte[] ds = new byte[n];
		byte[] fs = new byte[n];
		int[] order = known.stream().toArray();
		for (int s : order) {
			hs[s] = body.getShort();
			if (hs[s] == RegionRaw.UNKNOWN) {
				throw new IOException("known sample with no height");
			}
		}
		for (int s : order) {
			bs[s] = name(table, body.getShort() & 0xFFFF);
		}
		for (int s : order) {
			bis[s] = name(table, body.getShort() & 0xFFFF);
		}
		for (int s : order) {
			ds[s] = body.get();
		}
		for (int s : order) {
			fs[s] = (byte) (body.get() & RegionRaw.ROCK);
		}
		return new Delta(sender, server, dimension, rx, rz, version, level, x0, z0, width, height, known, hs, bs, bis, ds, fs);
	}

	private static String name(String[] table, int index) throws IOException {
		if (index >= table.length) {
			throw new IOException("name index " + index + " of " + table.length);
		}
		return table[index];
	}

	private static String readString(ByteBuffer in, int max) throws IOException {
		int length = in.get() & 0xFF;
		if (length > max) {
			throw new IOException("string too long");
		}
		byte[] b = new byte[length];
		in.get(b);
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(b))
				.toString();
		} catch (CharacterCodingException e) {
			throw new IOException("not UTF-8");
		}
	}

	/**
	 * The player's own region with what friends shared laid under it: a new region (neither input is changed) where
	 * every column the player knows is exactly theirs, and every column only a friend knows is the friend's, marked
	 * in {@link Merged#fromFriend}. Either input may be null (not both); they must be the same region.
	 */
	public static Merged merge(RegionRaw own, SharedLayer shared) {
		if (own == null && shared == null) {
			throw new IllegalArgumentException("nothing to merge");
		}
		if (own != null && shared != null && (own.x != shared.x || own.z != shared.z)) {
			throw new IllegalArgumentException("different regions: " + own.x + "," + own.z + " and " + shared.x + "," + shared.z);
		}
		RegionRaw out = own == null ? new RegionRaw(shared.x, shared.z) : new RegionRaw(own.x, own.z);
		if (own != null) {
			System.arraycopy(own.height, 0, out.height, 0, RegionRaw.AREA);
			System.arraycopy(own.block, 0, out.block, 0, RegionRaw.AREA);
			System.arraycopy(own.depth, 0, out.depth, 0, RegionRaw.AREA);
			System.arraycopy(own.biome, 0, out.biome, 0, RegionRaw.AREA);
			System.arraycopy(own.flags, 0, out.flags, 0, RegionRaw.AREA);
		}
		BitSet friend = new BitSet(RegionRaw.AREA);
		if (shared != null) {
			for (int i = 0; i < RegionRaw.AREA; i++) {
				if ((out.flags[i] & RegionRaw.KNOWN) != 0 || !shared.known(i)) {
					continue;
				}
				out.height[i] = shared.height[i];
				out.block[i] = shared.block[i];
				out.depth[i] = shared.depth[i];
				out.biome[i] = shared.biome[i];
				out.flags[i] = (byte) (RegionRaw.KNOWN | (shared.flags[i] & RegionRaw.ROCK));
				friend.set(i);
			}
		}
		return new Merged(out, friend, shared);
	}

	/**
	 * A merged region.
	 *
	 * @param raw        the player's columns, with friends' filling only where the player knew nothing
	 * @param fromFriend set for each column that came from a friend (a parallel array, so {@code raw.flags} keep
	 *                   their meaning)
	 * @param shared     where those columns came from, for {@link #sender}
	 */
	public record Merged(RegionRaw raw, BitSet fromFriend, SharedLayer shared) {
		public boolean fromFriend(int i) {
			return this.fromFriend.get(i);
		}

		/** Who shared a column, or null for the player's own and unknown columns. */
		public String sender(int i) {
			return this.fromFriend.get(i) && this.shared != null ? this.shared.sender(i) : null;
		}

		public int friendColumns() {
			return this.fromFriend.cardinality();
		}
	}

	/** One message to publish. */
	public record Message(String topic, byte[] payload) {
	}

	private record Key(String dimension, int rx, int rz) {
	}

	/**
	 * Sends this player's explored terrain as deltas. Tell it what changed ({@link #changed}, or {@link #changedRegion}
	 * to share a whole region); repeated changes to the same area wait as one. {@link #drain} then cuts the changed
	 * areas into pieces of at most {@value #PIECE} x {@value #PIECE} samples (smaller when a piece does not fit in
	 * {@value #MAX_MESSAGE_BYTES} bytes) and lets out only what the byte budget allows: a token bucket that fills at
	 * the budget's rate and holds at most one second of it (or one largest message, if more). A piece is read when it
	 * is sent, so it carries the newest columns. Thread-safe.
	 */
	public static final class Outbox {
		/** Pieces encoded per {@link #drain} at most, so one call never does much work. */
		public static final int MAX_ENCODES_PER_DRAIN = 16;
		/** Regions waiting at most; past it the longest-waiting is dropped (it goes again when it next changes). */
		public static final int MAX_PENDING_REGIONS = 1024;

		private final String sender;
		private final NamePalette blocks;
		private final NamePalette biomes;
		private final LinkedHashMap<Key, Pending> pending = new LinkedHashMap<>();
		private final ArrayDeque<Message> held = new ArrayDeque<>();
		private int bytesPerSecond = DEFAULT_BYTES_PER_SECOND;
		private int level;
		private String server;
		private Privacy privacy = Privacy.DEFAULT;
		private double tokens;
		private long refilledAt;
		private boolean started;
		private long sentBytes;
		private long sentMessages;

		public Outbox(String sender, NamePalette blocks, NamePalette biomes) {
			this.sender = Objects.requireNonNull(sender, "sender");
			this.blocks = Objects.requireNonNull(blocks, "blocks");
			this.biomes = Objects.requireNonNull(biomes, "biomes");
		}

		/** The budget, in bytes per second (at least 1 KB/s). */
		public synchronized Outbox bytesPerSecond(int rate) {
			this.bytesPerSecond = Math.max(1024, rate);
			this.tokens = Math.min(this.tokens, capacity());
			return this;
		}

		/** The detail level to share at, 0 (every column) to {@value #MAX_LEVEL}. */
		public synchronized Outbox level(int level) {
			this.level = Math.max(0, Math.min(MAX_LEVEL, level));
			return this;
		}

		/** The server this player is on and how much they share; with no server or {@link Privacy#NOBODY} nothing is sent and what waits is dropped. */
		public synchronized void local(String serverHash, Privacy privacy) {
			this.server = serverHash == null || serverHash.isBlank() ? null : serverHash;
			this.privacy = privacy == null ? Privacy.DEFAULT : privacy;
			if (!sharing()) {
				this.pending.clear();
				this.held.clear();
			}
		}

		/** Columns {@code x0..x0+width-1}, {@code z0..z0+height-1} (inside the region) of a region changed. */
		public synchronized void changed(String dimension, RegionRaw raw, int x0, int z0, int width, int height) {
			if (!sharing() || raw == null || dimension == null) {
				return;
			}
			int ax = Math.max(0, x0);
			int az = Math.max(0, z0);
			int bx = Math.min(RegionRaw.SIZE, x0 + width) - 1;
			int bz = Math.min(RegionRaw.SIZE, z0 + height) - 1;
			if (bx < ax || bz < az) {
				return;
			}
			Key key = new Key(dimension, raw.x, raw.z);
			Pending p = this.pending.get(key);
			if (p == null) {
				if (this.pending.size() >= MAX_PENDING_REGIONS) {
					Iterator<Key> it = this.pending.keySet().iterator();
					it.next();
					it.remove();
				}
				p = new Pending(raw, Math.min(RegionRaw.SIZE, PIECE << this.level));
				this.pending.put(key, p);
			}
			p.raw = raw;
			p.mark(ax, az, bx, bz);
		}

		/** The whole region, to share all of it (it still goes piece by piece). */
		public void changedRegion(String dimension, RegionRaw raw) {
			changed(dimension, raw, 0, 0, RegionRaw.SIZE, RegionRaw.SIZE);
		}

		/** Pieces waiting to be read and sent (not counting messages already cut and held for budget). */
		public synchronized int pendingPieces() {
			int n = 0;
			for (Pending p : this.pending.values()) {
				n += p.dirty;
			}
			return n;
		}

		/** Messages cut and waiting for budget. */
		public synchronized int heldMessages() {
			return this.held.size();
		}

		public synchronized long sentBytes() {
			return this.sentBytes;
		}

		public synchronized long sentMessages() {
			return this.sentMessages;
		}

		/** The messages the budget allows at {@code now} (milliseconds, any steady clock), in order. */
		public synchronized List<Message> drain(long now) {
			if (!sharing()) {
				return List.of();
			}
			refill(now);
			List<Message> out = new ArrayList<>();
			int encodes = 0;
			while (true) {
				Message next = this.held.peek();
				if (next == null) {
					if (encodes >= MAX_ENCODES_PER_DRAIN || !cutNext()) {
						break;
					}
					encodes++;
					continue;
				}
				if (this.tokens < next.payload().length) {
					break;
				}
				this.tokens -= next.payload().length;
				this.held.poll();
				this.sentBytes += next.payload().length;
				this.sentMessages++;
				out.add(next);
			}
			return out;
		}

		/** {@link #drain}s into a transport; does nothing (and keeps everything waiting) when it is not available. */
		public int pump(AtlasSocialTransport transport, long now) {
			if (!transport.available()) {
				return 0;
			}
			List<Message> out = drain(now);
			for (Message m : out) {
				transport.publish(m.topic(), m.payload());
			}
			return out.size();
		}

		private boolean sharing() {
			return this.server != null && this.privacy != Privacy.NOBODY;
		}

		private double capacity() {
			return Math.max(this.bytesPerSecond, MAX_MESSAGE_BYTES);
		}

		private void refill(long now) {
			if (!this.started) {
				this.started = true;
				this.tokens = capacity();
				this.refilledAt = now;
			} else if (now > this.refilledAt) {
				this.tokens = Math.min(capacity(), this.tokens + (now - this.refilledAt) * (double) this.bytesPerSecond / 1000.0);
				this.refilledAt = now;
			}
		}

		/** Reads the next changed piece into held messages; false when nothing has changed. */
		private boolean cutNext() {
			Iterator<Map.Entry<Key, Pending>> it = this.pending.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Key, Pending> e = it.next();
				Pending p = e.getValue();
				int piece = p.next();
				if (piece < 0) {
					it.remove();
					continue;
				}
				int ax = p.minX[piece];
				int az = p.minZ[piece];
				int bx = p.maxX[piece];
				int bz = p.maxZ[piece];
				p.clear(piece);
				if (p.dirty == 0) {
					it.remove();
				}
				int l = this.level;
				cut(e.getKey(), p.raw, l, ax >> l, az >> l, (bx >> l) - (ax >> l) + 1, (bz >> l) - (az >> l) + 1);
				return true;
			}
			return false;
		}

		private void cut(Key key, RegionRaw raw, int level, int x0, int z0, int width, int height) {
			if (width * height <= MAX_SAMPLES) {
				Delta d = capture(this.sender, this.server, key.dimension, raw, this.blocks, this.biomes, level, x0, z0, width, height);
				if (d.knownCount() == 0) {
					return;
				}
				byte[] bytes = nameTable(d).size() <= MAX_NAMES ? encode(d) : null;
				if (bytes != null && bytes.length <= MAX_MESSAGE_BYTES) {
					this.held.add(new Message(d.topic(), bytes));
					return;
				}
			}
			if (width >= height && width > 1) {
				int half = width / 2;
				cut(key, raw, level, x0, z0, half, height);
				cut(key, raw, level, x0 + half, z0, width - half, height);
			} else if (height > 1) {
				int half = height / 2;
				cut(key, raw, level, x0, z0, width, half);
				cut(key, raw, level, x0, z0 + half, width, height - half);
			}
		}

		/** One region's changed pieces: per piece, the bounding box of what changed, in columns (-1 when clean). */
		private static final class Pending {
			RegionRaw raw;
			final int pieceColumns;
			final int across;
			final int[] minX;
			final int[] minZ;
			final int[] maxX;
			final int[] maxZ;
			int dirty;
			int cursor;

			Pending(RegionRaw raw, int pieceColumns) {
				this.raw = raw;
				this.pieceColumns = pieceColumns;
				this.across = RegionRaw.SIZE / pieceColumns;
				int n = this.across * this.across;
				this.minX = new int[n];
				this.minZ = new int[n];
				this.maxX = new int[n];
				this.maxZ = new int[n];
				Arrays.fill(this.minX, -1);
			}

			void mark(int ax, int az, int bx, int bz) {
				for (int pz = az / this.pieceColumns; pz <= bz / this.pieceColumns; pz++) {
					for (int px = ax / this.pieceColumns; px <= bx / this.pieceColumns; px++) {
						int k = pz * this.across + px;
						int lx = Math.max(ax, px * this.pieceColumns);
						int lz = Math.max(az, pz * this.pieceColumns);
						int hx = Math.min(bx, (px + 1) * this.pieceColumns - 1);
						int hz = Math.min(bz, (pz + 1) * this.pieceColumns - 1);
						if (this.minX[k] < 0) {
							this.minX[k] = lx;
							this.minZ[k] = lz;
							this.maxX[k] = hx;
							this.maxZ[k] = hz;
							this.dirty++;
						} else {
							this.minX[k] = Math.min(this.minX[k], lx);
							this.minZ[k] = Math.min(this.minZ[k], lz);
							this.maxX[k] = Math.max(this.maxX[k], hx);
							this.maxZ[k] = Math.max(this.maxZ[k], hz);
						}
					}
				}
			}

			/** The next dirty piece, in scan order from where the last one was taken. */
			int next() {
				int n = this.minX.length;
				for (int j = 0; j < n; j++) {
					int k = (this.cursor + j) % n;
					if (this.minX[k] >= 0) {
						this.cursor = k;
						return k;
					}
				}
				return -1;
			}

			void clear(int k) {
				this.minX[k] = -1;
				this.dirty--;
			}
		}
	}

	/**
	 * Receives friends' deltas into a {@link SharedLayer} per region, separate from the player's own map. Drops a
	 * delta from this player, from a peer {@link Privacy} does not allow, from another server, whose inner sender is
	 * not the transport's, or that does not read. Names are turned into ids in this player's own palettes; at most
	 * {@value #MAX_NEW_NAMES} names new to those palettes are added through sharing, so a peer cannot fill them. At
	 * most {@code maxRegions} layers are kept (the least recently used goes first). Thread-safe.
	 */
	public static final class Inbox {
		public static final int DEFAULT_MAX_REGIONS = 64;
		public static final int MAX_NEW_NAMES = 2048;

		private final String localPlayer;
		private final Roster roster;
		private final NamePalette blocks;
		private final NamePalette biomes;
		private final LinkedHashMap<Key, SharedLayer> layers = new LinkedHashMap<>(16, 0.75f, true);
		private final Map<String, Character> blockIds = new HashMap<>();
		private final Map<String, Character> biomeIds = new HashMap<>();
		private int newNames;
		private int maxRegions = DEFAULT_MAX_REGIONS;
		private String server;
		private Privacy privacy = Privacy.DEFAULT;

		public Inbox(String localPlayer, Roster roster, NamePalette blocks, NamePalette biomes) {
			this.localPlayer = Objects.requireNonNull(localPlayer, "localPlayer");
			this.roster = Objects.requireNonNull(roster, "roster");
			this.blocks = Objects.requireNonNull(blocks, "blocks");
			this.biomes = Objects.requireNonNull(biomes, "biomes");
		}

		public synchronized Inbox maxRegions(int regions) {
			this.maxRegions = Math.max(1, regions);
			evict();
			return this;
		}

		/** The server this player is on and how much they share. Moving to another server forgets everything shared. */
		public synchronized void local(String serverHash, Privacy privacy) {
			String next = serverHash == null || serverHash.isBlank() ? null : serverHash;
			if (!Objects.equals(next, this.server)) {
				this.layers.clear();
			}
			this.server = next;
			this.privacy = privacy == null ? Privacy.DEFAULT : privacy;
		}

		/** Reads one delta the transport says came from {@code fromPlayer}. */
		public Received receive(String fromPlayer, byte[] payload) {
			return receive(fromPlayer, null, payload);
		}

		/** As {@link #receive(String, byte[])}, also checking the delta belongs on {@code topic} (null to skip). */
		public Received receive(String fromPlayer, String topic, byte[] payload) {
			synchronized (this) {
				if (fromPlayer == null || fromPlayer.equals(this.localPlayer)) {
					return Received.FROM_SELF;
				}
				if (!this.roster.mayShare(this.privacy, fromPlayer)) {
					return Received.NOT_ALLOWED;
				}
				if (this.server == null) {
					return Received.OTHER_SERVER;
				}
			}
			if (payload == null || payload.length > MAX_MESSAGE_BYTES) {
				return Received.TOO_LARGE;
			}
			Delta d;
			try {
				d = decode(payload);
			} catch (IOException e) {
				return Received.MALFORMED;
			}
			if (!d.sender().equals(fromPlayer)) {
				return Received.WRONG_SENDER;
			}
			if (topic != null && !topic.equals(d.topic())) {
				return Received.MALFORMED;
			}
			synchronized (this) {
				if (!d.server().equals(this.server)) {
					return Received.OTHER_SERVER;
				}
				Key key = new Key(d.dimension(), d.rx(), d.rz());
				SharedLayer layer = this.layers.get(key);
				if (layer == null) {
					layer = new SharedLayer(d.dimension(), d.rx(), d.rz());
					this.layers.put(key, layer);
					evict();
				}
				int slot = layer.senderSlot(fromPlayer);
				if (slot == 0) {
					return Received.FULL;
				}
				apply(d, layer, slot);
				return Received.ACCEPTED;
			}
		}

		/** What friends shared of a region, or null. */
		public synchronized SharedLayer layer(String dimension, int rx, int rz) {
			return this.layers.get(new Key(dimension, rx, rz));
		}

		/** Every region of a dimension friends shared some of. */
		public synchronized List<SharedLayer> layers(String dimension) {
			List<SharedLayer> out = new ArrayList<>();
			for (SharedLayer l : this.layers.values()) {
				if (l.dimension.equals(dimension)) {
					out.add(l);
				}
			}
			return out;
		}

		/** Forgets everything one peer shared (they left the party, or were unfriended); returns how many columns. */
		public synchronized int forget(String player) {
			int n = 0;
			for (SharedLayer l : this.layers.values()) {
				n += l.forget(player);
			}
			return n;
		}

		public synchronized void clear() {
			this.layers.clear();
		}

		/** Feeds every terrain message the transport delivers into this. */
		public AutoCloseable listen(AtlasSocialTransport transport) {
			return transport.subscribe(AtlasSocialTransport.TILES_PREFIX + "*", this::receive);
		}

		private void apply(Delta d, SharedLayer layer, int slot) {
			int cell = 1 << d.level();
			for (int s = d.known().nextSetBit(0); s >= 0; s = d.known().nextSetBit(s + 1)) {
				int dx = s % d.width();
				int dz = s / d.width();
				char block = id(this.blocks, this.blockIds, d.blocks()[s]);
				char biome = id(this.biomes, this.biomeIds, d.biomes()[s]);
				int cx = (d.x0() + dx) << d.level();
				int cz = (d.z0() + dz) << d.level();
				for (int z = cz; z < cz + cell; z++) {
					for (int x = cx; x < cx + cell; x++) {
						layer.put(RegionRaw.index(x, z), d.heights()[s], block, biome, d.depths()[s] & 0xFF, d.flags()[s], d.level(), slot);
					}
				}
			}
		}

		private char id(NamePalette palette, Map<String, Character> cache, String name) {
			if (name == null || name.isEmpty()) {
				return NamePalette.NONE;
			}
			Character id = cache.get(name);
			if (id != null) {
				return id;
			}
			if (this.newNames >= MAX_NEW_NAMES) {
				return NamePalette.NONE;
			}
			int before = palette.size();
			char made = (char) palette.id(name);
			if (palette.size() > before) {
				this.newNames++;
			}
			cache.put(name, made);
			return made;
		}

		private void evict() {
			Iterator<Key> it = this.layers.keySet().iterator();
			while (this.layers.size() > this.maxRegions && it.hasNext()) {
				it.next();
				it.remove();
			}
		}
	}
}
