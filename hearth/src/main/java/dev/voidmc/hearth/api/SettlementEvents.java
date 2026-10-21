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
	public static final Event<CaravanEvent> CARAVAN = EventFactory.createArrayBacked(CaravanEvent.class, ls -> (w, from, to, k, id, cargo) -> {
		for (CaravanEvent l : ls) {
			l.onCaravan(w, from, to, k, id, cargo);
		}
	});

	/** Blocks of a built plot were damaged (by a player, an explosion, fire). Fired by the repair module, batched. */
	public static final Event<Damaged> DAMAGED = EventFactory.createArrayBacked(Damaged.class, ls -> (w, s, p, cause, n, player) -> {
		for (Damaged l : ls) {
			l.onDamaged(w, s, p, cause, n, player);
		}
	});

	/** A fire broke out in a settlement (fired by the repair module once per fire, when it first takes hold of a plot). */
	public static final Event<FireStarted> FIRE_STARTED = EventFactory.createArrayBacked(FireStarted.class, ls -> (w, s, at) -> {
		for (FireStarted l : ls) {
			l.onFireStarted(w, s, at);
		}
	});

	/** A fire is out: {@code burned} blocks lost, {@code helpers} the players who fought it. */
	public static final Event<FireOut> FIRE_OUT = EventFactory.createArrayBacked(FireOut.class, ls -> (w, s, burned, helpers) -> {
		for (FireOut l : ls) {
			l.onFireOut(w, s, burned, helpers);
		}
	});

	/** A damaged plot is whole again ({@code blocks} restored, abstract or embodied). */
	public static final Event<Repaired> REPAIRED = EventFactory.createArrayBacked(Repaired.class, ls -> (w, s, plot, blocks) -> {
		for (Repaired l : ls) {
			l.onRepaired(w, s, plot, blocks);
		}
	});

	/** Repairs cannot go on for want of goods (once per stall, like CONSTRUCTION_STALLED), so quests can ask for them. */
	public static final Event<RepairStalled> REPAIR_STALLED = EventFactory.createArrayBacked(RepairStalled.class, ls -> (w, s, g, n) -> {
		for (RepairStalled l : ls) {
			l.onRepairStalled(w, s, g, n);
		}
	});

	/**
	 * An escort started with {@link NpcService#follow} ended: the folk reached the destination with the player
	 * ({@code arrived}) or was lost (died, left behind, the player gone).
	 */
	public static final Event<EscortEnded> ESCORT_ENDED = EventFactory.createArrayBacked(EscortEnded.class, ls -> (w, s, r, p, arrived) -> {
		for (EscortEnded l : ls) {
			l.onEscortEnded(w, s, r, p, arrived);
		}
	});

	// ---- living world, part 2 (docs/LIVING_WORLD_2.md §11.4)

	/** A journey on the roads set out, arrived, was waylaid or was lost ({@code from}/{@code to}: settlement ids, 0 for the wild). */
	public static final Event<Traveller> TRAVELLER = EventFactory.createArrayBacked(Traveller.class, ls -> (w, k, id, from, to, phase) -> {
		for (Traveller l : ls) {
			l.onTraveller(w, k, id, from, to, phase);
		}
	});

	/** A town event (a wedding, a fair, a trial, a raid; §3) was scheduled, began or ended; {@code outcome} "" until it ends. */
	public static final Event<TownEvent> TOWN_EVENT = EventFactory.createArrayBacked(TownEvent.class, ls -> (w, s, kind, phase, outcome) -> {
		for (TownEvent l : ls) {
			l.onTownEvent(w, s, kind, phase, outcome);
		}
	});

	/** Something happened in a resident's life (§4): {@code kind} as {@link LivesService#lifeEvent}. */
	public static final Event<LifeEvent> LIFE_EVENT = EventFactory.createArrayBacked(LifeEvent.class, ls -> (w, s, r, kind, player) -> {
		for (LifeEvent l : ls) {
			l.onLifeEvent(w, s, r, kind, player);
		}
	});

	/** A resident was hired as a player's companion, dismissed, left or died (§8). */
	public static final Event<Companion> COMPANION = EventFactory.createArrayBacked(Companion.class, ls -> (w, s, r, player, phase) -> {
		for (Companion l : ls) {
			l.onCompanion(w, s, r, player, phase);
		}
	});

	private SettlementEvents() {
	}

	@FunctionalInterface
	public interface Traveller {
		void onTraveller(HearthWorld world, TravelService.TravelKind kind, long id, long from, long to, Phase phase);

		enum Phase {
			DEPARTED, ARRIVED, WAYLAID, LOST
		}
	}

	@FunctionalInterface
	public interface TownEvent {
		void onTownEvent(HearthWorld world, Settlement settlement, String kind, Phase phase, String outcome);

		enum Phase {
			SCHEDULED, BEGUN, ENDED
		}
	}

	@FunctionalInterface
	public interface LifeEvent {
		void onLifeEvent(HearthWorld world, Settlement settlement, java.util.UUID resident, String kind, java.util.@org.jspecify.annotations.Nullable UUID player);
	}

	@FunctionalInterface
	public interface Companion {
		void onCompanion(HearthWorld world, Settlement settlement, java.util.UUID resident, java.util.UUID player, Phase phase);

		enum Phase {
			HIRED, DISMISSED, LEFT, DIED
		}
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

	/** {@code cargo} by {@link Goods#ordinal()}; {@code caravanId} as in {@link HearthWorld#caravans()}. */
	@FunctionalInterface
	public interface CaravanEvent {
		void onCaravan(HearthWorld world, Settlement from, Settlement to, Kind kind, long caravanId, int[] cargo);

		enum Kind {
			DEPARTED, ARRIVED, ROBBED, LOST
		}
	}

	/** {@code player}: who did it, when {@code cause} is PLAYER (their breaks, their TNT, their fire), else null. */
	@FunctionalInterface
	public interface Damaged {
		void onDamaged(HearthWorld world, Settlement settlement, PlotView plot, Cause cause, int blocks, java.util.@org.jspecify.annotations.Nullable UUID player);

		enum Cause {
			PLAYER, EXPLOSION, FIRE, OTHER
		}
	}

	@FunctionalInterface
	public interface FireStarted {
		void onFireStarted(HearthWorld world, Settlement settlement, net.minecraft.core.BlockPos at);
	}

	@FunctionalInterface
	public interface FireOut {
		void onFireOut(HearthWorld world, Settlement settlement, int burned, java.util.Set<java.util.UUID> helpers);
	}

	@FunctionalInterface
	public interface Repaired {
		void onRepaired(HearthWorld world, Settlement settlement, int plotId, int blocks);
	}

	@FunctionalInterface
	public interface RepairStalled {
		void onRepairStalled(HearthWorld world, Settlement settlement, Goods goods, int blocks);
	}

	@FunctionalInterface
	public interface EscortEnded {
		void onEscortEnded(HearthWorld world, Settlement settlement, java.util.UUID resident, java.util.UUID player, boolean arrived);
	}
}
