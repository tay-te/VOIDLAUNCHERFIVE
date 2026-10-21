package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's remote work sites (docs/LIVING_WORLD_2.md §0.2, §6.2): the places in its territory beyond the houses
 * where its people work by day — the meadows its flocks are driven out to, the Great Mine's pithead, the charcoal clamp
 * and the logging camp in the woods — and who works at each. Worked out from the settlement's plots, its surveyed sites
 * and its residents, so the same books give the same sites; nothing is saved.
 *
 * <p>A pasture plot whose herders have a meadow of the territory within {@code pasture_distance} gets that meadow (each
 * meadow taken once, nearest first, by plot id). The Great Mine's pithead, the charcoal kilns and the logging camps are
 * sites of their plots. Sites outside the town's bounds are {@link Site#remote remote}: embodied when a player comes
 * near, whether or not the town is.
 */
public final class RemoteSites {
	public static final Identifier PASTURE = Hearth.id("farm/pasture");
	public static final Identifier GREAT_PITHEAD = Hearth.id("extract/great_pithead");
	public static final Identifier CHARCOAL_KILN = Hearth.id("work/charcoal_kiln");
	public static final Identifier LOGGING_CAMP = Hearth.id("work/logging_camp");

	/** Herders drive the flock out at 07:00 and have it back in the pen by 18:00. */
	public static final int DROVE_START = 7 * 60;
	public static final int DROVE_END = 18 * 60;
	/** Miners walk up to the pithead at 06:00 and come home at 18:00. */
	public static final int SHIFT_START = 6 * 60;
	public static final int SHIFT_END = 18 * 60;

	public enum Kind {
		PASTURE, PITHEAD, CLAMP, CAMP
	}

	/**
	 * @param work     where the work is (a meadow's middle, the pithead deck, the clamp's clearing)
	 * @param from     the town end of the way out: the pen's gate for a flock, null where each worker sets out from home
	 * @param radius   how far about {@code work} the site spreads (a meadow grazed, a clearing)
	 * @param plot     the plot the site belongs to (the pasture whose flock grazes there; the pithead, kiln or camp)
	 * @param resource the surveyed site it lies on, or -1
	 * @param remote   it lies outside the town's bounds: embodied on its own when a player comes near
	 */
	public record Site(Kind kind, BlockPos work, @Nullable BlockPos from, int radius, int plot, int resource, boolean remote) {
		/** The working window (minutes of the day) for this kind of site, or null for the trade's own hours. */
		public int @Nullable [] window() {
			return switch (this.kind) {
				case PASTURE -> new int[]{DROVE_START, DROVE_END};
				case PITHEAD -> new int[]{SHIFT_START, SHIFT_END};
				default -> null;
			};
		}
	}

	/** The sites, and which site each worker works at (an index into {@code sites}). */
	public record Plan(List<Site> sites, Map<UUID, Integer> workers) {
		public static final Plan NONE = new Plan(List.of(), Map.of());

		public @Nullable Site of(UUID resident) {
			Integer i = this.workers.get(resident);
			return i == null ? null : this.sites.get(i);
		}

		/** The residents working at site {@code index}. */
		public List<UUID> workersAt(int index) {
			List<UUID> out = new ArrayList<>();
			for (Map.Entry<UUID, Integer> e : this.workers.entrySet()) {
				if (e.getValue() == index) {
					out.add(e.getKey());
				}
			}
			out.sort(Comparator.naturalOrder());
			return out;
		}

		public boolean anyRemote() {
			for (Site s : this.sites) {
				if (s.remote) {
					return true;
				}
			}
			return false;
		}
	}

	private RemoteSites() {
	}

	/** The trade that works a kind of site. */
	public static JobType trade(Kind kind) {
		return switch (kind) {
			case PASTURE -> JobType.HERDER;
			case PITHEAD -> JobType.MINER;
			case CLAMP -> JobType.CHARCOAL_BURNER;
			case CAMP -> JobType.FORESTER;
		};
	}

	/** Works out a settlement's sites. Cheap (one pass over its plots, sites and residents); callers cache it. */
	public static Plan plan(Settlement s, FieldWorkConfig.Sites cfg) {
		if (!cfg.enabled()) {
			return Plan.NONE;
		}
		// whose work plot is which, by trade
		Map<Integer, List<Resident>> byPlot = new HashMap<>();
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && r.work != Resident.NO_PLOT && !s.isResidentAway(r.id)) {
				byPlot.computeIfAbsent(r.work, k -> new ArrayList<>()).add(r);
			}
		}
		List<PlotView> plots = new ArrayList<>(s.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		BoundingBox town = s.bounds();
		List<ResourceSite> meadows = new ArrayList<>();
		for (ResourceSite r : s.sites()) {
			// the open land beyond the houses: not the town's own paddocks, nor under a plot
			if (r.kind() == ResourceSite.Kind.PASTURE && r.remaining() > 0 && Commute.distance(r.center(), s.center()) >= MEADOW_FROM_HEART
				&& !underPlot(plots, r.center())) {
				meadows.add(r);
			}
		}
		Set<Integer> taken = new HashSet<>();
		List<Site> sites = new ArrayList<>();
		Map<UUID, Integer> workers = new HashMap<>();
		for (PlotView p : plots) {
			if (p.state() != PlotState.BUILT) {
				continue;
			}
			Identifier a = p.spec().archetype();
			Kind kind = a.equals(PASTURE) ? Kind.PASTURE : a.equals(GREAT_PITHEAD) ? Kind.PITHEAD : a.equals(CHARCOAL_KILN) ? Kind.CLAMP
				: a.equals(LOGGING_CAMP) ? Kind.CAMP : null;
			if (kind == null) {
				continue;
			}
			List<Resident> crew = new ArrayList<>();
			for (Resident r : byPlot.getOrDefault(p.id(), List.of())) {
				if (r.job == trade(kind)) {
					crew.add(r);
				}
			}
			if (crew.isEmpty()) {
				continue;
			}
			Site site;
			if (kind == Kind.PASTURE) {
				BlockPos gate = gate(p.spec());
				ResourceSite best = null;
				double bestD = Double.MAX_VALUE;
				for (ResourceSite m : meadows) {
					double d = Commute.distance(m.center(), gate);
					if (!taken.contains(m.id()) && d <= cfg.pastureDistance() && d < bestD) {
						best = m;
						bestD = d;
					}
				}
				if (best == null) {
					continue;
				}
				taken.add(best.id());
				BoundingBox area = best.area();
				int radius = Math.max(6, Math.min(20, Math.min(area.getXSpan(), area.getZSpan()) / 2));
				site = new Site(kind, best.center(), gate, radius, p.id(), best.id(), true);
			} else {
				BlockPos c = centre(p.spec());
				site = new Site(kind, c, null, Math.max(4, Math.min(p.spec().sizeX(), p.spec().sizeZ()) / 2), p.id(), p.spec().params().getIntOr("site", -1),
					!inside(town, c, 0));
			}
			int index = sites.size();
			sites.add(site);
			for (Resident r : crew) {
				workers.put(r.id, index);
			}
			if (sites.size() >= cfg.maxPerTown()) {
				break;
			}
		}
		return sites.isEmpty() ? Plan.NONE : new Plan(List.copyOf(sites), Map.copyOf(workers));
	}

	/** The middle of a plot at its floor. */
	public static BlockPos centre(PlotSpec spec) {
		return spec.origin().offset(spec.sizeX() / 2, 0, spec.sizeZ() / 2);
	}

	/** The middle of a plot's front edge, a block out: where its gate opens on the street. */
	public static BlockPos gate(PlotSpec spec) {
		BlockPos c = centre(spec);
		Direction f = spec.facing();
		int half = (f.getAxis() == Direction.Axis.X ? spec.sizeX() : spec.sizeZ()) / 2 + 1;
		return c.relative(f, half);
	}

	/** A meadow driven out to lies at least this far from the town's heart. */
	static final int MEADOW_FROM_HEART = 48;

	private static boolean underPlot(List<PlotView> plots, BlockPos p) {
		for (PlotView v : plots) {
			PlotSpec s = v.spec();
			BlockPos o = s.origin();
			if (p.getX() >= o.getX() && p.getX() < o.getX() + s.sizeX() && p.getZ() >= o.getZ() && p.getZ() < o.getZ() + s.sizeZ()) {
				return true;
			}
		}
		return false;
	}

	private static boolean inside(BoundingBox box, BlockPos p, int margin) {
		return p.getX() >= box.minX() - margin && p.getX() <= box.maxX() + margin && p.getZ() >= box.minZ() - margin && p.getZ() <= box.maxZ() + margin;
	}
}
