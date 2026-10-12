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

	/**
	 * The Classes page's unit lands the hand cards' 4 px texel grid on whole device px (a quarter unit), is readable (at
	 * least 0.75 where the framebuffer has room, so GUI scale 1 is not 4 px text) and keeps the shell inside the window.
	 */
	@Test
	void classesUnitIsOnTheTexelGridAndReadable() {
		int[][] sizes = {{854, 480, 1}, {854, 480, 2}, {1280, 720, 3}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 4}, {2560, 1440, 6}, {3440, 1440, 6}, {3840, 2160, 9}};
		for (int[] z : sizes) {
			HubLayout l = HubLayout.classes(z[0], z[1], z[2]);
			String at = z[0] + "x" + z[1] + " gs" + z[2] + ": u " + l.u;
			assertEquals(Math.rint(l.u * 4), l.u * 4, 1e-9, at + " is a quarter");
			assertTrue(l.u >= HubLayout.unit(z[0], z[1], z[2]) - 0.25, at + " is about the shell's or larger");
			assertTrue(l.window.right() <= z[0] && l.window.bottom() <= z[1] && l.strip.bottom() <= z[1], at + " fits");
			if (z[0] >= 1280) {
				assertTrue(l.u >= 0.75, at + " is readable");
			}
		}
		assertEquals(1.25, HubLayout.classes(1920, 1080, 4).u, 1e-9);
		assertEquals(0.75, HubLayout.classes(1920, 1080, 1).u, 1e-9);
		assertEquals(2.5, HubLayout.classes(3840, 2160, 9).u, 1e-9);
	}
}
