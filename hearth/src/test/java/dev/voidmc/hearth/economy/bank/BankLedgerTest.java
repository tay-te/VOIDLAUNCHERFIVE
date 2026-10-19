package dev.voidmc.hearth.economy.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BankLedgerTest {
	@Test
	void newestFirstAndOnlyTheLastFew() {
		List<BankLedger.Line> lines = List.of();
		for (int i = 0; i < BankLedger.MAX + 5; i++) {
			lines = BankLedger.push(lines, new BankLedger.Line(i, BankLedger.DEPOSIT, -i - 1, i + 1, "Bradfield", ""));
		}
		assertEquals(BankLedger.MAX, lines.size());
		assertEquals(BankLedger.MAX + 4, lines.getFirst().day());
		assertEquals(5, lines.getLast().day());
	}

	@Test
	void everyCoinMovingActionHasAKind() {
		assertEquals(BankLedger.DEPOSIT, BankLedger.kindOf(EconomyActionPayload.DEPOSIT));
		assertEquals(BankLedger.FUND, BankLedger.kindOf(EconomyActionPayload.FUND));
		assertEquals(BankLedger.DELIVER, BankLedger.kindOf(EconomyActionPayload.DELIVER));
		assertNull(BankLedger.kindOf(EconomyActionPayload.ACCEPT_CONTRACT));
		List<String> kinds = new ArrayList<>();
		for (int a : new int[] {EconomyActionPayload.WITHDRAW, EconomyActionPayload.SELL_EMERALDS, EconomyActionPayload.BUY_EMERALDS,
			EconomyActionPayload.BUY_PLOT, EconomyActionPayload.HIRE}) {
			kinds.add(BankLedger.kindOf(a));
		}
		assertEquals(List.of("withdraw", "sell_emeralds", "buy_emeralds", "buy_plot", "hire"), kinds);
	}
}
