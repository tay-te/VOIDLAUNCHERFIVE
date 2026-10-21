package dev.voidmc.hearth.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Speech and the law's checks as they happen (request 13 of docs/requests/mettle-speech-stealth.md, accepted by Hearth
 * 2026-10-21, not yet in Hearth): award reasons stay as they are; these say whether a check was won. Fired on the server
 * thread. Mettle's copy: Mettle registers on them only when the loaded Hearth declares them.
 */
public final class StandingEvents {
	/** A speech check (persuade, intimidate, bribe, flatter) in conversation. */
	public static final Event<Check> SPEECH_CHECK = EventFactory.createArrayBacked(Check.class, ls -> (p, s, kind, passed, difficulty) -> {
		for (Check l : ls) {
			l.onCheck(p, s, kind, passed, difficulty);
		}
	});

	/** A check with the law's guards and witnesses (persuade, hush, threaten). */
	public static final Event<Check> LAW_CHECK = EventFactory.createArrayBacked(Check.class, ls -> (p, s, kind, passed, difficulty) -> {
		for (Check l : ls) {
			l.onCheck(p, s, kind, passed, difficulty);
		}
	});

	private StandingEvents() {
	}

	@FunctionalInterface
	public interface Check {
		void onCheck(ServerPlayer player, @Nullable Settlement settlement, String kind, boolean passed, int difficulty);
	}
}
