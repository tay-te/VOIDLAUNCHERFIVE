package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.gloam.compat.Expanse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LandTilesTest {
	/** A source whose N0 texel is the cell's x and N1 its z, so the texture says which cell each texel holds. */
	private static final LandTiles.CellSource WHERE = (cx, cz) -> (long) cz << 32 | cx & 0xFFFFFFFFL;

	private final byte[] bytes = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
	private final List<int[]> marks = new ArrayList<>();
	private int centreX, centreZ;

	/** Steps until there is nothing left to do and writes every strip; returns how many strips came. */
	private int settle(LandTiles land, int tx, int tz) {
		while (land.step(WHERE, tx, tz, false)) {
			// the worker's loop
		}
		int strips = 0;
		LandTiles.Strip s;
		while ((s = land.poll()) != null) {
			LandTiles.write(this.bytes, s, (tile, column, line) -> this.marks.add(new int[]{tile, column ? 1 : 0, line}));
			this.centreX = s.centreX();
			this.centreZ = s.centreZ();
			strips++;
		}
		return strips;
	}

	private void assertWindowHolds(int cx, int cz) {
		assertEquals(cx, this.centreX);
		assertEquals(cz, this.centreZ);
		for (int x = cx - 127; x <= cx + 127; x++) {
			for (int z = cz - 127; z <= cz + 127; z++) {
				int o0 = Contract.offset(Contract.texelX(Contract.N0, x), Contract.texelY(Contract.N0, z));
				int o1 = Contract.offset(Contract.texelX(Contract.N1, x), Contract.texelY(Contract.N1, z));
				if (Contract.getInt(this.bytes, o0) != x || Contract.getInt(this.bytes, o1) != z) {
					throw new AssertionError("cell " + x + "," + z + " holds " + Contract.getInt(this.bytes, o0) + "," + Contract.getInt(this.bytes, o1)
						+ " round " + cx + "," + cz);
				}
			}
		}
	}

	@Test
	void aFreshWindowIsClearedThenFilledNearestFirst() {
		LandTiles land = new LandTiles();
		land.step(WHERE, 1000, -50, false);
		LandTiles.Strip clear = land.poll();
		assertTrue(clear.clear());
		land.step(WHERE, 1000, -50, false);
		LandTiles.Strip first = land.poll();
		assertFalse(first.column());
		assertEquals(-50, first.at(), "the camera's own row comes first");
		land.step(WHERE, 1000, -50, false);
		assertEquals(1, Math.abs(land.poll().at() + 50));
	}

	@Test
	void theWindowRollsWithTheCameraOneStripAStep() {
		LandTiles land = new LandTiles();
		assertEquals(257, this.settle(land, 0, 0), "a clear and 256 rows");
		this.assertWindowHolds(0, 0);
		assertFalse(land.filling());

		this.marks.clear();
		assertEquals(1, this.settle(land, 1, 0), "one cell east is one column");
		this.assertWindowHolds(1, 0);
		assertEquals(2, this.marks.size(), "a column each in N0 and N1");
		assertEquals(1, this.marks.get(0)[1]);
		assertEquals(129 & 255, this.marks.get(0)[2], "the column that came into range");

		// a walk about, across the wrap and back, in both directions and diagonally
		int[][] path = {{5, 0}, {5, -7}, {-20, -7}, {-20, 40}, {37, 97}, {-26, 33}, {-26, -30}};
		for (int[] p : path) {
			int steps = Math.abs(p[0] - this.centreX) + Math.abs(p[1] - this.centreZ);
			assertEquals(steps, this.settle(land, p[0], p[1]));
			this.assertWindowHolds(p[0], p[1]);
		}
	}

	@Test
	void aJumpStartsAfreshAndTheRefreshGoesRoundTheRows() {
		LandTiles land = new LandTiles();
		this.settle(land, 0, 0);
		assertEquals(257, this.settle(land, 100_000, -3_000), "a teleport clears and refills");
		this.assertWindowHolds(100_000, -3_000);

		// the rolling refresh redoes one row a call, each in the window
		for (int i = 0; i < 300; i++) {
			assertTrue(land.step(WHERE, 100_000, -3_000, true));
			LandTiles.Strip s = land.poll();
			assertFalse(s.column());
			assertTrue(s.at() >= -3_000 - 127 && s.at() <= -3_000 + 128);
			assertNull(land.poll());
		}
		// nothing to do without a refresh
		assertFalse(land.step(WHERE, 100_000, -3_000, false));
		// another world (another source) starts over
		assertTrue(land.step((cx, cz) -> 0L, 100_000, -3_000, false));
		assertTrue(land.poll().clear());
	}

	@Test
	void expanseLandEncodesIntoTheContract() {
		// a low, wet meadow by a river, at the camera's season
		long c = LandTiles.encode(new Expanse.Raw(0.8F, 0.1F, 18, 40, 5000, 71.5F, "humid_lowland"), 63, 0.5F);
		int n0 = (int) c, n1 = (int) (c >>> 32);
		assertEquals(71.5F, Contract.fromH16(n0 & 255, n0 >>> 8 & 255), 1e-6);
		assertEquals(10, n0 >>> 24 & 255, "40 blocks to the river");
		assertTrue((n0 >>> 16 & 255) > 200, "18 blocks below the land round is nearly a full hollow");
		int a = n1 >>> 24 & 255;
		assertEquals(2, a >> 4, "humid_lowland");
		assertEquals(Contract.CELL_PRESENT | Contract.CELL_EXPANSE, a & 15);
		assertTrue((n1 & 255) > 180, "a wet hollow holds mist");

		// out at sea the ground is the sea's surface, and it is water
		long sea = LandTiles.encode(new Expanse.Raw(0.6F, 0.2F, -5, 1000, -300, 31, "temperate"), 63, 0);
		int s0 = (int) sea, s1 = (int) (sea >>> 32);
		assertEquals(63.0F, Contract.fromH16(s0 & 255, s0 >>> 8 & 255), 1e-6);
		assertEquals(0, s0 >>> 24 & 255);
		assertTrue((s1 >>> 24 & Contract.CELL_WATER) != 0);

		// a hot dry land has no mist, and a cold one lies under snow
		long desert = LandTiles.encode(new Expanse.Raw(0.1F, 0.9F, 10, 1000, 5000, 90, "dusty"), 63, 1);
		assertEquals(0, (int) (desert >>> 32) & 255);
		long alpine = LandTiles.encode(new Expanse.Raw(0.5F, -0.7F, 0, 1000, 5000, 400, "clear_alpine"), 63, 0);
		assertTrue(((int) (alpine >>> 32) >>> 24 & Contract.CELL_SNOW) != 0);
	}
}
