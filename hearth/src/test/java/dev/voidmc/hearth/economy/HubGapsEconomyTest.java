package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.haggle.Haggle;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** The Hub's 2026-10-19 gaps on the economy side: "he takes it" odds and fees read back from a partner's label. */
class HubGapsEconomyTest {
	@Test
	void anOfferAsGoodAsHisWordIsAlwaysTaken() {
		assertEquals(100, Haggle.takesOdds(true, 100, 90, -1, -1, 90));
		assertEquals(100, Haggle.takesOdds(true, 100, 90, -1, -1, 95));
		assertEquals(100, Haggle.takesOdds(false, 100, 110, -1, -1, 105));
	}

	@Test
	void theOddsFallAsTheOfferGoesLowAndAnExactReadIsCertain() {
		int near = Haggle.takesOdds(true, 100, 100, -1, -1, 97);
		int far = Haggle.takesOdds(true, 100, 100, -1, -1, 60);
		assertTrue(near > far, near + " > " + far);
		assertEquals(0, Haggle.takesOdds(true, 100, 100, -1, -1, 0));
		// Speech 15 reads the limit exactly: inside a fifth of the room of his word he takes it, past it never
		assertEquals(100, Haggle.takesOdds(true, 100, 100, 80, 80, 96));
		assertEquals(0, Haggle.takesOdds(true, 100, 100, 80, 80, 79));
		// matches the haggle itself: a merchant with that limit closes on that offer
		Haggle h = Haggle.start(true, 100, 0.2, 100, 0, EconomyConfig.Haggle.DEFAULTS);
		assertEquals(Haggle.Event.DEAL, h.offer(96));
	}

	@Test
	void aFeeWrittenWithTheEconomysFormatIsReadBack() {
		Component label = Component.translatable("mettle.trainer.train", Component.literal("Speech"), CoinFormat.format(345));
		assertEquals(345, CoinFormat.read(label));
		assertEquals(7, CoinFormat.read(Component.literal("Lesson: ").append(CoinFormat.format(7))));
		assertEquals(-1, CoinFormat.read(Component.literal("Train Speech (40 bits)")));
		assertEquals(-1, CoinFormat.read(null));
		assertEquals(0, CoinFormat.read(CoinFormat.format(0)));
	}
}
