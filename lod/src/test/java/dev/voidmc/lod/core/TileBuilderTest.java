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
			assertFacing(face, n);
		}
	}

	private static void assertFacing(int face, long[] n) {
		long[] expected = switch (face) {
			case 0 -> new long[]{0, 1, 0};
			case 2 -> new long[]{0, 0, -1};
			case 3 -> new long[]{0, 0, 1};
			case 4 -> new long[]{-1, 0, 0};
			case 6 -> new long[]{0, -1, 0};
			default -> new long[]{1, 0, 0};
		};
		for (int a = 0; a < 3; a++) {
			assertEquals(Long.signum(expected[a]), Long.signum(n[a]), "normal of face " + face);
		}
	}

	/** An island over the void: a disc of ground at y 60 whose underside sags to 40 at its middle. */
	private static TerrainSource island(int cx, int cz, int radius) {
		return (x, z, cell, out) -> {
			double r = Math.hypot(x - cx, z - cz);
			if (r > radius) {
				out.empty = true;
				return;
			}
			out.ground = 60;
			out.bottom = (float) (40 + 18 * r / radius);
			out.top = out.side = out.soil = out.rock = 0xDBDE9E;
		};
	}

	@Test
	void islandsAreSlabsOverTheVoid() {
		TileBuilder.Mesh mesh = builder().build(island(32, 32, 20), TileKey.of(0, 0, 0));
		assertFalse(mesh.holes());
		Map<Integer, Integer> faces = faces(mesh);
		assertTrue(faces.getOrDefault(TileBuilder.DOWN, 0) > 0, "an underside");
		assertEquals(40, mesh.minY(), "down to the underside, no deeper");
		ByteBuffer v = vertices(mesh);
		for (int q = 0; q < mesh.vertexCount() / 4; q++) {
			int base = q * 4;
			int face = v.getShort(base * TileBuilder.VERTEX_BYTES + 6) & 7;
			assertFacing(face, normal(v, base, base + 1, base + 2));
			// nothing at all over the void: every face lies within the island's footprint (a cell's corner)
			for (int c = 0; c < 4; c++) {
				long[] p = position(v, base + c);
				assertTrue(Math.hypot(p[0] - 32, p[2] - 32) <= 20 + 1.5, "vertex over the void at " + p[0] + "," + p[2]);
			}
		}
		// the rim's walls hang to the underside: some wall reaches below 50
		boolean deep = false;
		for (int i = 0; i < mesh.vertexCount(); i++) {
			int face = v.getShort(i * TileBuilder.VERTEX_BYTES + 6) & 7;
			deep |= face >= 2 && face <= 5 && v.getShort(i * TileBuilder.VERTEX_BYTES + 2) < 50 * 8;
		}
		assertTrue(deep, "rim walls down to the underside");
	}

	@Test
	void voidAloneDrawsNothing() {
		TerrainSource none = (x, z, cell, out) -> out.empty = true;
		TileBuilder.Mesh blocky = builder().build(none, TileKey.of(0, 0, 0));
		assertEquals(0, blocky.vertexCount());
		TileBuilder.Mesh smooth = builder().build(none, TileKey.of(TileBuilder.smoothFrom + 2, 0, 0));
		assertEquals(0, smooth.vertexCount());
		assertEquals(0, smooth.indexCount());
		assertFalse(smooth.holes());
	}

	@Test
	void smoothIslandsKeepTheGridAndFlagTheVoid() {
		int level = TileBuilder.smoothFrom + 1;
		int cell = TileKey.cell(level);
		// an island a quarter of the tile across, in its middle
		TileBuilder.Mesh mesh = builder().build(island(32 * cell, 32 * cell, 16 * cell), TileKey.of(level, 0, 0));
		assertTrue(mesh.holes(), "drawn with the variant that cuts out the void");
		assertEquals(65 * 65 + 4 * 65, mesh.vertexCount());
		assertEquals(TileBuilder.smoothIndexCount(), mesh.indexCount());
		ByteBuffer v = vertices(mesh);
		int land = 0, rim = 0;
		for (int j = 0; j < 65; j++) {
			for (int i = 0; i < 65; i++) {
				int o = (i + j * 65) * TileBuilder.VERTEX_BYTES;
				boolean empty = (v.getShort(o + 6) & TileBuilder.EMPTY) != 0;
				boolean over = Math.hypot(i - 32, j - 32) > 16;
				assertEquals(over, empty, "flag at " + i + "," + j);
				if (!empty) {
					assertEquals(60 * 8, v.getShort(o + 2), "the island's top stays level to its edge");
					land++;
				} else if (Math.hypot(i - 32, j - 32) <= 17.5) {
					// next to land, the vertex hangs to the underside there: the island's flank
					assertTrue(v.getShort(o + 2) < 60 * 8 && v.getShort(o + 2) >= 40 * 8, "rim at " + i + "," + j);
					rim++;
				}
			}
		}
		assertTrue(land > 700 && rim > 50, land + " land, " + rim + " rim");
		assertEquals(40, mesh.minY(), 1);
	}

	@Test
	void conifersAreConesAndBroadleafDomes() {
		float[] shade = new float[2];
		double cones = 0, domes = 0;
		for (int z = 0; z < 200; z++) {
			for (int x = 0; x < 200; x++) {
				cones += Trees.crown(x + 0.5, z + 0.5, 14, 0.8F, 3, 1, shade);
				domes += Trees.crown(x + 0.5, z + 0.5, 14, 0.8F, 3, 0, shade);
			}
		}
		// the same trees, but a cone holds about half the leaves of a dome over the same footprint
		assertTrue(cones < 0.8 * domes, "cones " + cones + " vs domes " + domes);
	}

	private static TerrainSource farForest(float conifer, int leavesTop) {
		return (x, z, cell, out) -> {
			out.ground = 70;
			out.top = 0x60A040;
			out.canopy = 12;
			out.cover = 0.9F;
			out.leaves = 0x305828;
			out.leavesTop = leavesTop;
			out.conifer = conifer;
		};
	}

	/** The mean brightness of the far grid's top colours, and of its side colours. */
	private static float[] farColours(TerrainSource source) {
		TileBuilder.Mesh mesh = builder().build(source, TileKey.of(TileBuilder.smoothFrom + 2, 0, 0));
		ByteBuffer v = vertices(mesh);
		float top = 0, side = 0;
		for (int i = 0; i < 65 * 65; i++) {
			int o = i * TileBuilder.VERTEX_BYTES;
			top += (v.get(o + 8) & 0xFF) + (v.get(o + 9) & 0xFF) + (v.get(o + 10) & 0xFF);
			side += (v.get(o + 12) & 0xFF) + (v.get(o + 13) & 0xFF) + (v.get(o + 14) & 0xFF);
		}
		return new float[]{top / (65 * 65 * 3), side / (65 * 65 * 3)};
	}

	@Test
	void farConiferForestsAreDarkerAndSnowWhitensOnlyTheirTops() {
		float[] broadleaf = farColours(farForest(0, -1));
		float[] conifers = farColours(farForest(1, -1));
		assertTrue(conifers[0] < 0.92F * broadleaf[0], "conifer tops darker: " + conifers[0] + " vs " + broadleaf[0]);
		assertTrue(conifers[1] < 0.92F * broadleaf[1], "conifer flanks darker");
		float[] snowy = farColours(farForest(1, 0xF8FCFC));
		assertTrue(snowy[0] > 1.5F * conifers[0], "snowy tops partly white: " + snowy[0] + " vs " + conifers[0]);
		assertEquals(conifers[1], snowy[1], 1, "the flanks keep their leaves");
	}

	@Test
	void snowLiesOnTheCrownsTopsNotTheirFlanks() {
		TerrainSource snowyForest = (x, z, cell, out) -> {
			out.ground = 70;
			out.top = 0xF0F8F8;
			out.canopy = 10;
			out.cover = 0.6F;
			out.leaves = 0x2F4F2F;
			out.leavesTop = 0xF8FCFC;
			out.conifer = 1;
		};
		TileBuilder.Mesh mesh = builder().build(snowyForest, TileKey.of(0, 0, 0));
		ByteBuffer v = vertices(mesh);
		boolean whiteTop = false;
		for (int i = 0; i < mesh.vertexCount(); i++) {
			int o = i * TileBuilder.VERTEX_BYTES;
			int face = v.getShort(o + 6) & 7;
			int y = v.getShort(o + 2) / 8;
			int green = v.get(o + 9) & 0xFF;
			if (y > 72 && face >= 2 && face <= 5) {
				assertTrue(green < 0x80, "a crown's flank is its leaves");
			}
			whiteTop |= y > 72 && face == TileBuilder.UP && green > 0xE0;
		}
		assertTrue(whiteTop, "snow on the crowns' tops");
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
