package dev.voidmc.hearth.client.quest;

import org.jspecify.annotations.Nullable;

/**
 * What other client code (the Atlas plugin) may know of the quest panel and tell it, without either side naming the
 * other's types: the panel's live rectangle, and whether a map already shows where the tracked quest is.
 */
public final class QuestHudHook {
	/** A rectangle in GUI-scaled pixels, from (x0, y0) to (x1, y1). */
	public record Rect(int x0, int y0, int x1, int y1) {
	}

	private static volatile boolean compassCovered;

	private QuestHudHook() {
	}

	/**
	 * The room the quest panel takes now, with the contest line under it (F6, 2026-10-21), or null when neither shows (no
	 * quest tracked and no contest, or the HUD hidden). Client thread.
	 */
	public static @Nullable Rect rect() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		QuestHud.Layout l = QuestHud.layout(mc);
		Rect quest = l == null ? null : l.rect();
		Rect contest = ContestHud.rect(mc);
		if (quest == null || contest == null) {
			return quest != null ? quest : contest;
		}
		return new Rect(Math.min(quest.x0(), contest.x0()), Math.min(quest.y0(), contest.y0()), Math.max(quest.x1(), contest.x1()),
			Math.max(quest.y1(), contest.y1()));
	}

	/** Set while a map tracks the quest (compass, beam): the panel then leaves out its own mini-compass. */
	public static void setCompassCovered(boolean covered) {
		compassCovered = covered;
	}

	static boolean compassCovered() {
		return compassCovered;
	}
}
