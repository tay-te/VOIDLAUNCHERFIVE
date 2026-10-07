package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import org.junit.jupiter.api.Test;

/**
 * The Map page's layout: the islands stay exactly where every other hub page has them, and the
 * window Atlas fills sits between them on the GUI-pixel grid.
 */
class MapLayoutTest {

	@Test
	void theDesignFrameIsReproducedAtOneToOne() {
		// Figma 333:32995 is 1600 x 980 at u = 1: the host rectangle is 1280 x 744 at (160, 110).
		HubLayout l = HubLayout.map(1600, 980, 3);
		assertEquals(1.0, l.u, 1e-9);
		// Snapped to the GUI grid (3 px): within a GUI pixel of the design.
		assertTrue(Math.abs(l.window.x() - 160) < 3 && Math.abs(l.window.y() - 110) < 3, "window at " + l.window);
		assertTrue(Math.abs(l.window.w() - 1280) < 3 && Math.abs(l.window.h() - 744) < 3, "window " + l.window);
		assertEquals(new Rect(565, 24, 471, 50), l.pages);
		assertEquals(new Rect(621, 884, 358, 72), l.strip);
	}

	@Test
	void islandsDoNotMoveBetweenPages() {
		for (int[] w : HubLayoutTest.WINDOWS) {
			for (int gs = 1; gs <= 4; gs++) {
				HubLayout inv = HubLayout.inventory(w[0], w[1], gs);
				HubLayout map = HubLayout.map(w[0], w[1], gs);
				String name = w[0] + "x" + w[1] + " @" + gs;
				assertEquals(inv.u, map.u, 1e-12, name);
				assertEquals(inv.mark, map.mark, name);
				assertEquals(inv.pages, map.pages, name);
				assertEquals(inv.close, map.close, name);
				assertEquals(inv.strip, map.strip, name);
			}
		}
	}

	@Test
	void theWindowLiesBetweenTheIslandsOnTheGuiGrid() {
		for (int[] w : HubLayoutTest.WINDOWS) {
			for (int gs = 1; gs <= 4; gs++) {
				HubLayout l = HubLayout.map(w[0], w[1], gs);
				String name = w[0] + "x" + w[1] + " @" + gs;
				Rect win = l.window;
				assertTrue(new Rect(0, 0, l.fbW, l.fbH).containsRect(win), name + ": window " + win + " off screen");
				assertTrue(win.y() >= l.pages.bottom(), name + ": window under the pages island");
				assertTrue(win.bottom() <= l.strip.y(), name + ": window under the world strip");
				assertTrue(win.w() <= HubLayout.MAP_W * l.u + 1 && win.h() <= HubLayout.MAP_H * l.u + 1, name + ": larger than the design");
				for (int v : new int[] {win.x(), win.y(), win.w(), win.h()}) {
					assertEquals(0, v % gs, name + ": " + win + " off the GUI grid");
				}
				assertTrue(l.cells().isEmpty(), name + ": the map page has no wells");
				assertTrue(l.onSurface(win.centerX(), win.centerY()), name + ": the window is a VOID surface");
			}
		}
	}

	@Test
	void theLayoutMessageCarriesTheWindowSize() {
		HubLayout l = HubLayout.map(1920, 1080, 4);
		String json = l.toJson();
		assertTrue(json.contains("\"kind\":\"map\""), json);
		assertTrue(json.contains("\"slots\":[]"), json);
	}
}
