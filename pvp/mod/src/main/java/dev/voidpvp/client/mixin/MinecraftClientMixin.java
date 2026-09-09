package dev.voidpvp.client.mixin;

import dev.voidpvp.client.HiDpi;
import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.sensor.HitTally;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-tick, resize and server connect/disconnect sensors (§6.6).
 *
 * <p>Thin on purpose: each hook forwards to {@link VoidClient} and the logic
 * lives in plain classes that can be unit-tested, because a Mixin cannot.</p>
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /** One client tick: the 20 Hz beat behind the {@code tick} event. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void void$onTick(CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.onClientTick();
        }
    }

    /**
     * One run of {@code MinecraftClient.doAttack}: one swing, reported with what it resolved
     * onto, so {@link dev.voidpvp.client.sensor.HitTally} can decide whether it landed.
     *
     * <p>Everything else Wave 2 reads is a *level* on the player entity that can be sampled once
     * per tick (`VoidClient.readWave2`). A landed hit is not: the player's own state says nothing
     * about whether their swing connected, and `hurtTime` on the target is on the target, which
     * the client is not tracking. So this is the one place the sensor has to be told rather than
     * ask.</p>
     *
     * <p><b>What {@code doAttack} actually does in 1.8.9, and how that is known.</b> The comment
     * that used to stand here said {@code doAttack} "runs only when there is something to attack
     * … so reaching TAIL is the swing having connected". That is false, and it is why
     * {@code hits.dealt} shipped as a second CPS counter. Disassembling the named 1.8.9 jar
     * ({@code net.legacyfabric:yarn:1.8.9+build.604}, Loom's
     * {@code minecraft-merged-legacy-intermediary-1-v2} artifact) with {@code javap -p -c}:</p>
     *
     * <ul>
     *   <li>{@code MinecraftClient.tick} at offset 1751 is
     *       {@code while (options.attackKey.wasPressed()) doAttack();} — a bare
     *       {@code KeyBinding.wasPressed()} test with no look at {@code result}. Every click
     *       calls {@code doAttack}, whatever the crosshair is on.</li>
     *   <li>{@code doAttack} returns early only twice: at offset 7 when {@code attackCooldown > 0}
     *       and at offset 49 when {@code result} is null. Otherwise it swings the hand at offset
     *       12 — unconditionally, before anything is examined — and then switches on
     *       {@code result.type} into three arms: {@code ENTITY} (92, the only one that calls
     *       {@code ClientPlayerInteractionManager.attackEntity}), {@code BLOCK} (113, mining) and
     *       {@code MISS} (162, which only re-arms the cooldown). All three reach the single
     *       trailing {@code return} at offset 178, which is the instruction {@code @At("TAIL")}
     *       injects before.</li>
     *   <li>The arm numbering is the {@code MinecraftClient$10.field_10313} switch map, whose
     *       static initialiser assigns 1 to {@code BlockHitResult$Type.ENTITY} and 2 to
     *       {@code BLOCK}, leaving {@code MISS} on the {@code default} branch.</li>
     * </ul>
     *
     * <p>So TAIL is reached by a swing at air and by a swing at a block just as surely as by a
     * hit, and the fix is not to move the injection point but to read {@code result} here and
     * pass the truth on. The hook stays at TAIL precisely because it is the one place all three
     * arms meet: classifying them is then this method's job and is visible, rather than being an
     * implicit property of an injection point that a later refactor could quietly change.</p>
     *
     * <p>It only ever moves a counter. The <em>policy</em> — how long a combo survives without a
     * hit — is `combo.reset_ms` and lives on the client, for the reason `bridge.json`'s `hits`
     * field records: a timeout is a mod setting, and putting a mod setting in a sensor is how a
     * sensor starts needing to know about mods.</p>
     */
    @Inject(method = "doAttack", at = @At("TAIL"))
    private void void$onAttack(CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client == null) {
            return;
        }
        MinecraftClient mc = (MinecraftClient) (Object) this;
        BlockHitResult hit = mc.result;
        // Null is unreachable from TAIL — offset 49 returns before it — but the field is public
        // and mutable and this costs one branch.
        Entity target = hit == null ? null : hit.entity;
        HitTally.Swing at;
        if (hit == null || hit.type == null) {
            at = HitTally.Swing.AIR;
        } else if (hit.type == BlockHitResult.Type.ENTITY && target != null) {
            at = HitTally.Swing.ENTITY;
        } else if (hit.type == BlockHitResult.Type.BLOCK) {
            at = HitTally.Swing.BLOCK;
        } else {
            at = HitTally.Swing.AIR;
        }
        client.onAttackSwing(at,
                target != null && target.isAlive(),
                target != null && target.isAttackable(),
                mc.player != null && mc.player.isSpectator());
    }

    /**
     * Head of the game loop, once per rendered frame: drain LWJGL's input queues into the VOID
     * menu, instead of leaving them to {@code tick}'s own {@code Screen.handleInput} at 20 Hz.
     *
     * <p>Here rather than anywhere inside the render pass because this is a few statements ahead
     * of where 1.8.9 runs {@code handleInput} itself — before the scheduled executables, before the
     * tick loop, before a framebuffer is bound. Everything these callbacks are allowed to do from
     * the tick they are therefore still allowed to do: replace the screen, take a screenshot,
     * toggle full screen. See {@link VoidClient#pumpMenuInput()}.</p>
     */
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void void$pumpMenuInput(CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.pumpMenuInput();
        }
    }

    /** The framebuffer changed size; the Ultralight view follows it (§6.2). */
    @Inject(method = "onResolutionChanged", at = @At("TAIL"))
    private void void$onResize(int width, int height, CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.onResize();
        }
    }

    /**
     * Retina (§13): {@code onResolutionChanged} is handed {@code Display.getWidth()/getHeight()},
     * which LWJGL reports in points even when the backing store is 2x. It assigns them straight to
     * {@code width}/{@code height}, and those are what size the viewport, the main framebuffer and
     * {@code Window}'s GUI scale — so rewriting the arguments here is what makes the whole game
     * render at the display's real resolution rather than being upscaled by the compositor.
     * See {@link HiDpi}; a no-op wherever the factor is 1.
     */
    @ModifyVariable(method = "onResolutionChanged(II)V", at = @At("HEAD"), argsOnly = true,
            ordinal = 0)
    private int void$resolutionWidthInPixels(int width) {
        return HiDpi.toPixels(width);
    }

    @ModifyVariable(method = "onResolutionChanged(II)V", at = @At("HEAD"), argsOnly = true,
            ordinal = 1)
    private int void$resolutionHeightInPixels(int height) {
        return HiDpi.toPixels(height);
    }

    /**
     * A world is being loaded or torn down. This is 1.8.9's single
     * connect/disconnect funnel — joining a server, leaving one and switching
     * all pass through here — which makes it a steadier hook than the packet
     * handlers, several of which legacy yarn leaves unnamed.
     */
    @Inject(method = "connect(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V",
            at = @At("HEAD"))
    private void void$onConnect(ClientWorld world, String message, CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client == null) {
            return;
        }
        MinecraftClient mc = (MinecraftClient) (Object) this;
        ServerInfo entry = mc.getCurrentServerEntry();
        client.onWorldChanged(world != null && entry != null,
                entry == null ? null : entry.address);
    }
}
