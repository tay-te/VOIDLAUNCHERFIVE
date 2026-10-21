package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;

/**
 * A charcoal clamp's burn (docs/LIVING_WORLD_2.md §6.1–6.2): a day building the stack of billets round its pole, then
 * covered with earth and turf and smouldering for the middle days (a column of smoke seen from the road, the burner
 * tending the vents), and a last day raking it open into charcoal. A pure function of the town's books — a burner
 * working the kiln and wood in store to burn — and of the day, so every look at the clamp agrees.
 */
public final class ClampCycle {
	public enum Stage {
		/** Nobody burns (no burner, nothing to burn): a cold clamp. */
		COLD,
		/** The stack of billets going up round the pole. */
		BUILDING,
		/** Covered and smouldering: smoke from the crown. */
		BURNING,
		/** Opened and raked out into charcoal. */
		RAKING
	}

	private ClampCycle() {
	}

	/** The stage on {@code day} of a kiln plot's clamp, with {@code cycleDays} to a burn (at least 3). */
	public static Stage stage(boolean active, long day, int plot, int cycleDays) {
		if (!active) {
			return Stage.COLD;
		}
		int n = Math.max(3, cycleDays);
		// each kiln on its own day of the cycle, so a town with two does not light both at once
		int d = (int) Math.floorMod(day + plot * 7L, n);
		if (d == 0) {
			return Stage.BUILDING;
		}
		return d == n - 1 ? Stage.RAKING : Stage.BURNING;
	}

	/** Whether a kiln is burning at all: an adult burner works it and the town has wood or logs in store. */
	public static boolean active(Settlement s, int plot) {
		boolean burner = false;
		for (Resident r : s.residents()) {
			if (r.work == plot && r.job == JobType.CHARCOAL_BURNER && r.age == AgeStage.ADULT && !s.isResidentAway(r.id)) {
				burner = true;
				break;
			}
		}
		return burner && s.stockpile().get(Goods.LOGS) + s.stockpile().get(Goods.WOOD) > 0;
	}
}
