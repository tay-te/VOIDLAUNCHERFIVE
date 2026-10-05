package dev.voidmc.atlas.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Least-recently-viewed eviction: oldest first, never near the player, never anything just looked at. */
class DiskCapTest {
	static final long MIN = 60_000L;
	static final long NOW = 1_000_000_000_000L;

	private static RegionIndex.Entry e(String layer, int rx, int rz, long minutesAgo, long bytes) {
		return new RegionIndex.Entry(layer, rx, rz, NOW - minutesAgo * MIN, bytes);
	}

	private static RegionIndex index(RegionIndex.Entry... entries) {
		RegionIndex index = new RegionIndex();
		for (RegionIndex.Entry entry : entries) {
			index.put(entry);
		}
		return index;
	}

	private static List<String> names(List<RegionIndex.Entry> plan) {
		return plan.stream().map(x -> x.layer() + ":" + x.rx() + "," + x.rz()).toList();
	}

	@Test
	void nothingWhileUnderOrAtTheCap() {
		RegionIndex index = index(e("surface", 10, 10, 1000, 500), e("surface", 11, 10, 1000, 500));
		assertTrue(DiskCap.plan(index, 1000, 0, 0, 2, NOW).isEmpty());
		assertTrue(DiskCap.plan(index, 5000, 0, 0, 2, NOW).isEmpty());
		assertTrue(DiskCap.plan(new RegionIndex(), 0, 0, 0, 2, NOW).isEmpty());
	}

	@Test
	void oldestFirstUntilBelowNinetyPercent() {
		// 10 regions of 100 bytes = 1000; cap 950 -> must get below 855 -> delete 2 (800)
		RegionIndex index = new RegionIndex();
		for (int i = 0; i < 10; i++) {
			index.put(e("surface", 10 + i, 0, 100 + i * 10, 100));
		}
		List<RegionIndex.Entry> plan = DiskCap.plan(index, 950, 0, 0, 2, NOW);
		assertEquals(List.of("surface:19,0", "surface:18,0"), names(plan));
	}

	@Test
	void stopsStrictlyBelowTheTarget() {
		// 1000 bytes, cap 999 -> target 899.1 (899 in whole bytes): 900 is not below, so two go
		RegionIndex index = new RegionIndex();
		for (int i = 0; i < 10; i++) {
			index.put(e("surface", 10 + i, 0, 100 + i, 100));
		}
		assertEquals(2, DiskCap.plan(index, 999, 0, 0, 0, NOW).size());
		// cap 990 -> target 891: deleting one leaves 900, not below; two leave 800
		assertEquals(2, DiskCap.plan(index, 990, 0, 0, 0, NOW).size());
		// exactly at the cap is not over it
		assertEquals(0, DiskCap.plan(index, 1000, 0, 0, 0, NOW).size());
	}

	@Test
	void neverNearThePlayer() {
		RegionIndex index = index(
			e("surface", 0, 0, 5000, 400), // the player's own region, oldest of all
			e("surface", 2, -2, 5000, 400), // corner of radius 2 (Chebyshev)
			e("surface", 3, 0, 4000, 400), // just outside
			e("surface", -1, 50, 100, 400));
		List<RegionIndex.Entry> plan = DiskCap.plan(index, 1000, 0, 0, 2, NOW);
		assertEquals(List.of("surface:3,0", "surface:-1,50"), names(plan));
	}

	@Test
	void neverAnythingViewedInTheLastTenMinutes() {
		RegionIndex index = index(
			e("surface", 100, 0, 9, 600),
			e("surface", 101, 0, 10, 600),
			e("surface", 102, 0, 11, 600));
		List<RegionIndex.Entry> plan = DiskCap.plan(index, 1000, 0, 0, 2, NOW);
		assertEquals(List.of("surface:102,0", "surface:101,0"), names(plan), "exactly 10 minutes ago is no longer recent");
	}

	@Test
	void returnsWhatItCanWhenTheTargetIsOutOfReach() {
		RegionIndex index = index(e("surface", 0, 0, 1000, 5000), e("surface", 50, 50, 1000, 100), e("surface", 60, 60, 1, 100));
		List<RegionIndex.Entry> plan = DiskCap.plan(index, 1000, 0, 0, 1, NOW);
		assertEquals(List.of("surface:50,50"), names(plan));
	}

	@Test
	void tiesBreakByBiggerFirstThenPosition() {
		RegionIndex index = index(
			e("surface", 5, 5, 100, 100),
			e("cave_-2", 5, 5, 100, 100),
			e("surface", 4, 5, 100, 150),
			e("surface", 6, 5, 100, 100));
		List<RegionIndex.Entry> plan = DiskCap.plan(index, 300, 0, 0, 0, NOW);
		// 450 total, cap 300 -> below 270: the 150-byte one first (300 left), then cave_-2 by layer name (200 left)
		assertEquals(List.of("surface:4,5", "cave_-2:5,5"), names(plan));
	}

	@Test
	void layersHaveTheirOwnPlayerPosition() {
		RegionIndex index = index(
			e("surface", 8, 0, 1000, 500),
			e("nether", 1, 0, 1000, 500),
			e("end", 0, 0, 1000, 500));
		Map<String, DiskCap.Region> player = Map.of("surface", new DiskCap.Region(8, 0), "nether", new DiskCap.Region(1, 0));
		List<RegionIndex.Entry> plan = DiskCap.plan(index, 1000, player::get, 1, NOW);
		assertEquals(List.of("end:0,0"), names(plan), "the End has no player in it, so its region near 0,0 can go");
	}

	@Test
	void emptyRegionsAreNotWorthDeleting() {
		RegionIndex index = index(e("surface", 50, 0, 9999, 0), e("surface", 51, 0, 10, 2000));
		assertEquals(List.of("surface:51,0"), names(DiskCap.plan(index, 1000, 0, 0, 1, NOW)));
	}

	@Test
	void deterministic() {
		RegionIndex index = new RegionIndex();
		for (int i = 0; i < 200; i++) {
			index.put(e(i % 3 == 0 ? "surface" : "cave_" + (i % 3), i % 17 - 8, i / 17 - 6, (i * 37) % 50 + 11, 100 + (i * 13) % 90));
		}
		List<RegionIndex.Entry> a = DiskCap.plan(index, 10_000, 0, 0, 2, NOW);
		List<RegionIndex.Entry> b = DiskCap.plan(index, 10_000, 0, 0, 2, NOW);
		assertEquals(a, b);
		assertFalse(a.isEmpty());
		long left = index.totalBytes() - a.stream().mapToLong(RegionIndex.Entry::bytes).sum();
		assertTrue(left < 9_000, "left " + left);
		for (int i = 1; i < a.size(); i++) {
			assertTrue(a.get(i - 1).lastViewed() <= a.get(i).lastViewed());
		}
	}

	@Test
	void hugeCapsDoNotOverflow() {
		RegionIndex index = index(e("surface", 50, 0, 100, Long.MAX_VALUE / 2), e("surface", 51, 0, 100, Long.MAX_VALUE / 2));
		assertEquals(1, DiskCap.plan(index, Long.MAX_VALUE - 10, 0, 0, 1, NOW).size());
	}

	@Test
	void negativeCapIsAnError() {
		assertThrows(IllegalArgumentException.class, () -> DiskCap.plan(new RegionIndex(), -1, 0, 0, 1, NOW));
	}
}
