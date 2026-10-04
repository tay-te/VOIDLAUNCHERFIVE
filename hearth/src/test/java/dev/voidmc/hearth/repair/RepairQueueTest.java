package dev.voidmc.hearth.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RepairQueueTest {
	private static final UUID ALICE = new UUID(1, 1);
	private static final UUID BOB = new UUID(2, 2);

	@BeforeAll
	static void boot() {
		RepairTestKit.boot();
	}

	private static Damage at(int x, int y, int z, int plot, int stage) {
		return new Damage(BlockPos.asLong(x, y, z), plot, stage, -1, Cause.PLAYER, 3, null, 0);
	}

	@Test
	void onePerPositionAndBounded() {
		RepairQueue q = new RepairQueue();
		assertTrue(q.add(at(0, 64, 0, 1, 0), 2));
		assertFalse(q.add(at(0, 64, 0, 1, 0), 2), "a position is queued once");
		assertTrue(q.add(at(1, 64, 0, 1, 0), 2));
		assertFalse(q.add(at(2, 64, 0, 1, 0), 2), "the queue is bounded");
		assertEquals(2, q.size());
		assertEquals(2, q.countOf(1));
		q.remove(BlockPos.asLong(0, 64, 0));
		assertEquals(1, q.countOf(1));
	}

	@Test
	void claimsNearestEarlierStagesFirstAndHoldsOneAtATime() {
		RepairQueue q = new RepairQueue();
		Damage far = at(30, 64, 0, 1, 0);
		Damage nearLate = at(2, 64, 0, 1, 3);
		Damage nearEarly = at(4, 64, 0, 1, 0);
		q.add(far, 100);
		q.add(nearLate, 100);
		q.add(nearEarly, 100);
		Damage first = q.claim(ALICE, BlockPos.ZERO.atY(64), 100, 0, 1200, d -> true);
		assertSame(nearEarly, first, "the wall before the lamp on it, when both are near");
		Damage second = q.claim(BOB, BlockPos.ZERO.atY(64), 100, 0, 1200, d -> true);
		assertSame(nearLate, second, "a claimed block is not handed out twice");
		Damage again = q.claim(ALICE, BlockPos.ZERO.atY(64), 100, 0, 1200, d -> true);
		assertSame(nearEarly, again, "claiming again releases one's own earlier claim first");
		assertNull(q.claim(BOB, BlockPos.ZERO.atY(64), 10, 0, 1200, d -> d == far), "out of range");
	}

	@Test
	void claimsLapseAndUnusableAreSkipped() {
		RepairQueue q = new RepairQueue();
		Damage a = at(1, 64, 0, 1, 0);
		Damage b = at(2, 64, 0, 1, 0);
		q.add(a, 100);
		q.add(b, 100);
		assertSame(b, q.claim(ALICE, BlockPos.ZERO, 100, 0, 1200, d -> d != a), "the caller can refuse one (no goods)");
		assertEquals(1, q.openCount(10, 1200));
		assertEquals(2, q.openCount(1300, 1200), "a claim runs out");
		assertNotNull(q.claimOf(ALICE, 10, 1200));
		assertNull(q.claimOf(ALICE, 1300, 1200));
	}

	@Test
	void mendedHeldAndDeferredAreNotOpen() {
		Damage d = at(0, 0, 0, 1, 0);
		assertTrue(RepairQueue.open(d, 0, 100));
		d.set(Damage.MENDED, true);
		assertFalse(RepairQueue.open(d, 0, 100), "mended in the record already");
		d.set(Damage.MENDED, false);
		d.set(Damage.NEEDS_PAYMENT, true);
		assertFalse(RepairQueue.open(d, 0, 100), "a deeded house waits for its owner");
		d.set(Damage.PAID, true);
		assertTrue(RepairQueue.open(d, 0, 100));
		d.defer(50);
		assertFalse(RepairQueue.open(d, 10, 100));
		assertTrue(RepairQueue.open(d, 60, 100));
	}

	@Test
	void chunksAndPlots() {
		RepairQueue q = new RepairQueue();
		q.add(at(1, 64, 1, 1, 0), 100);
		q.add(at(17, 64, 1, 1, 0), 100);
		q.add(at(18, 64, 2, 2, 0), 100);
		assertEquals(1, q.inChunk(0, 0).size());
		assertEquals(2, q.inChunk(1, 0).size());
		q.add(at(19, 64, 3, 2, 0), 100);
		assertEquals(3, q.inChunk(1, 0).size(), "the chunk index follows additions");
		assertEquals(2, q.removePlot(2));
		assertEquals(1, q.inChunk(1, 0).size());
		assertEquals(0, q.countOf(2));
	}

	@Test
	void savesAndLoads() {
		RepairQueue q = new RepairQueue();
		Damage d = new Damage(BlockPos.asLong(5, 70, -3), 4, 2, 17, Cause.EXPLOSION, 12, ALICE, Damage.NEEDS_PAYMENT | Damage.REPORTED);
		q.add(d, 100);
		q.add(at(1, 2, 3, 1, 0), 100);
		q.lastAccrual = 123456;
		q.carry = 2.5;
		q.addScaffold(BOB, new BlockPos(1, 65, 1));
		q.claim(BOB, BlockPos.ZERO, 100, 0, 100, x -> true);
		Tag tag = RepairQueue.CODEC.encodeStart(NbtOps.INSTANCE, q).getOrThrow();
		RepairQueue back = RepairQueue.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		assertEquals(2, back.size());
		Damage r = back.get(d.pos);
		assertNotNull(r);
		assertEquals(4, r.plot);
		assertEquals(2, r.stage);
		assertEquals(17, r.index);
		assertEquals(Cause.EXPLOSION, r.cause);
		assertEquals(ALICE, r.offender);
		assertTrue(r.has(Damage.NEEDS_PAYMENT) && r.has(Damage.REPORTED));
		assertEquals(123456, back.lastAccrual);
		assertEquals(2.5, back.carry);
		assertEquals(1, back.scaffoldCount());
		assertTrue(back.all().stream().allMatch(x -> x.claimedBy() == null), "claims are not saved");
	}
}
