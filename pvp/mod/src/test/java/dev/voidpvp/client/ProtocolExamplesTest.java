package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.net.Protocol;
import dev.voidpvp.client.state.GlobalSettings;
import dev.voidpvp.client.state.HudItem;
import dev.voidpvp.client.state.Loadout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every example in {@code schema/protocol.json}, round-tripped through {@code net/}. */
class ProtocolExamplesTest {

    @Test
    @DisplayName("every protocol.json example round-trips through the codec")
    void roundTripsEveryExample() {
        JsonArray examples = Schemas.examples("protocol.json");
        assertTrue(examples.size() >= 9, "protocol.json lost examples: " + examples.size());
        int javaToRust = 0;
        int rustToJava = 0;

        for (int i = 0; i < examples.size(); i++) {
            JsonObject example = examples.get(i).getAsJsonObject();
            String t = example.get("t").getAsString();
            String where = "protocol.json examples[" + i + "] (" + t + ")";

            if ("hello".equals(t)) {
                JsonObject rebuilt = Protocol.hello(
                        example.get("mc").getAsString(),
                        example.get("mod").getAsString(),
                        example.get("token").getAsString());
                assertEquals(example, rebuilt, where);
                javaToRust++;
            } else if ("state".equals(t)) {
                Map<String, JsonElement> patch = new LinkedHashMap<String, JsonElement>();
                for (Map.Entry<String, JsonElement> e
                        : example.getAsJsonObject("patch").entrySet()) {
                    patch.put(e.getKey(), e.getValue());
                }
                JsonObject rebuilt = Protocol.state(
                        example.get("loadout").getAsString(), patch);
                assertEquals(example, rebuilt, where);
                javaToRust++;
            } else if ("hud".equals(t)) {
                List<HudItem> items = new ArrayList<HudItem>();
                JsonArray arr = example.getAsJsonArray("items");
                for (int j = 0; j < arr.size(); j++) {
                    items.add(HudItem.fromJson(arr.get(j).getAsJsonObject()));
                }
                JsonObject rebuilt = Protocol.hud(
                        example.get("loadout").getAsString(), items);
                Schemas.assertContains(example, rebuilt, where);
                javaToRust++;
            } else if ("session".equals(t)) {
                JsonObject rebuilt = Protocol.session(
                        example.get("fps_avg").getAsDouble(),
                        example.get("played_ms").getAsLong(),
                        example.has("server") && !example.get("server").isJsonNull()
                                ? example.get("server").getAsString() : null,
                        example.has("loadout") ? example.get("loadout").getAsString() : null);
                assertEquals(example, rebuilt, where);
                javaToRust++;
            } else if ("server".equals(t)) {
                JsonObject rebuilt = Protocol.server(
                        example.get("host").getAsString(),
                        example.get("connected").getAsBoolean(),
                        example.has("port") ? example.get("port").getAsInt() : 25565);
                assertEquals(example, rebuilt, where);
                javaToRust++;
            } else if ("init".equals(t)) {
                Protocol.Inbound in = Protocol.parse(example.toString());
                assertEquals(Protocol.Inbound.Kind.INIT, in.kind, where);
                assertEquals(Protocol.VERSION, in.version, where);
                assertNotNull(in.loadout, where);
                assertEquals(example.getAsJsonObject("loadout").get("id").getAsString(),
                        in.loadout.id(), where);
                Schemas.assertContains(example.getAsJsonObject("loadout"),
                        in.loadout.toJson(), where + " loadout");
                assertEquals(example.getAsJsonArray("loadouts").size(), in.loadouts.size(),
                        where + " library size");
                Schemas.assertContains(example.getAsJsonObject("settings"),
                        in.settings.toJson(), where + " settings");
                rustToJava++;
            } else if ("loadout".equals(t)) {
                Protocol.Inbound in = Protocol.parse(example.toString());
                assertEquals(Protocol.Inbound.Kind.LOADOUT, in.kind, where);
                Schemas.assertContains(example.getAsJsonObject("loadout"),
                        in.loadout.toJson(), where);
                rustToJava++;
            } else if ("settings".equals(t)) {
                Protocol.Inbound in = Protocol.parse(example.toString());
                assertEquals(Protocol.Inbound.Kind.SETTINGS, in.kind, where);
                Schemas.assertContains(example.getAsJsonObject("settings"),
                        in.settings.toJson(), where);
                rustToJava++;
            } else {
                throw new AssertionError("unhandled protocol message type " + t
                        + "; teach net/Protocol about it");
            }
        }
        assertEquals(6, javaToRust, "expected six Java to Rust examples");
        assertEquals(3, rustToJava, "expected three Rust to Java examples");
    }

    @Test
    @DisplayName("every loadout.json example survives a parse and re-serialise")
    void loadoutExamplesRoundTrip() {
        JsonArray examples = Schemas.examples("loadout.json");
        for (int i = 0; i < examples.size(); i++) {
            JsonObject example = examples.get(i).getAsJsonObject();
            Loadout loadout = Loadout.fromJson(example);
            Schemas.assertContains(example, loadout.toJson(),
                    "loadout.json examples[" + i + "]");
            Schemas.assertContains(example, loadout.copy().toJson(),
                    "loadout.json examples[" + i + "] after copy");
        }
    }

    @Test
    @DisplayName("an unknown t and unknown fields are ignored, never an error")
    void forwardCompatible() {
        assertEquals(Protocol.Inbound.Kind.UNKNOWN,
                Protocol.parse("{\"t\":\"cosmetics\",\"payload\":{}}").kind);
        assertEquals(Protocol.Inbound.Kind.UNKNOWN, Protocol.parse("{}").kind);
        assertEquals(Protocol.Inbound.Kind.UNKNOWN, Protocol.parse("not json at all{").kind);

        Protocol.Inbound in = Protocol.parse("{\"t\":\"settings\",\"settings\":"
                + "{\"menu_key\":\"RSHIFT\",\"unheard_of\":42},\"also_new\":true}");
        assertEquals(Protocol.Inbound.Kind.SETTINGS, in.kind);
        assertEquals("RSHIFT", in.settings.menuKey);
    }

    @Test
    @DisplayName("global settings fall back to the schema defaults")
    void settingsDefaults() {
        GlobalSettings defaults = GlobalSettings.fromJson(new JsonObject());
        assertEquals("RSHIFT", defaults.menuKey);
        assertEquals("L", defaults.cycleLoadoutKey);
        assertEquals("void-dark", defaults.theme);
        assertEquals(1.0, defaults.uiScale, 1e-9);
        assertEquals(4, defaults.hudEditorGrid);

        JsonObject nonsense = new JsonObject();
        nonsense.addProperty("menu_key", "NOT_A_KEY");
        nonsense.addProperty("ui_scale", 99);
        GlobalSettings clamped = GlobalSettings.fromJson(nonsense);
        assertEquals("RSHIFT", clamped.menuKey, "an invalid keybind falls back");
        assertEquals(3.0, clamped.uiScale, 1e-9, "ui_scale clamps to its maximum");
    }
}
