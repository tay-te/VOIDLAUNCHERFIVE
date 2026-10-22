package dev.voidmc.fray.combat.action;

import java.util.List;

/**
 * A weapon family's moves (COMBAT.md §4, §5): the light string (the last is the finisher), the heavy, the charge
 * (extra ticks to full and the multiplier at full), and the family's poise factor.
 */
public record Moveset(WeaponFamily family, List<ActionDef> lights, ActionDef heavy, int chargeTicks, float chargeMax, float chargedStaminaMax, float poiseFactor) {
	public ActionDef light(int combo) {
		return this.lights.get(Math.floorMod(combo, this.lights.size()));
	}

	public String canonical() {
		StringBuilder s = new StringBuilder(this.family.name()).append(':');
		for (ActionDef d : this.lights) {
			s.append(d.canonical()).append(';');
		}
		return s.append(" H ").append(this.heavy.canonical()).append(" C+").append(this.chargeTicks).append(" x").append(this.chargeMax).append(" st")
			.append(this.chargedStaminaMax).append(" p").append(this.poiseFactor).toString();
	}
}
