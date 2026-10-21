package dev.voidmc.mettle.character.skill.speech;

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
import dev.voidmc.mettle.character.skill.SpeechSkill;
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
 * The shipped Speech data (docs/BUILD_FEEL.md §14.2): the skill is released with its paths; every perk id in code has a
 * file and the tree has the design's shape; the fourteen traits with their ranges and attributes; the card skill's prefix,
 * natives and pool; every text and icon exists.
 */
class SpeechDataTest {
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
		Skill s = read(DATA.resolve("skill/speech.json"), Skill.CODEC);
		assertTrue(s.released(), "Speech ships (BUILD_FEEL.md §14)");
		JsonObject lang = lang();
		assertTrue(lang.has("mettle.path.speech.a") && lang.has("mettle.path.speech.b"));
		assertEquals("Trader", lang.get("mettle.path.speech.a").getAsString());
		assertEquals("Courtier", lang.get("mettle.path.speech.b").getAsString());
		assertEquals(0xE8C46A, s.colour(), "honey");
		assertTrue(Files.exists(RES.resolve("assets/mettle/textures/gui/skill/speech.png")));
	}

	@Test
	void treeMatchesTheDesign() throws Exception {
		JsonObject lang = lang();
		Map<Perk.Kind, Integer> kinds = new EnumMap<>(Perk.Kind.class);
		Map<String, Integer> paths = new HashMap<>();
		Map<String, Integer> sides = new HashMap<>();
		int files = 0;
		try (Stream<Path> s = Files.list(DATA.resolve("perk/speech"))) {
			files = (int) s.count();
		}
		int fields = 0;
		for (Field f : SpeechSkill.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Identifier.class || !((Identifier) f.get(null)).getPath().startsWith("speech/")) {
				continue;
			}
			fields++;
			Identifier id = (Identifier) f.get(null);
			Perk perk = load(id);
			assertEquals("mettle:speech", perk.skill().identifier().toString(), id + " belongs to Speech");
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
				assertEquals(10, perk.synergy().get(SpeechSkill.SPEECH));
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
		assertEquals(files, fields, "every perk file has its id in SpeechSkill");
		assertEquals(2, kinds.get(Perk.Kind.ROOT));
		assertEquals(8, kinds.get(Perk.Kind.PATH));
		assertEquals(3, kinds.get(Perk.Kind.MASTERY));
		assertEquals(2, kinds.get(Perk.Kind.CURSED));
		assertEquals(2, kinds.get(Perk.Kind.FEAT));
		assertEquals(2, kinds.get(Perk.Kind.SYNERGY));
		assertEquals(1, kinds.get(Perk.Kind.TEACHING));
		assertEquals(Map.of("a2", 2, "a3", 2, "b2", 2, "b3", 2), paths);
		assertEquals(Map.of("a", 1, "any", 1, "b", 1), sides);
		// Haggler and Glib's ranks; the feats' counters; the synergies' partners; the masteries' sides.
		assertEquals(List.of("2", "4", "6"), load(SpeechSkill.HAGGLER).rankValues());
		assertEquals(List.of("1", "2", "3"), load(SpeechSkill.GLIB).rankValues());
		assertEquals(Map.of(SpeechSkill.SPEECH, 10, dev.voidmc.mettle.character.Progress.skill("athletics"), 10), load(SpeechSkill.CARAVAN_MASTER).synergy());
		assertEquals(Map.of(SpeechSkill.SPEECH, 10, dev.voidmc.mettle.character.Progress.skill("survival"), 10), load(SpeechSkill.TAVERN_KEEPER).synergy());
		assertEquals(SpeechSkill.GOLDEN_MOUTH_COUNTER, load(SpeechSkill.GOLDEN_MOUTH).feat().orElseThrow().counter());
		assertEquals(SpeechRules.GOLDEN_MOUTH_TARGET, load(SpeechSkill.GOLDEN_MOUTH).feat().orElseThrow().target());
		assertEquals(SpeechSkill.MARKET_REGULAR_COUNTER, load(SpeechSkill.MARKET_REGULAR).feat().orElseThrow().counter());
		assertEquals(SpeechRules.MARKET_REGULAR_TARGET, load(SpeechSkill.MARKET_REGULAR).feat().orElseThrow().target());
		assertEquals("a", load(SpeechSkill.MERCHANT_PRINCE).side());
		assertEquals("any", load(SpeechSkill.HEROS_WELCOME).side());
		assertEquals("b", load(SpeechSkill.VOICE_OF_THE_REALM).side());
	}

	/** §14.2's trait table: id → min, max, the attribute that boosts it, whether it is boosted, whether it needs Hearth's keys. */
	static final Map<String, Object[]> TRAITS = Map.ofEntries(
		Map.entry("haggling", new Object[] {1, 3, Trait.Kind.FORTUNE, true, false}), Map.entry("persuasive", new Object[] {1, 2, Trait.Kind.FORTUNE, false, false}),
		Map.entry("appraising", new Object[] {3, 8, Trait.Kind.FORTUNE, true, false}), Map.entry("provisioning", new Object[] {3, 8, Trait.Kind.FORTUNE, true, false}),
		Map.entry("charming", new Object[] {5, 12, Trait.Kind.FORTUNE, true, true}), Map.entry("imposing", new Object[] {3, 8, Trait.Kind.MIGHT, true, true}),
		Map.entry("smooth", new Object[] {3, 8, Trait.Kind.FORTUNE, true, true}), Map.entry("patient", new Object[] {5, 15, Trait.Kind.ENDURANCE, true, true}),
		Map.entry("shrewd", new Object[] {1, 3, Trait.Kind.FORTUNE, false, true}), Map.entry("bartering", new Object[] {5, 12, Trait.Kind.FORTUNE, true, false}),
		Map.entry("gregarious", new Object[] {5, 12, Trait.Kind.FORTUNE, true, false}), Map.entry("rallying", new Object[] {2, 5, Trait.Kind.FORTUNE, true, false}),
		Map.entry("heroic", new Object[] {25, 50, Trait.Kind.FORTUNE, true, false}), Map.entry("coinwise", new Object[] {20, 40, Trait.Kind.MIGHT, true, false}));

	@Test
	void theFourteenTraits() throws IOException {
		JsonObject lang = lang();
		int n = 0;
		try (Stream<Path> files = Files.list(DATA.resolve("trait/speech"))) {
			for (Path p : files.toList()) {
				String id = p.getFileName().toString().replace(".json", "");
				Trait t = read(p, Trait.CODEC);
				Object[] want = TRAITS.get(id);
				assertNotNull(want, "a trait the design doesn't list: " + id);
				assertEquals(((Integer) want[0]).doubleValue(), t.min(), 1e-9, id + " min");
				assertEquals(((Integer) want[1]).doubleValue(), t.max(), 1e-9, id + " max");
				assertEquals(want[2], t.kind(), id + " attribute");
				assertEquals(want[3], t.boosted(), id + " boosted");
				assertEquals((Boolean) want[4], t.requires().map(Trait.HEARTH_BONUS::equals).orElse(false), id + " requires Hearth's keys");
				assertEquals((Boolean) want[4], !dev.voidmc.mettle.character.cards.CardRules.rollable(t, false), id + " rolled without keys");
				assertTrue(dev.voidmc.mettle.character.cards.CardRules.rollable(t, true), id + " rolled with keys");
				assertEquals("mettle:speech", t.skill().identifier().toString());
				assertTrue(lang.has(key(t.name())) && lang.has(key(t.description())), id + " text");
				n++;
			}
		}
		assertEquals(14, n);
	}

	@Test
	void theCardSkill() throws IOException {
		CardSkill cs = read(DATA.resolve("card_skill/speech.json"), CardSkill.CODEC);
		assertEquals("SP", cs.prefix());
		assertEquals(List.of("kin", "hoard", "keen", "warden", "ember"), cs.pool().stream().map(Identifier::getPath).toList());
		Map<String, String> natives = new HashMap<>();
		cs.natives().forEach((perk, kw) -> natives.put(perk.identifier().getPath(), kw.getPath()));
		assertEquals(Map.ofEntries(Map.entry("speech/haggler", "hoard"), Map.entry("speech/glib", "kin"), Map.entry("speech/provisioner", "ember"),
			Map.entry("speech/appraiser", "keen"), Map.entry("speech/ledger_keeper", "hoard"), Map.entry("speech/shrewd_eye", "keen"),
			Map.entry("speech/charmer", "kin"), Map.entry("speech/barterer", "hoard"), Map.entry("speech/commanding_voice", "kin"),
			Map.entry("speech/second_word", "echo"), Map.entry("speech/merchant_prince", "hoard"), Map.entry("speech/heros_welcome", "warden"),
			Map.entry("speech/voice_of_the_realm", "kin"), Map.entry("speech/honeyed_lies", "hex"), Map.entry("speech/demagogue", "hex")), natives);
		for (String kw : natives.values()) {
			read(DATA.resolve("keyword/" + kw + ".json"), Keyword.CODEC);
		}
	}

	@Test
	void everyFeedbackChipHasText() throws IOException {
		JsonObject lang = lang();
		for (String key : new String[] {"golden_mouth", "barterer", "restock", "heros_welcome", "caravan", "cooked"}) {
			assertTrue(lang.has("mettle.feedback.speech." + key), key);
		}
		for (String key : new String[] {"mettle.tome.the_courtiers_manners", "mettle.speech.cooked_by", "mettle.speech.appraise", "mettle.speech.appraise.villager",
			"mettle.speech.appraise.none", "mettle.skill.speech.levels", "mettle.byname.skill.speech.noun", "mettle.byname.pair.stealth.speech"}) {
			assertTrue(lang.has(key), key);
		}
	}

	private static Perk load(Identifier id) throws IOException {
		String path = "/data/" + id.getNamespace() + "/mettle/perk/" + id.getPath() + ".json";
		try (InputStream in = SpeechDataTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "no data file for " + id);
			return Perk.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
		}
	}
}
