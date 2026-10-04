package dev.voidmc.hearth.plan.resource;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.PlanConfig;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.model.QueuedProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What the land round a settlement gives it, as the planner reads it (LIVING_WORLD.md §7): the resource sites the sim's
 * survey found ({@code Settlement.sites()}), how many folk the territory's food can carry, which way the wind blows
 * (tanneries and the poor quarter go downwind), and the materials its woods and rock give every building ({@code local}).
 * Made once per planning call ({@link PlanContext#territory()}); pure arithmetic on the sites and the probe.
 */
public final class Territory {
	/** Food a resident eats a day (SIM.md: adults 2, children 1, elders 1.5): what a site's food feeds. */
	private static final double FOOD_PER_HEAD = 2.0;
	private static final int[] FOOD_GOODS = {Goods.FOOD.ordinal(), Goods.GRAIN.ordinal(), Goods.MEAT.ordinal(), Goods.FISH.ordinal(),
		Goods.PRODUCE.ordinal()};

	private final PlanContext ctx;
	private final List<ResourceSite> sites;
	private final double windX;
	private final double windZ;
	private int capacity = -1;
	private @Nullable CompoundTag local;
	private boolean localDone;

	public Territory(PlanContext ctx) {
		this.ctx = ctx;
		List<ResourceSite> s = new ArrayList<>(ctx.settlement.sites());
		s.sort(Comparator.comparingInt(ResourceSite::id));
		this.sites = List.copyOf(s);
		double[] w = windTo(ctx.config.living(), ctx.ground.probe());
		this.windX = w[0];
		this.windZ = w[1];
	}

	/** Every site the survey found, in id order. */
	public List<ResourceSite> sites() {
		return this.sites;
	}

	/** Sites of a kind that still hold something and lie within reach of the heart, nearest first. */
	public List<ResourceSite> workable(ResourceSite.Kind kind) {
		List<ResourceSite> out = new ArrayList<>();
		int reach = Math.min(this.ctx.config.living().haulMaxDistance(), Math.max(96, this.ctx.settlement.territoryRadius() + 32));
		for (ResourceSite s : this.sites) {
			if (s.kind() == kind && !s.depleted() && s.remaining() > 0 && distance(s) <= reach) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparingDouble((ResourceSite s) -> distance(s)).thenComparingInt(ResourceSite::id));
		return out;
	}

	public double distance(ResourceSite s) {
		return this.ctx.distanceToHeart(s.center().getX(), s.center().getZ());
	}

	/** Whether the survey gave this settlement any sites (no sim survey yet, or none in reach: the phase 2 behaviour). */
	public boolean surveyed() {
		return !this.sites.isEmpty();
	}

	/**
	 * How many folk the territory carries (LIVING_WORLD.md §7.2): the food its fertile land, pasture and waters give back
	 * each day, over what a head eats, times the trade margin (a town may import a little); 0 when nothing is known, which
	 * leaves growth unbounded. The sim decides who comes; the planner only stops building beds past this.
	 */
	public int capacity() {
		if (this.capacity < 0) {
			// one carrying capacity: the sim's, when it reckons one (requests/p3-plan.md §2); else the planner's estimate
			int sim = this.ctx.settlement.carryingCapacity();
			if (!this.ctx.config.living().carryingCapacity()) {
				this.capacity = 0;
			} else if (sim > 0 && sim != Integer.MAX_VALUE) {
				this.capacity = sim;
			} else {
				this.capacity = estimateCapacity(this.sites, this.ctx.config.living());
			}
		}
		return this.capacity;
	}

	/** The carrying-capacity estimate on its own (tests and previews). */
	public static int estimateCapacity(List<ResourceSite> sites, PlanConfig.Living living) {
		if (!living.carryingCapacity()) {
			return 0;
		}
		double food = 0;
		boolean any = false;
		for (ResourceSite s : sites) {
			if (s.kind() != ResourceSite.Kind.FERTILE && s.kind() != ResourceSite.Kind.PASTURE && s.kind() != ResourceSite.Kind.FISH) {
				continue;
			}
			double share = foodShare(s);
			if (share <= 0) {
				continue;
			}
			any = true;
			// what comes back each day is what can be eaten for ever; a site with no regrowth is food for a month
			double perDay = s.regenPerDay() > 0 ? s.regenPerDay() : s.remaining() / 30.0;
			food += perDay * share;
		}
		if (!any) {
			return 0;
		}
		return (int) Math.round(food / FOOD_PER_HEAD * Math.max(1, living.tradeMargin())) + 6;
	}

	private static double foodShare(ResourceSite s) {
		float[] y = s.yields();
		if (y == null || y.length == 0) {
			return 1;
		}
		double all = 0;
		double food = 0;
		for (int i = 0; i < y.length; i++) {
			all += Math.max(0, y[i]);
		}
		for (int g : FOOD_GOODS) {
			if (g < y.length) {
				food += Math.max(0, y[g]);
			}
		}
		return all <= 0 ? 1 : food / all;
	}

	/** How much the wind carries a column's smoke and smell away from the heart: 1 straight downwind, -1 upwind. */
	public double downwind(int x, int z) {
		double dx = x - this.ctx.heartX();
		double dz = z - this.ctx.heartZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		return d < 1 ? 0 : (dx * this.windX + dz * this.windZ) / d;
	}

	/** The direction the wind blows toward, as a unit vector (x, z). */
	public double[] windToward() {
		return new double[]{this.windX, this.windZ};
	}

	/**
	 * The prevailing wind of a world: the config's, or one per world from its seed — mostly westerly, as on the middle of
	 * a real continent, swinging up to sixty degrees either way.
	 */
	static double[] windTo(PlanConfig.Living living, TerrainProbe probe) {
		double from = living.prevailingWind();
		if (from < 0) {
			long h = probe.seed() * 0x9E3779B97F4A7C15L ^ 0x57494E44L;
			h ^= h >>> 29;
			h *= 0xBF58476D1CE4E5B9L;
			h ^= h >>> 32;
			double u = (h >>> 11) * 0x1.0p-53;
			from = 270 + (u - 0.5) * 120;
		}
		double a = Math.toRadians(from);
		// from north (0°) the wind blows toward +z (south); from east (90°) toward -x
		return new double[]{-StrictMath.sin(a), StrictMath.cos(a)};
	}

	/** The materials the territory gives (the {@code local} param), or null where nothing is known. */
	public @Nullable CompoundTag local() {
		if (!this.localDone) {
			this.localDone = true;
			this.local = this.ctx.config.living().localMaterials() ? Materials.local(this.ctx, this) : null;
		}
		return this.local;
	}

	/**
	 * Centres of the plots of an archetype, standing, building or queued, in id order (then queue order). Indexed once
	 * per call, on first use: what is queued later in the same call is not seen (it is the next call's).
	 */
	public List<int[]> positions(Identifier id) {
		if (this.index == null) {
			java.util.Map<Identifier, List<int[]>> m = new java.util.HashMap<>();
			List<PlotView> plots = new ArrayList<>(this.ctx.settlement.plots());
			plots.sort(Comparator.comparingInt(PlotView::id));
			for (PlotView p : plots) {
				if (p.state() != dev.voidmc.hearth.api.PlotState.DEMOLISHING) {
					m.computeIfAbsent(p.spec().archetype(), k -> new ArrayList<>()).add(centre(p.spec()));
				}
			}
			for (QueuedProject q : this.ctx.state.queue) {
				m.computeIfAbsent(q.spec().archetype(), k -> new ArrayList<>()).add(centre(q.spec()));
			}
			this.index = m;
		}
		return this.index.getOrDefault(id, List.of());
	}

	private java.util.@Nullable Map<Identifier, List<int[]>> index;

	/** The first standing or planned plot of any of these archetypes, as its centre, or null. */
	public int @Nullable [] first(Identifier... ids) {
		for (Identifier id : ids) {
			List<int[]> p = positions(id);
			if (!p.isEmpty()) {
				return p.get(0);
			}
		}
		return null;
	}

	/** The nearest plot of an archetype to a point, as its centre, or null. */
	public int @Nullable [] nearest(Identifier id, int x, int z) {
		int[] best = null;
		double bestD = Double.MAX_VALUE;
		for (int[] p : positions(id)) {
			double d = Math.hypot(p[0] - x, p[1] - z);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	public static int[] centre(PlotSpec s) {
		return new int[]{s.origin().getX() + s.sizeX() / 2, s.origin().getZ() + s.sizeZ() / 2};
	}
}
