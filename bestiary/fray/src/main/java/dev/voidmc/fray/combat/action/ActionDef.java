package dev.voidmc.fray.combat.action;

import dev.voidmc.fray.combat.math.Phases;

/**
 * One move: its phases, damage multiplier over the weapon, stamina cost, reach and arc (COMBAT.md §4).
 *
 * @param multiplier damage × the weapon's base (heavy 1.6, finisher 1.3 ...)
 * @param arc        degrees, centred on the look (360 for a spin)
 * @param slow       movement multiplier during windup and active (light 0.5, heavy 0.2)
 * @param step       blocks of client root motion over ACTIVE (light 0.3, heavy 0.6, running 1.5)
 */
public record ActionDef(Phases phases, float multiplier, float stamina, float reach, float arc, float slow, float step) {
	public ActionDef(int w, int a, int r, float multiplier, float stamina, float reach, float arc, float slow, float step) {
		this(new Phases(w, a, r), multiplier, stamina, reach, arc, slow, step);
	}

	public String canonical() {
		return this.phases.windup() + "/" + this.phases.active() + "/" + this.phases.recovery() + " x" + this.multiplier + " st" + this.stamina + " r" + this.reach
			+ " a" + this.arc + " s" + this.slow + " m" + this.step;
	}
}
