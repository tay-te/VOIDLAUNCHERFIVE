package dev.voidpvp.client.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The closed registry of the twelve mods, transcribed from
 * {@code schema/mods.json} (registry document {@code examples[0]} plus each
 * mod's settings sub-schema).
 *
 * <p>The mod ships no config files (PVP_ARCHITECTURE.md §6.1) and cannot read
 * {@code schema/} at runtime, so the parts of the registry the game actually
 * needs — the id set, the {@code kind} split, the factory defaults and the
 * clamp ranges — live here. {@code schema/mods.json} stays the source of
 * truth: when it changes, this file changes with it and
 * {@code ModRegistryTest} is what catches the drift.</p>
 */
public final class ModRegistry {

    /** Data direction of a mod, {@code mods.json#/definitions/kind}. */
    public enum Kind { HUD, GAMEPLAY }

    /**
     * Mods-panel filter taxonomy, {@code mods.json#/definitions/category}.
     *
     * <p>Deliberately not derivable from {@link Kind}: kind is a data-direction
     * split, category is the product one the panel tabs across. Crosshair is
     * {@code GAMEPLAY} but {@code VISUAL}; Zoom is {@code GAMEPLAY} but
     * {@code UTILITY}. The mod itself never filters anything — it carries the
     * value so {@code ModRegistryTest} can prove the transcription matches the
     * schema the UI reads.</p>
     */
    public enum Category { HUD, PVP, VISUAL, UTILITY }

    private ModRegistry() {
    }

    // -----------------------------------------------------------------
    // Setting descriptors
    // -----------------------------------------------------------------

    private enum Type { BOOL, INT, NUMBER, ENUM, COLOR, KEYBIND }

    private static final class Setting {
        final Type type;
        final double min;
        final double max;
        final Set<String> values;
        final JsonElement fallback;

        Setting(Type type, double min, double max, Set<String> values, JsonElement fallback) {
            this.type = type;
            this.min = min;
            this.max = max;
            this.values = values;
            this.fallback = fallback;
        }
    }

    private static final Map<String, Kind> KINDS = new LinkedHashMap<String, Kind>();
    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<String, Category>();
    private static final Map<String, String> LABELS = new LinkedHashMap<String, String>();
    private static final Map<String, Map<String, Setting>> SETTINGS =
            new LinkedHashMap<String, Map<String, Setting>>();

    private static final Pattern COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    private static Setting bool(boolean def) {
        return new Setting(Type.BOOL, 0, 0, null, new JsonPrimitive(Boolean.valueOf(def)));
    }

    private static Setting number(double min, double max, double def) {
        return new Setting(Type.NUMBER, min, max, null, new JsonPrimitive(Double.valueOf(def)));
    }

    private static Setting integer(double min, double max, long def) {
        return new Setting(Type.INT, min, max, null, new JsonPrimitive(Long.valueOf(def)));
    }

    private static Setting enumOf(String def, String... values) {
        return new Setting(Type.ENUM, 0, 0,
                new LinkedHashSet<String>(Arrays.asList(values)), new JsonPrimitive(def));
    }

    private static Setting color(String def) {
        return new Setting(Type.COLOR, 0, 0, null, new JsonPrimitive(def));
    }

    private static Setting keybind(String def) {
        return new Setting(Type.KEYBIND, 0, 0, null, new JsonPrimitive(def));
    }

    private static Map<String, Setting> mod(String id, Kind kind, Category category,
                                            String label, Object... pairs) {
        Map<String, Setting> map = new LinkedHashMap<String, Setting>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Setting) pairs[i + 1]);
        }
        KINDS.put(id, kind);
        CATEGORIES.put(id, category);
        LABELS.put(id, label);
        SETTINGS.put(id, Collections.unmodifiableMap(map));
        return map;
    }

    static {
        // --- HUD mods -------------------------------------------------
        mod("fps", Kind.HUD, Category.HUD, "FPS display",
                "on", bool(true),
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "color", color("#FFFFFF"),
                "show_label", bool(true));

        mod("keystrokes", Kind.HUD, Category.HUD, "Keystrokes",
                "on", bool(true),
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 0.85),
                "keybind", keybind("NONE"),
                "show_mouse", bool(true),
                "show_spacebar", bool(true),
                "show_cps", bool(false),
                "corner_radius", integer(0, 20, 8),
                "key_color", enumOf("shell", "shell", "raised", "pill", "sky", "teal"),
                "pressed_color", enumOf("accent", "accent", "sky", "warn", "fear", "teal"));

        mod("cps", Kind.HUD, Category.HUD, "CPS counter",
                "on", bool(true),
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "mode", enumOf("left", "left", "right", "both"),
                "window_ms", integer(200, 5000, 1000));

        mod("ping", Kind.HUD, Category.HUD, "Ping display",
                "on", bool(true),
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "show_label", bool(true),
                "good_ms", integer(0, 1000, 60),
                "bad_ms", integer(0, 2000, 150));

        mod("coordinates", Kind.HUD, Category.HUD, "Coordinates",
                "on", bool(false),
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "decimals", integer(0, 3, 1),
                "show_direction", bool(true),
                "layout", enumOf("stacked", "stacked", "inline"));

        mod("armor_status", Kind.HUD, Category.HUD, "Armor status",
                "on", bool(true),
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "orientation", enumOf("horizontal", "horizontal", "vertical"),
                "show_durability", bool(true),
                "show_held_item", bool(true));

        mod("potion_effects", Kind.HUD, Category.HUD, "Potion effects",
                "on", bool(true),
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "show_duration", bool(true),
                "show_amplifier", bool(true),
                "hide_ambient", bool(false));

        // --- Gameplay mods --------------------------------------------
        mod("toggle_sprint", Kind.GAMEPLAY, Category.PVP, "Toggle sprint",
                "on", bool(true),
                "mode", enumOf("toggle", "toggle", "hold"),
                "sneak_too", bool(false),
                "show_status", bool(true));

        mod("fullbright", Kind.GAMEPLAY, Category.VISUAL, "Fullbright",
                "on", bool(false),
                "gamma", number(1, 15, 10));

        mod("hitboxes", Kind.GAMEPLAY, Category.PVP, "Hitboxes",
                "on", bool(false),
                "line_width", number(0.5, 5, 2),
                "color", color("#FFFFFFFF"),
                "show_eye_line", bool(false));

        mod("zoom", Kind.GAMEPLAY, Category.UTILITY, "Zoom",
                "on", bool(true),
                "key", keybind("C"),
                "fov_divisor", number(1.1, 10, 4),
                "smooth", bool(true),
                "cinematic", bool(false));

        mod("crosshair", Kind.GAMEPLAY, Category.VISUAL, "Crosshair",
                "on", bool(false),
                "style", enumOf("cross", "default", "cross", "dot", "circle", "t_shape", "none"),
                "size", integer(1, 20, 5),
                "thickness", integer(1, 5, 1),
                "gap", integer(0, 10, 2),
                "color", color("#FFFFFFFF"),
                "outline", bool(true),
                "dynamic", bool(false));
    }

    /** The twelve mod ids, in registry order. */
    public static List<String> modIds() {
        return Collections.unmodifiableList(new java.util.ArrayList<String>(KINDS.keySet()));
    }

    public static boolean isMod(String id) {
        return KINDS.containsKey(id);
    }

    public static Kind kind(String id) {
        return KINDS.get(id);
    }

    public static boolean isGameplay(String id) {
        return KINDS.get(id) == Kind.GAMEPLAY;
    }

    public static boolean isHud(String id) {
        return KINDS.get(id) == Kind.HUD;
    }

    /** Mods-panel filter category of a mod, or {@code null} for an unknown id. */
    public static Category category(String id) {
        return CATEGORIES.get(id);
    }

    /** Panel copy for a mod, e.g. {@code FPS display}; {@code null} for an unknown id. */
    public static String label(String id) {
        return LABELS.get(id);
    }

    /** Setting keys of a mod, in schema order. */
    public static Set<String> settingKeys(String modId) {
        Map<String, Setting> m = SETTINGS.get(modId);
        return m == null ? Collections.<String>emptySet() : m.keySet();
    }

    /** Factory defaults for one mod as a fresh mutable object. */
    public static JsonObject defaults(String modId) {
        JsonObject out = new JsonObject();
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null) {
            return out;
        }
        for (Map.Entry<String, Setting> e : m.entrySet()) {
            out.add(e.getKey(), e.getValue().fallback);
        }
        return out;
    }

    /** Factory default of a single setting, or {@code null} if unknown. */
    public static JsonElement defaultOf(String modId, String key) {
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null) {
            return null;
        }
        Setting s = m.get(key);
        return s == null ? null : s.fallback;
    }

    /**
     * Clamps an incoming setting value to what the schema allows.
     *
     * @return the value to store, or {@code null} when the mod, the key or the
     *         value's type make it unusable — the caller then keeps whatever it
     *         already had, which is what {@code setModSetting} returns
     *         ({@code bridge.json#/definitions/setModSetting_returns}).
     */
    public static JsonElement clamp(String modId, String key, JsonElement value) {
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null || value == null || value.isJsonNull()) {
            return null;
        }
        Setting s = m.get(key);
        if (s == null || !value.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = value.getAsJsonPrimitive();
        switch (s.type) {
            case BOOL:
                if (p.isBoolean()) {
                    return p;
                }
                return null;
            case INT:
            case NUMBER: {
                if (!p.isNumber()) {
                    return null;
                }
                double d = p.getAsDouble();
                if (Double.isNaN(d)) {
                    return null;
                }
                d = Math.max(s.min, Math.min(s.max, d));
                if (s.type == Type.INT) {
                    return new JsonPrimitive(Long.valueOf(Math.round(d)));
                }
                return new JsonPrimitive(Double.valueOf(d));
            }
            case ENUM:
                if (p.isString() && s.values.contains(p.getAsString())) {
                    return p;
                }
                return null;
            case COLOR:
                if (p.isString() && COLOR.matcher(p.getAsString()).matches()) {
                    return p;
                }
                return null;
            case KEYBIND:
                if (p.isString()) {
                    String up = p.getAsString().toUpperCase(java.util.Locale.ROOT);
                    if (dev.voidpvp.client.input.KeyNames.isValidKeybind(up)) {
                        return new JsonPrimitive(up);
                    }
                }
                return null;
            default:
                return null;
        }
    }

    /** {@code JsonNull} for absent values, so callers never hand out Java null. */
    public static JsonElement nullValue() {
        return JsonNull.INSTANCE;
    }
}
