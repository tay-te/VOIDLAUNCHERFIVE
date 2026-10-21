package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The town's places of work and homes as the lives need them: each trade's workplaces with their job slots, and each
 * home's nearest neighbour. Built once a day from the settlement (an interface so the tests can hand in a made-up town).
 */
public interface Workplaces {
	/**
	 * The trades taught from master to apprentice: the workshop trades and the builders (the land's trades are learnt by
	 * doing). Builders work from home where the town has no yard: their master is the senior builder of the town.
	 */
	java.util.Set<JobType> CRAFTS = java.util.EnumSet.of(JobType.BUILDER, JobType.CARPENTER, JobType.MASON, JobType.SMITH, JobType.BAKER, JobType.BREWER, JobType.TAILOR,
		JobType.TANNER, JobType.BUTCHER, JobType.CHANDLER, JobType.GLASSMAKER, JobType.SMELTER, JobType.KILN_WORKER, JobType.PAPERMAKER, JobType.DYER,
		JobType.BOATWRIGHT, JobType.HEALER, JobType.MINTER, JobType.MERCHANT, JobType.SALTER, JobType.BEEKEEPER);

	/** A workplace of a trade: the plot and how many may work the trade there. */
	record Place(int plot, int slots) {
	}

	/** The trade's places that employ now. */
	List<Place> places(JobType trade);

	/** The home plot nearest to this one (within 32 blocks), or {@link dev.voidmc.hearth.api.Resident#NO_PLOT}. */
	int neighbour(int homePlot);

	/** The live view of a sim settlement. */
	static Workplaces of(Settlement settlement) {
		if (settlement instanceof SimSettlement s) {
			return new Live(s, s.data(Lives.TOWN));
		}
		return new Workplaces() {
			@Override
			public List<Place> places(JobType trade) {
				return List.of();
			}

			@Override
			public int neighbour(int homePlot) {
				return dev.voidmc.hearth.api.Resident.NO_PLOT;
			}
		};
	}

	final class Live implements Workplaces {
		private final SimSettlement s;
		private final TownLives town;
		private final Map<JobType, List<Place>> places = new EnumMap<>(JobType.class);

		Live(SimSettlement s, TownLives town) {
			this.s = s;
			this.town = town;
		}

		@Override
		public List<Place> places(JobType trade) {
			return this.places.computeIfAbsent(trade, t -> {
				List<Place> out = new ArrayList<>(2);
				for (SimPlot p : this.s.plots()) {
					int n = p.jobSlots(t);
					if (n > 0 && p.employs()) {
						out.add(new Place(p.id(), n));
					}
				}
				return out;
			});
		}

		@Override
		public int neighbour(int homePlot) {
			// kept a week on the town (or until its plots change): houses are not built every day, and pairing them is dear
			TownLives.Neighbours n = this.town.neighbours;
			long today = this.town.lastDay;
			if (n == null || n.plots() != this.s.plots().size() || today - n.day() >= 7 || today < n.day()) {
				n = new TownLives.Neighbours(today, this.s.plots().size(), neighbours(this.s));
				this.town.neighbours = n;
			}
			return n.map().getOrDefault(homePlot, dev.voidmc.hearth.api.Resident.NO_PLOT);
		}

		private static it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap neighbours(SimSettlement s) {
			List<PlotView> homes = new ArrayList<>();
			for (SimPlot p : s.plots()) {
				if (p.isStanding() && p.beds() > 0) {
					homes.add(p);
				}
			}
			it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap out = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(homes.size());
			int n = homes.size();
			int[] x = new int[n];
			int[] z = new int[n];
			for (int i = 0; i < n; i++) {
				PlotSpec spec = homes.get(i).spec();
				x[i] = spec.origin().getX() + spec.sizeX() / 2;
				z[i] = spec.origin().getZ() + spec.sizeZ() / 2;
			}
			for (int i = 0; i < n; i++) {
				int best = -1;
				long bd = 32L * 32L;
				for (int j = 0; j < n; j++) {
					if (i != j) {
						long dx = x[i] - x[j];
						long dz = z[i] - z[j];
						long d = dx * dx + dz * dz;
						if (d < bd) {
							bd = d;
							best = j;
						}
					}
				}
				if (best >= 0) {
					out.put(homes.get(i).id(), homes.get(best).id());
				}
			}
			return out;
		}
	}
}
