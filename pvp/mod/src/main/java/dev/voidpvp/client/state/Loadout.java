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
 * thirteen mods gets every one of its settings, taken from the loadout where it
 * is present and from {@link ModRegistry} where it is not. The schema makes
 * both the mods and their settings optional precisely so old loadouts stay
 * valid when a mod is added, and resolving that once here means no caller ever
 * has to remember the "absent means default" rule.</p>
 *
 * <p><b>Not thread-safe, deliberately.</b> A loadout is a plain mutable object;
 * {@link LiveState} owns every instance that is live and touches it only under
 * its own monitor, which is what makes it safe with a UI thread, a game thread
 * and the WS thread all writing settings. Adding a second lock down here would
 * buy nothing and would put a lock in the frame loop. Anything that escapes
 * {@code LiveState} — {@link #toJson}, {@link #hud} — is a copy for that
 * reason.</p>
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

    /**
     * A loadout built from nothing but the registry defaults.
     *
     * <p>The {@code mods} object is <em>seeded</em>, one entry per registered mod. It used to be
     * empty, which made this a loadout that knows about no mods at all — and every write to it
     * silently did nothing: {@link #putSetting} looks the mod up in {@code mods} first and returns
     * null when it is absent, so {@code setModSetting} handed the page back null and the value it
     * had just been asked to change never moved. Invisible with a launcher attached, because the
     * first {@code loadout} push replaces this object wholesale; the only client that runs on it
     * is one started without {@code -Dvoid.port}, i.e. every dev client, where it read as "the
     * sliders don't work".</p>
     *
     * <p><b>{@code hud} is seeded too, and for the same reason.</b> It used to be an empty array,
     * which is the exact analogue of the empty {@code mods} object one paragraph up: the overlay's
     * {@code HudEntry} draws a widget only when its mod is on <em>and</em> the loadout places it
     * ({@code HudLayer.tsx}), so a loadout that places nothing draws nothing — all seven HUD mods
     * invisible, whatever their {@code on} says and whatever their settings are changed to. Again
     * invisible with a launcher attached, because Rust's {@code default_library()} seeds
     * placements and the first {@code loadout} push replaces this object wholesale; again the only
     * client that runs on it is the dev client, where it read as "the HUD mods don't do
     * anything".</p>
     */
    public static Loadout defaults(String id, String name) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("icon", "sword");
        o.addProperty("mc", "1.8.9");
        JsonObject mods = new JsonObject();
        for (String modId : ModRegistry.modIds()) {
            mods.add(modId, ModRegistry.defaults(modId));
        }
        o.add("mods", mods);
        JsonArray hud = new JsonArray();
        for (Object[] place : DEFAULT_HUD) {
            JsonObject item = new JsonObject();
            item.addProperty("id", (String) place[0]);
            item.addProperty("anchor", (String) place[1]);
            item.addProperty("dx", (Number) place[2]);
            item.addProperty("dy", (Number) place[3]);
            item.addProperty("scale", Integer.valueOf(1));
            hud.add(item);
        }
        o.add("hud", hud);
        return fromJson(o);
    }

    /**
     * The factory HUD layout, in the overlay's own design-canvas pixels.
     *
     * <p>Transcribed from {@code packages/ingame/src/menu/HudEditorScreen.tsx}'s
     * {@code DEFAULT_HUD}, which is the layout drawn on Figma frame 244:1722 and the one the HUD
     * editor's <em>Reset</em> button restores. Those are the coordinates the page actually lays
     * out in — {@code VoidClient.pumpUi} fits the view to a 1300 x 820 canvas — so they are the
     * ones to copy, rather than the tighter offsets in {@code crates/void-loadout}'s default
     * library, whose 18 px vertical spacing overlaps chips that are taller than that.</p>
     *
     * <p>Mods that ship off ({@code coordinates}) are placed too: a placement is where a widget
     * would go, not whether it is drawn — the {@code on} switch decides that, in one place, in
     * the page.</p>
     *
     * <p><b>{@code watermark} is placed here, and its number is not the schema's.</b>
     * {@code loadout.json}'s factory layout puts it at {@code top-left 20,58}, under an fps at
     * {@code dy 20} and a ping at {@code dy 38} — an 18-20 px rhythm. This table's rhythm is
     * 38-42 px, for the reason the paragraph above gives, and 58 here would land the mark on top
     * of the ping chip at 65 rather than under it. So it takes the next row of <em>this</em>
     * column instead: 103 + 38. Same intent — third in the top-left stack — expressed in the
     * space the page actually lays out in. Change both numbers together or neither.</p>
     */
    private static final Object[][] DEFAULT_HUD = {
        {"fps", "top-left", Integer.valueOf(23), Integer.valueOf(23)},
        {"ping", "top-left", Integer.valueOf(23), Integer.valueOf(65)},
        {"coordinates", "top-left", Integer.valueOf(23), Integer.valueOf(103)},
        {"watermark", "top-left", Integer.valueOf(23), Integer.valueOf(141)},
        {"potion_effects", "top-right", Integer.valueOf(-25), Integer.valueOf(23)},
        {"armor_status", "top-right", Integer.valueOf(-25), Integer.valueOf(299)},
        {"keystrokes", "bottom-left", Integer.valueOf(31), Integer.valueOf(-109)},
        {"cps", "bottom-left", Integer.valueOf(175), Integer.valueOf(-108)},
    };

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

    /**
     * The HUD layout. A copy, not a view: the caller — {@code LiveState.setHud} handing the layout
     * to the sink — reads it after the monitor that guards every write to this list is released,
     * and an unmodifiable view would still see those writes. At most one immutable
     * {@link HudItem} per HUD mod, so the copy is free.
     */
    public List<HudItem> hud() {
        return Collections.unmodifiableList(new ArrayList<HudItem>(hud));
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
