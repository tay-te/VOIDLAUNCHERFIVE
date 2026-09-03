package dev.voidpvp.client.state;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One HUD placement, {@code loadout.json#/definitions/hud_item}: anchor plus
 * offset plus scale, never absolute pixels (§8.1).
 */
public final class HudItem {

    /** {@code loadout.json#/definitions/anchor}. */
    public static final List<String> ANCHORS = Collections.unmodifiableList(Arrays.asList(
            "top-left", "top", "top-right", "left", "center", "right",
            "bottom-left", "bottom", "bottom-right"));

    public final String id;
    public final String anchor;
    public final double dx;
    public final double dy;
    public final double scale;

    public HudItem(String id, String anchor, double dx, double dy, double scale) {
        this.id = id;
        this.anchor = anchor;
        this.dx = dx;
        this.dy = dy;
        this.scale = scale;
    }

    public static HudItem fromJson(JsonObject o) {
        return new HudItem(
                o.get("id").getAsString(),
                o.get("anchor").getAsString(),
                o.has("dx") ? o.get("dx").getAsDouble() : 0,
                o.has("dy") ? o.get("dy").getAsDouble() : 0,
                o.has("scale") && !o.get("scale").isJsonNull() ? o.get("scale").getAsDouble() : 1);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("anchor", anchor);
        o.add("dx", Json.number(dx));
        o.add("dy", Json.number(dy));
        o.add("scale", Json.number(scale));
        return o;
    }

    /**
     * Applies the schema's bounds plus the HUD editor's snap grid, and reports
     * the value actually kept — {@code setHud} returns this so the editor can
     * settle the dragged element where Java put it.
     *
     * @param grid snap grid in unscaled GUI pixels; 0 disables snapping
     */
    public HudItem normalised(int grid) {
        double nx = snap(clamp(dx, -4096, 4096), grid);
        double ny = snap(clamp(dy, -4096, 4096), grid);
        double ns = clamp(scale, 0.25, 4);
        String a = ANCHORS.contains(anchor) ? anchor : "top-left";
        return new HudItem(id, a, nx, ny, ns);
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    private static double snap(double v, int grid) {
        if (grid <= 0) {
            return v;
        }
        return Math.round(v / grid) * (double) grid;
    }

    @Override
    public String toString() {
        return "HudItem[" + id + " " + anchor + " " + dx + "," + dy + " x" + scale + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HudItem)) {
            return false;
        }
        HudItem h = (HudItem) o;
        return id.equals(h.id) && anchor.equals(h.anchor)
                && Double.compare(dx, h.dx) == 0 && Double.compare(dy, h.dy) == 0
                && Double.compare(scale, h.scale) == 0;
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + anchor.hashCode();
    }
}
