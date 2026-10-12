package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Every shipped en_us line, formatted as Minecraft formats it with as many arguments as it asks for: nothing a player
 * reads may show a raw {@code %s}, {@code %d} or a stray placeholder {@code X} (a lone "%" beside a "%s" makes the game
 * fall back to the raw template, which is how "Finishing" and "Burden-wise" printed "%s").
 */
class LangFormatTest {
	static {
		TestCards.PERKS.size();
	}

	private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s");
	private static final Pattern LEFT = Pattern.compile("%(?:\\d+\\$)?[sd]|\\$s|\\bX\\b");

	private static Map<String, String> lang() throws IOException {
		Map<String, String> out = new TreeMap<>();
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/assets/mettle/lang/en_us"))) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
				JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				o.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
			}
		}
		return out;
	}

	/** How many arguments a line takes: its plain {@code %s}s, or its highest {@code %n$s}. */
	static int args(String template) {
		Matcher m = ARG.matcher(template.replace("%%", ""));
		int plain = 0;
		int positional = 0;
		while (m.find()) {
			if (m.group(1) != null) {
				positional = Math.max(positional, Integer.parseInt(m.group(1)));
			} else {
				plain++;
			}
		}
		return Math.max(plain, positional);
	}

	@Test
	void everyLineFormatsWithItsArguments() throws IOException {
		Map<String, String> lang = lang();
		Language.inject(new Language() {
			@Override
			public String getOrDefault(String key, String fallback) {
				return lang.getOrDefault(key, fallback);
			}

			@Override
			public boolean has(String key) {
				return lang.containsKey(key);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return false;
			}

			@Override
			public FormattedCharSequence getVisualOrder(FormattedText text) {
				return FormattedCharSequence.EMPTY;
			}
		});
		List<Executable> checks = new ArrayList<>();
		lang.forEach((key, template) -> checks.add(() -> {
			Object[] values = new Object[args(template)];
			for (int i = 0; i < values.length; i++) {
				values[i] = String.valueOf(7 + i);
			}
			String shown = Component.translatable(key, values).getString();
			Matcher left = LEFT.matcher(shown);
			assertTrue(!left.find(), key + " shows a raw placeholder: " + shown);
		}));
		assertTrue(lang.size() > 1000, "the lang fragments were not read: " + lang.size());
		assertAll(checks);
	}
}
