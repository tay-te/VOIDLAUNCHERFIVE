package dev.voidmc.hearth.npc.work.field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Haulers' handcarts: capped a town and a server, shrinking as the server slows, only on longer hauls (§6.1, §9.2). */
class HandcartsTest {
	private static final FieldWorkConfig.Carts CFG = new FieldWorkConfig.Carts(true, 16, 4, 32);

	@AfterEach
	void reset() {
		FieldWork.setConfig(FieldWorkConfig.DEFAULTS);
	}

	@Test
	void aTownDrawsAtMostItsCapAndTheServerAtMostItsOwn() {
		assertTrue(Handcarts.admits(1, 0, 0, 10, 50, CFG));
		assertTrue(Handcarts.admits(1, 3, 20, 10, 50, CFG));
		assertFalse(Handcarts.admits(1, 4, 20, 10, 50, CFG), "four a town");
		assertFalse(Handcarts.admits(1, 0, 32, 10, 50, CFG), "thirty-two a server");
	}

	@Test
	void aSlowServerLetsNoNewCartOut() {
		// comfortable up to 60 % of high_mspt (30 of 50); above it nothing new starts
		assertTrue(Handcarts.admits(1, 0, 0, 29, 50, CFG));
		assertFalse(Handcarts.admits(1, 0, 0, 31, 50, CFG));
		assertFalse(Handcarts.admits(1, 0, 0, 60, 50, CFG));
	}

	@Test
	void switchedOffOrZeroCapsMeanNoCarts() {
		assertFalse(Handcarts.admits(1, 0, 0, 10, 50, new FieldWorkConfig.Carts(false, 16, 4, 32)));
		assertFalse(Handcarts.admits(1, 0, 0, 10, 50, new FieldWorkConfig.Carts(true, 16, 0, 32)));
		assertFalse(Handcarts.admits(1, 0, 0, 10, 50, new FieldWorkConfig.Carts(true, 16, 4, 0)));
	}

	@Test
	void shortHaulsAreCarriedInTheArms() {
		FieldWork.setConfig(FieldWorkConfig.DEFAULTS.withCarts(CFG));
		assertFalse(Handcarts.worthACart(8));
		assertTrue(Handcarts.worthACart(16));
		assertTrue(Handcarts.worthACart(120));
		FieldWork.setConfig(FieldWorkConfig.DEFAULTS.withCarts(new FieldWorkConfig.Carts(false, 16, 4, 32)));
		assertFalse(Handcarts.worthACart(120));
	}

	@Test
	void theTagIsNeverSaved() {
		assertTrue(FieldWork.NO_SAVE.contains(Handcarts.TAG));
	}
}
