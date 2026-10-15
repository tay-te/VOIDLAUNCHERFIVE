package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;

/**
 * One animated vector channel: keys of {@code (t, x, y, z)} with an ease per key that shapes the
 * segment arriving at it. Shared by model animations and emotes; compiled by
 * {@code scripts/gen-cosmetics.mjs} into {@code [[t, x, y, z, ease], ...]}.
 */
public final class Keyframes {

    public static final int LINEAR = 0, STEP = 1, SINE = 2, QUAD = 3, CUBIC = 4, BACK = 5,
            ELASTIC = 6, BOUNCE = 7;

    final float[] t;
    final float[] v; // 3 per key
    final int[] ease;

    Keyframes(float[] t, float[] v, int[] ease) {
        this.t = t;
        this.v = v;
        this.ease = ease;
    }

    public static Keyframes parse(JsonArray keys) {
        int n = keys.size();
        float[] t = new float[n];
        float[] v = new float[n * 3];
        int[] e = new int[n];
        for (int i = 0; i < n; i++) {
            JsonArray k = keys.get(i).getAsJsonArray();
            t[i] = k.get(0).getAsFloat();
            v[i * 3] = k.get(1).getAsFloat();
            v[i * 3 + 1] = k.get(2).getAsFloat();
            v[i * 3 + 2] = k.get(3).getAsFloat();
            e[i] = k.size() > 4 ? k.get(4).getAsInt() : SINE;
        }
        return new Keyframes(t, v, e);
    }

    /** Writes the value at time {@code time} into {@code out[o..o+2]}. */
    public void sample(float time, float[] out, int o) {
        int n = t.length;
        if (n == 0) {
            out[o] = out[o + 1] = out[o + 2] = 0;
            return;
        }
        if (time <= t[0] || n == 1) {
            out[o] = v[0];
            out[o + 1] = v[1];
            out[o + 2] = v[2];
            return;
        }
        if (time >= t[n - 1]) {
            out[o] = v[(n - 1) * 3];
            out[o + 1] = v[(n - 1) * 3 + 1];
            out[o + 2] = v[(n - 1) * 3 + 2];
            return;
        }
        int k = 1;
        while (k < n - 1 && t[k] < time) {
            k++;
        }
        float span = t[k] - t[k - 1];
        float f = span <= 0 ? 1 : (time - t[k - 1]) / span;
        float e = ease(ease[k], f);
        for (int c = 0; c < 3; c++) {
            float a = v[(k - 1) * 3 + c], b = v[k * 3 + c];
            out[o + c] = a + (b - a) * e;
        }
    }

    public float end() {
        return t.length == 0 ? 0 : t[t.length - 1];
    }

    public static float ease(int kind, float f) {
        switch (kind) {
            case LINEAR:
                return f;
            case STEP:
                return f < 1 ? 0 : 1;
            case QUAD:
                return f < 0.5f ? 2 * f * f : 1 - (float) Math.pow(-2 * f + 2, 2) / 2;
            case CUBIC:
                return f < 0.5f ? 4 * f * f * f : 1 - (float) Math.pow(-2 * f + 2, 3) / 2;
            case BACK: {
                // Out-back: a small overshoot that settles, which is what makes a raised arm land.
                float c1 = 1.4f, c3 = c1 + 1;
                float g = f - 1;
                return 1 + c3 * g * g * g + c1 * g * g;
            }
            case ELASTIC: {
                if (f <= 0 || f >= 1) {
                    return f;
                }
                return (float) (Math.pow(2, -10 * f) * Math.sin((f * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
            }
            case BOUNCE: {
                float n1 = 7.5625f, d1 = 2.75f;
                if (f < 1 / d1) {
                    return n1 * f * f;
                } else if (f < 2 / d1) {
                    f -= 1.5f / d1;
                    return n1 * f * f + 0.75f;
                } else if (f < 2.5 / d1) {
                    f -= 2.25f / d1;
                    return n1 * f * f + 0.9375f;
                }
                f -= 2.625f / d1;
                return n1 * f * f + 0.984375f;
            }
            case SINE:
            default:
                return (float) (-(Math.cos(Math.PI * f) - 1) / 2);
        }
    }

    /** Smoothstep, for blend weights. */
    public static float smooth(float f) {
        f = f < 0 ? 0 : f > 1 ? 1 : f;
        return f * f * (3 - 2 * f);
    }
}
