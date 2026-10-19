package dev.voidmc.hearth.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The client's copy of what the server said, and the revision hosts watch: every change counts, nothing else does. */
class HearthViewsTest {
	static {
		OverlayFixtures.boot();
	}

	@BeforeEach
	void clean() {
		HearthViews.reset();
	}

	@Test
	void everyPayloadBumpsTheVersion() {
		long v = HearthViews.version();
		HearthViews.talk(OverlayFixtures.talk());
		assertTrue(HearthViews.version() > v);
		long after = HearthViews.version();
		HearthViews.hint(LawPayloads.Hint.NONE);
		assertEquals(after, HearthViews.version(), "the same hint again changes nothing");
		HearthViews.stealth(new LawPayloads.Stealth(true, 0.5F, false));
		assertTrue(HearthViews.version() > after);
	}

	@Test
	void theDetailBelongsToTheConversationItCameWith() {
		HearthViews.talk(OverlayFixtures.talk());
		TalkPayloads.Detail other = new TalkPayloads.Detail(999, "", "", List.of(), 0, 0, "", 0, 0, "", "", 0, 0, 0, List.of());
		HearthViews.talkDetail(other);
		assertNull(HearthViews.talkDetail(), "a detail for another folk is dropped");
		HearthViews.talkDetail(OverlayFixtures.detail());
		assertNotNull(HearthViews.talkDetail());
		// a new conversation with someone else forgets the old card
		HearthViews.talk(new TalkPayloads.Show(5, true, "Tom", "hearth.npc.job.farmer", "Bradfield", Component.literal("Hm?"), List.of()));
		assertNull(HearthViews.talkDetail());
		HearthViews.talkClosed(5);
		assertNull(HearthViews.talk());
	}

	@Test
	void theStandingLineOnlyShowsForTheOpenFolk() {
		HearthViews.talk(OverlayFixtures.talk());
		HearthViews.talkStanding(new StandingPayloads.Talk(3, "Someone else", 10, Component.literal("x")));
		assertNull(HearthViews.talkStanding());
		HearthViews.talkStanding(new StandingPayloads.Talk(OverlayFixtures.AGNES, "Agnes Garland", 18, Component.literal("Persuade passed")));
		assertEquals(18, HearthViews.talkStanding().opinion());
	}

	@Test
	void aLockpickAttemptEndsByItsSession() {
		HearthViews.lockpickStart(new LawPayloads.LockpickStart(4, 3, 99L, 5, 6, Component.literal("A chest")));
		assertEquals("picking", HearthViews.lockpick().state());
		HearthViews.lockpickEnd(new LawPayloads.LockpickEnd(7, LawPayloads.LockpickEnd.OPENED, 6));
		assertEquals("picking", HearthViews.lockpick().state(), "another session's end is ignored");
		HearthViews.lockpickEnd(new LawPayloads.LockpickEnd(4, LawPayloads.LockpickEnd.PICKS, 5));
		assertEquals(5, HearthViews.lockpick().picks());
		assertEquals("picking", HearthViews.lockpick().state());
		HearthViews.lockpickEnd(new LawPayloads.LockpickEnd(4, LawPayloads.LockpickEnd.SPOTTED, 5));
		assertEquals("spotted", HearthViews.lockpick().state());
		HearthViews.lockpickClosed();
		assertNull(HearthViews.lockpick());
	}

	@Test
	void aClosedConfrontationIsGone() {
		HearthViews.confront(OverlayFixtures.confront());
		assertNotNull(HearthViews.confront());
		LawPayloads.Confront c = OverlayFixtures.confront();
		HearthViews.confront(new LawPayloads.Confront(c.entity(), false, c.name(), c.speech(), c.bounty(), c.choices()));
		assertNull(HearthViews.confront());
	}

	@Test
	void theRevisionCountsChangesAndNeverGoesDown() {
		RevisionCounter counter = new RevisionCounter();
		long first = counter.next(10);
		assertEquals(first, counter.next(10), "the same inputs: the same revision");
		long second = counter.next(11);
		assertEquals(first + 1, second);
		assertEquals(second + 1, counter.next(10), "back to old inputs is still a change, and still counts up");
		assertNotEquals(RevisionCounter.mix(17, 1), RevisionCounter.mix(17, 2));
	}

	@Test
	void resetForgetsTheWorld() {
		HearthViews.talk(OverlayFixtures.talk());
		HearthViews.pocket(OverlayFixtures.pocket(LawPayloads.Pocket.OPEN));
		HearthViews.status(new LawPayloads.Status("Bradfield", 50, false, 0, 0, false, false), 0);
		HearthViews.reset();
		assertNull(HearthViews.talk());
		assertNull(HearthViews.pocket());
		assertEquals(0, HearthViews.status().bounty());
	}
}
