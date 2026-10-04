package dev.voidmc.hearth.social;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The social module's calls into the optional services, each guarded: with no economy there is nothing to pay with
 * (services are refused, dice are played for honour), with no standing service deeds go to the settlement's raw
 * reputation, with no law nothing is reported, with no npc service replies are written to the player.
 */
public final class Hooks {
	private Hooks() {
	}

	public static @Nullable EconomyService economy() {
		return HearthServices.economy();
	}

	/** Formats coin the economy's way (or as plain bits without it). */
	public static Component coins(long bits) {
		EconomyService e = economy();
		return e != null ? e.format(bits) : Component.translatable("hearth.social.bits", bits);
	}

	/**
	 * Charges the player; on failure tells them (in the speaker's voice when there is one) and returns false. A price of
	 * 0 is always paid. Without an economy only free things can be had.
	 */
	public static boolean charge(ServerPlayer player, long bits, @Nullable Resident speaker, @Nullable Settlement s) {
		if (bits <= 0) {
			return true;
		}
		EconomyService e = economy();
		if (e == null) {
			reply(player, speaker, s, Component.translatable("hearth.social.no_economy"));
			return false;
		}
		if (!e.charge(player, bits)) {
			reply(player, speaker, s, Component.translatable("hearth.social.cannot_afford", e.format(bits)));
			return false;
		}
		return true;
	}

	public static long balance(ServerPlayer player) {
		EconomyService e = economy();
		return e == null ? 0 : e.balance(player);
	}

	public static void pay(ServerPlayer player, long bits) {
		EconomyService e = economy();
		if (e != null && bits > 0) {
			e.pay(player, bits);
		}
	}

	/** Reports a deed to the standing service, or nudges the settlement's raw reputation when it is absent. */
	public static void deed(ServerPlayer player, Settlement s, String kind, int weight, @Nullable UUID resident, BlockPos pos, boolean witnessed) {
		StandingService st = HearthServices.standing();
		if (st != null) {
			st.deed(new StandingService.Deed(player.getUUID(), s.id(), kind, weight, resident, pos.immutable(), witnessed));
		} else if (weight != 0) {
			s.addReputation(player.getUUID(), Integer.signum(weight) * Math.max(1, Math.abs(weight) / 3));
			s.markDirty();
		}
	}

	/** The player's standing in a settlement (the standing service's, else the raw reputation). */
	public static int standing(ServerPlayer player, Settlement s) {
		StandingService st = HearthServices.standing();
		return st != null ? st.standing(s.id(), player.getUUID()) : s.reputation(player.getUUID());
	}

	/** A resident's opinion of the player (0 without the standing service). */
	public static int opinion(UUID resident, ServerPlayer player) {
		StandingService st = HearthServices.standing();
		return st != null ? st.opinion(resident, player.getUUID()) : 0;
	}

	public static StandingService.@Nullable Title title(ServerPlayer player, Settlement s) {
		StandingService st = HearthServices.standing();
		return st != null ? st.title(s.id(), player.getUUID()) : null;
	}

	public static void report(LawService.Crime crime) {
		LawService law = HearthServices.law();
		if (law != null) {
			law.report(crime);
		}
	}

	public static boolean wanted(ServerPlayer player, Settlement s) {
		LawService law = HearthServices.law();
		return law != null && law.wanted(player.getUUID(), s.id());
	}

	public static long bounty(ServerPlayer player, Settlement s) {
		LawService law = HearthServices.law();
		return law == null ? 0 : law.bounty(player.getUUID(), s.id());
	}

	public static NpcService.@Nullable FolkHandle folk(UUID resident) {
		NpcService npc = HearthServices.npc();
		return npc == null ? null : npc.folk(resident);
	}

	/**
	 * A resident answers the player: a line in chat ("Edda: ..."), which stays readable behind the conversation screen,
	 * while the embodied folk turns to the player and gestures.
	 */
	public static void reply(ServerPlayer player, @Nullable Resident speaker, @Nullable Settlement s, Component line) {
		if (speaker == null) {
			player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GRAY));
			return;
		}
		player.sendSystemMessage(Component.translatable("hearth.social.says", Component.literal(speaker.givenName).withStyle(ChatFormatting.GOLD),
			line.copy().withStyle(ChatFormatting.WHITE)));
		NpcService.FolkHandle h = folk(speaker.id);
		if (h != null) {
			h.lookAt(player);
			h.swing();
		}
	}
}
