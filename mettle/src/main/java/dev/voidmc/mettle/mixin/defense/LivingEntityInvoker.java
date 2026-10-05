package dev.voidmc.mettle.mixin.defense;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Roll with the Blow's held-back damage give a totem its chance, as a normal killing blow would. */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
	@Invoker("checkTotemDeathProtection")
	boolean mettle$checkTotemDeathProtection(DamageSource source);
}
