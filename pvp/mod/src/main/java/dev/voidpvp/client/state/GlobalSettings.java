package dev.voidpvp.client.state;

import com.google.gson.JsonObject;

/**
 * The global, non-loadout settings the game needs,
 * {@code protocol.json#/definitions/global_settings}. Account, Java path and
 * RAM stay on the Rust side; they are launcher concerns (§8.3).
 */
public final class GlobalSettings {

    public final String menuKey;
    public final String cycleLoadoutKey;
    public final String theme;
    public final double uiScale;
    public final int hudEditorGrid;

    public GlobalSettings(String menuKey, String cycleLoadoutKey, String theme,
                          double uiScale, int hudEditorGrid) {
        this.menuKey = menuKey;
        this.cycleLoadoutKey = cycleLoadoutKey;
        this.theme = theme;
        this.uiScale = uiScale;
        this.hudEditorGrid = hudEditorGrid;
    }

    /** The schema defaults, used until Rust sends an {@code init}. */
    public static GlobalSettings defaults() {
        return new GlobalSettings("RSHIFT", "L", "void-dark", 1.0, 4);
    }

    public static GlobalSettings fromJson(JsonObject o) {
        if (o == null) {
            return defaults();
        }
        GlobalSettings d = defaults();
        String menu = Json.string(o, "menu_key", d.menuKey);
        String cycle = Json.string(o, "cycle_loadout_key", d.cycleLoadoutKey);
        return new GlobalSettings(
                dev.voidpvp.client.input.KeyNames.isValidKeybind(menu) ? menu : d.menuKey,
                dev.voidpvp.client.input.KeyNames.isValidKeybind(cycle) ? cycle : d.cycleLoadoutKey,
                Json.string(o, "theme", d.theme),
                clamp(Json.number(o, "ui_scale", d.uiScale), 0.5, 3),
                (int) clamp(Json.integer(o, "hud_editor_grid", d.hudEditorGrid), 0, 64));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("menu_key", menuKey);
        o.addProperty("cycle_loadout_key", cycleLoadoutKey);
        o.addProperty("theme", theme);
        o.add("ui_scale", Json.number(uiScale));
        o.addProperty("hud_editor_grid", Integer.valueOf(hudEditorGrid));
        return o;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
