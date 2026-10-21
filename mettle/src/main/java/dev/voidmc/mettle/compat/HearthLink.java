package dev.voidmc.mettle.compat;

/**
 * What the loaded VOID Hearth can hear from Mettle, for code outside {@code compat.hearth} (no Hearth types here). Set
 * once by {@code HearthCompat.init}, from the running Hearth's own classes (the {@code src/hearthApi} copy is never
 * packaged); without Hearth all stay false. A Hearth from before ae4da055 lacks requests 7-14, so each is checked:
 *
 * <p>{@link #bonus()}: Hearth's {@code PlayerSkills} declares {@code bonus(player, key)} (request 12). Until it does, every
 * perk that reaches Hearth through a bonus key uses its documented fallback instead (docs/BUILD_FEEL.md §14), never both
 * (see {@link HearthChannels}). {@link #checks()}: it fires {@code StandingEvents} (request 13). {@link #broker()}: it
 * lists {@code VirtualGoods} at a card broker (request 7).
 */
public final class HearthLink {
	private static volatile boolean hearth;
	private static volatile boolean bonus;
	private static volatile boolean checks;
	private static volatile boolean broker;

	private HearthLink() {
	}

	/** Called once by HearthCompat when Hearth is loaded (and by tests). */
	public static void set(boolean hearthLoaded, boolean bonusDeclared) {
		set(hearthLoaded, bonusDeclared, false, false);
	}

	public static void set(boolean hearthLoaded, boolean bonusDeclared, boolean checkEvents, boolean cardBroker) {
		hearth = hearthLoaded;
		bonus = hearthLoaded && bonusDeclared;
		checks = hearthLoaded && checkEvents;
		broker = hearthLoaded && cardBroker;
	}

	public static boolean hearth() {
		return hearth;
	}

	/** Whether the loaded Hearth reads bonus keys. False without Hearth. */
	public static boolean bonus() {
		return bonus;
	}

	/** Whether the loaded Hearth tells whether speech and law checks were won. */
	public static boolean checks() {
		return checks;
	}

	/** Whether the loaded Hearth has a card broker Mettle's cards are registered with. */
	public static boolean broker() {
		return broker;
	}
}
