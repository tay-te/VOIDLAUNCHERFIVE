package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorCacheTest {
	/** Material ids as names, n12 for id 12: what a session's registry would hand out and take back. */
	private static final ColumnFiles.Names NAMES = new ColumnFiles.Names() {
		@Override
		public String name(int id) {
			return "n" + id;
		}

		@Override
		public int id(String name) {
			return Integer.parseInt(name.substring(1));
		}
	};

	private static final int[] STRATA = {0xC08040, 0xA06030, 0xE0C090};
	private static final int[] STRATA_MATERIALS = {21, 22, 23};

	/** A generator that varies every field, and counts what it is asked. */
	private static final class Counting implements TerrainSource {
		final AtomicInteger calls = new AtomicInteger();

		@Override
		public void sample(int x, int z, int cell, ColumnSample out) {
			this.calls.incrementAndGet();
			out.ground = 60 + Math.floorMod(x * 7 + z * 3, 40) + 0.25F;
			out.water = out.ground < 70 ? 70 : Integer.MIN_VALUE;
			out.lava = Math.floorMod(x + z, 97) == 0;
			out.top = 0x40A040 + Math.floorMod(x, 50);
			out.side = 0x7F6B42;
			out.canopy = Math.floorMod(x ^ z, 3) == 0 ? 9 : 0;
			out.cover = 0.4F;
			out.topMaterial = 5 + Math.floorMod(x, 3);
			out.leavesMaterial = 9;
			out.light = Math.floorMod(x, 11) == 0 ? 7.5F : 0;
			if (Math.floorMod(z, 5) == 0) {
				out.bands = STRATA;
				out.bandMaterials = STRATA_MATERIALS;
				out.bandHeight = 3;
			}
		}
	}

	private static void assertSame(ColumnSample a, ColumnSample b) {
		assertEquals(a.ground, b.ground);
		assertEquals(a.water, b.water);
		assertEquals(a.lava, b.lava);
		assertEquals(a.top, b.top);
		assertEquals(a.side, b.side);
		assertEquals(a.canopy, b.canopy);
		assertEquals(a.cover, b.cover);
		assertEquals(a.topMaterial, b.topMaterial);
		assertEquals(a.leavesMaterial, b.leavesMaterial);
		assertEquals(a.light, b.light);
		assertEquals(a.bandHeight, b.bandHeight);
		if (a.bands == null) {
			assertNull(b.bands);
		} else {
			assertArrayEquals(a.bands, b.bands);
			assertArrayEquals(a.bandMaterials, b.bandMaterials);
		}
	}

	/** Samples a level 3 tile's cell centres through {@code source} as Samples does, border included. */
	private static void sampleTile(TerrainSource source, long key, TerrainSource check) {
		int cell = TileKey.cell(TileKey.level(key));
		int x0 = TileKey.minX(key) + cell / 2, z0 = TileKey.minZ(key) + cell / 2;
		int border = 1, size = TileKey.TILE + 2 * border;
		source.beginTile(key, x0, z0, cell, border, size);
		ColumnSample got = new ColumnSample();
		ColumnSample expected = new ColumnSample();
		for (int j = -border; j < TileKey.TILE + border; j++) {
			for (int i = -border; i < TileKey.TILE + border; i++) {
				got.clear();
				source.sample(x0 + i * cell, z0 + j * cell, cell, got);
				if (check != null) {
					expected.clear();
					check.sample(x0 + i * cell, z0 + j * cell, cell, expected);
					assertSame(expected, got);
				}
			}
		}
		source.endTile();
	}

	@Test
	void pagesComeBackInTheNextSessionExactlyAndAnotherGeneratorsAreDropped(@TempDir Path dir) throws Exception {
		long key = TileKey.of(3, -2, 5);
		Counting generator = new Counting();
		GeneratorCache first = new GeneratorCache(generator, dir, NAMES, 42);
		assertTrue(first.keepsAnswers());
		sampleTile(first, key, new Counting());
		assertEquals(66 * 66, generator.calls.get());
		first.close().get(10, TimeUnit.SECONDS);
		assertFalse(first.keepsAnswers());

		Counting again = new Counting();
		GeneratorCache second = new GeneratorCache(again, dir, NAMES, 42);
		sampleTile(second, key, new Counting());
		assertEquals(0, again.calls.get(), "the whole tile came off the disk");
		assertEquals(1, second.pagesRead.get());

		// another seed or another mod version: its pages are not this generator's answers
		Counting other = new Counting();
		GeneratorCache third = new GeneratorCache(other, dir, NAMES, 43);
		sampleTile(third, key, null);
		assertEquals(66 * 66, other.calls.get());
	}

	@Test
	void samplesOffTheTilesGridOrOutsideATileGoStraightToTheGenerator() {
		Counting generator = new Counting();
		GeneratorCache cache = new GeneratorCache(generator, null, NAMES, 1);
		ColumnSample out = new ColumnSample();
		cache.sample(3, 5, 8, out);
		cache.beginTile(TileKey.of(3, 0, 0), 4, 4, 8, 1, 66);
		cache.sample(5, 4, 8, out);
		cache.sample(4, 4, 16, out);
		cache.sample(4, 4, 8, out);
		cache.sample(4, 4, 8, out);
		cache.endTile();
		assertEquals(4, generator.calls.get(), "only the sample on the tile's grid is kept, and asked once");
	}
}
