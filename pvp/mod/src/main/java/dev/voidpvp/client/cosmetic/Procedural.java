package dev.voidpvp.client.cosmetic;

import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Motion that answers to what the wearer is doing, layered on top of a model's keyframed idle:
 *
 * <ul>
 *   <li>{@code wings} — breathe at rest; sweep back and tuck in at a sprint; spread wide and glide
 *       with a tip flutter while falling; beat while rising or flying; fold tight while sneaking.
 *       Every target eases through a {@link Spring}, so a jump is a gesture, not a cut.</li>
 *   <li>{@code halo} — rises and settles slowly, and trails a little when the wearer runs.</li>
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
            float lag = Math.min(1f, fx.speed.value / 6f);
            for (int r : m.roots) {
                pose.pos[r * 3 + 1] += bob;
                pose.pos[r * 3 + 2] += lag * 0.8f;
                pose.rot[r * 3] += -lag * 0.12f;
            }
        } else if ("float".equals(m.procedural)) {
            float bob = (float) Math.sin(time * 1.1) * 0.5f;
            float lag = Math.min(1f, fx.speed.value / 6f);
            for (int r : m.roots) {
                pose.pos[r * 3 + 1] += bob + lag * 0.6f;
                pose.pos[r * 3 + 2] += lag * 1.6f;
                pose.rot[r * 3] += lag * 0.18f;
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
        float rootY = (15f + 60f * w) * DEG;
        float rootZ = (-55f + 70f * s + flap) * DEG;
        float rootX = (-6f * s) * DEG;
        float tipY = 85f * t * DEG;
        float tipZ = (18f * s + tipFlap) * DEG;
        set(m, pose, "wing_r", rootX, rootY, rootZ);
        set(m, pose, "wing_l", rootX, -rootY, -rootZ);
        set(m, pose, "wing_r_tip", 0, tipY, tipZ);
        set(m, pose, "wing_l_tip", 0, -tipY, -tipZ);
    }

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
