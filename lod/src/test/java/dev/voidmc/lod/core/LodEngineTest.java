package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
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
		out.top = 0x60A040;
		if (out.ground < 63) {
			out.water = 63;
		}
	};

	@Test
	void aMovingCameraAsksForTheLandAheadAndAStillOneDoesNot() throws InterruptedException {
		LodEngine engine = new LodEngine(2, 512L << 20);
		InstantGpu gpu = new InstantGpu();
		gpu.engine = engine;
		engine.reset(HILLS, gpu);
		for (int frame = 0; frame < 40; frame++) {
			engine.update(gpu, 0, 150, 0, 4096, 150, 771, 5);
			Thread.sleep(5);
		}
		assertEquals(0, engine.aheadWanted, "standing still, nothing is built ahead");
		// flying east at about 400 blocks a second (frames 5 ms apart, 2 blocks each)
		double x = 0;
		for (int frame = 0; frame < 80; frame++) {
			x += 2;
			engine.update(gpu, x, 150, 0, 4096, 150, 771, 5);
			Thread.sleep(5);
		}
		assertTrue(engine.aheadWanted > 0, "the land ahead is asked for");
		engine.close();
	}

	@Test
	void drawnTilesHandOverWhatTheyShowOnce() throws InterruptedException {
		TerrainSource biomes = (x, z, cell, out) -> {
			HILLS.sample(x, z, cell, out);
			out.biome = BiomeIds.id(x < 0 ? "minecraft:desert" : "minecraft:plains");
		};
		TileBuilder.captureTerrain = true;
		try {
			LodEngine engine = new LodEngine(2, 512L << 20);
			InstantGpu gpu = new InstantGpu();
			gpu.engine = engine;
			engine.reset(biomes, gpu);
			java.util.List<TileTerrain> got = new java.util.ArrayList<>();
			int frames = 0;
			do {
				engine.update(gpu, 0, 150, 0, 4096, 150, 771, 5);
				got.addAll(engine.drainTerrain());
				Thread.sleep(2);
				frames++;
			} while (!engine.idle() && frames < 3_000);
			for (int i = 0; i < 5; i++) {
				engine.update(gpu, 0, 150, 0, 4096, 150, 771, 5);
				got.addAll(engine.drainTerrain());
			}
			assertTrue(got.size() > 20, "tiles handed over: " + got.size());
			assertEquals(got.size(), got.stream().mapToLong(t -> t.key).distinct().count(), "each once");
			TileTerrain t = got.get(got.size() - 1);
			int c = 10 + 20 * t.size;
			int x = t.minX + 10 * t.cell + t.cell / 2;
			assertEquals(x < 0 ? "minecraft:desert" : "minecraft:plains", BiomeIds.name(t.biomes[c]));
			assertTrue(t.heights[c] > 0 && !t.seen[c]);
			engine.resendTerrain();
			engine.update(gpu, 0, 150, 0, 4096, 150, 771, 5);
			assertTrue(engine.drainTerrain().size() > 20, "a new listener is told of what is drawn");
			engine.close();
		} finally {
			TileBuilder.captureTerrain = false;
		}
	}

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

	private static void settle(LodEngine engine, InstantGpu gpu, double x) throws InterruptedException {
		int frames = 0;
		do {
			engine.update(gpu, x, 150, 0, 4096, 150, 771, 5);
			Thread.sleep(2);
			frames++;
		} while (!engine.idle() && frames < 3_000);
	}

	/** The drawn tile over block (x, z), or -1 if there is a hole there. */
	private static long covering(LongList draw, int x, int z) {
		for (int i = 0; i < draw.size(); i++) {
			long key = draw.get(i);
			int span = TileKey.span(TileKey.level(key));
			if (x >= TileKey.minX(key) && x < TileKey.minX(key) + span && z >= TileKey.minZ(key) && z < TileKey.minZ(key) + span) {
				return key;
			}
		}
		return -1;
	}

	@Test
	void changedLandIsRebuiltBehindWhatIsDrawn() throws InterruptedException {
		AtomicInteger raise = new AtomicInteger();
		TerrainSource land = (x, z, cell, out) -> {
			out.ground = 64 + (x >= 500 && x < 700 && z >= -50 && z < 50 ? raise.get() : 0);
			out.top = 0x60A040;
		};
		LodEngine engine = new LodEngine(2, 512L << 20);
		InstantGpu gpu = new InstantGpu();
		gpu.engine = engine;
		engine.reset(land, gpu);
		settle(engine, gpu, 0);
		long before = engine.tilesBuilt.get();
		int frees = gpu.frees;
		// the land changes (a plateau built at 600, 0) and the engine is told
		raise.set(40);
		engine.invalidate(500, -50, 700, 50);
		long start = System.nanoTime();
		int top = 0;
		while (System.nanoTime() - start < 15_000_000_000L) {
			LongList draw = engine.update(gpu, 0, 150, 0, 4096, 150, 771, 5);
			long key = covering(draw, 600, 0);
			assertTrue(key != -1, "never a hole while the tile is rebuilt");
			top = engine.tile(key).maxY;
			if (top >= 104 && engine.idle()) {
				break;
			}
			Thread.sleep(5);
		}
		System.out.printf("rebuild: %d tiles rebuilt, %d builds, %.1f s%n", engine.rebuilt, engine.tilesBuilt.get() - before,
			(System.nanoTime() - start) / 1e9);
		assertEquals(104, top, "the plateau shows");
		assertTrue(engine.rebuilt > 0);
		assertTrue(engine.tilesBuilt.get() - before <= 2L * engine.rebuilt + 4, "only the tiles over the change rebuild");
		assertEquals(engine.rebuilt, gpu.frees - frees, "every replaced tile is freed");
		engine.close();
	}

	@Test
	void landNobodyKnowsIsNotBuilt() throws InterruptedException {
		TerrainSource unknown = new TerrainSource() {
			@Override
			public void sample(int x, int z, int cell, ColumnSample out) {
				throw new AssertionError("sampled land the source says it does not know");
			}

			@Override
			public boolean covers(int minX, int minZ, int maxX, int maxZ) {
				return false;
			}
		};
		LodEngine engine = new LodEngine(2, 512L << 20);
		InstantGpu gpu = new InstantGpu();
		gpu.engine = engine;
		engine.reset(unknown, gpu);
		settle(engine, gpu, 0);
		assertTrue(engine.idle(), engine.busy());
		assertEquals(0, engine.tilesBuilt.get());
		assertTrue(engine.tilesEmpty.get() > 0);
		assertEquals(0, gpu.uploads);
		engine.close();
	}

	@Test
	void changesUnderTheChunksWaitUntilTheCameraMovesAway() throws InterruptedException {
		AtomicInteger raise = new AtomicInteger();
		TerrainSource land = (x, z, cell, out) -> {
			out.ground = 64 + (x >= 80 && x < 96 && z >= 0 && z < 16 ? raise.get() : 0);
			out.top = 0x60A040;
		};
		LodEngine engine = new LodEngine(2, 512L << 20);
		InstantGpu gpu = new InstantGpu();
		gpu.engine = engine;
		engine.reset(land, gpu);
		int frames = 0;
		do {
			engine.update(gpu, 0, 150, 0, 4096, 100, 771, 5);
			Thread.sleep(2);
		} while (!engine.idle() && ++frames < 3_000);
		// built where vanilla's chunks draw (within 100 blocks): the tile over it is drawn, but none of the change shows
		raise.set(40);
		engine.invalidate(80, 0, 96, 16);
		long start = System.nanoTime();
		while (System.nanoTime() - start < 3_500_000_000L) {
			engine.update(gpu, 0, 150, 0, 4096, 100, 771, 5);
			Thread.sleep(5);
		}
		assertEquals(0, engine.rebuilt, "nothing rebuilt for a change the chunks hide");
		// walk away: now the LOD shows that land, and it is rebuilt
		int top = 0;
		start = System.nanoTime();
		while (System.nanoTime() - start < 15_000_000_000L) {
			LongList draw = engine.update(gpu, -400, 150, 0, 4096, 100, 771, 5);
			long key = covering(draw, 88, 8);
			top = key == -1 ? 0 : engine.tile(key).maxY;
			if (top >= 104 && engine.idle()) {
				break;
			}
			Thread.sleep(5);
		}
		assertEquals(104, top, "the change shows once the camera has moved away");
		engine.close();
	}
}
