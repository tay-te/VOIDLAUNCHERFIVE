package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RainShadowTest {
	@Test
	void flatLandLeansNowhere() {
		assertEquals(0, RainShadow.lean(70, new float[]{70, 70, 70}), 1e-6);
	}

	@Test
	void aHighRidgeUpwindCastsAFullShadow() {
		assertEquals(-RainShadow.SHADOW, RainShadow.lean(70, new float[]{400, 70, 70}), 1e-6);
		// further off it counts for less
		float near = RainShadow.lean(70, new float[]{170, 70, 70}), farther = RainShadow.lean(70, new float[]{70, 70, 170});
		assertTrue(near < farther && farther < 0);
	}

	@Test
	void aWindwardSlopeIsCloudier() {
		float lift = RainShadow.lean(300, new float[]{100, 100, 100});
		assertEquals(RainShadow.LIFT, lift, 1e-6);
	}

	@Test
	void unknownLandLeansNowhere() {
		assertEquals(0, RainShadow.lean(Float.NaN, new float[]{400, 400, 400}), 1e-6);
		assertEquals(0, RainShadow.lean(70, new float[]{Float.NaN, Float.NaN, Float.NaN}), 1e-6);
		assertEquals(0, RainShadow.leanAt(null, 0, 0, 1, 0), 1e-6);
	}

	@Test
	void theFrontStaysInRange() {
		assertEquals(0, RainShadow.front(0.1F, -0.25F), 1e-6);
		assertEquals(1, RainShadow.front(0.97F, 0.08F), 1e-6);
	}

	@Test
	void theLatticeAsksEachPointOnce() {
		LandRaster.Lattice lattice = new LandRaster.Lattice(512);
		int[] asked = {0};
		LandRaster.Probe probe = (x, z, out) -> {
			asked[0]++;
			out[0] = x * 0.01F;
			out[1] = 0.5F;
		};
		LandRaster a = lattice.raster(probe, -5000, -5000, 5000, 5000);
		int first = asked[0];
		LandRaster b = lattice.raster(probe, -4000, -4000, 6000, 6000);
		assertTrue(asked[0] - first < first / 3, "only the new strip is asked");
		assertEquals(10.24F, a.altitude(1024, 77), 1e-3);
		assertEquals(b.altitude(300, 300), a.altitude(300, 300), 1e-4);
		assertTrue(Float.isNaN(a.altitude(1e6, 0)));
	}
}
