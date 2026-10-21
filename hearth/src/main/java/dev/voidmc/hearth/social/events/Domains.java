package dev.voidmc.hearth.social.events;

import java.util.List;
import java.util.function.ToDoubleBiFunction;
import net.minecraft.core.BlockPos;

/**
 * Lords' domains (docs/LIVING_WORLD_2.md §3.2): a lord holds the settlements whose road distance to the seat is under
 * {@link #REACH} blocks and less than to any other seat. Pure: the caller gives the seats and the road distance (or the
 * crow's flight a quarter longer where the roads are not built yet).
 */
public final class Domains {
	/** The farthest a domain reaches from its seat, by road. */
	public static final double REACH = 2000;

	private Domains() {
	}

	/** A lord's seat: a settlement with a lord in office. */
	public record Seat(long id, BlockPos pos) {
	}

	/**
	 * The seat whose domain a settlement is in: its own id if it is a seat, else the nearest seat by road within reach, or
	 * 0 for none.
	 *
	 * @param road the road distance between two settlements, or a negative number when they are not joined
	 */
	public static long seatOf(long town, BlockPos at, List<Seat> seats, ToDoubleBiFunction<Long, Long> road, boolean roadsKnown) {
		long best = 0;
		double bestD = REACH;
		for (Seat s : seats) {
			if (s.id() == town) {
				return town;
			}
			double d = road.applyAsDouble(s.id(), town);
			if (d < 0) {
				if (roadsKnown) {
					continue; // no road joins them: not this lord's to tax
				}
				d = Math.sqrt(s.pos().distSqr(at)) * 1.25;
			}
			if (d < bestD) {
				bestD = d;
				best = s.id();
			}
		}
		return best;
	}
}
