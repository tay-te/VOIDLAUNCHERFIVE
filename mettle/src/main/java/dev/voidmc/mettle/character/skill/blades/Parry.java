package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.BladesSkill;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Duelist (mastery): right-click with a sword opens a 0.4 s parry. A melee hit from in front caught in it does nothing;
 * the attacker staggers for a second (slowed, pushed back, unable to land a hit) and your next full swing is a
 * critical. Swords have no right-click use in vanilla, so nothing else changes; with a shield in the off hand the shield
 * wins, so a sword-and-board fighter blocks as before. A second attempt waits a second, shown as the sword's cooldown.
 */
public final class Parry {
	private static final Map<UUID, Long> STAGGERED = new HashMap<>();

	private Parry() {
	}

	/** The sword was used (right-click). */
	public static void start(ServerPlayer player) {
		ItemStack sword = player.getMainHandItem();
		if (!sword.is(ItemTags.SWORDS) || player.isSpectator() || !Perks.has(player, BladesSkill.DUELIST)
			|| player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) || player.getCooldowns().isOnCooldown(sword)) {
			return;
		}
		BladeState.of(player).parryUntil = player.level().getGameTime() + BladeMath.PARRY_WINDOW;
		player.getCooldowns().addCooldown(sword, BladeMath.PARRY_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, sword.getAttackAnimation(), true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8f, 1.5f);
	}

	/** Fabric's ALLOW_DAMAGE: a parried or staggered melee hit never lands. */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker || !melee(source)) {
			return true;
		}
		long now = entity.level().getGameTime();
		Long staggered = STAGGERED.isEmpty() ? null : STAGGERED.get(attacker.getUUID());
		if (staggered != null && staggered >= now) {
			return false;
		}
		if (entity instanceof ServerPlayer player && BladeState.of(player).parryUntil >= now && player.getMainHandItem().is(ItemTags.SWORDS)
			&& Perks.has(player, BladesSkill.DUELIST) && facing(player, attacker)) {
			parried(player, attacker, now);
			return false;
		}
		return true;
	}

	public static boolean staggered(LivingEntity entity) {
		Long until = STAGGERED.get(entity.getUUID());
		return until != null && until >= entity.level().getGameTime();
	}

	private static void parried(ServerPlayer player, LivingEntity attacker, long now) {
		BladeState state = BladeState.of(player);
		state.parryUntil = -1;
		state.riposteUntil = now + BladeMath.RIPOSTE_WINDOW;
		if (Perks.has(player, BladesSkill.MASTER_STRIKE)) {
			state.counterUntil = now + BladeMath.COUNTER_WINDOW;
		}
		STAGGERED.put(attacker.getUUID(), now + BladeMath.STAGGER);
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BladeMath.STAGGER, 3), player);
		attacker.knockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ(), player.damageSources().playerAttack(player), 0f);
		if (attacker instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		ServerLevel level = player.level();
		Vec3 clash = player.getEyePosition().add(attacker.getEyePosition()).scale(0.5);
		level.sendParticles(ParticleTypes.CRIT, clash.x, clash.y, clash.z, 14, 0.15, 0.15, 0.15, 0.4);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0f, 1.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 2.0f);
		BladesSkill.chip(player, BladesSkill.DUELIST, "duelist");
	}

	/** Melee only: arrows, explosions, magic and thorns can't be parried. */
	private static boolean melee(DamageSource source) {
		return !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.BYPASSES_ARMOR)
			&& !source.is(DamageTypes.THORNS);
	}

	/** A parry faces forwards: the attacker must be within 90° of where you look. */
	private static boolean facing(ServerPlayer player, LivingEntity attacker) {
		Vec3 look = player.getViewVector(1f);
		return BladeMath.inFront(look.x, look.z, attacker.getX() - player.getX(), attacker.getZ() - player.getZ());
	}

	public static void expire(long now) {
		STAGGERED.values().removeIf(until -> until < now);
	}

	public static void clear() {
		STAGGERED.clear();
	}
}
