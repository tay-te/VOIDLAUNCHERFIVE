package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.bridge.BridgeHost;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.render.EffectSurface;
import dev.voidpvp.client.state.GlobalSettings;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import dev.voidpvp.client.ui.NullWebView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a freshly loaded page has to be told, and that there is one definition of it.
 *
 * <p>The mid-session GPU fallback rebuilds the view, and a replacement view is a new view: the
 * page reloads and every envelope already delivered went to a document that no longer exists. The
 * menu the player had open stopped being drawn, the mod went on believing it was open, and the
 * only way out was a key combination nobody would find. These tests pin the contract that fixes
 * it — {@link VoidBridge#pushWholeState()} — and the property that makes it correct, which is
 * that it re-reads {@link LiveState} rather than replaying what was sent before.
 */
class PageReloadTest {

    private static final class Host implements BridgeHost {
        @Override
        public void closeMenu() {
        }

        @Override
        public void beginKeybindCapture(String modId) {
        }

        @Override
        public void setSurfaces(List<EffectSurface> surfaces) {
        }
    }

    /** The launcher's own {@code init} example, so the state under test is the shipped shape. */
    private static LiveState seededState() {
        JsonObject protocol = Schemas.load("protocol.json");
        JsonArray examples = protocol.getAsJsonArray("examples");
        JsonObject init = null;
        for (int i = 0; i < examples.size(); i++) {
            JsonObject o = examples.get(i).getAsJsonObject();
            if ("init".equals(Json.string(o, "t", ""))) {
                init = o;
            }
        }
        assertNotNull(init, "protocol.json lost its init example");
        List<Loadout> library = new ArrayList<Loadout>();
        JsonArray arr = init.getAsJsonArray("loadouts");
        for (int i = 0; i < arr.size(); i++) {
            library.add(Loadout.fromJson(arr.get(i).getAsJsonObject()));
        }
        LiveState state = new LiveState();
        state.applyInit(Loadout.fromJson(init.getAsJsonObject("loadout")), library,
                GlobalSettings.fromJson(init.getAsJsonObject("settings")));
        return state;
    }

    /** The envelopes of one drain, in the order the page will see them. */
    private static List<JsonObject> drain(VoidBridge bridge) {
        String script = bridge.drainScript();
        assertNotNull(script, "nothing was queued");
        assertTrue(script.startsWith("window.void.__emit("), script);
        String json = script.substring("window.void.__emit(".length(), script.length() - 1);
        JsonArray batch = new com.google.gson.JsonParser().parse(json).getAsJsonArray();
        List<JsonObject> out = new ArrayList<JsonObject>();
        for (int i = 0; i < batch.size(); i++) {
            out.add(batch.get(i).getAsJsonObject());
        }
        return out;
    }

    private static JsonElement payload(List<JsonObject> batch, String event) {
        for (JsonObject envelope : batch) {
            if (event.equals(Json.string(envelope, "e", null))) {
                return envelope.get("payload");
            }
        }
        return null;
    }

    @Test
    @DisplayName("a reloaded page is sent the library, the active loadout and the menu state")
    void wholeStateCoversEveryChannelThatCannotResendItself() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());

        bridge.pushWholeState();
        List<JsonObject> batch = drain(bridge);

        // The library first, then the live copy of the active loadout — the order onInit uses, so
        // the page's store settles on the loadout and not on the library's stale copy of it.
        assertEquals(3, batch.size(), "one envelope per channel that cannot resend itself");
        assertEquals(VoidBridge.EVENT_LOADOUTS, Json.string(batch.get(0), "e", null));
        assertEquals(VoidBridge.EVENT_LOADOUT, Json.string(batch.get(1), "e", null));
        assertEquals(VoidBridge.EVENT_MENU, Json.string(batch.get(2), "e", null));

        // tick, keys and server are deliberately absent: their sensors push whole state every
        // tick, so the queue refills with current values long before a reloading page is ready to
        // receive anything. Sending a remembered one would be adding a second source of truth for
        // no gain.
        assertEquals(null, payload(batch, VoidBridge.EVENT_TICK));
        assertEquals(null, payload(batch, VoidBridge.EVENT_KEYS));
        assertEquals(null, payload(batch, VoidBridge.EVENT_SERVER));
    }

    @Test
    @DisplayName("the loadout it sends is read live, not replayed from what was sent before")
    void theLoadoutIsReReadRatherThanRemembered() {
        LiveState state = seededState();
        VoidBridge bridge = new VoidBridge(state, new Host());

        bridge.pushWholeState();
        JsonElement before = payload(drain(bridge), VoidBridge.EVENT_LOADOUT);
        assertNotNull(before);

        // A change the page made itself. §6.5 deliberately does not push these back — the page
        // already knows — so nothing is emitted on the loadout channel and a remembered envelope
        // would still be the one from init. Every toggle the player had flipped this session would
        // be missing from the page it came back to.
        assertTrue(state.setGameplay("fullbright", true), "fullbright is a gameplay mod");
        state.setModSetting("zoom", "fov", new com.google.gson.JsonPrimitive(Integer.valueOf(15)));

        bridge.pushWholeState();
        JsonElement after = payload(drain(bridge), VoidBridge.EVENT_LOADOUT);
        assertNotNull(after);
        assertNotEquals(before, after,
                "pushWholeState replayed a stale snapshot instead of re-reading LiveState");
        assertEquals(state.loadoutJson(), after);
    }

    @Test
    @DisplayName("the menu state survives, because nothing else in the mod can answer for it")
    void theMenuStateIsRememberedBecauseItHasNoOtherHome() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());

        bridge.emitMenu(true);
        drain(bridge);

        // The reload happens here. LiveState knows the loadout; nothing but the bridge knows the
        // player had the menu open, and a page told `menu: false` would leave them looking at the
        // world with the mouse ungrabbed — which is the failure this whole path exists to prevent.
        bridge.pushWholeState();
        assertEquals(Boolean.TRUE,
                Boolean.valueOf(payload(drain(bridge), VoidBridge.EVENT_MENU).getAsBoolean()));

        bridge.emitMenu(false);
        drain(bridge);
        bridge.pushWholeState();
        assertEquals(Boolean.FALSE,
                Boolean.valueOf(payload(drain(bridge), VoidBridge.EVENT_MENU).getAsBoolean()));
    }

    @Test
    @DisplayName("a view that never loads a document never reports one")
    void nullViewHasNoDocument() {
        // The host compares this against the generation it loaded itself; a NullWebView is never
        // asked to load anything, so it must never look like it reloaded.
        assertEquals(0, new NullWebView().documentGeneration());
        assertEquals(0, new NullWebView().documentGeneration());
    }

    @Test
    @DisplayName("a view that paints nothing asks for no supersampling")
    void nullViewIsNotSupersampled() {
        // The cap is a property of the renderer, and this one has none. 1 is the only honest
        // answer, and the host never asks anyway — it returns early on an unavailable view.
        assertEquals(1, new NullWebView().maxSupersample());
    }

    @Test
    @DisplayName("nothing outside a view can be asked which renderer is in use")
    void thereIsNoStaticAnswerToStealTheCapFrom() throws Exception {
        // The regression this pins is structural rather than behavioural: the supersample cap has
        // broken three times, each time because some object other than the view held the answer.
        // WebViews.acceleratedInUse() was the third. If it comes back, so does the bug.
        Class<?> webViews = Class.forName("dev.voidpvp.client.ui.WebViews");
        for (java.lang.reflect.Method m : webViews.getDeclaredMethods()) {
            assertNotEquals("acceleratedInUse", m.getName(),
                    "the renderer in use must only ever be asked of the view that is running it");
        }
        for (java.lang.reflect.Field f : webViews.getDeclaredFields()) {
            assertNotEquals("acceleratedInUse", f.getName(),
                    "a cached copy of which renderer is running is what goes stale");
        }
    }
}
