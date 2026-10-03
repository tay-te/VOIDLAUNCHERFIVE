package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.economy.coin.Change;
import dev.voidmc.hearth.economy.coin.Coin;
import dev.voidmc.hearth.economy.coin.Purse;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PurseTest {
	@Test
	void chargeMakesChangeAcrossSlotsAndPouches() {
		Purse purse = new Purse(64);
		purse.addSlot(0, Coin.CROWN, 1);
		purse.addSlot(1, null, 0);
		purse.addPouch(2, new long[] {3, 0, 0}, 512);
		assertEquals(103, purse.balance());
		long[] overflow = purse.charge(47);
		assertNotNull(overflow);
		assertArrayEquals(new long[] {0, 0, 0}, overflow);
		assertEquals(56, purse.balance());
	}

	@Test
	void chargeIsAllOrNothing() {
		Purse purse = new Purse(64);
		Purse.Slot s = purse.addSlot(0, Coin.MARK, 4);
		Purse.Pouch p = purse.addPouch(1, new long[] {9, 0, 0}, 512);
		assertNull(purse.charge(50));
		assertEquals(49, purse.balance());
		assertFalse(s.changed());
		assertFalse(p.changed());
	}

	@Test
	void changeGoesIntoThePouchFirst() {
		Purse purse = new Purse(64);
		purse.addSlot(0, Coin.CROWN, 1);
		Purse.Pouch p = purse.addPouch(1, new long[] {0, 0, 0}, 512);
		purse.charge(1);
		// 99 back in the fewest coins: 9 marks, 9 bits, into the pouch
		assertArrayEquals(new long[] {9, 9, 0}, p.counts());
	}

	@Test
	void payFillsStacksThenEmptySlotsThenOverflows() {
		Purse purse = new Purse(64);
		Purse.Slot a = purse.addSlot(0, Coin.BIT, 60);
		Purse.Slot b = purse.addSlot(1, null, 0);
		long[] overflow = purse.addCoins(new long[] {70, 0, 0});
		assertEquals(64, a.count());
		assertEquals(Coin.BIT, b.coin());
		assertEquals(64, b.count());
		assertArrayEquals(new long[] {2, 0, 0}, overflow);
	}

	@Test
	void pouchCapacityIsRespected() {
		Purse purse = new Purse(64);
		Purse.Pouch p = purse.addPouch(0, new long[] {0, 0, 0}, 10);
		long[] overflow = purse.addCoins(new long[] {0, 25, 0});
		assertEquals(10, Change.coins(p.counts()));
		assertArrayEquals(new long[] {0, 15, 0}, overflow);
	}

	@Test
	void takesFromTheSmallestLooseStackFirst() {
		Purse purse = new Purse(64);
		Purse.Slot big = purse.addSlot(0, Coin.BIT, 40);
		Purse.Slot small = purse.addSlot(1, Coin.BIT, 3);
		purse.charge(5);
		assertEquals(Coin.BIT, big.coin());
		assertEquals(38, big.count());
		assertTrue(small.coin() == null && small.count() == 0, "the small stack is used up and the slot freed");
	}

	/** Random purses and amounts: value is always conserved (paid + kept + dropped). */
	@Test
	void valueIsConserved() {
		Random r = new Random(42);
		for (int trial = 0; trial < 5000; trial++) {
			Purse purse = new Purse(64);
			int slots = r.nextInt(6);
			for (int i = 0; i < slots; i++) {
				if (r.nextInt(3) == 0) {
					purse.addSlot(i, null, 0);
				} else {
					purse.addSlot(i, Coin.VALUES[r.nextInt(3)], 1 + r.nextInt(64));
				}
			}
			if (r.nextBoolean()) {
				purse.addPouch(10, new long[] {r.nextInt(50), r.nextInt(50), r.nextInt(20)}, 200);
			}
			long before = purse.balance();
			long amount = r.nextInt((int) before + 20);
			long[] overflow = purse.charge(amount);
			if (amount > before) {
				assertNull(overflow);
				assertEquals(before, purse.balance());
			} else {
				assertNotNull(overflow);
				assertEquals(before - amount, purse.balance() + Change.value(overflow));
			}
		}
	}
}
