package dev.voidmc.gloam.far;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Climate;
import dev.voidmc.gloam.air.Season;
import dev.voidmc.gloam.air.Wind;
import dev.voidmc.gloam.bridge.AirReadout;
import org.junit.jupiter.api.Test;

/** What the bridge works out for the pack agrees with what Gloam's own far air did, while both are there. */
class BridgeAgreementTest {
	@Test
	void cloudLayersAreCloudGridsLayers() {
		for (int maxY : new int[]{320, 383, 448, 600, 640, 1024}) {
			for (double lift : new double[]{-1, 0, 120}) {
				CloudGrid.Layers l = CloudGrid.Layers.of(192.33F, maxY, lift);
				assertArrayEquals(new int[]{l.low(), l.main(), l.high(), l.towerTop()}, AirReadout.cloudLayers(192.33F, maxY, lift), "maxY " + maxY);
			}
		}
	}

	@Test
	void farThicknessIsTheHazeVoidLodWasGiven() {
		for (float humid = 0; humid <= 1; humid += 0.25F) {
			for (float warmth = -1; warmth <= 1; warmth += 0.5F) {
				for (float mist = 0; mist <= 1; mist += 0.5F) {
					AirSample s = new AirSample(0.3F, Season.SUMMER, warmth, 7, Wind.CALM, 0.5F, 0.4F, 0.2F, 0, mist, 80, 0.3F,
						new Climate(0.8F, humid, 0.2F, 0), 0);
					assertEquals(FarAir.hazeThickness(s), AirReadout.farThickness(humid, warmth, s.overcast(), s.wetness(), s.cloudCover(), mist, 1.0), 1e-6);
				}
			}
		}
	}
}
