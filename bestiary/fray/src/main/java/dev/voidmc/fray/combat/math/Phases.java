package dev.voidmc.fray.combat.math;

/**
 * An action's three phases in ticks (COMBAT.md §3): WINDUP [0, w), ACTIVE [w, w + a), RECOVERY [w + a, total).
 */
public record Phases(int windup, int active, int recovery) {
	public enum Phase {
		WINDUP, ACTIVE, RECOVERY, DONE
	}

	public int total() {
		return this.windup + this.active + this.recovery;
	}

	public Phase at(int tick) {
		if (tick < this.windup) {
			return Phase.WINDUP;
		}
		if (tick < this.windup + this.active) {
			return Phase.ACTIVE;
		}
		if (tick < this.total()) {
			return Phase.RECOVERY;
		}
		return Phase.DONE;
	}

	public boolean active(int tick) {
		return this.at(tick) == Phase.ACTIVE;
	}

	/** The chain window: from the end of ACTIVE to the end of RECOVERY. */
	public boolean chainOpen(int tick) {
		return tick >= this.windup + this.active;
	}

	/** Dodge may cut the recovery. */
	public boolean dodgeCancel(int tick) {
		return tick >= this.windup + this.active;
	}

	/** Guard may cut the last 3 ticks of recovery. */
	public boolean guardCancel(int tick) {
		return tick >= this.total() - 3;
	}

	/** A heavy's feint window: Guard during windup ticks 0 to windup - 3. */
	public boolean feint(int tick) {
		return tick <= this.windup - 3;
	}

	/** The same phases with recovery (and windup) scaled by an action-speed factor (Fencer: 1.1 = 10 % faster). */
	public Phases speed(double factor) {
		if (factor == 1 || factor <= 0) {
			return this;
		}
		return new Phases(Math.max(1, (int) Math.round(this.windup / factor)), this.active, Math.max(1, (int) Math.round(this.recovery / factor)));
	}
}
