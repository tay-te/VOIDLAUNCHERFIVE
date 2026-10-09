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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class TileBuilderTest {
	/** Every far quad built by these tests came in corners its record can say. */
	@AfterAll
	static void everyCompactQuadFitsItsRecord() {
		assertEquals(0, TileBuilder.MISFITS.get());
	}

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
			// the skirts under the surface too: through vanilla's water at the seam they must look like water
			assertEquals(TileBuilder.WATER, flags & TileBuilder.WATER);
			if ((flags & 7) == TileBuilder.UP) {
				assertEquals(63 * 8, v.getShort(i * TileBuilder.VERTEX_BYTES + 2));
				sawWaterTop = true;
			}
		}
		assertTrue(sawWaterTop);
		// and a block deep, not a curtain hanging into the sea
		assertEquals(62, mesh.minY());
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

	@Test
	void forAShaderPackTheWaterComesLastOverABedAndEveryFaceKnowsItsNormal() {
		// a lake over the western half of a level 0 tile, its bed at 55, the land beside it at 70
		TerrainSource shore = (x, z, cell, out) -> {
			boolean lake = Math.floorMod(x, 64) < 32;
			out.ground = lake ? 55 : 70;
			if (lake) {
				out.water = 63;
				out.waterColor = 0x2050C0;
			}
			out.top = 0x40A040;
		};
		TileBuilder.Mesh mesh;
		TileBuilder.packFormat = true;
		try {
			mesh = builder().build(shore, TileKey.of(0, 0, 0));
		} finally {
			TileBuilder.packFormat = false;
		}
		assertFalse(mesh.compact());
		int quads = mesh.vertexCount() / 4;
		int land = mesh.landIndices() / 6;
		assertEquals(0, mesh.landIndices() % 6);
		assertTrue(land > 0 && land < quads, land + " land quads of " + quads);
		ByteBuffer v = vertices(mesh);
		boolean sawBed = false;
		// Distant Horizons' normals: down, up, north, south, west, east
		int[] ours = {6, 0, 2, 3, 4, 5};
		for (int q = 0; q < quads; q++) {
			int o = q * 4 * TileBuilder.VERTEX_BYTES;
			int material = v.get(o + 12) & 0xFF;
			int normal = v.get(o + 13) & 0xFF;
			// the land's range first, then the water's, which the pack's water program draws blended over it
			assertEquals(q >= land, material == TileBuilder.PACK_WATER, "quad " + q + " of material " + material);
			if (material == TileBuilder.PACK_WATER) {
				assertEquals(180, v.get(o + 11) & 0xFF, "the water lets the bed show through");
			}
			int y = (v.getShort(o + 2) & 0xFFFF) - TileBuilder.PACK_Y_OFFSET;
			if (q < land && normal == 1 && y == 55) {
				sawBed = true;
			}
			assertFacing(ours[normal], normal(v, q * 4, q * 4 + 1, q * 4 + 2));
		}
		assertTrue(sawBed, "the lake's bed is drawn under its water");
	}

	@Test
	void aDeepSeaFloorStepsDownInFewLevelsForAShaderPack() {
		// a swelling floor with block-sized bumps, 21 to 41 blocks under the surface, and the same in shallows 2 to 8 deep
		TerrainSource deep = (x, z, cell, out) -> {
			out.ground = (float) (31 + 8 * Math.sin(x / 23.0) * Math.cos(z / 17.0) + Math.floorMod(x * 7 + z * 13, 3));
			out.water = 63;
			out.top = 0xC0B080;
		};
		TerrainSource shallow = (x, z, cell, out) -> {
			out.ground = (float) (57 + 2 * Math.sin(x / 23.0) * Math.cos(z / 17.0) + Math.floorMod(x * 7 + z * 13, 3));
			out.water = 63;
			out.top = 0xC0B080;
		};
		int deepQuads, shallowQuads;
		TileBuilder.packFormat = true;
		try {
			TileBuilder.Mesh mesh = builder().build(deep, TileKey.of(2, 0, 0));
			deepQuads = mesh.landIndices() / 6;
			ByteBuffer v = vertices(mesh);
			for (int q = 0; q < deepQuads; q++) {
				int o = q * 4 * TileBuilder.VERTEX_BYTES;
				if ((v.get(o + 13) & 0xFF) == 1) {
					int depth = 63 - ((v.getShort(o + 2) & 0xFFFF) - TileBuilder.PACK_Y_OFFSET);
					assertTrue(depth == 12 || depth == 20 || depth == 28 || depth == 36, "the floor lies about as deep as it is, in 8-block steps: " + depth);
				}
			}
			shallowQuads = builder().build(shallow, TileKey.of(2, 0, 0)).landIndices() / 6;
		} finally {
			TileBuilder.packFormat = false;
		}
		System.out.println("pack quads: deep floor " + deepQuads + ", shallow floor " + shallowQuads);
		assertTrue(deepQuads * 3 < shallowQuads, "a deep floor merges into far fewer faces: " + deepQuads + " against " + shallowQuads);
		assertTrue(shallowQuads > 500, "shallows keep their floor: " + shallowQuads);
	}

	@Test
	void blockLightRidesInEveryVertexFormat() {
		// a lit yard (block light 14) beside dark ground, and a lava pool, which always glows
		TerrainSource town = (x, z, cell, out) -> {
			out.ground = 64;
			out.top = 0x707070;
			if (Math.floorMod(x, 64) < 16) {
				out.light = 14;
			} else if (Math.floorMod(z, 64) < 16) {
				out.water = 65;
				out.lava = true;
			}
		};
		// near: the 3 bits under the occlusion in the flags' high byte
		TileBuilder.Mesh near = builder().build(town, TileKey.of(0, 0, 0));
		java.util.Set<Integer> nearLights = new java.util.TreeSet<>();
		ByteBuffer v = vertices(near);
		for (int i = 0; i < near.vertexCount(); i++) {
			nearLights.add(v.getShort(i * TileBuilder.VERTEX_BYTES + 6) >> 8 & 7);
		}
		assertEquals(java.util.Set.of(0, 7), nearLights, "dark, and lit (14 and 15 both round to 7)");
		// far: the top 2 bits of the style's first word
		TileBuilder.Mesh far = builder().build(town, TileKey.of(TileBuilder.COMPACT_FROM, 0, 0));
		assertTrue(far.compact());
		java.util.Set<Integer> farLights = new java.util.TreeSet<>();
		for (int q = 0; q < far.vertexCount() / 4; q++) {
			farLights.add((int) TileBuilder.style(far, q) >>> 30);
		}
		assertEquals(java.util.Set.of(0, 3), farLights);
		// through a pack: Distant Horizons' block light nibble
		TileBuilder.Mesh pack;
		TileBuilder.packFormat = true;
		try {
			pack = builder().build(town, TileKey.of(0, 0, 0));
		} finally {
			TileBuilder.packFormat = false;
		}
		java.util.Set<Integer> packLights = new java.util.TreeSet<>();
		v = vertices(pack);
		for (int i = 0; i < pack.vertexCount(); i++) {
			packLights.add(v.getShort(i * TileBuilder.VERTEX_BYTES + 6) >> 4 & 15);
		}
		assertEquals(java.util.Set.of(0, 14, 15), packLights);
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

	/** Runs {@code body} with the far field drawn smooth from level 2, as it was by default before. */
	private static void smoothFar(Runnable body) {
		int was = TileBuilder.smoothFrom;
		TileBuilder.smoothFrom = 2;
		try {
			body.run();
		} finally {
			TileBuilder.smoothFrom = was;
		}
	}

	@Test
	void everyLevelIsBlockyByDefault() {
		for (int level = 0; level <= TileKey.MAX_LEVEL; level += 3) {
			assertFalse(builder().build(flat(70), TileKey.of(level, 1, -1)).smooth(), "level " + level);
		}
	}

	@Test
	void smoothTilesAreTheSharedGrid() {
		smoothFar(this::smoothTilesAreTheSharedGridAtLevel3);
	}

	private void smoothTilesAreTheSharedGridAtLevel3() {
		TileBuilder.Mesh mesh = builder().build(flat(70), TileKey.of(3, 2, 5));
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
		smoothFar(this::smoothGridTrianglesFaceUp);
	}

	private void smoothGridTrianglesFaceUp() {
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
		// far out too, crowns, as tall as the trees within a step (a quarter of a 64-block cell, heights rounded up)
		int far = builder().build(forest, TileKey.of(6, 0, 0)).maxY();
		assertTrue(far >= 78 && far <= 70 + 12 + 2 * 16, "far crowns top out at " + far);
		// and drawn smooth, a canopy as high as a dense forest stands
		smoothFar(() -> assertEquals(70 + Math.round(Smooth.blanket(12, 1)), builder().build(forest, TileKey.of(6, 0, 0)).maxY(), 1));
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

	/**
	 * Rough land of every kind a far tile meets: hills, a cliff two kilometres tall, lakes, lit towns, forests whose
	 * flanks run down into their shade, colours that drift from block to block.
	 */
	private static final TerrainSource ROUGH = (x, z, cell, out) -> {
		int h = Math.floorMod(x * 7919 + z * 104729, 97);
		out.ground = (x < 96 ? -1400 : 600) + (float) (40 * Math.sin(x / 37.0) * Math.cos(z / 23.0)) + h % 5;
		out.top = 0x204010 + (h << 16) + (h * 3 % 200 << 8);
		out.side = 0x7F6B42 ^ h;
		out.soil = 0x5A4030 + (h % 7 << 8);
		out.soilDepth = 1 + h % 4;
		out.rock = 0x808080 - (h % 11) * 0x030303;
		out.light = (x / 16 + z / 32) % 16;
		out.biome = h % 3;
		out.topMaterial = 1 + h % 2;
		if (h % 13 == 0) {
			out.water = (int) out.ground + 3;
			out.waterColor = 0x2050C0;
		} else if (z > 160) {
			out.canopy = 6 + h % 9;
			out.cover = 0.8F;
			out.crown = h / 97.0F;
			out.leaves = 0x305828 + h;
			out.leavesTop = out.leaves;
			out.conifer = h % 2;
			out.leavesMaterial = 3;
		}
	};

	@Test
	void everyFarQuadComesBackFromItsRecordAndStyle() {
		dev.voidmc.lod.api.LodApi.setSeasons((kind, biome, material, conifer) -> 1 + kind * 3 + biome);
		List<String> traced = new ArrayList<>();
		TileBuilder.recordTrace = r -> {
			int[][] corners = new int[4][];
			for (int k = 0; k < 4; k++) {
				corners[k] = new int[] {r[k * 5], r[k * 5 + 1], r[k * 5 + 2], r[k * 5 + 3], r[k * 5 + 4]};
			}
			traced.add(quad(corners, r[20], r[21], r[22], r[23], r[24], r[25]));
		};
		long styles = 0, quads = 0;
		try {
			for (TerrainSource source : List.of(ROUGH, island(128, 128, 90), flat(-60))) {
				for (int level = TileBuilder.COMPACT_FROM; level <= TileBuilder.COMPACT_FROM + 3; level++) {
					for (boolean lit : new boolean[] {false, true}) {
						traced.clear();
						long key = TileKey.of(level, 0, 0);
						TileBuilder.Mesh mesh = new TileBuilder(true).build(source, key, lit ? new Heights() : null);
						assertTrue(mesh.compact());
						List<String> decoded = new ArrayList<>();
						int[] c = new int[5];
						ByteBuffer v = mesh.vertices();
						for (int q = 0; q < mesh.quads(); q++) {
							int w0 = v.getInt(q * TileBuilder.RECORD_BYTES), w1 = v.getInt(q * TileBuilder.RECORD_BYTES + 4);
							long style = TileBuilder.style(mesh, q);
							int[][] corners = new int[4][];
							int low = Integer.MAX_VALUE;
							for (int k = 0; k < 4; k++) {
								TileBuilder.recordCorner(w0, w1, mesh.minY(), k, c);
								corners[k] = new int[] {c[0], c[1], c[2], 0, c[4]};
								low = Math.min(low, c[1]);
							}
							int face = w0 >>> 29;
							boolean lying = face == TileBuilder.UP || face == TileBuilder.DOWN;
							for (int[] corner : corners) {
								// as the shader picks it: a wall's colour A along its top, B along its foot
								corner[3] = (int) (lying || corner[1] > low ? style : style >> 15) & 32767;
							}
							int hi = (int) (style >>> 32);
							decoded.add(quad(corners, face | hi >> 17 & TileBuilder.WATER, (int) style >>> 30, hi & 15, hi >> 4 & 15, hi >> 8 & 31,
								hi >> 13 & 127));
						}
						traced.sort(null);
						decoded.sort(null);
						assertEquals(traced, decoded, "level " + level + (lit ? ", lit" : ""));
						styles += (mesh.bytes() - mesh.quads() * TileBuilder.RECORD_BYTES) / TileBuilder.RECORD_BYTES;
						quads += mesh.quads();
					}
				}
			}
		} finally {
			TileBuilder.recordTrace = null;
			dev.voidmc.lod.api.LodApi.setSeasons(null);
		}
		assertTrue(styles * 2 < quads, styles + " styles for " + quads + " quads");
	}

	/**
	 * A quad as it draws: its corners (x, y, z, colour, occlusion) in drawing order, from whichever corner comes first
	 * in a fixed order unless its corners are shaded apart (then the diagonal it splits along matters), then its face
	 * and light.
	 */
	private static String quad(int[][] corners, int face, int block, int east, int west, int occlusion, int season) {
		String[] cs = new String[4];
		for (int k = 0; k < 4; k++) {
			cs[k] = java.util.Arrays.toString(corners[k]);
		}
		boolean shaded = corners[0][4] != corners[1][4] || corners[0][4] != corners[2][4] || corners[0][4] != corners[3][4];
		String best = null;
		for (int r = 0; r < (shaded ? 1 : 4); r++) {
			String s = cs[r] + cs[r + 1 & 3] + cs[r + 2 & 3] + cs[r + 3 & 3];
			if (best == null || s.compareTo(best) < 0) {
				best = s;
			}
		}
		return best + " face " + face + " light " + block + "/" + east + "/" + west + "/" + occlusion + " season " + season;
	}
}
