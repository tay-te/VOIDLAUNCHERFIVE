package dev.voidmc.hearth.quest.commission;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LivesService;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementKind;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.quest.model.SettlementQuests.Happening;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Binds a lord's commission to the domain's real state (docs/LIVING_WORLD_2.md §8.1–§8.2): the band on the lord's most
 * dangerous road (§1's road danger), the takings to carry, a reeve whose town's coffers run dry, a feud between two houses
 * (§4, when the lives service is there), a letter to the neighbouring lord, the poacher where the town has known crime, a
 * toll gate's takings (§7's posts, or a toll gate in a town of the domain), a village of the domain to hold against
 * raiders (§3's raids when the events are there, else the domain's worst-guarded village), a tournament (§3, only when one
 * is on), ground for a new village in the lord's name, the Great Mine (§6), and the lord's road by night. A kind the world
 * cannot bind now is skipped (null). Pure over {@link World}; the game's world reads the services.
 */
public final class Binder {
	private Binder() {
	}

	public enum Kind {
		BAND, TAX, REEVE, FEUD, LETTER, POACHER, TOLL, RAID, TOURNAMENT, NEW_VILLAGE, GREAT_MINE, PATROL;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Kind byId(String id) {
			for (Kind k : values()) {
				if (k.id().equals(id)) {
					return k;
				}
			}
			return null;
		}
	}

	/** What the binder reads of the world. */
	public interface World {
		long day();

		/** Other live settlements and posts of the seat's dimension. */
		List<? extends Settlement> others();

		@Nullable Resident lord(Settlement s);

		@Nullable Resident reeve(Settlement s);

		RoadNetwork roads();

		List<LivesService.Feud> feuds(Settlement s);

		/** The town events of a day (lane B1; empty without them). */
		List<String> events(Settlement s, long day);

		/** The quest board's crime pressure in a town. */
		int crime(Settlement s);

		/** Towns this near a seat without a lord of their own are of its domain. */
		int domainRadius();
	}

	/**
	 * A commission bound: the happening its template reads ({@code place: event}, {@code {other}}), and who to speak to (a
	 * TALK or COURIER objective's resident, by objective index, in their town).
	 */
	public record Binding(Happening happening, List<Speak> speak) {
	}

	public record Speak(int objective, Settlement town, Resident resident) {
	}

	/** Happening ids for commissions: below any a board makes. */
	static final int ID_BASE = -1_000_000;

	/** The seat's domain: towns near it with no lord of their own (posts too), nearest first. */
	public static List<Settlement> domain(Settlement seat, World w) {
		long r2 = (long) w.domainRadius() * w.domainRadius();
		List<Settlement> out = new ArrayList<>();
		for (Settlement o : w.others()) {
			if (o.id() != seat.id() && o.dimension() == seat.dimension() && seat.center().distSqr(o.center()) <= r2 && w.lord(o) == null) {
				out.add(o);
			}
		}
		out.sort(Comparator.comparingDouble(o -> o.center().distSqr(seat.center())));
		return out;
	}

	public static @Nullable Binding bind(Kind kind, Settlement seat, World w, long salt) {
		List<Settlement> domain = domain(seat, w);
		long day = w.day();
		int id = ID_BASE - (int) Math.floorMod(salt, 900_000L);
		return switch (kind) {
			case BAND -> {
				Danger d = mostDangerous(seat, domain, w, 0.25f);
				yield d == null ? null : new Binding(happening(id, "caravan_robbed", day, d.pos, d.other, "band"), List.of());
			}
			case PATROL -> {
				Danger d = mostDangerous(seat, domain, w, 0.0f);
				yield d == null ? null : new Binding(happening(id, "caravan_departed", day, d.pos, d.other, "patrol"), List.of());
			}
			case TAX -> {
				Settlement to = null;
				for (Settlement o : w.others()) {
					if (o.id() != seat.id() && w.lord(o) != null && (to == null || o.center().distSqr(seat.center()) < to.center().distSqr(seat.center()))) {
						to = o;
					}
				}
				if (to == null && !domain.isEmpty()) {
					to = domain.getFirst();
				}
				boolean taxDay = has(w.events(seat, day), "tax") || has(w.events(seat, day + 1), "tax");
				yield to == null || seat.treasury() < 200 && !taxDay ? null
					: new Binding(happening(id, "caravan_departed", day, seat.center(), to.id(), "tax"), List.of());
			}
			case REEVE -> {
				Settlement poorest = null;
				Resident reeve = null;
				for (Settlement o : domain) {
					Resident rv = w.reeve(o);
					if (rv != null && o.treasury() < 100L * (o.tier().ordinal() + 1) && (poorest == null || o.treasury() < poorest.treasury())) {
						poorest = o;
						reeve = rv;
					}
				}
				yield poorest == null ? null
					: new Binding(happening(id, "declining", day, poorest.center(), poorest.id(), reeve.fullName()), List.of(new Speak(0, poorest, reeve)));
			}
			case FEUD -> {
				List<Settlement> places = new ArrayList<>();
				places.add(seat);
				places.addAll(domain);
				for (Settlement o : places) {
					for (LivesService.Feud f : w.feuds(o)) {
						Resident a = o.resident(f.headA());
						Resident b = o.resident(f.headB());
						if (a != null && b != null) {
							long other = o.id() == seat.id() ? anyOther(seat, w) : o.id();
							if (other < 0) {
								continue;
							}
							yield new Binding(happening(id, "declining", day, o.center(), other, a.familyName + "|" + b.familyName),
								List.of(new Speak(0, o, a), new Speak(1, o, b)));
						}
					}
				}
				yield null;
			}
			case LETTER -> {
				Settlement best = null;
				Resident lord = null;
				for (Settlement o : w.others()) {
					Resident l = o.id() == seat.id() ? null : w.lord(o);
					if (l != null && (best == null || o.center().distSqr(seat.center()) < best.center().distSqr(seat.center()))) {
						best = o;
						lord = l;
					}
				}
				yield best == null ? null : new Binding(happening(id, "caravan_departed", day, best.center(), best.id(), lord.fullName()),
					List.of(new Speak(0, best, lord)));
			}
			case POACHER -> {
				Settlement where = w.crime(seat) > 0 ? seat : null;
				for (Settlement o : domain) {
					if (where == null && w.crime(o) > 0) {
						where = o;
					}
				}
				yield where == null ? null : new Binding(happening(id, "caravan_robbed", day, ring(where.center(), 120, salt), anyOther(seat, w), "poacher"),
					List.of());
			}
			case TOLL -> {
				BlockPos gate = null;
				long at = -1;
				for (Settlement o : domain) {
					if (o.kind() == SettlementKind.POST && o.postType().startsWith("toll")) {
						gate = o.center();
						at = o.id();
						break;
					}
					for (PlotView p : o.plots()) {
						if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().contains("toll")) {
							gate = p.spec().origin();
							at = o.id();
							break;
						}
					}
					if (gate != null) {
						break;
					}
				}
				yield gate == null ? null : new Binding(happening(id, "caravan_departed", day, gate, at, "toll"), List.of());
			}
			case RAID -> {
				Settlement threatened = null;
				for (Settlement o : domain) {
					if (o.kind() == SettlementKind.TOWN && (has(w.events(o, day), "raid") || has(w.events(o, day + 1), "raid"))) {
						threatened = o;
						break;
					}
				}
				if (threatened == null) {
					// without the events' raids: the domain's village with the fewest guards
					int fewest = Integer.MAX_VALUE;
					for (Settlement o : domain) {
						if (o.kind() != SettlementKind.TOWN || o.tier().ordinal() < SettlementTier.HAMLET.ordinal()) {
							continue;
						}
						int guards = 0;
						for (Resident r : o.residents()) {
							if (r.job == JobType.GUARD) {
								guards++;
							}
						}
						if (guards < fewest) {
							fewest = guards;
							threatened = o;
						}
					}
				}
				yield threatened == null ? null
					: new Binding(happening(id, "caravan_robbed", day, ring(threatened.center(), 40, salt), threatened.id(), "raid"), List.of());
			}
			case TOURNAMENT -> {
				Settlement at = null;
				List<Settlement> places = new ArrayList<>();
				places.add(seat);
				places.addAll(domain);
				for (Settlement o : places) {
					for (long d = day; d <= day + 3 && at == null; d++) {
						if (has(w.events(o, d), "tournament")) {
							at = o;
						}
					}
				}
				long other = at == null ? -1 : at.id() == seat.id() ? anyOther(seat, w) : at.id();
				yield at == null || other < 0 ? null : new Binding(happening(id, "caravan_departed", day, at.center(), other, "tournament"), List.of());
			}
			case NEW_VILLAGE -> seat.tier().ordinal() >= SettlementTier.VILLAGE.ordinal()
				? new Binding(happening(id, "caravan_departed", day, seat.center(), -1, "new village"), List.of()) : null;
			case GREAT_MINE -> {
				List<Settlement> places = new ArrayList<>();
				places.add(seat);
				places.addAll(domain);
				for (Settlement o : places) {
					for (ResourceSite site : o.sites()) {
						if (dev.voidmc.hearth.sim.land.GreatMines.isGreat(site)) {
							BlockPos c = site.center();
							yield new Binding(happening(id, "depleted", day, c, o.id() == seat.id() ? -1 : o.id(), "great mine"), List.of());
						}
					}
				}
				yield null;
			}
		};
	}

	private record Danger(BlockPos pos, long other, float danger) {
	}

	/** The most dangerous point on the roads out of the seat and its domain, at least {@code least} dangerous, or null. */
	private static @Nullable Danger mostDangerous(Settlement seat, List<Settlement> domain, World w, float least) {
		RoadNetwork roads = w.roads();
		Danger best = null;
		List<Settlement> places = new ArrayList<>();
		places.add(seat);
		places.addAll(domain);
		for (Settlement s : places) {
			for (RoadNetwork.RoadView road : roads.from(s.id())) {
				for (int i = 1; i < 16; i++) {
					double along = road.length() * i / 16.0;
					float d = roads.danger(road.id(), along);
					if (d >= least && (best == null || d > best.danger)) {
						long far = road.from() == s.id() ? road.to() : road.from();
						best = new Danger(road.at(along), far == seat.id() ? road.from() : far, d);
					}
				}
			}
		}
		return best;
	}

	private static long anyOther(Settlement seat, World w) {
		long best = -1;
		double bestD = Double.MAX_VALUE;
		for (Settlement o : w.others()) {
			if (o.id() != seat.id() && o.kind() == SettlementKind.TOWN) {
				double d = o.center().distSqr(seat.center());
				if (d < bestD) {
					best = o.id();
					bestD = d;
				}
			}
		}
		return best;
	}

	private static boolean has(List<String> events, String word) {
		for (String e : events) {
			if (e.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static BlockPos ring(BlockPos c, int r, long salt) {
		double a = Math.floorMod(salt * 2654435761L, 3600L) / 3600.0 * Math.PI * 2;
		return new BlockPos(c.getX() + (int) Math.round(Math.cos(a) * r), dev.voidmc.hearth.quest.model.Objective.SURFACE_Y,
			c.getZ() + (int) Math.round(Math.sin(a) * r));
	}

	private static Happening happening(int id, String kind, long day, BlockPos pos, long other, String detail) {
		return new Happening(id, kind, day, -1, pos, other, 1, "", detail, false);
	}
}
