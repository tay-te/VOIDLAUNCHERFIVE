package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UndergroundTileTest {
	private static final Object WORLD = new Object();

	/**
	 * A great cavern from y 20 to 60 west of x 0 (glowworms, a great river along its floor), solid rock east of it with a
	 * passage at y 30..33 every 64 blocks of z.
	 */
	private static final UndergroundTile.CaveSource CAVES = (x, y, z, out) -> {
		if (x < 0 && y >= 20 && y <= 60) {
			out.space = Math.min(y - 20, 60 - y) + 1;
			out.floor = 20;
			out.ceiling = 60;
			out.water = TileCodec.WATER_RIVER;
			out.waterDistance = 3; // water at the cell (within 4 blocks) is what the U nibble names (pack U tile v1)
			out.greatRiver = 0.8F;
			out.glowworms = true;
			out.realm = TileCodec.realm("tidal");
			return true;
		}
		if (x >= 0 && y >= 30 && y <= 33 && Math.floorMod(z, 64) < 4) {
			out.space = 2;
			out.floor = 30;
			out.ceiling = 33;
			return true;
		}
		out.clear(y);
		return true;
	};

	private static UndergroundTile.Inputs at(double x, double y, double z, int budget) {
		return new UndergroundTile.Inputs(WORLD, CAVES, x, y, z, budget);
	}

	private static List<TileStrip> run(UndergroundTile u, UndergroundTile.Inputs in, byte[] bytes) {
		List<TileStrip> seen = new ArrayList<>();
		u.schedule(in, Runnable::run);
		u.queue.drain(bytes, (t, c, l) -> {
		}, 1, seen::add);
		return seen;
	}

	@Test
	void theSliceFillsNearestFirstWithinItsBudget() {
		UndergroundTile u = new UndergroundTile();
		byte[] bytes = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
		List<TileStrip> first = run(u, at(-10, 41.6, 5, 2048), bytes);
		assertTrue(first.getFirst().clear());
		assertEquals(1 + 16, first.size(), "the clear and 16 rows of 128");
		assertEquals(40, first.getFirst().base());
		for (int i = 0; i < 8; i++) {
			run(u, at(-10, 41.6, 5, 2048), bytes);
		}
		int cave = TileStrip.read(bytes, Contract.U, -10 >> 2, 1);
		assertEquals(20 - 40, TileCodec.unsigned(TileCodec.g(cave)));
		assertEquals(60 - 40, TileCodec.unsigned(TileCodec.b(cave)));
		assertEquals(TileCodec.WATER_RIVER, TileCodec.a(cave) >>> 4);
		assertEquals(TileCodec.UG_PRESENT | TileCodec.UG_GLOWWORMS | TileCodec.UG_RIVER, TileCodec.a(cave) & 15);
		int rock = TileStrip.read(bytes, Contract.U, 10, 1);
		assertEquals(0, TileCodec.r(rock));
		assertEquals(TileCodec.UG_PRESENT, TileCodec.a(rock) & 15);
		// the eye
		UndergroundTile.Eye eye = u.eye();
		assertEquals(TileCodec.realm("tidal"), eye.realm());
		assertTrue(eye.glowworms());
		assertTrue(eye.river() > 0.5F);
	}

	@Test
	void climbingMovesTheSliceAndReEncodesEverythingAtOnce() {
		UndergroundTile u = new UndergroundTile();
		byte[] bytes = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
		for (int i = 0; i < 9; i++) {
			run(u, at(-10, 40, 5, 2048), bytes);
		}
		List<TileStrip> rebased = run(u, at(-10, 49, 5, 0), bytes);
		assertEquals(128, rebased.size(), "every row, in one batch");
		assertTrue(rebased.stream().allMatch(s -> s.base() == 48));
		int cave = TileStrip.read(bytes, Contract.U, -3, 1);
		assertEquals(20 - 48, TileCodec.unsigned(TileCodec.g(cave)));
		assertEquals(60 - 48, TileCodec.unsigned(TileCodec.b(cave)));
		// within the hysteresis nothing moves
		assertTrue(run(u, at(-10, 53, 5, 0), bytes).isEmpty());
	}

	@Test
	void walkingSixteenMetresBringsInFourLines() {
		UndergroundTile u = new UndergroundTile();
		byte[] bytes = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
		for (int i = 0; i < 9; i++) {
			run(u, at(-10, 40, 5, 2048), bytes);
		}
		assertTrue(run(u, at(-10 + 12, 40, 5, 2048), bytes).isEmpty(), "three cells: not yet");
		List<TileStrip> moved = run(u, at(-10 + 16, 40, 5, 2048), bytes);
		assertEquals(4, moved.size());
		assertTrue(moved.stream().allMatch(TileStrip::column));
		assertFalse(moved.stream().anyMatch(TileStrip::clear));
	}
}
