package dev.voidmc.mettle.mixin.blades;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import dev.voidmc.mettle.character.skill.BladesSkill;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.PiercingWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Whirlwind for spears: they stab through PiercingWeapon#attack instead of Player#attack, so the spin hooks in here too. */
@Mixin(PiercingWeapon.class)
abstract class BladesPiercingWeaponMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void mettle$charge(LivingEntity attacker, EquipmentSlot hand, CallbackInfo ci, @Share("charge") LocalFloatRef charge) {
		charge.set(attacker instanceof Player player ? player.getAttackStrengthScale(0.5f) : 0f);
	}

	@Inject(method = "attack", at = @At("TAIL"))
	private void mettle$whirlwind(LivingEntity attacker, EquipmentSlot hand, CallbackInfo ci, @Share("charge") LocalFloatRef charge) {
		BladesSkill.stabbed(attacker, charge.get());
	}
}
