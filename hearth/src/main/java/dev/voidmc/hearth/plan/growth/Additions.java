package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.road.Roads;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Houses that grow with their households (docs/buildings/age.md, LIVING_WORLD.md §8.4): a house first built with the
 * {@code additions} param (Build gives it to every house with a yard to grow into) has the count raised by one as its
 * household fills it, and a little as the town grows: a lean-to, an outshot, a dormer, a jetty, a storey, a roof room.
 * Counting up only adds to the house (the build module keeps a house's first n additions when it draws n + 1), so the
 * project replaces the plot in place: the sim builds the addition over the standing house, which stays lived in
 * ({@code SimSettlement.inPlace}). Never given to a house built without the param (it would be redrawn smaller on its plot).
 */
public final class Additions {
	/** The build module's ceiling on the count. */
	static final int MOST = 8;

	private Additions() {
	}

	/** The need, when some household has outgrown its house or the town has grown past what its houses show. */
	public static Growth.@Nullable Need need(PlanContext ctx) {
		if (pending(ctx)) {
			return null;
		}
		Candidate c = choose(ctx);
		if (c == null) {
			return null;
		}
		return new Growth.Need("additions", c.crowded ? 260 : 90, Additions::plan);
	}

	/** Raises the additions of one house: the most crowded, then the one nearest the heart with the fewest. */
	public static boolean plan(PlanContext ctx) {
		if (pending(ctx)) {
			return false;
		}
		Candidate c = choose(ctx);
		if (c == null) {
			return false;
		}
		PlotSpec old = c.plot.spec();
		CompoundTag params = old.params().copy();
		params.putInt("additions", c.count + 1);
		params.putInt("replaces", c.plot.id());
		PlotSpec spec = new PlotSpec(old.archetype(), old.style(), old.seed(), old.origin(), old.sizeX(), old.sizeZ(), old.facing(), old.maxHeight(),
			old.tier(), params);
		ArchetypeInfo info = ctx.info(old.archetype());
		String name = info == null ? old.archetype().getPath() : Build.shortName(info);
		Roads.issue(ctx, spec, c.plot.id(), (c.crowded ? "a household outgrows its " : "a household prospers: its ") + name + " gets "
			+ (c.count == 0 ? "its first addition" : "addition " + (c.count + 1)));
		return true;
	}

	private record Candidate(PlotView plot, int count, boolean crowded) {
	}

	/** One addition at a time is planned or going up. */
	private static boolean pending(PlanContext ctx) {
		for (QueuedProject q : ctx.state.queue) {
			if (q.replaces() >= 0 && q.spec().params().contains("additions")) {
				return true;
			}
		}
		for (PlotView p : ctx.settlement.plots()) {
			if ((p.state() == PlotState.PLANNED || p.state() == PlotState.BUILDING) && p.spec().params().contains("replaces")
				&& p.spec().params().contains("additions")) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Candidate choose(PlanContext ctx) {
		Int2IntOpenHashMap household = new Int2IntOpenHashMap();
		for (Resident r : ctx.settlement.residents()) {
			if (r.home != Resident.NO_PLOT) {
				household.addTo(r.home, 1);
			}
		}
		Set<Integer> replacing = Upgrades.replacing(ctx);
		// the town's houses show about one addition for every four folk past the first dozen, the oldest streets most
		int total = 0;
		for (PlotView p : ctx.settlement.plots()) {
			total += Math.max(0, p.spec().paramInt("additions", 0));
		}
		boolean roomToGrow = total < Math.max(0, (ctx.population - 12) / 4);
		int cap = 1 + ctx.tier.ordinal();
		Candidate best = null;
		double bestScore = Double.MAX_VALUE;
		for (PlotView p : ctx.settlement.plots()) {
			if (p.state() != PlotState.BUILT || p.owner() != null || !p.spec().hasParam("additions") || replacing.contains(p.id())) {
				continue;
			}
			ArchetypeInfo info = ctx.info(p.spec().archetype());
			if (info == null || info.category() != ArchetypeCategory.HOUSING) {
				continue;
			}
			int n = p.spec().paramInt("additions", -1);
			if (n < 0 || n >= Math.min(MOST, cap)) {
				continue;
			}
			int folk = household.get(p.id());
			int beds = Math.max(1, info.beds());
			boolean crowded = folk >= beds && folk > 0;
			if (!crowded && (!roomToGrow || folk == 0)) {
				continue;
			}
			int cx = p.spec().origin().getX() + p.spec().sizeX() / 2;
			int cz = p.spec().origin().getZ() + p.spec().sizeZ() / 2;
			double score = (crowded ? 0 : 10_000) + n * 400 + ctx.distanceToHeart(cx, cz) + ctx.hash(cx, cz, 57) * 8;
			if (score < bestScore) {
				bestScore = score;
				best = new Candidate(p, n, crowded);
			}
		}
		return best;
	}
}
