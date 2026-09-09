package dev.voidpvp.client.mixin;

import dev.voidpvp.client.HiDpi;
import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.input.Mouse;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Zoom actuator (§6.7): the player's FOV is divided while the zoom key is
 * held. The easing lives in {@code ZoomController}; this only multiplies.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov(FZ)F", at = @At("RETURN"), cancellable = true)
    private void void$zoomFov(float tickDelta, boolean changingFov,
                              CallbackInfoReturnable<Float> cir) {
        VoidClient client = VoidClient.get();
        if (client == null) {
            return;
        }
        double factor = client.zoomFactor();
        if (Math.abs(factor - 1.0) < 0.0005) {
            return;
        }
        cir.setReturnValue(Float.valueOf((float) (cir.getReturnValueF() * factor)));
    }

    /**
     * The hover half of Retina support (§13). The render path builds the mouse position it hands
     * to {@code Screen.render} the same way {@code Screen.handleMouse} builds the click position —
     * {@code Mouse.getX() * scaledWidth / mc.width} — so it needs the same correction, or the
     * pointer draws and highlights in a different place from where it clicks.
     */
    @Redirect(method = "render(FJ)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Mouse;getX()I"))
    private int void$mouseXInPixels() {
        return HiDpi.toPixels(Mouse.getX());
    }

    @Redirect(method = "render(FJ)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Mouse;getY()I"))
    private int void$mouseYInPixels() {
        return HiDpi.toPixels(Mouse.getY());
    }

    /**
     * {@code zoom.sensitivity} — the look step, scaled while the zoom is engaged (§6.7).
     *
     * <p><b>The injection point was read, not remembered.</b> {@code GameOptions.sensitivity} is
     * referenced from exactly two classes in 1.8.9 — {@code GameOptions} itself and this one —
     * and from two methods here: {@code tick()} and {@code render(FJ)V}. The one that matters is
     * in {@code render}, at offsets 161-193:</p>
     *
     * <pre>
     *   f  = options.sensitivity * 0.6F + 0.2F;
     *   f1 = f * f * f * 8.0F;
     *   dx = mouse.x * f1;  dy = mouse.y * f1;
     *   player.increaseTransforms(dx, dy * invert);
     * </pre>
     *
     * <p>So this is the single value the whole look step is built from, and scaling it here is
     * the same arithmetic vanilla does with a smaller number — which is why the setting is a
     * fraction of the <em>setting</em> rather than of the resulting angle. Scoping the redirect
     * to {@code render(FJ)V} is what keeps the read in {@code tick()} alone; a class-wide field
     * redirect would have taken both, and the one in {@code tick} is the smooth-camera
     * accumulator, where scaling it would make {@code cinematic} fight the zoom.</p>
     *
     * <p>Redirecting the <em>read</em> rather than the resulting angle is deliberate and is the
     * same shape as the bob redirect below: the cubic curve stays vanilla's, so a player who has
     * tuned their sensitivity keeps that tuning and scales it, and a frame with the feature
     * unused returns the field untouched.</p>
     */
    @Redirect(method = "render(FJ)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;sensitivity:F",
            opcode = Opcodes.GETFIELD))
    private float void$zoomSensitivity(GameOptions options) {
        VoidClient client = VoidClient.get();
        if (client == null) {
            return options.sensitivity;
        }
        double scale = client.zoomSensitivityScale();
        if (scale >= 1) {
            return options.sensitivity;
        }
        // The curve below this read is `s * 0.6 + 0.2`, cubed. It has a floor: at `sensitivity`
        // 0 the step is still 0.2^3 * 8, which is 6.4% of a normal look rather than none — so
        // scaling the field can slow the mouse a long way but can never stop it, and a player
        // who drags this to 0.2 is left with a usable camera rather than a stuck one. That is a
        // property of vanilla's own arithmetic and is the reason it is safe to scale here.
        return (float) (options.sensitivity * scale);
    }

    /**
     * The Overlay mod's {@code view_bobbing}, camera half (§6.7).
     *
     * <p>1.8.9 has one bob routine, {@code bobView(float)}, and calls it from two places behind
     * the same {@code GameOptions.bobView} flag: once in {@code setupCamera}, where it moves the
     * <em>camera</em>, and twice in {@code renderHand}, where it moves the <em>held item</em>.
     * Vanilla's own switch is all-or-nothing because it is one field; {@code minimal} is the
     * split, and the split exists in the game already — it is just not exposed. So this answers
     * the flag differently per call site rather than cancelling {@code bobView} itself, which
     * could not tell the two apart.</p>
     *
     * <p>Redirecting the field <em>read</em> and not the {@code bobView} call is deliberate: it
     * is the same shape as vanilla's own guard, so a frame with the mod off runs byte-identical
     * vanilla, and the player's real "View Bobbing" option still wins — {@code vanillaValue &&}
     * means this can only ever take bobbing away, never turn it on for someone who switched it
     * off.</p>
     *
     * <p>Both {@code minimal} and {@code off} hold the camera still; that is what they have in
     * common and what most players actually want from the vanilla switch.</p>
     */
    @Redirect(method = "setupCamera", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;bobView:Z",
            opcode = Opcodes.GETFIELD))
    private boolean void$cameraBobEnabled(GameOptions options) {
        LiveState state = LiveState.get();
        return options.bobView && !(state.overlayOn && state.overlayLockCameraBob);
    }

    /**
     * The Overlay mod's {@code view_bobbing}, held-item half (§6.7).
     *
     * <p>Only {@code off} reaches this one. {@code minimal} keeps the hand moving on purpose:
     * the hand is where the bob reads as your own speed rather than as the world moving, so
     * taking the camera's and leaving the hand's is the setting most players are reaching for
     * when they turn the vanilla switch off and then miss something.</p>
     *
     * <p>Two reads in {@code renderHand} share this, and both should: the first wraps the item
     * draw and the second is vanilla's own trailing call after the matrix has been popped. They
     * are the same flag answering the same question, so answering them differently would be a
     * distinction with no picture behind it.</p>
     */
    @Redirect(method = "renderHand", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;bobView:Z",
            opcode = Opcodes.GETFIELD))
    private boolean void$handBobEnabled(GameOptions options) {
        LiveState state = LiveState.get();
        return options.bobView && !(state.overlayOn && state.overlayLockHandBob);
    }

    // -----------------------------------------------------------------
    // Freelook (§6.7)
    //
    // Everything below was established by disassembling the named 1.8.9 jar
    // (`net.legacyfabric:yarn:1.8.9+build.604`, Loom's
    // `minecraft-merged-legacy-intermediary-1-v2` artifact — *not* the `-intermediary`
    // sibling) with `javap -p -c`, and the instruction counts in each `require` are counted
    // out of that disassembly rather than guessed.
    // -----------------------------------------------------------------

    /**
     * The camera's yaw, while freelook is engaged.
     *
     * <p>{@code transformCamera(F)V} — MCP's {@code orientCamera} — is the whole of where the
     * 1.8.9 camera goes, and it reads the camera entity's rotation in three roles. All three
     * want freelook's angle and none of them wants a different one:</p>
     *
     * <ul>
     *   <li><b>The seat.</b> Offsets 300-310 snapshot {@code yaw}/{@code pitch} into locals and
     *       offsets 334-419 turn them into the third-person offset
     *       {@code (-sin y cos p, -sin p, cos y cos p) * distance}.</li>
     *   <li><b>The block clamp.</b> Offsets 421-610 ray-trace that offset from eight corner
     *       offsets and shorten {@code distance} to the nearest hit — vanilla's own "do not put
     *       the camera through a wall", which needs the same direction the seat used or it
     *       clamps against the wrong wall.</li>
     *   <li><b>The orientation.</b> Offsets 708-813, guarded by the debug-camera flag, rotate by
     *       the interpolated {@code prevPitch -> pitch} and {@code prevYaw -> yaw + 180}. This is
     *       the view direction.</li>
     * </ul>
     *
     * <p>Counted out of that method: five {@code GETFIELD Entity.yaw}, five
     * {@code Entity.pitch}, four {@code Entity.prevYaw}, four {@code Entity.prevPitch}. All are
     * redirected, so the seat, the clamp and the orientation cannot disagree.</p>
     *
     * <p><b>Nothing here can move the eye off the pivot, and that is structural.</b> The
     * distance is {@code lastThirdPersonDistance -> thirdPersonDistance}, which this mod never
     * reads and never writes; the pivot is the camera entity's own interpolated position plus
     * its eye height, read at offsets 13-71 from position fields this mod does not redirect;
     * and which of vanilla's three arms runs is {@code GameOptions.perspective}, which the mod
     * may only set to one of the three values vanilla's own F5 cycles. Substituting an angle
     * therefore rotates the camera about the pivot at vanilla's radius and can do nothing else —
     * there is no expression in this file that adds a length to a position.</p>
     *
     * <p>{@code prevYaw} and {@code yaw} answer with the same number on purpose: the camera
     * angle is a per-frame value, so vanilla's interpolation between the two has nothing to
     * interpolate and would only smear a sweep by a frame.</p>
     */
    @Redirect(method = "transformCamera", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/Entity;yaw:F", opcode = Opcodes.GETFIELD),
            require = 5)
    private float void$cameraYaw(Entity entity) {
        VoidClient client = VoidClient.get();
        return client == null ? entity.yaw : client.cameraYaw(entity, entity.yaw);
    }

    @Redirect(method = "transformCamera", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/Entity;prevYaw:F", opcode = Opcodes.GETFIELD),
            require = 4)
    private float void$cameraPrevYaw(Entity entity) {
        VoidClient client = VoidClient.get();
        return client == null ? entity.prevYaw : client.cameraYaw(entity, entity.prevYaw);
    }

    @Redirect(method = "transformCamera", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/Entity;pitch:F", opcode = Opcodes.GETFIELD),
            require = 5)
    private float void$cameraPitch(Entity entity) {
        VoidClient client = VoidClient.get();
        return client == null ? entity.pitch : client.cameraPitch(entity, entity.pitch);
    }

    @Redirect(method = "transformCamera", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/Entity;prevPitch:F", opcode = Opcodes.GETFIELD),
            require = 4)
    private float void$cameraPrevPitch(Entity entity) {
        VoidClient client = VoidClient.get();
        return client == null ? entity.prevPitch : client.cameraPitch(entity, entity.prevPitch);
    }

    /**
     * The Damage tint mod's {@code camera_shake} (§6.7): vanilla's hurt roll, scaled.
     *
     * <p>{@code bobViewWhenHurt(F)V} — MCP's {@code hurtCameraEffect} — is thirteen instructions
     * of roll once the death spin is past. At offsets 100-134:</p>
     *
     * <pre>
     *   float g = entity.knockbackVelocity;          // MCP attackedAtYaw
     *   GlStateManager.rotate(-g,         0, 1, 0);
     *   GlStateManager.rotate(-f * 14.0F, 0, 0, 1);
     *   GlStateManager.rotate( g,         0, 1, 0);
     * </pre>
     *
     * <p>The two rotations about Y are the direction the hit came from and the {@code ldc 14.0f}
     * is the entire amplitude — and it is the <b>only</b> {@code 14.0f} in the method, which is
     * what makes a constant modifier unambiguous here where an ordinal on one of the four
     * {@code rotate} calls would have been a bet on instruction order. Scaling it keeps the lean
     * and takes the amplitude, which is exactly what {@code reduced} promises; {@code off} is
     * zero, at which point the two Y rotations are exact inverses and the frame is untouched.
     * The death spin above it is a different {@code rotate} with a different constant and is
     * deliberately left alone.</p>
     *
     * <p>With the mod off, {@link LiveState#damageTintShake} is vanilla's own 14 and the
     * substitution is the identity, so a frame with the mod off rolls byte-identically to
     * vanilla.</p>
     */
    @ModifyConstant(method = "bobViewWhenHurt", constant = @Constant(floatValue = 14.0F))
    private float void$hurtShakeAmplitude(float vanilla) {
        LiveState state = LiveState.get();
        return state.damageTintOn ? state.damageTintShake : vanilla;
    }
}
