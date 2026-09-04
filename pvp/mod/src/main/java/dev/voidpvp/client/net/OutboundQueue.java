package dev.voidpvp.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.state.HudItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the mod owes Rust while the socket is down.
 *
 * <p>"If Rust is unreachable, in-memory state persists for the session and is
 * flushed on reconnect" (§6.1) — the same {@code state} and {@code hud}
 * messages, replayed. Replaying them one by one would be wasteful and, worse,
 * out of order with respect to itself, so patches coalesce per loadout (last
 * write wins per path) and a HUD layout simply replaces the previous one.</p>
 */
public final class OutboundQueue {

    private final Map<String, Map<String, JsonElement>> patches =
            new LinkedHashMap<String, Map<String, JsonElement>>();
    private final Map<String, List<HudItem>> huds = new LinkedHashMap<String, List<HudItem>>();
    private JsonObject serverMessage;

    public synchronized void addState(String loadoutId, Map<String, JsonElement> patch) {
        Map<String, JsonElement> merged = patches.get(loadoutId);
        if (merged == null) {
            merged = new LinkedHashMap<String, JsonElement>();
            patches.put(loadoutId, merged);
        }
        merged.putAll(patch);
    }

    public synchronized void addHud(String loadoutId, List<HudItem> items) {
        huds.put(loadoutId, new ArrayList<HudItem>(items));
    }

    /** Only the latest presence matters; the launcher wants current state. */
    public synchronized void setServer(JsonObject message) {
        serverMessage = message;
    }

    public synchronized boolean isEmpty() {
        return patches.isEmpty() && huds.isEmpty() && serverMessage == null;
    }

    /** Drains everything into the frames to send, in a stable order. */
    public synchronized List<JsonObject> drain() {
        List<JsonObject> out = new ArrayList<JsonObject>();
        for (Map.Entry<String, Map<String, JsonElement>> e : patches.entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.add(Protocol.state(e.getKey(), e.getValue()));
            }
        }
        for (Map.Entry<String, List<HudItem>> e : huds.entrySet()) {
            out.add(Protocol.hud(e.getKey(), e.getValue()));
        }
        if (serverMessage != null) {
            out.add(serverMessage);
        }
        patches.clear();
        huds.clear();
        serverMessage = null;
        return out;
    }
}
