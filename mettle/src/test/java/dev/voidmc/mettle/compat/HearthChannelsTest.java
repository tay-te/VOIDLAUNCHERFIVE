package dev.voidmc.mettle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.character.skill.stealth.StealthRules;
import dev.voidmc.mettle.character.skill.stealth.StealthRules.Holdings;
import dev.voidmc.mettle.compat.HearthChannels.Channel;
import dev.voidmc.mettle.compat.HearthChannels.Route;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Rule (a) of docs/BUILD_FEEL.md §14, one channel per effect: each Stealth perk, trait and Teaching and the talent Silver
 * Tongue, held alone, reaches Hearth through exactly the one channel the table names (the effective level, a bonus key or
 * a price factor) with and without Hearth's bonus keys, and a fallback is never on while the keys are read.
 */
class HearthChannelsTest {
	/** Each source held alone, in the conditions that make it act (sneaking at night in the dark, not attacking). */
	static Holdings alone(String source) {
		boolean shadow = source.equals("stealth/shadow");
		return new Holdings(8, shadow ? 2 : 0, source.equals("stealth/cloak_of_dusk"), source.equals("stealth/unseen") ? 0.08 : 0,
			source.equals("stealth/ghost"), source.equals("stealth/the_quiet_way"), source.equals("stealth/light_fingered") ? 0.08 : 0,
			source.equals("talent/silver_tongue") ? 0.02 : 0, true, true, true, false);
	}

	/** The channels a holding reaches Hearth through (and Mettle-only stand-ins), as the code computes them. */
	static Set<Channel> reached(Holdings h, boolean bonus) {
		Set<Channel> out = EnumSet.noneOf(Channel.class);
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

	@Test
	void everyEffectReachesHearthThroughExactlyOneChannel() {
		List<String> problems = new ArrayList<>();
		for (Route r : HearthChannels.ROUTES) {
			for (boolean bonus : new boolean[] {true, false}) {
				Set<Channel> got = reached(alone(r.source()), bonus);
				if (!got.equals(EnumSet.of(r.channel(bonus)))) {
					problems.add(r.source() + (bonus ? " with" : " without") + " bonus keys: " + got + ", the table says " + r.channel(bonus));
				}
			}
			if (r.channel() == Channel.BONUS) {
				String key = r.key().orElseThrow();
				assertTrue(StealthRules.bonus(alone(r.source()), key) > 0, r.source() + " answers " + key);
			}
		}
		assertEquals(List.of(), problems);
	}

	@Test
	void aFallbackIsNeverOnWhileHearthReadsKeys() {
		for (Route r : HearthChannels.ROUTES) {
			Holdings h = alone(r.source());
			if (r.channel() == Channel.BONUS) {
				// With keys: no level, no stand-in; the key alone.
				assertEquals(0, StealthRules.levelsAdded(h, true), r.source());
				assertEquals(0, StealthRules.animalsCloser(h, true), r.source());
			}
		}
		HearthLink.set(true, true);
		try {
			HearthChannels.ROUTES.forEach(r -> assertTrue(!HearthChannels.fallback(r.source()), r.source() + "'s fallback with bonus keys"));
		} finally {
			HearthLink.set(false, false);
		}
		HearthLink.set(true, false);
		try {
			assertTrue(HearthChannels.fallback("stealth/ghost") && HearthChannels.fallback("stealth/light_fingered"));
			assertTrue(!HearthChannels.fallback("stealth/shadow") && !HearthChannels.fallback("talent/silver_tongue"), "no fallback where no key is needed");
		} finally {
			HearthLink.set(false, false);
		}
	}

	/** Every Stealth perk or trait whose words mention Hearth is in the table, and nothing in the table is unknown. */
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
			boolean perk = k.startsWith("mettle.perk.stealth.") && k.endsWith(".description");
			boolean trait = k.startsWith("mettle.trait.stealth.") && k.endsWith(".description");
			if ((perk || trait) && e.getValue().getAsString().contains("Hearth")) {
				String id = "stealth/" + k.split("\\.")[3];
				if (HearthChannels.route(id).isEmpty()) {
					missing.add(id);
				}
			}
		}
		assertEquals(List.of(), missing, "Stealth effects that speak of Hearth but have no channel");
		for (Route r : HearthChannels.ROUTES) {
			String[] p = r.source().split("/");
			String base = p[0].equals("talent") ? null : "mettle." + (lang.has("mettle.perk." + p[0] + "." + p[1]) ? "perk" : "trait") + "." + p[0] + "." + p[1];
			assertTrue(base == null || lang.has(base), r.source() + " is a real perk or trait");
		}
	}
}
