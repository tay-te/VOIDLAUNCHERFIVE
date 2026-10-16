package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * How a cape's texture animates and how its cloth should feel ({@code <id>/cape.json}). The
 * texture itself is the vanilla 64 x 32 cape layout at any integer scale, frames stacked down.
 */
public final class CapeStyle {

    public final int frames;
    public final float fps;
    /** Bend stiffness, 0..1. */
    public final float stiffness;
    /** Mass multiplier: heavier cloth falls faster and lifts less. */
    public final float weight;
    /** Glow anchors on the outer face: [u, v (cape pixels), rgb, size, strength]. */
    public final float[][] glow;
    /** The sheet's material where {@code material.png} says nothing: cloth unless set. */
    public final int material;

    private CapeStyle(int frames, float fps, float stiffness, float weight, float[][] glow, int material) {
        this.material = material;
        this.frames = frames;
        this.fps = fps;
        this.stiffness = stiffness;
        this.weight = weight;
        this.glow = glow;
    }

    public static CapeStyle parse(JsonObject o) {
        JsonArray fr = o.getAsJsonArray("frames");
        JsonArray g = o.has("glow") ? o.getAsJsonArray("glow") : new JsonArray();
        float[][] glow = new float[g.size()][];
        int i = 0;
        for (JsonElement e : g) {
            glow[i++] = CosmeticModel.floats(e.getAsJsonArray());
        }
        return new CapeStyle(fr.get(0).getAsInt(), fr.get(1).getAsFloat(),
                o.get("stiffness").getAsFloat(), o.get("weight").getAsFloat(), glow,
                o.has("m") ? o.get("m").getAsInt() : CosmeticShader.CLOTH);
    }
}
