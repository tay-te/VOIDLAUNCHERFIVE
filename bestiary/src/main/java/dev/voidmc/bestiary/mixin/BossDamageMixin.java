package dev.voidmc.bestiary.mixin;

import dev.voidmc.bestiary.boss.BossEncounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A boss's toughness (the party and the game difficulty) on damage that doesn't come through Fray's pipeline (arrows,
 * vanilla hits, other mods): the amount is divided by it, as Fray's own hits are in the attacker stage. Vanilla caps
 * max health at 1024, so a boss's extra health for a party is toughness rather than hit points.
 */
@Mixin(LivingEntity.class)
abstract class BossDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float bestiary$toughness(float amount, ServerLevel level, DamageSource source) {
		return BossEncounter.foreignDamage((LivingEntity) (Object) this, source, amount);
	}
}
