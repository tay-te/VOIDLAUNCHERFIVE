package dev.voidmc.hearth.law.justice;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.crime.LawStatusSender;
import dev.voidmc.hearth.law.folk.FolkTraits;
import dev.voidmc.hearth.law.item.StolenGoods;
import dev.voidmc.hearth.law.item.StolenMark;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What happens when a player answers a guard (docs/LIVING_WORLD.md §4): pay the fine into the treasury, submit to the
 * cell or the pillory (stolen goods confiscated), persuade, bribe, resist or flee; and a guard's stop-and-search.
 * Every outcome moves the warrant and reports a deed; the folk behaviours decide when to ask.
 */
public final class Justice {
	private Justice() {
	}

	/** How it went, for the guard to say and do next. */
	public enum Result {
		/** Settled: the guard lets them go. */
		SETTLED,
		/** Off to the cell or the pillory. */
		SENTENCED,
		/** Part of it: the bounty is smaller but still there. */
		REDUCED,
		/** Talked or paid in vain: the bounty grew. */
		FAILED,
		/** Cannot (no coin, no economy, tried already). */
		UNABLE,
		/** They fight or run: the guard gives chase. */
		RESISTED
	}

	/** Whether the player can pay a sum: coin carried, through the economy module. */
	public static boolean canAfford(ServerPlayer player, long bits) {
		EconomyService economy = HearthServices.economy();
		return economy != null && economy.balance(player) >= bits;
	}

	/** Pays the whole bounty into the treasury; stolen goods from this town are handed back. */
	public static Result pay(ServerPlayer player, Settlement s) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		EconomyService economy = HearthServices.economy();
		if (w == null || !w.active()) {
			return Result.SETTLED;
		}
		// the fine goes to the treasury, through the economy (requests/p3-economy.md §1)
		if (economy == null || w.bounty() <= 0 || !economy.fine(player, s, w.bounty())) {
			return Result.UNABLE;
		}
		long paid = w.bounty();
		w.clear();
		confiscate(player, s, false);
		Law.state(s).prune();
		s.markDirty();
		CrimeDesk.deed(s, player.getUUID(), "law:fine_paid", 1, null, player.blockPosition(), true);
		player.sendSystemMessage(Component.translatable("hearth.law.paid", economy.format(paid), s.name()).withStyle(ChatFormatting.GRAY));
		LawStatusSender.refresh(player);
		return Result.SETTLED;
	}

	/**
	 * Submits: stolen goods are taken, then the cell, or the pillory for lesser crimes, settles the warrant. With
	 * neither (no jail in the settings, or nowhere to hold them) the warrant is settled in standing instead.
	 */
	public static Result submit(ServerPlayer player, Settlement s, boolean preferPillory) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		if (w == null || !w.active()) {
			return Result.SETTLED;
		}
		LawConfig.Justice cfg = Law.config().justice();
		confiscate(player, s, cfg.confiscateAllStolen());
		long owed = w.bounty();
		boolean murder = w.murder();
		boolean pillory = preferPillory && Offences.pilloryEligible(w, cfg) && Jail.pillory(s, player.blockPosition()) != null;
		boolean held = false;
		if (pillory) {
			held = Jail.sentence(player, s, cfg.pilloryMinutes(), true, owed);
		} else if (cfg.jail()) {
			held = Jail.sentence(player, s, Offences.jailMinutes(owed, murder, cfg), false, owed);
		}
		if (!held) {
			// no cell to put them in: the town's scorn has to do (a public reprimand)
			CrimeDesk.deed(s, player.getUUID(), "law:reprimand", (int) -Math.min(40, 5 + owed / 20), null, player.blockPosition(), true);
			player.sendSystemMessage(Component.translatable("hearth.law.reprimand", s.name()).withStyle(ChatFormatting.GOLD));
		} else {
			CrimeDesk.deed(s, player.getUUID(), "law:jailed", -3, null, player.blockPosition(), true);
		}
		w.clear();
		Law.state(s).prune();
		s.markDirty();
		LawStatusSender.refresh(player);
		return held ? Result.SENTENCED : Result.SETTLED;
	}

	/** The odds of persuading this guard, for the label. */
	public static double persuadeOdds(ServerPlayer player, Settlement s, Resident guard) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		int severity = w == null ? 1 : Offences.severity(w.worst());
		StandingService standing = HearthServices.standing();
		int st = standing != null ? standing.standing(s.id(), player.getUUID()) : s.reputation(player.getUUID());
		int opinion = standing != null ? standing.opinion(guard.id, player.getUUID()) : 0;
		float charisma = standing != null ? standing.charisma(player) : 0;
		return SpeechCheck.persuade(st, opinion, charisma, severity, FolkTraits.of(guard).listener());
	}

	/** A word with the guard: small matters are let go, larger ones halved; failing makes it worse. One try per warrant. */
	public static Result persuade(ServerPlayer player, Settlement s, Resident guard) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		if (w == null || !w.active()) {
			return Result.SETTLED;
		}
		if (w.persuadeTried() || w.murder()) {
			return Result.UNABLE;
		}
		double odds = persuadeOdds(player, s, guard);
		long day = Law.day(player.level());
		w.setPersuadeTried(true);
		s.markDirty();
		if (player.getRandom().nextDouble() < odds) {
			if (Offences.severity(w.worst()) <= Offences.severity(Offence.THEFT) && w.bounty() <= Law.config().justice().pilloryMaxBounty()) {
				w.clear();
				Law.state(s).prune();
				LawStatusSender.refresh(player);
				return Result.SETTLED;
			}
			w.reduce(w.bounty() / 2, day);
			LawStatusSender.refresh(player);
			return Result.REDUCED;
		}
		w.grow(0.1, 5, day);
		LawStatusSender.refresh(player);
		return Result.FAILED;
	}

	public static long bribeAmount(Settlement s, UUID player) {
		Warrant w = Law.state(s).warrant(player);
		return SpeechCheck.guardBribe(w == null ? 0 : w.bounty());
	}

	public static double bribeOdds(ServerPlayer player, Settlement s, Resident guard) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		long bounty = w == null ? 0 : w.bounty();
		int severity = w == null ? 1 : Offences.severity(w.worst());
		return SpeechCheck.bribe(bribeAmount(s, player.getUUID()), bounty, severity, FolkTraits.of(guard).listener());
	}

	/** Coin in the guard's palm: taken either way; on success the warrant is gone, on failure it grows. One try. */
	public static Result bribe(ServerPlayer player, Settlement s, Resident guard) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		EconomyService economy = HearthServices.economy();
		if (w == null || !w.active()) {
			return Result.SETTLED;
		}
		long amount = bribeAmount(s, player.getUUID());
		if (w.bribeTried() || economy == null || !Law.config().witnesses().bribes() || economy.balance(player) < amount) {
			return Result.UNABLE;
		}
		double odds = bribeOdds(player, s, guard);
		w.setBribeTried(true);
		long day = Law.day(player.level());
		s.markDirty();
		if (player.getRandom().nextDouble() < odds) {
			// the coin goes into the guard's own purse
			economy.bribe(player, s, guard, amount);
			w.clear();
			Law.state(s).prune();
			LawStatusSender.refresh(player);
			return Result.SETTLED;
		}
		// an honest guard: no coin taken, and the attempt is on the record
		w.grow(0.25, 10, day);
		LawStatusSender.refresh(player);
		return Result.FAILED;
	}

	/** Fights or runs from the guard: resisting arrest, and a murderer who does so is an outlaw. */
	public static Result resist(ServerPlayer player, Settlement s) {
		long day = Law.day(player.level());
		Warrant w = Law.state(s).warrantFor(player.getUUID(), day);
		long fine = Math.max(1, Math.round(Law.config().fines().resistingArrest() * Law.config().fines().multiplier()));
		w.charge(Offence.RESISTING_ARREST, fine, day);
		w.addEvaded();
		if (w.murder() && Law.config().justice().outlaws()) {
			w.setOutlaw(true);
		}
		s.markDirty();
		CrimeDesk.deed(s, player.getUUID(), "law:resisting_arrest", -15, null, player.blockPosition(), true);
		LawStatusSender.refresh(player);
		return Result.RESISTED;
	}

	/**
	 * A stop and search: stolen goods this player took from this town are found and taken, and the theft is filed
	 * (witnessed now, by the guard); finding nothing clears much of the suspicion.
	 *
	 * @return the value found (0: nothing)
	 */
	public static long search(ServerPlayer player, Settlement s, @Nullable UUID guard) {
		ServerLevel level = player.level();
		long today = Law.day(level);
		int fade = Law.config().stealth().stolenFadeDays();
		Predicate<StolenMark> mine = m -> m.owner().settlementId() == s.id() && m.thief().equals(player.getUUID());
		List<ItemStack> found = StolenGoods.confiscate(player.getInventory(), today, fade, mine);
		if (found.isEmpty()) {
			Law.state(s).addSuspicion(player.getUUID(), -40);
			s.markDirty();
			LawStatusSender.refresh(player);
			return 0;
		}
		long value = 0;
		Owner victim = null;
		for (ItemStack stack : found) {
			value += Law.value(s, stack);
			StolenMark m = stack.get(dev.voidmc.hearth.law.item.LawComponents.STOLEN);
			if (m != null) {
				victim = m.owner();
			}
		}
		CrimeDesk.file(level, s, new dev.voidmc.hearth.api.LawService.Crime(Offence.THEFT, player.getUUID(), s.id(), player.blockPosition(),
			victim == null ? null : victim.resident(), value, true, level.getGameTime()), guard);
		return value;
	}

	/** Takes stolen goods: this town's (or, with {@code all}, every town's) that this player stole. */
	public static int confiscate(ServerPlayer player, Settlement s, boolean all) {
		long today = Law.day(player.level());
		int fade = Law.config().stealth().stolenFadeDays();
		Predicate<StolenMark> which = all ? m -> m.thief().equals(player.getUUID())
			: m -> m.owner().settlementId() == s.id() && m.thief().equals(player.getUUID());
		List<ItemStack> taken = StolenGoods.confiscate(player.getInventory(), today, fade, which);
		if (!taken.isEmpty()) {
			player.sendSystemMessage(Component.translatable("hearth.law.confiscated", taken.size()).withStyle(ChatFormatting.GRAY));
		}
		return taken.size();
	}

	/** Lets a player off entirely (admin pardon). */
	public static void pardon(Settlement s, UUID player) {
		Warrant w = Law.state(s).warrant(player);
		if (w != null) {
			w.clear();
		}
		Law.state(s).addSuspicion(player, -100);
		Law.state(s).prune();
		s.markDirty();
	}
}
