package dev.voidpvp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.state.ModRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java copy of the mod registry against the real {@code schema/mods.json}.
 * The mod cannot read the schema at runtime, so this is the guard that keeps
 * the transcription honest.
 */
class ModRegistryTest {

    private static JsonObject registry() {
        return Schemas.examples("mods.json").get(0).getAsJsonObject().getAsJsonObject("mods");
    }

    @Test
    @DisplayName("the twelve mod ids match mods.json")
    void idsMatch() {
        Set<String> schemaIds = new LinkedHashSet<String>();
        for (Map.Entry<String, JsonElement> e : registry().entrySet()) {
            schemaIds.add(e.getKey());
        }
        List<String> ours = ModRegistry.modIds();
        assertEquals(12, ours.size());
        assertEquals(schemaIds, new LinkedHashSet<String>(ours));
    }

    @Test
    @DisplayName("every mod's kind matches mods.json")
    void kindsMatch() {
        for (Map.Entry<String, JsonElement> e : registry().entrySet()) {
            String id = e.getKey();
            String kind = e.getValue().getAsJsonObject().get("kind").getAsString();
            ModRegistry.Kind ours = ModRegistry.kind(id);
            assertNotNull(ours, id);
            assertEquals(kind, ours.name().toLowerCase(java.util.Locale.ROOT), id + " kind");
        }
    }

    @Test
    @DisplayName("every factory default matches mods.json")
    void defaultsMatch() {
        for (Map.Entry<String, JsonElement> e : registry().entrySet()) {
            String id = e.getKey();
            JsonObject expected = e.getValue().getAsJsonObject().getAsJsonObject("defaults");
            JsonObject ours = ModRegistry.defaults(id);
            for (Map.Entry<String, JsonElement> d : expected.entrySet()) {
                JsonElement mine = ours.get(d.getKey());
                assertNotNull(mine, id + "." + d.getKey() + " is missing from ModRegistry");
                if (d.getValue().getAsJsonPrimitive().isNumber()) {
                    assertEquals(d.getValue().getAsDouble(), mine.getAsDouble(), 1e-9,
                            id + "." + d.getKey());
                } else {
                    assertEquals(d.getValue(), mine, id + "." + d.getKey());
                }
            }
        }
    }

    @Test
    @DisplayName("every registry default survives its own clamp untouched")
    void defaultsAreInRange() {
        for (String id : ModRegistry.modIds()) {
            JsonObject defaults = ModRegistry.defaults(id);
            for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
                JsonElement clamped = ModRegistry.clamp(id, e.getKey(), e.getValue());
                assertNotNull(clamped, id + "." + e.getKey() + " was rejected by its own clamp");
                if (e.getValue().getAsJsonPrimitive().isNumber()) {
                    assertEquals(e.getValue().getAsDouble(), clamped.getAsDouble(), 1e-9,
                            id + "." + e.getKey());
                } else {
                    assertEquals(e.getValue(), clamped, id + "." + e.getKey());
                }
            }
        }
    }

    @Test
    @DisplayName("the hud and gameplay splits match mods.json")
    void splitsMatch() {
        JsonObject mods = Schemas.load("mods.json").getAsJsonObject("definitions")
                .getAsJsonObject("hud_mod_id");
        for (JsonElement id : mods.getAsJsonArray("enum")) {
            assertTrue(ModRegistry.isHud(id.getAsString()), id + " should be a HUD mod");
        }
        JsonObject gameplay = Schemas.load("mods.json").getAsJsonObject("definitions")
                .getAsJsonObject("gameplay_mod_id");
        for (JsonElement id : gameplay.getAsJsonArray("enum")) {
            assertTrue(ModRegistry.isGameplay(id.getAsString()),
                    id + " should be a gameplay mod");
        }
    }
}
