package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.economy.bank.Deposits;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepositsTest {
	private static final UUID A = new UUID(1, 2);
	private static final UUID B = new UUID(3, 4);

	@Test
	void depositAndWithdraw() {
		Deposits d = new Deposits();
		assertTrue(d.add(A, 500));
		assertTrue(d.add(A, -120));
		assertEquals(380, d.balance(A));
		assertEquals(0, d.balance(B));
	}

	@Test
	void cannotOverdraw() {
		Deposits d = new Deposits();
		d.add(A, 50);
		assertFalse(d.add(A, -51));
		assertEquals(50, d.balance(A));
		assertFalse(d.add(A, Long.MAX_VALUE), "overflow refused");
		assertEquals(50, d.balance(A));
	}

	@Test
	void emptiedAccountsAreDropped() {
		Deposits d = new Deposits();
		d.add(A, 10);
		d.add(A, -10);
		assertTrue(d.accounts().isEmpty());
	}

	@Test
	void savesAndLoads() {
		Deposits d = new Deposits();
		d.add(A, 1234);
		d.add(B, 1);
		var json = Deposits.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
		Deposits back = Deposits.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(1234, back.balance(A));
		assertEquals(1, back.balance(B));
	}
}
