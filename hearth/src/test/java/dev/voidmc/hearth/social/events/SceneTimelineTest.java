package dev.voidmc.hearth.social.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The scenes' timelines (docs/LIVING_WORLD_2.md §3.8, {@code SceneTimelineTest}): every rule's timeline ends within its
 * scene, every role has a place, every action, venue and pick is known, every bark category it names has lines, and
 * every kind and venue has its words.
 */
class SceneTimelineTest {
	static List<EventRule> all() {
		List<EventRule> out = new ArrayList<>(EventRules.builtin().values());
		out.addAll(EventRules.builtinScenes().values());
		return out;
	}

	static JsonObject json(String path) throws Exception {
		try (InputStream in = SceneTimelineTest.class.getResourceAsStream(path)) {
			assertNotNull(in, path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@Test
	void everyTimelineIsSound() {
		List<String> problems = new ArrayList<>();
		for (EventRule r : all()) {
			problems.addAll(Timeline.problems(r));
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void timelinesEndAndEveryRoleHasAPlace() {
		for (EventRule r : all()) {
			assertTrue(r.duration() > 0);
			int last = 0;
			for (EventRule.Beat b : r.timeline()) {
				last = Math.max(last, b.at());
			}
			assertTrue(last <= r.duration(), r.kind().id() + " runs past its end");
			// walking the timeline to its end plays every beat exactly once, in order
			List<EventRule.Beat> played = new ArrayList<>();
			int from = -1;
			for (int minute = 0; minute <= r.duration(); minute += 3) {
				played.addAll(Timeline.due(r.timeline(), from, minute));
				from = minute;
			}
			played.addAll(Timeline.due(r.timeline(), from, r.duration()));
			assertEquals(r.timeline().size(), played.size(), r.kind().id());
			for (EventRule.RoleSpec role : r.roles()) {
				assertFalse(Timeline.placeAt(r, role.name(), r.duration()).isEmpty(), r.kind().id() + ": " + role.name() + " has no place");
			}
		}
	}

	@Test
	void dueTakesTheBeatsBetween() {
		List<EventRule.Beat> beats = List.of(new EventRule.Beat(0, "a", "walk", "square", "", 0), new EventRule.Beat(5, "a", "bark", "", "", 0),
			new EventRule.Beat(10, "a", "end", "", "", 0));
		assertEquals(1, Timeline.due(beats, -1, 0).size());
		assertEquals(0, Timeline.due(beats, 0, 4).size());
		assertEquals(2, Timeline.due(beats, 4, 10).size());
		assertEquals(0, Timeline.minutes(1000, 1000));
		assertEquals(60, Timeline.minutes(0, 1000), "an hour is a thousand ticks");
	}

	@Test
	void everyBarkCategoryHasLines() throws Exception {
		JsonObject barks = json("/data/hearth/hearth_dialogue/barks/events.json");
		JsonObject crowd = json("/data/hearth/hearth_dialogue/barks/crowd.json");
		for (EventRule r : all()) {
			if (!r.barks().isEmpty()) {
				assertTrue(barks.has(r.barks()), r.kind().id() + ": no lines for " + r.barks());
			}
			for (EventRule.Beat b : r.timeline()) {
				if (b.action().equals("bark") && !b.arg().isEmpty()) {
					assertTrue(barks.has(b.arg()), r.kind().id() + ": no lines for " + b.arg());
				}
				if (b.action().equals("spectacle")) {
					String kind = b.arg().isEmpty() ? r.kind().id() : b.arg();
					assertTrue(barks.has("crowd_spectacle_" + kind) || crowd.has("crowd_spectacle"), "no crowd lines for a " + kind);
				}
			}
		}
		// the director's own
		assertTrue(barks.has("event_tournament_herald"));
		for (Map.Entry<String, com.google.gson.JsonElement> e : barks.entrySet()) {
			assertTrue(e.getValue().getAsJsonArray().size() >= 2, e.getKey() + " has too few lines");
		}
	}

	@Test
	void everyKindAndVenueHasItsWords() throws Exception {
		JsonObject lang;
		try (var r = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("src/main/resources/assets/hearth/lang/en_us/lw_events.json"))) {
			lang = JsonParser.parseReader(r).getAsJsonObject();
		}
		for (EventKind k : EventKind.VALUES) {
			assertTrue(lang.has(k.translationKey()), k.translationKey());
		}
		for (EventRule r : all()) {
			assertTrue(lang.has("hearth.events.venue." + r.venue()) || r.kind() == EventKind.SCENE, "hearth.events.venue." + r.venue());
		}
		for (String o : dev.voidmc.hearth.law.justice.Trials.OFFENCES) {
			assertTrue(lang.has("hearth.events.offence." + o), o);
		}
		for (String sentence : Set.of("fine", "pillory", "gallows", "cell")) {
			assertTrue(lang.has("hearth.events.notice.guilty." + sentence), sentence);
		}
	}
}
