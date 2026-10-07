package dev.voidmc.mettle.character.moment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The progress toast queue (merge, order, timing, overflow) and the chime throttle. */
class MomentQueueTest {
	private static final String MINING = "mettle:mining";
	private static final String BLADES = "mettle:blades";

	private static MomentQueue.Entry level(MomentQueue q, String skill, int level) {
		return q.offer(MomentKind.LEVEL, skill, null, level, 0, 0);
	}

	@Test
	void showsOneAtATimeInOrder() {
		MomentQueue q = new MomentQueue();
		level(q, MINING, 3);
		q.offer(MomentKind.LEARNED, MINING, "mettle:mining/quarryman", 0, 0, 1);
		assertNull(q.current(), "nothing shows before the clock moves");
		q.advance(0);
		assertEquals(MomentKind.LEVEL, q.current().kind());
		assertEquals(1, q.pendingCount());
		// Something waits, so the first toast gives way after the busy time.
		q.advance(MomentQueue.BUSY_MS - 1);
		assertEquals(MomentKind.LEVEL, q.current().kind());
		q.advance(1);
		assertEquals(MomentKind.LEARNED, q.current().kind());
		q.advance(MomentQueue.SHOW_MS);
		assertNull(q.current());
		assertEquals(0f, q.visibility());
	}

	@Test
	void aLoneToastStaysTheFullTimeAndFades() {
		MomentQueue q = new MomentQueue();
		level(q, MINING, 3);
		q.advance(0);
		assertEquals(0f, q.visibility(), 1e-6, "slides in from nothing");
		q.advance(MomentQueue.IN_MS);
		assertEquals(1f, q.visibility(), 1e-6);
		q.advance(MomentQueue.SHOW_MS - MomentQueue.IN_MS - MomentQueue.OUT_MS / 2);
		assertEquals(0.5f, q.visibility(), 0.01f, "half way through the fade out");
		q.advance(MomentQueue.OUT_MS / 2);
		assertNull(q.current());
	}

	@Test
	void rapidLevelsOfOneSkillMergeIntoTheShowingToast() {
		MomentQueue q = new MomentQueue();
		level(q, MINING, 3);
		q.advance(1000);
		MomentQueue.Entry merged = level(q, MINING, 4);
		assertSame(q.current(), merged);
		assertEquals(4, merged.level());
		assertEquals(1, merged.repeats());
		assertEquals(0, q.pendingCount());
		// It stays up long enough to read the new number.
		q.advance(MomentQueue.SHOW_MS - MomentQueue.IN_MS - 1);
		assertSame(merged, q.current());
	}

	@Test
	void aTierUpgradesAMergedLevel() {
		MomentQueue q = new MomentQueue();
		level(q, BLADES, 1);
		q.advance(0);
		level(q, MINING, 5);
		MomentQueue.Entry e = q.offer(MomentKind.TIER, MINING, null, 6, 2, 0);
		assertEquals(1, q.pendingCount(), "merged into the waiting Mining toast");
		assertEquals(MomentKind.TIER, e.kind());
		assertEquals(6, e.level());
		assertEquals(2, e.tier());
		// A later plain level keeps the tier.
		level(q, MINING, 7);
		assertEquals(MomentKind.TIER, e.kind());
		assertEquals(7, e.level());
	}

	@Test
	void ranksOfOnePerkMergeButDifferentPerksQueue() {
		MomentQueue q = new MomentQueue();
		q.offer(MomentKind.LEARNED, MINING, "mettle:mining/quarryman", 0, 0, 1);
		q.offer(MomentKind.LEARNED, MINING, "mettle:mining/quarryman", 0, 0, 2);
		q.offer(MomentKind.LEARNED, MINING, "mettle:mining/quarryman", 0, 0, 3);
		q.offer(MomentKind.LEARNED, MINING, "mettle:mining/prospector", 0, 0, 1);
		assertEquals(2, q.pendingCount());
		q.advance(0);
		assertEquals(3, q.current().rank());
		assertEquals(4, q.offered());
	}

	@Test
	void aFadingToastDoesNotSwallowARepeat() {
		MomentQueue q = new MomentQueue();
		level(q, MINING, 3);
		q.advance(0);
		q.advance(MomentQueue.SHOW_MS - MomentQueue.OUT_MS / 2);
		MomentQueue.Entry e = level(q, MINING, 4);
		assertFalse(e == q.current(), "a new toast, not a merge into one that is leaving");
		assertEquals(1, q.pendingCount());
		// And the leaving one is not cut short.
		assertTrue(q.visibility() > 0.4f);
	}

	@Test
	void aNewArrivalShortensTheShowingToastWithoutCuttingItOff() {
		MomentQueue q = new MomentQueue();
		level(q, MINING, 3);
		q.advance(0);
		q.advance(2500);
		// Past the busy time already: it starts fading now instead of vanishing.
		level(q, BLADES, 2);
		assertEquals(1f, q.visibility(), 1e-6);
		q.advance(MomentQueue.OUT_MS - 1);
		assertEquals(MomentKind.LEVEL, q.current().kind());
		assertEquals(MINING, q.current().skill());
		q.advance(1);
		assertEquals(BLADES, q.current().skill());
	}

	@Test
	void overflowDropsTheOldestBeforeAnythingEarned() {
		MomentQueue q = new MomentQueue();
		q.offer(MomentKind.FEAT, MINING, "mettle:mining/diamond_hands", 0, 0, 1);
		String[] skills = {"a", "b", "c", "d", "e", "f"};
		for (String s : skills) {
			level(q, "mettle:" + s, 1);
		}
		assertEquals(MomentQueue.MAX_PENDING, q.pendingCount());
		q.advance(0);
		assertEquals(MomentKind.FEAT, q.current().kind(), "the feat survived the overflow");
		q.advance(MomentQueue.BUSY_MS);
		assertEquals("mettle:c", q.current().skill(), "the two oldest level toasts were dropped");
	}

	@Test
	void seenAndClear() {
		MomentQueue q = new MomentQueue();
		level(q, MINING, 2);
		q.offer(MomentKind.SYNERGY, MINING, "mettle:mining/forgemaster", 0, 0, 1);
		assertEquals(Set.of(MomentKind.LEVEL, MomentKind.SYNERGY), q.seen());
		q.clear();
		assertTrue(q.seen().isEmpty());
		assertEquals(0, q.offered());
		q.advance(1000);
		assertNull(q.current());
	}

	@Test
	void throttleKeepsChimesFromPilingUp() {
		SoundThrottle t = new SoundThrottle();
		assertTrue(t.allow(MomentKind.LEVEL, 0));
		assertFalse(t.allow(MomentKind.LEVEL, 500), "a second level chime so soon is skipped");
		assertFalse(t.allow(MomentKind.CHARACTER, 500), "the character level in the same breath is quieter, skipped");
		assertTrue(t.allow(MomentKind.TIER, 600), "a tier opening rings over a plain level");
		assertFalse(t.allow(MomentKind.TIER, 700));
		assertTrue(t.allow(MomentKind.LEVEL, 600 + MomentKind.LEVEL.soundIntervalMs()));
		// Groups are separate: a perk click is not held back by the level chime.
		assertTrue(t.allow(MomentKind.LEARNED, 700));
		assertFalse(t.allow(MomentKind.LEARNED, 750));
		assertTrue(t.allow(MomentKind.LEARNED, 800));
		assertTrue(t.allow(MomentKind.FEAT, 800));
		assertFalse(t.allow(MomentKind.SYNERGY, 900), "a synergy earned with a feat shares one fanfare");
	}
}
