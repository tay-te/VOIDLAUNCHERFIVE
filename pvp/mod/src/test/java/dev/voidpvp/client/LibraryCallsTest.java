package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.net.OutboundQueue;
import dev.voidpvp.client.net.Protocol;
import dev.voidpvp.client.state.GlobalSettings;
import dev.voidpvp.client.state.HudItem;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The in-game Loadouts screen's bridge calls: create, rename, duplicate, delete, export, import
 * and quick pins. Each answers synchronously with what was stored, tells Rust through the sink
 * (which is what persists it), and re-pushes the library to the page.
 */
class LibraryCallsTest {

    /** Records what Rust would be told. */
    static final class Recorder implements LiveState.Sink {
        final List<String> log = new ArrayList<String>();

        @Override
        public void state(String loadoutId, Map<String, JsonElement> patch) {
            log.add("state " + loadoutId);
        }

        @Override
        public void hud(String loadoutId, List<HudItem> items) {
            log.add("hud " + loadoutId);
        }

        @Override
        public void globals(Map<String, JsonElement> patch) {
            log.add("globals");
        }

        @Override
        public void loadoutSave(JsonObject loadout) {
            log.add("save " + loadout.get("id").getAsString() + " " + loadout.get("name").getAsString());
        }

        @Override
        public void loadoutDelete(String loadoutId) {
            log.add("delete " + loadoutId);
        }

        @Override
        public void loadoutSelect(String loadoutId) {
            log.add("select " + loadoutId);
        }
    }

    private static LiveState state(Recorder sink) {
        LiveState s = new LiveState();
        s.applyInit(Loadout.defaults("sword-pvp", "Sword PvP"),
                Arrays.asList(Loadout.defaults("sword-pvp", "Sword PvP"),
                        Loadout.defaults("bedwars", "Bedwars")),
                GlobalSettings.defaults());
        s.setSink(sink);
        return s;
    }

    private static JsonElement call(VoidBridge bridge, String name, Object... params) {
        JsonObject env = new JsonObject();
        env.addProperty("c", name);
        JsonArray p = new JsonArray();
        for (Object o : params) {
            p.add(Json.parse(new com.google.gson.Gson().toJson(o)));
        }
        env.add("params", p);
        return Json.parseObject(bridge.dispatch(env.toString())).get("returns");
    }

    private static List<String> pushedChannels(VoidBridge bridge) {
        List<String> out = new ArrayList<String>();
        String script = bridge.drainScript();
        if (script == null) {
            return out;
        }
        JsonArray batch = Json.parse(script.substring("window.void.__emit(".length(),
                script.length() - 1)).getAsJsonArray();
        for (JsonElement e : batch) {
            out.add(e.getAsJsonObject().get("e").getAsString());
        }
        return out;
    }

    @Test
    @DisplayName("create appends at the defaults under a slug id, tells Rust, and re-pushes the library")
    void create() {
        Recorder rust = new Recorder();
        LiveState s = state(rust);
        VoidBridge bridge = new VoidBridge(s, null);

        JsonElement created = call(bridge, "createLoadout", "  Sword PvP ");
        assertEquals("sword-pvp-2", created.getAsJsonObject().get("id").getAsString(),
                "a taken slug gets a numeric suffix, the way Rust's unique_id does");
        assertEquals("Sword PvP", created.getAsJsonObject().get("name").getAsString());
        assertEquals("sword-pvp", s.loadoutId(), "creating does not switch");
        assertEquals(3, s.library().size());
        assertEquals("sword-pvp-2", s.library().get(2).id(), "appended, in library order");
        assertEquals(Arrays.asList("save sword-pvp-2 Sword PvP"), rust.log);
        assertEquals(Arrays.asList("loadouts"), pushedChannels(bridge));

        assertTrue(call(bridge, "createLoadout", "   ").isJsonNull(), "an empty name is refused");
    }

    @Test
    @DisplayName("rename keeps the id; renaming the active loadout pushes it too")
    void rename() {
        Recorder rust = new Recorder();
        LiveState s = state(rust);
        VoidBridge bridge = new VoidBridge(s, null);

        assertEquals("Swords", call(bridge, "renameLoadout", "sword-pvp", " Swords ").getAsString());
        assertEquals("Swords", s.loadout().name());
        assertEquals("sword-pvp", s.loadoutId());
        assertEquals(Arrays.asList("loadouts", "loadout"), pushedChannels(bridge));
        assertEquals(Arrays.asList("save sword-pvp Swords"), rust.log);
        assertTrue(call(bridge, "renameLoadout", "nope", "X").isJsonNull());
    }

    @Test
    @DisplayName("duplicate copies settings and pins, without stats, as '<name> copy'")
    void duplicate() {
        Recorder rust = new Recorder();
        LiveState s = state(rust);
        VoidBridge bridge = new VoidBridge(s, null);
        s.setModSetting("zoom", "fov_divisor", new com.google.gson.JsonPrimitive(Double.valueOf(7)));
        call(bridge, "setQuickPins", Arrays.asList("zoom", "hitboxes"));

        JsonObject copy = call(bridge, "duplicateLoadout", "sword-pvp").getAsJsonObject();
        assertEquals("sword-pvp-copy", copy.get("id").getAsString());
        assertEquals("Sword PvP copy", copy.get("name").getAsString());
        assertEquals(7.0, copy.getAsJsonObject("mods").getAsJsonObject("zoom")
                .get("fov_divisor").getAsDouble(), 1e-9);
        assertEquals(2, copy.getAsJsonArray("quick").size());
        assertFalse(copy.has("stats"));
    }

    @Test
    @DisplayName("delete refuses the last loadout, and deleting the active one switches first")
    void delete() {
        Recorder rust = new Recorder();
        LiveState s = state(rust);
        VoidBridge bridge = new VoidBridge(s, null);

        assertTrue(call(bridge, "deleteLoadout", "sword-pvp").getAsBoolean());
        assertEquals("bedwars", s.loadoutId(), "the first remaining loadout took over");
        assertEquals(Arrays.asList("loadouts", "loadout"), pushedChannels(bridge));
        assertTrue(rust.log.contains("select bedwars"));
        assertEquals("delete sword-pvp", rust.log.get(rust.log.size() - 1));
        assertFalse(call(bridge, "deleteLoadout", "bedwars").getAsBoolean(), "never the last");
        assertFalse(call(bridge, "deleteLoadout", "nope").getAsBoolean());
    }

    @Test
    @DisplayName("export then import lands a copy under a fresh id; a bad code says why")
    void exportImport() {
        Recorder rust = new Recorder();
        LiveState s = state(rust);
        VoidBridge bridge = new VoidBridge(s, null);
        s.setModSetting("cps", "mode", new com.google.gson.JsonPrimitive("both"));

        String code = call(bridge, "exportLoadout", "sword-pvp").getAsString();
        assertTrue(code.startsWith("VOID1."));
        pushedChannels(bridge);
        rust.log.clear();

        JsonObject result = call(bridge, "importLoadout", code).getAsJsonObject();
        assertTrue(result.get("ok").getAsBoolean());
        assertEquals(0, result.get("dropped").getAsInt());
        JsonObject imported = result.getAsJsonObject("loadout");
        assertEquals("sword-pvp-2", imported.get("id").getAsString());
        assertEquals("both", imported.getAsJsonObject("mods").getAsJsonObject("cps")
                .get("mode").getAsString());
        assertEquals(Arrays.asList("save sword-pvp-2 Sword PvP"), rust.log);
        assertEquals(Arrays.asList("loadouts"), pushedChannels(bridge));

        JsonObject bad = call(bridge, "importLoadout", "not a code").getAsJsonObject();
        assertFalse(bad.get("ok").getAsBoolean());
        assertEquals("format", bad.get("error").getAsString());
        assertNull(bridge.drainScript(), "a refused import pushes nothing");
        assertTrue(call(bridge, "exportLoadout", "nope").isJsonNull());
    }

    @Test
    @DisplayName("quick pins are normalised and land on the active loadout")
    void quickPins() {
        Recorder rust = new Recorder();
        LiveState s = state(rust);
        VoidBridge bridge = new VoidBridge(s, null);
        JsonArray stored = call(bridge, "setQuickPins",
                Arrays.asList("zoom", "zoom", "teleport", "fullbright", "fps", "cps", "ping",
                        "keystrokes", "hitboxes", "crosshair", "fov")).getAsJsonArray();
        assertEquals(8, stored.size(), "cut to eight");
        assertEquals("zoom", stored.get(0).getAsString());
        assertEquals("fullbright", stored.get(1).getAsString(), "repeats and unknown ids dropped");
        assertEquals(8, s.loadout().quick().size());
        assertEquals("save sword-pvp Sword PvP", rust.log.get(0));
    }

    @Test
    @DisplayName("a picked loadout moves Rust's pointer; the cycle key's switch does not double-report")
    void selectReportsOnce() {
        Recorder rust = new Recorder();
        LiveState s = state(rust);
        VoidBridge bridge = new VoidBridge(s, null);
        assertTrue(call(bridge, "switchLoadout", "bedwars").getAsBoolean());
        assertTrue(rust.log.contains("select bedwars"));
        rust.log.clear();
        assertTrue(s.switchLoadout("sword-pvp"), "the L-key path");
        assertFalse(rust.log.contains("select sword-pvp"), "the L key reports itself as a hotkey");
    }

    @Test
    @DisplayName("Rust's library replaces the library but keeps the live active loadout")
    void applyLibrary() {
        LiveState s = state(new Recorder());
        s.setModSetting("cps", "mode", new com.google.gson.JsonPrimitive("both"));
        Loadout renamed = Loadout.defaults("sword-pvp", "Renamed in launcher");
        assertFalse(s.applyLibrary(Arrays.asList(renamed, Loadout.defaults("uhc", "UHC"))));
        assertEquals(2, s.library().size());
        assertEquals("Renamed in launcher", s.loadout().name());
        assertEquals("both", s.loadout().stringSetting("cps", "mode", "?"),
                "live settings are Java's, not the launcher's copy");

        assertTrue(s.applyLibrary(Arrays.asList(Loadout.defaults("uhc", "UHC"))),
                "the active loadout was deleted in the launcher");
        assertEquals("uhc", s.loadoutId());
    }

    @Test
    @DisplayName("library changes made offline replay first, coalesced per loadout")
    void offlineQueue() {
        OutboundQueue q = new OutboundQueue();
        JsonObject a = Loadout.defaults("a", "A").toJson();
        q.addState("a", java.util.Collections.<String, JsonElement>singletonMap("mods.cps.on",
                new com.google.gson.JsonPrimitive(Boolean.TRUE)));
        q.addLoadoutSave("a", a);
        q.addLoadoutSave("b", Loadout.defaults("b", "B").toJson());
        q.addLoadoutDelete("b");
        q.setSelected("a");
        List<JsonObject> out = q.drain();
        assertEquals("loadout_save", out.get(0).get("t").getAsString(), "library first");
        assertEquals("loadout_delete", out.get(1).get("t").getAsString(), "b's save became its delete");
        assertEquals("loadout_select", out.get(2).get("t").getAsString());
        assertEquals("state", out.get(3).get("t").getAsString());
        assertEquals(4, out.size());
        assertTrue(q.isEmpty());
        assertNotNull(Protocol.loadoutSave(a).get("loadout"));
    }
}
