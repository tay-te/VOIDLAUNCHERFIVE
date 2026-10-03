package dev.voidmc.lod.core;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongPredicate;

/**
 * Cross-fades between levels of detail, so a tile never pops. When the selection swaps a tile for its four
 * children (or back), the newcomers fade in over {@link #FADE_NANOS} while what they replace keeps drawing and
 * fades out, through complementary dither: a pixel is drawn by the incoming tile where its dither value is
 * under the fade's progress and by the outgoing tile everywhere else, so at every moment each pixel shows one
 * of the two, never both and never neither.
 *
 * <p>A tile that changes its mind halfway (leaves while fading in, or comes back while fading out) picks up
 * from where it was, so a camera dithering on a boundary does not flicker.
 *
 * <p>Fades are signed: 0 is solid, {@code +n} fades in (drawn where dither &lt; n / 255), {@code -n} fades out
 * (drawn where dither &ge; n / 255).
 */
public final class Fades {
	public static final long FADE_NANOS = 450_000_000L;

	private final Long2LongOpenHashMap entered = new Long2LongOpenHashMap();
	private final Long2LongOpenHashMap leaving = new Long2LongOpenHashMap();
	private final LongOpenHashSet current = new LongOpenHashSet();
	private final LongList keys = new LongList();
	private int[] fades = new int[256];

	/**
	 * Works out this frame's draws from the selection: every selected tile, fading in if it is new, and every
	 * tile that recently left the selection and is still on the GPU ({@code ready}), fading out.
	 */
	public void update(LongList draw, long now, LongPredicate ready) {
		this.keys.clear();
		this.current.clear();
		for (int i = 0; i < draw.size(); i++) {
			this.current.add(draw.get(i));
		}
		// tiles that left the selection start fading out, from however far they had faded in
		var it = this.entered.long2LongEntrySet().fastIterator();
		while (it.hasNext()) {
			Long2LongMap.Entry e = it.next();
			if (!this.current.contains(e.getLongKey())) {
				long shown = Math.min(FADE_NANOS, now - e.getLongValue());
				this.leaving.put(e.getLongKey(), now - (FADE_NANOS - shown));
				it.remove();
			}
		}
		for (int i = 0; i < draw.size(); i++) {
			long key = draw.get(i);
			long start = this.entered.getOrDefault(key, Long.MIN_VALUE);
			if (start == Long.MIN_VALUE) {
				start = now;
				if (this.leaving.containsKey(key)) {
					// back before it had gone: fade in from what still shows
					long gone = Math.min(FADE_NANOS, now - this.leaving.remove(key));
					start = now - (FADE_NANOS - gone);
				}
				this.entered.put(key, start);
			}
			this.add(key, progress(now - start));
		}
		var out = this.leaving.long2LongEntrySet().fastIterator();
		while (out.hasNext()) {
			Long2LongMap.Entry e = out.next();
			long gone = now - e.getLongValue();
			if (gone >= FADE_NANOS || !ready.test(e.getLongKey())) {
				out.remove();
				continue;
			}
			this.add(e.getLongKey(), -Math.max(1, progress(gone)));
		}
	}

	/** Progress in 1..255, or 0 once complete. */
	private static int progress(long elapsed) {
		if (elapsed >= FADE_NANOS) {
			return 0;
		}
		return (int) Math.max(1, Math.min(254, elapsed * 255 / FADE_NANOS));
	}

	private void add(long key, int fade) {
		int i = this.keys.size();
		this.keys.add(key);
		if (i == this.fades.length) {
			this.fades = java.util.Arrays.copyOf(this.fades, i * 2);
		}
		this.fades[i] = fade;
	}

	public void clear() {
		this.entered.clear();
		this.leaving.clear();
	}

	public int size() {
		return this.keys.size();
	}

	public long key(int i) {
		return this.keys.get(i);
	}

	public int fade(int i) {
		return this.fades[i];
	}
}
