package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class SampleCacheTest {
	private static final int[][] PALETTES = {{0xA05030, 0xC08040, 0x804020}, {0xE0D0B0, 0x906050}};

	/**
	 * A source that sets every field of a column, by reflection, from a hash of its position and cell size, so a
	 * field the cache forgot to copy (one added to ColumnSample and Samples later, say) shows up as a difference.
	 */
	private static final TerrainSource EVERYTHING = (x, z, cell, out) -> {
		long h = Colors.hash(cell, x, z);
		int n = 0;
		try {
			for (Field f : ColumnSample.class.getFields()) {
				if (Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				long v = Colors.hash(h, n++, 0);
				if (f.getType() == float.class) {
					f.setFloat(out, Colors.unit(v, 0) * 4);
				} else if (f.getType() == int.class) {
					f.setInt(out, (int) (v & 0xFFFFFF));
				} else if (f.getType() == boolean.class) {
					f.setBoolean(out, (v & 7) == 0);
				} else if (f.getType() == int[].class) {
					f.set(out, (v & 3) == 0 ? null : PALETTES[(int) (v >>> 8 & 1)]);
				}
			}
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
		// then the ones the meshers need in range
		out.ground = 64 + (float) (30 * Math.sin(x / 300.0) * Math.cos(z / 200.0)) + Colors.unit(h, 3) * 3;
		out.water = out.ground < 62 ? 63 : Integer.MIN_VALUE;
		out.cover = Colors.unit(h, 11);
		out.canopy = Colors.unit(h, 17) * 15;
		out.crown = 1 + Colors.unit(h, 23) * 3;
		out.bandHeight = 1 + (int) (h >>> 40 & 3);
		out.soilDepth = 1 + (int) (h >>> 44 & 3);
		out.lava = false;
	};

	@Test
	void tilesBuiltFromTheirNeighboursEdgesAreExactlyTheFreshOnes() throws IllegalAccessException {
		for (int level : new int[]{0, 1, TileBuilder.smoothFrom, 5}) {
			SampleCache cache = new SampleCache();
			TileBuilder cached = new TileBuilder(true, cache);
			TileBuilder fresh = new TileBuilder(true);
			// a 3 x 3 block, the middle last, so it has all eight neighbours to copy from; the rest in an order where
			// each finds some of its own
			int[][] order = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}, {1, 1}};
			long before = TileBuilder.SAMPLES_REUSED.get();
			for (int[] at : order) {
				long key = TileKey.of(level, at[0] - 7, at[1] + 3);
				long reused = TileBuilder.SAMPLES_REUSED.get();
				ByteBuffer a = copy(cached.build(EVERYTHING, key));
				Samples s = cached.samples();
				reused = TileBuilder.SAMPLES_REUSED.get() - reused;
				ByteBuffer b = copy(fresh.build(EVERYTHING, key));
				assertEquals(b, a, "the vertices of " + TileKey.toString(key));
				assertSameSamples(fresh.samples(), s, TileKey.toString(key));
				if (at[0] == 1 && at[1] == 1) {
					// everything but the inside, which no neighbour samples
					int inner = s.size - 2 * (s.size - TileKey.TILE);
					assertEquals(s.size * s.size - inner * inner, reused, "the middle tile takes its whole edge from its neighbours");
				}
			}
			assertTrue(TileBuilder.SAMPLES_REUSED.get() > before);
		}
	}

	@Test
	void farBlockyTilesToo() throws IllegalAccessException {
		// every level blocky, as when smooth tiles are off: cell centres with a one-sample border, at any cell size
		int smoothFrom = TileBuilder.smoothFrom;
		TileBuilder.smoothFrom = TileKey.MAX_LEVEL + 1;
		try {
			this.tilesBuiltFromTheirNeighboursEdgesAreExactlyTheFreshOnes();
		} finally {
			TileBuilder.smoothFrom = smoothFrom;
		}
	}

	@Test
	void forgottenAreasAreSampledAgain() {
		SampleCache cache = new SampleCache();
		TileBuilder builder = new TileBuilder(true, cache);
		int level = TileBuilder.smoothFrom + 1;
		builder.build(EVERYTHING, TileKey.of(level, 0, 0));
		cache.forget(0, 0, 1, 1);
		long reused = TileBuilder.SAMPLES_REUSED.get();
		builder.build(EVERYTHING, TileKey.of(level, 1, 0));
		assertEquals(reused, TileBuilder.SAMPLES_REUSED.get(), "nothing reused from a forgotten tile");
		builder.build(EVERYTHING, TileKey.of(level, 2, 0));
		assertTrue(TileBuilder.SAMPLES_REUSED.get() > reused, "but the next one reuses its fresh neighbour");
	}

	@Test
	void anotherSourceOrVersionIsNotReused() {
		SampleCache cache = new SampleCache();
		TileBuilder builder = new TileBuilder(true, cache);
		int level = TileBuilder.smoothFrom;
		builder.build(EVERYTHING, TileKey.of(level, 0, 0));
		TerrainSource other = (x, z, cell, out) -> EVERYTHING.sample(x, z, cell, out);
		long reused = TileBuilder.SAMPLES_REUSED.get();
		builder.build(other, TileKey.of(level, 1, 0));
		assertEquals(reused, TileBuilder.SAMPLES_REUSED.get());
	}

	/** Every per-sample array of the two, over the grid. */
	private static void assertSameSamples(Samples expected, Samples actual, String tile) throws IllegalAccessException {
		assertEquals(expected.size, actual.size);
		int n = expected.size * expected.size;
		for (Field f : Samples.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) || !f.getType().isArray() || f.getName().equals("known")) {
				continue;
			}
			f.setAccessible(true);
			Object e = f.get(expected), a = f.get(actual);
			for (int k = 0; k < n; k++) {
				assertEquals(Array.get(e, k), Array.get(a, k), tile + ": " + f.getName() + "[" + k + "]");
			}
		}
	}

	private static ByteBuffer copy(TileBuilder.Mesh mesh) {
		ByteBuffer out = ByteBuffer.allocate(mesh.bytes());
		out.put(mesh.vertices().duplicate()).flip();
		return out;
	}

	@Test
	void openWaterIsAFlatGridOfTheSamePlane() {
		TerrainSource sea = (x, z, cell, out) -> {
			out.ground = 20;
			out.water = 63;
			out.waterColor = 0x2050C0;
		};
		int level = TileBuilder.smoothFrom + 2;
		TileBuilder.Mesh mesh = new TileBuilder(false).build(sea, TileKey.of(level, 3, -2));
		assertEquals(Smooth.FLAT_VERTICES, mesh.vertexCount());
		assertEquals(Smooth.FLAT_INDICES, mesh.indexCount());
		assertEquals(Smooth.FLAT_FIRST_INDEX, mesh.firstIndex());
		ByteBuffer v = mesh.vertices().duplicate().order(java.nio.ByteOrder.nativeOrder());
		int grid = (TileKey.TILE / Smooth.FLAT_STEP + 1) * (TileKey.TILE / Smooth.FLAT_STEP + 1);
		// y, z, flags, then both colours with their slopes; all but z must match
		byte[] first = new byte[14];
		v.get(2, first);
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < mesh.vertexCount(); i++) {
			int o = i * TileBuilder.VERTEX_BYTES;
			minX = Math.min(minX, v.getShort(o));
			maxX = Math.max(maxX, v.getShort(o));
			minZ = Math.min(minZ, v.getShort(o + 4));
			maxZ = Math.max(maxZ, v.getShort(o + 4));
			assertEquals(TileBuilder.SMOOTH | TileBuilder.WATER, v.getShort(o + 6));
			if (i < grid) {
				// every grid vertex the same: height, flags, colours and slopes
				byte[] rest = new byte[14];
				v.get(o + 2, rest);
				assertEquals(63 * 8, v.getShort(o + 2));
				assertArrayEquals(new byte[]{first[0], first[1]}, new byte[]{rest[0], rest[1]});
				assertArrayEquals(java.util.Arrays.copyOfRange(first, 4, 14), java.util.Arrays.copyOfRange(rest, 4, 14));
			} else {
				assertTrue(v.getShort(o + 2) < 63 * 8, "skirts hang below");
			}
		}
		assertEquals(0, minX);
		assertEquals(TileKey.TILE, maxX);
		assertEquals(0, minZ);
		assertEquals(TileKey.TILE, maxZ);
		// its triangles, from their own part of the shared index buffer, stay in range and the grid faces up
		ByteBuffer idx = TileBuilder.smoothIndices();
		assertEquals((TileBuilder.smoothIndexCount() + Smooth.FLAT_INDICES) * 2, idx.remaining());
		int gridTriangles = (TileKey.TILE / Smooth.FLAT_STEP) * (TileKey.TILE / Smooth.FLAT_STEP) * 2;
		for (int t = 0; t < mesh.indexCount() / 3; t++) {
			int at = (mesh.firstIndex() + t * 3) * 2;
			int a = idx.getShort(at) & 0xFFFF, b = idx.getShort(at + 2) & 0xFFFF, c = idx.getShort(at + 4) & 0xFFFF;
			assertTrue(a < mesh.vertexCount() && b < mesh.vertexCount() && c < mesh.vertexCount());
			if (t < gridTriangles) {
				long[] p0 = {v.getShort(a * 16), v.getShort(a * 16 + 4)}, p1 = {v.getShort(b * 16), v.getShort(b * 16 + 4)},
					p2 = {v.getShort(c * 16), v.getShort(c * 16 + 4)};
				// y up is (z x x) for counter-clockwise from above
				long up = (p1[1] - p0[1]) * (p2[0] - p0[0]) - (p1[0] - p0[0]) * (p2[1] - p0[1]);
				assertTrue(up > 0, "flat triangle " + t + " faces up");
			}
		}
		assertEquals(63, mesh.maxY());
	}

	@Test
	void aShoreIsNotFlat() {
		TerrainSource shore = (x, z, cell, out) -> {
			out.ground = x < 2000 ? 20 : 70;
			out.water = 63;
		};
		TileBuilder.Mesh mesh = new TileBuilder(false).build(shore, TileKey.of(TileBuilder.smoothFrom + 3, 0, 0));
		assertEquals(Smooth.VERTICES, mesh.vertexCount());
		assertEquals(0, mesh.firstIndex());
	}
}
