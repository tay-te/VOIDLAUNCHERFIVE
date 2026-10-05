package dev.voidmc.mettle.mixin.defense;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.mettle.character.skill.defense.Wounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Roll with the Blow: a quarter of a hit, measured after armour and Resistance, is held back to be dealt over 4 s. */
@Mixin(Player.class)
abstract class PlayerMixin {
	@ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
	private float mettle$rollWithTheBlow(float damage, ServerLevel level, DamageSource source) {
		return Wounds.hold((Player) (Object) this, source, damage);
	}
}
