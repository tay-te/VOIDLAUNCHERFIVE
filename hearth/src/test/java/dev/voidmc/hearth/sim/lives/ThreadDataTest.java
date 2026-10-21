package dev.voidmc.hearth.sim.lives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.Leisure;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
import dev.voidmc.hearth.npc.talk.PlayerTalk;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The named characters' data (docs/LIVING_WORLD_2.md §4.7, §4.8): every role and thread file valid, every beat reachable,
 * every branch ends (no loops), every quest template, line, label and bark it names exists; 13 roles, 4–8 beats and two
 * or three endings a thread; the talk about the player covers every deed slug.
 */
class ThreadDataTest {
	static final Set<String> EFFECTS = Set.of("news", "relation", "sweethearts", "feud", "peace", "master_to", "master", "idle", "leave", "die", "away",
		"mourn", "treasury", "memory", "deed", "gift", "coin");
	static final Set<String> NEEDS = Set.of("", "widowed", "apprentice", "master", "single", "newcomer", "young", "parent");
	static final Set<String> PARTNERS = Set.of("none", "apprentice", "master", "sweetheart", "creditor", "kin", "household");
	static final Set<String> PARTNER_CONDITIONS = Set.of("present", "gone", "grown", "sweetheart", "married", "feud", "peace");

	static LivesBook book;
	static JsonObject lang;
	static Map<String, List<DialogueTemplates.Line>> dialogue;
	static Map<String, List<Barks.Line>> barks;

	@BeforeAll
	static void load() throws Exception {
		dev.voidmc.hearth.sim.TestSupport.boot();
		book = LivesBook.builtin();
		try (InputStream in = ThreadDataTest.class.getResourceAsStream("/assets/hearth/lang/en_us.json")) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		dialogue = DialogueTemplates.loadBuiltin();
		barks = new HashMap<>();
		try (InputStream in = ThreadDataTest.class.getResourceAsStream("/data/hearth/hearth_dialogue/barks/lives.json")) {
			Barks.parseInto(new InputStreamReader(in, StandardCharsets.UTF_8), barks);
		}
	}

	@Test
	void readsWithoutErrors() {
		assertEquals(List.of(), book.errors);
		assertEquals(13, book.roles.size(), "13 roles (§4.1)");
		assertEquals(13, book.threads.size());
		for (LivesBook.Role r : book.roles.values()) {
			assertNotNull(book.thread(r.thread()), r.id() + ": its thread");
			assertTrue(NEEDS.contains(r.who().needs()), r.id() + ": need " + r.who().needs());
			assertTrue(PARTNERS.contains(r.partner()), r.id() + ": partner " + r.partner());
			assertTrue(lang.has("hearth.lives.role." + r.id()), r.id() + ": its name");
			for (LivesBook.Window w : r.schedule()) {
				Activity.valueOf(w.activity().toUpperCase(Locale.ROOT));
				if (w.leisure() != null) {
					Leisure.valueOf(w.leisure().toUpperCase(Locale.ROOT));
				}
				if (w.where() != null) {
					Schedule.Where.valueOf(w.where().toUpperCase(Locale.ROOT));
				}
				assertTrue(Set.of("all", "rest", "work").contains(w.days()), r.id() + ": days " + w.days());
				for (String b : w.beats()) {
					assertNotNull(book.thread(r.thread()).beat(b), r.id() + ": a window names beat " + b);
				}
			}
		}
	}

	@Test
	void everyBeatReachableAndEveryBranchEnds() {
		for (LivesBook.Thread t : book.threads.values()) {
			int beats = t.beats().size();
			assertTrue(beats >= 4 && beats <= 8, t.id() + ": 4-8 beats, has " + beats);
			long ends = t.beats().values().stream().filter(LivesBook.Beat::end).count();
			assertTrue(ends >= 2 && ends <= 3, t.id() + ": two or three endings, has " + ends);
			Map<String, Set<String>> edges = new HashMap<>();
			for (LivesBook.Beat b : t.beats().values()) {
				Set<String> to = new HashSet<>();
				for (LivesBook.Next n : b.next()) {
					to.add(n.to());
				}
				for (LivesBook.Choice c : b.choices()) {
					to.add(c.to());
					if (c.fail() != null && !c.fail().equals(b.id())) {
						to.add(c.fail());
					}
					if (c.fallback() != null) {
						to.add(c.fallback().to());
					}
				}
				for (String x : to) {
					assertNotNull(t.beat(x), t.id() + "." + b.id() + " goes to a beat that is not there: " + x);
				}
				if (b.end()) {
					assertTrue(b.next().isEmpty() && b.choices().isEmpty(), t.id() + "." + b.id() + ": an end leads nowhere");
				} else {
					assertTrue(!b.next().isEmpty(), t.id() + "." + b.id() + ": a beat that is not an end must move on by itself in time");
				}
				edges.put(b.id(), to);
			}
			// reachable from the start
			Set<String> seen = new HashSet<>();
			Deque<String> q = new ArrayDeque<>(List.of(t.start()));
			while (!q.isEmpty()) {
				String x = q.poll();
				if (seen.add(x)) {
					q.addAll(edges.getOrDefault(x, Set.of()));
				}
			}
			assertEquals(t.beats().keySet(), seen, t.id() + ": every beat reachable");
			// no loops, so every branch ends
			Map<String, Integer> state = new HashMap<>();
			for (String b : t.beats().keySet()) {
				assertTrue(acyclic(b, edges, state), t.id() + ": a loop through " + b);
			}
			// a time-only way out of every beat that is not an end (no story waits forever on a player)
			for (LivesBook.Beat b : t.beats().values()) {
				if (!b.end()) {
					assertTrue(b.next().stream().anyMatch(n -> n.flag() == null && n.partner() == null && n.event() == null),
						t.id() + "." + b.id() + ": a way on by time alone");
				}
			}
		}
	}

	private static boolean acyclic(String b, Map<String, Set<String>> edges, Map<String, Integer> state) {
		Integer st = state.get(b);
		if (st != null) {
			return st == 2;
		}
		state.put(b, 1);
		for (String x : edges.getOrDefault(b, Set.of())) {
			if (!acyclic(x, edges, state)) {
				return false;
			}
		}
		state.put(b, 2);
		return true;
	}

	@Test
	void everythingNamedExists() {
		for (LivesBook.Thread t : book.threads.values()) {
			Set<String> quests = new HashSet<>();
			for (LivesBook.Beat b : t.beats().values()) {
				assertTrue(lang.has("hearth.lives.story." + t.id() + "." + b.id()), t.id() + "." + b.id() + ": a People line");
				assertTrue(dialogue.getOrDefault("thread." + t.id() + "." + b.id(), List.of()).size() >= 1, t.id() + "." + b.id() + ": lines to say");
				List<LivesBook.Effect> all = new ArrayList<>(b.effects());
				for (LivesBook.Choice c : b.choices()) {
					assertTrue(lang.has("hearth.lives.option." + t.id() + "." + c.id()), t.id() + "." + c.id() + ": a label");
					all.addAll(c.effects());
					if (c.speech() != null) {
						assertTrue(Set.of("persuade", "intimidate", "flatter").contains(c.speech()), c.speech());
					}
					if (c.quest() != null) {
						quests.add(c.quest().toString());
						String path = "/data/" + c.quest().getNamespace() + "/hearth_quest/" + c.quest().getPath() + ".json";
						try (InputStream in = ThreadDataTest.class.getResourceAsStream(path)) {
							assertNotNull(in, "quest template " + path);
							JsonObject q = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
							assertEquals("personal", q.get("type").getAsString(), path + ": a personal quest");
							assertEquals(0, q.get("weight").getAsInt(), path + ": never offered on the board (weight 0)");
						} catch (java.io.IOException e) {
							throw new AssertionError(e);
						}
						assertNotNull(c.fallback(), t.id() + "." + c.id() + ": a plain way when no personal quest can be offered");
					}
				}
				for (LivesBook.Effect e : all) {
					assertTrue(EFFECTS.contains(e.type()), t.id() + "." + b.id() + ": effect " + e.type());
					if (e.type().equals("gift")) {
						assertTrue(e.item().startsWith("minecraft:"), e.item());
					}
				}
				for (LivesBook.Next n : b.next()) {
					if (n.partner() != null) {
						assertTrue(PARTNER_CONDITIONS.contains(n.partner()), n.partner());
					}
					if (n.flag() != null && n.flag().startsWith("quest:")) {
						assertTrue(quests.contains(n.flag().substring(6)) || t.beats().values().stream().flatMap(x -> x.choices().stream())
							.anyMatch(c -> c.quest() != null && n.flag().equals("quest:" + c.quest())), t.id() + ": a quest flag no choice offers: " + n.flag());
					}
				}
			}
		}
	}

	@Test
	void barksAndTalk() {
		for (String role : book.roles.keySet()) {
			assertTrue(barks.getOrDefault("lives." + role, List.of()).size() >= 12, role + ": 12+ barks (§4.7)");
		}
		for (String cat : List.of("lives.courting", "lives.courting.flower", "lives.courting.thanks", "lives.feud.insult", "lives.feud.answer", "lives.feud.brawl",
			"lives.grief", "lives.masterpiece", "lives.overheard.good", "lives.overheard.bad", "lives.overheard.answer.good", "lives.overheard.answer.bad")) {
			assertTrue(!barks.getOrDefault(cat, List.of()).isEmpty(), cat);
		}
		for (String cat : List.of("lives.condolence", "lives.peace.yes", "lives.peace.no", "lives.refused")) {
			assertTrue(!dialogue.getOrDefault(cat, List.of()).isEmpty() || !barks.getOrDefault(cat, List.of()).isEmpty(), cat);
		}
		// every slug the talk folds deeds into has lines of both kinds
		Set<String> slugs = new HashSet<>();
		for (String kind : List.of("repair:fire_brigade", "law:theft", "law:murder", "law:assault", "quest:completed", "quest:failed", "defense:monster_slain",
			"npc:gift", "npc:rescue", "economy:donation", "repair:helped", "law:resisting_arrest", "law:pillory", "social:bought_a_round",
			"social:attended_mass", "npc:insult", "law:trespass", "law:poaching", "economy:bought_property", "lives:paid_debt")) {
			slugs.add(PlayerTalk.slug(kind, 10));
		}
		slugs.add("good");
		slugs.add("bad");
		for (String s : slugs) {
			assertTrue(!dialogue.getOrDefault("about.player." + s, List.of()).isEmpty(), "about.player." + s);
			assertTrue(!dialogue.getOrDefault("remember.last." + s, List.of()).isEmpty(), "remember.last." + s);
			assertTrue(!barks.getOrDefault("lives.overheard." + s, List.of()).isEmpty(), "lives.overheard." + s);
		}
		int lines = 0;
		for (Map.Entry<String, List<DialogueTemplates.Line>> e : dialogue.entrySet()) {
			if (e.getKey().startsWith("thread.") || e.getKey().startsWith("about.player.") || e.getKey().startsWith("remember.last.")) {
				lines += e.getValue().size();
			}
		}
		assertTrue(lines >= 150, "150+ dialogue lines across the categories (§4.7), has " + lines);
	}
}
