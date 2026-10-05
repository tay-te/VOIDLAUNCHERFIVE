package dev.voidmc.mettle.mixin.heavyweapons;

import dev.voidmc.mettle.character.skill.HeavyWeaponsSkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Heft and Berserker scale a heavy hit as it arrives; Armour Crusher lowers the armour vanilla weighs it against. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$heavyHit(float damage, ServerLevel level, DamageSource source) {
		return HeavyWeaponsSkill.outgoing((LivingEntity) (Object) this, source, damage);
	}

	@ModifyArg(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"),
		index = 3)
	private float mettle$heavyArmourCrusher(LivingEntity victim, float damage, DamageSource source, float armor, float toughness) {
		return HeavyWeaponsSkill.armour(source, armor);
	}
}
