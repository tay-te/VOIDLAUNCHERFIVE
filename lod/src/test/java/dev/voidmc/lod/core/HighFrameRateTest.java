package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/** The engine at two thousand frames a second, the renderer's fades included: a still view must settle. */
class HighFrameRateTest {
	private static final TerrainSource HILLS = (x, z, cell, out) -> {
		out.ground = 64 + (float) (40 * Math.sin(x / 300.0) * Math.cos(z / 250.0));
		out.top = 0x60A040;
		if (out.ground < 63) {
			out.water = 63;
		}
	};

	@Test
	void aStillViewSettlesAtThousandsOfFramesASecond() throws InterruptedException {
		LodEngine engine = new LodEngine(4, 512L << 20);
		Fades fades = new Fades();
		LodEngine.Gpu gpu = new LodEngine.Gpu() {
			@Override
			public boolean upload(Tile tile, ByteBuffer vertices) {
				tile.gpu = this;
				engine.uploaded(tile);
				return true;
			}

			@Override
			public void free(Tile tile) {
				tile.gpu = null;
			}
		};
		engine.reset(HILLS, gpu);
		// eight seconds of frames, then as long as the last builds take on a busy machine (at most thirty more): a view
		// that never settles keeps building or queueing forever, one on a loaded machine just finishes late
		long churn = System.nanoTime() + 8_000_000_000L;
		long end = churn + 30_000_000_000L;
		long frames = 0;
		while (System.nanoTime() < end && (System.nanoTime() < churn || !engine.idle() || engine.queued() != 0)) {
			LongList draw = engine.update(gpu, 0, 150, 0, 8192, 104, 771, 5);
			fades.update(draw, System.nanoTime(), engine::ready);
			for (int i = 0; i < fades.size(); i++) {
				if (fades.fade(i) < 0) {
					engine.touch(fades.key(i));
				}
			}
			frames++;
			Thread.sleep(0, 400_000);
		}
		long built = engine.tilesBuilt.get();
		System.out.printf("high frame rate: %d frames, %d built, %d cached, %d queued, idle %s (%s); dropped %d, discarded %d, evicted %d%n", frames, built,
			engine.tileCount(), engine.queued(), engine.idle(), engine.busy(), engine.jobsDropped.get(), engine.resultsDiscarded, engine.evictedReady);
		assertTrue(engine.idle() && engine.queued() == 0, "settles: " + engine.busy());
		assertTrue(built <= engine.tileCount() + 16, "no tile built twice: built " + built + ", cached " + engine.tileCount());
		engine.close();
	}
}
