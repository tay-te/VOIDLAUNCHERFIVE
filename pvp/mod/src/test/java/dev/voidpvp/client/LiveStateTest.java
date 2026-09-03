package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.state.GlobalSettings;
import dev.voidpvp.client.state.HudItem;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live state: what a bridge call actually applies, what it reports back, and
 * what Rust is told afterwards (§6.1).
 */
class LiveStateTest {

    /** Records what would have gone out over the WS. */
    private static final class RecordingSink implements LiveState.Sink {
        final List<Map<String, JsonElement>> patches =
                new ArrayList<Map<String, JsonElement>>();
        final List<String> patchLoadouts = new ArrayList<String>();
        final List<List<HudItem>> layouts = new ArrayList<List<HudItem>>();

        @Override
        public void state(String loadoutId, Map<String, JsonElement> patch) {
            patchLoadouts.add(loadoutId);
            patches.add(new LinkedHashMap<String, JsonElement>(patch));
        }

        @Override
        public void hud(String loadoutId, List<HudItem> items) {
            layouts.add(new ArrayList<HudItem>(items));
        }

        Map<String, JsonElement> lastPatch() {
            return patches.get(patches.size() - 1);
        }
    }

    private LiveState state;
    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        JsonObject init = initExample();
        List<JsonObject> summaries = new ArrayList<JsonObject>();
        JsonArray arr = init.getAsJsonArray("loadouts");
        for (int i = 0; i < arr.size(); i++) {
            summaries.add(arr.get(i).getAsJsonObject());
        }
        state = new LiveState();
        sink = new RecordingSink();
        state.setSink(sink);
        state.applyInit(Loadout.fromJson(init.getAsJsonObject("loadout")), summaries,
                GlobalSettings.fromJson(init.getAsJsonObject("settings")));
    }

    private static JsonObject initExample() {
        JsonArray examples = Schemas.examples("protocol.json");
        for (int i = 0; i < examples.size(); i++) {
            JsonObject o = examples.get(i).getAsJsonObject();
            if ("init".equals(o.get("t").getAsString())) {
                return o;
            }
        }
        throw new AssertionError("protocol.json lost its init example");
    }

    @Test
    @DisplayName("init writes every actuator field from the loadout")
    void initAppliesActuators() {
        assertTrue(state.isInitialised());
        assertEquals("sword-pvp", state.loadout().id());
        assertTrue(state.toggleSprintOn, "the example has toggle_sprint on");
        assertFalse(state.fullbrightOn, "the example has fullbright off");
        assertTrue(state.zoomOn);
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("C"), state.zoomKeyCode);
        assertEquals(4.0, state.zoomFovDivisor, 1e-9);
        // Absent from the example, so the registry defaults fill in.
        assertFalse(state.hitboxesOn);
        assertEquals(10f, state.fullbrightGamma, 1e-6);
    }

    @Test
    @DisplayName("setGameplay applies in-process and reports the state applied")
    void setGameplayIsAuthoritative() {
        assertTrue(state.setGameplay("fullbright", true));
        assertTrue(state.fullbrightOn, "the actuator field is written synchronously");
        assertEquals(1, sink.patches.size());
        assertEquals("sword-pvp", sink.patchLoadouts.get(0));
        assertEquals(new JsonPrimitive(Boolean.TRUE),
                sink.lastPatch().get("mods.fullbright.on"));

        assertFalse(state.setGameplay("keystrokes", true), "a HUD mod is not a gameplay mod");
        assertEquals(1, sink.patches.size(), "a refused call tells Rust nothing");
    }

    @Test
    @DisplayName("setModSetting clamps rather than throws, and returns what it stored")
    void setModSettingClamps() {
        assertEquals(new JsonPrimitive(Double.valueOf(0.6)),
                state.setModSetting("keystrokes", "opacity", new JsonPrimitive(0.6)));
        assertEquals(new JsonPrimitive(Double.valueOf(1.0)),
                state.setModSetting("keystrokes", "opacity", new JsonPrimitive(4.0)),
                "opacity clamps to 1");
        assertEquals(new JsonPrimitive(Double.valueOf(0.0)),
                state.setModSetting("keystrokes", "opacity", new JsonPrimitive(-3)),
                "opacity clamps to 0");
        assertEquals(new JsonPrimitive(Long.valueOf(5000)),
                state.setModSetting("cps", "window_ms", new JsonPrimitive(999999)),
                "window_ms clamps to its maximum and stays an integer");

        // An unusable value leaves the stored one alone.
        assertEquals(new JsonPrimitive("left"),
                state.setModSetting("cps", "mode", new JsonPrimitive("sideways")));
        assertNull(state.setModSetting("cps", "no_such_setting", new JsonPrimitive(1)));
        assertNull(state.setModSetting("no_such_mod", "on", new JsonPrimitive(true)));
    }

    @Test
    @DisplayName("a keybind is upper-cased and validated against the schema pattern")
    void keybindsAreNormalised() {
        assertEquals(new JsonPrimitive("V"),
                state.setModSetting("zoom", "key", new JsonPrimitive("v")));
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("V"), state.zoomKeyCode);
        assertEquals(new JsonPrimitive("V"),
                state.setModSetting("zoom", "key", new JsonPrimitive("NOT_A_KEY")),
                "an invalid keybind leaves the old one in place");
    }

    @Test
    @DisplayName("setModSetting only reports a real change")
    void unchangedSettingsAreNotReported() {
        state.setModSetting("keystrokes", "opacity", new JsonPrimitive(0.5));
        int after = sink.patches.size();
        state.setModSetting("keystrokes", "opacity", new JsonPrimitive(0.5));
        assertEquals(after, sink.patches.size(), "writing the same value again says nothing");
    }

    @Test
    @DisplayName("setHud snaps to the editor grid and mirrors the whole layout")
    void setHudSnapsAndMirrors() {
        HudItem stored = state.setHud("keystrokes", "bottom-left", 33, -41, Double.valueOf(1.25));
        assertNotNull(stored);
        assertEquals("bottom-left", stored.anchor);
        assertEquals(32.0, stored.dx, 1e-9, "snapped to the 4px grid from init.settings");
        assertEquals(-40.0, stored.dy, 1e-9);
        assertEquals(1.25, stored.scale, 1e-9);

        assertEquals(1, sink.layouts.size(), "the whole layout goes to Rust, not a delta");
        assertTrue(sink.layouts.get(0).size() >= 2);
        assertNull(state.setHud("fullbright", "top-left", 0, 0, null),
                "a gameplay mod has no HUD item");
    }

    @Test
    @DisplayName("setHud clamps the schema bounds")
    void setHudClamps() {
        HudItem stored = state.setHud("fps", "nowhere", 99999, -99999, Double.valueOf(99));
        assertEquals("top-left", stored.anchor, "an unknown anchor falls back");
        assertEquals(4096.0, stored.dx, 1e-9);
        assertEquals(-4096.0, stored.dy, 1e-9);
        assertEquals(4.0, stored.scale, 1e-9);
    }

    @Test
    @DisplayName("switchLoadout needs the full loadout, and waits for it when it must")
    void switchLoadoutWaitsForRust() {
        assertFalse(state.switchLoadout("not-in-the-library"));
        assertTrue(state.switchLoadout("sword-pvp"), "switching to the active one is a no-op");
        assertNull(state.pendingSwitch());

        // `bedwars` is in init.loadouts as a summary only: the protocol has no
        // way to ask for the rest, so the switch is armed and completes when
        // the launcher pushes it.
        assertTrue(state.switchLoadout("bedwars"));
        assertEquals("bedwars", state.pendingSwitch());
        assertEquals("sword-pvp", state.loadout().id(), "nothing changed yet");

        state.cacheLoadout(Loadout.fromJson(bedwarsExample()));
        assertNull(state.pendingSwitch());
        assertEquals("bedwars", state.loadout().id());
        assertTrue(state.zoomOn);
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("V"), state.zoomKeyCode,
                "the new loadout's zoom key is live");
        assertFalse(state.toggleSprintOn == false, "bedwars keeps toggle_sprint on");

        assertFalse(sink.patches.isEmpty(), "the switch reported a state delta");
        assertEquals("bedwars", sink.patchLoadouts.get(sink.patchLoadouts.size() - 1),
                "the delta is tagged with the loadout it applies to");
        assertTrue(sink.lastPatch().containsKey("mods.zoom.key"));
    }

    @Test
    @DisplayName("a loadout switched in the launcher applies without a state echo")
    void remoteLoadoutDoesNotEcho() {
        int before = sink.patches.size();
        state.applyRemoteLoadout(Loadout.fromJson(bedwarsExample()));
        assertEquals("bedwars", state.loadout().id());
        assertEquals(before, sink.patches.size(),
                "the launcher already knows; Java does not answer with state");
    }

    @Test
    @DisplayName("the L key cycles the library in order, wrapping")
    void cycleWrapsThroughTheLibrary() {
        assertEquals("bedwars", state.nextLoadoutId());
        state.cacheLoadout(Loadout.fromJson(bedwarsExample()));
        state.switchLoadout("bedwars");
        assertEquals("sword-pvp", state.nextLoadoutId(), "and back round");
    }

    @Test
    @DisplayName("settings from the launcher move the hotkeys")
    void settingsMoveHotkeys() {
        JsonObject settings = new JsonObject();
        settings.addProperty("menu_key", "GRAVE");
        settings.addProperty("cycle_loadout_key", "K");
        settings.addProperty("hud_editor_grid", Integer.valueOf(0));
        state.applySettings(GlobalSettings.fromJson(settings));
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("GRAVE"), state.menuKeyCode);
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("K"), state.cycleLoadoutKeyCode);

        HudItem stored = state.setHud("fps", "top-left", 33, 21, null);
        assertEquals(33.0, stored.dx, 1e-9, "grid 0 disables snapping");
    }

    @Test
    @DisplayName("colours parse to packed ARGB")
    void colours() {
        assertEquals(0xFFFFFFFF, LiveState.parseColor("#FFFFFF", 0));
        assertEquals(0x80FF0000, LiveState.parseColor("#FF000080", 0));
        assertEquals(0xFF112233, LiveState.parseColor("#112233", 0));
        assertEquals(7, LiveState.parseColor("nonsense", 7));
        assertEquals(7, LiveState.parseColor(null, 7));
    }

    private static JsonObject bedwarsExample() {
        JsonArray examples = Schemas.examples("protocol.json");
        for (int i = 0; i < examples.size(); i++) {
            JsonObject o = examples.get(i).getAsJsonObject();
            if ("loadout".equals(o.get("t").getAsString())) {
                return o.getAsJsonObject("loadout");
            }
        }
        throw new AssertionError("protocol.json lost its loadout example");
    }
}
