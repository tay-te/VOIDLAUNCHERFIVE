package dev.voidmc.hearth.standing.speech;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlayerSkills;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * A character mod's "second word" ({@code hearth:speech/retry}, docs/requests/mettle-speech-stealth.md §12): this many
 * failed conversation checks a sim day are taken back, leaving no memory with the resident, so the player may try again
 * at once. Without a skills mod the budget is 0 and nothing changes. Kept in memory only (a restart gives a fresh day's
 * budget, which is harmless). Server thread only.
 */
public final class SpeechRetries {
	/** The most retries a day a bonus may give (Hearth's clamp). */
	public static final int MAX = 3;
	private static final Ledger LEDGER = new Ledger();

	private SpeechRetries() {
	}

	/** Uses one of the player's retries for today if they have one left. */
	public static boolean take(ServerPlayer player, long day) {
		int budget = budget(PlayerSkills.bonus(HearthServices.playerSkills(), player, PlayerSkills.BONUS_SPEECH_RETRY, 0, MAX));
		return budget > 0 && LEDGER.take(player.getUUID(), day, budget);
	}

	/** The bonus as whole retries, 0–{@link #MAX}. */
	public static int budget(double bonus) {
		return (int) Math.floor(Math.clamp(Double.isNaN(bonus) ? 0 : bonus, 0, MAX));
	}

	public static void clear() {
		LEDGER.used.clear();
	}

	/** Retries used per player per day (conversations and the law share one budget). Pure. */
	public static final class Ledger {
		private final Map<UUID, long[]> used = new HashMap<>();

		/** Uses one retry of {@code budget} on {@code day}; false when none is left. */
		public boolean take(UUID player, long day, int budget) {
			if (budget <= 0) {
				return false;
			}
			long[] u = this.used.computeIfAbsent(player, k -> new long[] {day, 0});
			if (u[0] != day) {
				u[0] = day;
				u[1] = 0;
			}
			if (u[1] >= budget) {
				return false;
			}
			u[1]++;
			if (this.used.size() > 4096) {
				this.used.entrySet().removeIf(e -> e.getValue()[0] != day);
			}
			return true;
		}
	}
}
