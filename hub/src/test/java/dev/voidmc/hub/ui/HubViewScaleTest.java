package dev.voidmc.hub.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.layout.HubLayout;
import org.junit.jupiter.api.Test;

/**
 * Ultralight never gets a device scale under 1 (1.4 paints only (w * s) x (h * s) CSS px then); under 1 the page zooms
 * itself, and device scale times page zoom is always the layout's unit.
 */
class HubViewScaleTest {

	@Test
	void deviceScaleNeverUnderOneAndTheProductIsTheUnit() {
		int[][] windows = {{854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}};
		for (int[] w : windows) {
			for (int gs = 1; gs <= 6; gs++) {
				for (double u : new double[] {HubLayout.inventory(w[0], w[1], gs).u, HubLayout.classes(w[0], w[1], gs).u, HubLayout.hearth(w[0], w[1], gs).u}) {
					double d = HubView.deviceScale(u);
					double z = HubView.pageZoom(u);
					assertTrue(d >= 1.0, "device scale " + d + " for u " + u);
					assertTrue(z > 0 && z <= 1.0, "page zoom " + z + " for u " + u);
					assertEquals(u, d * z, 1e-12, "device scale x page zoom is the unit at " + w[0] + "x" + w[1] + " gs" + gs);
					assertTrue(d == 1.0 || z == 1.0, "only one of them scales");
				}
			}
		}
	}

	@Test
	void guiScaleTwoAt1080pZoomsThePage() {
		double u = HubLayout.inventory(1920, 1080, 2).u;
		assertEquals(2.0 / 3.0, HubView.pageZoom(u), 1e-9);
		assertEquals(1.0, HubView.deviceScale(u), 0);
		assertTrue(HubView.zoomScript(u).contains("window.HUBZ=z") && HubView.zoomScript(u).endsWith("(" + u + ")"));
		assertTrue(HubView.zoomScript(1.0).contains("z===1?''"), "zoom 1 clears the root's zoom");
		assertTrue(HubView.zoomScript(u).contains("classList.toggle('hz',z!==1)"), "the root says it is zoomed (hub.css widens word spaces)");
	}
}
