package dev.voidmc.hearth.economy.purse;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;

/**
 * Folk can die (docs/LIVING_WORLD.md: permanently, and murder is possible) or leave. What was in their purse goes to
 * their household — the grown-up members first, else the children — or, if they lived alone, to the town's treasury. On
 * {@code SettlementEvents.RESIDENT_REMOVED}, after the resident has left the settlement's records.
 */
public final class Inheritance {
	private Inheritance() {
	}

	/** @return who inherited (null: the treasury, or there was nothing to leave) */
	public static Resident settle(Settlement settlement, Resident gone) {
		long purse = Purses.get(gone);
		if (purse <= 0) {
			return null;
		}
		Purses.set(gone, 0);
		Resident heir = null;
		if (gone.home != Resident.NO_PLOT) {
			for (Resident r : settlement.residents()) {
				if (r.home == gone.home && !r.id.equals(gone.id) && (heir == null || heir.age == AgeStage.CHILD && r.age != AgeStage.CHILD)) {
					heir = r;
				}
			}
		}
		if (heir != null) {
			Purses.add(heir, purse);
		} else {
			settlement.deposit(purse);
		}
		settlement.markDirty();
		return heir;
	}
}
