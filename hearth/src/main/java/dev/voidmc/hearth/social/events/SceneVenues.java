package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Where the parts of a scene are played (docs/LIVING_WORLD_2.md §3.3): the venue names a rule's timeline uses, found
 * from the town's plots and points of interest, falling back to the square when the town has no such place. Pure apart
 * from reading the settlement.
 */
public final class SceneVenues {
	/** Every name a timeline may use. */
	public static final Set<String> NAMES = Set.of("square", "chapel", "chapel_door", "altar", "cemetery", "home", "home_door", "feast", "stage",
		"festival_ground", "courthouse", "throne", "pillory", "gallows", "town_hall", "plot", "plot_front", "storehouse", "barn", "gate", "well",
		"tilt_yard", "range", "ring", "kin_home");

	private SceneVenues() {
	}

	/** Where a venue is for this event, never null (the square at worst). */
	public static BlockPos at(Settlement s, Scheduled e, String name) {
		BlockPos p = find(s, e, name);
		return p == null ? Venues.square(s) : p;
	}

	/** The venue a crowd at this one faces: the altar in the chapel, else the place itself (the pillory, the stage). */
	public static String focusOf(String name) {
		return switch (name) {
			case "chapel" -> "altar";
			default -> name;
		};
	}

	static @Nullable BlockPos find(Settlement s, Scheduled e, String name) {
		BlockPos center = s.center();
		return switch (name) {
			case "square" -> Venues.square(s);
			case "altar", "chapel" -> nearest(s.pois(PoiType.ALTAR), center);
			case "chapel_door" -> {
				BlockPos altar = nearest(s.pois(PoiType.ALTAR), center);
				PlotView chapel = altar == null ? null : Venues.plotAt(s, altar);
				yield chapel == null ? altar : door(s, chapel);
			}
			case "cemetery" -> {
				PlotView c = first(s, "civic/cemetery");
				yield c != null ? Venues.middle(c.spec()) : nearest(s.pois(PoiType.ALTAR), center);
			}
			case "home", "home_door", "kin_home" -> {
				PlotView home = home(s, name.equals("kin_home") ? e.family() : e.principals());
				if (home == null && name.equals("kin_home")) {
					home = home(s, e.principals());
				}
				yield home == null ? null : name.equals("home") ? Venues.middle(home.spec()) : door(s, home);
			}
			case "feast" -> {
				List<BlockPos> tables = new ArrayList<>();
				for (PlotView p : Venues.built(s, "commerce/inn", "commerce/tavern", "landscape/festival_ground")) {
					tables.addAll(Venues.positions(Venues.inPlot(s.pois(PoiType.TABLE), p)));
				}
				yield tables.isEmpty() ? null : Venues.nearest(tables, Venues.square(s));
			}
			case "stage" -> {
				BlockPos stage = nearest(s.pois(PoiType.STAGE), Venues.square(s));
				if (stage == null) {
					PlotView g = first(s, "landscape/festival_ground");
					stage = g == null ? null : Venues.middle(g.spec());
				}
				yield stage;
			}
			case "festival_ground", "range", "ring" -> {
				PlotView g = first(s, "landscape/festival_ground");
				if (g == null) {
					g = first(s, "landscape/plaza");
				}
				yield g == null ? null : Venues.middle(g.spec());
			}
			case "courthouse", "throne" -> {
				BlockPos throne = nearest(s.pois(PoiType.THRONE), center);
				if (throne == null) {
					PlotView h = first(s, "law/courthouse");
					if (h == null) {
						h = first(s, "civic/town_hall");
					}
					throne = h == null ? null : Venues.middle(h.spec());
				}
				yield throne;
			}
			case "pillory" -> {
				BlockPos p = nearest(s.pois(PoiType.PILLORY), center);
				yield p != null ? p : null;
			}
			case "gallows" -> {
				BlockPos g = nearest(s.pois(PoiType.GALLOWS), center);
				yield g != null ? g : nearest(s.pois(PoiType.PILLORY), center);
			}
			case "town_hall" -> {
				PlotView h = first(s, "civic/town_hall");
				if (h == null) {
					h = first(s, "house/reeve_house");
				}
				yield h == null ? null : door(s, h);
			}
			case "plot", "plot_front" -> {
				PlotView p = e.plot() < 0 ? null : s.plot(e.plot());
				yield p == null ? null : name.equals("plot") ? Venues.middle(p.spec()) : door(s, p);
			}
			case "storehouse" -> {
				PlotView p = first(s, "storage/storehouse", "storage/granary", "storage/warehouse", "work/barn");
				yield p == null ? null : door(s, p);
			}
			case "barn" -> {
				PlotView p = first(s, "work/barn", "storage/granary");
				yield p == null ? null : door(s, p);
			}
			case "gate" -> gate(s);
			case "well" -> {
				PlotView p = first(s, "landscape/well", "landscape/fountain");
				yield p == null ? null : Venues.middle(p.spec());
			}
			case "tilt_yard" -> {
				PlotView p = first(s, "civic/tilt_yard");
				if (p == null) {
					yield null;
				}
				List<Poi> meets = Venues.inPlot(s.pois(PoiType.MEETING_POINT), p);
				yield meets.isEmpty() ? Venues.middle(p.spec()) : meets.get(0).pos();
			}
			default -> null;
		};
	}

	/** Where the town's roads leave it (the planner's exits), the one nearest the heart; else the heart. */
	/** Each town's gate as last worked out, with the plot count it was worked out at (the planner's exits are not free). */
	private static final java.util.Map<Long, Object[]> GATES = new java.util.concurrent.ConcurrentHashMap<>();

	static @Nullable BlockPos gate(Settlement s) {
		int plots = s.plots().size();
		Object[] known = GATES.get(s.id());
		if (known != null && (int) known[0] == plots) {
			return (BlockPos) known[1];
		}
		BlockPos g = gate0(s);
		if (GATES.size() > 512) {
			GATES.clear();
		}
		GATES.put(s.id(), new Object[]{plots, g});
		return g;
	}

	/** The town's gate if it has been worked out at its present plots, else null (nothing is worked out). */
	static @Nullable BlockPos gateIfKnown(Settlement s) {
		Object[] known = GATES.get(s.id());
		return known != null && (int) known[0] == s.plots().size() ? (BlockPos) known[1] : null;
	}

	private static @Nullable BlockPos gate0(Settlement s) {
		List<BlockPos> exits = List.of();
		try {
			if (HearthServices.hasPlanner()) {
				exits = HearthServices.planner().exits(s);
			}
		} catch (RuntimeException ignored) {
			exits = List.of();
		}
		if (exits.isEmpty()) {
			PlotView g = first(s, "defense/gatehouse");
			return g == null ? null : Venues.middle(g.spec());
		}
		return Venues.nearest(exits, s.center());
	}

	static @Nullable PlotView home(Settlement s, List<UUID> who) {
		for (UUID id : who) {
			Resident r = s.resident(id);
			if (r != null && r.home != Resident.NO_PLOT) {
				PlotView p = s.plot(r.home);
				if (p != null) {
					return p;
				}
			}
		}
		return null;
	}

	/** A plot's door (the DOOR point of interest in it nearest its middle), else its middle. */
	static BlockPos door(Settlement s, PlotView p) {
		List<Poi> doors = Venues.inPlot(s.pois(PoiType.DOOR), p);
		BlockPos mid = Venues.middle(p.spec());
		return doors.isEmpty() ? mid : Venues.nearest(Venues.positions(doors), mid);
	}

	static @Nullable PlotView first(Settlement s, String... paths) {
		List<PlotView> b = Venues.built(s, paths);
		if (b.isEmpty()) {
			return null;
		}
		BlockPos c = s.center();
		PlotView best = b.get(0);
		double bd = Double.MAX_VALUE;
		for (PlotView p : b) {
			double d = Venues.middle(p.spec()).distSqr(c);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	static @Nullable BlockPos nearest(List<Poi> pois, BlockPos to) {
		return pois.isEmpty() ? null : Venues.nearest(Venues.positions(pois), to);
	}
}
