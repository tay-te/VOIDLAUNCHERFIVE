package dev.voidpvp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.net.Protocol;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.Loadout;
import dev.voidpvp.client.state.LoadoutDiff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hot-swap delta: what a loadout switch tells Rust (§8.2). */
class LoadoutDiffTest {

    private static Loadout loadout(String json) {
        return Loadout.fromJson(Json.parseObject(json));
    }

    @Test
    @DisplayName("identical loadouts produce an empty patch")
    void noChangeNoPatch() {
        Loadout a = loadout("{\"id\":\"a\",\"name\":\"A\",\"icon\":\"sword\",\"mc\":\"1.8.9\","
                + "\"mods\":{\"fullbright\":{\"on\":true}},\"hud\":[]}");
        assertTrue(LoadoutDiff.diff(a, a.copy()).isEmpty());
    }

    @Test
    @DisplayName("the patch names every effective setting that moved")
    void diffNamesTheChanges() {
        Loadout before = loadout("{\"id\":\"a\",\"name\":\"A\",\"icon\":\"sword\",\"mc\":\"1.8.9\","
                + "\"mods\":{\"fullbright\":{\"on\":false},\"zoom\":{\"on\":true,\"key\":\"C\"}},"
                + "\"hud\":[]}");
        Loadout after = loadout("{\"id\":\"b\",\"name\":\"B\",\"icon\":\"bed\",\"mc\":\"1.8.9\","
                + "\"mods\":{\"fullbright\":{\"on\":true},\"zoom\":{\"on\":true,\"key\":\"V\"}},"
                + "\"hud\":[]}");

        Map<String, JsonElement> patch = LoadoutDiff.diff(before, after);
        assertEquals(2, patch.size(), "only what moved: " + patch);
        assertEquals(new JsonPrimitive(Boolean.TRUE), patch.get("mods.fullbright.on"));
        assertEquals(new JsonPrimitive("V"), patch.get("mods.zoom.key"));
    }

    @Test
    @DisplayName("a setting a loadout omits still diffs against the registry default")
    void defaultsParticipate() {
        Loadout before = loadout("{\"id\":\"a\",\"name\":\"A\",\"icon\":\"s\",\"mc\":\"1.8.9\","
                + "\"mods\":{\"fullbright\":{\"on\":false,\"gamma\":15}},\"hud\":[]}");
        Loadout after = loadout("{\"id\":\"b\",\"name\":\"B\",\"icon\":\"s\",\"mc\":\"1.8.9\","
                + "\"mods\":{\"fullbright\":{\"on\":false}},\"hud\":[]}");

        Map<String, JsonElement> patch = LoadoutDiff.diff(before, after);
        assertEquals(1, patch.size());
        assertEquals(10.0, patch.get("mods.fullbright.gamma").getAsDouble(), 1e-9,
                "gamma falls back to the factory default of 10");
    }

    @Test
    @DisplayName("switching from nothing reports the whole loadout")
    void diffFromNull() {
        Loadout after = loadout("{\"id\":\"b\",\"name\":\"B\",\"icon\":\"s\",\"mc\":\"1.8.9\","
                + "\"mods\":{},\"hud\":[]}");
        Map<String, JsonElement> patch = LoadoutDiff.diff(null, after);
        assertTrue(patch.size() > 40, "every setting of all twelve mods: " + patch.size());
        for (String path : patch.keySet()) {
            assertTrue(path.matches("^mods\\.[a-z_]+\\.[a-z_]+$"),
                    path + " does not match the state_patch path pattern");
        }
    }

    @Test
    @DisplayName("a diff serialises as a valid state message")
    void diffSerialises() {
        Loadout before = loadout("{\"id\":\"a\",\"name\":\"A\",\"icon\":\"s\",\"mc\":\"1.8.9\","
                + "\"mods\":{\"fullbright\":{\"on\":false}},\"hud\":[]}");
        Loadout after = loadout("{\"id\":\"a\",\"name\":\"A\",\"icon\":\"s\",\"mc\":\"1.8.9\","
                + "\"mods\":{\"fullbright\":{\"on\":true}},\"hud\":[]}");
        JsonObject message = Protocol.state("a", LoadoutDiff.diff(before, after));
        assertEquals("state", message.get("t").getAsString());
        assertEquals("a", message.get("loadout").getAsString());
        assertEquals(1, message.getAsJsonObject("patch").entrySet().size());
    }

    @Test
    @DisplayName("single() is the one-entry form of the same patch")
    void singlePatch() {
        Map<String, JsonElement> patch =
                LoadoutDiff.single("zoom", "key", new JsonPrimitive("V"));
        assertEquals(1, patch.size());
        assertEquals(new JsonPrimitive("V"), patch.get("mods.zoom.key"));
    }
}
