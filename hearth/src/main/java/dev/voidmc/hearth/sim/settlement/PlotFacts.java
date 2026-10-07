package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.chains.SimData;
import org.jspecify.annotations.Nullable;

/**
 * What the clock needs to know about a plot's blueprint, read once and kept with the plot (blueprints are cached by the
 * build module, but the clock asks for these numbers every step and must not pay for a cache lookup each time).
 *
 * @param stepCount steps per stage
 * @param work      labour per stage
 * @param goods     goods per stage, {@code [stage][goods.ordinal()]}
 * @param jobs      job sites by {@link JobType#ordinal()}
 * @param cuts      for an excavation ({@code hearth:extract/*}), the blocks each stage cuts out of the natural land (air
 *                  steps below the probe's ground; a woodlot's felled blocks at any height), else null
 */
public record PlotFacts(int stageCount, int[] stepCount, int[] work, int[][] goods, int beds, int[] jobs, boolean storage, int @Nullable [] cuts,
	int[] campaigns) {
	public PlotFacts(int stageCount, int[] stepCount, int[] work, int[][] goods, int beds, int[] jobs, boolean storage, int @Nullable [] cuts) {
		this(stageCount, stepCount, work, goods, beds, jobs, storage, cuts, new int[stageCount]);
	}

	/**
	 * Whether a stage is the last of a campaign after the opening one ({@code Blueprint#campaign}): a bench, a gallery, a
	 * woodlot's coupe finished.
	 */
	public boolean endsCampaign(int stage) {
		if (stage < 0 || stage >= this.campaigns.length || this.campaigns[stage] <= 0) {
			return false;
		}
		return stage == this.campaigns.length - 1 || this.campaigns[stage + 1] != this.campaigns[stage];
	}

	public static PlotFacts of(PlotSpec spec, BlueprintService blueprints, TerrainProbe terrain) {
		return of(spec, blueprints, terrain, null);
	}

	/** As above; {@code extraction} (if the archetype is an excavation) says which cuts count. */
	public static PlotFacts of(PlotSpec spec, BlueprintService blueprints, TerrainProbe terrain, SimData.@Nullable Extraction extraction) {
		return of(spec, blueprints, terrain, extraction, null);
	}

	/**
	 * As above, for a building written over another in place ({@code over}: the spec it changes, {@code
	 * SimSettlement#inPlace}): each stage's labour and goods are only its share of the steps that change what stands
	 * (an addition costs the lean-to, not the house again).
	 */
	public static PlotFacts of(PlotSpec spec, BlueprintService blueprints, TerrainProbe terrain, SimData.@Nullable Extraction extraction,
		@Nullable PlotSpec over) {
		Blueprint bp = blueprints.blueprint(spec, terrain);
		int n = bp.stageCount();
		int[] steps = new int[n];
		int[] work = new int[n];
		int[][] goods = new int[n][];
		int[] campaigns = new int[n];
		for (int s = 0; s < n; s++) {
			campaigns[s] = bp.campaign(s);
			steps[s] = bp.stepCount(s);
			work[s] = Math.max(0, bp.work(s));
			int[] g = bp.goods(s);
			goods[s] = g.length == Goods.VALUES.length ? g.clone() : java.util.Arrays.copyOf(g, Goods.VALUES.length);
		}
		int[] jobs = new int[JobType.values().length];
		for (Poi poi : bp.pois()) {
			if (poi.type() == PoiType.JOB_SITE && poi.job() != null && poi.job() != JobType.NONE) {
				jobs[poi.job().ordinal()]++;
			}
		}
		ArchetypeInfo info = blueprints.archetype(spec.archetype());
		if (info != null) {
			// A workplace whose blueprint marks no job sites still employs one of each of its trades.
			for (JobType job : info.jobs()) {
				if (jobs[job.ordinal()] == 0) {
					jobs[job.ordinal()] = 1;
				}
			}
		}
		jobs = listed(jobs, info);
		boolean storage = info != null && info.category() == ArchetypeCategory.STORAGE;
		int[] cuts = null;
		if (extraction != null) {
			cuts = new int[n];
			boolean any = extraction.anyHeight() || extraction.aboveGround();
			boolean above = extraction.aboveGround();
			for (int s = 0; s < n; s++) {
				int[] count = {0};
				for (int i = 0; i < steps[s]; i++) {
					bp.step(s, i, (x, y, z, state, tag) -> {
						// air below the natural ground is rock or earth taken out; a woodlot's air steps over it are felled trees
						int ground = terrain.groundY(x, z);
						if (state.isAir() && (above ? y >= ground : any || y < ground)) {
							count[0]++;
						}
					});
				}
				cuts[s] = count[0];
			}
		}
		if (over != null) {
			discount(bp, blueprints.blueprint(over, terrain), steps, work, goods);
		}
		return new PlotFacts(n, steps, work, goods, bp.beds(), jobs, storage, cuts, campaigns);
	}

	/**
	 * Job sites only for the trades the archetype lists (requests/w5-tour.md §2): a tenement's or townhouse's workshop
	 * bench, a farmhouse's hay, a builders' yard's mason's block are furnishing, not places the town hires for. Counted
	 * as jobs, every city tenement took on carpenters nobody had a carpentry for; the planner, which counts a trade's
	 * places by the trades an archetype lists, then asked for a carpentry for them, again and again (69 in a city of
	 * 441). Unknown archetypes keep what their blueprint marks.
	 */
	public static int[] listed(int[] jobs, @Nullable ArchetypeInfo info) {
		if (info == null) {
			return jobs;
		}
		int[] out = null;
		for (int j = 0; j < jobs.length; j++) {
			if (jobs[j] > 0 && !info.jobs().contains(JobType.values()[j])) {
				if (out == null) {
					out = jobs.clone();
				}
				out[j] = 0;
			}
		}
		return out == null ? jobs : out;
	}

	/** Scales each stage's labour and goods to the share of its steps that differ from what the old building put there. */
	private static void discount(Blueprint bp, Blueprint old, int[] steps, int[] work, int[][] goods) {
		it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<net.minecraft.world.level.block.state.BlockState> stood = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		for (int s = 0; s < old.stageCount(); s++) {
			for (int i = 0; i < old.stepCount(s); i++) {
				old.step(s, i, (x, y, z, state, tag) -> stood.put(net.minecraft.core.BlockPos.asLong(x, y, z), state));
			}
		}
		for (int s = 0; s < steps.length; s++) {
			int[] changed = {0};
			for (int i = 0; i < steps[s]; i++) {
				bp.step(s, i, (x, y, z, state, tag) -> {
					if (stood.get(net.minecraft.core.BlockPos.asLong(x, y, z)) != state) {
						changed[0]++;
					}
				});
			}
			double share = steps[s] == 0 ? 0 : changed[0] / (double) steps[s];
			work[s] = (int) Math.ceil(work[s] * share);
			for (int g = 0; g < goods[s].length; g++) {
				goods[s][g] = (int) Math.ceil(goods[s][g] * share);
			}
		}
	}

	public long complete() {
		return Cursor.pack(this.stageCount, 0);
	}

	public int totalSteps() {
		int t = 0;
		for (int s : this.stepCount) {
			t += s;
		}
		return t;
	}
}
