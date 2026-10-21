package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.skill.SpeechSkill;
import dev.voidmc.mettle.character.skill.speech.SpeechRules;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Speech's feats and XP perks on what Hearth reports (docs/BUILD_FEEL.md §14.2): Market Regular (struck haggles, +25 %
 * haggle XP) and Golden Mouth (Hearth speech checks won, then full XP for a failed one). The award reasons come through
 * {@link MettlePlayerSkills#award}; whether a check was won comes from Hearth's {@code StandingEvents} (request 13,
 * {@link HearthCheckEvents}), which a Hearth before ae4da055 lacks: then Golden Mouth counts the checks tried instead.
 */
final class HearthChecks {
	static final String HAGGLE = "hearth:economy/haggle";
	static final String TALK = "hearth:speech/";
	static final String LAW = "hearth:law/";
	/** The law's speech checks (the XP reasons under {@code hearth:law/} that are checks, as Hearth's LAW_CHECK kinds). */
	static final Set<String> LAW_KINDS = Set.of("persuade_guard", "persuade_search", "bribe_guard", "bribe_search", "bribe_witness", "threaten_witness");

	/** Whether the loaded Hearth fires its check events (set once at init). */
	static volatile boolean events;

	private record Last(String reason, double xp, long tick) {
	}

	/** The last speech-check XP Hearth awarded each player: a failure's top-up needs it (the award comes just before the event). */
	private static final Map<UUID, Last> LAST = new HashMap<>();

	private HearthChecks() {
	}

	static void forget(UUID player) {
		LAST.remove(player);
	}

	static boolean speechCheck(String reason) {
		return reason.startsWith(TALK) && !reason.equals(TALK + "retry") || reason.startsWith(LAW) && LAW_KINDS.contains(reason.substring(LAW.length()));
	}

	/** Speech XP Hearth awards, before Mettle's multipliers: Market Regular's haggles, and what Golden Mouth needs to know. */
	static double awarded(ServerPlayer player, String reason, double xp) {
		if (reason.equals(HAGGLE)) {
			SpeechSkill.tally(player, SpeechSkill.MARKET_REGULAR_COUNTER, SpeechRules.MARKET_REGULAR_HAGGLE);
			return Perks.has(player, SpeechSkill.MARKET_REGULAR) ? xp * (1 + SpeechRules.MARKET_REGULAR) : xp;
		}
		if (speechCheck(reason)) {
			if (!events) {
				SpeechSkill.tally(player, SpeechSkill.GOLDEN_MOUTH_COUNTER, SpeechRules.GOLDEN_MOUTH_TRIED);
			}
			LAST.put(player.getUUID(), new Last(reason, xp, player.level().getGameTime()));
		}
		return xp;
	}

	/** A check Hearth rolled ({@code reason}: the award reason it matches). Won: Golden Mouth's tally; lost: its full XP. */
	static void checked(ServerPlayer player, String reason, boolean passed, boolean law) {
		if (passed) {
			SpeechSkill.tally(player, SpeechSkill.GOLDEN_MOUTH_COUNTER, SpeechRules.GOLDEN_MOUTH_WON);
			return;
		}
		Last last = LAST.remove(player.getUUID());
		if (last == null || !last.reason().equals(reason) || last.tick() != player.level().getGameTime() || !Perks.has(player, SpeechSkill.GOLDEN_MOUTH)) {
			return;
		}
		double more = SpeechRules.goldenMouthTopUp(last.xp(), law);
		if (more > 0 && !player.isCreative()) {
			Progress.award(player, SpeechSkill.SPEECH, more);
			SpeechSkill.chip(player, SpeechSkill.GOLDEN_MOUTH, "golden_mouth");
		}
	}
}
