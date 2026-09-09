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
        final List<Map<String, JsonElement>> globalPatches =
                new ArrayList<Map<String, JsonElement>>();

        @Override
        public void state(String loadoutId, Map<String, JsonElement> patch) {
            patchLoadouts.add(loadoutId);
            patches.add(new LinkedHashMap<String, JsonElement>(patch));
        }

        @Override
        public void hud(String loadoutId, List<HudItem> items) {
            layouts.add(new ArrayList<HudItem>(items));
        }

        @Override
        public void globals(Map<String, JsonElement> patch) {
            globalPatches.add(new LinkedHashMap<String, JsonElement>(patch));
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
        List<Loadout> library = new ArrayList<Loadout>();
        JsonArray arr = init.getAsJsonArray("loadouts");
        for (int i = 0; i < arr.size(); i++) {
            library.add(Loadout.fromJson(arr.get(i).getAsJsonObject()));
        }
        state = new LiveState();
        sink = new RecordingSink();
        state.setSink(sink);
        state.applyInit(Loadout.fromJson(init.getAsJsonObject("loadout")), library,
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
    @DisplayName("freelook mirrors a perspective as vanilla's own int, never a fourth value")
    void freelookFieldsAreMirrored() {
        // Absent from the example, so these are the registry's factory values — and the factory
        // values are Snaplook: hold, snap back, behind the head, on a key nobody has bound yet.
        assertFalse(state.freelookOn);
        assertEquals(dev.voidpvp.client.input.KeyNames.KEY_NONE, state.freelookKeyCode);
        assertTrue(state.freelookHold);
        assertTrue(state.freelookSnapBack);
        assertEquals(1, state.freelookPerspective, "third_back is vanilla's F5 view 1");

        assertEquals(new JsonPrimitive("free"),
                state.setModSetting("freelook", "perspective", new JsonPrimitive("free")));
        assertEquals(0, state.freelookPerspective,
                "free is vanilla's first-person arm: the eye is the pivot and nothing translates");
        assertEquals(new JsonPrimitive("third_front"),
                state.setModSetting("freelook", "perspective", new JsonPrimitive("third_front")));
        assertEquals(2, state.freelookPerspective);
        // The schema's enum is the only thing that can be stored, so the mirror is the only
        // thing that can be read — and it is inside vanilla's own range either way.
        assertEquals(new JsonPrimitive("third_front"),
                state.setModSetting("freelook", "perspective", new JsonPrimitive("freecam")),
                "a value outside the enum leaves the stored one alone");
        assertTrue(state.freelookPerspective >= 0 && state.freelookPerspective <= 2);

        state.setModSetting("freelook", "mode", new JsonPrimitive("toggle"));
        assertFalse(state.freelookHold);
        state.setModSetting("freelook", "snap_back", new JsonPrimitive(false));
        assertFalse(state.freelookSnapBack);
        state.setModSetting("freelook", "keybind", new JsonPrimitive("v"));
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("V"), state.freelookKeyCode);
    }

    @Test
    @DisplayName("hit_color: intensity owns alpha, and an alpha byte is refused rather than fixed")
    void hitColourAlphaHasOneOwner() {
        assertFalse(state.hitColorOn);
        assertEquals(0x2FB8A6, state.hitColorRgb, "six digits, and deliberately not red");
        assertTrue(state.hitColorOwnHitsOnly);
        assertEquals(1f, state.hitColorIntensity, 1e-6);

        // This setting `$ref`s `hex_color_rgb`, not the shared `hex_color`, so eight digits are
        // **refused at the clamp** and the last good value stands. It used to be accepted and
        // masked, which was the wrong half of the contract to enforce: `void-loadout`'s
        // `HexColorRgb` rejects an alpha byte on deserialization, so a stored one is fatal at
        // load whatever Java does with it — masking here would only have hidden the
        // disagreement from the player and from the bridge, which reports what was stored.
        // The write is refused, and `bridge.json` says what that looks like: Java is
        // authoritative for live state and a call returns *the value actually applied*, so a
        // rejected colour echoes the one still in force rather than null. Null is reserved for a
        // setting that does not exist — which is what `toggle_sprint.sneak_too` returns below.
        assertEquals(new JsonPrimitive("#2FB8A6"),
                state.setModSetting("hit_color", "color", new JsonPrimitive("#2FB8A600")),
                "an alpha byte belongs to intensity, and a refused write echoes the old colour");
        assertEquals(0x2FB8A6, state.hitColorRgb, "the refused write left the colour alone");

        // A six-digit change is stored normally, so the refusal above is about the alpha and not
        // about the setting being read-only.
        assertEquals(new JsonPrimitive("#FF8A3D"),
                state.setModSetting("hit_color", "color", new JsonPrimitive("#FF8A3D")));
        assertEquals(0xFF8A3D, state.hitColorRgb);
        assertEquals(0, state.hitColorRgb & 0xFF000000, "no alpha reaches the renderer");

        assertEquals(new JsonPrimitive(Double.valueOf(1.0)),
                state.setModSetting("hit_color", "intensity", new JsonPrimitive(4.0)),
                "there is no value above vanilla's own alpha");
        assertEquals(1f, state.hitColorIntensity, 1e-6);
        state.setModSetting("hit_color", "intensity", new JsonPrimitive(0.25));
        assertEquals(0.25f, state.hitColorIntensity, 1e-6);
    }

    @Test
    @DisplayName("damage_tint mirrors the hurt roll as degrees, and vanilla's own when off")
    void damageTintFieldsAreMirrored() {
        assertFalse(state.damageTintOn);
        assertEquals(6, state.damageTintThreshold, "three hearts");
        assertEquals(0.6f, state.damageTintStrength, 1e-6);
        assertEquals(14f, state.damageTintShake, 0f,
                "with the mod off the mixin's substitution has to be the identity");

        state.setGameplay("damage_tint", true);
        assertTrue(state.damageTintOn);
        assertEquals(14f, state.damageTintShake, 0f, "`vanilla` is the default and is unchanged");
        state.setModSetting("damage_tint", "camera_shake", new JsonPrimitive("reduced"));
        assertEquals(3.5f, state.damageTintShake, 1e-6);
        state.setModSetting("damage_tint", "camera_shake", new JsonPrimitive("off"));
        assertEquals(0f, state.damageTintShake, 0f);

        // Switching the mod off puts vanilla's own number back, whatever camera_shake says.
        state.setGameplay("damage_tint", false);
        assertEquals(14f, state.damageTintShake, 0f);

        assertEquals(new JsonPrimitive(Long.valueOf(20)),
                state.setModSetting("damage_tint", "threshold", new JsonPrimitive(99)));
        assertEquals(20, state.damageTintThreshold);
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
    @DisplayName("the FOV changer mirrors a value and two independent locks")
    void fovFieldsAreMirrored() {
        // Absent from the example, so these are the registry's factory values — and the factory
        // values are the mod: 90 degrees, sprint locked, bow left alone.
        assertFalse(state.fovOn);
        assertEquals(90f, state.fovDegrees, 1e-6);
        assertTrue(state.fovLockSprint, "lock_sprint is the mod, so it ships on");
        assertFalse(state.fovLockBow, "the bow zoom is draw feedback, so it ships off");

        assertEquals(new JsonPrimitive(Long.valueOf(110)),
                state.setModSetting("fov", "fov", new JsonPrimitive(140)),
                "the range is vanilla's own slider, so 140 clamps to 110");
        assertEquals(110f, state.fovDegrees, 1e-6);
        assertEquals(new JsonPrimitive(Long.valueOf(30)),
                state.setModSetting("fov", "fov", new JsonPrimitive(0)));
        assertEquals(30f, state.fovDegrees, 1e-6);

        state.setModSetting("fov", "lock_bow", new JsonPrimitive(true));
        assertTrue(state.fovLockBow);
        assertTrue(state.fovLockSprint, "the two locks are independent");
    }

    @Test
    @DisplayName("view_bobbing resolves to the two flags the two injection points need")
    void overlayViewBobbingSplitsCameraFromHand() {
        // The enum exists because 1.8.9's own switch cannot express `minimal`: one flag,
        // `GameOptions.bobView`, guards both the camera bob and the held-item bob. It is
        // resolved into two booleans on the loadout write rather than compared per frame in
        // the render path — see the fields.
        assertFalse(state.overlayLockCameraBob, "vanilla is the default and touches nothing");
        assertFalse(state.overlayLockHandBob);

        assertEquals(new JsonPrimitive("minimal"),
                state.setModSetting("overlay", "view_bobbing", new JsonPrimitive("minimal")));
        assertTrue(state.overlayLockCameraBob, "minimal holds the camera still");
        assertFalse(state.overlayLockHandBob, "and leaves the hand moving — that is the point");

        assertEquals(new JsonPrimitive("off"),
                state.setModSetting("overlay", "view_bobbing", new JsonPrimitive("off")));
        assertTrue(state.overlayLockCameraBob, "off is the vanilla switch off: both still");
        assertTrue(state.overlayLockHandBob);

        state.setModSetting("overlay", "view_bobbing", new JsonPrimitive("vanilla"));
        assertFalse(state.overlayLockCameraBob, "and it goes all the way back");
        assertFalse(state.overlayLockHandBob);

        // A value outside the enum leaves the stored one alone rather than resolving to
        // something arbitrary: `!"vanilla".equals(x)` would read a typo as "hold the camera".
        assertEquals(new JsonPrimitive("vanilla"),
                state.setModSetting("overlay", "view_bobbing", new JsonPrimitive("minimum")));
        assertFalse(state.overlayLockCameraBob);
    }

    @Test
    @DisplayName("the overlay's four booleans are five independent suppressions")
    void overlaySuppressionsAreIndependent() {
        // The factory configuration is the argument for the mod: the two that decide fights are
        // on, the two that are preferences are at their vanilla value.
        assertFalse(state.overlayOn);
        assertTrue(state.overlayHideFire, "the switch the roster singles out");
        assertTrue(state.overlayHidePumpkin);
        assertTrue(state.overlayHideStuckArrows);
        assertFalse(state.overlayHideOwnArmor, "cosmetic, so it ships off");

        assertTrue(state.setGameplay("overlay", true));
        assertTrue(state.overlayOn);

        state.setModSetting("overlay", "hide_fire", new JsonPrimitive(false));
        assertFalse(state.overlayHideFire);
        assertTrue(state.overlayHidePumpkin, "one switch off is one switch off");
        assertTrue(state.overlayOn, "and the mod is still on");
    }

    @Test
    @DisplayName("toggle_sneak mirrors its own bind, and sneak_too is gone")
    void toggleSneakReplacesSneakToo() {
        assertFalse(state.toggleSneakOn);
        assertFalse(state.toggleSneakHold, "`toggle` is the default and the mod");
        assertEquals(dev.voidpvp.client.input.KeyNames.KEY_NONE, state.toggleSneakCode,
                "a latch on a key nobody chose is a player stuck crouched");

        assertTrue(state.setGameplay("toggle_sneak", true));
        assertEquals(new JsonPrimitive("V"),
                state.setModSetting("toggle_sneak", "keybind", new JsonPrimitive("v")));
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("V"), state.toggleSneakCode);
        assertTrue(state.toggleSneakOn);

        state.setModSetting("toggle_sneak", "mode", new JsonPrimitive("hold"));
        assertTrue(state.toggleSneakHold);

        // The two settings `toggle_sprint` shed are both refused rather than quietly stored,
        // which is the failure mode the schema's $comment was written for: a setting that still
        // exists on one side and not the other.
        //
        // `sneak_too` was replaced by this mod. `mode` was removed because `hold` was provably a
        // no-op — `KeyBinding.setKeyPressed` writes the same `pressed` field the sprint tests in
        // `tickMovement` read, so the latch is indistinguishable from a held key, and there was
        // nothing for `hold` to do but write nothing, which is what `on: false` does. This mod's
        // `mode` is unaffected and the difference is structural: `toggle_sneak.keybind` is the
        // key this mod *latches*, not a toggle-the-mod bind, so `hold` here means sneak on a key
        // that is not Shift.
        assertNull(state.setModSetting("toggle_sprint", "sneak_too", new JsonPrimitive(true)));
        assertNull(state.setModSetting("toggle_sprint", "mode", new JsonPrimitive("hold")));
    }

    @Test
    @DisplayName("the stopwatch's two keys are mirrored like every other keybind")
    void stopwatchKeysAreMirrored() {
        // Neither is bound in the example, so both start at NONE and the poll never samples
        // them — a timer nobody bound costs nothing (schema/mods/stopwatch.json).
        assertEquals(dev.voidpvp.client.input.KeyNames.KEY_NONE, state.stopwatchStartCode);
        assertEquals(dev.voidpvp.client.input.KeyNames.KEY_NONE, state.stopwatchResetCode);

        assertEquals(new JsonPrimitive("N"),
                state.setModSetting("stopwatch", "start_key", new JsonPrimitive("n")));
        assertEquals(new JsonPrimitive("M"),
                state.setModSetting("stopwatch", "reset_key", new JsonPrimitive("m")));
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("N"), state.stopwatchStartCode);
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("M"), state.stopwatchResetCode);

        // Two settings, two codes, two rows in the table: binding one must not move the other.
        assertEquals(new JsonPrimitive("P"),
                state.setModSetting("stopwatch", "start_key", new JsonPrimitive("P")));
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("M"), state.stopwatchResetCode,
                "rebinding start_key left reset_key alone");
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
    @DisplayName("switchLoadout applies immediately, because init carries whole loadouts")
    void switchLoadoutIsImmediate() {
        assertFalse(state.switchLoadout("not-in-the-library"));
        assertTrue(state.switchLoadout("sword-pvp"), "switching to the active one is a no-op");

        // `bedwars` arrived in init.loadouts in full, so there is nothing to wait for:
        // the switch is done before this line returns (§8.2). That is the whole reason
        // the protocol sends whole loadouts and has no `request_loadout` message.
        assertTrue(state.switchLoadout("bedwars"));
        assertEquals("bedwars", state.loadout().id());
        assertTrue(state.zoomOn);
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("V"), state.zoomKeyCode,
                "the new loadout's zoom key is live");

        assertFalse(sink.patches.isEmpty(), "the switch reported a state delta");
        assertEquals("bedwars", sink.patchLoadouts.get(sink.patchLoadouts.size() - 1),
                "the delta is tagged with the loadout it applies to");
        assertTrue(sink.lastPatch().containsKey("mods.zoom.key"));
    }

    @Test
    @DisplayName("the library is the whole library, in library order")
    void libraryIsWholeLoadouts() {
        assertEquals(2, state.library().size());
        assertEquals("sword-pvp", state.library().get(0).id());
        assertEquals("bedwars", state.library().get(1).id());
        // Whole loadouts: every entry can be applied on its own.
        assertTrue(state.library().get(1).isOn("keystrokes"));
        assertEquals(2, state.libraryJson().size());
        assertTrue(state.libraryJson().get(1).getAsJsonObject().has("mods"));

        // L wraps in library order.
        assertEquals("bedwars", state.nextLoadoutId());
        state.switchLoadout("bedwars");
        assertEquals("sword-pvp", state.nextLoadoutId());
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
    @DisplayName("watermark is a HUD mod, so it places and round-trips like one")
    void watermarkPlacesLikeAnyHudMod() {
        HudItem stored = state.setHud("watermark", "top-left", 21, 59, Double.valueOf(1));
        assertNotNull(stored, "setHud takes every HUD mod, and this is one");
        assertEquals("watermark", stored.id);
        assertEquals(20.0, stored.dx, 1e-9, "snapped to the 4px grid from init.settings");
        assertEquals(60.0, stored.dy, 1e-9);

        // …and it survives the trip out to the page and back in, which is all Java owes it:
        // nothing here draws the mark.
        Loadout round = Loadout.fromJson(state.loadout().toJson());
        assertEquals(stored, round.hudItem("watermark"));
    }

    @Test
    @DisplayName("the factory loadout places the watermark, so a launcher-less client draws it")
    void theFactoryLoadoutPlacesTheWatermark() {
        // This is the loadout a bare `runClient` runs on: no -Dvoid.port, so no init ever lands
        // and LiveState keeps the one it was constructed with. The page's HudEntry bails on
        // `!on || !item`, so an unplaced mod is invisible however its settings read — which makes
        // this the difference between the feature working and it looking broken.
        Loadout factory = Loadout.defaults("default", "Default");
        HudItem mark = factory.hudItem("watermark");
        assertNotNull(mark, "the factory layout must place the watermark or nothing draws it");
        assertEquals("top-left", mark.anchor);
        assertTrue(factory.isOn("watermark"), "and it ships on");

        // It sits under the rest of the top-left column rather than on top of it. The number is
        // the factory layout's, not loadout.json's example library's — see the watermark's
        // `default_placement` in schema/mods/watermark.json, and the block it is generated into
        // in ModRegistry.
        assertTrue(mark.dy > factory.hudItem("ping").dy,
                "the mark goes under ping, not over it");
        assertTrue(mark.dy > factory.hudItem("coordinates").dy,
                "and under coordinates, which is placed whether or not it is on");
    }

    @Test
    @DisplayName("setGlobal clamps, and returns what it stored")
    void setGlobalClamps() {
        // The same contract as setModSetting: clamp rather than refuse, and hand back the stored
        // value so a slider that was dragged to 7 snaps to 3 rather than lying about it.
        assertEquals(1.5, state.setGlobal("ui_scale", new JsonPrimitive(1.5)).getAsDouble(), 1e-9);
        assertEquals(1.5, state.uiScale, 1e-9, "the field the frame loop reads is written");
        assertEquals(3.0, state.setGlobal("ui_scale", new JsonPrimitive(7)).getAsDouble(), 1e-9);
        assertEquals(0.5, state.setGlobal("ui_scale", new JsonPrimitive(0.1)).getAsDouble(), 1e-9);
        assertEquals(0.5, state.uiScale, 1e-9);

        assertEquals(64, state.setGlobal("hud_editor_grid", new JsonPrimitive(999)).getAsInt());
        assertEquals(0, state.setGlobal("hud_editor_grid", new JsonPrimitive(-4)).getAsInt());

        // The record a later `settings` push serialises must move with the mirrored fields, or
        // a reloaded page would be told the scale the launcher sent rather than the live one.
        assertEquals(0.5, state.settings().uiScale, 1e-9);
        assertEquals(0, state.settings().hudEditorGrid);
    }

    @Test
    @DisplayName("setGlobal moves the menu key live, and refuses to unbind it")
    void setGlobalMovesTheMenuKey() {
        assertEquals(new JsonPrimitive("GRAVE"),
                state.setGlobal("menu_key", new JsonPrimitive("grave")),
                "a keybind is upper-cased, exactly as setModSetting does it");
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("GRAVE"), state.menuKeyCode,
                "pollHotkeys reads this field every frame, so the key has already moved");
        assertEquals("GRAVE", state.settings().menuKey);

        // NONE is a legal keybind everywhere else, and is refused here on purpose: isKeyDown
        // answers false for it forever, so storing it would leave the player with no way to open
        // the menu again and no way to change it back.
        assertNull(state.setGlobal("menu_key", new JsonPrimitive("NONE")));
        assertEquals(dev.voidpvp.client.input.KeyNames.codeOf("GRAVE"), state.menuKeyCode,
                "a refused write leaves the key where it was");

        // The cycle key may be unbound: that only turns the L-key cycle off, which is a thing to
        // want rather than a way to get stuck.
        assertEquals(new JsonPrimitive("NONE"),
                state.setGlobal("cycle_loadout_key", new JsonPrimitive("none")));
        assertEquals(dev.voidpvp.client.input.KeyNames.KEY_NONE, state.cycleLoadoutKeyCode);
    }

    @Test
    @DisplayName("setGlobal tells Rust, which is the only thing that can persist a global")
    void setGlobalReachesTheSink() {
        // Without this the write lives in LiveState and dies with the process: the Snap
        // toggle came back at the factory 4 on every launch for as long as `Sink` had no
        // globals channel at all.
        assertEquals(8, state.setGlobal("hud_editor_grid", new JsonPrimitive(8)).getAsInt());
        assertEquals(1, sink.globalPatches.size(), "one frame per accepted change");
        assertEquals(new JsonPrimitive(8),
                sink.globalPatches.get(0).get("hud_editor_grid"));
        assertEquals(1, sink.globalPatches.get(0).size(),
                "a delta: only the key that moved, or a global the mod cannot model is erased");

        // §6.5 is about the *page*, which already knows — Rust is a different audience and
        // is told about every one of these.
        state.setGlobal("menu_key", new JsonPrimitive("grave"));
        state.setGlobal("theme", new JsonPrimitive("void-light"));
        state.setGlobal("ui_scale", new JsonPrimitive(1.5));
        assertEquals(4, sink.globalPatches.size());
        assertEquals(new JsonPrimitive("GRAVE"), sink.globalPatches.get(1).get("menu_key"),
                "what was stored, not what was sent");
    }

    @Test
    @DisplayName("setGlobal stays quiet when nothing moved, or when it refused")
    void setGlobalDoesNotReportANonChange() {
        // A clamp that lands on the value already stored is not a change, and neither is a
        // refusal. Reporting either would write settings.json for nothing — and `resetHud`
        // writes hud_editor_grid twice per reset, so this is a live path, not a hypothetical.
        state.setGlobal("hud_editor_grid", new JsonPrimitive(8));
        sink.globalPatches.clear();

        assertEquals(8, state.setGlobal("hud_editor_grid", new JsonPrimitive(8)).getAsInt());
        assertEquals(64, state.setGlobal("hud_editor_grid", new JsonPrimitive(999)).getAsInt());
        assertEquals(64, state.setGlobal("hud_editor_grid", new JsonPrimitive(128)).getAsInt(),
                "clamped to the same 64 as the write before it");
        assertNull(state.setGlobal("menu_key", new JsonPrimitive("NOT_A_KEY")));
        assertNull(state.setGlobal("no_such_global", new JsonPrimitive(1)));

        assertEquals(1, sink.globalPatches.size(), "only the 8 -> 64 move is a change");
        assertEquals(new JsonPrimitive(64), sink.globalPatches.get(0).get("hud_editor_grid"));
    }

    @Test
    @DisplayName("setGlobal answers null for an unknown key or an unusable value")
    void setGlobalRefusesWhatItCannotStore() {
        // Null, not a throw and not a silent success — the page binds to the return, and null is
        // how it learns nothing moved.
        assertNull(state.setGlobal("no_such_global", new JsonPrimitive(1)));
        assertNull(state.setGlobal(null, new JsonPrimitive(1)));
        assertNull(state.setGlobal("ui_scale", new JsonPrimitive("big")), "a number, not a string");
        assertNull(state.setGlobal("ui_scale", com.google.gson.JsonNull.INSTANCE));
        assertNull(state.setGlobal("ui_scale", new JsonObject()), "an object is not a scalar");
        assertNull(state.setGlobal("ui_scale", new JsonPrimitive(Double.NaN)));
        assertNull(state.setGlobal("menu_key", new JsonPrimitive("NOT_A_KEY")));
        assertNull(state.setGlobal("theme", new JsonPrimitive("")), "minLength 1");

        // Java's own account of the globals is untouched by every one of those.
        assertEquals(1.0, state.uiScale, 1e-9);
        assertEquals("void-dark", state.settings().theme);

        // theme is the one global Java stores without reading: the page applies it, and gets it
        // back from this return and from the `settings` channel after a reload.
        assertEquals(new JsonPrimitive("void-light"),
                state.setGlobal("theme", new JsonPrimitive("void-light")));
        assertEquals("void-light", state.theme);
        assertEquals("void-light", state.settings().theme);
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

    @Test
    @DisplayName("block_hit resolves to the one boolean both render paths ask for")
    void oldAnimationsBlockHitMirrorsAsABoolean() {
        // `one_seven` is the mod and the default, and the mod itself ships off — so the factory
        // state is "the revert is configured, and nothing is drawn differently".
        assertFalse(state.oldAnimationsOn);
        assertTrue(state.oldAnimationsBlockHitOneSeven, "the default is the revert");
        assertFalse(state.oldAnimationsSwingDuringDelay,
                "an arm that swings on a click the game ate can be read as a hit that landed");

        assertTrue(state.setGameplay("old_animations", true));
        assertTrue(state.oldAnimationsOn);

        assertEquals(new JsonPrimitive("vanilla"),
                state.setModSetting("old_animations", "block_hit", new JsonPrimitive("vanilla")));
        assertFalse(state.oldAnimationsBlockHitOneSeven,
                "vanilla is what a player picks to compare, in both render paths at once");

        assertEquals(new JsonPrimitive("one_seven"),
                state.setModSetting("old_animations", "block_hit",
                        new JsonPrimitive("one_seven")));
        assertTrue(state.oldAnimationsBlockHitOneSeven);

        // A value outside the enum leaves the stored one alone. The mirror is written as an
        // equality against `one_seven` rather than as `!"vanilla".equals(x)` so that a value
        // this class did not expect can only ever read as "leave the frame alone".
        assertEquals(new JsonPrimitive("one_seven"),
                state.setModSetting("old_animations", "block_hit", new JsonPrimitive("1.7")));
        assertTrue(state.oldAnimationsBlockHitOneSeven);
    }

    @Test
    @DisplayName("swing_during_delay is independent of block_hit, and of old_input")
    void oldAnimationsSwingDuringDelayIsItsOwnSwitch() {
        state.setGameplay("old_animations", true);
        state.setModSetting("old_animations", "swing_during_delay", new JsonPrimitive(true));
        assertTrue(state.oldAnimationsSwingDuringDelay);
        assertTrue(state.oldAnimationsBlockHitOneSeven, "one switch on is one switch on");

        // The packetless animation and the switch that actually restores 1.7's click cadence are
        // in different mods on purpose: this one is `safe`, `old_input` is `grey`.
        assertFalse(state.oldInputOn);
        assertFalse(state.oldInputNoMissDelay);

        state.setGameplay("old_animations", false);
        assertFalse(state.oldAnimationsOn);
        assertTrue(state.oldAnimationsSwingDuringDelay,
                "the mod's `on` gates it; the setting keeps what the player chose");
    }

    @Test
    @DisplayName("old_input is three independent guards, all off at the factory")
    void oldInputIsThreeIndependentGuards() {
        assertFalse(state.oldInputOn, "a grey mod ships off");
        assertFalse(state.oldInputUseWhileDigging);
        assertFalse(state.oldInputDigWhileUsing);
        assertFalse(state.oldInputNoMissDelay);

        assertTrue(state.setGameplay("old_input", true));
        assertTrue(state.oldInputOn);
        assertFalse(state.oldInputUseWhileDigging,
                "turning the mod on changes nothing until a switch is chosen");

        state.setModSetting("old_input", "use_while_digging", new JsonPrimitive(true));
        assertTrue(state.oldInputUseWhileDigging);
        assertFalse(state.oldInputDigWhileUsing, "its mirror is a separate decision");
        assertFalse(state.oldInputNoMissDelay);

        state.setModSetting("old_input", "dig_while_using", new JsonPrimitive(true));
        state.setModSetting("old_input", "no_miss_delay", new JsonPrimitive(true));
        assertTrue(state.oldInputDigWhileUsing);
        assertTrue(state.oldInputNoMissDelay);
        assertFalse(state.oldAnimationsOn, "and none of it reached the animations mod");
    }
}
