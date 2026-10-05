package dev.voidmc.mettle.mixin.character;

import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.MiningSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Backstab (Rogue): a sneaking melee hit on a mob that is not hunting you deals double damage. Deep Delver (Mining):
 * 30 % less damage taken below y 0.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$backstab(float damage, ServerLevel level, DamageSource source) {
		if (damage <= 0 || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) {
			return damage;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Mob mob) || mob.getTarget() == player || !player.isShiftKeyDown() || !SignaturePerks.has(player, SignaturePerks.BACKSTAB)) {
			return damage;
		}
		SignaturePerks.feedback(player, SignaturePerks.BACKSTAB, Component.translatable("mettle.feedback.backstab"));
		return damage * 2f;
	}

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$deepDelver(float damage) {
		return (Object) this instanceof ServerPlayer player ? MiningSkill.damageTaken(player, damage) : damage;
	}
}
