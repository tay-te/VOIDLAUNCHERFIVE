package dev.voidmc.lod.core;

import java.util.Arrays;

/** A growable list of primitive longs, reused frame to frame so selection allocates nothing. */
public final class LongList {
	private long[] items = new long[256];
	private int size;

	public void add(long v) {
		if (this.size == this.items.length) {
			this.items = Arrays.copyOf(this.items, this.size * 2);
		}
		this.items[this.size++] = v;
	}

	public long get(int i) {
		return this.items[i];
	}

	public int size() {
		return this.size;
	}

	public void truncate(int size) {
		this.size = size;
	}

	public void clear() {
		this.size = 0;
	}
}
