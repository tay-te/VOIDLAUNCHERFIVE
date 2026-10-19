package dev.voidmc.hearth.law.crime;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawSkills;
import dev.voidmc.hearth.law.folk.FolkTraits;
import dev.voidmc.hearth.law.justice.Offences;
import dev.voidmc.hearth.law.justice.Warrant;
import dev.voidmc.hearth.law.state.Codecs;
import dev.voidmc.hearth.law.state.LawState.PendingReport;
import dev.voidmc.hearth.law.state.LawState;
import dev.voidmc.hearth.law.stealth.StealthTracker;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Where crimes go (docs/LIVING_WORLD.md §4). A crime is committed; the folk who witness it ({@link Witnesses}) either
 * are guards (the offender is wanted at once) or set off to report it ({@link PendingReport}, carried out by the witness
 * behaviour or, failing that, by the report deadline). A crime nobody saw is kept as {@link LawState.Unsolved}: it may be
 * discovered and cast suspicion. When a report reaches the law the bounty grows, standing falls and
 * {@link SettlementEvents#CRIME_COMMITTED} fires — once per crime.
 */
public final class CrimeDesk {
	/** Crimes by the same hand against the same victim this close together are one crime (a looted chest, a smashed wall). */
	static final long MERGE_TICKS = 200;
	/** How many witnesses set off to report one crime (the first to arrive is enough). */
	static final int MAX_REPORTERS = 3;

	private CrimeDesk() {
	}

	public enum Outcome {
		/** The law does not apply (exempt, disabled, outside a settlement). */
		IGNORED,
		/** Nobody saw it. */
		UNSEEN,
		/** Seen; a witness is going to report it. */
		WITNESSED,
		/** Seen by a guard: wanted now. */
		REPORTED
	}

	/**
	 * A crime was committed by {@code offender} at {@code pos} in {@code settlement}.
	 *
	 * @param victim     the resident wronged, or null (the settlement, a player's property)
	 * @param value      bits taken or broken
	 * @param deedNoise  how loud it was, 0–1
	 * @param victimFolk the victim's embodied folk when they were there to notice it themselves (assault, a hand caught in
	 *                   their pocket), else null
	 */
	public static Outcome commit(ServerLevel level, ServerPlayer offender, Settlement settlement, Offence offence, BlockPos pos, @Nullable UUID victim,
		long value, double deedNoise, @Nullable FolkHandle victimFolk) {
		if (!Law.config().enabled() || !Law.detects(offence) || Law.exempt(offender)) {
			return Outcome.IGNORED;
		}
		long now = level.getGameTime();
		Crime crime = new Crime(offence, offender.getUUID(), settlement.id(), pos.immutable(), victim, value, false, now);
		List<Witnesses.Witness> witnesses = new java.util.ArrayList<>(Witnesses.find(level, offender, deedNoise));
		if (victimFolk != null && victimFolk.awake() && witnesses.stream().noneMatch(w -> w.folk().resident().id.equals(victimFolk.resident().id))) {
			witnesses.add(0, new Witnesses.Witness(victimFolk, FolkTraits.of(victimFolk.resident()), 1.0, 1.0));
		}
		Outcome outcome = commitSeen(level, settlement, crime, witnesses);
		if (outcome == Outcome.UNSEEN && (offence == Offence.THEFT || offence == Offence.LOCKPICKING)) {
			// a quiet hand: a character mod's Stealth learns from it
			LawSkills.award(offender, PlayerSkills.STEALTH, LawSkills.unseenXp(value), "unseen/" + offence.getSerializedName());
		}
		return outcome;
	}

	/** {@link #commit} with the witnesses already found (tests, and crimes whose witnesses are decided elsewhere). */
	public static Outcome commitSeen(ServerLevel level, Settlement settlement, Crime crime, List<Witnesses.Witness> witnesses) {
		LawState state = Law.state(settlement);
		long day = Law.day(level);
		if (witnesses.isEmpty()) {
			unsolved(state, crime, day);
			settlement.markDirty();
			return Outcome.UNSEEN;
		}
		Crime seen = Codecs.witnessed(crime, true);
		ServerPlayer offender = level.getServer().getPlayerList().getPlayer(crime.offender());
		if (offender != null) {
			StealthTracker.noticed(offender);
		}
		for (Witnesses.Witness w : witnesses) {
			if (w.traits().isGuard()) {
				// an officer of the law saw it: no report needed
				PendingReport merged = merge(state, seen);
				file(level, settlement, merged != null ? merged.crime() : seen, w.folk().resident().id);
				if (merged != null) {
					state.pending().removeIf(p -> p.id() == merged.id());
				}
				say(w.folk(), seen, offender, true);
				return Outcome.REPORTED;
			}
		}
		PendingReport merged = merge(state, seen);
		if (merged == null) {
			int id = state.nextId();
			long deadline = level.getGameTime() + Law.config().witnesses().reportSeconds() * 20L;
			for (int i = 0; i < witnesses.size() && i < MAX_REPORTERS; i++) {
				state.pending().add(new PendingReport(id, witnesses.get(i).folk().resident().id, seen, deadline));
			}
		}
		Witnesses.Witness first = witnesses.get(0);
		say(first.folk(), seen, offender, false);
		if (offender != null) {
			offender.sendOverlayMessage(Component.translatable("hearth.law.seen_by", first.folk().resident().fullName()).withStyle(ChatFormatting.RED));
		}
		settlement.markDirty();
		return Outcome.WITNESSED;
	}

	/** Folds a new sighting into a recent report of the same deed (the same thief at the same victim moments later). */
	private static @Nullable PendingReport merge(LawState state, Crime crime) {
		PendingReport found = null;
		for (int i = 0; i < state.pending().size(); i++) {
			PendingReport p = state.pending().get(i);
			if (same(p.crime(), crime)) {
				Crime sum = new Crime(p.crime().offence(), p.crime().offender(), p.crime().settlementId(), p.crime().pos(), p.crime().victim(),
					p.crime().value() + crime.value(), true, crime.gameTime());
				PendingReport replaced = new PendingReport(p.id(), p.witness(), sum, p.deadline());
				state.pending().set(i, replaced);
				found = replaced;
			}
		}
		return found;
	}

	private static void unsolved(LawState state, Crime crime, long day) {
		for (int i = 0; i < state.unsolved().size(); i++) {
			LawState.Unsolved u = state.unsolved().get(i);
			if (same(u.crime(), crime)) {
				state.unsolved().set(i, new LawState.Unsolved(new Crime(crime.offence(), crime.offender(), crime.settlementId(), u.crime().pos(),
					crime.victim(), u.crime().value() + crime.value(), false, crime.gameTime()), u.day()));
				return;
			}
		}
		state.unsolved().add(new LawState.Unsolved(crime, day));
	}

	static boolean same(Crime a, Crime b) {
		return a.offence() == b.offence() && a.offender().equals(b.offender()) && java.util.Objects.equals(a.victim(), b.victim())
			&& Math.abs(b.gameTime() - a.gameTime()) <= MERGE_TICKS && a.offence() != Offence.MURDER;
	}

	/**
	 * A report reached the law: the offender is wanted, standing falls, the event fires. Removes the crime's other pending
	 * reports.
	 *
	 * @param reporter the resident who reported it (null: the guard found out for themselves, or a module reported it)
	 */
	public static void file(ServerLevel level, Settlement settlement, Crime crime, @Nullable UUID reporter) {
		LawState state = Law.state(settlement);
		long day = Law.day(level);
		Crime filed = Codecs.witnessed(crime, true);
		Warrant warrant = state.warrantFor(filed.offender(), day);
		long fine = Offences.fine(filed, Law.config().fines());
		warrant.charge(filed.offence(), fine, day);
		state.addSuspicion(filed.offender(), -100);
		deed(settlement, filed.offender(), Offences.deedKind(filed.offence()), Offences.standingWeight(filed), filed.victim(), filed.pos(), true);
		settlement.markDirty();
		HearthWorld world = HearthWorld.of(level);
		if (world != null) {
			try {
				SettlementEvents.CRIME_COMMITTED.invoker().onCrime(world, settlement, filed);
			} catch (RuntimeException e) {
				Hearth.LOG.error("a CRIME_COMMITTED listener failed", e);
			}
		}
		ServerPlayer offender = level.getServer().getPlayerList().getPlayer(filed.offender());
		if (offender != null) {
			offender.sendSystemMessage(Component.translatable("hearth.law.wanted_now", settlement.name(),
				Component.translatable(filed.offence().translationKey()), warrant.bounty()).withStyle(ChatFormatting.RED));
			LawStatusSender.refresh(offender);
			// the watch sends a guard for them now, rather than waiting for one to happen by
			dev.voidmc.hearth.law.folk.GuardDispatch.call(level, settlement, offender.getUUID());
		}
		Hearth.LOG.debug("law: {} filed in {} against {} (fine {}, bounty {})", filed.offence(), settlement.name(), filed.offender(), fine,
			warrant.bounty());
	}

	/** A report that names a pending crime: files it and drops the crime's other reports. */
	public static void file(ServerLevel level, Settlement settlement, PendingReport report) {
		LawState state = Law.state(settlement);
		state.pending().removeIf(p -> p.id() == report.id());
		file(level, settlement, report.crime(), report.witness());
	}

	/** Witnesses who were bribed, threatened or killed: their reports of this player's crimes are gone. */
	public static int silence(Settlement settlement, UUID witness, UUID offender) {
		LawState state = Law.state(settlement);
		int before = state.pending().size();
		state.pending().removeIf(p -> p.witness().equals(witness) && p.crime().offender().equals(offender));
		int removed = before - state.pending().size();
		if (removed > 0) {
			settlement.markDirty();
		}
		return removed;
	}

	/** Every pending report a witness carries against a player. */
	public static List<PendingReport> reportsBy(Settlement settlement, UUID witness, UUID offender) {
		return Law.state(settlement).pending().stream().filter(p -> p.witness().equals(witness) && p.crime().offender().equals(offender)).toList();
	}

	/** Reports a deed to the standing service (or straight to the settlement's raw reputation when it is absent). */
	public static void deed(Settlement settlement, UUID player, String kind, int weight, @Nullable UUID resident, BlockPos pos, boolean witnessed) {
		StandingService standing = HearthServices.standing();
		if (standing != null) {
			try {
				standing.deed(new StandingService.Deed(player, settlement.id(), kind, weight, resident, pos, witnessed));
				return;
			} catch (RuntimeException e) {
				Hearth.LOG.error("standing deed {} failed", kind, e);
			}
		}
		if (witnessed) {
			settlement.addReputation(player, weight);
		}
	}

	/** What the witness cries out, to everyone near (a guard to the offender directly). */
	private static void say(FolkHandle folk, Crime crime, @Nullable ServerPlayer offender, boolean guard) {
		String key = guard ? "hearth.law.say.guard." : "hearth.law.say.witness.";
		String which = switch (crime.offence()) {
			case MURDER -> "murder";
			case ASSAULT, RESISTING_ARREST -> "assault";
			case TRESPASS, LOCKPICKING -> "trespass";
			case VANDALISM -> "vandalism";
			default -> "theft";
		};
		try {
			if (offender != null) {
				folk.lookAt(offender);
			}
			folk.say(Component.translatable(key + which), null);
		} catch (RuntimeException e) {
			Hearth.LOG.debug("law: folk could not speak", e);
		}
	}

	/** The name of a resident by id in a settlement, or "someone". */
	public static Component residentName(@Nullable Settlement s, @Nullable UUID id) {
		Resident r = s == null || id == null ? null : s.resident(id);
		return r == null ? Component.translatable("hearth.law.owner.someone") : Component.literal(r.fullName());
	}

	/** An external report (LawService.report): witnessed crimes are filed, others kept as unsolved suspicion. */
	public static void report(MinecraftServer server, Crime crime) {
		if (!Law.config().enabled() || !Law.detects(crime.offence())) {
			return;
		}
		HearthWorld world = HearthWorld.of(server.overworld());
		Settlement s = world == null ? null : world.settlement(crime.settlementId());
		if (s == null) {
			return;
		}
		ServerPlayer offender = server.getPlayerList().getPlayer(crime.offender());
		if (offender != null && Law.exempt(offender)) {
			return;
		}
		if (crime.witnessed()) {
			file(server.overworld(), s, crime, null);
		} else {
			unsolved(Law.state(s), crime, Law.day(server.overworld()));
			s.markDirty();
		}
	}
}
