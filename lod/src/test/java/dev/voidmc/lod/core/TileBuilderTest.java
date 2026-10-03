package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TileBuilderTest {
	private static TerrainSource flat(int y) {
		return (x, z, cell, out) -> {
			out.ground = y;
			out.groundColor = 0x40A040;
		};
	}

	@Test
	void flatGroundIsOneTopQuadPlusSkirts() {
		TileBuilder.Mesh mesh = new TileBuilder().build(flat(70), TileKey.of(2, 3, -4));
		Map<Integer, Integer> faces = faces(mesh);
		assertEquals(1, faces.getOrDefault(0, 0), "one merged top");
		// each border hangs one merged skirt
		for (int face = 2; face <= 5; face++) {
			assertEquals(1, faces.getOrDefault(face, 0), "one skirt on face " + face);
		}
		assertEquals(70, mesh.maxY());
	}

	@Test
	void aStepMakesOneMergedWall() {
		// x < 32 cells stands at 80, the rest at 64: one wall facing +x along the whole tile
		TerrainSource step = (x, z, cell, out) -> {
			out.ground = x < 32 ? 80 : 64;
			out.groundColor = 0x808080;
		};
		TileBuilder.Mesh mesh = new TileBuilder().build(step, TileKey.of(0, 0, 0));
		Map<Integer, Integer> faces = faces(mesh);
		assertEquals(2, faces.get(0), "two merged tops");
		// east-facing: the step itself plus the skirt on the tile's east border
		assertEquals(2, faces.get(5));
	}

	@Test
	void waterCoversTheBedAndIsFlagged() {
		TerrainSource lake = (x, z, cell, out) -> {
			out.ground = 50;
			out.water = 63;
			out.waterColor = 0x2050C0;
		};
		TileBuilder.Mesh mesh = new TileBuilder().build(lake, TileKey.of(1, 0, 0));
		ByteBuffer v = mesh.vertices().duplicate().order(mesh.vertices().order());
		boolean sawWaterTop = false;
		for (int q = 0; q < mesh.quads(); q++) {
			int face = v.getShort(q * TileBuilder.QUAD_BYTES + 6);
			int y = v.getShort(q * TileBuilder.QUAD_BYTES + 2);
			if ((face & 7) == 0) {
				assertEquals(TileBuilder.WATER, face & TileBuilder.WATER);
				assertEquals(63, y);
				sawWaterTop = true;
			}
		}
		assertTrue(sawWaterTop);
	}

	@Test
	void topsAreWoundCounterClockwiseFromAbove() {
		TileBuilder.Mesh mesh = new TileBuilder().build(flat(70), TileKey.of(0, 0, 0));
		ByteBuffer v = mesh.vertices().duplicate().order(mesh.vertices().order());
		for (int q = 0; q < mesh.quads(); q++) {
			int base = q * TileBuilder.QUAD_BYTES;
			int face = v.getShort(base + 6) & 7;
			int[] p0 = vertex(v, base, 0), p1 = vertex(v, base, 1), p2 = vertex(v, base, 2);
			long[] n = cross(sub(p1, p0), sub(p2, p0));
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
	void forestsRaiseTheCanopy() {
		TerrainSource forest = (x, z, cell, out) -> {
			out.ground = 70;
			out.groundColor = 0x60A040;
			out.canopy = 12;
			out.cover = 1;
			out.canopyColor = 0x205010;
		};
		TileBuilder.Mesh mesh = new TileBuilder().build(forest, TileKey.of(1, 0, 0));
		assertEquals(82, mesh.maxY());
		// far out, heights snap to a quarter cell: 64-block cells step in 16s
		assertEquals(80, new TileBuilder().build(forest, TileKey.of(6, 0, 0)).maxY());
	}

	private static Map<Integer, Integer> faces(TileBuilder.Mesh mesh) {
		Map<Integer, Integer> count = new HashMap<>();
		ByteBuffer v = mesh.vertices().duplicate().order(mesh.vertices().order());
		for (int q = 0; q < mesh.quads(); q++) {
			int face = v.getShort(q * TileBuilder.QUAD_BYTES + 6) & 7;
			count.merge(face, 1, Integer::sum);
		}
		return count;
	}

	private static int[] vertex(ByteBuffer v, int base, int i) {
		int o = base + i * TileBuilder.VERTEX_BYTES;
		return new int[]{v.getShort(o), v.getShort(o + 2), v.getShort(o + 4)};
	}

	private static int[] sub(int[] a, int[] b) {
		return new int[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static long[] cross(int[] a, int[] b) {
		return new long[]{(long) a[1] * b[2] - (long) a[2] * b[1], (long) a[2] * b[0] - (long) a[0] * b[2], (long) a[0] * b[1] - (long) a[1] * b[0]};
	}
}
