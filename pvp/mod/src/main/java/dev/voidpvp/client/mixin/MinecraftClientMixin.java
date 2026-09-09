package dev.voidpvp.client.mixin;

import dev.voidpvp.client.HiDpi;
import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.actuator.OldAnimations;
import dev.voidpvp.client.actuator.OldInput;
import dev.voidpvp.client.sensor.HitTally;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
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
    /**
     * {@code isSprinting()} as it was at the top of the swing, for {@code HitTally.sprintDealt}.
     *
     * <p><b>A field rather than an argument because the fact expires inside the method.</b>
     * 1.8.9's {@code PlayerEntity.attack} calls {@code setSprinting(false)} at offset 339, in the
     * branch that applies the knockback a sprint earned — and the client reaches it: {@code
     * ClientPlayerInteractionManager.attackEntity} runs {@code PlayerEntity.attack} at its own
     * offsets 32-34 for every game mode but spectator. So by {@code doAttack} TAIL the flag reads
     * false for exactly the swings the counter exists to count, and a player landing a perfect
     * chain of sprint-hits would be shown zero of them.</p>
     *
     * <p><b>And it is read at HEAD rather than reported from there</b>, because whether the swing
     * landed is not known until TAIL. Splitting the read from the decision keeps
     * {@link HitTally}'s one definition of "landed" — the alternative, a second injection next to
     * {@code attackEntity} that reports its own hits, is exactly the second definition the
     * {@code onAttackSwing} comment below refuses.</p>
     *
     * <p>Not reset between swings: {@code doAttack} cannot run re-entrantly and every TAIL is
     * preceded by the HEAD that wrote this, so a stale value is unreachable rather than
     * defended against with a flag that would need its own reasoning.</p>
     */
    @Unique
    private boolean void$sprintingAtSwing;

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void void$readSprintBeforeAttack(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        void$sprintingAtSwing = mc.player != null && mc.player.isSprinting();
    }

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
                mc.player != null && mc.player.isSpectator(),
                void$sprintingAtSwing);
        // The same swing, reported a second way for `hit_color.own_hits_only`. Separate from the
        // HitTally call on purpose: that one is a counter with a defended definition of "landed"
        // and this one is a scope filter that only has to know which entity was under the
        // crosshair, so widening the counter's contract to carry an entity id would put a mod
        // setting inside a sensor — the thing `HitTally`'s own comment refuses.
        if (at == HitTally.Swing.ENTITY && target != null) {
            client.onAttackedEntity(target.getEntityId());
        }
    }

    /**
     * Old animations' {@code swing_during_delay} (§6.7): the arm keeps up with the mouse during
     * the ten ticks 1.8 swallows after a whiffed click.
     *
     * <p><b>This is the injection point, and it is the whole condition.</b> {@code doAttack} has
     * exactly three {@code return} instructions — offset 7 (the {@code attackCooldown > 0} early
     * out), offset 49 (the null-hit-result path) and offset 178 (the single trailing return every
     * arm of the {@code tableswitch} reaches). {@code ordinal = 0} is therefore the swallowed
     * click and nothing else: reaching it <em>is</em> the cooldown having eaten the click, so the
     * guard does not have to be restated here and cannot drift away from vanilla's.</p>
     *
     * <p><b>Nothing leaves the client.</b> {@code ClientPlayerEntity.swingHand()} is
     * {@code super.swingHand()} plus {@code networkHandler.sendPacket(new HandSwingC2SPacket())};
     * this reproduces the body of {@code LivingEntity.swingHand()} — the guard in
     * {@link OldAnimations#restartsSwing}, then {@code handSwingTicks = -1} and
     * {@code handSwinging = true}, both public fields — and calls neither. The remaining branch
     * of the vanilla method, {@code world instanceof ServerWorld} broadcasting an
     * {@code EntityAnimationS2CPacket}, is the server's own copy and is unreachable from a client
     * world; it is not reproduced either. That is why this setting is in the {@code safe} mod
     * while {@code old_input.no_miss_delay} — which lets the click through, and so lets the swing
     * packet out — is in the {@code grey} one.</p>
     *
     * <p><b>And the click is still swallowed.</b> It does not attack, does not start mining and
     * is not sent anywhere. Only the animation comes back, which is why this is a third behaviour
     * rather than a 1.7 revert and why it ships off.</p>
     */
    @Inject(method = "doAttack", at = @At(value = "RETURN", ordinal = 0))
    private void void$swingDuringDelay(CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (!state.oldAnimationsOn || !state.oldAnimationsSwingDuringDelay) {
            return;
        }
        MinecraftClient mc = (MinecraftClient) (Object) this;
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return;
        }
        int multiplier = ((LivingEntityInvoker) (Object) player).void$getMiningSpeedMultiplier();
        if (OldAnimations.restartsSwing(player.handSwinging, player.handSwingTicks, multiplier)) {
            player.handSwingTicks = -1;
            player.handSwinging = true;
        }
    }

    /**
     * Old input's {@code no_miss_delay} (§6.7): the half-second of dead time after a whiff.
     *
     * <p><b>Why this needs a runtime test and not just an injection point.</b> 1.8.9's
     * {@code doAttack} arms {@code attackCooldown = 10} behind {@code hasLimitedAttackSpeed()} in
     * two places — offsets 33-46 on the null-hit-result path, and offsets 162-175 on the tail
     * <em>shared</em> by the {@code tableswitch}'s MISS arm and by a BLOCK hit whose block turned
     * out to be {@code Material.AIR} (the {@code if_acmpeq 162} at offset 140). 1.7.10 arms the
     * same cooldown on both of those other paths: its null path is offsets 33-46 and its
     * air-block path is 156-169. Only MISS differs — 1.7's {@code lookupswitch} has keys 1 and 2
     * alone and sends everything else to {@code default: 192}, the bare {@code return}.</p>
     *
     * <p>So {@code ordinal = 1} picks the right instruction and is still not enough: the two
     * cases that share it have to be told apart by reading {@code result.type}. Suppressing the
     * field write, or this whole tail, would be a shorter mixin that goes past 1.7 rather than
     * back to it.</p>
     */
    @Redirect(method = "doAttack", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;"
                    + "hasLimitedAttackSpeed()Z"))
    private boolean void$noMissDelay(ClientPlayerInteractionManager manager) {
        boolean limited = manager.hasLimitedAttackSpeed();
        LiveState state = LiveState.get();
        MinecraftClient mc = (MinecraftClient) (Object) this;
        BlockHitResult hit = mc.result;
        boolean miss = hit != null && hit.type == BlockHitResult.Type.MISS;
        return OldInput.hasLimitedAttackSpeed(limited, state.oldInputOn, state.oldInputNoMissDelay,
                miss);
    }

    /**
     * Old input's {@code use_while_digging} (§6.7): right click acts while you are mining.
     *
     * <p>{@code doUse()} opens at offsets 0-10 with
     * {@code if (this.interactionManager.isBreakingBlock()) return;}, and that is the only call to
     * {@code isBreakingBlock()} in the whole class — so no ordinal, and the scope is exact.
     * 1.7.10's {@code doUse} has no such guard and its
     * {@code ClientPlayerInteractionManager} has no {@code isBreakingBlock()} at all.</p>
     *
     * <p>Answering the guard rather than cancelling the method is deliberate and is the same
     * shape as the Overlay mod's bobbing redirects: with the mod off this returns exactly what
     * the interaction manager said, so the click path is vanilla's.</p>
     */
    @Redirect(method = "doUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;"
                    + "isBreakingBlock()Z"))
    private boolean void$useWhileDigging(ClientPlayerInteractionManager manager) {
        LiveState state = LiveState.get();
        return OldInput.isBreakingBlock(manager.isBreakingBlock(), state.oldInputOn,
                state.oldInputUseWhileDigging);
    }

    /**
     * Old input's {@code dig_while_using} (§6.7): left click keeps mining while an item is in use.
     *
     * <p>The mirror of the setting above, in the method next door.
     * {@code handleBlockBreaking(Z)V} opens at offsets 9-26 with
     * {@code if (this.attackCooldown > 0 || this.player.isUsingItem()) return;}; 1.7.10's, at
     * offsets 9-16, is the cooldown term alone.</p>
     *
     * <p><b>The scope is the reason this is a {@code method =} and not an ordinal.</b>
     * {@code isUsingItem()} is called three times in 1.8.9's {@code MinecraftClient} — here and
     * twice inside {@code tick}, at offsets 1673 and 1835 — and those two are byte-identical to
     * 1.7.10's own and must stay untouched. Naming the method is what keeps this to the one call
     * that differs.</p>
     */
    @Redirect(method = "handleBlockBreaking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ClientPlayerEntity;isUsingItem()Z"))
    private boolean void$digWhileUsing(ClientPlayerEntity player) {
        LiveState state = LiveState.get();
        return OldInput.isUsingItemForBreaking(player.isUsingItem(), state.oldInputOn,
                state.oldInputDigWhileUsing);
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
