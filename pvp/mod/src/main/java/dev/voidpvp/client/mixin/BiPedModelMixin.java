package dev.voidpvp.client.mixin;

import dev.voidpvp.client.actuator.OldAnimations;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.render.entity.model.BiPedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Old animations' {@code block_hit}, third-person half (§6.7).
 *
 * <p>This is how <em>other</em> players' blocking looks on your screen. Your own body is drawn by
 * their client, so nothing about how you appear to anyone else changes — the mod is still
 * {@code safe} and still sends nothing.</p>
 *
 * <p><b>The revert is one assignment.</b> 1.8.9 {@code BiPedModel.setAngles}, offsets 251-363:
 * {@code rightArm.posY} is zeroed, then a {@code tableswitch} on {@code rightArmPose} runs, and
 * case 3 — the value {@code PlayerEntityRenderer} writes when the held stack's
 * {@code UseAction} is {@code BLOCK} (offsets 183-191 of its own bytecode) — applies the pitch
 * line and then {@code rightArm.posY = -0.5235988f} at offset 329. 1.7.10 tests
 * {@code rightArmPose != 0}, applies the identical pitch line, and zeroes {@code posY}. So
 * substituting 1.7's zero for that one constant is the whole of it.</p>
 *
 * <p><b>{@code @ModifyConstant} rather than a redirect on the field write, because the constant
 * is unique and the field is not.</b> {@code ldc -0.5235988f} appears exactly once in the whole
 * class; {@code putfield ModelPart.posY} appears fourteen times in {@code setAngles} alone, so a
 * {@code @Redirect} would have to be pinned by an ordinal that means nothing to a reader and
 * everything to the injector.</p>
 *
 * <p><b>{@code ModelPart.posY} is a rotation.</b> Legacy Fabric names the rotation triple
 * {@code posX/posY/posZ} and the translation triple {@code pivotX/pivotY/pivotZ};
 * {@code ModelPart.render} feeds the first to {@code GlStateManager.rotate} at offsets 117-135
 * and the second to {@code GlStateManager.translate}. The constant is therefore a yaw of −30°,
 * not an offset, and implementing it as a translate draws nothing.</p>
 *
 * <p>With the mod off {@link OldAnimations#blockArmYaw} returns vanilla's own constant, so this
 * is the identity substitution and every other biped in the frame is untouched.</p>
 */
@Mixin(BiPedModel.class)
public abstract class BiPedModelMixin {

    @ModifyConstant(method = "setAngles",
            constant = @Constant(floatValue = OldAnimations.VANILLA_BLOCK_ARM_YAW))
    private float void$blockArmYaw(float vanilla) {
        LiveState state = LiveState.get();
        return OldAnimations.blockArmYaw(state.oldAnimationsOn,
                state.oldAnimationsBlockHitOneSeven);
    }
}
