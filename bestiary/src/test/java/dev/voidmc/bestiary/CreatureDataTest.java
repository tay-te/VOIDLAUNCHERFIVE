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

	/** §4.2: a boss's ultimate (red) gives 20 ticks. */
	static final int ULTIMATE = 20;

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
				if (m.tagged("transition")) {
					// A boss's phase change (the knight tears its cage off): no hit, no tell; it only has to bake.
					if (t.hasPhases()) {
						problems.add(p.id() + "/" + m.id() + ": a transition must not strike");
					}
					continue;
				}
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
				int min = m.tagged("ultimate") ? ULTIMATE : minimum(p.rank(), t.tellColour);
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

	/** BESTIARY §8.3: the Gallows Knight's two phases, 4-8 moves each, the transition, its reactions. */
	@Test
	void theGallowsKnight() throws Exception {
		CreatureProfile k = PROFILE.get(Identifier.fromNamespaceAndPath("bestiary", "gallows_knight"));
		assertNotNull(k, "gallows_knight profile");
		assertEquals("boss", k.rank());
		assertTrue(k.ignoresTokens(), "bosses ignore tokens");
		int[] perPhase = new int[3];
		int reds = 0;
		for (MoveDef m : k.moves()) {
			if (m.tagged("transition")) {
				continue;
			}
			String phases = m.params().has("phases") ? m.params().get("phases").getAsString() : "12";
			for (int ph = 1; ph <= 2; ph++) {
				if (phases.indexOf((char) ('0' + ph)) >= 0) {
					perPhase[ph]++;
				}
			}
			assertTrue(m.behaviour().equals("knight"), m.id() + " runs the knight's behaviour");
			reds += clips.get(m.clip()).timeline().tellColour.equals("red") ? 1 : 0;
		}
		assertTrue(perPhase[1] >= 4 && perPhase[1] <= 8, "phase 1 has " + perPhase[1] + " moves");
		assertTrue(perPhase[2] >= 4 && perPhase[2] <= 8, "phase 2 has " + perPhase[2] + " moves");
		assertEquals(2, reds, "two red moves (the charge, the Hanging)");
		assertNotNull(k.move("tear_cage"), "the transition");
		for (String c : List.of("stagger", "broken", "tear_cage")) {
			assertNotNull(clips.get(Identifier.fromNamespaceAndPath("bestiary", "gallows_knight/" + c)), c);
		}
		// Colours as designed: cuts and the kick white, slam and sweep orange, charge and the Hanging red, the lash purple.
		Map<String, String> want = Map.of("hook_cut", "white", "two_cut", "white", "three_cut", "white", "kick", "white", "slam", "orange",
			"chain_sweep", "orange", "shoulder_charge", "red", "the_hanging", "red", "chain_lash", "purple");
		want.forEach((id, colour) -> assertEquals(colour, clips.get(k.move(id).clip()).timeline().tellColour, id + "'s tell"));
		// The slam leaves a 14+ tick punish window; the kick crushes guards; the Hanging's tell is 24 ticks.
		Timeline slam = clips.get(k.move("slam").clip()).timeline();
		int[] punish = slam.window("punish");
		assertNotNull(punish, "slam punish window");
		assertTrue(punish[1] - punish[0] >= 14, "slam punish " + (punish[1] - punish[0]));
		assertTrue(clips.get(k.move("kick").clip()).timeline().events.stream().anyMatch(e -> e.name().equals("guard_crush")), "kick guard_crush marker");
		Timeline hanging = clips.get(k.move("the_hanging").clip()).timeline();
		assertEquals(24, hanging.activeStart - hanging.tellFrom, "the Hanging's tell");
		assertFalse(hanging.hurtVolumes.isEmpty(), "the chest is open while it hauls");
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
