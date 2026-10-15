package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keyframes, springs, the body frame and the part frame: the small maths everything leans on. */
class MotionMathTest {

    @Test
    @DisplayName("keyframes hold at the ends and ease between")
    void keyframes() {
        JsonArray keys = new JsonParser().parse("[[0,0,0,0,2],[1,10,20,-4,2],[2,10,20,-4,0]]").getAsJsonArray();
        Keyframes k = Keyframes.parse(keys);
        float[] v = new float[3];
        k.sample(-1, v, 0);
        assertEquals(0, v[0], 1e-6);
        k.sample(0.5f, v, 0);
        assertEquals(5, v[0], 1e-4, "sine in-out is half way at half time");
        k.sample(0.25f, v, 0);
        assertTrue(v[0] < 2.5f, "eased: slower at the start");
        k.sample(5, v, 0);
        assertEquals(-4, v[2], 1e-6);
        assertEquals(2, k.end(), 1e-6);
    }

    @Test
    @DisplayName("every ease starts at 0 and ends at 1")
    void eases() {
        for (int e = 0; e <= Keyframes.BOUNCE; e++) {
            assertEquals(0, Keyframes.ease(e, 0), 1e-4, "ease " + e);
            assertEquals(1, Keyframes.ease(e, 1), 1e-4, "ease " + e);
        }
        assertTrue(Keyframes.ease(Keyframes.BACK, 0.7f) > 1, "back overshoots");
    }

    @Test
    @DisplayName("a spring settles on its target the same way at any frame rate")
    void spring() {
        Spring a = new Spring(6, 0), b = new Spring(6, 0);
        for (int i = 0; i < 30; i++) {
            a.step(1, 1 / 30f);
        }
        for (int i = 0; i < 240; i++) {
            b.step(1, 1 / 240f);
        }
        assertEquals(a.value, b.value, 1e-3);
        assertTrue(a.value > 0.95 && a.value <= 1.0001, "critically damped: no overshoot");
    }

    @Test
    @DisplayName("the body frame round-trips model pixels through world space")
    void bodyFrame() {
        BodyFrame f = new BodyFrame();
        f.set(100.25, 64, -30.5, 37f, 0, 11.2f, 0);
        double[] w = new double[3], m = new double[3];
        f.toWorld(3, 7, 2.5, w);
        f.toModel(w[0], w[1], w[2], m);
        assertEquals(3, m[0], 1e-9);
        assertEquals(7, m[1], 1e-9);
        assertEquals(2.5, m[2], 1e-9);
        // The neck of an upright player with no emote is 1.5078 blocks * 0.9375 above the feet.
        f.set(0, 0, 0, 0, 0, 0, 0);
        f.toWorld(0, 0, 0, w);
        assertEquals(1.5078125 * 0.9375, w[1], 1e-9);
        // Model +z is behind the player: at body yaw 0 the player faces +z in world, so behind is -z.
        f.toWorld(0, 0, 16, w);
        assertTrue(w[2] < 0, "model +z maps behind a player facing south");
    }

    @Test
    @DisplayName("a part frame's local and model spaces are inverse")
    void partFrame() {
        PartFrame p = new PartFrame();
        p.set(1, 12, -0.5, 0.4, -0.3, 0.2);
        double[] a = new double[3], b = new double[3];
        p.toModel(2, 5, -1, a);
        p.toLocal(a[0], a[1], a[2], b);
        assertEquals(2, b[0], 1e-9);
        assertEquals(5, b[1], 1e-9);
        assertEquals(-1, b[2], 1e-9);
    }
}
