package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.mettle.character.cards.CardFees;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Rebuild fees in Hearth coin (docs/CARDS.md §7.1): to the trainer's till or purse at a trainer's rebuild, burned at a
 * Card Shrine. Without Hearth's economy module at the time of paying, emeralds as without Hearth.
 */
final class HearthFees implements CardFees {
	/** Who a trainer's rebuild pays. */
	record Trainer(Settlement settlement, Resident resident) {
	}

	@Override
	public boolean canPay(ServerPlayer player, long bits, int emeralds, @Nullable Object payee) {
		EconomyService economy = HearthServices.economy();
		if (economy == null) {
			return EMERALDS.canPay(player, bits, emeralds, payee);
		}
		return bits <= 0 || player.hasInfiniteMaterials() || economy.balance(player) >= bits;
	}

	@Override
	public boolean pay(ServerPlayer player, long bits, int emeralds, @Nullable Object payee) {
		EconomyService economy = HearthServices.economy();
		if (economy == null) {
			return EMERALDS.pay(player, bits, emeralds, payee);
		}
		if (bits <= 0) {
			return true;
		}
		return payee instanceof Trainer t ? economy.charge(player, t.settlement(), t.resident(), bits) : economy.charge(player, bits);
	}

	@Override
	public Component describe(long bits, int emeralds) {
		EconomyService economy = HearthServices.economy();
		return economy == null ? EMERALDS.describe(bits, emeralds) : economy.format(bits);
	}

	@Override
	public long balance(ServerPlayer player) {
		EconomyService economy = HearthServices.economy();
		return economy == null ? 0 : economy.balance(player);
	}

	/** A trade's coin: charged from one, paid to the other, in the same tick (request 8 would make it one call). */
	@Override
	public boolean transfer(ServerPlayer from, ServerPlayer to, long bits) {
		EconomyService economy = HearthServices.economy();
		if (economy == null || bits <= 0) {
			return bits <= 0;
		}
		if (!economy.charge(from, bits)) {
			return false;
		}
		economy.pay(to, bits);
		return true;
	}

	@Override
	public boolean coin() {
		return HearthServices.economy() != null;
	}
}
