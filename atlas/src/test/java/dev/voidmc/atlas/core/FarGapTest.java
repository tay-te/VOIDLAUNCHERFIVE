package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Far land's gaps: a thin one (a missed LOD tile's seam) is filled from its neighbours, a wide one stays unexplored. */
class FarGapTest {
	private static TileRenderer.Looks looks() {
		return new TileRenderer.Looks() {
			@Override
			public Material material(int block) {
				return Material.GRASS;
			}

			@Override
			public int colour(int block, int biome) {
				return 0x5A8A3C;
			}

			@Override
			public int water(int biome) {
				return 0x3F76E4;
			}
		};
	}

	/** Far land over the whole region but for a hole from {@code (x0, z0)} to {@code (x1, z1)}. */
	private static int[] render(int x0, int z0, int x1, int z1) {
		RegionRaw raw = new RegionRaw(0, 0);
		for (int z = 0; z < Coords.REGION_SIZE; z++) {
			for (int x = 0; x < Coords.REGION_SIZE; x++) {
				if (x >= x0 && x < x1 && z >= z0 && z < z1) {
					continue;
				}
				raw.set(x, z, 64, 1, 0, 1, RegionRaw.KNOWN);
			}
		}
		RegionRaw[] around = new RegionRaw[9];
		around[4] = raw;
		RegionImage image = new RegionImage(0, 0, Palette.VERSION);
		new TileRenderer(looks()).render(around, image, 0, 0, Coords.REGION_SIZE, Coords.REGION_SIZE, TileRenderer.Mode.FAR);
		return image.level(0);
	}

	@Test
	void aSeamTwoChunksWideIsFilled() {
		int[] out = render(200, 0, 232, Coords.REGION_SIZE);
		assertNotEquals(0, out[100 * Coords.REGION_SIZE + 216] >>> 24, "the middle of a 32-block seam is drawn");
	}

	@Test
	void aSquareHoleOfTwoChunksIsFilled() {
		int[] out = render(200, 200, 232, 232);
		assertNotEquals(0, out[216 * Coords.REGION_SIZE + 216] >>> 24);
	}

	@Test
	void aWideHoleStaysUnexplored() {
		int[] out = render(160, 160, 320, 320);
		assertEquals(0, out[240 * Coords.REGION_SIZE + 240] >>> 24, "the middle of a 10-chunk hole is not made up");
	}
}
