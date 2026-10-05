package dev.voidmc.mettle.character.skill.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
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
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/**
 * The shipped Survival data: every perk id in code has a file that decodes, the tree has TREES.md §9's shape, the
 * general feats (§12) are feats with counters the code bumps, and every text exists.
 */
class SurvivalDataTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static JsonObject lang() throws IOException {
		return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mettle/lang/en_us/survival.json"))).getAsJsonObject();
	}

	@Test
	void treeMatchesTheDesign() throws Exception {
		JsonObject lang = lang();
		Map<Perk.Kind, Integer> kinds = new EnumMap<>(Perk.Kind.class);
		Map<String, Integer> paths = new HashMap<>();
		Map<String, Integer> sides = new HashMap<>();
		Map<String, Long> counters = new HashMap<>();
		int general = 0;
		for (Field f : SurvivalSkill.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Identifier.class) {
				continue;
			}
			Identifier id = (Identifier) f.get(null);
			Perk perk = load(id);
			assertEquals("mettle:survival", perk.skill().identifier().toString(), id + " is filed under Survival");
			if (id.getPath().startsWith("general/")) {
				general++;
				assertEquals(Perk.Kind.FEAT, perk.kind(), id + " is a general feat");
			} else {
				kinds.merge(perk.kind(), 1, Integer::sum);
			}
			if (perk.kind() == Perk.Kind.PATH) {
				paths.merge(perk.path() + perk.tier(), 1, Integer::sum);
				assertEquals(perk.tier(), perk.cost());
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
				assertEquals(2, perk.synergy().size());
			}
			perk.feat().ifPresent(feat -> counters.put(feat.counter(), feat.target()));
			for (Component text : new Component[] {perk.name(), perk.description()}) {
				String key = ((TranslatableContents) text.getContents()).getKey();
				assertTrue(lang.has(key), "missing lang " + key);
			}
			if (perk.kind() == Perk.Kind.FEAT || perk.kind() == Perk.Kind.TEACHING) {
				assertTrue(perk.hint().isPresent(), id + " needs a hint");
				assertTrue(lang.has(((TranslatableContents) perk.hint().get().getContents()).getKey()));
			}
		}
		assertEquals(2, kinds.get(Perk.Kind.ROOT));
		assertEquals(8, kinds.get(Perk.Kind.PATH));
		assertEquals(3, kinds.get(Perk.Kind.MASTERY));
		assertEquals(2, kinds.get(Perk.Kind.FEAT));
		assertEquals(1, kinds.get(Perk.Kind.SYNERGY));
		assertEquals(1, kinds.get(Perk.Kind.TEACHING));
		assertEquals(5, general);
		assertEquals(Map.of("a2", 2, "a3", 2, "b2", 2, "b3", 2), paths);
		assertEquals(Map.of("a", 1, "any", 1, "b", 1), sides);
		// The counters the code bumps, with TREES.md's targets.
		assertEquals(Map.of(SurvivalSkill.BIOMES_VISITED, 40L, SurvivalSkill.BIG_GAME_KILLS, 30L, SurvivalSkill.NEAR_DEATHS, 9L,
			SurvivalSkill.LANDMARK_KINDS, 8L, SurvivalSkill.OVERBURDENED_METRES, 1000L, SurvivalSkill.LIFE_TICKS, 720_000L,
			SurvivalSkill.SKILLS_AT_FIVE, 8L), counters);
		assertTrue(lang.has("mettle.path.survival.a") && lang.has("mettle.path.survival.b"));
	}

	@Test
	void everyFeedbackChipHasText() throws IOException {
		JsonObject lang = lang();
		for (String key : new String[] {"gatherer", "field_cook", "wild_food", "skinner", "big_game", "stalker", "sixth_sense",
			"iron_stomach", "campfire_rest", "tracker", "tracker.none", "direction.n", "direction.ne", "direction.e", "direction.se",
			"direction.s", "direction.sw", "direction.w", "direction.nw", "lay_of_the_land", "living_off_the_land",
			"weatherwise.rain", "weatherwise.storm", "trailblazer", "poultice", "poultice.wait", "discovered.biome",
			"discovered.place", "discovered.landmark"}) {
			assertTrue(lang.has("mettle.feedback.survival." + key), key);
		}
	}

	@Test
	void dataFilesExist() {
		for (String path : new String[] {"tags/block/survival/wild_plants.json", "tags/block/survival/forage_cover.json",
			"tags/block/survival/paths.json", "tags/item/survival/herbs.json", "tags/item/survival/cooked_food.json",
			"tags/item/survival/wild_food.json", "tags/item/survival/raw_food.json", "tags/item/survival/game_meat.json",
			"tags/item/survival/hides.json", "tags/entity_type/survival/big_game.json", "tags/worldgen/structure/survival/notable.json",
			"loot_table/survival/forage.json"}) {
			assertNotNull(SurvivalDataTest.class.getResource("/data/mettle/" + path), path);
		}
	}

	private static Perk load(Identifier id) throws IOException {
		String path = "/data/" + id.getNamespace() + "/mettle/perk/" + id.getPath() + ".json";
		try (InputStream in = SurvivalDataTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "no data file for " + id);
			return Perk.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
		}
	}
}
