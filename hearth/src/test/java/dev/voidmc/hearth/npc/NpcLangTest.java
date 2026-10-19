package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.person.Office;
import dev.voidmc.hearth.npc.person.Trait;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every string the npc module shows has its English line: each trade, trait and office, and every literal
 * {@code hearth.npc.*} key in the module's server and client code (a missing key shows the raw key on screen).
 */
class NpcLangTest {
	private static final Path LANG = Path.of("src/main/resources/assets/hearth/lang/en_us/npc.json");
	private static final Pattern KEY = Pattern.compile("\"(hearth\\.npc\\.[a-z0-9_.]*[a-z0-9_])\"");

	private static JsonObject lang() throws IOException {
		try (Reader r = Files.newBufferedReader(LANG, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(r).getAsJsonObject();
		}
	}

	@Test
	void everyTradeTraitAndOfficeIsNamed() throws IOException {
		JsonObject lang = lang();
		List<String> missing = new ArrayList<>();
		for (JobType job : JobType.values()) {
			String k = "hearth.npc.job." + job.getSerializedName();
			if (!lang.has(k)) {
				missing.add(k);
			}
		}
		for (Trait t : Trait.values()) {
			if (!lang.has(t.translationKey())) {
				missing.add(t.translationKey());
			}
		}
		for (Office o : Office.values()) {
			if (o != Office.NONE && !lang.has(o.translationKey())) {
				missing.add(o.translationKey());
			}
		}
		assertTrue(missing.isEmpty(), "missing lang keys: " + missing);
	}

	@Test
	void everyLiteralKeyInTheCodeIsDefined() throws IOException {
		JsonObject lang = lang();
		List<String> missing = new ArrayList<>();
		for (Path root : List.of(Path.of("src/main/java/dev/voidmc/hearth/npc"), Path.of("src/client/java/dev/voidmc/hearth/client/npc"))) {
			try (Stream<Path> files = Files.walk(root)) {
				for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
					Matcher m = KEY.matcher(Files.readString(f, StandardCharsets.UTF_8));
					while (m.find()) {
						if (!lang.has(m.group(1)) && !missing.contains(m.group(1))) {
							missing.add(m.group(1) + " (" + f.getFileName() + ")");
						}
					}
				}
			}
		}
		assertTrue(missing.isEmpty(), "keys used but not defined: " + missing);
	}
}
