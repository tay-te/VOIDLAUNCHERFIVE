package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.core.RegionRaw;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What friends have shared of one region, kept apart from the player's own {@link RegionRaw}: the same columns
 * (height, block, water depth, biome, flags, with block and biome as ids in this player's own palettes), plus for
 * each column who shared it and at what detail level (0 is every column; at level L one shared sample was spread
 * over 2^L x 2^L columns). It is only ever shown through {@link SharedTiles#merge}, which never lets it cover a
 * column the player has seen themselves.
 *
 * <p>Like {@link RegionRaw}, written by one thread ({@link SharedTiles.Inbox}) and read by others without locks.
 */
public final class SharedLayer {
	public static final int AREA = RegionRaw.AREA;
	/** The most senders one region remembers. */
	public static final int MAX_SENDERS = 255;

	public final String dimension;
	public final int x;
	public final int z;
	public final short[] height = new short[AREA];
	public final char[] block = new char[AREA];
	public final byte[] depth = new byte[AREA];
	public final char[] biome = new char[AREA];
	/** {@link RegionRaw#KNOWN} when something was shared, and {@link RegionRaw#ROCK} as shared. */
	public final byte[] flags = new byte[AREA];
	private final byte[] level = new byte[AREA];
	/** 1 + the index in {@link #senders}, or 0. */
	private final byte[] sender = new byte[AREA];
	private final List<String> senders = new CopyOnWriteArrayList<>();
	private volatile long version;
	private int known;

	public SharedLayer(String dimension, int x, int z) {
		this.dimension = dimension;
		this.x = x;
		this.z = z;
		Arrays.fill(this.height, RegionRaw.UNKNOWN);
	}

	public boolean known(int i) {
		return (this.flags[i] & RegionRaw.KNOWN) != 0;
	}

	/** The detail level a column was shared at (0 to {@value SharedTiles#MAX_LEVEL}). */
	public int level(int i) {
		return this.level[i];
	}

	/** Who shared a column, or null. */
	public String sender(int i) {
		int s = this.sender[i] & 0xFF;
		return s == 0 || s > this.senders.size() ? null : this.senders.get(s - 1);
	}

	public int depth(int i) {
		return this.depth[i] & 0xFF;
	}

	/** Everyone who has shared some of this region, in order of first arrival. */
	public List<String> senders() {
		return List.copyOf(this.senders);
	}

	/** How many columns are known. */
	public synchronized int knownCount() {
		return this.known;
	}

	public boolean isEmpty() {
		return knownCount() == 0;
	}

	/** Bumped by every write. */
	public long version() {
		return this.version;
	}

	/** The slot for a sender (1 to {@value #MAX_SENDERS}), added if new; 0 when the region has no room for more. */
	synchronized int senderSlot(String player) {
		int at = this.senders.indexOf(player);
		if (at >= 0) {
			return at + 1;
		}
		if (this.senders.size() >= MAX_SENDERS) {
			return 0;
		}
		this.senders.add(player);
		return this.senders.size();
	}

	/**
	 * Writes a shared column unless a finer one is already there: detail never gets worse, and at the same level the
	 * newer share wins. Returns whether it wrote.
	 */
	synchronized boolean put(int i, short height, char block, char biome, int depth, int flags, int level, int slot) {
		boolean was = known(i);
		if (was && level > this.level[i]) {
			return false;
		}
		this.height[i] = height;
		this.block[i] = block;
		this.depth[i] = (byte) Math.min(255, Math.max(0, depth));
		this.biome[i] = biome;
		this.flags[i] = (byte) (RegionRaw.KNOWN | (flags & RegionRaw.ROCK));
		this.level[i] = (byte) level;
		this.sender[i] = (byte) slot;
		if (!was) {
			this.known++;
		}
		this.version++;
		return true;
	}

	/** Forgets every column a sender shared; returns how many. */
	synchronized int forget(String player) {
		int at = this.senders.indexOf(player);
		if (at < 0) {
			return 0;
		}
		byte slot = (byte) (at + 1);
		int n = 0;
		for (int i = 0; i < AREA; i++) {
			if (this.sender[i] == slot && known(i)) {
				this.flags[i] = 0;
				this.height[i] = RegionRaw.UNKNOWN;
				this.block[i] = 0;
				this.biome[i] = 0;
				this.depth[i] = 0;
				this.level[i] = 0;
				this.sender[i] = 0;
				n++;
			}
		}
		this.known -= n;
		if (n > 0) {
			this.version++;
		}
		return n;
	}
}
