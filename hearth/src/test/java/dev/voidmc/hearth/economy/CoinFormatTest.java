package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The amount wording, with the economy's own English lang fragment injected as the language. */
class CoinFormatTest {
	@BeforeAll
	static void english() throws Exception {
		Map<String, String> lang;
		// the fragment itself (the build merges fragments into en_us.json, so it is not on the test classpath)
		try (var in = Files.newBufferedReader(Path.of("src/main/resources/assets/hearth/lang/en_us/economy.json"), StandardCharsets.UTF_8)) {
			lang = new Gson().fromJson(in, new TypeToken<Map<String, String>>() { }.getType());
		}
		Map<String, String> en = lang;
		Language.inject(new Language() {
			@Override
			public String getOrDefault(String key, String fallback) {
				return en.getOrDefault(key, fallback);
			}

			@Override
			public boolean has(String key) {
				return en.containsKey(key);
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
	}

	@Test
	void formatsInTheFewestCoins() {
		assertEquals("2 crowns, 3 marks, 4 bits", CoinFormat.format(234).getString());
		assertEquals("1 crown", CoinFormat.format(100).getString());
		assertEquals("1 mark, 1 bit", CoinFormat.format(11).getString());
		assertEquals("0 bits", CoinFormat.format(0).getString());
		assertEquals("12 crowns, 5 bits", CoinFormat.format(1205).getString());
		assertEquals("minus 3 bits", CoinFormat.format(-3).getString());
	}
}
