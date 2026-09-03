package dev.voidpvp.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.voidpvp.client.state.GlobalSettings;
import dev.voidpvp.client.state.HudItem;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.Loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Rust/Java WS messages of {@code schema/protocol.json} (§7). One JSON
 * object per text frame, discriminated on {@code t}.
 *
 * <p>Forward compatibility is the rule that shapes this class: an unknown
 * {@code t} and an unknown field are ignored, never an error, because the two
 * halves are independently updatable. {@code v} rides on {@code hello} and
 * {@code init} only, and a mismatch is fatal.</p>
 */
public final class Protocol {

    /**
     * {@code protocol.json#/definitions/protocol_version}.
     *
     * <p>2 since {@code init.loadouts} became whole loadouts rather than
     * summaries: against a v1 launcher this mod would receive summaries,
     * materialise every mod at its factory default, and silently apply the
     * wrong loadout on a switch. Refusing that pair is what {@code v} is for.</p>
     */
    public static final int VERSION = 2;

    private Protocol() {
    }

    // -- Java to Rust ---------------------------------------------------

    public static JsonObject hello(String mcVersion, String modVersion, String token) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "hello");
        o.addProperty("v", Integer.valueOf(VERSION));
        o.addProperty("mc", mcVersion);
        o.addProperty("mod", modVersion);
        o.addProperty("token", token);
        return o;
    }

    public static JsonObject state(String loadoutId, Map<String, JsonElement> patch) {
        JsonObject p = new JsonObject();
        for (Map.Entry<String, JsonElement> e : patch.entrySet()) {
            p.add(e.getKey(), e.getValue() == null ? JsonNull.INSTANCE : e.getValue());
        }
        JsonObject o = new JsonObject();
        o.addProperty("t", "state");
        o.addProperty("loadout", loadoutId);
        o.add("patch", p);
        return o;
    }

    public static JsonObject hud(String loadoutId, List<HudItem> items) {
        JsonArray arr = new JsonArray();
        for (HudItem item : items) {
            arr.add(item.toJson());
        }
        JsonObject o = new JsonObject();
        o.addProperty("t", "hud");
        o.addProperty("loadout", loadoutId);
        o.add("items", arr);
        return o;
    }

    public static JsonObject session(double fpsAvg, long playedMs, String server,
                                     String loadoutId) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "session");
        o.add("fps_avg", Json.number(Math.round(fpsAvg * 10.0) / 10.0));
        o.addProperty("played_ms", Long.valueOf(Math.max(0, playedMs)));
        if (server == null || server.isEmpty()) {
            o.add("server", JsonNull.INSTANCE);
        } else {
            o.addProperty("server", server);
        }
        if (loadoutId != null) {
            o.addProperty("loadout", loadoutId);
        }
        return o;
    }

    /** {@code hotkey}: the player pressed a global hotkey; §6.3, already applied. */
    public static JsonObject hotkey(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "hotkey");
        o.addProperty("id", id);
        return o;
    }

    /** {@code hotkey.id} values, {@code protocol.json#/definitions/hotkey_id}. */
    public static final String HOTKEY_LOADOUT_NEXT = "loadout.next";
    public static final String HOTKEY_OVERLAY = "overlay";

    public static JsonObject server(String host, boolean connected, int port) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "server");
        o.addProperty("host", host == null ? "" : host);
        o.addProperty("connected", Boolean.valueOf(connected));
        if (connected && port != 25565) {
            o.addProperty("port", Integer.valueOf(port));
        }
        return o;
    }

    // -- Rust to Java ---------------------------------------------------

    /** One decoded inbound frame; {@link Kind#UNKNOWN} for anything we ignore. */
    public static final class Inbound {

        public enum Kind { INIT, LOADOUT, SETTINGS, UNKNOWN }

        public final Kind kind;
        public final int version;
        public final Loadout loadout;
        /** The whole library from {@code init.loadouts} — full loadouts, not summaries. */
        public final List<Loadout> loadouts;
        public final GlobalSettings settings;

        Inbound(Kind kind, int version, Loadout loadout, List<Loadout> loadouts,
                GlobalSettings settings) {
            this.kind = kind;
            this.version = version;
            this.loadout = loadout;
            this.loadouts = loadouts;
            this.settings = settings;
        }

        static Inbound unknown() {
            return new Inbound(Kind.UNKNOWN, 0, null, null, null);
        }
    }

    /** Decodes one inbound frame. Never throws on shape; unknown means ignore. */
    public static Inbound parse(String text) {
        JsonObject o;
        try {
            o = Json.parseObject(text);
        } catch (RuntimeException e) {
            return Inbound.unknown();
        }
        if (o == null || !o.has("t") || !o.get("t").isJsonPrimitive()) {
            return Inbound.unknown();
        }
        String t = o.get("t").getAsString();
        try {
            if ("init".equals(t)) {
                // Whole loadouts: every entry is applyable on its own, which is what
                // makes switchLoadout and the L cycle sub-frame with no round trip.
                List<Loadout> loadouts = new ArrayList<Loadout>();
                if (o.has("loadouts") && o.get("loadouts").isJsonArray()) {
                    JsonArray arr = o.getAsJsonArray("loadouts");
                    for (int i = 0; i < arr.size(); i++) {
                        if (arr.get(i).isJsonObject()) {
                            loadouts.add(Loadout.fromJson(arr.get(i).getAsJsonObject()));
                        }
                    }
                }
                Loadout loadout = o.has("loadout") && o.get("loadout").isJsonObject()
                        ? Loadout.fromJson(o.getAsJsonObject("loadout")) : null;
                GlobalSettings settings = GlobalSettings.fromJson(
                        o.has("settings") && o.get("settings").isJsonObject()
                                ? o.getAsJsonObject("settings") : null);
                return new Inbound(Inbound.Kind.INIT, Json.integer(o, "v", 0), loadout,
                        loadouts, settings);
            }
            if ("loadout".equals(t)) {
                if (!o.has("loadout") || !o.get("loadout").isJsonObject()) {
                    return Inbound.unknown();
                }
                return new Inbound(Inbound.Kind.LOADOUT, 0,
                        Loadout.fromJson(o.getAsJsonObject("loadout")), null, null);
            }
            if ("settings".equals(t)) {
                if (!o.has("settings") || !o.get("settings").isJsonObject()) {
                    return Inbound.unknown();
                }
                return new Inbound(Inbound.Kind.SETTINGS, 0, null, null,
                        GlobalSettings.fromJson(o.getAsJsonObject("settings")));
            }
        } catch (RuntimeException e) {
            return Inbound.unknown();
        }
        return Inbound.unknown();
    }
}
