package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.RegionRaw;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntBinaryOperator;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Regions, palettes and hand-built messages for the shared-tile tests. */
final class TileFixtures {
	static final String DIM = "minecraft:overworld";
	static final String SERVER = ServerHash.of("play.example.com");
	static final String[] BLOCKS = {"minecraft:stone", "minecraft:grass_block", "minecraft:sand", "minecraft:water", "minecraft:oak_leaves", "minecraft:snow"};
	static final String[] BIOMES = {"minecraft:plains", "minecraft:forest", "minecraft:river", "minecraft:desert"};

	private TileFixtures() {
	}

	static NamePalette palette(String... names) {
		NamePalette p = new NamePalette();
		for (String n : names) {
			p.id(n);
		}
		return p;
	}

	/**
	 * A region whose column (x, z) is known when {@code known} gives non-zero; its height, block and biome come from
	 * a seeded random, so two regions with different seeds differ almost everywhere.
	 */
	static RegionRaw region(int rx, int rz, NamePalette blocks, NamePalette biomes, long seed, IntBinaryOperator known) {
		RegionRaw raw = new RegionRaw(rx, rz);
		Random r = new Random(seed);
		for (int z = 0; z < RegionRaw.SIZE; z++) {
			for (int x = 0; x < RegionRaw.SIZE; x++) {
				int h = 40 + r.nextInt(80);
				int b = blocks.id(BLOCKS[r.nextInt(BLOCKS.length)]);
				int bi = biomes.id(BIOMES[r.nextInt(BIOMES.length)]);
				int d = r.nextInt(8) == 0 ? 1 + r.nextInt(12) : 0;
				int rock = r.nextInt(16) == 0 ? RegionRaw.ROCK : 0;
				if (known.applyAsInt(x, z) != 0) {
					raw.set(x, z, h, b, d, bi, RegionRaw.KNOWN | rock);
				}
			}
		}
		return raw;
	}

	static RegionRaw full(int rx, int rz, NamePalette blocks, NamePalette biomes, long seed) {
		return region(rx, rz, blocks, biomes, seed, (x, z) -> 1);
	}

	/** A region of random heights and {@code names.length} block names, the worst case for compression. */
	static RegionRaw noisy(int rx, int rz, NamePalette blocks, NamePalette biomes, String[] names, long seed) {
		RegionRaw raw = new RegionRaw(rx, rz);
		Random r = new Random(seed);
		for (int z = 0; z < RegionRaw.SIZE; z++) {
			for (int x = 0; x < RegionRaw.SIZE; x++) {
				raw.set(x, z, r.nextInt(3000) - 1000, blocks.id(names[r.nextInt(names.length)]), r.nextInt(256),
					biomes.id(BIOMES[r.nextInt(BIOMES.length)]), RegionRaw.KNOWN | (r.nextBoolean() ? RegionRaw.ROCK : 0));
			}
		}
		return raw;
	}

	static String[] manyNames(int n) {
		String[] out = new String[n];
		for (int i = 0; i < n; i++) {
			out[i] = "somemod:block_number_" + i;
		}
		return out;
	}

	/** A party of the given players, plus "friend" who is only a friend. */
	static Roster party(String... players) {
		Roster r = new Roster();
		r.setParty(List.of(players));
		r.setFriends(List.of("friend"));
		return r;
	}

	static SharedTiles.Inbox inbox(String me, Roster roster, NamePalette blocks, NamePalette biomes) {
		SharedTiles.Inbox in = new SharedTiles.Inbox(me, roster, blocks, biomes);
		in.local(SERVER, Privacy.PARTY);
		return in;
	}

	static SharedTiles.Outbox outbox(String me, NamePalette blocks, NamePalette biomes) {
		SharedTiles.Outbox out = new SharedTiles.Outbox(me, blocks, biomes);
		out.local(SERVER, Privacy.PARTY);
		return out;
	}

	/** Drains an outbox until nothing waits, a step of {@code stepMs} at a time; returns every message. */
	static List<SharedTiles.Message> drainAll(SharedTiles.Outbox out, long stepMs) {
		List<SharedTiles.Message> all = new ArrayList<>();
		long now = 0;
		for (int i = 0; i < 100_000 && (out.pendingPieces() > 0 || out.heldMessages() > 0 || i == 0); i++) {
			all.addAll(out.drain(now));
			now += stepMs;
		}
		return all;
	}

	/** A body, written exactly as the layout in {@link SharedTiles} documents it. */
	static byte[] body(String[] names, int width, int height, int[] knownSamples, short[] heights, int[] blocks, int[] biomes, int[] depths, int[] flags) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeShort(names.length);
			for (String n : names) {
				byte[] b = n.getBytes(StandardCharsets.UTF_8);
				out.writeByte(b.length);
				out.write(b);
			}
			byte[] bitmap = new byte[(width * height + 7) / 8];
			for (int s : knownSamples) {
				bitmap[s >> 3] |= (byte) (1 << (s & 7));
			}
			out.write(bitmap);
			for (short h : heights) {
				out.writeShort(h);
			}
			for (int b : blocks) {
				out.writeShort(b);
			}
			for (int b : biomes) {
				out.writeShort(b);
			}
			for (int d : depths) {
				out.writeByte(d);
			}
			for (int f : flags) {
				out.writeByte(f);
			}
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A whole message, written exactly as documented: header, CRC over header and body, zlib-deflated body. */
	static byte[] message(int version, int level, String sender, String server, String dim, int rx, int rz, long contentVersion,
		int x0, int z0, int w, int h, byte[] body, long claimedLength, byte[] deflatedOverride, byte[] trailing) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(0x41545354);
			out.writeByte(version);
			out.writeByte(level);
			out.writeShort(0);
			for (String s : new String[] {sender, server, dim}) {
				byte[] b = s.getBytes(StandardCharsets.UTF_8);
				out.writeByte(b.length);
				out.write(b);
			}
			out.writeInt(rx);
			out.writeInt(rz);
			out.writeLong(contentVersion);
			out.writeShort(x0);
			out.writeShort(z0);
			out.writeShort(w);
			out.writeShort(h);
			out.writeInt((int) claimedLength);
			out.flush();
			CRC32 crc = new CRC32();
			crc.update(bytes.toByteArray());
			crc.update(body);
			out.writeInt((int) crc.getValue());
			out.write(deflatedOverride != null ? deflatedOverride : deflate(body));
			if (trailing != null) {
				out.write(trailing);
			}
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static byte[] message(int level, int x0, int z0, int w, int h, byte[] body) {
		return message(1, level, "alice", SERVER, DIM, 3, -2, 7, x0, z0, w, h, body, body.length, null, null);
	}

	static byte[] deflate(byte[] data) {
		Deflater d = new Deflater();
		try {
			d.setInput(data);
			d.finish();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			while (!d.finished()) {
				out.write(buf, 0, d.deflate(buf));
			}
			return out.toByteArray();
		} finally {
			d.end();
		}
	}
}
