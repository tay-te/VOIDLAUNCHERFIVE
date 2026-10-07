package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.model.Json;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The shared layout: what the page is told about every well must be exactly what Java hit-tests
 * and draws items into, at every GUI scale and window size.
 */
class HubLayoutTest {

	static final int[][] WINDOWS = {{854, 480}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}};

	static Stream<Arguments> layouts() {
		List<Arguments> out = new ArrayList<>();
		for (int[] w : WINDOWS) {
			for (int gs = 1; gs <= 4; gs++) {
				out.add(Arguments.of(HubLayout.inventory(w[0], w[1], gs), "inventory " + w[0] + "x" + w[1] + " @" + gs));
				out.add(Arguments.of(HubLayout.inventory(w[0], w[1], gs, HubLayout.GearSlots.METTLE), "inventory+gear " + w[0] + "x" + w[1] + " @" + gs));
				out.add(Arguments.of(HubLayout.gear(w[0], w[1], gs, HubLayout.GearSlots.METTLE), "gear " + w[0] + "x" + w[1] + " @" + gs));
				for (int rows = 1; rows <= 6; rows++) {
					int maxScroll = Math.max(0, rows - 3);
					for (int scroll = 0; scroll <= maxScroll; scroll++) {
						out.add(Arguments.of(HubLayout.chest(w[0], w[1], gs, rows, scroll), "chest" + rows + "/" + scroll + " " + w[0] + "x" + w[1] + " @" + gs));
					}
				}
			}
		}
		return out.stream();
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("layouts")
	void everyWellIsInsideTheWindowAndTheWindowInsideTheScreen(HubLayout l, String name) {
		Rect screen = new Rect(0, 0, l.fbW, l.fbH);
		assertTrue(screen.containsRect(l.window), name + ": window " + l.window + " off screen");
		for (HubLayout.Cell c : l.cells()) {
			assertTrue(l.window.containsRect(c.cell()), name + ": slot " + c.slot() + " " + c.cell() + " outside window " + l.window);
			assertTrue(c.cell().containsRect(c.icon()), name + ": icon of slot " + c.slot() + " spills out of its well");
			if (c.cell().w() >= 18) {
				assertEquals(0, c.icon().w() % 16, name + ": icon size must be a whole multiple of 16 px");
			}
			assertEquals(c.icon().w(), c.icon().h());
		}
		assertTrue(screen.containsRect(l.pages) && screen.containsRect(l.mark) && screen.containsRect(l.close) && screen.containsRect(l.strip), name);
		assertFalse(l.window.intersects(l.strip), name + ": window overlaps the world strip");
		assertFalse(l.window.intersects(l.pages), name + ": window overlaps the page island");
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("layouts")
	void wellsNeverOverlapAndAreAllTheSameSize(HubLayout l, String name) {
		List<HubLayout.Cell> cells = l.cells();
		int w = cells.get(0).cell().w();
		for (int i = 0; i < cells.size(); i++) {
			// Mettle's belt and quiver pockets are smaller wells by design (Gear page, worn card).
			boolean pocket = cells.get(i).role() == HubLayout.Role.BELT_POCKET || cells.get(i).role() == HubLayout.Role.QUIVER_POCKET;
			if (!pocket) {
				assertEquals(w, cells.get(i).cell().w(), name);
			}
			assertEquals(cells.get(i).cell().w(), cells.get(i).cell().h(), name);
			for (int j = i + 1; j < cells.size(); j++) {
				assertFalse(cells.get(i).cell().intersects(cells.get(j).cell()),
					name + ": slots " + cells.get(i).slot() + " and " + cells.get(j).slot() + " overlap");
			}
		}
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("layouts")
	void hitTestingFindsEveryWellAndNothingBetweenThem(HubLayout l, String name) {
		for (HubLayout.Cell c : l.cells()) {
			Rect r = c.cell();
			assertSame(c, l.cellAt(r.centerX(), r.centerY()), name + ": centre of slot " + c.slot());
			assertSame(c, l.cellAt(r.x(), r.y()), name + ": top-left pixel of slot " + c.slot());
			assertSame(c, l.cellAt(r.right() - 0.01, r.bottom() - 0.01), name + ": bottom-right pixel of slot " + c.slot());
			HubLayout.Cell right = l.cellAt(r.right() + 0.5, r.centerY());
			assertTrue(right == null || right != c, name);
			// The gap to the right of a well belongs to no well (wells are 46 of a 52 pitch).
			if (l.u >= 0.5) {
				assertNull(l.cellAt(r.right() + 1.5 * l.u, r.centerY()), name + ": gap right of slot " + c.slot() + " hit a well");
			}
			assertTrue(l.onSurface(r.centerX(), r.centerY()));
		}
		assertNull(l.cellAt(-5, -5));
		assertFalse(l.onSurface(1, l.fbH / 2.0), name + ": the left screen edge is outside every surface");
	}

	/**
	 * The page positions each well from the layout message (CSS px); Ultralight maps CSS px to
	 * device px by {@code u}. That must land on exactly the rect Java hit-tests.
	 */
	@ParameterizedTest(name = "{1}")
	@MethodSource("layouts")
	@SuppressWarnings("unchecked")
	void thePagesCopyOfEveryWellIsTheRectJavaHitTests(HubLayout l, String name) {
		Map<String, Object> msg = Json.parseObject(l.toJson());
		assertEquals("layout", msg.get("type"));
		double u = (Double) msg.get("u");
		assertEquals(l.u, u, 1e-6);
		List<Object> slots = (List<Object>) msg.get("slots");
		assertEquals(l.cells().size(), slots.size(), name);
		Set<Integer> seen = new HashSet<>();
		for (Object o : slots) {
			Map<String, Object> s = (Map<String, Object>) o;
			int slot = ((Double) s.get("i")).intValue();
			assertTrue(seen.add(slot), name + ": slot " + slot + " sent twice");
			HubLayout.Cell c = l.cellForSlot(slot);
			assertNotNull(c, name);
			assertEquals(c.role().css(), s.get("r"));
			assertEquals(c.cell().x(), Math.round((Double) s.get("x") * u), name + " x of " + slot);
			assertEquals(c.cell().y(), Math.round((Double) s.get("y") * u), name + " y of " + slot);
			assertEquals(c.cell().w(), Math.round((Double) s.get("w") * u), name + " w of " + slot);
			assertEquals(c.cell().h(), Math.round((Double) s.get("h") * u), name + " h of " + slot);
			// Hit-testing the page's own centre of the well finds the same slot.
			double cx = ((Double) s.get("x") + (Double) s.get("w") / 2) * u;
			double cy = ((Double) s.get("y") + (Double) s.get("h") / 2) * u;
			assertEquals(slot, l.cellAt(cx, cy).slot(), name);
		}
		Map<String, Object> win = (Map<String, Object>) msg.get("win");
		assertEquals(l.window.x(), Math.round((Double) win.get("x") * u));
		assertEquals(l.window.y(), Math.round((Double) win.get("y") * u));
	}

	@Test
	void inventoryHasEveryInventoryMenuSlotOnce() {
		HubLayout l = HubLayout.inventory(1920, 1080, 3);
		Set<Integer> slots = new HashSet<>();
		for (HubLayout.Cell c : l.cells()) {
			slots.add(c.slot());
		}
		assertEquals(46, l.cells().size());
		for (int i = 0; i <= 45; i++) {
			assertTrue(slots.contains(i), "InventoryMenu slot " + i + " missing");
		}
		assertEquals(HubLayout.Role.RESULT, l.cellForSlot(0).role());
		assertEquals(HubLayout.Role.HEAD, l.cellForSlot(5).role());
		assertEquals(HubLayout.Role.FEET, l.cellForSlot(8).role());
		assertEquals(HubLayout.Role.OFFHAND, l.cellForSlot(45).role());
		assertEquals(HubLayout.Role.HOTBAR, l.cellForSlot(36).role());
		assertEquals(0, l.cellForSlot(36).ordinal());
	}

	@Test
	void chestSlotsFollowChestMenuNumbering() {
		for (int rows = 1; rows <= 6; rows++) {
			HubLayout l = HubLayout.chest(1920, 1080, 3, rows, 0);
			int visibleContainer = Math.min(rows, 3) * 9;
			assertEquals(visibleContainer + 36, l.cells().size());
			assertEquals(HubLayout.Role.STORAGE, l.cellForSlot(rows * 9).role());
			assertEquals(HubLayout.Role.HOTBAR, l.cellForSlot(rows * 9 + 27).role());
			assertEquals(Math.max(0, rows - 3), l.maxScroll);
		}
		HubLayout scrolled = HubLayout.chest(1920, 1080, 3, 6, 3);
		assertNull(scrolled.cellForSlot(0), "row 0 is scrolled out of view");
		assertNotNull(scrolled.cellForSlot(27), "row 3 is the first visible row");
		assertEquals(scrolled.cellForSlot(27).cell(), HubLayout.chest(1920, 1080, 3, 6, 0).cellForSlot(0).cell());
		assertEquals(3, HubLayout.chest(1920, 1080, 3, 6, 99).scrollRow, "scroll clamps to the last row");
	}

	@Test
	void guiScaleThreeDrawsTheDesignOneToOne() {
		HubLayout l = HubLayout.inventory(1920, 1080, 3);
		assertEquals(1.0, l.u, 1e-9);
		Rect hotbar0 = l.cellForSlot(36).cell();
		assertEquals(new Rect(l.window.x() + 28, l.window.y() + 516, 46, 46), hotbar0);
		Rect craft0 = l.cellForSlot(1).cell();
		assertEquals(new Rect(l.window.x() + 318, l.window.y() + 118, 46, 46), craft0);
		assertEquals(new Rect(l.window.x() + 444, l.window.y() + 144, 46, 46), l.cellForSlot(0).cell());
		assertEquals(32, l.iconPx, "28/46 of a 46 px well rounds to 32 px (two device px per item texel)");
		assertEquals(987, l.window.w());
	}

	@Test
	void unitFollowsGuiScaleAndShrinksToFit() {
		assertEquals(2.0 / 3.0, HubLayout.unit(1920, 1080, 2), 1e-9);
		assertEquals(20.0 / 46.0, HubLayout.unit(1920, 1080, 1), 1e-9, "scale 1 would make 15 px wells; 20 px is the floor");
		// Scale 4 would be 1.333; 1080 px of height only fits 1080 / 854.
		assertEquals(1080.0 / 854.0, HubLayout.unit(1920, 1080, 4), 1e-9);
		assertTrue(HubLayout.unit(854, 480, 4) < 0.6);
	}

	@Test
	void iconSizesAreWholeTexelMultiples() {
		assertEquals(16, HubLayout.iconSize(20, 0.61));
		assertEquals(16, HubLayout.iconSize(31, 0.9), "48 would spill a 31 px well");
		assertEquals(13, HubLayout.iconSize(15, 0.61), "tiny windows shrink the item rather than spill");
		assertEquals(32, HubLayout.iconSize(46, 0.61));
		assertEquals(48, HubLayout.iconSize(77.6, 0.61));
		assertEquals(64, HubLayout.fitIcon(64, 72), "the hovered card's 64 px icon in its 72 px well");
		assertEquals(48, HubLayout.fitIcon(64, 60));
	}
}
