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

	// ---- phase 3 (docs/LIVING_WORLD.md)

	/** The law module saw or confirmed a crime in a settlement (fired once per crime, after witnesses had their say). */
	public static final Event<CrimeCommitted> CRIME_COMMITTED = EventFactory.createArrayBacked(CrimeCommitted.class, ls -> (w, s, c) -> {
		for (CrimeCommitted l : ls) {
			l.onCrime(w, s, c);
		}
	});

	public static final Event<StandingChanged> STANDING_CHANGED = EventFactory.createArrayBacked(StandingChanged.class,
		ls -> (w, s, p, from, to) -> {
			for (StandingChanged l : ls) {
				l.onStandingChanged(w, s, p, from, to);
			}
		});

	public static final Event<SiteChanged> SITE_DEPLETED = EventFactory.createArrayBacked(SiteChanged.class, ls -> (w, s, site) -> {
		for (SiteChanged l : ls) {
			l.onSite(w, s, site);
		}
	});

	public static final Event<SiteChanged> SITE_FOUND = EventFactory.createArrayBacked(SiteChanged.class, ls -> (w, s, site) -> {
		for (SiteChanged l : ls) {
			l.onSite(w, s, site);
		}
	});

	/** A settlement's fortunes turned: rising (new trade, rich sites) or declining (sites spent, folk leaving). */
	public static final Event<ProsperityChanged> PROSPERITY_CHANGED = EventFactory.createArrayBacked(ProsperityChanged.class,
		ls -> (w, s, rising, why) -> {
			for (ProsperityChanged l : ls) {
				l.onProsperityChanged(w, s, rising, why);
			}
		});

	/** A caravan set out, arrived, or was lost (robbed, killed) between two settlements. */
	public static final Event<CaravanEvent> CARAVAN = EventFactory.createArrayBacked(CaravanEvent.class, ls -> (w, from, to, k, cargo) -> {
		for (CaravanEvent l : ls) {
			l.onCaravan(w, from, to, k, cargo);
		}
	});

	/** Blocks of a built plot were damaged (by a player, an explosion, fire). Fired by the repair module, batched. */
	public static final Event<Damaged> DAMAGED = EventFactory.createArrayBacked(Damaged.class, ls -> (w, s, p, cause, n) -> {
		for (Damaged l : ls) {
			l.onDamaged(w, s, p, cause, n);
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

	@FunctionalInterface
	public interface CrimeCommitted {
		void onCrime(HearthWorld world, Settlement settlement, LawService.Crime crime);
	}

	@FunctionalInterface
	public interface StandingChanged {
		void onStandingChanged(HearthWorld world, Settlement settlement, java.util.UUID player, StandingService.Title from,
			StandingService.Title to);
	}

	@FunctionalInterface
	public interface SiteChanged {
		void onSite(HearthWorld world, Settlement settlement, ResourceSite site);
	}

	@FunctionalInterface
	public interface ProsperityChanged {
		void onProsperityChanged(HearthWorld world, Settlement settlement, boolean rising, String why);
	}

	/** {@code cargo} by {@link Goods#ordinal()}. */
	@FunctionalInterface
	public interface CaravanEvent {
		void onCaravan(HearthWorld world, Settlement from, Settlement to, Kind kind, int[] cargo);

		enum Kind {
			DEPARTED, ARRIVED, ROBBED, LOST
		}
	}

	@FunctionalInterface
	public interface Damaged {
		void onDamaged(HearthWorld world, Settlement settlement, PlotView plot, Cause cause, int blocks);

		enum Cause {
			PLAYER, EXPLOSION, FIRE, OTHER
		}
	}
}
