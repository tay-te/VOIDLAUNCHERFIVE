package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.bridge.BridgeHost;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.cosmetic.CosmeticState;
import dev.voidpvp.client.cosmetic.Emotes;
import dev.voidpvp.client.cosmetic.EquippedSet;
import dev.voidpvp.client.net.OutboundQueue;
import dev.voidpvp.client.net.Protocol;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cosmetics half of both seams: the {@code cosmetics} / {@code emote} protocol messages and
 * the {@code cosmetics} / {@code emote} channels plus the four wardrobe calls on the bridge.
 *
 * <p>{@link CosmeticState} and {@link Emotes} are process singletons, so every test starts from
 * the same known state ({@link #reset()}) rather than from whatever the previous one left.</p>
 */
class CosmeticsLinkTest {

    private static final String SELF = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String OTHER = "853c80ef-3c37-49fd-aa49-938b674adae6";

    /** Records what CosmeticState tells the launcher. */
    private static final class Sink implements CosmeticState.Sink {
        final List<String> sent = new ArrayList<String>();

        @Override
        public void query(List<String> uuids) {
            sent.add("query " + uuids);
        }

        @Override
        public void emote(String id) {
            sent.add("emote " + id);
        }

        @Override
        public void equip(String slot, String id, String tint) {
            sent.add("equip " + slot + " " + id + " " + tint);
        }

        @Override
        public void emotes(List<String> ids) {
            sent.add("emotes " + ids);
        }

        @Override
        public boolean openStore(String id) {
            sent.add("store " + id);
            return true;
        }
    }

    private static final class Host implements BridgeHost {
        @Override
        public void closeMenu() {
        }

        @Override
        public JsonObject sessionJson() {
            return null;
        }

        @Override
        public void beginKeybindCapture(String modId) {
        }

        @Override
        public void setSurfaces(List<dev.voidpvp.client.render.EffectSurface> surfaces) {
        }

        @Override
        public void resendSensors() {
        }
    }

    private Sink sink;

    @BeforeEach
    void reset() {
        sink = new Sink();
        CosmeticState cs = CosmeticState.get();
        cs.setSink(null);
        cs.setOwnsEverything(false);
        cs.clearPlayers();
        cs.applyLauncher(EquippedSet.empty(SELF), null, Collections.<String>emptyList());
        cs.setSelfUuid(SELF);
        Emotes.get().clear();
        Emotes.get().setSelfUuid(SELF);
        cs.setSink(sink);
    }

    @AfterEach
    void detach() {
        CosmeticState.get().setSink(null);
        Emotes.get().clear();
    }

    private static VoidBridge bridge() {
        VoidBridge bridge = new VoidBridge(new LiveState(), new Host());
        bridge.drainScript();
        return bridge;
    }

    private static JsonArray drain(VoidBridge bridge) {
        String script = bridge.drainScript();
        if (script == null) {
            return new JsonArray();
        }
        return Json.parse(script.substring("window.void.__emit(".length(), script.length() - 1))
                .getAsJsonArray();
    }

    private static List<JsonObject> on(JsonArray batch, String event) {
        List<JsonObject> out = new ArrayList<JsonObject>();
        for (JsonElement e : batch) {
            JsonObject env = e.getAsJsonObject();
            if (env.has("e") && event.equals(env.get("e").getAsString())) {
                out.add(env.getAsJsonObject("payload"));
            }
        }
        return out;
    }

    private static String call(VoidBridge bridge, String json) {
        return bridge.dispatch(json);
    }

    // -----------------------------------------------------------------
    // Protocol
    // -----------------------------------------------------------------

    @Test
    @DisplayName("an id the catalog does not know, or one in the wrong slot, reads as nothing worn")
    void equippedSetRejectsUnknownAndMisplacedIds() {
        Protocol.Inbound in = Protocol.parse("{\"t\":\"cosmetics\",\"players\":[{\"uuid\":\""
                + OTHER.toUpperCase() + "\",\"cape\":\"cape-from-the-future\",\"halo\":\"void-cape\","
                + "\"head\":\"dapper-top-hat\",\"emotes\":[\"emote-wave\",\"spark-trail\",\"emote-wave\"],"
                + "\"tints\":{\"head\":\"#ABCDEF\",\"wings\":\"#000000\",\"cape\":\"red\"},"
                + "\"slot_from_the_future\":\"x\"}]}");
        assertEquals(Protocol.Inbound.Kind.COSMETICS, in.kind);
        assertNull(in.self, "self absent means unchanged");
        EquippedSet s = in.players.get(0);
        assertEquals(OTHER, s.uuid, "uuids are normalised to dashed lower case");
        assertNull(s.get("cape"), "an unknown id is not worn");
        assertNull(s.get("halo"), "a cape in the halo slot is not worn");
        assertEquals("dapper-top-hat", s.get("head"));
        assertEquals(Arrays.asList("emote-wave"), s.emotes(), "non-emotes and repeats are dropped");
        assertEquals(0xABCDEF, s.tint("head"));
        assertEquals(-1, s.tint("cape"), "a malformed tint is dropped");
    }

    @Test
    @DisplayName("malformed cosmetics and emote frames are ignored, never thrown")
    void malformedFramesAreUnknown() {
        assertEquals(Protocol.Inbound.Kind.UNKNOWN, Protocol.parse("{\"t\":\"cosmetics\"}").kind,
                "nothing to apply");
        assertEquals(Protocol.Inbound.Kind.UNKNOWN,
                Protocol.parse("{\"t\":\"cosmetics\",\"self\":42,\"players\":\"no\"}").kind);
        assertEquals(Protocol.Inbound.Kind.UNKNOWN,
                Protocol.parse("{\"t\":\"emote\",\"id\":\"emote-wave\"}").kind,
                "the outbound shape is not an inbound emote");
        assertEquals(Protocol.Inbound.Kind.UNKNOWN,
                Protocol.parse("{\"t\":\"emote\",\"uuid\":{},\"id\":\"emote-wave\"}").kind);
        Protocol.Inbound noClock = Protocol.parse(
                "{\"t\":\"emote\",\"uuid\":\"" + OTHER + "\",\"id\":\"emote-wave\",\"at\":\"soon\"}");
        assertEquals(Protocol.Inbound.Kind.EMOTE, noClock.kind);
        assertEquals(0L, noClock.atMs, "an unreadable clock is 0, which means start now");
        // A self of null is the documented "no online session", and players still apply.
        Protocol.Inbound nullSelf = Protocol.parse("{\"t\":\"cosmetics\",\"self\":null,\"players\":[]}");
        assertEquals(Protocol.Inbound.Kind.COSMETICS, nullSelf.kind);
        assertNull(nullSelf.self);
        assertTrue(nullSelf.players.isEmpty());
    }

    @Test
    @DisplayName("a query is chunked to the schema's 200 uuids and sent lower case, once each")
    void queryBuilder() {
        JsonObject q = Protocol.cosmeticsQuery(Arrays.asList(OTHER.toUpperCase(), OTHER,
                "853c80ef3c3749fdaa49938b674adae6", ""));
        assertEquals(1, q.getAsJsonArray("uuids").size());
        assertEquals(OTHER, q.getAsJsonArray("uuids").get(0).getAsString());
        assertEquals(200, Protocol.MAX_QUERY_UUIDS);
    }

    // -----------------------------------------------------------------
    // Offline replay
    // -----------------------------------------------------------------

    @Test
    @DisplayName("equips queue per slot while the link is down, last write wins")
    void equipsCoalescePerSlot() {
        OutboundQueue queue = new OutboundQueue();
        queue.addEquip(Protocol.equip("cape", "void-cape"));
        queue.addEquip(Protocol.equip("halo", "seraph-halo"));
        queue.addEquip(Protocol.equip("cape", "dusk-cape"));
        queue.addEquip(Protocol.equip("cape", "ring-cape"));
        queue.addEquip(Protocol.equip("halo", null));
        assertFalse(queue.isEmpty());

        List<JsonObject> out = queue.drain();
        assertEquals(2, out.size(), "one message per slot: cape, halo");
        assertEquals(Protocol.equip("cape", "ring-cape"), out.get(0),
                "the cape the player kept, not the ones they tried on");
        assertEquals(Protocol.equip("halo", null), out.get(1), "taking it off is a state too");
        assertTrue(queue.isEmpty(), "draining empties it");
    }

    // -----------------------------------------------------------------
    // CosmeticState
    // -----------------------------------------------------------------

    @Test
    @DisplayName("equip refuses what is not owned, unknown, or for another slot, and says what is worn")
    void equipRefusals() {
        CosmeticState cs = CosmeticState.get();
        cs.applyLauncher(null, null, Arrays.asList("void-cape", "seraph-halo"));
        sink.sent.clear();

        assertEquals("void-cape", cs.equip("cape", "void-cape"));
        assertEquals("void-cape", cs.equip("cape", "cell-cape"), "not owned: refused");
        assertEquals("void-cape", cs.equip("cape", "seraph-halo"), "wrong slot: refused");
        assertEquals("void-cape", cs.equip("cape", "cape-from-the-future"), "unknown: refused");
        assertEquals("origin-cape", cs.equip("cape", "origin-cape"), "free items are owned");
        assertNull(cs.equip("wings", "void-wings"), "not a slot");
        assertNull(cs.equip("cape", null), "null takes it off");
        assertEquals(Arrays.asList("equip cape void-cape null", "equip cape origin-cape null",
                "equip cape null null"), sink.sent, "a refusal tells the launcher nothing");
    }

    // -----------------------------------------------------------------
    // Bridge
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the four wardrobe calls answer with what was applied and push nothing back")
    void wardrobeCallsAnswerAndDoNotEcho() {
        VoidBridge bridge = bridge();
        CosmeticState.get().applyLauncher(null, null,
                Arrays.asList("ring-cape", "emote-wave", "emote-clap"));
        drain(bridge);

        assertEquals("{\"c\":\"equipCosmetic\",\"returns\":\"ring-cape\"}",
                call(bridge, "{\"c\":\"equipCosmetic\",\"params\":[\"cape\",\"ring-cape\"]}"));
        assertEquals("{\"c\":\"equipCosmetic\",\"returns\":\"ring-cape\"}",
                call(bridge, "{\"c\":\"equipCosmetic\",\"params\":[\"cape\",\"cell-cape\"]}"),
                "refused: the answer is the cape still worn");
        assertEquals("{\"c\":\"setCosmeticTint\",\"returns\":\"#9f8bff\"}",
                call(bridge, "{\"c\":\"setCosmeticTint\",\"params\":[\"cape\",\"#9F8BFF\"]}"));
        assertEquals("{\"c\":\"setCosmeticTint\",\"returns\":null}",
                call(bridge, "{\"c\":\"setCosmeticTint\",\"params\":[\"halo\",\"#123456\"]}"),
                "nothing tintable in the halo slot");
        assertEquals("{\"c\":\"setEmoteWheel\",\"returns\":[\"emote-wave\",\"emote-clap\"]}",
                call(bridge, "{\"c\":\"setEmoteWheel\",\"params\":[[\"emote-wave\",\"emote-bow\","
                        + "\"spark-trail\",\"emote-clap\",\"emote-wave\"]]}"));
        assertEquals("{\"c\":\"playEmote\",\"returns\":false}",
                call(bridge, "{\"c\":\"playEmote\",\"params\":[\"emote-bow\"]}"), "not owned");
        assertEquals("{\"c\":\"equipCosmetic\",\"returns\":null}",
                call(bridge, "{\"c\":\"equipCosmetic\",\"params\":[]}"), "no slot, no answer");

        assertTrue(on(drain(bridge), VoidBridge.EVENT_COSMETICS).isEmpty(),
                "§6.5: the page's own changes are not pushed back at it");
        assertTrue(bridge.errors().isEmpty(), "no dispatch threw: " + bridge.errors());
        assertEquals(Arrays.asList("equip cape ring-cape null", "equip cape ring-cape #9f8bff",
                "emotes [emote-wave, emote-clap]"), sink.sent, "and the launcher heard each change");
    }

    @Test
    @DisplayName("playEmote starts the clip, tells the launcher, and pushes only the end")
    void playEmotePushesOnlyTheEnd() {
        VoidBridge bridge = bridge();
        CosmeticState.get().applyLauncher(null, null, Arrays.asList("emote-wave"));
        drain(bridge);
        sink.sent.clear();

        assertEquals("{\"c\":\"playEmote\",\"returns\":true}",
                call(bridge, "{\"c\":\"playEmote\",\"params\":[\"emote-wave\"]}"));
        assertEquals("emote-wave", Emotes.get().localEmote());
        assertEquals(Arrays.asList("emote emote-wave"), sink.sent);
        assertTrue(on(drain(bridge), VoidBridge.EVENT_EMOTE).isEmpty(), "the start is the answer");

        Emotes.get().clear();
        List<JsonObject> ends = on(drain(bridge), VoidBridge.EVENT_EMOTE);
        assertEquals(1, ends.size());
        assertTrue(ends.get(0).get("id").isJsonNull(), "the end is pushed: only Java knows it");
    }

    @Test
    @DisplayName("an emote Java starts on its own pushes its start")
    void ownEmotePushesItsStart() {
        VoidBridge bridge = bridge();
        CosmeticState.get().applyLauncher(null, null, Arrays.asList("emote-clap"));
        drain(bridge);
        assertTrue(Emotes.get().playLocal("emote-clap"), "the wheel key, from the game thread");
        List<JsonObject> starts = on(drain(bridge), VoidBridge.EVENT_EMOTE);
        assertEquals(1, starts.size());
        assertEquals("emote-clap", starts.get(0).get("id").getAsString());
    }

    @Test
    @DisplayName("a launcher change reaches the page as one cosmetics payload of the documented shape")
    void launcherChangesArePushed() throws Exception {
        final VoidBridge bridge = bridge();
        final EquippedSet self = Protocol.parse("{\"t\":\"cosmetics\",\"self\":{\"uuid\":\"" + SELF
                + "\",\"cape\":\"ring-cape\",\"halo\":\"seraph-halo\",\"emotes\":[\"emote-wave\"],"
                + "\"tints\":{\"cape\":\"#9f8bff\"}}}").self;
        // From another thread, the way void-ws delivers it: the flag that suppresses echoes is
        // per thread, so a launcher change can never be mistaken for the page's own.
        Thread ws = new Thread(new Runnable() {
            @Override
            public void run() {
                CosmeticState.get().applyLauncher(self, null,
                        Arrays.asList("ring-cape", "seraph-halo", "emote-wave"));
            }
        }, "void-ws");
        ws.start();
        ws.join();

        List<JsonObject> pushed = on(drain(bridge), VoidBridge.EVENT_COSMETICS);
        assertEquals(1, pushed.size(), "whole state, coalesced to one per frame");
        JsonObject payload = pushed.get(0);
        assertEquals(3, payload.entrySet().size(), "{self, owned, catalog_version}: " + payload);
        assertEquals(SELF, payload.getAsJsonObject("self").get("uuid").getAsString());
        assertEquals("ring-cape", payload.getAsJsonObject("self").get("cape").getAsString());
        assertEquals("#9f8bff", payload.getAsJsonObject("self").getAsJsonObject("tints")
                .get("cape").getAsString());
        assertTrue(payload.getAsJsonArray("owned").toString().contains("\"origin-cape\""),
                "free items are owned and listed");
        assertEquals(1, payload.get("catalog_version").getAsInt());
    }

    @Test
    @DisplayName("the bridge.json cosmetic call examples return their documented results")
    void callExamplesReturnTheirResults() {
        VoidBridge bridge = bridge();
        CosmeticState.get().applyLauncher(null, null,
                Arrays.asList("ring-cape", "seraph-halo", "emote-wave", "emote-clap"));
        CosmeticState.get().equip("halo", "seraph-halo");
        JsonArray examples = Schemas.examples("bridge.json");
        int checked = 0;
        for (int i = 0; i < examples.size(); i++) {
            JsonObject call = examples.get(i).getAsJsonObject();
            if (!call.has("c") || !call.has("params")) {
                continue;
            }
            String name = call.get("c").getAsString();
            if (!Arrays.asList("equipCosmetic", "setCosmeticTint", "setEmoteWheel", "playEmote",
                    "openStore").contains(name)) {
                continue;
            }
            // The result example is the one that follows its call in the file.
            JsonObject expected = examples.get(i + 1).getAsJsonObject();
            assertEquals(name, expected.get("c").getAsString(), "examples[" + (i + 1) + "]");
            JsonObject answer = Json.parseObject(bridge.dispatch(call.toString()));
            assertEquals(expected.get("returns"), answer.get("returns"), name + " " + call);
            checked++;
        }
        assertEquals(6, checked, "every cosmetic call example was replayed");
    }

    @Test
    @DisplayName("openStore tells the launcher, and answers false with no launcher behind it")
    void openStoreReachesTheLauncher() {
        VoidBridge bridge = bridge();
        assertEquals("{\"c\":\"openStore\",\"returns\":true}",
                call(bridge, "{\"c\":\"openStore\",\"params\":[\"seraph-halo\"]}"));
        assertEquals(Arrays.asList("store seraph-halo"), sink.sent);
        assertEquals("{\"c\":\"openStore\",\"returns\":false}",
                call(bridge, "{\"c\":\"openStore\",\"params\":[]}"), "no id, nothing sent");
        CosmeticState.get().setSink(null);
        assertEquals("{\"c\":\"openStore\",\"returns\":false}",
                call(bridge, "{\"c\":\"openStore\",\"params\":[\"seraph-halo\"]}"),
                "no launcher link: the page must not say the Store opened");
    }

    @Test
    @DisplayName("a reloaded page gets the wardrobe it would otherwise never be told")
    void pushWholeStateCarriesCosmetics() {
        VoidBridge bridge = bridge();
        CosmeticState.get().applyLauncher(null, null, Arrays.asList("void-cape"));
        drain(bridge);
        // The page equips; nothing is pushed (no echo)...
        call(bridge, "{\"c\":\"equipCosmetic\",\"params\":[\"cape\",\"void-cape\"]}");
        assertTrue(on(drain(bridge), VoidBridge.EVENT_COSMETICS).isEmpty());
        // ...so a reload must re-read it live, not replay something remembered.
        bridge.pushWholeState();
        List<JsonObject> pushed = on(drain(bridge), VoidBridge.EVENT_COSMETICS);
        assertEquals(1, pushed.size());
        assertEquals("void-cape", pushed.get(0).getAsJsonObject("self").get("cape").getAsString());
    }

    @Test
    @DisplayName("the cosmetics mod's switches reach the fields the renderer reads")
    void actuatorFields() {
        JsonObject init = null;
        JsonArray protocol = Schemas.examples("protocol.json");
        for (int i = 0; i < protocol.size(); i++) {
            if ("init".equals(protocol.get(i).getAsJsonObject().get("t").getAsString())) {
                init = protocol.get(i).getAsJsonObject();
            }
        }
        assertNotNull(init);
        Loadout loadout = Loadout.fromJson(init.getAsJsonObject("loadout"));
        LiveState state = new LiveState();
        state.applyInit(loadout, Collections.singletonList(loadout),
                dev.voidpvp.client.state.GlobalSettings.fromJson(init.getAsJsonObject("settings")));
        // Factory: on, others on, own trail off in first person, glow on, emote camera on.
        assertTrue(state.cosmeticsOn);
        assertTrue(state.cosmeticsOthers);
        assertFalse(state.cosmeticsOwnFirstPerson);
        assertTrue(state.cosmeticsGlow);
        assertTrue(state.cosmeticsEmoteCamera);
        assertEquals(1f, state.cosmeticsReactive, 0f, "reactions default to full");

        state.setModSetting("cosmetics", "reactive", new com.google.gson.JsonPrimitive("subtle"));
        assertEquals(0.45f, state.cosmeticsReactive, 1e-6f);
        state.setModSetting("cosmetics", "reactive", new com.google.gson.JsonPrimitive("off"));
        assertEquals(0f, state.cosmeticsReactive, 0f);
        state.setModSetting("cosmetics", "others", new com.google.gson.JsonPrimitive(false));
        state.setModSetting("cosmetics", "own_first_person", new com.google.gson.JsonPrimitive(true));
        state.setModSetting("cosmetics", "glow", new com.google.gson.JsonPrimitive(false));
        state.setModSetting("cosmetics", "emote_camera", new com.google.gson.JsonPrimitive(false));
        assertFalse(state.setGameplay("cosmetics", false), "the switch reports what it applied");
        assertFalse(state.cosmeticsOn);
        assertFalse(state.cosmeticsOthers);
        assertTrue(state.cosmeticsOwnFirstPerson);
        assertFalse(state.cosmeticsGlow);
        assertFalse(state.cosmeticsEmoteCamera);
    }
}
