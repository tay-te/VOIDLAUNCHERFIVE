package dev.voidpvp.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.cosmetic.EquippedSet;
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

    /**
     * {@code globals}: a global setting written in game, as a delta.
     *
     * <p>A delta and not the whole object on purpose. {@code global_settings} is
     * {@code additionalProperties: true} so the launcher may add a global without a
     * protocol bump, and {@link GlobalSettings} here is a fixed five-field class that
     * cannot carry one — so echoing the whole object back would silently erase every
     * global this build does not model. Rust merges the named keys and leaves the rest
     * of {@code settings.json} alone.</p>
     */
    public static JsonObject globals(Map<String, JsonElement> patch) {
        JsonObject p = new JsonObject();
        for (Map.Entry<String, JsonElement> e : patch.entrySet()) {
            if (e.getValue() != null && !e.getValue().isJsonNull()) {
                p.add(e.getKey(), e.getValue());
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("t", "globals");
        o.add("patch", p);
        return o;
    }

    public static JsonObject session(double fpsAvg, long playedMs, String server,
                                     String loadoutId) {
        return session(fpsAvg, -1, playedMs, server, loadoutId);
    }

    /** {@code session} with the mean ping; a negative {@code pingAvg} leaves the field out. */
    public static JsonObject session(double fpsAvg, double pingAvg, long playedMs, String server,
                                     String loadoutId) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "session");
        o.add("fps_avg", Json.number(Math.round(fpsAvg * 10.0) / 10.0));
        o.addProperty("played_ms", Long.valueOf(Math.max(0, playedMs)));
        if (pingAvg >= 0) {
            o.add("ping_avg", Json.number(Math.round(pingAvg * 10.0) / 10.0));
        }
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

    /** {@code loadout_save}: a whole loadout the in-game Loadouts screen created or changed. */
    public static JsonObject loadoutSave(JsonObject loadout) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "loadout_save");
        o.add("loadout", loadout);
        return o;
    }

    /** {@code loadout_delete}: a loadout deleted in game. */
    public static JsonObject loadoutDelete(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "loadout_delete");
        o.addProperty("id", id);
        return o;
    }

    /** {@code loadout_select}: a loadout picked in game other than by the cycle key. */
    public static JsonObject loadoutSelect(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "loadout_select");
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

    // -- cosmetics, Java to Rust ----------------------------------------
    //
    // Additive to protocol v2: a launcher that predates these ignores an unknown `t` (§7), so
    // an old launcher against this mod loses the cosmetics and nothing else. `v` stays 2.

    /** Most uuids one {@code cosmetics_query} may carry ({@code protocol.json}'s maxItems). */
    public static final int MAX_QUERY_UUIDS = 200;

    /**
     * {@code cosmetics_query}: the players this client sees and does not know yet. The caller
     * chunks to {@link #MAX_QUERY_UUIDS}; uuids go out dashed, lower case, once each.
     */
    public static JsonObject cosmeticsQuery(List<String> uuids) {
        JsonArray arr = new JsonArray();
        List<String> seen = new ArrayList<String>();
        for (String u : uuids) {
            String n = EquippedSet.normalise(u);
            if (!n.isEmpty() && !seen.contains(n)) {
                seen.add(n);
                arr.add(new JsonPrimitive(n));
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("t", "cosmetics_query");
        o.add("uuids", arr);
        return o;
    }

    /**
     * {@code emote} (outbound): the local player started an emote. Never carries a
     * {@code uuid}: that is what tells it apart from the inbound {@code emote}, and the schema's
     * {@code not} enforces it.
     */
    public static JsonObject emote(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "emote");
        o.addProperty("id", id);
        return o;
    }

    /**
     * {@code equip}: {@code {t, slot, id}}, {@code id} null when the slot was taken off. Always
     * written, null included, because the message is the whole state of one slot, which is what
     * lets {@link OutboundQueue} keep only the latest per slot. The wire carries no tint and no
     * emote wheel (yet); those stay in game for the session.
     */
    public static JsonObject equip(String slot, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "equip");
        o.addProperty("slot", slot);
        o.add("id", id == null ? JsonNull.INSTANCE : new JsonPrimitive(id));
        return o;
    }

    /**
     * {@code open_store}: {@code {t, item}}. Get in launcher Store on a locked item; the launcher
     * shows its window on that item's Store sheet. Nothing is bought in game.
     */
    public static JsonObject openStore(String item) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "open_store");
        o.addProperty("item", item);
        return o;
    }

    // -- Rust to Java ---------------------------------------------------

    /** One decoded inbound frame; {@link Kind#UNKNOWN} for anything we ignore. */
    public static final class Inbound {

        public enum Kind { INIT, LOADOUT, SETTINGS, LIBRARY, COSMETICS, EMOTE, UNKNOWN }

        public final Kind kind;
        public final int version;
        public final Loadout loadout;
        /** The whole library from {@code init.loadouts} — full loadouts, not summaries. */
        public final List<Loadout> loadouts;
        public final GlobalSettings settings;
        /** {@code cosmetics.self}; null when absent or null (leave the local set alone). */
        public final EquippedSet self;
        /** {@code cosmetics.players}, a delta; null when absent. */
        public final List<EquippedSet> players;
        /** Remote {@code emote}: who, dashed lower case. */
        public final String uuid;
        /** Remote {@code emote}: which emote. */
        public final String emoteId;
        /** Remote {@code emote}: the sender's wall clock, ms since the epoch; 0 when absent. */
        public final long atMs;

        Inbound(Kind kind, int version, Loadout loadout, List<Loadout> loadouts,
                GlobalSettings settings) {
            this(kind, version, loadout, loadouts, settings, null, null, null, null, 0L);
        }

        private Inbound(Kind kind, int version, Loadout loadout, List<Loadout> loadouts,
                        GlobalSettings settings, EquippedSet self, List<EquippedSet> players,
                        String uuid, String emoteId, long atMs) {
            this.kind = kind;
            this.version = version;
            this.loadout = loadout;
            this.loadouts = loadouts;
            this.settings = settings;
            this.self = self;
            this.players = players;
            this.uuid = uuid;
            this.emoteId = emoteId;
            this.atMs = atMs;
        }

        static Inbound unknown() {
            return new Inbound(Kind.UNKNOWN, 0, null, null, null);
        }

        static Inbound cosmetics(EquippedSet self, List<EquippedSet> players) {
            return new Inbound(Kind.COSMETICS, 0, null, null, null, self, players, null, null, 0L);
        }

        static Inbound emote(String uuid, String id, long atMs) {
            return new Inbound(Kind.EMOTE, 0, null, null, null, null, null, uuid, id, atMs);
        }
    }

    /** {@code loadouts}, whole, from {@code init} or {@code library}; malformed entries skipped. */
    private static List<Loadout> loadouts(JsonObject o) {
        List<Loadout> loadouts = new ArrayList<Loadout>();
        if (o.has("loadouts") && o.get("loadouts").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("loadouts");
            for (int i = 0; i < arr.size(); i++) {
                if (arr.get(i).isJsonObject()) {
                    loadouts.add(Loadout.fromJson(arr.get(i).getAsJsonObject()));
                }
            }
        }
        return loadouts;
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
            if ("library".equals(t)) {
                List<Loadout> loadouts = loadouts(o);
                if (loadouts.isEmpty()) {
                    return Inbound.unknown();
                }
                return new Inbound(Inbound.Kind.LIBRARY, 0, null, loadouts, null);
            }
            if ("init".equals(t)) {
                // Whole loadouts: every entry is applyable on its own, which is what
                // makes switchLoadout and the L cycle sub-frame with no round trip.
                List<Loadout> loadouts = loadouts(o);
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
            if ("cosmetics".equals(t)) {
                return parseCosmetics(o);
            }
            if ("emote".equals(t)) {
                // Only the inbound shape carries a uuid. A frame without one is the outbound
                // `emote` reflected back, or nonsense, and either way not for us.
                if (!o.has("uuid") || !o.get("uuid").isJsonPrimitive()
                        || !o.has("id") || !o.get("id").isJsonPrimitive()) {
                    return Inbound.unknown();
                }
                String uuid = EquippedSet.normalise(o.get("uuid").getAsString());
                if (uuid.isEmpty()) {
                    return Inbound.unknown();
                }
                long at = o.has("at") && o.get("at").isJsonPrimitive()
                        && o.get("at").getAsJsonPrimitive().isNumber()
                        ? o.get("at").getAsLong() : 0L;
                return Inbound.emote(uuid, o.get("id").getAsString(), at);
            }
        } catch (RuntimeException e) {
            return Inbound.unknown();
        }
        return Inbound.unknown();
    }

    /**
     * {@code cosmetics}: {@code self} (null = no online session, leave the local set alone) and
     * {@code players}, a delta. The message carries no ownership: the backend validates equips
     * and reverts a refused one with a fresh {@code self}. A message with neither field has
     * nothing to apply and reads as {@link Inbound.Kind#UNKNOWN}, so a malformed one costs
     * nothing. Ids the catalog does not know are dropped by
     * {@link EquippedSet#fromJson} and {@code CosmeticState.applyLauncher}, not here: the frame
     * is well formed, it just names things this jar cannot draw.
     */
    private static Inbound parseCosmetics(JsonObject o) {
        EquippedSet self = o.has("self") && o.get("self").isJsonObject()
                ? EquippedSet.fromJson(o.getAsJsonObject("self")) : null;
        List<EquippedSet> players = null;
        if (o.has("players") && o.get("players").isJsonArray()) {
            players = new ArrayList<EquippedSet>();
            for (JsonElement e : o.getAsJsonArray("players")) {
                if (e.isJsonObject()) {
                    EquippedSet s = EquippedSet.fromJson(e.getAsJsonObject());
                    if (!s.uuid.isEmpty()) {
                        players.add(s);
                    }
                }
            }
        }
        if (self == null && players == null) {
            return Inbound.unknown();
        }
        return Inbound.cosmetics(self, players);
    }
}
