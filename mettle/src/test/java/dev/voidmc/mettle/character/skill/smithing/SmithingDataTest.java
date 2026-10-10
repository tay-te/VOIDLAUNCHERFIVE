package dev.voidmc.mettle.character.skill.smithing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.skill.SmithingSkill;
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

/** The shipped Smithing data: every perk id in code has a file, every file decodes, the tree has TREES.md §10's shape, and every text exists. */
class SmithingDataTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void treeMatchesTheDesign() throws Exception {
		JsonObject lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mettle/lang/en_us/smithing.json"))).getAsJsonObject();
		Map<Perk.Kind, Integer> kinds = new EnumMap<>(Perk.Kind.class);
		Map<String, Integer> paths = new HashMap<>();
		Map<String, Integer> sides = new HashMap<>();
		for (Field f : SmithingSkill.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Identifier.class) {
				continue;
			}
			Identifier id = (Identifier) f.get(null);
			Perk perk = load(id);
			assertEquals("mettle:smithing", perk.skill().identifier().toString(), id + " belongs to Smithing");
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
		// Forgemaster and Battle-Worn belong to Mining and Defense; Smithing has no synergy of its own.
		assertEquals(null, kinds.get(Perk.Kind.SYNERGY));
		assertEquals(1, kinds.get(Perk.Kind.TEACHING));
		assertEquals(Map.of("a2", 2, "a3", 2, "b2", 2, "b3", 2), paths);
		assertEquals(Map.of("a", 1, "any", 1, "b", 1), sides);
		assertTrue(lang.has("mettle.path.smithing.a") && lang.has("mettle.path.smithing.b"));
	}

	@Test
	void everyFeedbackChipHasText() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mettle/lang/en_us/smithing.json"))).getAsJsonObject();
		// The cursed cards' names live with the draft's text.
		JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mettle/lang/en_us/draft.json"))).getAsJsonObject().entrySet()
			.forEach(e -> lang.add(e.getKey(), e.getValue()));
		// Every chip key the code sends, spelled out once here.
		for (String key : new String[] {"thrifty", "masterwork", "stamped", "anvil", "salvager", "honing.weapon", "honing.tool", "honing.worn",
			"reforge", "black_iron"}) {
			assertTrue(lang.has("mettle.feedback.smithing." + key), key);
		}
		for (Forged.Mark mark : Forged.Mark.values()) {
			if (mark != Forged.Mark.MASTERWORK && mark != Forged.Mark.FORGED) {
				assertTrue(lang.has("mettle.smithing.tooltip." + mark.getSerializedName()), mark.name());
			}
			assertTrue(lang.has("mettle.perk." + SmithingSkill.perk(mark).getPath().replace('/', '.')), mark.name());
		}
		for (Forged.Reforge reforge : Forged.Reforge.values()) {
			if (reforge != Forged.Reforge.NONE) {
				assertTrue(lang.has("mettle.smithing.tooltip.reforge." + reforge.getSerializedName()));
				assertTrue(lang.has("mettle.smithing.reforge." + reforge.getSerializedName()));
			}
		}
	}

	private static Perk load(Identifier id) throws IOException {
		String path = "/data/" + id.getNamespace() + "/mettle/perk/" + id.getPath() + ".json";
		try (InputStream in = SmithingDataTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "no data file for " + id);
			return Perk.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
		}
	}
}
