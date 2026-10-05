package dev.voidmc.mettle.character.skill.marksmanship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.Perk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BowItem;
import org.junit.jupiter.api.Test;

class MarksmanshipRulesTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final Path PERKS = Path.of("src/main/resources/data/mettle/mettle/perk/marksmanship");

	@Test
	void withoutPerksTheBowIsVanilla() {
		for (int t = 0; t <= 30; t++) {
			assertEquals(BowItem.getPowerForTime(t), MarksmanshipRules.bowPower(t * MarksmanshipRules.drawSpeed(0, false, false)), 1e-6);
		}
	}

	@Test
	void drawSpeedsStack() {
		assertEquals(1.3f, MarksmanshipRules.drawSpeed(3, false, false), 1e-6);
		assertEquals(1.4f, MarksmanshipRules.drawSpeed(0, true, false), 1e-6);
		// Steppe Draw: full power at three-quarter draw, 15 ticks.
		assertEquals(1f, MarksmanshipRules.bowPower(15 * MarksmanshipRules.drawSpeed(0, false, true)), 1e-6);
		assertTrue(MarksmanshipRules.bowPower(14 * MarksmanshipRules.drawSpeed(0, false, true)) < 1f);
		assertEquals(1.3f * 1.4f * 4f / 3f, MarksmanshipRules.drawSpeed(3, true, true), 1e-5);
	}

	@Test
	void quickNockIsADrawStartedWithinASecond() {
		assertTrue(MarksmanshipRules.quickNock(110, 100));
		assertTrue(MarksmanshipRules.quickNock(120, 100));
		assertFalse(MarksmanshipRules.quickNock(121, 100));
		assertFalse(MarksmanshipRules.quickNock(95, 100), "a draw begun before the last shot is not a new nock");
		assertFalse(MarksmanshipRules.quickNock(10, -1), "no shot yet");
	}

	@Test
	void longShotStepsEveryFiveBlocksAndCaps() {
		assertEquals(1f, MarksmanshipRules.longShot(10), 1e-6);
		assertEquals(1f, MarksmanshipRules.longShot(19.9), 1e-6);
		assertEquals(1.05f, MarksmanshipRules.longShot(20), 1e-6);
		assertEquals(1.25f, MarksmanshipRules.longShot(42), 1e-6);
		assertEquals(1.5f, MarksmanshipRules.longShot(65), 1e-6);
		assertEquals(1.5f, MarksmanshipRules.longShot(200), 1e-6);
	}

	@Test
	void headshotsAreTheTopOfTheBox() {
		// A zombie: eyes at 1.74 of 1.95.
		assertTrue(MarksmanshipRules.headshot(1.8, 1.74));
		assertTrue(MarksmanshipRules.headshot(1.5, 1.74));
		assertFalse(MarksmanshipRules.headshot(1.2, 1.74));
		assertFalse(MarksmanshipRules.headshot(0.5, 1.74));
	}

	@Test
	void smallCounts() {
		assertEquals(6, MarksmanshipRules.fletcher(4));
		assertEquals(3, MarksmanshipRules.fletcher(2));
		assertEquals(12, MarksmanshipRules.fletcher(8));
		assertEquals(15, MarksmanshipRules.crossbowCharge(25));
		assertEquals(0.45f, MarksmanshipRules.retrieval(3), 1e-6);
		assertFalse(MarksmanshipRules.doubleNock(4));
		assertTrue(MarksmanshipRules.doubleNock(5));
		assertTrue(MarksmanshipRules.doubleNock(10));
		assertFalse(MarksmanshipRules.doubleNock(0));
	}

	@Test
	void shippedTreeHasTheRightShape() throws IOException {
		Map<Perk.Kind, Integer> kinds = new EnumMap<>(Perk.Kind.class);
		int pathA = 0;
		int pathB = 0;
		List<Path> files;
		try (Stream<Path> s = Files.list(PERKS)) {
			files = s.filter(p -> p.toString().endsWith(".json")).toList();
		}
		for (Path file : files) {
			Perk perk = Perk.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file))).getOrThrow();
			assertEquals("marksmanship", perk.skill().identifier().getPath(), file.toString());
			kinds.merge(perk.kind(), 1, Integer::sum);
			if (perk.kind() == Perk.Kind.PATH) {
				if ("a".equals(perk.path())) {
					pathA++;
				} else {
					pathB++;
				}
			}
		}
		assertEquals(2, kinds.get(Perk.Kind.ROOT));
		assertEquals(4, pathA);
		assertEquals(4, pathB);
		assertEquals(3, kinds.get(Perk.Kind.MASTERY));
		assertEquals(2, kinds.get(Perk.Kind.FEAT));
		assertEquals(2, kinds.get(Perk.Kind.SYNERGY));
		assertEquals(1, kinds.get(Perk.Kind.TEACHING));
	}
}
