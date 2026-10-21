package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The rest day's service (docs/LIVING_WORLD_2.md §2.3): who goes, where it is held and when each part of it is. Pure apart
 * from reading the settlement; cached per settlement and day.
 *
 * <p>Who goes: the priest (and monks) always, the elders, the pious and the lord always, three adults in five otherwise
 * (fixed for the person and the week), the children with their parents. Where: the town's chapel, temple or church (its
 * altar, its pews, its door), else an open-air blessing at the square.
 */
public final class Service {
	static final List<String> CHURCHES = List.of("landmark/cathedral", "landmark/temple", "landmark/pagoda", "civic/monastery", "civic/convent",
		"civic/chapel");
	/** Minutes before the service begins that folk set off. */
	static final int GATHER = 20;
	/** Minutes after it ends that they linger at the door. */
	static final int PORCH = 15;

	/** Where the service is held. */
	public record Church(BlockPos altar, Direction altarFacing, List<Poi> seats, BlockPos door, @Nullable PlotView plot) {
		public boolean open() {
			return this.plot == null;
		}
	}

	public enum Part {
		NONE, GATHER, SERMON, CHANT, PORCH
	}

	/** {@code leaders}: who may lead, best first (the priest, a monk, elders, grown-ups); see {@link #leader}. */
	record Day(long day, int residents, Set<UUID> attending, Map<UUID, Integer> seatOf, @Nullable Church church, @Nullable UUID officiant,
		List<UUID> leaders) {
	}

	/**
	 * Who leads the service now: the first of the day's leaders who is here (embodied), so a congregation near a player
	 * never waits at an empty altar for a priest out of reach; the first of them when none is.
	 */
	static @Nullable UUID leader(Settlement s, Day d) {
		for (UUID u : d.leaders()) {
			if (s.isResidentEmbodied(u)) {
				return u;
			}
		}
		return d.officiant();
	}

	private static final Map<Long, Day> DAYS = new LinkedHashMap<>();

	private Service() {
	}

	/** The part of the service at this minute (on a rest day). */
	public static Part part(RhythmData.Service svc, int minute) {
		int start = svc.start();
		int end = svc.end();
		if (minute >= start - GATHER && minute < start) {
			return Part.GATHER;
		}
		if (minute >= start && minute < end) {
			int into = minute - start;
			int third = Math.max(1, svc.minutes() / 3);
			// two chants: at the end of the first and of the second third, eight minutes each
			if (into >= third - 8 && into < third || into >= 2 * third - 8 && into < 2 * third) {
				return Part.CHANT;
			}
			return Part.SERMON;
		}
		if (minute >= end && minute < end + PORCH) {
			return Part.PORCH;
		}
		return Part.NONE;
	}

	/** Whether one resident goes to the service this rest day (children by their parents' choice: see {@link #attending}). */
	public static boolean goes(Resident r, long day, double share) {
		if (r.job == JobType.PRIEST || r.job == JobType.MONK || r.job == JobType.LORD) {
			return true;
		}
		if (r.job == JobType.GUARD || r.job == JobType.JAILER || r.job == JobType.INNKEEPER) {
			return false; // the watch keeps the town, the inn its fires
		}
		if (r.age == AgeStage.ELDER || FolkFacts.has(r, "pious")) {
			return true;
		}
		long week = Math.floorDiv(day, 7);
		return TownCalendar.chance(r.id.getMostSignificantBits(), r.id.getLeastSignificantBits(), week * 13 + 5) < share;
	}

	/** Everyone at this rest day's service: those who go and the children of those who go. Pure. */
	public static Set<UUID> attending(Iterable<Resident> residents, long day, double share) {
		Set<UUID> out = new HashSet<>();
		List<Resident> children = new ArrayList<>();
		for (Resident r : residents) {
			if (r.age == AgeStage.CHILD) {
				children.add(r);
			} else if (goes(r, day, share)) {
				out.add(r.id);
			}
		}
		for (Resident c : children) {
			List<UUID> parents = FolkFacts.parents(c);
			boolean with = false;
			for (UUID p : parents) {
				if (out.contains(p)) {
					with = true;
					break;
				}
			}
			if (with || parents.isEmpty() && TownCalendar.chance(c.id.getMostSignificantBits(), c.id.getLeastSignificantBits(), day) < share) {
				out.add(c.id);
			}
		}
		return out;
	}

	/** The town's church nearest its square (a far monastery is not the parish), else an open-air place at the square. */
	public static Church church(Settlement s) {
		BlockPos square = Venues.square(s);
		Church best = null;
		double bestD = Double.MAX_VALUE;
		for (String path : CHURCHES) {
			for (PlotView p : Venues.built(s, path)) {
				List<Poi> altars = Venues.inPlot(s.pois(PoiType.ALTAR), p);
				if (altars.isEmpty()) {
					continue;
				}
				Poi altar = altars.get(0);
				double d = altar.pos().distSqr(square);
				if (d >= bestD) {
					continue;
				}
				List<Poi> seats = new ArrayList<>(Venues.inPlot(s.pois(PoiType.SEAT), p));
				seats.sort(Comparator.comparingDouble(q -> q.pos().distSqr(altar.pos())));
				List<Poi> doors = Venues.inPlot(s.pois(PoiType.DOOR), p);
				BlockPos door = doors.isEmpty() ? Venues.middle(p.spec()) : doors.get(0).pos();
				if (doors.isEmpty()) {
					// a door just outside the plot (blueprints report a doorway's outside block)
					double near = 36;
					for (Poi dp : s.pois(PoiType.DOOR)) {
						double dist = Math.sqrt(dp.pos().distSqr(Venues.middle(p.spec())));
						if (dist < near) {
							near = dist;
							door = dp.pos();
						}
					}
				}
				best = new Church(altar.pos(), altar.facing(), List.copyOf(seats), door, p);
				bestD = d;
			}
		}
		return best != null ? best : new Church(square, Direction.NORTH, List.of(), square, null);
	}

	static synchronized Day day(Settlement s, long day) {
		Day d = DAYS.get(s.id());
		if (d != null && d.day == day && d.residents == s.population()) {
			return d;
		}
		RhythmData data = RhythmData.of(s.culture());
		boolean rest = HearthServices.calendar().restDay(s, day);
		Set<UUID> attending = rest ? attending(s.residents(), day, data.service().share()) : Set.of();
		Church church = rest ? church(s) : null;
		Map<UUID, Integer> seats = new HashMap<>();
		List<UUID> leaders = rest ? leaders(s, 6) : List.of();
		Resident leads = leaders.isEmpty() ? null : s.resident(leaders.get(0));
		if (church != null && !church.seats().isEmpty()) {
			// the lord and the elders in front, then by a fixed order of the week
			List<Resident> sitting = new ArrayList<>();
			for (Resident r : s.residents()) {
				if (attending.contains(r.id) && (leads == null || !r.id.equals(leads.id))) {
					sitting.add(r);
				}
			}
			sitting.sort(Comparator.<Resident>comparingInt(r -> r.job == JobType.LORD ? 0 : r.age == AgeStage.ELDER ? 1 : 2)
				.thenComparingLong(r -> TownCalendar.mix(r.id.getLeastSignificantBits() ^ day)));
			for (int i = 0; i < sitting.size() && i < church.seats().size(); i++) {
				seats.put(sitting.get(i).id, i);
			}
		}
		d = new Day(day, s.population(), attending, seats, church, leads == null ? null : leads.id, leaders);
		DAYS.put(s.id(), d);
		if (DAYS.size() > 512) {
			DAYS.remove(DAYS.keySet().iterator().next());
		}
		return d;
	}

	/**
	 * Who in the town is the officiant: its priest, else a monk, else an elder says the words (a pious one first), else a
	 * grown-up of the town who goes (a pious one first). Null only for a town with no grown folk.
	 */
	static @Nullable Resident officiant(Settlement s) {
		Resident best = null;
		int bestRank = Integer.MAX_VALUE;
		for (Resident r : s.residents()) {
			if (r.job == JobType.PRIEST) {
				return r;
			}
			int rank = officiantRank(r);
			if (rank < bestRank) {
				best = r;
				bestRank = rank;
			}
		}
		return best;
	}

	/** The {@code most} best to lead, best first. */
	static List<UUID> leaders(Settlement s, int most) {
		List<Resident> all = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.job == JobType.PRIEST || officiantRank(r) != Integer.MAX_VALUE) {
				all.add(r);
			}
		}
		all.sort(Comparator.comparingInt(r -> r.job == JobType.PRIEST ? -1 : officiantRank(r)));
		List<UUID> out = new ArrayList<>();
		for (int i = 0; i < all.size() && i < most; i++) {
			out.add(all.get(i).id);
		}
		return out;
	}

	private static int officiantRank(Resident r) {
		if (r.age == AgeStage.CHILD || r.job == JobType.GUARD || r.job == JobType.JAILER || r.job == JobType.INNKEEPER || r.job == JobType.LORD) {
			return Integer.MAX_VALUE;
		}
		boolean pious = FolkFacts.has(r, "pious");
		if (r.job == JobType.MONK) {
			return 0;
		}
		if (r.age == AgeStage.ELDER) {
			return pious ? 1 : 2;
		}
		return pious ? 3 : 4;
	}

	static synchronized void clear() {
		DAYS.clear();
	}

	static synchronized void forget(long settlement) {
		DAYS.remove(settlement);
	}
}
