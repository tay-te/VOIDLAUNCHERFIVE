package dev.voidmc.hearth.client.overlay;

import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import org.jspecify.annotations.Nullable;

/**
 * What the server last told this client for Hearth's event screens, kept whatever draws them (the native Java screens,
 * or a host such as the VOID Hub): the conversation, the notice board, a guard's confrontation, a lockpicking or
 * pickpocketing attempt, the reputation page and the law HUD's facts. The menu-backed screens (market, bank, dice) need
 * no copy here: their views live on the open menu. Every change bumps {@link #version}, which
 * {@link HearthState#revision} folds in. Client thread.
 */
public final class HearthViews {
	private HearthViews() {
	}

	private static long version;

	private static TalkPayloads.@Nullable Show talk;
	private static TalkPayloads.@Nullable Detail talkDetail;
	private static StandingPayloads.@Nullable Talk talkStanding;
	private static QuestPayloads.@Nullable Board board;
	private static LawPayloads.@Nullable Confront confront;
	private static LawPayloads.@Nullable ConfrontDetail confrontDetail;
	private static @Nullable Lockpick lockpick;
	private static LawPayloads.@Nullable Pocket pocket;
	private static StandingPayloads.@Nullable Page reputation;
	private static LawPayloads.Hint hint = LawPayloads.Hint.NONE;
	private static LawPayloads.Status status = LawPayloads.Status.CLEAR;
	private static long statusAt;
	private static LawPayloads.Stealth stealth = new LawPayloads.Stealth(false, 0, false);
	private static StandingPayloads.@Nullable Here here;
	private static QuestPayloads.@Nullable PosterTrack posterTrack;

	/**
	 * A lockpicking attempt as the screens show it between the discrete events (the pins and the pick move natively).
	 *
	 * @param state {@code picking}, {@code opened}, {@code spotted}, {@code noPicks} or {@code gone}
	 */
	public record Lockpick(LawPayloads.LockpickStart start, int picks, String state) {
	}

	/** Something changed that no payload above carries (a client option, a menu view): hosts rebuild. */
	public static void bump() {
		version++;
	}

	public static long version() {
		return version;
	}

	// ---------------------------------------------------------------- the conversation

	public static void talk(TalkPayloads.Show show) {
		if (talk == null || talk.entity() != show.entity()) {
			talkDetail = null;
		}
		talk = show;
		bump();
	}

	public static void talkDetail(TalkPayloads.Detail detail) {
		if (talk != null && talk.entity() == detail.entity()) {
			talkDetail = detail;
			bump();
		}
	}

	public static void talkStanding(StandingPayloads.Talk payload) {
		talkStanding = payload;
		bump();
	}

	/** The conversation ended (the server closed it, the player left, the folk walked off). */
	public static void talkClosed(int entity) {
		if (talk != null && (entity < 0 || talk.entity() == entity)) {
			talk = null;
			talkDetail = null;
			bump();
		}
	}

	public static TalkPayloads.@Nullable Show talk() {
		return talk;
	}

	public static TalkPayloads.@Nullable Detail talkDetail() {
		return talkDetail;
	}

	/** The standing module's line for the open conversation (the last check's outcome), if it is for the same folk. */
	public static StandingPayloads.@Nullable Talk talkStanding() {
		return talk != null && talkStanding != null && talkStanding.entity() == talk.entity() ? talkStanding : null;
	}

	// ---------------------------------------------------------------- quests, the law, standing

	public static void board(QuestPayloads.@Nullable Board payload) {
		board = payload;
		bump();
	}

	public static QuestPayloads.@Nullable Board board() {
		return board;
	}

	public static void confront(LawPayloads.Confront payload) {
		if (!payload.open() || confront == null || confront.entity() != payload.entity()) {
			confrontDetail = null;
		}
		confront = payload.open() ? payload : null;
		bump();
	}

	public static void confrontDetail(LawPayloads.ConfrontDetail detail) {
		if (confront != null && confront.entity() == detail.entity()) {
			confrontDetail = detail;
			bump();
		}
	}

	public static LawPayloads.@Nullable ConfrontDetail confrontDetail() {
		return confrontDetail;
	}

	public static void confrontClosed(int entity) {
		if (confront != null && confront.entity() == entity) {
			confront = null;
			confrontDetail = null;
			bump();
		}
	}

	public static LawPayloads.@Nullable Confront confront() {
		return confront;
	}

	public static void lockpickStart(LawPayloads.LockpickStart start) {
		lockpick = new Lockpick(start, start.picks(), "picking");
		bump();
	}

	public static void lockpickEnd(LawPayloads.LockpickEnd end) {
		if (lockpick == null || lockpick.start().session() != end.session()) {
			return;
		}
		String state = switch (end.reason()) {
			case LawPayloads.LockpickEnd.OPENED -> "opened";
			case LawPayloads.LockpickEnd.SPOTTED -> "spotted";
			case LawPayloads.LockpickEnd.NO_PICKS -> "noPicks";
			case LawPayloads.LockpickEnd.GONE -> "gone";
			default -> lockpick.state();
		};
		lockpick = new Lockpick(lockpick.start(), end.picks(), state);
		bump();
	}

	/** The attempt is over and its screen gone: nothing to show. */
	public static void lockpickClosed() {
		if (lockpick != null) {
			lockpick = null;
			bump();
		}
	}

	public static @Nullable Lockpick lockpick() {
		return lockpick;
	}

	public static void pocket(LawPayloads.Pocket payload) {
		pocket = payload;
		bump();
	}

	public static void pocketClosed() {
		if (pocket != null) {
			pocket = null;
			bump();
		}
	}

	public static LawPayloads.@Nullable Pocket pocket() {
		return pocket;
	}

	public static void reputation(StandingPayloads.Page page) {
		reputation = page;
		bump();
	}

	public static StandingPayloads.@Nullable Page reputation() {
		return reputation;
	}

	public static void hint(LawPayloads.Hint payload) {
		if (!payload.equals(hint)) {
			hint = payload;
			bump();
		}
	}

	public static LawPayloads.Hint hint() {
		return hint;
	}

	public static void status(LawPayloads.Status payload, long now) {
		status = payload;
		statusAt = now;
		bump();
	}

	public static LawPayloads.Status status() {
		return status;
	}

	/** When {@link #status} arrived (milliseconds), to count a sentence down between updates. */
	public static long statusAt() {
		return statusAt;
	}

	public static void stealth(LawPayloads.Stealth payload) {
		if (!payload.equals(stealth)) {
			stealth = payload;
			bump();
		}
	}

	public static LawPayloads.Stealth stealth() {
		return stealth;
	}

	public static void here(StandingPayloads.Here payload) {
		here = payload.none() ? null : payload;
		bump();
	}

	/** The town the player stands in, as the standing module last said, or null. */
	public static StandingPayloads.@Nullable Here here() {
		return here;
	}

	/** The wanted poster the server says the player tracks (null: none). */
	public static void posterTrack(QuestPayloads.PosterTrack payload) {
		QuestPayloads.PosterTrack next = payload.active() ? payload : null;
		if (!java.util.Objects.equals(next, posterTrack)) {
			posterTrack = next;
			bump();
		}
	}

	public static QuestPayloads.@Nullable PosterTrack posterTrack() {
		return posterTrack;
	}

	/** Leaving a world: forget everything. */
	public static void reset() {
		talk = null;
		talkDetail = null;
		talkStanding = null;
		board = null;
		confront = null;
		confrontDetail = null;
		lockpick = null;
		pocket = null;
		reputation = null;
		hint = LawPayloads.Hint.NONE;
		status = LawPayloads.Status.CLEAR;
		stealth = new LawPayloads.Stealth(false, 0, false);
		here = null;
		posterTrack = null;
		bump();
	}
}
