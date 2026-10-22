package dev.voidmc.fray.combat.action;

import dev.voidmc.fray.combat.math.Phases;
import dev.voidmc.fray.motion.Timeline;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * When a player action's hits land and its windows open (ANIM_EVENTS.md §1.1, §2), and which clip plays at what speed.
 *
 * <p>The clip is the source: when the action plays its own clip ({@link #owns}: the sword string, heavy, charged
 * release, plunge, and every dodge), its phases, ACTIVE strikes and the {@code chain}, {@code dodge_cancel},
 * {@code feint} and {@code hyper_armour} windows come from the baked timeline. An action that borrows another's clip
 * (a dagger's light plays the sword's, an Execute plays the heavy) keeps its moveset's phases and the clip is retimed so
 * its strike lands on the action's ACTIVE phase. Common code: the server runs it, the client predicts with the same
 * numbers.
 *
 * @param strikes         ACTIVE windows as {@code [start, endExclusive]}
 * @param chainFrom       the next attack (or Art) may start from this tick
 * @param dodgeCancelFrom a dodge may cut the action from this tick
 * @param guardCancelFrom a guard may cut it from this tick
 * @param feintTo         Guard feints the action up to this tick (-1: no feint)
 * @param hyperTo         hyper-armour up to this tick (-1: none)
 * @param clipSpeed       playback speed of {@code clip}
 * @param fromClip        true when the numbers came from the clip's timeline
 */
public record ActionTiming(Phases phases, int[][] strikes, int chainFrom, int dodgeCancelFrom, int guardCancelFrom, int feintTo, int hyperTo,
	@Nullable Identifier clip, float clipSpeed, boolean fromClip) {

	public int total() {
		return this.phases.total();
	}

	public boolean active(int k) {
		return this.strikeAt(k) >= 0;
	}

	public int strikeAt(int k) {
		for (int i = 0; i < this.strikes.length; i++) {
			if (k >= this.strikes[i][0] && k < this.strikes[i][1]) {
				return i;
			}
		}
		return -1;
	}

	public Phases.Phase at(int k) {
		if (k >= this.total()) {
			return Phases.Phase.DONE;
		}
		if (this.active(k)) {
			return Phases.Phase.ACTIVE;
		}
		return this.strikes.length > 0 && k < this.strikes[this.strikes.length - 1][1] ? Phases.Phase.WINDUP : this.phases.at(k);
	}

	public boolean chainOpen(int k) {
		return k >= this.chainFrom;
	}

	public boolean dodgeCancel(int k) {
		return k >= this.dodgeCancelFrom;
	}

	public boolean guardCancel(int k) {
		return k >= this.guardCancelFrom;
	}

	public boolean feint(int k) {
		return k <= this.feintTo;
	}

	public boolean hyperArmour(int k) {
		return k <= this.hyperTo;
	}

	/** Whether {@code kind} with a {@code family} weapon plays a clip made for it (so the clip's timing is the action's). */
	public static boolean owns(ActionKind kind, WeaponFamily family) {
		return switch (kind) {
			case DODGE, BACKSTEP -> true;
			case LIGHT, HEAVY, CHARGED, PLUNGE, RUNNING, RIPOSTE -> family == WeaponFamily.SWORD;
			default -> false;
		};
	}

	/**
	 * The timing of an action: {@code def} is the moveset's numbers, {@code clip} and {@code t} the clip it plays (either
	 * may be null: no clip in the jar), {@code speed} Mettle's action speed (1 = as authored; dodges never scale).
	 */
	public static ActionTiming of(ActionKind kind, WeaponFamily family, Phases def, @Nullable Identifier clip, @Nullable Timeline t, double speed) {
		boolean dodge = kind == ActionKind.DODGE || kind == ActionKind.BACKSTEP;
		if (t != null && owns(kind, family) && (dodge || t.hasPhases())) {
			return dodge ? dodge(def, clip, t) : fromClip(kind, clip, t, speed);
		}
		Phases p = dodge ? def : def.speed(speed);
		int end = p.windup() + p.active();
		float clipSpeed = 1;
		if (!dodge && t != null && t.hasPhases()) {
			clipSpeed = Math.max(0.5F, Math.min(4F, t.activeStart / (float) Math.max(1, p.windup())));
		} else if (!dodge && clip != null && clip.equals(ActionClips.SWING_ALIAS)) {
			clipSpeed = ActionKind.clipSpeed(kind, p.windup());
		}
		int[][] strikes = dodge || !kind.attack() ? new int[0][] : new int[][] {{p.windup(), end}};
		boolean feints = kind == ActionKind.HEAVY || kind == ActionKind.CHARGED;
		return new ActionTiming(p, strikes, dodge ? p.total() - 3 : end, end, p.total() - 3, feints ? p.windup() - 3 : -1,
			kind.hyperArmour() ? end - 1 : -1, clip, clipSpeed, false);
	}

	private static ActionTiming fromClip(ActionKind kind, @Nullable Identifier clip, Timeline t, double speed) {
		double f = speed > 0 ? speed : 1;
		int[][] strikes = new int[t.strikes.length][];
		for (int i = 0; i < strikes.length; i++) {
			strikes[i] = new int[] {scale(t.strikes[i][0], f), Math.max(scale(t.strikes[i][0], f) + 1, scale(t.strikes[i][1], f))};
		}
		int total = Math.max(strikes[strikes.length - 1][1] + 1, scale(t.ticks, f));
		int w = strikes[0][0];
		int a = strikes[0][1] - w;
		Phases p = new Phases(w, a, total - w - a);
		int last = strikes[strikes.length - 1][1];
		int[] chain = t.window("chain");
		int[] dodge = t.window("dodge_cancel");
		int[] feint = t.window("feint");
		int[] hyper = t.window("hyper_armour");
		return new ActionTiming(p, strikes, chain != null ? scale(chain[0], f) : last, dodge != null ? scale(dodge[0], f) : last, total - 3,
			feint != null ? scale(feint[1], f) : -1, hyper != null ? scale(hyper[1], f) : kind.hyperArmour() ? last - 1 : -1, clip, (float) f, true);
	}

	private static ActionTiming dodge(Phases def, @Nullable Identifier clip, Timeline t) {
		int total = Math.max(def.windup() + def.active() + 1, t.ticks);
		Phases p = new Phases(def.windup(), def.active(), total - def.windup() - def.active());
		int[] roll = t.window("roll_attack");
		int[] recovery = t.window("recovery");
		int chain = roll != null ? roll[0] : total - 3;
		return new ActionTiming(p, new int[0][], chain, recovery != null ? recovery[0] : total, total - 2, -1, -1, clip, 1, true);
	}

	private static int scale(int tick, double f) {
		return f == 1 ? tick : (int) Math.round(tick / f);
	}
}
