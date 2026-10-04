package dev.voidmc.hearth.law.crime;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.justice.Offences;
import dev.voidmc.hearth.law.justice.Warrant;
import dev.voidmc.hearth.law.state.LawState;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * The law's day (on {@code DAY_PASSED}, once per settlement per sim day, including days caught up for towns nobody was
 * near — so everything here is cheap arithmetic on the settlement's own law state):
 * <ul>
 *   <li>reports whose witnesses never reached a guard are made anyway;</li>
 *   <li>crimes nobody saw may be discovered (a missing purse, a forced lock, a body) and cast suspicion on whoever was
 *       in town that day;</li>
 *   <li>suspicion fades; unpaid murder makes an outlaw; bounties lapse if the settings say so;</li>
 *   <li>word of warrants reaches the neighbouring towns, which hold the player to a share of the bounty;</li>
 *   <li>picked locks are locked again and forced ones mended.</li>
 * </ul>
 */
public final class LawDays {
	/** Suspicion lost per day. */
	static final int SUSPICION_DECAY = 10;
	/** Days an unsolved crime may still be discovered. */
	static final long DISCOVERY_DAYS = 3;
	/** Days a forced lock stays broken before it is mended. */
	static final long MEND_DAYS = 2;

	private LawDays() {
	}

	public static void dayPassed(HearthWorld world, Settlement s, long day) {
		LawState state = Law.state(s);
		if (state.isEmpty()) {
			return;
		}
		ServerLevel level = world.level();
		LawConfig cfg = Law.config();
		boolean dirty = false;

		// reports nobody carried to a guard in time
		long now = level.getGameTime();
		for (LawState.PendingReport p : List.copyOf(state.pending())) {
			if (p.deadline() <= now && state.pending().contains(p)) {
				if (s.resident(p.witness()) != null) {
					CrimeDesk.file(level, s, p);
				} else {
					state.pending().removeIf(x -> x.id() == p.id() && x.witness().equals(p.witness()));
				}
				dirty = true;
			}
		}

		// discoveries
		Random random = new Random(s.id() * 31 + day);
		for (Iterator<LawState.Unsolved> it = state.unsolved().iterator(); it.hasNext(); ) {
			LawState.Unsolved u = it.next();
			Crime c = u.crime();
			if (random.nextDouble() < discoveryChance(c.offence())) {
				it.remove();
				dirty = true;
				if (state.seenDay(c.offender()) >= u.day()) {
					state.addSuspicion(c.offender(), suspicion(c));
					CrimeDesk.deed(s, c.offender(), "law:suspected", Math.max(-10, Offences.standingWeight(c) / 4), c.victim(), c.pos(), false);
				}
			} else if (day - u.day() >= DISCOVERY_DAYS) {
				it.remove();
				dirty = true;
			}
		}

		// suspicion fades
		for (UUID p : List.copyOf(state.suspicionMap().keySet())) {
			state.addSuspicion(p, -SUSPICION_DECAY);
			dirty = true;
		}

		for (Warrant w : List.copyOf(state.warrants().values())) {
			if (!w.active()) {
				continue;
			}
			if (w.murder() && !w.outlaw() && cfg.justice().outlaws() && day - w.lastDay() >= cfg.justice().outlawAfterDays()) {
				w.setOutlaw(true);
				w.setSpread(false);
				dirty = true;
			}
			if (cfg.justice().bountyExpiryDays() > 0 && day - w.lastDay() >= cfg.justice().bountyExpiryDays()) {
				w.clear();
				dirty = true;
				continue;
			}
			if (!w.spread() && w.origin() == 0 && day - w.lastDay() >= cfg.justice().spreadDays()) {
				spread(world, s, w, day);
				w.setSpread(true);
				dirty = true;
			}
		}

		// locks: picked ones re-locked, forced ones mended after a while
		if (!state.locks().isEmpty()) {
			state.locks().values().removeIf(l -> l.broken() ? false : l.until() <= now);
			for (Iterator<Map.Entry<Long, LawState.LockOverride>> it = state.locks().entrySet().iterator(); it.hasNext(); ) {
				LawState.LockOverride l = it.next().getValue();
				if (l.broken() && random.nextDouble() < 1.0 / MEND_DAYS) {
					it.remove();
				}
			}
			dirty = true;
		}
		state.prune();
		if (dirty) {
			s.markDirty();
		}
	}

	/** Word of a warrant reaches the towns within the spread radius: each holds the player to a share of the bounty. */
	static void spread(HearthWorld world, Settlement from, Warrant w, long day) {
		LawConfig.Justice cfg = Law.config().justice();
		if (cfg.spreadRadius() <= 0 || cfg.spreadFraction() <= 0 && !w.outlaw()) {
			return;
		}
		long r2 = (long) cfg.spreadRadius() * cfg.spreadRadius();
		List<Settlement> neighbours = new ArrayList<>();
		for (Settlement other : world.settlements()) {
			if (other.id() != from.id() && other.center().distSqr(from.center()) <= r2) {
				neighbours.add(other);
			}
		}
		for (Settlement n : neighbours) {
			LawState ns = Law.state(n);
			Warrant there = ns.warrantFor(w.player, day);
			long share = Math.round(w.bounty() * cfg.spreadFraction());
			there.absorb(w, share, day);
			there.setOrigin(from.id());
			there.setSpread(true);
			n.markDirty();
		}
	}

	/** The chance per day that a crime nobody saw is discovered. */
	static double discoveryChance(Offence offence) {
		return switch (offence) {
			case MURDER, JAILBREAK -> 1.0;
			case VANDALISM -> 0.9;
			case THEFT, ROBBERY -> 0.6;
			case PICKPOCKETING -> 0.5;
			case LOCKPICKING -> 0.4;
			case POACHING -> 0.3;
			case TRESPASS, ASSAULT, RESISTING_ARREST -> 0.0;
		};
	}

	/** Suspicion a discovered crime casts on someone who was in town that day. */
	static int suspicion(Crime c) {
		return Math.min(80, Offences.severity(c.offence()) * 8 + (int) Math.min(20, c.value() / 10));
	}

	/** Files the reports whose deadline passed, for towns with players near (once a second). */
	public static void deadlines(ServerLevel level, Settlement s) {
		LawState state = Law.state(s);
		if (state.pending().isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (LawState.PendingReport p : List.copyOf(state.pending())) {
			if (p.deadline() <= now && state.pending().contains(p)) {
				if (s.resident(p.witness()) != null) {
					CrimeDesk.file(level, s, p);
				} else {
					state.pending().removeIf(x -> x.id() == p.id() && x.witness().equals(p.witness()));
					s.markDirty();
				}
			}
		}
	}
}
