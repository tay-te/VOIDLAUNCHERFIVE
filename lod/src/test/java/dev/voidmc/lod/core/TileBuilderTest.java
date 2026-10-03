package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TileBuilderTest {
	private static TerrainSource flat(int y) {
		return (x, z, cell, out) -> {
			out.ground = y;
			out.top = 0x40A040;
			out.side = 0x7F6B42;
		};
	}

	private static TileBuilder builder() {
		return new TileBuilder(false);
	}

	@Test
	void flatGroundIsOneTopQuadPlusBandedSkirts() {
		TileBuilder.Mesh mesh = builder().build(flat(70), TileKey.of(0, 3, -4));
		assertFalse(mesh.smooth());
		Map<Integer, Integer> faces = faces(mesh);
		assertEquals(1, faces.getOrDefault(0, 0), "one merged top");
		// each border hangs one merged skirt, in three bands: the grass side, the soil, the rock
		for (int face = 2; face <= 5; face++) {
			assertEquals(3, faces.getOrDefault(face, 0), "skirt bands on face " + face);
		}
		assertEquals(70, mesh.maxY());
	}

	@Test
	void aStepIsAWallOfItsBandsWithOcclusionAtItsFoot() {
		// x < 32 cells stands at 80, the rest at 64: one wall facing +x along the whole tile
		TerrainSource step = (x, z, cell, out) -> {
			out.ground = x < 32 ? 80 : 64;
			out.top = 0x808080;
		};
		TileBuilder.Mesh mesh = builder().build(step, TileKey.of(0, 0, 0));
		Map<Integer, Integer> faces = faces(mesh);
		// the upper half merges into one top; the row at the wall's foot is darkened corner by corner, so each
		// of its cells is its own quad; the rest of the lower half merges
		assertEquals(1 + 64 + 1, faces.get(0));
		// east-facing: the step's three bands plus the skirt's three on the tile's east border
		assertEquals(6, faces.get(5));
	}

	@Test
	void waterCoversTheBedAndIsFlagged() {
		TerrainSource lake = (x, z, cell, out) -> {
			out.ground = 50;
			out.water = 63;
			out.waterColor = 0x2050C0;
		};
		TileBuilder.Mesh mesh = builder().build(lake, TileKey.of(1, 0, 0));
		ByteBuffer v = vertices(mesh);
		boolean sawWaterTop = false;
		for (int i = 0; i < mesh.vertexCount(); i++) {
			int flags = v.getShort(i * TileBuilder.VERTEX_BYTES + 6);
			if ((flags & 7) == TileBuilder.UP) {
				assertEquals(TileBuilder.WATER, flags & TileBuilder.WATER);
				assertEquals(63 * 8, v.getShort(i * TileBuilder.VERTEX_BYTES + 2));
				sawWaterTop = true;
			}
		}
		assertTrue(sawWaterTop);
	}

	@Test
	void blockyFacesAreWoundCounterClockwiseFromOutside() {
		TerrainSource bumpy = (x, z, cell, out) -> out.ground = 64 + Math.floorMod(x * 7 + z * 13, 5);
		TileBuilder.Mesh mesh = builder().build(bumpy, TileKey.of(0, 0, 0));
		ByteBuffer v = vertices(mesh);
		for (int q = 0; q < mesh.vertexCount() / 4; q++) {
			int base = q * 4;
			int face = v.getShort(base * TileBuilder.VERTEX_BYTES + 6) & 7;
			long[] n = normal(v, base, base + 1, base + 2);
			long[] expected = switch (face) {
				case 0 -> new long[]{0, 1, 0};
				case 2 -> new long[]{0, 0, -1};
				case 3 -> new long[]{0, 0, 1};
				case 4 -> new long[]{-1, 0, 0};
				default -> new long[]{1, 0, 0};
			};
			for (int a = 0; a < 3; a++) {
				assertEquals(Long.signum(expected[a]), Long.signum(n[a]), "normal of face " + face);
			}
		}
	}

	@Test
	void smoothTilesAreTheSharedGrid() {
		TileBuilder.Mesh mesh = builder().build(flat(70), TileKey.of(TileBuilder.smoothFrom + 1, 2, 5));
		assertTrue(mesh.smooth());
		assertEquals(65 * 65 + 4 * 65, mesh.vertexCount());
		assertEquals(TileBuilder.smoothIndexCount(), mesh.indexCount());
		assertEquals(70, mesh.maxY());
		ByteBuffer v = vertices(mesh);
		for (int i = 0; i < 65 * 65; i++) {
			assertEquals(70 * 8, v.getShort(i * TileBuilder.VERTEX_BYTES + 2));
			assertEquals(TileBuilder.SMOOTH, v.getShort(i * TileBuilder.VERTEX_BYTES + 6) & 7);
		}
	}

	@Test
	void smoothGridTrianglesFaceUpAndStayInRange() {
		TerrainSource hills = (x, z, cell, out) -> out.ground = 64 + (float) (30 * Math.sin(x / 700.0) * Math.cos(z / 500.0));
		TileBuilder.Mesh mesh = builder().build(hills, TileKey.of(4, 0, 0));
		ByteBuffer v = vertices(mesh);
		ByteBuffer idx = TileBuilder.smoothIndices();
		int grid = 64 * 64 * 6;
		for (int t = 0; t < TileBuilder.smoothIndexCount() / 3; t++) {
			int a = idx.getShort(t * 6) & 0xFFFF, b = idx.getShort(t * 6 + 2) & 0xFFFF, c = idx.getShort(t * 6 + 4) & 0xFFFF;
			assertTrue(a < mesh.vertexCount() && b < mesh.vertexCount() && c < mesh.vertexCount());
			if (t * 3 < grid) {
				assertTrue(normal(v, a, b, c)[1] > 0, "grid triangle " + t + " faces up");
			}
		}
	}

	@Test
	void forestsRaiseTheCanopy() {
		TerrainSource forest = (x, z, cell, out) -> {
			out.ground = 70;
			out.canopy = 12;
			out.cover = 1;
			out.leaves = 0x205010;
		};
		// close in, individual crowns: as tall as the trees, give or take a quarter
		int crowns = builder().build(forest, TileKey.of(0, 0, 0)).maxY();
		assertTrue(crowns >= 78 && crowns <= 86, "crowns top out at " + crowns);
		// far out, a canopy as high as a dense forest stands
		assertEquals(70 + Math.round(Smooth.blanket(12, 1)), builder().build(forest, TileKey.of(6, 0, 0)).maxY(), 1);
	}

	@Test
	void slopesSurviveTheirByte() {
		for (float g : new float[]{0, 0.01F, -0.05F, 0.3F, -1, 2.5F, 7.5F}) {
			float back = Smooth.decode(Smooth.encode(g));
			assertEquals(g, back, Math.max(0.006F, Math.abs(g) * 0.12F), "slope " + g);
		}
	}

	private static ByteBuffer vertices(TileBuilder.Mesh mesh) {
		return mesh.vertices().duplicate().order(ByteOrder.nativeOrder());
	}

	private static Map<Integer, Integer> faces(TileBuilder.Mesh mesh) {
		Map<Integer, Integer> count = new HashMap<>();
		ByteBuffer v = vertices(mesh);
		for (int q = 0; q < mesh.vertexCount() / 4; q++) {
			int face = v.getShort(q * 4 * TileBuilder.VERTEX_BYTES + 6) & 7;
			count.merge(face, 1, Integer::sum);
		}
		return count;
	}

	private static long[] position(ByteBuffer v, int i) {
		int o = i * TileBuilder.VERTEX_BYTES;
		return new long[]{v.getShort(o), v.getShort(o + 2), v.getShort(o + 4)};
	}

	private static long[] normal(ByteBuffer v, int i0, int i1, int i2) {
		long[] p0 = position(v, i0), p1 = position(v, i1), p2 = position(v, i2);
		long[] a = {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]};
		long[] b = {p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]};
		return new long[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}
}
