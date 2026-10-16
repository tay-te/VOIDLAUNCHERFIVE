package dev.voidpvp.client.cosmetic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Secondary motion and fight reactions: the damped spring's maths, the reaction curves' timing,
 * intensity and its decay, attribution, and the cloth's kick and ripple.
 */
class ReactiveMotionTest {

    @AfterEach
    void reset() {
        Reactive.devMotion(true, -1f);
        Reactive.clear();
    }

    // ------------------------------------------------------------------ the spring

    private static float settle(DampedSpring.Params p, float fps, float seconds, float[] peak) {
        DampedSpring s = new DampedSpring(p, 0);
        int n = (int) Math.floor(seconds * fps + 1e-4);
        for (int i = 0; i < n; i++) {
            s.step(1, 1 / fps);
            if (peak != null) {
                peak[0] = Math.max(peak[0], s.value);
            }
        }
        // The frame that ends exactly at `seconds`.
        s.step(1, seconds - n / fps);
        return s.value;
    }

    @Test
    @DisplayName("an underdamped spring is the same at 30, 60, 144 and 400 fps")
    void springFrameRateIndependent() {
        DampedSpring.Params p = new DampedSpring.Params(13f, 0.28f);
        for (float t : new float[] {0.1f, 0.25f, 0.5f, 1f}) {
            float a = settle(p, 30, t, null), b = settle(p, 60, t, null), c = settle(p, 144, t, null),
                    d = settle(p, 400, t, null);
            assertEquals(a, d, 2e-3, "30 vs 400 fps at " + t + "s");
            assertEquals(b, d, 2e-3, "60 vs 400 fps at " + t + "s");
            assertEquals(c, d, 2e-3, "144 vs 400 fps at " + t + "s");
        }
    }

    @Test
    @DisplayName("the exact step matches the analytic solution")
    void springAnalytic() {
        float w = 10f, z = 0.3f;
        DampedSpring.Params p = new DampedSpring.Params(w, z);
        DampedSpring s = new DampedSpring(p, 0);
        float t = 0;
        for (int i = 0; i < 37; i++) {
            s.step(1, 1 / 60f);
            t += 1 / 60f;
        }
        // x(t) for a unit step from rest: 1 - e^{-zwt} (cos wd t + zw/wd sin wd t)
        double wd = w * Math.sqrt(1 - z * z);
        double x = 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + z * w / wd * Math.sin(wd * t));
        assertEquals(x, s.value, 1e-4);
    }

    @Test
    @DisplayName("underdamped overshoots and settles; critical never overshoots")
    void springOvershoot() {
        float[] peak = {0};
        float end = settle(new DampedSpring.Params(13f, 0.28f), 120, 2f, peak);
        assertTrue(peak[0] > 1.25f, "a hat-like spring overshoots: " + peak[0]);
        assertEquals(1f, end, 1e-3, "and settles within two seconds");
        float[] cpeak = {0};
        float cend = settle(new DampedSpring.Params(22f, 1f), 120, 1f, cpeak);
        assertTrue(cpeak[0] <= 1.0001f, "critically damped: no overshoot " + cpeak[0]);
        assertEquals(1f, cend, 1e-3);
    }

    @Test
    @DisplayName("critical DampedSpring agrees with the old Spring")
    void springAgreesWithSpring() {
        Spring a = new Spring(6f, 0);
        DampedSpring b = new DampedSpring(new DampedSpring.Params(6f, 1f), 0);
        for (int i = 0; i < 50; i++) {
            a.step(1, 1 / 50f);
            b.step(1, 1 / 50f);
            assertEquals(a.value, b.value, 1e-4);
        }
    }

    @Test
    @DisplayName("a kick rings down to rest and never blows up at a long frame")
    void springKick() {
        DampedSpring s = new DampedSpring(new DampedSpring.Params(15f, 0.12f), 0);
        s.kick(10f);
        float max = 0;
        for (int i = 0; i < 400; i++) {
            s.step(0, 1 / 100f);
            max = Math.max(max, Math.abs(s.value));
        }
        assertTrue(max > 0.3f && max < 1f, "a visible wobble: " + max);
        assertEquals(0, s.value, 0.01);
        s.kick(10f);
        s.step(0, 5f); // a hitch: clamped to a tenth of a second
        assertTrue(Math.abs(s.value) < 2f && !Float.isNaN(s.value));
    }

    @Test
    @DisplayName("shared params give each spring its own state")
    void springSharedParams() {
        DampedSpring.Params p = new DampedSpring.Params(9f, 0.4f);
        DampedSpring a = new DampedSpring(p, 0), b = new DampedSpring(p, 5);
        a.step(1, 1 / 60f);
        b.step(5, 1 / 60f);
        assertEquals(5f, b.value, 1e-6, "at its target, stays");
        assertTrue(a.value > 0 && a.value < 0.1f);
    }

    @Test
    @DisplayName("soft limit is linear near zero and never passes the limit")
    void softLimit() {
        assertEquals(0.1f, Secondary.soft(0.1f, 10f), 2e-3);
        assertTrue(Secondary.soft(1e6f, 3.5f) < 3.5f);
        assertEquals(-Secondary.soft(4f, 3.5f), Secondary.soft(-4f, 3.5f), 1e-6);
    }

    // ------------------------------------------------------------------ curves

    @Test
    @DisplayName("a pulse rises over its attack, peaks at one, decays exponentially")
    void pulseCurve() {
        assertEquals(0, Reaction.pulse(-0.01f, 0.03f, 0.2f), 0);
        assertEquals(0.5f, Reaction.pulse(0.015f, 0.03f, 0.2f), 1e-4, "smoothstep half way");
        assertEquals(1f, Reaction.pulse(0.03f, 0.03f, 0.2f), 1e-4);
        assertEquals((float) Math.exp(-1), Reaction.pulse(0.23f, 0.03f, 0.2f), 1e-4, "one time constant");
        float prev = 1f;
        for (float t = 0.03f; t < 3f; t += 0.01f) {
            float v = Reaction.pulse(t, 0.03f, 0.2f);
            assertTrue(v <= prev + 1e-6f, "monotone after the peak");
            prev = v;
        }
        assertEquals(0, Reaction.pulse(10f, 0.03f, 0.2f), 1e-6);
    }

    @Test
    @DisplayName("the kill flourish pops in 80 ms, holds, and is gone within two and a half seconds")
    void flourishCurve() {
        assertEquals(1f, Reaction.flourishCurve(0.08f), 1e-4);
        assertEquals(1f, Reaction.flourishCurve(0.2f), 1e-4);
        assertTrue(Reaction.flourishCurve(0.7f) < 0.4f);
        assertTrue(Reaction.flourishCurve(2.5f) < 0.01f);
    }

    @Test
    @DisplayName("being hit: the flinch peaks within 40 ms and is under a tenth by 600 ms")
    void flinchTiming() {
        Reaction r = new Reaction();
        r.update(10f, 1f);
        r.hurt(10f, 1f);
        r.update(10.03f, 1f);
        assertTrue(r.flinch > 0.95f, "peak " + r.flinch);
        r.update(10.6f, 1f);
        assertTrue(r.flinch < 0.1f, "faded " + r.flinch);
    }

    @Test
    @DisplayName("a combo flares a step per hit, six to full, lapses after the window, a hit taken resets it")
    void comboFlare() {
        Reaction r = new Reaction();
        float t = 0;
        r.update(t, 1f);
        float last = 0;
        for (int k = 1; k <= 6; k++) {
            assertEquals(k, r.landed(t));
            for (int i = 0; i < 30; i++) {
                t += 1 / 60f;
                r.update(t, 1f);
            }
            assertTrue(r.flare > last, "flare rises with hit " + k + ": " + r.flare);
            last = r.flare;
        }
        assertTrue(r.flare > 0.85f, "six hits is (nearly) full: " + r.flare);
        // Lapse: three seconds with no hit, then it eases down.
        for (int i = 0; i < 60 * 5; i++) {
            t += 1 / 60f;
            r.update(t, 1f);
        }
        assertTrue(r.flare < 0.05f, "lapsed " + r.flare);
        assertEquals(0, r.comboAt(t));
        assertEquals(1, r.landed(t), "a new combo starts from one");
        r.landed(t + 0.1f);
        r.hurt(t + 0.2f, 1f);
        assertEquals(0, r.comboAt(t + 0.2f), "taking a hit ends the combo");
    }

    @Test
    @DisplayName("kills inside the streak window make a streak at full strength")
    void streak() {
        Reaction r = new Reaction();
        assertEquals(1, r.kill(5f));
        assertEquals(0.8f, r.killStrength, 1e-6);
        assertEquals(2, r.kill(15f));
        assertEquals(1f, r.killStrength, 1e-6);
        assertEquals(1, r.kill(15f + Reaction.STREAK_WINDOW + 1));
    }

    @Test
    @DisplayName("intensity is exactly 1 at rest and with reactive off, brighter with a combo, decays back")
    void intensity() {
        Reaction r = new Reaction();
        r.update(0, 1f);
        for (int s = 0; s <= Reaction.BADGE; s++) {
            assertEquals(1f, r.intensity(s), 0, "rest, slot " + s);
        }
        float t = 0;
        for (int k = 0; k < 6; k++) {
            r.landed(t);
            t += 0.4f;
            r.update(t, 1f);
        }
        float lit = r.intensity(Reaction.HALO);
        assertTrue(lit > 1.4f, "a full combo lights the halo: " + lit);
        r.kill(t);
        r.update(t + 0.15f, 1f);
        assertTrue(r.intensity(Reaction.HALO) > lit, "the kill outshines the combo");
        assertTrue(r.intensity(Reaction.HALO) <= 2.5f, "capped");
        // Decay: five seconds later everything is back to rest.
        for (int i = 0; i < 300; i++) {
            t += 1 / 60f;
            r.update(t, 1f);
        }
        assertEquals(1f, r.intensity(Reaction.HALO), 0.02);
        // Off: the same events, no change at all.
        Reaction off = new Reaction();
        off.update(0, 0f);
        off.landed(0);
        off.kill(0);
        off.hurt(0.01f, 1f);
        off.health(0.05f);
        off.update(0.1f, 0f);
        for (int s = 0; s <= Reaction.BADGE; s++) {
            assertEquals(1f, off.intensity(s), 0, "off, slot " + s);
        }
    }

    @Test
    @DisplayName("subtle is a fraction of full")
    void subtle() {
        Reaction full = new Reaction(), sub = new Reaction();
        full.update(0, 1f);
        sub.update(0, Reaction.SUBTLE);
        full.kill(0);
        sub.kill(0);
        full.update(0.15f, 1f);
        sub.update(0.15f, Reaction.SUBTLE);
        float f = full.intensity(Reaction.HALO) - 1, s = sub.intensity(Reaction.HALO) - 1;
        assertEquals(Reaction.SUBTLE, s / f, 0.02);
    }

    @Test
    @DisplayName("low health dims slightly and eases, never below 0.7")
    void lowHealthDim() {
        assertEquals(0f, Reaction.dimTarget(1f), 0);
        assertEquals(0f, Reaction.dimTarget(0.35f), 0);
        assertEquals(0.3f, Reaction.dimTarget(0f), 1e-6);
        Reaction r = new Reaction();
        r.update(0, 1f);
        r.health(0.05f);
        r.update(0.05f, 1f);
        float early = r.dim;
        float t = 0.05f;
        for (int i = 0; i < 240; i++) {
            t += 1 / 60f;
            r.update(t, 1f);
        }
        assertTrue(early < r.dim, "eases in, not a step");
        assertTrue(r.intensity(Reaction.CAPE) >= 0.7f && r.intensity(Reaction.CAPE) < 0.8f, "" + r.intensity(Reaction.CAPE));
    }

    @Test
    @DisplayName("update is idempotent within a frame")
    void idempotent() {
        Reaction r = new Reaction();
        r.update(0, 1f);
        r.landed(0);
        r.update(0.5f, 1f);
        float f = r.flare;
        r.update(0.5f, 1f);
        r.update(0.5f, 1f);
        assertEquals(f, r.flare, 0);
    }

    // ------------------------------------------------------------- public API

    @Test
    @DisplayName("the public API: intensity per uuid and slot, pulses to listeners, off silences")
    void publicApi() {
        final List<Reactive.Pulse> heard = new ArrayList<Reactive.Pulse>();
        Reactive.Listener l = new Reactive.Listener() {
            @Override
            public void onPulse(Reactive.Pulse p) {
                heard.add(p);
            }
        };
        Reactive.addListener(l);
        try {
            Reactive.devMotion(true, 1f);
            String u = "00000000-0000-0000-0000-00000000000a";
            assertEquals(1f, Reactive.intensity(u, "halo"), 0, "unknown player");
            assertEquals(1f, Reactive.intensity(u, "nonsense"), 0);
            Reactive.landed(u, false);
            Reactive.landed(u, false);
            Reactive.kill(u, false);
            assertEquals(3, heard.size());
            assertEquals(Reactive.Kind.HIT, heard.get(0).kind);
            assertEquals(2, heard.get(1).count, "the combo count");
            assertEquals(Reactive.Kind.KILL, heard.get(2).kind);
            assertTrue(Reactive.intensity(u, "halo") > 1f);
            Reactive.devMotion(true, 0f);
            Reactive.hurt(u, 1f, false);
            assertEquals(3, heard.size(), "off: no pulses");
        } finally {
            Reactive.removeListener(l);
        }
    }

    // ------------------------------------------------------------ attribution

    @Test
    @DisplayName("attribution: in reach, recent swing, facing the victim; nothing else")
    void attribution() {
        // Attacker looking +z (yaw 0) at a victim 3 blocks ahead.
        assertTrue(Reaction.attackScore(0, 0, 3, 0, 0, 1) > 0, "a clean hit");
        assertTrue(Reaction.attackScore(0, 0, 3, 180, 0, 1) < 0, "facing away");
        assertTrue(Reaction.attackScore(0, 0, 6, 0, 0, 1) < 0, "out of reach");
        assertTrue(Reaction.attackScore(0, 0, 3, 0, 0, 30) < 0, "the swing was long ago");
        assertTrue(Reaction.attackScore(0, 0, 3, 0, 0, -1) < 0, "never swung");
        // yaw 90 looks -x.
        assertTrue(Reaction.attackScore(-3, 0, 0, 90, 0, 1) > 0);
        // Closer and fresher scores higher.
        assertTrue(Reaction.attackScore(0, 0, 2, 0, 0, 1) > Reaction.attackScore(0, 0, 4, 0, 0, 1));
        assertTrue(Reaction.attackScore(0, 0, 3, 0, 0, 0) > Reaction.attackScore(0, 0, 3, 0, 0, 6));
    }

    // ------------------------------------------------------------------ cloth

    @Test
    @DisplayName("the lift limit: a cape may stream out behind, never fold over the head")
    void liftLimit() {
        // Hanging straight down, and streaming out level behind: untouched.
        double[] down = {0, 12, 2.35};
        assertTrue(!CapeRenderer.liftLimit(down, 0));
        double[] level = {0, 0.25, 14};
        assertTrue(!CapeRenderer.liftLimit(level, 0));
        // Straight up above the collar: swung down behind to the limit, same distance.
        double[] up = {1, -11.75, 2.36};
        assertTrue(CapeRenderer.liftLimit(up, 0));
        double dy = up[1] - 0.25, dz = up[2] - 2.35;
        double len = Math.sqrt(12 * 12 + 0.01 * 0.01);
        assertEquals(len, Math.sqrt(dy * dy + dz * dz), 1e-9);
        assertEquals(-0.2 * len, dy, 1e-9);
        assertEquals(1, up[0], 0, "x is kept");
        // Thrown forward over the head: brought down in front, below horizontal.
        double[] over = {0, -6, -4};
        assertTrue(CapeRenderer.liftLimit(over, 0));
        assertTrue(over[1] > 0.25 && over[2] < 2.35, "in front and below the collar");
    }

    private static Cloth hung() {
        Cloth c = new Cloth(6, 8, 0.06, 0.9);
        double[] ax = new double[7], ay = new double[7], az = new double[7];
        for (int i = 0; i <= 6; i++) {
            ax[i] = i * 0.06;
            ay[i] = 2;
        }
        c.advance(0, ax, ay, az, null, 0, 1);
        for (int i = 0; i < 240; i++) {
            c.advance(1 / 120.0, ax, ay, az, null, 0, 1);
        }
        return c;
    }

    @Test
    @DisplayName("a kick moves the hem, not the collar, and the sheet stays whole")
    void clothKick() {
        Cloth c = hung();
        double[] ax = new double[7], ay = new double[7], az = new double[7];
        for (int i = 0; i <= 6; i++) {
            ax[i] = i * 0.06;
            ay[i] = 2;
        }
        int hem = c.idx(3, c.rows), collar = c.idx(3, 0);
        double hz = c.z(hem), cz = c.z(collar);
        c.kick(0, 0, 3, 1.0);
        for (int i = 0; i < 12; i++) {
            c.advance(1 / 120.0, ax, ay, az, null, 0, 1);
        }
        assertTrue(c.z(hem) - hz > 0.05, "the hem flew out: " + (c.z(hem) - hz));
        assertEquals(cz, c.z(collar), 1e-9, "the collar is pinned");
        assertTrue(c.maxStretch() < 1.3, "still a sheet: " + c.maxStretch());
        c.kick(Double.NaN, 0, 0, 1);
        assertTrue(!Double.isNaN(c.z(hem)), "non-finite kicks are ignored");
    }

    @Test
    @DisplayName("a ripple moves the sheet and dies out on its own")
    void clothWave() {
        Cloth c = hung();
        double[] ax = new double[7], ay = new double[7], az = new double[7];
        for (int i = 0; i <= 6; i++) {
            ax[i] = i * 0.06;
            ay[i] = 2;
        }
        // Hang it a little off vertical so the sheet has a well-defined normal to push along.
        c.kick(0, 0, 1, 1.0);
        for (int i = 0; i < 240; i++) {
            c.advance(1 / 120.0, ax, ay, az, null, 0, 1);
        }
        int mid = c.idx(3, c.rows / 2);
        double before = c.z(mid);
        c.wave(75);
        double moved = 0;
        for (int i = 0; i < 60; i++) {
            c.advance(1 / 120.0, ax, ay, az, null, 0, 1);
            moved = Math.max(moved, Math.abs(c.z(mid) - before));
        }
        assertTrue(moved > 0.01, "the ripple moved the sheet: " + moved);
        for (int i = 0; i < 600; i++) {
            c.advance(1 / 120.0, ax, ay, az, null, 0, 1);
        }
        assertTrue(c.maxStretch() < 1.2);
        double settled = c.z(mid);
        for (int i = 0; i < 60; i++) {
            c.advance(1 / 120.0, ax, ay, az, null, 0, 1);
        }
        assertEquals(settled, c.z(mid), 0.01, "at rest again once the ripple has run");
    }
}
