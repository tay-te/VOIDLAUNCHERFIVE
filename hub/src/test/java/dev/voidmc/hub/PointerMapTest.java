package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.PointerMap;
import dev.voidmc.hub.layout.Rect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The gesture path: a pointer on a VOID well must land, in vanilla's own geometry, on exactly
 * that menu slot — so vanilla's click logic (pickup, split, shift, drag, collect, outside drop)
 * acts on the slot the player sees. Checked against a copy of vanilla's hover rule and the real
 * vanilla slot positions of InventoryMenu and ChestMenu.
 */
class PointerMapTest {

	/** InventoryMenu's slot positions, as vanilla adds them (176 x 166 panel). */
	static PointerMap.Geometry inventoryGeometry(int guiW, int guiH) {
		int[] x = new int[46];
		int[] y = new int[46];
		x[0] = 154;
		y[0] = 28;
		for (int i = 0; i < 4; i++) {
			x[1 + i] = 98 + (i % 2) * 18;
			y[1 + i] = 18 + (i / 2) * 18;
		}
		for (int i = 0; i < 4; i++) {
			x[5 + i] = 8;
			y[5 + i] = 8 + i * 18;
		}
		for (int i = 0; i < 27; i++) {
			x[9 + i] = 8 + (i % 9) * 18;
			y[9 + i] = 84 + (i / 9) * 18;
		}
		for (int i = 0; i < 9; i++) {
			x[36 + i] = 8 + i * 18;
			y[36 + i] = 142;
		}
		x[45] = 77;
		y[45] = 62;
		return new PointerMap.Geometry((guiW - 176) / 2, (guiH - 166) / 2, 176, 166, x, y);
	}

	/** ChestMenu's slot positions for {@code rows} rows (176 x 114 + rows*18 panel). */
	static PointerMap.Geometry chestGeometry(int rows, int guiW, int guiH) {
		int n = rows * 9 + 36;
		int[] x = new int[n];
		int[] y = new int[n];
		for (int i = 0; i < rows * 9; i++) {
			x[i] = 8 + (i % 9) * 18;
			y[i] = 18 + (i / 9) * 18;
		}
		int top = 18 + rows * 18 + 13;
		for (int i = 0; i < 27; i++) {
			x[rows * 9 + i] = 8 + (i % 9) * 18;
			y[rows * 9 + i] = top + (i / 9) * 18;
		}
		for (int i = 0; i < 9; i++) {
			x[rows * 9 + 27 + i] = 8 + i * 18;
			y[rows * 9 + 27 + i] = top + 58;
		}
		int h = 114 + rows * 18;
		return new PointerMap.Geometry((guiW - 176) / 2, (guiH - h) / 2, 176, h, x, y);
	}

	@ParameterizedTest(name = "{0}x{1} @{2}")
	@CsvSource({"854,480,1", "854,480,2", "1280,720,2", "1920,1080,1", "1920,1080,2", "1920,1080,3", "1920,1080,4", "2560,1440,4", "3840,2160,4"})
	void everyInventoryWellReachesItsOwnVanillaSlot(int w, int h, int gs) {
		HubLayout l = HubLayout.inventory(w, h, gs);
		PointerMap.Geometry g = inventoryGeometry((int) Math.ceil(w / (double) gs), (int) Math.ceil(h / (double) gs));
		for (HubLayout.Cell c : l.cells()) {
			// Anywhere in the well, not just its centre.
			for (double[] p : new double[][] {{c.cell().centerX(), c.cell().centerY()}, {c.cell().x() + 0.5, c.cell().y() + 0.5},
				{c.cell().right() - 0.5, c.cell().bottom() - 0.5}}) {
				PointerMap.Target t = PointerMap.resolve(l, p[0], p[1]);
				assertInstanceOf(PointerMap.OnSlot.class, t);
				double[] v = PointerMap.vanillaPoint(t, g);
				assertEquals(c.slot(), PointerMap.vanillaSlotAt(g, v[0], v[1]), "well " + c.slot());
				assertFalse(PointerMap.vanillaOutside(g, v[0], v[1]), "a slot click must never count as outside");
			}
		}
	}

	@ParameterizedTest(name = "rows {0}")
	@CsvSource({"1", "2", "3", "4", "5", "6"})
	void everyChestWellReachesItsOwnVanillaSlot(int rows) {
		for (int scroll = 0; scroll <= Math.max(0, rows - 3); scroll++) {
			HubLayout l = HubLayout.chest(1920, 1080, 3, rows, scroll);
			PointerMap.Geometry g = chestGeometry(rows, 640, 360);
			for (HubLayout.Cell c : l.cells()) {
				double[] v = PointerMap.vanillaPoint(PointerMap.resolve(l, c.cell().centerX(), c.cell().centerY()), g);
				assertEquals(c.slot(), PointerMap.vanillaSlotAt(g, v[0], v[1]), "rows " + rows + " scroll " + scroll + " well " + c.slot());
			}
		}
	}

	@Test
	void chromeIsInsideVanillasPanelButOnNoSlot() {
		HubLayout l = HubLayout.inventory(1920, 1080, 3);
		PointerMap.Geometry g = inventoryGeometry(640, 360);
		// The window background between the segments and the first row of wells.
		Rect w = l.window;
		PointerMap.Target t = PointerMap.resolve(l, w.x() + 500 * l.u, w.y() + 20 * l.u);
		assertInstanceOf(PointerMap.OnChrome.class, t);
		double[] v = PointerMap.vanillaPoint(t, g);
		assertEquals(-1, PointerMap.vanillaSlotAt(g, v[0], v[1]));
		assertFalse(PointerMap.vanillaOutside(g, v[0], v[1]), "chrome clicks must not drop the carried stack");
		// The islands are surfaces too: clicking the close button must not throw items.
		assertInstanceOf(PointerMap.OnChrome.class, PointerMap.resolve(l, l.close.centerX(), l.close.centerY()));
		assertInstanceOf(PointerMap.OnChrome.class, PointerMap.resolve(l, l.strip.centerX(), l.strip.centerY()));
		// So is the gap between two wells.
		Rect a = l.cellForSlot(36).cell();
		assertInstanceOf(PointerMap.OnChrome.class, PointerMap.resolve(l, a.right() + 2, a.centerY()));
	}

	@Test
	void offEverySurfaceIsVanillasOutside() {
		HubLayout l = HubLayout.inventory(1920, 1080, 3);
		PointerMap.Geometry g = inventoryGeometry(640, 360);
		PointerMap.Target t = PointerMap.resolve(l, 5, 540);
		assertInstanceOf(PointerMap.Outside.class, t);
		double[] v = PointerMap.vanillaPoint(t, g);
		assertTrue(PointerMap.vanillaOutside(g, v[0], v[1]), "outside the VOID shell drops the stack, as outside vanilla's panel does");
		assertEquals(-1, PointerMap.vanillaSlotAt(g, v[0], v[1]));
	}

	@Test
	void deadPointIsOnNoSlotForEveryMenu() {
		for (PointerMap.Geometry g : new PointerMap.Geometry[] {inventoryGeometry(640, 360), chestGeometry(3, 640, 360), chestGeometry(6, 640, 360)}) {
			int[] d = PointerMap.deadPoint(g);
			assertEquals(-1, PointerMap.vanillaSlotAt(g, g.leftPos() + d[0] + 0.5, g.topPos() + d[1] + 0.5));
		}
	}
}
