package dev.voidpvp.client.mixin;

import dev.voidpvp.client.actuator.FovLock;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The FOV changer's two locks (§6.7): {@code lock_sprint} and {@code lock_bow}.
 *
 * <p><b>Why here and not in {@code GameRenderer}.</b> 1.8.9 computes the field of view in
 * {@code GameRenderer.getFov}, which multiplies {@code GameOptions.fov} by an eased
 * {@code movementFovMultiplier}; that multiplier is eased, in {@code updateMovementFovMultiplier},
 * towards whatever this method last returned, and this method is its <em>only</em> input. So
 * {@code getSpeed} is the single place where both of the things this mod suppresses are decided —
 * the movement-speed scaling and the bow-pull zoom — and it is the only place either of them can
 * be suppressed without reimplementing the easing. Verified against the 1.8.9 bytecode under
 * {@code net.legacyfabric:yarn:1.8.9+build.604}: {@code AbstractClientPlayerEntity.getSpeed()} is
 * invoked from exactly one site in the whole client, {@code GameRenderer
 * .updateMovementFovMultiplier}, so overriding it cannot reach anything but the field of view.</p>
 *
 * <p>Returning through the easing rather than around it is also what makes the lock look right:
 * {@code movementFovMultiplier} moves half the remaining distance per tick, so switching the mod
 * on eases the camera back out over a few ticks instead of snapping it, which is the same motion
 * the player already knows from stopping sprinting.</p>
 *
 * <p>The arithmetic is {@link FovLock}'s, because {@code getSpeed} hands back the two components
 * multiplied together and the two settings are independent — see that class. This method's own
 * job is only to say whether a bow is being drawn and for how long, which it reads from the same
 * three calls vanilla reads two instructions earlier.</p>
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
    private void void$lockFov(CallbackInfoReturnable<Float> cir) {
        LiveState state = LiveState.get();
        if (!state.fovOn || (!state.fovLockSprint && !state.fovLockBow)) {
            return;
        }
        PlayerEntity self = (PlayerEntity) (Object) this;
        // Vanilla's own test, in vanilla's own order: `isUsingItem() && getUsedItem().getItem()
        // == Items.BOW`, then `getRemainingUseTime()` for how far it is drawn. Recomputed rather
        // than captured, because the value we need is the bow's contribution on its own and
        // `getSpeed` only ever returns it multiplied by the speed term.
        ItemStack using = self.isUsingItem() ? self.getUsedItem() : null;
        boolean drawingBow = using != null && using.getItem() == Items.BOW;
        float bow = FovLock.bowFactor(drawingBow, drawingBow ? self.getRemainingUseTime() : 0);
        cir.setReturnValue(Float.valueOf(FovLock.apply(true, state.fovLockSprint,
                state.fovLockBow, cir.getReturnValueF(), bow)));
    }
}
