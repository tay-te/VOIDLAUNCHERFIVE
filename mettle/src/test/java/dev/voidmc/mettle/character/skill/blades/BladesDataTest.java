package dev.voidmc.mettle.character.skill.blades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.skill.BladesSkill;
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
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/** The shipped Blades data: every perk id in code has a file, every file decodes, the tree has TREES.md §4's shape, and every text exists. */
class BladesDataTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void treeMatchesTheDesign() throws Exception {
		JsonObject lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mettle/lang/en_us/blades.json"))).getAsJsonObject();
		Map<Perk.Kind, Integer> kinds = new EnumMap<>(Perk.Kind.class);
		Map<String, Integer> paths = new HashMap<>();
		Map<String, Integer> sides = new HashMap<>();
		for (Field f : BladesSkill.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Identifier.class) {
				continue;
			}
			Identifier id = (Identifier) f.get(null);
			Perk perk = load(id);
			assertEquals("mettle:blades", perk.skill().identifier().toString(), id + " belongs to Blades");
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
				assertEquals(2, perk.synergy().size());
			}
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
		assertEquals(2, kinds.get(Perk.Kind.SYNERGY));
		assertEquals(1, kinds.get(Perk.Kind.TEACHING));
		assertEquals(Map.of("a2", 2, "a3", 2, "b2", 2, "b3", 2), paths);
		assertEquals(Map.of("a", 1, "any", 1, "b", 1), sides);
		assertTrue(lang.has("mettle.path.blades.a") && lang.has("mettle.path.blades.b"));
	}

	@Test
	void everyFeedbackChipHasText() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mettle/lang/en_us/blades.json"))).getAsJsonObject();
		// Every chip key the code sends, spelled out once here.
		for (String key : new String[] {"quick_draw", "momentum", "single_combat", "single_combat.guard", "giant_slayer", "executioner",
			"executioner.boss", "disarm", "bleed", "light_feet", "cut_and_run", "duelist", "duelist.critical", "whirlwind", "whirlwind.ready",
			"unbroken", "master_strike", "sword_and_board", "weapon_master"}) {
			assertTrue(lang.has("mettle.feedback.blades." + key), key);
		}
	}

	private static Perk load(Identifier id) throws IOException {
		String path = "/data/" + id.getNamespace() + "/mettle/perk/" + id.getPath() + ".json";
		try (InputStream in = BladesDataTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "no data file for " + id);
			return Perk.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
		}
	}
}
