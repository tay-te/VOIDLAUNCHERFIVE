package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LodSelectorTest {
	private static final double SCALE = 771; // 1080p at a 70-degree field of view

	/** A store where a chosen set of tiles is ready. */
	private static final class FakeStore implements LodSelector.Store {
		final Set<Long> ready = new HashSet<>();
		final Map<Long, Float> wanted = new HashMap<>();
		boolean allReady;

		@Override
		public boolean ready(long key) {
			return this.allReady || this.ready.contains(key);
		}

		@Override
		public boolean heightRange(long key, int[] out) {
			out[0] = 60;
			out[1] = 80;
			return true;
		}

		@Override
		public void want(long key, float priority) {
			this.wanted.put(key, priority);
		}

		@Override
		public void touch(long key) {
		}
	}

	@Test
	void selectionTilesTheRingWithoutOverlapOrGaps() {
		FakeStore store = new FakeStore();
		store.allReady = true;
		LongList draw = new LodSelector().select(store, 100, 120, -50, 6000, 200, SCALE, 3);
		// sample a grid of points in the ring: each must be covered by exactly one selected tile
		for (int x = -5500; x <= 5500; x += 97) {
			for (int z = -5500; z <= 5500; z += 89) {
				double dx = x - 100, dz = z + 50;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < 300 || d > 5900) {
					continue;
				}
				int covering = 0;
				for (int i = 0; i < draw.size(); i++) {
					long k = draw.get(i);
					int x0 = TileKey.minX(k), z0 = TileKey.minZ(k), s = TileKey.span(TileKey.level(k));
					if (x >= x0 && x < x0 + s && z >= z0 && z < z0 + s) {
						covering++;
					}
				}
				assertEquals(1, covering, "point " + x + "," + z + " at distance " + (int) d);
			}
		}
	}

	@Test
	void detailFallsOffWithDistance() {
		FakeStore store = new FakeStore();
		store.allReady = true;
		LongList draw = new LodSelector().select(store, 0, 100, 0, 8000, 150, SCALE, 3);
		int nearLevel = Integer.MAX_VALUE, farLevel = 0;
		for (int i = 0; i < draw.size(); i++) {
			long k = draw.get(i);
			double cx = TileKey.minX(k) + TileKey.span(TileKey.level(k)) / 2.0;
			double cz = TileKey.minZ(k) + TileKey.span(TileKey.level(k)) / 2.0;
			double d = Math.sqrt(cx * cx + cz * cz);
			if (d < 400) {
				nearLevel = Math.min(nearLevel, TileKey.level(k));
			}
			if (d > 6000) {
				farLevel = Math.max(farLevel, TileKey.level(k));
			}
		}
		assertTrue(nearLevel <= 1, "fine tiles near the clip: " + nearLevel);
		assertTrue(farLevel >= 4, "coarse tiles at the horizon: " + farLevel);
		// and the count stays modest: the whole 8 km disc in a few hundred tiles
		assertTrue(draw.size() < 1500, "tiles: " + draw.size());
	}

	@Test
	void coarseTilesAreFetchedFirstAndFillInWhileFineOnesLoad() {
		FakeStore store = new FakeStore();
		LodSelector selector = new LodSelector();
		LongList draw = selector.select(store, 0, 100, 0, 4000, 150, SCALE, 3);
		assertEquals(0, draw.size());
		float best = Float.MAX_VALUE;
		long bestKey = 0;
		for (var e : store.wanted.entrySet()) {
			if (e.getValue() < best) {
				best = e.getValue();
				bestKey = e.getKey();
			}
		}
		int top = 0;
		for (long k : store.wanted.keySet()) {
			top = Math.max(top, TileKey.level(k));
		}
		assertEquals(top, TileKey.level(bestKey), "the coarsest tiles come first");

		// once only the coarse tiles exist, they are drawn in place of their missing children
		for (long k : store.wanted.keySet()) {
			if (TileKey.level(k) == top) {
				store.ready.add(k);
			}
		}
		draw = selector.select(store, 0, 100, 0, 4000, 150, SCALE, 3);
		assertFalse(draw.size() == 0);
		for (int i = 0; i < draw.size(); i++) {
			assertEquals(top, TileKey.level(draw.get(i)));
		}
	}

	@Test
	void zoomingInRefinesOnlyWhatIsInView() {
		FakeStore store = new FakeStore();
		store.allReady = true;
		// a lens four times as strong, looking along +x: only the boxes east of the camera are in view
		double zoomed = 4 * SCALE;
		LodSelector.Visibility east = (x0, y0, z0, x1, y1, z1) -> x1 > 0;
		int all = new LodSelector().select(store, 0, 120, 0, 8192, 200, zoomed, 5, 3).size();
		int aware = new LodSelector().select(store, 0, 120, 0, 8192, 200, zoomed, 5, 3, east, 0.25).size();
		int normal = new LodSelector().select(store, 0, 120, 0, 8192, 200, SCALE, 5, 3).size();
		assertTrue(aware < all * 0.7, "out of view stays coarse: " + aware + " of " + all);
		assertTrue(aware > normal, "in view refines: " + aware + " vs " + normal + " unzoomed");
	}

	@Test
	void smallMovesDoNotFlipLevels() {
		// a camera drifting back and forth by 20 blocks: without hysteresis tiles at their thresholds flip every frame.
		// Measured as the level drawn at points between the clip ring and the edge (tiles entering and leaving at the
		// edges are another matter)
		FakeStore store = new FakeStore();
		store.allReady = true;
		LodSelector selector = new LodSelector();
		int[] last = null;
		int flips = 0;
		for (int frame = 0; frame < 40; frame++) {
			double x = 1000 + (frame % 2 == 0 ? 0 : 20.0);
			int[] levels = levelsAt(selector.select(store, x, 120, -300, 6000, 200, SCALE, 5), 1000, -300, 500, 5000);
			if (last != null && frame >= 2) {
				for (int i = 0; i < levels.length; i++) {
					if (levels[i] != last[i]) {
						flips++;
					}
				}
			}
			last = levels;
		}
		assertEquals(0, flips, "no level flips while the camera wobbles by 20 blocks");
	}

	/** The level of the tile covering each point of a grid around (cx, cz), between two distances (-1: none). */
	private static int[] levelsAt(LongList draw, double cx, double cz, double near, double far) {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		for (int x = (int) (cx - far); x <= cx + far; x += 37) {
			for (int z = (int) (cz - far); z <= cz + far; z += 41) {
				double d = Math.hypot(x - cx, z - cz);
				if (d < near || d > far) {
					continue;
				}
				int level = -1;
				for (int i = 0; i < draw.size(); i++) {
					long k = draw.get(i);
					int x0 = TileKey.minX(k), z0 = TileKey.minZ(k), span = TileKey.span(TileKey.level(k));
					if (x >= x0 && x < x0 + span && z >= z0 && z < z0 + span) {
						level = TileKey.level(k);
					}
				}
				out.add(level);
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	@Test
	void hysteresisStillFollowsRealMoves() {
		// moving on for good, tiles do split and merge: hysteresis only delays a change by a few percent of the distance
		FakeStore store = new FakeStore();
		store.allReady = true;
		LodSelector selector = new LodSelector();
		int start = selector.select(store, 0, 120, 0, 6000, 200, SCALE, 5).size();
		java.util.Set<Long> first = new java.util.HashSet<>();
		LongList a = selector.select(store, 0, 120, 0, 6000, 200, SCALE, 5);
		for (int i = 0; i < a.size(); i++) {
			first.add(a.get(i));
		}
		LongList b = selector.select(store, 1500, 120, 0, 6000, 200, SCALE, 5);
		java.util.Set<Long> moved = new java.util.HashSet<>();
		for (int i = 0; i < b.size(); i++) {
			moved.add(b.get(i));
		}
		assertTrue(start > 0);
		assertTrue(!first.equals(moved), "1.5 km on, the selection has changed");
	}
}
