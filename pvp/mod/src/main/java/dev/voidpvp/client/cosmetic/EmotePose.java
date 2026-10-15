package dev.voidpvp.client.cosmetic;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.entity.model.BiPedModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Puts a running emote onto the player model. Called from the tail of
 * {@code PlayerEntityModel.setAngles} — after vanilla has posed the limbs for walking, swinging and
 * sneaking, and after it has copied those angles onto the overlay layers — so the emote blends
 * from exactly what vanilla drew toward the clip by {@link Emotes.Active#weight}, and the overlay
 * layers (hat, jacket, sleeves, trousers) are re-copied afterwards so the second skin layer moves
 * with the first.
 *
 * <p>{@code upper} bends head, torso and both arms about the waist: their pivots swing round the
 * waist point and their pitch gains the same angle, which composes exactly for the pitch-only bend
 * the bow uses. {@code root} is a whole-body translation, applied in {@code setupTransforms} by
 * {@link #applyRoot} so the player's shadow, nametag and hitbox stay where the player stands.</p>
 */
public final class EmotePose {

    private static final float[] V = new float[3];
    private static final float WAIST_Y = 12f;

    private EmotePose() {
    }

    static String uuid(Entity e) {
        return e instanceof PlayerEntity ? EquippedSet.normalise(String.valueOf(e.getUuid())) : null;
    }

    /** The tail of {@code setAngles}. */
    public static void apply(PlayerEntityModel model, Entity entity) {
        String uuid = uuid(entity);
        if (uuid == null) {
            return;
        }
        Emotes.Active a = Emotes.get().of(uuid);
        if (a == null) {
            return;
        }
        long now = System.nanoTime();
        float w = a.weight(now);
        if (w <= 0.001f) {
            return;
        }
        float t = a.clip.clipTime(a.time(now));
        BiPedModel m = model;
        part(a.clip, EmoteClip.HEAD, m.head, t, w);
        part(a.clip, EmoteClip.BODY, m.body, t, w);
        part(a.clip, EmoteClip.RIGHT_ARM, m.rightArm, t, w);
        part(a.clip, EmoteClip.LEFT_ARM, m.leftArm, t, w);
        part(a.clip, EmoteClip.RIGHT_LEG, m.rightLeg, t, w);
        part(a.clip, EmoteClip.LEFT_LEG, m.leftLeg, t, w);
        if (a.clip.drivesRotation(EmoteClip.UPPER)) {
            a.clip.rotation(EmoteClip.UPPER, t, V, 0);
            float bend = V[0] * w;
            bend(m.head, bend);
            bend(m.body, bend);
            bend(m.rightArm, bend);
            bend(m.leftArm, bend);
        }
        copy(m.head, m.hat);
        copy(model.body, model.jacket);
        copy(model.rightArm, model.rightSleeve);
        copy(model.leftArm, model.leftSleeve);
        copy(model.rightLeg, model.rightPants);
        copy(model.leftLeg, model.leftPants);
    }

    private static void part(EmoteClip clip, int index, ModelPart p, float t, float w) {
        if (clip.drivesRotation(index)) {
            clip.rotation(index, t, V, 0);
            p.posX += (V[0] - p.posX) * w;
            p.posY += (V[1] - p.posY) * w;
            p.posZ += (V[2] - p.posZ) * w;
        }
        if (clip.drivesPosition(index)) {
            clip.position(index, t, V, 0);
            p.pivotX += V[0] * w;
            p.pivotY += V[1] * w;
            p.pivotZ += V[2] * w;
        }
    }

    private static void bend(ModelPart p, float angle) {
        float dy = p.pivotY - WAIST_Y, dz = p.pivotZ;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        p.pivotY = WAIST_Y + dy * c - dz * s;
        p.pivotZ = dy * s + dz * c;
        p.posX += angle;
    }

    private static void copy(ModelPart from, ModelPart to) {
        to.pivotX = from.pivotX;
        to.pivotY = from.pivotY;
        to.pivotZ = from.pivotZ;
        to.posX = from.posX;
        to.posY = from.posY;
        to.posZ = from.posZ;
    }

    /**
     * The emote's root offset in model pixels for a player now, into {@code out}; false (and
     * zeros) when none.
     */
    static boolean root(String uuid, float[] out) {
        out[0] = out[1] = out[2] = 0;
        Emotes.Active a = uuid == null ? null : Emotes.get().of(uuid);
        if (a == null || !a.clip.drivesPosition(EmoteClip.ROOT)) {
            return false;
        }
        long now = System.nanoTime();
        float w = a.weight(now);
        a.clip.position(EmoteClip.ROOT, a.clip.clipTime(a.time(now)), out, 0);
        out[0] *= w;
        out[1] *= w;
        out[2] *= w;
        return true;
    }

    private static final float[] ROOT = new float[3];

    /**
     * The tail of {@code PlayerEntityRenderer.setupTransforms}: translates the whole body by the
     * root channel. The matrix there is rotated to the body's yaw but not yet flipped and scaled,
     * so a model-space offset (pixels, y down) becomes (-x, -y, z) / 16 * 0.9375.
     */
    public static void applyRoot(Entity entity) {
        if (!root(uuid(entity), ROOT)) {
            return;
        }
        float k = 0.0625f * 0.9375f;
        GlStateManager.translate(-ROOT[0] * k, -ROOT[1] * k, ROOT[2] * k);
    }
}
