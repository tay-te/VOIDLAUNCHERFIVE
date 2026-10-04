package dev.voidmc.hearth.law.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeCategory;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LockRulesTest {
	@Test
	void doorsFollowTheBuilding() {
		assertEquals(1, Locks.doorTier(ArchetypeCategory.HOUSING, "house/hovel", 0));
		assertEquals(2, Locks.doorTier(ArchetypeCategory.HOUSING, "house/merchant_house", 3));
		assertEquals(4, Locks.doorTier(ArchetypeCategory.LAW, "law/jail", 1));
		assertEquals(4, Locks.doorTier(ArchetypeCategory.COMMERCE, "commerce/bank", 2));
		assertEquals(0, Locks.doorTier(ArchetypeCategory.COMMERCE, "commerce/inn", 2), "inns never lock");
		assertEquals(0, Locks.doorTier(ArchetypeCategory.CIVIC, "civic/chapel", 2), "nor chapels");
		assertEquals(0, Locks.doorTier(ArchetypeCategory.INFRASTRUCTURE, "infra/road", 2));
	}

	@Test
	void someHomeChestsAreLockedAndBankChestsAlways() {
		Random random = new Random(1);
		int locked = 0;
		int n = 4000;
		for (int i = 0; i < n; i++) {
			int tier = Locks.chestTier(ArchetypeCategory.HOUSING, "house/cottage", 1, random.nextLong());
			assertTrue(tier >= 0 && tier <= 3);
			if (tier > 0) {
				locked++;
			}
			assertEquals(5, Locks.chestTier(ArchetypeCategory.COMMERCE, "commerce/bank", 1, random.nextLong()));
			assertEquals(4, Locks.chestTier(ArchetypeCategory.NOBLE, "noble/keep", 1, random.nextLong()));
		}
		assertEquals(0.55, locked / (double) n, 0.05);
	}

	@Test
	void sameChestSameLock() {
		assertEquals(Locks.chestTier(ArchetypeCategory.WORKPLACE, "work/smithy", 2, 12345L),
			Locks.chestTier(ArchetypeCategory.WORKPLACE, "work/smithy", 2, 12345L));
	}

	@Test
	void openHouses() {
		assertTrue(Locks.isOpenHouse("commerce/tavern"));
		assertTrue(Locks.isOpenHouse("civic/cathedral"));
		assertFalse(Locks.isOpenHouse("house/cottage"));
	}
}
