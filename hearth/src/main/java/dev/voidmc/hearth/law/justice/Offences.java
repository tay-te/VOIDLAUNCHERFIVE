package dev.voidmc.hearth.law.justice;

import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.law.LawConfig;

/** What each offence weighs: severity, fine, the standing it costs, the sentence it earns. Pure. */
public final class Offences {
	private Offences() {
	}

	/** How grave an offence is, for "the worst thing you did" and for who may take the pillory instead of the cell. */
	public static int severity(Offence offence) {
		return switch (offence) {
			case TRESPASS -> 1;
			case LOCKPICKING, VANDALISM, POACHING -> 2;
			case THEFT, PICKPOCKETING -> 3;
			case ASSAULT, RESISTING_ARREST -> 4;
			case JAILBREAK -> 5;
			case ROBBERY -> 6;
			case MURDER -> 10;
		};
	}

	/**
	 * Whether a player's blow on a resident is brawling (a fight with fists the crowds module runs, fined on the spot by the
	 * guard who parts it) rather than assault (docs/LIVING_WORLD_2.md §5.1): then no assault is filed for it.
	 */
	public static boolean brawling(java.util.UUID player, java.util.UUID resident) {
		dev.voidmc.hearth.api.CrowdService crowds = dev.voidmc.hearth.api.HearthServices.crowds();
		return crowds != null && crowds.brawling(player, resident);
	}

	/** Whether the value of what was taken adds to the fine. */
	public static boolean valued(Offence offence) {
		return offence == Offence.THEFT || offence == Offence.PICKPOCKETING || offence == Offence.ROBBERY || offence == Offence.POACHING;
	}

	/** The fine for one crime, in bits (at least 1). */
	public static long fine(Crime crime, LawConfig.Fines fines) {
		double raw;
		if (crime.offence() == Offence.VANDALISM) {
			// the detector already priced the damage (blocks × the per-block fine); a bare report counts as five blocks
			raw = crime.value() > 0 ? crime.value() : fines.vandalismPerBlock() * 5.0;
		} else {
			raw = fines.base(crime.offence());
			if (valued(crime.offence())) {
				raw += Math.max(0, crime.value()) * fines.valueMultiplier();
			}
		}
		return Math.max(1L, Math.round(raw * fines.multiplier()));
	}

	/**
	 * The standing a crime costs in the settlement (a {@code StandingService.Deed} weight, negative), larger for
	 * valuable thefts.
	 */
	public static int standingWeight(Crime crime) {
		return switch (crime.offence()) {
			case TRESPASS -> -5;
			case LOCKPICKING, VANDALISM -> -8;
			case POACHING -> -10;
			case THEFT, PICKPOCKETING -> -10 - (int) Math.min(20, Math.max(0, crime.value()) / 10);
			case RESISTING_ARREST -> -15;
			case ASSAULT, JAILBREAK -> -20;
			case ROBBERY -> -40;
			case MURDER -> -100;
		};
	}

	/** The standing deed kind for an offence, e.g. {@code law:theft}. */
	public static String deedKind(Offence offence) {
		return "law:" + offence.getSerializedName();
	}

	/** Minutes in the cell for a bounty. Murder earns the longest sentence. */
	public static int jailMinutes(long bounty, boolean murder, LawConfig.Justice justice) {
		if (murder) {
			return justice.jailMaxMinutes();
		}
		long minutes = (bounty + justice.jailBitsPerMinute() - 1) / justice.jailBitsPerMinute();
		return (int) Math.clamp(minutes, justice.jailMinMinutes(), justice.jailMaxMinutes());
	}

	/** Whether a warrant may be settled in the pillory: small enough, and nothing violent. */
	public static boolean pilloryEligible(Warrant warrant, LawConfig.Justice justice) {
		return justice.pillory() && warrant.bounty() <= justice.pilloryMaxBounty() && !warrant.murder()
			&& severity(warrant.worst()) < severity(Offence.ASSAULT);
	}
}
