package dev.voidmc.mettle.character.skill.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.Perk;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

class DefenseRulesTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final Path PERKS = Path.of("src/main/resources/data/mettle/mettle/perk/defense");
	private static final Path LANG = Path.of("src/main/resources/assets/mettle/lang/en_us/defense.json");

	@Test
	void bulwarkOpensEverythingButTheBack() {
		// Vanilla's 90° now reaches 135°: a hit from the side (100°) is caught, one from behind (150°) is not.
		assertTrue(DefenseRules.bulwarkAngle(Math.toRadians(100)) <= Math.toRadians(90));
		assertTrue(DefenseRules.bulwarkAngle(Math.toRadians(135)) <= Math.toRadians(90) + 1e-9);
		assertFalse(DefenseRules.bulwarkAngle(Math.toRadians(150)) <= Math.toRadians(90));
		// No source position reads as 180°: still not blocked.
		assertFalse(DefenseRules.bulwarkAngle(Math.PI) <= Math.toRadians(90));
	}

	@Test
	void perfectBlockIsTheFirstQuarterSecondTheShieldIsUp() {
		assertTrue(DefenseRules.perfectBlock(0, 0), "Shield Up: up at once");
		assertTrue(DefenseRules.perfectBlock(4, 0));
		assertFalse(DefenseRules.perfectBlock(5, 0));
		assertFalse(DefenseRules.perfectBlock(3, 5), "not up yet without Shield Up");
		assertTrue(DefenseRules.perfectBlock(5, 5), "the window starts when the shield comes up");
		assertTrue(DefenseRules.perfectBlock(9, 5));
		assertFalse(DefenseRules.perfectBlock(10, 5));
	}

	@Test
	void holdTheLineStacksToFive() {
		int stacks = 0;
		for (int i = 0; i < 8; i++) {
			stacks = DefenseRules.holdTheLine(stacks);
		}
		assertEquals(5, stacks);
		assertEquals(1, DefenseRules.holdTheLine(0));
	}

	@Test
	void unbowedHalvesButKeepsInfinite() {
		assertEquals(100, DefenseRules.unbowed(200));
		assertEquals(1, DefenseRules.unbowed(1));
		assertEquals(-1, DefenseRules.unbowed(-1));
	}

	@Test
	void menderSparesAboutThirtyPercent() {
		java.util.Random random = new java.util.Random(42);
		int kept = DefenseRules.mender(10_000, random::nextDouble);
		assertTrue(kept > 6_700 && kept < 7_300, "kept " + kept);
		assertEquals(0, DefenseRules.mender(5, () -> 0.1));
		assertEquals(5, DefenseRules.mender(5, () -> 0.9));
	}

	@Test
	void battleWornStopsAtOneDurability() {
		assertEquals(3, DefenseRules.battleWorn(3, 0, 240));
		assertEquals(1, DefenseRules.battleWorn(3, 238, 240), "238 + 1 = 239 = max - 1");
		assertEquals(0, DefenseRules.battleWorn(3, 239, 240));
		assertEquals(0, DefenseRules.battleWorn(3, 240, 240));
	}

	@Test
	void stillStandingCutsOnlyBigHits() {
		assertEquals(10f, DefenseRules.stillStanding(10f));
		assertEquals(8f, DefenseRules.stillStanding(12f), 1e-5);
		assertEquals(12f, DefenseRules.stillStanding(18f), 1e-5);
	}

	@Test
	void rollWithTheBlowHoldsAQuarter() {
		assertEquals(2f, DefenseRules.rollDeferred(8f), 1e-6);
		assertEquals(8f, 8f - DefenseRules.rollDeferred(8f) + DefenseRules.ROLL_SECONDS * (DefenseRules.rollDeferred(8f) / DefenseRules.ROLL_SECONDS), 1e-5);
	}

	/** Every shipped Defense perk parses, sits where TREES.md §7 puts it, and has its text. */
	@Test
	void shippedPerksMatchTheTree() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
		Map<Perk.Kind, AtomicInteger> kinds = new EnumMap<>(Perk.Kind.class);
		Map<String, AtomicInteger> paths = new java.util.HashMap<>();
		List<Path> files;
		try (Stream<Path> s = Files.list(PERKS)) {
			files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
		assertEquals(18, files.size());
		for (Path file : files) {
			Perk perk = Perk.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)))
				.getOrThrow(e -> new AssertionError(file + ": " + e));
			assertEquals("mettle:defense", perk.skill().identifier().toString(), file.toString());
			kinds.computeIfAbsent(perk.kind(), k -> new AtomicInteger()).incrementAndGet();
			if (perk.kind() == Perk.Kind.PATH) {
				paths.computeIfAbsent(perk.path() + perk.tier(), k -> new AtomicInteger()).incrementAndGet();
				assertEquals(perk.tier(), perk.cost(), file + ": a tier II perk costs 2, tier III 3");
			}
			String id = file.getFileName().toString().replace(".json", "");
			assertKey(lang, perk.name(), "mettle.perk.defense." + id);
			assertKey(lang, perk.description(), "mettle.perk.defense." + id + ".description");
			perk.hint().ifPresent(h -> assertKey(lang, h, "mettle.perk.defense." + id + ".hint"));
			if (perk.kind() == Perk.Kind.FEAT) {
				assertTrue(perk.hint().isPresent() && perk.feat().isPresent(), id + ": a feat needs a hint and a counter");
			}
		}
		assertEquals(2, kinds.get(Perk.Kind.ROOT).get());
		assertEquals(8, kinds.get(Perk.Kind.PATH).get());
		assertEquals(3, kinds.get(Perk.Kind.MASTERY).get());
		assertEquals(2, kinds.get(Perk.Kind.FEAT).get());
		assertEquals(2, kinds.get(Perk.Kind.SYNERGY).get());
		assertEquals(1, kinds.get(Perk.Kind.TEACHING).get());
		for (String slot : List.of("a2", "a3", "b2", "b3")) {
			assertEquals(2, paths.get(slot).get(), "two perks at " + slot);
		}
		assertTrue(lang.has("mettle.path.defense.a") && lang.has("mettle.path.defense.b"));
	}

	private static void assertKey(JsonObject lang, net.minecraft.network.chat.Component text, String key) {
		assertTrue(text.getContents() instanceof TranslatableContents t && t.getKey().equals(key), "expected " + key);
		assertTrue(lang.has(key), "missing lang " + key);
	}
}
