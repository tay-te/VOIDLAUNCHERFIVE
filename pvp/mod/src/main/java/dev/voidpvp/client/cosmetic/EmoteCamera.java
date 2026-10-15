package dev.voidpvp.client.cosmetic;

import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ClientPlayerEntity;

/**
 * The local emote's camera: when you emote, the view eases out of your head, swings round to face
 * you and settles into a slow third-person orbit; when the emote ends or you cancel it, it eases
 * back the same way and hands you your own perspective back exactly as you left it.
 *
 * <p>Everything is a blend {@link #blend} in [0, 1] stepped by real frame time through a
 * {@link Spring}, applied where freelook already reroutes the camera angles
 * ({@code GameRendererMixin} reads yaw and pitch through {@link #yaw}/{@link #pitch}, the
 * third-person distance through {@link #distance}). The perspective itself is a session field
 * that never reaches {@code options.txt} (see {@code VoidClient.savedPerspective}), so forcing it
 * to third person for the length of an emote cannot leak into the player's settings.</p>
 *
 * <p>Cancelling is the player's: any movement key, jump, sneak, attack or use fades the emote out
 * ({@link #tick}). The first {@link #GRACE_NANOS} are exempt so the click that started it does
 * not also stop it.</p>
 */
public final class EmoteCamera {

    private static final long GRACE_NANOS = 250_000_000L;

    private static final Spring BLEND = new Spring(5.0f, 0f);
    private static float blend;
    private static boolean engaged;
    private static Integer savedPerspective;
    private static float startDistance = 0.4f;
    private static long startNanos;
    private static long lastFrameNanos;
    private static boolean low;
    private static String engagedFor;

    private EmoteCamera() {
    }

    /** Game thread, once per client tick: engage, cancel, release. */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        Emotes.Active local = Emotes.get().local();
        boolean want = local != null && !local.cancelled() && player != null
                && LiveState.get().cosmeticsEmoteCamera && mc.currentScreen == null;
        if (local != null && !local.cancelled() && player != null
                && System.nanoTime() - local.startNanos > GRACE_NANOS && wantsToMove(mc, player)) {
            Emotes.get().cancelLocal();
            want = false;
        }
        if (want && !engaged) {
            engaged = true;
            engagedFor = local.id;
            startNanos = System.nanoTime();
            low = "low".equals(local.clip.camera);
            savedPerspective = Integer.valueOf(mc.options.perspective);
            startDistance = mc.options.perspective == 0 ? 0.35f : 4.0f;
            mc.options.perspective = 1;
        } else if (want && engaged && local != null && !local.id.equals(engagedFor)) {
            engagedFor = local.id;
            low = "low".equals(local.clip.camera);
        }
        if (!want) {
            engaged = false;
        }
        if (!engaged && savedPerspective != null && blend < 0.02f) {
            // Hand the perspective back — unless the player changed it themselves meanwhile.
            if (mc.options.perspective == 1) {
                mc.options.perspective = savedPerspective.intValue();
            }
            savedPerspective = null;
            BLEND.snap(0);
            blend = 0;
        }
    }

    private static boolean wantsToMove(MinecraftClient mc, ClientPlayerEntity p) {
        if (p.input != null && (Math.abs(p.input.movementForward) > 0.01f
                || Math.abs(p.input.movementSideways) > 0.01f || p.input.jumping || p.input.sneaking)) {
            return true;
        }
        return mc.options.attackKey.isPressed() || mc.options.useKey.isPressed();
    }

    /** Render thread, once per frame, before the camera is set up. */
    public static void frame() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1e9f;
        lastFrameNanos = now;
        blend = Math.max(0, Math.min(1, BLEND.step(engaged ? 1f : 0f, dt)));
    }

    public static boolean active() {
        return blend > 0.001f && savedPerspective != null;
    }

    private static boolean owns(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return active() && e != null && e == mc.getCameraEntity() && e == mc.player;
    }

    private static float eased() {
        return Keyframes.smooth(blend);
    }

    private static float orbit() {
        float t = (System.nanoTime() - startNanos) / 1e9f;
        return (float) Math.sin(t * 0.32) * 24f;
    }

    public static float yaw(Entity e, float vanilla) {
        if (!owns(e)) {
            return vanilla;
        }
        return vanilla + (180f + orbit()) * eased();
    }

    public static float pitch(Entity e, float vanilla) {
        if (!owns(e)) {
            return vanilla;
        }
        float target = low ? 4f : 12f;
        return vanilla + (target - vanilla) * eased();
    }

    /** The third-person distance, eased from where the view started to the orbit's radius. */
    public static float distance(float vanilla) {
        if (!active()) {
            return vanilla;
        }
        float target = low ? 2.8f : 3.4f;
        return startDistance + (target - startDistance) * eased();
    }
}
