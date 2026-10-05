package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.road.Specs;
import dev.voidmc.hearth.plan.terrain.Ground;

/**
 * Piers and jetties where a road meets the water: a harbour town's quays, a lagoon village's jetties, a river town's
 * landing stages. A dock runs straight out from a road over open water for 8 to 16 blocks, at least a dozen blocks
 * from the next.
 */
public final class Docks {
	private Docks() {
	}

	static boolean wanted(PlanContext ctx, Needs needs) {
		if (!ctx.catalogue.has(Archetypes.DOCK) || ctx.tier.ordinal() < SettlementTier.HAMLET.ordinal() || ctx.state.site == null
			|| !ctx.state.site.hasWater() || ctx.state.site.waterDistance() > ctx.coreRadius() * 1.5) {
			return false;
		}
		boolean harbour = ctx.state.signature.docks || ctx.state.layout == LayoutType.LINEAR || ctx.state.layout == LayoutType.STILT;
		int want = harbour ? Math.min(6, 1 + ctx.population / 30) : Math.min(2, ctx.population / 50);
		return needs.count(Archetypes.DOCK) < want;
	}

	static boolean plan(PlanContext ctx) {
		Ground g = ctx.ground;
		int[] best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		java.util.List<int[]> docks = existing(ctx);
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (!e.emits() || e.built < 2) {
				continue;
			}
			Polyline.Dense d = e.dense();
			int lim = Math.min(e.built, d.length());
			for (int i = 0; i < lim; i += 2) {
				if (e.typeAt(i) != RoadEdge.GRADE) {
					// beside a bridge the parapet would cut the pier off (requests/arch-infra.md §3)
					continue;
				}
				int x = d.xs()[i];
				int z = d.zs()[i];
				for (int h = 0; h < 8; h += 2) {
					int dx = Roads.DX[h];
					int dz = Roads.DZ[h];
					// from the road's edge, find the shore within a few blocks
					int start = -1;
					for (int k = e.width / 2 + 1; k <= e.width / 2 + 4; k++) {
						if (g.isWater(x + dx * k, z + dz * k)) {
							start = k;
							break;
						}
						if (!Cell.isBuildable(ctx.occ.get(x + dx * k, z + dz * k)) && !Cell.isAccess(ctx.occ.get(x + dx * k, z + dz * k))) {
							break;
						}
					}
					if (start < 0) {
						continue;
					}
					int len = 0;
					for (int k = start; k < start + 16; k++) {
						int px = x + dx * k;
						int pz = z + dz * k;
						if (!g.isWater(px, pz) || g.depth(px, pz) < 1 || !clear(ctx, px, pz, h)) {
							break;
						}
						len++;
					}
					if (len < 8) {
						continue;
					}
					int ex = x + dx * (start + len - 1);
					int ez = z + dz * (start + len - 1);
					boolean spaced = true;
					for (int[] o : docks) {
						if (Math.hypot(o[0] - x, o[1] - z) < 14) {
							spaced = false;
							break;
						}
					}
					if (!spaced) {
						continue;
					}
					double score = -ctx.distanceToHeart(x, z) + len * 0.5 + ctx.hash(x, z, 23) * 4;
					if (score > bestScore) {
						bestScore = score;
						// the pier starts on the road's edge column: no unpaved strip between road and pier (arch-infra.md §3)
						int edge = e.width / 2;
						best = new int[]{x + dx * edge, z + dz * edge, ex, ez, d.ys()[i]};
					}
				}
			}
		}
		if (best == null) {
			return false;
		}
		int water = g.water(best[2], best[3]);
		int deck = Math.max(water, best[4] - 1);
		int[] pts = {best[0], best[4], best[1], best[2], deck, best[3]};
		Roads.issue(ctx, Specs.dock(ctx, pts, 3), "harbour: a pier out over the water");
		return true;
	}

	private static boolean clear(PlanContext ctx, int x, int z, int heading) {
		int ox = -Roads.DZ[heading];
		int oz = Roads.DX[heading];
		for (int s = -1; s <= 1; s++) {
			if (ctx.occ.get(x + ox * s, z + oz * s) != Cell.FREE) {
				return false;
			}
		}
		return true;
	}

	private static java.util.List<int[]> existing(PlanContext ctx) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		for (PlotView p : ctx.settlement.plots()) {
			if (p.spec().archetype().equals(Archetypes.DOCK)) {
				int[] pts = p.spec().params().getIntArray("points").orElse(new int[3]);
				out.add(new int[]{pts[0], pts[2]});
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			if (q.spec().archetype().equals(Archetypes.DOCK)) {
				int[] pts = q.spec().params().getIntArray("points").orElse(new int[3]);
				out.add(new int[]{pts[0], pts[2]});
			}
		}
		return out;
	}
}
