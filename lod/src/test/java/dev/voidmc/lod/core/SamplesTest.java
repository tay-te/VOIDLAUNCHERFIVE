package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SamplesTest {
	private static final int GRASS = 0x60A040, ROCK = 0x8A8A8A;
	private static final int GRASS_ID = 5, ROCK_ID = 9;

	/** Ground rising {@code slope} blocks a block eastward, grass over rock that the generator bares where steep. */
	private static TerrainSource hill(float slope) {
		return (x, z, cell, out) -> {
			out.ground = 64 + slope * x;
			out.top = out.side = out.soil = GRASS;
			out.topMaterial = out.sideMaterial = out.soilMaterial = GRASS_ID;
			out.rock = ROCK;
			out.rockMaterial = ROCK_ID;
			out.bareWhenSteep = true;
			out.bareRise = ColumnSample.DEFAULT_BARE_RISE;
		};
	}

	@Test
	void steepGroundThatStripsToRockShowsRock() {
		Samples s = new Samples(false);
		s.sample(hill(2), TileKey.of(0, 0, 0), false, 1, null);
		int k = s.at(10, 10);
		assertEquals(ROCK, s.top[k]);
		assertEquals(ROCK, s.side[k]);
		assertEquals(ROCK_ID, s.topMaterial[k]);
		// the generator's own threshold: a rise of 2.9 over two blocks
		s.sample(hill(1.4F), TileKey.of(0, 0, 0), false, 1, null);
		assertEquals(GRASS, s.top[s.at(10, 10)]);
		// and only where the source says its ground strips
		s.sample((x, z, cell, out) -> {
			hill(2).sample(x, z, cell, out);
			out.bareWhenSteep = false;
		}, TileKey.of(0, 0, 0), false, 1, null);
		assertEquals(GRASS, s.top[s.at(10, 10)]);
	}
}
