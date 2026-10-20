package dev.voidmc.hearth.social.rhythm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.Culture;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The rhythm's data (docs/LIVING_WORLD_2.md §2.8): every culture is covered and every bark the code says exists. */
class RhythmDataTest {
	static final Set<String> PLACEHOLDERS = Set.of("player", "name", "settlement", "lord", "child", "hour", "hour_lower", "service");

	@Test
	void everyCultureHasAFileOrTheDefault() {
		RhythmData.Loaded loaded = RhythmData.loadBuiltin();
		assertNotNull(loaded.defaults(), "default.json");
		RhythmData.install(loaded);
		for (Culture c : Culture.values()) {
			RhythmData d = RhythmData.of(c);
			assertNotNull(d, c.getSerializedName());
			assertTrue(loaded.cultures().containsKey(c) || d == RhythmData.parse(loaded.defaults(), RhythmData.DEFAULT) || d.equals(
				RhythmData.parse(loaded.defaults(), RhythmData.DEFAULT)), c + " has neither its own file nor the default");
			// sane hours: the service in the day, the tavern of an evening, the watch over midnight
			assertTrue(d.service().start() >= 6 * 60 && d.service().end() <= 22 * 60, c + " service " + d.service());
			assertTrue(d.service().share() > 0 && d.service().share() <= 1);
			assertTrue(d.tavern().open() >= 16 * 60 && d.tavern().close() > d.tavern().open(), c + " tavern " + d.tavern());
			assertTrue(d.watch().covers(0) && d.watch().covers(3 * 60) && !d.watch().covers(12 * 60), c + " watch " + d.watch());
			assertTrue(d.wash().start() < d.wash().end());
			assertFalse(d.wash().places().isEmpty());
			assertFalse(d.meals().isEmpty());
			assertTrue(d.restDayWork().contains(dev.voidmc.hearth.api.JobType.GUARD), c + ": the watch never rests");
			assertTrue(d.restDayWork().contains(dev.voidmc.hearth.api.JobType.INNKEEPER), c + ": the inn keeps its fire");
			assertTrue(Set.of("mass", "blessing", "prayers", "offering").contains(d.service().name()), c + " service name " + d.service().name());
		}
		// the files differ: the week is the culture's
		assertTrue(RhythmData.of(Culture.MEDITERRANEAN).tavern().close() > RhythmData.of(Culture.NORTHERN).tavern().close());
		assertTrue(RhythmData.of(Culture.TUNDRA).snow().cold() && !RhythmData.of(Culture.TEMPERATE).snow().cold());
	}

	@Test
	void everyCultureFileIsReadWithoutComplaint() throws Exception {
		for (Culture c : Culture.values()) {
			var in = RhythmData.class.getResourceAsStream("/data/hearth/hearth_social/rhythm/" + c.getSerializedName() + ".json");
			if (in == null) {
				continue;
			}
			try (in) {
				JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (String k : j.keySet()) {
					assertTrue(Set.of("_comment", "rest_weekday", "service", "wash", "curfew", "tavern", "watch", "meals", "rest_day_work", "first_snow")
						.contains(k), c + ": unknown field " + k);
				}
				RhythmData.parse(j, RhythmData.DEFAULT);
			}
		}
	}

	@Test
	void everyBarkCategoryReferencedExists() {
		Map<String, List<RhythmBarks.Line>> all = RhythmBarks.loadBuiltin();
		for (String cat : RhythmBarks.CATEGORIES) {
			assertTrue(all.containsKey(cat) && !all.get(cat).isEmpty(), "no lines for " + cat);
			// every culture and every hour of the watch finds a line
			for (Culture c : Culture.values()) {
				assertNotNull(RhythmBarks.pick(cat, c, 23 * 60, 1, Map.of(), -1, 0), cat + " for " + c);
			}
		}
		assertTrue(all.get(RhythmBarks.SERMON).size() >= 20, "twenty sermon lines at least");
		Pattern p = Pattern.compile("\\{([a-z_]+)}");
		for (Map.Entry<String, List<RhythmBarks.Line>> e : all.entrySet()) {
			assertTrue(RhythmBarks.CATEGORIES.contains(e.getKey()), "a category nothing says: " + e.getKey());
			for (RhythmBarks.Line l : e.getValue()) {
				Matcher m = p.matcher(l.text());
				while (m.find()) {
					assertTrue(PLACEHOLDERS.contains(m.group(1)), "unknown placeholder " + m.group() + " in " + l.text());
				}
			}
		}
	}

	@Test
	void barksFillAndDoNotRepeatInATown() {
		Map<String, String> vars = new HashMap<>();
		vars.put("hour", "Eleven");
		vars.put("settlement", "Ashbrook");
		String first = RhythmBarks.pick(RhythmBarks.WATCH_CALL, Culture.TEMPERATE, 23 * 60, 3, vars, 77, 1000);
		assertNotNull(first);
		assertFalse(first.contains("{"), first);
		assertTrue(first.startsWith("Eleven"), first);
		int lines = (int) RhythmBarks.all().get(RhythmBarks.WATCH_CALL).stream().filter(l -> l.cultures().isEmpty()).count();
		java.util.Set<String> said = new java.util.HashSet<>();
		said.add(first);
		for (int i = 1; i < lines; i++) {
			String next = RhythmBarks.pick(RhythmBarks.WATCH_CALL, Culture.TEMPERATE, 23 * 60, 3 + i, vars, 77, 1000 + i);
			assertNotNull(next);
			assertTrue(said.add(next), "a line again within ten minutes: " + next);
		}
		assertEquals(null, RhythmBarks.pick(RhythmBarks.WATCH_CALL, Culture.TEMPERATE, 23 * 60, 99, vars, 77, 1200), "all said: quiet until the cooldown");
		assertNotNull(RhythmBarks.pick(RhythmBarks.WATCH_CALL, Culture.TEMPERATE, 23 * 60, 99, vars, 77, 1000 + RhythmBarks.LINE_COOLDOWN + 10));
		RhythmBarks.clear();
	}
}
