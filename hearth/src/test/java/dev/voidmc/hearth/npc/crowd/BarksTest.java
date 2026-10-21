package dev.voidmc.hearth.npc.crowd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** The crowd's barks: every stimulus and role has lines, conditions hold, and the three cooldowns of §0.2 apply. */
class BarksTest {
	private static final Set<String> PLACEHOLDERS = Set.of("player", "settlement", "given");

	@Test
	void everyStimulusHasEnoughLines() {
		Map<String, List<Barks.Line>> all = Barks.loadBuiltin();
		for (Stimulus s : Stimulus.VALUES) {
			List<Barks.Line> lines = all.get(s.category());
			assertNotNull(lines, s.category());
			assertTrue(lines.size() >= 12, s.category() + " has " + lines.size() + " lines");
		}
		for (String c : List.of("crowd_spectacle", "crowd_spectacle_brawl", "crowd_spectacle_body", "crowd_brawl", "crowd_brawl_yield", "crowd_brawl_part")) {
			assertFalse(all.getOrDefault(c, List.of()).isEmpty(), c);
		}
	}

	@Test
	void everyRoleThatReactsHasALine() {
		Map<String, List<Barks.Line>> all = Barks.loadBuiltin();
		RandomSource random = RandomSource.create(1);
		for (Stimulus s : Stimulus.VALUES) {
			for (Role role : Role.VALUES) {
				if (Reaction.of(s, role, 1) == null) {
					continue;
				}
				Barks.Line line = Barks.pick(all.get(s.category()), role, Title.STRANGER, Culture.TEMPERATE, 12 * 60, Set.of(), l -> true, random);
				assertNotNull(line, s.category() + " for a " + role.id());
			}
		}
	}

	@Test
	void placeholdersAreKnown() {
		for (Map.Entry<String, List<Barks.Line>> e : Barks.loadBuiltin().entrySet()) {
			for (Barks.Line l : e.getValue()) {
				String t = l.text();
				int i = t.indexOf('{');
				while (i >= 0) {
					int end = t.indexOf('}', i);
					assertTrue(end > i && PLACEHOLDERS.contains(t.substring(i + 1, end)), e.getKey() + ": " + t);
					i = t.indexOf('{', end);
				}
			}
		}
	}

	@Test
	void conditionsChooseTheLine() {
		Map<String, List<Barks.Line>> all = Barks.loadBuiltin();
		RandomSource random = RandomSource.create(7);
		// a guard's armour remark is a guard's
		for (int i = 0; i < 50; i++) {
			Barks.Line l = Barks.pick(all.get("crowd_armour"), Role.GUARD, Title.STRANGER, Culture.TEMPERATE, 600, Set.of(), x -> true, random);
			assertTrue(l.roles().contains(Role.GUARD), l.text());
		}
		// nobody remarks on armour who is not a guard or merchant: and there is no line for them
		assertNull(Barks.pick(all.get("crowd_armour"), Role.CHILD, Title.STRANGER, Culture.TEMPERATE, 600, Set.of(), x -> true, random));
		// a line said lately is not said again
		List<Barks.Line> few = all.get("crowd_brawl_part");
		assertNull(Barks.pick(few, Role.GUARD, null, Culture.TEMPERATE, 600, Set.of(), x -> false, random));
	}

	@Test
	void theDialogueLoaderTakesBarkFilesHarmlessly() throws Exception {
		// the dialogue loader reads every file under hearth_dialogue/, barks/ included: it must parse them
		Map<String, List<DialogueTemplates.Line>> out = new HashMap<>();
		try (var in = BarksTest.class.getResourceAsStream("/data/hearth/hearth_dialogue/barks/crowd.json")) {
			DialogueTemplates.parseInto(new InputStreamReader(in, StandardCharsets.UTF_8), out);
		}
		assertTrue(out.containsKey("crowd_blade"));
	}

	@Test
	void theThreeCooldowns() {
		CrowdConfig.Barks cfg = CrowdConfig.Barks.DEFAULTS;
		Barks.Limiter lim = new Barks.Limiter();
		UUID a = new UUID(0, 1);
		UUID b = new UUID(0, 2);
		UUID player = new UUID(1, 1);
		assertTrue(lim.may(a, player, 5, 0, cfg));
		lim.said(a, player, 5, "crowd_blade#0", 0);
		// per folk: a minute
		assertFalse(lim.may(a, null, 5, 20 * 59, cfg));
		assertTrue(lim.may(a, null, 5, 20 * 60, cfg));
		// per player in a town: four seconds, whoever speaks
		assertFalse(lim.may(b, player, 5, 20 * 3, cfg));
		assertTrue(lim.may(b, player, 6, 20 * 3, cfg), "another town");
		assertTrue(lim.may(b, player, 5, 20 * 4, cfg));
		// per line: ten minutes
		assertFalse(lim.fresh("crowd_blade#0", 20 * 599, cfg));
		assertTrue(lim.fresh("crowd_blade#0", 20 * 600, cfg));
		assertTrue(lim.fresh("crowd_blade#1", 0, cfg));
		assertEquals(60, cfg.perFolkSeconds());
	}
}
