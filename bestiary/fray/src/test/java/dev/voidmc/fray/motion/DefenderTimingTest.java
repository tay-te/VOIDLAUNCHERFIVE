package dev.voidmc.fray.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * Defender-favoured timing (ANIM_EVENTS.md §4): the clock maths at 0, 150 and 250 ms round trips, as the server runs it.
 *
 * <p>A zombie's claw is ACTIVE from tick {@code H} of the server's clock. A defender whose round trip is {@code r} ms
 * (half each way) sees that tick on their screen at their client time {@code H} (their clock trails the server's by the
 * downlink) and presses dodge at client time {@code P}; the request reaches the server at {@code P + r}. The server
 * held the hit from {@code H} for the grace; the request's (clamped) stamp decides whether its i-frames covered
 * {@code H}.
 */
class DefenderTimingTest {
	/** A 12-tick dodge with i-frames 1-7 (perfect 1-3), as player/dodge_left. */
	private static final Timeline DODGE = new Timeline(Identifier.parse("bestiary:player/dodge_left"), "x", HumanoidRig.ID, "", 12, 1, new int[0][],
		List.of(), Map.of(), Map.of(), Map.of("iframes", new int[] {1, 7}, "perfect", new int[] {1, 3}), 0, -1, "");

	/** The server's decision for one press, the way MotionServer holds and re-checks hits. */
	static DefenderTiming.Window outcome(int hitTick, int pressClientTick, int rttMs, int cap) {
		int rttTicks = DefenderTiming.rttTicks(rttMs);
		long receipt = pressClientTick + rttTicks;
		long stamp = DefenderTiming.clamp(pressClientTick, receipt, rttMs, cap, Long.MIN_VALUE);
		int grace = DefenderTiming.grace(rttMs, cap);
		if (receipt <= hitTick) {
			// The request was in before the hit: its window decides at once.
			return DefenderTiming.windowAt(DODGE, stamp, hitTick);
		}
		if (receipt > hitTick + grace) {
			// The hold ran out first: the hit landed.
			return DefenderTiming.Window.NONE;
		}
		return DefenderTiming.windowAt(DODGE, stamp, hitTick);
	}

	@Test
	void inTimeOnScreenIsSafeAtEveryLatency() {
		int h = 100;
		for (int rtt : new int[] {0, 150, 250}) {
			StringBuilder row = new StringBuilder("rtt " + rtt + " ms: ");
			for (int offset = -9; offset <= 3; offset++) {
				DefenderTiming.Window w = outcome(h, h + offset, rtt, 5);
				row.append(offset).append('=').append(w).append(' ');
				// i-frames cover stamp+1 .. stamp+7: presses 1 to 7 ticks before the hit (on the defender's screen) dodge it.
				boolean inTime = offset >= -7 && offset <= -1;
				assertEquals(inTime, w != DefenderTiming.Window.NONE, "rtt " + rtt + ", pressed " + offset + " ticks from the hit: " + w);
				if (offset >= -3 && offset <= -1) {
					assertEquals(DefenderTiming.Window.PERFECT, w, "the first i-frame ticks are a perfect dodge");
				}
			}
			System.out.println(row);
		}
	}

	@Test
	void withoutTheGraceLateArrivalsLose() {
		// The control: no defender timing (cap 0). At 250 ms, a press two ticks before the hit on screen arrives 3 ticks
		// after it on the server, and the server judges by arrival: hit.
		assertEquals(DefenderTiming.Window.NONE, outcome(100, 98, 250, 0));
		assertEquals(DefenderTiming.Window.NONE, outcome(100, 98, 150, 0));
		// With no latency the press is already in: dodged either way.
		assertTrue(outcome(100, 98, 0, 0) != DefenderTiming.Window.NONE);
	}

	@Test
	void theWindowIsBounded() {
		// RTT + 1 tick, never more than the cap (5 ticks, 250 ms).
		assertEquals(1, DefenderTiming.grace(0, 5));
		assertEquals(4, DefenderTiming.grace(150, 5));
		assertEquals(5, DefenderTiming.grace(250, 5));
		assertEquals(5, DefenderTiming.grace(900, 5));
		assertEquals(0, DefenderTiming.grace(150, 0));
		// PvP's cap.
		assertEquals(2, DefenderTiming.grace(250, DefenderTiming.PVP_CAP_TICKS));
	}

	@Test
	void cheatsAreClamped() {
		long receipt = 200;
		// Claims more lag than measured (50 ms measured, stamp 8 ticks old): clamped to receipt - (1 + 1).
		assertEquals(198, DefenderTiming.clamp(192, receipt, 50, 5, Long.MIN_VALUE));
		// A huge measured RTT can't widen past the cap.
		assertEquals(195, DefenderTiming.clamp(150, receipt, 2000, 5, Long.MIN_VALUE));
		// A stamp from the future is the receipt.
		assertEquals(200, DefenderTiming.clamp(260, receipt, 50, 5, Long.MIN_VALUE));
		// Never before the previous action's stamp (no re-using an old moment twice).
		assertEquals(199, DefenderTiming.clamp(196, receipt, 250, 5, 199));
		// A late presser who fakes an early stamp gains nothing beyond the measured RTT: pressed 6 ticks after the hit
		// (screen), claims 3 before, 50 ms measured: stamp clamped to receipt - 2, i-frames start after the hit.
		int hit = 100;
		long fakeReceipt = hit + 6 + 1;
		long stamp = DefenderTiming.clamp(hit - 3, fakeReceipt, 50, 5, Long.MIN_VALUE);
		assertFalse(DefenderTiming.windowAt(DODGE, stamp, hit) != DefenderTiming.Window.NONE, "stamp " + stamp);
	}

	@Test
	void rttIsTheMedianOfFive() {
		java.util.UUID id = java.util.UUID.randomUUID();
		for (int ms : new int[] {150, 160, 2000, 140, 155}) {
			DefenderTiming.recordRtt(id, ms);
		}
		// One slow echo doesn't widen the window.
		assertEquals(155, DefenderTiming.clock(id).rttMs());
		DefenderTiming.forget(id);
	}
}
