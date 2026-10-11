package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class TileCodecTest {
	@Test
	void weatherTexelHoldsItsFourValues() {
		int t = TileCodec.weather(0.5F, 1.0F, 0.0F, 0.25F);
		assertEquals(128, TileCodec.r(t));
		assertEquals(255, TileCodec.g(t));
		assertEquals(0, TileCodec.b(t));
		assertEquals(64, TileCodec.a(t));
	}

	@Test
	void farTexelsRoundTrip() {
		int f0 = TileCodec.f0(-12.5F, 1.0F, 0.5F);
		assertEquals(-12.5F, Contract.fromH16(TileCodec.r(f0), TileCodec.g(f0)), 1 / 32.0F);
		assertEquals(255, TileCodec.b(f0));
		assertEquals(128, TileCodec.a(f0));
		int f1 = TileCodec.f1(0.2F, -1, 1, 3, Contract.CELL_PRESENT | Contract.CELL_WATER);
		assertEquals(3, TileCodec.a(f1) >>> 4);
		assertEquals(Contract.CELL_PRESENT | Contract.CELL_WATER, TileCodec.a(f1) & 15);
		assertEquals(0, TileCodec.g(f1));
	}

	@Test
	void undergroundStoresFloorAndRoofAgainstTheBase() {
		int t = TileCodec.underground(10.0F, 52, 75, 60, TileCodec.WATER_RIVER, TileCodec.UG_PRESENT | TileCodec.UG_DRIP);
		assertEquals(20, TileCodec.r(t));
		assertEquals(10.0F, TileCodec.space(TileCodec.r(t)));
		assertEquals(-8, TileCodec.unsigned(TileCodec.g(t)));
		assertEquals(15, TileCodec.unsigned(TileCodec.b(t)));
		assertEquals(TileCodec.WATER_RIVER, TileCodec.a(t) >>> 4);
		assertEquals(TileCodec.UG_PRESENT | TileCodec.UG_DRIP, TileCodec.a(t) & 15);
		// clamped, not wrapped
		int far = TileCodec.underground(500, -1000, 1000, 0, 0, 0);
		assertEquals(255, TileCodec.r(far));
		assertEquals(-128, TileCodec.unsigned(TileCodec.g(far)));
		assertEquals(127, TileCodec.unsigned(TileCodec.b(far)));
	}

	@Test
	void namesMapToStableNumbers() {
		assertEquals(TileCodec.WATER_SEA, TileCodec.waterKind("sea"));
		assertEquals(TileCodec.WATER_NONE, TileCodec.waterKind("none"));
		assertEquals(TileCodec.WATER_NONE, TileCodec.waterKind(null));
		assertEquals(1, TileCodec.realm("wilds"));
		assertEquals(12, TileCodec.realm("brimstone"));
		assertEquals(13, TileCodec.realm("sea"));
		assertEquals(0, TileCodec.realm("somewhere new"));
	}

	@Test
	void temperatureScaleRoundTripsAndMatchesTheLandmarks() {
		assertEquals(0, TileCodec.temperature(0.8F), 1e-6);
		assertEquals(0.9F, TileCodec.temperature(2.0F), 1e-6);
		assertEquals(-1, TileCodec.temperature(-0.7F), 1e-6);
		for (float b = -0.5F; b <= 2.0F; b += 0.1F) {
			assertEquals(b, TileCodec.biomeTemperature(TileCodec.temperature(b)), 1e-4);
		}
		assertEquals(1, TileCodec.dry(1, 0), 1e-6);
		assertEquals(0, TileCodec.dry(0, 0.6F), 1e-6);
	}

	/**
	 * The pack wraps the camera's world position by the tile period and adds {@code void_weatherShift}; that must land
	 * every place in the same texel as its cell of the fronts' frame does, in float maths, far out and late in a world.
	 */
	@Test
	void weatherShiftLandsEveryPlaceInItsFrameCellsTexel() {
		Random random = new Random(42);
		float[] shift = new float[4];
		for (int i = 0; i < 20_000; i++) {
			double x = (random.nextDouble() - 0.5) * 6.0e7;
			double z = (random.nextDouble() - 0.5) * 6.0e7;
			double seconds = random.nextDouble() * 2.0e7;
			TileCodec.weatherShift(seconds, shift);
			// as the pack does it: cameraPositionInt & mask in ints, the rest in floats
			int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
			float fx = (float) (x - ix), fz = (float) (z - iz);
			float nearX = (ix & 32767) + fx + shift[0], nearZ = (iz & 32767) + fz + shift[1];
			float farX = (ix & 262143) + fx + shift[2], farZ = (iz & 262143) + fz + shift[3];
			int wantNearX = Math.floorMod(WeatherTiles.frameCellX(x, seconds, 128), 256);
			int wantNearZ = Math.floorMod(WeatherTiles.frameCellZ(z, seconds, 128), 256);
			int wantFarX = Math.floorMod(WeatherTiles.frameCellX(x, seconds, 1024), 256);
			int wantFarZ = Math.floorMod(WeatherTiles.frameCellZ(z, seconds, 1024), 256);
			// a place within a hair of a cell edge may fall either side in floats: only those are let off
			if (edge(x - dev.voidmc.gloam.air.Air.FRONT_DRIFT * seconds, 128) || edge(z - dev.voidmc.gloam.air.Air.FRONT_DRIFT * 0.2 * seconds, 128)) {
				continue;
			}
			assertEquals(wantNearX, (int) Math.floor(nearX / 128.0F) & 255);
			assertEquals(wantNearZ, (int) Math.floor(nearZ / 128.0F) & 255);
			if (!edge(x - dev.voidmc.gloam.air.Air.FRONT_DRIFT * seconds, 1024) && !edge(z - dev.voidmc.gloam.air.Air.FRONT_DRIFT * 0.2 * seconds, 1024)) {
				assertEquals(wantFarX, (int) Math.floor(farX / 1024.0F) & 255);
				assertEquals(wantFarZ, (int) Math.floor(farZ / 1024.0F) & 255);
			}
		}
	}

	@Test
	void weatherShiftStaysInItsWrap() {
		float[] s = new float[4];
		for (double seconds : new double[]{0, 1, 1e3, 1e6, 3.3e7}) {
			TileCodec.weatherShift(seconds, s);
			assertTrue(s[0] >= 0 && s[0] <= 32768 && s[1] >= 0 && s[1] <= 32768);
			assertTrue(s[2] >= 0 && s[2] <= 262144 && s[3] >= 0 && s[3] <= 262144);
		}
	}

	private static boolean edge(double frame, int size) {
		double f = frame / size - Math.floor(frame / size);
		return f < 1e-3 || f > 1 - 1e-3;
	}
}
