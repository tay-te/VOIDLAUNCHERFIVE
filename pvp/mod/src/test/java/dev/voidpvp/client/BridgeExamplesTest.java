package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.bridge.BridgeHost;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.sensor.ArmorSlot;
import dev.voidpvp.client.sensor.KeyStateTracker;
import dev.voidpvp.client.sensor.PotionFx;
import dev.voidpvp.client.sensor.ServerWatcher;
import dev.voidpvp.client.sensor.TickCoalescer;
import dev.voidpvp.client.sensor.TickInput;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every example in {@code schema/bridge.json}, replayed against the real
 * bridge: events are produced by the sensors that own them and pushed through
 * {@link VoidBridge}, calls are dispatched and their answers compared with the
 * {@code {c, returns}} examples.
 */
class BridgeExamplesTest {

    private static final class Host implements BridgeHost {
        int closes;
        String captured;
        java.util.List<dev.voidpvp.client.render.EffectSurface> surfaces =
                java.util.Collections.emptyList();

        @Override
        public void closeMenu() {
            closes++;
        }

        @Override
        public void beginKeybindCapture(String modId) {
            captured = modId;
        }

        @Override
        public void setSurfaces(java.util.List<dev.voidpvp.client.render.EffectSurface> next) {
            surfaces = next;
        }

        /** Counted: `pushWholeState` asking the host to resend the once-only sensors. */
        int resendSensorsCalls;

        @Override
        public void resendSensors() {
            resendSensorsCalls++;
        }

        /** Not exercised here; the bridge only asks for it from pushWholeState. */
        @Override
        public com.google.gson.JsonObject sessionJson() {
            return null;
        }
    }

    private static JsonObject exampleWith(String key, String value) {
        JsonArray examples = Schemas.examples("bridge.json");
        for (int i = 0; i < examples.size(); i++) {
            JsonObject o = examples.get(i).getAsJsonObject();
            if (o.has(key) && value.equals(o.get(key).getAsString())) {
                return o;
            }
        }
        throw new AssertionError("bridge.json has no example with " + key + " = " + value);
    }

    private static JsonObject callExample(String name, boolean wantResult) {
        JsonArray examples = Schemas.examples("bridge.json");
        for (int i = 0; i < examples.size(); i++) {
            JsonObject o = examples.get(i).getAsJsonObject();
            if (o.has("c") && name.equals(o.get("c").getAsString())
                    && o.has(wantResult ? "returns" : "params")) {
                return o;
            }
        }
        throw new AssertionError("bridge.json has no " + (wantResult ? "result" : "call")
                + " example for " + name);
    }

    private static LiveState seededState() {
        JsonArray protocolExamples = Schemas.examples("protocol.json");
        JsonObject init = null;
        for (int i = 0; i < protocolExamples.size(); i++) {
            JsonObject o = protocolExamples.get(i).getAsJsonObject();
            if ("init".equals(o.get("t").getAsString())) {
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
                dev.voidpvp.client.state.GlobalSettings.fromJson(
                        init.getAsJsonObject("settings")));
        return state;
    }

    // -----------------------------------------------------------------
    // Java to JS
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the keys example is what the key sensor produces")
    void keysEvent() {
        JsonObject example = exampleWith("e", "keys");
        JsonObject expected = example.getAsJsonObject("payload");

        KeyStateTracker keys = new KeyStateTracker();
        keys.setBindings(17, 30, 31, 32, -100, -99, 57, 42);
        assertTrue(keys.update(17, true), "W down is a change");
        assertTrue(keys.update(-100, true), "LMB down is a change");
        assertTrue(keys.update(42, true), "shift down is a change");
        assertTrue(!keys.update(17, true), "the same state again is not a change");

        assertEquals(expected, keys.payload());
    }

    @Test
    @DisplayName("the tick example is what the tick sensor produces")
    void tickEvent() {
        JsonObject example = exampleWith("e", "tick");
        JsonObject expected = example.getAsJsonObject("payload");

        List<ArmorSlot> armor = new ArrayList<ArmorSlot>();
        JsonArray expectedArmor = expected.getAsJsonArray("armor");
        for (int i = 0; i < expectedArmor.size(); i++) {
            JsonObject slot = expectedArmor.get(i).getAsJsonObject();
            if (slot.get("item").isJsonNull()) {
                armor.add(ArmorSlot.empty(slot.get("slot").getAsString()));
            } else {
                armor.add(new ArmorSlot(
                        slot.get("slot").getAsString(),
                        slot.get("item").getAsString(),
                        slot.get("damage").getAsInt(),
                        slot.get("max_damage").getAsInt(),
                        slot.get("count").getAsInt(),
                        slot.get("enchanted").getAsBoolean()));
            }
        }
        List<PotionFx> fx = new ArrayList<PotionFx>();
        JsonArray expectedFx = expected.getAsJsonArray("fx");
        for (int i = 0; i < expectedFx.size(); i++) {
            JsonObject e = expectedFx.get(i).getAsJsonObject();
            fx.add(new PotionFx(
                    e.get("id").getAsInt(),
                    e.get("name").getAsString(),
                    e.get("amplifier").getAsInt(),
                    e.get("duration_ms").getAsInt(),
                    e.get("ambient").getAsBoolean()));
        }
        JsonObject pos = expected.getAsJsonObject("pos");

        TickCoalescer ticks = new TickCoalescer();
        TickInput in = new TickInput();
        in.fps = expected.get("fps").getAsInt();
        in.ping = expected.get("ping").getAsInt();
        in.x = pos.get("x").getAsDouble();
        in.y = pos.get("y").getAsDouble();
        in.z = pos.get("z").getAsDouble();
        in.yaw = (float) pos.get("yaw").getAsDouble();
        in.armor = armor;
        in.fx = fx;
        JsonObject payload = ticks.build(in);
        Schemas.assertContains(expected, payload, "tick payload");
        assertEquals(expected.entrySet().size(), payload.entrySet().size(),
                "the first tick carries everything");
    }

    @Test
    @DisplayName("the server example is what the server sensor produces")
    void serverEvent() {
        JsonObject example = exampleWith("e", "server");
        JsonObject expected = example.getAsJsonObject("payload");

        ServerWatcher watcher = new ServerWatcher();
        assertTrue(watcher.update(true, expected.get("host").getAsString(), 25565));
        assertEquals(expected, watcher.payload());
        assertTrue(!watcher.update(true, expected.get("host").getAsString(), 25565),
                "the same connection again is not an event");
    }

    @Test
    @DisplayName("the loadout example survives the round trip into the loadout event")
    void loadoutEvent() {
        JsonObject example = exampleWith("e", "loadout");
        Loadout loadout = Loadout.fromJson(example.getAsJsonObject("payload"));
        Schemas.assertContains(example.getAsJsonObject("payload"), loadout.toJson(),
                "loadout event payload");
    }

    @Test
    @DisplayName("a frame's events are pushed as one __emit call, in order")
    void eventsAreBatchedIntoOneCall() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        assertNull(bridge.drainScript(), "an empty frame pushes nothing");

        JsonArray examples = Schemas.examples("bridge.json");
        int events = 0;
        for (int i = 0; i < examples.size(); i++) {
            JsonObject example = examples.get(i).getAsJsonObject();
            if (!example.has("e")) {
                continue;
            }
            bridge.emit(example.get("e").getAsString(), example.get("payload"));
            events++;
        }
        assertTrue(events >= 5, "bridge.json lost event examples: " + events);

        String script = bridge.drainScript();
        assertNotNull(script);
        assertTrue(script.startsWith("window.void.__emit(["), script);
        assertTrue(script.endsWith("])"), script);

        JsonArray batch = Json.parse(script.substring("window.void.__emit(".length(),
                script.length() - 1)).getAsJsonArray();
        assertEquals(events, batch.size(), "every event of the frame is in the one call");
        for (int i = 0; i < batch.size(); i++) {
            JsonObject envelope = batch.get(i).getAsJsonObject();
            assertTrue(envelope.has("e") && envelope.has("payload"),
                    "an envelope is {e, payload}: " + envelope);
        }
        assertNull(bridge.drainScript(), "draining twice pushes nothing");
    }

    @Test
    @DisplayName("whole-state channels coalesce so a slow frame pushes one tick")
    void wholeStateChannelsCoalesce() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        JsonObject first = new JsonObject();
        first.addProperty("fps", Integer.valueOf(60));
        JsonObject second = new JsonObject();
        second.addProperty("fps", Integer.valueOf(61));
        bridge.emit(VoidBridge.EVENT_TICK, first);
        bridge.emit(VoidBridge.EVENT_TICK, second);

        String script = bridge.drainScript();
        JsonArray batch = Json.parse(script.substring("window.void.__emit(".length(),
                script.length() - 1)).getAsJsonArray();
        assertEquals(1, batch.size(), "only the newest tick survives");
        assertEquals(second, batch.get(0).getAsJsonObject().getAsJsonObject("payload"));
    }

    // -----------------------------------------------------------------
    // JS to Java
    // -----------------------------------------------------------------

    @Test
    @DisplayName("every call example returns the result example says it does")
    void callsReturnTheDocumentedResults() {
        LiveState state = seededState();
        Host host = new Host();
        VoidBridge bridge = new VoidBridge(state, host);

        for (String name : new String[] {"setGameplay", "setHud", "setModSetting",
                "switchLoadout", "closeMenu"}) {
            JsonObject call = callExample(name, false);
            JsonObject expected = callExample(name, true);
            JsonObject answer = Json.parseObject(bridge.dispatch(call.toString()));
            assertEquals(name, answer.get("c").getAsString(), name + " echoes its call name");
            Schemas.assertContains(expected.get("returns"), answer.get("returns"),
                    name + " returns");
        }
        // closeMenu touches screens and GL, so it is queued rather than run inside the call —
        // see the classification block in VoidBridge. The answer is still the documented one.
        assertEquals(0, host.closes, "closeMenu does not touch the game inside the call");
        assertEquals(1, bridge.runGameThreadWork(), "it ran at the drain instead");
        assertEquals(1, host.closes, "closeMenu reached the host");
        assertTrue(bridge.errors().isEmpty(), "no dispatch threw: " + bridge.errors());
    }

    @Test
    @DisplayName("switchLoadout pushes the loadout event bridge.json promises")
    void switchLoadoutPushesTheLoadoutEvent() {
        LiveState state = seededState();
        VoidBridge bridge = new VoidBridge(state, new Host());
        String before = state.loadoutId();
        String target = state.nextLoadoutId();
        assertNotNull(target, "the seeded library has more than one loadout");
        assertTrue(!target.equals(before), "the cycle target is a different loadout");

        JsonObject answer = Json.parseObject(bridge.dispatch(
                "{\"c\":\"switchLoadout\",\"params\":[\"" + target + "\"]}"));
        assertTrue(answer.get("returns").getAsBoolean(), "the switch was applied");

        // bridge.json's `call_switchLoadout`: "A `loadout` event follows with the new
        // loadout, so the caller does not need the returned object" — and LoadoutsScreen
        // holds no optimistic state, so without this the page keeps rendering the loadout
        // the player just left while Java runs the new one.
        JsonObject batch = pushedEnvelope(bridge, VoidBridge.EVENT_LOADOUT);
        assertNotNull(batch, "a `loadout` event must follow the switch");
        assertEquals(target, batch.getAsJsonObject("payload").get("id").getAsString());

        // And a switch that names an id the library does not have changes nothing and
        // pushes nothing, so the page is not told about a switch that did not happen.
        VoidBridge second = new VoidBridge(state, new Host());
        JsonObject refused = Json.parseObject(second.dispatch(
                "{\"c\":\"switchLoadout\",\"params\":[\"not-a-loadout\"]}"));
        assertTrue(!refused.get("returns").getAsBoolean(), "an unknown id is refused");
        assertNull(pushedEnvelope(second, VoidBridge.EVENT_LOADOUT), "and pushes nothing");
    }

    /** The first envelope on {@code event} in the bridge's pending batch, or null. */
    private static JsonObject pushedEnvelope(VoidBridge bridge, String event) {
        String script = bridge.drainScript();
        if (script == null) {
            return null;
        }
        String json = script.substring("window.void.__emit(".length(), script.length() - 1);
        JsonArray batch = new com.google.gson.JsonParser().parse(json).getAsJsonArray();
        for (int i = 0; i < batch.size(); i++) {
            JsonObject envelope = batch.get(i).getAsJsonObject();
            if (envelope.has("e") && event.equals(envelope.get("e").getAsString())) {
                return envelope;
            }
        }
        return null;
    }

    @Test
    @DisplayName("setSurfaces hands the host the geometry the GL shadow pass draws")
    void surfacesReachTheHost() {
        LiveState state = seededState();
        Host host = new Host();
        VoidBridge bridge = new VoidBridge(state, host);

        JsonObject call = callExample("setSurfaces", false);
        JsonObject answer = Json.parseObject(bridge.dispatch(call.toString()));
        assertEquals("setSurfaces", answer.get("c").getAsString());
        assertEquals(1, answer.get("returns").getAsInt(), "one surface accepted");

        assertEquals(1, host.surfaces.size());
        dev.voidpvp.client.render.EffectSurface panel = host.surfaces.get(0);
        assertEquals("panel", panel.id);
        assertEquals(1184f, panel.width, 0.001f);
        // The authored --shadow-panel-gl, carried through from design/tokens.css untouched. This
        // is the whole point of the channel: the overlay's CSS drops the shadow because a blur
        // costs the CPU rasteriser more than everything else combined, and the host draws the
        // Figma value instead of an approximation of it.
        assertEquals(70f, panel.blur, 0.001f);
        assertEquals(-20f, panel.spread, 0.001f);
        assertEquals(0.6f, panel.alpha, 0.001f);
        assertTrue(panel.visible(), "a sized, non-transparent surface is worth drawing");
    }

    @Test
    @DisplayName("a malformed surface is skipped, not thrown")
    void malformedSurfacesAreSkipped() {
        LiveState state = seededState();
        Host host = new Host();
        VoidBridge bridge = new VoidBridge(state, host);

        // This arrives whenever the page's layout moves, so a bad entry has to cost a missing
        // shadow and never a broken frame.
        String call = "{\"c\":\"setSurfaces\",\"params\":[["
                + "{\"id\":\"no-shadow\",\"x\":0,\"y\":0,\"w\":10,\"h\":10,\"radius\":0},"
                + "\"not-an-object\","
                + "{\"id\":\"ok\",\"x\":1,\"y\":2,\"w\":3,\"h\":4,\"radius\":5,"
                + "\"shadow\":{\"dx\":0,\"dy\":1,\"blur\":2,\"spread\":0,"
                + "\"color\":[0,0,0,0.5]}}]]}";
        JsonObject answer = Json.parseObject(bridge.dispatch(call));
        assertEquals(1, answer.get("returns").getAsInt(), "only the well-formed one survives");
        assertEquals("ok", host.surfaces.get(0).id);
    }

    @Test
    @DisplayName("openKeybindCapture answers immediately and resolves later")
    void keybindCaptureIsTheOneAsyncCall() {
        LiveState state = seededState();
        Host host = new Host();
        VoidBridge bridge = new VoidBridge(state, host);

        JsonObject call = callExample("openKeybindCapture", false);
        JsonObject answer = Json.parseObject(bridge.dispatch(call.toString()));
        assertEquals("openKeybindCapture", answer.get("c").getAsString());
        assertTrue(answer.get("returns").isJsonNull(),
                "the synchronous answer only arms the capture; the key arrives later");
        assertEquals("zoom", host.captured, "the host was told which mod is being bound");

        // The captured key comes back as a call-result envelope on the __emit channel,
        // which is what bridge.json specifies and what the shim listens for. Null
        // arrives on both channels, so the shim tells them apart by channel, never by
        // value — hence the deferred one is an envelope and not a bare value.
        JsonElement expected = null;
        JsonArray examples = Schemas.examples("bridge.json");
        for (int i = 0; i < examples.size(); i++) {
            JsonObject o = examples.get(i).getAsJsonObject();
            if (o.has("c") && "openKeybindCapture".equals(o.get("c").getAsString())
                    && o.has("returns") && !o.get("returns").isJsonNull()) {
                expected = o.get("returns");
            }
        }
        assertNotNull(expected, "bridge.json lost its captured-key example");
        assertEquals("window.void.__emit({\"c\":\"openKeybindCapture\",\"returns\":"
                        + expected + "})",
                VoidBridge.keybindScript(expected.getAsString()));
        assertEquals("window.void.__emit({\"c\":\"openKeybindCapture\",\"returns\":null})",
                VoidBridge.keybindScript(null));
    }

    @Test
    @DisplayName("the setting event carries what Java stored, not what was asked for")
    void settingEventCarriesTheStoredValue() {
        LiveState state = seededState();
        VoidBridge bridge = new VoidBridge(state, new Host());

        // 9 is far outside opacity's 0..1 range: the registry clamps, and the event
        // carries the clamped value, exactly as setModSetting's return would.
        JsonElement stored = state.setModSetting("keystrokes", "opacity",
                new com.google.gson.JsonPrimitive(Integer.valueOf(9)));
        bridge.emitSetting("keystrokes", "opacity", stored);

        String script = bridge.drainScript();
        JsonArray batch = Json.parse(script.substring("window.void.__emit(".length(),
                script.length() - 1)).getAsJsonArray();
        JsonObject envelope = batch.get(0).getAsJsonObject();
        assertEquals("setting", envelope.get("e").getAsString());
        JsonObject payload = envelope.getAsJsonObject("payload");
        assertEquals("keystrokes", payload.get("id").getAsString());
        assertEquals("opacity", payload.get("key").getAsString());
        assertEquals(1.0, payload.get("value").getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("the mod knows every event name and every call name bridge.json declares")
    void theSurfaceIsCovered() {
        JsonObject defs = Schemas.load("bridge.json").getAsJsonObject("definitions");

        // Every channel bridge.json declares must be one the mod has a constant for. The
        // assertion runs this way round — schema is a subset of the mod — rather than as list
        // equality, because the mod deliberately carries channels the schema does not:
        // `session` (whose payload comes from the game, not the launcher) and `settings`. A
        // straight equality here would have to be edited every time one of those is added, which
        // makes it a chore rather than a guard.
        java.util.Set<String> ours = new java.util.LinkedHashSet<String>(java.util.Arrays.asList(
                VoidBridge.EVENT_KEYS, VoidBridge.EVENT_TICK, VoidBridge.EVENT_SERVER,
                VoidBridge.EVENT_LOADOUT, VoidBridge.EVENT_LOADOUTS, VoidBridge.EVENT_SETTING,
                VoidBridge.EVENT_MENU, VoidBridge.EVENT_SESSION, VoidBridge.EVENT_SETTINGS));
        java.util.List<String> events = new ArrayList<String>();
        for (JsonElement e : defs.getAsJsonObject("event_name").getAsJsonArray("enum")) {
            events.add(e.getAsString());
        }
        for (String name : events) {
            assertTrue(ours.contains(name),
                    "bridge.json declares the channel `" + name + "` and the mod has no constant "
                            + "for it — see the note on EVENTS in void-shim.js for what that "
                            + "costs: a channel nothing listens on fails silently");
        }
        // And the seven the schema owns are all still there, so nothing can be dropped from
        // bridge.json and quietly go unnoticed here.
        assertTrue(events.containsAll(java.util.Arrays.asList(
                VoidBridge.EVENT_KEYS, VoidBridge.EVENT_TICK, VoidBridge.EVENT_SERVER,
                VoidBridge.EVENT_LOADOUT, VoidBridge.EVENT_LOADOUTS, VoidBridge.EVENT_SETTING,
                VoidBridge.EVENT_MENU)), "bridge.json lost a channel: " + events);

        // Every declared call must be dispatchable: an unknown call answers `null`, so a
        // call the mod forgot would silently do nothing in game.
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        for (JsonElement e : defs.getAsJsonObject("call_name").getAsJsonArray("enum")) {
            String name = e.getAsString();
            JsonObject answer = Json.parseObject(
                    bridge.dispatch(callExample(name, false).toString()));
            assertEquals(name, answer.get("c").getAsString(), name + " is dispatched");
        }
        assertTrue(bridge.errors().isEmpty(), "no dispatch threw: " + bridge.errors());
    }

    @Test
    @DisplayName("setGlobal answers with the stored value, and null for what it will not store")
    void setGlobalIsDispatched() {
        LiveState state = seededState();
        VoidBridge bridge = new VoidBridge(state, new Host());

        // The page's only way to change a global. Before this call existed LiveState held
        // menuKeyCode, uiScale and theme, Rust could push them down, and the page could neither
        // read nor write any of them.
        assertEquals("{\"c\":\"setGlobal\",\"returns\":2}",
                bridge.dispatch("{\"c\":\"setGlobal\",\"params\":[\"ui_scale\",2]}"));
        assertEquals(2.0, state.uiScale, 1e-9, "and the field VoidClient.pumpUi reads has moved");

        // Clamped, and the answer is the clamp's — the page binds to this, not to what it sent.
        assertEquals("{\"c\":\"setGlobal\",\"returns\":3}",
                bridge.dispatch("{\"c\":\"setGlobal\",\"params\":[\"ui_scale\",9]}"));
        assertEquals("{\"c\":\"setGlobal\",\"returns\":\"GRAVE\"}",
                bridge.dispatch("{\"c\":\"setGlobal\",\"params\":[\"menu_key\",\"grave\"]}"));

        // Null on the same terms setModSetting uses: an unknown key, an unusable value, or a
        // call that arrived without enough arguments to mean anything.
        assertEquals("{\"c\":\"setGlobal\",\"returns\":null}",
                bridge.dispatch("{\"c\":\"setGlobal\",\"params\":[\"java_path\",\"/usr/bin\"]}"));
        assertEquals("{\"c\":\"setGlobal\",\"returns\":null}",
                bridge.dispatch("{\"c\":\"setGlobal\",\"params\":[\"ui_scale\",\"huge\"]}"));
        assertEquals("{\"c\":\"setGlobal\",\"returns\":null}",
                bridge.dispatch("{\"c\":\"setGlobal\",\"params\":[\"ui_scale\"]}"));

        // §6.5: no echo. The call answered, so pushing the same value back would only fight the
        // control the player is holding — and the game thread was never involved either.
        assertNull(bridge.drainScript(), "setGlobal pushes nothing back at the page");
        assertFalse(bridge.hasGameThreadWork(), "and queues nothing for the game thread");
        assertTrue(bridge.errors().isEmpty(), "no dispatch threw: " + bridge.errors());
    }

    @Test
    @DisplayName("a malformed or unknown call never throws into the frame")
    void malformedCallsAreSurvivable() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        assertEquals("{\"c\":\"\",\"returns\":null}", bridge.dispatch("]not json["));
        assertEquals("{\"c\":\"\",\"returns\":null}", bridge.dispatch("{}"));
        assertEquals("{\"c\":\"noSuchCall\",\"returns\":null}",
                bridge.dispatch("{\"c\":\"noSuchCall\",\"params\":[1,2]}"));
        assertEquals("{\"c\":\"setGameplay\",\"returns\":false}",
                bridge.dispatch("{\"c\":\"setGameplay\",\"params\":[]}"));
    }

    @Test
    @DisplayName("setGameplay refuses a HUD mod")
    void setGameplayOnlyTakesGameplayMods() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        assertEquals("{\"c\":\"setGameplay\",\"returns\":false}",
                bridge.dispatch("{\"c\":\"setGameplay\",\"params\":[\"keystrokes\",true]}"));
    }
}
