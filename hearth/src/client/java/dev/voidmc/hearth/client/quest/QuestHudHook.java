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

	/** The room the quest panel takes now, or null when no quest is tracked or the HUD is hidden. Client thread. */
	public static @Nullable Rect rect() {
		QuestHud.Layout l = QuestHud.layout(net.minecraft.client.Minecraft.getInstance());
		return l == null ? null : l.rect();
	}

	/** Set while a map tracks the quest (compass, beam): the panel then leaves out its own mini-compass. */
	public static void setCompassCovered(boolean covered) {
		compassCovered = covered;
	}

	static boolean compassCovered() {
		return compassCovered;
	}
}
