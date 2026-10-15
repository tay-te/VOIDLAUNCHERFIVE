package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A full-body emote: keyframed rotation/position per player part, compiled from
 * {@code cosmetics-src/<id>/emote.json} into {@code assets/void/cosmetics/<id>/emote.json}.
 *
 * <p>Parts, in this order: {@link #ROOT} (moves and turns the whole player — sit lowers it to the
 * ground), {@link #UPPER} (bends head, torso and arms about the waist — bow), then the six model
 * parts. Rotations are radians in model space, positions model pixels.</p>
 */
public final class EmoteClip {

    public static final int ROOT = 0, UPPER = 1, HEAD = 2, BODY = 3, RIGHT_ARM = 4, LEFT_ARM = 5,
            RIGHT_LEG = 6, LEFT_LEG = 7, PARTS = 8;

    public final float length;
    public final boolean loop;
    public final float loopFrom;
    public final float blend;
    /** Camera framing hint for the local orbit: null, or "low". */
    public final String camera;
    final Keyframes[] rot = new Keyframes[PARTS];
    final Keyframes[] pos = new Keyframes[PARTS];

    private EmoteClip(float length, boolean loop, float loopFrom, float blend, String camera) {
        this.length = length;
        this.loop = loop;
        this.loopFrom = loopFrom;
        this.blend = blend;
        this.camera = camera;
    }

    public static EmoteClip parse(JsonObject o) {
        float len = o.get("len").getAsFloat();
        boolean loop = o.has("loop") && o.get("loop").getAsInt() != 0;
        float from = o.has("loopFrom") ? o.get("loopFrom").getAsFloat() : 0;
        float blend = o.has("blend") ? o.get("blend").getAsFloat() : 0.25f;
        String cam = o.has("cam") ? o.get("cam").getAsString() : null;
        EmoteClip c = new EmoteClip(len, loop, from, blend, cam);
        JsonArray ch = o.getAsJsonArray("ch");
        for (int i = 0; i < ch.size(); i++) {
            JsonArray e = ch.get(i).getAsJsonArray();
            int part = e.get(0).getAsInt();
            if (part < 0 || part >= PARTS) {
                continue;
            }
            Keyframes k = Keyframes.parse(e.get(2).getAsJsonArray());
            if ("r".equals(e.get(1).getAsString())) {
                c.rot[part] = k;
            } else {
                c.pos[part] = k;
            }
        }
        return c;
    }

    /** Maps wall time since start to clip time: loops hold in their loop section. */
    public float clipTime(float t) {
        if (loop && t > length) {
            float span = length - loopFrom;
            return span <= 0 ? length : loopFrom + ((t - loopFrom) % span);
        }
        return Math.min(t, length);
    }

    /** True when the clip drives this part's rotation. */
    public boolean drivesRotation(int part) {
        return rot[part] != null;
    }

    public boolean drivesPosition(int part) {
        return pos[part] != null;
    }

    /** Samples a part's rotation (radians) at clip time {@code t} into {@code out[o..o+2]}. */
    public void rotation(int part, float t, float[] out, int o) {
        if (rot[part] == null) {
            out[o] = out[o + 1] = out[o + 2] = 0;
        } else {
            rot[part].sample(t, out, o);
        }
    }

    public void position(int part, float t, float[] out, int o) {
        if (pos[part] == null) {
            out[o] = out[o + 1] = out[o + 2] = 0;
        } else {
            pos[part].sample(t, out, o);
        }
    }
}
