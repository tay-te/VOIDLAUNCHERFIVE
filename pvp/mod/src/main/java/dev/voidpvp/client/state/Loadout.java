package dev.voidpvp.client.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A loadout, {@code schema/loadout.json}: the named snapshot of which mods are
 * on, their settings and the HUD layout (§8).
 *
 * <p>Mod state is <em>materialised</em> on construction: every one of the
 * twelve mods gets every one of its settings, taken from the loadout where it
 * is present and from {@link ModRegistry} where it is not. The schema makes
 * both the mods and their settings optional precisely so old loadouts stay
 * valid when a mod is added, and resolving that once here means no caller ever
 * has to remember the "absent means default" rule.</p>
 */
public final class Loadout {

    private final String id;
    private String name;
    private String icon;
    private String server;
    private String mc;
    private final Map<String, JsonObject> mods = new LinkedHashMap<String, JsonObject>();
    private final List<HudItem> hud = new ArrayList<HudItem>();
    private JsonObject stats;

    private Loadout(String id) {
        this.id = id;
    }

    /** Parses a loadout as it arrives in {@code init}, {@code loadout} or a test. */
    public static Loadout fromJson(JsonObject o) {
        Loadout l = new Loadout(Json.string(o, "id", "default"));
        l.name = Json.string(o, "name", l.id);
        l.icon = Json.string(o, "icon", "sword");
        l.server = o.has("server") && !o.get("server").isJsonNull()
                ? o.get("server").getAsString() : null;
        l.mc = Json.string(o, "mc", "1.8.9");

        JsonObject src = o.has("mods") && o.get("mods").isJsonObject()
                ? o.getAsJsonObject("mods") : new JsonObject();
        for (String modId : ModRegistry.modIds()) {
            JsonObject merged = ModRegistry.defaults(modId);
            if (src.has(modId) && src.get(modId).isJsonObject()) {
                JsonObject given = src.getAsJsonObject(modId);
                for (Map.Entry<String, JsonElement> e : given.entrySet()) {
                    JsonElement clamped = ModRegistry.clamp(modId, e.getKey(), e.getValue());
                    if (clamped != null) {
                        merged.add(e.getKey(), clamped);
                    }
                }
            }
            l.mods.put(modId, merged);
        }

        if (o.has("hud") && o.get("hud").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("hud");
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isJsonObject()) {
                    continue;
                }
                HudItem item = HudItem.fromJson(arr.get(i).getAsJsonObject());
                if (ModRegistry.isHud(item.id)) {
                    l.hud.add(item);
                }
            }
        }
        l.stats = o.has("stats") && o.get("stats").isJsonObject()
                ? Json.deepCopy(o.getAsJsonObject("stats")) : null;
        return l;
    }

    /** A loadout built from nothing but the registry defaults. */
    public static Loadout defaults(String id, String name) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("icon", "sword");
        o.addProperty("mc", "1.8.9");
        o.add("mods", new JsonObject());
        o.add("hud", new JsonArray());
        return fromJson(o);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String icon() {
        return icon;
    }

    public String server() {
        return server;
    }

    public String mc() {
        return mc;
    }

    /** Effective value of one setting; never {@code null} for a known key. */
    public JsonElement setting(String modId, String key) {
        JsonObject m = mods.get(modId);
        if (m == null) {
            return null;
        }
        JsonElement v = m.get(key);
        return v != null ? v : ModRegistry.defaultOf(modId, key);
    }

    public boolean isOn(String modId) {
        JsonElement v = setting(modId, "on");
        return v != null && v.isJsonPrimitive() && v.getAsBoolean();
    }

    public double numberSetting(String modId, String key, double fallback) {
        JsonElement v = setting(modId, key);
        if (v == null || !v.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return v.getAsDouble();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public String stringSetting(String modId, String key, String fallback) {
        JsonElement v = setting(modId, key);
        if (v == null || !v.isJsonPrimitive()) {
            return fallback;
        }
        return v.getAsString();
    }

    public boolean boolSetting(String modId, String key, boolean fallback) {
        JsonElement v = setting(modId, key);
        if (v == null || !v.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return v.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * Writes one setting after clamping.
     *
     * @return the value actually stored, or the value already there when the
     *         incoming one was unusable
     */
    public JsonElement putSetting(String modId, String key, JsonElement value) {
        JsonObject m = mods.get(modId);
        if (m == null) {
            return null;
        }
        JsonElement clamped = ModRegistry.clamp(modId, key, value);
        if (clamped == null) {
            return setting(modId, key);
        }
        m.add(key, clamped);
        return clamped;
    }

    public List<HudItem> hud() {
        return Collections.unmodifiableList(hud);
    }

    /** Replaces (or appends) the placement of one HUD item, preserving paint order. */
    public void putHud(HudItem item) {
        for (int i = 0; i < hud.size(); i++) {
            if (hud.get(i).id.equals(item.id)) {
                hud.set(i, item);
                return;
            }
        }
        hud.add(item);
    }

    public HudItem hudItem(String modId) {
        for (HudItem h : hud) {
            if (h.id.equals(modId)) {
                return h;
            }
        }
        return null;
    }

    /** The loadout as JSON, in the shape {@code loadout.json} describes. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("icon", icon);
        if (server == null) {
            o.add("server", com.google.gson.JsonNull.INSTANCE);
        } else {
            o.addProperty("server", server);
        }
        o.addProperty("mc", mc);
        JsonObject m = new JsonObject();
        for (Map.Entry<String, JsonObject> e : mods.entrySet()) {
            m.add(e.getKey(), Json.deepCopy(e.getValue()));
        }
        o.add("mods", m);
        JsonArray h = new JsonArray();
        for (HudItem item : hud) {
            h.add(item.toJson());
        }
        o.add("hud", h);
        if (stats != null) {
            o.add("stats", Json.deepCopy(stats));
        }
        return o;
    }

    public Loadout copy() {
        return fromJson(toJson());
    }
}
