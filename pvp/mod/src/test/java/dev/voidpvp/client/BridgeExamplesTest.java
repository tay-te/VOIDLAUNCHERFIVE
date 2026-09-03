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
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        @Override
        public void closeMenu() {
            closes++;
        }

        @Override
        public void beginKeybindCapture(String modId) {
            captured = modId;
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
        List<JsonObject> summaries = new ArrayList<JsonObject>();
        JsonArray arr = init.getAsJsonArray("loadouts");
        for (int i = 0; i < arr.size(); i++) {
            summaries.add(arr.get(i).getAsJsonObject());
        }
        LiveState state = new LiveState();
        state.applyInit(Loadout.fromJson(init.getAsJsonObject("loadout")), summaries,
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
        JsonObject payload = ticks.build(
                expected.get("fps").getAsInt(),
                expected.get("ping").getAsInt(),
                pos.get("x").getAsDouble(), pos.get("y").getAsDouble(),
                pos.get("z").getAsDouble(), (float) pos.get("yaw").getAsDouble(),
                armor, fx);
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
        assertEquals(1, host.closes, "closeMenu reached the host");
        assertTrue(bridge.errors().isEmpty(), "no dispatch threw: " + bridge.errors());
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

        // bridge.json's {c: openKeybindCapture, returns: "V"} is the value the
        // Promise resolves with, which Java delivers through the shim.
        JsonElement expected = callExample("openKeybindCapture", true).get("returns");
        assertEquals("window.void.__emitKeybind(" + expected + ")",
                VoidBridge.keybindScript(expected.getAsString()));
        assertEquals("window.void.__emitKeybind(null)", VoidBridge.keybindScript(null));
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
