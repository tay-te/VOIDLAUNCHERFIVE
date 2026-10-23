package dev.voidmc.bestiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.fray.motion.Timeline;
import dev.voidmc.fray.motion.bake.MotionBake;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The data test of BESTIARY.md §14: every creature profile loads; every move's clip exists and bakes; every move meets
 * §4.2's tell minimums (the time from the tell to the first ACTIVE tick, by colour and rank), its string is short enough,
 * and heavy, red and purple moves leave a punish window of at least 8 ticks.
 */
class CreatureDataTest {
	static final Path PROFILES = Path.of("src/main/resources/data/bestiary/bestiary/creature");
	static final Map<Identifier, CreatureProfile> PROFILE = new LinkedHashMap<>();
	static Map<Identifier, MotionBake.Baked> clips;

	@BeforeAll
	static void load() throws Exception {
		MotionBake.Result r = MotionBake.bakeAll(Path.of("src/main/motion"), Path.of("src/main/blockbench/rig"), null);
		assertTrue(r.failures().isEmpty(), "bake failures: " + r.failures());
		clips = r.clips();
		try (Stream<Path> files = Files.list(PROFILES)) {
			for (Path f : files.sorted().toList()) {
				String name = f.getFileName().toString().replace(".json", "");
				Identifier id = Identifier.fromNamespaceAndPath("bestiary", name);
				PROFILE.put(id, CreatureProfile.parse(id, JsonParser.parseString(Files.readString(f)).getAsJsonObject()));
			}
		}
	}

	/** §4.2: minimum tell lead in ticks by colour, for a rank. */
	static int minimum(String rank, String colour) {
		boolean boss = rank.equals("boss");
		boolean elite = rank.equals("elite");
		return switch (colour) {
			case "orange" -> boss ? 10 : 8;
			case "red", "purple" -> boss ? 14 : 12;
			default -> boss ? 5 : elite ? 4 : 5;
		};
	}

	static int longestString(String rank) {
		return rank.equals("boss") ? 4 : rank.equals("elite") ? 3 : 2;
	}

	@Test
	void stageOneRosterLoads() {
		for (String n : List.of("zombie", "skeleton", "spider", "creeper", "revenant_soldier")) {
			CreatureProfile p = PROFILE.get(Identifier.fromNamespaceAndPath("bestiary", n));
			assertNotNull(p, n);
			assertFalse(p.moves().isEmpty(), n + " has moves");
		}
		assertEquals("Gravewalker", PROFILE.get(Identifier.fromNamespaceAndPath("bestiary", "zombie")).codexName());
	}

	@Test
	void everyMoveMeetsTheTellMinimums() {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (CreatureProfile p : PROFILE.values()) {
			for (MoveDef m : p.moves()) {
				MotionBake.Baked b = clips.get(m.clip());
				if (b == null) {
					problems.add(p.id() + "/" + m.id() + ": no clip " + m.clip());
					continue;
				}
				Timeline t = b.timeline();
				checked++;
				if (m.speed() != 1) {
					problems.add(p.id() + "/" + m.id() + ": creature clips play at speed 1 (server timelines aren't retimed)");
				}
				if (!t.hasPhases()) {
					problems.add(p.id() + "/" + m.id() + ": clip has no ACTIVE window");
					continue;
				}
				if (t.tellFrom < 0) {
					problems.add(p.id() + "/" + m.id() + ": clip has no tell");
					continue;
				}
				int lead = t.activeStart - t.tellFrom;
				int min = minimum(p.rank(), t.tellColour);
				if (lead < min) {
					problems.add(String.format("%s/%s: %s tell %d ticks before the hit, minimum %d", p.id(), m.id(), t.tellColour, lead, min));
				}
				if (t.strikes.length > longestString(p.rank())) {
					problems.add(p.id() + "/" + m.id() + ": a string of " + t.strikes.length + " strikes");
				}
				boolean heavy = !t.tellColour.equals("white");
				int recovery = t.ticks - t.strikes[t.strikes.length - 1][1];
				if (heavy && !m.tagged("self_destruct") && recovery < 8) {
					problems.add(p.id() + "/" + m.id() + ": punish window " + recovery + " ticks after a " + t.tellColour + " attack, minimum 8");
				}
			}
		}
		assertTrue(checked >= 19, "checked " + checked + " moves");
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void reactionClipsExist() {
		for (String n : List.of("zombie", "skeleton", "spider", "creeper", "revenant_soldier")) {
			assertNotNull(clips.get(Identifier.fromNamespaceAndPath("bestiary", n + "/stagger")), n + "/stagger");
		}
	}

	@Test
	void vanillaRigsAreVanillaBound() throws Exception {
		MotionBake.Result r = MotionBake.bakeAll(Path.of("src/main/motion"), Path.of("src/main/blockbench/rig"), null);
		assertTrue(r.rigs().get(Identifier.fromNamespaceAndPath("bestiary", "spider")).rig().vanilla);
		assertTrue(r.rigs().get(Identifier.fromNamespaceAndPath("bestiary", "creeper")).rig().vanilla);
		assertFalse(r.rigs().get(Identifier.fromNamespaceAndPath("bestiary", "gallows_knight")).rig().vanilla);
	}
}
