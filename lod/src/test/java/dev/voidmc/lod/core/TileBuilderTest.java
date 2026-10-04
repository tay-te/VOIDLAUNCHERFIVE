package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

	@Test
	void facesCarryWhatTheyAreMadeOf() {
		TerrainSource grassy = (x, z, cell, out) -> {
			out.ground = 70;
			out.top = 0x40A040;
			out.topMaterial = 5;
			out.sideMaterial = 6;
			out.soilMaterial = 7;
			out.rockMaterial = 300;
		};
		TileBuilder.Mesh mesh = builder().build(grassy, TileKey.of(0, 0, 0));
		ByteBuffer v = vertices(mesh);
		for (int q = 0; q < mesh.vertexCount() / 4; q++) {
			int base = q * 4;
			int face = v.getShort(base * TileBuilder.VERTEX_BYTES + 6) & 7;
			int material = material(v, base);
			for (int i = 1; i < 4; i++) {
				assertEquals(material, material(v, base + i), "one material per quad");
			}
			if (face == TileBuilder.UP) {
				assertEquals(5, material);
				continue;
			}
			// the skirt's bands, top down: the grass side, the soil, the rock (an id past one byte)
			int top = v.getShort(base * TileBuilder.VERTEX_BYTES + 2) / 8;
			assertEquals(top == 70 ? 6 : top == 69 ? 7 : 300, material, "band from y " + top);
		}
	}

	@Test
	void topsMergeOnlyWithinOneMaterial() {
		// the same colour everywhere, but two blocks: half the tile is one, half the other
		TerrainSource halves = (x, z, cell, out) -> {
			out.ground = 70;
			out.topMaterial = x < 32 ? 3 : 4;
		};
		TileBuilder.Mesh mesh = builder().build(halves, TileKey.of(0, 0, 0));
		ByteBuffer v = vertices(mesh);
		List<Integer> tops = new ArrayList<>();
		for (int q = 0; q < mesh.vertexCount() / 4; q++) {
			if ((v.getShort(q * 4 * TileBuilder.VERTEX_BYTES + 6) & 7) == TileBuilder.UP) {
				tops.add(material(v, q * 4));
			}
		}
		assertEquals(List.of(3, 4), tops);
	}

	@Test
	void wallsBreakWhereTheirMaterialsDo() {
		// a step facing +x along the whole tile, made of one colour throughout; its sides are one block for z < 32
		// and another beyond
		TerrainSource step = (x, z, cell, out) -> {
			out.ground = x < 32 ? 80 : 64;
			out.sideMaterial = z < 32 ? 1 : 2;
		};
		// the step's three bands (side, soil, rock) twice over, plus the tile border's skirt in two runs as well
		assertEquals(12, faces(builder().build(step, TileKey.of(0, 0, 0))).get(5));
		TerrainSource plain = (x, z, cell, out) -> out.ground = x < 32 ? 80 : 64;
		assertEquals(6, faces(builder().build(plain, TileKey.of(0, 0, 0))).get(5));
	}

	@Test
	void bandedRockIsABandPerMaterial() {
		// strata of one colour but alternating blocks: each band its own quad, made of its own block
		int[] bands = {0x9A5A3A, 0x9A5A3A, 0x9A5A3A, 0x9A5A3A};
		int[] blocks = {11, 12, 13, 14};
		TerrainSource canyon = (x, z, cell, out) -> {
			out.ground = x < 32 ? 80 : 64;
			out.soilDepth = 0;
			out.bands = bands;
			out.bandMaterials = blocks;
			out.bandHeight = 1;
		};
		TileBuilder.Mesh mesh = builder().build(canyon, TileKey.of(0, 0, 0));
		ByteBuffer v = vertices(mesh);
		Map<Integer, Integer> wall = new TreeMap<>();
		for (int q = 0; q < mesh.vertexCount() / 4; q++) {
			int o = q * 4 * TileBuilder.VERTEX_BYTES;
			if ((v.getShort(o + 6) & 7) == 5 && v.getShort(o) == 32) {
				int top = v.getShort(o + 2) / 8;
				int bottom = v.getShort(o + 2 * TileBuilder.VERTEX_BYTES + 2) / 8;
				if (top < 79) {
					assertEquals(1, top - bottom, "one band a block");
				}
				wall.put(top, material(v, q * 4));
			}
		}
		// the surface block's side on top, then one quad per block of rock down to the floor
		assertEquals(16, wall.size());
		for (int y = 65; y < 80; y++) {
			assertEquals(blocks[Math.floorMod(y - 1, 4)], wall.get(y), "band under y " + y);
		}
		// without the materials, the one colour is one quad
		TerrainSource plain = (x, z, cell, out) -> {
			out.ground = x < 32 ? 80 : 64;
			out.soilDepth = 0;
			out.bands = bands;
			out.bandHeight = 1;
		};
		ByteBuffer p = vertices(builder().build(plain, TileKey.of(0, 0, 0)));
		int quads = 0;
		for (int q = 0; q < p.limit() / TileBuilder.VERTEX_BYTES / 4; q++) {
			int o = q * 4 * TileBuilder.VERTEX_BYTES;
			if ((p.getShort(o + 6) & 7) == 5 && p.getShort(o) == 32) {
				quads++;
			}
		}
		assertEquals(2, quads);
	}

	@Test
	void crownsAreTheirLeavesAndWaterIsUntextured() {
		TerrainSource forest = (x, z, cell, out) -> {
			out.ground = 70;
			out.topMaterial = 2;
			out.canopy = 12;
			out.cover = 1;
			out.leavesMaterial = 9;
		};
		TileBuilder.Mesh mesh = builder().build(forest, TileKey.of(0, 0, 0));
		ByteBuffer v = vertices(mesh);
		boolean sawCrown = false;
		for (int q = 0; q < mesh.vertexCount() / 4; q++) {
			int o = q * 4 * TileBuilder.VERTEX_BYTES;
			if ((v.getShort(o + 6) & 7) == TileBuilder.UP && v.getShort(o + 2) / 8 > 70) {
				assertEquals(9, material(v, q * 4));
				sawCrown = true;
			}
		}
		assertTrue(sawCrown);

		TerrainSource lake = (x, z, cell, out) -> {
			out.ground = 50;
			out.water = 63;
			out.topMaterial = 4;
			out.sideMaterial = 4;
		};
		mesh = builder().build(lake, TileKey.of(0, 0, 0));
		v = vertices(mesh);
		for (int i = 0; i < mesh.vertexCount(); i++) {
			assertEquals(0, material(v, i), "water has no block texture");
		}
	}

	/** The material id a blocky vertex carries in its second colour. */
	private static int material(ByteBuffer v, int i) {
		int o = i * TileBuilder.VERTEX_BYTES;
		assertEquals(0, v.get(o + 14), "the material's spare bytes stay clear");
		return (v.get(o + 12) & 0xFF) | (v.get(o + 13) & 0xFF) << 8;
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
