package dev.voidmc.mettle.character.skill.stealth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.CardSkill;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.skill.StealthSkill;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/**
 * The shipped Stealth data (docs/BUILD_FEEL.md §14.1): the skill is released with its paths; every perk id in code has a
 * file and the tree has the design's shape; the fourteen traits with their ranges and attributes; the card skill's prefix,
 * natives and pool; every text and icon exists.
 */
class StealthDataTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static final Path RES = Path.of("src/main/resources");
	static final Path DATA = RES.resolve("data/mettle/mettle");

	static JsonObject lang() throws IOException {
		JsonObject all = new JsonObject();
		try (Stream<Path> files = Files.list(RES.resolve("assets/mettle/lang/en_us"))) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				JsonParser.parseString(Files.readString(p)).getAsJsonObject().entrySet().forEach(e -> all.add(e.getKey(), e.getValue()));
			}
		}
		return all;
	}

	static <T> T read(Path file, Codec<T> codec) throws IOException {
		return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file))).getOrThrow();
	}

	static String key(Component c) {
		return ((TranslatableContents) c.getContents()).getKey();
	}

	@Test
	void theSkillIsReleased() throws IOException {
		Skill s = read(DATA.resolve("skill/stealth.json"), Skill.CODEC);
		assertTrue(s.released(), "Stealth ships (BUILD_FEEL.md §14)");
		JsonObject lang = lang();
		assertTrue(lang.has("mettle.path.stealth.a") && lang.has("mettle.path.stealth.b"));
		assertEquals("Ghost", lang.get("mettle.path.stealth.a").getAsString());
		assertEquals("Knife in the Dark", lang.get("mettle.path.stealth.b").getAsString());
		assertTrue(Files.exists(RES.resolve("assets/mettle/textures/gui/skill/stealth.png")));
	}

	@Test
	void treeMatchesTheDesign() throws Exception {
		JsonObject lang = lang();
		Map<Perk.Kind, Integer> kinds = new EnumMap<>(Perk.Kind.class);
		Map<String, Integer> paths = new HashMap<>();
		Map<String, Integer> sides = new HashMap<>();
		int files = 0;
		try (Stream<Path> s = Files.list(DATA.resolve("perk/stealth"))) {
			files = (int) s.count();
		}
		int fields = 0;
		for (Field f : StealthSkill.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Identifier.class || !((Identifier) f.get(null)).getPath().startsWith("stealth/")) {
				continue;
			}
			fields++;
			Identifier id = (Identifier) f.get(null);
			Perk perk = load(id);
			assertEquals("mettle:stealth", perk.skill().identifier().toString(), id + " belongs to Stealth");
			kinds.merge(perk.kind(), 1, Integer::sum);
			if (perk.kind() == Perk.Kind.PATH) {
				paths.merge(perk.path() + perk.tier(), 1, Integer::sum);
			}
			if (perk.kind() == Perk.Kind.MASTERY) {
				sides.merge(perk.side(), 1, Integer::sum);
				assertEquals(5, perk.cost());
			}
			if (perk.kind() == Perk.Kind.ROOT) {
				assertEquals(3, perk.ranks());
				assertEquals(3, perk.rankValues().size());
			}
			if (perk.kind() == Perk.Kind.SYNERGY) {
				assertEquals(10, perk.synergy().get(StealthSkill.STEALTH));
				assertEquals(2, perk.synergy().size());
			}
			for (Component text : new Component[] {perk.name(), perk.description()}) {
				assertTrue(lang.has(key(text)), "missing lang " + key(text));
			}
			if (perk.kind() == Perk.Kind.FEAT || perk.kind() == Perk.Kind.TEACHING) {
				assertTrue(perk.hint().isPresent() && lang.has(key(perk.hint().get())), id + " needs a hint");
			}
			if (perk.kind() == Perk.Kind.CURSED) {
				assertTrue(perk.downside().isPresent() && lang.has(key(perk.downside().get())), id + " needs a downside");
			}
			String icon = id.getPath().substring(id.getPath().indexOf('/') + 1);
			assertTrue(Files.exists(RES.resolve("assets/mettle/textures/gui/perk/" + icon + ".png")), id + " has no icon");
		}
		assertEquals(files, fields, "every perk file has its id in StealthSkill");
		assertEquals(2, kinds.get(Perk.Kind.ROOT));
		assertEquals(8, kinds.get(Perk.Kind.PATH));
		assertEquals(3, kinds.get(Perk.Kind.MASTERY));
		assertEquals(2, kinds.get(Perk.Kind.CURSED));
		assertEquals(2, kinds.get(Perk.Kind.FEAT));
		assertEquals(2, kinds.get(Perk.Kind.SYNERGY));
		assertEquals(1, kinds.get(Perk.Kind.TEACHING));
		assertEquals(Map.of("a2", 2, "a3", 2, "b2", 2, "b3", 2), paths);
		assertEquals(Map.of("a", 1, "any", 1, "b", 1), sides);
		// Soft Step and Skulker are sneaking speed (data); Shadow's numbers are the rules'.
		assertEquals(List.of(0.1, 0.2, 0.3), load(StealthSkill.SOFT_STEP).effects().getFirst().amounts());
		assertEquals(List.of(0.4), load(StealthSkill.SKULKER).effects().getFirst().amounts());
		assertEquals(List.of("90", "80", "70"), load(StealthSkill.SHADOW).rankValues());
		assertEquals(Map.of(StealthSkill.STEALTH, 10, dev.voidmc.mettle.character.Progress.skill("marksmanship"), 10), load(StealthSkill.NIGHT_HUNTER).synergy());
		assertEquals(Map.of(StealthSkill.STEALTH, 10, dev.voidmc.mettle.character.Progress.skill("athletics"), 10), load(StealthSkill.FOOTPAD).synergy());
		assertEquals(StealthSkill.NEVER_SEEN_COUNTER, load(StealthSkill.NEVER_SEEN).feat().orElseThrow().counter());
		assertEquals(StealthSkill.WARDEN_PASS_COUNTER, load(StealthSkill.UNDER_ITS_NOSE).feat().orElseThrow().counter());
	}

	/** §14.1's trait table: id → min, max, the attribute that boosts it. */
	static final Map<String, Object[]> TRAITS = Map.ofEntries(
		Map.entry("shadowed", new Object[] {5, 12, Trait.Kind.FINESSE}), Map.entry("hushed", new Object[] {15, 25, Trait.Kind.FINESSE}),
		Map.entry("nightstalker", new Object[] {10, 25, Trait.Kind.FINESSE}), Map.entry("backbiting", new Object[] {8, 20, Trait.Kind.MIGHT}),
		Map.entry("hamstringing", new Object[] {15, 25, Trait.Kind.FINESSE}), Map.entry("vanishing", new Object[] {15, 25, Trait.Kind.FINESSE}),
		Map.entry("catfooted", new Object[] {10, 25, Trait.Kind.FINESSE}), Map.entry("muffled", new Object[] {10, 25, Trait.Kind.FINESSE}),
		Map.entry("patient", new Object[] {6, 15, Trait.Kind.FINESSE}), Map.entry("lurking", new Object[] {15, 25, Trait.Kind.ENDURANCE}),
		Map.entry("swift_exit", new Object[] {4, 10, Trait.Kind.FINESSE}), Map.entry("light_fingered", new Object[] {5, 12, Trait.Kind.FORTUNE}),
		Map.entry("unseen", new Object[] {5, 12, Trait.Kind.FINESSE}), Map.entry("lootfinger", new Object[] {3, 8, Trait.Kind.FORTUNE}));

	@Test
	void theFourteenTraits() throws IOException {
		JsonObject lang = lang();
		int n = 0;
		try (Stream<Path> files = Files.list(DATA.resolve("trait/stealth"))) {
			for (Path p : files.toList()) {
				String id = p.getFileName().toString().replace(".json", "");
				Trait t = read(p, Trait.CODEC);
				Object[] want = TRAITS.get(id);
				assertNotNull(want, "a trait the design doesn't list: " + id);
				assertEquals(((Integer) want[0]).doubleValue(), t.min(), 1e-9, id + " min");
				assertEquals(((Integer) want[1]).doubleValue(), t.max(), 1e-9, id + " max");
				assertEquals(want[2], t.kind(), id + " attribute");
				assertEquals("mettle:stealth", t.skill().identifier().toString());
				assertTrue(lang.has(key(t.name())) && lang.has(key(t.description())), id + " text");
				n++;
			}
		}
		assertEquals(14, n);
	}

	@Test
	void theCardSkill() throws IOException {
		CardSkill cs = read(DATA.resolve("card_skill/stealth.json"), CardSkill.CODEC);
		assertEquals("SL", cs.prefix());
		assertEquals(List.of("deep", "keen", "swift", "wild", "echo"), cs.pool().stream().map(Identifier::getPath).toList());
		Map<String, String> natives = new HashMap<>();
		cs.natives().forEach((perk, kw) -> natives.put(perk.identifier().getPath(), kw.getPath()));
		assertEquals(Map.ofEntries(Map.entry("stealth/soft_step", "swift"), Map.entry("stealth/shadow", "deep"), Map.entry("stealth/quiet_feet", "gale"),
			Map.entry("stealth/cloak_of_dusk", "deep"), Map.entry("stealth/vanish", "echo"), Map.entry("stealth/silent_fall", "gale"),
			Map.entry("stealth/ambusher", "keen"), Map.entry("stealth/hamstring", "keen"), Map.entry("stealth/unseen_blade", "echo"),
			Map.entry("stealth/poisoner", "wild"), Map.entry("stealth/ghost", "deep"), Map.entry("stealth/shadowstep", "swift"),
			Map.entry("stealth/assassin", "keen"), Map.entry("stealth/skulker", "hex"), Map.entry("stealth/cowards_edge", "hex")), natives);
		for (String kw : natives.values()) {
			read(DATA.resolve("keyword/" + kw + ".json"), Keyword.CODEC);
		}
	}

	@Test
	void everyFeedbackChipHasText() throws IOException {
		JsonObject lang = lang();
		for (String key : new String[] {"vanish", "shadowstep", "assassin", "ghost", "unseen_blade", "night_hunter"}) {
			assertTrue(lang.has("mettle.feedback.stealth." + key), key);
		}
		assertTrue(lang.has("mettle.tome.the_quiet_way"));
	}

	private static Perk load(Identifier id) throws IOException {
		String path = "/data/" + id.getNamespace() + "/mettle/perk/" + id.getPath() + ".json";
		try (InputStream in = StealthDataTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "no data file for " + id);
			return Perk.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
		}
	}
}
