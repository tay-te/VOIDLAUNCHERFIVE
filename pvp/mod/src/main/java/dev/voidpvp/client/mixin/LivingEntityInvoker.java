package dev.voidpvp.client.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches {@code LivingEntity.getMiningSpeedMultiplier()}, which is private.
 *
 * <p>Old animations' {@code swing_during_delay} reproduces the body of
 * {@code LivingEntity.swingHand()} without {@code ClientPlayerEntity.swingHand()}'s outbound
 * packet, and that body opens with a re-entry guard reading this method — {@code handSwingTicks
 * >= getMiningSpeedMultiplier() / 2}. The two fields it also reads, {@code handSwinging} and
 * {@code handSwingTicks}, are public; this one is not, so it takes an {@code @Invoker} rather
 * than a copy. Copying it would be worse than verbose: it returns 6 normally,
 * {@code 6 - (1 + amplifier)} under Haste and {@code 6 + (1 + amplifier) * 2} under Mining
 * Fatigue, so a copy would silently disagree with the game the moment a potion landed.</p>
 *
 * <p><b>Declared on {@code LivingEntity}, checked rather than assumed.</b> Legacy yarn 1.8.9 puts
 * {@code getMiningSpeedMultiplier}, {@code handSwinging} and {@code handSwingTicks} on
 * {@code LivingEntity} and none of the three on {@code Entity} — a previous wave shipped an
 * accessor aimed at the class the calling instruction named rather than the class that declares
 * the member, and it compiled, passed every test and failed at load. The player this is called on
 * is a {@code ClientPlayerEntity}, which reaches {@code LivingEntity} through
 * {@code AbstractClientPlayerEntity} and {@code PlayerEntity}.</p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {

    @Invoker("getMiningSpeedMultiplier")
    int void$getMiningSpeedMultiplier();
}
