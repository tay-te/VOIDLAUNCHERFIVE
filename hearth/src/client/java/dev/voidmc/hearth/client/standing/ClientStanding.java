package dev.voidmc.hearth.client.standing;

import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** What the client knows of the player's standing: as the server last said, nothing decided here. */
final class ClientStanding {
	/** Repeated arrivals at the same town within this long are not announced again (the sim's banner does the same). */
	private static final long REPEAT_MS = 60_000;

	private static StandingPayloads.@Nullable Here here;
	private static StandingPayloads.@Nullable Here shown;
	private static long shownAt;
	private static long lastShownId = Long.MIN_VALUE;
	private static long lastShownTime;
	private static StandingPayloads.@Nullable Talk talk;

	private ClientStanding() {
	}

	static void receive(StandingPayloads.Here payload) {
		here = payload.none() ? null : payload;
		if (payload.none() || !payload.arrived()) {
			if (shown != null && !payload.none() && shown.id() == payload.id()) {
				shown = payload; // keep the line on screen up to date
			}
			return;
		}
		long now = Util.getMillis();
		if (payload.id() == lastShownId && now - lastShownTime < REPEAT_MS) {
			return;
		}
		shown = payload;
		shownAt = now;
		lastShownId = payload.id();
		lastShownTime = now;
	}

	static void receive(StandingPayloads.Talk payload) {
		talk = payload;
	}

	static void reset() {
		here = null;
		shown = null;
		talk = null;
		lastShownId = Long.MIN_VALUE;
	}

	/** The town the player stands in, or null. */
	static StandingPayloads.@Nullable Here here() {
		return here;
	}

	/** The arrival line being shown, or null. */
	static StandingPayloads.@Nullable Here shown() {
		return shown;
	}

	static long shownAt() {
		return shownAt;
	}

	static void hide() {
		shown = null;
	}

	static StandingPayloads.@Nullable Talk talk() {
		return talk;
	}

	static Title title(String name) {
		for (Title t : Title.values()) {
			if (t.getSerializedName().equals(name)) {
				return t;
			}
		}
		return Title.STRANGER;
	}

	static String signed(int v) {
		return v > 0 ? "+" + v : Integer.toString(v);
	}

	static Component fame(int fame, int renown) {
		int tier = fame < 5 ? 0 : fame < 20 ? 1 : fame < 50 ? 2 : fame < 80 ? 3 : 4;
		Component t = Component.translatable("hearth.standing.fame." + tier);
		if (tier == 0 || renown == 0) {
			return t;
		}
		return Component.empty().append(t).append(" (").append(Component.translatable(renown > 0 ? "hearth.standing.renown.good" : "hearth.standing.renown.bad"))
			.append(")");
	}

	static Component charisma(float c) {
		String k = c < -0.5F ? "repellent" : c < -0.15F ? "offputting" : c < 0.15F ? "plain" : c < 0.45F ? "pleasant" : "charming";
		return Component.translatable("hearth.standing.charisma." + k);
	}

	static Component menace(float m) {
		String k = m < 0.2F ? "harmless" : m < 0.45F ? "wary" : m < 0.7F ? "dangerous" : "terrifying";
		return Component.translatable("hearth.standing.menace." + k);
	}

	static Component opinionWord(int opinion) {
		String k = opinion <= -50 ? "hates" : opinion <= -10 ? "dislikes" : opinion < 10 ? "neutral" : opinion < 50 ? "likes" : "loves";
		return Component.translatable("hearth.standing.opinion." + k);
	}
}
