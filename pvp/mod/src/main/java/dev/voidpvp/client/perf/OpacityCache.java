package dev.voidpvp.client.perf;

/**
 * A per-pass memo in front of a slow {@link BlockOpacity} — the world reader, which has to find
 * a chunk and a section for every lookup.
 *
 * <p>The rays of one pass revisit the same cells constantly: nine rays to one box share most of
 * their path, and every box behind the same wall asks about the same wall. An open-addressed
 * table of packed coordinates keyed by a generation stamp, so {@link #reset} between passes is
 * one increment rather than a clear of 64 K slots. A full table simply stops memoising — a miss
 * costs a lookup, never a wrong answer.</p>
 *
 * <p>Single-threaded: one instance belongs to the culling thread.</p>
 */
public final class OpacityCache implements BlockOpacity {

    private static final int BITS = 16;
    private static final int SIZE = 1 << BITS;
    private static final int MASK = SIZE - 1;
    private static final int PROBES = 8;

    private final long[] keys = new long[SIZE];
    private final int[] stamps = new int[SIZE];
    private final boolean[] values = new boolean[SIZE];
    private int generation = 1;
    private BlockOpacity source;
    private int lookups;

    /** Points the cache at a source and forgets everything memoised for the previous pass. */
    public void reset(BlockOpacity source) {
        this.source = source;
        this.lookups = 0;
        generation++;
        if (generation == Integer.MAX_VALUE) {
            java.util.Arrays.fill(stamps, 0);
            generation = 1;
        }
    }

    /** Source lookups since the last {@link #reset} — what the memo did not save. */
    public int sourceLookups() {
        return lookups;
    }

    @Override
    public boolean opaque(int x, int y, int z) {
        long key = pack(x, y, z);
        int h = mix(key);
        for (int p = 0; p < PROBES; p++) {
            int slot = (h + p) & MASK;
            if (stamps[slot] != generation) {
                boolean v = lookup(x, y, z);
                stamps[slot] = generation;
                keys[slot] = key;
                values[slot] = v;
                return v;
            }
            if (keys[slot] == key) {
                return values[slot];
            }
        }
        return lookup(x, y, z);
    }

    private boolean lookup(int x, int y, int z) {
        lookups++;
        return source != null && source.opaque(x, y, z);
    }

    /** 26 bits of x, 12 of y, 26 of z — the whole 1.8.9 world border fits. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h >>> (64 - BITS));
    }
}
