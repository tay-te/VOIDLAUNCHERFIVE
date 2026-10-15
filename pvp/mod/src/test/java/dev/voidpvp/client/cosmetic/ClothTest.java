package dev.voidpvp.client.cosmetic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cape's cloth, without Minecraft: it must hang, stay finite under abuse, keep its springs
 * near rest length, behave the same at 30 fps and at 240, and reset rather than smear across a
 * teleport.
 */
class ClothTest {

    private static final double SPACING = 1.0 / 16.0;

    private static void anchors(Cloth c, double x, double y, double z, double[] ax, double[] ay, double[] az) {
        for (int i = 0; i <= c.cols; i++) {
            ax[i] = x + (i - c.cols / 2.0) * SPACING;
            ay[i] = y;
            az[i] = z;
        }
    }

    /** Anchors spread across z, so motion along x meets the sheet face-on, as running does. */
    private static void across(Cloth c, double x, double y, double z, double[] ax, double[] ay, double[] az) {
        for (int i = 0; i <= c.cols; i++) {
            ax[i] = x;
            ay[i] = y;
            az[i] = z + (i - c.cols / 2.0) * SPACING;
        }
    }

    private static Cloth cloth() {
        return new Cloth(10, 16, SPACING, 0.9);
    }

    @Test
    @DisplayName("left alone, it hangs straight down from its anchors")
    void hangs() {
        Cloth c = cloth();
        double[] ax = new double[11], ay = new double[11], az = new double[11];
        anchors(c, 0, 10, 0, ax, ay, az);
        c.windX = c.windY = c.windZ = 0;
        for (int f = 0; f < 600; f++) {
            c.advance(1 / 60.0, ax, ay, az, null, 0, 1);
        }
        int bottom = c.idx(5, 16);
        assertEquals(10 - 16 * SPACING, c.y(bottom), 0.08, "hem hangs a cape-length below the anchors");
        assertEquals(0, c.x(bottom), 0.05);
        assertEquals(0, c.z(bottom), 0.05);
        assertTrue(c.maxStretch() < 1.06, "springs near rest: " + c.maxStretch());
    }

    @Test
    @DisplayName("violent random anchor motion never blows up or tears")
    void stable() {
        Cloth c = cloth();
        double[] ax = new double[11], ay = new double[11], az = new double[11];
        Random r = new Random(42);
        double x = 0, y = 64, z = 0;
        for (int f = 0; f < 3000; f++) {
            x += (r.nextDouble() - 0.5) * 0.6;
            y += (r.nextDouble() - 0.5) * 0.4;
            z += (r.nextDouble() - 0.5) * 0.6;
            anchors(c, x, y, z, ax, ay, az);
            double dt = 0.002 + r.nextDouble() * 0.06;
            c.advance(dt, ax, ay, az, null, 0, 1);
            for (int i = 0; i < 11 * 17; i++) {
                assertTrue(Double.isFinite(c.x(i)) && Double.isFinite(c.y(i)) && Double.isFinite(c.z(i)));
            }
        }
        assertTrue(c.maxStretch() < 1.6, "never torn: " + c.maxStretch());
    }

    @Test
    @DisplayName("the same motion at 30 and 240 fps ends in nearly the same shape")
    void frameRateIndependent() {
        double[] a30 = run(30), a240 = run(240);
        double worst = 0;
        for (int i = 0; i < a30.length; i++) {
            worst = Math.max(worst, Math.abs(a30[i] - a240[i]));
        }
        assertTrue(worst < 0.06, "max divergence " + worst + " blocks");
    }

    private static double[] run(int fps) {
        Cloth c = cloth();
        double[] ax = new double[11], ay = new double[11], az = new double[11];
        double t = 0;
        double dt = 1.0 / fps;
        while (t < 3.0) {
            // Run forward for a second and a half, then stop dead: the cape trails, then swings.
            double x = Math.min(t, 1.5) * 5.6;
            across(c, x, 70, 0, ax, ay, az);
            c.advance(dt, ax, ay, az, null, -1, 0);
            t += dt;
        }
        double[] out = new double[3];
        int hem = c.idx(5, 16);
        out[0] = c.x(hem);
        out[1] = c.y(hem);
        out[2] = c.z(hem);
        return out;
    }

    @Test
    @DisplayName("running makes it trail behind and lift")
    void trailsWhenRunning() {
        Cloth c = cloth();
        double[] ax = new double[11], ay = new double[11], az = new double[11];
        double x = 0;
        for (int f = 0; f < 240; f++) {
            x += 5.6 / 60.0;
            across(c, x, 70, 0, ax, ay, az);
            c.advance(1 / 60.0, ax, ay, az, null, -1, 0);
        }
        int hem = c.idx(5, 16);
        assertTrue(c.x(hem) < x - 0.3, "hem behind the runner: " + (c.x(hem) - x));
        assertTrue(c.y(hem) > 70 - 16 * SPACING + 0.15, "hem lifted: " + c.y(hem));
    }

    @Test
    @DisplayName("a teleport resets instead of dragging the cape across the map")
    void teleportResets() {
        Cloth c = cloth();
        double[] ax = new double[11], ay = new double[11], az = new double[11];
        anchors(c, 0, 70, 0, ax, ay, az);
        c.advance(1 / 60.0, ax, ay, az, null, 0, 1);
        c.advance(1 / 60.0, ax, ay, az, null, 0, 1);
        anchors(c, 500, 70, 500, ax, ay, az);
        assertFalse(c.advance(1 / 60.0, ax, ay, az, null, 0, 1));
        int hem = c.idx(5, 16);
        assertEquals(500, c.x(hem), 0.2);
        assertEquals(500, c.z(hem), 0.2);
    }

    @Test
    @DisplayName("the collider keeps the sheet out of a solid it is pulled against")
    void collides() {
        Cloth c = cloth();
        double[] ax = new double[11], ay = new double[11], az = new double[11];
        // A wall at z < 0.05 the cloth is pinned just in front of, with gravity pulling it
        // and a wind pushing it into the wall.
        Cloth.Collider wall = new Cloth.Collider() {
            @Override
            public void collide(double[] xs, double[] ys, double[] zs, int i) {
                if (zs[i] < 0.05) {
                    zs[i] = 0.05;
                }
            }
        };
        c.windZ = -6;
        anchors(c, 0, 70, 0.06, ax, ay, az);
        for (int f = 0; f < 300; f++) {
            c.advance(1 / 60.0, ax, ay, az, wall, 0, 1);
        }
        for (int i = 0; i < 11 * 17; i++) {
            assertTrue(c.z(i) >= 0.049, "particle " + i + " inside the wall: " + c.z(i));
        }
    }
}
