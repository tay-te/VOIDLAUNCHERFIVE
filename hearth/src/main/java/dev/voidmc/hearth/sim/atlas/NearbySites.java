package dev.voidmc.hearth.sim.atlas;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.SettlementTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The world generation histories round a point that are not yet live towns (a history becomes one when a player first
 * comes near it): for {@code /hearth settlement list}. Reads only plans the atlas already holds, so it never loads a
 * chunk or works out a history on the calling thread; regions in range with no plan yet are counted, and optionally
 * surveyed on the atlas worker so the next ask has them.
 */
public final class NearbySites {
	private NearbySites() {
	}

	/** A history not yet adopted. {@code distance} is in blocks, horizontal, from the asking point. */
	public record Unvisited(long id, String name, Culture culture, SettlementTier tier, int souls, BlockPos center, int distance) {
	}

	/** @param surveying regions in range whose history is still being worked out */
	public record Result(List<Unvisited> sites, int surveying) {
	}

	/**
	 * @param known  true for a settlement id that is already live, or whose region was adopted (a town since gone is not
	 *               offered again)
	 * @param survey whether to start working out the regions in range that have no plan yet (off this thread)
	 */
	public static Result around(Atlas atlas, int x, int z, int radius, LongPredicate known, boolean survey) {
		List<Unvisited> out = new ArrayList<>();
		int surveying = 0;
		if (!atlas.enabled()) {
			return new Result(out, 0);
		}
		long r2 = (long) radius * radius;
		for (int rx = Atlas.region(x - radius); rx <= Atlas.region(x + radius); rx++) {
			for (int rz = Atlas.region(z - radius); rz <= Atlas.region(z + radius); rz++) {
				if (!touches(rx, rz, x, z, r2)) {
					continue;
				}
				RegionPlan plan = atlas.planIfReady(rx, rz);
				if (plan == null) {
					surveying++;
					if (survey) {
						atlas.planAsync(rx, rz);
					}
					continue;
				}
				Unvisited u = of(plan, x, z);
				if (u != null && (long) u.distance() * u.distance() <= r2 && !known.test(u.id())) {
					out.add(u);
				}
			}
		}
		out.sort(Comparator.comparingInt(Unvisited::distance));
		return new Result(out, surveying);
	}

	/** The plan's settlement as seen from x, z, or null for an unsettled region. */
	public static @Nullable Unvisited of(RegionPlan plan, int x, int z) {
		RegionPlan.Site site = plan.site();
		if (site == null || plan.history() == null) {
			return null;
		}
		BlockPos c = site.center();
		double dx = c.getX() + 0.5 - x;
		double dz = c.getZ() + 0.5 - z;
		return new Unvisited(site.settlementId(), site.name(), site.culture(), plan.history().tier(), plan.history().population(), c,
			(int) Math.sqrt(dx * dx + dz * dz));
	}

	/** Whether any of the region lies within the radius (squared) of x, z. */
	private static boolean touches(int rx, int rz, int x, int z, long r2) {
		int x0 = rx << Atlas.REGION_BITS;
		int z0 = rz << Atlas.REGION_BITS;
		long dx = Math.max(0, Math.max(x0 - x, x - (x0 + Atlas.REGION_SIZE - 1)));
		long dz = Math.max(0, Math.max(z0 - z, z - (z0 + Atlas.REGION_SIZE - 1)));
		return dx * dx + dz * dz <= r2;
	}
}
