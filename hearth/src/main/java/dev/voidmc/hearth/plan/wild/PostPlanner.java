package dev.voidmc.hearth.plan.wild;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.wild.PostData;
import dev.voidmc.hearth.sim.wild.PostSite;
import dev.voidmc.hearth.sim.wild.PostType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The planner of a post (docs/LIVING_WORLD_2.md §7.2), called by {@code HearthPlanner} for any settlement of kind
 * {@code POST}: {@link #found} lays out the type's fixed programme ({@link PostLayouts}); {@link #next} only ever rebuilds
 * a programme plot that is gone, or pitches a nomad camp on the ground it moved to. A post never grows past its
 * programme.
 *
 * <p>Cheap when nothing is missing: the plots standing are counted against the programme's size first, and the layout
 * (a few hundred probe reads) is worked out again only when one is short.
 */
public final class PostPlanner {
	private PostPlanner() {
	}

	public static List<PlotSpec> found(SettlementPlanner.FoundingContext fc) {
		Settlement s = fc.settlement();
		PostData d = PostData.of(s);
		if (d == null || d.site == null) {
			return List.of();
		}
		return programme(d, s, fc.terrain());
	}

	/** The plots the post should have now (a nomad camp: those of the ground it stands on). */
	static List<PlotSpec> programme(PostData d, Settlement s, TerrainProbe terrain) {
		PostSite site = d.site;
		if (site.type() == PostType.NOMAD_CAMP) {
			return PostLayouts.camp(site, s.culture(), terrain, d.ground);
		}
		return PostLayouts.layOut(site, s.culture(), terrain);
	}

	/** A plot to rebuild, the nomads' next tent, or nothing (the usual answer). */
	public static SettlementPlanner.@Nullable GrowthProject next(Settlement s, TerrainProbe terrain, RandomSource random) {
		PostData d = PostData.of(s);
		if (d == null || d.site == null) {
			return null;
		}
		int standing = 0;
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.DEMOLISHING && p.state() != PlotState.ABANDONED) {
				standing++;
			}
		}
		int expected = expectedCount(s, d, terrain);
		if (standing >= expected) {
			return null;
		}
		for (PlotSpec spec : programme(d, s, terrain)) {
			if (!present(s, spec)) {
				String why = d.site.type() == PostType.NOMAD_CAMP ? "the camp pitched on its other ground" : "rebuilding what was lost";
				return new SettlementPlanner.GrowthProject(spec, -1, why);
			}
		}
		return null;
	}

	/** The programme's size, remembered per settlement and ground (it never changes for a site). */
	private static int expectedCount(Settlement s, PostData d, TerrainProbe terrain) {
		long key = s.id() * 4 + d.ground;
		Integer n = COUNTS.get(key);
		if (n == null) {
			n = programme(d, s, terrain).size();
			if (COUNTS.size() > 4096) {
				COUNTS.clear();
			}
			COUNTS.put(key, n);
		}
		return n;
	}

	private static final java.util.Map<Long, Integer> COUNTS = new java.util.concurrent.ConcurrentHashMap<>();

	/** Whether a plot of this archetype stands (or is going up) at this spot. */
	static boolean present(Settlement s, PlotSpec spec) {
		for (PlotView p : s.plots()) {
			if (p.state() == PlotState.DEMOLISHING || p.state() == PlotState.ABANDONED) {
				continue;
			}
			PlotSpec o = p.spec();
			if (o.archetype().equals(spec.archetype()) && o.origin().getX() == spec.origin().getX() && o.origin().getZ() == spec.origin().getZ()) {
				return true;
			}
		}
		return false;
	}

	/** Where roads end at a post: its lane's mouth on the road, or its front (the first point of its first road). */
	public static List<BlockPos> exits(Settlement s) {
		List<BlockPos> out = new ArrayList<>();
		for (PlotView p : s.plots()) {
			PlotSpec spec = p.spec();
			if (spec.archetype().equals(PostLayouts.ROAD)) {
				int[] pts = spec.paramInts("points").orElse(new int[0]);
				if (pts.length >= 3) {
					out.add(new BlockPos(pts[0], pts[1], pts[2]));
				}
				break;
			}
		}
		return out;
	}
}
