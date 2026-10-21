package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.StandingEvents;

/**
 * Hearth's speech and law check events (request 13). Only loaded when the running Hearth has {@code StandingEvents}
 * (HearthCompat checks first), so a Hearth from before ae4da055 never meets this class.
 */
final class HearthCheckEvents {
	private HearthCheckEvents() {
	}

	static void init() {
		StandingEvents.SPEECH_CHECK.register((player, settlement, kind, passed, difficulty) ->
			HearthChecks.checked(player, HearthChecks.TALK + kind, passed, false));
		StandingEvents.LAW_CHECK.register((player, settlement, kind, passed, difficulty) ->
			HearthChecks.checked(player, HearthChecks.LAW + kind, passed, true));
		HearthChecks.events = true;
	}
}
