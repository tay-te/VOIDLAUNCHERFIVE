package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The sim's block tags name only blocks that exist: one unknown id makes the game drop the whole tag, and an empty
 * {@code #hearth:buildable_over} would have the applier refuse natural ground everywhere.
 */
class TagsTest {
	@Test
	void buildableOverNamesRealBlocks() throws Exception {
		TestSupport.boot();
		var in = TagsTest.class.getClassLoader().getResourceAsStream("data/hearth/tags/block/buildable_over.json");
		assertNotNull(in);
		int checked = 0;
		try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			for (JsonElement v : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")) {
				if (!v.isJsonPrimitive()) {
					// optional entries ({"id": ..., "required": false}) may name blocks of absent mods
					continue;
				}
				String id = v.getAsString();
				if (id.startsWith("#")) {
					continue;
				}
				assertTrue(BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)), "no such block: " + id);
				checked++;
			}
		}
		assertTrue(checked > 40);
	}
}
