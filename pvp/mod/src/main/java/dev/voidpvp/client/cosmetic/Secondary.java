package dev.voidpvp.client.cosmetic;

import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Secondary motion: everything a worn thing does because the body under it moved, layered on top
 * of the primary pose (the item attached rigidly to its bone) and the reactions ({@link Reaction}).
 *
 * <ul>
 *   <li><b>Hats</b> ride the head on an underdamped spring: a snap turn leaves the brim a beat
 *       behind and it rocks once and settles; a landing presses it down and it bounces back.</li>
 *   <li><b>Halos</b> float: their position follows the head through a looser spring, so they
 *       trail when you run and drift back when you stop, tilt like a pendulum against the lag,
 *       wobble when you are hit, pulse on a kill and spin up with a combo.</li>
 *   <li><b>Landings</b> squash everything for a moment, in proportion to the fall: wings dip,
 *       halos sink, hats compress, the cape drops and billows.</li>
 *   <li><b>Take-offs</b>: a jump gives the wings a downstroke; a sprint-jump lifts the cape.</li>
 *   <li><b>The cape</b> gets the body's acceleration exaggerated into it (a whip on every change
 *       of direction, a flare on a sharp turn) and one-shot kicks for hits, landings and kills.</li>
 * </ul>
 *
 * Every spring steps with the shared frame time ({@link #frameDt}) through exact integration, so
 * the motion is the same at any frame rate; inputs are interpolated positions and angles, so it is
 * smooth between ticks. With {@code on} false (beyond the mid LOD band, or the inventory preview)
 * outputs are zero and the springs snap to the body, so nothing jumps when it comes back.
 */
final class Secondary {

    /** Seconds since the previous rendered frame, shared by every player (set in frameStart). */
    static float frameDt;
    private static long lastFrame;
    /** Dev A/B switch (the showcase's "before" takes): false rests every spring. */
    static boolean enabled = true;

    static void frame() {
        long now = System.nanoTime();
        frameDt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
    }

    // Spring kinds, shared so their coefficients are computed once per frame.
    static final DampedSpring.Params HAT = new DampedSpring.Params(13f, 0.28f);
    static final DampedSpring.Params HAT_Y = new DampedSpring.Params(17f, 0.32f);
    static final DampedSpring.Params HALO = new DampedSpring.Params(8.5f, 0.42f);
    static final DampedSpring.Params WOBBLE = new DampedSpring.Params(15f, 0.12f);
    static final DampedSpring.Params LAND = new DampedSpring.Params(15f, 0.36f);
    static final DampedSpring.Params LIFT = new DampedSpring.Params(7f, 0.55f);
    static final DampedSpring.Params SMOOTH = new DampedSpring.Params(22f, 1f);
    /** Feather layers, inner to outer: looser and later further out. */
    static final DampedSpring.Params[] LAYER = {
        new DampedSpring.Params(30f, 0.55f), new DampedSpring.Params(22f, 0.5f),
        new DampedSpring.Params(18f, 0.46f), new DampedSpring.Params(13f, 0.42f)};
    static final DampedSpring.Params TIP = new DampedSpring.Params(16f, 0.45f);

    private static final float PX = 16f / 0.9375f;
    private static final float DEG = 0.017453292f;

    // ---- head
    private float yawU, lastYaw;
    private final DampedSpring hatYaw = new DampedSpring(HAT, 0);
    private final DampedSpring hatPitch = new DampedSpring(HAT, 0);
    private final DampedSpring hatY = new DampedSpring(HAT_Y, 0);
    // ---- halo
    private final DampedSpring haloX = new DampedSpring(HALO, 0);
    private final DampedSpring haloY = new DampedSpring(HALO, 0);
    private final DampedSpring haloZ = new DampedSpring(HALO, 0);
    private final DampedSpring wobX = new DampedSpring(WOBBLE, 0);
    private final DampedSpring wobZ = new DampedSpring(WOBBLE, 0);
    private float spin;
    // ---- body
    private final DampedSpring land = new DampedSpring(LAND, 0);
    private final DampedSpring lift = new DampedSpring(LIFT, 0);
    private final DampedSpring vx = new DampedSpring(SMOOTH, 0);
    private final DampedSpring vy = new DampedSpring(SMOOTH, 0);
    private final DampedSpring vz = new DampedSpring(SMOOTH, 0);
    private final DampedSpring yawRate = new DampedSpring(SMOOTH, 0);
    private double lx, ly, lz;
    private float lastBodyYaw;
    private boolean wasOnGround = true;
    private float fall;
    private boolean fresh = true;
    private long lastNanos;
    private int hurtSeen, hitSeen, killSeen;
    // ---- wings
    final DampedSpring[] layers = {new DampedSpring(LAYER[0], 0), new DampedSpring(LAYER[1], 0),
        new DampedSpring(LAYER[2], 0), new DampedSpring(LAYER[3], 0)};
    final DampedSpring tip = new DampedSpring(TIP, 0);
    boolean wingsFresh = true;

    // ---- outputs (radians, model pixels)
    boolean on;
    float hatRoll, hatPitchOut, hatLift, hatSquash;
    float haloOx, haloOy, haloOz, haloTiltX, haloTiltZ, haloScale = 1f, haloSpin;
    /** 0..1: how compressed a landing has everything right now. */
    float squash;
    /** 0..1: the take-off downstroke. */
    float beat;
    /** World-space velocity change for the cape this frame, blocks/s, hem-weighted. */
    double capeKickX, capeKickY, capeKickZ;
    /** Model-space one-shot kick for the cape (blocks/s): +z away from the back, -y up. */
    double capeShotX, capeShotY, capeShotZ;
    /** A kill ripple to start on the cape this frame (its strength), or 0. */
    float capeWave;

    /**
     * Advances everything for this frame. {@code r} may be null (nothing has happened to this
     * player); {@code dt} is {@link #frameDt}.
     */
    void update(AbstractClientPlayerEntity p, float td, float dt, boolean active, Reaction r) {
        capeKickX = capeKickY = capeKickZ = 0;
        capeShotX = capeShotY = capeShotZ = 0;
        capeWave = 0;
        double x = p.prevX + (p.x - p.prevX) * td;
        double y = p.prevY + (p.y - p.prevY) * td;
        double z = p.prevZ + (p.z - p.prevZ) * td;
        float headYaw = p.prevHeadYaw + wrap(p.headYaw - p.prevHeadYaw) * td;
        float pitch = p.prevPitch + (p.pitch - p.prevPitch) * td;
        float bodyYaw = p.prevBodyYaw + wrap(p.bodyYaw - p.prevBodyYaw) * td;
        long nowN = System.nanoTime();
        if (nowN - lastNanos > 250_000_000L) {
            // Not drawn for a while (first person, off screen): start from the body again.
            fresh = true;
        }
        lastNanos = nowN;
        if (!active || !enabled) {
            fresh = true;
            on = false;
            clearOutputs();
            return;
        }
        if (fresh) {
            rest(x, y, z, headYaw, pitch, bodyYaw, p.onGround, r);
            fresh = false;
            on = true;
            clearOutputs();
            return;
        }
        on = true;
        if (dt <= 0) {
            return;
        }
        float flinch = r == null ? 0 : r.flinch;
        float flare = r == null ? 0 : r.flare;
        float step = r == null ? 0 : r.step;
        float flourish = r == null ? 0 : r.flourish;

        // -------------------------------------------------------------- velocities
        double rvx = (x - lx) / dt, rvy = (y - ly) / dt, rvz = (z - lz) / dt;
        if (rvx * rvx + rvy * rvy + rvz * rvz > 900) {
            // A teleport: start again rather than fling everything.
            rest(x, y, z, headYaw, pitch, bodyYaw, p.onGround, r);
            clearOutputs();
            return;
        }
        float pvx = vx.value, pvy = vy.value, pvz = vz.value;
        vx.step((float) rvx, dt);
        vy.step((float) rvy, dt);
        vz.step((float) rvz, dt);
        float ax = (vx.value - pvx) / dt, ay = (vy.value - pvy) / dt, az = (vz.value - pvz) / dt;
        lx = x;
        ly = y;
        lz = z;

        // -------------------------------------------------------------- landing / take-off
        boolean onGround = p.onGround;
        if (!onGround) {
            fall = Math.max(fall, (float) -rvy);
        }
        if (onGround && !wasOnGround) {
            float s = clamp((fall - 3f) / 10f, 0f, 1f);
            if (s > 0) {
                land.kick(-s * 9f);
                // The cape keeps falling, then air under it billows it out.
                capeShotY += 1.2 * s;
                capeShotZ += 1.0 * s;
            }
            fall = 0;
        } else if (!onGround && wasOnGround && rvy > 1.5) {
            float horiz = (float) Math.sqrt(rvx * rvx + rvz * rvz);
            boolean sprint = horiz > 4.6f;
            lift.kick(sprint ? 7f : 4f);
            if (sprint) {
                // A sprint-jump: the cape lifts and streams.
                capeShotY -= 1.6;
                capeShotZ += 1.4;
            }
        }
        if (onGround) {
            fall = 0;
        }
        wasOnGround = onGround;
        land.step(0, dt);
        lift.step(0, dt);
        squash = clamp(-land.value, 0f, 1f);
        beat = clamp(lift.value, 0f, 1f);

        // -------------------------------------------------------------- hats
        yawU += wrap(headYaw - lastYaw);
        lastYaw = headYaw;
        hatYaw.step(yawU, dt);
        hatPitch.step(pitch, dt);
        hatY.step((float) y, dt);
        float yawLag = hatYaw.value - yawU;
        float pitchLag = hatPitch.value - pitch;
        float yLag = (hatY.value - (float) y) * PX;
        hatRoll = soft(yawLag * 0.11f, 9f) * DEG;
        hatPitchOut = soft(pitchLag * 0.14f - yLag * 0.9f, 10f) * DEG;
        hatLift = -clamp(yLag * 0.35f, 0f, 1.3f);
        hatSquash = clamp(-yLag * 0.03f, 0f, 0.08f) + 0.07f * squash;

        // -------------------------------------------------------------- halo
        haloX.step((float) x, dt);
        haloY.step((float) y, dt);
        haloZ.step((float) z, dt);
        float wx = (float) (haloX.value - x), wy = (float) (haloY.value - y), wz = (float) (haloZ.value - z);
        // World offset into the halo's frame (it turns with the head's yaw).
        double th = Math.toRadians(180.0 - headYaw);
        float c = (float) Math.cos(th), s = (float) Math.sin(th);
        float mx = -(wx * c - wz * s) * PX, mz = (wx * s + wz * c) * PX, my = -wy * PX;
        haloOx = soft(mx, 3.5f);
        haloOz = soft(mz, 3.5f);
        haloOy = soft(my, 2.5f) + 1.6f * squash - 0.8f * beat;
        wobX.step(0, dt);
        wobZ.step(0, dt);
        haloTiltX = haloOz * 0.07f + wobX.value;
        haloTiltZ = -haloOx * 0.07f + wobZ.value;
        spin += dt * (2.2f * flare + 7f * flourish);
        if (spin > 6.2831855f) {
            spin -= 6.2831855f;
        }
        haloSpin = spin;
        haloScale = 1f + 0.2f * flourish + 0.05f * step - 0.06f * squash;

        // -------------------------------------------------------------- one-shot reactions
        if (r != null) {
            if (r.hurtSerial != hurtSeen) {
                hurtSeen = r.hurtSerial;
                float k = r.level * r.hurtStrength;
                float side = (hurtSeen & 1) == 0 ? 1f : -1f;
                wobX.kick(-4.2f * k);
                wobZ.kick(3.4f * side * k);
                hatYaw.kick(700f * side * k);
                hatPitch.kick(-500f * k);
                capeShotX += 1.4 * side * k;
                capeShotY -= 0.6 * k;
                capeShotZ += 2.4 * k;
            }
            if (r.hitSerial != hitSeen) {
                hitSeen = r.hitSerial;
                float k = r.level * (0.5f + 0.5f * Math.min(1f, r.combo / (float) Reaction.COMBO_FULL));
                capeShotZ += 0.9 * k;
                capeShotY -= 0.4 * k;
            }
            if (r.killSerial != killSeen) {
                killSeen = r.killSerial;
                capeWave = r.level * r.killStrength;
                capeShotZ += 1.5 * capeWave;
                capeShotY -= 1.2 * capeWave;
                wobX.kick(3f * capeWave);
            }
        }

        // -------------------------------------------------------------- cape inertia
        // The body's acceleration, exaggerated into the sheet: the cloth already lags the
        // anchors, this makes it whip. A flare outward on sharp turns (centrifugal on the hem).
        float g = 0.25f;
        capeKickX = -ax * g * dt;
        capeKickY = -Math.min(0f, ay) * 0.15f * dt;
        capeKickZ = -az * g * dt;
        float pRate = yawRate.value;
        yawRate.step(wrap(bodyYaw - lastBodyYaw) / dt, dt);
        lastBodyYaw = bodyYaw;
        float w = yawRate.value * DEG;
        float out = Math.min(14f, w * w * 0.4f * 0.3f) * dt;
        float turnAcc = (yawRate.value - pRate) / dt * DEG;
        capeShotZ += out;
        capeShotX += clamp(turnAcc * 0.4f * 0.12f, -40f, 40f) * dt;
        // Flinch reads in the wings and halo; with it the tremor on everything, kept small.
        if (flinch > 0.05f) {
            hatSquash += 0.02f * flinch;
        }
    }

    private void rest(double x, double y, double z, float headYaw, float pitch, float bodyYaw,
                      boolean onGround, Reaction r) {
        lx = x;
        ly = y;
        lz = z;
        lastYaw = headYaw;
        yawU = headYaw;
        lastBodyYaw = bodyYaw;
        hatYaw.snap(yawU);
        hatPitch.snap(pitch);
        hatY.snap((float) y);
        haloX.snap((float) x);
        haloY.snap((float) y);
        haloZ.snap((float) z);
        wobX.snap(0);
        wobZ.snap(0);
        land.snap(0);
        lift.snap(0);
        vx.snap(0);
        vy.snap(0);
        vz.snap(0);
        yawRate.snap(0);
        wasOnGround = onGround;
        fall = 0;
        wingsFresh = true;
        if (r != null) {
            // Events from while this player was out of range are not replayed on return.
            hurtSeen = r.hurtSerial;
            hitSeen = r.hitSerial;
            killSeen = r.killSerial;
        }
    }

    private void clearOutputs() {
        hatRoll = hatPitchOut = hatLift = hatSquash = 0;
        haloOx = haloOy = haloOz = haloTiltX = haloTiltZ = 0;
        haloScale = 1f;
        haloSpin = spin;
        squash = beat = 0;
    }

    /** A soft limit: linear near zero, approaching {@code limit} asymptotically. */
    static float soft(float x, float limit) {
        return x / (1f + Math.abs(x) / limit);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) {
            deg -= 360f;
        }
        if (deg < -180f) {
            deg += 360f;
        }
        return deg;
    }
}
