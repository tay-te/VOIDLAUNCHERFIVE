package dev.voidmc.mettle.compat;

import java.util.List;
import java.util.Optional;

/**
 * Rule (a) of docs/BUILD_FEEL.md §14, one channel per effect: every Mettle effect that reaches VOID Hearth does so through
 * exactly one of the effective level ({@code PlayerSkills.level} / {@code attribute}), a bonus key ({@code bonus}) or a
 * price factor ({@code MettlePrices}), never two; and an effect whose key the loaded Hearth can't read yet takes its
 * fallback instead, never on top of it. The code paths read this table (MettlePlayerSkills, MettlePrices, the Stealth
 * hooks) and a JUnit test walks it against the rules that compute each channel.
 */
public final class HearthChannels {
	/** How an effect reaches Hearth. {@link #METTLE}: it doesn't (a Mettle-only stand-in: vanilla mobs, animals, villagers). */
	public enum Channel {
		LEVEL, BONUS, PRICE, METTLE
	}

	/**
	 * One effect of a perk, trait, Teaching or talent that concerns Hearth.
	 *
	 * @param source   the perk, trait or talent id ({@code stealth/cloak_of_dusk}, {@code talent/silver_tongue})
	 * @param effect   what it does at Hearth, in a few words
	 * @param channel  its channel while the loaded Hearth reads bonus keys
	 * @param fallback its channel while it doesn't (the same as {@code channel} when the effect needs no key)
	 * @param key      the bonus key, for {@link Channel#BONUS}
	 */
	public record Route(String source, String effect, Channel channel, Channel fallback, Optional<String> key) {
		public Channel channel(boolean bonus) {
			return bonus ? this.channel : this.fallback;
		}
	}

	private static Route route(String source, String effect, Channel channel) {
		return new Route(source, effect, channel, channel, Optional.empty());
	}

	private static Route keyed(String source, String effect, String key, Channel fallback) {
		return new Route(source, effect, Channel.BONUS, fallback, Optional.of(key));
	}

	/** Stealth (§14.1) and the talent Silver Tongue (§13.2). Speech joins when it ships. */
	public static final List<Route> ROUTES = List.of(
		route("stealth/shadow", "Stealth counts 1 / 2 / 3 higher with Hearth folk", Channel.LEVEL),
		keyed("stealth/cloak_of_dusk", "witnesses see you 25 % nearer in the dark (fallback: Stealth +2 at night)", HearthKeys.STEALTH_SIGHT, Channel.LEVEL),
		keyed("stealth/unseen", "witnesses see you X nearer (fallback: Stealth +1 at night)", HearthKeys.STEALTH_SIGHT, Channel.LEVEL),
		keyed("stealth/ghost", "witnesses can't see you beyond 4 blocks (fallback: Stealth +5 while Ghost holds)", HearthKeys.STEALTH_UNSEEN_BEYOND, Channel.LEVEL),
		keyed("stealth/the_quiet_way", "witnesses 10 % nearer, guards give up a third sooner (fallback: Stealth +2)", HearthKeys.STEALTH_SIGHT, Channel.LEVEL),
		keyed("stealth/light_fingered", "the mark notices you X slower (fallback: animals notice you X closer)", HearthKeys.PICKPOCKET_AWARENESS, Channel.METTLE),
		route("talent/silver_tongue", "market edge +X (without Hearth: villagers' prices)", Channel.PRICE));

	public static Optional<Route> route(String source) {
		return ROUTES.stream().filter(r -> r.source().equals(source)).findFirst();
	}

	/** Whether a source's fallback is on now: only while the loaded Hearth reads no bonus keys. */
	public static boolean fallback(String source) {
		return !HearthLink.bonus() && route(source).map(r -> r.channel() != r.fallback()).orElse(false);
	}

	private HearthChannels() {
	}
}
