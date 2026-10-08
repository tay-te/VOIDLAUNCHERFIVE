package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.layout.HubLayout;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** VOID Mettle's pages: every gear slot has a well where the design puts it, and Classes has none. */
class MettleLayoutTest {

	static final HubLayout.GearSlots G = HubLayout.GearSlots.METTLE;

	@Test
	void inventoryAddsTheThreeGearWellsAboveTheOffhand() {
		HubLayout l = HubLayout.inventory(1920, 1080, 3, G);
		HubLayout.Cell back = l.cellForSlot(G.back());
		HubLayout.Cell offhand = l.cellForSlot(45);
		assertNotNull(back);
		assertEquals(offhand.cell().x(), back.cell().x(), "same column as the offhand");
		assertTrue(back.cell().y() < l.cellForSlot(G.belt()).cell().y() && l.cellForSlot(G.quiver()).cell().y() < offhand.cell().y());
		assertNull(HubLayout.inventory(1920, 1080, 3).cellForSlot(G.back()), "no gear wells without Mettle");
	}

	@Test
	void gearPageHasEveryPocketOnceAndTheInventory() {
		HubLayout l = HubLayout.gear(1920, 1080, 3, G);
		Set<Integer> slots = new HashSet<>();
		l.cells().forEach(c -> assertTrue(slots.add(c.slot()), "slot " + c.slot() + " twice"));
		for (int i = G.packFirst(); i < G.packFirst() + G.packSlots(); i++) {
			assertTrue(slots.contains(i), "pack pocket " + i);
		}
		for (int i = G.beltFirst(); i < G.quiverFirst() + G.quiverSlots(); i++) {
			assertTrue(slots.contains(i), "belt/quiver pocket " + i);
		}
		for (int i = 9; i < 45; i++) {
			assertTrue(slots.contains(i), "inventory slot " + i);
		}
		assertTrue(slots.contains(G.back()) && slots.contains(G.belt()) && slots.contains(G.quiver()));
		assertEquals(27 + 6 + 4 + 36 + 3, l.cells().size());
		assertNull(l.detailIcon, "the Gear page has no Held card");
		assertNull(l.player);
	}

	@Test
	void classesPageDrawsNoSlots() {
		HubLayout l = HubLayout.classes(1920, 1080, 3);
		assertEquals(0, l.cells().size());
		assertNull(l.detailIcon);
		assertTrue(l.toJson().contains("\"kind\":\"classes\""));
	}
}
