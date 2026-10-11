package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.gloam.air.Air;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeatherTilesTest {
	private static final Air AIR = new Air(8675309L, 8);

	private static WeatherTiles.Inputs at(double x, double z, long gameTime, float rain, LandRaster.Probe land) {
		return new WeatherTiles.Inputs(AIR, gameTime, 0.6F, rain, 0, true, 1, 0, x, z, 0.6F, land, 60_000);
	}

	/** A ridge 400 high running north-south at x 0..3000, on flat land at 70; wet everywhere. */
	private static final LandRaster.Probe RIDGE = (x, z, out) -> {
		out[0] = x >= 0 && x <= 3000 ? 470 : 70;
		out[1] = 0.6F;
	};

	@Test
	void aRebuildFillsBothTilesRoundTheCameraOverFourTicks() {
		WeatherTiles w = new WeatherTiles();
		WeatherTiles.Inputs in = at(12_345, -67_890, 1_234_567, 0.0F, null);
		w.tick(in, Runnable::run);
		byte[] bytes = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
		List<TileStrip> seen = new ArrayList<>();
		for (int tick = 0; tick < 4; tick++) {
			w.queue.drain(bytes, (t, c, l) -> {
			}, 128, seen::add);
		}
		assertEquals(512, seen.size());
		assertTrue(w.ready());
		double s = in.seconds();
		int cx = WeatherTiles.frameCellX(in.cameraX(), s, 128), cz = WeatherTiles.frameCellZ(in.cameraZ(), s, 128);
		TileStrip lastNear = seen.stream().filter(t -> t.tileA() == Contract.W0).reduce((a, b) -> b).orElseThrow();
		assertEquals(cx, lastNear.centreX());
		assertEquals(cz, lastNear.centreZ());
		// the camera's own texel is what the camera's air works out there (no land: no lean)
		double wx = (cx + 0.5) * 128 + Air.FRONT_DRIFT * s, wz = (cz + 0.5) * 128 + Air.FRONT_DRIFT * 0.2 * s;
		float front = AIR.front(wx, wz, in.gameTime(), in.year(), in.downfall());
		int want = TileCodec.weather(front, Air.cloudCover(front, Air.rain(0, front, true)), Air.rain(0, front, true), Air.storm(front, in.year()));
		assertEquals(want, TileStrip.read(bytes, Contract.W0, cx, cz));
	}

	@Test
	void theWindowStepsAsTheFrameDriftsAndRebuildsWhenDue() {
		WeatherTiles w = new WeatherTiles();
		long t = 100_000;
		w.tick(at(0, 0, t, 0, null), Runnable::run);
		int[] lines = {0};
		w.queue.drain(new byte[Contract.WIDTH * Contract.HEIGHT * 4], (a, b, c) -> {
		}, Integer.MAX_VALUE, s -> lines[0]++);
		assertEquals(512, lines[0]);
		// 25 s later the fronts' frame has drifted 150 blocks east under a still camera: W0's window moves a cell west
		lines[0] = 0;
		w.tick(at(0, 0, t + 500, 0, null), Runnable::run);
		List<TileStrip> got = new ArrayList<>();
		w.queue.drain(new byte[Contract.WIDTH * Contract.HEIGHT * 4], (a, b, c) -> {
		}, Integer.MAX_VALUE, got::add);
		// that is a rebuild (more than 200 ticks): both tiles again
		assertEquals(512, got.size());
		// within the rebuild time, a step: one W0 column
		w.tick(at(130, 0, t + 520, 0, null), Runnable::run);
		got.clear();
		w.queue.drain(new byte[Contract.WIDTH * Contract.HEIGHT * 4], (a, b, c) -> {
		}, Integer.MAX_VALUE, got::add);
		assertEquals(1, got.size());
		assertTrue(got.getFirst().column());
		assertEquals(Contract.W0, got.getFirst().tileA());
		// vanilla rain setting in: a rebuild at once
		w.tick(at(130, 0, t + 530, 0.5F, null), Runnable::run);
		got.clear();
		w.queue.drain(new byte[Contract.WIDTH * Contract.HEIGHT * 4], (a, b, c) -> {
		}, Integer.MAX_VALUE, got::add);
		assertEquals(512, got.size());
	}

	@Test
	void theLeeOfARidgeIsDrierAndTheCameraAgrees() {
		WeatherTiles w = new WeatherTiles();
		// the wind blows east: the lee is east of the ridge
		WeatherTiles.Inputs in = at(5_000, 0, 50_000, 0, RIDGE);
		w.tick(in, Runnable::run);
		float lee = w.leanAt(5_000, 0, 1, 0);
		float windward = w.leanAt(-500, 0, 1, 0);
		float far = w.leanAt(-20_000, 0, 1, 0);
		assertTrue(lee < -0.2F, "lee " + lee);
		assertEquals(0, windward, 1e-6, "flat land upwind of the ridge");
		assertEquals(0, far, 1e-6);
		assertTrue(w.leanAt(1_500, 0, 1, 0) > 0, "on the windward ridge");
	}

	@Test
	void maxStormIsInRange() {
		WeatherTiles w = new WeatherTiles();
		w.tick(at(0, 0, 9_999_999, 1.0F, null), Runnable::run);
		assertTrue(w.maxStorm() >= 0 && w.maxStorm() <= 1);
	}
}
