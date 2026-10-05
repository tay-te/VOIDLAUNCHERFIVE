package dev.voidmc.atlas.core.under;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.detect.BlockSource;
import dev.voidmc.atlas.core.detect.FakeWorld;
import dev.voidmc.atlas.core.detect.Sig;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The underground reveal's no-x-ray guarantees: what is connected to the player through open space is revealed, what
 * lies behind rock is not, however close; the radius, unloaded chunks and lava all stop the walk; water does not.
 */
class RevealTest {
	private static final int PY = 10;
	private static final int R = Reveal.RADIUS;

	/** Solid rock far above and below the player. */
	private static FakeWorld rock() {
		return new FakeWorld(200);
	}

	private static int band(int y) {
		return Math.floorDiv(y, Reveal.BAND);
	}

	/** The player's room: x and z -3..3, from the player's feet up through the next band. */
	private static int room(FakeWorld w) {
		int top = PY + Reveal.BAND;
		w.fill(-3, PY, -3, 3, top, 3, "minecraft:cave_air");
		return top;
	}

	private static void assertHidden(Reveal.Result r, int x, int z, String what) {
		for (int b = r.band0; b < r.band0 + r.bands; b++) {
			assertEquals(Reveal.NOTHING, r.topAt(x, z, b), what + " open at " + x + "," + z + " band " + b);
			assertFalse(r.wallAt(x, z, b), what + " wall at " + x + "," + z + " band " + b);
		}
	}

	@Test
	void caveThePlayerStandsInIsRevealedBandByBand() {
		FakeWorld w = rock();
		int top = room(w);
		Reveal.Result r = new Reveal().walk(w, 0, PY, 0, R);
		int lower = band(PY);
		int upper = band(top);
		assertTrue(upper > lower, "the room spans two bands");
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				// the lower band is open to its top, the upper to the room's ceiling
				assertEquals((lower + 1) * Reveal.BAND - 1, r.topAt(x, z, lower));
				assertEquals(top, r.topAt(x, z, upper));
				// floor and ceiling are wall
				assertTrue(r.wallAt(x, z, band(PY - 1)), "floor");
				assertTrue(r.wallAt(x, z, band(top + 1)), "ceiling");
			}
		}
		// the walls round it are seen, as walls only
		for (int i = -3; i <= 3; i++) {
			for (int[] c : new int[][]{{4, i}, {-4, i}, {i, 4}, {i, -4}}) {
				assertTrue(r.wallAt(c[0], c[1], lower), "wall at " + Arrays.toString(c));
				assertEquals(Reveal.NOTHING, r.topAt(c[0], c[1], lower));
			}
		}
		// nothing past them, and every open block of the room walked once
		assertHidden(r, 5, 0, "rock");
		assertEquals(7 * 7 * (top - PY + 1), r.opened);
	}

	@Test
	void caveBehindRockStaysHiddenHoweverClose() {
		FakeWorld w = rock();
		int top = room(w);
		// three blocks of rock (x 4..6), then a second cave
		w.fill(7, PY, -3, 11, top, 3, "minecraft:cave_air");
		Reveal.Result r = new Reveal().walk(w, 0, PY, 0, R);
		for (int x = 5; x <= 11; x++) {
			for (int z = -3; z <= 3; z++) {
				assertHidden(r, x, z, "the cave behind the rock");
			}
		}
		assertEquals(7 * 7 * (top - PY + 1), r.opened, "only the player's own room walked");
	}

	@Test
	void caveRoundACornerIsRevealed() {
		FakeWorld w = rock();
		room(w);
		// a passage south out of the room, then east, into a chamber no straight line from the player reaches
		w.fill(0, PY, 4, 0, PY + 1, 12, "minecraft:cave_air");
		w.fill(0, PY, 12, 10, PY + 1, 12, "minecraft:cave_air");
		w.fill(11, PY, 10, 15, PY + 3, 14, "minecraft:cave_air");
		Reveal.Result r = new Reveal().walk(w, 0, PY, 0, R);
		for (int x = 11; x <= 15; x++) {
			for (int z = 10; z <= 14; z++) {
				assertEquals(PY + 3, r.topAt(x, z, band(PY)), "chamber at " + x + "," + z);
			}
		}
		assertEquals(PY + 1, r.topAt(5, 12, band(PY)), "the passage");
		// the rock between the room and the chamber is not
		assertHidden(r, 8, 6, "the rock inside the corner");
	}

	@Test
	void nothingBeyondTheRadius() {
		FakeWorld w = rock();
		// a passage south to z 17, then east to x 30: inside the square of the result, partly outside the sphere
		w.fill(0, PY, 0, 0, PY + 1, 17, "minecraft:cave_air");
		w.fill(0, PY, 17, 30, PY + 1, 17, "minecraft:cave_air");
		Reveal.Result r = new Reveal().walk(w, 0, PY, 0, R);
		// 16^2 + 17^2 = 545 <= 576 < 17^2 + 17^2
		assertEquals(PY + 1, r.topAt(16, 17, band(PY)));
		for (int x = 17; x <= 30; x++) {
			assertHidden(r, x, 17, "past the radius");
		}
	}

	@Test
	void unloadedChunksStopTheWalk() {
		FakeWorld w = rock().load(-2, 0);
		w.fill(0, PY, 0, 23, PY + 1, 0, "minecraft:cave_air");
		Reveal.Result r = new Reveal().walk(w, 0, PY, 0, R);
		assertEquals(PY + 1, r.topAt(15, 0, band(PY)), "the last loaded column");
		for (int x = 16; x <= 23; x++) {
			// not loaded: neither open nor wall, simply unknown
			assertHidden(r, x, 0, "unloaded");
		}
		// once the chunk is there, the passage goes on
		w.load(-2, 1);
		Reveal.Result loaded = new Reveal().walk(w, 0, PY, 0, R);
		assertEquals(PY + 1, loaded.topAt(20, 0, band(PY)));
	}

	@Test
	void lavaBoundsTheWalk() {
		FakeWorld w = rock();
		w.fill(0, PY, 0, 12, PY + 1, 0, "minecraft:cave_air");
		w.fill(6, PY, 0, 8, PY + 1, 0, "minecraft:lava");
		Reveal.Result r = new Reveal().walk(w, 0, PY, 0, R);
		assertEquals(PY + 1, r.topAt(5, 0, band(PY)));
		assertTrue(r.wallAt(6, 0, band(PY)), "the lava's face is seen");
		assertEquals(Reveal.NOTHING, r.topAt(6, 0, band(PY)), "but not walked into");
		for (int x = 7; x <= 12; x++) {
			assertHidden(r, x, 0, "past the lava");
		}
	}

	@Test
	void waterIsWalkedThrough() {
		FakeWorld w = rock();
		w.fill(0, PY, 0, 12, PY + 1, 0, "minecraft:cave_air");
		w.fill(6, PY, 0, 8, PY + 1, 0, "minecraft:water");
		Reveal.Result r = new Reveal().walk(w, 0, PY, 0, R);
		assertEquals(PY + 1, r.topAt(7, 0, band(PY)), "the flooded stretch");
		assertEquals(PY + 1, r.topAt(12, 0, band(PY)), "the dry passage past it");
	}

	// --- big caverns -----------------------------------------------------------------------------------------------

	private static final int AIR = Sig.code(Sig.NONE, Sig.OPEN);
	private static final int STONE = Sig.code(Sig.EARTH, Sig.SOLID);

	/** One vast cavern, open everywhere within 200 blocks of the origin. */
	private static final BlockSource CAVERN = (x, y, z) -> Math.abs(x) < 200 && Math.abs(y) < 200 && Math.abs(z) < 200 ? AIR : STONE;

	private static int latticePointsInSphere(int r) {
		int n = 0;
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z <= r * r) {
						n++;
					}
				}
			}
		}
		return n;
	}

	@Test
	void hugeCavernIsWalkedUpToTheCap() {
		Reveal.Result nether = new Reveal().walk(CAVERN, 0, 0, 0, Reveal.NETHER_RADIUS);
		assertTrue(latticePointsInSphere(Reveal.NETHER_RADIUS) > Reveal.MAX_OPEN, "the Nether sphere is bigger than the cap");
		assertEquals(Reveal.MAX_OPEN, nether.opened);
		// the overworld sphere fits under it, and is walked whole
		Reveal.Result over = new Reveal().walk(CAVERN, 0, 0, 0, R);
		assertEquals(latticePointsInSphere(R), over.opened);
		assertTrue(over.opened < Reveal.MAX_OPEN);
	}

	@Test
	void repeatedWalksWithOneRevealAgree() {
		FakeWorld w = rock();
		room(w);
		w.fill(0, PY, 4, 0, PY + 1, 12, "minecraft:cave_air");
		w.fill(7, PY, -3, 11, PY + 4, 3, "minecraft:cave_air");
		Reveal reveal = new Reveal();
		Reveal.Result first = reveal.walk(w, 0, PY, 0, R);
		// a bigger walk between, and a smaller one: the stamps of the old walks must not leak into the next
		reveal.walk(CAVERN, 0, 0, 0, Reveal.NETHER_RADIUS);
		reveal.walk(w, 0, PY, 0, 8);
		Reveal.Result again = reveal.walk(w, 0, PY, 0, R);
		Reveal.Result fresh = new Reveal().walk(w, 0, PY, 0, R);
		for (Reveal.Result r : new Reveal.Result[]{again, fresh}) {
			assertEquals(first.opened, r.opened);
			assertEquals(first.band0, r.band0);
			assertArrayEquals(first.top, r.top);
			assertArrayEquals(first.wall, r.wall);
		}
	}

	@Test
	void bigCavernWalkIsFastAndDeterministic() {
		Reveal reveal = new Reveal();
		Reveal.Result first = reveal.walk(CAVERN, 3, 40, -7, R);
		for (int i = 0; i < 5; i++) {
			reveal.walk(CAVERN, 3, 40, -7, R);
		}
		long[] times = new long[7];
		for (int i = 0; i < times.length; i++) {
			long t0 = System.nanoTime();
			Reveal.Result r = reveal.walk(CAVERN, 3, 40, -7, R);
			times[i] = System.nanoTime() - t0;
			assertArrayEquals(first.top, r.top);
			assertArrayEquals(first.wall, r.wall);
		}
		Arrays.sort(times);
		double median = times[times.length / 2] / 1e6;
		System.out.printf("Reveal: radius %d walk of %d open blocks, median %.2f ms (best %.2f ms)%n", R, first.opened, median, times[0] / 1e6);
		assertTrue(median < 50, "median " + median + " ms");
	}
}
