package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.gloam.compat.Expanse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Test;

class FarTilesTest {
	private static final Object WORLD = new Object();

	/**
	 * Expanse's land as a function: rolling ground at 100 with a deep valley (40) along far cell x = 3, a lake in far cell
	 * (5, 5), humid and temperate, misty lakes by the lake.
	 */
	private static final Expanse.RawSampler COUNTRY = (x, z) -> {
		int cx = Math.floorDiv(x, 1024), cz = Math.floorDiv(z, 1024);
		float alt = cx == 3 ? 40 : 100 + (Math.floorMod(x, 1024) < 512 ? 4 : 0);
		boolean lake = cx == 5 && cz == 5;
		return new Expanse.Raw(0.7F, 0.0F, cx == 3 ? 30 : 0, lake ? 0 : 1000, 5000, alt, lake ? "misty_lakes" : "temperate");
	};

	private static FarTiles.Inputs at(double x, double z, Expanse.RawSampler country, FarTiles.FarSource far, long tick) {
		return new FarTiles.Inputs(WORLD, country, far, x, z, 0.2F, 0, 0, 63, tick);
	}

	/** Runs jobs until the tiles are full, writing them into a texture; returns the bytes. */
	private static byte[] settle(FarTiles tiles, FarTiles.Inputs in, byte[] bytes, List<TileStrip> seen) {
		for (int i = 0; i < 400; i++) {
			tiles.queue.post(tiles.job(new FarTiles.Inputs(in.world(), in.country(), in.far(), in.cameraX(), in.cameraZ(), in.wetness(), in.cold(),
				in.summer(), in.seaLevel(), in.tick() + i)));
			tiles.queue.drain(bytes, (t, c, l) -> {
			}, Integer.MAX_VALUE, seen::add);
		}
		return bytes;
	}

	@Test
	void expanseFillsTheWholeReachNearestFirst() {
		FarTiles tiles = new FarTiles();
		List<TileStrip> seen = new ArrayList<>();
		byte[] bytes = settle(tiles, at(500, 500, COUNTRY, null, 0), new byte[Contract.WIDTH * Contract.HEIGHT * 4], seen);
		assertTrue(seen.getFirst().clear());
		// the first row filled is the camera's
		assertEquals(0, seen.get(1).at());
		int f0 = TileStrip.read(bytes, Contract.F0, 0, 0), f1 = TileStrip.read(bytes, Contract.F1, 0, 0);
		int flags = TileCodec.a(f1) & 15;
		assertEquals(Contract.CELL_PRESENT | Contract.CELL_EXPANSE, flags & (Contract.CELL_PRESENT | Contract.CELL_EXPANSE));
		// near the camera five samples a cell: the floor is the second lowest of 104, 104, 100, 104, 100
		assertEquals(100, Contract.fromH16(TileCodec.r(f0), TileCodec.g(f0)), 0.05);
		// the far corner of the window is filled too
		int corner = TileStrip.read(bytes, Contract.F1, 128, -127);
		assertTrue((TileCodec.a(corner) & Contract.CELL_PRESENT) != 0);
		// the valley's cells lie low under their neighbours (two of the eight are valley too: some 46 blocks under them)
		int valley = TileStrip.read(bytes, Contract.F0, 3, 0);
		assertTrue(TileCodec.b(valley) >= 250, "valley lowness " + TileCodec.b(valley));
		assertTrue(TileCodec.b(TileStrip.read(bytes, Contract.F0, 1, 0)) < 40, "the rolling ground beside it is not low");
		int lake = TileStrip.read(bytes, Contract.F1, 5, 5);
		assertEquals(3, TileCodec.a(lake) >>> 4);
		assertTrue((TileCodec.a(lake) & Contract.CELL_WATER) != 0);
	}

	@Test
	void lodsLandGivesTheGroundAndArrivesLater() {
		FarTiles tiles = new FarTiles();
		List<Long> arrivals = new ArrayList<>();
		boolean[] known = {false};
		FarTiles.FarSource lod = new FarTiles.FarSource() {
			@Override
			public boolean summary(int minX, int minZ, int size, FarTiles.Summary out) {
				if (!known[0] || minX != 2 * 1024 || minZ != 0) {
					return false;
				}
				out.known = 64;
				out.floor = 55;
				out.mean = 80;
				out.water = 0.25F;
				out.temperature = 0.8F;
				out.downfall = 0.4F;
				return true;
			}

			@Override
			public void drainArrivals(LongConsumer cells) {
				arrivals.forEach(cells::accept);
				arrivals.clear();
			}
		};
		byte[] bytes = settle(tiles, at(500, 500, COUNTRY, lod, 0), new byte[Contract.WIDTH * Contract.HEIGHT * 4], new ArrayList<>());
		int before = TileStrip.read(bytes, Contract.F0, 2, 0);
		assertEquals(100, Contract.fromH16(TileCodec.r(before), TileCodec.g(before)), 0.05);
		known[0] = true;
		arrivals.add((long) 2 << 32);
		settle(tiles, at(500, 500, COUNTRY, lod, 1000), bytes, new ArrayList<>());
		int after = TileStrip.read(bytes, Contract.F0, 2, 0);
		assertEquals(55, Contract.fromH16(TileCodec.r(after), TileCodec.g(after)), 0.05);
		assertEquals(64, TileCodec.a(after));
		// Expanse still gives the climate
		int f1 = TileStrip.read(bytes, Contract.F1, 2, 0);
		assertEquals(Contract.unorm8(0.7F, 0, 1), TileCodec.b(f1));
	}

	@Test
	void withOnlyLodTheUnknownStaysEmpty() {
		FarTiles tiles = new FarTiles();
		FarTiles.FarSource lod = new FarTiles.FarSource() {
			@Override
			public boolean summary(int minX, int minZ, int size, FarTiles.Summary out) {
				if (minX != 0 || minZ != 0) {
					return false;
				}
				out.known = 10;
				out.floor = 90;
				out.mean = 95;
				out.water = 0;
				out.temperature = 2.0F;
				out.downfall = 0;
				return true;
			}

			@Override
			public void drainArrivals(LongConsumer cells) {
			}
		};
		byte[] bytes = settle(tiles, at(10, 10, null, lod, 0), new byte[Contract.WIDTH * Contract.HEIGHT * 4], new ArrayList<>());
		int f1 = TileStrip.read(bytes, Contract.F1, 0, 0);
		assertEquals(Contract.CELL_PRESENT, TileCodec.a(f1) & (Contract.CELL_PRESENT | Contract.CELL_EXPANSE));
		// a desert: dusty, and no mist
		assertEquals(5, TileCodec.a(f1) >>> 4);
		assertEquals(0, TileCodec.r(f1));
		assertEquals(0, TileStrip.read(bytes, Contract.F1, 1, 0));
	}

	@Test
	void crossingAKilometreBringsInOneLine() {
		FarTiles tiles = new FarTiles();
		settle(tiles, at(500, 500, COUNTRY, null, 0), new byte[Contract.WIDTH * Contract.HEIGHT * 4], new ArrayList<>());
		TileStrip.Batch b = tiles.job(at(1500, 500, COUNTRY, null, 1000));
		long columns = b.strips().stream().filter(TileStrip::column).count();
		assertEquals(3, columns, "the new column and its neighbours re-encoded");
		assertTrue(b.strips().stream().allMatch(s -> s.centreX() == 1));
	}

	@Test
	void winterBringsSnowToTheHills() {
		FarTiles summerTiles = new FarTiles(), winterTiles = new FarTiles();
		Expanse.RawSampler hills = (x, z) -> new Expanse.Raw(0.6F, 0.0F, 0, 1000, 5000, 200, "temperate");
		byte[] summer = settle(summerTiles, at(0, 0, hills, null, 0), new byte[Contract.WIDTH * Contract.HEIGHT * 4], new ArrayList<>());
		FarTiles.Inputs cold = new FarTiles.Inputs(WORLD, hills, null, 0, 0, 0, 1, 0, 63, 0);
		byte[] winter = settle(winterTiles, cold, new byte[Contract.WIDTH * Contract.HEIGHT * 4], new ArrayList<>());
		assertEquals(0, TileCodec.a(TileStrip.read(summer, Contract.F1, 0, 0)) & Contract.CELL_SNOW);
		assertEquals(Contract.CELL_SNOW, TileCodec.a(TileStrip.read(winter, Contract.F1, 0, 0)) & Contract.CELL_SNOW);
	}
}
