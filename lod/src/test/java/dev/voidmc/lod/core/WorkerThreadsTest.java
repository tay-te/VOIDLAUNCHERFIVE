package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The settings screen changes the worker count while tiles build: no tile may be lost or left half built, and no thread left behind. */
class WorkerThreadsTest {
	private static final TerrainSource HILLS = (x, z, cell, out) -> {
		out.ground = 64 + (float) (40 * Math.sin(x / 300.0) * Math.cos(z / 250.0));
		out.top = 0x60A040;
	};

	private static Set<Thread> workers() {
		return Thread.getAllStackTraces().keySet().stream().filter(t -> t.isAlive() && t.getName().startsWith("VOID LOD worker")).collect(Collectors.toSet());
	}

	@Test
	void theWorkerCountChangesWhileTilesBuild() throws InterruptedException {
		Set<Thread> before = workers();
		LodEngine engine = new LodEngine(4, 512L << 20);
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
		int frames = 0;
		int[] counts = {1, 3, 2, 6, 1, 3};
		do {
			if (frames % 40 == 0 && frames / 40 < counts.length) {
				engine.setThreads(counts[frames / 40]);
			}
			engine.update(gpu, 0, 150, 0, 8192, 150, 771, 5);
			Thread.sleep(2);
			frames++;
		} while ((frames < 40 * counts.length || !engine.idle()) && frames < 10_000);
		assertTrue(engine.idle(), "settles: " + engine.busy());
		assertEquals(3, engine.threads());
		// the workers told to stop finish the tile in hand and leave
		Thread.sleep(600);
		Set<Thread> mine = workers();
		mine.removeAll(before);
		assertEquals(3, mine.size(), "live workers: " + mine);
		System.out.printf("worker threads: %d frames, %d built, %d cached, %d dropped%n", frames, engine.tilesBuilt.get(), engine.tileCount(),
			engine.jobsDropped.get());
		engine.close();
	}
}
