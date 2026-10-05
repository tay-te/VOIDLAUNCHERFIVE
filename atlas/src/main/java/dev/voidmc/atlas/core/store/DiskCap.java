package dev.voidmc.atlas.core.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Keeps a map inside a disk budget by forgetting the regions looked at longest ago.
 *
 * <p>Nothing happens until the map is over its cap; then regions are chosen, least recently viewed first, until what
 * is left is below {@value #TARGET_PERCENT}% of the cap, so the next few regions mapped do not trigger another round
 * straight away. Never chosen: a region within {@code keepRadius} regions of the player (Chebyshev distance, so a
 * square around them), and anything viewed in the last {@link #RECENT_MILLIS} (10 minutes).
 *
 * <p>A pure function of its inputs: the same index and arguments give the same list, in the same order.
 */
public final class DiskCap {
	/** A region viewed this recently is never deleted. */
	public static final long RECENT_MILLIS = 10 * 60 * 1000L;
	/** Deleting stops once the total is below this share of the cap. */
	public static final int TARGET_PERCENT = 90;

	private static final Comparator<RegionIndex.Entry> OLDEST_FIRST = Comparator
		.comparingLong(RegionIndex.Entry::lastViewed)
		.thenComparing(Comparator.comparingLong(RegionIndex.Entry::bytes).reversed())
		.thenComparing(RegionIndex.Entry::layer)
		.thenComparingInt(RegionIndex.Entry::rx)
		.thenComparingInt(RegionIndex.Entry::rz);

	private DiskCap() {
	}

	/** A region position. */
	public record Region(int rx, int rz) {
	}

	/** {@link #plan(RegionIndex, long, Function, int, long)} with the player at the same region in every layer. */
	public static List<RegionIndex.Entry> plan(RegionIndex index, long capBytes, int playerRx, int playerRz, int keepRadius, long now) {
		Region here = new Region(playerRx, playerRz);
		return plan(index, capBytes, layer -> here, keepRadius, now);
	}

	/**
	 * The regions to delete, in the order to delete them, so the map's total drops below {@value #TARGET_PERCENT}% of
	 * {@code capBytes}; empty if the map is within its cap.
	 *
	 * @param playerRegionByLayer the player's region in each layer's own coordinates (a Nether layer is in Nether
	 *                            coordinates), or null for a layer the player is not in: then nothing in it is kept
	 *                            for being near them
	 * @param keepRadius          regions within this many regions of the player's (in x and in z) are kept
	 * @param now                 epoch millis
	 * @return as many as needed, or every region allowed if even that is not enough
	 */
	public static List<RegionIndex.Entry> plan(RegionIndex index, long capBytes, Function<String, Region> playerRegionByLayer, int keepRadius, long now) {
		if (capBytes < 0) {
			throw new IllegalArgumentException("capBytes " + capBytes);
		}
		List<RegionIndex.Entry> all = index.entries();
		long total = 0;
		for (RegionIndex.Entry e : all) {
			total += e.bytes();
		}
		if (total <= capBytes) {
			return List.of();
		}
		long target = capBytes / 100 * TARGET_PERCENT + capBytes % 100 * TARGET_PERCENT / 100;
		List<RegionIndex.Entry> candidates = new ArrayList<>();
		for (RegionIndex.Entry e : all) {
			if (e.bytes() <= 0 || now - e.lastViewed() < RECENT_MILLIS) {
				continue;
			}
			Region player = playerRegionByLayer.apply(e.layer());
			if (player != null && Math.max(Math.abs((long) e.rx() - player.rx()), Math.abs((long) e.rz() - player.rz())) <= keepRadius) {
				continue;
			}
			candidates.add(e);
		}
		candidates.sort(OLDEST_FIRST);
		List<RegionIndex.Entry> out = new ArrayList<>();
		for (RegionIndex.Entry e : candidates) {
			if (total < target) {
				break;
			}
			out.add(e);
			total -= e.bytes();
		}
		return out;
	}
}
