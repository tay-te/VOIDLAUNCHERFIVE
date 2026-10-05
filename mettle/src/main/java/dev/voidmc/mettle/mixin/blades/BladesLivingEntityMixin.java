package dev.voidmc.mettle.mixin.blades;

import dev.voidmc.mettle.character.skill.BladesSkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blade hit bonuses and Single Combat's guard (on the incoming damage, before shields and armour), Master Strike's
 * armour-piercing counter, and Measure's longer spear reach (both sides, so the server's stab and the client agree).
 */
@Mixin(LivingEntity.class)
abstract class BladesLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$blades(float damage, ServerLevel level, DamageSource source) {
		return BladesSkill.damage((LivingEntity) (Object) this, source, damage);
	}

	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
	private void mettle$masterStrike(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if (BladesSkill.ignoresArmour((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(damage);
		}
	}

	@Inject(method = "getAttackRangeWith", at = @At("RETURN"), cancellable = true)
	private void mettle$measure(ItemStack weapon, CallbackInfoReturnable<AttackRange> cir) {
		AttackRange range = cir.getReturnValue();
		AttackRange longer = BladesSkill.reach((LivingEntity) (Object) this, weapon, range);
		if (longer != range) {
			cir.setReturnValue(longer);
		}
	}
}
