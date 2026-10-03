package dev.voidmc.hearth.quest;

/**
 * Hooks the client side of the module fills in, so common code (the journal item) can ask for client things without
 * referring to client classes. No-ops on a dedicated server.
 */
public final class QuestHooks {
	private static Runnable openJournal = () -> {
	};

	private QuestHooks() {
	}

	public static void setOpenJournal(Runnable action) {
		openJournal = action;
	}

	public static void openJournal() {
		openJournal.run();
	}
}
