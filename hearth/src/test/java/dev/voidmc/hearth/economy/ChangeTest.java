package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.economy.coin.Change;
import dev.voidmc.hearth.economy.coin.Coin;
import org.junit.jupiter.api.Test;

class ChangeTest {
	@Test
	void fewestIsExactAndMinimal() {
		for (long bits = 0; bits <= 2500; bits++) {
			long[] c = Change.fewest(bits);
			assertEquals(bits, Change.value(c));
			assertTrue(c[Coin.BIT.ordinal()] < 10 && c[Coin.MARK.ordinal()] < 10, "never ten of a coin when the next would do: " + bits);
			assertEquals(bruteFewest(bits), Change.coins(c), "fewest coins for " + bits);
		}
	}

	/** Every purse of up to 12 bits, 12 marks and 4 crowns, against every amount up to its worth and beyond. */
	@Test
	void planIsCorrectForEveryPurse() {
		for (int b = 0; b <= 12; b++) {
			for (int m = 0; m <= 12; m++) {
				for (int c = 0; c <= 4; c++) {
					long[] held = {b, m, c};
					long worth = Change.value(held);
					for (long amount = 0; amount <= worth + 3; amount++) {
						Change.Payment p = Change.plan(held, amount);
						if (amount > worth) {
							assertNull(p, "cannot pay " + amount + " from " + worth);
							continue;
						}
						assertNotNull(p);
						for (Coin coin : Coin.VALUES) {
							long g = p.give()[coin.ordinal()];
							assertTrue(g >= 0 && g <= held[coin.ordinal()], "gives only coins held");
						}
						assertEquals(amount, Change.value(p.give()) - p.change(), "pays exactly, after change");
						assertTrue(p.change() >= 0);
						if (exactPossible(held, amount)) {
							assertEquals(0, p.change(), "exact payment found when one exists: " + b + "/" + m + "/" + c + " for " + amount);
						} else {
							assertTrue(p.change() > 0);
							// the change is less than the largest coin handed over: no coin given needlessly
							long largest = 0;
							for (Coin coin : Coin.VALUES) {
								if (p.give()[coin.ordinal()] > 0) {
									largest = Math.max(largest, coin.value);
								}
							}
							assertTrue(p.change() < largest);
						}
					}
				}
			}
		}
	}

	@Test
	void planDoesNotTouchHeld() {
		long[] held = {3, 2, 1};
		Change.plan(held, 57);
		assertEquals(3, held[0]);
		assertEquals(2, held[1]);
		assertEquals(1, held[2]);
	}

	private static long bruteFewest(long bits) {
		long best = Long.MAX_VALUE;
		for (long c = 0; c * 100 <= bits; c++) {
			for (long m = 0; c * 100 + m * 10 <= bits; m++) {
				best = Math.min(best, c + m + (bits - c * 100 - m * 10));
			}
		}
		return best;
	}

	private static boolean exactPossible(long[] held, long amount) {
		for (long c = 0; c <= held[2]; c++) {
			for (long m = 0; m <= held[1]; m++) {
				long rest = amount - c * 100 - m * 10;
				if (rest >= 0 && rest <= held[0]) {
					return true;
				}
			}
		}
		return false;
	}
}
