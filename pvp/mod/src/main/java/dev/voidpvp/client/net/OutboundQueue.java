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
 * write wins per path), a HUD layout simply replaces the previous one, and a
 * globals patch coalesces per key.</p>
 *
 * <p>Coalescing globals is not only a saving. The HUD editor's <em>Reset layout</em>
 * stands the snap grid down and puts it straight back, so a session that resets
 * while the link is down owes {@code hud_editor_grid} twice with the second write
 * undoing the first; replaying both would write the transient 0 to
 * {@code settings.json} and then correct it, which is two disk writes and one
 * window in which a crash leaves snapping off. Last write wins collapses it to
 * nothing.</p>
 */
public final class OutboundQueue {

    private final Map<String, Map<String, JsonElement>> patches =
            new LinkedHashMap<String, Map<String, JsonElement>>();
    private final Map<String, List<HudItem>> huds = new LinkedHashMap<String, List<HudItem>>();
    private final Map<String, JsonElement> globals = new LinkedHashMap<String, JsonElement>();
    /**
     * In-game library changes, one per loadout id, last write wins: a save then a delete of the
     * same loadout is just the delete, a delete then a save (impossible today) just the save.
     * Drained <em>first</em>, because a {@code state} patch for a loadout created while the link
     * was down names an id Rust only learns from its {@code loadout_save}.
     */
    private final Map<String, JsonObject> library = new LinkedHashMap<String, JsonObject>();
    /** The last in-game pick, if any; only the newest matters. */
    private String selected;
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

    public synchronized void addGlobals(Map<String, JsonElement> patch) {
        globals.putAll(patch);
    }

    public synchronized void addLoadoutSave(String id, JsonObject loadout) {
        library.remove(id);
        library.put(id, Protocol.loadoutSave(loadout));
    }

    public synchronized void addLoadoutDelete(String id) {
        library.remove(id);
        library.put(id, Protocol.loadoutDelete(id));
    }

    public synchronized void setSelected(String id) {
        selected = id;
    }

    /** Only the latest presence matters; the launcher wants current state. */
    public synchronized void setServer(JsonObject message) {
        serverMessage = message;
    }

    public synchronized boolean isEmpty() {
        return patches.isEmpty() && huds.isEmpty() && globals.isEmpty() && serverMessage == null
                && library.isEmpty() && selected == null;
    }

    /** Drains everything into the frames to send, in a stable order. */
    public synchronized List<JsonObject> drain() {
        List<JsonObject> out = new ArrayList<JsonObject>(library.values());
        if (selected != null) {
            out.add(Protocol.loadoutSelect(selected));
        }
        for (Map.Entry<String, Map<String, JsonElement>> e : patches.entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.add(Protocol.state(e.getKey(), e.getValue()));
            }
        }
        for (Map.Entry<String, List<HudItem>> e : huds.entrySet()) {
            out.add(Protocol.hud(e.getKey(), e.getValue()));
        }
        if (!globals.isEmpty()) {
            out.add(Protocol.globals(globals));
        }
        if (serverMessage != null) {
            out.add(serverMessage);
        }
        library.clear();
        selected = null;
        patches.clear();
        huds.clear();
        globals.clear();
        serverMessage = null;
        return out;
    }
}
