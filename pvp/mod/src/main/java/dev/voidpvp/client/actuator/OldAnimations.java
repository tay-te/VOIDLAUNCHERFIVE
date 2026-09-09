package dev.voidpvp.client.actuator;

/**
 * The Old animations actuator (§6.7): the two numbers {@code block_hit} substitutes, and the
 * re-entry guard {@code swing_during_delay} has to reproduce.
 *
 * <p><b>Every constant here was read out of the bytecode rather than remembered</b> — Loom's
 * named 1.8.9 jar ({@code net.legacyfabric:yarn:1.8.9+build.604}) against a 1.7.10 client jar
 * remapped with Legacy Fabric yarn {@code 1.7.10+build.603}, method against method. The mod's own
 * schema records the three settings that did not survive that comparison; what is left is below.
 * </p>
 *
 * <h2>{@code block_hit}, first person — one float argument</h2>
 *
 * <p>1.8.9 {@code HeldItemRenderer.renderArmHoldingItem(F)V}, offsets 131-241, is a
 * {@code tableswitch} over {@code UseAction} taken while {@code getItemUseTicks() > 0}. Every arm
 * calls {@code applyEquipAndSwingOffset(equipProgress, 0.0F)} — {@code fconst_0}, the swing
 * progress thrown away — and only the not-using path at 244 passes the live value. The BLOCK arm
 * is key 4 of that switch ({@code HeldItemRenderer$1.field_10793}'s {@code <clinit>} assigns 4 to
 * {@code UseAction.BLOCK}) at offsets 216-226: {@code applyEquipAndSwingOffset(equip, 0.0F)} then
 * {@code applySwordBlockTransformation()}.</p>
 *
 * <p>1.7.10's {@code renderArmHoldingItem} is the same code without the helper methods, and the
 * three pieces sit in a different order:</p>
 *
 * <ul>
 *   <li>Offsets 1202-1266, inside the <em>else</em> of {@code getItemUseTicks() > 0}: the swing
 *       <b>translate</b> — the same three sines 1.8.9 factored out as
 *       {@code translateSwingProgress}. <b>1.7 skipped it while an item was in use, exactly as
 *       1.8.9 does.</b> This is why {@link #blockSwingProgress} is the whole fix and why nothing
 *       here adds that translate back: adding it would overshoot 1.7, not restore it.</li>
 *   <li>Offsets 1269-1397, <b>outside</b> that branch: the equip translate
 *       ({@code 0.7f*0.8f, -0.65f*0.8f - (1-equip)*0.6f, -0.9f*0.8f}, which is 1.8.9's
 *       constant-folded {@code translate(0.56f, -0.52f, -0.71999997f)} plus its
 *       {@code equip * -0.6f}), the 45° yaw, and then a <b>fresh, live</b>
 *       {@code getHandSwingProgress(tickDelta)} at 1312 driving the same three rotations
 *       ({@code sin(p*p*PI) * -20} about Y, {@code sin(sqrt(p)*PI) * -20} about Z,
 *       {@code sin(sqrt(p)*PI) * -80} about X) and the same {@code scale(0.4f)}. That whole
 *       stretch is byte-for-byte 1.8.9's {@code applyEquipAndSwingOffset(F F)V}.</li>
 *   <li>Offsets 1422-1456: the block pose, {@code translate(-0.5f, 0.2f, 0)} then 30° Y, -80° X,
 *       60° Y — identical constants to 1.8.9's {@code applySwordBlockTransformation}, which is
 *       therefore not touched by any of this.</li>
 * </ul>
 *
 * <p>So the entire difference between the two versions, in the blocking case, is that 1.7 reached
 * the equip-and-swing block with a live swing progress and 1.8.9 reaches it with zero.</p>
 *
 * <h2>{@code block_hit}, third person — one assignment</h2>
 *
 * <p>1.8.9 {@code BiPedModel.setAngles}, offsets 251-363: {@code rightArm.posY} is zeroed, then a
 * {@code tableswitch} on {@code rightArmPose} runs. Case 3 — which
 * {@code PlayerEntityRenderer} sets on {@code UseAction.BLOCK} — applies the pitch line
 * ({@code rightArm.posX = rightArm.posX * 0.5f - 0.31415927f * rightArmPose}) and then
 * <b>{@code rightArm.posY = -0.5235988f}</b> at offset 329. 1.7.10's equivalent (offsets 274-312)
 * tests {@code rightArmPose != 0}, applies the identical pitch line, and then zeroes
 * {@code rightArm.posY} — it has no such assignment.</p>
 *
 * <p><b>{@code ModelPart.posY} is a rotation, not a position.</b> Legacy Fabric names the
 * rotation triple {@code posX/posY/posZ} and the translation triple {@code pivotX/pivotY/pivotZ};
 * {@code ModelPart.render} feeds the first to {@code GlStateManager.rotate} and the second to
 * {@code GlStateManager.translate}. So the value below is a yaw of −π/6 = −30°, and the revert is
 * to write 1.7's zero in its place.</p>
 *
 * <h2>{@code swing_during_delay} — the local half of a swallowed click</h2>
 *
 * <p>{@link #restartsSwing} is the condition at the top of 1.8.9's
 * {@code LivingEntity.swingHand()} (offsets 0-24), and it is here rather than inlined in the
 * Mixin because it is the one piece of this mod that is arithmetic and can therefore be wrong:
 * an integer division whose truncation decides whether a fast click restarts the arm or is
 * absorbed into the swing already playing.</p>
 */
public final class OldAnimations {

    /**
     * The right-arm yaw 1.8.9 adds to the blocking pose, in radians: {@code ldc -0.5235988f} at
     * offset 329 of {@code BiPedModel.setAngles}, the only occurrence of that constant in the
     * class. −30°, to the last digit of {@code (float) (-Math.PI / 6)}.
     */
    public static final float VANILLA_BLOCK_ARM_YAW = -0.5235988f;

    /** What 1.7.10 left {@code rightArm.posY} at in the same pose: nothing. */
    public static final float ONE_SEVEN_BLOCK_ARM_YAW = 0f;

    private OldAnimations() {
    }

    /**
     * The swing progress the BLOCK arm of {@code renderArmHoldingItem} should hand
     * {@code applyEquipAndSwingOffset}.
     *
     * @param on       {@code old_animations.on}
     * @param oneSeven {@code block_hit} is {@code one_seven} rather than {@code vanilla}
     * @param live     {@code player.getHandSwingProgress(tickDelta)}, as 1.7 read it
     * @return {@code live} when the revert is active, and otherwise vanilla's own hard-coded
     *         {@code 0.0F}, so a frame with the mod off draws byte-identically to vanilla
     */
    public static float blockSwingProgress(boolean on, boolean oneSeven, float live) {
        if (!on || !oneSeven || Float.isNaN(live)) {
            return 0f;
        }
        return live;
    }

    /**
     * The value {@code BiPedModel.setAngles} should write to {@code rightArm.posY} in the BLOCK
     * pose.
     *
     * <p>With the mod off this is vanilla's own constant, so the substitution is the identity and
     * every other entity model in the frame is untouched.</p>
     */
    public static float blockArmYaw(boolean on, boolean oneSeven) {
        if (!on || !oneSeven) {
            return VANILLA_BLOCK_ARM_YAW;
        }
        return ONE_SEVEN_BLOCK_ARM_YAW;
    }

    /**
     * Whether a swing should be (re)started, which is {@code LivingEntity.swingHand()}'s own
     * guard and nothing else.
     *
     * <p>The bytecode, at offsets 0-24 of that method: {@code if (!handSwinging) restart;
     * if (handSwingTicks >= getMiningSpeedMultiplier() / 2) restart; if (handSwingTicks < 0)
     * restart; else return;}. The division is integer division of a value that is 6 with no
     * status effect, {@code 6 - (1 + amplifier)} under Haste and {@code 6 + (1 + amplifier) * 2}
     * under Mining Fatigue — so the half-way point a fast click has to pass is 3 normally and can
     * be as low as 2. Truncation is vanilla's and is kept.</p>
     *
     * @param handSwinging          {@code LivingEntity.handSwinging}, public
     * @param handSwingTicks        {@code LivingEntity.handSwingTicks}, public
     * @param miningSpeedMultiplier {@code LivingEntity.getMiningSpeedMultiplier()}, which is
     *                              private and therefore reached through an {@code @Invoker}
     */
    public static boolean restartsSwing(boolean handSwinging, int handSwingTicks,
                                        int miningSpeedMultiplier) {
        return !handSwinging
                || handSwingTicks >= miningSpeedMultiplier / 2
                || handSwingTicks < 0;
    }
}
