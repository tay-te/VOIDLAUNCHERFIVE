package dev.voidpvp.client.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.state.HudItem;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.LiveState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * {@code window.void}, the in-process seam between the mod and the in-game
 * React bundle — {@code schema/bridge.json}, PVP_ARCHITECTURE.md §6.5.
 *
 * <p>JS to Java is a call: the shim hands us a {@code {c, params}} envelope
 * through {@code window.__void_native(json)} and we answer with
 * {@code {c, returns}}. Ultralight runs inside the JVM, so the answer is the
 * state actually applied — there is no ack, no request id and no optimistic
 * UI. {@code openKeybindCapture} is the one exception and resolves later.</p>
 *
 * <p>Java to JS is a push. Events raised during a frame are queued here and
 * drained into a single {@code window.void.__emit([...])} evaluation, so a
 * frame costs one JS call however many sensors fired.</p>
 */
public final class VoidBridge {

    /** The five channels of {@code bridge.json#/definitions/event_name}. */
    public static final String EVENT_KEYS = "keys";
    public static final String EVENT_TICK = "tick";
    public static final String EVENT_SERVER = "server";
    public static final String EVENT_LOADOUT = "loadout";
    public static final String EVENT_MENU = "menu";

    private final LiveState state;
    private final BridgeHost host;
    private final Deque<JsonObject> pending = new ArrayDeque<JsonObject>();
    private final List<String> errors = new ArrayList<String>();

    public VoidBridge(LiveState state, BridgeHost host) {
        this.state = state;
        this.host = host;
    }

    // -----------------------------------------------------------------
    // JS -> Java
    // -----------------------------------------------------------------

    /**
     * Handles one {@code {c, params}} envelope and returns the
     * {@code {c, returns}} answer as JSON text.
     *
     * <p>Never throws: the caller is JavaScript inside a paint, and an
     * exception there would take the frame with it. A malformed or unknown
     * call comes back with {@code returns: null}.</p>
     */
    public String dispatch(String requestJson) {
        String call = "";
        try {
            JsonObject req = Json.parseObject(requestJson);
            if (req == null || !req.has("c")) {
                return result("", JsonNull.INSTANCE);
            }
            call = req.get("c").getAsString();
            JsonArray params = req.has("params") && req.get("params").isJsonArray()
                    ? req.getAsJsonArray("params") : new JsonArray();
            return result(call, invoke(call, params));
        } catch (RuntimeException e) {
            errors.add(call + ": " + e);
            return result(call, JsonNull.INSTANCE);
        }
    }

    private JsonElement invoke(String call, JsonArray p) {
        if ("setGameplay".equals(call)) {
            if (p.size() < 2) {
                return new JsonPrimitive(Boolean.FALSE);
            }
            boolean applied = state.setGameplay(p.get(0).getAsString(), p.get(1).getAsBoolean());
            return new JsonPrimitive(Boolean.valueOf(applied));
        }
        if ("setHud".equals(call)) {
            if (p.size() < 2 || !p.get(1).isJsonObject()) {
                return JsonNull.INSTANCE;
            }
            JsonObject placement = p.get(1).getAsJsonObject();
            Double scale = placement.has("scale") && !placement.get("scale").isJsonNull()
                    ? Double.valueOf(placement.get("scale").getAsDouble()) : null;
            HudItem stored = state.setHud(
                    p.get(0).getAsString(),
                    Json.string(placement, "anchor", "top-left"),
                    Json.number(placement, "dx", 0),
                    Json.number(placement, "dy", 0),
                    scale);
            return stored == null ? JsonNull.INSTANCE : stored.toJson();
        }
        if ("setModSetting".equals(call)) {
            if (p.size() < 3) {
                return JsonNull.INSTANCE;
            }
            JsonElement stored = state.setModSetting(
                    p.get(0).getAsString(), p.get(1).getAsString(), p.get(2));
            return stored == null ? JsonNull.INSTANCE : stored;
        }
        if ("switchLoadout".equals(call)) {
            if (p.size() < 1) {
                return new JsonPrimitive(Boolean.FALSE);
            }
            return new JsonPrimitive(Boolean.valueOf(state.switchLoadout(p.get(0).getAsString())));
        }
        if ("closeMenu".equals(call)) {
            if (host != null) {
                host.closeMenu();
            }
            return JsonNull.INSTANCE;
        }
        if ("openKeybindCapture".equals(call)) {
            if (host != null) {
                host.beginKeybindCapture(p.size() > 0 ? p.get(0).getAsString() : null);
            }
            // The captured key arrives later through __emitKeybind; the
            // synchronous answer only says the capture is armed.
            return JsonNull.INSTANCE;
        }
        return JsonNull.INSTANCE;
    }

    private static String result(String call, JsonElement returns) {
        JsonObject o = new JsonObject();
        o.addProperty("c", call);
        o.add("returns", returns == null ? JsonNull.INSTANCE : returns);
        return o.toString();
    }

    /** Dispatch failures, for the log and for tests. */
    public List<String> errors() {
        return errors;
    }

    // -----------------------------------------------------------------
    // Java -> JS
    // -----------------------------------------------------------------

    /** Queues one {@code {e, payload}} envelope for this frame's push. */
    public void emit(String event, JsonElement payload) {
        JsonObject env = new JsonObject();
        env.addProperty("e", event);
        env.add("payload", payload == null ? JsonNull.INSTANCE : payload);
        synchronized (pending) {
            // `tick`, `keys`, `menu` and `server` carry whole state, so an
            // older envelope on the same channel is dead weight. `loadout` is
            // whole-state too but a switch is rare enough to keep in order.
            if (EVENT_TICK.equals(event) || EVENT_KEYS.equals(event) || EVENT_MENU.equals(event)) {
                dropChannel(event);
            }
            pending.addLast(env);
        }
    }

    private void dropChannel(String event) {
        java.util.Iterator<JsonObject> it = pending.iterator();
        while (it.hasNext()) {
            if (event.equals(Json.string(it.next(), "e", null))) {
                it.remove();
            }
        }
    }

    public boolean hasPending() {
        synchronized (pending) {
            return !pending.isEmpty();
        }
    }

    /**
     * Drains the frame's events into the one script that delivers them, or
     * {@code null} when nothing happened. The shim's {@code __emit} takes an
     * array of envelopes and fans them out to the {@code on} handlers.
     */
    public String drainScript() {
        JsonArray batch = new JsonArray();
        synchronized (pending) {
            if (pending.isEmpty()) {
                return null;
            }
            while (!pending.isEmpty()) {
                batch.add(pending.pollFirst());
            }
        }
        return "window.void.__emit(" + batch.toString() + ")";
    }

    /** The script that resolves an {@code openKeybindCapture} promise. */
    public static String keybindScript(String keyName) {
        JsonElement value = keyName == null ? JsonNull.INSTANCE : new JsonPrimitive(keyName);
        return "window.void.__emitKeybind(" + value.toString() + ")";
    }
}
