package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** A trail's particle behaviour ({@code <id>/trail.json}); the sprite is {@code sprite.png}. */
public final class TrailStyle {

    public static final int SPARK = 0, DRIFT = 1, SETTLE = 2, STAIR = 3;

    public final int motion;
    public final int[] colors;
    /** Seconds a particle lives. */
    public final float life;
    /** Particles per second at a sprint. */
    public final float rate;
    /** Sprite size, blocks. */
    public final float size;
    /** Most particles alive at once for one player. */
    public final int cap;
    /** Additive bloom strength around each particle, 0 for none. */
    public final float glow;

    private TrailStyle(int motion, int[] colors, float life, float rate, float size, int cap, float glow) {
        this.motion = motion;
        this.colors = colors;
        this.life = life;
        this.rate = rate;
        this.size = size;
        this.cap = cap;
        this.glow = glow;
    }

    public static TrailStyle parse(JsonObject o) {
        String m = o.get("motion").getAsString();
        int motion = "drift".equals(m) ? DRIFT : "settle".equals(m) ? SETTLE : "stair".equals(m) ? STAIR : SPARK;
        JsonArray c = o.getAsJsonArray("colors");
        int[] colors = new int[Math.max(1, c.size())];
        colors[0] = 0xFFFFFF;
        for (int i = 0; i < c.size(); i++) {
            colors[i] = c.get(i).getAsInt();
        }
        return new TrailStyle(motion, colors, o.get("life").getAsFloat(), o.get("rate").getAsFloat(),
                o.get("size").getAsFloat(), o.get("cap").getAsInt(), o.get("glow").getAsFloat());
    }
}
