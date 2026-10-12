package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.model.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The hand-drawn pixel font the cards use for names in hand mode ({@code MC.pix} in cards-core.js): every character of
 * every perk and Mythic name VOID Mettle ships must have a glyph (anything else would draw as a space), the roman ranks
 * included, and no two letters may look the same once drawn bold. The names come from {@code libs/void-mettle.jar}
 * (the perk and Mythic json and their English names); without the jar only the font itself is checked.
 */
class PixelFontTest {

	private static final Pattern ENTRY = Pattern.compile("(?:([A-Z0-9])|\"([^\"])\")\\s*:\\s*\"([^\"]*)\"");

	static Map<String, String> font() throws IOException {
		String src;
		try (InputStream in = PixelFontTest.class.getResourceAsStream("/assets/voidhub/ui/cards-core.js")) {
			assertNotNull(in, "cards-core.js on the classpath");
			src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		int i = src.indexOf("var FONT = {");
		int j = src.indexOf("};", i);
		assertTrue(i >= 0 && j > i, "the FONT table in cards-core.js");
		Map<String, String> out = new TreeMap<>();
		Matcher m = ENTRY.matcher(src.substring(i, j));
		while (m.find()) {
			out.put(m.group(1) != null ? m.group(1) : m.group(2), m.group(3));
		}
		return out;
	}

	/** A glyph as drawn: smeared one texel right (bold), unless it is drawn bold already ("="). */
	static List<String> bold(String spec) {
		List<String> out = new ArrayList<>();
		if (spec.startsWith("=")) {
			out.addAll(List.of(spec.substring(1).split("\\|")));
			return out;
		}
		for (String row : spec.split("\\|")) {
			String r = row + ".";
			StringBuilder b = new StringBuilder();
			for (int x = 0; x < r.length(); x++) {
				b.append(r.charAt(x) == '#' || x > 0 && r.charAt(x - 1) == '#' ? '#' : '.');
			}
			out.add(b.toString());
		}
		return out;
	}

	@Test
	void everyGlyphIsSevenRowsOfOneWidth() throws IOException {
		Map<String, String> font = font();
		assertTrue(font.size() >= 26 + 10, "letters and digits: " + font.keySet());
		for (Map.Entry<String, String> e : font.entrySet()) {
			List<String> rows = bold(e.getValue());
			assertEquals(7, rows.size(), "glyph '" + e.getKey() + "' rows");
			for (String r : rows) {
				assertEquals(rows.getFirst().length(), r.length(), "glyph '" + e.getKey() + "' is ragged");
			}
		}
	}

	@Test
	void noTwoLettersLookTheSameDrawnBold() throws IOException {
		Map<String, String> font = font();
		Map<String, String> seen = new HashMap<>();
		for (char c = 'A'; c <= 'Z'; c++) {
			String drawn = String.join("|", bold(font.get(String.valueOf(c))));
			String other = seen.put(drawn, String.valueOf(c));
			assertTrue(other == null, c + " draws exactly like " + other);
			// A letter whose middle rows smear into solid bars reads as another letter (M, N and W did): no glyph has
			// more than one row that is solid from edge to edge.
			int solid = 0;
			for (String r : bold(font.get(String.valueOf(c)))) {
				if (!r.contains(".")) {
					solid++;
				}
			}
			assertTrue(solid <= 2, c + " has " + solid + " solid rows");
		}
	}

	@Test
	void everyPerkAndMythicNameHasItsGlyphs() throws IOException {
		Path jar = Path.of("libs", "void-mettle.jar");
		Assumptions.assumeTrue(Files.isRegularFile(jar), "libs/void-mettle.jar is not staged");
		Map<String, String> font = font();
		List<String> names = new ArrayList<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			Map<String, Object> lang = Json.parseObject(read(zip, zip.getEntry("assets/mettle/lang/en_us.json")));
			for (ZipEntry e : java.util.Collections.list(zip.entries())) {
				String n = e.getName();
				if (!n.endsWith(".json") || !(n.startsWith("data/mettle/mettle/perk/") || n.startsWith("data/mettle/mettle/mythic/"))) {
					continue;
				}
				Object name = Json.parseObject(read(zip, e)).get("name");
				if (name instanceof Map<?, ?> t && t.get("translate") instanceof String key) {
					name = lang.getOrDefault(key, key);
				}
				if (name instanceof String s) {
					names.add(s);
				}
			}
		}
		assertTrue(names.size() > 100, "perk and Mythic names in the jar: " + names.size());
		Map<Character, List<String>> missing = new TreeMap<>();
		for (String n : names) {
			// A root's card name carries its rank ("Quarryman III"); hand mode draws names in capitals.
			for (char c : (n + " I II III").toUpperCase(Locale.ROOT).toCharArray()) {
				if (!font.containsKey(String.valueOf(c))) {
					missing.computeIfAbsent(c, k -> new ArrayList<>()).add(n);
				}
			}
		}
		assertTrue(missing.isEmpty(), "characters the pixel font cannot draw: " + missing);
		TreeSet<Character> used = new TreeSet<>();
		for (String n : names) {
			for (char c : n.toUpperCase(Locale.ROOT).toCharArray()) {
				used.add(c);
			}
		}
		System.out.println("PixelFontTest: " + names.size() + " names, characters " + used);
	}

	private static String read(ZipFile zip, ZipEntry e) throws IOException {
		assertNotNull(e, "entry in the Mettle jar");
		try (InputStream in = zip.getInputStream(e)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
