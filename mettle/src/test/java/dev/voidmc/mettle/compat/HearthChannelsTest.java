package dev.voidmc.mettle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.character.skill.speech.SpeechRules;
import dev.voidmc.mettle.character.skill.stealth.StealthRules;
import dev.voidmc.mettle.character.skill.stealth.StealthRules.Holdings;
import dev.voidmc.mettle.compat.HearthChannels.Channel;
import dev.voidmc.mettle.compat.HearthChannels.Route;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Rule (a) of docs/BUILD_FEEL.md §14, one channel per effect: each Stealth and Speech perk, trait and Teaching and the
 * talent Silver Tongue, held alone, reaches Hearth through exactly the channels its rows in the table name (the effective
 * level, a bonus key or a price factor) with and without Hearth's bonus keys, a fallback is never on while the keys are
 * read, and every key Hearth reads is answered by an effect in the table.
 */
class HearthChannelsTest {
	/** Each Stealth source held alone, in the conditions that make it act (sneaking at night in the dark, not attacking). */
	static Holdings alone(String source) {
		boolean shadow = source.equals("stealth/shadow");
		return new Holdings(8, shadow ? 2 : 0, source.equals("stealth/cloak_of_dusk"), source.equals("stealth/unseen") ? 0.08 : 0,
			source.equals("stealth/ghost"), source.equals("stealth/the_quiet_way"), source.equals("stealth/light_fingered") ? 0.08 : 0,
			source.equals("talent/silver_tongue") ? 0.02 : 0, true, true, true, false);
	}

	/** Each Speech source held alone (Speech 8, Presence 10). */
	static SpeechRules.Holdings speech(String source) {
		String s = source;
		return new SpeechRules.Holdings(8, 10, s.equals("speech/haggler") ? 2 : 0, s.equals("speech/glib") ? 2 : 0,
			s.equals("speech/haggling") ? 0.02 : 0, s.equals("speech/persuasive") ? 2 : 0, s.equals("speech/shrewd") ? 2 : 0,
			s.equals("speech/patient") ? 0.10 : 0, s.equals("speech/smooth") ? 0.05 : 0, s.equals("speech/imposing") ? 0.05 : 0,
			s.equals("speech/charming") ? 0.08 : 0, s.equals("speech/shrewd_eye"), s.equals("speech/charmer"), s.equals("speech/commanding_voice"),
			s.equals("speech/second_word"), s.equals("speech/merchant_prince"), s.equals("speech/heros_welcome"), s.equals("speech/voice_of_the_realm"),
			s.equals("speech/honeyed_lies"), s.equals("speech/honeyed_lies") ? SpeechRules.HONEYED_LIES_COST : 0, s.equals("speech/demagogue"),
			s.equals("speech/the_courtiers_manners"), s.equals("speech/provisioner"), s.equals("speech/provisioning") ? 0.05 : 0,
			s.equals("speech/appraising") ? 0.05 : 0, s.equals("speech/tavern_keeper"));
	}

	/** The channels a source reaches Hearth through (and Mettle-only stand-ins), as the code computes them. */
	static Set<Channel> reached(String source, boolean bonus) {
		Set<Channel> out = EnumSet.noneOf(Channel.class);
		if (source.startsWith("speech/")) {
			SpeechRules.Holdings h = speech(source);
			if (SpeechRules.speechAdded(h, bonus) > 0 || SpeechRules.presenceAdded(h, bonus) > 0) {
				out.add(Channel.LEVEL);
			}
			if (bonus && SpeechRules.bonuses(h).values().stream().anyMatch(v -> v > 0)) {
				out.add(Channel.BONUS);
			}
			boolean kinds = false;
			for (boolean buys : new boolean[] {true, false}) {
				kinds |= SpeechRules.kindFactor(h, true, false, false, true, buys) != 1 || SpeechRules.kindFactor(h, false, true, false, false, buys) != 1
					|| SpeechRules.kindFactor(h, false, false, true, false, buys) != 1;
			}
			if (SpeechRules.edge(h, bonus) != 0 || kinds) {
				out.add(Channel.PRICE);
			}
			return out;
		}
		Holdings h = alone(source);
		if (StealthRules.levelsAdded(h, bonus) > 0) {
			out.add(Channel.LEVEL);
		}
		// Hearth calls bonus() only when it declares it.
		if (bonus && StealthRules.bonuses(h).values().stream().anyMatch(v -> v > 0)) {
			out.add(Channel.BONUS);
		}
		if (StealthRules.priceEdge(h) > 0) {
			out.add(Channel.PRICE);
		}
		if (StealthRules.animalsCloser(h, bonus) > 0) {
			out.add(Channel.METTLE);
		}
		return out;
	}

	/** The channels the table names for a source (every row of it), NONE left out. */
	static Set<Channel> named(String source, boolean bonus) {
		Set<Channel> out = EnumSet.noneOf(Channel.class);
		HearthChannels.routes(source).forEach(r -> out.add(r.channel(bonus)));
		out.remove(Channel.NONE);
		return out;
	}

	static double bonusOf(String source, String key) {
		return source.startsWith("speech/") ? SpeechRules.bonus(speech(source), key) : StealthRules.bonus(alone(source), key);
	}

	@Test
	void everyEffectReachesHearthThroughExactlyItsChannels() {
		List<String> problems = new ArrayList<>();
		Set<String> sources = new LinkedHashSet<>();
		HearthChannels.ROUTES.forEach(r -> sources.add(r.source()));
		for (String source : sources) {
			for (boolean bonus : new boolean[] {true, false}) {
				Set<Channel> got = reached(source, bonus);
				if (!got.equals(named(source, bonus))) {
					problems.add(source + (bonus ? " with" : " without") + " bonus keys: " + got + ", the table says " + named(source, bonus));
				}
			}
		}
		for (Route r : HearthChannels.ROUTES) {
			if (r.channel() == Channel.BONUS) {
				String key = r.key().orElseThrow();
				assertTrue(bonusOf(r.source(), key) > 0, r.source() + " answers " + key);
			}
		}
		assertEquals(List.of(), problems);
	}

	@Test
	void aFallbackIsNeverOnWhileHearthReadsKeys() {
		for (Route r : HearthChannels.ROUTES) {
			if (r.channel() == Channel.BONUS && r.source().startsWith("stealth/")) {
				Holdings h = alone(r.source());
				// With keys: no level, no stand-in; the key alone.
				assertEquals(0, StealthRules.levelsAdded(h, true), r.source());
				assertEquals(0, StealthRules.animalsCloser(h, true), r.source());
			}
		}
		// Speech's fallbacks: Shrewd Eye's and never-sour's edge, Charmer's Presence, Second Word's Speech; none with keys.
		assertEquals(SpeechRules.FALLBACK_SHREWD_EYE_EDGE, SpeechRules.edge(speech("speech/shrewd_eye"), false), 1e-9);
		assertEquals(0, SpeechRules.edge(speech("speech/shrewd_eye"), true), 1e-9);
		assertEquals(SpeechRules.MERCHANT_PRINCE_EDGE + SpeechRules.FALLBACK_NEVER_SOUR_EDGE, SpeechRules.edge(speech("speech/merchant_prince"), false), 1e-9);
		assertEquals(SpeechRules.MERCHANT_PRINCE_EDGE, SpeechRules.edge(speech("speech/merchant_prince"), true), 1e-9);
		assertEquals(1, SpeechRules.presenceAdded(speech("speech/charmer"), false));
		assertEquals(0, SpeechRules.presenceAdded(speech("speech/charmer"), true));
		assertEquals(1, SpeechRules.speechAdded(speech("speech/second_word"), false));
		assertEquals(0, SpeechRules.speechAdded(speech("speech/second_word"), true));
		HearthLink.set(true, true);
		try {
			HearthChannels.ROUTES.forEach(r -> assertFalse(HearthChannels.fallback(r.source()), r.source() + "'s fallback with bonus keys"));
		} finally {
			HearthLink.set(false, false);
		}
		HearthLink.set(true, false);
		try {
			assertTrue(HearthChannels.fallback("stealth/ghost") && HearthChannels.fallback("stealth/light_fingered"));
			assertTrue(HearthChannels.fallback("speech/shrewd_eye") && HearthChannels.fallback("speech/second_word") && HearthChannels.fallback("speech/charmer"));
			assertTrue(!HearthChannels.fallback("stealth/shadow") && !HearthChannels.fallback("talent/silver_tongue") && !HearthChannels.fallback("speech/glib"),
				"no fallback where no key is needed");
		} finally {
			HearthLink.set(false, false);
		}
	}

	/**
	 * Every key Hearth reads (all eleven {@code BONUS_*}, the speech kinds spelled out) is answered by an effect in the
	 * table, and only through a BONUS row: except {@code hearth:speech/bribe}, which no Mettle effect answers (§14.2 has none).
	 */
	@Test
	void everyKeyIsCovered() {
		Set<String> sources = new LinkedHashSet<>();
		HearthChannels.ROUTES.forEach(r -> sources.add(r.source()));
		List<String> unanswered = new ArrayList<>();
		for (String key : HearthKeys.ALL) {
			List<String> answer = sources.stream().filter(s -> bonusOf(s, key) > 0).toList();
			if (answer.isEmpty()) {
				unanswered.add(key);
				continue;
			}
			for (String s : answer) {
				assertTrue(HearthChannels.routes(s).stream().anyMatch(r -> r.channel() == Channel.BONUS), s + " answers " + key + " without a BONUS row");
			}
		}
		assertEquals(List.of(HearthKeys.SPEECH + "bribe"), unanswered, "keys no effect answers");
		assertEquals(14, HearthKeys.ALL.size(), "eleven keys, the speech kind spelled out four ways");
	}

	/** Every Stealth and Speech perk or trait whose words mention Hearth is in the table, and nothing in the table is unknown. */
	@Test
	void theTableCoversEveryHearthEffect() throws IOException {
		JsonObject lang = new JsonObject();
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/assets/mettle/lang/en_us"))) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				JsonParser.parseString(Files.readString(p)).getAsJsonObject().entrySet().forEach(e -> lang.add(e.getKey(), e.getValue()));
			}
		}
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, com.google.gson.JsonElement> e : lang.entrySet()) {
			String k = e.getKey();
			for (String skill : new String[] {"stealth", "speech"}) {
				boolean perk = k.startsWith("mettle.perk." + skill + ".") && k.endsWith(".description");
				boolean trait = k.startsWith("mettle.trait." + skill + ".") && k.endsWith(".description");
				if ((perk || trait) && e.getValue().getAsString().contains("Hearth")) {
					String id = skill + "/" + k.split("\\.")[3];
					if (HearthChannels.route(id).isEmpty()) {
						missing.add(id);
					}
				}
			}
		}
		assertEquals(List.of(), missing, "effects that speak of Hearth but have no channel");
		for (Route r : HearthChannels.ROUTES) {
			String[] p = r.source().split("/");
			String base = p[0].equals("talent") ? null : "mettle." + (lang.has("mettle.perk." + p[0] + "." + p[1]) ? "perk" : "trait") + "." + p[0] + "." + p[1];
			assertTrue(base == null || lang.has(base), r.source() + " is a real perk or trait");
		}
	}
}
