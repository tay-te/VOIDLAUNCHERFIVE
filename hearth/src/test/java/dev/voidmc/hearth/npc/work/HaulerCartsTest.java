package dev.voidmc.hearth.npc.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import org.junit.jupiter.api.Test;

/** The handcart's bed shows what the hauler moves: logs from the logging camp to the sawmill (§6.1). */
class HaulerCartsTest {
	@Test
	void everyKindOfLoadHasABed() {
		for (HaulerWork.Kind k : HaulerWork.Kind.values()) {
			assertNotNull(HaulerWork.cartLoad(k), k.name());
			assertTrue(HaulerWork.cartLoad(k) != CartEntity.Load.NONE, k + " shows a load");
		}
	}

	@Test
	void theLoggingCampsLogsGoToTheSawmillAsLogs() {
		HaulerWork.Kind out = HaulerWork.produces("extract/logging_camp");
		assertEquals(HaulerWork.Kind.LOGS, out);
		assertTrue(HaulerWork.wants("craft/sawmill").contains(out));
		assertEquals(CartEntity.Load.LOGS, HaulerWork.cartLoad(out));
		assertEquals(CartEntity.Load.STONE, HaulerWork.cartLoad(HaulerWork.produces("extract/quarry")));
	}
}
