package dev.voidmc.hearth.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Speech checks as they are rolled, for other mods (a character mod counting checks won). Fired on the server thread
 * right after the roll, beside the XP {@link PlayerSkills#award} (whose reasons are unchanged). Added 2026-10-21
 * (docs/requests/mettle-speech-stealth.md §13).
 */
public final class StandingEvents {
	/**
	 * A conversation's speech check (the standing module's: flattery, amends, persuading, threatening, bribing a resident).
	 * {@code kind} is {@code persuade}, {@code intimidate}, {@code bribe} or {@code flatter}.
	 */
	public static final Event<SpeechChecked> SPEECH_CHECK = EventFactory.createArrayBacked(SpeechChecked.class, ls -> (p, s, kind, passed, d) -> {
		for (SpeechChecked l : ls) {
			l.onSpeechCheck(p, s, kind, passed, d);
		}
	});

	/**
	 * The law's speech checks (guards and witnesses). {@code kind} is what was tried: {@code persuade_guard},
	 * {@code persuade_search}, {@code bribe_guard}, {@code bribe_search}, {@code bribe_witness} (hushing a witness) or
	 * {@code threaten_witness}: the same words as the XP reasons under {@code hearth:law/}.
	 */
	public static final Event<SpeechChecked> LAW_CHECK = EventFactory.createArrayBacked(SpeechChecked.class, ls -> (p, s, kind, passed, d) -> {
		for (SpeechChecked l : ls) {
			l.onSpeechCheck(p, s, kind, passed, d);
		}
	});

	private StandingEvents() {
	}

	/**
	 * The plain-check difficulty (1–25, {@link PlayerSkills#check}'s scale: 5 easy, 10 fair, 15 hard, 20 heroic) that a
	 * character with no skills would face at these odds: {@link PlayerSkills#odds} turned round.
	 */
	public static int difficulty(double chance) {
		double d = 1.5 + (0.5 - Math.clamp(chance, 0, 1)) * 20;
		return (int) Math.clamp(Math.round(d), 1, 25);
	}

	/**
	 * @param settlement where it happened, or null
	 * @param kind       what was tried (above)
	 * @param passed     whether the check succeeded
	 * @param difficulty {@link #difficulty} of the odds it was rolled at
	 */
	@FunctionalInterface
	public interface SpeechChecked {
		void onSpeechCheck(ServerPlayer player, @Nullable Settlement settlement, String kind, boolean passed, int difficulty);
	}
}
