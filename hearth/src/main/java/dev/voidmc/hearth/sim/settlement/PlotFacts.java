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

/**
 * What the clock needs to know about a plot's blueprint, read once and kept with the plot (blueprints are cached by the
 * build module, but the clock asks for these numbers every step and must not pay for a cache lookup each time).
 *
 * @param stepCount steps per stage
 * @param work      labour per stage
 * @param goods     goods per stage, {@code [stage][goods.ordinal()]}
 * @param jobs      job sites by {@link JobType#ordinal()}
 */
public record PlotFacts(int stageCount, int[] stepCount, int[] work, int[][] goods, int beds, int[] jobs, boolean storage) {
	public static PlotFacts of(PlotSpec spec, BlueprintService blueprints, TerrainProbe terrain) {
		Blueprint bp = blueprints.blueprint(spec, terrain);
		int n = bp.stageCount();
		int[] steps = new int[n];
		int[] work = new int[n];
		int[][] goods = new int[n][];
		for (int s = 0; s < n; s++) {
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
		boolean storage = info != null && info.category() == ArchetypeCategory.STORAGE;
		return new PlotFacts(n, steps, work, goods, bp.beds(), jobs, storage);
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
