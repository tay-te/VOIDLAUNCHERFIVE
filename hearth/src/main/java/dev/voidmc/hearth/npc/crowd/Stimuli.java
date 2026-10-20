package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.StandingService.Title;
import java.util.ArrayList;
import java.util.List;

/** From a player's state, the set of stimuli the crowd notices, most salient first (docs/LIVING_WORLD_2.md §5.3). Pure. */
public final class Stimuli {
	/** Hurt or hurting within this many ticks: a fight. */
	public static final int FIGHT_TICKS = 100;
	/** Leather clothes are 7 points; above this is heavy armour that folk remark on. */
	public static final int HEAVY_ARMOUR = 14;
	/** A weapon drawn this long brings a guard over. */
	public static final int DRAWN_TOO_LONG = 200;

	private Stimuli() {
	}

	/** One stimulus and how strongly it draws the crowd. */
	public record Sensed(Stimulus stimulus, int salience) {
	}

	public static List<Sensed> of(PlayerFacts f) {
		List<Sensed> out = new ArrayList<>(4);
		boolean outlaw = f.outlaw() || f.title() == Title.OUTLAW;
		if (outlaw) {
			out.add(new Sensed(Stimulus.OUTLAW, Stimulus.OUTLAW.salience()));
		}
		if (f.bodyNear()) {
			out.add(new Sensed(Stimulus.BODY, Stimulus.BODY.salience()));
		}
		if (f.ticksSinceCombat() <= FIGHT_TICKS) {
			out.add(new Sensed(Stimulus.FIGHT, Stimulus.FIGHT.salience() + Math.round(f.menace() * 5)));
		} else if (f.weaponDrawn()) {
			// a blade drawn for long, or drawn by someone fearsome, draws more notice (never more than a fight)
			int s = Stimulus.BLADE.salience() + Math.round(f.menace() * 6) + (f.drawnTicks() >= DRAWN_TOO_LONG ? 2 : 0);
			out.add(new Sensed(Stimulus.BLADE, Math.min(s, Stimulus.FIGHT.salience() - 1)));
		}
		if (!outlaw && f.title() == Title.HERO) {
			out.add(new Sensed(Stimulus.HERO, Stimulus.HERO.salience()));
		}
		if (f.health() < 0.5F || f.health() < 0.75F && f.ticksSinceCombat() <= 20 * 30) {
			out.add(new Sensed(Stimulus.BLOODIED, Stimulus.BLOODIED.salience()));
		}
		if (f.armour() > HEAVY_ARMOUR) {
			out.add(new Sensed(Stimulus.ARMOUR, Stimulus.ARMOUR.salience() + Math.min(4, f.armour() - HEAVY_ARMOUR)));
		}
		if (f.sneaking() || f.night()) {
			out.add(new Sensed(Stimulus.PROWL, Stimulus.PROWL.salience() + (f.sneaking() && f.night() ? 3 : 0)));
		}
		if (!outlaw && f.title() == Title.DISTRUSTED) {
			out.add(new Sensed(Stimulus.DISTRUSTED, Stimulus.DISTRUSTED.salience()));
		}
		if (!outlaw && (f.title() == Title.FRIEND || f.title() == Title.KNOWN || f.title() == Title.BURGHER)) {
			out.add(new Sensed(Stimulus.FRIEND, Stimulus.FRIEND.salience() + (f.title() == Title.KNOWN ? 0 : 2)));
		}
		out.sort((a, b) -> Integer.compare(b.salience(), a.salience()));
		return out;
	}

	/** The highest salience in a set (0 when there is nothing to notice). */
	public static int top(List<Sensed> sensed) {
		return sensed.isEmpty() ? 0 : sensed.get(0).salience();
	}

	/** Whether a stimulus is in the set. */
	public static boolean has(List<Sensed> sensed, Stimulus s) {
		for (Sensed x : sensed) {
			if (x.stimulus() == s) {
				return true;
			}
		}
		return false;
	}
}
