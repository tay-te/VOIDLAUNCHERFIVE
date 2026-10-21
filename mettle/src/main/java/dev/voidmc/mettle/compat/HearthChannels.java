package dev.voidmc.mettle.compat;

import java.util.List;
import java.util.Optional;

/**
 * Rule (a) of docs/BUILD_FEEL.md §14, one channel per effect: every Mettle effect that reaches VOID Hearth does so through
 * exactly one of the effective level ({@code PlayerSkills.level} / {@code attribute}), a bonus key ({@code bonus}) or a
 * price factor ({@code MettlePrices}), never two; and an effect whose key the loaded Hearth can't read yet takes its
 * fallback instead, never on top of it. The code paths read this table (MettlePlayerSkills, MettlePrices, the Stealth and
 * Speech hooks) and a JUnit test walks it against the rules that compute each channel. A source with two effects (Voice of
 * the Realm's Speech and Presence; Honeyed Lies' gift and cost; Merchant Prince's edge and never-sour) has one row each.
 */
public final class HearthChannels {
	/**
	 * How an effect reaches Hearth. {@link #METTLE}: it doesn't (a Mettle-only stand-in: vanilla mobs, animals, villagers).
	 * {@link #NONE}: nothing at all (a key's effect without keys, where the design gives it no fallback: the trait is then
	 * not even rolled).
	 */
	public enum Channel {
		LEVEL, BONUS, PRICE, METTLE, NONE
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

	/** Stealth (§14.1), Speech (§14.2) and the talent Silver Tongue (§13.2). */
	public static final List<Route> ROUTES = List.of(
		// Stealth
		route("stealth/shadow", "Stealth counts 1 / 2 / 3 higher with Hearth folk", Channel.LEVEL),
		keyed("stealth/cloak_of_dusk", "witnesses see you 25 % nearer in the dark (fallback: Stealth +2 at night)", HearthKeys.STEALTH_SIGHT, Channel.LEVEL),
		keyed("stealth/unseen", "witnesses see you X nearer (fallback: Stealth +1 at night)", HearthKeys.STEALTH_SIGHT, Channel.LEVEL),
		keyed("stealth/ghost", "witnesses can't see you beyond 4 blocks (fallback: Stealth +5 while Ghost holds)", HearthKeys.STEALTH_UNSEEN_BEYOND, Channel.LEVEL),
		keyed("stealth/the_quiet_way", "witnesses 10 % nearer, guards give up a third sooner (fallback: Stealth +2)", HearthKeys.STEALTH_SIGHT, Channel.LEVEL),
		keyed("stealth/light_fingered", "the mark notices you X slower (fallback: animals notice you X closer)", HearthKeys.PICKPOCKET_AWARENESS, Channel.METTLE),
		// Speech: the effective Speech and Presence
		route("speech/glib", "Speech counts 1 / 2 / 3 higher", Channel.LEVEL),
		route("speech/persuasive", "Speech counts X higher", Channel.LEVEL),
		route("speech/voice_of_the_realm", "Speech counts 4 higher, Presence 2", Channel.LEVEL),
		route("speech/honeyed_lies", "Speech counts 4 higher", Channel.LEVEL),
		route("speech/commanding_voice", "Presence counts 2 higher", Channel.LEVEL),
		route("speech/demagogue", "Presence counts 4 higher", Channel.LEVEL),
		route("speech/the_courtiers_manners", "Presence counts 2 higher", Channel.LEVEL),
		// Speech: bonus keys
		keyed("speech/shrewd_eye", "the limit band read as Speech 5 higher (fallback: market edge +2 %)", HearthKeys.HAGGLE_BAND, Channel.PRICE),
		keyed("speech/shrewd", "the limit band read as Speech X higher (not rolled without keys)", HearthKeys.HAGGLE_BAND, Channel.NONE),
		keyed("speech/patient", "a merchant's patience +X (not rolled without keys)", HearthKeys.HAGGLE_PATIENCE, Channel.NONE),
		keyed("speech/merchant_prince", "a merchant walked away from is never sour (fallback: market edge +2 %)", HearthKeys.HAGGLE_NEVER_SOUR, Channel.PRICE),
		keyed("speech/smooth", "persuade and flatter odds +X (not rolled without keys)", HearthKeys.SPEECH + "persuade", Channel.NONE),
		keyed("speech/imposing", "intimidate odds +X (not rolled without keys)", HearthKeys.SPEECH + "intimidate", Channel.NONE),
		keyed("speech/second_word", "a failed check tried again once a day (fallback: Speech +1)", HearthKeys.SPEECH_RETRY, Channel.LEVEL),
		keyed("speech/charmer", "reputation gains +25 % (fallback: Presence +1)", HearthKeys.STANDING_GAIN, Channel.LEVEL),
		keyed("speech/charming", "reputation gains +X (not rolled without keys)", HearthKeys.STANDING_GAIN, Channel.NONE),
		keyed("speech/heros_welcome", "folk greet you as a hero (nothing without keys)", HearthKeys.GREET_HERO, Channel.NONE),
		// Speech: price factors
		route("speech/haggler", "market edge +2 / 4 / 6 %", Channel.PRICE),
		route("speech/haggling", "market edge +X", Channel.PRICE),
		route("speech/merchant_prince", "market edge +5 %", Channel.PRICE),
		route("speech/honeyed_lies", "market edge 5 % worse", Channel.PRICE),
		route("speech/provisioner", "food, drink and potions 10 % better both ways", Channel.PRICE),
		route("speech/provisioning", "food and drink X cheaper", Channel.PRICE),
		route("speech/appraising", "tools, weapons and armour sell X dearer", Channel.PRICE),
		route("speech/tavern_keeper", "food you cooked sells 20 % dearer", Channel.PRICE),
		route("talent/silver_tongue", "market edge +X (without Hearth: villagers' prices)", Channel.PRICE));

	/** The first route of a source. */
	public static Optional<Route> route(String source) {
		return ROUTES.stream().filter(r -> r.source().equals(source)).findFirst();
	}

	/** Every route of a source (a source with two effects has two). */
	public static List<Route> routes(String source) {
		return ROUTES.stream().filter(r -> r.source().equals(source)).toList();
	}

	/** Whether one of a source's fallbacks is on now: only while the loaded Hearth reads no bonus keys. */
	public static boolean fallback(String source) {
		return !HearthLink.bonus() && routes(source).stream().anyMatch(r -> r.channel() != r.fallback());
	}

	private HearthChannels() {
	}
}
