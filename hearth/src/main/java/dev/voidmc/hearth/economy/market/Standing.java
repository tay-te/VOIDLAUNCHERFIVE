package dev.voidmc.hearth.economy.market;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyModule;
import net.minecraft.server.level.ServerPlayer;

/**
 * Honest trade earns a little standing: a point of reputation per crown of business, at most three points a day per
 * settlement, so it rewards a regular customer without letting coin buy a hero's welcome.
 */
final class Standing {
	static final long BITS_PER_POINT = 100;
	static final int POINTS_PER_DAY = 3;

	private Standing() {
	}

	static void earn(ServerPlayer player, Settlement settlement, MarketLedger ledger, long bits) {
		long day = EconomyModule.day(player.level());
		long packed = ledger.standingGain.getOrDefault(player.getUUID(), 0L);
		long gainedDay = packed >> 8;
		int gained = gainedDay == day ? (int) (packed & 0xFF) : 0;
		int points = (int) Math.min(POINTS_PER_DAY - gained, bits / BITS_PER_POINT); // whole crowns per trade
		if (points > 0) {
			settlement.addReputation(player.getUUID(), points);
			gained += points;
		}
		ledger.standingGain.put(player.getUUID(), day << 8 | gained);
	}
}
