package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class LodEngineTest {
	/** A GPU that takes every upload at once. */
	private static final class InstantGpu implements LodEngine.Gpu {
		LodEngine engine;
		int uploads;
		int frees;

		@Override
		public boolean upload(Tile tile, ByteBuffer vertices) {
			this.uploads++;
			tile.gpu = this;
			this.engine.uploaded(tile);
			return true;
		}

		@Override
		public void free(Tile tile) {
			this.frees++;
			tile.gpu = null;
		}
	}

	private static final TerrainSource HILLS = (x, z, cell, out) -> {
		out.ground = 64 + (int) (40 * Math.sin(x / 300.0) * Math.cos(z / 250.0));
		out.groundColor = 0x60A040;
		if (out.ground < 63) {
			out.water = 63;
		}
	};

	@Test
	void aStillCameraBuildsEachTileOnce() throws InterruptedException {
		LodEngine engine = new LodEngine(2, 512L << 20);
		InstantGpu gpu = new InstantGpu();
		gpu.engine = engine;
		engine.reset(HILLS, gpu);
		int frames = 0;
		do {
			engine.update(gpu, 0, 150, 0, 4096, 150, 771, 5);
			Thread.sleep(2);
			frames++;
		} while (!engine.idle() && frames < 3_000);
		engine.update(gpu, 0, 150, 0, 4096, 150, 771, 5);
		long built = engine.tilesBuilt.get();
		int cached = engine.tileCount();
		System.out.printf("still camera: %d frames, %d built, %d cached, %d uploads, %d frees%n", frames, built, cached, gpu.uploads, gpu.frees);
		assertTrue(engine.idle(), "settles: " + engine.busy());
		assertTrue(built <= cached + 8, "no tile built twice: built " + built + ", cached " + cached);
		engine.close();
	}

	@Test
	void aMovingCameraOnlyBuildsWhatComesIntoView() throws InterruptedException {
		LodEngine engine = new LodEngine(2, 512L << 20);
		InstantGpu gpu = new InstantGpu();
		gpu.engine = engine;
		engine.reset(HILLS, gpu);
		// settle, then walk 512 blocks east and settle again
		for (int step = 0; step <= 8; step++) {
			int frames = 0;
			do {
				engine.update(gpu, step * 64, 150, 0, 4096, 150, 771, 5);
				Thread.sleep(2);
				frames++;
			} while (!engine.idle() && frames < 3_000);
		}
		long built = engine.tilesBuilt.get();
		System.out.printf("moving camera: %d built, %d cached, %d frees%n", built, engine.tileCount(), gpu.frees);
		// a 512-block walk changes a fraction of the view, not all of it nine times over
		assertTrue(built < 3L * engine.tileCount(), "built " + built + " for " + engine.tileCount() + " cached");
		engine.close();
	}
}
