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
		// Figma 375:36239 is 1600 x 980 at u = 1: the window is 1280 x 744 at (160, 110).
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
				for (int v : new int[] {win.x(), win.y(), win.w(), win.h()}) {
					assertEquals(0, v % gs, name + ": " + win + " off the GUI grid");
				}
				assertTrue(l.cells().isEmpty(), name + ": the map page has no wells");
				// Atlas, measuring this rectangle, lays its page out at the unit the layout chose: the
				// 1280 x 744 page, not a stretched one (Figma 375:36239).
				double a = HubLayout.atlasUnit(l.fbW, l.fbH, l.u);
				double fit = Math.min(win.w() / (double) HubLayout.MAP_W, win.h() / (double) HubLayout.MAP_H);
				if (fit >= HubLayout.ATLAS_UNITS[0]) {
					assertEquals(a, pick(fit), 1e-9, name + ": Atlas would draw at " + pick(fit) + ", not " + a);
					assertTrue(win.w() - HubLayout.MAP_W * a < gs && win.h() - HubLayout.MAP_H * a < gs, name + ": " + win + " is not 1280 x 744 at " + a);
				}
				assertTrue(a <= l.u + 1e-9 || a == HubLayout.ATLAS_UNITS[0], name + ": the map outgrows the islands (" + a + " > " + l.u + ")");
				assertTrue(l.onSurface(win.centerX(), win.centerY()), name + ": the window is a VOID surface");
			}
		}
	}

	static double pick(double fit) {
		double best = HubLayout.ATLAS_UNITS[0];
		for (double u : HubLayout.ATLAS_UNITS) {
			if (u <= fit + 1e-4) {
				best = u;
			}
		}
		return best;
	}

	@Test
	void atTenEightyTheMapIsAtlasAtOneToOne() {
		HubLayout l = HubLayout.map(1920, 1080, 4);
		assertEquals(1.0, HubLayout.atlasUnit(1920, 1080, l.u), 1e-9);
		assertEquals(1280, l.window.w());
		assertEquals(744, l.window.h());
		HubLayout big = HubLayout.map(2560, 1440, 4);
		assertEquals(1.25, HubLayout.atlasUnit(2560, 1440, big.u), 1e-9);
		assertEquals(new Rect(big.window.x(), big.window.y(), 1600, 932), big.window);
	}

	@Test
	void theLayoutMessageCarriesTheWindowSize() {
		HubLayout l = HubLayout.map(1920, 1080, 4);
		String json = l.toJson();
		assertTrue(json.contains("\"kind\":\"map\""), json);
		assertTrue(json.contains("\"slots\":[]"), json);
	}
}
