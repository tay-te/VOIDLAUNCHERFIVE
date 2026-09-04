package dev.voidpvp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.net.Backoff;
import dev.voidpvp.client.net.OutboundQueue;
import dev.voidpvp.client.net.Protocol;
import dev.voidpvp.client.net.SessionStats;
import dev.voidpvp.client.state.HudItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "If Rust is unreachable, in-memory state persists for the session and is
 * flushed on reconnect" (§6.1).
 */
class OfflineFlushTest {

    private static Map<String, JsonElement> patch(String path, JsonElement value) {
        Map<String, JsonElement> map = new LinkedHashMap<String, JsonElement>();
        map.put(path, value);
        return map;
    }

    @Test
    @DisplayName("patches for one loadout coalesce, last write wins")
    void patchesCoalesce() {
        OutboundQueue queue = new OutboundQueue();
        assertTrue(queue.isEmpty());
        queue.addState("sword-pvp", patch("mods.fullbright.on", new JsonPrimitive(true)));
        queue.addState("sword-pvp", patch("mods.fullbright.on", new JsonPrimitive(false)));
        queue.addState("sword-pvp", patch("mods.zoom.key", new JsonPrimitive("V")));
        assertFalse(queue.isEmpty());

        List<JsonObject> drained = queue.drain();
        assertEquals(1, drained.size(), "one state message for the loadout");
        JsonObject patch = drained.get(0).getAsJsonObject("patch");
        assertEquals(2, patch.entrySet().size());
        assertFalse(patch.get("mods.fullbright.on").getAsBoolean(), "the last write wins");
        assertTrue(queue.isEmpty(), "draining empties it");
    }

    @Test
    @DisplayName("each loadout gets its own state message")
    void patchesAreGroupedByLoadout() {
        OutboundQueue queue = new OutboundQueue();
        queue.addState("sword-pvp", patch("mods.zoom.on", new JsonPrimitive(true)));
        queue.addState("bedwars", patch("mods.zoom.on", new JsonPrimitive(false)));
        List<JsonObject> drained = queue.drain();
        assertEquals(2, drained.size());
        assertEquals("sword-pvp", drained.get(0).get("loadout").getAsString());
        assertEquals("bedwars", drained.get(1).get("loadout").getAsString());
    }

    @Test
    @DisplayName("a HUD layout replaces the previous one, never merges")
    void hudLayoutsReplace() {
        OutboundQueue queue = new OutboundQueue();
        queue.addHud("sword-pvp", Arrays.asList(
                new HudItem("fps", "top-left", 20, 20, 1)));
        queue.addHud("sword-pvp", Arrays.asList(
                new HudItem("fps", "top-left", 20, 20, 1),
                new HudItem("ping", "top-left", 20, 38, 1)));
        List<JsonObject> drained = queue.drain();
        assertEquals(1, drained.size());
        assertEquals(2, drained.get(0).getAsJsonArray("items").size(),
                "a whole-list write cannot leave Rust with half a layout");
    }

    @Test
    @DisplayName("only the latest presence is replayed")
    void serverPresenceIsLatestOnly() {
        OutboundQueue queue = new OutboundQueue();
        queue.setServer(Protocol.server("a.example", true, 25565));
        queue.setServer(Protocol.server("", false, 25565));
        List<JsonObject> drained = queue.drain();
        assertEquals(1, drained.size());
        assertFalse(drained.get(0).get("connected").getAsBoolean());
    }

    @Test
    @DisplayName("backoff grows to a ceiling and resets on a good connection")
    void backoffGrows() {
        Backoff backoff = new Backoff(500, 15000);
        assertEquals(500, backoff.peekDelayMs());
        long previous = 0;
        for (int i = 0; i < 10; i++) {
            long delay = backoff.nextDelayMs();
            assertTrue(delay >= previous || delay >= 15000, "delay went backwards");
            assertTrue(delay <= 15000 * 1.25, "delay blew past the ceiling: " + delay);
            previous = delay;
        }
        assertEquals(15000, backoff.peekDelayMs(), "and it settles at the ceiling");
        backoff.reset();
        assertEquals(500, backoff.peekDelayMs());
        assertEquals(0, backoff.attempts());
    }

    @Test
    @DisplayName("session telemetry averages fps and reports every sixty seconds")
    void sessionStats() {
        long start = 1_000_000L;
        SessionStats stats = new SessionStats(start);
        stats.sample(100);
        stats.sample(200);
        stats.sample(0);
        assertEquals(150.0, stats.fpsAverage(), 1e-9, "a zero sample is not a frame");
        assertEquals(5000, stats.playedMs(start + 5000));

        assertFalse(stats.shouldReport(start + 59_000));
        assertTrue(stats.shouldReport(start + 60_000));
        assertFalse(stats.shouldReport(start + 61_000), "the timer restarted");
        assertTrue(stats.shouldReport(start + 121_000));
    }

    @Test
    @DisplayName("a session message with no server carries an explicit null")
    void sessionSerialisation() {
        JsonObject message = Protocol.session(141.96, 812000, null, "sword-pvp");
        assertEquals("session", message.get("t").getAsString());
        assertTrue(message.get("server").isJsonNull());
        assertEquals(142.0, message.get("fps_avg").getAsDouble(), 0.05);
        assertEquals("sword-pvp", message.get("loadout").getAsString());
    }

    @Test
    @DisplayName("a non-default server port rides along, the default does not")
    void serverPort() {
        assertFalse(Protocol.server("mc.hypixel.net", true, 25565).has("port"));
        assertEquals(25577, Protocol.server("mc.hypixel.net", true, 25577).get("port").getAsInt());
        assertFalse(Protocol.server("", false, 25577).has("port"),
                "a disconnect has no port to report");
    }
}
