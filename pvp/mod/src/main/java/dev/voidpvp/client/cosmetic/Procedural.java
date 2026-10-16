package dev.voidpvp.client.cosmetic;

import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Motion that answers to what the wearer is doing, layered on top of a model's keyframed idle:
 *
 * <ul>
 *   <li>{@code wings} — breathe at rest; sweep back and tuck in at a sprint; spread wide and glide
 *       with a tip flutter while falling; beat while rising or flying; fold tight while sneaking.
 *       Every target eases through a {@link Spring}, so a jump is a gesture, not a cut.</li>
 *   <li>{@code halo} — floats on its own spring ({@link Secondary}): trails the head, tilts
 *       against the lag, wobbles on a hit, spins up with a combo, pulses on a kill.</li>
 *   <li>{@code float} — a back item hovering behind the wearer: bobs, and lags out behind at
 *       speed.</li>
 * </ul>
 *
 * Bone names are the contract with the painter: {@code wing_r}, {@code wing_r_tip},
 * {@code wing_l}, {@code wing_l_tip}; the right side is model -x.
 */
final class Procedural {

    private static final float DEG = 0.017453292f;

    private Procedural() {
    }

    static void apply(CosmeticModel m, CosmeticModel.Pose pose, PlayerFx fx,
                      AbstractClientPlayerEntity p, float time, float dt) {
        if (m.procedural == null) {
            return;
        }
        if ("wings".equals(m.procedural)) {
            wings(m, pose, fx, p, time, dt);
        } else if ("halo".equals(m.procedural)) {
            float bob = (float) Math.sin(time * 1.3) * 0.55f;
            Secondary sec = fx.sec;
            if (sec.on) {
                // Floating on its own spring (Secondary): trails the head, tilts against the lag,
                // wobbles, spins up with a combo and pulses on a kill.
                for (int r : m.roots) {
                    pose.pos[r * 3] += sec.haloOx;
                    pose.pos[r * 3 + 1] += bob + sec.haloOy;
                    pose.pos[r * 3 + 2] += sec.haloOz;
                    pose.rot[r * 3] += sec.haloTiltX;
                    pose.rot[r * 3 + 2] += sec.haloTiltZ;
                    pose.scale[r * 3] *= sec.haloScale;
                    pose.scale[r * 3 + 1] *= sec.haloScale;
                    pose.scale[r * 3 + 2] *= sec.haloScale;
                }
                int ring = m.boneIndex("ring");
                if (ring >= 0) {
                    pose.rot[ring * 3 + 1] += sec.haloSpin;
                }
                int motes = m.boneIndex("motes");
                if (motes >= 0) {
                    pose.rot[motes * 3 + 1] -= sec.haloSpin * 0.6f;
                }
            } else {
                float lag = Math.min(1f, fx.speed.value / 6f);
                for (int r : m.roots) {
                    pose.pos[r * 3 + 1] += bob;
                    pose.pos[r * 3 + 2] += lag * 0.8f;
                    pose.rot[r * 3] += -lag * 0.12f;
                }
            }
        } else if ("float".equals(m.procedural)) {
            float bob = (float) Math.sin(time * 1.1) * 0.5f;
            Secondary sec = fx.sec;
            float lag = Math.min(1f, fx.speed.value / 6f);
            for (int r : m.roots) {
                if (sec.on) {
                    // The halo's follower, a little looser: it hangs further from the body.
                    pose.pos[r * 3] += sec.haloOx * 1.2f;
                    pose.pos[r * 3 + 1] += bob + sec.haloOy * 1.2f + lag * 0.6f;
                    pose.pos[r * 3 + 2] += sec.haloOz * 0.5f + lag * 1.2f;
                    pose.rot[r * 3] += lag * 0.18f + sec.haloTiltX * 0.8f;
                    pose.rot[r * 3 + 2] += sec.haloTiltZ * 0.8f;
                    float k = sec.haloScale;
                    pose.scale[r * 3] *= k;
                    pose.scale[r * 3 + 1] *= k;
                    pose.scale[r * 3 + 2] *= k;
                } else {
                    pose.pos[r * 3 + 1] += bob + lag * 0.6f;
                    pose.pos[r * 3 + 2] += lag * 1.6f;
                    pose.rot[r * 3] += lag * 0.18f;
                }
            }
            if (sec.on) {
                int g = m.boneIndex("gimbal");
                if (g >= 0) {
                    pose.rot[g * 3 + 1] += sec.haloSpin;
                }
            }
        }
    }

    private static void wings(CosmeticModel m, CosmeticModel.Pose pose, PlayerFx fx,
                              AbstractClientPlayerEntity p, float time, float dt) {
        float speed = fx.speed.value;
        float vy = fx.vy.value;
        boolean flying = p.abilities != null && p.abilities.flying;
        float spread, sweep, amp, rate, tip;
        if (p.isSneaking()) {
            spread = 0f; sweep = 0.95f; amp = 0f; rate = 0.4f; tip = 1f;
        } else if (flying) {
            spread = 0.95f; sweep = 0.1f; amp = 0.4f; rate = 1.5f; tip = 0.1f;
        } else if (!p.onGround && vy < -3.5f) {
            spread = 1f; sweep = 0.05f; amp = 0.05f; rate = 2.6f; tip = 0f;
        } else if (!p.onGround && vy > 1.0f) {
            spread = 0.75f; sweep = 0.2f; amp = 0.55f; rate = 2.0f; tip = 0.15f;
        } else if (speed > 4.8f) {
            spread = 0.35f; sweep = 0.7f; amp = 0.05f; rate = 1.6f; tip = 0.5f;
        } else if (speed > 1f) {
            spread = 0.65f; sweep = 0.3f; amp = 0.07f; rate = 1.0f; tip = 0.25f;
        } else {
            // At rest the wings stay half open and breathe: they were bought to be seen.
            spread = 0.8f; sweep = 0.15f; amp = 0.06f; rate = 0.42f; tip = 0.12f;
        }
        // Reactions (Reaction): a combo flares the wings open and quickens the beat, a kill gives
        // one big full-spread beat, being hit flinches them inward and folds the tips.
        Reaction rx = fx.react;
        Secondary sec = fx.sec;
        float flinch = rx == null ? 0 : rx.flinch;
        float flare = rx == null ? 0 : rx.flare;
        float hit = rx == null ? 0 : rx.step;
        float flourish = rx == null ? 0 : rx.flourish;
        if (!p.isSneaking()) {
            // Toward fully open and forward whatever the wearer is doing: a combo at a sprint
            // still reads as wings thrown wide, not swept back.
            float open = Math.min(1f, Math.max(0.85f * flare + 0.15f * hit, flourish));
            spread += (1f - spread) * open;
            sweep *= 1f - 0.85f * open;
            amp += 0.1f * flare + 0.4f * flourish;
            rate += 0.5f * flare + 1.0f * flourish;
            tip *= 1f - open;
        }
        spread = Math.max(0f, spread - 0.6f * flinch);
        tip = Math.min(1f, tip + 0.5f * flinch);
        float s = fx.spread.step(spread, dt);
        float w = fx.sweep.step(sweep, dt);
        float a = fx.flapAmp.step(amp, dt);
        float t = fx.tipFold.step(tip, dt);
        fx.flapRate += (rate - fx.flapRate) * Math.min(1f, dt * 3f);
        fx.flapPhase += dt * fx.flapRate * (float) (Math.PI * 2);
        if (fx.flapPhase > Math.PI * 2000) {
            fx.flapPhase -= (float) (Math.PI * 2000);
        }
        float flap = a * (float) Math.sin(fx.flapPhase) * 55f;
        float tipFlap = a * (float) Math.sin(fx.flapPhase - 0.9f) * 35f;
        // A flinch snaps the wings back and down at once (the state springs are too slow for
        // a quarter-second reaction); the follow-through springs below give it its shudder.
        float rootY = (15f + 60f * w + 28f * flinch) * DEG;
        float squash = sec.on ? sec.squash : 0f;
        float beat = sec.on ? sec.beat : 0f;
        float rootZDeg = -55f + 70f * s + flap
                + 16f * hit - 24f * squash - 34f * beat - 34f * flinch
                + (flinch > 0.02f ? (float) Math.sin(time * 47f) * 6f * flinch : 0f);
        float rootZ = rootZDeg * DEG;
        float rootX = (-6f * s) * DEG;
        float tipY = 85f * t * DEG;
        // Folding brings the hand down along the arm rather than leaving it raised.
        float tipZ = (8f * s - 46f * (1 - s) * t + tipFlap) * DEG;
        // Follow-through: the hand and each feather layer chase the arm on their own looser,
        // later springs, so a beat or a sudden flinch rolls out along the wing and overshoots a
        // touch before it settles. Same frame time for everyone (Secondary.frameDt).
        float[] ft = FOLLOW;
        if (sec.on) {
            float fdt = Secondary.frameDt;
            if (sec.wingsFresh) {
                for (DampedSpring l : sec.layers) {
                    l.snap(rootZDeg);
                }
                sec.tip.snap(rootZDeg);
                sec.wingsFresh = false;
            }
            for (int k = 0; k < 4; k++) {
                ft[k] = (sec.layers[k].step(rootZDeg, fdt) - rootZDeg) * FOLLOW_GAIN[k] * DEG;
            }
            tipZ += (sec.tip.step(rootZDeg, fdt) - rootZDeg) * 0.75f * DEG;
        } else {
            ft[0] = ft[1] = ft[2] = ft[3] = 0f;
        }
        set(m, pose, "wing_r", rootX, rootY, rootZ);
        set(m, pose, "wing_l", rootX, -rootY, -rootZ);
        set(m, pose, "wing_r_tip", 0, tipY, tipZ);
        set(m, pose, "wing_l_tip", 0, -tipY, -tipZ);
        // Feather layers follow the beat a little late, each further out a little later, and
        // the long primaries trail and twist the most: the flap rolls through the wing instead
        // of moving it as one board.
        int[] idx = layerBones(m);
        for (int k = 0; k < LAYERS.length; k++) {
            float lagged = a * (float) Math.sin(fx.flapPhase - LAG[k]) * GAIN[k];
            float twist = LAYER_TWIST[k] * (s - 0.5f) * DEG;
            add(pose, idx[k * 2], twist, 0, lagged * DEG + ft[k]);
            add(pose, idx[k * 2 + 1], twist, 0, -lagged * DEG - ft[k]);
        }
        // Void fingers fan open as the wing spreads and close as it folds.
        for (int k = 1; k <= 3; k++) {
            float fan = (1 - s) * (k - 2) * -9f * DEG + a * (float) Math.sin(fx.flapPhase - 0.6f - k * 0.2f) * 6f * DEG;
            add(pose, idx[8 + (k - 1) * 2], 0, 0, fan);
            add(pose, idx[8 + (k - 1) * 2 + 1], 0, 0, -fan);
        }
    }

    private static final String[] LAYERS = {"cov", "sec", "pcov", "pri"};
    private static final java.util.Map<CosmeticModel, int[]> LAYER_BONES =
            new java.util.IdentityHashMap<CosmeticModel, int[]>();

    /** Bone indices of the feather layers and fingers (r, l pairs), -1 where a model has none. */
    private static int[] layerBones(CosmeticModel m) {
        int[] idx = LAYER_BONES.get(m);
        if (idx == null) {
            idx = new int[14];
            for (int k = 0; k < LAYERS.length; k++) {
                idx[k * 2] = m.boneIndex("wing_r_" + LAYERS[k]);
                idx[k * 2 + 1] = m.boneIndex("wing_l_" + LAYERS[k]);
            }
            for (int k = 1; k <= 3; k++) {
                idx[8 + (k - 1) * 2] = m.boneIndex("wing_r_f" + k);
                idx[8 + (k - 1) * 2 + 1] = m.boneIndex("wing_l_f" + k);
            }
            LAYER_BONES.put(m, idx);
        }
        return idx;
    }

    private static void add(CosmeticModel.Pose pose, int b, float x, float y, float z) {
        if (b < 0) {
            return;
        }
        pose.rot[b * 3] += x;
        pose.rot[b * 3 + 1] += y;
        pose.rot[b * 3 + 2] += z;
    }
    private static final float[] LAG = {0.35f, 0.7f, 0.9f, 1.25f};
    /** How much of each layer's lag behind the arm shows: the primaries trail the most. */
    private static final float[] FOLLOW_GAIN = {0.5f, 0.8f, 0.8f, 1.15f};
    /** Scratch for the follow-through per layer (render thread only). */
    private static final float[] FOLLOW = new float[4];
    private static final float[] GAIN = {8f, 14f, 10f, 20f};
    private static final float[] LAYER_TWIST = {0f, 4f, 0f, 9f};

    private static void set(CosmeticModel m, CosmeticModel.Pose pose, String bone, float x, float y, float z) {
        int b = m.boneIndex(bone);
        if (b < 0) {
            return;
        }
        pose.rot[b * 3] += x;
        pose.rot[b * 3 + 1] += y;
        pose.rot[b * 3 + 2] += z;
    }
}
