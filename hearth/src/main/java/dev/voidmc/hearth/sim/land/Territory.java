package dev.voidmc.hearth.sim.land;

import dev.voidmc.hearth.api.SettlementTier;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * How much land a settlement works (docs/LIVING_WORLD.md §7.1): a radius growing with its tier, never reaching into
 * another settlement's core — where two towns' territories would overlap, each stops short of the other's built-up heart
 * (its bounds plus a margin), so the land between is shared and no town works its neighbour's streets.
 */
public final class Territory {
	/** Another settlement's heart and the radius of its built-up core. */
	public record Core(BlockPos center, int radius) {
	}

	private Territory() {
	}

	/** The radius a tier works, from the config ({@code camp: 96 ... city: 512}). */
	public static int byTier(SettlementTier tier, Map<String, Integer> radii) {
		Integer r = radii.get(tier.getSerializedName());
		if (r != null) {
			return r;
		}
		return switch (tier) {
			case CAMP -> 96;
			case HAMLET -> 160;
			case VILLAGE -> 256;
			case TOWN -> 384;
			case CITY -> 512;
		};
	}

	/**
	 * @param own   this settlement's own core radius (its land always reaches past its own buildings)
	 * @param cores the other settlements round about
	 */
	public static int radius(BlockPos heart, int wanted, int own, List<Core> cores) {
		int r = wanted;
		for (Core c : cores) {
			double dx = c.center().getX() - heart.getX();
			double dz = c.center().getZ() - heart.getZ();
			int d = (int) Math.sqrt(dx * dx + dz * dz);
			r = Math.min(r, d - c.radius());
		}
		return Math.max(Math.max(48, own + 32), r);
	}

	/** Whether a column belongs to another settlement's core. */
	public static boolean inCore(int x, int z, List<Core> cores) {
		for (Core c : cores) {
			long dx = x - c.center().getX();
			long dz = z - c.center().getZ();
			if (dx * dx + dz * dz <= (long) c.radius() * c.radius()) {
				return true;
			}
		}
		return false;
	}
}
