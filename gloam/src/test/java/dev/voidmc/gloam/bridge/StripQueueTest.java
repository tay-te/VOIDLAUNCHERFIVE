package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StripQueueTest {
	private static TileStrip row(int tile, int z) {
		return new TileStrip(tile, -1, false, false, z, 0, new int[256], null, 0, 0, z);
	}

	@Test
	void aSpreadableBatchGoesUpABudgetAtATimeAndInOrder() {
		StripQueue q = new StripQueue();
		List<TileStrip> big = new ArrayList<>();
		for (int z = 0; z < 512; z++) {
			big.add(row(Contract.W0, z));
		}
		q.post(new TileStrip.Batch(big, true));
		q.post(new TileStrip.Batch(List.of(row(Contract.W1, 7)), true));
		byte[] bytes = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
		List<Integer> order = new ArrayList<>();
		for (int tick = 0; tick < 4; tick++) {
			q.drain(bytes, (t, c, l) -> {
			}, 128, s -> order.add(s.base()));
			assertEquals(128, q.lastLines);
			assertTrue(q.waiting());
		}
		q.drain(bytes, (t, c, l) -> {
		}, 128, s -> order.add(s.base()));
		assertEquals(1, q.lastLines);
		assertFalse(q.waiting());
		assertEquals(513, order.size());
		for (int i = 0; i < 512; i++) {
			assertEquals(i, order.get(i));
		}
	}

	@Test
	void aBatchThatMustStayWholeIsWrittenInOneGo() {
		StripQueue q = new StripQueue();
		List<TileStrip> rows = new ArrayList<>();
		for (int z = 0; z < 128; z++) {
			rows.add(row(Contract.U, z));
		}
		q.post(new TileStrip.Batch(rows, false));
		int[] n = {0};
		q.drain(new byte[Contract.WIDTH * Contract.HEIGHT * 4], (t, c, l) -> {
		}, 1, s -> n[0]++);
		assertEquals(128, n[0]);
		assertFalse(q.waiting());
	}
}
