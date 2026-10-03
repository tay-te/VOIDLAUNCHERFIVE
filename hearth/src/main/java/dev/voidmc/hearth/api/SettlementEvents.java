package dev.voidmc.hearth.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * What happens to settlements, for modules that react to it (quests from stalled construction, NPC reactions to a new
 * building, toasts). Fired by the sim module on the server thread, including during abstract simulation of settlements
 * nobody is near, so listeners must be cheap and must not load chunks.
 */
public final class SettlementEvents {
	public static final Event<Founded> FOUNDED = EventFactory.createArrayBacked(Founded.class, ls -> (w, s) -> {
		for (Founded l : ls) {
			l.onFounded(w, s);
		}
	});

	public static final Event<BuildingCompleted> BUILDING_COMPLETED = EventFactory.createArrayBacked(BuildingCompleted.class, ls -> (w, s, p) -> {
		for (BuildingCompleted l : ls) {
			l.onBuildingCompleted(w, s, p);
		}
	});

	public static final Event<ConstructionStalled> CONSTRUCTION_STALLED = EventFactory.createArrayBacked(ConstructionStalled.class,
		ls -> (w, s, p, g, n) -> {
			for (ConstructionStalled l : ls) {
				l.onStalled(w, s, p, g, n);
			}
		});

	public static final Event<PlotAbandoned> PLOT_ABANDONED = EventFactory.createArrayBacked(PlotAbandoned.class, ls -> (w, s, p, why) -> {
		for (PlotAbandoned l : ls) {
			l.onPlotAbandoned(w, s, p, why);
		}
	});

	public static final Event<TierChanged> TIER_CHANGED = EventFactory.createArrayBacked(TierChanged.class, ls -> (w, s, from, to) -> {
		for (TierChanged l : ls) {
			l.onTierChanged(w, s, from, to);
		}
	});

	public static final Event<ResidentChanged> RESIDENT_ADDED = EventFactory.createArrayBacked(ResidentChanged.class, ls -> (w, s, r) -> {
		for (ResidentChanged l : ls) {
			l.onResident(w, s, r);
		}
	});

	public static final Event<ResidentChanged> RESIDENT_REMOVED = EventFactory.createArrayBacked(ResidentChanged.class, ls -> (w, s, r) -> {
		for (ResidentChanged l : ls) {
			l.onResident(w, s, r);
		}
	});

	public static final Event<DayPassed> DAY_PASSED = EventFactory.createArrayBacked(DayPassed.class, ls -> (w, s, day) -> {
		for (DayPassed l : ls) {
			l.onDayPassed(w, s, day);
		}
	});

	public static final Event<EmbodimentChanged> EMBODIMENT_CHANGED = EventFactory.createArrayBacked(EmbodimentChanged.class, ls -> (w, s, e) -> {
		for (EmbodimentChanged l : ls) {
			l.onEmbodimentChanged(w, s, e);
		}
	});

	private SettlementEvents() {
	}

	@FunctionalInterface
	public interface Founded {
		void onFounded(HearthWorld world, Settlement settlement);
	}

	@FunctionalInterface
	public interface BuildingCompleted {
		void onBuildingCompleted(HearthWorld world, Settlement settlement, PlotView plot);
	}

	/** Construction of a plot cannot go on: the stockpile is short of {@code missing} units of {@code goods}. */
	@FunctionalInterface
	public interface ConstructionStalled {
		void onStalled(HearthWorld world, Settlement settlement, PlotView plot, Goods goods, int missing);
	}

	/** A plot was given up: a player built over it, or its rebuild replaced it. {@code why} is short and readable. */
	@FunctionalInterface
	public interface PlotAbandoned {
		void onPlotAbandoned(HearthWorld world, Settlement settlement, PlotView plot, String why);
	}

	@FunctionalInterface
	public interface TierChanged {
		void onTierChanged(HearthWorld world, Settlement settlement, SettlementTier from, SettlementTier to);
	}

	@FunctionalInterface
	public interface ResidentChanged {
		void onResident(HearthWorld world, Settlement settlement, Resident resident);
	}

	/** Once per sim day per settlement (in catch-up, once per day caught up, so keep it cheap). */
	@FunctionalInterface
	public interface DayPassed {
		void onDayPassed(HearthWorld world, Settlement settlement, long day);
	}

	@FunctionalInterface
	public interface EmbodimentChanged {
		void onEmbodimentChanged(HearthWorld world, Settlement settlement, boolean embodied);
	}
}
