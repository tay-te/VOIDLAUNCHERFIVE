package dev.voidmc.hearth.law.talk;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.folk.FolkTraits;
import dev.voidmc.hearth.law.item.StolenGoods;
import dev.voidmc.hearth.law.item.StolenMark;
import dev.voidmc.hearth.law.justice.Justice;
import dev.voidmc.hearth.law.justice.Offences;
import dev.voidmc.hearth.law.justice.SpeechCheck;
import dev.voidmc.hearth.law.justice.Warrant;
import dev.voidmc.hearth.law.state.LawState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The law in conversation (the npc module's screen): settle a warrant with a guard of your own accord (pay, or turn
 * yourself in), hush a witness before they reach the guard (a bribe or a threat, the odds shown), and sell stolen goods
 * to a fence — a FENCE resident anywhere, or the innkeeper in some towns. Works with nothing but the residents' records,
 * so it needs no embodied folk.
 */
public final class LawDialogue implements DialogueProvider {
	/** What a fence pays, as a part of what the goods are worth. */
	static final double FENCE_RATE = 0.35;

	@Override
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		if (!Law.config().enabled() || Law.exempt(player)) {
			return;
		}
		Warrant w = Law.state(s).warrant(player.getUUID());
		boolean guard = r.job == JobType.GUARD || r.job == JobType.JAILER;
		EconomyService economy = HearthServices.economy();
		if (guard && w != null && w.active()) {
			if (economy != null) {
				out.add(new Option("law:pay", Component.translatable("hearth.law.talk.pay", economy.format(w.bounty())), 150));
			}
			out.add(new Option("law:submit", Component.translatable("hearth.law.talk.submit"), 151));
		}
		List<LawState.PendingReport> reports = CrimeDesk.reportsBy(s, r.id, player.getUUID());
		if (!reports.isEmpty() && !guard) {
			long fine = reports.stream().mapToLong(p -> Offences.fine(p.crime(), Law.config().fines())).max().orElse(10);
			int worst = reports.stream().mapToInt(p -> Offences.severity(p.crime().offence())).max().orElse(1);
			if (economy != null && Law.config().witnesses().bribes()) {
				long amount = SpeechCheck.witnessBribe(fine);
				int odds = SpeechCheck.percent(SpeechCheck.bribe(amount, amount, worst, FolkTraits.of(r).listener()));
				out.add(new Option("law:hush", Component.translatable("hearth.law.choice.hush", economy.format(amount), odds), 60));
			}
			if (Law.config().witnesses().threats()) {
				int odds = SpeechCheck.percent(SpeechCheck.threaten(menace(player), FolkTraits.of(r).listener()));
				out.add(new Option("law:threaten", Component.translatable("hearth.law.choice.threaten", odds), 61));
			}
		}
		if (economy != null && isFence(s, r) && stolenValue(player, s) > 0) {
			out.add(new Option("law:fence", Component.translatable("hearth.law.talk.fence"), 120));
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement s, Resident r, String optionId) {
		switch (optionId) {
			case "law:pay" -> {
				Justice.Result result = Justice.pay(player, s);
				tell(player, r, result == Justice.Result.SETTLED ? "hearth.law.guard.settled" : "hearth.law.guard.unable");
				return true;
			}
			case "law:submit" -> {
				Justice.submit(player, s, false);
				return true;
			}
			case "law:hush" -> {
				return hush(player, s, r);
			}
			case "law:threaten" -> {
				return threaten(player, s, r);
			}
			case "law:fence" -> {
				return fence(player, s, r);
			}
			default -> {
				return false;
			}
		}
	}

	private static boolean hush(ServerPlayer player, Settlement s, Resident r) {
		List<LawState.PendingReport> reports = CrimeDesk.reportsBy(s, r.id, player.getUUID());
		EconomyService economy = HearthServices.economy();
		if (reports.isEmpty() || economy == null) {
			return false;
		}
		long fine = reports.stream().mapToLong(p -> Offences.fine(p.crime(), Law.config().fines())).max().orElse(10);
		int worst = reports.stream().mapToInt(p -> Offences.severity(p.crime().offence())).max().orElse(1);
		long amount = SpeechCheck.witnessBribe(fine);
		if (economy.balance(player) < amount) {
			tell(player, r, "hearth.law.guard.unable");
			return true;
		}
		if (player.getRandom().nextDouble() < SpeechCheck.bribe(amount, amount, worst, FolkTraits.of(r).listener())) {
			economy.bribe(player, s, r, amount);
			CrimeDesk.silence(s, r.id, player.getUUID());
			CrimeDesk.deed(s, player.getUUID(), "law:bribed_witness", -2, r.id, player.blockPosition(), false);
			tell(player, r, "hearth.law.witness.bribed");
		} else {
			hurry(s, reports);
			tell(player, r, "hearth.law.witness.refused");
		}
		return true;
	}

	private static boolean threaten(ServerPlayer player, Settlement s, Resident r) {
		List<LawState.PendingReport> reports = CrimeDesk.reportsBy(s, r.id, player.getUUID());
		if (reports.isEmpty()) {
			return false;
		}
		if (player.getRandom().nextDouble() < SpeechCheck.threaten(menace(player), FolkTraits.of(r).listener())) {
			CrimeDesk.silence(s, r.id, player.getUUID());
			CrimeDesk.deed(s, player.getUUID(), "law:threatened_witness", -8, r.id, player.blockPosition(), false);
			tell(player, r, "hearth.law.witness.cowed");
		} else {
			hurry(s, reports);
			tell(player, r, "hearth.law.witness.defiant");
		}
		return true;
	}

	/** A witness who will not be bought or frightened makes haste: their report is due at once. */
	private static void hurry(Settlement s, List<LawState.PendingReport> reports) {
		LawState state = Law.state(s);
		for (LawState.PendingReport p : reports) {
			int i = state.pending().indexOf(p);
			if (i >= 0) {
				state.pending().set(i, new LawState.PendingReport(p.id(), p.witness(), p.crime(), Math.min(p.deadline(), 0)));
			}
		}
		s.markDirty();
	}

	/** A fence: a FENCE anywhere; in one town in three of village size or more, the innkeeper. */
	static boolean isFence(Settlement s, Resident r) {
		if (r.job == JobType.FENCE) {
			// with the economy module a FENCE keeps a real shop (its till, its shelves): the market's fence, not this one
			return HearthServices.economy() == null;
		}
		return r.job == JobType.INNKEEPER && s.tier().ordinal() >= SettlementTier.VILLAGE.ordinal() && Math.floorMod(s.id() * 0x9E3779B97F4A7C15L, 3) == 0;
	}

	private static long stolenValue(ServerPlayer player, Settlement s) {
		long today = Law.day(player.level());
		int fade = Law.config().stealth().stolenFadeDays();
		Inventory inv = player.getInventory();
		long value = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (StolenGoods.mark(stack, today, fade) != null) {
				value += Law.value(s, stack);
			}
		}
		return value;
	}

	/** Sells every stolen item the player carries to the fence, cheaply, as far as the fence's cash for the day goes. */
	private static boolean fence(ServerPlayer player, Settlement s, Resident r) {
		EconomyService economy = HearthServices.economy();
		if (economy == null) {
			return false;
		}
		long today = Law.day(player.level());
		int fade = Law.config().stealth().stolenFadeDays();
		CompoundTag tag = r.extra("law");
		long spentDay = tag.getLongOr("fence_day", Long.MIN_VALUE);
		long spent = spentDay == today ? tag.getLongOr("fence_spent", 0) : 0;
		// the fence's own coin: no more than their purse holds, paid out of it (no new money)
		long cash = Math.min(fenceCash(r, s) - spent, economy.purse(r));
		Inventory inv = player.getInventory();
		long paid = 0;
		int sold = 0;
		for (int i = 0; i < inv.getContainerSize() && cash > 0; i++) {
			ItemStack stack = inv.getItem(i);
			StolenMark mark = StolenGoods.mark(stack, today, fade);
			if (mark == null) {
				continue;
			}
			long price = Math.max(1, Math.round(Law.value(s, stack) * FENCE_RATE));
			if (price > cash) {
				continue;
			}
			cash -= price;
			paid += price;
			sold += stack.getCount();
			inv.setItem(i, ItemStack.EMPTY);
		}
		if (paid > 0) {
			paid = economy.takeFromPurse(s, r, paid);
			economy.pay(player, paid);
			tag.putLong("fence_day", today);
			tag.putLong("fence_spent", spent + paid);
			s.markDirty();
			player.sendSystemMessage(Component.translatable("hearth.law.fence.sold", sold, economy.format(paid)).withStyle(ChatFormatting.GRAY));
		} else {
			tell(player, r, "hearth.law.fence.broke");
		}
		return true;
	}

	/** A fence's coin for a day: more in bigger towns. */
	static long fenceCash(Resident r, Settlement s) {
		return 120L + s.tier().ordinal() * 80L + Math.floorMod(r.id.getLeastSignificantBits(), 60);
	}

	private static float menace(ServerPlayer player) {
		var standing = HearthServices.standing();
		if (standing != null) {
			return standing.menace(player);
		}
		float armour = player.getArmorValue() / 20.0F;
		boolean armed = player.getMainHandItem().has(net.minecraft.core.component.DataComponents.WEAPON);
		return Math.min(1.0F, armour * 0.6F + (armed ? 0.35F : 0.0F));
	}

	private static void tell(ServerPlayer player, Resident r, String key) {
		player.sendSystemMessage(Component.literal("<" + r.fullName() + "> ").append(Component.translatable(key)));
	}
}
