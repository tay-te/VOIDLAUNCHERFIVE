package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.voidmc.hearth.economy.command.AmountParser;
import org.junit.jupiter.api.Test;

class AmountParserTest {
	@Test
	void parsesBitsAndCoins() {
		assertEquals(250L, AmountParser.parse("250"));
		assertEquals(253L, AmountParser.parse("2c5m3b"));
		assertEquals(300L, AmountParser.parse("3crowns"));
		assertEquals(140L, AmountParser.parse("1 crown 4 marks"));
		assertEquals(12L, AmountParser.parse("1m2"));
	}

	@Test
	void rejectsNonsense() {
		assertNull(AmountParser.parse(""));
		assertNull(AmountParser.parse("abc"));
		assertNull(AmountParser.parse("3x"));
		assertNull(AmountParser.parse("99999999999999999999"));
	}
}
